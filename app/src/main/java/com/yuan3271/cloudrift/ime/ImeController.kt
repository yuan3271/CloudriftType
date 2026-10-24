package com.yuan3271.cloudrift.ime

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.inputmethod.EditorInfo
import com.yuan3271.cloudrift.data.AppGraph
import com.yuan3271.cloudrift.data.AppSettings
import com.yuan3271.cloudrift.data.ClipEntry
import com.yuan3271.cloudrift.data.ClipboardStore
import com.yuan3271.cloudrift.data.ExternalKeyFlavor
import com.yuan3271.cloudrift.data.SettingsRepository
import com.yuan3271.cloudrift.data.SymbolWidth
import com.yuan3271.cloudrift.data.ThemeMode
import com.yuan3271.cloudrift.engine.Candidate
import com.yuan3271.cloudrift.engine.CandidateKind
import com.yuan3271.cloudrift.engine.EngineKind
import com.yuan3271.cloudrift.engine.InputEngine
import com.yuan3271.cloudrift.engine.emoji.EmojiGroup
import com.yuan3271.cloudrift.input.EditorProxy
import com.yuan3271.cloudrift.input.CompatPanelKind
import com.yuan3271.cloudrift.input.ExternalInputSnapshot
import com.yuan3271.cloudrift.input.KeyCode
import com.yuan3271.cloudrift.input.KeyDef
import com.yuan3271.cloudrift.input.KeyboardLayouts
import com.yuan3271.cloudrift.input.KeyboardPage
import com.yuan3271.cloudrift.input.KeyboardHaptics
import com.yuan3271.cloudrift.input.InputWindowHost
import com.yuan3271.cloudrift.input.LayoutId
import com.yuan3271.cloudrift.ui.settings.SettingsActivity
import com.yuan3271.cloudrift.voice.VoiceInputController
import com.yuan3271.cloudrift.voice.VoiceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The keyboard's brain: it owns the reading buffer, asks the engine for candidates, and is
 * the only component that writes to the editor.
 *
 * Deliberately free of Compose so the same logic can be driven from unit tests or from a
 * headless harness.
 */
class ImeController(
    private val service: InputMethodService,
    private val settings: SettingsRepository = AppGraph.settings,
    private val voice: VoiceInputController = VoiceInputController(
        context = service.applicationContext,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    ),
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 缩放时没花掉的零头（见 [resizeFloatingKeyboard]），只活在这一次拖动里。 */
    private var floatingWidthResidual = 0f
    private var floatingHeightResidual = 0f
    /** The service, when it can frame the window; null in tests and headless use. */
    private val windowHost: InputWindowHost? = service as? InputWindowHost
    private val editor = EditorProxy { service.currentInputConnection }
    private val clipboard = ClipboardStore(service, scope)
    private val haptics = KeyboardHaptics(service)

    private val _state = MutableStateFlow(ImeUiState())
    val state: StateFlow<ImeUiState> = _state.asStateFlow()

    /**
     * 表情表的分类（Unicode 标准分组）。读不到就是空表，符号页少一页而已——所以这不是
     * [ImeUiState] 的一部分：它不随打字变化，放进 state 只会让每次按键都多拷一份 1900 个字符。
     */
    val emojiGroups: List<EmojiGroup> get() = AppGraph.emoji.groups

    private var editorInfo: EditorInfo? = null
    private var engine: InputEngine = AppGraph.engines.engineFor(LayoutId.Pinyin26.engine)
    private var selfEditCounter = 0
    /**
     * 用户在组合区里把光标挪到哪里（相对原始读音的字符偏移）。null 表示正常在末尾继续输入。
     */
    private var compositionCaret: Int? = null
    private var lastSpaceAt = 0L
    private var doubleSpaceArmed = false
    /** Timer that applies a finished transcript on its own; see voiceAutoApplyDelayMs. */
    private var autoApplyJob: Job? = null
    /** True while the editor reports a selection rather than a plain caret. */
    private var hasSelection: Boolean = false

    /**
     * 有没有一个输入框正在被编辑（[onStartInput] / [onFinishInput]）。
     *
     * 物理键盘只在**确实在输入框里打字**的时候才归输入法管：输入法窗口有时会留在屏幕上（比如
     * 应用自己没把焦点收走），那时候敲字母、按方向键都是用户在对那个应用做事，不该被输入法接过去
     * 拼成候选（用户点名"没有进行输入框输入的时候，按下键盘输入法不要被触发"）。
     */
    private var editingField = false

    /**
     * 外接键鼠把这次会话的布局从 9 键临时换成 26 键了吗（见 [onExternalInputs]）。只活在内存里：
     * 用户自己选的布局还在设置里躺着，拔掉键鼠就还回去。
     */
    private var layoutSwitchedForHardware = false

    /**
     * 中 ⇄ 英 来回切时，英文那边要回到哪个中文布局（见 [toggleLanguage]）。跟着用户自己选的布局
     * 走：9 键用户被快捷键切到英文，切回来还得是 9 键。
     */
    private var languageReturn: LayoutId = LayoutId.Pinyin26

    /** Shift / Caps Lock 的按下时刻，以及"这次 Shift 期间有没有按过别的键"（见 [onHardwareKeyUp]）。 */
    private var shiftDownAt = 0L
    private var shiftDirty = false
    private var capsDownAt = 0L
    /** Caps Lock 的长按已经处理过了：抬起时不再把它当短按。 */
    private var capsLongFired = false

    /**
     * 屏幕上那条联想候选是替哪个词做的预测（见 [showAssociations]）。null 表示当前没有联想条。
     * 光标本该是唯一依据——它是"这个词仍然贴在光标左边"的当场证据。
     */
    private var associationSeed: String? = null

    /**
     * The run of single characters that went in back to back, used to spot a word the user is
     * spelling out one character at a time. See [CharacterChain] for why the whole run is kept and
     * not just the last pair.
     */
    private val characterChain = CharacterChain(AUTO_WORD_WINDOW_MS, AUTO_WORD_MAX_CHARS)

    init {
        scope.launch {
            AppGraph.engines.dictionaryReady.collect { ready ->
                _state.value = _state.value.copy(dictionaryReady = ready)
            }
        }
        scope.launch {
            voice.state.collect { voiceState ->
                // A pending automatic apply only makes sense while the result is still on screen.
                autoApplyJob?.cancel()
                autoApplyJob = null
                _state.value = _state.value.copy(
                    voice = voiceState,
                    autoApplyPending = _state.value.autoApplyPending &&
                        voiceState is VoiceState.Ready,
                    // A hold-to-talk session ends the moment the pipeline leaves Recording,
                    // whatever stopped it: the finger lifting, the two minute cap, or an error.
                    holdToTalk = _state.value.holdToTalk && voiceState is VoiceState.Recording,
                    notice = (voiceState as? VoiceState.Failed)?.message,
                    noticeNeedsMicrophonePermission =
                        (voiceState as? VoiceState.Failed)?.needsMicrophonePermission == true,
                )
                if (voiceState is VoiceState.Ready) {
                    // The pause is deliberate: the corrected text is visible for a moment, and
                    // anything the user does (上屏 / 取消 / 重说) cancels the timer.
                    val delayMs = settings.current.voiceAutoApplyDelayMs
                    if (delayMs > 0) {
                        _state.value = _state.value.copy(autoApplyPending = true)
                        autoApplyJob = scope.launch {
                            delay(delayMs.toLong())
                            commitVoiceResult()
                        }
                    }
                }
            }
        }
        scope.launch {
            settings.state.collect { snapshot ->
                _state.value = _state.value.withAppearance(snapshot)
            }
        }
        scope.launch {
            clipboard.entries.collect { entries ->
                _state.value = _state.value.copy(clipboardEntries = entries)
            }
        }
        scope.launch {
            clipboard.pending.collect { offer ->
                _state.value = _state.value.copy(clipboardOffer = offer)
            }
        }
        scope.launch {
            AppGraph.profile.stats.collect { stats ->
                _state.value = _state.value.copy(userStats = stats)
            }
        }
        scope.launch {
            AppGraph.updates.available.collect { update ->
                _state.value = _state.value.copy(update = update)
            }
        }
        // One check per interval, in the background; the result is also cached across restarts.
        AppGraph.updates.checkIfDue()
        clipboard.start()
        AppGraph.engines.warmUp()
        restoreLayout()
        refreshSettings()
    }

    // ---- lifecycle ------------------------------------------------------------------

    fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        editingField = true
        editorInfo = info
        // 生命周期回调偶尔会跳过 onFinishInputView；这时也把原始读音上屏，绝不替用户采用首选词。
        commitCompositionVerbatim()
        // 新的一次输入会话：上一条联想说的是**上一个**输入框里的事。
        clearAssociations()
        updateEnterLabel()
    }

    /**
     * 输入框不再被编辑了（失焦 / 输入法解绑）。物理键盘从这一刻起不归输入法管，除非用户又点进
     * 某个输入框（见 [onStartInput]）。
     */
    fun onFinishInput() {
        editingField = false
        commitCompositionVerbatim()
    }

    fun onStartInputView() {
        editor.clearComposing()
        refreshSettings()
        updateEnterLabel()
        // A fresh input session always starts on the keys, never inside the settings sheet - and
        // never on the symbol or 123 page either. Those pages are "for this number, right now";
        // leaving the keyboard on 123 and finding it still on 123 the next time an input box is
        // tapped is how a keyboard ends up unable to type letters at all.
        _state.value = _state.value.copy(
            quickSettingsVisible = false,
            clipboardVisible = false,
            page = KeyboardPage.Letters,
            // 每次开键盘都换一个数：更新提示的动画跟着它重播一次（见 KeyboardToolbar.UpdateMark）。
            keyboardShows = _state.value.keyboardShows + 1,
        )
        clearAssociations()
        if (!AppGraph.engines.dictionaryReady.value) AppGraph.engines.warmUp()
        // 每一次进入输入框都要把两块面板重新立起来：输入法窗口在会话结束时被拆掉过（见服务的
        // onFinishInputView），而"工具栏只要输入激活就该在"这件事就靠这一步；顺带把光标订阅
        // 重新挂到新的 InputConnection 上（见 CloudriftImeService.setCaretFollowing）。
        syncCompatWindows()
        // 键盘每次弹出都问一次「该不该检测更新」，判断完全交给 UpdateInterval.isDue：每天 /
        // 每周 / 每月这些档位在这里是空转（间隔没到就返回），选了「每次打开键盘」才是真的一次
        // 一次地问，而且同一时刻只保留一个在途请求。
        AppGraph.updates.checkIfDue()
    }

    fun onFinishInputView() {
        commitCompositionVerbatim()
        if (_state.value.isVoiceActive) voice.cancel()
    }

    /**
     * The keyboard window is gone. Anything still recording has nowhere to show its result, so
     * the microphone is handed back to the system here rather than at the two minute cap.
     */
    fun onWindowHidden() {
        commitCompositionVerbatim()
        voice.releaseMicrophone()
    }

    /**
     * Called by the service when the editor's selection moves. A move that we did not cause
     * means the user tapped somewhere else, so the pending reading is committed.
     */
    fun onSelectionChanged() {
        onSelectionChanged(
            selectionStart = -1,
            selectionEnd = -1,
            composingStart = -1,
            composingEnd = -1,
        )
    }

    /**
     * @param selectionStart selectionEnd as the editor reports them; equal means a plain caret.
     * @param composingStart candidatesStart from Android's selection callback. A caret that stays
     *   inside this range is the user editing the raw reading, not moving away from it.
     */
    fun onSelectionChanged(
        selectionStart: Int,
        selectionEnd: Int,
        composingStart: Int = -1,
        composingEnd: Int = -1,
    ) {
        if (selectionStart >= 0 && selectionEnd >= 0) {
            hasSelection = selectionStart != selectionEnd
        }
        val current = _state.value
        val caret = compositionCaretOffset(
            selectionStart = selectionStart,
            selectionEnd = selectionEnd,
            composingStart = composingStart,
            composingEnd = composingEnd,
            previewLength = current.preview.length,
        )
        if (caret != null && current.isComposing) {
            // A callback caused by our own setComposingText may still be in flight. Consume that
            // bookkeeping here, but treat an actual arrow-key move the same way: update the edit
            // point and leave the candidate buffer untouched.
            if (selfEditCounter > 0) selfEditCounter--
            compositionCaret = caret
            return
        }
        if (selfEditCounter > 0) {
            selfEditCounter--
            return
        }
        // The caret moved without us: the user is editing somewhere else, so the character chain
        // that could become a learned word is broken.
        characterChain.clear()
        // Losing the composing caret must not silently choose the first candidate. What the user
        // actually typed stays visible until they explicitly pick a conversion.
        commitCompositionVerbatim()
        dropStaleAssociations()
    }

    /**
     * 光标被挪走之后，联想条必须跟着走。
     *
     * 联想候选回答的是"刚上屏那个词之后接什么"。光标一旦落到别的词后面，那条预测就与眼前
     * 的位置无关了，挂着它等于凭空冒出一串跟当前位置不相干的词。判断不用猜"这个选择回调是我
     * 自己那次提交的重复回调吗"——直接问编辑器：光标左边还是不是那个词。重复回调看到的文本
     * 不变，因此联想条留得住；用户点到别处时文本变了，联想条自己退场。
     */
    private fun dropStaleAssociations() {
        val seed = associationSeed ?: return
        if (_state.value.raw.isNotEmpty()) return
        if (editor.textBeforeCaret(seed.length) == seed) return
        clearAssociations()
    }

    fun dispose() {
        AppGraph.profile.flush()
        scope.cancel()
        voice.cancel()
        clipboard.stop()
    }

    /**
     * One commit's worth of learning. The keyboard only ever observes what the user picked - it
     * never asks a model or the network - and the switch in the settings screen turns it off.
     */
    private fun learn(code: String, candidate: Candidate) {
        if (!settings.current.learningEnabled) return
        if (code.isEmpty()) return
        // Raw 是"原样上屏的缓冲串"。中文模式下那是一串拼音（`nihao` 不是词），记下来只会污染
        // 记录；英文布局里缓冲串本身就是用户打的英文词（`cloudrift`），正是要学的东西。
        if (candidate.kind == CandidateKind.Raw && engine.kind != EngineKind.Latin) return

        AppGraph.profile.recordChoice(code, candidate.text, candidate.annotation)

        // Single characters committed back to back are a word of their own: 张, 伟, 来 teaches
        // 张伟来 (reading "zhangweilai") even though no dictionary ships it.
        val now = System.currentTimeMillis()
        val isCharacter = candidate.kind == CandidateKind.Character &&
            candidate.annotation.isNotEmpty() &&
            candidate.text.length == 1
        if (isCharacter) {
            // Every step of the run is learned, not just its end: 张伟 and then 张伟来 are both
            // remembered, so the shorter reading stays typeable too. Past the learned-word length
            // there is nothing more to remember, and the run is dropped rather than kept growing.
            characterChain.append(candidate.annotation, candidate.text, now)?.let { learned ->
                AppGraph.profile.rememberWord(reading = learned.reading, word = learned.word)
            }
        } else {
            characterChain.clear()
        }
    }

    /** Clears the learned habits and invented words; the switch stays where the user left it. */
    fun clearLearning() = AppGraph.profile.clear()

    // ---- key handling ---------------------------------------------------------------

    fun onKey(key: KeyDef) {
        if (key.code != KeyCode.Settings && key.code != KeyCode.HideKeyboard) feedback()
        // 用户开始编辑了：那条"要不要粘贴"的提示就此收掉，别再回到候选栏。剪贴板提示是复制
        // 之后的一次性邀请，不是常驻控件（见 ClipboardStore：一段内容只提示一次）。
        if (key.code in EDITING_KEYS) clipboard.acknowledgePending()
        when (key.code) {
            KeyCode.Char -> appendReading(key.output)
            KeyCode.Text -> commitLiteral(key.output)
            KeyCode.Backspace -> backspace()
            KeyCode.Enter -> enter()
            KeyCode.Space -> space()
            KeyCode.Shift -> toggleShift()
            KeyCode.Language -> cycleLanguage()
            KeyCode.Symbols -> showPage(KeyboardPage.Symbols)
            KeyCode.Numbers -> showPage(KeyboardPage.Numbers)
            KeyCode.Letters -> showPage(KeyboardPage.Letters)
            KeyCode.Voice -> toggleVoice()
            KeyCode.Settings -> toggleQuickSettings()
            KeyCode.HideKeyboard -> service.requestHideSelf(0)
            KeyCode.CandidateNext -> selectCandidate(0)
            KeyCode.None -> Unit
        }
    }

    /** Long press on a key with alternates either picks the next alternate or opens a menu;
     *  the keyboard posts the chosen text through this entry point. */
    fun onAlternateChosen(alternate: String) {
        appendReading(alternate)
    }

    /** Swipe up on a key types the symbol painted above it, bypassing composition. */
    fun onSwipeUp(key: KeyDef) {
        if (!settings.current.swipeUpSymbols || key.swipeUp.isEmpty()) return
        commitLiteral(key.swipeUp)
    }

    /** Horizontal drag on the space bar moves the caret. */
    fun onSpaceCursorDrag(steps: Int) {
        if (!settings.current.spaceCursorControl) return
        selfEditCounter++
        editor.moveCursor(steps)
    }

    /**
     * Long pressing space starts dictation, exactly like the mic key - except that this one is
     * a hold: [onSpaceRelease] ends it. The keyboard keeps its keys on screen for the whole
     * hold (see [holdToTalk]), because the space bar has to stay under the finger.
     */
    fun onSpaceLongPress() {
        if (_state.value.holdToTalk) return
        toggleVoice()
        if (_state.value.voice is VoiceState.Recording) {
            _state.value = _state.value.copy(holdToTalk = true)
        }
    }

    /**
     * The finger left the space bar. Normally that ends the recording and sends it to the speech
     * API; when the finger was slid up past the cancel line the audio is thrown away instead, so a
     * mis-tap (or a change of mind) costs nothing and never reaches a server.
     */
    fun onSpaceRelease(cancelled: Boolean = false) {
        if (!_state.value.holdToTalk) return
        _state.value = _state.value.copy(holdToTalk = false)
        if (_state.value.voice !is VoiceState.Recording) return
        if (cancelled) voice.cancel() else voice.stopAndProcess(settings.current)
    }

    /** Live feedback for the slide-up gesture while the space bar is still held. */
    fun onSpaceCancelChanged(armed: Boolean) {
        if (!_state.value.holdToTalk) return
        voice.setCancelArmed(armed)
    }

    /**
     * 物理键盘上的一颗键。返回 true 表示输入法把它消化了——应用不会再收到这颗键。
     *
     * 只在真的有外接键鼠时才接管（和面板同为 [ExternalInputSnapshot.present] 这一个条件）：没有
     * 外接设备时这条路一个键都不碰，行为与从前逐字节相同。这颗键"是什么意思"由
     * [hardwareKeyAction] 判定（纯函数，可单测），这里只决定按下之后发生什么。
     *
     * 另外还要**有一个输入框正在被编辑**（[editingField]）：没在输入框里打字的时候，敲键盘的是
     * 用户和那个应用之间的事，输入法不该把字母接过去拼成候选、更不该把候选面板叫出来。
     */
    fun onHardwareKeyDown(
        keyCode: Int,
        unicode: Int,
        ctrlPressed: Boolean,
        altPressed: Boolean,
        metaPressed: Boolean = false,
        repeatCount: Int = 0,
        shiftPressed: Boolean = false,
    ): Boolean {
        if (!editingField || !_state.value.externalInputs.present) return false
        // 方向键在组合区里是改拼音，不是离开这次拼写。显式接管左右键，不依赖编辑器是否把
        // candidatesStart/End 完整回传；Shift+方向仍交给应用做选字。
        if (!ctrlPressed && !altPressed && !metaPressed && !shiftPressed &&
            keyCode in COMPOSITION_ARROW_KEYS && moveCompositionCaret(keyCode)
        ) {
            return true
        }
        // Shift 期间只要按过别的键，这一次 Shift 就是当修饰键用的（Shift+字母打大写、Shift+方向键
        // 选字），抬起时不该再切中英。
        //
        // Shift 自己按下时**要把标记清掉**：这个标志是"这一轮 Shift 里有没有按过别的键"，不是"最近
        // 有没有打过字"。不清的话上一次打字的 true 会留到下一次单击 Shift，把切中英整个吃掉——真机
        // 上就是这么表现的：先打几个字，再按 Shift 没反应。
        if (modifierKey(keyCode) == ModifierKey.Shift) shiftDirty = false else shiftDirty = true
        val action = hardwareKeyAction(
            keyCode = keyCode,
            unicode = unicode,
            ctrlPressed = ctrlPressed,
            altPressed = altPressed,
            metaPressed = metaPressed,
            down = true,
            flavor = settings.current.externalKeyFlavor,
        )
        if (action is HardwareKeyAction.Modifier || action is HardwareKeyAction.Edit) {
            logHardwareKey(
                direction = "down",
                keyCode = keyCode,
                detail = "$action repeat=$repeatCount meta=$metaPressed ctrl=$ctrlPressed alt=$altPressed",
            )
        }
        // ⌘ / Win 按着的时候**每一颗键都记一行**：用户键盘上那颗"桌面键"到底发什么 keycode、
        // 有没有 meta 位，全靠这行（它不经过上面那条路——没有对应编辑器动作时 action 是 null）。
        if (metaPressed) logHardwareKey(direction = "meta", keyCode = keyCode, detail = "action=$action")
        return when (action) {
            null -> false
            is HardwareKeyAction.Type -> {
                // 正在拼写时，数字 1-9 是"选第 N 个候选"：键鼠面板里每颗候选前面就写着这个号
                // （见 CompatPanel 的 numbered 那条），所以它不是一个看不见的快捷键。没在拼写时
                // 数字还是数字——打"2026"不该被吃掉。
                if (!selectCandidateByDigit(action.text)) typeFromHardware(action.text)
                true
            }

            HardwareKeyAction.Backspace -> {
                backspace()
                true
            }

            HardwareKeyAction.Enter -> {
                enter()
                true
            }

            HardwareKeyAction.Space -> {
                hardwareSpace()
                true
            }

            // 没在拼写的时候 Esc 不是输入法的键（应用可能拿它关弹窗），原样放行。
            HardwareKeyAction.Cancel -> if (_state.value.isComposing) {
                clearBuffer()
                true
            } else {
                false
            }

            is HardwareKeyAction.Modifier -> when (action.key) {
                // Shift 自己不吃掉：按住 Shift 打字母要由系统换算成大写（见 [hardwareKeyAction]），
                // 输入法只在"单独按了一下就抬起"时才动手（见 [onHardwareKeyUp]）。
                ModifierKey.Shift -> {
                    // 长按 Shift 会被系统送成"重复按下"，那只说明键一直按着，不该重新开始计时。
                    if (repeatCount == 0) shiftDownAt = System.currentTimeMillis()
                    false
                }

                // Caps Lock 由输入法整个接管：短按切中英、长按锁大小写（见 [onHardwareKeyUp]）。
                // 吃掉它，系统就不会再自己把大写状态切一遍。
                ModifierKey.CapsLock -> {
                    if (repeatCount == 0) {
                        capsDownAt = System.currentTimeMillis()
                        capsLongFired = false
                    } else if (!capsLongFired &&
                        System.currentTimeMillis() - capsDownAt >= CAPS_LOCK_LONG_PRESS_MS
                    ) {
                        // 按住不放时系统会补发重复按下。**只有真的超过阈值才算长按**：这台平板上
                        // 系统的长按重复来得比 400ms 还早，直接拿它当长按会把"慢一点的单击"也锁成
                        // 大写。够时长就当长按，当场给反馈，不必等抬起。
                        capsLongFired = true
                        toggleCapsLock()
                    }
                    true
                }
            }

            is HardwareKeyAction.Edit -> performEdit(action.action)
        }
    }

    /**
     * 修饰键与 ⌘ 组合的按下 / 抬起打一行日志。
     *
     * 只在这几种键上打：字母、数字、退格那些不经过这里（它们每秒都可能来好几颗），而"Shift 单击
     * 切中英""Caps Lock 短按 / 长按"这两条偏偏是**看不见摸不着**的——真机上要确认那颗键到底有没有
     * 送到输入法、送的是哪个 keycode，这行日志是唯一线索（`adb logcat -s CloudriftIme`）。
     */
    private fun logHardwareKey(direction: String, keyCode: Int, detail: String = "") {
        Log.i(LOG_TAG, "hw-$direction keyCode=$keyCode $detail")
    }

    /**
     * 物理键盘的抬起。只有 Shift 与 Caps Lock 需要它——"短按一次"和"按住不放"靠按下与抬起这一对
     * 事件才分得出来。返回 true 表示输入法把这颗键消化了。
     */
    fun onHardwareKeyUp(keyCode: Int): Boolean {
        val mine = editingField && _state.value.externalInputs.present
        if (modifierKey(keyCode) != null) {
            logHardwareKey(direction = "up", keyCode = keyCode, detail = "mine=$mine")
        }
        if (!mine) return false
        return when (modifierKey(keyCode)) {
            ModifierKey.Shift -> {
                val tapped = !shiftDirty && System.currentTimeMillis() - shiftDownAt <= SHIFT_TAP_MAX_MS
                shiftDirty = false
                // Mac 档切中英的是**大小写键**，Shift 在那边是纯粹的修饰键（Shift+字母打大写），
                // 单击不该切语言；Windows 档反过来——切中英的是 Shift，大小写键只管大小写。
                if (tapped && settings.current.externalKeyFlavor == ExternalKeyFlavor.Windows) {
                    toggleLanguage()
                    true
                } else {
                    false
                }
            }

            ModifierKey.CapsLock -> {
                // 重复按下那条路已经处理过（长按）就不重复处理；没有重复事件（有些键盘不给）时按住
                // 了多久也算数，两条路都留。
                if (!capsLongFired) {
                    val mac = settings.current.externalKeyFlavor == ExternalKeyFlavor.Mac
                    val held = System.currentTimeMillis() - capsDownAt
                    // Mac：短按切中英、长按锁大小写。Windows：大小写键就是大小写锁，不切中英。
                    if (mac && held < CAPS_LOCK_LONG_PRESS_MS) toggleLanguage() else toggleCapsLock()
                }
                capsLongFired = false
                true
            }

            null -> false
        }
    }

    /**
     * Mac 档下 ⌘C / ⌘V / ⌘X / ⌘A 翻成编辑器动作（见 [ExternalKeyFlavor.Mac]）。
     *
     * 输入法不自己去读写剪贴板，只用 `performContextMenuAction` 替用户点长按菜单里那一项；编辑器
     * 不给做就返回 false，把这颗键原样交回系统，不至于被输入法闷掉。
     */
    private fun performEdit(action: EditAction): Boolean {
        // 手里的拼写先定下来，否则复制到的是"还没上屏的拼音"和输入框里那串预览的混合。
        commitBuffer()
        return when (action) {
            EditAction.Copy -> editor.copySelection()
            EditAction.Cut -> editor.cutSelection()
            EditAction.Paste -> editor.pasteFromClipboard()
            EditAction.SelectAll -> editor.selectAllText()
        }
    }

    /**
     * 物理键盘上的空格。
     *
     * 拼写中它就是**选首选词**——和屏上那颗空格同一条规矩，一个多余的字符都不补：选完候选再
     * 自己多出一个空格，用户看到的是一句话里凭空多了一个空格（用户点名）。想要词与词之间的
     * 间隔就再按一下空格，那时缓冲已经空了，走下面 [space] 那条普通空格的路。
     */
    private fun hardwareSpace() {
        if (_state.value.isComposing) {
            // 一个候选都拿不到（引擎给不出转换）时，这颗空格至少要把这串字母原样送上去，
            // 不能白按一下什么都不发生。
            if (_state.value.candidates.isEmpty()) commitBuffer() else selectCandidate(0)
            return
        }
        space()
    }

    /**
     * 物理键盘上的一颗数字键：正在拼写、而且这个位置真有候选，就是选它。
     *
     * @return true 表示这颗键已经当"选候选"用掉了。
     */
    private fun selectCandidateByDigit(text: String): Boolean {
        val digit = text.singleOrNull()?.digitToIntOrNull() ?: return false
        // 0 没有对应的候选位（第 0 个候选是空格 / 首选键的事）。
        if (digit !in 1..9) return false
        val current = _state.value
        if (!current.isComposing) return false
        if (digit > current.candidates.size) return false
        selectCandidate(digit - 1)
        return true
    }

    /**
     * 物理键盘打出来的一个字符。
     *
     * 字母进读音缓冲：拼音、罗马音、英文补全都靠它拼。数字与标点直接上屏——它们不参与拼写，
     * 走缓冲只会让用户多按一次空格（"2026" 变成要按四下空格）。有正在拼的内容时，直接上屏
     * 会先把拼写定下来（[commitLiteral] 自己处理），这和屏上从符号页打字是同一个规矩。
     */
    private fun typeFromHardware(text: String) {
        val current = _state.value
        val letter = text.length == 1 && text[0].isAsciiLetter()
        if (!letter) {
            commitLiteral(text)
            return
        }
        // Caps Lock 被输入法接管了，系统那份大写状态不会跟着走，所以这里自己把字母变大写。
        // Shift 那条路不用管：按住 Shift 打出来的字符本来就由系统换算好了（见 [hardwareKeyAction]）。
        val cased = if (current.capsLock && text[0].isLowerCase()) text.uppercase() else text
        // 符号页上敲字母 = 回到字母页继续拼（屏上做不到这件事，物理键盘做得到）。
        if (current.page != KeyboardPage.Letters) showPage(KeyboardPage.Letters)
        appendReading(cased)
    }

    // ---- 外接键鼠 ---------------------------------------------------------------------

    /**
     * 设备表或系统配置变了（插入/拔出键盘鼠标、切到桌面模式）。
     *
     * 除了"9 键例外"以外什么都不改：键鼠兼容面板与虚拟键盘读的是同一份状态、同一套引擎，所以
     * 插拔键鼠不会打断正在打的东西，也不会丢掉候选。
     *
     * 9 键例外：九宫格是给手指的布局，物理键盘打出来的是字母，喂给 T9 引擎一个字都出不来
     * （用户看到的是"打字没反应"）。所以面板生效时把**这次会话**的布局换成 26 键，但不写设置——
     * 拔掉键鼠时 [restoreLayout] 会把用户原来选的 9 键还回来。
     */
    fun onExternalInputs(inputs: ExternalInputSnapshot) {
        if (_state.value.externalInputs != inputs) {
            _state.value = _state.value.copy(externalInputs = inputs)
        }
        val panel = _state.value.showsCompatPanel
        when {
            panel && !layoutSwitchedForHardware && _state.value.layout == LayoutId.Pinyin9 -> {
                layoutSwitchedForHardware = true
                applyLayout(LayoutId.Pinyin26, persist = false)
            }

            !panel && layoutSwitchedForHardware -> {
                layoutSwitchedForHardware = false
                restoreLayout()
            }
        }
    }

    /** Quick settings edits the same store the full settings screen writes to. */
    fun updateSettings(transform: (AppSettings) -> AppSettings) = settings.update(transform)

    /**
     * 工具面板开不开。设置页里是一个开关，面板右端那颗 ✕ 关的也是它——同一个设置，两条路。
     */
    fun setCompatToolbarEnabled(enabled: Boolean) {
        settings.update { it.copy(compatToolbarEnabled = enabled) }
        refreshSettings()
        syncCompatWindows()
    }

    /** Inserts text from a panel (clipboard, snippets) without touching the reading buffer. */
    fun commitText(text: String) {
        if (text.isEmpty()) return
        clipboard.acknowledgePending()
        characterChain.clear()
        if (_state.value.isComposing) commitBuffer()
        selfEditCounter++
        editor.commit(text)
        // Pasted text is not something we can predict from: the 联想 strip described the previous
        // caret position, not this one.
        clearAssociations()
    }

    fun setQuickSettingsVisible(visible: Boolean) {
        if (_state.value.quickSettingsVisible == visible) return
        _state.value = _state.value.copy(quickSettingsVisible = visible)
    }

    /** 全角 / 半角, remembered across sessions like any other keyboard preference. */
    fun setSymbolWidth(width: SymbolWidth) {
        settings.update { it.copy(symbolWidth = width) }
        // 按「全角/半角」的意思就是"看标点表"：从表情页按它要回到标点页，而不是改了设置却停在
        // 表情上什么都不变。
        if (_state.value.symbolSheet != SymbolSheet.Punctuation) {
            _state.value = _state.value.copy(symbolSheet = SymbolSheet.Punctuation)
        }
    }

    /** 符号页左栏的另一半：标点 ⇄ 表情。 */
    fun setSymbolSheet(sheet: SymbolSheet) {
        if (_state.value.symbolSheet == sheet) return
        _state.value = _state.value.copy(symbolSheet = sheet)
    }

    /** 表情页里换一个标准分类（表情 / 人物 / 动物 …）。 */
    fun setEmojiGroup(key: String) {
        if (_state.value.emojiGroup == key) return
        _state.value = _state.value.copy(emojiGroup = key)
    }

    /**
     * 键鼠兼容面板上的「符号」：在这一页与字母页之间来回。
     *
     * 面板上没有 `123` 也没有 `符` 那两个键，符号页就是它的第二条界——再点一次回到字母页，
     * 和屏上那颗 `符` 的进出方式一致。
     */
    fun toggleSymbolPage() {
        showPage(
            if (_state.value.page == KeyboardPage.Symbols) {
                KeyboardPage.Letters
            } else {
                KeyboardPage.Symbols
            },
        )
    }

    /** Landscape framing, decided by the UI (it knows the orientation) and applied by the service. */
    fun applyInputFrame(floating: Boolean, widthPercent: Int) {
        windowHost?.applyInputFrame(floating, widthPercent)
    }

    /**
     * Live resize from the floating keyboard's corner handle. The values live in the UI state while
     * the finger is down - writing settings on every frame would mean a preferences write per
     * frame - and are persisted once by [commitFloatingKeyboardSize].
     */
    fun resizeFloatingKeyboard(widthDeltaPercent: Float, keyHeightDeltaDp: Float) {
        val current = _state.value
        // 缩放"很怪异"的根源在这里：每次指针事件都把 (当前值 + 这一小段增量) 直接 toInt()，
        // 慢拖时每一段都不到 1（百分比或 dp），于是整段被截掉——手在动、卡片不动，攒到某一下
        // 又突然跳一大格。把没花掉的小数留到下一段，缩放才是连续的。
        floatingWidthResidual += widthDeltaPercent
        floatingHeightResidual += keyHeightDeltaDp
        val width = (current.floatingWidthPercent + floatingWidthResidual)
            .toInt()
            .coerceIn(MIN_FLOATING_WIDTH_PERCENT, MAX_FLOATING_WIDTH_PERCENT)
        val height = (current.floatingKeyHeightDp + floatingHeightResidual)
            .toInt()
            .coerceIn(MIN_FLOATING_KEY_HEIGHT, MAX_FLOATING_KEY_HEIGHT)
        floatingWidthResidual -= (width - current.floatingWidthPercent)
        floatingHeightResidual -= (height - current.floatingKeyHeightDp)
        if (width == current.floatingWidthPercent && height == current.floatingKeyHeightDp) return
        _state.value = current.copy(
            floatingWidthPercent = width,
            floatingKeyHeightDp = height,
        )
    }

    fun commitFloatingKeyboardSize() {
        floatingWidthResidual = 0f
        floatingHeightResidual = 0f
        val current = _state.value
        settings.update {
            it.copy(
                floatingWidthPercent = current.floatingWidthPercent,
                floatingKeyHeightDp = current.floatingKeyHeightDp,
            )
        }
    }

    /** Drag delta from the floating keyboard's handle, in pixels. */
    fun moveFloatingKeyboard(dx: Float, dy: Float) {
        windowHost?.moveInputWindowBy(dx, dy)
    }

    // ---- 键鼠兼容面板的两个窗口 ---------------------------------------------------------

    /**
     * 键鼠兼容模式的两块面板各是一个独立窗口：这里同步"该不该出现、在哪儿"。
     *
     * 两块都要能开出来才算数：只开出一块（比如工具面板开得出来、候选词面板开不出来）会变成
     * "候选有、工具栏没有"的半截样子——还不如整块退回输入法窗口里（见 [ImeUiState.compatWindowsReady]）。
     *
     * @return true 表示两块都在自己的窗口里。
     */
    fun syncCompatWindows(): Boolean {
        val host = windowHost ?: return false
        val current = _state.value
        if (!current.showsCompatPanel) {
            host.setCaretFollowing(false)
            host.removeCompatPanels()
            publishCompatWindowState(ready = false, needsPermission = false)
            return false
        }
        // 候选词面板要知道光标在哪；工具面板不跟光标，但它也得先站起来。
        host.setCaretFollowing(true)
        // 工具面板也要它自己的位置（记住的那个），并且和候选词那一块同一个条件出现 / 收起：
        // 只在真的在输入的时候露脸（用户点名），空闲时屏幕上不留任何一条。
        val toolbarReady = host.applyCompatPanel(
            kind = CompatPanelKind.Toolbar,
            visible = current.compatToolbarVisible,
            xPercent = current.compatToolbarXPercent,
            yPercent = current.compatToolbarYPercent,
        )
        val contentReady = toolbarReady && host.applyCompatPanel(
            kind = CompatPanelKind.Content,
            visible = current.compatContentVisible,
            // 候选词面板的位置由光标决定（或前后都没有光标时贴屏幕底部），这里没有百分比可说。
            xPercent = AppSettings.UNSET_POSITION,
            yPercent = AppSettings.UNSET_POSITION,
        )
        val ready = toolbarReady && contentReady
        if (!ready) host.removeCompatPanels()
        // 开不出来时，只有一种原因用户自己能解决：没有「显示在其他应用上层」。
        val needsPermission = !ready && !Settings.canDrawOverlays(service)
        publishCompatWindowState(ready = ready, needsPermission = needsPermission)
        return ready
    }

    private fun publishCompatWindowState(ready: Boolean, needsPermission: Boolean) {
        val current = _state.value
        if (current.compatWindowsReady == ready &&
            current.compatNeedsOverlayPermission == needsPermission
        ) {
            return
        }
        _state.value = current.copy(
            compatWindowsReady = ready,
            compatNeedsOverlayPermission = needsPermission,
        )
    }

    /** Drag delta from one panel's grip. */
    fun moveCompatPanelBy(kind: CompatPanelKind, dx: Float, dy: Float) {
        windowHost?.moveCompatPanelBy(kind, dx, dy)
    }

    /** 松手：把落点记下来（只有工具面板需要记）。 */
    fun commitCompatPanelPosition(kind: CompatPanelKind) {
        windowHost?.commitCompatPanelPosition(kind)
    }

    /** 两块面板要悬浮在别的应用上层，这一条只有用户自己能给。 */
    fun openOverlayPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${service.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        service.startActivity(intent)
    }

    /** 服务把工具面板拖完之后的落点写回来（屏幕百分比）。 */
    fun onCompatToolbarMoved(xPercent: Int, yPercent: Int) {
        val current = _state.value
        if (current.compatToolbarXPercent == xPercent && current.compatToolbarYPercent == yPercent) {
            return
        }
        _state.value = current.copy(
            compatToolbarXPercent = xPercent,
            compatToolbarYPercent = yPercent,
        )
        settings.update { it.copy(compatToolbarXPercent = xPercent, compatToolbarYPercent = yPercent) }
    }

    /** The toolbar's yellow mark opens the settings screen, where the release is described. */
    fun openUpdate() = openSettings()

    /**
     * The clipboard is its own panel rather than a sheet over the keys: history is the point, and
     * a list needs the whole key area to be readable.
     */
    fun toggleClipboard() {
        val next = !_state.value.clipboardVisible
        setQuickSettingsVisible(false)
        // 打开剪贴板历史本身就是"这段内容我已经看见了"：那条一次性的"要不要粘贴"提示就此收掉，
        // 免得切回虚拟键盘时它又冒出来提一段早就贴过的内容（键鼠模式下这条提示不露脸，用户只会
        // 从工具栏的 📋 进来，拿一条走的是 commitText，那边同样会记账）。
        if (next) clipboard.acknowledgePending()
        _state.value = _state.value.copy(
            clipboardVisible = next,
            candidatesExpanded = false,
        )
    }

    fun setClipboardVisible(visible: Boolean) {
        if (_state.value.clipboardVisible == visible) return
        if (visible) setQuickSettingsVisible(false)
        _state.value = _state.value.copy(clipboardVisible = visible)
    }

    /** Inserting a clipboard entry closes the panel, like any other one-shot pick. */
    fun pickClipboardEntry(entry: ClipEntry) {
        commitText(entry.text)
        setClipboardVisible(false)
    }

    fun deleteClipboardEntry(entry: ClipEntry) = clipboard.remove(entry)

    /** Long pressing an entry puts it back on the clipboard instead of throwing it away. */
    fun copyClipboardEntry(entry: ClipEntry) = clipboard.copyToClipboard(entry.text)

    fun clearClipboard() = clipboard.clear()

    /**
     * 候选栏上那条剪贴板提示：点一下就把内容贴上去（和剪贴板面板里点一条是同一个动作），
     * 然后提示收掉——已经用过了。
     */
    fun pasteClipboardOffer() {
        val offer = _state.value.clipboardOffer ?: return
        commitText(offer.text)
        clipboard.acknowledgePending()
    }

    /** 点右边的叉：只收掉提示，那条内容仍然留在剪贴板历史里。 */
    fun dismissClipboardOffer() = clipboard.acknowledgePending()

    /** Tapping the toolbar gear again is the fastest way out of the sheet. */
    fun toggleQuickSettings() {
        setQuickSettingsVisible(!_state.value.quickSettingsVisible)
    }

    fun openFullSettings() {
        // Leaving for the full settings screen also drops any overlay that was on top, so the
        // keyboard is back to its normal state when the user returns.
        setQuickSettingsVisible(false)
        _state.value = _state.value.copy(candidatesExpanded = false)
        openSettings()
    }

    fun onBackspaceLongPress() {
        if (_state.value.isComposing) {
            clearBuffer()
        } else {
            // One character, never a word: a long press is not a licence to guess where the
            // previous one started. Holding the key already repeats single character deletes.
            selfEditCounter++
            editor.deleteSurroundingBefore(1)
        }
    }

    /**
     * The 清空 key: empties the editor in one go. The pending reading is dropped first, so the
     * selection covers the text rather than our own composing region.
     */
    /** Lights up while the finger is held above the backspace key. */
    fun onClearAllArmedChanged(armed: Boolean) {
        if (_state.value.clearAllArmed == armed) return
        _state.value = _state.value.copy(clearAllArmed = armed)
    }

    fun clearAllText() {
        onClearAllArmedChanged(false)
        characterChain.clear()
        clearBuffer()
        selfEditCounter++
        editor.clearAll()
    }

    fun selectCandidate(index: Int) {
        val current = _state.value
        val candidate = current.candidates.getOrNull(index) ?: return
        if (settings.current.hapticFeedback) haptics.candidate()
        clipboard.acknowledgePending()
        // 学的是"这个候选真正答完的那段码"，不是"当时屏幕上还剩多长"。
        //
        // 整句输入里点一个单字（我 只吃掉 `wo`）时，记成整条 `wojintianqulebeijing` 是错的：那条
        // 记录既不能给 `wo` 用，又会在下次打同一句时把 我 顶到整句前面——而它每被点一次就更稳，
        // 于是"长句只能一个字一个字地拿"。按被吃掉的那段码记，单字就只是"wo 这个码的习惯"。
        learn(current.raw.take(candidate.consumed), candidate)
        val remaining = if (candidate.consumed >= current.raw.length) {
            ""
        } else {
            current.raw.drop(candidate.consumed)
        }
        selfEditCounter++
        editor.commit(candidate.text)
        if (remaining.isEmpty()) {
            // 这一条拼写已经全部上屏：缓冲连同候选一起清空，再让候选栏翻成"接着上个词的联想"。
            //
            // 清空**必须在这里做**，不能托付给 showAssociations：键鼠兼容模式下联想条是不显示的
            // （见 showAssociations 的开头），它一早就 return，raw 与候选就留在了状态里。那样
            // 物理键盘的下一次空格 / 数字仍然算"还在拼写"，把同一个词再上屏一次——用户报的
            // "空格打出两个已候选""按数字能重复打出内容"。
            _state.value = _state.value.clearedBuffer()
            clearComposingQuietly()
            showAssociations(candidate.text)
        } else {
            applyBuffer(remaining)
        }
    }

    /**
     * 打完一个词，联想下一个词: with the reading buffer empty, the candidate strip lists the words
     * the association table says tend to follow what was just committed. Tapping one commits it and
     * chains into its own predictions; typing anything replaces the strip with normal candidates.
     *
     * **键鼠兼容模式下不联想**（用户点名）：外接键盘的人手在键盘上，候选栏本来就只是"看一眼要选
     * 哪个"，打完一个词还弹一排下一个词的预测，除了挡屏幕没有别的用处。
     */
    private fun showAssociations(seed: String) {
        if (_state.value.showsCompatPanel) return
        val predictions = engine.associations(seed)
        associationSeed = seed.takeIf { predictions.isNotEmpty() }
        _state.value = _state.value.copy(
            raw = "",
            preview = "",
            candidates = predictions,
            candidatesExpanded = _state.value.candidatesExpanded && predictions.isNotEmpty(),
        )
    }

    /**
     * Drops the 联想 strip. Only ever called with an empty buffer, where the candidate list cannot
     * be a composition - otherwise the words being composed would go with it.
     */
    private fun clearAssociations() {
        associationSeed = null
        if (_state.value.raw.isNotEmpty() || _state.value.candidates.isEmpty()) return
        _state.value = _state.value.copy(candidates = emptyList(), candidatesExpanded = false)
    }

    fun toggleCandidatesExpanded() {
        _state.value = _state.value.copy(candidatesExpanded = !_state.value.candidatesExpanded)
    }

    fun selectLayout(layout: LayoutId) {
        applyLayout(layout, persist = true)
    }

    /**
     * @param persist 用户自己选的布局要记住（写进设置）；外接键鼠下的临时切换不写，拔掉之后
     *   用户原来选的布局还在（见 [onExternalInputs]）。
     */
    private fun applyLayout(layout: LayoutId, persist: Boolean) {
        if (layout == _state.value.layout) return
        commitBuffer()
        _state.value = _state.value.copy(
            layout = layout,
            page = KeyboardPage.Letters,
            shifted = false,
            capsLock = false,
            // Switching layout is a "show me the keyboard" action, so it also leaves the
            // in-keyboard settings sheet.
            quickSettingsVisible = false,
        )
        engine = AppGraph.engines.engineFor(layout.engine)
        voice.layoutHint = layout
        // 切语言在光标右下角闪一下"中 / 英 / 日"：眼睛在光标那儿，面板上那颗标签太远。
        windowHost?.showLanguageBadge(badgeText())
        if (persist) {
            settings.update { it.copy(lastLayout = layout.name) }
            // 用户自己选的中文布局要记住：快捷键从英文切回来时用它，而不是一律回 26 键
            // （外接键鼠那次临时的 9→26 切换走的是 persist = false，不会污染这里）。
            if (layout.isChinese) languageReturn = layout
        }
        updateEnterLabel()
        if (layout == LayoutId.Pinyin9) warmUpNineKey()
    }

    fun cycleThemeMode() {
        val next = when (settings.current.themeMode) {
            ThemeMode.System -> ThemeMode.Light
            ThemeMode.Light -> ThemeMode.Dark
            ThemeMode.Dark -> ThemeMode.System
        }
        settings.update { it.copy(themeMode = next) }
    }

    // ---- voice ----------------------------------------------------------------------

    fun toggleVoice() {
        when (_state.value.voice) {
            is VoiceState.Idle,
            is VoiceState.Failed,
            -> {
                // The voice panel replaces the key area; the settings sheet would hide it.
                setQuickSettingsVisible(false)
                commitBuffer()
                voice.layoutHint = _state.value.layout
                voice.start(settings.current)
            }

            is VoiceState.Recording -> voice.stopAndProcess(settings.current)
            is VoiceState.Transcribing, is VoiceState.Correcting -> Unit
            is VoiceState.Ready -> Unit
        }
    }

    fun cancelVoice() {
        voice.cancel()
    }

    fun dismissVoice() {
        voice.dismiss()
    }

    /** 面板上的「跳过修正」：不等文本修正 API，直接用识别原文上屏。 */
    fun skipVoiceCorrection() {
        voice.skipCorrection()
    }

    fun retryVoice() {
        voice.dismiss()
        voice.start(settings.current)
    }

    fun commitVoiceResult() {
        val ready = _state.value.voice as? VoiceState.Ready ?: return
        autoApplyJob?.cancel()
        autoApplyJob = null
        _state.value = _state.value.copy(autoApplyPending = false)
        characterChain.clear()
        selfEditCounter++
        editor.commit(ready.text)
        voice.dismiss()
        updateEnterLabel()
    }

    /**
     * A tap on the result stops this round's automatic apply and leaves the text on screen until
     * the user says 上屏 - the keyboard should never change the text while someone is reading it.
     */
    fun cancelAutoApply() {
        if (!_state.value.autoApplyPending) return
        autoApplyJob?.cancel()
        autoApplyJob = null
        _state.value = _state.value.copy(autoApplyPending = false)
    }

    fun openMicrophonePermissionSettings() {
        val intent = Intent(service, SettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(SettingsActivity.EXTRA_REQUEST_MICROPHONE, true)
        }
        service.startActivity(intent)
    }

    fun dismissNotice() {
        _state.value = _state.value.copy(notice = null, noticeNeedsMicrophonePermission = false)
    }

    // ---- internals ------------------------------------------------------------------

    private fun appendReading(text: String) {
        if (text.isEmpty()) return
        val current = _state.value
        if (current.page != KeyboardPage.Letters && current.raw.isEmpty()) {
            // Punctuation typed from a symbol page goes straight in.
            commitLiteral(text)
            return
        }
        val shiftedText = if (current.shifted && text.length == 1 && text[0].isLetter()) {
            text.uppercase()
        } else {
            text
        }
        val edited = insertReading(current.raw, compositionCaret, shiftedText)
        applyBuffer(edited.text, edited.caret)
        if (current.shifted && !current.capsLock) {
            _state.value = _state.value.copy(shifted = false)
        }
    }

    /** Moves inside the raw composing preview and keeps the editor caret there. */
    private fun moveCompositionCaret(keyCode: Int): Boolean {
        val current = _state.value
        if (!current.isComposing || current.preview.length != current.raw.length) return false
        val previous = compositionCaret ?: current.raw.length
        val target = movedReadingCaret(
            raw = current.raw,
            caret = compositionCaret,
            delta = if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1,
        )
        // At either end let the host move out of the composition.
        if (target == previous) return false
        compositionCaret = target
        selfEditCounter++
        editor.setComposing(current.preview, target)
        return true
    }

    /**
     * Key feedback. The system wide setting is deliberately ignored so the keyboard's own
     * switch is the single source of truth.
     */
    private fun feedback() {
        val snapshot = settings.current
        if (snapshot.hapticFeedback) haptics.key()
        val decor = service.window?.window?.decorView ?: return
        if (snapshot.soundFeedback) {
            decor.playSoundEffect(SoundEffectConstants.CLICK)
        }
    }

    private fun applyBuffer(raw: String, caret: Int? = null) {
        val output = engine.evaluate(raw, engine.candidateLimit)
        val preview = output.composingPreview.ifEmpty { raw }
        val rawCaret = caret?.coerceIn(0, raw.length) ?: raw.length
        _state.value = _state.value.copy(
            raw = raw,
            preview = preview,
            candidates = output.candidates,
            candidatesExpanded = _state.value.candidatesExpanded && output.candidates.isNotEmpty(),
        )
        if (raw.isEmpty()) {
            compositionCaret = null
            clearComposingQuietly()
        } else {
            // The engine may translate as you type (kana / romaji). In that case the editor caret
            // is in preview characters, not raw input characters; keep the edit point at the end.
            val previewCaret = if (preview.length == raw.length) rawCaret else preview.length
            compositionCaret = if (preview.length == raw.length) rawCaret else null
            selfEditCounter++
            editor.setComposing(preview, previewCaret)
        }
    }

    private fun clearBuffer() {
        compositionCaret = null
        _state.value = _state.value.clearedBuffer()
        clearComposingQuietly()
    }

    /**
     * Drops the composing region without letting the editor's selection callback look like a user
     * edit - the callback would otherwise break the character chain that can become a learned word.
     */
    private fun clearComposingQuietly() {
        // 没有正在显示的上屏区就什么都不做：那一下 selfEditCounter++ 永远等不到选择回调来配平，
        // 会攒下来把用户**真正**的那次点击吃掉（联想条于是留在屏幕上不走了）。
        if (!editor.isComposing) return
        selfEditCounter++
        editor.clearComposing()
    }

    /** Commits whatever is in the reading buffer without accepting a candidate. */
    private fun commitBuffer() {
        characterChain.clear()
        val current = _state.value
        if (current.raw.isEmpty()) {
            editor.clearComposing()
            return
        }
        val text = current.primaryCandidate?.takeIf { it.kind != CandidateKind.Raw }?.text
            ?: engine.literal(current.raw)
        selfEditCounter++
        editor.commit(text)
        compositionCaret = null
        _state.value = current.clearedBuffer()
    }

    /**
     * Ends the current composition exactly as the user typed it. Focus loss is not an instruction
     * to accept the top candidate: `nihao` must remain `nihao`, just as every conventional IME
     * preserves a half-finished composition when the keyboard leaves.
     */
    private fun commitCompositionVerbatim() {
        if (_state.value.isComposing) commitRawLiteral()
    }

    private fun commitLiteral(text: String) {
        if (text.isEmpty()) return
        characterChain.clear()
        if (_state.value.isComposing) commitBuffer()
        selfEditCounter++
        // 只有**成对键**（符号页最前面那些「（）」「“”」）才一次出两个并把光标放中间；
        // 单个的左括号、右括号各按各的——按 `（` 想要的是 `（`，不是 `（）`。
        if (text.length == 2 && text in PAIR_KEYS) {
            editor.insertPair(text[0].toString(), text[1].toString())
        } else {
            editor.commit(text)
        }
        updateEnterLabel()
    }

    /**
     * Always exactly one character. Backspace used to take the whole just-committed candidate back
     * within a few seconds of it going in, which reads as "it ate a word I did not ask it to".
     * Deleting what is on screen one character at a time is the only thing a backspace needs to do.
     */
    private fun backspace() {
        characterChain.clear()
        // A selected range is the user's target, not the text next to the caret, and
        // deleteSurroundingText is ignored by most editors while a selection is live - pressing the
        // key is what makes them delete the selection.
        //
        // 判断以编辑器**当场**的选区为准（见 EditorProxy.hasLiveSelection）：缓存的那个标志会
        // 在拖选、编辑器自己改选区之后过期，所以同一个动作时而删得掉、时而删不掉。两条路都走：
        // 先把选区替换成空串，若编辑器不吃这一套，再补一次真正的退格键按下。
        if (hasSelection || editor.hasLiveSelection()) {
            selfEditCounter++
            editor.deleteSelection()
            if (editor.hasLiveSelection()) editor.sendBackspaceKey()
            doubleSpaceArmed = false
            return
        }
        val current = _state.value
        if (current.raw.isNotEmpty()) {
            val edited = backspaceReading(current.raw, compositionCaret)
            if (edited.text.isEmpty()) {
                clearBuffer()
            } else {
                applyBuffer(edited.text, edited.caret)
            }
            return
        }
        // Deleting into text that is already in the editor: the 联想 strip was about what used to
        // be in front of the caret, so it goes away rather than predicting from stale text.
        clearAssociations()
        selfEditCounter++
        editor.deleteSurroundingBefore(1)
        doubleSpaceArmed = false
    }

    private fun space() {
        val current = _state.value
        if (current.raw.isNotEmpty()) {
            selectCandidate(0)
            return
        }
        val now = System.currentTimeMillis()
        val isDoubleSpace = doubleSpaceArmed && now - lastSpaceAt <= DOUBLE_SPACE_WINDOW_MS
        lastSpaceAt = now
        doubleSpaceArmed = !isDoubleSpace
        // A space is not another character of the word being spelled out.
        characterChain.clear()
        if (isDoubleSpace && current.layout == LayoutId.English) {
            selfEditCounter++
            // Replace the previous space with a period and a space.
            editor.commit(". ")
        } else {
            selfEditCounter++
            editor.commit(" ")
        }
    }

    private fun enter() {
        // In a composition language Enter means "commit exactly what I typed", not "accept the
        // candidate" - that is what Space is for. So "nihao" + Enter inserts "nihao".
        characterChain.clear()
        if (_state.value.isComposing) {
            commitRawLiteral()
            return
        }
        val action = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        if (action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED) {
            // A plain "换行" field: insert the newline ourselves. performEditorAction does nothing at
            // all in most editors when the user has a selection, and committing replaces the
            // selection, which is what pressing enter on selected text should do.
            selfEditCounter++
            editor.newline()
            updateEnterLabel()
            return
        }
        selfEditCounter++
        editor.performEditorAction(editorInfo)
    }

    /** Commits the reading buffer verbatim, without converting it. */
    private fun commitRawLiteral() {
        characterChain.clear()
        val current = _state.value
        val literal = engine.literal(current.raw)
        // 回车在英文布局里是"这个词我打完了"：上屏的那串就是词本身，照记不误（空格走
        // selectCandidate，那条路已经会记）。中文模式下这里上屏的是拼音串，learn 会自己挡掉。
        learn(current.raw, Candidate(text = literal, consumed = current.raw.length, kind = CandidateKind.Raw))
        selfEditCounter++
        editor.commit(literal)
        compositionCaret = null
        _state.value = current.clearedBuffer()
    }

    private fun toggleShift() {
        val current = _state.value
        when {
            !current.shifted -> _state.value = current.copy(shifted = true, capsLock = false)
            current.shifted && !current.capsLock -> _state.value = current.copy(capsLock = true)
            else -> _state.value = current.copy(shifted = false, capsLock = false)
        }
    }

    /**
     * 中 ⇄ 英 一步切换：外接键盘上单独按一下 Shift，或短按一下 Caps Lock。
     *
     * 和工具栏那颗语言键不一样：那颗是在 中 → 英 → 日 里转圈，日文开着的时候要按两下才回得来。
     * 快捷键要的就是"切一下中英"，所以从英文回去时回到用户上一次用的那个中文布局——9 键用户不该
     * 被顺手换成 26 键。
     */
    private fun toggleLanguage() {
        val current = _state.value.layout
        if (current.isChinese) {
            languageReturn = current
            selectLayout(LayoutId.English)
        } else {
            selectLayout(languageReturn)
        }
    }

    /** Caps Lock 长按：锁大小写，再长按一次解除（短按是 [toggleLanguage]）。 */
    private fun toggleCapsLock() {
        val current = _state.value
        _state.value = current.copy(capsLock = !current.capsLock, shifted = false)
        // 大写锁是"一直生效"的状态，面板上另有常驻提示（见 CompatToolbar 的那颗"大写"胶囊），
        // 这里再闪一下，按下去的那一下就有反馈。
        windowHost?.showLanguageBadge(badgeText())
    }

    /** 闪示上写什么：大写锁优先，否则是当前语言（中 / 英 / 日）。 */
    private fun badgeText(): String {
        val state = _state.value
        return when {
            state.capsLock -> "大写"
            state.layout.isChinese -> "中"
            state.layout.isJapanese -> "日"
            else -> "英"
        }
    }

    /** Walks the enabled layouts: 中 ⇄ 英, with 日 in the rotation only when it is switched on. */
    fun cycleLanguage() {
        val order = LayoutId.cycleFor(settings.current.japaneseEnabled)
        val index = order.indexOf(_state.value.layout)
        val next = if (index < 0) order.first() else order[(index + 1) % order.size]
        selectLayout(next)
    }

    private fun showPage(page: KeyboardPage) {
        if (_state.value.page == page) return
        // Leaving the letter page with a live reading would strand the composition.
        if (page != KeyboardPage.Letters) commitBuffer()
        _state.value = _state.value.copy(page = page, shifted = false)
    }

    fun openSettings() {
        val intent = Intent(service, SettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        service.startActivity(intent)
    }

    fun hideKeyboard() {
        service.requestHideSelf(0)
    }

    private fun warmUpNineKey() {
        scope.launch(Dispatchers.Default) {
            // Building the keypad index costs a full pass over the word table; do it while the
            // user is still looking at the layout rather than on the first keystroke.
            runCatching { engine.evaluate("1") }
        }
    }

    private fun restoreLayout() {
        val saved = layoutOrFallback(settings.current.lastLayout, settings.current.japaneseEnabled)
        _state.value = _state.value.copy(layout = saved)
        engine = AppGraph.engines.engineFor(saved.engine)
        voice.layoutHint = saved
        if (saved.isChinese) languageReturn = saved
    }

    /**
     * A saved layout is only usable while it is still enabled: turning Japanese off has to move a
     * 日 keyboard back to 中文 rather than leaving the user stuck on a layout they cannot cycle
     * away from.
     */
    private fun layoutOrFallback(saved: String?, japaneseEnabled: Boolean): LayoutId {
        val layout = LayoutId.fromKey(saved) ?: LayoutId.Pinyin26
        val allowed = LayoutId.enabled(japaneseEnabled)
        return if (layout in allowed) layout else LayoutId.Pinyin26
    }

    private fun refreshSettings() {
        val snapshot: AppSettings = settings.current
        _state.value = _state.value
            .withAppearance(snapshot)
            .copy(dictionaryReady = AppGraph.engines.dictionaryReady.value)
        // Japanese turned off while it was the active layout: move back to Chinese instead of
        // leaving the user on a layout that is no longer part of the rotation.
        if (!snapshot.japaneseEnabled && _state.value.layout == LayoutId.JapaneseRomaji) {
            selectLayout(LayoutId.Pinyin26)
        }
    }

    /**
     * Everything the keyboard surface reads straight out of settings. Keeping it in one place
     * stops the two settings paths (live flow and refresh) from drifting apart.
     */
    private fun ImeUiState.withAppearance(snapshot: AppSettings) = copy(
            themeMode = snapshot.themeMode,
            themeSource = snapshot.themeSource,
            accentHue = snapshot.accentHue,
            accentSaturation = snapshot.accentSaturation,
            keyCornerRadiusDp = snapshot.keyCornerRadiusDp,
            keyLabelScalePercent = snapshot.keyLabelScalePercent,
            keyBackground = snapshot.keyBackground,
            keyHeightDp = snapshot.keyHeightDp,
            bottomGapDp = snapshot.bottomGapDp,
            symbolWidth = snapshot.symbolWidth,
            landscapeFrame = snapshot.landscapeFrame,
            floatingWidthPercent = snapshot.floatingWidthPercent,
            floatingKeyHeightDp = snapshot.floatingKeyHeightDp,
            japaneseEnabled = snapshot.japaneseEnabled,
            externalInputMode = snapshot.externalInputMode,
            compatToolbarEnabled = snapshot.compatToolbarEnabled,
            compatToolbarXPercent = snapshot.compatToolbarXPercent,
            compatToolbarYPercent = snapshot.compatToolbarYPercent,
            showUpdateDot = snapshot.showUpdateDot,
            showNumberRow = snapshot.showNumberRow,
            swipeUpSymbols = snapshot.swipeUpSymbols,
            spaceCursorControl = snapshot.spaceCursorControl,
            hapticFeedback = snapshot.hapticFeedback,
            voiceCorrection = snapshot.voiceCorrection,
            voiceAutoApplyDelayMs = snapshot.voiceAutoApplyDelayMs,
        )

    private fun updateEnterLabel() {
        val action = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val label = when (action) {
            EditorInfo.IME_ACTION_SEARCH -> "搜索"
            EditorInfo.IME_ACTION_SEND -> "发送"
            EditorInfo.IME_ACTION_GO -> "前往"
            EditorInfo.IME_ACTION_DONE -> "完成"
            EditorInfo.IME_ACTION_NEXT -> "下一项"
            else -> "换行"
        }
        if (_state.value.enterLabel != label) _state.value = _state.value.copy(enterLabel = label)
    }

    companion object {
        /** 会改动编辑器内容的键：按下其中任何一颗，剪贴板提示就算看过了。 */
        private val EDITING_KEYS = setOf(
            KeyCode.Char,
            KeyCode.Text,
            KeyCode.Backspace,
            KeyCode.Enter,
            KeyCode.Space,
        )

        /** 成对键的输出（与 KeyboardLayouts 的 *_PAIRS 一一对应）。 */
        private val PAIR_KEYS: Set<String> = setOf(
            "（）", "【】", "《》", "〈〉", "「」", "『』", "“”", "‘’", "〔〕", "〖〗",
            "()", "[]", "{}", "<>", "\"\"", "''",
        )

        private const val DOUBLE_SPACE_WINDOW_MS = 450L
        /** 与 [CloudriftImeService] 同一个 tag：`adb logcat -s CloudriftIme` 一条就够。 */
        private const val LOG_TAG = "CloudriftIme"
        /** 单独按一下 Shift、且这么久以内抬起，才算"单击切中英"（按住 Shift 打字不算）。 */
        private const val SHIFT_TAP_MAX_MS = 500L
        private val COMPOSITION_ARROW_KEYS = setOf(
            android.view.KeyEvent.KEYCODE_DPAD_LEFT,
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
        )
        /** Caps Lock 按住超过这么久算长按（锁大小写）；不到就是短按（切中英）。 */
        private const val CAPS_LOCK_LONG_PRESS_MS = 400L
        /** How long two single character commits may be apart and still belong to one learned word. */
        private const val AUTO_WORD_WINDOW_MS = 3000L
        /** Longest run of single characters that is still remembered as one learned word. */
        private const val AUTO_WORD_MAX_CHARS = 12
        /** Bounds the corner drag may move the floating keyboard within. */
        const val MIN_FLOATING_WIDTH_PERCENT = 45
        const val MAX_FLOATING_WIDTH_PERCENT = 100
        const val MIN_FLOATING_KEY_HEIGHT = 28
        const val MAX_FLOATING_KEY_HEIGHT = 72
    }
}

/**
 * A-Z / a-z。用 ASCII 判断而不是 `Char.isLetter()`：后者对汉字、假名也为真，而那些字符从物理
 * 键盘打出来时应当直接上屏，不该被塞进拼音缓冲。
 */
private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

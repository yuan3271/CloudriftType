package com.yuan3271.cloudrift.ime

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.inputmethod.EditorInfo
import com.yuan3271.cloudrift.data.AppGraph
import com.yuan3271.cloudrift.data.AppSettings
import com.yuan3271.cloudrift.data.ClipEntry
import com.yuan3271.cloudrift.data.ClipboardStore
import com.yuan3271.cloudrift.data.SettingsRepository
import com.yuan3271.cloudrift.data.SymbolWidth
import com.yuan3271.cloudrift.data.ThemeMode
import com.yuan3271.cloudrift.engine.Candidate
import com.yuan3271.cloudrift.engine.CandidateKind
import com.yuan3271.cloudrift.engine.InputEngine
import com.yuan3271.cloudrift.input.EditorProxy
import com.yuan3271.cloudrift.input.KeyCode
import com.yuan3271.cloudrift.input.KeyDef
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
    /** The service, when it can frame the window; null in tests and headless use. */
    private val windowHost: InputWindowHost? = service as? InputWindowHost
    private val editor = EditorProxy { service.currentInputConnection }
    private val clipboard = ClipboardStore(service, scope)
    private val haptics = KeyboardHaptics(service)

    private val _state = MutableStateFlow(ImeUiState())
    val state: StateFlow<ImeUiState> = _state.asStateFlow()

    private var editorInfo: EditorInfo? = null
    private var engine: InputEngine = AppGraph.engines.engineFor(LayoutId.Pinyin26.engine)
    private var selfEditCounter = 0
    private var lastSpaceAt = 0L
    private var doubleSpaceArmed = false
    /** Timer that applies a finished transcript on its own; see voiceAutoApplyDelayMs. */
    private var autoApplyJob: Job? = null
    /** The last thing that went in as one unit, so one backspace can take it back out. */
    private var lastCommit: CommitRecord? = null
    /** True while the editor reports a selection rather than a plain caret. */
    private var hasSelection: Boolean = false

    private data class CommitRecord(val text: String, val at: Long)
    /** Last single character that went in, used to spot a word the user is spelling out. */
    private var lastCharacter: CharacterCommit? = null

    private data class CharacterCommit(val text: String, val reading: String, val at: Long)

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
        editorInfo = info
        commitBuffer()
        updateEnterLabel()
    }

    fun onStartInputView() {
        editor.clearComposing()
        refreshSettings()
        updateEnterLabel()
        // A fresh input session always starts on the keys, never inside the settings sheet.
        _state.value = _state.value.copy(quickSettingsVisible = false, clipboardVisible = false)
        if (!AppGraph.engines.dictionaryReady.value) AppGraph.engines.warmUp()
    }

    fun onFinishInputView() {
        commitBuffer()
        if (_state.value.isVoiceActive) voice.cancel()
    }

    /**
     * Called by the service when the editor's selection moves. A move that we did not cause
     * means the user tapped somewhere else, so the pending reading is committed.
     */
    fun onSelectionChanged() {
        onSelectionChanged(selectionStart = -1, selectionEnd = -1)
    }

    /**
     * @param selectionStart selectionEnd as the editor reports them; equal means a plain caret.
     */
    fun onSelectionChanged(selectionStart: Int, selectionEnd: Int) {
        if (selectionStart >= 0 && selectionEnd >= 0) {
            hasSelection = selectionStart != selectionEnd
        }
        if (selfEditCounter > 0) {
            selfEditCounter--
            return
        }
        // The caret moved without us: the user is editing somewhere else, so the character chain
        // that could become a learned word is broken.
        lastCharacter = null
        // lastCommit is deliberately *not* cleared here any more. Editors report selection changes
        // in their own time and with their own granularity, so clearing on every callback used to
        // disarm the "one backspace takes the commit back" window a few frames after every commit.
        // takeBackLastCommit() re-reads the text in front of the caret instead, which is the only
        // thing that actually proves the commit is still there to take back.
        if (_state.value.isComposing) commitBuffer()
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
        if (code.isEmpty() || candidate.kind == CandidateKind.Raw) return

        AppGraph.profile.recordChoice(code, candidate.text, candidate.annotation)

        // Two single characters committed back to back are a word of their own: 张 then 伟 teaches
        // 张伟 (reading "zhangwei") even though no dictionary ships it.
        val now = System.currentTimeMillis()
        val previous = lastCharacter
        val isCharacter = candidate.kind == CandidateKind.Character &&
            candidate.annotation.isNotEmpty() &&
            candidate.text.length == 1
        if (isCharacter) {
            if (previous != null && now - previous.at <= AUTO_WORD_WINDOW_MS) {
                AppGraph.profile.rememberWord(
                    reading = previous.reading + candidate.annotation,
                    word = previous.text + candidate.text,
                )
            }
            lastCharacter = CharacterCommit(candidate.text, candidate.annotation, now)
        } else {
            lastCharacter = null
        }
    }

    /** Clears the learned habits and invented words; the switch stays where the user left it. */
    fun clearLearning() = AppGraph.profile.clear()

    // ---- key handling ---------------------------------------------------------------

    fun onKey(key: KeyDef) {
        if (key.code != KeyCode.Settings && key.code != KeyCode.HideKeyboard) feedback()
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

    /** Quick settings edits the same store the full settings screen writes to. */
    fun updateSettings(transform: (AppSettings) -> AppSettings) = settings.update(transform)

    /** Inserts text from a panel (clipboard, snippets) without touching the reading buffer. */
    fun commitText(text: String) {
        if (text.isEmpty()) return
        lastCharacter = null
        if (_state.value.isComposing) commitBuffer()
        selfEditCounter++
        editor.commit(text)
        // Pasted text is not a candidate pick: backspace should take it apart one character at a
        // time, not swallow the whole paste.
        lastCommit = null
    }

    fun setQuickSettingsVisible(visible: Boolean) {
        if (_state.value.quickSettingsVisible == visible) return
        _state.value = _state.value.copy(quickSettingsVisible = visible)
    }

    /** 全角 / 半角, remembered across sessions like any other keyboard preference. */
    fun setSymbolWidth(width: SymbolWidth) {
        settings.update { it.copy(symbolWidth = width) }
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
        val width = (current.floatingWidthPercent + widthDeltaPercent)
            .toInt()
            .coerceIn(MIN_FLOATING_WIDTH_PERCENT, MAX_FLOATING_WIDTH_PERCENT)
        val height = (current.floatingKeyHeightDp + keyHeightDeltaDp)
            .toInt()
            .coerceIn(MIN_FLOATING_KEY_HEIGHT, MAX_FLOATING_KEY_HEIGHT)
        if (width == current.floatingWidthPercent && height == current.floatingKeyHeightDp) return
        _state.value = current.copy(
            floatingWidthPercent = width,
            floatingKeyHeightDp = height,
        )
    }

    fun commitFloatingKeyboardSize() {
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

    /** The toolbar's yellow mark opens the settings screen, where the release is described. */
    fun openUpdate() = openSettings()

    /**
     * The clipboard is its own panel rather than a sheet over the keys: history is the point, and
     * a list needs the whole key area to be readable.
     */
    fun toggleClipboard() {
        val next = !_state.value.clipboardVisible
        setQuickSettingsVisible(false)
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
            editor.deleteSurroundingBefore(BACKSPACE_WORD_LENGTH)
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
        lastCharacter = null
        lastCommit = null
        clearBuffer()
        selfEditCounter++
        editor.clearAll()
    }

    fun selectCandidate(index: Int) {
        val current = _state.value
        val candidate = current.candidates.getOrNull(index) ?: return
        if (settings.current.hapticFeedback) haptics.candidate()
        learn(current.raw, candidate)
        val remaining = if (candidate.consumed >= current.raw.length) {
            ""
        } else {
            current.raw.drop(candidate.consumed)
        }
        selfEditCounter++
        editor.commit(candidate.text)
        // A sentence candidate can be ten characters long; one backspace should take the whole
        // thing back, which is what every Chinese keyboard does after a word goes in.
        rememberCommit(candidate.text)
        applyBuffer(remaining)
        if (remaining.isEmpty()) clearBuffer()
    }

    fun toggleCandidatesExpanded() {
        _state.value = _state.value.copy(candidatesExpanded = !_state.value.candidatesExpanded)
    }

    fun selectLayout(layout: LayoutId) {
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
        settings.update { it.copy(lastLayout = layout.name) }
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

    fun retryVoice() {
        voice.dismiss()
        voice.start(settings.current)
    }

    fun commitVoiceResult() {
        val ready = _state.value.voice as? VoiceState.Ready ?: return
        autoApplyJob?.cancel()
        autoApplyJob = null
        _state.value = _state.value.copy(autoApplyPending = false)
        selfEditCounter++
        editor.commit(ready.text)
        // A dictated sentence is not a candidate either. It used to be remembered like one, so the
        // first backspace after speaking deleted the entire sentence instead of one character.
        lastCommit = null
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
        lastCommit = null
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
        applyBuffer(current.raw + shiftedText)
        if (current.shifted && !current.capsLock) {
            _state.value = _state.value.copy(shifted = false)
        }
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

    private fun applyBuffer(raw: String) {
        val output = engine.evaluate(raw)
        val preview = output.composingPreview.ifEmpty { raw }
        _state.value = _state.value.copy(
            raw = raw,
            preview = preview,
            candidates = output.candidates,
            candidatesExpanded = _state.value.candidatesExpanded && output.candidates.isNotEmpty(),
        )
        if (raw.isEmpty()) {
            clearComposingQuietly()
        } else {
            selfEditCounter++
            editor.setComposing(preview)
        }
    }

    private fun clearBuffer() {
        _state.value = _state.value.copy(raw = "", preview = "", candidates = emptyList(), candidatesExpanded = false)
        clearComposingQuietly()
    }

    /**
     * Drops the composing region without letting the editor's selection callback look like a user
     * edit - otherwise the callback would consume the "one backspace takes the commit back" window
     * a few frames after every commit.
     */
    private fun clearComposingQuietly() {
        selfEditCounter++
        editor.clearComposing()
    }

    /** Commits whatever is in the reading buffer without accepting a candidate. */
    private fun commitBuffer() {
        val current = _state.value
        if (current.raw.isEmpty()) {
            editor.clearComposing()
            return
        }
        val text = current.primaryCandidate?.takeIf { it.kind != CandidateKind.Raw }?.text
            ?: engine.literal(current.raw)
        selfEditCounter++
        editor.commit(text)
        _state.value = current.copy(raw = "", preview = "", candidates = emptyList(), candidatesExpanded = false)
    }

    private fun commitLiteral(text: String) {
        if (text.isEmpty()) return
        lastCharacter = null
        if (_state.value.isComposing) commitBuffer()
        selfEditCounter++
        editor.commit(text)
        updateEnterLabel()
    }

    private fun backspace() {
        lastCharacter = null
        if (takeBackLastCommit()) return
        // A selected range is the user's target, not the text next to the caret, and
        // deleteSurroundingText is ignored by most editors while a selection is live - pressing the
        // key is what makes them delete the selection.
        if (hasSelection) {
            selfEditCounter++
            editor.sendBackspaceKey()
            doubleSpaceArmed = false
            return
        }
        val current = _state.value
        if (current.raw.isNotEmpty()) {
            applyBuffer(current.raw.dropLast(1))
            if (current.raw.length == 1) clearBuffer()
            return
        }
        selfEditCounter++
        editor.deleteSurroundingBefore(1)
        doubleSpaceArmed = false
    }

    /**
     * The first backspace after a commit takes that whole commit back, so a seven character
     * sentence does not have to be erased one character at a time.
     *
     * The editor is asked what is actually in front of the caret instead of trusting our own
     * bookkeeping: composing text and selection changes arrive asynchronously, and an editor that
     * reports them differently than expected used to leave the caret somewhere else while this
     * still deleted the remembered length.
     */
    private fun takeBackLastCommit(): Boolean {
        val commit = lastCommit ?: return false
        val elapsed = System.currentTimeMillis() - commit.at
        val before = if (_state.value.raw.isEmpty() && elapsed <= COMMIT_UNDO_WINDOW_MS) {
            editor.textBefore(commit.text.length)
        } else {
            null
        }
        val takeBack = takesBackCommit(
            remembered = commit.text,
            before = before,
            elapsedMs = elapsed,
            hasComposingText = _state.value.raw.isNotEmpty(),
        )
        lastCommit = null
        if (!takeBack) return false
        selfEditCounter++
        editor.deleteSurroundingBefore(commit.text.length)
        doubleSpaceArmed = false
        return true
    }

    private fun rememberCommit(text: String) {
        lastCommit = if (text.isEmpty()) null else CommitRecord(text, System.currentTimeMillis())
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
        val current = _state.value
        val literal = engine.literal(current.raw)
        selfEditCounter++
        editor.commit(literal)
        _state.value = current.copy(
            raw = "",
            preview = "",
            candidates = emptyList(),
            candidatesExpanded = false,
        )
    }

    private fun toggleShift() {
        val current = _state.value
        when {
            !current.shifted -> _state.value = current.copy(shifted = true, capsLock = false)
            current.shifted && !current.capsLock -> _state.value = current.copy(capsLock = true)
            else -> _state.value = current.copy(shifted = false, capsLock = false)
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
            showUpdateDot = snapshot.showUpdateDot,
            showNumberRow = snapshot.showNumberRow,
            swipeUpSymbols = snapshot.swipeUpSymbols,
            spaceCursorControl = snapshot.spaceCursorControl,
            hapticFeedback = snapshot.hapticFeedback,
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
        private const val DOUBLE_SPACE_WINDOW_MS = 450L
        private const val BACKSPACE_WORD_LENGTH = 8
        /** How long two single character commits may be apart and still form a learned word. */
        private const val AUTO_WORD_WINDOW_MS = 3000L
        /** How long after a commit one backspace still takes the whole thing back. */
        private const val COMMIT_UNDO_WINDOW_MS = 4000L
        /** Bounds the corner drag may move the floating keyboard within. */
        const val MIN_FLOATING_WIDTH_PERCENT = 45
        const val MAX_FLOATING_WIDTH_PERCENT = 100
        const val MIN_FLOATING_KEY_HEIGHT = 28
        const val MAX_FLOATING_KEY_HEIGHT = 72
    }
}

/**
 * Whether one backspace should remove the whole remembered commit instead of a single character.
 *
 * Kept as a pure function because this rule is the part that went wrong: it is not enough to
 * remember what was committed, the editor has to be showing exactly that text in front of the
 * caret right now. A different text, no answer from the editor, a live composition, or a commit
 * older than the window all fall back to deleting one character.
 */
internal fun takesBackCommit(
    remembered: String,
    before: String?,
    elapsedMs: Long,
    hasComposingText: Boolean,
    windowMs: Long = 4000L,
): Boolean {
    if (remembered.isEmpty() || hasComposingText) return false
    if (elapsedMs > windowMs) return false
    return before == remembered
}

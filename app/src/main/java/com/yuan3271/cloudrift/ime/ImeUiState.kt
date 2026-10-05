package com.yuan3271.cloudrift.ime

import com.yuan3271.cloudrift.data.ThemeMode
import com.yuan3271.cloudrift.data.ThemeSource
import com.yuan3271.cloudrift.data.ClipEntry
import com.yuan3271.cloudrift.data.ExternalInputMode
import com.yuan3271.cloudrift.data.KeyBackground
import com.yuan3271.cloudrift.data.KeyboardFrame
import com.yuan3271.cloudrift.data.SymbolWidth
import com.yuan3271.cloudrift.data.UpdateInfo
import com.yuan3271.cloudrift.data.UserStats
import com.yuan3271.cloudrift.engine.Candidate
import com.yuan3271.cloudrift.input.ExternalInputSnapshot
import com.yuan3271.cloudrift.input.KeyboardPage
import com.yuan3271.cloudrift.input.LayoutId
import com.yuan3271.cloudrift.voice.VoiceState

/**
 * 符号页的左栏：标点（可切全角/半角）还是表情。
 *
 * 不做成第三种 [SymbolWidth]：全角/半角是"这个符号打成哪种形式"，要跨会话记住；表情是"这一页
 * 看哪张表"，是浏览状态，两件事不该挤进同一个枚举。
 */
enum class SymbolSheet {
    Punctuation,
    Emoji,
}

/**
 * Everything the keyboard window needs to draw itself. The controller owns this and the
 * composables only read it, which keeps gesture handling out of the render path.
 */
data class ImeUiState(
    val layout: LayoutId = LayoutId.Pinyin26,
    val page: KeyboardPage = KeyboardPage.Letters,
    val shifted: Boolean = false,
    val capsLock: Boolean = false,

    /** Raw reading buffer: pinyin letters, keypad digits, romaji or kana. */
    val raw: String = "",
    /** What the editor is told to display; equals [raw] unless the engine translates. */
    val preview: String = "",
    val candidates: List<Candidate> = emptyList(),
    val candidatesExpanded: Boolean = false,

    val dictionaryReady: Boolean = false,
    /** What the on-device learner has picked up so far; shown in the settings screen. */
    val userStats: UserStats = UserStats(),
    /** Milliseconds a finished transcript waits before applying itself; 0 means wait for a tap. */
    val voiceAutoApplyDelayMs: Int = 1000,
    /** True while a finished transcript is counting down to applying itself. */
    val autoApplyPending: Boolean = false,
    /** True while holding backspace and sliding up has lit the "clear everything" option. */
    val clearAllArmed: Boolean = false,
    /** A newer release, when the checker has found one. */
    val update: UpdateInfo? = null,
    /** Whether the toolbar shows the little yellow update mark. */
    val showUpdateDot: Boolean = true,
    /**
     * 键盘被打开过几次。工具栏那枚「有更新」标记拿它当动画的钥匙——每次开键盘都重播一遍
     * 长条收成圆点的动画，而不是一辈子只播开头那一次。
     */
    val keyboardShows: Int = 0,
    val voice: VoiceState = VoiceState.Idle,
    /**
     * True while the user is holding the space bar to dictate. The keys stay on screen in
     * this mode, because lifting the finger off the space bar is what ends the recording.
     */
    val holdToTalk: Boolean = false,

    val enterLabel: String = "换行",
    val themeMode: ThemeMode = ThemeMode.System,
    val themeSource: ThemeSource = ThemeSource.Dynamic,
    val accentHue: Int = 222,
    val accentSaturation: Int = 42,

    val keyCornerRadiusDp: Int = 18,
    /** Key label size as a percentage of the designed size. */
    val keyLabelScalePercent: Int = 100,
    val keyBackground: KeyBackground = KeyBackground.Filled,
    /** 0 means "derive from the screen size". */
    val keyHeightDp: Int = 0,
    val bottomGapDp: Int = 0,
    /** Full or half width punctuation in the symbol bar. */
    val symbolWidth: SymbolWidth = SymbolWidth.Full,
    /** 符号页现在显示标点还是表情。 */
    val symbolSheet: SymbolSheet = SymbolSheet.Punctuation,
    /**
     * 表情页选中的分类（[com.yuan3271.cloudrift.engine.emoji.EmojiGroup.key]）。空串表示"还没
     * 选过"，界面落到第一个分类。
     */
    val emojiGroup: String = "",
    /** Landscape: the floating card or the full width keyboard. */
    val landscapeFrame: KeyboardFrame = KeyboardFrame.Floating,
    /** Width of the floating keyboard as a percentage of the screen. */
    val floatingWidthPercent: Int = 78,
    /** Key height of the floating keyboard in dp; independent of [keyHeightDp]. */
    val floatingKeyHeightDp: Int = 42,
    /** Whether the Japanese layout takes part in the language key rotation. */
    val japaneseEnabled: Boolean = false,
    val showNumberRow: Boolean = false,
    val swipeUpSymbols: Boolean = true,
    val spaceCursorControl: Boolean = true,
    val hapticFeedback: Boolean = true,
    /** 语音结果是否再过一遍文本修正 API；关掉就完全不发这个请求。快速设置里有开关。 */
    val voiceCorrection: Boolean = true,

    /** 外接键鼠接入时显示什么（用户设置）。 */
    val externalInputMode: ExternalInputMode = ExternalInputMode.CompatPanel,
    /** 场上有没有外接键鼠；由服务的设备监听推过来（见 ExternalInputMonitor）。 */
    val externalInputs: ExternalInputSnapshot = ExternalInputSnapshot(),
    /** 键鼠兼容面板上那块工具面板要不要显示（设置项；面板上的 ✕ 关的就是它）。 */
    val compatToolbarEnabled: Boolean = true,
    /** 工具面板的位置（屏幕百分比）；负数＝用户还没拖过，服务会放在默认位置。 */
    val compatToolbarXPercent: Int = -1,
    val compatToolbarYPercent: Int = -1,
    /**
     * 两块面板的独立窗口都开出来了吗（见 InputWindowHost.applyCompatPanel）。
     *
     * false 表示退回老样子：候选词面板与工具面板都画进输入法自己的那个窗口里。
     */
    val compatWindowsReady: Boolean = false,
    /**
     * 两块面板的窗口开不出来，而且原因是没有「显示在其他应用上层」权限。
     *
     * 这条要单独说，因为用户能自己解决（去授权），而别的失败原因用户无能为力。
     */
    val compatNeedsOverlayPermission: Boolean = false,

    /** True while the in-keyboard quick settings sheet is open. */
    val quickSettingsVisible: Boolean = false,

    /** True while the clipboard panel is open in place of the keys. */
    val clipboardVisible: Boolean = false,
    val clipboardEntries: List<ClipEntry> = emptyList(),
    /**
     * 刚复制进来、还没处理的那条剪贴板内容。非空时候选栏把它摆出来（图标 + 内容 + 一个叉）：
     * 点一下直接粘贴，点叉只把提示收掉、历史里那条照留。
     */
    val clipboardOffer: ClipEntry? = null,

    /** Transient notice shown above the keyboard (permission prompts, errors). */
    val notice: String? = null,
    val noticeNeedsMicrophonePermission: Boolean = false,
) {
    val isComposing: Boolean get() = raw.isNotEmpty()

    val primaryCandidate: Candidate? get() = candidates.firstOrNull()

    val isVoiceActive: Boolean get() = voice !is VoiceState.Idle

    /**
     * 一次拼写结束（选完候选 / 上屏 / 取消 / 清空）之后的状态：缓冲与候选一起清空。
     *
     * 单独写成一处，是因为"清空"这件事以前散在四条路上，其中 [ImeController.selectCandidate]
     * 那条**漏了**——它把清空托付给了联想条，而键鼠兼容模式下
     * 联想条不显示，于是 raw 与候选留在状态里：物理键盘的下一次空格 / 数字仍然算"还在拼写"，
     * 把同一个词又上屏一次（用户报的"空格打出两个词""数字按几次出几个词"）。
     */
    fun clearedBuffer(): ImeUiState =
        copy(raw = "", preview = "", candidates = emptyList(), candidatesExpanded = false)

    /**
     * 键盘窗口里画"键鼠兼容面板"（候选词栏 + 工具栏）而不是整块按键。
     *
     * 默认的 [ExternalInputMode.CompatPanel] 只在真的有外接键鼠时生效：没有外接设备的话，两种
     * 设置都走原来的虚拟键盘，路径一字不差。
     */
    val showsCompatPanel: Boolean
        get() = externalInputMode == ExternalInputMode.CompatPanel && externalInputs.present

    /**
     * 键鼠兼容模式下，候选词那个窗口现在有没有内容可显示。
     *
     * 用户点名：候选词栏**默认不出现**，只有真在打字（或者有候选、联想、剪贴板提示、展开的
     * 第二层）时才贴着光标露出来——外接键盘的人不该在屏幕上看一条空栏。
     *
     * 剪贴板提示（[clipboardOffer]）**不算"有内容"**：键鼠模式下只有工具栏那颗 📋 是剪贴板的
     * 入口，"刚复制的这段要不要贴"这条一次性提示在那边不露脸（用户点名"有内容了也隐藏"）。
     */
    val compatContentVisible: Boolean
        get() = showsCompatPanel && (
            isComposing ||
                candidates.isNotEmpty() ||
                notice != null ||
                candidatesExpanded ||
                clipboardVisible ||
                voice !is VoiceState.Idle ||
                page != KeyboardPage.Letters
            )

    /**
     * 键鼠兼容模式下，候选词那一块里画的是**展开的第二层**（语音 / 剪贴板 / 更多候选 / 符号页 /
     * 数字页），而不是那一排候选词。
     */
    val compatExpandedPanel: Boolean
        get() = candidatesExpanded || compatToolbarMenu

    /**
     * 工具面板现在露不露脸。
     *
     * 用户那一轮先说"工具栏也默认隐藏，只在输入的时候出现"，随后把话说清楚了：**只要输入模式
     * 被激活就出现**——鼠标点一下输入框、可以开始打字了，工具栏就该在；离开输入（点到别处、
     * 收起键盘）就没有。所以这条**不是**"打字才出现"（那是候选词那一块的规矩），也不是常驻。
     *
     * 实现上就是 [showsCompatPanel] 再看 [compatToolbarEnabled]：输入法只在输入会话里存在，进入
     * 会话时服务会把两块窗口重新建起来（见 `CloudriftImeService.onStartInputView`），会话结束一起
     * 拆掉；设置里可以整个关掉这块面板（面板右端那颗 ✕ 是同一个开关）。
     */
    val compatToolbarVisible: Boolean
        get() = showsCompatPanel && compatToolbarEnabled

    /**
     * 键鼠兼容模式下，候选词那一块现在是一份**从工具栏里弹出来的菜单**（符号页 / 数字页 / 语音 /
     * 剪贴板）——它的默认位置是压在工具面板上面（用户点名），而不是跟着光标跑。
     *
     * 「更多候选」（[candidatesExpanded]）不在这一条里：它是候选栏那一排的延续，位置照旧跟着光标。
     */
    val compatToolbarMenu: Boolean
        get() = when {
            voice !is VoiceState.Idle && !holdToTalk -> true
            clipboardVisible -> true
            page != KeyboardPage.Letters -> true
            else -> false
        }
}

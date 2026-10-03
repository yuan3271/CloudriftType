package com.yuan3271.cloudrift.ime

import com.yuan3271.cloudrift.data.ThemeMode
import com.yuan3271.cloudrift.data.ThemeSource
import com.yuan3271.cloudrift.data.ClipEntry
import com.yuan3271.cloudrift.data.KeyBackground
import com.yuan3271.cloudrift.data.KeyboardFrame
import com.yuan3271.cloudrift.data.SymbolWidth
import com.yuan3271.cloudrift.data.UpdateInfo
import com.yuan3271.cloudrift.data.UserStats
import com.yuan3271.cloudrift.engine.Candidate
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
}

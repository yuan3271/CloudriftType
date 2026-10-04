package com.yuan3271.cloudrift.data

/** Which colour scheme the keyboard should use. */
enum class ThemeMode {
    System,
    Light,
    Dark,
    ;

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: System
    }
}

/** Where the palette comes from. */
enum class ThemeSource {
    /** Material You: sampled from the wallpaper. Falls back to [Cloudrift] below Android 12. */
    Dynamic,

    /** The built in blue/peach pair. */
    Cloudrift,

    /** A palette generated from the user's own hue and saturation. */
    Custom,
    ;

    companion object {
        fun fromKey(key: String?): ThemeSource =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: Dynamic
    }
}

/**
 * Which punctuation forms the symbol bar offers. Chinese punctuation is always full width, so
 * this only decides between ，/。 and ,/. and their brackets and maths.
 */
/**
 * How the keyboard sits on the screen in landscape. A phone in landscape has very little height
 * left once a full width keyboard is up, so floating (a smaller, movable card) is the default and
 * full width is the opt in.
 */
/**
 * Candidate order for a reading that has more than one syllable.
 *
 * [LongFirst] is the sentence view: whole sentences, then the words they are made of, then single
 * characters. [CharacterFirst] is the classic single character view: the characters of the syllable
 * in front, then every word and sentence the reading can also make.
 */
enum class CandidateOrder {
    LongFirst,
    CharacterFirst,
    ;

    companion object {
        fun fromKey(key: String?): CandidateOrder =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: LongFirst
    }
}

enum class KeyboardFrame {
    Full,
    Floating,
    ;

    companion object {
        fun fromKey(key: String?): KeyboardFrame =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: Floating
    }
}

/**
 * 外接键鼠（蓝牙键盘、桌面模式 / Chromebook 自带的键盘、鼠标或触控板）在场时，输入法窗口画什么。
 *
 * 这**不是**"要不要显示输入法"——两种选择都会显示窗口，区别只在窗口里的东西：一种是照旧整块
 * 虚拟键盘，另一种只留候选词栏与工具栏（键鼠兼容面板）。物理键鼠在手时，屏上的按键既用不上，
 * 又会把应用顶掉小半屏，所以默认选面板；想边看边点虚拟键盘的用户可以在设置里切回来。
 */
enum class ExternalInputMode {
    /** 键鼠兼容面板：候选词栏 + 工具栏，一条圆角浮条。 */
    CompatPanel,

    /** 虚拟键盘：和没有外接键鼠时完全一样。 */
    VirtualKeyboard,
    ;

    companion object {
        fun fromKey(key: String?): ExternalInputMode =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: CompatPanel
    }
}

/**
 * 语音输入录音期间，外面正在放的声音怎么办。
 *
 * 录音要把人声收干净，而手机可能正在放歌 / 播视频。三种选择对应 Android 的三种音频焦点：
 * 有的用户愿意让音乐先停一下（静音），有的只希望它小下去（压低），也有人就是要它照常放。
 */
enum class VoiceMediaBehavior {
    /** 不管别人：不发音频焦点请求。 */
    LeaveAlone,

    /** 压低：`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`，别人把音量降下来继续放。 */
    Duck,

    /** 静音：`AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE`，别人停下播放。 */
    Mute,
    ;

    companion object {
        fun fromKey(key: String?): VoiceMediaBehavior =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: Duck
    }
}

enum class SymbolWidth {
    Full,
    Half,
    ;

    companion object {
        fun fromKey(key: String?): SymbolWidth =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: Full
    }
}

/** How key backgrounds are painted. */
enum class KeyBackground {
    /** A filled container per key. */
    Filled,

    /** Only a hairline outline. */
    Outlined,

    /** Nothing behind the glyph - the keyboard surface shows through. */
    Ghost,
    ;

    companion object {
        fun fromKey(key: String?): KeyBackground =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: Filled
    }
}

/**
 * Which wire protocol an endpoint speaks.
 *
 * Aliyun's OpenAI compatible endpoint only implements chat completions - probing
 * `POST /compatible-mode/v1/audio/transcriptions` returns 404 - so Aliyun speech recognition
 * goes through the 千问AI平台 multimodal-generation endpoint instead, which accepts the audio
 * inline as a base64 data URL.
 */
enum class ApiStyle {
    /** OpenAI `/audio/transcriptions` and `/chat/completions`. */
    OpenAiCompatible,

    /** 千问AI平台 / 阿里云百炼: synchronous `qwen-audio-*-asr-flash` recognition. */
    AliyunQianwen,
    ;

    companion object {
        fun fromKey(key: String?): ApiStyle =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: OpenAiCompatible
    }
}

/**
 * A single OpenAI-compatible endpoint. The speech endpoint and the chat endpoint are
 * configured separately because they are frequently different services (for example a
 * self hosted ASR plus a hosted chat model).
 */
data class ApiEndpoint(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    /** Optional BCP-47 hint forwarded to the speech endpoint. */
    val languageHint: String = "",
    val style: ApiStyle = ApiStyle.OpenAiCompatible,
) {
    val isConfigured: Boolean
        get() = when (style) {
            ApiStyle.OpenAiCompatible ->
                baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

            ApiStyle.AliyunQianwen ->
                baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
        }
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val themeSource: ThemeSource = ThemeSource.Dynamic,
    /** Accent hue in degrees, used when [themeSource] is [ThemeSource.Custom]. */
    val accentHue: Int = 222,
    /** Accent saturation in percent, used when [themeSource] is [ThemeSource.Custom]. */
    val accentSaturation: Int = 42,

    val hapticFeedback: Boolean = true,
    val soundFeedback: Boolean = false,

    // ---- keyboard appearance ------------------------------------------------------

    /** Key corner radius in dp. */
    val keyCornerRadiusDp: Int = 18,
    /** Label size on the keys, as a percentage; 100 is the designed size. */
    val keyLabelScalePercent: Int = 100,
    val keyBackground: KeyBackground = KeyBackground.Filled,
    /** Key height in dp; 0 means "derive from screen size". */
    val keyHeightDp: Int = 0,
    /** Gap between the keyboard and the bottom of the screen, in dp. */
    val bottomGapDp: Int = 0,
    /** Full width or half width punctuation in the symbol bar. */
    val symbolWidth: SymbolWidth = SymbolWidth.Full,
    /** Landscape frame: a floating card or the full width keyboard. */
    val landscapeFrame: KeyboardFrame = KeyboardFrame.Floating,
    /** Width of the floating keyboard as a percentage of the screen. */
    val floatingWidthPercent: Int = 78,
    /**
     * Key height of the floating keyboard, in dp. Deliberately separate from [keyHeightDp] and
     * [bottomGapDp]: a floating card is sized by its own corner drag, and shrinking the upright
     * keyboard should not shrink it too.
     */
    val floatingKeyHeightDp: Int = 42,

    // ---- keyboard behaviour -------------------------------------------------------

    val showNumberRow: Boolean = false,
    /** Swiping up on a letter key types the symbol printed above it. */
    val swipeUpSymbols: Boolean = true,
    /** Dragging on the space bar moves the cursor. */
    val spaceCursorControl: Boolean = true,
    /**
     * Let the keyboard learn from what gets committed: which candidate is picked for a code, and
     * words assembled character by character. Everything stays on the device and can be wiped
     * from the settings screen.
     */
    val learningEnabled: Boolean = true,

    /**
     * Whether the Japanese romaji layout takes part in the language key rotation. Off by default:
     * the key walks 中 ⇄ 英 unless the user asks for 日.
     */
    val japaneseEnabled: Boolean = false,

    /**
     * 外接键鼠接入时显示虚拟键盘还是键鼠兼容面板（见 [ExternalInputMode]）。没有外接键鼠时
     * 这项设置不参与任何判断。
     */
    val externalInputMode: ExternalInputMode = ExternalInputMode.CompatPanel,
    /**
     * 键鼠兼容面板里工具面板的位置（屏幕百分比，左上角）。负数＝用户还没拖过，交给服务放在
     * 默认位置（底部居中）；拖过之后就按这里记住，下次插上键鼠还在原地。
     */
    val compatToolbarXPercent: Int = UNSET_POSITION,
    val compatToolbarYPercent: Int = UNSET_POSITION,

    /** How often to look for a newer release; Never turns the check off completely. */
    val updateCheckInterval: UpdateInterval = UpdateInterval.Daily,
    /** The small yellow mark next to the theme key when an update is waiting. */
    val showUpdateDot: Boolean = true,
    /** Sentence first, or single characters first. */
    val candidateOrder: CandidateOrder = CandidateOrder.LongFirst,

    val speech: ApiEndpoint = ApiEndpoint(
        baseUrl = DEFAULT_SPEECH_BASE_URL,
        model = DEFAULT_SPEECH_MODEL,
    ),
    val chat: ApiEndpoint = ApiEndpoint(
        baseUrl = DEFAULT_CHAT_BASE_URL,
        model = DEFAULT_CHAT_MODEL,
    ),
    /** The chat endpoint is only ever asked to fix recognition slips, never to rewrite. */
    val voiceCorrection: Boolean = true,
    /**
     * 录音时对别的媒体声音做什么：压低（默认）/ 静音 / 不管。见 [VoiceMediaBehavior]。
     */
    val voiceMediaBehavior: VoiceMediaBehavior = VoiceMediaBehavior.Duck,
    /**
     * How long a finished transcript waits before it is applied on its own, in milliseconds.
     * The pause is what makes the automatic behaviour safe: the result is on screen long enough
     * to be cancelled or replaced. 0 keeps the result until the user taps 上屏.
     */
    val voiceAutoApplyDelayMs: Int = 1000,
    val autoPunctuation: Boolean = true,

    /**
     * Instant hot words forwarded to 千问 as `parameters.vocabulary`. One per line or comma
     * separated, optionally with a weight, e.g. `云隙输入:5`.
     */
    val hotWords: String = "",

    val lastLayout: String = "",
) {
    companion object {
        const val DEFAULT_SPEECH_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_SPEECH_MODEL = "whisper-1"
        const val DEFAULT_CHAT_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_CHAT_MODEL = "gpt-4o-mini"

        /** 千问AI平台 REST base; the ASR call appends its own path. */
        const val QIANWEN_BASE_URL = "https://maas.qianwenaiapi.com/api/v1"
        const val QIANWEN_SPEECH_PATH = "services/aigc/multimodal-generation/generation"
        const val QIANWEN_DEFAULT_SPEECH_MODEL = "qwen-audio-3.1-asr-flash"

        /** 百炼 / DashScope speaks the OpenAI protocol for chat, and only for chat. */
        const val DASHSCOPE_COMPATIBLE_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"
        const val DASHSCOPE_DEFAULT_CHAT_MODEL = "qwen-plus"

        /** "工具面板还没被拖过"的哨兵值：0..100 都是合法位置，所以只能用负数表示"没设过"。 */
        const val UNSET_POSITION = -1

        fun openAiSpeechPreset() = ApiEndpoint(
            baseUrl = DEFAULT_SPEECH_BASE_URL,
            model = DEFAULT_SPEECH_MODEL,
            style = ApiStyle.OpenAiCompatible,
        )

        fun aliyunSpeechPreset() = ApiEndpoint(
            baseUrl = QIANWEN_BASE_URL,
            model = QIANWEN_DEFAULT_SPEECH_MODEL,
            style = ApiStyle.AliyunQianwen,
            languageHint = "zh",
        )

        fun openAiChatPreset() = ApiEndpoint(
            baseUrl = DEFAULT_CHAT_BASE_URL,
            model = DEFAULT_CHAT_MODEL,
        )

        fun dashScopeChatPreset() = ApiEndpoint(
            baseUrl = DASHSCOPE_COMPATIBLE_BASE_URL,
            model = DASHSCOPE_DEFAULT_CHAT_MODEL,
        )
    }
}

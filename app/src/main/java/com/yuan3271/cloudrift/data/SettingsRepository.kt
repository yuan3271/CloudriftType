package com.yuan3271.cloudrift.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Settings live in their own preferences file so that they can be excluded from
 * Auto Backup independently of the rest of the app.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    val current: AppSettings get() = _state.value

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value)
        if (next == _state.value) return
        write(next)
        _state.value = next
    }

    private fun read(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            themeMode = ThemeMode.fromKey(prefs.getString(KEY_THEME_MODE, null)),
            themeSource = ThemeSource.fromKey(prefs.getString(KEY_THEME_SOURCE, null)),
            accentHue = prefs.getInt(KEY_ACCENT_HUE, defaults.accentHue),
            accentSaturation = prefs.getInt(KEY_ACCENT_SATURATION, defaults.accentSaturation),
            hapticFeedback = prefs.getBoolean(KEY_HAPTIC, defaults.hapticFeedback),
            soundFeedback = prefs.getBoolean(KEY_SOUND, defaults.soundFeedback),
            keyCornerRadiusDp = prefs.getInt(KEY_KEY_RADIUS, defaults.keyCornerRadiusDp),
            keyBackground = KeyBackground.fromKey(prefs.getString(KEY_KEY_BACKGROUND, null)),
            keyHeightDp = prefs.getInt(KEY_KEY_HEIGHT, defaults.keyHeightDp),
            bottomGapDp = prefs.getInt(KEY_BOTTOM_GAP, defaults.bottomGapDp),
            symbolWidth = SymbolWidth.fromKey(prefs.getString(KEY_SYMBOL_WIDTH, null)),
            landscapeFrame = KeyboardFrame.fromKey(prefs.getString(KEY_LANDSCAPE_FRAME, null)),
            floatingWidthPercent = prefs.getInt(KEY_FLOATING_WIDTH, defaults.floatingWidthPercent),
            floatingKeyHeightDp = prefs.getInt(KEY_FLOATING_KEY_HEIGHT, defaults.floatingKeyHeightDp),
            showNumberRow = prefs.getBoolean(KEY_NUMBER_ROW, defaults.showNumberRow),
            swipeUpSymbols = prefs.getBoolean(KEY_SWIPE_SYMBOLS, defaults.swipeUpSymbols),
            spaceCursorControl = prefs.getBoolean(KEY_SPACE_CURSOR, defaults.spaceCursorControl),
            learningEnabled = prefs.getBoolean(KEY_LEARNING, defaults.learningEnabled),
            japaneseEnabled = prefs.getBoolean(KEY_JAPANESE, defaults.japaneseEnabled),
            speech = migrateLegacyNls(readEndpoint(KEY_SPEECH_PREFIX, defaults.speech)),
            chat = readEndpoint(KEY_CHAT_PREFIX, defaults.chat),
            voiceCorrection = prefs.getBoolean(KEY_VOICE_CORRECTION, defaults.voiceCorrection),
            voiceAutoApplyDelayMs = prefs.getInt(
                KEY_VOICE_AUTO_APPLY,
                defaults.voiceAutoApplyDelayMs,
            ),
            autoPunctuation = prefs.getBoolean(KEY_AUTO_PUNCTUATION, defaults.autoPunctuation),
            hotWords = prefs.getString(KEY_HOT_WORDS, defaults.hotWords).orEmpty(),
            lastLayout = prefs.getString(KEY_LAST_LAYOUT, defaults.lastLayout).orEmpty(),
        )
    }

    private fun write(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_THEME_MODE, settings.themeMode.name)
            .putString(KEY_THEME_SOURCE, settings.themeSource.name)
            .putInt(KEY_ACCENT_HUE, settings.accentHue)
            .putInt(KEY_ACCENT_SATURATION, settings.accentSaturation)
            .putBoolean(KEY_HAPTIC, settings.hapticFeedback)
            .putBoolean(KEY_SOUND, settings.soundFeedback)
            .putInt(KEY_KEY_RADIUS, settings.keyCornerRadiusDp)
            .putString(KEY_KEY_BACKGROUND, settings.keyBackground.name)
            .putInt(KEY_KEY_HEIGHT, settings.keyHeightDp)
            .putInt(KEY_BOTTOM_GAP, settings.bottomGapDp)
            .putString(KEY_SYMBOL_WIDTH, settings.symbolWidth.name)
            .putString(KEY_LANDSCAPE_FRAME, settings.landscapeFrame.name)
            .putInt(KEY_FLOATING_WIDTH, settings.floatingWidthPercent)
            .putInt(KEY_FLOATING_KEY_HEIGHT, settings.floatingKeyHeightDp)
            .putBoolean(KEY_NUMBER_ROW, settings.showNumberRow)
            .putBoolean(KEY_SWIPE_SYMBOLS, settings.swipeUpSymbols)
            .putBoolean(KEY_SPACE_CURSOR, settings.spaceCursorControl)
            .putBoolean(KEY_LEARNING, settings.learningEnabled)
            .putBoolean(KEY_JAPANESE, settings.japaneseEnabled)
            .putString(KEY_SPEECH_PREFIX + KEY_SUFFIX_BASE_URL, settings.speech.baseUrl)
            .putString(KEY_SPEECH_PREFIX + KEY_SUFFIX_API_KEY, settings.speech.apiKey)
            .putString(KEY_SPEECH_PREFIX + KEY_SUFFIX_MODEL, settings.speech.model)
            .putString(KEY_SPEECH_PREFIX + KEY_SUFFIX_LANGUAGE, settings.speech.languageHint)
            .putString(KEY_SPEECH_PREFIX + KEY_SUFFIX_STYLE, settings.speech.style.name)
            .putString(KEY_CHAT_PREFIX + KEY_SUFFIX_BASE_URL, settings.chat.baseUrl)
            .putString(KEY_CHAT_PREFIX + KEY_SUFFIX_API_KEY, settings.chat.apiKey)
            .putString(KEY_CHAT_PREFIX + KEY_SUFFIX_MODEL, settings.chat.model)
            .putString(KEY_CHAT_PREFIX + KEY_SUFFIX_LANGUAGE, settings.chat.languageHint)
            .putString(KEY_CHAT_PREFIX + KEY_SUFFIX_STYLE, settings.chat.style.name)
            .putBoolean(KEY_VOICE_CORRECTION, settings.voiceCorrection)
            .putInt(KEY_VOICE_AUTO_APPLY, settings.voiceAutoApplyDelayMs)
            .putBoolean(KEY_AUTO_PUNCTUATION, settings.autoPunctuation)
            .putString(KEY_HOT_WORDS, settings.hotWords)
            .putString(KEY_LAST_LAYOUT, settings.lastLayout)
            .apply()
    }

    private fun readEndpoint(prefix: String, defaults: ApiEndpoint) = ApiEndpoint(
        baseUrl = prefs.getString(prefix + KEY_SUFFIX_BASE_URL, defaults.baseUrl).orEmpty(),
        apiKey = prefs.getString(prefix + KEY_SUFFIX_API_KEY, defaults.apiKey).orEmpty(),
        model = prefs.getString(prefix + KEY_SUFFIX_MODEL, defaults.model).orEmpty(),
        languageHint = prefs.getString(prefix + KEY_SUFFIX_LANGUAGE, defaults.languageHint).orEmpty(),
        style = ApiStyle.fromKey(prefs.getString(prefix + KEY_SUFFIX_STYLE, defaults.style.name)),
    )

    /**
     * Early builds shipped an 智能语音交互 (NLS) preset. That product needed an AppKey and a
     * token exchange; the 千问 multimodal endpoint only needs an API key, so a leftover NLS
     * gateway URL is rewritten instead of leaving the user with an endpoint that cannot work.
     */
    private fun migrateLegacyNls(endpoint: ApiEndpoint): ApiEndpoint =
        if (endpoint.baseUrl.startsWith("https://nls-gateway")) {
            endpoint.copy(
                baseUrl = AppSettings.QIANWEN_BASE_URL,
                style = ApiStyle.AliyunQianwen,
                model = AppSettings.QIANWEN_DEFAULT_SPEECH_MODEL,
            )
        } else {
            endpoint
        }

    companion object {
        const val FILE_NAME = "cloudrift_settings"

        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_THEME_SOURCE = "theme_source"
        private const val KEY_ACCENT_HUE = "accent_hue"
        private const val KEY_ACCENT_SATURATION = "accent_saturation"
        private const val KEY_HAPTIC = "haptic_feedback"
        private const val KEY_SOUND = "sound_feedback"
        private const val KEY_KEY_RADIUS = "key_corner_radius"
        private const val KEY_KEY_BACKGROUND = "key_background"
        private const val KEY_KEY_HEIGHT = "key_height"
        private const val KEY_BOTTOM_GAP = "bottom_gap"
        private const val KEY_SYMBOL_WIDTH = "symbol_width"
        private const val KEY_LANDSCAPE_FRAME = "landscape_frame"
        private const val KEY_FLOATING_WIDTH = "floating_width_percent"
        private const val KEY_FLOATING_KEY_HEIGHT = "floating_key_height"
        private const val KEY_NUMBER_ROW = "number_row"
        private const val KEY_SWIPE_SYMBOLS = "swipe_up_symbols"
        private const val KEY_SPACE_CURSOR = "space_cursor_control"
        private const val KEY_LEARNING = "learning_enabled"
        private const val KEY_JAPANESE = "japanese_enabled"
        private const val KEY_VOICE_CORRECTION = "voice_correction"
        private const val KEY_VOICE_AUTO_APPLY = "voice_auto_apply_delay_ms"
        private const val KEY_AUTO_PUNCTUATION = "auto_punctuation"
        private const val KEY_HOT_WORDS = "hot_words"
        private const val KEY_LAST_LAYOUT = "last_layout"

        private const val KEY_SPEECH_PREFIX = "speech_"
        private const val KEY_CHAT_PREFIX = "chat_"
        private const val KEY_SUFFIX_BASE_URL = "base_url"
        private const val KEY_SUFFIX_API_KEY = "api_key"
        private const val KEY_SUFFIX_MODEL = "model"
        private const val KEY_SUFFIX_LANGUAGE = "language"
        private const val KEY_SUFFIX_STYLE = "style"
    }
}

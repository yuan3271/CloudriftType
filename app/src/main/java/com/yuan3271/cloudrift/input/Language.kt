package com.yuan3271.cloudrift.input

import androidx.annotation.StringRes
import com.yuan3271.cloudrift.R
import com.yuan3271.cloudrift.engine.EngineKind

/**
 * The layouts the user can switch between. Each one maps to exactly one engine, which keeps
 * the controller free of language specific branching.
 */
enum class LayoutId(
    @StringRes val labelRes: Int,
    val engine: EngineKind,
    val numberOfRows: Int = 4,
) {
    Pinyin26(R.string.layout_pinyin26, EngineKind.Pinyin26),
    Pinyin9(R.string.layout_pinyin9, EngineKind.Pinyin9),
    JapaneseRomaji(R.string.layout_ja_romaji, EngineKind.Romaji),
    English(R.string.layout_english, EngineKind.Latin),
    ;

    val isChinese: Boolean get() = this == Pinyin26 || this == Pinyin9
    val isJapanese: Boolean get() = this == JapaneseRomaji

    /** The reading buffer only ever holds letters or digits, never both. */
    val usesDigits: Boolean get() = this == Pinyin9

    companion object {
        fun fromKey(key: String?): LayoutId? =
            entries.firstOrNull { it.name == key }

        /**
         * Layouts the language key cycles through, and the ones the layout picker offers. Japanese
         * is opt in: the key walks 中 ⇄ 英 by default, and 日 joins the rotation only when it has
         * been enabled in the settings.
         */
        fun cycleFor(japaneseEnabled: Boolean): List<LayoutId> = buildList {
            add(Pinyin26)
            add(English)
            if (japaneseEnabled) add(JapaneseRomaji)
        }

        /** Layouts the picker lists: both Chinese layouts plus whatever the user has enabled. */
        fun enabled(japaneseEnabled: Boolean): List<LayoutId> = buildList {
            add(Pinyin26)
            add(Pinyin9)
            add(English)
            if (japaneseEnabled) add(JapaneseRomaji)
        }
    }
}

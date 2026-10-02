package com.yuan3271.cloudrift.input

import com.yuan3271.cloudrift.engine.japanese.JapaneseEngine
import com.yuan3271.cloudrift.data.SymbolWidth

/** Which character set the canvas is showing. */
enum class KeyboardPage { Letters, Symbols, Numbers }

/**
 * Key tables for every layout.
 *
 * The layouts are data, not code paths: the controller walks the same rows for every
 * language and only the key definitions differ. Adding a language means adding a branch
 * here plus an engine.
 */
object KeyboardLayouts {

    fun rows(
        layout: LayoutId,
        page: KeyboardPage,
        shifted: Boolean,
        enterLabel: String,
        numberRow: Boolean = false,
    ): List<List<KeyDef>> = when (page) {
        // The symbol page is drawn as a scrollable bar (see SymbolPanel), so only its
        // function row is expressed as rows here.
        KeyboardPage.Symbols -> listOf(symbolFunctionRow(enterLabel))
        KeyboardPage.Numbers -> numberRows(enterLabel)
        KeyboardPage.Letters -> {
            val base = when (layout) {
                LayoutId.English -> qwertyRows(layout, shifted, enterLabel)
                LayoutId.Pinyin26 -> qwertyRows(layout, shifted, enterLabel)
                LayoutId.JapaneseRomaji -> qwertyRows(layout, shifted, enterLabel)
                LayoutId.Pinyin9 -> nineKeyRows()
            }
            if (numberRow && layout != LayoutId.Pinyin9) {
                listOf(numberRowKeys()) + base
            } else {
                base
            }
        }
    }

    /**
     * Key height used when the user has not pinned one. Shared with the settings previews so
     * what they show is what the keyboard does.
     */
    fun autoKeyHeight(screenHeightDp: Int): Float = (screenHeightDp * 0.072f).coerceIn(44f, 58f)

    /**
     * Sample row drawn by both appearance previews (the in-keyboard sheet and the full
     * settings screen): modifier, two glyph keys, space, delete and enter.
     */
    fun previewRow(enterLabel: String = "换行"): List<KeyDef> = listOf(
        KeyDef.shift,
        KeyDef.immediate("云"),
        KeyDef.immediate("隙"),
        KeyDef.space,
        KeyDef.backspace,
        KeyDef.enter.copy(label = enterLabel),
    )

    /** Optional strip of digits above the letter rows. */
    private fun numberRowKeys(): List<KeyDef> =
        "1234567890".map { KeyDef.immediate(it.toString()) }

    /** The key drawn where the language switch lives, e.g. 中 / En / 日. */
    fun languageLabel(layout: LayoutId): String = when (layout) {
        LayoutId.Pinyin26, LayoutId.Pinyin9 -> "中"
        LayoutId.English -> "En"
        LayoutId.JapaneseRomaji -> "日"
    }

    // ---- Latin style layouts ------------------------------------------------------

    private fun qwertyRows(
        layout: LayoutId,
        shifted: Boolean,
        enterLabel: String,
    ): List<List<KeyDef>> {
        val en = layout == LayoutId.English
        val (comma, period) = when {
            en -> "," to "."
            layout == LayoutId.JapaneseRomaji -> "、" to "。"
            else -> "，" to "。"
        }
        val top = "qwertyuiop".map { letter(it, shifted) }
        // Half a key of space on each side: the nine letter row keeps the same key width as the
        // ten letter row instead of being stretched, which is also how a real QWERTY is centred.
        val middle = buildList {
            add(KeyDef.spacer())
            addAll("asdfghjkl".map { letter(it, shifted) })
            add(KeyDef.spacer())
        }
        val bottom = buildList {
            if (en) add(KeyDef.shift) else add(KeyDef.modifier(KeyCode.Language, languageLabel(layout)))
            addAll("zxcvbnm".map { letter(it, shifted) })
            add(KeyDef.backspace)
        }
        // Ten units wide, like the letter rows, so every key on the page is the same size.
        val function = listOf(
            KeyDef.modifier(KeyCode.Symbols, if (en) "?123" else "符", weight = 1.3f),
            KeyDef.immediate(comma, weight = 0.9f),
            KeyDef.space.copy(weight = 5.1f),
            KeyDef.immediate(period, weight = 0.9f),
            KeyDef.enter.copy(label = enterLabel, weight = 1.8f),
        )
        return listOf(top, middle, bottom, function)
    }

    private fun letter(letter: Char, shifted: Boolean): KeyDef {
        val lower = letter.toString()
        val upper = letter.uppercaseChar().toString()
        return KeyDef.char(
            output = if (shifted) upper else lower,
            label = if (shifted) upper else lower,
            swipeUp = SWIPE_SYMBOLS[lower].orEmpty(),
        )
    }

    /** Long-press-free punctuation: swipe up on a letter to get the symbol above it. */
    private val SWIPE_SYMBOLS: Map<String, String> = mapOf(
        "q" to "1", "w" to "2", "e" to "3", "r" to "4", "t" to "5",
        "y" to "6", "u" to "7", "i" to "8", "o" to "9", "p" to "0",
        "a" to "@", "s" to "#", "d" to "¥", "f" to "&", "g" to "*",
        "h" to "-", "j" to "+", "k" to "(", "l" to ")",
        "z" to "/", "x" to "\\", "c" to "\"", "v" to "'", "b" to ":",
        "n" to ";", "m" to "!",
    )

    // ---- Chinese nine key ---------------------------------------------------------

    /**
     * The nine key pad is a grid, so every row carries the same four units: the digit keys stay
     * square and line up column by column instead of the function row stretching twice as wide as
     * the digits. The space bar still gets the extra width inside that budget.
     */
    private fun nineKeyRows(): List<List<KeyDef>> = listOf(
        listOf(
            digit("1", ""),
            digit("2", "ABC"),
            digit("3", "DEF"),
            KeyDef.backspace.copy(weight = 1f),
        ),
        listOf(
            digit("4", "GHI"),
            digit("5", "JKL"),
            digit("6", "MNO"),
            KeyDef.immediate("，"),
        ),
        listOf(
            digit("7", "PQRS"),
            digit("8", "TUV"),
            digit("9", "WXYZ"),
            KeyDef.immediate("。"),
        ),
        listOf(
            KeyDef.modifier(KeyCode.Symbols, "符", weight = 0.8f),
            KeyDef.modifier(KeyCode.Language, languageLabel(LayoutId.Pinyin9), weight = 0.8f),
            KeyDef.space.copy(label = "空格", weight = 1.4f),
            KeyDef.enter.copy(label = "换行", weight = 1f),
        ),
    )

    /**
     * Nine key pads are read by their letters, not their digits, so the letters are the main
     * label and the digit becomes a small corner badge.
     */
    private fun digit(value: String, letters: String) = KeyDef.char(
        output = value,
        label = letters.ifEmpty { value },
        badge = if (letters.isEmpty()) "" else value,
    )

    // ---- symbol and number pages --------------------------------------------------

    /**
     * Everything the scrollable symbol bar offers, in the order a Chinese typist reaches for
     * them: full width punctuation first, then quotes and brackets, then the ASCII forms, the
     * maths and currency sets and finally the arrows.
     */
    fun symbolBar(width: SymbolWidth): List<KeyDef> {
        val groups = when (width) {
            SymbolWidth.Full -> FULL_WIDTH_GROUPS
            SymbolWidth.Half -> HALF_WIDTH_GROUPS
        }
        // 去重是**必须**的，不是洁癖：符号页用的是 LazyVerticalGrid，items() 的 key 就是
        // 这个 output，重复 key 会在滚到那一项时抛 IllegalArgumentException——全角表里 ＄
        // 同时出现在货币行和符号行，于是"全角滑到底就闪退"，半角表没有重复所以没事。
        // 必须是**跨组**去重：按组去重挡不住这种一行一个的重复。
        return groups.joinToString("").toList().distinct().map { KeyDef.immediate(it.toString()) }
    }

    /** 全角：中文标点在前，随后是全角形式的 ASCII 与数学符号。 */
    private val FULL_WIDTH_GROUPS = listOf(
        "，。、？！；：…",
        "“”‘’「」『』",
        "（）【】《》〈〉",
        "—～·﹏〔〕〖〗",
        "＋－＝＊／＼＜＞",
        "＃＠＆％＄｜＾＿",
        "×÷≠≈±≤≥",
        "∞√‰°µ§¶",
        // 全角表里只放全角形式：￥ 是 U+FFE5、＄ 是 U+FF04；半角那两个（¥ U+00A5、$ U+0024）
        // 属于下一张表。以前这一行两个都放了，于是"切到全角"打出来的仍是半角符号。
        "￥＄€£₩¢₹",
        "†‡•®©™℃",
        "←→↑↓↔⇒⇔",
    )

    /** 半角：ASCII 标点与数学符号，中文标点不出现（那正是切到全角的理由）。 */
    private val HALF_WIDTH_GROUPS = listOf(
        ".,?!;:'\"",
        "()[]{}<>",
        "/\\|`~&@#",
        "+-=%*^_",
        "×÷≠≈±≤≥",
        "∞√‰°µ§¶",
        "¥$€£₩¢₹",
        "©®™†‡•℃",
        "←→↑↓↔⇒⇔",
    )

    /** Bottom row of the symbol bar: back to letters/numbers, space, delete, enter. */
    fun symbolFunctionRow(enterLabel: String): List<KeyDef> = listOf(
        KeyDef.modifier(KeyCode.Numbers, "123"),
        KeyDef.modifier(KeyCode.Letters, "ABC"),
        KeyDef.space.copy(label = "空格"),
        KeyDef.backspace,
        KeyDef.enter.copy(label = enterLabel),
    )

    /**
     * The number page is a phone dial pad with two side columns: the digits keep their letters
     * underneath (that is what makes it read as a dial pad), the left column carries the four
     * operations and the right column the keys a number field actually needs - delete, thousands
     * separator, decimal point and equals. Everything else lives in the scrollable symbol bar.
     */
    private fun numberRows(enterLabel: String): List<List<KeyDef>> = listOf(
        listOf(
            math("+"),
            dial("1", ""),
            dial("2", ""),
            dial("3", ""),
            // The right column is half a unit wider than the digits: it holds the editing keys,
            // and a delete that is the same size as a digit is a needlessly small target.
            KeyDef.backspace.copy(weight = WIDE),
        ),
        listOf(
            math("-"),
            dial("4", ""),
            dial("5", ""),
            dial("6", ""),
            math(",", weight = WIDE),
        ),
        listOf(
            math("×"),
            dial("7", ""),
            dial("8", ""),
            dial("9", ""),
            math(".", weight = WIDE),
        ),
        listOf(
            math("÷"),
            KeyDef.immediate("*"),
            dial("0", ""),
            KeyDef.immediate("#"),
            math("=", weight = WIDE),
        ),
        listOf(
            KeyDef.modifier(KeyCode.Symbols, "符", weight = 1.2f),
            KeyDef.modifier(KeyCode.Letters, "ABC", weight = 1.2f),
            KeyDef.space.copy(label = "空格", weight = 2.6f),
            KeyDef.enter.copy(label = enterLabel),
        ),
    )

    /**
     * A dial pad key is a big plain digit: no letters under it, no badge on it. The digit gets the
     * large label treatment so it keeps the size it had when the letters were there.
     */
    private fun dial(value: String, caption: String) = KeyDef.immediate(
        output = value,
        caption = caption,
        largeLabel = true,
    )

    /** Side column of the dial pad: nothing to convert, just a key that types a symbol. */
    private fun math(symbol: String, weight: Float = 1f) =
        KeyDef.immediate(symbol, style = KeyStyle.Modifier, weight = weight)

    /** Weight of the dial pad's right hand column. */
    private const val WIDE = 1.5f
}

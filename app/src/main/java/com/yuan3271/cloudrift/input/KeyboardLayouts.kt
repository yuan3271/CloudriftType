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
        // 数字页的第一列是一条滑动栏，由 NumberPanel 单独画；这里只给右边的四列。
        KeyboardPage.Numbers -> numberRows(enterLabel)
        KeyboardPage.Letters -> {
            val base = when (layout) {
                LayoutId.English -> qwertyRows(layout, shifted, enterLabel)
                LayoutId.Pinyin26 -> qwertyRows(layout, shifted, enterLabel)
                LayoutId.JapaneseRomaji -> qwertyRows(layout, shifted, enterLabel)
                LayoutId.Pinyin9 -> nineKeyRows(enterLabel)
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

    /**
     * 符号键上写的字。中文是「符」，英文布局是 `?123`——那是英文键盘上这个位置本来的写法。
     *
     * 26 键、9 键与键鼠兼容面板的工具栏都读这一处：同一个键在三个地方不能有三种写法。
     */
    fun symbolKeyLabel(layout: LayoutId): String =
        if (layout == LayoutId.English) "?123" else "符"

    /**
     * 语言的完整名字（状态行里用）。键位上那颗依旧写短名 [languageLabel]。
     *
     * 键鼠模式下**不报布局**（"中文 · 26 键拼音"里的"26 键"是给手指看的，物理键盘下没有意义），
     * 所以那一行只写语言。
     */
    fun languageName(layout: LayoutId): String = when (layout) {
        LayoutId.Pinyin26, LayoutId.Pinyin9 -> "中文"
        LayoutId.English -> "English"
        LayoutId.JapaneseRomaji -> "日本語"
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
            // 中文模式下 Z 旁边那颗是**大小写键**，不再是中英切换：拼音键盘上要打大写字母
            // （英文缩写、密码、英文单词）的场合比切语言多，而切语言工具栏上那颗 `中` 一直都在。
            // 英文本来就是 shift；日文罗马音沿用原来的语言键。
            when {
                layout == LayoutId.JapaneseRomaji ->
                    add(KeyDef.modifier(KeyCode.Language, languageLabel(layout)))
                else -> add(KeyDef.shift)
            }
            addAll("zxcvbnm".map { letter(it, shifted) })
            add(KeyDef.backspace)
        }
        // Ten units wide, like the letter rows, so every key on the page is the same size.
        val function = listOf(
            KeyDef.modifier(KeyCode.Symbols, symbolKeyLabel(layout), weight = 1.3f),
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
     * 九键：**五列**，数字占中间三列，左右各一列。
     *
     * 原来只有四列（三个数字 + 右边一列 `⌫ / ，/ 。`），数字那块的中心落在整行 37.5% 的地方，
     * 整块键盘看上去往左偏。现在数字三列被夹在正中间；底排的空格键正好铺在数字下面。每行都是
     * 五个单位（底排 1 + 3 + 1），所以列列对齐、换页不长高。
     *
     * 两列侧键按用户点名换过位：`，` `。` 走左边，`中` `换行` 走右边，`符` 与 `⌫` 留在第一行
     * 原来的两个角上（一个进符号页、一个退格，都是肌肉记忆最重的位置，不动）。
     */
    private fun nineKeyRows(enterLabel: String): List<List<KeyDef>> = listOf(
        listOf(
            KeyDef.modifier(KeyCode.Symbols, symbolKeyLabel(LayoutId.Pinyin9), weight = 1f),
            digit("1", ""),
            digit("2", "ABC"),
            digit("3", "DEF"),
            KeyDef.backspace.copy(weight = 1f),
        ),
        listOf(
            KeyDef.immediate("，"),
            digit("4", "GHI"),
            digit("5", "JKL"),
            digit("6", "MNO"),
            KeyDef.modifier(KeyCode.Language, languageLabel(LayoutId.Pinyin9), weight = 1f),
        ),
        listOf(
            KeyDef.immediate("。"),
            digit("7", "PQRS"),
            digit("8", "TUV"),
            digit("9", "WXYZ"),
            KeyDef.enter.copy(label = enterLabel, weight = 1f),
        ),
        listOf(
            KeyDef.spacer(1f),
            KeyDef.space.copy(label = "空格", weight = 3f),
            KeyDef.spacer(1f),
        ),
    )

    /**
     * Nine key pads are read by their letters, not their digits, so the letters stay the main
     * label and the digit is a small badge **above** them (see `BadgedLabel`). The badge used to
     * sit in the key's top start corner, which on a five column pad - where a key is only ~70dp
     * wide - ran straight into the centred `WXYZ`, and into `ABC` as soon as the font slider went
     * past 100%.
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
        // 成对键摆在最前面：一个键就出「开+闭」并把光标放中间。左括号键、右括号键仍然各自
        // 只出自己那一个——按 `（` 想要的是 `（`，不是 `（）`。
        val pairs = when (width) {
            SymbolWidth.Full -> FULL_WIDTH_PAIRS
            SymbolWidth.Half -> HALF_WIDTH_PAIRS
        }
        // 去重是**必须**的，不是洁癖：符号页用的是 LazyVerticalGrid，items() 的 key 就是
        // 这个 output，重复 key 会在滚到那一项时抛 IllegalArgumentException——全角表里 ＄
        // 同时出现在货币行和符号行，于是"全角滑到底就闪退"，半角表没有重复所以没事。
        // 必须是**跨组**去重：按组去重挡不住这种一行一个的重复。
        return pairs.map { KeyDef.immediate(it) } +
            groups.joinToString("").toList().distinct().map { KeyDef.immediate(it.toString()) }
    }

    /**
     * 成对键：一个键给出「开+闭」两个符号，光标落在中间（见 ImeController.PAIR_KEYS）。
     * 全角与半角各一份；单个的左右括号仍旧各按各的。
     */
    private val FULL_WIDTH_PAIRS = listOf("（）", "【】", "《》", "〈〉", "「」", "『』", "“”", "‘’", "〔〕", "〖〗")
    private val HALF_WIDTH_PAIRS = listOf("()", "[]", "{}", "<>", "\"\"", "''")

    /** 全角：中文标点在前，随后是全角形式的 ASCII 与数学符号。 */
    private val FULL_WIDTH_GROUPS = listOf(
        "，。、？！；：…",
        "“”‘’「」『』",
        "（）【】《》〈〉",
        "—～·﹏〔〕〖〗",
        "＋－＝＊／＼＜＞",
        "＃＠＆％＄｜＾＿",
        // 全角形式的非 ASCII 符号：¬ ¯ ¦ 与四种货币。Unicode 只在 U+FFE0–FFE6 里给它们准备了
        // 全角码位（￢￣￤￠￡￦ 与 ￥）。以前这一行写的是半角的 £ ₩ ¢——"切到全角"打出来仍是
        // 半角，和当年 $/¥ 是同一类错，只是那条测试只挡 ASCII，没抓住它们。
        "￢￣￤￥￠￡￦",
        // 没有全角形式的两个西欧货币，两种宽度共用同一个字符。
        "€₹",
        "×÷≠≈±≤≥",
        "∞√‰°µ§¶",
        "†‡•®©™℃",
        "←→↑↓↔⇒⇔",
    )

    /** 半角：ASCII 标点与数学符号，中文标点不出现（那正是切到全角的理由）。 */
    private val HALF_WIDTH_GROUPS = listOf(
        ".,?!;:'\"",
        "()[]{}<>",
        "/\\|`~&@#",
        "+-=%*^_",
        // ￢ ￣ ￤ 的半角原形。
        "¬¯¦",
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
     * 数字页右边那四列。数字页总共**四行**——和 26 键一样高，换到数字页键盘不长高。
     *
     * ```
     * ⎡滑⎤  1   2   3   ⌫     第一列是一条**可以滑动的竖条**（[mathStrip]），左下角
     * ⎢动⎥  4   5   6   =     固定一颗 `符`（[numberSymbolKey]）。
     * ⎢栏⎥  7   8   9   ⏎
     * ⎣符⎦  00  0   .   ABC
     * ```
     *
     * 退格在最顶上（用户要求：它用得最多，别贴在角落），等号与回车各往下让一格。
     */
    fun numberRows(enterLabel: String): List<List<KeyDef>> = listOf(
        listOf(digit("1"), digit("2"), digit("3"), KeyDef.backspace.copy(weight = COLUMN)),
        listOf(digit("4"), digit("5"), digit("6"), math("=")),
        listOf(digit("7"), digit("8"), digit("9"), KeyDef.enter.copy(label = enterLabel, weight = COLUMN)),
        // `00` 在 `0` 左边（用户点名换位）。
        listOf(digit("00"), digit("0"), digit("."), KeyDef.modifier(KeyCode.Letters, "ABC", weight = COLUMN)),
    )

    /**
     * 第一列那条竖条里的算术符号，从上到下。一次看得见三个（竖条占三行，第四行留给 `符`），
     * 手指往下滑就把根号、百分号这些滑出来——用户要的是"滑动"，不是"划一下换一组"。
     */
    fun mathStrip(): List<KeyDef> = listOf(
        math("+"),
        math("-"),
        math("×"),
        math("÷"),
        math("√"),
        math("%"),
        math("^"),
        math("("),
        math(")"),
    )

    /** 数字页左下角固定的 `符`：滑条占三行，它占第四行，所以整页还是四行。 */
    fun numberSymbolKey(): KeyDef = KeyDef.modifier(KeyCode.Symbols, "符", weight = COLUMN)

    /**
     * 数字键。有意做得和 26 键的字母键**一模一样**：同样的圆角（由设置决定）、同样的字号
     * （titleMedium）、同样的 Primary 底色——以前那套"拨号盘"的大字与胶囊圆角已经去掉。
     */
    private fun digit(value: String) = KeyDef.immediate(output = value, weight = COLUMN)

    /** 算术符号键：只输入那个符号，深浅交给 Modifier 样式（与 26 键的 符 / 退格同款）。 */
    private fun math(symbol: String) =
        KeyDef.immediate(symbol, style = KeyStyle.Modifier, weight = COLUMN)

    /** 数字页四行五列（含左边那条滑动栏），每列一个单位宽。 */
    private const val COLUMN = 1f
}

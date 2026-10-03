package com.yuan3271.cloudrift.input

import com.yuan3271.cloudrift.data.SymbolWidth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.roundToInt
import org.junit.Test

/**
 * Locks the two layout rules that are easy to break by adding one key: rows of a letter page all
 * carry the same number of units (otherwise the keys in one row are a different width than the
 * keys in the next), and the nine letter row is padded so it stays centred instead of stretching.
 */
class KeyboardLayoutTest {

    private fun rows(layout: LayoutId) = KeyboardLayouts.rows(
        layout = layout,
        page = KeyboardPage.Letters,
        shifted = false,
        enterLabel = "换行",
    )

    /** Rounded, because adding Floats (1.3 + 0.9 + …) lands on 9.9999998 rather than 10. */
    private fun totals(rows: List<List<KeyDef>>) = rows.map { row ->
        (row.sumOf { it.weight.toDouble() } * 100).roundToInt() / 100.0
    }

    @Test
    fun `every row of a qwerty page is ten units wide`() {
        for (layout in listOf(LayoutId.Pinyin26, LayoutId.English, LayoutId.JapaneseRomaji)) {
            val totals = totals(rows(layout)).distinct()

            assertEquals("$layout rows differ: $totals", listOf(10.0), totals)
        }
    }

    @Test
    fun `the nine letter row is padded on both sides`() {
        for (layout in listOf(LayoutId.Pinyin26, LayoutId.English, LayoutId.JapaneseRomaji)) {
            val middle = rows(layout)[1]

            assertEquals(KeyCode.None, middle.first().code)
            assertEquals(KeyCode.None, middle.last().code)
            assertEquals(9, middle.count { it.code != KeyCode.None })
        }
    }

    @Test
    fun `the numeric pages have equal keys in their key rows`() {
        // The nine key pad is a grid: all four rows share one width, digits included.
        val nineKey = rows(LayoutId.Pinyin9).map { row -> row.sumOf { it.weight.toDouble() } }
        val dial = numberRows().map { row -> row.sumOf { it.weight.toDouble() } }

        assertEquals(1, nineKey.distinct().size)
        assertEquals(1, dial.distinct().size)
    }

    /**
     * 九键的主输入区必须落在正中间。
     *
     * 老布局是四列（三个数字 + 右边一列 `⌫/，/。`），数字那块的中心落在整行的 37.5%，
     * 整块键盘看上去往左偏——这是用户点名的那件事。现在数字占中间三列、左右各一列，
     * 底排的空格正好铺在数字下面。右边那一列的位置一动不动（用户的肌肉记忆在那里）。
     */
    @Test
    fun `the nine key digits sit in the middle with a rail on each side`() {
        val grid = rows(LayoutId.Pinyin9)

        assertEquals(4, grid.size)
        for (row in grid) {
            assertEquals(
                "每行都是五个单位: ${row.map { it.weight }}",
                5.0,
                (row.sumOf { it.weight.toDouble() } * 100).roundToInt() / 100.0,
                0.0,
            )
        }
        // 前三行：左右各一颗（左右等宽），中间三颗数字。
        for (row in grid.take(3)) {
            assertEquals(5, row.size)
            assertEquals("两边要一样宽: ${row.map { it.weight }}", row.first().weight, row.last().weight, 0f)
            assertEquals(3, row.subList(1, 4).count { it.code == KeyCode.Char })
        }
        // 右边一列没动：⌫ 仍在右上角，`，` 与 `。` 跟着它往下排。
        assertEquals(KeyCode.Backspace, grid[0].last().code)
        assertEquals("，", grid[1].last().output)
        assertEquals("。", grid[2].last().output)
        // 左边补出来的一列是功能键。
        assertEquals(KeyCode.Symbols, grid[0].first().code)
        assertEquals(KeyCode.Language, grid[1].first().code)
        assertEquals(KeyCode.Enter, grid[2].first().code)
        // 空格键铺在数字那三列下面。
        assertEquals(listOf(KeyCode.None, KeyCode.Space, KeyCode.None), grid[3].map { it.code })
        assertEquals(3f, grid[3][1].weight)
    }

    @Test
    fun `the number page is four rows of four keys plus the sliding strip`() {
        val grid = numberRows()

        assertEquals(4, grid.size)
        assertTrue("每一行都该是四列: ${grid.map { it.size }}", grid.all { it.size == 4 })
        // 每列一个单位宽：数字键和右边那列功能键一样宽。
        assertTrue(grid.all { row -> row.all { it.weight == 1f } })
        // 三列数字：前三行 1–9，末行 0 / . / 00。
        assertEquals(listOf("1", "2", "3"), grid[0].take(3).map { it.output })
        assertEquals(listOf("4", "5", "6"), grid[1].take(3).map { it.output })
        assertEquals(listOf("7", "8", "9"), grid[2].take(3).map { it.output })
        // `00` 在 `0` 左边（用户点名换位）。
        assertEquals(listOf("00", "0", "."), grid[3].take(3).map { it.output })
    }

    /**
     * 中文（26 键）的 `Z` 左边那颗是**大小写键**，不是中英切换：切语言工具栏上那颗 `中` 一直在，
     * 而拼音键盘上要打大写字母的场合更常见。英文本来就是 shift，日文罗马音保留语言键。
     */
    @Test
    fun `the key next to z is shift in chinese and english, language in japanese`() {
        assertEquals(KeyCode.Shift, rows(LayoutId.Pinyin26)[2].first().code)
        assertEquals(KeyCode.Shift, rows(LayoutId.English)[2].first().code)
        assertEquals(KeyCode.Language, rows(LayoutId.JapaneseRomaji)[2].first().code)
        // 换掉这一颗不动行宽：shift 与语言键都是 1.5 单位。
        assertEquals(listOf(10.0), totals(rows(LayoutId.Pinyin26)).distinct())
    }

    @Test
    fun `the right column puts delete on top and letters at the bottom`() {
        val right = numberRows().map { it[3] }

        assertEquals(KeyCode.Backspace, right[0].code)
        assertEquals("=", right[1].output)
        assertEquals(KeyCode.Enter, right[2].code)
        assertEquals(KeyCode.Letters, right[3].code)
        // 等号只输入一个等号：数字页不做计算。
        assertEquals(KeyCode.Text, right[1].code)
    }

    /**
     * 左边那条竖条：`+ − × ÷` 在最上面，往下滑是根号、百分号这些地方；左下角固定一颗 `符`。
     * 竖条 + `符` = 四行，和 26 键一样高——键盘高度不能长，这条测试就是钉住这件事。
     */
    @Test
    fun `the sliding strip starts with the four operations`() {
        val strip = KeyboardLayouts.mathStrip()

        assertEquals(listOf("+", "-", "×", "÷"), strip.take(4).map { it.output })
        assertEquals(listOf("√", "%", "^", "(", ")"), strip.drop(4).map { it.output })
        assertEquals(KeyCode.Symbols, KeyboardLayouts.numberSymbolKey().code)
    }

    /**
     * 数字页画出来就该长这样。左边那条竖条只画出最上面三个（其余要滑），左下角是 `符`；
     * 右边四行四列。
     *
     * 这条同时钉住用户点名的两件事：**四行**（和 26 键一样高，换页不长高）与**五列**
     * （竖条一列 + 右边四列）。
     */
    @Test
    fun `the number page reads as four rows of five columns`() {
        val strip = KeyboardLayouts.mathStrip().take(3).map { keyLabel(it) } +
            keyLabel(KeyboardLayouts.numberSymbolKey())
        val picture = numberRows().mapIndexed { index, row ->
            (listOf(strip[index]) + row.map { keyLabel(it) })
                .joinToString(" ") { it.padStart(5) }
        }
            .joinToString("\n")

        assertEquals(
            """
            |    +     1     2     3     ⌫
            |    -     4     5     6     =
            |    ×     7     8     9    换行
            |    符    00     0     .   ABC
            """.trimMargin().trimEnd(),
            picture,
        )
    }

    /** 只把键上的字拿出来，退格这种画图标的键用一个方块字代表。 */
    private fun keyLabel(key: KeyDef) = when (key.code) {
        KeyCode.Backspace -> "⌫"
        else -> key.display.ifEmpty { "?" }
    }

    private fun numberRows() = KeyboardLayouts.rows(
        layout = LayoutId.Pinyin26,
        page = KeyboardPage.Numbers,
        shifted = false,
        enterLabel = "换行",
    )

    /**
     * 全角表里不许出现半角字符，半角表里不许出现全角字符。
     *
     * 这条是被用户投诉"符号的半角和全角搞混"之后加上的：全角那一行货币里同时有 ￥(U+FFE5) 和
     * 半角的 ¥(U+00A5)、$(U+0024)，于是"切到全角"打出来的仍是半角符号。两张表都还允许
     * **没有全/半之别的符号**（×÷≤≈ 箭头 °µ§¶ 这些），它们没有另一个宽度版本，不算混淆。
     */
    @Test
    fun `full width and half width symbol tables do not mix`() {
        val full = KeyboardLayouts.symbolBar(SymbolWidth.Full)
        val half = KeyboardLayouts.symbolBar(SymbolWidth.Half)

        val asciiInFull = full.filter { it.output.length == 1 && it.output[0].code in 0x21..0x7e }
        val wideInHalf = half.filter { it.output.length == 1 && it.output[0].code in 0xff00..0xffef }

        assertTrue("全角表里出现了半角字符: ${asciiInFull.map { it.output }}", asciiInFull.isEmpty())
        assertTrue("半角表里出现了全角字符: ${wideInHalf.map { it.output }}", wideInHalf.isEmpty())

        // 有全角形式的字符**逐个**对账，而不是只查 ASCII：¥/¢/£/₩/¬/¯/¦ 都是非 ASCII，
        // 它们混进全角表里用上面那两条宽度范围检查是查不出来的（当年 $/¥ 就是从这里漏的）。
        val forms = listOf(
            "$" to "＄",
            "¥" to "￥",
            "¢" to "￠",
            "£" to "￡",
            "₩" to "￦",
            "¬" to "￢",
            "¯" to "￣",
            "¦" to "￤",
        )
        for ((halfForm, fullForm) in forms) {
            assertTrue("全角表缺 $fullForm", full.any { it.output == fullForm })
            assertTrue("半角表缺 $halfForm", half.any { it.output == halfForm })
            assertTrue("全角表里混进了半角的 $halfForm", full.none { it.output == halfForm })
            assertTrue("半角表里混进了全角的 $fullForm", half.none { it.output == fullForm })
        }

        // 成对键也要逐个对账。上面那两条只查 `output.length == 1`，于是**两字键整个漏在网外**——
        // 用户报的"全角左侧组合是半角"正是这个位置：一对里哪怕只有一个字符是半角，两字键都查不出来。
        val asciiInFullPairs = full.filter { it.output.length == 2 }
            .flatMap { it.output.toList() }
            .filter { it.code in 0x21..0x7e }
        val wideInHalfPairs = half.filter { it.output.length == 2 }
            .flatMap { it.output.toList() }
            .filter { it.code in 0xff00..0xffef }
        assertTrue("全角成对键里出现了半角字符: ${asciiInFullPairs.map { it.toString() }}", asciiInFullPairs.isEmpty())
        assertTrue("半角成对键里出现了全角字符: ${wideInHalfPairs.map { it.toString() }}", wideInHalfPairs.isEmpty())
    }

    /**
     * 成对键本身：全角一排、半角一排，开闭成对，一字不差。第一对就是 `（）`——用户点名的那一颗。
     */
    @Test
    fun `the pair keys are the paired forms of both widths`() {
        val full = KeyboardLayouts.symbolBar(SymbolWidth.Full).filter { it.output.length == 2 }.map { it.output }
        val half = KeyboardLayouts.symbolBar(SymbolWidth.Half).filter { it.output.length == 2 }.map { it.output }

        assertEquals(
            listOf("（）", "【】", "《》", "〈〉", "「」", "『』", "“”", "‘’", "〔〕", "〖〗"),
            full,
        )
        assertEquals(listOf("()", "[]", "{}", "<>", "\"\"", "''"), half)
        // 两字键在两套表里都摆在最前面（用户伸手就够得到的地方）。
        assertEquals(full, KeyboardLayouts.symbolBar(SymbolWidth.Full).map { it.output }.take(full.size))
        assertEquals(half, KeyboardLayouts.symbolBar(SymbolWidth.Half).map { it.output }.take(half.size))
    }

    /**
     * 符号表里不许有重复的键。
     *
     * 符号页是 LazyVerticalGrid，key 就是 output：重复 key 让它在那一项被合成时抛
     * IllegalArgumentException —— 表现是"全角模式滑到底就闪退"（￥ 曾经同时出现在货币行和
     * 符号行，半角表没有重复所以没事）。这条测试就是那次崩溃留下的钉子。
     */
    @Test
    fun `the symbol bar never repeats a key`() {
        for (width in listOf(SymbolWidth.Full, SymbolWidth.Half)) {
            val outputs = KeyboardLayouts.symbolBar(width).map { it.output }
            val duplicated = outputs.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
            assertTrue("$width 的符号表里有重复的键: $duplicated", duplicated.isEmpty())
        }
    }
}

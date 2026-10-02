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

    @Test
    fun `the number page is a four by five grid`() {
        val grid = numberRows().take(4)

        assertEquals(4, grid.size)
        assertTrue("每一行都该是五列: ${grid.map { it.size }}", grid.all { it.size == 5 })
        // 五列就是五列：数字键和两侧的功能键一样宽。
        assertTrue(grid.all { row -> row.all { it.weight == 1f } })
        // 中间三列是数字键盘，前三行 1–9，末行 0 / . / 00。
        assertEquals(listOf("1", "2", "3"), grid[0].drop(1).take(3).map { it.output })
        assertEquals(listOf("4", "5", "6"), grid[1].drop(1).take(3).map { it.output })
        assertEquals(listOf("7", "8", "9"), grid[2].drop(1).take(3).map { it.output })
        assertEquals(listOf("0", ".", "00"), grid[3].drop(1).take(3).map { it.output })
    }

    @Test
    fun `the right column is equals minus enter and calculate`() {
        val right = numberRows().take(4).map { it[4] }

        assertEquals("=", right[0].output)
        assertEquals("-", right[1].output)
        assertEquals(KeyCode.Enter, right[2].code)
        assertEquals(KeyCode.Calculate, right[3].code)
        // 等号按要求只输入一个等号，不做计算。
        assertEquals(KeyCode.Text, right[0].code)
    }

    /**
     * 第一列是一个可以上下滑动的窗口：四个一组，环绕，滑不到头。
     *
     * 这条锁住的是用户要的手感——`+ − × ÷` 是开场那一组，`√ % ^ ( )` 滑一下就能用，
     * 而且往回滑一定回得到开头。
     */
    @Test
    fun `the maths column slides through its symbols and wraps around`() {
        val symbols = KeyboardLayouts.mathSymbols
        val first = KeyboardLayouts.mathWindow(0)

        assertEquals(listOf("+", "-", "×", "÷"), first)
        assertEquals(listOf("-", "×", "÷", "√"), KeyboardLayouts.mathWindow(1))
        // 环绕：滑到最后再往前一步就回到开头。
        assertEquals(first, KeyboardLayouts.mathWindow(symbols.size))
        assertEquals(listOf(")", "+", "-", "×"), KeyboardLayouts.mathWindow(symbols.size - 1))
        // 往下滑一步等于往回滑一步的逆运算。
        assertEquals(KeyboardLayouts.mathWindow(0), KeyboardLayouts.mathWindow(symbols.size - 1 + 1))

        // 数字页每次画出来的第一列，就是那个窗口。
        assertEquals(first, numberRows().take(4).map { it[0].output })
        assertEquals(listOf("√", "%", "^", "("), numberRows(offset = 4).take(4).map { it[0].output })
        // 第一列的键纵划换组，所以都带着动作名。
        assertTrue(numberRows().take(4).all { it[0].action == KeyboardLayouts.MATH_SWIPE_ACTION })
    }

    /**
     * 数字页画出来就该长这样。这条是给"四行五列"这句需求留的钉子：列错了、键跑到别的行上，
     * 这段 ASCII 会先变，而不是等到真机上才发现。
     */
    @Test
    fun `the number page reads as a four by five keypad`() {
        val picture = numberRows().joinToString("\n") { row ->
            row.joinToString(" ") { key -> keyLabel(key).padStart(5) }
        }

        assertEquals(
            """
            |    +     1     2     3     =
            |    -     4     5     6     -
            |    ×     7     8     9    换行
            |    ÷     0     .    00    计算
            |    符   ABC     ⌫    空格
            """.trimMargin().trimEnd(),
            picture,
        )
    }

    /** 只把键上的字拿出来，退格这种画图标的键用一个方块字代表。 */
    private fun keyLabel(key: KeyDef) = when (key.code) {
        KeyCode.Backspace -> "⌫"
        else -> key.display.ifEmpty { "?" }
    }

    private fun numberRows(offset: Int = 0) = KeyboardLayouts.rows(
        layout = LayoutId.Pinyin26,
        page = KeyboardPage.Numbers,
        shifted = false,
        enterLabel = "换行",
        mathOffset = offset,
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

        // 这一对是当年真的搞混了的：￥/＄ 是全角，¥/$ 是半角。
        assertTrue(full.any { it.output == "＄" } && full.any { it.output == "￥" })
        assertTrue(half.any { it.output == "$" } && half.any { it.output == "¥" })
        assertTrue("全角表不该有半角美元", full.none { it.output == "$" })
        assertTrue("半角表不该有全角日元", half.none { it.output == "￥" })
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

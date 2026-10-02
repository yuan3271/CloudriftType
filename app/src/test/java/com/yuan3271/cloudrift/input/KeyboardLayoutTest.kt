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
        val dial = KeyboardLayouts
            .rows(LayoutId.Pinyin26, KeyboardPage.Numbers, shifted = false, enterLabel = "换行")
            .take(4)
            .map { row -> row.sumOf { it.weight.toDouble() } }

        assertEquals(1, nineKey.distinct().size)
        assertEquals(1, dial.distinct().size)
    }

    @Test
    fun `the dial pad is plain digits with a wide delete`() {
        val dial = KeyboardLayouts.rows(
            LayoutId.Pinyin26,
            KeyboardPage.Numbers,
            shifted = false,
            enterLabel = "换行",
        )
        val keys = dial.flatten()
        val delete = keys.first { it.code == KeyCode.Backspace }
        val digit = keys.first { it.output == "2" }

        assertEquals("", digit.caption)
        assertTrue("the delete key should be wider than a digit", delete.weight > digit.weight)
        assertTrue("the left column should offer arithmetic", keys.any { it.output == "÷" })
    }

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
}

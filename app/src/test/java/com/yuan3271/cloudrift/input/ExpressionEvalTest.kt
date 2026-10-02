package com.yuan3271.cloudrift.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 数字页 `计算` 键的规则。这些断言回答的是"用户按下去会看到什么"，而不是"函数返回了什么"：
 * 12+3×4 必须得 14（先乘后加），`1/3` 不许印出一串 0.3333333333333333，
 * 而 `2024` 这种只是个数字的东西**不许**被替换掉——按错一个键不该毁掉用户写的东西。
 */
class ExpressionEvalTest {

    @Test
    fun `multiplication binds tighter than addition`() {
        assertEquals(14.0, ExpressionEval.evaluate("2+3*4"))
        assertEquals(20.0, ExpressionEval.evaluate("(2+3)*4"))
        assertEquals(9.0, ExpressionEval.evaluate("3+2×3"))
        assertEquals(5.0, ExpressionEval.evaluate("10÷2"))
    }

    @Test
    fun `unary sign power root and percent`() {
        assertEquals(-10.0, ExpressionEval.evaluate("10-20"))
        assertEquals(1024.0, ExpressionEval.evaluate("2^10"))
        assertEquals(3.0, ExpressionEval.evaluate("√9"))
        assertEquals(0.5, ExpressionEval.evaluate("50%"))
        assertEquals(10.5, ExpressionEval.evaluate("50%+10"))
        // 负号不是幂的一部分：-2^10 是 -(2^10)。
        assertEquals(-1024.0, ExpressionEval.evaluate("-2^10"))
    }

    @Test
    fun `full width symbols from the symbol page are accepted`() {
        assertEquals(15.0, ExpressionEval.evaluate("１２＋３"))
        assertEquals(6.0, ExpressionEval.evaluate("２×３"))
    }

    @Test
    fun `an impossible sum is refused instead of guessed`() {
        assertNull(ExpressionEval.evaluate("1/0"))
        assertNull(ExpressionEval.evaluate("√-4"))
        assertNull(ExpressionEval.evaluate("2+"))
        assertNull(ExpressionEval.evaluate("(1+2"))
        assertNull(ExpressionEval.evaluate("1 2"))
    }

    @Test
    fun `a bare number is not an expression`() {
        // 按在 "2024" 上应该什么都不做，而不是把年份原样替换成年份。
        assertNull(ExpressionEval.expression("2024"))
        assertEquals("2024+1", ExpressionEval.expression("2024+1"))
        assertNull(ExpressionEval.expression(""))
        assertNull(ExpressionEval.expression("你好"))
    }

    @Test
    fun `only the tail of the line is taken as the expression`() {
        assertEquals("12+3×4", ExpressionEval.trailingExpression("合计 12+3×4"))
        assertEquals("3.5×2", ExpressionEval.trailingExpression("3.5×2"))
        assertNull(ExpressionEval.trailingExpression("你好"))
        // 算式前面的正文一个字都不能被卷进来。
        assertNull(ExpressionEval.trailingExpression("今天买了两斤苹果"))
    }

    @Test
    fun `results are printed the way a person would write them`() {
        assertEquals("14", ExpressionEval.format(14.0))
        assertEquals("0.333333", ExpressionEval.format(1.0 / 3.0))
        assertEquals("-10", ExpressionEval.format(-10.0))
        assertEquals("0", ExpressionEval.format(0.0))
        assertEquals("2.5", ExpressionEval.format(2.5))
    }
}

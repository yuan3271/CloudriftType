package com.yuan3271.cloudrift.input

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.sqrt

/**
 * 数字页 `计算` 键背后的算式求值。
 *
 * 刻意做成纯函数：不碰编辑器、不碰键盘状态，于是"2+3×4 得 14"这类规则可以在单元测试里钉死，
 * 不用上真机按一遍才知道错在哪。
 *
 * 支持 `+ - * / ^ ( )` 与 `√` 前缀、`%` 后缀（百分号：`50%` = 0.5，不是取模）。全角的
 * 数字与符号先归一化，因为符号页里两种宽度都拿得到。
 */
object ExpressionEval {

    /** 结果最多留几位小数；再多就不是"键盘上算一下"该有的精度了。 */
    private const val MAX_SCALE = 6

    /** 归一化之后允许出现在算式里的字符。 */
    private const val ALLOWED = "0123456789.+-*/^%√() "

    /**
     * 光标左边末尾那段连续算式，例如 `"合计 12+3×4"` → `"12+3×4"`；不像算式时返回 null。
     *
     * 用"连续一段"而不是"整行"：整行会把前面的正文一起吃进来，算式求值失败是小事，
     * 把用户写的话删掉才是大事。
     */
    fun trailingExpression(before: String): String? {
        var start = before.length
        while (start > 0 && isExpressionChar(before[start - 1])) start--
        return expression(before.substring(start))
    }

    /** 整段都当算式来算（选中的文字走这条路）；混了别的字就返回 null。 */
    fun expression(text: String): String? {
        // 归一化只用来**判断**；返回原文，这样提示语里引用的是用户自己写的那串字符，
        // 而且长度与原文一致（归一化是一个字符换一个字符），删起来位置对得上。
        val trimmed = text.trim()
        val normalized = normalize(trimmed)
        if (normalized.isEmpty()) return null
        if (normalized.any { it !in ALLOWED }) return null
        if (normalized.none { it.isDigit() }) return null
        // 光一个数字不算"算式"：`计算` 键按在 "2024" 上应该什么都不做，
        // 而不是把年份原样替换成年份。
        if (normalized.none { it in "+-*/^%√" }) return null
        return trimmed
    }

    /** 求值；算式不合语法（或除零、负数开方）时返回 null，调用方据此给用户一句话。 */
    fun evaluate(expression: String): Double? = try {
        val parser = Parser(normalize(expression))
        val value = parser.parseExpression()
        parser.expectEnd()
        value.takeIf { it.isFinite() }
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: ArithmeticException) {
        null
    }

    /** 5.0 → `5`，1/3 → `0.333333`；整数值不带小数点。 */
    fun format(value: Double): String {
        if (value == 0.0) return "0"
        val rounded = BigDecimal.valueOf(value)
            .setScale(MAX_SCALE, RoundingMode.HALF_UP)
            .stripTrailingZeros()
        return rounded.toPlainString()
    }

    private fun isExpressionChar(c: Char): Boolean = normalize(c) in ALLOWED

    private fun normalize(text: String): String = buildString(text.length) {
        for (c in text) append(normalize(c))
    }

    /**
     * 全角数字与全角运算符归到半角，乘号除号归到 `*` `/`；其余原样。
     *
     * 归一化是**一个字符换一个字符**，所以算式的长度不变——替换时用原始长度去删，
     * 位置才对得上。
     */
    private fun normalize(c: Char): Char = when (c) {
        in '０'..'９' -> '0' + (c - '０')
        '．' -> '.'
        '＋' -> '+'
        '－', '−', '—', '–' -> '-'
        '×', '✕' -> '*'
        '÷' -> '/'
        '＊' -> '*'
        '／' -> '/'
        '（' -> '('
        '）' -> ')'
        '％' -> '%'
        '　' -> ' '
        else -> c
    }

    /** 递归下降：加减 < 乘除 < 乘方 < 一元正负与根号 < 数字、括号、百分号。 */
    private class Parser(private val text: String) {
        private var pos = 0

        fun parseExpression(): Double {
            var value = parseTerm()
            while (true) {
                value = when (peek()) {
                    '+' -> { pos++; value + parseTerm() }
                    '-' -> { pos++; value - parseTerm() }
                    else -> return value
                }
            }
        }

        private fun parseTerm(): Double {
            var value = parseUnary()
            while (true) {
                value = when (peek()) {
                    '*' -> { pos++; value * parseUnary() }
                    '/' -> {
                        pos++
                        val divisor = parseUnary()
                        if (divisor == 0.0) throw ArithmeticException("÷0")
                        value / divisor
                    }
                    else -> return value
                }
            }
        }

        private fun parseUnary(): Double = when (peek()) {
            '+' -> { pos++; parseUnary() }
            '-' -> { pos++; -parseUnary() }
            '√' -> {
                pos++
                val value = parseUnary()
                if (value < 0) throw ArithmeticException("负数开方")
                sqrt(value)
            }
            else -> parsePower()
        }

        private fun parsePower(): Double {
            val base = parseAtom()
            if (peek() == '^') {
                pos++
                return Math.pow(base, parseUnary())
            }
            return base
        }

        private fun parseAtom(): Double {
            if (peek() == '(') {
                pos++
                val value = parseExpression()
                if (peek() != ')') throw IllegalArgumentException("括号没关上")
                pos++
                return maybePercent(value)
            }
            val start = pos
            while (true) {
                val c = peek() ?: break
                if (c.isDigit() || c == '.') pos++ else break
            }
            if (pos == start) throw IllegalArgumentException("这里要一个数字")
            val number = text.substring(start, pos).toDoubleOrNull()
                ?: throw IllegalArgumentException("不是数字")
            return maybePercent(number)
        }

        /** `%` 是后缀百分号：紧跟在一个数（或右括号）后面才有意义。 */
        private fun maybePercent(value: Double): Double {
            if (peek() == '%') {
                pos++
                return value / 100
            }
            return value
        }

        fun expectEnd() {
            if (pos != text.length) throw IllegalArgumentException("后面还有没算的东西")
        }

        /** 跳过空格并看向下一个字符；到头返回 null。 */
        private fun peek(): Char? {
            while (pos < text.length && text[pos] == ' ') pos++
            return text.getOrNull(pos)
        }
    }
}

package com.yuan3271.cloudrift.input

import android.view.inputmethod.ExtractedText
import android.view.inputmethod.InputConnection
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 退格必须"在哪个应用里都删得动"：这一组测试用一个假编辑器分别模拟三种真实情况——
 * 只认按码点删的、只认老的按单元删的、两条接口都不认只认按键的——以及一个读不到文本的编辑器。
 */
class EditorProxyDeletionTest {

    @Test
    fun `a chinese character is deleted through the code point api`() {
        val host = FakeEditor(initialText = "你好")
        val editor = proxyFor(host)

        editor.deleteBeforeCaret()

        assertEquals("你", host.text)
        assertTrue("用按码点的接口删", host.calls.contains("deleteSurroundingTextInCodePoints"))
        assertEquals("不应该再补按键", 0, host.keyPresses)
    }

    @Test
    fun `an editor that only knows the legacy delete still loses the character`() {
        val host = FakeEditor(initialText = "你好", honorsCodePoints = false)
        val editor = proxyFor(host)

        editor.deleteBeforeCaret()

        assertEquals("你", host.text)
        assertTrue("退回按 UTF-16 单元删", host.calls.contains("deleteSurroundingText"))
        assertEquals("删成功了就不该再补按键", 0, host.keyPresses)
    }

    @Test
    fun `an editor that ignores every delete api gets a real backspace key`() {
        val host = FakeEditor(
            initialText = "你好",
            honorsCodePoints = false,
            honorsUnits = false,
        )
        val editor = proxyFor(host)

        editor.deleteBeforeCaret()

        assertEquals("两条接口都不吃时，只能发按键", 1, host.keyPresses)
    }

    @Test
    fun `an editor that will not describe its text gets a key press immediately`() {
        val host = FakeEditor(initialText = "你好", readable = false)
        val editor = proxyFor(host)

        editor.deleteBeforeCaret()

        assertEquals(1, host.keyPresses)
        assertTrue(
            "读不到文本就不该盲删",
            host.calls.none { it == "deleteSurroundingText" || it == "deleteSurroundingTextInCodePoints" },
        )
    }

    @Test
    fun `an emoji is not split in half`() {
        val host = FakeEditor(initialText = "a😀")
        val editor = proxyFor(host)

        editor.deleteBeforeCaret()

        assertEquals("a", host.text)
        assertEquals(0, host.keyPresses)
        assertEquals(
            "一次删整个 emoji（按码点算一个）",
            listOf<Any?>(1, 0),
            host.deleteCalls.first(),
        )
    }

    @Test
    fun `the selected text is reported apart from a failure to report`() {
        val none = FakeEditor(initialText = "abc")
        assertEquals("", proxyFor(none).selectedTextOrNull())

        val selected = FakeEditor(initialText = "abcdef", selection = 0 to 3)
        assertEquals("abc", proxyFor(selected).selectedTextOrNull())

        val silent = FakeEditor(initialText = "abc", answerSelection = false)
        assertNull(proxyFor(silent).selectedTextOrNull())
    }

    private fun proxyFor(host: FakeEditor): EditorProxy =
        EditorProxy({ _ -> host.keyPresses++ }) { host.proxy }

    /**
     * 一个够真的假编辑器：文本会真的变短，删除接口可以分别"实现"或"忽略"，也能拒绝报文本。
     */
    private class FakeEditor(
        initialText: String,
        private val honorsCodePoints: Boolean = true,
        private val honorsUnits: Boolean = true,
        private val readable: Boolean = true,
        private val selection: Pair<Int, Int>? = null,
        private val answerSelection: Boolean = true,
    ) {
        var text: String = initialText
        var keyPresses = 0
        val calls = mutableListOf<String>()
        val deleteCalls = mutableListOf<List<Any?>>()

        val proxy: InputConnection = Proxy.newProxyInstance(
            InputConnection::class.java.classLoader,
            arrayOf(InputConnection::class.java),
        ) { _, method, args ->
            calls += method.name
            when (method.name) {
                "getExtractedText" -> if (readable) extracted() else null
                "getSelectedText" -> if (!answerSelection) {
                    throw IllegalStateException("editor refuses to report a selection")
                } else {
                    selection?.let { text.substring(it.first, it.second) } ?: ""
                }

                "deleteSurroundingTextInCodePoints" -> {
                    deleteCalls += args?.toList().orEmpty()
                    if (honorsCodePoints) deleteCodePoints((args!![0] as Int))
                    true
                }

                "deleteSurroundingText" -> {
                    deleteCalls += args?.toList().orEmpty()
                    if (honorsUnits) text = text.dropLast((args!![0] as Int).coerceIn(0, text.length))
                    true
                }

                "sendKeyEvent" -> {
                    keyPresses++
                    true
                }

                else -> defaultOf(method.returnType)
            }
        } as InputConnection

        private fun extracted(): ExtractedText = ExtractedText().apply {
            this.text = this@FakeEditor.text
            startOffset = 0
            selectionStart = this@FakeEditor.text.length
            selectionEnd = this@FakeEditor.text.length
        }

        private fun deleteCodePoints(count: Int) {
            var index = text.length
            repeat(count) {
                if (index <= 0) return@repeat
                index -= 1
                if (index > 0 && Character.isLowSurrogate(text[index]) &&
                    Character.isHighSurrogate(text[index - 1])
                ) {
                    index -= 1
                }
            }
            text = text.substring(0, index)
        }

        private fun defaultOf(type: Class<*>): Any? = when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            else -> null
        }
    }
}

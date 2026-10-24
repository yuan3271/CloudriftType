package com.yuan3271.cloudrift.input

import android.view.inputmethod.InputConnection
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 上屏区（composing region）在这个输入法里**只是当前缓冲的预览**：中文模式下它就是用户刚打
 * 的那串拼音字母。所以"清掉上屏区"必须真的把那些字母删掉——用 `finishComposingText()` 收尾
 * 会把它们原地定稿留下，屏幕上就成了"退格删不掉最后一个字"。
 *
 * 锁的是这个意图：先 `setComposingText("")`（删），再 `finishComposingText()`（收尾）。
 */
class EditorProxyTest {

    @Test
    fun `clearing the composing region deletes the preview instead of keeping it`() {
        val host = RecordingConnection()
        val editor = EditorProxy { host.proxy }

        editor.setComposing("a")
        editor.clearComposing()

        val removal = host.calls.indexOfFirst { it.name == "setComposingText" && it.args.first() == "" }
        val finish = host.calls.indexOfFirst { it.name == "finishComposingText" }
        assertTrue("清上屏区必须先把它替换成空串", removal >= 0)
        assertTrue("收尾要排在删除之后", finish > removal)
        assertFalse("清完之后不再处于组合态", editor.isComposing)
    }

    @Test
    fun `clearing when nothing is composed never touches the editor`() {
        val host = RecordingConnection()
        val editor = EditorProxy { host.proxy }

        editor.clearComposing()

        assertEquals(emptyList<String>(), host.calls.map { it.name })
    }

    @Test
    fun `appending a composition uses only the standard relative caret update`() {
        val host = RecordingConnection()
        val editor = EditorProxy { host.proxy }

        editor.setComposing("g")

        assertEquals(listOf("setComposingText"), host.calls.map { it.name })
        assertEquals(listOf<Any?>("g", 1), host.calls.single().args)
    }

    @Test
    fun `an edit in the middle uses one batched absolute selection`() {
        val host = RecordingConnection(selectionEndAfterComposition = 15)
        val editor = EditorProxy { host.proxy }

        // Existing text ends at 15. Replacing `nhao` with `nihao` puts its start at 10; offset 2
        // is the caret after `ni`, independent of any surrounding text in the field.
        editor.setComposing("nihao", cursorOffset = 2)

        assertEquals(
            listOf("beginBatchEdit", "setComposingText", "getExtractedText", "setSelection", "endBatchEdit"),
            host.calls.map { it.name },
        )
        assertEquals(listOf<Any?>("nihao", 1), host.calls[1].args)
        assertEquals(listOf<Any?>(12, 12), host.calls[3].args)
        assertEquals(11, composingSelectionTarget(14, textLength = 5, cursorOffset = 2))
    }

    /** A recording stand-in for [InputConnection]; uninteresting methods answer with a default. */
    private class RecordingConnection(
        private val selectionEndAfterComposition: Int? = null,
    ) {
        val calls = mutableListOf<Call>()
        val proxy: InputConnection = Proxy.newProxyInstance(
            InputConnection::class.java.classLoader,
            arrayOf(InputConnection::class.java),
        ) { _, method, args ->
            calls += Call(method.name, args?.toList().orEmpty())
            if (method.name == "getExtractedText") {
                return@newProxyInstance android.view.inputmethod.ExtractedText().apply {
                    selectionEndAfterComposition?.let { selectionEnd = it }
                }
            }
            when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                java.lang.Float.TYPE -> 0f
                java.lang.Double.TYPE -> 0.0
                else -> null
            }
        } as InputConnection
    }

    private data class Call(val name: String, val args: List<Any?>)
}

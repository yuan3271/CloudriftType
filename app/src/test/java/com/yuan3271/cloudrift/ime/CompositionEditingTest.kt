package com.yuan3271.cloudrift.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 组合区里的光标属于这次拼写本身。方向键在 `nhao` 的 n 和 h 之间停下时，输入法要记住那个
 * 位置并把 i 插进去；不能把"用户正在改拼音"误读成"离开输入"，更不能顺手采用首选词。
 */
class CompositionEditingTest {

    @Test
    fun `a caret inside the composing region is an edit point`() {
        // "nhao" 占 20..24，光标放到 n 和 h 中间（偏移 1）。
        assertEquals(
            1,
            compositionCaretOffset(
                selectionStart = 21,
                selectionEnd = 21,
                composingStart = 20,
                composingEnd = 24,
                previewLength = 4,
            ),
        )
    }

    @Test
    fun `a caret outside or a range is not an edit point`() {
        assertNull(
            compositionCaretOffset(
                selectionStart = 25,
                selectionEnd = 25,
                composingStart = 20,
                composingEnd = 24,
                previewLength = 4,
            ),
        )
        assertNull(
            compositionCaretOffset(
                selectionStart = 20,
                selectionEnd = 22,
                composingStart = 20,
                composingEnd = 24,
                previewLength = 4,
            ),
        )
    }

    @Test
    fun `inserting i between n and h rebuilds the reading and keeps the caret after i`() {
        val edited = insertReading(raw = "nhao", caret = 1, insert = "i")

        assertEquals(EditedReading("nihao", 2), edited)
    }

    @Test
    fun `successive keys stay in typing order at the end of the reading`() {
        // 用户打 `g` 再打 `u`：第二次必须接在 `g` 后得到 `gu`。若光标回调把编辑点误记到 0，
        // 这里会变成 `ug`，引擎自然也就拿不到「鼓」。
        val first = insertReading(raw = "", caret = null, insert = "g")
        val second = insertReading(raw = first.text, caret = first.caret, insert = "u")

        assertEquals(EditedReading("gu", 2), second)
    }

    @Test
    fun `successive edits keep advancing from the edited position`() {
        val first = insertReading(raw = "nhao", caret = 1, insert = "i")
        val second = insertReading(raw = first.text, caret = first.caret, insert = "a")

        assertEquals(EditedReading("niahao", 3), second)
    }

    @Test
    fun `backspace respects the edit point instead of always eating the last letter`() {
        assertEquals(EditedReading("nao", 1), backspaceReading("nhao", caret = 2))
        assertEquals(EditedReading("nha", 3), backspaceReading("nhao", caret = null))
        assertEquals(EditedReading("nhao", 0), backspaceReading("nhao", caret = 0))
    }

    @Test
    fun `arrow movement is clamped inside the raw reading`() {
        assertEquals(3, movedReadingCaret("nhao", caret = null, delta = -1))
        assertEquals(1, movedReadingCaret("nhao", caret = 2, delta = -1))
        assertEquals(4, movedReadingCaret("nhao", caret = 4, delta = 1))
        assertEquals(0, movedReadingCaret("nhao", caret = 0, delta = -1))
    }
}

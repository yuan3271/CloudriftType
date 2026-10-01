package com.yuan3271.cloudrift.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule behind "one backspace takes back what was just committed".
 *
 * It used to be decided from our own bookkeeping (the remembered length plus a couple of flags
 * maintained from selection callbacks), and editors do not report those the way the keyboard
 * expects, so the pass could fire while the caret was somewhere else. The rule is now: only when
 * the editor itself confirms the text in front of the caret is exactly what was committed.
 */
class CommitUndoTest {

    @Test
    fun `the commit is taken back when the editor confirms it is right in front of the caret`() {
        assertTrue(
            takesBackCommit(
                remembered = "我今天去了北京",
                before = "我今天去了北京",
                elapsedMs = 500,
                hasComposingText = false,
            ),
        )
    }

    @Test
    fun `a longer word works the same way`() {
        assertTrue(
            takesBackCommit(
                remembered = "百度百科",
                before = "百度百科",
                elapsedMs = 1_200,
                hasComposingText = false,
            ),
        )
    }

    @Test
    fun `an editor that reports a different text falls back to one character`() {
        assertFalse(
            takesBackCommit(
                remembered = "百度百科",
                before = "百度",
                elapsedMs = 500,
                hasComposingText = false,
            ),
        )
    }

    @Test
    fun `an editor that will not say anything falls back to one character`() {
        assertFalse(
            takesBackCommit(
                remembered = "百度百科",
                before = null,
                elapsedMs = 500,
                hasComposingText = false,
            ),
        )
    }

    @Test
    fun `a live composition is not taken back wholesale`() {
        assertFalse(
            takesBackCommit(
                remembered = "百度百科",
                before = "百度百科",
                elapsedMs = 500,
                hasComposingText = true,
            ),
        )
    }

    @Test
    fun `an old commit is not taken back wholesale`() {
        assertFalse(
            takesBackCommit(
                remembered = "百度百科",
                before = "百度百科",
                elapsedMs = 9_000,
                hasComposingText = false,
            ),
        )
    }
}

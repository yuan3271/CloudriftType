package com.yuan3271.cloudrift.ui

import androidx.compose.ui.unit.dp
import com.yuan3271.cloudrift.data.CursorSpeed
import com.yuan3271.cloudrift.input.KeyCode
import com.yuan3271.cloudrift.input.KeyDef
import com.yuan3271.cloudrift.input.KeyboardLayouts
import com.yuan3271.cloudrift.input.KeyboardPage
import com.yuan3271.cloudrift.input.LayoutId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomCursorGestureTest {

    private val step = 24f

    @Test
    fun `cursor movement waits for one complete distance`() {
        assertEquals(0, cursorStepsFor(0f, step))
        assertEquals(0, cursorStepsFor(23.9f, step))
        assertEquals(1, cursorStepsFor(24f, step))
    }

    @Test
    fun `left and right use the same distance per character`() {
        assertEquals(1, cursorStepsFor(24f, step))
        assertEquals(-1, cursorStepsFor(-24f, step))
        assertEquals(2, cursorStepsFor(60f, step))
        assertEquals(-2, cursorStepsFor(-60f, step))
    }

    @Test
    fun `extra distance never accelerates the cursor`() {
        // Each additional 24px is exactly one additional character, whether the finger is near
        // the edge or still in the middle. Going back crosses the same thresholds in reverse.
        assertEquals(3, cursorStepsFor(72f, step))
        assertEquals(5, cursorStepsFor(120f, step))
        assertEquals(1, cursorStepsFor(24f, step))
    }

    @Test
    fun `the rail matches the active layout space bar`() {
        val qwerty = KeyboardLayouts.rows(
            layout = LayoutId.Pinyin26,
            page = KeyboardPage.Letters,
            shifted = false,
            enterLabel = "换行",
        ).first { row -> row.any { it.code == KeyCode.Space } }
        assertEquals(147f, spaceBarRailWidth(300.dp, qwerty).value, 0.01f)

        val nine = KeyboardLayouts.rows(
            layout = LayoutId.Pinyin9,
            page = KeyboardPage.Letters,
            shifted = false,
            enterLabel = "换行",
        ).first { row -> row.any { it.code == KeyCode.Space } }
        assertEquals(174f, spaceBarRailWidth(300.dp, nine).value, 0.01f)
    }

    @Test
    fun `cursor speed defaults to medium and each step is faster`() {
        assertEquals(CursorSpeed.Medium, CursorSpeed.fromKey(null))
        assertTrue(CursorSpeed.Slow.stepDp > CursorSpeed.Medium.stepDp)
        assertTrue(CursorSpeed.Medium.stepDp > CursorSpeed.Fast.stepDp)
    }

    @Test
    fun `a row without a space key keeps a safe fallback width`() {
        assertEquals(
            240.dp,
            spaceBarRailWidth(240.dp, listOf(KeyDef.immediate("1"))),
        )
    }
}

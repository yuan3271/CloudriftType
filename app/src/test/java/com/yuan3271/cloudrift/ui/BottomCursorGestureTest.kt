package com.yuan3271.cloudrift.ui

import org.junit.Assert.assertEquals
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
}

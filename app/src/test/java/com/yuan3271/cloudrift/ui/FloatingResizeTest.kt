package com.yuan3271.cloudrift.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Resizing the floating keyboard has to feel like dragging a window edge under a window manager:
 * the edge the finger is holding moves by exactly what the finger moves. Both halves of that are
 * asserted in the card's own units (pixels of card edge), because that is the thing the user sees
 * and the thing the previous mapping got wrong by a factor of four.
 */
class FloatingResizeTest {

    private val density = 2.75f // a typical phone: 1dp = 2.75px
    private val screenWidthDp = 400f

    /** The card's height changes by `keyHeightDeltaDp * rows` dp - the finger's own budget. */
    private fun cardHeightPx(keyHeightDeltaDp: Float, rows: Float) =
        keyHeightDeltaDp * rows * density

    @Test
    fun `dragging the corner up by 40px raises the card edge by 40px`() {
        val rows = 4f // a 26 key page: four rows of keys
        val (_, keyHeightDeltaDp) = floatingResizeDeltas(
            dragXPx = 0f,
            dragYPx = -40f,
            density = density,
            screenWidthDp = screenWidthDp,
            resizeRows = rows,
        )

        assertEquals(40f, cardHeightPx(keyHeightDeltaDp, rows), 0.01f)
    }

    @Test
    fun `dragging the corner sideways moves the right edge by the same pixels`() {
        val drag = 55f
        val (widthPercent, keyHeightDeltaDp) = floatingResizeDeltas(
            dragXPx = drag,
            dragYPx = 0f,
            density = density,
            screenWidthDp = screenWidthDp,
            resizeRows = 4f,
        )

        // The width is a percentage of the screen; turn it back into pixels of card edge.
        val cardWidthPx = widthPercent / 100f * screenWidthDp * density
        assertEquals(drag, cardWidthPx, 0.01f)
        // A purely sideways drag must not change the height: a corner drag that grew the card while
        // the finger moved right would be the old bug in a different direction.
        assertEquals(0f, keyHeightDeltaDp, 0.0001f)
    }

    @Test
    fun `the row count is recovered from the height of a key page`() {
        val keyHeightPx = 42f * density
        val gapPx = 6f * density
        // 4 rows: 4*42 + 3*6 = 186dp, the letter page - and the symbol page, which is three symbol
        // rows plus the function row, measures the same because it is the same shape.
        val letterPage = (4 * 42f + 3 * 6f) * density
        // The nine key pad is four rows too, with a shorter key.
        val nineKey = (4 * 36f + 3 * 6f) * density

        assertEquals(4f, floatingResizeRows(letterPage, keyHeightPx, gapPx), 0.001f)
        assertEquals(4f, floatingResizeRows(nineKey, 36f * density, gapPx), 0.001f)
    }

    @Test
    fun `an unmeasured panel does not amplify the drag`() {
        // Before the first layout (or on a panel that is not made of keys) the row count is unknown;
        // falling back to a single row means "one pixel of drag, one pixel of card", never a jump.
        assertEquals(1f, floatingResizeRows(keyAreaHeightPx = 0f, keyHeightPx = 100f, gapPx = 10f))

        val (_, keyHeightDeltaDp) = floatingResizeDeltas(
            dragXPx = 0f,
            dragYPx = -30f,
            density = density,
            screenWidthDp = screenWidthDp,
            resizeRows = 0f,
        )
        assertTrue("a zero row count must not divide the drag away", keyHeightDeltaDp > 0f)
        assertEquals(30f, cardHeightPx(keyHeightDeltaDp, 1f), 0.01f)
    }
}

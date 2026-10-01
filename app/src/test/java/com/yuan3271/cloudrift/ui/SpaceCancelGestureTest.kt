package com.yuan3271.cloudrift.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slide-up-to-cancel on the space bar. The hysteresis matters: without it a finger resting on the
 * line would flip the "松开取消" hint on and off, and the recording would be thrown away on a
 * boundary jitter.
 */
class SpaceCancelGestureTest {

    private val threshold = 100f

    @Test
    fun `a short slide does not arm the cancel`() {
        assertFalse(slideUpCancels(dy = -40f, threshold = threshold, currentlyArmed = false))
    }

    @Test
    fun `sliding past the threshold arms the cancel`() {
        assertTrue(slideUpCancels(dy = -120f, threshold = threshold, currentlyArmed = false))
    }

    @Test
    fun `coming back only part way keeps it armed`() {
        // Inside the release band of a line that is already armed: still cancelling.
        assertTrue(slideUpCancels(dy = -70f, threshold = threshold, currentlyArmed = true))
    }

    @Test
    fun `coming back down disarms and the recording is kept`() {
        assertFalse(slideUpCancels(dy = -30f, threshold = threshold, currentlyArmed = true))
    }
}

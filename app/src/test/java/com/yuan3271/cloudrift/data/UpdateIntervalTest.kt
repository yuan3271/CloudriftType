package com.yuan3271.cloudrift.data

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The update check frequency. What matters is the *meaning* of each notch, so the tests are about
 * when a check happens, not about which constant feeds which millisecond count: "每 6 小时" that
 * fired at 5 hours 59 would be a lie to the user, and "每次开键盘" that needed a whole day between
 * opens would be the same lie in the other direction.
 */
class UpdateIntervalTest {

    private val hour = TimeUnit.HOURS.toMillis(1)
    private val now = 1_700_000_000_000L

    @Test
    fun `every keyboard open is due on the next open, whenever that is`() {
        // Nothing between two opens: the user opened the keyboard twice, so we ask twice.
        assertTrue(UpdateInterval.EveryKeyboardOpen.isDue(lastCheckMillis = now, nowMillis = now))
        assertTrue(
            UpdateInterval.EveryKeyboardOpen.isDue(
                lastCheckMillis = now - 1_000L,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun `six hours means six hours, not five`() {
        assertFalse(
            UpdateInterval.Every6Hours.isDue(
                lastCheckMillis = now - 6 * hour + 1_000L,
                nowMillis = now,
            ),
        )
        assertTrue(
            UpdateInterval.Every6Hours.isDue(lastCheckMillis = now - 6 * hour, nowMillis = now),
        )
    }

    @Test
    fun `never is never due, and the plain intervals keep their lengths`() {
        assertFalse(
            UpdateInterval.Never.isDue(lastCheckMillis = now - 10 * 24 * hour, nowMillis = now),
        )
        assertFalse(
            UpdateInterval.Daily.isDue(lastCheckMillis = now - 5 * hour, nowMillis = now),
        )
        assertTrue(
            UpdateInterval.Daily.isDue(lastCheckMillis = now - 24 * hour, nowMillis = now),
        )
        assertTrue(
            UpdateInterval.Weekly.isDue(lastCheckMillis = now - 7 * 24 * hour, nowMillis = now),
        )
        assertFalse(
            UpdateInterval.Monthly.isDue(lastCheckMillis = now - 29 * 24 * hour, nowMillis = now),
        )
    }

    @Test
    fun `a stored key that this build does not know falls back to daily`() {
        // The preference stores enum names; a build that loses a notch (or meets a newer one) must
        // not turn "check for updates" into "do not check".
        assertEquals(UpdateInterval.Daily, UpdateInterval.fromKey("EveryFortnight"))
        assertEquals(UpdateInterval.Daily, UpdateInterval.fromKey(null))
        assertEquals(UpdateInterval.Every6Hours, UpdateInterval.fromKey("Every6Hours"))
        assertEquals(
            UpdateInterval.EveryKeyboardOpen,
            UpdateInterval.fromKey("everykeyboardopen"),
        )
    }
}

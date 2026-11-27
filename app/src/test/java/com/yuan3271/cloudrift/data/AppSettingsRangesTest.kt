package com.yuan3271.cloudrift.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 设置里的数值都有合法范围，滑杆给的却是连续值。读盘与写入都要经过 [AppSettings.normalized]，
 * 所以这里锁的是"存进去的东西永远能用"这条不变量。
 */
class AppSettingsRangesTest {

    @Test
    fun `zero key height still means auto`() {
        assertEquals(0, AppSettings.normalizeKeyHeight(0))
    }

    @Test
    fun `a key height that would make an unusable keyboard snaps to the minimum`() {
        // 1dp 的按键按不下去：滑杆滑过自动档之后的第一格必须还是一条能用的键盘。
        assertEquals(AppSettings.MIN_KEY_HEIGHT_DP, AppSettings.normalizeKeyHeight(1))
        assertEquals(AppSettings.MIN_KEY_HEIGHT_DP, AppSettings.normalizeKeyHeight(27))
        assertEquals(30, AppSettings.normalizeKeyHeight(30))
        assertEquals(AppSettings.MAX_KEY_HEIGHT_DP, AppSettings.normalizeKeyHeight(500))
    }

    @Test
    fun `out of range sliders are pulled back into their range`() {
        val wild = AppSettings(
            keyHeightDp = 3,
            bottomGapDp = 999,
            keyCornerRadiusDp = 0,
            keyLabelScalePercent = 400,
            glassIntensityPercent = 90,
            accentHue = -30,
            accentSaturation = 5,
            floatingWidthPercent = 10,
            floatingKeyHeightDp = 5,
            voiceAutoApplyDelayMs = 60_000,
        )

        val fixed = wild.normalized()

        assertEquals(AppSettings.MIN_KEY_HEIGHT_DP, fixed.keyHeightDp)
        assertEquals(AppSettings.MAX_BOTTOM_GAP_DP, fixed.bottomGapDp)
        assertEquals(AppSettings.MIN_KEY_CORNER_RADIUS_DP, fixed.keyCornerRadiusDp)
        assertEquals(AppSettings.MAX_KEY_LABEL_SCALE, fixed.keyLabelScalePercent)
        assertEquals(AppSettings.GLASS_INTENSITY_MAX, fixed.glassIntensityPercent)
        assertEquals(330, fixed.accentHue)
        assertEquals(AppSettings.MIN_ACCENT_SATURATION, fixed.accentSaturation)
        assertEquals(AppSettings.MIN_FLOATING_WIDTH_PERCENT, fixed.floatingWidthPercent)
        assertEquals(AppSettings.MIN_FLOATING_KEY_HEIGHT, fixed.floatingKeyHeightDp)
        assertEquals(AppSettings.MAX_VOICE_AUTO_APPLY_MS, fixed.voiceAutoApplyDelayMs)
    }

    @Test
    fun `normalizing twice changes nothing`() {
        val fixed = AppSettings(keyHeightDp = 12, accentHue = 400).normalized()
        assertEquals(fixed, fixed.normalized())
    }

    @Test
    fun `an in range setting is left exactly as it was`() {
        val sane = AppSettings(
            keyHeightDp = 42,
            bottomGapDp = 8,
            keyCornerRadiusDp = 18,
            keyLabelScalePercent = 100,
            glassIntensityPercent = 18,
            accentHue = 222,
            accentSaturation = 42,
            floatingWidthPercent = 78,
            floatingKeyHeightDp = 42,
            voiceAutoApplyDelayMs = 1000,
        )
        assertEquals(sane, sane.normalized())
    }
}

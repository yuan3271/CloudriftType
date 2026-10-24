package com.yuan3271.cloudrift.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeTest {

    @Test
    fun `missing preference defaults renders as material but still requires a choice`() {
        val defaults = AppSettings()

        assertEquals(AppTheme.Material3Expressive, AppTheme.fromKey(null))
        assertEquals(AppTheme.Material3Expressive, defaults.appTheme)
        assertFalse(defaults.appThemeChosen)
    }

    @Test
    fun `both persisted keys survive case-insensitive reads`() {
        assertEquals(
            AppTheme.Material3Expressive,
            AppTheme.fromKey("material3expressive"),
        )
        assertEquals(AppTheme.LiquidGlass, AppTheme.fromKey("liquidglass"))
    }

    @Test
    fun `1_1_2 rebuild enables liquid glass and its required choice`() {
        assertTrue(AppTheme.LIQUID_GLASS_ENABLED)
        assertTrue(AppTheme.THEME_CHOICE_ENABLED)
        assertEquals(
            AppTheme.LiquidGlass,
            AppTheme.effective("liquidglass"),
        )
    }

    @Test
    fun `making the required choice records it and preserves existing appearance`() {
        val before = AppSettings(
            themeMode = ThemeMode.Dark,
            themeSource = ThemeSource.Custom,
            accentHue = 315,
        )

        val after = before.withAppThemeChoice(AppTheme.LiquidGlass)

        assertTrue(after.appThemeChosen)
        assertEquals(AppTheme.LiquidGlass, after.appTheme)
        assertEquals(ThemeMode.Dark, after.themeMode)
        assertEquals(ThemeSource.Custom, after.themeSource)
        assertEquals(315, after.accentHue)
    }

    @Test
    fun `liquid glass key concentration defaults to medium and persists by name`() {
        assertEquals(
            LiquidGlassKeyDensity.Medium,
            AppSettings().liquidGlassKeyDensity,
        )
        assertEquals(
            LiquidGlassKeyDensity.High,
            LiquidGlassKeyDensity.fromKey("high"),
        )
        assertEquals(18, AppSettings().glassIntensityPercent)
    }
}

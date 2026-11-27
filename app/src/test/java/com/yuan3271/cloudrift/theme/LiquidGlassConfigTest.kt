package com.yuan3271.cloudrift.theme

import com.yuan3271.cloudrift.data.LiquidGlassKeyDensity
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidGlassConfigTest {

    @Test
    fun `interior planes stay subtle and never become glass`() {
        val fills = listOf(
            LiquidGlassConfig.INTERNAL_FILL_LOW,
            LiquidGlassConfig.INTERNAL_FILL_MEDIUM,
            LiquidGlassConfig.INTERNAL_FILL_HIGH,
            LiquidGlassConfig.INTERNAL_FILL_RAISED,
        )

        assertTrue(fills.all { it in 0.06f..0.12f })
        assertEquals(fills.sorted(), fills)
    }

    @Test
    fun `semantic states are solid rather than another translucent layer`() {
        assertEquals(1f, LiquidGlassConfig.SOLID_STATE_ALPHA)
    }

    @Test
    fun `key concentration gets more transparent from low to high in both modes`() {
        val lightLow = glassKeyFillAlpha(LiquidGlassKeyDensity.Low, dark = false)
        val lightMedium = glassKeyFillAlpha(LiquidGlassKeyDensity.Medium, dark = false)
        val lightHigh = glassKeyFillAlpha(LiquidGlassKeyDensity.High, dark = false)
        assertTrue(lightLow > lightMedium)
        assertTrue(lightMedium > lightHigh)
        assertTrue(lightHigh in 0.06f..0.14f)

        val darkLow = glassKeyFillAlpha(LiquidGlassKeyDensity.Low, dark = true)
        val darkMedium = glassKeyFillAlpha(LiquidGlassKeyDensity.Medium, dark = true)
        val darkHigh = glassKeyFillAlpha(LiquidGlassKeyDensity.High, dark = true)
        assertTrue(darkLow > darkMedium)
        assertTrue(darkMedium > darkHigh)
        // 深色下的键帽是浅色的那层，必须比浅色模式的墨色更实一点才看得出按键。
        assertTrue(darkHigh >= 0.10f)
    }

    @Test
    fun `ambient base lights stay on the panel chord and share one intensity`() {
        assertTrue(LiquidGlassConfig.HIGHLIGHT_WIDTH_DP > 0f)
        assertEquals(3, LiquidGlassConfig.CENTER_LIGHT_STOPS.size)
        assertTrue(LiquidGlassConfig.CENTER_LIGHT_STOPS.all { it in 0.2f..0.8f })
        assertEquals(0.12f, LiquidGlassConfig.BASE_LIGHT_ALPHA)
    }

    @Test
    fun `glass intensity stays frosted at one end and never becomes fully clear at the other`() {
        val frosted = outerGlassMaterial(0, dark = false)
        val clear = outerGlassMaterial(100, dark = false)
        val default = outerGlassMaterial(35, dark = false)

        assertTrue(frosted.blurDp >= 12f)
        assertTrue(frosted.fillAlpha >= 0.75f)
        assertTrue(frosted.highlightAlpha > frosted.darkEdgeAlpha)
        // 「最透明」也留一点底色、一点模糊、一点边光：全透时壁纸会直接穿到字底下，
        // 键帽也会溶进背景（见 LiquidGlassConfig.CLEAR_FILL_ALPHA 的注释）。
        assertTrue(clear.blurDp >= 6f)
        assertTrue(clear.fillAlpha >= 0.18f)
        assertTrue(clear.highlightAlpha >= 0.10f)
        assertTrue(clear.darkEdgeAlpha >= 0.08f)
        assertTrue(clear.refractionAmountDp >= 6f)
        assertTrue(default.fillAlpha in 0.55f..0.72f)
    }

    @Test
    fun `light and dark use the same intensity structure with different highlight strength`() {
        assertTrue(LiquidGlassConfig.SURFACE_COLOR_LIGHT.red > LiquidGlassConfig.SURFACE_COLOR_DARK.red)
        assertTrue(LiquidGlassConfig.SURFACE_COLOR_LIGHT.alpha == 1f)
        assertTrue(LiquidGlassConfig.SURFACE_COLOR_DARK.alpha == 1f)
        assertTrue(
            outerGlassMaterial(35, dark = false).highlightAlpha >
                outerGlassMaterial(35, dark = true).highlightAlpha,
        )
    }

    @Test
    fun `inner radius is the outer radius minus spacing and never goes negative`() {
        assertEquals(18f, concentricInnerRadius(26f.dp, 8f.dp).value)
        assertEquals(0f, concentricInnerRadius(6f.dp, 8f.dp).value)
    }

    @Test
    fun `liquid glass light mode uses half of the dark bottom light`() {
        assertEquals(LiquidGlassConfig.BASE_LIGHT_ALPHA, bottomLightAlpha(dark = true))
        assertEquals(LiquidGlassConfig.BASE_LIGHT_ALPHA * 0.5f, bottomLightAlpha(dark = false))
    }

    @Test
    fun `visible glass strength maps zero to fifty into zero to one hundred`() {
        assertEquals(0f, glassIntensityFraction(0))
        assertEquals(0.5f, glassIntensityFraction(25))
        assertEquals(1f, glassIntensityFraction(50))
        assertEquals(1f, glassIntensityFraction(80))
        assertEquals(100, effectiveGlassIntensityPercent(50))
    }

}

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
    fun `key concentration gets more transparent from low to high`() {
        val low = glassKeyFillAlpha(LiquidGlassKeyDensity.Low)
        val medium = glassKeyFillAlpha(LiquidGlassKeyDensity.Medium)
        val high = glassKeyFillAlpha(LiquidGlassKeyDensity.High)

        assertTrue(low > medium)
        assertTrue(medium > high)
        assertTrue(low in 0.06f..0.12f)
        assertTrue(high in 0.06f..0.12f)
    }

    @Test
    fun `ambient base lights stay on the panel chord and share one intensity`() {
        assertTrue(LiquidGlassConfig.HIGHLIGHT_WIDTH_DP > 0f)
        assertEquals(3, LiquidGlassConfig.CENTER_LIGHT_STOPS.size)
        assertTrue(LiquidGlassConfig.CENTER_LIGHT_STOPS.all { it in 0.2f..0.8f })
        assertTrue(LiquidGlassConfig.BASE_LIGHT_ALPHA in 0.01f..0.1f)
    }

    @Test
    fun `glass intensity spans frosted readable to fully clear`() {
        val frosted = outerGlassMaterial(0, dark = false)
        val clear = outerGlassMaterial(100, dark = false)
        val default = outerGlassMaterial(35, dark = false)

        assertTrue(frosted.blurDp >= 12f)
        assertTrue(frosted.fillAlpha >= 0.75f)
        assertTrue(frosted.highlightAlpha > frosted.darkEdgeAlpha)
        assertEquals(0f, clear.blurDp)
        assertEquals(0f, clear.fillAlpha)
        assertEquals(0f, clear.darkEdgeAlpha)
        assertEquals(0f, clear.highlightAlpha)
        assertEquals(0f, clear.refractionAmountDp)
        assertTrue(default.fillAlpha in 0.4f..0.6f)
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

}

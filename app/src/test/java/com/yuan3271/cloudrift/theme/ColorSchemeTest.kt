package com.yuan3271.cloudrift.theme

import androidx.compose.ui.graphics.Color
import com.yuan3271.cloudrift.data.ThemeSource
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorSchemeTest {

    @Test
    fun `saturated accent keeps hue and increases chroma`() {
        val source = Color(0xFF6B7C9A)
        val saturated = source.saturated(1.4f)

        assertTrue(saturated.chroma > source.chroma)
        assertEquals(source.alpha, saturated.alpha)
    }

    @Test
    fun `light mode resolves the light accent roles, not the dark ones`() {
        val source = accentSourceFor(
            dark = false,
            themeSource = ThemeSource.Cloudrift,
            accentHue = 222,
            accentSaturation = 42,
            dynamic = null,
        )
        val resolved = CloudriftLightScheme.withAccentRolesFrom(source)

        assertEquals(CloudriftLightScheme.surface, resolved.surface)
        assertEquals(CloudriftLightScheme.primary, resolved.primary)
        assertEquals(CloudriftLightScheme.primaryContainer, resolved.primaryContainer)
        // 这条是这次修的关键：浅色页面里以前装的是深色主题的 tone-30 容器。
        assertNotEquals(CloudriftDarkScheme.primaryContainer, resolved.primaryContainer)
        assertTrue(
            "浅色容器必须比浅色强调色更浅",
            resolved.primaryContainer.oklabLightness() > resolved.primary.oklabLightness(),
        )
    }

    @Test
    fun `dark mode pairs dark surfaces with the dark accent roles`() {
        val source = accentSourceFor(
            dark = true,
            themeSource = ThemeSource.Cloudrift,
            accentHue = 222,
            accentSaturation = 42,
            dynamic = null,
        )
        val resolved = CloudriftDarkScheme.withAccentRolesFrom(source)

        assertEquals(CloudriftDarkScheme.surface, resolved.surface)
        assertEquals(CloudriftDarkScheme.primary, resolved.primary)
        assertEquals(CloudriftDarkScheme.primaryContainer, resolved.primaryContainer)
        assertTrue(resolved.primaryContainer.oklabLightness() < resolved.primary.oklabLightness())
    }

    @Test
    fun `custom accents build their own tonal ramp in each mode`() {
        val light = accentSourceFor(false, ThemeSource.Custom, 222, 42, null)
        val dark = accentSourceFor(true, ThemeSource.Custom, 222, 42, null)

        assertTrue(
            "同一色相下，浅色的强调色应当比深色的暗",
            light.primary.oklabLightness() < dark.primary.oklabLightness(),
        )
        assertTrue(light.primaryContainer.oklabLightness() > light.primary.oklabLightness())
        assertTrue(dark.primaryContainer.oklabLightness() < dark.primary.oklabLightness())
    }

    @Test
    fun `custom accent foregrounds stay readable on every hue`() {
        for (hue in 0 until 360 step 30) {
            val light = accentSourceFor(false, ThemeSource.Custom, hue, 70, null)
            val dark = accentSourceFor(true, ThemeSource.Custom, hue, 70, null)
            for (scheme in listOf(light, dark)) {
                assertTrue(
                    "hue=$hue 的强调色前景压不住底色",
                    contrast(scheme.primary, scheme.onPrimary) >= 4.4f,
                )
                assertTrue(
                    "hue=$hue 的容器前景压不住容器",
                    contrast(scheme.primaryContainer, scheme.onPrimaryContainer) >= 4.4f,
                )
            }
        }
    }

    @Test
    fun `a missing dynamic palette falls back to the cloudrift scheme`() {
        assertEquals(
            CloudriftLightScheme,
            accentSourceFor(false, ThemeSource.Dynamic, 222, 42, dynamic = null),
        )
        assertEquals(
            CloudriftDarkScheme,
            accentSourceFor(true, ThemeSource.Dynamic, 222, 42, dynamic = null),
        )
    }

    @Test
    fun `the same lightness setting looks equally bright across hues`() {
        for (hue in 0 until 360 step 30) {
            val color = oklchColor(hue.toFloat(), 0.06f, 0.55f)
            assertEquals(
                "hue=$hue 的 OKLab 明度偏了",
                0.55f,
                color.oklabLightness(),
                0.02f,
            )
            assertTrue(color.red in 0f..1f && color.green in 0f..1f && color.blue in 0f..1f)
        }
    }

    @Test
    fun `chroma that would leave the sRGB gamut is reduced instead of clipped`() {
        // 亮绿在 sRGB 里放不下这么高的 chroma：结果应当是"没那么艳的绿"，
        // 而不是某个通道被裁到 1 之后偏成另一种颜色。
        val vivid = oklchColor(150f, 0.6f, 0.55f)

        assertTrue(vivid.red in 0f..1f && vivid.green in 0f..1f && vivid.blue in 0f..1f)
        assertEquals(0.55f, vivid.oklabLightness(), 0.02f)
        assertTrue("色相应当仍是绿的", vivid.green > vivid.red && vivid.green > vivid.blue)
    }

    private fun contrast(a: Color, b: Color): Float {
        val first = a.relativeLuminance()
        val second = b.relativeLuminance()
        val lighter = maxOf(first, second)
        val darker = minOf(first, second)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun Color.relativeLuminance(): Float {
        fun channel(value: Float): Float =
            if (value <= 0.04045f) value / 12.92f else ((value + 0.055f) / 1.055f).pow(2.4f)
        return channel(red) * 0.2126f + channel(green) * 0.7152f + channel(blue) * 0.0722f
    }

    private val Color.chroma: Float
        get() = maxOf(red, green, blue) - minOf(red, green, blue)
}

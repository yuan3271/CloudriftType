package com.yuan3271.cloudrift.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
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
    fun `material light and dark surfaces share the dark accent roles`() {
        val mixed = CloudriftLightScheme.withAccentRolesFrom(CloudriftDarkScheme)

        assertEquals(CloudriftDarkScheme.primary, mixed.primary)
        assertEquals(CloudriftDarkScheme.primaryContainer, mixed.primaryContainer)
        assertEquals(CloudriftLightScheme.surface, mixed.surface)
    }

    private val Color.chroma: Float
        get() = maxOf(red, green, blue) - minOf(red, green, blue)
}

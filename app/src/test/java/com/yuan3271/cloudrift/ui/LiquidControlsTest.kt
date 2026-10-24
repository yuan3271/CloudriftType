package com.yuan3271.cloudrift.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LiquidControlsTest {

    @Test
    fun `segment lens follows the finger and clamps at both ends`() {
        assertEquals(0f, liquidSelectionPosition(x = 10f, itemWidthPx = 60f, maxIndex = 2))
        assertEquals(1f, liquidSelectionPosition(x = 90f, itemWidthPx = 60f, maxIndex = 2))
        assertEquals(2f, liquidSelectionPosition(x = 150f, itemWidthPx = 60f, maxIndex = 2))
        assertEquals(2f, liquidSelectionPosition(x = 400f, itemWidthPx = 60f, maxIndex = 2))
    }

    @Test
    fun `slider maps the whole track and clamps outside values`() {
        assertEquals(
            25f,
            liquidSliderValue(x = 50f, width = 200, range = 0f..100f),
        )
        assertEquals(
            0f,
            liquidSliderValue(x = -20f, width = 200, range = 0f..100f),
        )
        assertEquals(
            100f,
            liquidSliderValue(x = 260f, width = 200, range = 0f..100f),
        )
    }
}

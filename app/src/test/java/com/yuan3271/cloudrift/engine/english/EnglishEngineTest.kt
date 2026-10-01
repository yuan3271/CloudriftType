package com.yuan3271.cloudrift.engine.english

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishEngineTest {

    @Test
    fun `literal buffer is always the first candidate`() {
        val output = EnglishEngine().evaluate("hello")

        assertEquals("hello", output.candidates.first().text)
    }

    @Test
    fun `completions extend the typed prefix`() {
        val output = EnglishEngine().evaluate("keyb")

        assertTrue(output.candidates.any { it.text == "keyboard" })
    }

    @Test
    fun `completions follow the typed capitalisation`() {
        val output = EnglishEngine().evaluate("Keyb")

        assertTrue(output.candidates.any { it.text == "Keyboard" })
    }
}

package com.yuan3271.cloudrift.engine.japanese

import com.yuan3271.cloudrift.engine.CandidateKind
import com.yuan3271.cloudrift.engine.EngineKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JapaneseEngineTest {

    @Test
    fun `romaji mode previews kana while typing`() {
        val output = JapaneseEngine(EngineKind.Romaji).evaluate("nihon")

        assertEquals("にほん", output.composingPreview)
        assertEquals("にほん", output.candidates.first().text)
    }

    @Test
    fun `kana reading is offered before kanji conversions`() {
        val output = JapaneseEngine().evaluate("nihon")

        assertEquals("にほん", output.candidates.first().text)
        assertTrue(output.candidates.drop(1).any { it.text == "日本" })
    }

    @Test
    fun `committing kanji consumes the whole reading`() {
        val output = JapaneseEngine().evaluate("denwa")
        val kanji = output.candidates.first { it.kind == CandidateKind.Conversion }

        assertEquals("電話", kanji.text)
        assertEquals(5, kanji.consumed)
    }

    @Test
    fun `an unknown reading still commits as kana`() {
        val output = JapaneseEngine().evaluate("nununu")

        assertEquals("ぬぬぬ", output.candidates.first().text)
    }
}

package com.yuan3271.cloudrift.engine.japanese

import org.junit.Assert.assertEquals
import org.junit.Test

class RomajiKanaTest {

    @Test
    fun `basic syllables translate`() {
        assertEquals("にほん", RomajiKana.convert("nihon").kana)
        assertEquals("こんにちは", RomajiKana.convert("konnichiha").kana)
    }

    @Test
    fun `doubled consonants become sokuon`() {
        assertEquals("がっこう", RomajiKana.convert("gakkou").kana)
        assertEquals("きって", RomajiKana.convert("kitte").kana)
    }

    @Test
    fun `n before a consonant is syllabic`() {
        assertEquals("しんぶん", RomajiKana.convert("shinbun").kana)
        assertEquals("かんじ", RomajiKana.convert("kanji").kana)
    }

    @Test
    fun `n before a vowel starts a syllable`() {
        assertEquals("かな", RomajiKana.convert("kana").kana)
        assertEquals("にゃ", RomajiKana.convert("nya").kana)
    }

    @Test
    fun `a trailing n becomes syllabic n`() {
        val result = RomajiKana.convert("n")
        assertEquals("ん", result.kana)
        assertEquals("", result.pending)
    }

    @Test
    fun `re-converting absorbs the trailing n into a syllable`() {
        assertEquals("かん", RomajiKana.convert("kan").kana)
        assertEquals("かな", RomajiKana.convert("kana").kana)
    }

    @Test
    fun `an unknown sequence stays pending instead of being dropped`() {
        val result = RomajiKana.convert("kaq")
        assertEquals("か", result.kana)
        assertEquals("q", result.pending)
    }

    @Test
    fun `a doubled n is one syllabic n followed by a new syllable`() {
        // Only the first n is consumed: the second one starts the next syllable.
        assertEquals("こんにち", RomajiKana.convert("konnichi").kana)
        assertEquals("こんにゃく", RomajiKana.convert("konnyaku").kana)
    }

    @Test
    fun `the apostrophe escape forces a syllabic n`() {
        // "ny" is the にゃ digraph, so ほんやく needs the escape.
        assertEquals("ほんやく", RomajiKana.convert("hon'yaku").kana)
        assertEquals("ほにゃく", RomajiKana.convert("honyaku").kana)
    }
}

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

    /**
     * 词表要覆盖到高考：这几个词只在课程标准词表里（手挑的日常词与技术词都没有），
     * 也就是"课本上的词认得出来"这条需求唯一的钉子。掉一个就说明那张生成表没接进来。
     */
    @Test
    fun `completions reach the school vocabulary all the way to gaokao`() {
        val expected = mapOf(
            "aband" to "abandon",
            "pronun" to "pronunciation",
            "volley" to "volleyball",
            "strawb" to "strawberry",
            "unfort" to "unfortunately",
        )

        for ((typed, word) in expected) {
            val output = EnglishEngine().evaluate(typed)
            assertTrue("$typed 应该补出 $word", output.candidates.any { it.text == word })
        }
    }
}

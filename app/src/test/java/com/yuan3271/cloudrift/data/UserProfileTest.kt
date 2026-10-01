package com.yuan3271.cloudrift.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The learner is deliberately observable, so its contract can be pinned: a habit needs two picks,
 * a habit applies to any code that starts with the one it was learned on, and everything survives
 * a restart of the process.
 */
class UserProfileTest {

    private class MemoryStore : StringStore {
        val values = HashMap<String, String>()
        override fun read(key: String): String? = values[key]
        override fun write(key: String, value: String) {
            values[key] = value
        }
    }

    private fun profile(store: StringStore = MemoryStore()) =
        UserProfile(store, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun `one pick is not a habit and two are`() {
        val learner = profile()

        learner.recordChoice(code = "ni", text = "你好", reading = "nihao")
        // Counted, but still below UserProfile.HABIT_THRESHOLD so the engine ignores it.
        assertEquals(1, habitOf(learner, "ni", "你好"))

        learner.recordChoice(code = "ni", text = "你好", reading = "nihao")
        assertEquals(2, habitOf(learner, "ni", "你好"))
    }

    @Test
    fun `a habit carries over to longer codes that start with it`() {
        val learner = profile()
        repeat(2) { learner.recordChoice(code = "ni", text = "你好", reading = "nihao") }

        assertEquals(2, habitOf(learner, "nih", "你好"))
        assertEquals(2, habitOf(learner, "nihao", "你好"))
        assertEquals(0, habitOf(learner, "hao", "你好"))
    }

    @Test
    fun `words the user spelled out are remembered and offered`() {
        val learner = profile()

        learner.rememberWord(reading = "zhangwei", word = "张伟")

        assertEquals("张伟", learner.inventedWord("zhangwei"))
        assertNull(learner.inventedWord("zhang"))
    }

    @Test
    fun `the profile survives a restart`() {
        val store = MemoryStore()
        val first = profile(store)
        first.rememberWord("zhangwei", "张伟")
        repeat(2) { first.recordChoice("ni", "你好", "nihao") }
        first.flush()

        val second = profile(store)

        assertEquals("张伟", second.inventedWord("zhangwei"))
        assertEquals(2, habitOf(second, "ni", "你好"))
    }

    @Test
    fun `clearing wipes habits and invented words`() {
        val learner = profile()
        learner.rememberWord("zhangwei", "张伟")
        repeat(2) { learner.recordChoice("ni", "你好", "nihao") }

        learner.clear()

        assertEquals(0, habitOf(learner, "ni", "你好"))
        assertNull(learner.inventedWord("zhangwei"))
        assertEquals(0, learner.stats.value.inventedWords)
    }

    @Test
    fun `the stats count what has been learned`() {
        val learner = profile()
        learner.recordChoice("ni", "你", "ni")
        learner.recordChoice("ni", "你", "ni")
        learner.rememberWord("zhangwei", "张伟")

        val stats = learner.stats.value

        assertEquals(1, stats.habits)
        assertEquals(1, stats.inventedWords)
        assertEquals(2, stats.learnedCommits)
    }

    private fun habitOf(learner: UserProfile, code: String, text: String): Int =
        learner.habit(code, text)
}

package com.yuan3271.cloudrift.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    /**
     * The engine asks about a whole candidate list at once on every keystroke, so the batched
     * answer has to be the same answer: same counts, same order, and a prefix of the code counts
     * for a longer one exactly as [UserProfile.habit] does.
     */
    @Test
    fun `the batched habit counts match asking one candidate at a time`() {
        val learner = profile()
        repeat(2) { learner.recordChoice(code = "ni", text = "你好", reading = "nihao") }
        repeat(3) { learner.recordChoice(code = "wo", text = "我", reading = "wo") }

        val texts = listOf("你好", "我", "你们", "你好")
        val batched = learner.habitCounts("wo", texts)

        assertEquals(texts.size, batched.size)
        texts.forEachIndexed { index, text ->
            assertEquals("$text at $index", habitOf(learner, "wo", text), batched[index])
        }
        // 逐字确认的那条路也要一致：一个从未出现过的候选是 0，而不是继承上一个候选的分。
        assertEquals(0, batched[2])
    }

    @Test
    fun `an empty code or list has no habits`() {
        val learner = profile()
        repeat(2) { learner.recordChoice(code = "ni", text = "你好", reading = "nihao") }

        // 没有码就没有前缀可查，但候选列表的形状要原样返回：调用方按下标取值。
        val none = learner.habitCounts("", listOf("你好"))
        assertEquals(1, none.size)
        assertEquals(0, none[0])
        assertTrue(learner.habitCounts("ni", emptyList()).isEmpty())
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

    @Test
    fun `an exported payload carries a profile to another device`() {
        val store = MemoryStore()
        val source = profile(store)
        source.rememberWord("zhangwei", "张伟")
        repeat(2) { source.recordChoice("ni", "你好", "nihao") }

        val target = profile()
        val outcome = target.importPayload(source.exportPayload())

        assertEquals(true, outcome.ok)
        assertEquals("张伟", target.inventedWord("zhangwei"))
        assertEquals(2, habitOf(target, "ni", "你好"))
    }

    @Test
    fun `importing the same payload twice does not inflate the evidence`() {
        val source = profile()
        source.recordChoice("ni", "你好", "nihao")
        val payload = source.exportPayload()

        val target = profile()
        target.importPayload(payload)
        target.importPayload(payload)

        // Two imports of one pick must still be one pick, otherwise a habit would cross
        // UserProfile.HABIT_THRESHOLD just by being transferred twice.
        assertEquals(1, habitOf(target, "ni", "你好"))
    }

    @Test
    fun `importing merges with what the device already knew`() {
        val source = profile()
        source.recordChoice("ni", "你好", "nihao")
        source.rememberWord("zhangwei", "张伟")

        val target = profile()
        repeat(2) { target.recordChoice("wo", "我", "wo") }
        target.importPayload(source.exportPayload())

        assertEquals(2, habitOf(target, "wo", "我"))
        assertEquals(1, habitOf(target, "ni", "你好"))
        assertEquals("张伟", target.inventedWord("zhangwei"))
    }

    @Test
    fun `a payload from nowhere else is refused without touching the profile`() {
        val learner = profile()
        repeat(2) { learner.recordChoice("wo", "我", "wo") }

        val outcome = learner.importPayload("这不是云隙输入的学习记录")

        assertEquals(false, outcome.ok)
        assertEquals(2, habitOf(learner, "wo", "我"))
    }

    @Test
    fun `the payload survives being written to a file and read back`() {
        val source = profile()
        repeat(50) { index ->
            source.recordChoice("ni", "你好$index", "nihao")
            source.rememberWord("zhangwei$index", "张伟")
        }

        val payload = source.exportPayload()
        // 导出走的是系统文件选择器，文件头尾常有换行被读进来：单行、纯 ASCII 才不会在
        // 存取之间被改坏，round-trip 也才能成立。
        assertTrue("payload has whitespace: ${payload.take(40)}", payload.none { it.isWhitespace() })
        assertTrue("payload is not ASCII", payload.all { it.code in 32..126 })

        val target = profile()
        val outcome = target.importPayload(payload)

        assertEquals(true, outcome.ok)
        assertEquals("张伟", target.inventedWord("zhangwei0"))
        assertEquals(1, habitOf(target, "ni", "你好0"))
    }

    private fun habitOf(learner: UserProfile, code: String, text: String): Int =
        learner.habit(code, text)
}

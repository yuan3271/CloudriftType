package com.yuan3271.cloudrift.engine.english

import com.yuan3271.cloudrift.data.StringStore
import com.yuan3271.cloudrift.data.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishEngineTest {

    private class MemoryStore : StringStore {
        private val values = HashMap<String, String>()
        override fun read(key: String): String? = values[key]
        override fun write(key: String, value: String) {
            values[key] = value
        }
    }

    private fun learner() = UserProfile(MemoryStore(), CoroutineScope(Dispatchers.Unconfined))

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

    /**
     * 第二个拐点：课本表是"课本收过哪些词"，不是"真人写句子时在用哪些词"。`said`、`don't`、
     * `didn't` 这类词过去三张表里一个都没有——打 `said` 只会补出 `sail` 这些同前缀的词。
     * 它们来自 Tatoeba 英文句子训出来的 [EnglishEverydayWords]（见 tools/wordgen）。
     */
    @Test
    fun `completions reach the words a corpus says people actually write`() {
        val expected = mapOf(
            "sai" to "said",
            "didn" to "didn't",
            "isn" to "isn't",
            "thorou" to "thoroughly",
            "well-k" to "well-known",
        )

        for ((typed, word) in expected) {
            val output = EnglishEngine().evaluate(typed)
            assertTrue("$typed 应该补出 $word: ${output.candidates.map { it.text }}",
                output.candidates.any { it.text == word })
        }
    }

    /**
     * 词表没有的词（人名、术语、缩写）也能被记住：这个人打过两次以上，它就进补全列表。
     * 一次不算——一次是偶然。
     */
    @Test
    fun `a word the list has never heard of becomes a completion after two commits`() {
        val profile = learner()
        val engine = EnglishEngine(profile)

        profile.recordChoice(code = "cloudrift", text = "cloudrift", reading = "")
        assertTrue(
            "只打过一次不该进候选: ${engine.evaluate("cloudr").candidates.map { it.text }}",
            engine.evaluate("cloudr").candidates.none { it.text == "cloudrift" },
        )

        profile.recordChoice(code = "cloudrift", text = "cloudrift", reading = "")
        assertTrue(
            "打过两次应该补出来: ${engine.evaluate("cloudr").candidates.map { it.text }}",
            engine.evaluate("cloudr").candidates.any { it.text == "cloudrift" },
        )
    }

    /**
     * 中英共用一份学习记录的另一半：为同一个码选过两次的词直接提到最前，其余保持词表顺序。
     * 词表里 `comp` 的第一条是 `company`，用户连着选了两次 `computer`，那之后的第一条就该是它。
     */
    @Test
    fun `a completion picked twice for a code moves to the front`() {
        val profile = learner()
        val engine = EnglishEngine(profile)
        assertEquals("company", engine.evaluate("comp").candidates[1].text)

        repeat(2) { profile.recordChoice(code = "comp", text = "Computer", reading = "") }

        val output = engine.evaluate("Comp")
        assertEquals("Comp", output.candidates[0].text)
        // 大小写跟着打出来的走，习惯却只看词本身（"Computer" 与 "computer" 是同一个）。
        assertEquals("Computer", output.candidates[1].text)
    }
}

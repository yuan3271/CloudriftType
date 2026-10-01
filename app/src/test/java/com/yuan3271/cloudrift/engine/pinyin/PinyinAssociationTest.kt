package com.yuan3271.cloudrift.engine.pinyin

import com.yuan3271.cloudrift.engine.CandidateKind
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 打完一个词，联想下一个词.
 *
 * The point of these tests is not that a particular word comes first - the corpus is small and
 * hand-written, so that would be tuning against the fixture. It is that the wiring works end to
 * end: a committed word reaches the association table, a *sentence* commit still finds the word
 * at its tail to look up, and an engine with no table offers nothing instead of guessing.
 */
class PinyinAssociationTest {

    private lateinit var engine: PinyinEngine

    @Before
    fun setUp() {
        val dictionary = PinyinDictionary.fromReaders(
            charTable = { reader("pinyin_chars.txt") },
            wordTable = { reader("pinyin_words.txt") },
            bigramTable = { reader("pinyin_bigrams.txt") },
        )
        dictionary.load()
        engine = PinyinEngine(dictionary, nineKey = false)
    }

    @Test
    fun `a committed word offers what tends to follow it`() {
        val predictions = engine.associations("今天", limit = 8)

        assertTrue("今天 should have successors, got ${predictions.map { it.text }}", predictions.isNotEmpty())
        assertEquals(CandidateKind.Prediction, predictions.first().kind)
        assertEquals(0, predictions.first().consumed)
        assertTrue(
            "expected 天气 among ${predictions.take(3).map { it.text }}",
            predictions.take(3).any { it.text == "天气" },
        )
    }

    @Test
    fun `picking one prediction can chain into the next`() {
        // Two hops of the same lookup: 今天 -> 天气 -> ...
        val first = engine.associations("今天", limit = 8).first()
        val second = engine.associations(first.text, limit = 8)

        assertTrue("联想 should chain, second hop had none", second.isNotEmpty())
    }

    @Test
    fun `a sentence commit predicts from the word at its tail`() {
        // The whole string is not in the table, and neither are its 4 and 3 character tails, so
        // the lookup has to fall back to 天气 - otherwise picking a sentence candidate would end
        // the 联想 strip entirely.
        val fromSentence = engine.associations("他说今天天气", limit = 8)
        val fromWord = engine.associations("天气", limit = 8)

        assertEquals(fromWord.map { it.text }, fromSentence.map { it.text })
    }

    @Test
    fun `an engine without an association table offers nothing`() {
        val plain = PinyinDictionary.fromReaders(
            charTable = { reader("pinyin_chars.txt") },
            wordTable = { reader("pinyin_words.txt") },
        )
        plain.load()

        assertTrue(PinyinEngine(plain, nineKey = false).associations("今天", limit = 8).isEmpty())
    }

    @Test
    fun `nothing known means no predictions, not a guess`() {
        assertTrue(engine.associations("阿坝藏族羌族自治州", limit = 8).isEmpty())
    }

    /**
     * A scoreboard for the strip itself, in the same spirit as the sentence benchmark: the numbers
     * are printed on every run so a change to the corpus or the scoring that quietly makes the
     * predictions worse is visible. The floor is deliberately loose - the set is small and the
     * corpus it is checked against is small too, so this guards against breakage, not against a
     * point of ranking.
     */
    @Test
    fun `the strip usually holds the word that comes next`() {
        var hits = 0
        val misses = ArrayList<String>()
        for ((context, expected) in CONTINUATIONS) {
            val predictions = engine.associations(context, limit = 5).map { it.text }
            if (expected in predictions) {
                hits++
            } else {
                misses += "$context -> ${predictions.take(3)} (期望 $expected)"
            }
        }
        val total = CONTINUATIONS.size
        println("联想 top5: $hits/$total")
        misses.forEach { println("  miss  $it") }

        assertTrue("联想命中率过低: $hits/$total", hits >= total * 2 / 3)
    }

    private fun reader(name: String) = BufferedReader(
        InputStreamReader(FileInputStream(File("src/main/assets", name)), Charsets.UTF_8),
    )

    private companion object {
        /** word that went in -> a word a person would naturally type after it. */
        val CONTINUATIONS: List<Pair<String, String>> = listOf(
            "今天" to "天气",
            "明天" to "要",
            "晚上" to "早点",
            "早上" to "好",
            "手机" to "快",
            "谢谢" to "你",
            "工作" to "忙",
            "时间" to "一起",
            "什么" to "时候",
            "我们" to "去",
            "天气" to "好",
            "一起" to "吃",
            "可以" to "接受",
            "几点" to "了",
        )
    }
}

package com.yuan3271.cloudrift.engine.pinyin

import com.yuan3271.cloudrift.engine.CandidateKind
import com.yuan3271.cloudrift.data.StringStore
import com.yuan3271.cloudrift.data.UserProfile
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exercises the real generated tables rather than a fixture, so a regression in
 * `tools/dictgen` or in the dictionary loader shows up here.
 */
class PinyinEngineTest {

    private lateinit var dictionary: PinyinDictionary

    @Before
    fun setUp() {
        dictionary = PinyinDictionary.fromReaders(
            charTable = { reader("pinyin_chars.txt") },
            wordTable = { reader("pinyin_words.txt") },
        )
        dictionary.load()
    }

    @Test
    fun `words are preferred over single characters`() {
        val output = PinyinEngine(dictionary, nineKey = false).evaluate("nihao")

        assertEquals("你好", output.candidates.first().text)
        assertEquals(5, output.candidates.first().consumed)
    }

    @Test
    fun `a bare syllable offers characters`() {
        val output = PinyinEngine(dictionary, nineKey = false).evaluate("ni")
        val first = output.candidates.first()

        assertEquals("你", first.text)
        assertEquals(CandidateKind.Character, first.kind)
    }

    @Test
    fun `long readings still resolve to a word`() {
        val output = PinyinEngine(dictionary, nineKey = false).evaluate("zhongguo")

        assertTrue(
            "expected 中国 among ${output.candidates.take(3).map { it.text }}",
            output.candidates.take(3).any { it.text == "中国" },
        )
    }

    @Test
    fun `committing a single character leaves the rest of the buffer`() {
        val engine = PinyinEngine(dictionary, nineKey = false)
        val output = engine.evaluate("nihao")
        val single = output.candidates.first { it.kind == CandidateKind.Character && it.text == "你" }

        assertEquals(2, single.consumed)
        // "nihao".drop(2) == "hao", which must still produce candidates on its own.
        val rest = engine.evaluate("nihao".substring(single.consumed))
        assertTrue(rest.candidates.isNotEmpty())
    }

    @Test
    fun `nine key input maps digits back to words`() {
        val engine = PinyinEngine(dictionary, nineKey = true)
        val output = engine.evaluate("64426")

        assertTrue(
            "expected 你好 among ${output.candidates.take(5).map { it.text }}",
            output.candidates.take(5).any { it.text == "你好" },
        )
    }

    @Test
    fun `nine key input converts a single syllable`() {
        val output = PinyinEngine(dictionary, nineKey = true).evaluate("64")

        assertTrue(
            "expected 你 among ${output.candidates.take(6).map { it.text }}",
            output.candidates.take(6).any { it.text == "你" },
        )
    }

    @Test
    fun `an unknown reading is not silently dropped`() {
        val output = PinyinEngine(dictionary, nineKey = false).evaluate("zzz")

        assertTrue(output.candidates.isNotEmpty())
        assertEquals("zzz", output.candidates.first().text)
    }

    @Test
    fun `a word shows up before the whole reading is typed`() {
        // The complaint that started this: 你好 has to be there after "nih", not only after "nihao".
        val output = PinyinEngine(dictionary, nineKey = false).evaluate("nih")

        assertEquals("你好", output.candidates.first().text)
        assertEquals(3, output.candidates.first().consumed)
    }

    @Test
    fun `a finished syllable already suggests the words it opens`() {
        val output = PinyinEngine(dictionary, nineKey = false).evaluate("ni")
        val texts = output.candidates.map { it.text }

        assertEquals("你", texts.first())
        assertTrue("expected 你好 among ${texts.take(3)}", texts.take(3).contains("你好"))
    }

    @Test
    fun `the untyped tail of a completion is marked for the bar`() {
        val engine = PinyinEngine(dictionary, nineKey = false)

        val partial = engine.evaluate("nih").candidates.first { it.text == "你好" }
        val typed = engine.evaluate("nihao").candidates.first { it.text == "你好" }

        // "nih" spells 你 only, so 好 has to be drawn as not typed yet.
        assertEquals(1, partial.unmatchedFrom)
        assertEquals(-1, typed.unmatchedFrom)
    }

    @Test
    fun `a whole sentence is decoded in one go`() {
        val output = PinyinEngine(dictionary, nineKey = false).evaluate("wojintianqulebeijing")

        assertEquals("我今天去了北京", output.candidates.first().text)
        assertEquals(20, output.candidates.first().consumed)
    }

    @Test
    fun `the white part follows the split the buffer actually spells`() {
        val engine = PinyinEngine(dictionary, nineKey = false)

        // 西安 reads "xian" as xi + an, so after "xi" its first character is already finished even
        // though the reading cannot be split at "xi" on its own.
        val xian = engine.evaluate("xi").candidates.first { it.text == "西安" }

        assertEquals(1, xian.unmatchedFrom)
    }

    @Test
    fun `sentences of several words decode as a whole`() {
        val engine = PinyinEngine(dictionary, nineKey = false)
        val expected = mapOf(
            "nizaiganma" to "你在干吗",
            "zhegeshoujizhenbucuo" to "这个手机真不错",
            "wojintianqulebeijing" to "我今天去了北京",
        )

        for ((code, sentence) in expected) {
            assertEquals(sentence, engine.evaluate(code).candidates.first().text)
        }
    }

    @Test
    fun `a half typed last syllable still completes the word`() {
        // The complaint that started this: 百度百科 has to show up while the 科 is only a "k".
        val engine = PinyinEngine(dictionary, nineKey = false)
        val candidate = engine.evaluate("baidubaik").candidates.first()

        assertEquals("百度百科", candidate.text)
        // Picking it has to consume the whole buffer, including the half typed "k".
        assertEquals(9, candidate.consumed)
        // 百度百 are spelled out, 科 is the character the keyboard is filling in.
        assertEquals(3, candidate.unmatchedFrom)
    }

    @Test
    fun `a whole word still outranks a sentence guess with the same prefix`() {
        val engine = PinyinEngine(dictionary, nineKey = false)

        // 试试看 is a real word for "shishik"; the decoder can also build 实施 + 可, and the real
        // word has to win.
        assertEquals("试试看", engine.evaluate("shishik").candidates.first().text)
    }

    @Test
    fun `a reading with several words offers all of them`() {
        val texts = PinyinEngine(dictionary, nineKey = false).evaluate("shishi").candidates.map { it.text }

        assertTrue("expected 实施 among $texts", texts.contains("实施"))
        assertTrue("expected 试试 among $texts", texts.contains("试试"))
        assertTrue("expected 事实 among $texts", texts.contains("事实"))
    }

    @Test
    fun `candidates run from long to short`() {
        val engine = PinyinEngine(dictionary, nineKey = false)
        val texts = engine.evaluate("shishizhege").candidates.map { it.text }
        println("shishizhege -> " + texts.take(12))

        // The same-sound sentences first, then the words that make them up, then single characters.
        assertTrue("expected 实施这个 among $texts", texts.contains("实施这个"))
        assertTrue("expected 试试这个 among $texts", texts.contains("试试这个"))
        val sentence = texts.indexOfFirst { it == "实施这个" || it == "试试这个" }
        val word = texts.indexOfFirst { it == "实施" || it == "试试" }
        val single = texts.indexOfFirst { it.length == 1 }
        assertTrue("sentences before words", sentence in 0 until word)
        assertTrue("words before single characters", word in 0 until single)
    }

    @Test
    fun `one letter offers the characters of the syllables it can open`() {
        val output = PinyinEngine(dictionary, nineKey = false).evaluate("n")
        val texts = output.candidates.map { it.text }

        assertTrue("expected more than the single 嗯, got $texts", texts.size > 3)
        assertTrue("expected 你 among $texts", texts.contains("你"))
    }

    @Test
    fun `character candidates never carry the weight column of the asset`() {
        // The character table is `syllable<TAB>chars<TAB>weight`; reading it as one tail used to
        // put 1 2 2 1 5 9 among the candidates of any short syllable.
        val engine = PinyinEngine(dictionary, nineKey = false)
        for (code in listOf("a", "n", "ni", "ai")) {
            val characters = engine.evaluate(code).candidates
                .filter { it.kind == CandidateKind.Character }
                .map { it.text }
            assertTrue(
                "$code leaked non characters: $characters",
                characters.all { it.length == 1 && it[0].code in 0x3400..0x9FFF },
            )
        }
        // "n" has one character and a weight column; the parser must not glue the two together.
        assertEquals("嗯", dictionary.charsFor("n", 8))
        assertEquals(20, dictionary.charsFor("ai", 20).length)
    }

    @Test
    fun `a learned habit puts the users own choice first`() {
        val store = object : StringStore {
            private val values = HashMap<String, String>()
            override fun read(key: String): String? = values[key]
            override fun write(key: String, value: String) {
                values[key] = value
            }
        }
        val learner = UserProfile(store, CoroutineScope(Dispatchers.Unconfined))
        repeat(2) { learner.recordChoice(code = "ni", text = "你好", reading = "nihao") }

        val engine = PinyinEngine(dictionary, nineKey = false, profile = learner)

        assertEquals("你好", engine.evaluate("ni").candidates.first().text)
    }

    @Test
    fun `a word the user spelled out becomes a candidate`() {
        val store = object : StringStore {
            private val values = HashMap<String, String>()
            override fun read(key: String): String? = values[key]
            override fun write(key: String, value: String) {
                values[key] = value
            }
        }
        val learner = UserProfile(store, CoroutineScope(Dispatchers.Unconfined))
        learner.rememberWord(reading = "zhangweilaile", word = "张伟来了")

        val engine = PinyinEngine(dictionary, nineKey = false, profile = learner)

        assertEquals("张伟来了", engine.evaluate("zhangweilaile").candidates.first().text)
    }

    private fun reader(name: String) = BufferedReader(
        InputStreamReader(FileInputStream(File("src/main/assets", name)), Charsets.UTF_8),
    )
}

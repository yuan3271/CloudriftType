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
        // 读不出任何拼音的字母串必须留在候选里（随时能整串上屏），但不再抢占第一位——它现在是
        // 列表里的最后一项，前面是引擎读出来的东西。见 [PinyinEngine.rawLast] 的注释。
        assertTrue("原样字母仍在候选里: ${output.candidates.map { it.text }}", output.candidates.any { it.text == "zzz" })
    }

    @Test
    fun `a guessed second reading never displaces the word that owns it`() {
        // 半 is listed bàn,pàn, so the per-character product used to file 半点 under "pandian" as
        // well - and because 半点 is the more common word, it came out *first* for anyone typing
        // 盘点. The reading belongs to 盘点 and the guess has no business leading it.
        val output = PinyinEngine(dictionary, nineKey = false).evaluate("pandian")
        val texts = output.candidates.map { it.text }

        assertEquals(texts.toString(), "盘点", texts.first())
        assertTrue("expected no 半点 under pandian, got $texts", "半点" !in texts)
    }

    @Test
    fun `genuinely polyphonic words keep their reading`() {
        val engine = PinyinEngine(dictionary, nineKey = false)

        // 重 is zhòng,chóng and 行 is xíng,háng: both words are read with the second reading and
        // have to survive the rule above, which only drops readings another word already spells.
        assertEquals("重新", engine.evaluate("chongxin").candidates.first().text)
        assertEquals("银行", engine.evaluate("yinhang").candidates.first().text)
    }

    @Test
    fun `a word shows up before the whole reading is typed`() {
        // The complaint that started this: 你好 has to be there after "nih", not only after "nihao".
        val output = PinyinEngine(dictionary, nineKey = false).evaluate("nih")

        assertEquals("你好", output.candidates.first().text)
        assertEquals(3, output.candidates.first().consumed)
    }

    @Test
    fun `a finished syllable offers characters, not the words it could open`() {
        // Typing "kan" means 看. 看到 starts with the same syllable, but its 到 has no letter
        // behind it at all - the word only becomes an answer once the d is typed. Before this the
        // bar was a list of every word that happens to begin with the syllable, and 看 was the
        // only single character in it.
        val engine = PinyinEngine(dictionary, nineKey = false)

        val texts = engine.evaluate("kan").candidates.map { it.text }
        assertEquals("看", texts.first())
        assertTrue("kan must offer single characters only: $texts", texts.none { it.length > 1 })

        // The next syllable's initial is that letter, and that is the whole difference.
        assertEquals("看到", engine.evaluate("kand").candidates.first().text)
    }

    @Test
    fun `a completion needs the beginning of every character it adds`() {
        // 西安 reads "xian" as xi + an, so after "xi" the 安 has nothing behind it yet: the bar
        // offers 西/洗/系/喜 and not 西安. Typing the reading out reaches it.
        val engine = PinyinEngine(dictionary, nineKey = false)

        assertTrue(
            "xi must not complete to 西安: ${engine.evaluate("xi").candidates.map { it.text }}",
            engine.evaluate("xi").candidates.none { it.text == "西安" },
        )
        assertEquals("西安", engine.evaluate("xian").candidates.first().text)
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
    fun `a sentence final particle is not buried by the word that shares its sound`() {
        // 毫巴 is a real word (the news corpus knows it), 好吗 is not a word at all - and the two
        // consume exactly the same input, so no frequency can separate them. What separates them
        // is that nobody ends a sentence with 巴 or 麦, and everybody ends one with 吧 and 吗.
        val engine = PinyinEngine(dictionary, nineKey = false)

        assertEquals("好吧", engine.evaluate("haoba").candidates.first().text)
        assertEquals("是吗", engine.evaluate("shima").candidates.first().text)
        assertEquals("是吧", engine.evaluate("shiba").candidates.first().text)
        assertEquals("好吗", engine.evaluate("haoma").candidates.first().text)
        // 呀 is not even among the first three characters of "ya"; a sentence can still end on it.
        assertEquals("好呀", engine.evaluate("haoya").candidates.first().text)
    }

    @Test
    fun `the pair model still chooses between two particles of one syllable`() {
        // 吗 and 嘛 are both 句末语气词 and both read "ma". 你在干吗 is the standard spelling and
        // 干嘛 is the colloquial one, so the choice between them stays the corpus's, not the
        // particle rule's: that rule only lifts a particle over the *ordinary* character of its
        // syllable (呀 over 压, 啦 over 拉), never over another particle.
        val engine = PinyinEngine(dictionary, nineKey = false)

        assertEquals("你在干吗", engine.evaluate("nizaiganma").candidates.first().text)
        assertEquals("好了吗", engine.evaluate("haolema").candidates.first().text)
    }

    @Test
    fun `a sentence does not open on a final particle`() {
        // 搭配表多了一行"句子开头"的上下文（`^`），而且只作用在**词**上：
        //  - `ma` 的首选字是 吗，而 吗 不会是一句话的开头——挡住它之后，`mashangle` 里的 马上
        //    才有机会赢（原来这里是 吗上了）；
        //  - 句首证据不发给单字，否则 事|是|这个 会因为"是"常见而压过整词 实施。
        val paired = PinyinDictionary.fromReaders(
            charTable = { reader("pinyin_chars.txt") },
            wordTable = { reader("pinyin_words.txt") },
            bigramTable = { reader("pinyin_bigrams.txt") },
        )
        paired.load()
        val engine = PinyinEngine(paired, nineKey = false)

        assertEquals("马上了", engine.evaluate("mashangle").candidates.first().text)
        // 词频单独看时 下午 更常见；语料说 下雨 才是一句话常见的开头，所以 xiayu 现在给 下雨。
        assertEquals("下雨", engine.evaluate("xiayu").candidates.first().text)
        // 单音节输入不受影响：`ma` 仍是 吗（它就是一个可以单独说的字）。
        assertEquals("吗", engine.evaluate("ma").candidates.first().text)
        // 叹词照旧可以开头，被挡掉的只有句末语气词。
        assertTrue(engine.evaluate("a").candidates.first().text == "啊")
    }

    @Test
    fun `a character is filed under every reading it is really used with`() {
        // pinyin-data lists a character's readings but carries no counts, and the character table
        // only filed the first one. Unihan's 读音频率 (《现代汉语频率词典》) is what says 乐 is read
        // yuè in 音乐, 得 is děi in 得走了, and 谁 is shéi in speech - so those characters now sit
        // under both syllables. "shei" had *no character at all* before this.
        assertTrue("shei -> ${dictionary.charsFor("shei", 8)}", dictionary.charsFor("shei", 8).contains("谁"))
        assertTrue("dei -> ${dictionary.charsFor("dei", 8)}", dictionary.charsFor("dei", 8).contains("得"))
        assertTrue("yue -> ${dictionary.charsFor("yue", 40)}", dictionary.charsFor("yue", 40).contains("乐"))
        assertTrue("hang -> ${dictionary.charsFor("hang", 20)}", dictionary.charsFor("hang", 20).contains("行"))
        assertTrue("de -> ${dictionary.charsFor("de", 8)}", dictionary.charsFor("de", 8).contains("地"))

        // The primary placement keeps its own syllable, and keeps its rank there.
        assertTrue(dictionary.charsFor("shui", 8).contains("谁"))
        assertTrue(dictionary.charsFor("ge", 8).contains("个"))
    }

    @Test
    fun `the strip keeps the endings a written corpus under-reports`() {
        // A sentence corpus writes 好主意 and 好用; someone typing 好 next usually wants 好吗 /
        // 好吧 / 好呢. The floor lifts an ending the corpus has seen at all into the visible part
        // of the 联想 strip without letting it overtake what the corpus is sure about.
        val paired = PinyinDictionary.fromReaders(
            charTable = { reader("pinyin_chars.txt") },
            wordTable = { reader("pinyin_words.txt") },
            bigramTable = { reader("pinyin_bigrams.txt") },
        )
        paired.load()
        val engine = PinyinEngine(paired, nineKey = false)

        val afterHao = engine.associations("好", 6).map { it.text }
        assertTrue("expected an ending among $afterHao", afterHao.any { it in listOf("吗", "吧", "呢") })
        assertEquals("天气", engine.associations("今天", 6).first().text)
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
        // The bar for "nih" leads with 你好; picking 拟合 instead, twice, is exactly the observation
        // that has to overrule it the next time.
        repeat(2) { learner.recordChoice(code = "nih", text = "拟合", reading = "nihe") }

        val engine = PinyinEngine(dictionary, nineKey = false, profile = learner)

        assertEquals("拟合", engine.evaluate("nih").candidates.first().text)
        // A habit is keyed by the code and every prefix of it, so the same choice still speaks for
        // the longer code the user types next.
        assertEquals("拟合", engine.evaluate("nihe").candidates.first().text)
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

    @Test
    fun `initials index knows the everyday words`() {
        // 机制层：首字母索引里必须有 你好，否则"nh"永远不可能给出它。
        val bucket = dictionary.wordsForInitials("nh", 60).map { it.word }
        assertTrue("你好 must be indexed: $bucket", bucket.contains("你好"))

        // 引擎层：目前"nh"的候选以 南河/拟合 这类语料高频词开头，你好 还在更后面——
        // 排序要达标得把"音节自然度 + 常用词加权"下沉到词典桶分数（见 PLAN 待办）。
        val texts = PinyinEngine(dictionary, nineKey = false).evaluate("nh").candidates.map { it.text }
        assertTrue("nh should still offer something: $texts", texts.isNotEmpty())
    }

    @Test
    fun `initials never shadow a real reading`() {
        val engine = PinyinEngine(dictionary, nineKey = false)

        // "nihao" is a real reading, so it keeps its exact match first.
        assertEquals("你好", engine.evaluate("nihao").candidates.first().text)
        // A single letter still means characters, not a wall of words.
        assertEquals("你", engine.evaluate("ni").candidates.first().text)
    }

    @Test
    fun `a readable full pinyin word is never buried by a 简拼 reading of the same letters`() {
        // The bug this guards: "wo" also reads as w|o = 无藕, "women" as 无藕木耳南, "shang" as
        // 时候按那个, "hao" as 黑暗藕. Those letters spell real pinyin, so the answer has to be 我 /
        // 我们 / 上 / 好 - 简拼 is only an alternative when full pinyin cannot read the buffer at all.
        // None of these contain i/u/ü, so they used to be the everyday words that broke.
        val engine = PinyinEngine(dictionary, nineKey = false)
        val cases = listOf(
            "wo" to "我", "ta" to "他", "women" to "我们", "tamen" to "他们",
            "zhege" to "这个", "nage" to "那个", "shang" to "上", "hao" to "好", "ma" to "吗",
            "zhende" to "真的", "zenme" to "怎么", "shenme" to "什么", "mashang" to "马上",
            "haode" to "好的", "wanshang" to "晚上", "ganshenme" to "干什么",
            "zenmeyang" to "怎么样", "zenmeban" to "怎么办",
        )
        for ((code, expected) in cases) {
            assertEquals("$code 被首字母劫持了", expected, engine.evaluate(code).candidates.first().text)
        }
    }

    @Test
    fun `简拼 is still reached after the fix`() {
        // The other direction of the same requirement: removing the interference must not remove
        // the feature. "nh" is consonant only, so it is exactly what 简拼 is for.
        val engine = PinyinEngine(dictionary, nineKey = false)

        assertEquals("你好", engine.evaluate("nh").candidates.first().text)
        val phrase = engine.evaluate("jtzmy").candidates.map { it.text }
        assertTrue("expected a 今天 segmentation among $phrase", phrase.any { it.startsWith("今天") })
    }

    @Test
    fun `简拼 does not invent a phrase out of lone characters`() {
        // Every run of letters fits this shape, so it is noise, not an answer: "zzz" -> 组织 via the
        // word index would leave the raw letters behind, and 在在 is two lone characters. A 简拼
        // reading has to actually touch a word, which is what keeps the feature from spraying
        // unrelated characters over the candidate bar.
        val texts = PinyinEngine(dictionary, nineKey = false).evaluate("zzz").candidates.map { it.text }

        assertTrue("lone characters must not be offered as a phrase: $texts", texts.none { it == "在在" })
        assertTrue("原样字母仍可整串上屏: $texts", texts.contains("zzz"))
    }

    @Test
    fun `a rare reading does not shadow the word that owns the reading`() {
        // pinyin-data lists 盒 as "hé, ān", so the cartesian product invented a reading 试剂盒 =
        // "shijian" for a word that never reads that way, and it pushed 时间 off the first slot.
        // The build now drops a reading a word only reaches through an alternate character when a
        // word that spells the same syllables with primary readings is much more common.
        val engine = PinyinEngine(dictionary, nineKey = false)

        assertEquals("时间", engine.evaluate("shijian").candidates.first().text)
    }

    @Test
    fun `a sentence final particle follows the pair model`() {
        // ba is both 把 and 吧 and the character table ranks 把 first, so the decoder used to force
        // 我们走把. Letting a lower-ranked homophone in only when the pair model expects it here
        // (走 -> 吧) is what makes the sentence come out right - so this engine has to be wired with
        // the association table, unlike the rest of this class.
        val paired = PinyinDictionary.fromReaders(
            charTable = { reader("pinyin_chars.txt") },
            wordTable = { reader("pinyin_words.txt") },
            bigramTable = { reader("pinyin_bigrams.txt") },
        )
        paired.load()
        val engine = PinyinEngine(paired, nineKey = false)

        assertEquals("我们走吧", engine.evaluate("womenzouba").candidates.first().text)
    }

    @Test
    fun `an unconvertible syllable does not take the whole sentence down`() {
        // "jidangeng" splits as ji + dang + eng under longest match, and "eng" is a syllable of the
        // table with no character and no word of its own. The sentence lattice then dead-ends on its
        // last position and the bar showed 激荡 plus single characters - nothing at all for a long
        // buffer, because the longer the run the likelier one syllable of it is unusable. Splitting
        // with one step of lookahead (ji + dan + geng) is what lets the sentence survive.
        val paired = PinyinDictionary.fromReaders(
            charTable = { reader("pinyin_chars.txt") },
            wordTable = { reader("pinyin_words.txt") },
            bigramTable = { reader("pinyin_bigrams.txt") },
        )
        paired.load()
        val engine = PinyinEngine(paired, nineKey = false)

        assertEquals("鸡蛋羹", engine.evaluate("jidangeng").candidates.first().text)
        assertEquals(
            "今天的晚饭是红烧肉和鸡蛋羹",
            engine.evaluate("jintiandewanfanshihongshaorouhejidangeng").candidates.first().text,
        )
    }

    @Test
    fun `a spoken word outranks the news corpus favourite`() {
        // jieba (a news corpus) puts 美食 above 没事; the self-authored colloquial corpus has it the
        // other way round, and that is the order an IME should follow.
        val engine = PinyinEngine(dictionary, nineKey = false)

        assertEquals("没事", engine.evaluate("meishi").candidates.first().text)
    }

    @Test
    fun `a run of initials decodes into a phrase`() {
        val engine = PinyinEngine(dictionary, nineKey = false)
        val candidates = engine.evaluate("jtzmy").candidates
        val texts = candidates.map { it.text }
        println("jtzmy -> " + candidates.take(8).map { "${it.text}[c=${it.consumed}]" })

        // 分段解码本身通了（今天 + 芝麻/几天 + 芝麻 都是合法分词），但"今天怎么样"要排到前面，
        // 同样取决于把常用词加权下沉到词典桶；这里先锁定"能按简拼分段"这一层。
        assertTrue("expected a 今天 segmentation among $texts", texts.any { it.startsWith("今天") })
    }

    @Test
    fun `debug initials bucket`() {
        println("nh bucket: " + dictionary.wordsForInitials("nh", 40).map { it.word })
        println("jt bucket: " + dictionary.wordsForInitials("jt", 20).map { it.word })
    }
}

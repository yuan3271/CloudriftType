package com.yuan3271.cloudrift.engine.pinyin

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 整句候选要过"这段话在语料里真的连过"这一关。
 *
 * 组合解码能拼出无穷多读法，而多数不是中文：`毫无|青年`、`辛苦|里`、`转账|一|不错` 每一段都是
 * 词典里的词或常用字，相邻两段却在语料里从来没一起出现过。候选栏铺满这类读法，用户看到的就是
 * 一整屏"不成句的结果"。
 *
 * 但这条线只能划在"一整串读法一条证据都没有"上。测试同时锁住另一头：语料没连过、却确实是用户
 * 想打的句子（`那|挺|好|的`、`票|买|好|了|吗`）必须原样留在候选里——它们和噪声长得一模一样，
 * 区别只在用户心里，所以宁可少删也不能多删。词组、专名是词典词，不受这条线影响。
 */
class PinyinAttestationTest {

    private lateinit var dictionary: PinyinDictionary

    @Before
    fun setUp() {
        dictionary = PinyinDictionary.fromReaders(
            charTable = { reader("pinyin_chars.txt") },
            wordTable = { reader("pinyin_words.txt") },
            bigramTable = { reader("pinyin_bigrams.txt") },
        )
        dictionary.load()
    }

    @Test
    fun `a buffer with no supporting pair anywhere collapses to one reading`() {
        // `hao wu qing nian` 拆出来的每一段单独看都是词，相邻两段在语料里却一次都没连过——
        // 这是"这串音节根本没被语料读到过"，不是"挑错了同音字"。留一条最好的猜测（候选栏不能
        // 空），其余整排同音字兄弟不再铺出来。
        val texts = PinyinEngine(dictionary, nineKey = false).evaluate("haowuqingnian")
            .candidates.map { it.text }

        assertTrue("最好的猜测应该留着: $texts", texts.contains("毫无青年"))
        for (noise in listOf("毫无请年", "好无青年", "好无情年", "好恶青年", "好无请年")) {
            assertFalse("$noise 是同音字洗牌，不该出现: $texts", texts.contains(noise))
        }
    }

    @Test
    fun `the sentence the user meant outranks its homophone neighbours`() {
        // 这几条都是"别的拼音逻辑挡住了不成句逻辑"的现场：解码器要么根本拼不出正确读法
        // （`杯` 是 bei 的第 6 个字，只探索前三个字时它进不来），要么把正确读法的上下文丢了
        // （`今天|天气` 与词典词 `今天天气` 撞成同一个文本）。它们排不到第一时，后面的非句过滤
        // 无从谈起——正确读法得先在候选里站住。
        val cases = listOf(
            "jintiantianqihenhao" to "今天天气很好",
            "woxianghebeikafei" to "我想喝杯咖啡",
            "natinghaode" to "那挺好的",
            "piaomaihaolema" to "票买好了吗",
        )
        val engine = PinyinEngine(dictionary, nineKey = false)
        for ((code, sentence) in cases) {
            assertEquals("$code 的第一名不是它想打的那句", sentence, engine.evaluate(code).candidates.first().text)
        }
    }

    @Test
    fun `a normal sentence whose joins the corpus missed is never deleted`() {
        // 这几句的头名读音每一段都是词典里有的字词，只是那一对在语料里没连过。删掉它们就等于
        // 用搭配表去否定用户真正想打的话——这正是回归台量出来、必须挡住的那一头。
        val cases = listOf(
            "natinghaode" to "那挺好的",
            "xinhaobutaihao" to "信号不太好",
            "yanjingyoudianlei" to "眼睛有点累",
            "piaomaihaolema" to "票买好了吗",
        )
        val engine = PinyinEngine(dictionary, nineKey = false)
        for ((code, sentence) in cases) {
            val texts = engine.evaluate(code).candidates.map { it.text }
            assertTrue("$code 的正常句子被删了: $texts", texts.contains(sentence))
        }
    }

    @Test
    fun `phrases and names are unaffected by the sentence filter`() {
        val engine = PinyinEngine(dictionary, nineKey = false)

        assertEquals("中国人民大学", engine.evaluate("zhongguorenmindaxue").candidates.first().text)
        assertEquals("北京大学", engine.evaluate("beijingdaxue").candidates.first().text)
    }

    @Test
    fun `a dictionary without an association model keeps every reading`() {
        // 没有搭配表时 bigramScore 对每一对都返回 0，那是"无从知道"，不是"语料里没连过"。把它
        // 当成没证据，整句候选会被清空——测试用的词典正是这一种。
        val plain = PinyinDictionary.fromReaders(
            charTable = { reader("pinyin_chars.txt") },
            wordTable = { reader("pinyin_words.txt") },
        )
        plain.load()

        val texts = PinyinEngine(plain, nineKey = false).evaluate("shishizhege").candidates.map { it.text }

        assertTrue("没有搭配表时依然要给出整句读法: $texts", texts.contains("试试这个"))
        assertTrue("没有搭配表时依然要给出整句读法: $texts", texts.contains("实施这个"))
    }

    private fun reader(name: String) = BufferedReader(
        InputStreamReader(FileInputStream(File("src/main/assets", name)), Charsets.UTF_8),
    )
}

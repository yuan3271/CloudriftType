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
 * 混合输入：一串输入里，一部分音节打全拼，另一部分只打首字母。
 *
 * 这是 [PinyinEngineTest] 里"全拼"和"简拼"两条通路之间的缝：以前只打首字母（`nh`）能出
 * 你好，整串全拼（`nihao`）也能出你好，但一半一半（`nhao`）两条路都走不到——全拼那条被
 * 单字母音节 `n` 骗过去（n|hao = 嗯好），首字母那条又要求每个字母都是一个声母，`hao` 读
 * 不成。这里锁定的是"混着打也必须出字"，并且顺带锁定它不许抢走整串全拼的位置。
 */
class PinyinMixedTest {

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
    fun `首字母接全拼`() {
        // n(简拼) + hao(全拼) —— 以前只会给出 嗯好，现在 你好 就在第一个。
        assertTop1("nhao", "你好")
        // j(简拼) + tian(全拼)：整个"今天"就是这一个混合形式。
        assertTop1("jtian", "今天")
        // w(简拼) + zhidao(全拼)
        assertTop1("wzhidao", "我知道")
        // n(简拼) + zhidao(全拼)：n 能代表的字不止一个（难/那/你…），你知道 必须在前面几个。
        assertInTop("nzhidao", "你知道", 3)
    }

    @Test
    fun `全拼接首字母`() {
        // wo(全拼) + jt(简拼)
        assertTop1("wojt", "我今天")
        // wo(全拼) + jintian(全拼) + qlbj(简拼)：全拼那半段必须原样出现在结果里。
        val long = engine.evaluate("wojintianqlbj").candidates.first()
        assertTrue("first candidate should be ${long.text}", long.text.startsWith("我今天"))
        assertEquals(13, long.consumed)
        // jintian(全拼) + wsm(简拼)
        assertTrue(
            "jintianwsm -> ${engine.evaluate("jintianwsm").candidates.take(5).map { it.text }}",
            engine.evaluate("jintianwsm").candidates.take(10).map { it.text }.any { it.startsWith("今天为") },
        )
    }

    @Test
    fun `混合候选带着标记上屏`() {
        // 整串正好是一个词（n + hao → 你好）：按词典命中处理，标成"混合"。
        val exact = engine.evaluate("nhao").candidates.first()

        assertEquals("你好", exact.text)
        assertEquals(CandidateKind.Conversion, exact.kind)
        assertEquals("混合", exact.annotation)
        // 整串都被覆盖了（n 给了声母，hao 拼全了），所以没有需要变淡的字。
        assertEquals(-1, exact.unmatchedFrom)

        // 由"全拼 + 声母"两半拼出来的阅读标成 Mixed。
        val composed = engine.evaluate("wojt").candidates.first()
        assertEquals("我今天", composed.text)
        assertEquals(CandidateKind.Mixed, composed.kind)
    }

    @Test
    fun `原样字母排在真读音后面`() {
        // nhao 的字母本身读不出整串拼音，以前"原样上屏"会顶在 你好 前面。
        val texts = engine.evaluate("nhao").candidates.map { it.text }

        assertTrue("expected 你好 before the raw letters: $texts", texts.indexOf("你好") < texts.indexOf("nhao"))
        assertTrue("the raw letters stay available: $texts", texts.contains("nhao"))
    }

    @Test
    fun `整串全拼仍然优先于混合读法`() {
        // 能整串读成拼音时，全拼就是答案：混合解码不许把这些顶掉。
        listOf(
            "wo" to "我",
            "women" to "我们",
            "shang" to "上",
            "hao" to "好",
            "wanshang" to "晚上",
            "zenmeyang" to "怎么样",
            "ganshenme" to "干什么",
        ).forEach { (code, expected) -> assertTop1(code, expected) }
    }

    @Test
    fun `纯简拼和纯全拼都没被混合解码带偏`() {
        assertTop1("nh", "你好")
        assertTop1("nihao", "你好")
        assertTop1("wojintianqulebeijing", "我今天去了北京")
        assertTop1("baidubaik", "百度百科")
        assertTrue(
            "jtzmy should still segment into 今天…",
            engine.evaluate("jtzmy").candidates.map { it.text }.any { it.startsWith("今天") },
        )
    }

    private fun assertTop1(code: String, expected: String) {
        val texts = engine.evaluate(code).candidates.map { it.text }
        assertEquals("$code -> $texts", expected, texts.first())
    }

    private fun assertInTop(code: String, expected: String, depth: Int) {
        val texts = engine.evaluate(code).candidates.map { it.text }
        assertTrue("$code -> ${texts.take(depth)} (期望 $expected)", expected in texts.take(depth))
    }

    private fun reader(name: String) = BufferedReader(
        InputStreamReader(FileInputStream(File("src/main/assets", name)), Charsets.UTF_8),
    )
}

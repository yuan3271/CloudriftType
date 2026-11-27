package com.yuan3271.cloudrift.engine.pinyin

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * 现代日常词（应用名、数码、通讯）不进新闻语料，但手机键盘天天打。这份名单由
 * tools/dictgen/clean/net_words.txt + prominent_words.txt 维护，这里锁住它们的**首选**，
 * 免得下一次重生成词表时又被同音词挤下去（微信曾经排在威信后面）。
 */
class PinyinCuratedVocabularyTest {

    private lateinit var engine: PinyinEngine

    @Before
    fun setUp() {
        val dictionary = PinyinDictionary.fromReaders(
            charTable = { reader("pinyin_chars.txt") },
            wordTable = { reader("pinyin_words.txt") },
            bigramTable = { reader("pinyin_bigrams.txt") },
            traditionalChars = { reader("traditional_chars.txt") },
        )
        dictionary.load()
        engine = PinyinEngine(dictionary, nineKey = false)
    }

    @Test
    fun `everyday app and device words come first`() {
        val cases = mapOf(
            "weixin" to "微信",
            "jingdong" to "京东",
            "weibo" to "微博",
            "douyin" to "抖音",
            "kuaishou" to "快手",
            "pinduoduo" to "拼多多",
            "shipin" to "视频",
            "erji" to "耳机",
            "denglu" to "登录",
            "youjian" to "邮件",
            "qingju" to "青桔",
            "feixingmoshi" to "飞行模式",
        )
        for ((pinyin, word) in cases) {
            assertEquals(
                "「" + pinyin + "」的首选应当是「" + word + "」",
                word,
                engine.evaluate(pinyin).candidates.firstOrNull()?.text,
            )
        }
    }

    private fun reader(name: String) = BufferedReader(
        InputStreamReader(FileInputStream(File("src/main/assets", name)), Charsets.UTF_8),
    )
}

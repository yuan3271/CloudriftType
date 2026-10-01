package com.yuan3271.cloudrift.engine.pinyin

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A scoreboard instead of a feeling.
 *
 * Tuning the decoder against hand-picked examples is how 你好 ends up first and 拟合按哦 second:
 * every change fixes one sentence and breaks another, and nothing says which. So the engine is
 * measured against a fixed set of sentences people actually type, twice - once typed out in full
 * pinyin, once as 首字母 - and the numbers are printed on every run.
 *
 * The set is deliberately small and hand-written (greetings, daily questions, a few classical
 * lines) so it can be read and argued about. When a change moves a case from hit to miss, that is
 * visible here rather than in someone's typing.
 *
 * The association model is part of what is measured: the engine under test loads the shipped
 * `pinyin_bigrams.txt`, and [association sweep] additionally scores every candidate table under
 * tools/tune/bigrams, so the shipped table can be picked from numbers instead of taste.
 */
class PinyinBenchmarkTest {

    private lateinit var engine: PinyinEngine

    @Before
    fun setUp() {
        engine = engineWith(shippedBigrams)
    }

    @Test
    fun `full pinyin keeps up with everyday sentences`() {
        val (top1, top5) = score(CASES.map { it.first }, engine)

        println("全拼: top1=$top1/${CASES.size}  top5=$top5/${CASES.size}")
        assertTrue("全拼 top5 命中率过低: $top5/${CASES.size}", top5 >= CASES.size * 3 / 4)
    }

    @Test
    fun `initials reach everyday sentences`() {
        val initials = CASES.map { (pinyin, _) -> initialsOf(pinyin) }
        val (top1, top5) = score(initials, engine)

        println("首字母: top1=$top1/${CASES.size}  top5=$top5/${CASES.size}")
        // Baseline, not a target: printed every run so progress (and regressions) are visible.
        assertTrue("首字母完全打不着任何句子", top1 > 0)
    }

    /**
     * Scores every association table the sweep script wrote, plus the empty one, so a table that
     * helps one script and hurts the other is visible rather than latent.
     */
    @Test
    fun `association sweep`() {
        val directory = File("../tools/tune/bigrams")
        val tables = directory.listFiles { file: File -> file.name.endsWith(".txt") }
            ?.sortedBy { it.name }
        if (tables.isNullOrEmpty()) {
            println("联想表扫描: 没有候选表（先跑 tools/dictgen/build_bigram.py --variant-sweep）")
            return
        }
        val initials = CASES.map { (pinyin, _) -> initialsOf(pinyin) }
        for (table in tables) {
            val variant = engineWith { readerFrom(table) }
            val (full1, full5) = score(CASES.map { it.first }, variant)
            val (init1, init5) = score(initials, variant)
            println(
                "联想表 ${table.name}: 全拼 top1=$full1 top5=$full5  " +
                    "首字母 top1=$init1 top5=$init5",
            )
        }
    }

    private fun score(inputs: List<String>, engine: PinyinEngine): Pair<Int, Int> {
        var top1 = 0
        var top5 = 0
        inputs.forEachIndexed { index, input ->
            val expected = CASES[index].second
            val candidates = engine.evaluate(input).candidates.map { it.text }
            val rank = candidates.indexOf(expected)
            if (rank == 0) top1++
            if (rank in 0 until 5) top5++
            else println("  miss  $input -> ${candidates.take(3)}  (期望 $expected)")
        }
        return top1 to top5
    }

    /** First letter of every syllable, which is what a 首字母 typist would press. */
    private fun initialsOf(pinyin: String): String {
        val initials = StringBuilder()
        var index = 0
        while (index < pinyin.length) {
            var step = 0
            for (end in minOf(pinyin.length, index + PinyinSyllables.maxLength) downTo index + 1) {
                if (PinyinSyllables.isSyllable(pinyin.substring(index, end))) {
                    step = end - index
                    break
                }
            }
            if (step == 0) return pinyin
            initials.append(pinyin[index])
            index += step
        }
        return initials.toString()
    }

    private fun engineWith(bigram: (() -> BufferedReader)?): PinyinEngine {
        val dictionary = PinyinDictionary.fromReaders(
            charTable = { reader("pinyin_chars.txt") },
            wordTable = { reader("pinyin_words.txt") },
            bigramTable = bigram,
        )
        dictionary.load()
        return PinyinEngine(dictionary, nineKey = false)
    }

    private fun reader(name: String) = BufferedReader(
        InputStreamReader(FileInputStream(File("src/main/assets", name)), Charsets.UTF_8),
    )

    private fun readerFrom(file: File) = BufferedReader(
        InputStreamReader(FileInputStream(file), Charsets.UTF_8),
    )

    private val shippedBigrams: () -> BufferedReader = { reader("pinyin_bigrams.txt") }

    private companion object {
        /** pinyin typed in full -> the sentence that should come out (ideally first). */
        val CASES: List<Pair<String, String>> = listOf(
            "nihao" to "你好",
            "zaijian" to "再见",
            "xiexie" to "谢谢",
            "duibuqi" to "对不起",
            "meiguanxi" to "没关系",
            "jintiantianqihenhao" to "今天天气很好",
            "mingtianjidian" to "明天几点",
            "nizaiganma" to "你在干吗",
            "chifanlema" to "吃饭了吗",
            "woxianghebeishui" to "我想喝杯水",
            "womenyiqichifanba" to "我们一起吃饭吧",
            "zhegeshoujizhenbucuo" to "这个手机真不错",
            "wanshangjidianhuijia" to "晚上几点回家",
            "qingwenxishoujianzaina" to "请问洗手间在哪",
            "wojintianqulebeijing" to "我今天去了北京",
            "taqunianmaileche" to "他去年买了车",
            "zhongguorenkouhenduo" to "中国人口很多",
            "xuexishiwomenziji" to "学习是我们自己",
            "shishizhege" to "实施这个",
            "baidubaike" to "百度百科",
            "shenmeshichenggong" to "什么是成功",
            "weishenmebutongyi" to "为什么不同意",
            "zenmecainengxuehao" to "怎么才能学好",
            "mashangjiudao" to "马上就到",
            "bukeyi" to "不可以",
            "huanyingguanglin" to "欢迎光临",
            "shengrikuaile" to "生日快乐",
            "xinxiangshicheng" to "心想事成",
            "yilushunfeng" to "一路顺风",
            "haohaoxuexi" to "好好学习",
            "tiantianxiangshang" to "天天向上",
        )
    }
}

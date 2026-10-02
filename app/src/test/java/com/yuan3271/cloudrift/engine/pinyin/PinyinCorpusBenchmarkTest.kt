package com.yuan3271.cloudrift.engine.pinyin

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.text.Normalizer
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 广谱回归台：把"手挑的 31 句"扩成"整个日常语料逐句跑"。
 *
 * 输入源是本项目自撰的 `tools/dictgen/raw/corpus_*.txt`（MIT，本项目原创，语料本身在仓库里）：
 * 一句话就是"用户想打的字"，测试要回答的是"打得出来吗"。每个句子跑三种输入方式——
 * **全拼**（`jintiantianqihenhao`）、**首字母**（`jttqhh`）、**九键**（数字）——并按句长分桶，
 * 因为解码是音节级最短路径，句子越长、格点越大，长度分桶才看得出问题出在哪一档。
 *
 * 期望值取自语料本身，而这份语料也正是搭配模型的训练文本，所以这里的数字**不是泛化准确率**，
 * 它是一张网：同一条语料在改动前后掉没掉、三种输入方式差多少、长句比短句差多少。
 * 泛化只能靠 PinyinBenchmarkTest 那 31 条手挑用例（它们不在语料里）。
 *
 * 句子 -> 拼音的推导只用仓库里已有的数据：词表给的词读音优先，剩下的字用 pinyin-data 的首读音，
 * 与 build_dict.py 取读音的方式一致。
 */
class PinyinCorpusBenchmarkTest {

    private lateinit var dictionary: PinyinDictionary
    private lateinit var full: PinyinEngine
    private lateinit var initials: PinyinEngine
    private lateinit var nine: PinyinEngine

    @Before
    fun setUp() {
        dictionary = PinyinDictionary.fromReaders(
            charTable = { reader("pinyin_chars.txt") },
            wordTable = { reader("pinyin_words.txt") },
            bigramTable = { reader("pinyin_bigrams.txt") },
        )
        dictionary.load()
        full = PinyinEngine(dictionary, nineKey = false)
        initials = PinyinEngine(dictionary, nineKey = false)
        nine = PinyinEngine(dictionary, nineKey = true)
    }

    @Test
    fun `everyday corpus decodes in all three input modes`() {
        val sentences = corpus().takeIf { it.size >= 100 }
            ?: run { println("语料缺失，跳过广谱回归台"); return }
        val rows = sentences.mapNotNull { sentence ->
            val reading = readingOf(sentence) ?: return@mapNotNull null
            if (reading.length < 3) return@mapNotNull null
            Row(
                text = sentence,
                full = reading,
                initials = initialsOf(reading),
                digits = T9.encode(reading),
            )
        }

        val buckets = listOf(6 to "2–5 字", 11 to "6–10 字", 17 to "11–16 字", Int.MAX_VALUE to "17 字以上")
        println("广谱回归台：${rows.size} 句（自撰语料，训练集内）")
        // 九键不在这里量整句：9 键一键多音节，格点会爆炸，整句解码本来就只在 26 键上做
        // （见 README）。九键有自己的靶子，见 nextWords…
        for ((engine, mode) in listOf(full to "全拼", initials to "首字母")) {
            val cheap = engine
            var top1 = 0
            var top5 = 0
            var rawTop1 = 0
            var top1WithoutRaw = 0
            val misses = ArrayList<String>()
            var from = 0
            for ((limit, label) in buckets) {
                val slice = rows.filter { it.text.length in from until limit }
                var b1 = 0
                var b5 = 0
                for (row in slice) {
                    val input = if (mode == "全拼") row.full else if (mode == "首字母") row.initials else row.digits
                    val hits = cheap.evaluate(input).candidates.map { it.text }
                    val rank = hits.indexOf(row.text)
                    if (rank == 0) b1++
                    if (hits.firstOrNull() == input && input != row.text) rawTop1++
                    if (hits.firstOrNull { it != input } == row.text) top1WithoutRaw++
                    if (rank in 0 until 5) b5++ else misses += "${row.full} -> ${hits.take(3)}（期望 ${row.text}）"
                }
                top1 += b1
                top5 += b5
                println("  $mode $label: top1=$b1/${slice.size} top5=$b5/${slice.size}")
                from = limit
            }
            println("  $mode 合计: top1=$top1/${rows.size} top5=$top5/${rows.size}")
            println("  $mode 诊断: 第一名是原样字母 $rawTop1 次；去掉原样字母后第一名命中 $top1WithoutRaw 次")
            misses.take(8).forEach { println("    miss $it") }
            // 松地板，只防"整块功能坏掉"，不当作目标。首字母天生弱得多（一串声母本来就多解），
            // 所以两条线分开定：全拼 50%、首字母 10%（当前分别是 492/573 与 73/573）。
            val floor = if (mode == "全拼") rows.size / 2 else rows.size / 10
            assertTrue("$mode top5 过低: $top5/${rows.size}", top5 >= floor)
        }
    }

    /**
     * 九键的靶子：**词**。9 键一键多音节，整句解码不在它的职责里（README 写明了），但"打几个
     * 数字出这个词"是它的全部意义。语料里出现过的二字词做样本，输入是它的九键签名。
     */
    @Test
    fun `nine key reaches the two character words people type`() {
        val pairs = LinkedHashSet<String>()
        for (sentence in corpus()) {
            for (index in 0 until sentence.length - 1) {
                val pair = sentence.substring(index, index + 2)
                if (wordReadings.containsKey(pair)) pairs += pair
            }
        }
        if (pairs.isEmpty()) {
            println("语料缺失，跳过九键词表台")
            return
        }
        var top1 = 0
        var top5 = 0
        val misses = ArrayList<String>()
        for (word in pairs) {
            val reading = wordReadings.getValue(word)
            val hits = nine.evaluate(T9.encode(reading)).candidates.map { it.text }
            val rank = hits.indexOf(word)
            if (rank == 0) top1++
            if (rank in 0 until 5) top5++ else misses += "$word($reading) -> ${hits.take(3)}"
        }
        println("九键 二字词：${pairs.size} 个样本 top1=$top1 top5=$top5")
        misses.take(8).forEach { println("    miss $it") }
        assertTrue("九键 top5 过低: $top5/${pairs.size}", top5 >= pairs.size / 2)
    }

    @Test
    fun `a long buffer still answers inside a keystroke`() {
        val sentence = "jintiandetianqizhenbucuowomenyiqichuquzouzouba"
        repeat(20) { full.evaluate(sentence) }  // 预热
        val start = System.nanoTime()
        val runs = 50
        repeat(runs) { full.evaluate(sentence) }
        val millis = (System.nanoTime() - start) / 1_000_000.0 / runs

        println("时延：${sentence.length} 字母 / ${runs} 次，平均 ${"%.1f".format(millis)} ms")
        assertTrue("长缓冲解码过慢: ${millis}ms", millis < 60)
    }

    private data class Row(val text: String, val full: String, val initials: String, val digits: String)

    /** 词表里的词优先（长词先切），剩下的字用首读音——和用户打字时的思路一致。 */
    private fun readingOf(sentence: String): String? {
        val builder = StringBuilder()
        var index = 0
        while (index < sentence.length) {
            var matched = 0
            var reading: String? = null
            val longest = minOf(MAX_WORD, sentence.length - index)
            for (length in longest downTo 2) {
                val candidate = wordReadings[sentence.substring(index, index + length)]
                if (candidate != null) {
                    matched = length
                    reading = candidate
                    break
                }
            }
            if (matched == 0) {
                reading = charReadings[sentence[index]]
                matched = 1
            }
            if (reading.isNullOrEmpty()) return null
            builder.append(reading)
            index += matched
        }
        return builder.toString()
    }

    private fun initialsOf(reading: String): String {
        val builder = StringBuilder()
        var index = 0
        while (index < reading.length) {
            var step = 0
            for (end in minOf(reading.length, index + PinyinSyllables.maxLength) downTo index + 1) {
                if (PinyinSyllables.isSyllable(reading.substring(index, end))) {
                    step = end - index
                    break
                }
            }
            if (step == 0) return reading
            builder.append(reading[index])
            index += step
        }
        return builder.toString()
    }

    /** 语料行：去掉注释行与标点，只留汉字。 */
    private fun corpus(): List<String> {
        val files = File("../tools/dictgen/raw").listFiles { file -> file.name.startsWith("corpus_") } ?: return emptyList()
        val sentences = ArrayList<String>()
        for (file in files.sortedBy { it.name }) {
            file.forEachLine { line ->
                if (line.startsWith("#")) return@forEachLine
                val han = line.filter { it in '\u4e00'..'\u9fff' }
                if (han.length >= 2) sentences += han
            }
        }
        return sentences
    }

    private fun reader(name: String) = BufferedReader(
        InputStreamReader(FileInputStream(File("src/main/assets", name)), Charsets.UTF_8),
    )

    private val wordReadings: Map<String, String> by lazy {
        val best = HashMap<String, Pair<String, Int>>()
        reader("pinyin_words.txt").useLines { lines ->
            for (line in lines) {
                val parts = line.split('\t')
                if (parts.size != 3) continue
                val reading = parts[0]
                val word = parts[1]
                val score = parts[2].toIntOrNull() ?: continue
                val known = best[word]
                if (known == null || score > known.second) best[word] = reading to score
            }
        }
        best.mapValues { it.value.first }
    }

    private val charReadings: Map<Char, String> by lazy {
        val readings = HashMap<Char, String>()
        File("../tools/dictgen/clean/pinyin_data.txt").forEachLine { line ->
            if (!line.startsWith("U+")) return@forEachLine
            val code = line.substringAfter("U+").substringBefore(':').trim().toIntOrNull(16) ?: return@forEachLine
            val syllable = line.substringAfter(':').substringBefore('#').trim().split(',').firstOrNull()?.trim().orEmpty()
            // 声调符号在 NFD 里是独立字符，去掉即可；ü 先换成 v 再分解，否则它会退成 u。
            val prepared = syllable.lowercase().replace("ü", "v").replace("u:", "v")
            val simplified = Normalizer.normalize(prepared, Normalizer.Form.NFD)
                .filter { it.code !in 0x0300..0x036f }
                .filter { it in 'a'..'z' }
            if (simplified.isNotEmpty() && !readings.containsKey(code.toChar())) {
                readings[code.toChar()] = simplified
            }
        }
        readings
    }

    private companion object {
        const val MAX_WORD = 8
    }
}

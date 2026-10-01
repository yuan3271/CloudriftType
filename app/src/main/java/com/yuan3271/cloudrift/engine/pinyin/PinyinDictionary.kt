package com.yuan3271.cloudrift.engine.pinyin

import androidx.annotation.WorkerThread
import java.io.BufferedReader
import java.io.InputStreamReader

data class WordEntry(val word: String, val reading: String, val score: Int)

/**
 * Loads the bundled Chinese dictionaries produced by `tools/dictgen`.
 *
 * Two tables are shipped:
 *  - `pinyin_chars.txt` syllable -> hanzi, ordered by corpus weight
 *  - `pinyin_words.txt` reading -> word + score
 *
 * The nine key tables are derived from the same data on demand, because building them costs
 * a full pass over the word index and most users never touch the T9 layout.
 *
 * The constructor takes readers rather than a Context so the engine can be unit tested
 * against the same assets that ship in the APK.
 */
class PinyinDictionary(
    private val charTableSource: () -> BufferedReader,
    private val wordTableSource: () -> BufferedReader,
) {

    private val charTable = HashMap<String, String>(1024)
    private val wordTable = HashMap<String, Array<WordEntry>>(32768)

    /**
     * Corpus weight per syllable, used to order the syllables a partial code can still turn into.
     * This is the third column of the character table.
     */
    private val syllableWeights = HashMap<String, Int>(1024)

    /**
     * How common a syllable is among the syllables starting with the same letter, 0 being the most
     * common. Used to rank 首字母 candidates by how naturally they expand: "nh" could be 你好
     * (ni + hao) or 女孩 (nv + hai), and ni/hao are the likelier readings of n and h.
     */
    private val syllableRanks = HashMap<String, Int>(1024)

    /**
     * The readings, sorted, so "what could this code still become" costs a binary search plus a
     * short scan instead of a pass over all 56k readings on every keystroke.
     */
    private var sortedReadings: Array<String> = emptyArray()

    /**
     * 首字母 (initials) index: "nh" -> 你好, "wsm" -> 为什么.
     *
     * Chinese IMEs let you type just the first letter of every syllable, and it is the one thing a
     * reader of pinyin expects to work. Only words of two to four characters are indexed, which is
     * what jianpin is actually used for, and each bucket is capped so the index stays a few MB.
     */
    private val initialsIndex = HashMap<String, MutableList<WordEntry>>(1 shl 15)

    @Volatile
    private var t9Ready = false
    private val t9Chars = HashMap<String, String>(1024)
    private val t9Syllables = HashMap<String, MutableList<String>>(1024)
    private val t9WordTable = HashMap<String, MutableList<WordEntry>>(32768)
    private var sortedT9Readings: Array<String> = emptyArray()

    @Volatile
    var isReady: Boolean = false
        private set

    @WorkerThread
    fun load() {
        if (isReady) return
        loadChars()
        loadWords()
        isReady = true
    }

    private fun loadChars() {
        charTableSource().use { reader ->
            for (line in reader.lineSequence()) {
                // The asset is `syllable<TAB>chars<TAB>syllableWeight`, so the columns have to be
                // split rather than "everything after the first tab" - taking the tail whole used
                // to append the weight digits to the character list, which showed up as 1 2 2 1 5 9
                // among the candidates of any syllable whose list was shorter than the limit.
                val first = line.indexOf('\t')
                if (first <= 0) continue
                val second = line.indexOf('\t', first + 1)
                val chars = if (second < 0) line.substring(first + 1) else line.substring(first + 1, second)
                if (chars.isEmpty()) continue
                val syllable = line.substring(0, first)
                charTable[syllable] = chars
                syllableWeights[syllable] = if (second < 0) {
                    0
                } else {
                    line.substring(second + 1).trim().toIntOrNull() ?: 0
                }
            }
        }
        buildSyllableRanks()
    }

    private fun buildSyllableRanks() {
        syllableWeights.keys
            .groupBy { it.first() }
            .forEach { (_, syllables) ->
                syllables
                    .sortedWith(
                        compareByDescending<String> { syllableWeights[it] ?: 0 }.thenBy { it },
                    )
                    .forEachIndexed { rank, syllable -> syllableRanks[syllable] = rank }
            }
    }

    private fun loadWords() {
        val scratch = HashMap<String, ArrayList<WordEntry>>(4096)
        wordTableSource().use { reader ->
            for (line in reader.lineSequence()) {
                val first = line.indexOf('\t')
                if (first <= 0) continue
                val second = line.indexOf('\t', first + 1)
                if (second <= 0) continue
                val reading = line.substring(0, first)
                val word = line.substring(first + 1, second)
                val score = line.substring(second + 1).toIntOrNull() ?: continue
                scratch.getOrPut(reading) { ArrayList(4) }.add(WordEntry(word, reading, score))
            }
        }
        for ((reading, list) in scratch) {
            list.sortWith(compareByDescending<WordEntry> { it.score }.thenBy { it.word })
            wordTable[reading] = list.toTypedArray()
        }
        sortedReadings = scratch.keys.toTypedArray()
        sortedReadings.sort()
        buildInitialsIndex(scratch)
        scratch.clear()
    }

    private fun buildInitialsIndex(words: Map<String, ArrayList<WordEntry>>) {
        // Readings ordered by their best word, so a cap per bucket keeps the words that matter
        // instead of whichever entries a hash map happened to hand over first.
        val ordered = words.entries.sortedByDescending { it.value.first().score }
        // A word enters the index through its best reading only. 系统 is xitong, but 系 also has a
        // ji reading, which used to put it in the "jt" bucket as if it were 今天's competition.
        val bestScore = HashMap<String, Int>(1 shl 16)
        for (entries in words.values) {
            for (entry in entries) {
                val known = bestScore[entry.word] ?: Int.MIN_VALUE
                if (entry.score > known) bestScore[entry.word] = entry.score
            }
        }
        for ((reading, entries) in ordered) {
            val initials = initialsOf(reading) ?: continue
            if (initials.length !in MIN_INITIALS..MAX_INITIALS) continue
            val bucket = initialsIndex.getOrPut(initials) { ArrayList(4) }
            if (bucket.size >= INITIALS_PER_BUCKET) continue
            val penalty = expansionPenalty(reading)
            for (entry in entries) {
                if (entry.word.length < 2 || entry.word.length > MAX_INITIALS) continue
                if (bucket.size >= INITIALS_PER_BUCKET) break
                if (bestScore[entry.word] != entry.score) continue
                if (bucket.any { it.word == entry.word }) continue
                // The adjusted score is what the candidate ranking uses, so store it with the entry.
                // The common word bonus belongs here rather than in the engine: the bucket is capped,
                // so a word that is not lifted at build time is not merely ranked low, it is absent.
                bucket.add(
                    WordEntry(
                        entry.word,
                        entry.reading,
                        entry.score - penalty + commonWordBonus(entry.word),
                    ),
                )
            }
        }
    }

    /**
     * Words a news and technical corpus will never rank the way a person does: 你好 loses to 南河
     * and 拟合 on frequency alone, and every keyboard still puts 你好 first. A short curated list is
     * cheaper and more honest than pretending the frequencies say otherwise.
     */
    private fun commonWordBonus(word: String): Int =
        if (word in COMMON_WORDS) INITIALS_COMMON_BONUS else 0

    /**
     * How unnatural this reading is as an expansion of its initials: the sum of how far each
     * syllable sits down the list for its first letter. 你好 (ni, hao) scores far better than
     * 女孩 (nv, hai), which is the difference between a greeting and a coincidence of letters.
     */
    private fun expansionPenalty(reading: String): Int {
        var penalty = 0
        var index = 0
        while (index < reading.length) {
            var step = 0
            val upper = minOf(reading.length, index + PinyinSyllables.maxLength)
            for (end in upper downTo index + 1) {
                if (PinyinSyllables.isSyllable(reading.substring(index, end))) {
                    step = end - index
                    break
                }
            }
            if (step == 0) return penalty
            penalty += (syllableRanks[reading.substring(index, index + step)] ?: 0) *
                INITIALS_RANK_PENALTY
            index += step
        }
        return penalty
    }

    /** First letters of the syllables in [reading], or null when it does not split cleanly. */
    private fun initialsOf(reading: String): String? {
        val initials = StringBuilder(reading.length)
        var index = 0
        while (index < reading.length) {
            var step = 0
            val upper = minOf(reading.length, index + PinyinSyllables.maxLength)
            for (end in upper downTo index + 1) {
                if (PinyinSyllables.isSyllable(reading.substring(index, end))) {
                    step = end - index
                    break
                }
            }
            if (step == 0) return null
            initials.append(reading[index])
            index += step
        }
        return initials.toString()
    }

    /**
     * Words whose initials are exactly [initials], best first. Two letters or more, so that typing
     * a single letter keeps offering that letter's characters rather than a wall of words.
     */
    fun wordsForInitials(initials: String, limit: Int): List<WordEntry> {
        if (initials.length < 2) return emptyList()
        val bucket = initialsIndex[initials] ?: return emptyList()
        bucket.sortWith(compareByDescending<WordEntry> { it.score }.thenBy { it.word })
        return bucket.take(limit)
    }

    fun charsFor(syllable: String, limit: Int): String {
        val chars = charTable[syllable] ?: return ""
        return if (chars.length <= limit) chars else chars.substring(0, limit)
    }

    fun wordsFor(reading: String, limit: Int): List<WordEntry> {
        val words = wordTable[reading] ?: return emptyList()
        return if (words.size <= limit) words.asList() else words.asList().subList(0, limit)
    }

    /**
     * The best word of every reading that starts with [prefix] and is longer than it.
     *
     * This is what lets 你好 be offered while only "nih" - or even just "ni" - is on screen: the
     * user typed a code the corpus can still grow, and these are the most likely ways it grows.
     * One word per reading keeps the list varied instead of repeating homophones of the same
     * word; the list is then ranked by corpus weight so the common words come first.
     */
    fun completionWords(prefix: String, limit: Int): List<WordEntry> =
        collectCompletions(sortedReadings, prefix, limit) { wordTable[it]?.asList() }

    fun syllablesStartingWith(prefix: String, limit: Int): List<String> {
        val merged = LinkedHashSet(charTable.keys.filter { it.startsWith(prefix) })
        merged.addAll(PinyinSyllables.startingWith(prefix))
        // Most used syllable first: typing one letter should lead with 那 before 囊.
        return merged
            .sortedWith(compareByDescending<String> { syllableWeights[it] ?: 0 }.thenBy { it })
            .take(limit)
    }

    // ---- nine key ----------------------------------------------------------------

    fun ensureT9() {
        if (t9Ready) return
        synchronized(this) {
            if (t9Ready) return
            for ((syllable, chars) in charTable) {
                t9Syllables.getOrPut(T9.encode(syllable)) { ArrayList(4) }.add(syllable)
                t9Chars[T9.encode(syllable)] = chars
            }
            // Several syllables share a keypad signature. Round-robin their character lists so
            // the merged ranking stays balanced instead of letting one syllable dominate it.
            for ((digits, syllables) in t9Syllables) {
                val lists = syllables.sorted().map { charTable[it].orEmpty() }
                t9Chars[digits] = interleave(lists)
            }
            for ((reading, words) in wordTable) {
                val digits = T9.encode(reading)
                val bucket = t9WordTable.getOrPut(digits) { ArrayList(4) }
                for (word in words) bucket.add(word)
            }
            for ((_, bucket) in t9WordTable) {
                bucket.sortWith(compareByDescending<WordEntry> { it.score }.thenBy { it.word })
            }
            sortedT9Readings = t9WordTable.keys.toTypedArray()
            sortedT9Readings.sort()
            t9Ready = true
        }
    }

    fun t9CharsFor(digits: String, limit: Int): String {
        ensureT9()
        val chars = t9Chars[digits] ?: return ""
        return if (chars.length <= limit) chars else chars.substring(0, limit)
    }

    fun t9WordsFor(digits: String, limit: Int): List<WordEntry> {
        ensureT9()
        val words = t9WordTable[digits] ?: return emptyList()
        return if (words.size <= limit) words.toList() else words.subList(0, limit).toList()
    }

    /**
     * How many characters sit in the first rank of a keypad signature. Several syllables share
     * one signature, and their character tables are interleaved rank by rank, so the first
     * "round" of the list is the set of most frequent characters - one per syllable.
     */
    fun t9TopRankSize(digits: String): Int {
        ensureT9()
        return t9Syllables[digits]?.size ?: 1
    }

    fun t9SyllablesStartingWith(prefixDigits: String, limit: Int): List<String> {
        ensureT9()
        val result = LinkedHashSet<String>()
        for ((digits, syllables) in t9Syllables) {
            if (!digits.startsWith(prefixDigits)) continue
            result.addAll(syllables)
            if (result.size >= limit) break
        }
        return result.take(limit)
    }

    /** Nine key twin of [completionWords]: the same idea over keypad signatures. */
    fun t9CompletionWords(prefixDigits: String, limit: Int): List<WordEntry> {
        ensureT9()
        return collectCompletions(sortedT9Readings, prefixDigits, limit) { t9WordTable[it] }
    }

    private fun collectCompletions(
        readings: Array<String>,
        prefix: String,
        limit: Int,
        wordsOf: (String) -> List<WordEntry>?,
    ): List<WordEntry> {
        if (prefix.isEmpty() || limit <= 0) return emptyList()
        var index = lowerBound(readings, prefix)
        val collected = ArrayList<WordEntry>(minOf(limit * 4, 64))
        var scanned = 0
        while (index < readings.size && scanned < COMPLETION_SCAN_LIMIT) {
            val reading = readings[index]
            if (!reading.startsWith(prefix)) break
            scanned++
            index++
            // The exact reading is already offered by the caller as a finished word.
            if (reading == prefix) continue
            wordsOf(reading)?.firstOrNull()?.let(collected::add)
        }
        if (collected.isEmpty()) return emptyList()
        collected.sortWith(compareByDescending<WordEntry> { it.score }.thenBy { it.word })
        return if (collected.size <= limit) collected else collected.subList(0, limit)
    }

    /** First index whose reading is not smaller than [prefix]. */
    private fun lowerBound(readings: Array<String>, prefix: String): Int {
        var low = 0
        var high = readings.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (readings[mid] < prefix) low = mid + 1 else high = mid
        }
        return low
    }

    /** True when [digits] spells at least one syllable on the keypad. */
    fun hasNineKeyReading(digits: String): Boolean {
        if (digits.isEmpty()) return false
        ensureT9()
        if (t9Chars.containsKey(digits)) return true
        if (t9WordTable.containsKey(digits)) return true
        return PinyinSyllables.all.any { T9.encode(it) == digits }
    }

    private fun interleave(lists: List<String>): String {
        if (lists.size == 1) return lists[0]
        val builder = StringBuilder(lists.sumOf { it.length })
        val longest = lists.maxOf { it.length }
        for (index in 0 until longest) {
            for (list in lists) {
                if (index < list.length) builder.append(list[index])
            }
        }
        return builder.toString()
    }

    companion object {
        private const val ASSET_CHARS = "pinyin_chars.txt"
        private const val ASSET_WORDS = "pinyin_words.txt"
        private const val BUFFER_SIZE = 1 shl 16

        /**
         * How many readings one completion search may walk. A two letter code such as "zh"
         * matches a few thousand readings, so this bounds the worst case to well under a
         * millisecond while leaving every longer prefix fully ranked.
         */
        private const val COMPLETION_SCAN_LIMIT = 4000
        /** 首字母 buckets only make sense for short words, and their keys are 2-4 letters. */
        private const val MIN_INITIALS = 2
        private const val MAX_INITIALS = 4
        /**
         * Words kept per initials key. Generous, because the corpus does not rank the way a reader
         * expects: 你好 is far down the "nh" bucket behind 南航 and 女孩, and it has to still be
         * on screen when someone types two letters.
         */
        private const val INITIALS_PER_BUCKET = 60
        /** Charged per rank a syllable sits down its letter's list when ranking initials hits. */
        /**
         * Charged per rank a syllable sits down its letter's list. Kept modest: too strong and it
         * overrules the corpus on every word, which pushed 你好 behind a dozen place names even
         * with the common word bonus applied.
         */
        /**
         * Set to zero now that the dictionary carries a conversational frequency (the HSK ranks).
         * It was a proxy for "would a person say this", and a bad one: 芝's syllable is commoner
         * than 怎's, so it preferred 芝麻鱼 to 怎么样. Real frequencies do that job properly.
         */
        private const val INITIALS_RANK_PENALTY = 0
        /** Lifts a greeting over a place name inside a bucket; see [commonWordBonus]. */
        private const val INITIALS_COMMON_BONUS = 900

        /**
         * The words people actually type with 首字母. Kept short and curated: this is a product
         * decision about what a Chinese keyboard is expected to offer first, not a statistic.
         */
        private val COMMON_WORDS: Set<String> = setOf(
            "你好", "您好", "谢谢", "再见", "对不起", "没关系", "请问", "麻烦",
            "什么", "为什么", "怎么", "怎么办", "怎么样", "多少", "哪里", "哪个",
            "可以", "没有", "知道", "觉得", "因为", "所以", "但是", "如果",
            "现在", "今天", "明天", "昨天", "时候", "时间", "手机", "电脑",
            "工作", "生活", "问题", "事情", "东西", "地方", "朋友", "老师",
            "吃饭", "喝水", "回家", "上班", "下班", "睡觉", "喜欢", "需要",
            "开始", "结束", "下午", "上午", "晚上", "这个", "那个", "我们",
            "你们", "他们", "自己", "大家", "一起", "已经", "还是", "或者",
        )

        /** Production wiring: read the generated tables straight out of the APK assets. */
        fun fromAssets(assets: android.content.res.AssetManager): PinyinDictionary =
            PinyinDictionary(
                charTableSource = {
                    BufferedReader(InputStreamReader(assets.open(ASSET_CHARS), Charsets.UTF_8), BUFFER_SIZE)
                },
                wordTableSource = {
                    BufferedReader(InputStreamReader(assets.open(ASSET_WORDS), Charsets.UTF_8), BUFFER_SIZE)
                },
            )

        /** Test wiring: read the same files from disk. */
        fun fromReaders(
            charTable: () -> BufferedReader,
            wordTable: () -> BufferedReader,
        ): PinyinDictionary =
            PinyinDictionary(charTable, wordTable)
    }
}

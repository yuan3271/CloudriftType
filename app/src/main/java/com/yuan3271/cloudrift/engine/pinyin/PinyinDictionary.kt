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
     * The readings, sorted, so "what could this code still become" costs a binary search plus a
     * short scan instead of a pass over all 56k readings on every keystroke.
     */
    private var sortedReadings: Array<String> = emptyArray()

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
        scratch.clear()
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

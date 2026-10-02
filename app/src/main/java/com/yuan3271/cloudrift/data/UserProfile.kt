package com.yuan3271.cloudrift.data

import android.content.Context
import android.content.SharedPreferences
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** What the keyboard has noticed about the way this person types. */
data class UserStats(
    /** Words the keyboard only knows because the user typed them. */
    val inventedWords: Int = 0,
    /** Codes whose candidate order the user has already overruled at least twice. */
    val habits: Int = 0,
    /** Number of commits the profile has learned from. */
    val learnedCommits: Int = 0,
)

/**
 * What came out of an import, in the words the settings screen shows.
 *
 * Importing never fails halfway: the payload is parsed and validated first, and only then merged,
 * so a rejected file cannot leave the profile half updated.
 */
data class ImportOutcome(
    val ok: Boolean,
    val message: String,
    val habitsMerged: Int = 0,
    val wordsMerged: Int = 0,
)

/**
 * The personal half of the input engine.
 *
 * Two things are remembered, both of them observed rather than configured:
 *
 *  - **habits**: which candidate was picked for which code. Picking 你好 for "ni" twice is what
 *    makes 你好 the first suggestion next time. Two, not one, because a single tap is usually a
 *    one-off and not a habit.
 *  - **invented words**: typing 张 then 伟 teaches the keyboard 张伟 (reading zhangwei) even
 *    though no dictionary ships it.
 *
 * The profile never leaves the device (its preferences file is excluded from backup and device
 * transfer) and can be cleared from the settings screen. Everything here is plain deterministic
 * data, no model and no network.
 */
class UserProfile(
    private val store: StringStore,
    private val scope: CoroutineScope,
) {

    constructor(context: Context, scope: CoroutineScope) : this(
        SharedPreferencesStore(
            context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE),
        ),
        scope,
    )

    /** code -> (candidate text -> how often it was chosen for that code). */
    private val choices = HashMap<String, HashMap<String, Int>>()

    /** reading -> word the user assembled by committing characters one by one. */
    private val invented = HashMap<String, String>()

    /** word -> how often it has been committed at all, any code. */
    private val wordCounts = HashMap<String, Int>()

    private val _stats = MutableStateFlow(UserStats())
    val stats: StateFlow<UserStats> = _stats.asStateFlow()

    private var saveJob: Job? = null

    init {
        load()
        publish()
    }

    /**
     * Called for every commit the user causes by picking a candidate.
     *
     * @param code the raw buffer that produced the candidate.
     * @param text what actually went into the editor.
     * @param reading the candidate's reading, when it has one.
     */
    fun recordChoice(code: String, text: String, reading: String) {
        if (code.isEmpty() || text.isEmpty()) return
        choices.getOrPut(code.take(MAX_CODE_CHARS)) { HashMap(4) }
            .merge(text, 1, Int::plus)
        wordCounts.merge(text, 1, Int::plus)
        schedulePersist()
        publish()
    }

    /**
     * Teaches a word the dictionaries do not know. Called when the user commits single characters
     * back to back, which is how names and jargon get typed before they are learned.
     */
    fun rememberWord(reading: String, word: String) {
        if (reading.isEmpty() || word.length < 2 || word.length > MAX_WORD_CHARS) return
        if (invented[reading] == word) return
        if (invented.size >= MAX_INVENTED_WORDS) return
        invented[reading] = word
        schedulePersist()
        publish()
    }

    /**
     * How many times this code - or any prefix of it, since a word chosen for "ni" is still a
     * good answer for "nih" - has already been used to pick [text].
     */
    fun habit(code: String, text: String): Int {
        if (code.isEmpty()) return 0
        var total = 0
        var end = minOf(code.length, MAX_CODE_CHARS)
        while (end > 0) {
            total += choices[code.substring(0, end)]?.get(text) ?: 0
            end--
        }
        return total
    }

    /** Words this user assembled themselves whose reading matches the code exactly. */
    fun inventedWord(reading: String): String? = invented[reading]

    fun timesUsed(text: String): Int = wordCounts[text] ?: 0

    fun clear() {
        choices.clear()
        invented.clear()
        wordCounts.clear()
        saveJob?.cancel()
        persist()
        publish()
    }

    /**
     * The learned profile as one portable string.
     *
     * The same string is what 「导出文件」 writes and what 「导出二维码」 encodes, which is why
     * the two ways are interchangeable: a file can be imported by scanning nothing, and a scanned
     * code can be pasted into a file. It is the stored JSON (the very object [persist] writes),
     * gzipped and base64url encoded because a QR code holds ~2 KB and the raw JSON does not fit.
     */
    fun exportPayload(): String {
        val json = profileJson().toString().toByteArray(Charsets.UTF_8)
        val compressed = ByteArrayOutputStream().use { buffer ->
            GZIPOutputStream(buffer).use { it.write(json) }
            buffer.toByteArray()
        }
        return PAYLOAD_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(compressed)
    }

    /**
     * Merges a payload back in.
     *
     * Merging rather than replacing is deliberate: the usual reason to import is a new phone, and
     * the new phone has already learned things of its own. Counts are merged with `max`, not by
     * adding - importing the same file twice must not look like twice the evidence, or a habit
     * would cross its threshold just by being transferred twice.
     */
    fun importPayload(raw: String): ImportOutcome {
        val root = decodePayload(raw.trim())
            ?: return ImportOutcome(false, "这不是云隙输入的学习记录")
        if (root.optInt(KEY_VERSION, 0) <= 0) {
            return ImportOutcome(false, "学习记录缺少版本号，无法确认格式")
        }
        val incomingChoices = root.optJSONObject(KEY_CHOICES)
        val incomingInvented = root.optJSONObject(KEY_INVENTED)
        val incomingCounts = root.optJSONObject(KEY_COUNTS)

        var habits = 0
        incomingChoices?.let { choiceJson ->
            for (code in choiceJson.keys()) {
                val bucket = choiceJson.optJSONObject(code) ?: continue
                val target = choices.getOrPut(code.take(MAX_CODE_CHARS)) { HashMap(4) }
                for (text in bucket.keys()) {
                    val count = bucket.optInt(text)
                    if (count <= 0) continue
                    val merged = maxOf(target[text] ?: 0, count)
                    if (merged != (target[text] ?: 0)) {
                        target[text] = merged
                        habits++
                    }
                }
            }
        }
        var words = 0
        incomingInvented?.let { wordJson ->
            for (reading in wordJson.keys()) {
                val word = wordJson.optString(reading)
                if (word.isEmpty() || invented.containsKey(reading)) continue
                if (invented.size >= MAX_INVENTED_WORDS) break
                invented[reading] = word
                words++
            }
        }
        incomingCounts?.let { countJson ->
            for (word in countJson.keys()) {
                val count = countJson.optInt(word)
                if (count <= 0) continue
                val merged = maxOf(wordCounts[word] ?: 0, count)
                if (merged > (wordCounts[word] ?: 0) && wordCounts.size < MAX_WORDS) {
                    wordCounts[word] = merged
                }
            }
        }

        saveJob?.cancel()
        persist()
        publish()
        val detail = buildString {
            append("已合并 ")
            append(if (habits > 0) "$habits 条习惯" else "0 条新习惯")
            append(" · ")
            append(if (words > 0) "$words 个自造词" else "0 个自造词")
        }
        return ImportOutcome(true, detail, habits, words)
    }

    /** Accepts both the encoded payload and a plain JSON profile (a hand-inspected file). */
    private fun decodePayload(raw: String): JSONObject? {
        if (raw.isEmpty()) return null
        if (!raw.startsWith(PAYLOAD_PREFIX)) {
            return runCatching { JSONObject(raw) }.getOrNull()
        }
        val encoded = raw.removePrefix(PAYLOAD_PREFIX)
        return runCatching {
            val bytes = Base64.getUrlDecoder().decode(encoded)
            val json = GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
            JSONObject(String(json, Charsets.UTF_8))
        }.getOrNull()
    }

    /** Flushes pending changes; called when the keyboard is torn down. */
    fun flush() {
        saveJob?.cancel()
        persist()
    }

    private fun publish() {
        _stats.value = UserStats(
            inventedWords = invented.size,
            habits = choices.values.count { bucket -> bucket.values.any { it >= HABIT_THRESHOLD } },
            learnedCommits = wordCounts.values.sum(),
        )
    }

    private fun schedulePersist() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DELAY_MS)
            persist()
        }
    }

    private fun persist() {
        store.write(KEY_PROFILE, profileJson().toString())
    }

    /** The exact document that is stored, exported and (after encoding) scanned. */
    private fun profileJson(): JSONObject {
        val root = JSONObject()
        runCatching {
            root.put(KEY_VERSION, PROFILE_VERSION)
            root.put(KEY_EXPORTED_AT, System.currentTimeMillis())

            val choiceJson = JSONObject()
            for ((code, bucket) in choices) {
                val trimmed = bucket.entries.sortedByDescending { it.value }.take(MAX_TEXTS_PER_CODE)
                val item = JSONObject()
                for ((text, count) in trimmed) item.put(text, count)
                choiceJson.put(code, item)
            }
            root.put(KEY_CHOICES, choiceJson)

            val wordJson = JSONObject()
            for ((reading, word) in invented) wordJson.put(reading, word)
            root.put(KEY_INVENTED, wordJson)

            val countJson = JSONObject()
            for ((word, count) in wordCounts.entries.sortedByDescending { it.value }.take(MAX_WORDS)) {
                countJson.put(word, count)
            }
            root.put(KEY_COUNTS, countJson)
        }
        return root
    }

    private fun load() {
        val raw = store.read(KEY_PROFILE) ?: return
        runCatching {
            val root = JSONObject(raw)
            root.optJSONObject(KEY_CHOICES)?.let { choiceJson ->
                for (code in choiceJson.keys()) {
                    val bucket = choiceJson.optJSONObject(code) ?: continue
                    val map = HashMap<String, Int>(4)
                    for (text in bucket.keys()) map[text] = bucket.optInt(text)
                    if (map.isNotEmpty()) choices[code] = map
                }
            }
            root.optJSONObject(KEY_INVENTED)?.let { wordJson ->
                for (reading in wordJson.keys()) {
                    invented[reading] = wordJson.optString(reading)
                }
            }
            root.optJSONObject(KEY_COUNTS)?.let { countJson ->
                for (word in countJson.keys()) wordCounts[word] = countJson.optInt(word)
            }
        }
    }

    companion object {
        /** Preferences file for the learned profile. */
        const val FILE_NAME = "cloudrift_profile"

        /** How often a candidate has to be picked for a code before it counts as a habit. */
        const val HABIT_THRESHOLD = 2

        private const val MAX_CODE_CHARS = 12
        private const val MAX_TEXTS_PER_CODE = 8
        private const val MAX_INVENTED_WORDS = 1000
        private const val MAX_WORDS = 5000
        private const val MAX_WORD_CHARS = 12
        private const val SAVE_DELAY_MS = 1500L
        private const val KEY_PROFILE = "profile"
        private const val KEY_CHOICES = "choices"
        private const val KEY_INVENTED = "invented"
        private const val KEY_COUNTS = "counts"
        private const val KEY_VERSION = "version"
        private const val KEY_EXPORTED_AT = "exportedAt"

        /** Bumped when the document's meaning changes; imported documents must state it. */
        private const val PROFILE_VERSION = 1

        /**
         * Marks a string as a 云隙输入 learning record. Kept short on purpose: every character
         * here is a character the QR code cannot spend on data.
         */
        const val PAYLOAD_PREFIX = "CRP1:"
    }
}

/**
 * The one thing the profile needs from the platform. Kept as a port so the learning rules can be
 * unit tested against an in-memory map instead of a device.
 */
interface StringStore {
    fun read(key: String): String?
    fun write(key: String, value: String)
}

private class SharedPreferencesStore(private val prefs: SharedPreferences) : StringStore {
    override fun read(key: String): String? = prefs.getString(key, null)
    override fun write(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}

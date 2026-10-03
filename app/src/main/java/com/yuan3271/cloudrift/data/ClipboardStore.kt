package com.yuan3271.cloudrift.data

import android.content.ClipboardManager
import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** One remembered clipboard entry. */
data class ClipEntry(
    val text: String,
    /** Wall clock millis of when it entered the history. */
    val at: Long,
)

/**
 * Rolling clipboard history.
 *
 * An input method is allowed to read the clipboard whenever it wants, so the store subscribes to
 * primary clip changes for the whole session instead of sampling it when the panel opens - that is
 * what makes entries copied in other apps show up here.
 *
 * The history is capped, deduplicated and persisted in a preferences file that is excluded from
 * cloud backup and device transfer, so what the user copies never leaves the phone.
 */
class ClipboardStore(
    context: Context,
    private val scope: CoroutineScope,
) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val manager = appContext.getSystemService(ClipboardManager::class.java)

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<ClipEntry>> = _entries.asStateFlow()

    private var job: Job? = null
    private var listening = false

    private val listener = ClipboardManager.OnPrimaryClipChangedListener { capture() }

    fun start() {
        if (listening) return
        listening = true
        runCatching { manager?.addPrimaryClipChangedListener(listener) }
        capture()
    }

    fun stop() {
        if (!listening) return
        listening = false
        runCatching { manager?.removePrimaryClipChangedListener(listener) }
        job?.cancel()
        persist(_entries.value)
    }

    fun clear() {
        _entries.value = emptyList()
        persist(emptyList())
    }

    fun remove(entry: ClipEntry) {
        val next = _entries.value.filterNot { it.text == entry.text && it.at == entry.at }
        if (next.size == _entries.value.size) return
        _entries.value = next
        persist(next)
    }

    /**
     * Puts an entry back on the system clipboard so it can be pasted into another app. The
     * listener will see this change and move the entry to the top, which is the right outcome.
     */
    fun copyToClipboard(text: String) {
        if (text.isEmpty()) return
        runCatching {
            manager?.setPrimaryClip(android.content.ClipData.newPlainText(CLIP_LABEL, text))
        }
    }

    /** Called by the clipboard listener; must stay cheap and never throw. */
    private fun capture() {
        val text = runCatching {
            val clip = manager?.primaryClip ?: return@runCatching null
            if (clip.itemCount == 0) return@runCatching null
            clip.getItemAt(0).coerceToText(appContext)?.toString()
        }.getOrNull()?.trim()?.take(MAX_ENTRY_CHARS) ?: return
        if (text.isBlank()) return

        val current = _entries.value
        if (current.firstOrNull()?.text == text) return
        val next = buildList {
            add(ClipEntry(text, System.currentTimeMillis()))
            addAll(current.filterNot { it.text == text })
        }.take(LIMIT)
        _entries.value = next
        schedulePersist(next)
    }

    /** Coalesces the writes; the clipboard can change several times in a row. */
    private fun schedulePersist(entries: List<ClipEntry>) {
        job?.cancel()
        job = scope.launch {
            delay(SAVE_DELAY_MS)
            persist(entries)
        }
    }

    private fun persist(entries: List<ClipEntry>) {
        val array = JSONArray()
        for (entry in entries) {
            array.put(
                JSONObject()
                    .put(KEY_TEXT, entry.text)
                    .put(KEY_AT, entry.at),
            )
        }
        runCatching {
            prefs.edit { putString(KEY_HISTORY, array.toString()) }
        }
    }

    private fun load(): List<ClipEntry> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val text = item.optString(KEY_TEXT)
                    if (text.isBlank()) continue
                    add(ClipEntry(text, item.optLong(KEY_AT, 0L)))
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        const val FILE_NAME = "cloudrift_clipboard"

        /** Enough history to be useful, small enough to stay out of the way. */
        const val LIMIT = 50

        private const val MAX_ENTRY_CHARS = 2000
        private const val SAVE_DELAY_MS = 800L
        private const val KEY_HISTORY = "history"
        private const val KEY_TEXT = "text"
        private const val KEY_AT = "at"
        private const val CLIP_LABEL = "云隙输入"
    }
}

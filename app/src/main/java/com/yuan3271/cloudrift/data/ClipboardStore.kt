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

    /**
     * 刚进剪贴板、还没提示过的那一条。候选栏拿它在键盘上方显示"要不要粘贴"——复制多半发生在
     * 别的应用里（键盘当时并不在屏幕上），所以提示不是弹在复制的那一刻，而是留给**下一次打开
     * 键盘**；用户贴了它、或者点了右边的叉，[acknowledgePending] 就把它清掉，历史里那条不动。
     *
     * 一条内容只提示一次。复制发生的那一刻剪贴板里就已经是那段文字，之后每一次进程重启
     * （[start] 会再读一次剪贴板）、每一次候选项清空，都还能看到同一段文字——不记住"这条提示
     * 已经给过了"，它就永远在候选栏里反复回来。记住的是**最近提示过的那段文字**，不是"提示过了
     * 没有"：换一段新内容仍然会给一次提示。
     */
    private val _pending = MutableStateFlow<ClipEntry?>(null)
    val pending: StateFlow<ClipEntry?> = _pending.asStateFlow()

    /**
     * 最近一次提示过的剪贴板文字（跨进程存活）。另一次复制可能落地完全一样的文字，那也算同一条
     * ——用户要的是"显示一次之后就会消失"，而一秒钟里连着按两次复制，看见的提示本来就该只有一条。
     */
    private val offers = ClipboardOfferLog(prefs.getString(KEY_OFFERED, null))

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
        acknowledgePending()
    }

    fun remove(entry: ClipEntry) {
        val next = _entries.value.filterNot { it.text == entry.text && it.at == entry.at }
        if (next.size == _entries.value.size) return
        _entries.value = next
        persist(next)
        if (_pending.value?.text == entry.text) acknowledgePending()
    }

    /**
     * 提示已经给过了（贴上了、用户把它关掉、或者用户直接开始打字）：历史保留，只是不再挂在那条上。
     * [offeredText] 不动——它正是"这段文字提示过了，别再提"的依据。
     */
    fun acknowledgePending() {
        _pending.value = null
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
        val entry = ClipEntry(text, System.currentTimeMillis())
        // 同一段文字的第二次"发现"不是新内容：它要么是进程重启后又读了一遍剪贴板，要么是用户
        // 又复制了一次同样的文字。两种情况下再挂一条提示都是"显示多次"——提示只给新内容，历史
        // 里那条照旧提到最前（下面那几步）。
        if (offers.offer(text)) {
            _pending.value = entry
            runCatching { prefs.edit { putString(KEY_OFFERED, text) } }
        }
        if (current.firstOrNull()?.text == text) return
        val next = buildList {
            add(entry)
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
        private const val KEY_OFFERED = "offered"
        private const val KEY_TEXT = "text"
        private const val KEY_AT = "at"
        private const val CLIP_LABEL = "云隙输入"
    }
}

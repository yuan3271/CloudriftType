package com.yuan3271.cloudrift.data

import android.app.DownloadManager
import android.content.Context
import androidx.core.content.edit
import androidx.core.net.toUri
import com.yuan3271.cloudrift.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** A published release that is newer than the one installed. */
data class UpdateInfo(
    val versionName: String,
    val releaseUrl: String,
    /** Direct APK asset when the release has one; null means "open the release page". */
    val apkUrl: String?,
)

enum class UpdateCheckStatus {
    Idle,
    Checking,
    UpToDate,
    Failed,
}

/**
 * Looks for a newer release on GitHub.
 *
 * Deliberately cheap and quiet: one request to the releases API, at most once per the interval the
 * user picked, skipped entirely when they picked "不检测". Nothing is downloaded until the user taps
 * the button, and a failed check just leaves the previous answer in place - an update prompt is not
 * worth an error message. The interval itself is [UpdateInterval].
 */
class UpdateChecker(
    context: Context,
    private val scope: CoroutineScope,
    private val settingsProvider: () -> AppSettings,
) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _available = MutableStateFlow<UpdateInfo?>(null)
    val available: StateFlow<UpdateInfo?> = _available.asStateFlow()
    private val _status = MutableStateFlow(UpdateCheckStatus.Idle)
    val status: StateFlow<UpdateCheckStatus> = _status.asStateFlow()

    /** True while a fetch is on the wire, so 每次打开键盘 cannot stack up requests. */
    @Volatile
    private var checking = false

    init {
        _available.value = readCached()
    }

    /** Runs only when the interval has elapsed; [force] is the settings screen's "立即检测". */
    fun checkIfDue(force: Boolean = false) {
        val interval = settingsProvider().updateCheckInterval
        if (interval == UpdateInterval.Never) {
            if (force) {
                clear()
                _status.value = UpdateCheckStatus.Idle
            }
            return
        }
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        val now = System.currentTimeMillis()
        if (!force && !interval.isDue(lastCheckMillis = last, nowMillis = now)) return
        if (checking) return
        checking = true
        _status.value = UpdateCheckStatus.Checking
        scope.launch {
            try {
                val found = withContext(Dispatchers.IO) { fetchLatest() }
                prefs.edit { putLong(KEY_LAST_CHECK, now) }
                if (found != null) {
                    _available.value = found
                    cache(found)
                    _status.value = UpdateCheckStatus.UpToDate
                } else {
                    clear()
                    _status.value = UpdateCheckStatus.UpToDate
                }
            } catch (_: Exception) {
                // Do not advance KEY_LAST_CHECK on failure: the next automatic opportunity retries.
                _status.value = UpdateCheckStatus.Failed
            } finally {
                checking = false
            }
        }
    }

    /**
     * Hands the APK to the system downloader.
     *
     * @param viaMirror fetch through a GitHub proxy instead of going straight to the release asset.
     *   GitHub's own download host is slow or unreachable on some networks (the same reason the
     *   dictionary fetch needed a mirror), so the user gets to pick rather than having one button
     *   that silently does nothing.
     */
    fun download(info: UpdateInfo, viaMirror: Boolean = false) {
        val apkUrl = info.apkUrl
        if (apkUrl == null) return
        val url = if (viaMirror) MIRROR_PREFIX + apkUrl else apkUrl
        val fileName = buildString {
            append("cloudrift-type-")
            append(info.versionName)
            if (viaMirror) append("-mirror")
            append(".apk")
        }
        runCatching {
            val request = DownloadManager.Request(url.toUri())
                .setTitle("云隙输入 ${info.versionName}")
                .setDescription(if (viaMirror) "正在通过加速镜像下载" else "正在下载新版本")
                .setMimeType(APK_MIME)
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
                )
                .setDestinationInExternalPublicDir(
                    android.os.Environment.DIRECTORY_DOWNLOADS,
                    fileName,
                )
            appContext.getSystemService(DownloadManager::class.java)?.enqueue(request)
        }
    }

    private fun fetchLatest(): UpdateInfo? =
        runCatching { fetchFromUrl(LATEST_RELEASE_URL) }
            .getOrElse { fetchFromUrl(RELEASES_URL) }

    private fun fetchFromUrl(url: String): UpdateInfo? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "CloudriftType/${BuildConfig.VERSION_NAME}")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("GitHub update request failed: HTTP ${response.code}")
            }
            val body = JSONObject(response.body.string())
            val releases = body.optJSONArray("items") ?: return@use parseRelease(body)
            for (index in 0 until releases.length()) {
                val candidate = releases.optJSONObject(index) ?: continue
                if (candidate.optBoolean("draft") || candidate.optBoolean("prerelease")) continue
                val parsed = parseRelease(candidate) ?: continue
                if (isNewer(parsed.versionName)) return@use parsed
            }
            null
        }
    }

    private fun parseRelease(body: JSONObject): UpdateInfo? {
        if (body.optBoolean("draft") || body.optBoolean("prerelease")) return null
        val tag = body.optString("tag_name").removePrefix("v")
        if (tag.isEmpty() || !isNewer(tag)) return null
        val assets = body.optJSONArray("assets")
        var apkUrl: String? = null
        if (assets != null) {
            for (index in 0 until assets.length()) {
                val url = assets.optJSONObject(index)?.optString("browser_download_url").orEmpty()
                if (url.endsWith(".apk")) {
                    apkUrl = url
                    break
                }
            }
        }
        return UpdateInfo(
            versionName = tag,
            releaseUrl = body.optString("html_url"),
            apkUrl = apkUrl,
        )
    }

    /** True when [tag] names a version above the installed one. */
    internal fun isNewer(tag: String): Boolean = compareVersions(tag, BuildConfig.VERSION_NAME) > 0

    private fun clear() {
        _available.value = null
        prefs.edit { remove(KEY_CACHED) }
    }

    private fun cache(info: UpdateInfo) {
        runCatching {
            prefs.edit {
                putString(
                    KEY_CACHED,
                    JSONObject()
                        .put("version", info.versionName)
                        .put("release", info.releaseUrl)
                        .put("apk", info.apkUrl ?: "")
                        .toString(),
                )
            }
        }
    }

    private fun readCached(): UpdateInfo? = runCatching {
        val raw = prefs.getString(KEY_CACHED, null) ?: return@runCatching null
        val json = JSONObject(raw)
        val version = json.optString("version")
        if (version.isEmpty() || !isNewer(version)) return@runCatching null
        UpdateInfo(
            versionName = version,
            releaseUrl = json.optString("release"),
            apkUrl = json.optString("apk").ifEmpty { null },
        )
    }.getOrNull()

    companion object {
        private const val FILE_NAME = "cloudrift_update"
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_CACHED = "cached"
        private const val APK_MIME = "application/vnd.android.package-archive"
        /** Public GitHub proxy; the download URL is appended to it verbatim. */
        private const val MIRROR_PREFIX = "https://ghproxy.net/"
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/yuan3271/CloudriftType/releases/latest"
        private const val RELEASES_URL =
            "https://api.github.com/repos/yuan3271/CloudriftType/releases?per_page=10"

        /**
         * Numeric comparison of dotted versions, so 0.2.10 is newer than 0.2.9 (a plain string
         * compare would call it older). Missing parts count as zero.
         */
        internal fun compareVersions(left: String, right: String): Int {
            val a = left.split('.', '-', '+')
            val b = right.split('.', '-', '+')
            for (index in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrNull(index)?.toIntOrNull() ?: 0
                val y = b.getOrNull(index)?.toIntOrNull() ?: 0
                if (x != y) return x - y
            }
            return 0
        }
    }
}

package com.yuan3271.cloudrift.voice

import com.yuan3271.cloudrift.data.ApiEndpoint
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

internal val sharedClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}

/**
 * The correction endpoint is a short text round trip, so it gets a deadline the recognition
 * endpoint must not have (a two minute recording legitimately takes longer than 15 s to upload
 * and transcribe).
 *
 * Without this cap a half-open connection could hold the panel on 「修正中…」 for the shared
 * client's 120 s read timeout, twice over with the retry below - close to four minutes with no
 * button that does anything. Now the wait is bounded, and running out of time falls back to the
 * raw transcript, which is a result the user can already use.
 *
 * newBuilder() shares the connection pool and dispatcher with [sharedClient], so this costs no
 * extra threads.
 */
internal val correctionClient: OkHttpClient by lazy {
    sharedClient.newBuilder()
        .callTimeout(CORRECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()
}

internal fun ApiEndpoint.endpointUrl(path: String): String =
    baseUrl.trimEnd('/') + "/" + path.trimStart('/')

/**
 * Runs a request with a single deterministic retry for the two cases where retrying is
 * actually useful. Everything else fails fast and reports why.
 */
internal fun executeWithRetry(
    description: String,
    client: OkHttpClient = sharedClient,
    buildRequest: () -> Request,
): Response {
    var attempt = 0
    while (true) {
        attempt++
        val response = try {
            client.newCall(buildRequest()).execute()
        } catch (e: IOException) {
            if (attempt < 2) {
                Thread.sleep(RETRY_DELAY_MS)
                continue
            }
            throw VoiceInputException("$description 网络不可用：${e.message ?: "连接失败"}")
        }
        if (response.isSuccessful) return response

        val retryable = response.code == 429 || response.code in 500..599
        if (retryable && attempt < 2) {
            response.close()
            Thread.sleep(RETRY_DELAY_MS)
            continue
        }
        val detail = readErrorDetail(response)
        response.close()
        throw VoiceInputException("$description 失败（HTTP ${response.code}）${detail?.let { "：$it" } ?: ""}")
    }
}

private fun readErrorDetail(response: Response): String? = runCatching {
    response.body.string().takeIf { it.isNotBlank() }?.take(160)
}.getOrNull()

class VoiceInputException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Deadline for one correction call. See [correctionClient]. */
internal const val CORRECTION_TIMEOUT_MS = 15_000L

private const val RETRY_DELAY_MS = 900L

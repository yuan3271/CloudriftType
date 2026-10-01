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

internal fun ApiEndpoint.endpointUrl(path: String): String =
    baseUrl.trimEnd('/') + "/" + path.trimStart('/')

/**
 * Runs a request with a single deterministic retry for the two cases where retrying is
 * actually useful. Everything else fails fast and reports why.
 */
internal fun executeWithRetry(
    description: String,
    buildRequest: () -> Request,
): Response {
    var attempt = 0
    while (true) {
        attempt++
        val response = try {
            sharedClient.newCall(buildRequest()).execute()
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

private const val RETRY_DELAY_MS = 900L

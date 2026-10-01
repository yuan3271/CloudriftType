package com.yuan3271.cloudrift.voice

import android.util.Base64
import com.yuan3271.cloudrift.data.ApiEndpoint
import com.yuan3271.cloudrift.data.AppSettings
import java.io.File
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * 千问AI平台（阿里云百炼）语音识别。
 *
 * Aliyun has no OpenAI compatible speech endpoint, so this uses the multimodal-generation
 * endpoint documented for `qwen-audio-3.x-asr-flash`. It is a synchronous call and - crucially
 * for an input method - accepts the audio **inline as a base64 data URL**, so the keyboard
 * never needs to host the recording anywhere.
 *
 * Request  : POST {base}/services/aigc/multimodal-generation/generation
 * Response : {"output":{"output":{"sentence":{"text":"..."}},"text":"..."}}
 *
 * The two optional accuracy features from the same documentation are wired in: instant hot
 * words (`parameters.vocabulary`) and the language hint (`parameters.asr_options.language`).
 */
class QianwenAsrClient {

    fun transcribe(
        endpoint: ApiEndpoint,
        audio: File,
        language: String,
        hotWords: String,
    ): String {
        if (!endpoint.isConfigured) throw VoiceInputException("尚未配置千问语音识别 API Key")

        val bytes = audio.readBytes()
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        if (encoded.length > MAX_ENCODED_CHARS) {
            throw VoiceInputException("录音过长，云端限制了单次上传大小，请分段说话")
        }

        val content = JSONObject()
            .put("type", "input_audio")
            .put(
                "input_audio",
                JSONObject().put("data", "$WAV_DATA_URL_PREFIX$encoded"),
            )
        val payload = JSONObject()
            .put("model", endpoint.model)
            .put(
                "input",
                JSONObject().put(
                    "messages",
                    JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", JSONArray().put(content)),
                    ),
                ),
            )
            .put("parameters", parameters(endpoint, language, hotWords))

        val request = Request.Builder()
            .url(endpoint.endpointUrl(AppSettings.QIANWEN_SPEECH_PATH))
            .addHeader("Authorization", "Bearer ${endpoint.apiKey}")
            .addHeader("X-DashScope-SSE", "disable")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        executeWithRetry("语音识别（千问）") { request }.use { response ->
            val body = response.body.string()
            val json = runCatching { JSONObject(body) }.getOrNull()
                ?: throw VoiceInputException("千问返回了无法解析的内容")
            extractText(json)?.let { return it }
            val message = json.optString("message")
                .ifBlank { json.optJSONObject("output")?.optString("message").orEmpty() }
                .ifBlank { body.take(160) }
            throw VoiceInputException("千问语音识别失败：$message")
        }
    }

    private fun parameters(endpoint: ApiEndpoint, language: String, hotWords: String): JSONObject {
        val parameters = JSONObject()
            .put("format", "wav")
            .put("sample_rate", SAMPLE_RATE)

        val vocabulary = parseHotWords(hotWords)
        if (vocabulary.length() > 0) parameters.put("vocabulary", vocabulary)

        val hint = endpoint.languageHint.ifBlank { language }
        if (hint.isNotBlank()) {
            parameters.put("asr_options", JSONObject().put("language", hint))
        }
        return parameters
    }

    /**
     * `词` or `词:权重`, one per line or comma separated. The documented weight range is 1..5,
     * with 50 as "super hot word"; anything else is clamped into the normal band.
     */
    internal fun parseHotWords(raw: String): JSONObject {
        val result = JSONObject()
        if (raw.isBlank()) return result
        val entries = raw.split(',', '\n', '，', ';', '；')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        for (entry in entries.take(MAX_HOT_WORDS)) {
            val separator = entry.lastIndexOf(':').takeIf { it > 0 }
                ?: entry.lastIndexOf('：').takeIf { it > 0 }
            val word: String
            val weight: Int
            if (separator == null) {
                word = entry
                weight = DEFAULT_HOT_WORD_WEIGHT
            } else {
                word = entry.substring(0, separator).trim()
                val parsed = entry.substring(separator + 1).trim().toIntOrNull()
                weight = when {
                    parsed == null -> DEFAULT_HOT_WORD_WEIGHT
                    parsed == SUPER_HOT_WORD_WEIGHT -> SUPER_HOT_WORD_WEIGHT
                    else -> parsed.coerceIn(1, 5)
                }
            }
            if (word.isNotEmpty() && word.length <= MAX_HOT_WORD_LENGTH) {
                result.put(word, weight)
            }
        }
        return result
    }

    /** The documented response nests the text two levels deep and has no `choices` array. */
    internal fun extractText(json: JSONObject): String? {
        val output = json.optJSONObject("output") ?: return null
        val candidates = listOf(
            output.optJSONObject("output")?.optJSONObject("sentence")?.optString("text"),
            output.optString("text"),
        )
        return candidates.firstOrNull { !it.isNullOrBlank() }?.trim()
    }

    private companion object {
        const val WAV_DATA_URL_PREFIX = "data:audio/wav;base64,"
        const val SAMPLE_RATE = 16_000
        const val DEFAULT_HOT_WORD_WEIGHT = 4
        const val SUPER_HOT_WORD_WEIGHT = 50
        const val MAX_HOT_WORDS = 100
        const val MAX_HOT_WORD_LENGTH = 15

        /** The service rejects payloads over 10 MB *after* base64 expansion. */
        const val MAX_ENCODED_CHARS = 10 * 1024 * 1024
    }
}

package com.yuan3271.cloudrift.voice

import com.yuan3271.cloudrift.data.ApiEndpoint
import java.io.File
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject

/**
 * Speech to text against an OpenAI compatible `/audio/transcriptions` endpoint.
 */
class SpeechApiClient {

    fun transcribe(endpoint: ApiEndpoint, audio: File, language: String): String {
        if (!endpoint.isConfigured) {
            throw VoiceInputException("尚未配置语音识别 API Key")
        }
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", endpoint.model)
            .addFormDataPart(
                "file",
                audio.name,
                audio.asRequestBody(WAV_MEDIA_TYPE),
            )
        val hint = endpoint.languageHint.ifBlank { language }
        if (hint.isNotBlank()) builder.addFormDataPart("language", hint)

        val request = Request.Builder()
            .url(endpoint.endpointUrl("audio/transcriptions"))
            .addHeader("Authorization", "Bearer ${endpoint.apiKey}")
            .post(builder.build())
            .build()

        executeWithRetry("语音识别") { request }.use { response ->
            val body = response.body.string()
            val text = runCatching { JSONObject(body).optString("text") }.getOrDefault("")
            if (text.isBlank()) throw VoiceInputException("语音识别没有返回文本")
            return text.trim()
        }
    }

    private companion object {
        val WAV_MEDIA_TYPE = "audio/wav".toMediaType()
    }
}

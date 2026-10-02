package com.yuan3271.cloudrift.voice

import com.yuan3271.cloudrift.data.ApiEndpoint
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * The chat endpoint is used for exactly one job: turning a raw transcript into what the
 * speaker meant to type. The prompt is a whitelist of three edits - homophones, a missing
 * sentence-final mark, and a sentence-final period that does not belong there - and the result
 * is discarded when it looks like the model went past that list (see [looksLikeCorrection]).
 */
class ChatCorrectionClient {

    /**
     * @param allowPunctuation false when the user turned "自动补充句末标点" off. The prompt then
     *   forbids adding marks, but removing an inappropriate sentence-final period is still
     *   allowed - that direction is a deletion of something the recogniser hallucinated, not an
     *   addition.
     */
    fun correct(
        endpoint: ApiEndpoint,
        transcript: String,
        language: String,
        allowPunctuation: Boolean = true,
    ): String {
        if (!endpoint.isConfigured) return transcript

        val payload = JSONObject()
            .put("model", endpoint.model)
            .put("temperature", 0)
            .put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "system")
                            .put("content", systemPrompt(language, allowPunctuation)),
                    )
                    .put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", transcript),
                    ),
            )

        val request = Request.Builder()
            .url(endpoint.endpointUrl("chat/completions"))
            .addHeader("Authorization", "Bearer ${endpoint.apiKey}")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        executeWithRetry("文本修正", client = correctionClient) { request }.use { response ->
            val body = response.body.string()
            val content = runCatching {
                JSONObject(body)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .optString("content")
            }.getOrDefault("")

            val cleaned = content.trim().trim('"', '“', '”').trim()
            return if (looksLikeCorrection(transcript, cleaned)) cleaned else transcript
        }
    }

    /**
     * Written as a whitelist of edits rather than a list of bans: the model decides only the two
     * things code cannot decide - which homophone was meant, and whether the utterance was a
     * finished sentence or just a fragment that should not end in a period.
     */
    internal fun systemPrompt(language: String, allowPunctuation: Boolean): String = buildString {
        append("你是语音识别结果的后处理器，只允许做下面这些改动：")
        append("一、修正同音字、近音字和错别字；")
        if (allowPunctuation) {
            append("二、当整句话意思已经完整时，补上缺失的句末标点（。？！）；")
        } else {
            append("二、不要新增任何标点；")
        }
        append(
            "三、如果句末的句号并不合适——整句只是短语、关键词、搜索词、语气词，" +
                "或者说话人明显还会接着说——就删掉这个句号；",
        )
        append("除以上改动外必须原样返回：不要改写、润色、扩写、缩写、翻译、调整语气，也不要补全没说完的句子。")
        append("拿不准就保持原文，改动越少越好。")
        if (language.isNotBlank()) append("文本语言为 $language。")
        append("只输出结果本身：不要解释、不要引号、不要代码块、不要换行。")
    }

    internal companion object {
        /**
         * Deterministic guard: a correction should stay close to the transcript in length and
         * must not smuggle in an explanation. Anything else is treated as no correction.
         */
        fun looksLikeCorrection(transcript: String, corrected: String): Boolean {
            if (corrected.isBlank()) return false
            if (corrected == transcript) return true
            // A real correction swaps a few characters or inserts a missing word or mark, so the
            // two strings stay close in length. The band is deliberately asymmetric-free and
            // scales with the utterance: fixed slack for short speech, a third of the length for
            // long speech, which is what tells a paraphrase apart from a fix.
            val slack = maxOf(MIN_LENGTH_SLACK, transcript.length / 3)
            if (corrected.length > transcript.length + slack) return false
            if (transcript.length > corrected.length + slack) return false
            if (corrected.contains('\n')) return false
            val bannedPrefixes = listOf(
                "好的", "这是", "修正后", "修改后", "结果：", "Sure", "Here", "The corrected",
            )
            val lower = corrected.lowercase()
            return bannedPrefixes.none { lower.startsWith(it.lowercase()) }
        }

        private const val MIN_LENGTH_SLACK = 8
    }
}

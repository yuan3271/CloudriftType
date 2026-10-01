package com.yuan3271.cloudrift.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the two decisions that sit between the chat model and the editor: what the model
 * is asked to do ([ChatCorrectionClient.systemPrompt]) and which of its answers are accepted
 * ([ChatCorrectionClient.looksLikeCorrection] plus [VoiceInputController.applyPunctuationPolicy]).
 */
class CorrectionPolicyTest {

    // ---- the prompt ----------------------------------------------------------------

    @Test
    fun `the prompt always allows taking back an unsuitable sentence final period`() {
        val client = ChatCorrectionClient()

        assertTrue(client.systemPrompt("zh", allowPunctuation = true).contains("删掉这个句号"))
        assertTrue(client.systemPrompt("zh", allowPunctuation = false).contains("删掉这个句号"))
    }

    @Test
    fun `the prompt only mentions adding punctuation when the user asked for it`() {
        val client = ChatCorrectionClient()

        assertTrue(
            client.systemPrompt("zh", allowPunctuation = true)
                .contains("补上缺失的句末标点"),
        )
        assertTrue(
            client.systemPrompt("zh", allowPunctuation = false)
                .contains("不要新增任何标点"),
        )
    }

    // ---- the accepted candidate ----------------------------------------------------

    @Test
    fun `a period the model took back is accepted even when punctuation repair is off`() {
        val result = VoiceInputController.applyPunctuationPolicy(
            transcript = "明天几点。",
            corrected = "明天几点",
            allowAddingPunctuation = false,
        )

        assertEquals("明天几点", result)
    }

    @Test
    fun `punctuation the model added is only kept when repair is on`() {
        val rejected = VoiceInputController.applyPunctuationPolicy(
            transcript = "明天几点",
            corrected = "明天几点。",
            allowAddingPunctuation = false,
        )
        val accepted = VoiceInputController.applyPunctuationPolicy(
            transcript = "明天几点",
            corrected = "明天几点。",
            allowAddingPunctuation = true,
        )

        assertEquals("明天几点", rejected)
        assertEquals("明天几点。", accepted)
    }

    @Test
    fun `a homophone fix keeps the sentence final mark the recogniser produced`() {
        val result = VoiceInputController.applyPunctuationPolicy(
            transcript = "我在用威信。",
            corrected = "我在用微信。",
            allowAddingPunctuation = false,
        )

        assertEquals("我在用微信。", result)
    }

    @Test
    fun `swapping one mark for another is not a fix and falls back when repair is off`() {
        val result = VoiceInputController.applyPunctuationPolicy(
            transcript = "真的吗。",
            corrected = "真的吗？",
            allowAddingPunctuation = false,
        )

        assertEquals("真的吗。", result)
    }

    @Test
    fun `an empty candidate falls back to the transcript`() {
        val result = VoiceInputController.applyPunctuationPolicy(
            transcript = "明天几点。",
            corrected = "   ",
            allowAddingPunctuation = true,
        )

        assertEquals("明天几点。", result)
    }

    // ---- the deterministic guard ---------------------------------------------------

    @Test
    fun `a homophone fix passes the guard`() {
        assertTrue(ChatCorrectionClient.looksLikeCorrection("我在用威信", "我在用微信"))
    }

    @Test
    fun `punctuation the model added still passes the guard`() {
        assertTrue(ChatCorrectionClient.looksLikeCorrection("今天几点了", "今天几点了。"))
    }

    @Test
    fun `an answer that grew into a sentence is rejected`() {
        assertFalse(
            ChatCorrectionClient.looksLikeCorrection(
                "今天几点了",
                "请问您是想知道今天的具体时间吗？",
            ),
        )
    }

    @Test
    fun `an answer that opens with a pleasantry is rejected`() {
        assertFalse(
            ChatCorrectionClient.looksLikeCorrection("云隙输入很好用", "好的，云隙输入很好用"),
        )
    }

    @Test
    fun `an answer with a line break is rejected`() {
        assertFalse(ChatCorrectionClient.looksLikeCorrection("你好", "你好\n我是助手"))
    }

    @Test
    fun `an empty answer is rejected`() {
        assertFalse(ChatCorrectionClient.looksLikeCorrection("你好", "   "))
    }
}

package com.yuan3271.cloudrift.voice

/**
 * The voice pipeline is a four step machine: record, transcribe, correct, hand back.
 * Modelling it explicitly keeps the UI from having to infer state from booleans.
 */
sealed interface VoiceState {

    /** Nothing in flight. */
    data object Idle : VoiceState

    data class Recording(
        val elapsedMs: Long,
        /** 0f..1f, already smoothed for display. */
        val level: Float,
        val cancelArmed: Boolean,
    ) : VoiceState

    /** Audio has been captured and is on its way to the speech endpoint. */
    data class Transcribing(val durationMs: Long) : VoiceState

    /** The speech endpoint returned; the chat endpoint is fixing recognition slips. */
    data class Correcting(val transcript: String) : VoiceState

    /** Ready to be committed. [text] is what will be inserted. */
    data class Ready(
        val transcript: String,
        val text: String,
        val corrected: Boolean,
    ) : VoiceState {
        val wasCorrected: Boolean get() = corrected && text != transcript
    }

    data class Failed(val message: String, val needsMicrophonePermission: Boolean = false) :
        VoiceState
}

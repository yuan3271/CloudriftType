package com.yuan3271.cloudrift.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.yuan3271.cloudrift.data.ApiStyle
import com.yuan3271.cloudrift.data.ApiEndpoint
import com.yuan3271.cloudrift.data.AppSettings
import com.yuan3271.cloudrift.input.LayoutId
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives record -> transcribe -> correct and publishes the result as [VoiceState].
 *
 * The class owns no UI and no editor access; the IME controller decides what to do with a
 * finished transcript. That split is what lets the whole pipeline be exercised from a
 * settings screen or a test.
 */
class VoiceInputController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val speechClient: SpeechApiClient = SpeechApiClient(),
    private val qianwenClient: QianwenAsrClient = QianwenAsrClient(),
    private val chatClient: ChatCorrectionClient = ChatCorrectionClient(),
) {

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)

    /**
     * 「跳过修正」按下的标记。纠错请求本身不打断（它已经有 15 秒上限），但它一回来就把结果丢掉，
     * 直接上屏识别原文——按钮的语义是"不用等了"，不是"取消这条语音"。
     */
    private var skipCorrection = false

    /** 面板上的「跳过修正」：立刻回到"可以上屏"，并让在途的纠错结果作废。 */
    fun skipCorrection() {
        val current = _state.value
        if (current !is VoiceState.Correcting) return
        skipCorrection = true
        publish(
            VoiceState.Ready(
                transcript = current.transcript,
                text = current.transcript,
                corrected = false,
            ),
        )
    }
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private var recorder: AudioRecorder? = null
    private var ticker: Job? = null
    private var work: Job? = null
    private var startedAt = 0L
    private var smoothedLevel = 0f
    private var activeSettings: AppSettings? = null

    /** Narrowed layout hint so the speech endpoint gets a useful language tag. */
    var layoutHint: LayoutId = LayoutId.Pinyin26

    val isIdle: Boolean get() = _state.value is VoiceState.Idle

    /**
     * The only way [VoiceState] is ever published, so the microphone can never outlive the state
     * that is allowed to hold it: the moment the state stops being Recording, the track is
     * released. Transitions out of Recording happen from a tap, an error and a two minute timer,
     * and a state machine that only remembers to stop the recorder on some of them is exactly how
     * the microphone stayed claimed for good.
     */
    private fun publish(state: VoiceState) {
        if (state !is VoiceState.Recording) stopCapture()
        _state.value = state
    }

    fun start(settings: AppSettings) {
        if (!isIdle) return
        activeSettings = settings
        skipCorrection = false
        if (!hasMicrophonePermission()) {
            publish(
                VoiceState.Failed(
                    message = "需要麦克风权限才能使用语音输入",
                    needsMicrophonePermission = true,
                ),
            )
            return
        }
        val file = File(File(context.cacheDir, "voice"), "capture-${System.currentTimeMillis()}.wav")
        val newRecorder = AudioRecorder(file)
        recorder = newRecorder
        startedAt = System.currentTimeMillis()
        smoothedLevel = 0f
        publish(VoiceState.Recording(0, 0f, cancelArmed = false))
        try {
            newRecorder.start(scope) { peak ->
                smoothedLevel = smoothedLevel * 0.7f + peak * 0.3f
            }
        } catch (e: Exception) {
            // The recorder releases its own track before rethrowing (see AudioRecorder.start);
            // cancel() is what also drops the file it may have opened, so a failed attempt leaves
            // neither a microphone held nor a stray wav in the cache.
            newRecorder.cancel()
            recorder = null
            publish(VoiceState.Failed(e.message ?: "无法访问麦克风"))
            return
        }
        ticker = scope.launch {
            while (true) {
                delay(TICK_MS)
                val current = _state.value
                if (current !is VoiceState.Recording) break
                val elapsed = System.currentTimeMillis() - startedAt
                if (elapsed >= MAX_RECORDING_MS) {
                    // The cloud endpoint caps a single upload, so stop on our own terms rather
                    // than sending something the service will reject.
                    activeSettings?.let { stopAndProcess(it) }
                    break
                }
                publish(current.copy(elapsedMs = elapsed, level = smoothedLevel))
            }
        }
    }

    fun setCancelArmed(armed: Boolean) {
        val current = _state.value
        if (current is VoiceState.Recording) publish(current.copy(cancelArmed = armed))
    }

    /** Stops capture and runs the pipeline. [settings] is read once so a mid flight edit
     *  cannot change the endpoints under the running request. */
    fun stopAndProcess(settings: AppSettings) {
        val active = recorder ?: return
        ticker?.cancel()
        ticker = null
        val duration = active.stop()
        recorder = null
        val file = active.file
        if (duration < MIN_DURATION_MS) {
            file.delete()
            publish(VoiceState.Failed("录音太短，请再说一次"))
            return
        }
        publish(VoiceState.Transcribing(duration))
        work = scope.launch { runPipeline(settings, file, duration) }
    }

    fun cancel() {
        stopCapture()
        work?.cancel()
        work = null
        publish(VoiceState.Idle)
    }

    /**
     * Lets go of the microphone without touching a pipeline that already finished with it.
     *
     * Called when the keyboard window goes away, where the recording has no way to be listened
     * to any more: a dictation the user can no longer see must not keep the microphone claimed.
     * Transcribing / correcting / ready are left alone - the track is already released by then,
     * and there is a result worth keeping.
     */
    fun releaseMicrophone() {
        if (_state.value !is VoiceState.Recording) return
        cancel()
    }

    /**
     * Discards the result without committing it.
     *
     * The recording panel and the result panel funnel their 取消 into this one method, so it has
     * to be able to end a *running* recording, not just drop a finished one. It used to only clear
     * the state, which left the AudioRecord open: the ticker saw a state that was no longer
     * recording and stopped checking, so the two minute cap never fired, and the capture loop went
     * on reading the microphone until the process died. That is what "the app holds the microphone
     * when it is not using it" looked like from the outside.
     */
    fun dismiss() {
        // A pipeline that is already running has to be stopped too, not just forgotten: its
        // `publish(Ready)` would otherwise land *after* the user cancelled and pop the result
        // panel back up over the keyboard they had just gone back to.
        work?.cancel()
        work = null
        stopCapture()
        cleanupFiles()
        publish(VoiceState.Idle)
    }

    /** Ends capture and hands the microphone back; the state is left to the caller. */
    private fun stopCapture() {
        ticker?.cancel()
        ticker = null
        recorder?.cancel()
        recorder = null
    }

    private suspend fun runPipeline(settings: AppSettings, file: File, duration: Long) {
        try {
            val language = languageTagFor(layoutHint)
            val transcript = withContext(Dispatchers.IO) {
                transcribe(settings.speech, file, language, settings.hotWords)
            }
            var text = transcript
            var corrected = false
            if (settings.voiceCorrection && canCorrect(settings.chat)) {
                publish(VoiceState.Correcting(transcript))
                // 修正可以被跳过：面板上的「跳过修正」把 skipCorrection 置位，这里在最靠近用户
                // 决定的地方检查一次——正在跑的请求不打断（它本来就有 15 秒上限），但结果丢掉，
                // 直接上屏识别原文。这样按钮的语义是"不用等了"，而不是"取消整条语音"。
                val fixed = withContext(Dispatchers.IO) {
                    runCatching {
                        chatClient.correct(
                            endpoint = settings.chat,
                            transcript = transcript,
                            language = language,
                            allowPunctuation = settings.autoPunctuation,
                        )
                    }
                        .getOrDefault(transcript)
                }
                if (skipCorrection) {
                    skipCorrection = false
                    publish(VoiceState.Ready(transcript, transcript, corrected = false))
                    return
                }
                val candidate = applyPunctuationPolicy(transcript, fixed, settings.autoPunctuation)
                corrected = candidate != transcript
                text = candidate
            } else if (settings.autoPunctuation) {
                text = ensureSentencePunctuation(transcript)
                corrected = text != transcript
            }
            publish(VoiceState.Ready(transcript = transcript, text = text, corrected = corrected))
        } catch (e: CancellationException) {
            // Cancelled means the user dismissed the panel: no state to publish, and above all no
            // 「失败」 blinking up after the cancel they just asked for.
            throw e
        } catch (e: VoiceInputException) {
            publish(VoiceState.Failed(e.message ?: "语音识别失败"))
        } catch (e: Exception) {
            publish(VoiceState.Failed(e.message ?: "语音识别失败"))
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { runCatching { file.delete() } }
        }
    }

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun transcribe(
        endpoint: ApiEndpoint,
        file: File,
        language: String,
        hotWords: String,
    ): String =
        when (endpoint.style) {
            ApiStyle.OpenAiCompatible -> speechClient.transcribe(endpoint, file, language)
            ApiStyle.AliyunQianwen -> qianwenClient.transcribe(endpoint, file, language, hotWords)
        }

    /** The chat endpoint is OpenAI-compatible only; 百炼 is reachable through its compat mode. */
    private fun canCorrect(endpoint: ApiEndpoint): Boolean =
        endpoint.style == ApiStyle.OpenAiCompatible && endpoint.isConfigured

    private fun cleanupFiles() {
        runCatching { File(context.cacheDir, "voice").listFiles()?.forEach { it.delete() } }
    }

    private fun languageTagFor(layout: LayoutId): String = when {
        layout.isChinese -> "zh"
        layout.isJapanese -> "ja"
        else -> "en"
    }

    private fun ensureSentencePunctuation(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return trimmed
        if (trimmed.last() in SENTENCE_END) return trimmed
        return trimmed + sentenceTerminator(layoutHint)
    }

    private fun sentenceTerminator(layout: LayoutId): String = when {
        layout.isJapanese -> "。"
        layout.isChinese -> "。"
        else -> "."
    }

    private val SENTENCE_END = setOf('。', '.', '！', '？', '!', '?', '…')

    internal companion object {
        const val TICK_MS = 60L
        const val MIN_DURATION_MS = 300L
        /** 16 kHz mono WAV is 32 KB/s, so two minutes stays well inside the 10 MB upload cap. */
        const val MAX_RECORDING_MS = 120_000L

        /** Sentence-final marks the recogniser may produce; only the periods are negotiable. */
        private const val TAIL_MARKS = "。．.!！?？…"

        /** The two characters that mean "句号": what the AI is allowed to take back. */
        private const val PERIODS = "。．."

        /**
         * Decides between the transcript and the model's candidate when the only disagreement is
         * the sentence-final punctuation.
         *
         * Removing a trailing 句号 is accepted in both directions of the setting: whether a
         * dictated fragment should end in a period is a judgement about speech, and the model is
         * the only part of the pipeline that can make it. Adding one is a different matter - that
         * is exactly what "自动补充句末标点" is for, so it is dropped when the user turned it off.
         * Anything else (a rewritten ending) falls back to the transcript.
         */
        fun applyPunctuationPolicy(
            transcript: String,
            corrected: String,
            allowAddingPunctuation: Boolean,
        ): String {
            if (corrected.isBlank()) return transcript
            val originalTail = tailMarks(transcript)
            val correctedTail = tailMarks(corrected)
            if (originalTail == correctedTail) return corrected
            val droppedPeriod = correctedTail.isEmpty() && originalTail.all { it in PERIODS }
            return when {
                droppedPeriod -> corrected
                allowAddingPunctuation -> corrected
                else -> transcript
            }
        }

        private fun tailMarks(text: String): String =
            text.trimEnd().takeLastWhile { it in TAIL_MARKS }
    }
}

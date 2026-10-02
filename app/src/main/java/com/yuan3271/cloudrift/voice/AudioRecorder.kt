package com.yuan3271.cloudrift.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Captures 16 kHz mono PCM and writes it into a WAV container.
 *
 * Speech endpoints on the OpenAI compatible API accept wav/mp3/m4a/flac; raw PCM is not on
 * that list, so the container is written by hand. Writing to a file rather than a byte array
 * keeps long recordings off the heap.
 */
class AudioRecorder(val file: File) {

    private var record: AudioRecord? = null
    private var job: Job? = null
    private var bytesWritten: Long = 0

    val isRecording: Boolean get() = job?.isActive == true

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope, onLevel: (Float) -> Unit) {
        require(!isRecording) { "recorder already running" }
        // A track left behind by an earlier attempt is still holding the microphone; opening a
        // second one would leak it for good.
        record?.let { runCatching { it.release() } }
        record = null
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        val bufferSize = if (minBuffer > 0) minBuffer * 2 else SAMPLE_RATE
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            CHANNEL,
            ENCODING,
            bufferSize,
        )
        // From here on the track owns the microphone, so every way out has to release it. The
        // device refuses the track when somebody else has the microphone (a call, another
        // recorder), and dropping the object instead of releasing it used to leave the input
        // claimed by this process - the microphone stayed "in use" long after dictation ended.
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "microphone unavailable" }
            file.parentFile?.mkdirs()
            bytesWritten = 0
            recorder.startRecording()
            record = recorder
            job = scope.launch(Dispatchers.IO) {
                RandomAccessFile(file, "rw").use { handle ->
                    handle.setLength(0)
                    // Leave room for the WAV header; the real one is written on stop.
                    handle.write(ByteArray(HEADER_SIZE))
                    val buffer = ShortArray(bufferSize / 2)
                    while (isActive) {
                        val read = recorder.read(buffer, 0, buffer.size)
                        if (read <= 0) continue
                        val bytes = ByteArray(read * 2)
                        var peak = 0
                        for (index in 0 until read) {
                            val sample = buffer[index]
                            bytes[index * 2] = (sample.toInt() and 0xFF).toByte()
                            bytes[index * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                            peak = maxOf(peak, abs(sample.toInt()))
                        }
                        handle.write(bytes)
                        bytesWritten += bytes.size
                        onLevel(min(1f, peak / 32768f * LEVEL_GAIN))
                    }
                }
            }
        } catch (e: Exception) {
            record = null
            job = null
            runCatching { recorder.release() }
            throw e
        }
    }

    /** Stops capture and finalises the WAV header. Returns the recorded duration in ms. */
    fun stop(): Long {
        val recorder = record ?: return 0
        val writer = job
        job = null
        record = null
        // Stop the device first so the blocking read() inside the writer returns, then wait
        // for the writer to close the file before rewriting the header.
        runCatching { recorder.stop() }
        runBlocking { withTimeoutOrNull(WRITER_JOIN_TIMEOUT_MS) { writer?.cancelAndJoin() } }
        // release() even if stop() threw: the track is what holds the microphone, and this is the
        // only place that lets it go.
        runCatching { recorder.release() }
        val written = bytesWritten
        runCatching { writeHeader(file, written) }
        val frames = written / 2
        return frames * 1000L / SAMPLE_RATE
    }

    fun cancel() {
        stop()
        runCatching { file.delete() }
    }

    private fun writeHeader(file: File, dataLength: Long) {
        if (dataLength <= 0) {
            file.delete()
            return
        }
        RandomAccessFile(file, "rw").use { out ->
            val header = ByteArray(HEADER_SIZE)
            var cursor = 0
            fun ascii(text: String) {
                for (ch in text) header[cursor++] = ch.code.toByte()
            }

            fun int32(value: Long) {
                header[cursor++] = (value and 0xFF).toByte()
                header[cursor++] = ((value shr 8) and 0xFF).toByte()
                header[cursor++] = ((value shr 16) and 0xFF).toByte()
                header[cursor++] = ((value shr 24) and 0xFF).toByte()
            }

            fun int16(value: Int) {
                header[cursor++] = (value and 0xFF).toByte()
                header[cursor++] = ((value shr 8) and 0xFF).toByte()
            }

            ascii("RIFF")
            int32(36 + dataLength)
            ascii("WAVE")
            ascii("fmt ")
            int32(16)
            int16(1) // PCM
            int16(CHANNELS)
            int32(SAMPLE_RATE.toLong())
            int32(SAMPLE_RATE.toLong() * CHANNELS * BITS_PER_SAMPLE / 8)
            int16(CHANNELS * BITS_PER_SAMPLE / 8)
            int16(BITS_PER_SAMPLE)
            ascii("data")
            int32(dataLength)
            out.seek(0)
            out.write(header)
        }
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val CHANNELS = 1
        private const val BITS_PER_SAMPLE = 16
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val HEADER_SIZE = 44
        private const val LEVEL_GAIN = 4f
        private const val WRITER_JOIN_TIMEOUT_MS = 500L
    }
}

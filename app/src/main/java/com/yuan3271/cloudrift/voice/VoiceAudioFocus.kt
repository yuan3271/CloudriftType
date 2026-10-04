package com.yuan3271.cloudrift.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import com.yuan3271.cloudrift.data.VoiceMediaBehavior

/**
 * 录音期间对外面正在放的声音的处理（静音 / 压低 / 不管）。
 *
 * 走的是系统给"我要暂时占用一下声音"准备的那条正规路径——音频焦点，而不是自己去调别人的音量：
 *
 * | 设置 | 焦点请求 | 别人的表现 |
 * | --- | --- | --- |
 * | 静音 | `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` | 停下播放（专为录音设计的那一档） |
 * | 压低 | `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` | 把音量降下来继续放 |
 * | 不管 | 不发请求 | 照常放 |
 *
 * 焦点跟着录音走：`VoiceInputController.publish()` 在离开 Recording 状态时收麦克风，[release]
 * 挂在那条唯一出口上（见 `stopCapture`），所以"录音停了音量还压着"这种漏掉是不会发生的。
 *
 * ### 为什么光请求焦点不够（这一版修的就是它）
 *
 * 官方文档（`developer.android.google.cn` 的音频焦点指南）写得很清楚：**自动降低音量只在满足一串
 * 条件时才发生**——正在放的那个应用得成功请求过音频焦点、不是在播 `CONTENT_TYPE_SPEECH`、也没有
 * 设置 `setWillPauseWhenDucked(true)`；否则系统根本不会替我们把它压低。现实里大量播放器/网页视频
 * 不满足这些条件，于是"明明请求了 `GAIN_TRANSIENT_MAY_DUCK`，别人的声音一点没小"。
 *
 * 所以这里除了请求焦点（对守规矩的应用，系统会替我们压低/暂停），**自己把媒体音量降下来**：记住
 * 原值 → 压低（约 30%）或静音 → 用完还原。用户如果在录音期间自己动了音量，我们就不还原（尊重他
 * 刚做的选择）。
 */
class VoiceAudioFocus(private val context: Context) {

    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var request: AudioFocusRequest? = null
    /** 我们自己改音量之前的原值，以及改成的那个值（还原时用来判断用户有没有插过手）。 */
    private var volumeBefore: Int? = null
    private var volumeWeSet: Int? = null

    /** 按当前设置申请焦点；重复调用会先把上一次的还回去。 */
    fun acquire(behavior: VoiceMediaBehavior) {
        release()
        if (behavior == VoiceMediaBehavior.LeaveAlone) return
        lowerVolume(behavior)
        val manager = audioManager ?: return
        val gain = when (behavior) {
            VoiceMediaBehavior.Mute -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            VoiceMediaBehavior.Duck -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            VoiceMediaBehavior.LeaveAlone -> return
        }
        val attributes = AudioAttributes.Builder()
            // 录的是人说话：内容类型是语音，用途按媒体算，焦点才对"正在放的东西"生效。
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val newRequest = AudioFocusRequest.Builder(gain)
            .setAudioAttributes(attributes)
            // 交给系统的自动 duck 就够了，不需要别人暂停后再等我们喊继续。
            .setWillPauseWhenDucked(false)
            .build()
        request = newRequest
        runCatching { manager.requestAudioFocus(newRequest) }
            .onFailure { request = null }
    }

    /** 录音结束（不论成功、取消还是出错）都要还回去。 */
    fun release() {
        restoreVolume()
        val manager = audioManager ?: return
        val current = request ?: return
        request = null
        runCatching { manager.abandonAudioFocusRequest(current) }
    }

    /**
     * 把媒体流调低（压低约三成，或直接静音）。原本就比目标还低就不动——不去把安静的声音"抬高"。
     */
    private fun lowerVolume(behavior: VoiceMediaBehavior) {
        val manager = audioManager ?: return
        runCatching {
            val stream = AudioManager.STREAM_MUSIC
            val current = manager.getStreamVolume(stream)
            val max = manager.getStreamMaxVolume(stream)
            val target = when (behavior) {
                VoiceMediaBehavior.Mute -> 0
                VoiceMediaBehavior.Duck -> minOf(current, (max * DUCK_FRACTION).toInt())
                VoiceMediaBehavior.LeaveAlone -> return@runCatching
            }
            if (target >= current) return@runCatching
            volumeBefore = current
            volumeWeSet = target
            manager.setStreamVolume(stream, target, 0)
        }
    }

    /** 还原成我们改之前的音量；用户自己在我们改完之后又调过，就不动他的。 */
    private fun restoreVolume() {
        val manager = audioManager ?: return
        val before = volumeBefore ?: return
        val ours = volumeWeSet
        volumeBefore = null
        volumeWeSet = null
        runCatching {
            val stream = AudioManager.STREAM_MUSIC
            if (ours != null && manager.getStreamVolume(stream) != ours) return@runCatching
            manager.setStreamVolume(stream, before, 0)
        }
    }

    private companion object {
        /** 压低时留下媒体音量的比例。 */
        const val DUCK_FRACTION = 0.3f
    }
}

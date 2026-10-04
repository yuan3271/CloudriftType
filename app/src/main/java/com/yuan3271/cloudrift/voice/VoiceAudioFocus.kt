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
 * 挂在那条唯一出口上（见 `stopCapture`），所以"录音停了音乐还哑着"这种漏掉是不会发生的。
 *
 * 拿不到焦点（别的应用正占着）时不做任何补救：录音照常进行，只是外面该多响还多响。
 */
class VoiceAudioFocus(private val context: Context) {

    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var request: AudioFocusRequest? = null

    /** 按当前设置申请焦点；重复调用会先把上一次的还回去。 */
    fun acquire(behavior: VoiceMediaBehavior) {
        release()
        if (behavior == VoiceMediaBehavior.LeaveAlone) return
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
        val manager = audioManager ?: return
        val current = request ?: return
        request = null
        runCatching { manager.abandonAudioFocusRequest(current) }
    }
}

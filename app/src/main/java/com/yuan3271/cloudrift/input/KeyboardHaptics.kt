package com.yuan3271.cloudrift.input

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Key feedback through [VibrationEffect] instead of the view's `performHapticFeedback`.
 *
 * The platform's `KEYBOARD_TAP` is a request, not a description: the length and strength come from
 * the ROM, which is why the same keyboard felt soft on one phone and buzzy on another. Driving the
 * vibrator directly pins both - a short, crisp tick for a key, a lighter one for a candidate - and
 * on Android 13+ the vibration is tagged as touch feedback so the system's own intensity setting
 * still applies.
 */
class KeyboardHaptics(context: Context) {

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
    }.getOrNull()

    private var attributes: VibrationAttributes? = null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            attributes = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
        }
    }

    /** One key press. */
    fun key() = tick(KEY_DURATION_MS, KEY_AMPLITUDE)

    /** Picking a candidate: the same click, a touch softer. */
    fun candidate() = tick(KEY_DURATION_MS, CANDIDATE_AMPLITUDE)

    /** Opening or closing a panel, so a mode change is felt rather than only seen. */
    fun panel() = tick(PANEL_DURATION_MS, PANEL_AMPLITUDE)

    private fun tick(durationMs: Long, amplitude: Int) {
        val vibrator = vibrator ?: return
        runCatching {
            val effect = VibrationEffect.createOneShot(durationMs, amplitude)
            val attributes = attributes
            if (attributes != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(effect, attributes)
            } else {
                vibrator.vibrate(effect)
            }
        }
    }

    companion object {
        /** Short enough to read as a tap and not as a buzz; the amplitude carries the feel. */
        private const val KEY_DURATION_MS = 10L
        private const val KEY_AMPLITUDE = 120
        private const val CANDIDATE_AMPLITUDE = 90
        private const val PANEL_DURATION_MS = 16L
        private const val PANEL_AMPLITUDE = 160
    }
}

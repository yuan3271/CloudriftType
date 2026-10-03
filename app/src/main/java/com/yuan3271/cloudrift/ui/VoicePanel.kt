package com.yuan3271.cloudrift.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yuan3271.cloudrift.theme.VoiceActiveRed
import com.yuan3271.cloudrift.ui.icons.CloudriftIcons
import com.yuan3271.cloudrift.voice.VoiceState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Replaces the key area while dictation is running. Four states, one panel, no dialogs:
 * a dialog in an IME window is fragile, and the pipeline is short enough to show inline.
 */
@Composable
fun VoicePanel(
    state: VoiceState,
    modifier: Modifier = Modifier,
    autoApplyDelayMs: Int = 0,
    autoApplyPending: Boolean = false,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    /** 「跳过修正」：不等纠错，直接拿识别原文上屏。 */
    onSkipCorrection: () -> Unit = {},
    onRetry: () -> Unit,
    onCommit: () -> Unit,
    onOpenPermission: () -> Unit,
    onCancelAutoApply: () -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(VOICE_PANEL_HEIGHT)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            when (state) {
                is VoiceState.Idle -> Unit
                is VoiceState.Recording -> RecordingContent(state, onStop, onCancel)
                is VoiceState.Transcribing -> BusyContent("识别中…", null, onCancel)
                is VoiceState.Correcting -> BusyContent(
                    title = "修正中…",
                    transcript = state.transcript,
                    onCancel = onCancel,
                    // 纠错是最慢的一段（翻译式润色）。不想等的人可以跳过：直接上屏识别原文，
                    // 在途的纠错结果回来后被丢掉（见 VoiceInputController.skipCorrection）。
                    onSkip = onSkipCorrection,
                )
                is VoiceState.Ready -> Box(
                    // A tap anywhere on the result takes the keyboard out of automatic mode for
                    // this round: the text stays until the user says 上屏.
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onCancelAutoApply,
                        ),
                ) {
                    ReadyContent(state, autoApplyDelayMs, autoApplyPending, onRetry, onCancel, onCommit)
                }
                is VoiceState.Failed -> FailedContent(state, onRetry, onCancel, onOpenPermission)
            }
        }
    }
}

/**
 * What the candidate strip turns into while the space bar is held down.
 *
 * The full [VoicePanel] cannot be used for this: it replaces the key area, which would pull the
 * space bar out from under the finger that is holding it. The gesture would be cancelled and
 * the release that is supposed to end the recording would never arrive. So hold-to-talk keeps
 * the keys and borrows only this strip.
 */
@Composable
fun ListeningStrip(
    level: Float,
    elapsedMs: Long,
    modifier: Modifier = Modifier,
    cancelArmed: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(HOLD_STRIP_HEIGHT)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = CloudriftIcons.Mic,
            contentDescription = null,
            tint = VoiceActiveRed,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = "正在聆听",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        LevelTrack(level = level)
        Spacer(Modifier.weight(1f))
        Text(
            text = formatSeconds(elapsedMs),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = VoiceActiveRed,
        )
        Text(
            text = if (cancelArmed) "↑ 松开取消" else "上滑取消",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (cancelArmed) FontWeight.SemiBold else FontWeight.Normal,
            color = if (cancelArmed) {
                VoiceActiveRed
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun LevelTrack(level: Float) {
    val smoothed by animateFloatAsState(level.coerceIn(0f, 1f), label = "hold-level")
    Box(
        modifier = Modifier
            .width(64.dp)
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(smoothed.coerceAtLeast(MIN_TRACK_FRACTION))
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(VoiceActiveRed),
        )
    }
}

@Composable
private fun RecordingContent(
    state: VoiceState.Recording,
    onStop: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LevelMeter(level = state.level)
        Text(
            text = formatDuration(state.elapsedMs),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Icon(CloudriftIcons.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("取消")
            }
            Button(
                onClick = onStop,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = VoiceActiveRed),
            ) {
                Icon(CloudriftIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("完成")
            }
        }
    }
}

@Composable
private fun LevelMeter(level: Float) {
    val smoothed by animateFloatAsState(level.coerceIn(0f, 1f), label = "level")
    val bars = 17
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(34.dp),
    ) {
        for (index in 0 until bars) {
            // A symmetric envelope so the meter reads as a voice, not as a progress bar.
            val distance = kotlin.math.abs(index - bars / 2) / (bars / 2f)
            val envelope = 1f - distance * 0.75f
            val height = (6f + 26f * smoothed * envelope).roundToInt().coerceAtLeast(6)
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (smoothed > 0.02f) {
                            VoiceActiveRed.copy(alpha = 0.45f + 0.55f * envelope)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BusyContent(
    title: String,
    transcript: String?,
    onCancel: () -> Unit,
    onSkip: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LoadingIndicator(modifier = Modifier.size(28.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!transcript.isNullOrBlank()) {
                Text(
                    text = transcript,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
        if (onSkip != null) {
            TextButton(onClick = onSkip) { Text("跳过修正") }
        }
        // 识别和纠错都可能卡在网络上，而这块面板会把按键整块顶掉：没有这个按钮，等待期间
        // 用户既不能打字也不能退出，只能等超时。
        TextButton(onClick = onCancel) { Text("取消") }
    }
}

@Composable
private fun ReadyContent(
    state: VoiceState.Ready,
    autoApplyDelayMs: Int,
    autoApplyPending: Boolean,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onCommit: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                imageVector = CloudriftIcons.Spellcheck,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = if (state.wasCorrected) "已修正识别结果" else "识别结果",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (autoApplyPending && autoApplyDelayMs > 0) {
                Spacer(Modifier.weight(1f))
                AutoApplyCountdown(autoApplyDelayMs)
            }
        }
        // A long sentence scrolls inside the panel instead of making the keyboard taller: the
        // result is worth reading in full, but the key area's height is not negotiable.
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = state.text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (state.wasCorrected) {
                Text(
                    text = state.transcript,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
        if (scroll.maxValue > 0) {
            Text(
                text = "上下滑动查看全文",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = onRetry) {
                Icon(CloudriftIcons.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("重说")
            }
            TextButton(onClick = onCancel) { Text("取消") }
            Spacer(Modifier.weight(1f))
            Button(onClick = onCommit) {
                Icon(CloudriftIcons.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("上屏")
            }
        }
    }
}

/**
 * Live countdown for the automatic apply. Showing the remaining time matters: the keyboard is
 * about to change the text by itself, and the user needs to see how long they have to stop it.
 */
@Composable
private fun AutoApplyCountdown(delayMs: Int) {
    var remainingMs by remember(delayMs) { mutableIntStateOf(delayMs) }
    LaunchedEffect(delayMs) {
        val startedAt = System.currentTimeMillis()
        while (true) {
            val left = delayMs - (System.currentTimeMillis() - startedAt)
            remainingMs = left.coerceAtLeast(0L).toInt()
            if (left <= 0L) break
            delay(COUNTDOWN_TICK_MS)
        }
    }
    Text(
        text = "${((remainingMs + 999) / 1000).coerceAtLeast(1)} 秒后自动上屏",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun FailedContent(
    state: VoiceState.Failed,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onOpenPermission: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = state.message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Start,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.needsMicrophonePermission) {
                Button(onClick = onOpenPermission) { Text("去授权") }
            } else {
                Button(onClick = onRetry) { Text("重试") }
            }
            // 重试之外必须有一条退路：失败面板同样占着整块按键区，只剩「重试」时，一次识别
            // 出错就等于把键盘锁在了这个面板上。
            TextButton(onClick = onCancel) { Text("取消") }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    return "%d:%02d.%d".format(totalSeconds / 60, totalSeconds % 60, (millis % 1000) / 100)
}

private fun formatSeconds(millis: Long): String {
    val totalSeconds = millis / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private val VOICE_PANEL_HEIGHT = 168.dp

/** Matches the candidate strip, so switching between the two never resizes the keyboard. */
private val HOLD_STRIP_HEIGHT = 44.dp

private const val MIN_TRACK_FRACTION = 0.06f

private const val COUNTDOWN_TICK_MS = 100L

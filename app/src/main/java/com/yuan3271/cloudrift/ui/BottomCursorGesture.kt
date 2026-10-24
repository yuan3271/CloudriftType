package com.yuan3271.cloudrift.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yuan3271.cloudrift.input.KeyCode
import com.yuan3271.cloudrift.input.KeyDef
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

private val CURSOR_RAIL_HEIGHT = 24.dp
private val CURSOR_DOT_SIZE = 14.dp

/**
 * Converts absolute finger displacement into a cursor step count. The sign is symmetric, so a
 * left pull and a right pull of the same distance cross the threshold together.
 */
internal fun cursorStepsFor(distancePx: Float, stepPx: Float): Int {
    if (!distancePx.isFinite() || !stepPx.isFinite() || stepPx <= 0f) return 0
    val steps = floor(abs(distancePx) / stepPx).toInt()
    return if (distancePx < 0f) -steps else steps
}

/**
 * The visible rail uses the same face width as the active layout's space bar. Key faces inside a
 * weighted row are shortened by one [KEY_GAP] because each key owns half a gap on both sides.
 */
internal fun spaceBarRailWidth(availableWidth: Dp, row: List<KeyDef>): Dp {
    val space = row.firstOrNull { it.code == KeyCode.Space } ?: return availableWidth
    val totalWeight = row.sumOf { it.weight.toDouble() }.toFloat()
    if (totalWeight <= 0f) return availableWidth
    val faceWidth = availableWidth * (space.weight / totalWeight) - KEY_GAP
    return if (faceWidth < 1.dp) 1.dp else faceWidth
}

/**
 * The keyboard's bottom edge doubles as a cursor track. Nothing is painted at rest; once a finger
 * lands, a rail the width of the space bar fades in and its dot follows the pointer. Movement is
 * relative to the touch down point and advances exactly one character per configured distance.
 */
@Composable
internal fun BottomCursorGesture(
    enabled: Boolean,
    stepDp: Dp,
    spaceBarRow: List<KeyDef>,
    onCursorDrag: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val stepPx = with(density) { stepDp.toPx() }
    val dotPx = with(density) { CURSOR_DOT_SIZE.toPx() }
    var active by remember { mutableStateOf(false) }
    var pointerXPx by remember { mutableFloatStateOf(0f) }
    var railWidthPx by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(enabled, stepPx) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    active = true
                    pointerXPx = down.position.x
                    var emittedSteps = 0
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) break
                            pointerXPx = change.position.x
                            val totalSteps = cursorStepsFor(
                                distancePx = change.position.x - down.position.x,
                                stepPx = stepPx,
                            )
                            if (totalSteps != emittedSteps) {
                                onCursorDrag(totalSteps - emittedSteps)
                                emittedSteps = totalSteps
                            }
                        }
                    } finally {
                        active = false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val railWidth = spaceBarRailWidth(maxWidth, spaceBarRow)
        val railStartPx = with(density) { ((maxWidth - railWidth) / 2f).toPx() }
        AnimatedVisibility(
            visible = active && enabled,
            enter = fadeIn(tween(90)),
            exit = fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Box(
                modifier = Modifier
                    .width(railWidth)
                    .height(CURSOR_RAIL_HEIGHT)
                    .onSizeChanged { railWidthPx = it.width.toFloat() }
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .height(2.dp)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                val dotX = (pointerXPx - railStartPx - dotPx / 2f)
                    .coerceIn(0f, (railWidthPx - dotPx).coerceAtLeast(0f))
                Box(
                    modifier = Modifier
                        .offset { IntOffset(dotX.roundToInt(), 0) }
                        .size(CURSOR_DOT_SIZE)
                        .align(Alignment.CenterStart)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

package com.yuan3271.cloudrift.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuan3271.cloudrift.data.KeyBackground
import com.yuan3271.cloudrift.input.KeyCode
import com.yuan3271.cloudrift.input.KeyDef
import com.yuan3271.cloudrift.input.KeyStyle
import com.yuan3271.cloudrift.ui.icons.CloudriftIcons
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Interaction contract for one key, so the canvas can route every gesture without knowing
 * what the key means.
 */
data class KeyCallbacks(
    val onKey: (KeyDef) -> Unit,
    val onAlternate: (KeyDef, String) -> Unit,
    val onLongPress: (KeyDef) -> Unit,
    val onSwipeUp: (KeyDef) -> Unit,
    val onSpaceCursorDrag: (Int) -> Unit,
    val onSpaceLongPress: () -> Unit,
    /**
     * The finger that started a hold-to-talk session has been lifted. [cancelled] is true when it
     * was slid far enough up to throw the recording away.
     */
    val onSpaceRelease: (cancelled: Boolean) -> Unit,
    /** The finger crossed (or came back from) the slide-up-to-cancel line while holding. */
    val onSpaceCancelChanged: (armed: Boolean) -> Unit,
    /** Holding backspace and sliding up lights up the "clear everything" option. */
    val onClearAllArmedChanged: (armed: Boolean) -> Unit,
    /** The finger was lifted while that option was lit. */
    val onClearAll: () -> Unit,
)

/**
 * Draws one keyboard page and owns all of its gestures: tap, repeat, long press, swipe up and
 * (on the space bar) horizontal cursor dragging.
 *
 * The long press menu is drawn inside this canvas rather than in a popup window, because an
 * IME window cannot reliably own extra windows.
 */
@Composable
fun KeyCanvas(
    rows: List<List<KeyDef>>,
    keyHeight: Dp,
    cornerRadius: Dp,
    keyBackground: KeyBackground,
    callbacks: KeyCallbacks,
    modifier: Modifier = Modifier,
    activeKeyCode: KeyCode? = null,
    labelScale: Float = 1f,
) {
    var popup by remember { mutableStateOf<AlternatePopup?>(null) }
    var canvasBounds by remember { mutableStateOf(Rect.Zero) }

    Box(modifier = modifier.onGloballyPositioned { canvasBounds = it.boundsInWindow() }) {
        CompositionLocalProvider(LocalKeyLabelScale provides labelScale) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(KEY_GAP),
        ) {
            for (row in rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (key in row) {
                        if (key.code == KeyCode.None) {
                            Spacer(Modifier.weight(key.weight))
                            continue
                        }
                        KeyButton(
                            key = key,
                            height = keyHeight,
                            cornerRadius = cornerRadius,
                            keyBackground = keyBackground,
                            active = key.code == activeKeyCode,
                            // The gap is half inside each key, which is what lets two rows with a
                            // different number of keys still line up column by column.
                            modifier = Modifier
                                .weight(key.weight)
                                .padding(horizontal = KEY_GAP / 2),
                            callbacks = callbacks,
                            onLongPress = { bounds ->
                                if (key.alternates.isEmpty()) {
                                    callbacks.onLongPress(key)
                                } else {
                                    popup = AlternatePopup(
                                        key = key,
                                        anchor = bounds.translate(
                                            -canvasBounds.left,
                                            -canvasBounds.top,
                                        ),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }

        popup?.let { current ->
            AlternateMenu(
                popup = current,
                canvasWidth = canvasBounds.width,
                onPick = { alternate ->
                    callbacks.onAlternate(current.key, alternate)
                    popup = null
                },
            )
        }
        }
    }
}

private data class AlternatePopup(val key: KeyDef, val anchor: Rect)

@Composable
private fun AlternateMenu(
    popup: AlternatePopup,
    canvasWidth: Float,
    onPick: (String) -> Unit,
) {
    val itemSize = 46.dp
    val menuWidth = (itemSize.value + 4f) * popup.key.alternates.size + 12f
    val rawX = popup.anchor.center.x - menuWidth / 2f
    val maxX = (canvasWidth - menuWidth).coerceAtLeast(0f)
    val offsetX = rawX.coerceIn(0f, maxX).dp
    val above = popup.anchor.top > 110f
    val offsetY = if (above) (popup.anchor.top - 56f).dp else (popup.anchor.bottom + 6f).dp

    Row(
        modifier = Modifier
            .padding(start = offsetX, top = offsetY)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (alternate in popup.key.alternates) {
            Box(
                modifier = Modifier
                    .size(itemSize)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .pointerInput(alternate) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            var released = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                if (!change.pressed) {
                                    released = true
                                    break
                                }
                            }
                            if (released) onPick(alternate)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = alternate,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RowScope.KeyButton(
    key: KeyDef,
    height: Dp,
    cornerRadius: Dp,
    keyBackground: KeyBackground,
    active: Boolean,
    modifier: Modifier,
    callbacks: KeyCallbacks,
    onLongPress: (Rect) -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val repeatScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 20.dp.toPx() }
    val cursorStep = with(density) { 24.dp.toPx() }
    val cancelDistance = with(density) { SPACE_CANCEL_DISTANCE.toPx() }
    val clearDistance = with(density) { CLEAR_ALL_DISTANCE.toPx() }

    KeyFace(
        key = key,
        height = height,
        cornerRadius = cornerRadius,
        keyBackground = keyBackground,
        active = active,
        pressed = pressed,
        modifier = modifier
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .pointerInput(key.code, key.output, key.alternates, key.swipeUp, keyBackground) {
                keyGesture(
                    key = key,
                    swipeThreshold = swipeThreshold,
                    cursorStep = cursorStep,
                    repeatScope = repeatScope,
                    onPressedChange = { pressed = it },
                    repeat = { callbacks.onKey(key) },
                    tap = { callbacks.onKey(key) },
                    longPress = {
                        if (key.code == KeyCode.Space) {
                            callbacks.onSpaceLongPress()
                        } else {
                            onLongPress(bounds)
                        }
                    },
                    swipeUp = { callbacks.onSwipeUp(key) },
                    cancelDistance = cancelDistance,
                    spaceRelease = { cancelled -> callbacks.onSpaceRelease(cancelled) },
                    spaceCancelChanged = { armed -> callbacks.onSpaceCancelChanged(armed) },
                    clearAllArmedChanged = { armed -> callbacks.onClearAllArmedChanged(armed) },
                    clearAll = { callbacks.onClearAll() },
                    clearDistance = clearDistance,
                    cursorDrag = { steps ->
                        if (key.code == KeyCode.Space) callbacks.onSpaceCursorDrag(steps)
                    },
                )
            },
    ) { contentColor -> KeyContent(key = key, contentColor = contentColor) }
}

/**
 * One key's paint: container, outline, press animation and content colour. Shared by the
 * canvas, the scrollable symbol bar and the settings previews so all three can never drift
 * apart.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun KeyFace(
    key: KeyDef,
    height: Dp,
    cornerRadius: Dp,
    keyBackground: KeyBackground,
    active: Boolean,
    pressed: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (contentColor: Color) -> Unit,
) {
    // Keyboard feedback has to feel instant, so the press animation uses the fast spatial
    // spec from the Material 3 Expressive motion scheme rather than a default spring.
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "key-scale",
    )
    val corner by animateDpAsState(
        targetValue = if (pressed) cornerRadius + 6.dp else cornerRadius,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "key-corner",
    )

    val isAccent = active || key.style == KeyStyle.Accent
    val container = when {
        isAccent -> MaterialTheme.colorScheme.primary
        key.style == KeyStyle.Space -> MaterialTheme.colorScheme.surfaceContainerHigh
        keyBackground == KeyBackground.Filled -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> Color.Transparent
    }
    val pressedContainer = when {
        key.style == KeyStyle.Accent -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when {
        isAccent -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val background = if (pressed) pressedContainer else container
    val outline = keyBackground == KeyBackground.Outlined && !isAccent &&
        key.style != KeyStyle.Space

    Box(
        modifier = modifier
            .height(height)
            .scale(scale)
            .clip(RoundedCornerShape(corner))
            .background(background)
            .then(
                if (outline) {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(corner),
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        content(contentColor)
    }
}

/**
 * A single key drawn outside the canvas, used as one cell of the scrollable symbol bar.
 * Taps scale the key exactly like the main canvas does, so the two feel like one keyboard.
 */
@Composable
fun KeyTile(
    key: KeyDef,
    height: Dp,
    cornerRadius: Dp,
    keyBackground: KeyBackground,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    labelScale: Float = 1f,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    CompositionLocalProvider(LocalKeyLabelScale provides labelScale) {
    KeyFace(
        key = key,
        height = height,
        cornerRadius = cornerRadius,
        keyBackground = keyBackground,
        active = false,
        pressed = pressed,
        modifier = modifier.clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
        ),
    ) { contentColor -> KeyContent(key = key, contentColor = contentColor) }
    }
}

/**
 * Non-interactive copy of the real key rows. The settings panels draw this so that "键盘高度"
 * and the other appearance sliders show their effect while the finger is still on the slider.
 */
@Composable
fun KeyPreviewRow(
    rows: List<List<KeyDef>>,
    keyHeight: Dp,
    cornerRadius: Dp,
    keyBackground: KeyBackground,
    modifier: Modifier = Modifier,
    labelScale: Float = 1f,
) {
    CompositionLocalProvider(LocalKeyLabelScale provides labelScale) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KEY_GAP),
    ) {
        for (row in rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (key in row) {
                    if (key.code == KeyCode.None) {
                        Spacer(Modifier.weight(key.weight))
                        continue
                    }
                    KeyFace(
                        key = key,
                        height = keyHeight,
                        cornerRadius = cornerRadius,
                        keyBackground = keyBackground,
                        active = key.style == KeyStyle.Accent,
                        pressed = false,
                        modifier = Modifier
                            .weight(key.weight)
                            .padding(horizontal = KEY_GAP / 2),
                    ) { contentColor -> KeyContent(key = key, contentColor = contentColor) }
                }
            }
        }
    }
    }
}

/**
 * One gesture loop for every key. Compose's canned detectors cannot express "repeat while
 * held, but also swipe up, but also drag horizontally on the space bar", and mixing two
 * detectors on one modifier makes taps unreliable, so the loop is explicit.
 */
private suspend fun PointerInputScope.keyGesture(
    key: KeyDef,
    swipeThreshold: Float,
    cursorStep: Float,
    repeatScope: kotlinx.coroutines.CoroutineScope,
    onPressedChange: (Boolean) -> Unit,
    repeat: () -> Unit,
    tap: () -> Unit,
    longPress: () -> Unit,
    swipeUp: () -> Unit,
    /** How far the finger has to travel up before the hold-to-talk recording is thrown away. */
    cancelDistance: Float,
    /** How far up backspace has to be dragged before "clear everything" lights up. */
    clearDistance: Float,
    spaceRelease: (cancelled: Boolean) -> Unit,
    spaceCancelChanged: (Boolean) -> Unit,
    clearAllArmedChanged: (Boolean) -> Unit,
    clearAll: () -> Unit,
    cursorDrag: (Int) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        onPressedChange(true)
        val startedAt = System.currentTimeMillis()
        // Anything else already claimed the gesture, so the release must not also tap.
        var claimed = false
        var released = false
        // Set when this gesture opened a hold-to-talk session, so lifting the finger closes it.
        var holdToTalk = false
        var cancelArmed = false
        // Backspace: sliding up cancels the repeat and lights the clear-everything option.
        var clearArmed = false
        var cursorAccumulator = 0f

        val repeater: Job? = if (key.repeatable) {
            claimed = true
            repeatScope.launch {
                repeat()
                delay(REPEAT_START_MS)
                var held = REPEAT_START_MS
                while (isActive) {
                    repeat()
                    // Holding backspace used to escalate into a word delete after a second, which
                    // read as "it jumped and then ate half the line". Now it just gets faster, so
                    // the caret never moves more than one character per tick.
                    val interval = if (held < REPEAT_ACCELERATE_AFTER_MS) {
                        REPEAT_INTERVAL_MS
                    } else {
                        REPEAT_FAST_INTERVAL_MS
                    }
                    delay(interval)
                    held += interval
                }
            }
        } else {
            null
        }

        // 长按必须由**计时器**触发，不能靠"下一个指针事件来了再看时间"：手指停住不动时系统
        // 根本不给 move 事件，阈值要等到下一次事件才被检查到（常常已经是抬手那一下）——这就是
        // "长按空格触发语音的时间不稳定"。计时器到点触发；抬手或别的动作先取消。可重复键
        // （退格）在按下那一刻就 claimed 了，计时器空转一次，什么都不做。
        val longPressTimer: Job = repeatScope.launch {
            delay(LONG_PRESS_MS)
            if (!claimed) {
                claimed = true
                longPress()
                if (key.code == KeyCode.Space) holdToTalk = true
            }
        }

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                change.consume()
                released = true
                break
            }
            val dy = change.position.y - down.position.y
            val dx = change.position.x - down.position.x

            if (!claimed && key.swipeUp.isNotEmpty() && dy < -swipeThreshold) {
                claimed = true
                swipeUp()
            }
            if (!claimed && key.code == KeyCode.Space && abs(dx) > cursorStep) {
                val steps = ((dx - cursorAccumulator) / cursorStep).toInt()
                if (steps != 0) {
                    cursorDrag(steps)
                    cursorAccumulator += steps * cursorStep
                }
            }
            if (!claimed && System.currentTimeMillis() - startedAt > LONG_PRESS_MS) {
                claimed = true
                longPress()
                holdToTalk = key.code == KeyCode.Space
            }
            if (holdToTalk) {
                val armed = slideUpCancels(
                    dy = dy,
                    threshold = cancelDistance,
                    currentlyArmed = cancelArmed,
                )
                if (armed != cancelArmed) {
                    cancelArmed = armed
                    spaceCancelChanged(armed)
                }
            }
            if (repeater != null && key.code == KeyCode.Backspace) {
                val armed = slideUpCancels(
                    dy = dy,
                    threshold = clearDistance,
                    currentlyArmed = clearArmed,
                )
                if (armed != clearArmed) {
                    clearArmed = armed
                    // Stop deleting while the option is lit: releasing will clear instead.
                    if (armed) repeater.cancel()
                    clearAllArmedChanged(armed)
                }
            }
            change.consume()
        }

        repeater?.cancel()
        // 手势结束了，计时器不能再自己触发一次长按（抬手那一刻的竞态就是"时而触发、时而不触发"）。
        longPressTimer.cancel()
        onPressedChange(false)
        if (clearArmed) clearAllArmedChanged(false)
        if (released && clearArmed) clearAll()
        // A cancelled gesture (the system took the pointer) keeps recording on purpose: the
        // strip above the keys still shows that we are listening, and the mic key stops it.
        if (released && holdToTalk) spaceRelease(cancelArmed)
        if (released && !claimed) tap()
    }
}

@Composable
private fun KeyContent(key: KeyDef, contentColor: Color) {
    when (key.code) {
        KeyCode.Backspace -> KeyGlyph(CloudriftIcons.Backspace, "删除", contentColor)
        KeyCode.Shift -> KeyGlyph(CloudriftIcons.Shift, "大写", contentColor)
        KeyCode.HideKeyboard -> KeyGlyph(CloudriftIcons.KeyboardHide, "收起键盘", contentColor)
        KeyCode.Enter -> if (key.display.isEmpty() || key.display == "换行") {
            KeyGlyph(CloudriftIcons.Return, "换行", contentColor)
        } else {
            KeyLabel(key.display, contentColor, MaterialTheme.typography.labelLarge)
        }

        else -> BadgedLabel(key, contentColor)
    }
}

@Composable
private fun KeyGlyph(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color) {
    val scale = LocalKeyLabelScale.current
    Icon(
        imageVector = icon,
        contentDescription = label,
        tint = tint,
        modifier = Modifier.size(22.dp * scale),
    )
}

/**
 * Main label centred, optional badge tucked into the top start corner and an optional caption
 * under the label. The badge exists so a nine key pad can lead with its letters and still show
 * the digit; the caption is what makes the number page read as a phone dial pad.
 */
@Composable
private fun BadgedLabel(key: KeyDef, contentColor: Color) {
    val scale = LocalKeyLabelScale.current
    Box(modifier = Modifier.fillMaxWidth()) {
        if (key.badge.isNotEmpty()) {
            Text(
                text = key.badge,
                color = contentColor.copy(alpha = 0.55f),
                fontSize = 11.sp * scale,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 9.dp, top = 6.dp),
            )
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            KeyLabel(
                text = key.display,
                color = contentColor,
                // A dial pad draws a big digit with a small letter row under it; the line
                // height is pinned so the pair still fits the shortest auto-sized key.
                style = if (key.caption.isNotEmpty() || key.largeLabel) {
                    MaterialTheme.typography.titleLarge.copy(lineHeight = 24.sp)
                } else {
                    MaterialTheme.typography.titleMedium
                },
            )
            if (key.caption.isNotEmpty()) {
                Text(
                    text = key.caption,
                    color = contentColor.copy(alpha = 0.75f),
                    fontSize = 10.sp * scale,
                    lineHeight = 12.sp * scale,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun KeyLabel(
    text: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val scale = LocalKeyLabelScale.current
    Text(
        text = text,
        color = color,
        style = style.copy(
            fontSize = style.fontSize * scale,
            lineHeight = style.lineHeight * scale,
        ),
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier,
    )
}

/** Gap between keys and between key rows; shared with the symbol bar so the two line up. */
internal val KEY_GAP = 6.dp

/** Label size multiplier, so one slider can resize the text on every key. */
private val LocalKeyLabelScale = compositionLocalOf { 1f }

/**
 * Slide-up-to-cancel, with hysteresis: arming takes a full [threshold] of upward travel, but the
 * finger only has to come back to [RELEASE_FRACTION] of it to disarm. Without the gap a hand
 * resting near the line flips the hint back and forth.
 */
internal fun slideUpCancels(dy: Float, threshold: Float, currentlyArmed: Boolean): Boolean {
    val bar = threshold * if (currentlyArmed) CANCEL_RELEASE_FRACTION else 1f
    return dy <= -bar
}

/** How far up the finger has to travel on the space bar to throw the recording away. */
private val SPACE_CANCEL_DISTANCE = 56.dp

/** How far up the finger has to travel on backspace for "clear everything" to light up. */
private val CLEAR_ALL_DISTANCE = 44.dp

private const val CANCEL_RELEASE_FRACTION = 0.55f

private const val REPEAT_START_MS = 400L
private const val REPEAT_INTERVAL_MS = 55L
/** After a while the repeat speeds up, which is the smooth version of "keep going". */
private const val REPEAT_ACCELERATE_AFTER_MS = 1200L
private const val REPEAT_FAST_INTERVAL_MS = 28L
private const val LONG_PRESS_MS = 380L

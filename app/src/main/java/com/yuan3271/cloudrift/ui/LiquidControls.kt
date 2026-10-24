package com.yuan3271.cloudrift.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.yuan3271.cloudrift.theme.glassSurfaceTint
import com.yuan3271.cloudrift.theme.isLiquidGlassTheme
import com.yuan3271.cloudrift.theme.liquidGlassPanel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A draggable Liquid Glass segmented control. Unlike the Material equivalent, the selection is a
 * physical lens that follows the finger and springs into place; the shared glass tray refracts the
 * moving highlight underneath it.
 */
@Composable
fun CloudriftSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isLiquidGlassTheme()) {
        LiquidSegmentedControl(
            options = options,
            selectedIndex = selectedIndex,
            onSelected = onSelected,
            modifier = modifier,
        )
    } else {
        SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = index == selectedIndex,
                    onClick = { onSelected(index) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) {
                    Text(label)
                }
            }
        }
    }
}

@Composable
fun LiquidSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(options.isNotEmpty())
    val scope = rememberCoroutineScope()
    val position = remember { Animatable(selectedIndex.toFloat()) }
    var pressing by remember { mutableStateOf(false) }
    val pressure by animateFloatAsState(
        targetValue = if (pressing) 1f else 0f,
        animationSpec = spring(0.62f, 360f),
        label = "liquid-segmented-pressure",
    )
    LaunchedEffect(selectedIndex) {
        position.animateTo(selectedIndex.toFloat(), spring(0.72f, 420f))
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .liquidGlassPanel(
                shape = RoundedCornerShape(50),
                tint = glassSurfaceTint(),
                interactive = pressure,
            ),
    ) {
        val density = LocalDensity.current
        val itemWidth = maxWidth / options.size
        val itemWidthPx = with(density) { itemWidth.toPx() }
        val itemWidthInt = with(density) { itemWidth.roundToPx() }
        Box(
            modifier = Modifier
                .padding(3.dp)
                .width(itemWidth)
                .fillMaxHeight()
                .offset {
                    IntOffset((position.value.coerceIn(0f, options.lastIndex.toFloat()) *
                        itemWidthInt).roundToInt(), 0)
                }
                .liquidGlassPanel(
                    shape = RoundedCornerShape(50),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.62f),
                    interactive = pressure,
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(options, itemWidthPx) {
                    inspectLiquidDrag(
                        horizontalOnly = true,
                        tapHandledByChild = true,
                        onDown = {
                            pressing = true
                            scope.launch {
                                position.animateTo(
                                    liquidSelectionPosition(it.x, itemWidthPx, options.lastIndex),
                                    spring(0.7f, 420f),
                                )
                            }
                        },
                        onMove = {
                            scope.launch {
                                position.snapTo(
                                    liquidSelectionPosition(it.x, itemWidthPx, options.lastIndex),
                                )
                            }
                        },
                        onFinish = {
                            pressing = false
                            val target = position.value.roundToInt().coerceIn(options.indices)
                            onSelected(target)
                            scope.launch {
                                position.animateTo(target.toFloat(), spring(0.72f, 420f))
                            }
                        },
                    )
                },
        ) {
            options.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelected(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (index == selectedIndex) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (index == selectedIndex) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

/** A glass toggle whose thumb can be dragged and stretches under the finger. */
@Composable
fun LiquidSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(if (checked) 1f else 0f) }
    var dragging by remember { mutableStateOf(false) }
    val pressure by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = spring(0.58f, 360f),
        label = "liquid-switch-pressure",
    )
    LaunchedEffect(checked) {
        progress.animateTo(if (checked) 1f else 0f, spring(0.72f, 420f))
    }
    BoxWithConstraints(
        modifier = modifier
            .size(width = 58.dp, height = 32.dp)
            .semantics {
                role = Role.Switch
                stateDescription = if (checked) "已开启" else "已关闭"
                onClick {
                    if (enabled) onCheckedChange(!checked)
                    enabled
                }
            }
            .liquidGlassPanel(
                shape = RoundedCornerShape(50),
                tint = if (checked) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.64f)
                } else {
                    glassSurfaceTint()
                },
                interactive = pressure,
            )
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                inspectLiquidDrag(
                    horizontalOnly = true,
                    onDown = { dragging = true },
                    onMove = {
                        scope.launch {
                            progress.snapTo((it.x / size.width).coerceIn(0f, 1f))
                        }
                    },
                    onFinish = {
                        dragging = false
                        val next = progress.value >= 0.5f
                        onCheckedChange(next)
                        scope.launch {
                            progress.animateTo(if (next) 1f else 0f, spring(0.72f, 420f))
                        }
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val travel = with(LocalDensity.current) { (maxWidth - 28.dp).toPx() }
        Box(
            modifier = Modifier
                .padding(3.dp)
                .size(26.dp)
                .graphicsLayer {
                    translationX = travel * progress.value
                    val stretch = 1f + 0.10f * pressure
                    scaleX = stretch
                    scaleY = 2f - stretch
                }
                .liquidGlassPanel(
                    shape = CircleShape,
                    tint = Color.White.copy(alpha = 0.78f),
                    interactive = pressure,
                ),
        )
    }
}

/** A continuous glass slider with an immediate tap position and a direct-manipulation thumb. */
@Composable
fun LiquidSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
) {
    var pressing by remember { mutableStateOf(false) }
    var localValue by remember(value) { mutableFloatStateOf(value) }
    val pressure by animateFloatAsState(
        targetValue = if (pressing) 1f else 0f,
        animationSpec = spring(0.58f, 360f),
        label = "liquid-slider-pressure",
    )
    LaunchedEffect(value) { localValue = value }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .pointerInput(valueRange) {
                inspectLiquidDrag(
                    horizontalOnly = true,
                    onDown = {
                        pressing = true
                        localValue = liquidSliderValue(it.x, size.width, valueRange)
                        onValueChange(localValue)
                    },
                    onMove = {
                        localValue = liquidSliderValue(it.x, size.width, valueRange)
                        onValueChange(localValue)
                    },
                    onFinish = { pressing = false },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val fraction = ((localValue - valueRange.start) /
            (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        val density = LocalDensity.current
        val thumbWidthPx = with(density) { 34.dp.roundToPx() }
        val trackWidthPx = with(density) { maxWidth.toPx() }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .liquidGlassPanel(
                    shape = RoundedCornerShape(50),
                    tint = glassSurfaceTint(),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(7.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                    RoundedCornerShape(50),
                ),
        )
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        ((trackWidthPx - thumbWidthPx) * fraction).roundToInt()
                            .coerceIn(0, (trackWidthPx - thumbWidthPx).toInt().coerceAtLeast(0)),
                        0,
                    )
                }
                .size(width = 34.dp, height = 24.dp)
                .graphicsLayer {
                    val stretch = 1f + 0.08f * pressure
                    scaleX = stretch
                    scaleY = 1f / stretch
                }
                .liquidGlassPanel(
                    shape = RoundedCornerShape(50),
                    tint = Color.White.copy(alpha = 0.82f),
                    interactive = pressure,
                ),
        )
    }
}

/**
 * A floating glass option used by the first-run picker. The whole card stretches slightly while
 * pressed, while its icon tray acts as a second optical layer.
 */
@Composable
fun LiquidChoiceCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val pressure by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(0.58f, 360f),
        label = "liquid-choice-pressure",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val stretch = 1f + 0.025f * pressure
                scaleX = stretch
                scaleY = 2f - stretch
            }
            .liquidGlassPanel(
                shape = RoundedCornerShape(24.dp),
                tint = glassSurfaceTint(),
                strong = true,
                interactive = pressure,
            )
            .semantics {
                role = Role.Button
                onClick {
                    onClick()
                    true
                }
            }
            .pointerInput(Unit) {
                inspectLiquidDrag(
                    onDown = { pressed = true },
                    onMove = {},
                    onFinish = {
                        pressed = false
                        onClick()
                    },
                )
            }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .liquidGlassPanel(
                    shape = RoundedCornerShape(15.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                    interactive = pressure,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Circular glass action used by the keyboard toolbar. The icon tray itself refracts; it is not an
 * IconButton with a translucent background.
 */
@Composable
fun LiquidIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    tint: Color = if (active) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.68f)
    } else {
        glassSurfaceTint()
    },
    contentColor: Color = if (active) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    },
    content: @Composable (Color) -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val pressure by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(0.58f, 360f),
        label = "liquid-icon-pressure",
    )
    Box(
        modifier = modifier
            .size(38.dp)
            .graphicsLayer {
                val scale = 1f + pressure * 0.045f
                scaleX = scale
                scaleY = 2f - scale
            }
            .liquidGlassPanel(
                shape = CircleShape,
                tint = tint,
                interactive = pressure,
            )
            .pointerInput(Unit) {
                inspectLiquidDrag(
                    onDown = { pressed = true },
                    onMove = {},
                    onFinish = {
                        pressed = false
                        onClick()
                    },
                )
            }
            .semantics {
                role = Role.Button
                onClick {
                    onClick()
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        content(contentColor)
    }
}

/** A capsule action that is a floating liquid object rather than a filled Material button. */
@Composable
fun LiquidPillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val pressure by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(0.58f, 360f),
        label = "liquid-pill-pressure",
    )
    Row(
        modifier = modifier
            .height(42.dp)
            .graphicsLayer {
                alpha = if (enabled) 1f else 0.44f
                val stretch = 1f + pressure * 0.035f
                scaleX = stretch
                scaleY = 2f - stretch
            }
            .liquidGlassPanel(
                shape = RoundedCornerShape(50),
                tint = if (primary) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.68f)
                } else {
                    glassSurfaceTint()
                },
                interactive = pressure,
            )
            .then(
                if (enabled) {
                    Modifier.pointerInput(Unit) {
                        inspectLiquidDrag(
                            onDown = { pressed = true },
                            onMove = {},
                            onFinish = {
                                pressed = false
                                onClick()
                            },
                        )
                    }
                } else {
                    Modifier
                },
            )
            .semantics {
                role = Role.Button
                if (enabled) {
                    onClick {
                        onClick()
                        true
                    }
                }
            }
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides if (primary) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        ) {
            content()
        }
    }
}

private suspend fun PointerInputScope.inspectLiquidDrag(
    horizontalOnly: Boolean = false,
    tapHandledByChild: Boolean = false,
    onDown: (Offset) -> Unit,
    onMove: (Offset) -> Unit,
    onFinish: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        var active = !horizontalOnly
        if (active) {
            down.consume()
            onDown(down.position)
        }
        var released = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                if (active) change.consume()
                released = true
                break
            }
            if (!active) {
                val dx = change.position.x - down.position.x
                val dy = change.position.y - down.position.y
                if (abs(dx) > slop && abs(dx) > abs(dy)) {
                    active = true
                    change.consume()
                    onDown(down.position)
                    onMove(change.position)
                } else if (abs(dy) > slop) {
                    return@awaitEachGesture
                }
            } else {
                change.consume()
                onMove(change.position)
            }
        }
        if (released && (active || !tapHandledByChild)) {
            if (horizontalOnly && !active) {
                onDown(down.position)
                onMove(down.position)
            }
            onFinish()
        }
    }
}

internal fun liquidSelectionPosition(x: Float, itemWidthPx: Float, maxIndex: Int): Float =
    (x / itemWidthPx - 0.5f).coerceIn(0f, maxIndex.toFloat())

internal fun liquidSliderValue(
    x: Float,
    width: Int,
    range: ClosedFloatingPointRange<Float>,
): Float {
    val fraction = if (width <= 0) 0f else (x / width).coerceIn(0f, 1f)
    return range.start + (range.endInclusive - range.start) * fraction
}

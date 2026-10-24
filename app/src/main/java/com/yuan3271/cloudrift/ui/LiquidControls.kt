package com.yuan3271.cloudrift.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Shape
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yuan3271.cloudrift.theme.InternalSurfaceTone
import com.yuan3271.cloudrift.theme.bestContentColor
import com.yuan3271.cloudrift.theme.liquidControlGlass
import com.yuan3271.cloudrift.theme.liquidTrackGlass
import com.yuan3271.cloudrift.theme.internalSurface
import com.yuan3271.cloudrift.theme.isLiquidGlassTheme
import com.yuan3271.cloudrift.ui.icons.CloudriftIcons
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A draggable segmented control in the Liquid Glass theme. The tray is a flat luminance step and
 * the selection is an opaque theme-colour fill; neither layer performs its own blur or refraction.
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
            .liquidControlGlass(
                shape = RoundedCornerShape(50),
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
                .liquidControlGlass(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                    active = true,
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
                            MaterialTheme.colorScheme.primary.bestContentColor()
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

/** A toggle whose track changes to a solid semantic fill instead of adding another material. */
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
            .liquidControlGlass(
                shape = RoundedCornerShape(50),
                active = checked,
                color = if (checked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    null
                },
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
                .liquidControlGlass(
                    shape = CircleShape,
                    active = checked,
                    color = if (checked) {
                        MaterialTheme.colorScheme.primary.bestContentColor()
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                ),
        )
    }
}

/** A continuous slider with flat track surfaces and a solid primary thumb. */
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
                .liquidTrackGlass(
                    shape = RoundedCornerShape(50),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(7.dp)
                .background(
                    MaterialTheme.colorScheme.primary,
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
                .liquidControlGlass(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                    active = true,
                ),
        )
    }
}

/**
 * A floating option used by the first-run picker. It is a flat card; only the keyboard shell is
 * allowed to be real glass.
 */
@Composable
fun LiquidChoiceCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val pressure by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(0.58f, 360f),
        label = "liquid-choice-pressure",
    )
    val foreground = if (selected) {
        MaterialTheme.colorScheme.primary.bestContentColor()
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val stretch = 1f + 0.025f * pressure
                scaleX = stretch
                scaleY = 2f - stretch
            }
            .liquidControlGlass(
                shape = RoundedCornerShape(24.dp),
                active = selected,
                color = if (selected) MaterialTheme.colorScheme.primary else null,
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
                .internalSurface(
                    shape = RoundedCornerShape(15.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary.bestContentColor().copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary.bestContentColor()
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(22.dp),
            )
        }
        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = foreground,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.bestContentColor().copy(alpha = 0.76f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (selected) {
            Icon(
                imageVector = CloudriftIcons.Check,
                contentDescription = "已选择",
                tint = MaterialTheme.colorScheme.primary.bestContentColor(),
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

/**
 * Circular toolbar action. A neutral action is a subtle plane fill; active and pressed states are
 * solid theme-colour fills.
 */
@Composable
fun LiquidIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    width: Dp = 38.dp,
    height: Dp = 38.dp,
    shape: Shape = CircleShape,
    tint: Color? = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        null
    },
    contentColor: Color = if (active) {
        MaterialTheme.colorScheme.primary.bestContentColor()
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
    val fill = when {
        pressed -> (tint ?: MaterialTheme.colorScheme.primary).copy(alpha = 1f)
        active -> tint?.copy(alpha = 1f) ?: MaterialTheme.colorScheme.primary
        else -> null
    }
    val foreground = if (pressed || active) {
        (fill ?: MaterialTheme.colorScheme.primary).bestContentColor()
    } else {
        contentColor
    }
    Box(
        modifier = modifier
            .size(width = width, height = height)
            .graphicsLayer {
                val scale = 1f + pressure * 0.045f
                scaleX = scale
                scaleY = 2f - scale
            }
            .liquidControlGlass(
                shape = shape,
                active = active || pressed,
                color = fill,
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
        content(foreground)
    }
}

/** A capsule action with a flat neutral fill or a solid primary fill. */
@Composable
fun LiquidPillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    containerColor: Color? = null,
    contentColor: Color? = null,
    content: @Composable () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val pressure by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(0.58f, 360f),
        label = "liquid-pill-pressure",
    )
    val activeColor = containerColor ?: MaterialTheme.colorScheme.primary
    val fill = when {
        pressed -> androidx.compose.ui.graphics.lerp(activeColor, Color.White, 0.08f)
        primary || containerColor != null -> activeColor
        else -> null
    }
    val foreground = when {
        primary || containerColor != null ->
            contentColor ?: activeColor.bestContentColor()
        else -> contentColor ?: MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = modifier
            .height(42.dp)
            .graphicsLayer {
                alpha = if (enabled) 1f else 0.44f
                val stretch = 1f + pressure * 0.035f
                scaleX = stretch
                scaleY = 2f - stretch
            }
            .liquidControlGlass(
                shape = RoundedCornerShape(50),
                active = primary || pressed || containerColor != null,
                color = fill,
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
            LocalContentColor provides foreground,
        ) {
            content()
        }
    }
}

/** Compact flat text action used by Liquid Glass chrome instead of Material TextButton. */
@Composable
fun LiquidTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .then(
                if (enabled) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .liquidControlGlass(
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSurface,
        ) {
            content()
        }
    }
}

/** Shared action wrappers keep Material widgets out of the Liquid Glass branch. */
@Composable
fun CloudriftTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    if (isLiquidGlassTheme()) {
        LiquidTextButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            content = content,
        )
    } else {
        TextButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
    }
}

@Composable
fun CloudriftFilledButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color? = null,
    contentColor: Color? = null,
    content: @Composable () -> Unit,
) {
    if (isLiquidGlassTheme()) {
        LiquidPillButton(
            onClick = onClick,
            modifier = modifier,
            primary = containerColor == null,
            enabled = enabled,
            containerColor = containerColor,
            contentColor = contentColor,
            content = content,
        )
    } else {
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = containerColor ?: MaterialTheme.colorScheme.primary,
                contentColor = contentColor ?: MaterialTheme.colorScheme.onPrimary,
            ),
            content = { content() },
        )
    }
}

@Composable
fun CloudriftOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (isLiquidGlassTheme()) {
        LiquidPillButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            content = content,
        )
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            content = { content() },
        )
    }
}

@Composable
fun CloudriftIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    content: @Composable (Color) -> Unit,
) {
    if (isLiquidGlassTheme()) {
        LiquidIconButton(
            onClick = onClick,
            modifier = modifier,
            active = active,
            content = content,
        )
    } else {
        IconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
            CompositionLocalProvider(
                LocalContentColor provides if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) {
                content(LocalContentColor.current)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun CloudriftLoadingIndicator(modifier: Modifier = Modifier) {
    if (!isLiquidGlassTheme()) {
        LoadingIndicator(modifier = modifier)
        return
    }
    val transition = rememberInfiniteTransition(label = "liquid-loading")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "liquid-loading-phase",
    )
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val wave = 0.45f + 0.55f * kotlin.math.sin(phase + index * 2.1f)
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .graphicsLayer {
                        alpha = wave.coerceIn(0.25f, 1f)
                        scaleX = 0.75f + wave * 0.25f
                        scaleY = 0.75f + wave * 0.25f
                    }
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
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

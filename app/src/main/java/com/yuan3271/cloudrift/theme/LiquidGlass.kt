package com.yuan3271.cloudrift.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.yuan3271.cloudrift.data.AppTheme
import com.yuan3271.cloudrift.data.LiquidGlassKeyDensity
import kotlin.math.roundToInt
import kotlin.math.pow

internal val LocalAppTheme = staticCompositionLocalOf {
    AppTheme.Material3Expressive
}

internal val LocalLiquidGlassBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }
internal val LocalLiquidGlassKeyDensity = staticCompositionLocalOf {
    LiquidGlassKeyDensity.Medium
}
internal val LocalLiquidGlassIntensity = staticCompositionLocalOf { 0.35f }
internal val LocalKeyboardOuterRadius = staticCompositionLocalOf { 26.dp }
internal val LocalLiquidGlassBottomLightAlpha = staticCompositionLocalOf {
    LiquidGlassConfig.BASE_LIGHT_ALPHA
}
internal val LocalLiquidGlassLightMode = staticCompositionLocalOf { false }

/**
 * Every tunable that belongs to the Liquid Glass layer system lives here.
 *
 * Persistent glass is used by panels and every button; content such as text fields and labels
 * stays on a quieter plane so the glass still communicates hierarchy.
 */
internal object LiquidGlassConfig {
    const val SOLID_STATE_ALPHA = 1f

    const val INTERNAL_FILL_LOW = 0.06f
    const val INTERNAL_FILL_MEDIUM = 0.08f
    const val INTERNAL_FILL_HIGH = 0.10f
    const val INTERNAL_FILL_RAISED = 0.12f

    /** Higher concentration means more transparent; these are matrix-key fills only. */
    const val KEY_FILL_LOW = 0.12f
    const val KEY_FILL_MEDIUM = 0.09f
    const val KEY_FILL_HIGH = 0.06f
    const val KEY_PRESS_SCALE = 0.04f
    const val KEY_PRESS_BRIGHTEN = 0.08f
    const val KEY_RADIUS_LIFT_DP = 2f
    const val KEY_MAX_RADIUS_FRACTION = 0.48f
    const val TOOLBAR_CONTROL_HEIGHT_DP = 44f
    const val LANGUAGE_THEME_CONTROL_WIDTH_DP = 62f
    const val HIDE_CONTROL_WIDTH_DP = 56f
    const val HIDE_CONTROL_RADIUS_INSET_DP = 4f
    const val BASE_LIGHT_ALPHA = 0.06f
    const val BASE_LIGHT_RADIUS_FRACTION = 1.2f
    val CENTER_LIGHT_STOPS = floatArrayOf(0.24f, 0.5f, 0.76f)
    const val EDGE_LIGHT_RADIUS_DP = 96f
    const val EDGE_LIGHT_ALPHA = 0.32f
    const val EDGE_LIGHT_WIDTH_DP = 1.1f

    // Mirrors AndroidLiquidGlass's LiquidBottomTabs/GlassPlayground composition.
    const val OUTER_BLUR_DP = 8f
    const val REFRACTION_HEIGHT_DP = 24f
    const val REFRACTION_AMOUNT_DP = 24f
    const val HIGHLIGHT_WIDTH_DP = 1.05f
    const val HIGHLIGHT_BLUR_DP = 0.65f
    const val SHADOW_RADIUS_DP = 24f
    const val SHADOW_OFFSET_Y_DP = 4f
    const val INNER_SHADOW_RADIUS_DP = 12f
    const val FROSTED_FILL_ALPHA = 0.86f
    const val CLEAR_FILL_ALPHA = 0f
    const val FROSTED_BLUR_DP = 14f
    const val CLEAR_BLUR_DP = 0f
    const val FROSTED_HIGHLIGHT_ALPHA = 0.92f
    const val CLEAR_HIGHLIGHT_ALPHA = 0f
    const val FROSTED_EDGE_ALPHA = 0.62f
    const val CLEAR_EDGE_ALPHA = 0f
    const val FROSTED_SHADOW_ALPHA = 0.14f
    const val CLEAR_SHADOW_ALPHA = 0f
    const val DEFAULT_INTENSITY = 0.35f

    val SURFACE_COLOR_LIGHT = Color(0xFFFAFAFA)
    val SURFACE_COLOR_DARK = Color(0xFF121212)
}

internal data class OuterGlassMaterial(
    val blurDp: Float,
    val fillAlpha: Float,
    val highlightAlpha: Float,
    val darkEdgeAlpha: Float,
    val shadowAlpha: Float,
    val refractionHeightDp: Float,
    val refractionAmountDp: Float,
)

internal fun outerGlassMaterial(intensityPercent: Int, dark: Boolean): OuterGlassMaterial {
    val strength = (intensityPercent / 100f).coerceIn(0f, 1f)
    fun lerp(from: Float, to: Float): Float = from + (to - from) * strength
    return OuterGlassMaterial(
        blurDp = lerp(LiquidGlassConfig.FROSTED_BLUR_DP, LiquidGlassConfig.CLEAR_BLUR_DP),
        fillAlpha = lerp(LiquidGlassConfig.FROSTED_FILL_ALPHA, LiquidGlassConfig.CLEAR_FILL_ALPHA),
        highlightAlpha = (LiquidGlassConfig.FROSTED_HIGHLIGHT_ALPHA +
            (LiquidGlassConfig.CLEAR_HIGHLIGHT_ALPHA -
                LiquidGlassConfig.FROSTED_HIGHLIGHT_ALPHA) *
            strength.pow(1.7f)) * if (dark) 0.92f else 1f,
        darkEdgeAlpha = lerp(
            LiquidGlassConfig.FROSTED_EDGE_ALPHA,
            LiquidGlassConfig.CLEAR_EDGE_ALPHA,
        ),
        shadowAlpha = lerp(
            LiquidGlassConfig.FROSTED_SHADOW_ALPHA,
            LiquidGlassConfig.CLEAR_SHADOW_ALPHA,
        ),
        refractionHeightDp = lerp(
            LiquidGlassConfig.REFRACTION_HEIGHT_DP,
            LiquidGlassConfig.CLEAR_BLUR_DP,
        ),
        refractionAmountDp = lerp(
            LiquidGlassConfig.REFRACTION_AMOUNT_DP,
            LiquidGlassConfig.CLEAR_BLUR_DP,
        ),
    )
}

internal fun bottomLightAlpha(dark: Boolean): Float =
    if (dark) LiquidGlassConfig.BASE_LIGHT_ALPHA else LiquidGlassConfig.BASE_LIGHT_ALPHA * 0.5f

internal fun concentricInnerRadius(outerRadius: Dp, spacing: Dp): Dp =
    (outerRadius.value - spacing.value).coerceAtLeast(0f).dp

internal enum class InternalSurfaceTone {
    Low,
    Medium,
    High,
    Raised,
}

/** Whether the current composition is using the liquid-glass visual language. */
@Composable
fun isLiquidGlassTheme(): Boolean = LocalAppTheme.current == AppTheme.LiquidGlass

@Composable
internal fun internalSurfaceColor(tone: InternalSurfaceTone = InternalSurfaceTone.Low): Color {
    val alpha = when (tone) {
        InternalSurfaceTone.Low -> LiquidGlassConfig.INTERNAL_FILL_LOW
        InternalSurfaceTone.Medium -> LiquidGlassConfig.INTERNAL_FILL_MEDIUM
        InternalSurfaceTone.High -> LiquidGlassConfig.INTERNAL_FILL_HIGH
        InternalSurfaceTone.Raised -> LiquidGlassConfig.INTERNAL_FILL_RAISED
    }
    // Light mode uses a dark plane fill and dark mode a light one. Adding a white wash in light
    // mode was what made the old panels look grey and washed out.
    return (if (isLikelyDark()) Color.White else Color.Black).copy(alpha = alpha)
}

internal fun glassKeyFillAlpha(density: LiquidGlassKeyDensity): Float = when (density) {
    LiquidGlassKeyDensity.Low -> LiquidGlassConfig.KEY_FILL_LOW
    LiquidGlassKeyDensity.Medium -> LiquidGlassConfig.KEY_FILL_MEDIUM
    LiquidGlassKeyDensity.High -> LiquidGlassConfig.KEY_FILL_HIGH
}

@Composable
internal fun glassKeySurfaceColor(): Color =
    (if (isLikelyDark()) Color.White else Color.Black).copy(
        alpha = glassKeyFillAlpha(LocalLiquidGlassKeyDensity.current),
    )

/** A flat interior surface: no blur, refraction, edge light, outline or shadow. */
@Composable
internal fun Modifier.internalSurface(
    shape: Shape,
    tone: InternalSurfaceTone = InternalSurfaceTone.Low,
    color: Color? = null,
): Modifier = background(color ?: internalSurfaceColor(tone), shape)

/** Supplies the backdrop capture layer. A null base keeps the IME window transparent. */
@Composable
internal fun LiquidGlassCanvas(
    baseColor: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    val bottomLight = MaterialTheme.colorScheme.primary
    val bottomLightAlpha = LocalLiquidGlassBottomLightAlpha.current
    CompositionLocalProvider(LocalLiquidGlassBackdrop provides backdrop) {
        Box {
            // Do not paint a solid sheet here. An opaque canvas makes the backdrop uniform, so
            // blur/refraction have nothing to sample and the keyboard is just a grey rectangle.
            // API 31+ applies cross-window blur to the real app content; older devices fall back
            // to the translucent glass fill.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .layerBackdrop(backdrop),
            ) {
                if (baseColor != null) {
                    Canvas(Modifier.matchParentSize()) {
                        drawRect(baseColor)
                        val radius = size.maxDimension *
                            LiquidGlassConfig.BASE_LIGHT_RADIUS_FRACTION
                        val center = Offset(size.width / 2f, size.height * 0.92f)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    bottomLight.copy(alpha = bottomLightAlpha),
                                    bottomLight.copy(alpha = 0.025f),
                                    Color.Transparent,
                                ),
                                center = center,
                                radius = radius,
                            ),
                            radius = radius,
                            center = center,
                        )
                    }
                }
            }
            content()
        }
    }
}

/** Shared renderer for panels and outer surfaces. */
@Composable
private fun Modifier.liquidGlassPanel(
    shape: Shape,
    tint: Color = glassSurfaceTint(),
    strong: Boolean = false,
    interactive: Float = 0f,
): Modifier {
    if (!isLiquidGlassTheme()) return this
    val backdrop = LocalLiquidGlassBackdrop.current ?: return this
    val pressure = interactive.coerceIn(0f, 1f)
    val dark = isLikelyDark()
    val material = outerGlassMaterial(
        intensityPercent = (LocalLiquidGlassIntensity.current * 100f).roundToInt(),
        dark = dark,
    )
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(material.blurDp.dp.toPx())
            lens(
                refractionHeight = material.refractionHeightDp.dp.toPx(),
                refractionAmount = material.refractionAmountDp.dp.toPx() *
                    (0.88f + pressure * 0.12f),
                depthEffect = true,
                chromaticAberration = false,
            )
        },
        highlight = {
            Highlight.Default.copy(
                width = LiquidGlassConfig.HIGHLIGHT_WIDTH_DP.dp,
                blurRadius = LiquidGlassConfig.HIGHLIGHT_BLUR_DP.dp,
                alpha = material.highlightAlpha,
            )
        },
        shadow = if (strong) {
            {
                Shadow(
                    radius = LiquidGlassConfig.SHADOW_RADIUS_DP.dp,
                    offset = DpOffset(0.dp, LiquidGlassConfig.SHADOW_OFFSET_Y_DP.dp),
                    color = Color.Black.copy(
                        alpha = material.shadowAlpha,
                    ),
                    alpha = 1f,
                )
            }
        } else {
            null
        },
        innerShadow = {
            InnerShadow(
                radius = LiquidGlassConfig.INNER_SHADOW_RADIUS_DP.dp,
                offset = DpOffset(0.dp, 2.dp),
                color = Color.Black.copy(
                    alpha = material.darkEdgeAlpha,
                ),
                alpha = 1f,
            )
        },
        onDrawSurface = {
            drawRect(tint)
        },
    )
}

/** Temporary second glass layer for transient interactive elements such as the cursor rail. */
@Composable
internal fun Modifier.liquidInteractiveGlass(
    shape: Shape,
    tint: Color = glassSurfaceTint(),
): Modifier {
    if (!isLiquidGlassTheme()) return this
    return liquidGlassPanel(
        shape = shape,
        tint = tint,
        strong = false,
        interactive = 1f,
    )
}

/**
 * Thin controls deliberately skip lens refraction: a 7–24dp rail does not have enough geometry
 * for a 24dp lens and otherwise shows tearing while dragged.
 */
@Composable
internal fun Modifier.liquidTrackGlass(
    shape: Shape,
    color: Color = internalSurfaceColor(InternalSurfaceTone.High),
): Modifier {
    if (!isLiquidGlassTheme()) return this
    val backdrop = LocalLiquidGlassBackdrop.current ?: return this
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(4.dp.toPx())
        },
        highlight = { Highlight.Ambient.copy(alpha = 0.2f) },
        onDrawSurface = { drawRect(color) },
    )
}

/**
 * Lightweight glass used by every interactive button. It follows the reference's LiquidButton
 * recipe while keeping the content legible with a low-alpha neutral or semantic surface fill.
 */
@Composable
internal fun Modifier.liquidControlGlass(
    shape: Shape,
    color: Color? = null,
    active: Boolean = false,
): Modifier {
    if (!isLiquidGlassTheme()) return this
    val backdrop = LocalLiquidGlassBackdrop.current ?: return this
    val surfaceFill = color ?: internalSurfaceColor(InternalSurfaceTone.Medium)
    var edgePosition by remember { mutableStateOf<Offset?>(null) }
    val base = if (active) {
        drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(2.dp.toPx())
                lens(
                    refractionHeight = 12.dp.toPx(),
                    refractionAmount = 24.dp.toPx(),
                    depthEffect = true,
                    chromaticAberration = active,
                )
            },
            highlight = {
                if (active) {
                    Highlight.Default.copy(alpha = 0.44f)
                } else {
                    Highlight.Ambient.copy(alpha = 0.26f)
                }
            },
            innerShadow = {
                InnerShadow(
                    radius = 4.dp,
                    color = Color.Black.copy(alpha = 0.12f),
                    alpha = 0.55f,
                )
            },
            onDrawSurface = {
                drawRect(surfaceFill)
            },
        )
    } else {
        internalSurface(shape = shape, color = surfaceFill)
    }
    return base
        .glassStaticEdge(shape)
        .trackFingerLight { edgePosition = it }
        .glassEdgeLight(shape = shape, position = edgePosition)
}

@Composable
private fun Modifier.ambientGlassLight(enabled: Boolean): Modifier {
    if (!enabled) return this
    val alpha = LocalLiquidGlassBottomLightAlpha.current
    return drawWithContent {
        LiquidGlassConfig.CENTER_LIGHT_STOPS.forEach { fraction ->
            val center = androidx.compose.ui.geometry.Offset(
                size.width / 2f,
                size.height * fraction,
            )
            val radius = size.maxDimension * LiquidGlassConfig.BASE_LIGHT_RADIUS_FRACTION
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = alpha),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
        drawContent()
    }
}

private fun Modifier.trackFingerLight(onPosition: (Offset?) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            onPosition(down.position)
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    onPosition(change.position)
                }
            } finally {
                onPosition(null)
            }
        }
    }

@Composable
private fun Modifier.glassEdgeLight(shape: Shape, position: Offset?): Modifier {
    if (position == null) return this
    return drawWithContent {
        drawContent()
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = when (outline) {
            is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
            is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
            is Outline.Generic -> outline.path
        }
        val radius = LiquidGlassConfig.EDGE_LIGHT_RADIUS_DP.dp.toPx()
        drawPath(
            path = path,
            brush = Brush.radialGradient(
                0f to Color.White.copy(alpha = LiquidGlassConfig.EDGE_LIGHT_ALPHA),
                0.42f to Color.White.copy(alpha = LiquidGlassConfig.EDGE_LIGHT_ALPHA * 0.28f),
                1f to Color.Transparent,
                center = position,
                radius = radius,
            ),
            style = Stroke(
                width = LiquidGlassConfig.EDGE_LIGHT_WIDTH_DP.dp.toPx(),
                cap = StrokeCap.Round,
            ),
        )
    }
}

@Composable
private fun Modifier.glassStaticEdge(shape: Shape): Modifier {
    val lightMode = LocalLiquidGlassLightMode.current
    return drawWithContent {
        drawContent()
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = when (outline) {
            is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
            is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
            is Outline.Generic -> outline.path
        }
        val end = Offset(size.width, size.height)
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Black.copy(alpha = if (lightMode) 0.07f else 0.20f),
                    Color.Transparent,
                ),
                start = end,
                end = Offset.Zero,
            ),
            style = Stroke(0.7.dp.toPx()),
        )
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (lightMode) 0.64f else 0.28f),
                    Color.Transparent,
                ),
                start = Offset.Zero,
                end = end,
            ),
            style = Stroke(0.85.dp.toPx()),
        )
    }
}

/** A full glass surface used by the keyboard shell, cards and dialogs. */
@Composable
internal fun KeyboardGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape,
    centerLight: Boolean = false,
    respondToFinger: Boolean = false,
    edgeLight: Boolean = false,
    content: @Composable () -> Unit,
) {
    var edgePosition by remember { mutableStateOf<Offset?>(null) }
    val base = modifier.liquidGlassPanel(
        shape = shape,
        tint = glassSurfaceTint(strong = true),
        strong = true,
    )
    val tracked = if (respondToFinger) {
        base.trackFingerLight { edgePosition = it }
    } else {
        base
    }
    val lighting = tracked
        .ambientGlassLight(centerLight)
        .glassEdgeLight(
            shape = shape,
            position = if (edgeLight) edgePosition else null,
        )
    Box(
        modifier = lighting,
    ) {
        content()
    }
}

/** Outer keyboard shell: one glass layer in Liquid Glass, or the regular Material surface. */
@Composable
internal fun KeyboardOuterSurface(
    modifier: Modifier = Modifier,
    shape: Shape,
    materialColor: Color,
    content: @Composable () -> Unit,
) {
    if (isLiquidGlassTheme()) {
        KeyboardGlassSurface(
            modifier = modifier,
            shape = shape,
            centerLight = true,
            respondToFinger = true,
            edgeLight = true,
            content = content,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = materialColor,
            content = content,
        )
    }
}

/** An interior keyboard surface that can never become a second glass layer. */
@Composable
internal fun KeyboardContentSurface(
    modifier: Modifier = Modifier,
    shape: Shape,
    materialColor: Color,
    tone: InternalSurfaceTone = InternalSurfaceTone.High,
    content: @Composable () -> Unit,
) {
    if (isLiquidGlassTheme()) {
        Box(
            modifier = modifier.internalSurface(shape = shape, tone = tone),
            content = { content() },
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = materialColor,
            content = content,
        )
    }
}

@Composable
internal fun glassSurfaceTint(strong: Boolean = false): Color {
    val dark = isLikelyDark()
    val material = outerGlassMaterial(
        intensityPercent = (LocalLiquidGlassIntensity.current * 100f).roundToInt(),
        dark = dark,
    )
    return if (dark) {
        LiquidGlassConfig.SURFACE_COLOR_DARK.copy(alpha = material.fillAlpha)
    } else {
        LiquidGlassConfig.SURFACE_COLOR_LIGHT.copy(alpha = material.fillAlpha)
    }
}

@Composable
internal fun isLikelyDark(): Boolean =
    MaterialTheme.colorScheme.onSurface.luminance() > 0.5f

private fun Color.luminance(): Float =
    red * 0.2126f + green * 0.7152f + blue * 0.0722f

/**
 * Material compatibility values for legacy Material controls inside the Liquid Glass branch.
 * The actual glass surfaces are drawn by [CloudriftSurface]; these are transparent fallbacks so
 * no opaque Material card accidentally sits on top of the optical canvas.
 */
internal fun glassColorScheme(base: ColorScheme, dark: Boolean): ColorScheme {
    val lightSurface = Color.White
    val darkSurface = Color.Black
    return base.copy(
        surface = Color.Transparent,
        surfaceVariant = (if (dark) lightSurface else darkSurface).copy(alpha = 0.08f),
        surfaceContainerLowest = lightSurface.copy(alpha = if (dark) 0.04f else 0.42f),
        surfaceContainerLow = (if (dark) lightSurface else darkSurface).copy(alpha = 0.06f),
        surfaceContainer = (if (dark) lightSurface else darkSurface).copy(alpha = 0.09f),
        surfaceContainerHigh = (if (dark) lightSurface else darkSurface).copy(alpha = 0.12f),
        surfaceContainerHighest = (if (dark) lightSurface else darkSurface).copy(alpha = 0.16f),
        outline = if (dark) Color.White.copy(alpha = 0.42f) else Color.Black.copy(alpha = 0.30f),
        outlineVariant = if (dark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.12f),
        onSurface = if (dark) Color(0xFFF7F7F8) else Color(0xFF111113),
        onSurfaceVariant = if (dark) Color(0xFFC8C8CC) else Color(0xFF55555B),
    )
}

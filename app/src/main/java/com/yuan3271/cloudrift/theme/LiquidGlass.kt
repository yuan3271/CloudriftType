package com.yuan3271.cloudrift.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.yuan3271.cloudrift.data.AppTheme

internal val LocalAppTheme = staticCompositionLocalOf {
    AppTheme.Material3Expressive
}

internal val LocalLiquidGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Whether the current composition is using the liquid-glass visual language. */
@Composable
fun isLiquidGlassTheme(): Boolean = LocalAppTheme.current == AppTheme.LiquidGlass

/**
 * A cloudscape sampled by the open-source Backdrop effect. An IME cannot reliably sample an app
 * behind its own window, so the glass gets a deterministic, high-contrast scene of its own. This
 * keeps the refraction legible while avoiding a flat, fake translucent rectangle.
 */
@Composable
internal fun rememberCloudriftGlassBackdrop(
    dark: Boolean,
    colors: ColorScheme,
): Backdrop {
    val skyTop = if (dark) Color(0xFF07152A) else Color(0xFFE9F3FF)
    val skyBottom = if (dark) Color(0xFF2C2448) else Color(0xFFFFF0E8)
    val rift = if (dark) colors.primary.copy(alpha = 0.72f) else colors.primary.copy(alpha = 0.42f)
    val glow = if (dark) colors.tertiary.copy(alpha = 0.64f) else colors.tertiary.copy(alpha = 0.38f)
    val draw: DrawScope.() -> Unit = remember(skyTop, skyBottom, rift, glow, dark) {
        {
            drawRect(Brush.linearGradient(listOf(skyTop, skyBottom)))
            drawCircle(
                color = rift,
                radius = size.maxDimension * 0.46f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.08f, size.height * 0.22f),
            )
            drawCircle(
                color = glow,
                radius = size.minDimension * 0.42f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.92f, size.height * 0.76f),
            )
            drawCircle(
                color = Color.White.copy(alpha = if (dark) 0.10f else 0.32f),
                radius = size.minDimension * 0.22f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.58f, size.height * 0.10f),
            )
        }
    }
    return rememberCanvasBackdrop(draw)
}

/** Background behind every translucent glass surface in this composition. */
internal fun cloudriftGlassBackground(
    dark: Boolean,
    colors: ColorScheme,
): Brush = if (dark) {
    Brush.linearGradient(
        listOf(
            colors.primary.copy(alpha = 0.22f),
            Color(0xFF07111E),
            colors.tertiary.copy(alpha = 0.18f),
        ),
    )
} else {
    Brush.linearGradient(
        listOf(
            colors.primary.copy(alpha = 0.13f),
            Color(0xFFF8FBFF),
            colors.tertiary.copy(alpha = 0.13f),
        ),
    )
}

/**
 * Backdrop's refracting, chromatic lens is intentionally reserved for panels. Individual keys use
 * a cheaper translucent treatment below, keeping a full keyboard to one or two shader layers.
 */
@Composable
fun Modifier.liquidGlassPanel(
    shape: Shape,
    tint: Color = defaultGlassTint(),
    strong: Boolean = false,
): Modifier {
    if (!isLiquidGlassTheme()) return this
    val backdrop = LocalLiquidGlassBackdrop.current ?: return this
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(if (strong) 5.dp.toPx() else 2.dp.toPx())
            lens(
                refractionHeight = if (strong) 18.dp.toPx() else 10.dp.toPx(),
                refractionAmount = if (strong) 32.dp.toPx() else 18.dp.toPx(),
                depthEffect = true,
                chromaticAberration = strong,
            )
        },
        highlight = { Highlight.Plain },
        onDrawSurface = { drawRect(tint) },
    )
}

/** A Material surface with the same API in both themes, painted as glass only in Liquid Glass. */
@Composable
fun CloudriftSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.liquidGlassPanel(
            shape = shape,
            tint = if (isLiquidGlassTheme()) color.copy(alpha = 0.30f) else color,
            strong = true,
        ),
        shape = shape,
        color = if (isLiquidGlassTheme()) Color.Transparent else color,
    ) {
        Box { content() }
    }
}

@Composable
private fun defaultGlassTint(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color.White.copy(alpha = 0.24f)
    } else {
        Color(0xFF07111E).copy(alpha = 0.36f)
    }

private fun Color.luminance(): Float =
    red * 0.2126f + green * 0.7152f + blue * 0.0722f

internal fun glassColorScheme(base: ColorScheme, dark: Boolean): ColorScheme {
    val surfaceAlpha = if (dark) 0.58f else 0.48f
    val containerAlpha = if (dark) 0.42f else 0.34f
    return base.copy(
        surface = base.surface.copy(alpha = surfaceAlpha),
        surfaceVariant = base.surfaceVariant.copy(alpha = containerAlpha),
        surfaceContainerLowest = base.surfaceContainerLowest.copy(alpha = 0.22f),
        surfaceContainerLow = base.surfaceContainerLow.copy(alpha = 0.28f),
        surfaceContainer = base.surfaceContainer.copy(alpha = containerAlpha),
        surfaceContainerHigh = base.surfaceContainerHigh.copy(alpha = 0.44f),
        surfaceContainerHighest = base.surfaceContainerHighest.copy(alpha = 0.52f),
    )
}

package com.yuan3271.cloudrift.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.DpOffset
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

internal val LocalAppTheme = staticCompositionLocalOf {
    AppTheme.Material3Expressive
}

internal val LocalLiquidGlassBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** Whether the current composition is using the liquid-glass visual language. */
@Composable
fun isLiquidGlassTheme(): Boolean = LocalAppTheme.current == AppTheme.LiquidGlass

/**
 * A neutral optical canvas, sampled as one shared layer by every glass control. Keeping the
 * backdrop monochrome is deliberate: light mode is white / warm grey, dark mode is black / graphite,
 * and colour only enters through the user's accent and semantic state colours.
 */
@Composable
internal fun LiquidGlassCanvas(dark: Boolean, content: @Composable BoxScope.() -> Unit) {
    val backdrop = rememberLayerBackdrop()
    CompositionLocalProvider(LocalLiquidGlassBackdrop provides backdrop) {
        Box {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .layerBackdrop(backdrop),
            ) {
                Canvas(Modifier.matchParentSize()) {
                    val baseTop = if (dark) Color.Black else Color(0xFFFFFFFF)
                    val baseBottom = if (dark) Color(0xFF090A0C) else Color(0xFFF1F1F3)
                    drawRect(Brush.verticalGradient(listOf(baseTop, baseBottom)))

                    // These grey fields are not decoration; they give the lens a changing
                    // luminance to refract while preserving the black / white base requested.
                    drawCircle(
                        color = if (dark) Color(0xFF202124) else Color(0xFFE2E2E6),
                        radius = size.maxDimension * 0.50f,
                        center = Offset(size.width * 0.06f, size.height * 0.16f),
                    )
                    drawCircle(
                        color = if (dark) Color(0xFF121316) else Color(0xFFF8F8FA),
                        radius = size.minDimension * 0.55f,
                        center = Offset(size.width * 0.92f, size.height * 0.78f),
                    )
                    drawRect(
                        color = if (dark) Color.Black.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.20f),
                    )
                }
            }
            content()
        }
    }
}

/**
 * Mirrors the optical layering used by AndroidLiquidGlass' catalog controls: vibrancy,
 * backdrop blur, edge refraction, ambient rim light and a soft inner shadow. `interactive`
 * intensifies the lens while a control is pressed without creating a second visual system.
 */
@Composable
fun Modifier.liquidGlassPanel(
    shape: Shape,
    tint: Color = glassSurfaceTint(),
    strong: Boolean = false,
    interactive: Float = 0f,
): Modifier {
    if (!isLiquidGlassTheme()) return this
    val backdrop = LocalLiquidGlassBackdrop.current ?: return this
    val pressure = interactive.coerceIn(0f, 1f)
    val dark = isLikelyDark()
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur((if (strong) 8.dp else 4.dp).toPx())
            lens(
                refractionHeight = (if (strong) 18.dp else 10.dp).toPx(),
                refractionAmount = (if (strong) 26.dp else 14.dp).toPx() * (0.72f + pressure * 0.28f),
                depthEffect = true,
                chromaticAberration = strong || pressure > 0.4f,
            )
        },
        highlight = {
            Highlight.Ambient.copy(alpha = if (strong) 0.92f else 0.72f)
        },
        shadow = if (strong) {
            {
                Shadow(
                    radius = 18.dp,
                    offset = DpOffset(0.dp, 7.dp),
                    color = Color.Black.copy(alpha = if (dark) 0.34f else 0.12f),
                    alpha = 0.85f,
                )
            }
        } else {
            null
        },
        innerShadow = {
            InnerShadow(
                radius = (if (strong) 10.dp else 6.dp) * (0.55f + pressure * 0.45f),
                alpha = 0.72f + pressure * 0.20f,
            )
        },
        onDrawSurface = {
            drawRect(tint)
            if (pressure > 0f) {
                drawRect(Color.White.copy(alpha = 0.08f * pressure))
            }
        },
    )
}

/**
 * Surface primitive shared by keyboard and settings. In Liquid Glass it is a real refractive
 * surface; in Material 3 Expressive it remains the original opaque Material surface.
 */
@Composable
fun CloudriftSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    interactive: Float = 0f,
    content: @Composable () -> Unit,
) {
    if (isLiquidGlassTheme()) {
        Box(
            modifier = modifier.liquidGlassPanel(
                shape = shape,
                tint = glassSurfaceTint(strong = true),
                strong = true,
                interactive = interactive,
            ),
        ) {
            content()
        }
    } else {
        androidx.compose.material3.Surface(
            modifier = modifier,
            shape = shape,
            color = color,
        ) {
            Box { content() }
        }
    }
}

@Composable
fun glassSurfaceTint(strong: Boolean = false): Color {
    val dark = isLikelyDark()
    return if (dark) {
        Color.Black.copy(alpha = if (strong) 0.42f else 0.28f)
    } else {
        Color.White.copy(alpha = if (strong) 0.46f else 0.30f)
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

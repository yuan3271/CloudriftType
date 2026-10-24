package com.yuan3271.cloudrift.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.pow

/**
 * "Cloud rift" palette: a cool high-altitude blue for the sky, a warm peach accent for
 * the light that leaks through the gap. Both schemes are deliberately low chroma so the
 * keyboard does not fight with whatever app is behind it.
 */
private val RiftBlue40 = Color(0xFF3F5B8F)
private val RiftBlue80 = Color(0xFFAFC6FF)
private val RiftBlue90 = Color(0xFFDBE1FF)
private val RiftBlue30 = Color(0xFF264777)
private val RiftBlue10 = Color(0xFF001B3D)
private val RiftBlue20 = Color(0xFF0A305F)

private val RiftSlate40 = Color(0xFF565E71)
private val RiftSlate80 = Color(0xFFBEC6DC)
private val RiftSlate90 = Color(0xFFDAE2F9)
private val RiftSlate30 = Color(0xFF3F4759)
private val RiftSlate10 = Color(0xFF131B2C)
private val RiftSlate20 = Color(0xFF273042)

private val RiftPeach40 = Color(0xFF8C4A62)
private val RiftPeach80 = Color(0xFFFFB1C8)
private val RiftPeach90 = Color(0xFFFFD9E2)
private val RiftPeach30 = Color(0xFF70334A)
private val RiftPeach10 = Color(0xFF3B071D)
private val RiftPeach20 = Color(0xFF561F35)

private val RiftTeal40 = Color(0xFF006A64)
private val RiftTeal80 = Color(0xFF6FDAD2)
private val RiftTeal90 = Color(0xFF9FF2E9)
private val RiftTeal30 = Color(0xFF00504B)
private val RiftTeal10 = Color(0xFF00201E)
private val RiftTeal20 = Color(0xFF003733)

val CloudriftLightScheme = lightColorScheme(
    primary = RiftBlue40,
    onPrimary = Color.White,
    primaryContainer = RiftBlue90,
    onPrimaryContainer = RiftBlue10,
    inversePrimary = RiftBlue80,
    secondary = RiftSlate40,
    onSecondary = Color.White,
    secondaryContainer = RiftSlate90,
    onSecondaryContainer = RiftSlate10,
    tertiary = RiftPeach40,
    onTertiary = Color.White,
    tertiaryContainer = RiftPeach90,
    onTertiaryContainer = RiftPeach10,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF191C23),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF43474E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F3FA),
    surfaceContainer = Color(0xFFEEEDF4),
    surfaceContainerHigh = Color(0xFFE8E7EF),
    surfaceContainerHighest = Color(0xFFE2E2E9),
    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC3C6CF),
    inverseSurface = Color(0xFF2E3037),
    inverseOnSurface = Color(0xFFF0F0F7),
    scrim = Color(0xFF000000),
)

val CloudriftDarkScheme = darkColorScheme(
    primary = RiftBlue80,
    onPrimary = RiftBlue20,
    primaryContainer = RiftBlue30,
    onPrimaryContainer = RiftBlue90,
    inversePrimary = RiftBlue40,
    secondary = RiftSlate80,
    onSecondary = RiftSlate20,
    secondaryContainer = RiftSlate30,
    onSecondaryContainer = RiftSlate90,
    tertiary = RiftPeach80,
    onTertiary = RiftPeach20,
    tertiaryContainer = RiftPeach30,
    onTertiaryContainer = RiftPeach90,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C6CF),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF43474E),
    inverseSurface = Color(0xFFE2E2E9),
    inverseOnSurface = Color(0xFF2E3037),
    scrim = Color(0xFF000000),
)

/** Accent used by the recording state, kept outside the scheme so it never shifts. */
val VoiceActiveRed = Color(0xFFE4573D)

/**
 * Builds a scheme around a single accent hue.
 *
 * This is a lightweight stand-in for Material's HCT tonal palette: the surfaces, outlines and
 * error colours stay on the neutral rift ramp so the keyboard keeps its identity, and only the
 * accent roles are regenerated. Lightness values follow Material's tone numbers (40 / 90 / 80 /
 * 30) so contrast lands where components expect it.
 */
fun accentColorScheme(hue: Float, saturation: Float, dark: Boolean): ColorScheme {
    val sat = saturation.coerceIn(0.08f, 0.9f)
    val accent = { tone: Float -> hsvToColor(hue, sat, tone) }
    val muted = { tone: Float -> hsvToColor(hue, sat * 0.28f, tone) }
    val support = { tone: Float -> hsvToColor((hue + 55f) % 360f, sat * 0.72f, tone) }

    return if (dark) {
        CloudriftDarkScheme.copy(
            primary = accent(0.82f),
            onPrimary = accent(0.18f),
            primaryContainer = accent(0.34f),
            onPrimaryContainer = accent(0.92f),
            inversePrimary = accent(0.44f),
            secondary = muted(0.80f),
            onSecondary = muted(0.20f),
            secondaryContainer = muted(0.32f),
            onSecondaryContainer = muted(0.90f),
            tertiary = support(0.82f),
            onTertiary = support(0.20f),
            tertiaryContainer = support(0.34f),
            onTertiaryContainer = support(0.92f),
        )
    } else {
        CloudriftLightScheme.copy(
            primary = accent(0.40f),
            onPrimary = Color.White,
            primaryContainer = accent(0.90f),
            onPrimaryContainer = accent(0.12f),
            inversePrimary = accent(0.80f),
            secondary = muted(0.42f),
            onSecondary = Color.White,
            secondaryContainer = muted(0.90f),
            onSecondaryContainer = muted(0.14f),
            tertiary = support(0.42f),
            onTertiary = Color.White,
            tertiaryContainer = support(0.90f),
            onTertiaryContainer = support(0.14f),
        )
    }
}

/** Boosts accent chroma without changing the sampled hue or surface roles. */
internal fun ColorScheme.withSaturatedAccents(factor: Float): ColorScheme = copy(
    primary = primary.saturated(factor),
    primaryContainer = primaryContainer.saturated(factor * 0.72f),
    inversePrimary = inversePrimary.saturated(factor),
    secondary = secondary.saturated(factor),
    secondaryContainer = secondaryContainer.saturated(factor * 0.62f),
    tertiary = tertiary.saturated(factor),
    tertiaryContainer = tertiaryContainer.saturated(factor * 0.68f),
)

/** Keeps dark surfaces while reusing the accent roles extracted in light mode. */
internal fun ColorScheme.withAccentRolesFrom(source: ColorScheme): ColorScheme = copy(
    primary = source.primary,
    onPrimary = source.onPrimary,
    primaryContainer = source.primaryContainer,
    onPrimaryContainer = source.onPrimaryContainer,
    inversePrimary = source.inversePrimary,
    secondary = source.secondary,
    onSecondary = source.onSecondary,
    secondaryContainer = source.secondaryContainer,
    onSecondaryContainer = source.onSecondaryContainer,
    tertiary = source.tertiary,
    onTertiary = source.onTertiary,
    tertiaryContainer = source.tertiaryContainer,
    onTertiaryContainer = source.onTertiaryContainer,
)

internal fun Color.saturated(factor: Float, minimumSaturation: Float = 0f): Color {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val delta = max - min
    if (max <= 0f || delta <= 0f) return this
    val hue = when (max) {
        red -> 60f * (((green - blue) / delta) % 6f)
        green -> 60f * (((blue - red) / delta) + 2f)
        else -> 60f * (((red - green) / delta) + 4f)
    }
    val saturation = (delta / max * factor).coerceIn(minimumSaturation, 1f)
    return hsvToColor(hue, saturation, max).copy(alpha = alpha)
}

/**
 * Pure black/white contrast choice for solid accents and selection fills. The 0.179 threshold is
 * where relative luminance gives both candidates the same WCAG contrast ratio.
 */
internal fun Color.bestContentColor(): Color =
    if (relativeLuminance() > 0.179f) Color.Black else Color.White

private fun Color.relativeLuminance(): Float {
    fun channel(value: Float): Float =
        if (value <= 0.04045f) value / 12.92f else ((value + 0.055f) / 1.055f).pow(2.4f)
    return channel(red) * 0.2126f + channel(green) * 0.7152f + channel(blue) * 0.0722f
}

/** HSV is the right space for a hue/saturation picker; Value doubles as the tone. */
internal fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val c = value * saturation
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = value - c
    val (r, g, b) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(red = r + m, green = g + m, blue = b + m)
}

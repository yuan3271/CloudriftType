package com.yuan3271.cloudrift.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.yuan3271.cloudrift.data.ThemeSource
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

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
    // 中性面用冷灰，不掺紫：原来的浅色面板 R≈G<B 带一点品红，整页看起来发灰紫，
    // 和蓝色的强调色放在一起显得脏。现在统一成 R<G<B 的冷灰（蓝味来自色相，不是品红）。
    surface = Color(0xFFF9FAFD),
    onSurface = Color(0xFF191C23),
    surfaceVariant = Color(0xFFDFE2EA),
    onSurfaceVariant = Color(0xFF43474E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F5FA),
    surfaceContainer = Color(0xFFEDEFF5),
    surfaceContainerHigh = Color(0xFFE7E9F0),
    surfaceContainerHighest = Color(0xFFE1E3EA),
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
    surface = Color(0xFF101319),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF41464E),
    onSurfaceVariant = Color(0xFFC3C6CF),
    surfaceContainerLowest = Color(0xFF0B0E13),
    surfaceContainerLow = Color(0xFF181B21),
    surfaceContainer = Color(0xFF1C1F25),
    surfaceContainerHigh = Color(0xFF262A31),
    surfaceContainerHighest = Color(0xFF31353C),
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF43474E),
    inverseSurface = Color(0xFFE2E2E9),
    inverseOnSurface = Color(0xFF2E3037),
    scrim = Color(0xFF000000),
)

/** Accent used by the recording state, kept outside the scheme so it never shifts. */
val VoiceActiveRed = Color(0xFFE4573D)

/**
 * 当前模式该用的那一套 accent。
 *
 * 浅色要 tone 40 的强调色和 tone 90 的容器，深色要 tone 80 / 30：两套不能混用。
 * "跟壁纸"用系统给的那一套；系统没有动态取色（Android 12 以下）时退回云隙蓝。
 */
internal fun accentSourceFor(
    dark: Boolean,
    themeSource: ThemeSource,
    accentHue: Int,
    accentSaturation: Int,
    dynamic: ColorScheme?,
): ColorScheme = when (themeSource) {
    ThemeSource.Dynamic -> dynamic ?: (if (dark) CloudriftDarkScheme else CloudriftLightScheme)
    ThemeSource.Cloudrift -> if (dark) CloudriftDarkScheme else CloudriftLightScheme
    ThemeSource.Custom -> accentColorScheme(
        hue = accentHue.toFloat(),
        saturation = accentSaturation / 100f,
        dark = dark,
    )
}

/**
 * 用一个色相生成整条强调色。
 *
 * 明度用的是 OKLab 的 L，不是 HSV 的 V。以前拿 V 当 Material 的 tone：蓝在同样的档位下
 * 总是发暗（V=1 的蓝本身就不亮），黄则发亮，同一个滑杆位置在不同色相上看起来完全不是一回事。
 * OKLab 的 L 是感知均匀的，同一个 L 下所有色相"看起来一样明"；chroma 超出 sRGB 时按比例
 * 收回来，色相保持不变，也不会靠裁通道硬塞（裁通道会让颜色发灰、偏色）。
 */
fun accentColorScheme(hue: Float, saturation: Float, dark: Boolean): ColorScheme {
    val sat = saturation.coerceIn(0.08f, 0.9f)
    /** 0.015 几乎是灰，0.16 已经很艳；再高就只能裁通道了。 */
    val chroma = 0.015f + sat * 0.145f
    val supportHue = (hue + 52f) % 360f
    fun accent(lightness: Float, scale: Float = 1f) =
        oklchColor(hue, chroma * scale, lightness)
    fun muted(lightness: Float, scale: Float = 1f) =
        oklchColor(hue, chroma * 0.30f * scale, lightness)
    fun support(lightness: Float, scale: Float = 1f) =
        oklchColor(supportHue, chroma * 0.85f * scale, lightness)

    return if (dark) {
        val primary = accent(0.80f, 0.90f)
        val secondary = muted(0.80f)
        val tertiary = support(0.80f)
        CloudriftDarkScheme.copy(
            primary = primary,
            // 前景色由底色算出来，而不是写死白色/黑色：黄、绿这类亮色相上白字会糊。
            onPrimary = primary.bestContentColor(),
            primaryContainer = accent(0.38f, 0.80f),
            onPrimaryContainer = accent(0.93f, 0.45f),
            inversePrimary = accent(0.52f, 0.95f),
            secondary = secondary,
            onSecondary = secondary.bestContentColor(),
            secondaryContainer = muted(0.36f),
            onSecondaryContainer = muted(0.92f),
            tertiary = tertiary,
            onTertiary = tertiary.bestContentColor(),
            tertiaryContainer = support(0.36f),
            onTertiaryContainer = support(0.92f),
        )
    } else {
        val primary = accent(0.50f)
        val secondary = muted(0.50f)
        val tertiary = support(0.50f)
        CloudriftLightScheme.copy(
            primary = primary,
            onPrimary = primary.bestContentColor(),
            primaryContainer = accent(0.93f, 0.45f),
            onPrimaryContainer = accent(0.22f, 0.80f),
            inversePrimary = accent(0.80f, 0.90f),
            secondary = secondary,
            onSecondary = secondary.bestContentColor(),
            secondaryContainer = muted(0.92f),
            onSecondaryContainer = muted(0.25f),
            tertiary = tertiary,
            onTertiary = tertiary.bestContentColor(),
            tertiaryContainer = support(0.92f),
            onTertiaryContainer = support(0.25f),
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
// ---- OKLab / OKLCH ---------------------------------------------------------------

private const val GAMUT_STEPS = 24
private const val GAMUT_EPSILON = 0.0005f

/**
 * OKLCH → sRGB。
 *
 * chroma 超出 sRGB 色域时把它按比例收回来（而不是裁 RGB 通道）：裁通道会改变色相、让颜色
 * 发灰，同一个滑杆位置换几个色相就得到几种「脏」得不一样的颜色。收回 chroma 只损失一点鲜艳度，
 * 明度和色相都不变。
 */
internal fun oklchColor(hueDegrees: Float, chroma: Float, lightness: Float): Color {
    val l = lightness.coerceIn(0f, 1f)
    val radians = Math.toRadians(hueDegrees.toDouble().mod(360.0))
    var c = chroma.coerceAtLeast(0f)
    repeat(GAMUT_STEPS) {
        val mapped = oklabToSrgb(l, (c * cos(radians)).toFloat(), (c * sin(radians)).toFloat())
        if (mapped != null) return mapped
        c *= 0.88f
    }
    return oklabToSrgb(l, 0f, 0f) ?: Color(l, l, l, 1f)
}

/** 超出 sRGB 色域时返回 null，交给调用方把 chroma 收小。 */
private fun oklabToSrgb(l: Float, a: Float, b: Float): Color? {
    val lCubeRoot = l + 0.3963377774f * a + 0.2158037573f * b
    val mCubeRoot = l - 0.1055613458f * a - 0.0638541728f * b
    val sCubeRoot = l - 0.0894841775f * a - 1.2914855480f * b
    val lc = lCubeRoot * lCubeRoot * lCubeRoot
    val mc = mCubeRoot * mCubeRoot * mCubeRoot
    val sc = sCubeRoot * sCubeRoot * sCubeRoot
    val r = 4.0767416621f * lc - 3.3077115913f * mc + 0.2309699292f * sc
    val g = -1.2684380046f * lc + 2.6097574011f * mc - 0.3413193965f * sc
    val bl = -0.0041960863f * lc - 0.7034186147f * mc + 1.7076147010f * sc
    if (r < -GAMUT_EPSILON || r > 1f + GAMUT_EPSILON ||
        g < -GAMUT_EPSILON || g > 1f + GAMUT_EPSILON ||
        bl < -GAMUT_EPSILON || bl > 1f + GAMUT_EPSILON
    ) {
        return null
    }
    return Color(
        red = linearToSrgb(r.coerceIn(0f, 1f)),
        green = linearToSrgb(g.coerceIn(0f, 1f)),
        blue = linearToSrgb(bl.coerceIn(0f, 1f)),
        alpha = 1f,
    )
}

private fun linearToSrgb(value: Float): Float =
    if (value <= 0.0031308f) 12.92f * value else 1.055f * value.pow(1f / 2.4f) - 0.055f

private fun srgbToLinear(value: Float): Float =
    if (value <= 0.04045f) value / 12.92f else ((value + 0.055f) / 1.055f).pow(2.4f)

/** 颜色在 OKLab 里的明度；测试用它核对：同一档位下不同色相应当看起来一样明。 */
internal fun Color.oklabLightness(): Float {
    val r = srgbToLinear(red)
    val g = srgbToLinear(green)
    val b = srgbToLinear(blue)
    val l = 0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b
    val m = 0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b
    val s = 0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b
    return 0.2104542553f * cbrt(l) + 0.7936177850f * cbrt(m) - 0.0040720468f * cbrt(s)
}

package com.yuan3271.cloudrift.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import com.yuan3271.cloudrift.data.AppTheme
import com.yuan3271.cloudrift.data.ThemeMode
import com.yuan3271.cloudrift.data.ThemeSource

/**
 * Resolves [ThemeMode] against the system setting and hands the result to
 * [MaterialExpressiveTheme], which supplies the Material 3 Expressive motion scheme and
 * shape system on top of the colour scheme.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CloudriftTheme(
    appTheme: AppTheme = AppTheme.Material3Expressive,
    themeMode: ThemeMode = ThemeMode.System,
    themeSource: ThemeSource = ThemeSource.Dynamic,
    accentHue: Int = 222,
    accentSaturation: Int = 42,
    darkThemeOverride: Boolean? = null,
    appWindow: Boolean = false,
    content: @Composable () -> Unit,
) {
    val effectiveAppTheme = if (
        appTheme == AppTheme.LiquidGlass && !AppTheme.LIQUID_GLASS_ENABLED
    ) {
        AppTheme.Material3Expressive
    } else {
        appTheme
    }
    val dark = darkThemeOverride ?: when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    // 明暗两套各有自己的 accent 档位：浅色是 tone 40 / 90（深一点的强调色、很浅的容器），
    // 深色是 tone 80 / 30。以前这里只取深色的那一套，再套到浅色表面上——浅色页面里就出现
    // 了 ton-30 的深蓝卡片和深蓝选中态，和浅色背景撞成一片。
    val dynamic = if (themeSource == ThemeSource.Dynamic) dynamicScheme(dark) else null
    val accentSource = accentSourceFor(
        dark = dark,
        themeSource = themeSource,
        accentHue = accentHue,
        accentSaturation = accentSaturation,
        dynamic = dynamic,
    )
    val materialScheme = if (dark) {
        CloudriftDarkScheme.withAccentRolesFrom(accentSource)
    } else {
        CloudriftLightScheme.withAccentRolesFrom(accentSource)
    }
    val colorScheme = if (effectiveAppTheme == AppTheme.LiquidGlass) {
        glassColorScheme(accentSource.withSaturatedAccents(1.15f), dark)
    } else {
        materialScheme
    }
    val windowBackground = if (dark) {
        CloudriftDarkScheme.surface
    } else {
        CloudriftLightScheme.surface
    }

    MaterialExpressiveTheme(colorScheme = colorScheme) {
        CompositionLocalProvider(
            LocalAppTheme provides effectiveAppTheme,
            LocalLiquidGlassBottomLightAlpha provides bottomLightAlpha(dark),
            LocalLiquidGlassLightMode provides !dark,
        ) {
            if (effectiveAppTheme == AppTheme.LiquidGlass) {
                LiquidGlassCanvas(
                    baseColor = if (appWindow) windowBackground else null,
                ) { content() }
            } else {
                content()
            }
        }
    }
}

@Composable
private fun dynamicScheme(dark: Boolean): ColorScheme? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val context = LocalContext.current
    return if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}

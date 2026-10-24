package com.yuan3271.cloudrift.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
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
    content: @Composable () -> Unit,
) {
    val dark = darkThemeOverride ?: when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val base = if (dark) CloudriftDarkScheme else CloudriftLightScheme
    val baseColorScheme = when (themeSource) {
        ThemeSource.Dynamic -> dynamicScheme(dark) ?: base
        ThemeSource.Cloudrift -> base
        ThemeSource.Custom -> accentColorScheme(
            hue = accentHue.toFloat(),
            saturation = accentSaturation / 100f,
            dark = dark,
        )
    }
    val colorScheme = if (appTheme == AppTheme.LiquidGlass) {
        glassColorScheme(baseColorScheme, dark)
    } else {
        baseColorScheme
    }

    MaterialExpressiveTheme(colorScheme = colorScheme) {
        CompositionLocalProvider(
            LocalAppTheme provides appTheme,
            LocalLiquidGlassBackdrop provides if (appTheme == AppTheme.LiquidGlass) {
                rememberCloudriftGlassBackdrop(dark, colorScheme)
            } else {
                null
            },
        ) {
            if (appTheme == AppTheme.LiquidGlass) {
                Box(modifier = Modifier.background(cloudriftGlassBackground(dark, colorScheme))) {
                    content()
                }
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

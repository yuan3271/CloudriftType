package com.yuan3271.cloudrift.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
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
    val colorScheme = when (themeSource) {
        ThemeSource.Dynamic -> dynamicScheme(dark) ?: base
        ThemeSource.Cloudrift -> base
        ThemeSource.Custom -> accentColorScheme(
            hue = accentHue.toFloat(),
            saturation = accentSaturation / 100f,
            dark = dark,
        )
    }

    MaterialExpressiveTheme(colorScheme = colorScheme, content = content)
}

@Composable
private fun dynamicScheme(dark: Boolean): ColorScheme? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val context = LocalContext.current
    return if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}

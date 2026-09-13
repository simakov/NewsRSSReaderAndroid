package com.newsrssreader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun NewsRSSReaderTheme(content: @Composable () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val colors = if (isDark) DarkAppColors else LightAppColors
    val materialScheme = if (isDark) {
        darkColorScheme(
            background = colors.background,
            surface = colors.background,
            onBackground = colors.black,
            onSurface = colors.black,
            primary = colors.red,
        )
    } else {
        lightColorScheme(
            background = colors.backgroundWhite,
            surface = colors.white,
            onBackground = colors.black,
            onSurface = colors.black,
            primary = colors.red,
        )
    }
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(colorScheme = materialScheme, content = content)
    }
}

object AppTheme {
    val colors: AppColors
        @Composable get() = LocalAppColors.current

    // @Composable here purely for call-site symmetry with `colors` above;
    // AppType is a plain object and reads no CompositionLocal.
    val type: AppType
        @Composable get() = AppType
}

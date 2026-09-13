package com.newsrssreader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun NewsRSSReaderTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkAppColors else LightAppColors
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(content = content)
    }
}

object AppTheme {
    val colors: AppColors
        @Composable get() = LocalAppColors.current
    val type: AppType
        @Composable get() = AppType
}

package com.newsrssreader.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppColors(
    val background: Color,
    val backgroundWhite: Color,
    val black: Color,
    val blackInversed: Color,
    val white: Color,
    val gray: Color,
    val lightGrey: Color,
    val red: Color,
)

val LightAppColors = AppColors(
    background = Color(0xFF292929),
    backgroundWhite = Color(0xFFF3F3F3),
    black = Color(0xFF292929),
    blackInversed = Color(0xFFFFFFFF),
    white = Color(0xFFFFFFFF),
    gray = Color(0xFF636363),
    lightGrey = Color(0xFFEAEAEA),
    red = Color(0xFFBB393F),
)

val DarkAppColors = AppColors(
    background = Color(0xFF292929),
    backgroundWhite = Color(0xFFFFFFFF),
    black = Color(0xFFFFFFFF),
    blackInversed = Color(0xFF292929),
    white = Color(0xFFFFFFFF),
    gray = Color(0xFFFFFFFF),
    lightGrey = Color(0xFFFFFFFF),
    red = Color(0xFFBB393F),
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

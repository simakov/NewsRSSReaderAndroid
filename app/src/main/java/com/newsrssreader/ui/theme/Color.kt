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
    // Unlike `gray` (which deliberately resolves to white in dark mode to stay legible/high
    // contrast for icons and dividers), this stays an actual muted gray in both themes — for
    // secondary text that should read as visually de-emphasized vs. black/blackInversed, even
    // in dark mode (e.g. the article meta line and announce text).
    val mutedGray: Color,
    // Hairline separator for the top bar's bottom edge. In light mode the top bar and the
    // content below it are already different colors (background vs. blackInversed), so this
    // stays fully transparent there; in dark mode both resolve to the same charcoal
    // (background == blackInversed), so a pale gray line is needed to keep the bar from
    // blending into the content underneath it.
    val topBarBorder: Color,
    // Background for news rows that just appeared in a pull-to-refresh. A subtle warm/pale
    // yellow tint in light mode (the screen background there is plain white), and a slightly
    // lighter charcoal than the screen background in dark mode (which is otherwise too dark for
    // a yellow tint to read as anything other than muddy).
    val newsHighlight: Color,
    // Fill for the skeleton bars/boxes of the loading placeholders (see `NewsRowPlaceholder`).
    // Deliberately its own token rather than `lightGrey`/`gray`, both of which resolve to white
    // in dark mode: a placeholder must read as an empty, muted shape in both themes, otherwise
    // the skeleton is brighter than the real content it stands in for.
    val placeholder: Color,
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
    mutedGray = Color(0xFF636363),
    topBarBorder = Color.Transparent,
    newsHighlight = Color(0xFFFFF6D9),
    placeholder = Color(0xFFE3E3E3),
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
    mutedGray = Color(0xFF9E9E9E),
    topBarBorder = Color(0xFF4D4D4D),
    newsHighlight = Color(0xFF3D3D3D),
    placeholder = Color(0xFF3D3D3D),
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

package com.newsrssreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.newsrssreader.ui.theme.AppTheme

/**
 * Top app bar: hamburger menu button, then the "LENTA.RU" wordmark, then a trailing spacer that
 * pushes both toward the leading edge. Mirrors the iOS `Header` view's `HStack`, which lets its
 * content (plus padding) determine the bar's height rather than forcing a fixed height.
 */
@Composable
fun TopPanel(onMenuClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        // Inset below the status bar first, then let CenterVertically center the icon/text within
        // whatever height remains (content + padding still determine the bar's total height).
        modifier = modifier
            .background(AppTheme.colors.background)
            .windowInsetsPadding(WindowInsets.statusBars),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenuClick, modifier = Modifier.padding(10.dp)) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menu",
                tint = AppTheme.colors.white,
            )
        }

        Text(
            text = "LENTA.RU",
            color = AppTheme.colors.white,
            modifier = Modifier
                .padding(10.dp)
                .size(width = 120.dp, height = 20.dp),
        )

        Spacer(modifier = Modifier.weight(1f))
    }
}

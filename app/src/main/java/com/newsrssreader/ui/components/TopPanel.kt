package com.newsrssreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.newsrssreader.R
import com.newsrssreader.ui.theme.AppTheme
import androidx.compose.foundation.Image

// Bar content height (below the status bar inset), 30% shorter than the original 48.dp row —
// which was governed by IconButton's forced 48.dp minimum touch target, not its own padding.
// A plain clickable Box replaces IconButton here so the row can actually be shrunk below that
// forced minimum.
val TopPanelDefaultContentHeight = 34.dp
private val TopPanelIconPadding = 5.dp

/**
 * Top app bar: hamburger menu button, then the "LENTA.RU" wordmark, then a trailing spacer that
 * pushes both toward the leading edge. Mirrors the iOS `Header` view's `HStack`.
 */
@Composable
fun TopPanel(
    onMenuClick: () -> Unit,
    onLogoClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    contentHeight: Dp = TopPanelDefaultContentHeight,
) {
    Row(
        // Inset below the status bar first, then let CenterVertically center the icon/text within
        // the fixed content height.
        modifier = modifier
            .background(AppTheme.colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(contentHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clickable(onClick = onMenuClick)
                .padding(TopPanelIconPadding),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menu",
                tint = AppTheme.colors.white,
            )
        }

        Image(
            painter = painterResource(id = R.drawable.ic_lenta_logo),
            contentDescription = "LENTA.RU",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .clickable(onClick = onLogoClick)
                .padding(TopPanelIconPadding)
                .height(contentHeight - TopPanelIconPadding * 2)
                .wrapContentWidth(),
        )

        Spacer(modifier = Modifier.weight(1f))
    }
}

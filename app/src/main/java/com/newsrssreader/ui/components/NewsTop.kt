package com.newsrssreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.newsrssreader.data.model.NewsItem
import com.newsrssreader.ui.theme.AppTheme

private val HeroHeight = 350.dp

/**
 * Hero banner shown at the top of the Home feed: a full-bleed background photo with a
 * bottom-fade scrim and the featured article's title/date/category overlaid at the bottom.
 *
 * Mirrors the iOS `NewsTop` view, which guards on `data.link` and `data.image` both being
 * non-nil before rendering anything. Here the guard lives *inside* this composable (it
 * no-ops and renders nothing when either is null) rather than being pushed onto the caller,
 * so `HomeScreen` (Task 10) can call `NewsTop(item)` unconditionally the same way the SwiftUI
 * call site does, without every caller needing to duplicate the null-check.
 */
@Composable
fun NewsTop(item: NewsItem?, modifier: Modifier = Modifier) {
    if (item?.link == null || item.image == null) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HeroHeight),
    ) {
        AsyncImage(
            model = item.image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Bottom-fade scrim, matching the SwiftUI gradient's `startPoint: .center,
        // endPoint: .bottom` — the fade begins at the vertical center of the image rather
        // than at its top, so the gradient stop starts at 0.5f instead of 0f.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.5f to Color.Transparent,
                        1f to AppTheme.colors.background,
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        ) {
            Text(
                text = item.title.orEmpty(),
                style = AppTheme.type.heroTitle,
                color = AppTheme.colors.white,
                overflow = TextOverflow.Ellipsis,
            )
            Row {
                Text(
                    text = item.publishedDate(),
                    style = AppTheme.type.meta,
                    color = AppTheme.colors.lightGrey,
                )
                item.categories?.firstOrNull()?.let { category ->
                    Text(
                        text = category,
                        style = AppTheme.type.meta,
                        color = AppTheme.colors.lightGrey,
                        modifier = Modifier.padding(start = 5.dp),
                    )
                }
            }
        }
    }
}

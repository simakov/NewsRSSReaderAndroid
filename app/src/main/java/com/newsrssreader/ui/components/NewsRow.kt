package com.newsrssreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.newsrssreader.data.model.NewsItem
import com.newsrssreader.ui.theme.AppTheme

private val ThumbnailSize = 60.dp
private val ThumbnailShape = RoundedCornerShape(4.dp)

/**
 * A single row in the news list: title + date on the left, a 60x60dp thumbnail (or a plain dark
 * placeholder box when the item has no image) on the right.
 */
@Composable
fun NewsRow(item: NewsItem, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title.orEmpty(),
                style = AppTheme.type.rowTitle,
                color = AppTheme.colors.black,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 5.dp),
            )
            Text(
                text = item.publishedDate(),
                style = AppTheme.type.meta,
                color = AppTheme.colors.gray,
            )
        }

        if (item.image != null) {
            AsyncImage(
                model = item.image,
                contentDescription = null,
                modifier = Modifier
                    .size(ThumbnailSize)
                    .clip(ThumbnailShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(ThumbnailSize)
                    .clip(ThumbnailShape)
                    .background(AppTheme.colors.black),
            )
        }
    }
}

/**
 * A shimmering placeholder row used while the feed is loading, matching the iOS pattern of
 * applying `.redacted(reason: .placeholder).shimmering()` to sample rows.
 *
 * Compose has no direct `.redacted` equivalent, so this simply renders a real `NewsRow` (using
 * `NewsItem.sample`) with the whole row wrapped in `.shimmer()`. Applying the shimmer to the
 * entire row (rather than building separate placeholder-shaped boxes for the title/date/
 * thumbnail) is simpler, reuses `NewsRow` directly so the placeholder's layout can never drift
 * from the real row's layout, and the diagonal highlight sweep reads clearly over the sample
 * row's text and thumbnail without needing bespoke placeholder shapes.
 */
@Composable
fun NewsRowPlaceholder(modifier: Modifier = Modifier) {
    NewsRow(item = NewsItem.sample, modifier = modifier.shimmer())
}

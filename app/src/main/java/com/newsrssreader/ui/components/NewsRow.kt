package com.newsrssreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.newsrssreader.data.model.NewsItem
import com.newsrssreader.ui.theme.AppTheme

private val ThumbnailSize = 60.dp
private val ThumbnailShape = RoundedCornerShape(4.dp)

// Bounds on the thumbnail's computed height so a pathologically tall/thin or wide/flat image
// can't blow up (or collapse) the row; genuine variation within this range is allowed through.
private val ThumbnailMinHeight = 40.dp
private val ThumbnailMaxHeight = 120.dp

/**
 * A single row in the news list: title + date on the left, a thumbnail (or a plain dark
 * placeholder box when the item has no image) on the right. The thumbnail is always
 * [ThumbnailSize] (60dp) wide; its height adapts to the loaded image's aspect ratio (clamped to
 * [ThumbnailMinHeight]..[ThumbnailMaxHeight]) instead of being forced into a fixed square.
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
            // Unknown until the image finishes loading; use the fixed square size as a
            // reasonable default in the meantime so the row doesn't start at a jarring size.
            var thumbnailHeight by remember(item.image) { mutableStateOf(ThumbnailSize) }

            AsyncImage(
                model = item.image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onState = { state ->
                    if (state is AsyncImagePainter.State.Success) {
                        val size = state.painter.intrinsicSize
                        if (size.isSpecified && size.height > 0f) {
                            val aspectRatio = size.width / size.height
                            thumbnailHeight = (ThumbnailSize / aspectRatio)
                                .coerceIn(ThumbnailMinHeight, ThumbnailMaxHeight)
                        }
                    }
                },
                modifier = Modifier
                    .width(ThumbnailSize)
                    .height(thumbnailHeight)
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

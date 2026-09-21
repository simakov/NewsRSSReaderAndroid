package com.newsrssreader.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Text
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.newsrssreader.data.model.NewsItem
import com.newsrssreader.ui.theme.AppTheme

private val ThumbnailSize = 60.dp
private val ThumbnailShape = RoundedCornerShape(4.dp)

// Gutter between the text column and the thumbnail, so the headline never butts up against the
// image. Sized as roughly two characters of the 15sp row title (an average glyph runs about half
// the font size wide).
private val TitleThumbnailGap = 16.dp

// Skeleton bar geometry for NewsRowPlaceholder: three title lines plus a shorter date line,
// matching the real row's line heights and 5dp title/date gap closely enough that swapping the
// placeholder for a loaded row doesn't visibly shift the list.
private val PlaceholderTitleLineHeight = 15.dp
private val PlaceholderDateLineHeight = 11.dp
private val PlaceholderLineGap = 6.dp
private val PlaceholderShape = RoundedCornerShape(3.dp)

/**
 * Opacity of an already-read headline. Applied as alpha rather than as a dimmer color token
 * because this app's dark palette resolves both `gray` and `black` to white (see Color.kt), so
 * there is no "slightly grayer than the title color" that works in both themes — but alpha does.
 *
 * Only the title is dimmed: the thumbnail and the date stay at full strength, so a read row still
 * reads as a row rather than as a disabled one. 0.45 is far enough to be caught by peripheral
 * vision during a fling, and near enough to leave the headline perfectly legible.
 */
private const val ReadTitleAlpha = 0.45f

/**
 * A single row in the news list: title + date on the left, a thumbnail (or a plain dark
 * placeholder box when the item has no image) on the right. The thumbnail is always a fixed
 * [ThumbnailSize] (60dp) square, matching iOS's `.frame(width: 60, height: 60)` with
 * `.aspectRatio(contentMode: .fill)` — the image is scaled (preserving its own aspect ratio) to
 * completely cover the square, cropping any excess (Compose's [ContentScale.Crop]).
 *
 * [isHighlighted] tints the row background to flag it as newly arrived from a pull-to-refresh;
 * the fade itself is animated here, but when the highlight should end (and disappear) is decided
 * by the caller's ViewModel, not this composable.
 *
 * [isRead] dims the headline of an article that has already been opened. It is passed in rather
 * than read from `ReadStateStore` here so the row stays a plain function of its inputs.
 */
@Composable
fun NewsRow(
    item: NewsItem,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    isRead: Boolean = false,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isHighlighted) AppTheme.colors.newsHighlight else Color.Transparent,
        label = "newsRowHighlight",
    )
    Row(
        modifier = modifier
            .background(backgroundColor)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = TitleThumbnailGap)) {
            Text(
                text = item.title.orEmpty(),
                style = AppTheme.type.rowTitle,
                color = if (isRead) {
                    AppTheme.colors.black.copy(alpha = ReadTitleAlpha)
                } else {
                    AppTheme.colors.black
                },
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
                contentScale = ContentScale.Crop,
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
 * Compose has no direct `.redacted` equivalent, so the row's shapes are drawn explicitly as
 * blank gray bars (three headline lines, a shorter date line, and the thumbnail square) rather
 * than by rendering a real `NewsRow` with sample text: a placeholder built from sample text is
 * readable, and readable placeholder text reads as actual — wrong — news. The layout mirrors
 * `NewsRow`'s (same padding, same 60dp thumbnail, same text/thumbnail gutter) so the swap to
 * real rows doesn't jump.
 */
@Composable
fun NewsRowPlaceholder(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(10.dp)
            .shimmer(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = TitleThumbnailGap)) {
            PlaceholderBar(widthFraction = 1f, height = PlaceholderTitleLineHeight)
            Spacer(modifier = Modifier.height(PlaceholderLineGap))
            PlaceholderBar(widthFraction = 1f, height = PlaceholderTitleLineHeight)
            Spacer(modifier = Modifier.height(PlaceholderLineGap))
            PlaceholderBar(widthFraction = 0.6f, height = PlaceholderTitleLineHeight)
            Spacer(modifier = Modifier.height(PlaceholderLineGap + 2.dp))
            PlaceholderBar(widthFraction = 0.22f, height = PlaceholderDateLineHeight)
        }

        Box(
            modifier = Modifier
                .size(ThumbnailSize)
                .clip(ThumbnailShape)
                .background(AppTheme.colors.placeholder),
        )
    }
}

@Composable
private fun PlaceholderBar(widthFraction: Float, height: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(PlaceholderShape)
            .background(AppTheme.colors.placeholder),
    )
}

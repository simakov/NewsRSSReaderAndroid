package com.newsrssreader.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

/**
 * A reusable diagonal shimmer sweep, analogous to the iOS `.shimmering()` modifier from
 * SwiftUI-Shimmer. Drives a soft highlight band diagonally (top-left to bottom-right) across
 * whatever content it decorates, looping forever on a ~1.5s linear sweep.
 *
 * Implementation note: rather than painting an opaque gradient over the content (which would
 * simply hide it), the content is first rendered into an offscreen layer and the gradient is
 * then composited with `BlendMode.SrcAtop`, so the highlight band only shows up over the
 * existing (opaque) shapes underneath it — e.g. the gray placeholder rectangles/boxes this is
 * applied to. This keeps the modifier generic: it works equally well over the list-row
 * placeholders built in this task and the paragraph placeholders that will reuse it later
 * (Task 12's ArticleDetailScreen loading state).
 */
fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val progress by transition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, delayMillis = 250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )

    val highlightColor = Color.White

    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()

            val diagonal = size.width + size.height
            val bandCenter = progress * diagonal
            val bandHalfWidth = diagonal * 0.15f
            val start = Offset(bandCenter - bandHalfWidth, bandCenter - bandHalfWidth)
            val end = Offset(bandCenter + bandHalfWidth, bandCenter + bandHalfWidth)

            val brush = Brush.linearGradient(
                0f to highlightColor.copy(alpha = 0.3f),
                0.5f to highlightColor.copy(alpha = 1.0f),
                1f to highlightColor.copy(alpha = 0.3f),
                start = start,
                end = end,
            )

            drawRect(brush = brush, blendMode = BlendMode.SrcAtop)
        }
}

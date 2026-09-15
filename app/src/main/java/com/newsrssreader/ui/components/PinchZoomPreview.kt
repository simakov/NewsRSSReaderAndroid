package com.newsrssreader.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch

private const val MIN_PREVIEW_SCALE = 1f
private const val DEFAULT_MAX_PREVIEW_SCALE = 2.5f

/**
 * Lets the user pinch a static image to temporarily magnify it in place, snapping back to its
 * original size the instant every finger is lifted - a quick preview zoom rather than the
 * persistent pan/zoom of the full-screen photo viewer (`PhotoViewerScreen`/`ZoomableAsyncImage`),
 * which is what article images navigate to on tap. Only reacts to genuine multi-touch pinches:
 * a single-finger touch never changes `calculateZoom()` away from 1 and is never consumed here,
 * so the enclosing scrollable article body keeps scrolling normally.
 */
fun Modifier.pinchZoomPreview(maxScale: Float = DEFAULT_MAX_PREVIEW_SCALE): Modifier = composed {
    val scale = remember { Animatable(MIN_PREVIEW_SCALE) }
    val coroutineScope = rememberCoroutineScope()

    this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .zIndex(if (scale.value > MIN_PREVIEW_SCALE) 1f else 0f)
        .pointerInput(maxScale) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var pointersDown = true
                while (pointersDown) {
                    val event = awaitPointerEvent()
                    val zoomChange = event.calculateZoom()
                    if (zoomChange != 1f) {
                        val newScale = (scale.value * zoomChange).coerceIn(MIN_PREVIEW_SCALE, maxScale)
                        coroutineScope.launch { scale.snapTo(newScale) }
                        event.changes.forEach { change -> if (change.positionChanged()) change.consume() }
                    }
                    pointersDown = event.changes.any { it.pressed }
                }
                coroutineScope.launch { scale.animateTo(MIN_PREVIEW_SCALE) }
            }
        }
}

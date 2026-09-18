package com.newsrssreader.ui.photo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.animate
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.lerp
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Precision
import com.newsrssreader.data.saveImageToGallery
import com.newsrssreader.data.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min

private const val DEFAULT_SCALE = 1f

// Fixed double-tap zoom levels (relative to fit-to-screen) - cycles 1x -> 2x -> 4x -> back to 1x.
// Also doubles as the hard ceiling for pinch-zoom, so pinching can always reach as far as
// double-tap can.
private val DOUBLE_TAP_SCALE_TIERS = floatArrayOf(DEFAULT_SCALE, 2f, 4f)
private val MAX_SCALE = DOUBLE_TAP_SCALE_TIERS.last()

private const val MAX_SHARPEN_AMOUNT = 1.4f
private const val SHARPEN_ENGAGE_EPSILON = 0.05f

/**
 * How large a bitmap this device may be handed for display: bounded both by what the screen can
 * actually show at full zoom (anything past that is detail no one can see, decoded and held in
 * memory for nothing) and by [MAX_SAFE_BITMAP_DIMENSION], past which uploading the bitmap as a
 * GPU texture stops being safe. Coil decodes straight to this bound, so an oversized image is
 * downsampled during decode rather than allocated in full and shrunk afterwards.
 */
private fun maxDisplayBitmapDimension(context: Context): Int {
    val metrics = context.resources.displayMetrics
    val longestScreenSide = maxOf(metrics.widthPixels, metrics.heightPixels)
    val neededAtFullZoom = (longestScreenSide * MAX_SCALE).toInt()
    return neededAtFullZoom.coerceIn(1, MAX_SAFE_BITMAP_DIMENSION)
}

/**
 * Full-screen photo viewer opened from any article image (hero or inline). Supports
 * pinch-to-zoom/pan anchored to the pinch's focal point, a three-step double-tap-to-zoom cycle
 * (1x -> 2x -> 4x -> back to 1x, anchored to where the tap landed), and saving the currently
 * displayed image to the device gallery.
 *
 * Pinch/pan and double-tap are both recognized by [detectPinchPanAndDoubleTap] below, a single
 * hand-rolled gesture reader, rather than by racing Compose foundation's `detectTransformGestures`
 * and `detectTapGestures` against each other. Two independent detectors - whether on separate
 * `pointerInput` modifiers or launched concurrently within one - both call `awaitFirstDown()` off
 * the same touch stream, and one can end up consuming or observing an event before the other is
 * ready to see it; in practice this showed up as the *first* double-tap after opening the viewer
 * sometimes doing nothing; every one after that has the pointerInput coroutines already settled
 * into their loops and behaves correctly. Reading the whole gesture through one loop removes that
 * race entirely.
 *
 * The image is loaded once as a plain `ImageBitmap` (via Coil's `ImageLoader.execute`, forcing a
 * software bitmap) instead of through `AsyncImage`, so the pixels are available directly to both
 * sharpening paths in `ImageSharpening.kt`: a `graphicsLayer.renderEffect` AGSL pass on devices
 * that have RuntimeShader, and a one-off CPU unsharp mask on those that don't. That file's header
 * explains why none of the API-33-only types may be named here - getting that wrong crashed the
 * screen outright on an API 29 device.
 *
 * This is a standalone nav destination, so the system/gesture back action closes it via
 * Navigation-Compose's own back stack without any extra handling here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewerScreen(imageUrl: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var imageBitmap by remember(imageUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(imageUrl) {
        imageBitmap = null
        val maxDimension = maxDisplayBitmapDimension(context)
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .allowHardware(false)
            .size(maxDimension, maxDimension)
            .precision(Precision.INEXACT)
            .build()
        val drawable = context.imageLoader.execute(request).drawable
        if (drawable is BitmapDrawable) {
            // Belt and braces: `size` above asks the decoder for a bounded bitmap, but a cache hit
            // or a decoder that ignores the hint can still hand back the full-size original, and
            // that is exactly the bitmap a weak GPU refuses to upload.
            imageBitmap = downscaledToFit(drawable.bitmap, maxDimension).asImageBitmap()
        }
    }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Plain snapshot state rather than `Animatable`s: a pinch/pan gesture delivers several touch
    // events per frame, and each one's new offset is computed from the *current* one. `Animatable`
    // can only be written from a coroutine (`snapTo` is suspend), so every event in a frame read
    // the same stale offset and the last write won - the image ended up tracking only the final
    // delta of each frame instead of their sum, so it visibly lagged behind the finger. Writing
    // the state synchronously here makes each event build on the previous one, so the image
    // follows the finger exactly. The double-tap animation is driven by `animate` below instead.
    var scale by remember { mutableFloatStateOf(DEFAULT_SCALE) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var zoomAnimationJob by remember { mutableStateOf<Job?>(null) }

    // Which step of the double-tap cycle (see [DOUBLE_TAP_SCALE_TIERS]) we're currently on.
    // Tracked explicitly rather than inferred from how close `scale.value` is to each tier, since
    // a manual pinch can leave `scale.value` anywhere between tiers; a double-tap right after
    // pinching just continues the cycle from wherever it last left off.
    var zoomTierIndex by remember(imageUrl) { mutableStateOf(0) }

    // Null on devices with no usable RuntimeShader (API < 33, or one that rejects it), which then
    // take the CPU sharpening path below instead. Every API-33-only type stays inside
    // `ImageSharpening.kt` - see its header.
    val gpuSharpener = remember { createGpuImageSharpener() }

    // The scale at which the image's own pixels map 1:1 to screen pixels ("native resolution"),
    // expressed relative to this screen's `scale` variable (whose baseline of 1 = fit-to-screen).
    // Used only to decide when the GPU sharpening pass should kick in (past this point, further
    // zoom is stretching pixels the source image doesn't actually have).
    fun nativeMaxScale(): Float {
        val bitmap = imageBitmap
        val container = containerSize
        if (bitmap == null || container.width <= 0 || container.height <= 0) {
            return MAX_SCALE
        }
        val fit = min(container.width / bitmap.width.toFloat(), container.height / bitmap.height.toFloat())
        if (fit <= 0f) return MAX_SCALE
        return (1f / fit).coerceIn(DEFAULT_SCALE, MAX_SCALE)
    }

    // The CPU sharpening fallback, for devices the GPU path isn't available on. `derivedStateOf`
    // rather than a plain expression on purpose: `scale` changes several times per frame while
    // pinching, and reading it directly here would recompose the whole screen that often, whereas
    // this recomposes only on the two frames where the threshold is actually crossed.
    val cpuSharpeningEngaged by remember {
        derivedStateOf {
            gpuSharpener == null && scale > nativeMaxScale() + SHARPEN_ENGAGE_EPSILON
        }
    }
    var cpuSharpenedBitmap by remember(imageUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(imageUrl, imageBitmap, cpuSharpeningEngaged) {
        val source = imageBitmap
        if (!cpuSharpeningEngaged || source == null || cpuSharpenedBitmap != null) {
            return@LaunchedEffect
        }
        // Once per image, off the main thread: a full-bitmap pass is far too slow to run per frame,
        // but as a one-off on first zoom-in it's unnoticeable.
        cpuSharpenedBitmap = withContext(Dispatchers.Default) {
            sharpenedCopy(source.asAndroidBitmap())?.asImageBitmap()
        }
    }

    // graphicsLayer's transformOrigin is set to the top-left corner below (rather than the
    // default center), so both this and the pinch/double-tap math treat `offset` as the screen
    // position of the content's own top-left corner - simpler to reason about and to keep
    // anchored under a moving focal point than center-relative translation.
    fun clampedOffset(candidate: Offset, currentScale: Float, container: IntSize): Offset {
        if (container.width <= 0 || container.height <= 0) return Offset.Zero
        val minX = (container.width * (1f - currentScale)).coerceAtMost(0f)
        val minY = (container.height * (1f - currentScale)).coerceAtMost(0f)
        return Offset(candidate.x.coerceIn(minX, 0f), candidate.y.coerceIn(minY, 0f))
    }

    // Keeps the content point under `focalPoint` stationary on screen while the scale changes
    // from the current value to `newScale` - this is what makes pinching/double-tapping zoom in on
    // the spot the user actually touched, rather than always zooming from the image's center.
    //
    // `focalPoint` arrives in the *content's own* coordinates, not screen coordinates: the
    // pointerInput modifier sits inside the graphicsLayer below, so Compose has already mapped
    // the touch through that layer's scale before handing it over. Its position on screen is
    // therefore `offset + focalPoint * scale`, and keeping it pinned there across the scale
    // change leaves the offset shifted by `focalPoint * (scale - newScale)`.
    fun zoomAnchoredOffset(focalPoint: Offset, newScale: Float): Offset =
        offset + focalPoint * (scale - newScale)

    val savePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            scope.launch {
                saveImageToGallery(context, context.imageLoader, imageUrl)
            }
        } else {
            showToast(context, "Нужно разрешение для сохранения фото")
        }
    }

    fun onSaveClick() {
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.O..Build.VERSION_CODES.P) {
            val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
            val granted = ContextCompat.checkSelfPermission(
                context,
                permission,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                savePermissionLauncher.launch(permission)
                return
            }
        }
        scope.launch {
            saveImageToGallery(context, context.imageLoader, imageUrl)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onSaveClick() }) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                ),
                expandedHeight = TopAppBarDefaults.TopAppBarExpandedHeight * 0.7f,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(innerPadding)
                .onSizeChanged { containerSize = it },
            contentAlignment = Alignment.Center,
        ) {
            // Zoomed past native resolution with no GPU sharpener, the sharpened copy stands in for
            // the original (until it's ready, and on any device where it couldn't be produced, this
            // is simply the original - the viewer never waits on it).
            val bitmap = if (cpuSharpeningEngaged) cpuSharpenedBitmap ?: imageBitmap else imageBitmap
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                            transformOrigin = TransformOrigin(0f, 0f)

                            val sharpener = gpuSharpener
                            val nativeMax = nativeMaxScale()
                            renderEffect = if (sharpener != null && scale > nativeMax + SHARPEN_ENGAGE_EPSILON) {
                                val digitalZoomRange = (MAX_SCALE - nativeMax).coerceAtLeast(0.01f)
                                val digitalZoomFraction = ((scale - nativeMax) / digitalZoomRange).coerceIn(0f, 1f)
                                sharpener.renderEffect(digitalZoomFraction * MAX_SHARPEN_AMOUNT)
                            } else {
                                null
                            }
                        }
                        .pointerInput(Unit) {
                            detectPinchPanAndDoubleTap(
                                onTransform = { centroid, pan, zoom ->
                                    // A touch taking over from a running double-tap animation
                                    // wins; otherwise the animation would keep writing over the
                                    // values the gesture is setting.
                                    zoomAnimationJob?.cancel()
                                    val newScale = (scale * zoom).coerceIn(DEFAULT_SCALE, MAX_SCALE)
                                    // `pan` is measured in the same pre-scale content
                                    // coordinates as `centroid` (see zoomAnchoredOffset), so a
                                    // finger that travelled N screen pixels reports N / scale
                                    // here. `offset` is a screen-space translation, so the pan
                                    // has to be scaled back up - without this the image drifted
                                    // behind the finger, more slowly the further it was zoomed
                                    // in.
                                    offset = clampedOffset(
                                        zoomAnchoredOffset(centroid, newScale) + pan * scale,
                                        newScale,
                                        containerSize,
                                    )
                                    scale = newScale
                                },
                                onDoubleTap = { tapPosition ->
                                    zoomTierIndex = (zoomTierIndex + 1) % DOUBLE_TAP_SCALE_TIERS.size
                                    val targetScale = DOUBLE_TAP_SCALE_TIERS[zoomTierIndex]
                                    val targetOffset = clampedOffset(
                                        zoomAnchoredOffset(tapPosition, targetScale),
                                        targetScale,
                                        containerSize,
                                    )
                                    val startScale = scale
                                    val startOffset = offset
                                    zoomAnimationJob?.cancel()
                                    zoomAnimationJob = scope.launch {
                                        animate(initialValue = 0f, targetValue = 1f) { fraction, _ ->
                                            scale = lerp(startScale, targetScale, fraction)
                                            offset = lerp(startOffset, targetOffset, fraction)
                                        }
                                    }
                                },
                            )
                        },
                )
            }
        }
    }
}

/**
 * Reads pinch/pan and double-tap off a single shared touch stream (see the "why" on
 * [PhotoViewerScreen]'s doc comment). Pinch/pan detection mirrors Compose foundation's own
 * `detectTransformGestures` (touch-slop-gated zoom/pan since we don't need rotation here); a
 * completed gesture that never exceeded touch slop is treated as a plain tap and checked against
 * the previous tap's time/position to recognize a double-tap, using the same system-standard
 * timeout/slop constants `detectTapGestures` itself would use.
 */
private suspend fun PointerInputScope.detectPinchPanAndDoubleTap(
    onTransform: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
    onDoubleTap: (Offset) -> Unit,
) {
    val touchSlop = viewConfiguration.touchSlop
    val doubleTapTimeoutMillis = viewConfiguration.doubleTapTimeoutMillis
    var lastTapTimeMillis = 0L
    var lastTapPosition: Offset? = null

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var pastTouchSlop = false
        var zoom = 1f
        var pan = Offset.Zero

        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    pan += panChange
                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoom) * centroidSize
                    val panMotion = pan.getDistance()
                    if (zoomMotion > touchSlop || panMotion > touchSlop) {
                        pastTouchSlop = true
                    }
                }

                if (pastTouchSlop) {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    if (zoomChange != 1f || panChange != Offset.Zero) {
                        onTransform(centroid, panChange, zoomChange)
                    }
                    event.changes.forEach { change -> if (change.positionChanged()) change.consume() }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })

        if (!pastTouchSlop) {
            val now = System.currentTimeMillis()
            val previousTapPosition = lastTapPosition
            val isDoubleTap = previousTapPosition != null &&
                now - lastTapTimeMillis <= doubleTapTimeoutMillis &&
                (down.position - previousTapPosition).getDistance() <= touchSlop * 4
            if (isDoubleTap) {
                onDoubleTap(down.position)
                lastTapTimeMillis = 0L
                lastTapPosition = null
            } else {
                lastTapTimeMillis = now
                lastTapPosition = down.position
            }
        }
    }
}

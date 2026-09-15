package com.newsrssreader.ui.photo

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import coil.imageLoader
import coil.request.ImageRequest
import com.newsrssreader.data.saveImageToGallery
import com.newsrssreader.data.showToast
import kotlinx.coroutines.launch
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

// A GPU-executed AGSL unsharp-mask: samples the four direct neighbors of each pixel, subtracts
// that local blur from the pixel to isolate edge/detail contrast, then re-adds it scaled by
// `amount`. This is the standard, well-established technique for making an upscaled image read as
// sharper/more detailed than plain bilinear interpolation without needing an ML model - it runs as
// a RenderEffect on the compositor's GPU pipeline via RuntimeShader (API 33+; devices below that
// simply display the plain, GPU-bilinear-scaled image with no extra sharpening pass).
private const val SHARPEN_SHADER_SRC = """
    uniform shader content;
    uniform float amount;

    half4 main(float2 coord) {
        half4 center = content.eval(coord);
        half4 neighborSum = content.eval(coord + float2(1.0, 0.0))
                           + content.eval(coord + float2(-1.0, 0.0))
                           + content.eval(coord + float2(0.0, 1.0))
                           + content.eval(coord + float2(0.0, -1.0));
        half4 blurred = neighborSum * 0.25;
        half4 sharpened = center + (center - blurred) * amount;
        return half4(clamp(sharpened.rgb, 0.0, 1.0), center.a);
    }
"""

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
 * software bitmap) instead of through `AsyncImage`, so the pixels are available directly for the
 * `graphicsLayer.renderEffect` sharpening pass described on [SHARPEN_SHADER_SRC].
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
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .allowHardware(false)
            .build()
        val drawable = context.imageLoader.execute(request).drawable
        if (drawable is BitmapDrawable) {
            imageBitmap = drawable.bitmap.asImageBitmap()
        }
    }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val scale = remember { Animatable(DEFAULT_SCALE) }
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    // Which step of the double-tap cycle (see [DOUBLE_TAP_SCALE_TIERS]) we're currently on.
    // Tracked explicitly rather than inferred from how close `scale.value` is to each tier, since
    // a manual pinch can leave `scale.value` anywhere between tiers; a double-tap right after
    // pinching just continues the cycle from wherever it last left off.
    var zoomTierIndex by remember(imageUrl) { mutableStateOf(0) }

    // API 33+ only; RuntimeShader/RenderEffect.createRuntimeShaderEffect don't exist below that,
    // so older devices simply see the plain GPU-bilinear-scaled image with no extra sharpening.
    val sharpenShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(SHARPEN_SHADER_SRC) else null
    }

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
    fun zoomAnchoredOffset(focalPoint: Offset, newScale: Float): Offset {
        val oldScale = scale.value
        val contentPointUnderFocal = (focalPoint - offset.value) / oldScale
        return focalPoint - contentPointUnderFocal * newScale
    }

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
            val bitmap = imageBitmap
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                            translationX = offset.value.x
                            translationY = offset.value.y
                            transformOrigin = TransformOrigin(0f, 0f)

                            val shader = sharpenShader
                            val nativeMax = nativeMaxScale()
                            renderEffect = if (shader != null && scale.value > nativeMax + SHARPEN_ENGAGE_EPSILON) {
                                val digitalZoomRange = (MAX_SCALE - nativeMax).coerceAtLeast(0.01f)
                                val digitalZoomFraction = ((scale.value - nativeMax) / digitalZoomRange).coerceIn(0f, 1f)
                                shader.setFloatUniform("amount", digitalZoomFraction * MAX_SHARPEN_AMOUNT)
                                RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
                            } else {
                                null
                            }
                        }
                        .pointerInput(Unit) {
                            detectPinchPanAndDoubleTap(
                                onTransform = { centroid, pan, zoom ->
                                    val newScale = (scale.value * zoom).coerceIn(DEFAULT_SCALE, MAX_SCALE)
                                    val newOffset = clampedOffset(
                                        zoomAnchoredOffset(centroid, newScale) + pan,
                                        newScale,
                                        containerSize,
                                    )
                                    scope.launch { scale.snapTo(newScale) }
                                    scope.launch { offset.snapTo(newOffset) }
                                },
                                onDoubleTap = { tapPosition ->
                                    zoomTierIndex = (zoomTierIndex + 1) % DOUBLE_TAP_SCALE_TIERS.size
                                    val targetScale = DOUBLE_TAP_SCALE_TIERS[zoomTierIndex]
                                    val targetOffset = clampedOffset(
                                        zoomAnchoredOffset(tapPosition, targetScale),
                                        targetScale,
                                        containerSize,
                                    )
                                    scope.launch { scale.animateTo(targetScale) }
                                    scope.launch { offset.animateTo(targetOffset) }
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

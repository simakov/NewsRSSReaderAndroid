package com.newsrssreader.ui.photo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.imageLoader
import com.newsrssreader.data.saveImageToGallery
import kotlinx.coroutines.launch
import kotlin.math.min

private const val DEFAULT_SCALE = 1f
private const val FALLBACK_MAX_SCALE = 3f

/**
 * Full-screen photo viewer opened from any article image (hero or inline). Supports
 * pinch-to-zoom/pan and double-tap-to-zoom (toggling between fit-to-width and "native
 * resolution" zoom), plus saving the currently displayed image to the device gallery. This is a
 * standalone nav destination, so the system/gesture back action closes it via Navigation-Compose's
 * own back stack without any extra handling here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewerScreen(imageUrl: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Intrinsic pixel size of the loaded image, used to compute the "native resolution" max zoom
    // scale once known; unknown until then, so max zoom falls back to a fixed multiplier.
    var intrinsicSize by remember { mutableStateOf<Size?>(null) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val scale = remember { Animatable(DEFAULT_SCALE) }
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    fun maxScale(): Float {
        val size = intrinsicSize
        val container = containerSize
        if (size == null || container.width <= 0 || container.height <= 0) {
            return FALLBACK_MAX_SCALE
        }
        // ContentScale.Fit scales the intrinsic image down (or up) by whichever factor makes it
        // fit entirely inside the container - the smaller of the width-fit and height-fit
        // ratios (landscape images end up width-constrained, portrait images height-constrained,
        // with letterboxing on the other axis either way). Native resolution (1 image pixel = 1
        // screen pixel) is reached by zooming in further from that fit scale by its reciprocal.
        val fitScale = min(container.width / size.width, container.height / size.height)
        if (fitScale <= 0f) return FALLBACK_MAX_SCALE
        return (1f / fitScale).coerceAtLeast(DEFAULT_SCALE)
    }

    fun clampedOffset(candidate: Offset, currentScale: Float, container: IntSize): Offset {
        if (container.width <= 0 || container.height <= 0) return candidate
        // How far the scaled content can extend past the container on each axis; beyond that the
        // image would be dragged entirely off-screen, so clamp to it.
        val maxX = (container.width * (currentScale - 1f) / 2f).coerceAtLeast(0f)
        val maxY = (container.height * (currentScale - 1f) / 2f).coerceAtLeast(0f)
        return Offset(
            x = candidate.x.coerceIn(-maxX, maxX),
            y = candidate.y.coerceIn(-maxY, maxY),
        )
    }

    val savePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            coroutineScope.launch {
                saveImageToGallery(context, context.imageLoader, imageUrl)
            }
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
        coroutineScope.launch {
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
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onState = { state ->
                    if (state is AsyncImagePainter.State.Success) {
                        val size = state.painter.intrinsicSize
                        if (size.isSpecified && size.width > 0f && size.height > 0f) {
                            intrinsicSize = size
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        translationX = offset.value.x
                        translationY = offset.value.y
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale.value * zoom).coerceIn(DEFAULT_SCALE, maxScale())
                            val newOffset = clampedOffset(
                                offset.value + pan.times(newScale / scale.value.coerceAtLeast(0.01f)),
                                newScale,
                                containerSize,
                            )
                            coroutineScope.launch { scale.snapTo(newScale) }
                            coroutineScope.launch { offset.snapTo(newOffset) }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                val isZoomedIn = scale.value > DEFAULT_SCALE + 0.05f
                                val targetScale = if (isZoomedIn) DEFAULT_SCALE else maxScale()
                                coroutineScope.launch { scale.animateTo(targetScale) }
                                coroutineScope.launch { offset.animateTo(Offset.Zero) }
                            },
                        )
                    },
            )
        }
    }
}

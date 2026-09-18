package com.newsrssreader.ui.photo

import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asComposeRenderEffect
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect

/**
 * The photo viewer's two sharpening paths and its bitmap-size guard.
 *
 * ## Why the GPU path lives behind [GpuImageSharpener] rather than inline in the screen
 *
 * `android.graphics.RuntimeShader` and `RenderEffect.createRuntimeShaderEffect` only exist on
 * API 33+. Guarding their use with a plain `if (Build.VERSION.SDK_INT >= TIRAMISU)` *inside the
 * composable* is not enough: R8 inlined that `remember { ... }` lambda straight into
 * `PhotoViewerScreen`'s own method body, and ART on at least one real device (Huawei MatePad
 * AGS3K-L09, EMUI 10.1 / API 29) then resolved the missing class as it entered that method,
 * killing the app with
 *
 * ```
 * java.lang.NoClassDefFoundError: Failed resolution of: Landroid/graphics/RuntimeShader;
 * ```
 *
 * the moment the photo screen was opened - the version check never even got a chance to run.
 *
 * So every mention of an API-33-only type is confined to [AgslImageSharpener]; the screen only
 * ever sees the [GpuImageSharpener] interface and Compose's own (version-independent)
 * `RenderEffect` type. A device below API 33 never touches that class, and both its construction
 * and its per-frame use are additionally wrapped in `catch (Throwable)` - `NoClassDefFoundError`
 * and `VerifyError` are `Error`s, not `Exception`s, so a narrower catch would miss exactly the
 * failure this is here to survive. When the GPU path is unavailable for any reason, the screen
 * falls back to [sharpenedCopy] below.
 */
internal interface GpuImageSharpener {

    /**
     * The render effect to hang on the image's `graphicsLayer` for the given unsharp-mask
     * `amount`, or null if this device turned out not to support it after all (in which case the
     * caller should stop asking and fall back to the CPU path).
     */
    fun renderEffect(amount: Float): ComposeRenderEffect?
}

/**
 * A GPU-executed AGSL unsharp-mask: samples the four direct neighbors of each pixel, subtracts
 * that local blur from the pixel to isolate edge/detail contrast, then re-adds it scaled by
 * `amount`. This is the standard, well-established technique for making an upscaled image read as
 * sharper/more detailed than plain bilinear interpolation without needing an ML model - it runs as
 * a RenderEffect on the compositor's GPU pipeline via RuntimeShader.
 *
 * [unsharpMaskPixels] is the same filter written for the CPU, for devices that have no
 * RuntimeShader.
 */
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
 * The only place in the app that names an API-33-only graphics type. Instantiated exclusively
 * through [createGpuImageSharpener], which version-checks first and catches anything thrown if a
 * device disagrees.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class AgslImageSharpener : GpuImageSharpener {

    private val shader = RuntimeShader(SHARPEN_SHADER_SRC)

    // Latches on the first failure: `renderEffect` is called from the draw path on every frame of
    // a pinch, and a device that refuses the effect once will refuse it every time. Without this
    // the viewer would throw and swallow an exception per frame for as long as it stayed zoomed in.
    private var unsupported = false

    override fun renderEffect(amount: Float): ComposeRenderEffect? {
        if (unsupported) return null
        return try {
            shader.setFloatUniform("amount", amount)
            RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
        } catch (t: Throwable) {
            unsupported = true
            null
        }
    }
}

/**
 * The GPU sharpener for this device, or null when it has no usable RuntimeShader - see the file
 * header for why the version check and the `catch (Throwable)` are both needed.
 */
internal fun createGpuImageSharpener(): GpuImageSharpener? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    return try {
        AgslImageSharpener()
    } catch (t: Throwable) {
        null
    }
}

/** How hard the CPU fallback sharpens; deliberately gentler than the GPU path's peak. */
internal const val CPU_SHARPEN_AMOUNT = 0.8f

/**
 * The CPU counterpart of [SHARPEN_SHADER_SRC]: a copy of [source] with an unsharp mask applied,
 * or null if it could not be produced (an unreadable bitmap, or not enough memory for the copy -
 * this runs on devices old enough to lack RuntimeShader, so a failed allocation is a real
 * possibility and simply means the viewer keeps showing the unsharpened image).
 *
 * Applied once, to the source bitmap, on a background thread - not per frame. Sharpening the
 * source and letting the GPU upscale that is not identical to sharpening after the upscale, but
 * it recovers most of the perceived detail at a fixed, one-off cost, which is the right trade on
 * the low-end hardware this path exists for.
 */
internal fun sharpenedCopy(source: Bitmap, amount: Float = CPU_SHARPEN_AMOUNT): Bitmap? {
    val width = source.width
    val height = source.height
    if (width < 3 || height < 3) return null
    return try {
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val sharpened = unsharpMaskPixels(pixels, width, height, amount)
        Bitmap.createBitmap(sharpened, width, height, Bitmap.Config.ARGB_8888)
    } catch (t: Throwable) {
        null
    }
}

/** 0xFF000000 - the alpha byte of a packed-ARGB pixel, written as a compile-time Int constant. */
private const val ALPHA_MASK: Int = -0x1000000

/**
 * Unsharp mask over a packed-ARGB pixel array: each interior pixel is pushed away from the
 * average of its four direct neighbors by `amount`, per color channel, with alpha carried
 * through untouched. Edge pixels have no full neighborhood and are copied as-is.
 *
 * Returns [pixels] itself when there is nothing to do (a non-positive amount, or an image with no
 * interior at all), so callers should treat the result as read-only.
 */
internal fun unsharpMaskPixels(pixels: IntArray, width: Int, height: Int, amount: Float): IntArray {
    if (amount <= 0f || width < 3 || height < 3 || pixels.size < width * height) return pixels

    val result = pixels.copyOf()
    for (y in 1 until height - 1) {
        val rowStart = y * width
        for (x in 1 until width - 1) {
            val index = rowStart + x
            val center = pixels[index]
            val up = pixels[index - width]
            val down = pixels[index + width]
            val left = pixels[index - 1]
            val right = pixels[index + 1]
            result[index] = (center and ALPHA_MASK) or
                (sharpenChannel(center, up, down, left, right, shift = 16, amount = amount) shl 16) or
                (sharpenChannel(center, up, down, left, right, shift = 8, amount = amount) shl 8) or
                sharpenChannel(center, up, down, left, right, shift = 0, amount = amount)
        }
    }
    return result
}

private fun sharpenChannel(
    center: Int,
    up: Int,
    down: Int,
    left: Int,
    right: Int,
    shift: Int,
    amount: Float,
): Int {
    val value = (center shr shift) and 0xFF
    val blurred = (
        ((up shr shift) and 0xFF) +
            ((down shr shift) and 0xFF) +
            ((left shr shift) and 0xFF) +
            ((right shr shift) and 0xFF)
        ) * 0.25f
    return (value + (value - blurred) * amount).roundToInt().coerceIn(0, 255)
}

/**
 * Upper bound on either side of the bitmap the viewer hands to the GPU.
 *
 * Hardware-accelerated drawing uploads a bitmap as an OpenGL texture, and anything past
 * `GL_MAX_TEXTURE_SIZE` either silently fails to draw or takes the app down inside the renderer,
 * depending on the driver. The GL ES 3.x floor is 2048 and mid-range GPUs sit at 4096-8192, so
 * 4096 is the largest value that is safe without querying the driver - and it is still far above
 * anything Lenta.ru actually serves, which means [downscaledSizeFor] is a guard that normally
 * does nothing rather than a quality compromise.
 */
internal const val MAX_SAFE_BITMAP_DIMENSION = 4096

/**
 * The (width, height) an image of [width] x [height] should be resized to so neither side exceeds
 * [maxDimension], or null when it already fits (the overwhelmingly common case) or the inputs are
 * degenerate. Aspect ratio is preserved, and neither side is ever allowed to round down to zero.
 */
internal fun downscaledSizeFor(width: Int, height: Int, maxDimension: Int): Pair<Int, Int>? {
    if (width <= 0 || height <= 0 || maxDimension <= 0) return null
    val largestSide = maxOf(width, height)
    if (largestSide <= maxDimension) return null
    val ratio = maxDimension.toFloat() / largestSide
    return Pair(
        (width * ratio).roundToInt().coerceAtLeast(1),
        (height * ratio).roundToInt().coerceAtLeast(1),
    )
}

/**
 * [source] itself when it is already within [maxDimension], otherwise a scaled-down copy that is
 * - unlike the original - safe to upload as a GPU texture. Falls back to the original if the copy
 * cannot be allocated: drawing an oversized bitmap may fail on some devices, but failing to show
 * anything fails on all of them.
 */
internal fun downscaledToFit(source: Bitmap, maxDimension: Int = MAX_SAFE_BITMAP_DIMENSION): Bitmap {
    val target = downscaledSizeFor(source.width, source.height, maxDimension) ?: return source
    return try {
        Bitmap.createScaledBitmap(source, target.first, target.second, true)
    } catch (t: Throwable) {
        source
    }
}

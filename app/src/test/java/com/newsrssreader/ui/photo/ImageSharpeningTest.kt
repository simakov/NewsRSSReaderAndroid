package com.newsrssreader.ui.photo

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the pure pixel/size math behind the photo viewer's CPU (non-GPU) sharpening and
 * resize-guard fallbacks. The Bitmap-level wrappers around these are thin and left to manual
 * on-device checks, as with the rest of the app's UI code.
 */
class ImageSharpeningTest {

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    private fun red(color: Int) = (color shr 16) and 0xFF
    private fun alpha(color: Int) = (color ushr 24) and 0xFF

    @Test
    fun `uniform image is left untouched`() {
        val flat = IntArray(5 * 5) { argb(255, 120, 130, 140) }

        val result = unsharpMaskPixels(flat, width = 5, height = 5, amount = 1.4f)

        assertArrayEquals(flat, result)
    }

    @Test
    fun `amount of zero returns the original pixels`() {
        val pixels = IntArray(4 * 4) { index -> argb(255, index * 10, 0, 0) }

        val result = unsharpMaskPixels(pixels, width = 4, height = 4, amount = 0f)

        assertArrayEquals(pixels, result)
    }

    @Test
    fun `a bright pixel on a dark field gets brighter`() {
        val width = 5
        val height = 5
        val pixels = IntArray(width * height) { argb(255, 100, 100, 100) }
        val center = 2 * width + 2
        pixels[center] = argb(255, 160, 160, 160)

        val result = unsharpMaskPixels(pixels, width, height, amount = 1f)

        assertTrue("center should be sharpened upward", red(result[center]) > 160)
    }

    @Test
    fun `channels stay inside the 0-255 range`() {
        val width = 3
        val height = 3
        val pixels = IntArray(width * height) { argb(255, 0, 0, 0) }
        pixels[1 * width + 1] = argb(255, 255, 255, 255)

        val result = unsharpMaskPixels(pixels, width, height, amount = 10f)

        result.forEach { color ->
            val r = red(color)
            assertTrue("channel out of range: $r", r in 0..255)
        }
    }

    @Test
    fun `alpha is preserved`() {
        val width = 3
        val height = 3
        val pixels = IntArray(width * height) { argb(128, 40, 40, 40) }
        pixels[1 * width + 1] = argb(128, 200, 200, 200)

        val result = unsharpMaskPixels(pixels, width, height, amount = 1.4f)

        result.forEach { color -> assertEquals(128, alpha(color)) }
    }

    @Test
    fun `edge pixels are copied unchanged`() {
        val width = 3
        val height = 3
        val pixels = IntArray(width * height) { argb(255, 10, 10, 10) }
        pixels[1 * width + 1] = argb(255, 250, 250, 250)

        val result = unsharpMaskPixels(pixels, width, height, amount = 1.4f)

        // Every pixel but the single interior one is an edge pixel here.
        for (index in pixels.indices) {
            if (index != 1 * width + 1) {
                assertEquals("edge pixel $index changed", pixels[index], result[index])
            }
        }
    }

    @Test
    fun `images too small to have an interior are returned as is`() {
        val pixels = IntArray(2 * 2) { argb(255, it, it, it) }

        val result = unsharpMaskPixels(pixels, width = 2, height = 2, amount = 1.4f)

        assertArrayEquals(pixels, result)
    }

    @Test
    fun `a bitmap within the limit needs no downscale`() {
        assertNull(downscaledSizeFor(width = 1200, height = 800, maxDimension = 4096))
        assertNull(downscaledSizeFor(width = 4096, height = 4096, maxDimension = 4096))
    }

    @Test
    fun `an oversized bitmap is capped while keeping its aspect ratio`() {
        val size = downscaledSizeFor(width = 8000, height = 4000, maxDimension = 4096)

        assertEquals(4096, size?.first)
        assertEquals(2048, size?.second)
    }

    @Test
    fun `the taller side drives the downscale for portrait images`() {
        val size = downscaledSizeFor(width = 3000, height = 9000, maxDimension = 3000)

        assertEquals(1000, size?.first)
        assertEquals(3000, size?.second)
    }

    @Test
    fun `a downscaled side never collapses to zero`() {
        val size = downscaledSizeFor(width = 20000, height = 3, maxDimension = 2048)

        assertEquals(2048, size?.first)
        assertEquals(1, size?.second)
    }

    @Test
    fun `degenerate sizes are rejected rather than divided by zero`() {
        assertNull(downscaledSizeFor(width = 0, height = 100, maxDimension = 2048))
        assertNull(downscaledSizeFor(width = 100, height = 0, maxDimension = 2048))
        assertNull(downscaledSizeFor(width = 100, height = 100, maxDimension = 0))
    }
}

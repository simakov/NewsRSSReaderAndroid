package com.newsrssreader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageSharerTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() } + ByteArray(16)

    @Test
    fun `recognizes the four formats Lenta serves by their magic bytes`() {
        assertEquals(SharedImageType("jpg", "image/jpeg"), detectImageType(bytes(0xFF, 0xD8, 0xFF, 0xE0)))
        assertEquals(SharedImageType("png", "image/png"), detectImageType(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
        assertEquals(SharedImageType("gif", "image/gif"), detectImageType("GIF89a".toByteArray() + ByteArray(8)))
        val webp = "RIFF".toByteArray() + ByteArray(4) + "WEBP".toByteArray() + ByteArray(8)
        assertEquals(SharedImageType("webp", "image/webp"), detectImageType(webp))
    }

    @Test
    fun `unknown, short and empty input is not an image`() {
        assertNull(detectImageType(ByteArray(0)))
        assertNull(detectImageType(byteArrayOf(0xFF.toByte(), 0xD8.toByte())))
        assertNull(detectImageType("<html>not an image</html>".toByteArray()))
        assertNull(detectImageType(byteArrayOf(0x47)))
    }

    @Test
    fun `RIFF that is not WEBP is rejected`() {
        assertNull(detectImageType("RIFF".toByteArray() + ByteArray(4) + "WAVE".toByteArray() + ByteArray(8)))
    }

    @Test
    fun `file name keeps the url's base name, drops query and extension, and is capped`() {
        assertEquals("photo.jpg", shareFileName("https://icdn.lenta.ru/images/a/photo.jpeg?x=1", SharedImageType("jpg", "image/jpeg")))
        assertEquals("image.png", shareFileName("https://icdn.lenta.ru/", SharedImageType("png", "image/png")))
        assertEquals("a".repeat(40) + ".webp", shareFileName("https://h/" + "a".repeat(80) + ".webp", SharedImageType("webp", "image/webp")))
    }

    @Test
    fun `file name cannot escape the share directory`() {
        val name = shareFileName("https://h/..%2F..%2Fx", SharedImageType("jpg", "image/jpeg"))
        assertTrue(name, Regex("[A-Za-z0-9_-]+\\.jpg").matches(name))
    }
}

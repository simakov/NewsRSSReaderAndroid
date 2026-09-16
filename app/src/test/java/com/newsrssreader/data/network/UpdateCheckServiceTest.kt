package com.newsrssreader.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric is required here because UpdateCheckService.parse() uses org.json.JSONObject,
// which throws RuntimeException on a plain local JVM (see CLAUDE.md's dependency philosophy
// note) — same reason LentaArticleParserTest needs it.
@RunWith(RobolectricTestRunner::class)
class UpdateCheckServiceTest {
    private val sampleJson = """
        {
          "tag_name": "v1.4.0",
          "body": "release notes text",
          "assets": [
            {"name": "other.txt", "browser_download_url": "https://example.com/other.txt"},
            {"name": "NewsRSSReader-v1.4.0.apk", "browser_download_url": "https://example.com/NewsRSSReader-v1.4.0.apk"}
          ]
        }
    """.trimIndent()

    @Test
    fun `parse extracts tag, notes, and the apk asset's download url`() {
        val release = UpdateCheckService.parse(sampleJson)
        assertEquals("v1.4.0", release?.tag)
        assertEquals("release notes text", release?.notes)
        assertEquals("https://example.com/NewsRSSReader-v1.4.0.apk", release?.downloadUrl)
    }

    @Test
    fun `parse returns null when no apk asset is present`() {
        val json = """
            {"tag_name": "v1.4.0", "body": "", "assets": [
              {"name": "source.zip", "browser_download_url": "https://example.com/source.zip"}
            ]}
        """.trimIndent()
        assertNull(UpdateCheckService.parse(json))
    }

    @Test
    fun `parse returns null when tag_name is missing`() {
        val json = """{"body": "", "assets": []}"""
        assertNull(UpdateCheckService.parse(json))
    }
}

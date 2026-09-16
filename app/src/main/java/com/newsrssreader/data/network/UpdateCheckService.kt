package com.newsrssreader.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

data class UpdateRelease(
    val tag: String,
    val notes: String,
    val downloadUrl: String,
)

interface UpdateChecker {
    suspend fun fetchLatestRelease(): UpdateRelease?
}

object UpdateCheckService : UpdateChecker {
    // Public repo, no auth needed for a GET on releases/latest.
    private const val API_URL =
        "https://api.github.com/repos/simakov/NewsRSSReaderAndroid/releases/latest"

    private val client = OkHttpClient()

    override suspend fun fetchLatestRelease(): UpdateRelease? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(API_URL).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected response ${response.code} for $API_URL")
            }
            val body = response.body?.string() ?: return@use null
            parse(body)
        }
    }

    // Exposed (not private) so UpdateCheckServiceTest can verify parsing without a real network
    // call — same pattern as FeedParser.parse() being tested directly from FeedParserTest.
    internal fun parse(body: String): UpdateRelease? {
        val json = JSONObject(body)
        val tag = json.optString("tag_name")
        if (tag.isBlank()) return null
        val notes = json.optString("body")
        val assets = json.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val url = assets.getJSONObject(i).optString("browser_download_url")
            if (url.endsWith(".apk")) {
                return UpdateRelease(tag, notes, url)
            }
        }
        return null
    }
}

package com.newsrssreader.data.network

import com.newsrssreader.data.model.NewsItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

enum class FeedSource(val path: String) {
    TOP7("top7"),
    LAST24("last24"),
    ALL("news"),
}

interface FeedFetcher {
    suspend fun fetchFeed(source: FeedSource, category: String? = null): List<NewsItem>
}

object LentaFeedService : FeedFetcher {
    private const val BASE_URL = "https://lenta.ru/rss"

    // Verified against LentaFeedService.swift — keep in sync if the iOS source changes.
    val categories: LinkedHashMap<String, String> = linkedMapOf(
        "russia" to "Россия",
        "world" to "Мир",
        "ussr" to "Бывший СССР",
        "economics" to "Экономика",
        "forces" to "Силовые структуры",
        "science" to "Наука и техника",
        "culture" to "Культура",
        "sport" to "Спорт",
        "media" to "Интернет и СМИ",
        "style" to "Ценности",
        "travel" to "Путешествия",
        "life" to "Из жизни",
        "realty" to "Среда обитания",
        "wellness" to "Забота о себе",
        "pobeda80" to "Победа",
    )

    private val client = OkHttpClient()

    override suspend fun fetchFeed(source: FeedSource, category: String?): List<NewsItem> =
        withContext(Dispatchers.IO) {
            val url = buildString {
                append(BASE_URL).append('/').append(source.path)
                if (!category.isNullOrEmpty()) append('/').append(category)
            }
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@use emptyList()
                FeedParser.parse(body)
            }
        }
}

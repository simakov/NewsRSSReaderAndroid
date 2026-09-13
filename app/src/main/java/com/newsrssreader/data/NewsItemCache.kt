package com.newsrssreader.data

import com.newsrssreader.data.model.NewsItem

/**
 * Simple in-memory holder that lets Home/Category screens hand a full [NewsItem] off to the
 * article detail screen without serializing it through a Navigation-Compose route argument.
 *
 * Populated by whichever screen the user tapped a row on (`put`), then read back by
 * `ArticleDetailScreen` (Task 13) using the id passed through the navigation route (`get`).
 */
object NewsItemCache {
    private val map = mutableMapOf<String, NewsItem>()

    fun put(item: NewsItem) {
        item.id.let { map[it] = item }
    }

    fun get(id: String): NewsItem? = map[id]
}

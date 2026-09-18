package com.newsrssreader.data

import com.newsrssreader.data.model.NewsItem

/**
 * Remembers the order of the feed the user was looking at when they opened an article, so the
 * article screen can walk forward through it (pull up past the bottom of an article to move to
 * the next one) without knowing which screen it was opened from.
 *
 * Only ids are kept here; the items themselves live in [NewsItemCache], which the article route
 * already uses to resolve its `article/{id}` argument. Like that cache this is plain in-memory
 * main-thread state — it is a navigation convenience, not a source of truth, and `next` simply
 * returns null once it has been lost (e.g. after process death).
 */
object NewsFeedContext {
    private var ids: List<String> = emptyList()

    /** Records [items] as the list the user is browsing, in display order. */
    fun set(items: List<NewsItem>) {
        items.forEach(NewsItemCache::put)
        ids = items.map { it.id }
    }

    /** The item after [afterId] in the recorded list, or null if it is the last one / unknown. */
    fun next(afterId: String): NewsItem? {
        val index = ids.indexOf(afterId)
        if (index < 0 || index == ids.lastIndex) return null
        return NewsItemCache.get(ids[index + 1])
    }
}

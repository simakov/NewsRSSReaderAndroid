package com.newsrssreader.data.store

import com.newsrssreader.data.model.NewsItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes the [NewsItem] snapshot a bookmark keeps, so the bookmarks list can be rendered — and
 * an article opened from it — after the process that fetched the feed is long gone.
 *
 * Every field is stored, including ones no current screen reads: the snapshot is the only copy of
 * this item that will exist once the feed has moved on, and a lossy one would quietly limit what
 * the bookmarks screen could ever show.
 */
internal object NewsItemJson {

    fun encode(item: NewsItem): JSONObject = JSONObject()
        .put("id", item.id)
        .putOpt("title", item.title)
        .putOpt("summary", item.summary)
        .putOpt("authors", item.authors?.let(::JSONArray))
        .putOpt("link", item.link)
        .putOpt("updated", item.updated?.time)
        .putOpt("categories", item.categories?.let(::JSONArray))
        .putOpt("content", item.content)
        .putOpt("published", item.published?.time)
        .putOpt("source", item.source)
        .putOpt("rights", item.rights)
        .putOpt("image", item.image)

    /** Null when the object carries no id, which is the one field nothing downstream can do without. */
    fun decode(json: JSONObject): NewsItem? {
        val id = json.optStringOrNull("id") ?: return null
        return NewsItem(
            id = id,
            title = json.optStringOrNull("title"),
            summary = json.optStringOrNull("summary"),
            authors = json.optStringList("authors"),
            link = json.optStringOrNull("link"),
            updated = json.optDate("updated"),
            categories = json.optStringList("categories"),
            content = json.optStringOrNull("content"),
            published = json.optDate("published"),
            source = json.optStringOrNull("source"),
            rights = json.optStringOrNull("rights"),
            image = json.optStringOrNull("image"),
        )
    }
}

private fun JSONObject.optStringList(key: String): List<String>? {
    val array = optJSONArray(key) ?: return null
    return (0 until array.length()).mapNotNull { array.optString(it).ifEmpty { null } }
}

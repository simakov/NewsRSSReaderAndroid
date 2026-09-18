package com.newsrssreader.data.network

import android.util.Xml
import com.newsrssreader.data.model.NewsItem
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Parses an RSS 2.0 feed payload into a list of [NewsItem]s.
 *
 * Mirrors the field mapping of the iOS app's `LentaRSSParser.swift` exactly (RSS 2.0 only —
 * no Atom, no JSON), with one intentional Android-specific deviation: `NewsItem.id` is derived
 * from the item's `link` so it is stable across parses (iOS instead assigns a fresh random UUID
 * per parse, which Navigation-Compose route keys can't rely on).
 */
object FeedParser {

    private val pubDateFormat: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale("en", "US"))
    }

    fun parse(xml: String): List<NewsItem> {
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        val items = mutableListOf<NewsItem>()

        var eventType = parser.eventType
        var inItem = false

        var title: String? = null
        var link: String? = null
        var author: String? = null
        var description: String? = null
        var category: String? = null
        var pubDate: Date? = null
        var image: String? = null

        fun resetItemState() {
            title = null
            link = null
            author = null
            description = null
            category = null
            pubDate = null
            image = null
        }

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "item" -> {
                            inItem = true
                            resetItemState()
                        }
                        "enclosure" -> if (inItem) {
                            // Second intentional deviation from iOS parity: iOS unconditionally
                            // overwrites its captured enclosure URL on every <enclosure> tag, so a
                            // later empty-url enclosure clears a prior valid image there. This port
                            // instead keeps the last *non-empty* url, treating an enclosure with no
                            // url as carrying no information rather than as a signal to clear.
                            val url = parser.getAttributeValue(null, "url")
                            if (!url.isNullOrEmpty()) {
                                image = url
                            }
                        }
                        "title" -> if (inItem) {
                            title = parser.nextTextSafe()?.trim()
                        }
                        "link" -> if (inItem) {
                            link = parser.nextTextSafe()?.trim()
                        }
                        "author" -> if (inItem) {
                            author = parser.nextTextSafe()?.trim()
                        }
                        "description" -> if (inItem) {
                            description = parser.nextTextSafe()?.trim()
                        }
                        "category" -> if (inItem) {
                            category = parser.nextTextSafe()?.trim()
                        }
                        "pubDate" -> if (inItem) {
                            val text = parser.nextTextSafe()?.trim()
                            pubDate = text?.let { parsePubDate(it) }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "item" && inItem) {
                        inItem = false
                        items += NewsItem(
                            id = stableId(link),
                            title = title,
                            summary = description?.ifEmpty { null },
                            authors = author?.takeIf { it.isNotEmpty() }?.let { listOf(it) },
                            link = link,
                            updated = pubDate,
                            categories = category?.takeIf { it.isNotEmpty() }?.let { listOf(it) },
                            content = null,
                            published = pubDate,
                            source = null,
                            rights = null,
                            image = image,
                        )
                    }
                }
            }
            eventType = parser.next()
        }

        // Lenta.ru's feeds occasionally carry the same article twice (verified live: the "all"
        // feed served two adjacent <item>s with an identical <link>). Since `id` is derived from
        // `link`, that made the news list hand LazyColumn two items with the same key, which
        // throws `IllegalArgumentException: Key "..." was already used` as soon as both
        // duplicates are on screen together — i.e. the app crashed mid-scroll. Dropping the
        // repeats here (keeping the first occurrence) fixes every list at once, and a feed that
        // lists one story twice has nothing extra to show anyway.
        return items.distinctBy { it.id }
    }

    private fun stableId(link: String?): String {
        if (link.isNullOrBlank()) {
            return UUID.randomUUID().toString()
        }
        // `link` is already a natural stable key; hashing it is a lossy compression that accepts
        // a negligible (but nonzero) collision probability in exchange for a short id. If that
        // ever becomes a real concern, `link` itself can be used directly as the id instead.
        return link.hashCode().toString()
    }

    private fun parsePubDate(text: String): Date? {
        return try {
            pubDateFormat.get()!!.parse(text)
        } catch (e: Exception) {
            null
        }
    }

    /** [XmlPullParser.nextText] wrapper that also handles an already-empty element gracefully. */
    private fun XmlPullParser.nextTextSafe(): String? {
        return try {
            nextText()
        } catch (e: Exception) {
            null
        }
    }
}

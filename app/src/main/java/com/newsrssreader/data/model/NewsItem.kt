package com.newsrssreader.data.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class NewsItem(
    val id: String,
    val title: String? = null,
    val summary: String? = null,
    val authors: List<String>? = null,
    val link: String? = null,
    val updated: Date? = null,
    val categories: List<String>? = null,
    val content: String? = null,
    val published: Date? = null,
    val source: String? = null,
    val rights: String? = null,
    val image: String? = null,
) {
    // Assumes Locale.getDefault() resolves to a Gregorian calendar (matches the iOS
    // counterpart's assumption); not hardcoded to Locale.US since the patterns are numeric-only.
    fun publishedDate(): String {
        val published = this.published ?: return ""
        val now = Calendar.getInstance()
        val pubCal = Calendar.getInstance().apply { time = published }
        val sameDay = now.get(Calendar.YEAR) == pubCal.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == pubCal.get(Calendar.DAY_OF_YEAR)
        val pattern = if (sameDay) "HH:mm" else "d.MM HH:mm"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(published)
    }

    // Used by the article detail header, which always shows a bare "HH:mm" time (never the
    // numeric "d.MM" form publishedDate() falls back to for older items) followed by a
    // Russian-language long date, e.g. "14:32, 15 сентября 2026".
    fun publishedTimeAndRuDate(): String {
        val published = this.published ?: return ""
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(published)
        val ruDate = SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(published)
        return "$time, $ruDate"
    }

    companion object {
        val sample = NewsItem(
            id = "sample",
            title = "Sample headline text for placeholder rows",
            published = Date(),
            image = null,
            link = "https://lenta.ru",
        )
    }
}

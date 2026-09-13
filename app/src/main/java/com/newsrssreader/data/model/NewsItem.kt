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
    fun publishedDate(): String {
        val published = this.published ?: return ""
        val now = Calendar.getInstance()
        val pubCal = Calendar.getInstance().apply { time = published }
        val sameDay = now.get(Calendar.YEAR) == pubCal.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == pubCal.get(Calendar.DAY_OF_YEAR)
        val pattern = if (sameDay) "HH:mm" else "d.MM HH:mm"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(published)
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

package com.newsrssreader.data.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Per-thread formatting scratch space for [NewsItem.publishedDate].
 *
 * `publishedDate()` runs during composition for every visible row of the news list, so during a
 * fling it is called several times per frame. Building a fresh `SimpleDateFormat` there (pattern
 * compilation plus a `DateFormatSymbols` lookup) and two fresh `Calendar`s (each cloning the
 * default `TimeZone`) is pure allocation churn on the scroll hot path, so all four are cached.
 *
 * `SimpleDateFormat` and `Calendar` are mutable and not thread-safe, hence the `ThreadLocal`.
 * The cache is keyed on the default locale *and* time zone, and rebuilt when either changes, so
 * a runtime change to one still takes effect — a locale or time-zone change does not restart the
 * process, and the previous code picked both up implicitly by re-reading them on every call.
 */
private class DateFormatting(val locale: Locale, val timeZone: TimeZone) {
    val sameDayFormat: SimpleDateFormat =
        SimpleDateFormat("HH:mm", locale).also { it.timeZone = timeZone }
    val otherDayFormat: SimpleDateFormat =
        SimpleDateFormat("d.MM HH:mm", locale).also { it.timeZone = timeZone }
    val nowCalendar: Calendar = Calendar.getInstance(timeZone, locale)
    val publishedCalendar: Calendar = Calendar.getInstance(timeZone, locale)
}

private val dateFormattingCache = ThreadLocal<DateFormatting>()

private fun dateFormatting(): DateFormatting {
    val locale = Locale.getDefault()
    val timeZone = TimeZone.getDefault()
    val cached = dateFormattingCache.get()
    if (cached != null && cached.locale == locale && cached.timeZone == timeZone) return cached
    return DateFormatting(locale, timeZone).also { dateFormattingCache.set(it) }
}

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
        val formatting = dateFormatting()
        val now = formatting.nowCalendar.apply { timeInMillis = System.currentTimeMillis() }
        val pubCal = formatting.publishedCalendar.apply { time = published }
        val sameDay = now.get(Calendar.YEAR) == pubCal.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == pubCal.get(Calendar.DAY_OF_YEAR)
        val format = if (sameDay) formatting.sameDayFormat else formatting.otherDayFormat
        return format.format(published)
    }

    // Used by the article detail header, which always shows a bare "HH:mm" time (never the
    // numeric "d.MM" form publishedDate() falls back to for older items) followed by a
    // Russian-language long date, e.g. "14:32, 15 сентября 2026". Not cached the way
    // publishedDate() is: this runs once per opened article, not once per visible list row.
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

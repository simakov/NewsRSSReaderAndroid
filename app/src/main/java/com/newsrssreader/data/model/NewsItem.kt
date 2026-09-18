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
    // Older items read "12:25, 11 октября": the time first, then the day spelled out in Russian.
    // Pinned to the Russian locale (rather than the default one, as the numeric-only sameDay
    // pattern can be) because the month name is content the app always shows in Russian, exactly
    // like the article header's `publishedTimeAndRuDate`.
    val otherDayFormat: SimpleDateFormat =
        SimpleDateFormat("HH:mm, d MMMM", RussianLocale).also { it.timeZone = timeZone }
    val nowCalendar: Calendar = Calendar.getInstance(timeZone, locale)
    val publishedCalendar: Calendar = Calendar.getInstance(timeZone, locale)
}

private val RussianLocale = Locale("ru")

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
    // Empty for an item with no date; "HH:mm" for items from today; "HH:mm, d MMMM" (e.g.
    // "12:25, 11 октября") for anything older. Assumes Locale.getDefault() resolves to a
    // Gregorian calendar (matches the iOS counterpart's assumption).
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

    // Used by the article detail header, which always shows the full date including the year
    // (unlike publishedDate(), which drops the year in the list), e.g. "14:32, 15 сентября 2026".
    // Not cached the way
    // publishedDate() is: this runs once per opened article, not once per visible list row.
    fun publishedTimeAndRuDate(): String {
        val published = this.published ?: return ""
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(published)
        val ruDate = SimpleDateFormat("d MMMM yyyy", RussianLocale).format(published)
        return "$time, $ruDate"
    }
}

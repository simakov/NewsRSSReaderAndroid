package com.newsrssreader.data.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

class NewsItemTest {

    private fun dateAt(daysAgo: Long, hour: Int, minute: Int): Date {
        val zone = ZoneId.systemDefault()
        val dt = LocalDateTime.now(zone).minusDays(daysAgo)
            .withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        return Date.from(dt.atZone(zone).toInstant())
    }

    @Test
    fun `publishedDate returns empty string when published is null`() {
        val item = NewsItem(id = "1", title = "t", published = null)
        assertEquals("", item.publishedDate())
    }

    @Test
    fun `publishedDate returns time only for today`() {
        val item = NewsItem(id = "1", title = "t", published = dateAt(0, 14, 32))
        assertEquals("14:32", item.publishedDate())
    }

    @Test
    fun `publishedDate returns date and time for other days`() {
        val zone = ZoneId.systemDefault()
        val reference = LocalDateTime.now(zone).minusDays(3)
        val date = Date.from(reference.withHour(9).withMinute(5).withSecond(0).withNano(0).atZone(zone).toInstant())
        val item = NewsItem(id = "1", title = "t", published = date)
        val expected = String.format("%d.%02d %02d:%02d", reference.dayOfMonth, reference.monthValue, 9, 5)
        assertEquals(expected, item.publishedDate())
    }
}

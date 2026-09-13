package com.newsrssreader.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FeedParserTest {
    private fun fixture(name: String) =
        javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader().readText()

    @Test
    fun `parses RSS 2 dot 0 feed items`() {
        val items = FeedParser.parse(fixture("rss_sample.xml"))
        assertEquals(3, items.size)
        assertNotNull(items.first().title)
        assertNotNull(items.first().link)
        assertNotNull(items.first().published)
        assertEquals(items.first().published, items.first().updated)
    }

    @Test
    fun `item id is stable and derived from link, not random`() {
        val items1 = FeedParser.parse(fixture("rss_sample.xml"))
        val items2 = FeedParser.parse(fixture("rss_sample.xml"))
        assertEquals(items1.first().id, items2.first().id)
    }

    @Test
    fun `missing enclosure yields null image`() {
        val items = FeedParser.parse(fixture("rss_sample.xml"))
        assertNull(items.first { it.image == null }.image)
    }

    @Test
    fun `last category tag wins when multiple are present`() {
        val items = FeedParser.parse(fixture("rss_sample.xml"))
        // The third fixture item has two <category> tags: "Спорт" then "Футбол" — last wins.
        val multiCategoryItem = items.first { it.link!!.contains("friendly_match") }
        assertEquals(listOf("Футбол"), multiCategoryItem.categories)
    }
}

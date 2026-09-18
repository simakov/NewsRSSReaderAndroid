package com.newsrssreader.data

import com.newsrssreader.data.model.NewsItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NewsFeedContextTest {

    private fun item(id: String) = NewsItem(id = id, title = "title $id")

    @Test
    fun `next returns the following item in the recorded order`() {
        NewsFeedContext.set(listOf(item("a"), item("b"), item("c")))

        assertEquals("b", NewsFeedContext.next("a")?.id)
        assertEquals("c", NewsFeedContext.next("b")?.id)
    }

    @Test
    fun `next returns null for the last item`() {
        NewsFeedContext.set(listOf(item("a"), item("b")))

        assertNull(NewsFeedContext.next("b"))
    }

    @Test
    fun `next returns null for an id that is not in the recorded list`() {
        NewsFeedContext.set(listOf(item("a"), item("b")))

        assertNull(NewsFeedContext.next("zzz"))
    }

    @Test
    fun `next returns null when nothing has been recorded`() {
        NewsFeedContext.set(emptyList())

        assertNull(NewsFeedContext.next("a"))
    }

    @Test
    fun `set replaces the previous list rather than appending to it`() {
        NewsFeedContext.set(listOf(item("a"), item("b")))
        NewsFeedContext.set(listOf(item("x"), item("y")))

        assertEquals("y", NewsFeedContext.next("x")?.id)
        assertNull(NewsFeedContext.next("a"))
    }

    @Test
    fun `set makes every item resolvable through NewsItemCache`() {
        NewsFeedContext.set(listOf(item("a"), item("b"), item("c")))

        assertEquals("title c", NewsItemCache.get("c")?.title)
    }
}

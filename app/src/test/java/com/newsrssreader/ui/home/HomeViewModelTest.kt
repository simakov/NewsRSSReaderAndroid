package com.newsrssreader.ui.home

import com.newsrssreader.data.model.NewsItem
import com.newsrssreader.data.network.FeedFetcher
import com.newsrssreader.data.network.FeedSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeFetcher(
        private val items: List<NewsItem>,
        private val shouldThrow: Boolean = false,
    ) : FeedFetcher {
        var lastSource: FeedSource? = null

        override suspend fun fetchFeed(source: FeedSource, category: String?): List<NewsItem> {
            lastSource = source
            if (shouldThrow) throw IOExceptionForTest()
            return items
        }
    }

    private class IOExceptionForTest : Exception("boom")

    @Test
    fun `first feed item becomes firstNews, rest become rssFeed`() = runTest {
        val items = (1..5).map { NewsItem(id = "$it", title = "Item $it") }
        val viewModel = HomeViewModel(FakeFetcher(items))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("1", viewModel.uiState.value.firstNews?.id)
        assertEquals(4, viewModel.uiState.value.rssFeed.size)
    }

    @Test
    fun `changeTab triggers reload with correct FeedSource per tab index`() = runTest {
        val fetcher = FakeFetcher(listOf(NewsItem(id = "1", title = "Item 1")))
        val viewModel = HomeViewModel(fetcher)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(FeedSource.TOP7, fetcher.lastSource)

        viewModel.changeTab(1)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(FeedSource.LAST24, fetcher.lastSource)

        viewModel.changeTab(2)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(FeedSource.ALL, fetcher.lastSource)

        viewModel.changeTab(0)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(FeedSource.TOP7, fetcher.lastSource)
    }

    @Test
    fun `fetch failure sets isShowError true and isLoading false`() = runTest {
        val fetcher = FakeFetcher(emptyList(), shouldThrow = true)
        val viewModel = HomeViewModel(fetcher)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isShowError)
        assertFalse(viewModel.uiState.value.isLoading)
    }
}

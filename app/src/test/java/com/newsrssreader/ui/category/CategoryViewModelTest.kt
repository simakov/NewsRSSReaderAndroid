package com.newsrssreader.ui.category

import com.newsrssreader.data.model.NewsItem
import com.newsrssreader.data.network.FeedFetcher
import com.newsrssreader.data.network.FeedSource
import com.newsrssreader.data.network.LentaFeedService
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

class CategoryViewModelTest {
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
        var lastCategory: String? = null

        override suspend fun fetchFeed(source: FeedSource, category: String?): List<NewsItem> {
            lastSource = source
            lastCategory = category
            if (shouldThrow) throw IOExceptionForTest()
            return items
        }
    }

    private class IOExceptionForTest : Exception("boom")

    @Test
    fun `category title resolves from LentaFeedService categories`() = runTest {
        val viewModel = CategoryViewModel("sport", FakeFetcher(emptyList()))
        assertEquals(LentaFeedService.categories["sport"], viewModel.uiState.value.categoryTitle)
    }

    @Test
    fun `fetched items populate news and fetcher is called with ALL and the category key`() =
        runTest {
            val items = (1..3).map { NewsItem(id = "$it", title = "Item $it") }
            val fetcher = FakeFetcher(items)
            val viewModel = CategoryViewModel("sport", fetcher)
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(FeedSource.ALL, fetcher.lastSource)
            assertEquals("sport", fetcher.lastCategory)
            assertEquals(3, viewModel.uiState.value.news.size)
            assertFalse(viewModel.uiState.value.isLoading)
            assertFalse(viewModel.uiState.value.isShowError)
        }

    @Test
    fun `fetch failure sets isShowError true and isLoading false`() = runTest {
        val fetcher = FakeFetcher(emptyList(), shouldThrow = true)
        val viewModel = CategoryViewModel("sport", fetcher)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isShowError)
        assertFalse(viewModel.uiState.value.isLoading)
    }
}

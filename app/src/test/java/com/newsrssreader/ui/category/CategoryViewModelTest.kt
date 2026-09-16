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

    @Test
    fun `refresh re-fetches the category and clears isRefreshing on success`() = runTest {
        val fetcher = FakeFetcher(listOf(NewsItem(id = "1", title = "Item 1")))
        val viewModel = CategoryViewModel("sport", fetcher)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.refresh()
        assertTrue(viewModel.uiState.value.isRefreshing)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("sport", fetcher.lastCategory)
        assertFalse(viewModel.uiState.value.isRefreshing)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `refresh failure keeps the existing feed and does not set isShowError`() = runTest {
        val items = (1..3).map { NewsItem(id = "$it", title = "Item $it") }
        val fetcher = FailAfterFirstFetcher(FakeFetcher(items))
        val viewModel = CategoryViewModel("sport", fetcher)
        dispatcher.scheduler.advanceUntilIdle()
        val itemsBeforeRefresh = viewModel.uiState.value.news

        viewModel.refresh()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isShowError)
        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals(itemsBeforeRefresh, viewModel.uiState.value.news)
    }

    @Test
    fun `refresh flags items absent from the previous feed as new`() = runTest {
        val initialItems = (1..3).map { NewsItem(id = "$it", title = "Item $it") }
        val refreshedItems = listOf(NewsItem(id = "0", title = "Breaking")) + initialItems
        val fetcher = SequencedFetcher(listOf(initialItems, refreshedItems))
        val viewModel = CategoryViewModel("sport", fetcher)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(emptySet<String>(), viewModel.uiState.value.newItemIds)

        // runCurrent (not advanceUntilIdle) - the latter would also fire the 30s highlight-clear
        // delay scheduled below, since it keeps advancing virtual time until nothing is queued.
        viewModel.refresh()
        dispatcher.scheduler.runCurrent()

        assertEquals(setOf("0"), viewModel.uiState.value.newItemIds)
    }

    @Test
    fun `new item highlight clears after 30 seconds`() = runTest {
        val initialItems = (1..3).map { NewsItem(id = "$it", title = "Item $it") }
        val refreshedItems = listOf(NewsItem(id = "0", title = "Breaking")) + initialItems
        val fetcher = SequencedFetcher(listOf(initialItems, refreshedItems))
        val viewModel = CategoryViewModel("sport", fetcher)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.refresh()
        dispatcher.scheduler.runCurrent()
        assertEquals(setOf("0"), viewModel.uiState.value.newItemIds)

        dispatcher.scheduler.advanceTimeBy(30_000)
        dispatcher.scheduler.runCurrent()
        assertEquals(emptySet<String>(), viewModel.uiState.value.newItemIds)
    }

    /** Returns each list in [responses] in order, one per call; throws once they're exhausted. */
    private class SequencedFetcher(private val responses: List<List<NewsItem>>) : FeedFetcher {
        private var callCount = 0

        override suspend fun fetchFeed(source: FeedSource, category: String?): List<NewsItem> {
            val response = responses.getOrNull(callCount) ?: throw IOExceptionForTest()
            callCount++
            return response
        }
    }

    /** Wraps a fetcher so its first call succeeds (initial load) and every later call throws. */
    private class FailAfterFirstFetcher(private val delegate: FeedFetcher) : FeedFetcher {
        private var callCount = 0

        override suspend fun fetchFeed(source: FeedSource, category: String?): List<NewsItem> {
            callCount++
            if (callCount > 1) throw IOExceptionForTest()
            return delegate.fetchFeed(source, category)
        }
    }
}

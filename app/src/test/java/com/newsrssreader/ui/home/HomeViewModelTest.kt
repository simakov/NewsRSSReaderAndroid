package com.newsrssreader.ui.home

import com.newsrssreader.data.model.NewsItem
import com.newsrssreader.data.network.FeedFetcher
import com.newsrssreader.data.network.FeedSource
import kotlinx.coroutines.CompletableDeferred
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

    @Test
    fun `refresh re-fetches the current tab and clears isRefreshing on success`() = runTest {
        val fetcher = FakeFetcher(listOf(NewsItem(id = "1", title = "Item 1")))
        val viewModel = HomeViewModel(fetcher)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.changeTab(1)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.refresh()
        assertTrue(viewModel.uiState.value.isRefreshing)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(FeedSource.LAST24, fetcher.lastSource)
        assertFalse(viewModel.uiState.value.isRefreshing)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `refresh failure keeps the existing feed and does not set isShowError`() = runTest {
        val items = (1..3).map { NewsItem(id = "$it", title = "Item $it") }
        val fetcher = FailAfterFirstFetcher(FakeFetcher(items))
        val viewModel = HomeViewModel(fetcher)
        dispatcher.scheduler.advanceUntilIdle()
        val itemsBeforeRefresh = viewModel.uiState.value.rssFeed

        viewModel.refresh()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isShowError)
        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals(itemsBeforeRefresh, viewModel.uiState.value.rssFeed)
    }

    @Test
    fun `refresh flags items absent from the previous feed as new`() = runTest {
        val initialItems = (1..3).map { NewsItem(id = "$it", title = "Item $it") }
        val refreshedItems = listOf(NewsItem(id = "0", title = "Breaking")) + initialItems
        val fetcher = SequencedFetcher(listOf(initialItems, refreshedItems))
        val viewModel = HomeViewModel(fetcher)
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
        val viewModel = HomeViewModel(fetcher)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.refresh()
        dispatcher.scheduler.runCurrent()
        assertEquals(setOf("0"), viewModel.uiState.value.newItemIds)

        dispatcher.scheduler.advanceTimeBy(30_000)
        dispatcher.scheduler.runCurrent()
        assertEquals(emptySet<String>(), viewModel.uiState.value.newItemIds)
    }

    @Test
    fun `changing tabs clears any pending new-item highlight`() = runTest {
        val initialItems = (1..3).map { NewsItem(id = "$it", title = "Item $it") }
        val refreshedItems = listOf(NewsItem(id = "0", title = "Breaking")) + initialItems
        val fetcher = SequencedFetcher(listOf(initialItems, refreshedItems))
        val viewModel = HomeViewModel(fetcher)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.refresh()
        dispatcher.scheduler.runCurrent()
        assertEquals(setOf("0"), viewModel.uiState.value.newItemIds)

        viewModel.changeTab(1)
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

    /**
     * A fetcher whose response per source is gated by a [CompletableDeferred], so a test can
     * control exactly when each call resolves and simulate a slow, superseded request.
     */
    private class GatedFetcher : FeedFetcher {
        private val gates = mutableMapOf<FeedSource, CompletableDeferred<List<NewsItem>>>()

        fun gateFor(source: FeedSource): CompletableDeferred<List<NewsItem>> =
            gates.getOrPut(source) { CompletableDeferred() }

        override suspend fun fetchFeed(source: FeedSource, category: String?): List<NewsItem> =
            gateFor(source).await()
    }

    @Test
    fun `stale in-flight fetch does not overwrite a newer tab's result`() = runTest {
        val fetcher = GatedFetcher()
        val viewModel = HomeViewModel(fetcher)

        // The initial TOP7 load (triggered by init) is now suspended awaiting its gate.
        // Switch to LAST24 before it resolves - this should cancel the TOP7 job.
        viewModel.changeTab(1)
        dispatcher.scheduler.advanceUntilIdle()

        val last24Items = listOf(NewsItem(id = "last24-1", title = "Last24 Item"))
        fetcher.gateFor(FeedSource.LAST24).complete(last24Items)
        dispatcher.scheduler.advanceUntilIdle()

        // Now let the stale TOP7 fetch resolve too - it must not clobber the LAST24 state.
        val top7Items = listOf(NewsItem(id = "top7-1", title = "Top7 Item"))
        fetcher.gateFor(FeedSource.TOP7).complete(top7Items)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("last24-1", viewModel.uiState.value.firstNews?.id)
        assertEquals(1, viewModel.uiState.value.tab)
        assertFalse(viewModel.uiState.value.isShowError)
    }
}

package com.newsrssreader.ui.article

import com.newsrssreader.data.model.ArticleContent
import com.newsrssreader.data.model.ArticleContentType
import com.newsrssreader.data.model.NewsItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ArticleViewModelTest {
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
        private val html: String = "",
        private val shouldThrow: Boolean = false,
    ) : ArticleHtmlFetcher {
        var lastUrl: String? = null

        override suspend fun fetchHtml(url: String): String {
            lastUrl = url
            if (shouldThrow) throw IOExceptionForTest()
            return html
        }
    }

    private class IOExceptionForTest : Exception("boom")

    private fun newsItem(link: String? = "https://lenta.ru/news/1") = NewsItem(
        id = "1",
        title = "Some title",
        link = link,
    )

    @Test
    fun `successful fetch and parse populates content and clears loading`() = runTest {
        val html = "<html><body><p class=\"content-body\"></p></body></html>"
        val fetcher = FakeFetcher(html = html)
        val viewModel = ArticleViewModel(newsItem(), fetcher, ioDispatcher = dispatcher)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("https://lenta.ru/news/1", fetcher.lastUrl)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNotNull(viewModel.uiState.value.content)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `fetch failure sets error and clears loading without content`() = runTest {
        val fetcher = FakeFetcher(shouldThrow = true)
        val viewModel = ArticleViewModel(newsItem(), fetcher, ioDispatcher = dispatcher)
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.content)
        assertNotNull(viewModel.uiState.value.error)
    }

    @Test
    fun `null link goes straight to error without attempting a fetch`() = runTest {
        val fetcher = FakeFetcher()
        val viewModel = ArticleViewModel(newsItem(link = null), fetcher)
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(fetcher.lastUrl)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.content)
        assertNotNull(viewModel.uiState.value.error)
    }

    private val savedArticle = ArticleContent(
        title = "Сохранённая статья",
        content = listOf(ArticleContentType.Paragraph("Сохранённый текст")),
    )

    @Test
    fun `fetch failure falls back to the saved bookmark body`() = runTest {
        val viewModel = ArticleViewModel(
            newsItem(),
            FakeFetcher(shouldThrow = true),
            savedBody = { savedArticle },
            ioDispatcher = dispatcher,
        )
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(savedArticle, state.content)
        // No error is surfaced: the reader got the article, just not from the network.
        assertNull(state.error)
        assertTrue(state.isOffline)
    }

    @Test
    fun `a saved body is never consulted while the network works`() = runTest {
        var consulted = false
        val html = "<html><body><p class=\"content-body\"></p></body></html>"
        val viewModel = ArticleViewModel(
            newsItem(),
            FakeFetcher(html = html),
            savedBody = { consulted = true; savedArticle },
            ioDispatcher = dispatcher,
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse("the saved copy is a fallback, not a cache", consulted)
        assertFalse(viewModel.uiState.value.isOffline)
    }

    @Test
    fun `fetch failure with nothing saved still reports the error`() = runTest {
        val viewModel = ArticleViewModel(
            newsItem(),
            FakeFetcher(shouldThrow = true),
            savedBody = { null },
            ioDispatcher = dispatcher,
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.content)
        assertNotNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isOffline)
    }

    @Test
    fun `a saved body that cannot be read is treated as no saved body`() = runTest {
        val viewModel = ArticleViewModel(
            newsItem(),
            FakeFetcher(shouldThrow = true),
            savedBody = { throw IOExceptionForTest() },
            ioDispatcher = dispatcher,
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.content)
        assertNotNull(viewModel.uiState.value.error)
    }
}

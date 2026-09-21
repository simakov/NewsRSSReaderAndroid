package com.newsrssreader.ui.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.newsrssreader.data.model.ArticleContent
import com.newsrssreader.data.model.NewsItem
import com.newsrssreader.data.parser.LentaArticleParser
import com.newsrssreader.data.store.BookmarkStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

data class ArticleUiState(
    val isLoading: Boolean = true,
    val content: ArticleContent? = null,
    val error: String? = null,
    /**
     * True when [content] came from the saved bookmark rather than from the network, so the
     * screen can say the reader is looking at a stored copy.
     */
    val isOffline: Boolean = false,
)

/**
 * Fetches the raw HTML for an article. Mirrors the [com.newsrssreader.data.network.FeedFetcher]
 * pattern from Task 4/6: a small interface so a fake can be injected in tests, with a real
 * OkHttp-backed implementation as the default used in production.
 */
interface ArticleHtmlFetcher {
    suspend fun fetchHtml(url: String): String
}

/**
 * Default, OkHttp-backed [ArticleHtmlFetcher]. Deliberately a separate, dedicated client rather
 * than reusing `LentaFeedService`'s: that client is scoped to feed-list fetching (small XML/JSON
 * payloads on `lenta.ru/rss/...`), while this one fetches full article HTML pages. There's no
 * shared configuration between them worth factoring out, and keeping them separate avoids
 * coupling article loading to feed-service internals.
 */
object HttpArticleHtmlFetcher : ArticleHtmlFetcher {
    private val client = OkHttpClient()

    override suspend fun fetchHtml(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected response ${response.code} for $url")
            }
            response.body?.string() ?: ""
        }
    }
}

/**
 * Loads and parses a single article's content for [com.newsrssreader.ui.article.ArticleDetailScreen].
 * Takes the already-resolved [NewsItem] (the caller looks it up via `NewsItemCache.get(id)`) rather
 * than an id, so this ViewModel doesn't need to know about the cache at all.
 *
 * Follows the same `runCatching` + rethrow-on-`CancellationException` pattern as
 * `HomeViewModel`/`CategoryViewModel` (Task 6/10), so cancelling the ViewModel's scope (e.g. the
 * screen being torn down mid-request) propagates normally instead of being reported as a fetch
 * error.
 */
class ArticleViewModel(
    private val newsItem: NewsItem,
    private val htmlFetcher: ArticleHtmlFetcher = HttpArticleHtmlFetcher,
    // The saved copy of a bookmarked article, used only when the network fetch fails. Injected as
    // a function so tests don't need a real BookmarkStore bound to a directory.
    private val savedBody: suspend (String) -> ArticleContent? = BookmarkStore::body,
    // Overridable so tests can pass the same `TestDispatcher` used for `Dispatchers.Main`,
    // keeping the whole fetch+parse pipeline on one deterministic virtual-time dispatcher
    // instead of racing against the real `Dispatchers.IO` thread pool, which `advanceUntilIdle()`
    // has no visibility into.
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArticleUiState())
    val uiState: StateFlow<ArticleUiState> = _uiState.asStateFlow()

    init {
        loadArticle()
    }

    private fun loadArticle() {
        val link = newsItem.link
        if (link.isNullOrEmpty()) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                content = null,
                error = "Ссылка на статью отсутствует",
            )
            return
        }

        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    val html = htmlFetcher.fetchHtml(link)
                    LentaArticleParser.parse(html, newsItem)
                }
            }
                .onSuccess { articleContent ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        content = articleContent,
                        error = null,
                    )
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    // The saved copy is a fallback, not a cache: online the reader always gets the
                    // live article, so there is no staleness to reason about, and a bookmark still
                    // opens with no connection - from the feed as well as from the bookmarks list.
                    // Without a network OkHttp fails on name resolution rather than waiting out a
                    // timeout, so this costs no perceptible delay.
                    val saved = runCatching {
                        withContext(ioDispatcher) { savedBody(newsItem.id) }
                    }.getOrNull()
                    _uiState.value = if (saved != null) {
                        _uiState.value.copy(
                            isLoading = false,
                            content = saved,
                            error = null,
                            isOffline = true,
                        )
                    } else {
                        _uiState.value.copy(
                            isLoading = false,
                            content = null,
                            error = e.message ?: "Не удалось загрузить статью",
                        )
                    }
                }
        }
    }
}

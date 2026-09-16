package com.newsrssreader.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.newsrssreader.data.model.NewsItem
import com.newsrssreader.data.network.FeedFetcher
import com.newsrssreader.data.network.FeedSource
import com.newsrssreader.data.network.LentaFeedService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// How long a pull-to-refresh's newly-arrived items stay highlighted in the list.
private const val NewItemHighlightDurationMs = 30_000L

data class HomeUiState(
    val tab: Int = 0,
    val firstNews: NewsItem? = null,
    val rssFeed: List<NewsItem> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isShowError: Boolean = false,
    val newItemIds: Set<String> = emptySet(),
)

class HomeViewModel(
    private val feedService: FeedFetcher = LentaFeedService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadFeeds(FeedSource.TOP7)
    }

    fun changeTab(tab: Int) {
        highlightClearJob?.cancel()
        _uiState.value = _uiState.value.copy(
            tab = tab,
            rssFeed = emptyList(),
            isLoading = true,
            newItemIds = emptySet(),
        )
        loadFeeds(sourceForTab(tab))
    }

    // Pull-to-refresh: re-fetch the currently selected tab's feed. Unlike changeTab, the existing
    // list is kept on screen (and kept on failure) so the refresh indicator is the only visible
    // loading state - no shimmer flash, no wiping the list out from under the user.
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        loadFeeds(sourceForTab(_uiState.value.tab), isRefresh = true)
    }

    private fun sourceForTab(tab: Int) = when (tab) {
        0 -> FeedSource.TOP7
        1 -> FeedSource.LAST24
        else -> FeedSource.ALL
    }

    private var loadJob: Job? = null
    private var highlightClearJob: Job? = null

    private fun loadFeeds(source: FeedSource, isRefresh: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val previousIds = allIds(_uiState.value.firstNews, _uiState.value.rssFeed)
            runCatching { feedService.fetchFeed(source) }
                .onSuccess { feed ->
                    val newItemIds = if (isRefresh) {
                        allIds(feed.firstOrNull(), feed.drop(1)) - previousIds
                    } else {
                        emptySet()
                    }
                    _uiState.value = _uiState.value.copy(
                        firstNews = feed.firstOrNull(),
                        rssFeed = feed.drop(1),
                        isLoading = false,
                        isRefreshing = false,
                        isShowError = false,
                        newItemIds = newItemIds,
                    )
                    highlightClearJob?.cancel()
                    if (newItemIds.isNotEmpty()) {
                        highlightClearJob = viewModelScope.launch {
                            delay(NewItemHighlightDurationMs)
                            _uiState.value = _uiState.value.copy(newItemIds = emptySet())
                        }
                    }
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isShowError = if (isRefresh) _uiState.value.isShowError else true,
                    )
                }
        }
    }

    private fun allIds(firstNews: NewsItem?, rest: List<NewsItem>): Set<String> =
        (listOfNotNull(firstNews) + rest).map { it.id }.toSet()
}

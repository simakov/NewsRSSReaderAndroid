package com.newsrssreader.ui.category

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

data class CategoryUiState(
    val news: List<NewsItem> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isShowError: Boolean = false,
    val categoryTitle: String = "",
    val newItemIds: Set<String> = emptySet(),
)

/**
 * Feed state for a single fixed category screen. Unlike [com.newsrssreader.ui.home.HomeViewModel],
 * there is no tab switching here - the category is fixed for the lifetime of this ViewModel, so
 * there's only ever one fetch in flight (the initial load). That means the
 * cancel-the-previous-job-before-starting-a-new-one bookkeeping `HomeViewModel` needs to guard
 * against a stale tab's response clobbering a newer tab's state mostly does not apply here, since
 * the category is fixed - the one exception is pull-to-refresh re-fetching the same category,
 * which still cancels an in-flight fetch so a slow initial load can't overwrite a later refresh's
 * result. The fetch is still wrapped in `runCatching` with the `CancellationException` rethrow so
 * cancelling the ViewModel's scope (e.g. the screen being torn down mid-request) propagates
 * normally instead of being reported as a fetch error.
 */
class CategoryViewModel(
    private val categoryKey: String,
    private val feedService: FeedFetcher = LentaFeedService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CategoryUiState(categoryTitle = LentaFeedService.categories[categoryKey].orEmpty()),
    )
    val uiState: StateFlow<CategoryUiState> = _uiState.asStateFlow()

    init {
        loadFeed()
    }

    // Pull-to-refresh: re-fetch the category feed, keeping the existing list on screen (and on
    // failure) so the refresh indicator is the only visible loading state.
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        loadFeed(isRefresh = true)
    }

    private var loadJob: Job? = null
    private var highlightClearJob: Job? = null

    private fun loadFeed(isRefresh: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val previousIds = _uiState.value.news.map { it.id }.toSet()
            runCatching { feedService.fetchFeed(FeedSource.ALL, category = categoryKey) }
                .onSuccess { feed ->
                    val newItemIds = if (isRefresh) {
                        feed.map { it.id }.toSet() - previousIds
                    } else {
                        emptySet()
                    }
                    _uiState.value = _uiState.value.copy(
                        news = feed,
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
}

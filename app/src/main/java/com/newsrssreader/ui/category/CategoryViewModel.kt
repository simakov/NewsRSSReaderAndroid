package com.newsrssreader.ui.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.newsrssreader.data.model.NewsItem
import com.newsrssreader.data.network.FeedFetcher
import com.newsrssreader.data.network.FeedSource
import com.newsrssreader.data.network.LentaFeedService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CategoryUiState(
    val news: List<NewsItem> = emptyList(),
    val isLoading: Boolean = true,
    val isShowError: Boolean = false,
    val categoryTitle: String = "",
)

/**
 * Feed state for a single fixed category screen. Unlike [com.newsrssreader.ui.home.HomeViewModel],
 * there is no tab switching here - the category is fixed for the lifetime of this ViewModel, so
 * there's only ever one fetch in flight (the initial load). That means the
 * cancel-the-previous-job-before-starting-a-new-one bookkeeping `HomeViewModel` needs to guard
 * against a stale tab's response clobbering a newer tab's state does not apply here: there is no
 * "newer" request that could race this one. The fetch is still wrapped in `runCatching` with the
 * `CancellationException` rethrow so cancelling the ViewModel's scope (e.g. the screen being torn
 * down mid-request) propagates normally instead of being reported as a fetch error.
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

    private fun loadFeed() {
        viewModelScope.launch {
            runCatching { feedService.fetchFeed(FeedSource.ALL, category = categoryKey) }
                .onSuccess { feed ->
                    _uiState.value = _uiState.value.copy(
                        news = feed,
                        isLoading = false,
                        isShowError = false,
                    )
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _uiState.value = _uiState.value.copy(isLoading = false, isShowError = true)
                }
        }
    }
}

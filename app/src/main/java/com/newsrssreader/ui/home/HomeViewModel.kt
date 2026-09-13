package com.newsrssreader.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.newsrssreader.data.model.NewsItem
import com.newsrssreader.data.network.FeedFetcher
import com.newsrssreader.data.network.FeedSource
import com.newsrssreader.data.network.LentaFeedService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val tab: Int = 0,
    val firstNews: NewsItem? = null,
    val rssFeed: List<NewsItem> = emptyList(),
    val isLoading: Boolean = true,
    val isShowError: Boolean = false,
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
        _uiState.value = _uiState.value.copy(tab = tab, rssFeed = emptyList(), isLoading = true)
        val source = when (tab) {
            0 -> FeedSource.TOP7
            1 -> FeedSource.LAST24
            else -> FeedSource.ALL
        }
        loadFeeds(source)
    }

    private fun loadFeeds(source: FeedSource) {
        viewModelScope.launch {
            runCatching { feedService.fetchFeed(source) }
                .onSuccess { feed ->
                    _uiState.value = _uiState.value.copy(
                        firstNews = feed.firstOrNull(),
                        rssFeed = feed.drop(1),
                        isLoading = false,
                        isShowError = false,
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false, isShowError = true)
                }
        }
    }
}

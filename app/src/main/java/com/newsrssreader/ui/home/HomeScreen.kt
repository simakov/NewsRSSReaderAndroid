package com.newsrssreader.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.newsrssreader.data.NewsItemCache
import com.newsrssreader.ui.components.NewsRow
import com.newsrssreader.ui.components.NewsRowPlaceholder
import com.newsrssreader.ui.components.NewsTabs
import com.newsrssreader.ui.components.NewsTop
import com.newsrssreader.ui.components.TopPanel

/**
 * Home feed screen: hero banner + top7/last24/all tabs + the selected feed's list, matching the
 * iOS `Home` view. Placeholders are rendered without dividers between them (per the design doc),
 * while real rows get a `HorizontalDivider()` between each pair.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onMenuClick: () -> Unit,
    onArticleClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        TopPanel(onMenuClick = onMenuClick)
        NewsTop(item = uiState.firstNews)
        NewsTabs(selectedTab = uiState.tab, onTabSelected = viewModel::changeTab)

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (uiState.isLoading) {
                items(7) {
                    NewsRowPlaceholder()
                }
            } else {
                itemsIndexed(uiState.rssFeed) { index, item ->
                    NewsRow(
                        item = item,
                        modifier = Modifier.clickable {
                            NewsItemCache.put(item)
                            onArticleClick(item.id)
                        },
                    )
                    if (index != uiState.rssFeed.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

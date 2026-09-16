package com.newsrssreader.ui.category

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.newsrssreader.data.NewsItemCache
import com.newsrssreader.ui.components.NewsRow
import com.newsrssreader.ui.components.NewsRowPlaceholder
import com.newsrssreader.ui.components.TopPanel
import com.newsrssreader.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * Category feed screen: the same shared header as [com.newsrssreader.ui.home.HomeScreen] (menu
 * button + logo, matching iOS's `ContentView` header which is rendered above both `Home` and
 * `CategoryView` so the hamburger menu is reachable from a category feed too, not just Home),
 * followed by a title header for the selected category and the same shimmer-while-loading /
 * real-rows-with-dividers list pattern as `HomeScreen`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    categoryKey: String,
    viewModel: CategoryViewModel = viewModel { CategoryViewModel(categoryKey) },
    onMenuClick: () -> Unit,
    onArticleClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.colors.blackInversed),
    ) {
        TopPanel(
            onMenuClick = onMenuClick,
            onLogoClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
        )
        Text(
            text = uiState.categoryTitle,
            style = AppTheme.type.categoryHeader,
            color = AppTheme.colors.black,
            modifier = Modifier.padding(top = 10.dp, start = 10.dp),
        )

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = WindowInsets.navigationBars.asPaddingValues(),
            ) {
                if (uiState.isLoading) {
                    items(7) {
                        NewsRowPlaceholder()
                    }
                } else {
                    itemsIndexed(uiState.news, key = { _, item -> item.id }) { index, item ->
                        NewsRow(
                            item = item,
                            modifier = Modifier.clickable {
                                NewsItemCache.put(item)
                                onArticleClick(item.id)
                            },
                            isHighlighted = item.id in uiState.newItemIds,
                        )
                        if (index != uiState.news.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

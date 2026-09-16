package com.newsrssreader.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.newsrssreader.data.NewsItemCache
import com.newsrssreader.ui.components.NewsRow
import com.newsrssreader.ui.components.NewsRowPlaceholder
import com.newsrssreader.ui.components.NewsTabs
import com.newsrssreader.ui.components.NewsTop
import com.newsrssreader.ui.components.TopPanel
import com.newsrssreader.ui.theme.AppTheme
import kotlinx.coroutines.launch

// Slightly taller than the shared default (used by CategoryScreen) — the home screen's top bar
// is the app's primary "brand" header, so it gets a bit more visual weight.
private val HomeTopPanelHeight = 44.dp

/**
 * Home feed screen: hero banner + top7/last24/all tabs + the selected feed's list, matching the
 * iOS `Home` view. Placeholders are rendered without dividers between them (per the design doc),
 * while real rows get a `HorizontalDivider()` between each pair.
 *
 * `TopPanel` is a fixed header above the scroll area; the hero banner, the tabs, and the list all
 * scroll together as one `LazyColumn` so the hero/tabs aren't pinned above the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onMenuClick: () -> Unit,
    onArticleClick: (String) -> Unit,
    showUpdateBadge: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Reset scroll to the top whenever the selected tab changes (also fires harmlessly on
    // initial composition, when the list is already at position 0).
    LaunchedEffect(uiState.tab) {
        listState.scrollToItem(0)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.colors.blackInversed),
    ) {
        TopPanel(
            onMenuClick = onMenuClick,
            onLogoClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
            contentHeight = HomeTopPanelHeight,
            showUpdateBadge = showUpdateBadge,
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
                item {
                    NewsTop(
                        item = uiState.firstNews,
                        onClick = {
                            uiState.firstNews?.let {
                                NewsItemCache.put(it)
                                onArticleClick(it.id)
                            }
                        },
                    )
                }
                item {
                    NewsTabs(selectedTab = uiState.tab, onTabSelected = viewModel::changeTab)
                }

                if (uiState.isLoading) {
                    items(7) {
                        NewsRowPlaceholder()
                    }
                } else {
                    itemsIndexed(uiState.rssFeed, key = { _, item -> item.id }) { index, item ->
                        NewsRow(
                            item = item,
                            modifier = Modifier.clickable {
                                NewsItemCache.put(item)
                                onArticleClick(item.id)
                            },
                            isHighlighted = item.id in uiState.newItemIds,
                        )
                        if (index != uiState.rssFeed.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

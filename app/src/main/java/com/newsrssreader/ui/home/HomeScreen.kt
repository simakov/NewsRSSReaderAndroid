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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.newsrssreader.data.NewsFeedContext
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

    // Every field of the ui state is read here, in the screen's own recomposition scope, and the
    // lazy item lambdas below capture the resulting plain values. Reading `uiState` *inside* an
    // item lambda instead would subscribe each visible row directly to the state snapshot, so any
    // unrelated change to it (the refresh flag toggling, the new-item highlight expiring after its
    // 30s window) would invalidate every visible row rather than letting the unaffected ones skip.
    val tab = uiState.tab
    val firstNews = uiState.firstNews
    val feed = uiState.rssFeed
    val newItemIds = uiState.newItemIds
    val isLoading = uiState.isLoading
    val isRefreshing = uiState.isRefreshing

    // Reset scroll to the top whenever the selected tab changes - but only on a real change.
    // This effect also runs on every (re)composition of the screen, including the one that
    // happens on the way back from an article, where Navigation-Compose has just restored the
    // saved `listState` position; scrolling to 0 unconditionally there would throw that
    // restored position away, which is exactly the "list jumps back to the top after reading an
    // article" bug. `lastTab` is saveable so the comparison survives the same restore.
    var lastTab by rememberSaveable { mutableIntStateOf(tab) }
    LaunchedEffect(tab) {
        if (tab != lastTab) {
            lastTab = tab
            listState.scrollToItem(0)
        }
    }

    // The feed in display order (hero item first), handed to NewsFeedContext when a row is
    // tapped so the article screen can walk forward through the same list.
    val orderedFeed = remember(firstNews, feed) { listOfNotNull(firstNews) + feed }

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
            isRefreshing = isRefreshing,
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
                        item = firstNews,
                        onClick = {
                            firstNews?.let {
                                NewsFeedContext.set(orderedFeed)
                                NewsItemCache.put(it)
                                onArticleClick(it.id)
                            }
                        },
                    )
                }
                item {
                    NewsTabs(selectedTab = tab, onTabSelected = viewModel::changeTab)
                }

                if (isLoading) {
                    items(7) {
                        NewsRowPlaceholder()
                    }
                } else {
                    // A leading divider skipped for the first row, rather than a trailing one
                    // guarded by `index != feed.lastIndex`: it puts a divider between exactly the
                    // same pairs of rows, but without the item lambda having to read the list.
                    itemsIndexed(feed, key = { _, item -> item.id }) { index, item ->
                        if (index > 0) {
                            HorizontalDivider()
                        }
                        NewsRow(
                            item = item,
                            modifier = Modifier.clickable {
                                NewsFeedContext.set(orderedFeed)
                                NewsItemCache.put(item)
                                onArticleClick(item.id)
                            },
                            isHighlighted = item.id in newItemIds,
                        )
                    }
                }
            }
        }
    }
}

package com.newsrssreader.ui.bookmarks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.newsrssreader.data.NewsFeedContext
import com.newsrssreader.data.NewsItemCache
import com.newsrssreader.data.store.BookmarkStore
import com.newsrssreader.ui.components.NewsRow
import com.newsrssreader.ui.components.TopPanel
import com.newsrssreader.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * The saved articles, newest first, laid out as the feed screens are so a bookmark looks like the
 * row it was saved from.
 *
 * It reads [BookmarkStore.bookmarks] directly rather than through a ViewModel of its own: the store
 * already exposes exactly this list as state, already off the main thread, so a ViewModel here
 * would only forward it.
 *
 * Read headlines are *not* dimmed here, though they are on the feed screens: by the time an article
 * is in this list it has almost certainly been read, so the dimming would mark everything and
 * distinguish nothing.
 *
 * Each row renders the bookmark's `displayItem`, whose image points at the locally saved copy, and
 * seeds `NewsItemCache` with that same item before navigating - so the article opens with its
 * picture off the disk, connection or not. [NewsFeedContext] is set to the bookmark list itself,
 * which makes pull-to-next walk through the saved articles the same way it walks a feed.
 */
@Composable
fun BookmarksScreen(
    onMenuClick: () -> Unit,
    onArticleClick: (String) -> Unit,
    showUpdateBadge: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val bookmarks by BookmarkStore.bookmarks.collectAsStateWithLifecycle()
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
            showUpdateBadge = showUpdateBadge,
        )
        Text(
            text = "Закладки",
            style = AppTheme.type.categoryHeader,
            color = AppTheme.colors.black,
            modifier = Modifier.padding(top = 10.dp, start = 10.dp),
        )

        if (bookmarks.isEmpty()) {
            EmptyBookmarks()
        } else {
            val orderedItems = bookmarks.map { it.displayItem }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = WindowInsets.navigationBars.asPaddingValues(),
            ) {
                // Leading divider skipped for the first row — see the note in HomeScreen.
                itemsIndexed(bookmarks, key = { _, bookmark -> bookmark.item.id }) { index, bookmark ->
                    if (index > 0) {
                        HorizontalDivider()
                    }
                    val item = bookmark.displayItem
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NewsRow(
                            item = item,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    NewsFeedContext.set(orderedItems)
                                    NewsItemCache.put(item)
                                    onArticleClick(item.id)
                                },
                            // Deliberately no `isRead` here, unlike the feed screens. Saving an
                            // article all but implies having read it, so on this screen almost
                            // every row would be dimmed - the distinction would carry no
                            // information and would only cost the whole list its legibility.
                        )
                        // Removal lives on the row itself rather than behind a swipe: the feed
                        // screens have no row gestures, so a swipe here would be a hidden
                        // affordance that exists on exactly one screen.
                        IconButton(onClick = { BookmarkStore.remove(item.id) }) {
                            Icon(
                                imageVector = Icons.Default.BookmarkRemove,
                                contentDescription = "Убрать из закладок",
                                tint = AppTheme.colors.gray,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyBookmarks(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = "Здесь пока ничего нет",
            style = AppTheme.type.errorTitle,
            color = AppTheme.colors.black,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Нажмите на закладку в статье, чтобы сохранить её вместе с текстом и фотографиями — она останется доступна без интернета.",
            style = AppTheme.type.imageCaption,
            color = AppTheme.colors.gray,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

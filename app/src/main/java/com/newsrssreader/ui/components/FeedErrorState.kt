package com.newsrssreader.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.newsrssreader.data.store.BookmarkStore
import com.newsrssreader.ui.theme.AppTheme

/**
 * What a feed screen shows in place of its list when the first load failed and there is nothing
 * to show: without it the screen is just empty, and there is no telling a failed load from a slow
 * one. Used by both HomeScreen and CategoryScreen, which render it as a list item so pull-to-refresh
 * keeps working over it.
 *
 * The way to the bookmarks is offered only when there are some, for the same reason the drawer
 * hides its bookmarks icon at zero: a link to an empty screen is an invitation to find nothing.
 */
@Composable
fun FeedErrorState(
    onRetry: () -> Unit,
    onBookmarksClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasBookmarks = BookmarkStore.bookmarks.collectAsStateWithLifecycle().value.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Нет соединения",
            style = AppTheme.type.errorTitle,
            color = AppTheme.colors.black,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Не удалось загрузить новости",
            style = AppTheme.type.meta,
            color = AppTheme.colors.gray,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Повторить",
            style = AppTheme.type.tabPill,
            color = AppTheme.colors.red,
            modifier = Modifier.clickable(onClick = onRetry).padding(8.dp),
        )
        if (hasBookmarks) {
            Text(
                text = "Сохранённые статьи",
                style = AppTheme.type.tabPill,
                color = AppTheme.colors.red,
                modifier = Modifier.clickable(onClick = onBookmarksClick).padding(8.dp),
            )
        }
    }
}

package com.newsrssreader.ui.category

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.newsrssreader.data.NewsItemCache
import com.newsrssreader.ui.components.NewsRow
import com.newsrssreader.ui.components.NewsRowPlaceholder
import com.newsrssreader.ui.theme.AppTheme

/**
 * Category feed screen: a title header for the selected category, followed by the same
 * shimmer-while-loading / real-rows-with-dividers list pattern as [com.newsrssreader.ui.home.HomeScreen].
 */
@Composable
fun CategoryScreen(
    categoryKey: String,
    viewModel: CategoryViewModel = viewModel { CategoryViewModel(categoryKey) },
    onArticleClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = uiState.categoryTitle,
            style = AppTheme.type.categoryHeader,
            color = AppTheme.colors.black,
            modifier = Modifier.padding(top = 10.dp, start = 10.dp),
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (uiState.isLoading) {
                items(7) {
                    NewsRowPlaceholder()
                }
            } else {
                itemsIndexed(uiState.news) { index, item ->
                    NewsRow(
                        item = item,
                        modifier = Modifier.clickable {
                            NewsItemCache.put(item)
                            onArticleClick(item.id)
                        },
                    )
                    if (index != uiState.news.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

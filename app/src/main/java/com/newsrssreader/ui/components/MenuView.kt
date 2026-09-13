package com.newsrssreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.newsrssreader.data.network.LentaFeedService
import com.newsrssreader.ui.theme.AppTheme

/**
 * Static content of the category drawer, matching the iOS `MenuView`. Presentation (slide-in
 * animation, overlay scrim) is left to the caller (Task 13's MainActivity wiring) — this
 * Composable only renders the full-height panel content given the current selection.
 */
@Composable
fun MenuView(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onDismiss, modifier = Modifier.padding(start = 20.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close menu",
                    tint = AppTheme.colors.white,
                )
            }
        }

        HorizontalDivider(
            color = AppTheme.colors.gray,
            modifier = Modifier.padding(vertical = 10.dp),
        )

        MenuItem(
            title = "Главная",
            selected = selectedCategory == "",
            onClick = { onCategorySelected("") },
        )
        LentaFeedService.categories.forEach { (key, title) ->
            MenuItem(
                title = title,
                selected = selectedCategory == key,
                onClick = { onCategorySelected(key) },
            )
        }
    }
}

@Composable
private fun MenuItem(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 20.dp)
                .background(if (selected) AppTheme.colors.red else AppTheme.colors.background),
        )
        Text(
            text = title,
            style = if (selected) AppTheme.type.menuItemSelected else AppTheme.type.menuItemUnselected,
            color = if (selected) AppTheme.colors.red else AppTheme.colors.white,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

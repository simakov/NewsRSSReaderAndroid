package com.newsrssreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.newsrssreader.ui.theme.AppTheme

private val tabLabels = listOf("Главное", "Последнее", "Все")

/**
 * Pill-style tab selector for the Home feed's three sources (top7/last24/all), matching the
 * iOS `NewsTabs` view. Labels are uppercased at render time via `.uppercase()` (rather than
 * being stored uppercase in [tabLabels]) so the underlying strings stay future-relabeling
 * friendly, per the design doc's cross-cutting note.
 */
@Composable
fun NewsTabs(selectedTab: Int, onTabSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabLabels.forEachIndexed { index, label ->
            val text = label.uppercase()
            if (index == selectedTab) {
                Text(
                    text = text,
                    style = AppTheme.type.tabPill,
                    color = AppTheme.colors.background,
                    modifier = Modifier
                        .background(AppTheme.colors.lightGrey, RoundedCornerShape(17.dp))
                        .clickable { onTabSelected(index) }
                        .padding(8.dp),
                )
            } else {
                Text(
                    text = text,
                    style = AppTheme.type.tabPill,
                    color = AppTheme.colors.black,
                    modifier = Modifier.clickable { onTabSelected(index) },
                )
            }
        }
    }
}

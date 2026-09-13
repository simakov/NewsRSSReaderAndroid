package com.newsrssreader.ui.components.article

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.newsrssreader.data.model.ArticleContentType
import com.newsrssreader.ui.theme.AppTheme

@Composable
fun SubheadingBlock(block: ArticleContentType.Subheading, modifier: Modifier = Modifier) {
    Text(
        text = block.text,
        style = AppTheme.type.subheading.withExtraLineSpacing(),
        color = AppTheme.colors.black,
        modifier = modifier.padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 8.dp),
    )
}

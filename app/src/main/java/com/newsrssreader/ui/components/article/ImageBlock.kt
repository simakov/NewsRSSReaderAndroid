package com.newsrssreader.ui.components.article

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.newsrssreader.data.model.ArticleContentType
import com.newsrssreader.ui.theme.AppTheme

/**
 * An in-article image with optional caption + credit. Credit is rendered italic at this call
 * site rather than baked into `AppType.imageCredit`, since italics are specific to this one
 * usage of that style.
 */
@Composable
fun ImageBlock(block: ArticleContentType.Image, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AsyncImage(
            model = block.url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .background(AppTheme.colors.gray.copy(alpha = 0.2f)),
        )

        if (block.caption != null || block.credit != null) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (block.caption != null) {
                    Text(
                        text = block.caption,
                        style = AppTheme.type.imageCaption,
                        color = AppTheme.colors.gray,
                    )
                }
                if (block.credit != null) {
                    Text(
                        text = block.credit,
                        style = AppTheme.type.imageCredit,
                        color = AppTheme.colors.gray,
                        fontStyle = FontStyle.Italic,
                    )
                }
            }
        }
    }
}

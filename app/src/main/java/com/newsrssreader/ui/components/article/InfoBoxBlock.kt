package com.newsrssreader.ui.components.article

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.newsrssreader.data.model.ArticleContentType
import com.newsrssreader.ui.theme.AppTheme

/**
 * An info box: a thin red accent bar above and below a block of body text, on a light-grey
 * rounded background.
 */
@Composable
fun InfoBoxBlock(block: ArticleContentType.InfoBox, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .background(
                color = AppTheme.colors.lightGrey.copy(alpha = 0.2f),
                shape = RoundedCornerShape(4.dp),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(AppTheme.colors.red),
        )
        Text(
            text = block.text,
            style = AppTheme.type.infoBox.withExtraLineSpacing(),
            color = AppTheme.colors.black,
            modifier = Modifier.padding(16.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(AppTheme.colors.red),
        )
    }
}

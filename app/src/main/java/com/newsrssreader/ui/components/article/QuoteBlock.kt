package com.newsrssreader.ui.components.article

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newsrssreader.data.model.ArticleContentType
import com.newsrssreader.ui.theme.AppTheme

/**
 * A pull quote: a large red "«" glyph beside the quote text, with an optional author name +
 * description below, all on a rounded light-grey box.
 *
 * Nesting per the design doc: the tinted/rounded box carries its own 16dp internal padding, and
 * the whole block additionally gets 16dp horizontal / 12dp vertical outer margin around that box.
 */
@Composable
fun QuoteBlock(block: ArticleContentType.Quote, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Column(
            modifier = Modifier
                .background(
                    color = AppTheme.colors.lightGrey.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "«",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.red,
                    modifier = Modifier.offset(y = (-8).dp),
                )
                Text(
                    text = block.text,
                    style = AppTheme.type.leadParagraph.withExtraLineSpacing(),
                    color = AppTheme.colors.black,
                    modifier = Modifier.weight(1f),
                )
            }

            if (block.authorName.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = block.authorName,
                        style = AppTheme.type.quoteAuthorName,
                        color = AppTheme.colors.black,
                    )
                    if (block.authorDescription != null) {
                        Text(
                            text = block.authorDescription,
                            style = AppTheme.type.quoteAuthorDescription,
                            color = AppTheme.colors.gray,
                        )
                    }
                }
            }
        }
    }
}

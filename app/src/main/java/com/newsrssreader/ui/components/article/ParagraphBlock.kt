package com.newsrssreader.ui.components.article

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newsrssreader.data.model.ArticleContentType
import com.newsrssreader.ui.theme.AppTheme

/**
 * Adds ~4dp of extra line spacing on top of a text style's own natural leading (mirrors SwiftUI's
 * `.lineSpacing(4)`, which adds 4pt on top of the font's normal line height rather than replacing
 * it). Compose has no built-in "natural leading" query for an arbitrary font size, so this
 * estimates it as `fontSize * 1.2` (a common default line-height multiplier) and then adds the
 * 4dp of extra spacing on top of that estimate.
 */
internal fun TextStyle.withExtraLineSpacing(): TextStyle =
    copy(lineHeight = (fontSize.value * 1.2f + 4f).sp)

/**
 * Renders a body paragraph: lead paragraphs use the larger `leadParagraph` style, regular ones
 * use `bodyParagraph`.
 */
@Composable
fun ParagraphBlock(block: ArticleContentType.Paragraph, modifier: Modifier = Modifier) {
    val baseStyle = if (block.isLead) AppTheme.type.leadParagraph else AppTheme.type.bodyParagraph
    Text(
        text = block.text,
        style = baseStyle.withExtraLineSpacing(),
        color = AppTheme.colors.black,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

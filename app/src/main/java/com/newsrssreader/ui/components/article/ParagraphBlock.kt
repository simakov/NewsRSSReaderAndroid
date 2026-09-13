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
 * Adds ~4dp of extra line spacing on top of a text style's own font size (mirrors SwiftUI's
 * `.lineSpacing(4)`, which is expressed in Compose as an explicit `lineHeight` above the font
 * size rather than a separate spacing property).
 */
internal fun TextStyle.withExtraLineSpacing(): TextStyle = copy(lineHeight = (fontSize.value + 4f).sp)

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

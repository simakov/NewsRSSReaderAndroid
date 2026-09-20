package com.newsrssreader.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.newsrssreader.data.parser.SimpleMarkdownParser
import com.newsrssreader.data.parser.SimpleMarkdownParser.Block
import com.newsrssreader.ui.theme.AppTheme

/**
 * Renders the Markdown subset produced by [SimpleMarkdownParser] (see its doc comment for what is
 * and isn't supported). Sizes are derived from [baseStyle] rather than from fixed `sp` values so
 * the same composable reads correctly whether it's used in a compact popup or at body size.
 */
@Composable
fun MarkdownText(
    markdown: String,
    baseStyle: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    linkColor: Color = AppTheme.colors.red,
) {
    val blocks = remember(markdown) { SimpleMarkdownParser.parse(markdown) }
    MarkdownBlocks(blocks, baseStyle, color, modifier, linkColor)
}

@Composable
fun MarkdownBlocks(
    blocks: List<Block>,
    baseStyle: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    linkColor: Color = AppTheme.colors.red,
) {
    Column(modifier = modifier) {
        blocks.forEachIndexed { index, block ->
            // No gap above the very first block, so the container's own padding isn't doubled.
            val topPadding = when {
                index == 0 -> 0.dp
                block is Block.Heading -> 12.dp
                block is Block.Bullet && blocks[index - 1] is Block.Bullet -> 4.dp
                else -> 8.dp
            }
            val text = block.toAnnotatedString(linkColor)
            when (block) {
                is Block.Heading -> Text(
                    text = text,
                    style = baseStyle.copy(
                        // h1/h2 (the "## Что нового" level these notes use as their top level)
                        // read as the section title; h3+ as a slightly smaller sub-title.
                        fontSize = baseStyle.fontSize * if (block.level <= 2) 1.3f else 1.12f,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = color,
                    modifier = Modifier.padding(top = topPadding),
                )

                is Block.Paragraph -> Text(
                    text = text,
                    style = baseStyle,
                    color = color,
                    modifier = Modifier.padding(top = topPadding),
                )

                is Block.Bullet -> Row(modifier = Modifier.padding(top = topPadding)) {
                    Text(text = "•", style = baseStyle, color = color)
                    Text(
                        text = text,
                        style = baseStyle,
                        color = color,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}

private fun Block.toAnnotatedString(linkColor: Color): AnnotatedString = buildAnnotatedString {
    spans.forEach { span ->
        val style = SpanStyle(
            fontWeight = if (span.bold) FontWeight.Bold else null,
            fontStyle = if (span.italic) FontStyle.Italic else null,
            fontFamily = if (span.code) FontFamily.Monospace else null,
            color = if (span.link != null) linkColor else Color.Unspecified,
            textDecoration = if (span.link != null) TextDecoration.Underline else null,
        )
        withStyle(style) { append(span.text) }
    }
}

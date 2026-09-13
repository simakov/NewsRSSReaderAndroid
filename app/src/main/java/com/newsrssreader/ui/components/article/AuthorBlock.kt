package com.newsrssreader.ui.components.article

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.newsrssreader.data.model.ArticleContentType
import com.newsrssreader.ui.theme.AppTheme

private val AuthorPhotoSize = 50.dp

/**
 * Author byline: circular photo (or a gray circle + person-icon fallback when no photo is
 * available), name, and optional job title.
 */
@Composable
fun AuthorBlock(block: ArticleContentType.Author, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(AppTheme.colors.lightGrey.copy(alpha = 0.2f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (block.photo != null) {
            AsyncImage(
                model = block.photo,
                contentDescription = null,
                modifier = Modifier
                    .size(AuthorPhotoSize)
                    .clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(AuthorPhotoSize)
                    .clip(CircleShape)
                    .background(AppTheme.colors.gray.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = AppTheme.colors.gray,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = block.name,
                style = AppTheme.type.authorName,
                color = AppTheme.colors.black,
            )
            if (block.jobTitle != null) {
                Text(
                    text = block.jobTitle,
                    style = AppTheme.type.authorJobTitle,
                    color = AppTheme.colors.gray.copy(alpha = 0.6f),
                )
            }
        }
    }
}

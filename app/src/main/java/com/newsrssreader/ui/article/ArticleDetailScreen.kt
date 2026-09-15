package com.newsrssreader.ui.article

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.newsrssreader.data.model.ArticleContentType
import com.newsrssreader.data.model.NewsItem
import com.newsrssreader.ui.components.article.AuthorBlock
import com.newsrssreader.ui.components.article.ImageBlock
import com.newsrssreader.ui.components.article.InfoBoxBlock
import com.newsrssreader.ui.components.article.ParagraphBlock
import com.newsrssreader.ui.components.article.QuoteBlock
import com.newsrssreader.ui.components.article.SubheadingBlock
import com.newsrssreader.ui.components.pinchZoomPreview
import com.newsrssreader.ui.components.shimmer
import com.newsrssreader.ui.theme.AppTheme

/**
 * Article detail screen: title/date/main-image header followed by a body that dispatches on the
 * ViewModel's loading/error/loaded state, matching iOS's minimal inline nav bar + scrollable
 * article body. `newsItem` is resolved by the caller (Task 13's NavHost) via
 * `NewsItemCache.get(id)` before this screen is constructed - the ViewModel itself only ever
 * needs the resolved [NewsItem], not the cache lookup.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleDetailScreen(
    newsItem: NewsItem,
    onBack: () -> Unit,
    onImageClick: (String) -> Unit,
    viewModel: ArticleViewModel = viewModel { ArticleViewModel(newsItem) },
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                // Title is intentionally left empty per user request — it still appears in the
                // scrollable body below, so showing it again here would be redundant.
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = AppTheme.colors.white,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val shareText = "${newsItem.title.orEmpty()}\n\n${newsItem.link.orEmpty()}"
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, null))
                    }) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = null,
                            tint = AppTheme.colors.white,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppTheme.colors.background),
                expandedHeight = TopAppBarDefaults.TopAppBarExpandedHeight * 0.7f,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .background(AppTheme.colors.blackInversed),
        ) {
            Text(
                text = newsItem.title.orEmpty(),
                style = AppTheme.type.articleTitle,
                color = AppTheme.colors.black,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
            )

            Text(
                text = newsItem.publishedDate(),
                style = AppTheme.type.meta,
                color = AppTheme.colors.gray,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
            )

            if (newsItem.image != null) {
                // Unknown until the image finishes loading; a 16:9 default matches typical
                // Lenta.ru article photo proportions so there's no jarring 0-height flash
                // before the real aspect ratio is known.
                var aspectRatio by remember(newsItem.image) { mutableFloatStateOf(16f / 9f) }

                AsyncImage(
                    model = newsItem.image,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    onState = { state ->
                        if (state is AsyncImagePainter.State.Success) {
                            val size = state.painter.intrinsicSize
                            if (size.isSpecified && size.width > 0f && size.height > 0f) {
                                aspectRatio = size.width / size.height
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspectRatio)
                        .background(AppTheme.colors.gray.copy(alpha = 0.2f))
                        .padding(bottom = 16.dp)
                        .clickable { onImageClick(newsItem.image) }
                        .pinchZoomPreview(),
                )
            }

            when {
                uiState.isLoading -> ArticleLoadingBody()
                uiState.error != null -> ArticleErrorBody(message = uiState.error.orEmpty())
                else -> uiState.content?.content?.forEach { block ->
                    ArticleContentBlock(block, onImageClick)
                }
            }
        }
    }
}

@Composable
private fun ArticleContentBlock(block: ArticleContentType, onImageClick: (String) -> Unit) {
    when (block) {
        is ArticleContentType.Paragraph -> ParagraphBlock(block)
        is ArticleContentType.Subheading -> SubheadingBlock(block)
        is ArticleContentType.Image -> ImageBlock(block, onImageClick)
        is ArticleContentType.Quote -> QuoteBlock(block)
        is ArticleContentType.Author -> AuthorBlock(block)
        is ArticleContentType.InfoBox -> InfoBoxBlock(block)
        is ArticleContentType.RelatedMaterial -> Unit // parsed but never rendered, matching iOS
    }
}

@Composable
private fun ArticleLoadingBody(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(5) {
            ShimmerParagraphPlaceholder()
        }
    }
}

/**
 * A single shimmering placeholder standing in for one paragraph while the article body is
 * loading: three gray bars (the first two full width, the third a fixed shorter width), the
 * whole block wrapped in [shimmer] so the sweep passes over all three bars together.
 */
@Composable
private fun ShimmerParagraphPlaceholder(modifier: Modifier = Modifier) {
    val barColor = AppTheme.colors.gray.copy(alpha = 0.3f)
    val barShape = RoundedCornerShape(4.dp)
    Column(
        modifier = modifier.shimmer(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(barShape)
                .background(barColor),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(barShape)
                .background(barColor),
        )
        Box(
            modifier = Modifier
                .size(width = 200.dp, height = 14.dp)
                .clip(barShape)
                .background(barColor),
        )
    }
}

@Composable
private fun ArticleErrorBody(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = AppTheme.colors.red,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = "Ошибка загрузки статьи",
            style = AppTheme.type.errorTitle,
            color = AppTheme.colors.black,
        )
        Text(
            text = message,
            style = AppTheme.type.imageCaption,
            color = AppTheme.colors.gray,
            textAlign = TextAlign.Center,
        )
    }
}

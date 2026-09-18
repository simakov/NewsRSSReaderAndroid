package com.newsrssreader.ui.article

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.newsrssreader.data.NewsFeedContext
import com.newsrssreader.data.NewsItemCache
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// How far the fully-scrolled article has to be dragged up (after resistance) before releasing
// switches to the next item in the feed.
private val PullToNextThreshold = 110.dp

// The pull follows the finger at a reduced rate, so the gesture reads as elastic overscroll
// rather than as the article having simply grown a blank tail.
private const val PullToNextResistance = 0.5f

// Duration of the article-to-article slide. Long enough to read as a deliberate transition,
// short enough not to sit between the user and the next article.
private const val ArticleSwitchDurationMs = 400

/**
 * Article detail screen: title/date/main-image header followed by a body that dispatches on the
 * ViewModel's loading/error/loaded state, matching iOS's minimal inline nav bar + scrollable
 * article body. `newsItem` is resolved by the caller (the NavHost) via `NewsItemCache.get(id)`
 * before this screen is constructed - the ViewModel itself only ever needs the resolved
 * [NewsItem], not the cache lookup.
 *
 * Dragging up past the bottom of a loaded article moves on to the next item of the feed it was
 * opened from (see [NewsFeedContext]), with the current article sliding out upwards and the next
 * one arriving from below. That happens *inside* this destination rather than as a new
 * navigation, so the back stack stays "feed -> article" however many articles deep the user
 * reads, and back always returns to the list they started from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleDetailScreen(
    newsItem: NewsItem,
    onBack: () -> Unit,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Which article is actually showing. Only the id is kept in saveable state - NewsItem isn't
    // parcelable, and NewsItemCache can resolve the id back for as long as this screen can be on
    // screen at all; if it somehow can't, falling back to the route's own item is harmless.
    var currentId by rememberSaveable(newsItem.id) { mutableStateOf(newsItem.id) }
    val currentItem = NewsItemCache.get(currentId) ?: newsItem

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val topBarBorderColor = AppTheme.colors.topBarBorder

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        val shareText = "${currentItem.title.orEmpty()}\n\n${currentItem.link.orEmpty()}"
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
                modifier = Modifier.drawBehind {
                    drawLine(
                        color = topBarBorderColor,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                },
            )
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentItem,
            contentKey = { it.id },
            transitionSpec = {
                slideInVertically(animationSpec = tween(ArticleSwitchDurationMs)) { it } togetherWith
                    slideOutVertically(animationSpec = tween(ArticleSwitchDurationMs)) { -it }
            },
            label = "article",
            modifier = Modifier.padding(innerPadding),
        ) { item ->
            ArticleBody(
                newsItem = item,
                onImageClick = onImageClick,
                onPullToNext = {
                    val next = NewsFeedContext.next(item.id)
                    if (next != null) {
                        currentId = next.id
                        true
                    } else {
                        coroutineScope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar("Список новостей закончен")
                        }
                        false
                    }
                },
            )
        }
    }
}

/**
 * One article's scrollable body, including the pull-up-for-the-next-one gesture.
 *
 * The gesture is implemented as a nested-scroll connection rather than a draggable: the article
 * is an ordinary `verticalScroll` column, so this only has to pick up the scroll delta the column
 * itself could no longer consume (i.e. the user dragging up while already at the bottom), shift
 * the whole body up by a damped fraction of it, and decide on release whether the pull was long
 * enough. [onPullToNext] returns whether a next article was actually found: if it wasn't, the
 * body springs back instead of latching the gesture off.
 */
@Composable
private fun ArticleBody(
    newsItem: NewsItem,
    onImageClick: (String) -> Unit,
    onPullToNext: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    // Keyed per article: switching to the next item builds a fresh ViewModel for it rather than
    // reloading into a shared one, so the outgoing article stays fully rendered while it slides
    // away instead of blanking into a loading state mid-animation.
    val viewModel: ArticleViewModel = viewModel(key = newsItem.id) { ArticleViewModel(newsItem) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val thresholdPx = with(LocalDensity.current) { PullToNextThreshold.toPx() }
    val pull = remember { mutableFloatStateOf(0f) }
    // While the article is still loading its "bottom" is only the bottom of the shimmer
    // placeholder, which would make the gesture fire almost immediately on open. `triggered`
    // latches it off once a switch is under way, so the outgoing body - still composed for the
    // length of the animation - can't fire a second time.
    var triggered by remember { mutableStateOf(false) }
    val gestureEnabled = rememberUpdatedState(!uiState.isLoading && !triggered)
    val pullToNext = rememberUpdatedState(onPullToNext)

    val nestedScrollConnection = remember(thresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Dragging back down pays off the accumulated pull before the article scrolls.
                if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                val delta = minOf(available.y, pull.floatValue)
                if (delta <= 0f) return Offset.Zero
                pull.floatValue -= delta
                return Offset(0f, delta)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // Anything left over after the column hit its bottom (negative = dragging up).
                if (source != NestedScrollSource.UserInput || available.y >= 0f) return Offset.Zero
                if (!gestureEnabled.value) return Offset.Zero
                pull.floatValue -= available.y * PullToNextResistance
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull.floatValue <= 0f) return Velocity.Zero
                if (pull.floatValue >= thresholdPx && gestureEnabled.value && pullToNext.value()) {
                    triggered = true
                } else {
                    animate(pull.floatValue, 0f) { value, _ -> pull.floatValue = value }
                }
                // The pull consumed the drag, so the fling that ends it belongs to it too.
                return available
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.colors.blackInversed)
            .nestedScroll(nestedScrollConnection),
    ) {
        // Revealed in the strip the pulled-up article uncovers at the bottom of the screen.
        if (pull.floatValue > 0f) {
            Text(
                text = if (pull.floatValue >= thresholdPx) {
                    "Отпустите — следующая новость"
                } else {
                    "Потяните вверх — следующая новость"
                },
                style = AppTheme.type.meta,
                color = AppTheme.colors.mutedGray,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, -pull.floatValue.roundToInt()) }
                .background(AppTheme.colors.blackInversed)
                .verticalScroll(rememberScrollState()),
        ) {
            // Time + Russian long-form date, then section (from the HTML-parsed rubric, falling
            // back to the RSS-derived category from the previous screen), e.g.
            // "14:32, 15 сентября 2026 · Бывший СССР".
            val section = uiState.content?.category ?: newsItem.categories?.firstOrNull()
            val metaLine = listOfNotNull(
                newsItem.publishedTimeAndRuDate().ifEmpty { null },
                section?.ifEmpty { null },
            ).joinToString(" · ")
            if (metaLine.isNotEmpty()) {
                Text(
                    text = metaLine,
                    style = AppTheme.type.meta,
                    color = AppTheme.colors.mutedGray,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 4.dp),
                )
            }

            Text(
                text = newsItem.title.orEmpty(),
                style = AppTheme.type.articleTitle,
                color = AppTheme.colors.black,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            )

            val announce = uiState.content?.announce
            if (!announce.isNullOrEmpty()) {
                Text(
                    text = announce,
                    style = AppTheme.type.bodyParagraph,
                    color = AppTheme.colors.mutedGray,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
                )
            }

            // Author is rendered here, ahead of the hero image, rather than inline at its
            // natural position in the parsed content list (skipped below to avoid a duplicate).
            val author = uiState.content?.content?.filterIsInstance<ArticleContentType.Author>()?.firstOrNull()
            if (author != null) {
                AuthorBlock(author, modifier = Modifier.padding(bottom = 16.dp))
            }

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
                        .clip(RoundedCornerShape(12.dp))
                        .padding(bottom = 16.dp)
                        .clickable { onImageClick(newsItem.image) }
                        .pinchZoomPreview(),
                )
            }

            when {
                uiState.isLoading -> ArticleLoadingBody()
                uiState.error != null -> ArticleErrorBody(message = uiState.error.orEmpty())
                else -> uiState.content?.content?.forEach { block ->
                    if (block !is ArticleContentType.Author) {
                        ArticleContentBlock(block, onImageClick)
                    }
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

package com.newsrssreader.ui.article

import android.content.Intent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
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

// The article body is a "sheet" that normally covers the screen edge to edge. Only once it has
// been read to the end does it lift by [SheetPeek], uncovering a sliver of the sheet below it
// (the next article) and casting [SheetShadowHeight] of shadow onto it. The lift is an offset,
// not padding: mid-article the text runs all the way to the bottom of the screen as before, and
// there is no gap or shadow to suggest an edge that isn't there yet.
private val SheetPeek = 26.dp
private val SheetShadowHeight = 12.dp
private val SheetShadowColor = Color.Black.copy(alpha = 0.26f)

// How long the sheet takes to lift once the reader arrives at the end of the article.
private const val SheetEdgeFadeMs = 200

// Blank space between the last content block and the sheet's own bottom edge.
private val ArticleEndGap = 40.dp

/**
 * Article detail screen: title/date/main-image header followed by a body that dispatches on the
 * ViewModel's loading/error/loaded state, matching iOS's minimal inline nav bar + scrollable
 * article body. `newsItem` is resolved by the caller (the NavHost) via `NewsItemCache.get(id)`
 * before this screen is constructed - the ViewModel itself only ever needs the resolved
 * [NewsItem], not the cache lookup.
 *
 * Dragging up past the bottom of a loaded article moves on to the next item of the feed it was
 * opened from (see [NewsFeedContext]). The whole thing is one continuous movement: the pull
 * lifts the current sheet off the sheet below, the revealed strip shows the next article's
 * headline in the exact place it will occupy once it is the article, and releasing past the
 * threshold simply carries that same movement on until the outgoing sheet's bottom edge reaches
 * the top of the screen. Only then is the article swapped, so the parser starts on the next
 * article after the animation rather than competing with it.
 *
 * That happens *inside* this destination rather than as a new navigation, so the back stack stays
 * "feed -> article" however many articles deep the user reads, and back always returns to the
 * list they started from.
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
    val nextItem = remember(currentId) { NewsFeedContext.next(currentId) }

    val context = LocalContext.current
    val topBarBorderColor = AppTheme.colors.topBarBorder

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
        // Keyed so that switching articles starts the new body from scratch - fresh scroll
        // position, no leftover pull, and a fresh ViewModel (hence a fresh parse) for the item
        // that is now on screen.
        key(currentItem.id) {
            ArticleBody(
                newsItem = currentItem,
                nextItem = nextItem,
                onImageClick = onImageClick,
                onSwitchToNext = { currentId = it.id },
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

/**
 * One article's sheet, the sheet peeking out beneath it, and the pull gesture that trades one for
 * the other.
 *
 * The gesture is a nested-scroll connection rather than a draggable: the article is an ordinary
 * `verticalScroll` column, so this only has to pick up the scroll delta the column itself could
 * no longer consume (i.e. the user dragging up while already at the bottom), lift the whole sheet
 * by a damped fraction of it, and decide on release whether the pull was long enough. When it
 * was, the same `pull` value keeps animating - all the way to the sheet's own height, which puts
 * its bottom edge at the top of the screen - and [onSwitchToNext] is only called once that
 * finishes, so the next article's header is already sitting in its final position when the real
 * article replaces the preview. With no [nextItem] to move on to there is nothing to animate
 * into, so the sheet springs back and the strip says so.
 */
@Composable
private fun ArticleBody(
    newsItem: NewsItem,
    nextItem: NewsItem?,
    onImageClick: (String) -> Unit,
    onSwitchToNext: (NewsItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ArticleViewModel = viewModel(key = newsItem.id) { ArticleViewModel(newsItem) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()
    // The lift only means anything at the end of the article, so it is tied to the scroll
    // position. An article too short to scroll at all (maxValue == 0) is excluded: it already
    // ends in blank paper, and lifting it would drag its own headline up under the top bar.
    val atEnd by remember {
        derivedStateOf { scrollState.maxValue > 0 && scrollState.value >= scrollState.maxValue }
    }
    val edgeLift = animateFloatAsState(
        targetValue = if (atEnd) 1f else 0f,
        animationSpec = tween(SheetEdgeFadeMs),
        label = "sheetEdge",
    )

    val density = LocalDensity.current
    val thresholdPx = with(density) { PullToNextThreshold.toPx() }
    val peekPx = with(density) { SheetPeek.toPx() }
    val pull = remember { mutableFloatStateOf(0f) }
    // Height of the whole body, needed to know how far the sheet has to travel to clear the
    // screen; 0 until the first layout pass, which is also when the gesture can't have run yet.
    val bodyHeight = remember { mutableIntStateOf(0) }
    // While the article is still loading its "bottom" is only the bottom of the shimmer
    // placeholder, which would make the gesture fire almost immediately on open. `triggered`
    // latches it off once a switch is under way so nothing can disturb the pull mid-animation.
    var triggered by remember { mutableStateOf(false) }
    val gestureEnabled = rememberUpdatedState(!uiState.isLoading && !triggered)
    val next = rememberUpdatedState(nextItem)
    val switchToNext = rememberUpdatedState(onSwitchToNext)

    // How far the sheet is currently raised off the one below: the end-of-article lift plus
    // whatever the pull gesture has added. Read inside layout/draw lambdas only, so neither
    // animation costs a recomposition.
    fun lift(): Float = peekPx * edgeLift.value + pull.floatValue

    // Where the sheet's bottom edge currently is: the seam the shadow is drawn under and the
    // next sheet's preview hangs from. At rest this is the bottom of the screen, which is what
    // keeps both of them invisible until there is something to show.
    fun seamY(): Int = bodyHeight.intValue - lift().roundToInt()

    val nestedScrollConnection = remember(thresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Dragging back down pays off the accumulated pull before the article scrolls.
                if (triggered) return Offset.Zero
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
                val target = next.value
                // The pull has to make up whatever the end-of-article lift is not already
                // covering, so that the seam lands exactly on the top of the screen and the
                // preview headline is standing in the real header's place when they swap.
                val sheetHeight = bodyHeight.intValue - peekPx * edgeLift.value
                if (pull.floatValue >= thresholdPx && gestureEnabled.value &&
                    target != null && sheetHeight > 0f
                ) {
                    triggered = true
                    // Carry the gesture on rather than restarting it: the sheet keeps rising from
                    // wherever the finger left it until its bottom edge reaches the top of the
                    // screen, and the next sheet keeps following it up behind the shadow.
                    animate(
                        initialValue = pull.floatValue,
                        targetValue = sheetHeight,
                        animationSpec = tween(ArticleSwitchDurationMs, easing = FastOutSlowInEasing),
                    ) { value, _ -> pull.floatValue = value }
                    switchToNext.value(target)
                } else {
                    animate(pull.floatValue, 0f) { value, _ -> pull.floatValue = value }
                }
                // The pull consumed the drag, so the fling that ends it belongs to it too.
                return available
            }
        }
    }

    // The section shown in the meta line: the parsed rubric once it's known, otherwise the
    // RSS-derived category carried over from the list screen.
    val section = uiState.content?.category ?: newsItem.categories?.firstOrNull()

    Box(
        modifier = modifier
            .fillMaxSize()
            // The sheet below the article: the same paper colour, told apart only by the shadow
            // the sheet above casts onto it.
            .background(AppTheme.colors.blackInversed)
            .clipToBounds()
            .onSizeChanged { bodyHeight.intValue = it.height }
            .nestedScroll(nestedScrollConnection),
    ) {
        // The next sheet's own header, hanging from the seam. It is laid out exactly like the
        // real header below, so when the animation finishes and the real article takes over,
        // the headline is already where it needs to be and nothing jumps.
        NextSheetPreview(
            nextItem = nextItem,
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, seamY()) }
                .graphicsLayer {
                    // Fades in over the course of the pull, so the strip reads as empty paper
                    // at rest and as a committed choice by the time the threshold is reached.
                    alpha = (pull.floatValue / thresholdPx).coerceIn(0f, 1f)
                },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, -lift().roundToInt()) }
                .background(AppTheme.colors.blackInversed)
                .verticalScroll(scrollState),
        ) {
            ArticleHeader(newsItem = newsItem, section = section)

            val announce = uiState.content?.announce
            when {
                uiState.isLoading -> ShimmerAnnouncePlaceholder(
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
                )

                !announce.isNullOrEmpty() -> Text(
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

            // Blank paper between the end of the article and the sheet's edge, so reaching the
            // bottom of the text is visibly reaching the bottom of the page.
            Spacer(Modifier.height(ArticleEndGap))
        }

        // The shadow the sheet casts on the one below, drawn last so it falls across the next
        // sheet's headline rather than under it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(SheetShadowHeight)
                .offset { IntOffset(0, seamY()) }
                .graphicsLayer {
                    // Gone by the time the outgoing sheet has left the screen: at that point the
                    // seam is at the very top and a dark band there would just look like grime.
                    // Mid-article nothing needs switching off - the seam is simply off-screen.
                    val height = bodyHeight.intValue.toFloat().coerceAtLeast(1f)
                    alpha = (1f - pull.floatValue / height).coerceIn(0f, 1f)
                }
                .background(
                    Brush.verticalGradient(listOf(SheetShadowColor, Color.Transparent)),
                ),
        )
    }
}

/**
 * The meta line and headline that open an article. Shared with [NextSheetPreview] so the preview
 * of the next article lands pixel-for-pixel where the real thing will be.
 *
 * [metaColor] is overridden to transparent by the preview: the user asked for the headline alone
 * down there, but the meta line still has to take up its space or the headline would shift
 * upwards by its height the moment the real article arrives.
 */
@Composable
private fun ArticleHeader(
    newsItem: NewsItem,
    section: String?,
    modifier: Modifier = Modifier,
    metaColor: Color = AppTheme.colors.mutedGray,
    titleColor: Color = AppTheme.colors.black,
) {
    Column(modifier = modifier) {
        // Time + Russian long-form date, then section, e.g. "14:32, 15 сентября 2026 · Бывший СССР".
        val metaLine = listOfNotNull(
            newsItem.publishedTimeAndRuDate().ifEmpty { null },
            section?.ifEmpty { null },
        ).joinToString(" · ")
        if (metaLine.isNotEmpty()) {
            Text(
                text = metaLine,
                style = AppTheme.type.meta,
                color = metaColor,
                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 4.dp),
            )
        }

        Text(
            text = newsItem.title.orEmpty(),
            style = AppTheme.type.articleTitle,
            color = titleColor,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
        )
    }
}

/**
 * What the strip uncovered by the pull shows: the headline of the article that releasing will
 * move on to, or - when the feed has run out - a plain statement that it has, since there is
 * nothing to promise the user in that case.
 */
@Composable
private fun NextSheetPreview(nextItem: NewsItem?, modifier: Modifier = Modifier) {
    if (nextItem != null) {
        ArticleHeader(
            newsItem = nextItem,
            section = nextItem.categories?.firstOrNull(),
            modifier = modifier,
            metaColor = Color.Transparent,
        )
    } else {
        Text(
            text = "Новости закончились",
            style = AppTheme.type.meta,
            color = AppTheme.colors.mutedGray,
            textAlign = TextAlign.Center,
            modifier = modifier.padding(horizontal = 16.dp, vertical = 24.dp),
        )
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

/** Two shimmering bars standing in for the announce line while the article body loads. */
@Composable
private fun ShimmerAnnouncePlaceholder(modifier: Modifier = Modifier) {
    val barColor = AppTheme.colors.gray.copy(alpha = 0.3f)
    val barShape = RoundedCornerShape(4.dp)
    Column(
        modifier = modifier.shimmer(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(barShape)
                .background(barColor),
        )
        Box(
            modifier = Modifier
                .size(width = 240.dp, height = 16.dp)
                .clip(barShape)
                .background(barColor),
        )
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

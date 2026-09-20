package com.newsrssreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.newsrssreader.data.network.LentaFeedService
import com.newsrssreader.data.network.UpdateRelease
import com.newsrssreader.data.parser.SimpleMarkdownParser
import com.newsrssreader.ui.theme.AppTheme
import com.newsrssreader.ui.update.DownloadPhase
import com.newsrssreader.ui.update.UpdateUiState

/**
 * Static content of the category drawer, matching the iOS `MenuView`. Presentation (slide-in
 * animation, overlay scrim) is left to the caller (Task 13's MainActivity wiring) — this
 * Composable only renders the full-height panel content given the current selection.
 */
@Composable
fun MenuView(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    onDismiss: () -> Unit,
    updateState: UpdateUiState = UpdateUiState(),
    onUpdateClick: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background)
            // Consume all touches so they don't fall through to the NavHost content still
            // composed behind this overlay (e.g. tapping blank space below the last category).
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) {}
            // Inset above the gesture/button navigation bar so the last category item isn't
            // drawn underneath it, mirroring the status-bar inset applied to the header Row.
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Row(
            // Same status-bar-inset-then-center treatment as TopPanel: inset below the status
            // bar, then let CenterVertically (via the IconButton's own padding) center the X
            // within the remaining height, instead of letting it sit under/against the status bar.
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss, modifier = Modifier.padding(start = 20.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close menu",
                    tint = AppTheme.colors.white,
                )
            }
        }

        HorizontalDivider(
            color = AppTheme.colors.gray,
            modifier = Modifier.padding(vertical = 10.dp),
        )

        // weight(1f) + verticalScroll lets this list shrink/scroll instead of pushing the update
        // banner below off screen on short devices.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            MenuItem(
                title = "Главная",
                selected = selectedCategory == "",
                onClick = { onCategorySelected("") },
            )
            LentaFeedService.categories.forEach { (key, title) ->
                MenuItem(
                    title = title,
                    selected = selectedCategory == key,
                    onClick = { onCategorySelected(key) },
                )
            }
        }

        updateState.release?.let { release ->
            UpdateBanner(release = release, phase = updateState.phase, onUpdateClick = onUpdateClick)
        }
    }
}

@Composable
private fun MenuItem(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 20.dp)
                .background(if (selected) AppTheme.colors.red else AppTheme.colors.background),
        )
        Text(
            text = title,
            style = if (selected) AppTheme.type.menuItemSelected else AppTheme.type.menuItemUnselected,
            color = if (selected) AppTheme.colors.red else AppTheme.colors.white,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun UpdateBanner(release: UpdateRelease, phase: DownloadPhase, onUpdateClick: () -> Unit) {
    var showNotes by remember { mutableStateOf(false) }
    val blocks = remember(release.notes) { SimpleMarkdownParser.parse(release.notes) }
    // Teaser above the link: headings are skipped (they'd just repeat "Что нового"), and so is
    // the "Full Changelog" trailer GitHub appends — neither tells the user what actually changed.
    val teaser = remember(blocks) {
        SimpleMarkdownParser.plainText(
            blocks.filter { block ->
                block !is SimpleMarkdownParser.Block.Heading &&
                    !SimpleMarkdownParser.plainText(listOf(block)).startsWith("Full Changelog")
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.backgroundWhite)
            .padding(16.dp),
    ) {
        Text(
            text = "Доступно обновление",
            style = AppTheme.type.menuItemSelected,
            color = AppTheme.colors.black,
        )

        if (teaser.isNotEmpty()) {
            Text(
                text = teaser,
                style = AppTheme.type.meta,
                color = AppTheme.colors.mutedGray,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Box(modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)) {
            Text(
                text = "Что нового",
                style = AppTheme.type.meta,
                color = AppTheme.colors.red,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { showNotes = !showNotes },
            )
            if (showNotes) {
                ReleaseNotesPopup(blocks = blocks, onDismiss = { showNotes = false })
            }
        }

        // A single slot: either the button or the progress readout, never both at once.
        when (phase) {
            is DownloadPhase.Downloading -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = { phase.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "${(phase.progress * 100).toInt()}%",
                        style = AppTheme.type.meta,
                        color = AppTheme.colors.black,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            is DownloadPhase.ReadyToInstall -> {
                Text(
                    text = "Готово к установке",
                    style = AppTheme.type.meta,
                    color = AppTheme.colors.black,
                )
            }
            is DownloadPhase.Failed -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Не удалось загрузить обновление",
                        style = AppTheme.type.meta,
                        color = AppTheme.colors.red,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Button(onClick = onUpdateClick, modifier = Modifier.fillMaxWidth()) {
                        Text("Повторить")
                    }
                }
            }
            DownloadPhase.Idle -> {
                Button(onClick = onUpdateClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Обновить")
                }
            }
        }
    }
}

/**
 * The release-notes card. It floats over the banner (itself a light surface) and over whatever
 * menu content is behind it, so it needs its own edge: `blackInversed` + a `gray` border are the
 * only pair of tokens that stay mutually contrasting in *both* themes — a `black` background with
 * `white` text, as this popup used to be, collapses to white-on-white in dark mode.
 */
@Composable
private fun ReleaseNotesPopup(
    blocks: List<SimpleMarkdownParser.Block>,
    onDismiss: () -> Unit,
) {
    Popup(
        alignment = Alignment.BottomStart,
        onDismissRequest = onDismiss,
        // Focusable so that a tap outside the card and the system back button both close it,
        // on top of the explicit X — a non-focusable popup would receive neither.
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .heightIn(max = 360.dp)
                .background(AppTheme.colors.blackInversed, shape = RoundedCornerShape(10.dp))
                .border(1.5.dp, AppTheme.colors.gray, RoundedCornerShape(10.dp))
                // Swallow taps inside the card: without this they'd reach the banner underneath
                // and the card would close on every attempt to scroll or read it.
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {},
        ) {
            MarkdownBlocks(
                blocks = blocks,
                baseStyle = AppTheme.type.meta,
                color = AppTheme.colors.black,
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    // Extra end padding keeps the first line clear of the close button.
                    .padding(start = 14.dp, top = 12.dp, end = 40.dp, bottom = 14.dp),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Закрыть",
                    tint = AppTheme.colors.black,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

NewsRSSReader (Android) is a native Android news reader app built with Kotlin and Jetpack Compose
that displays news from Lenta.ru RSS feeds. It replicates the design and behavior of a sibling iOS
app, but is a fully independent codebase with its own architecture, dependencies, and build system
— there is no build-time or runtime dependency on the iOS project.

The app features tabbed news browsing (Главное / Последнее / Все), a category drawer menu, article
detail viewing with rich content blocks (paragraphs, subheadings, images, quotes, info boxes,
author bylines), and a full-screen photo viewer with pinch-zoom and save-to-gallery.

## Tech Stack

- **Kotlin** + **Jetpack Compose** (Material3 API surface, fully custom design tokens)
- **MVVM** architecture with `StateFlow`-driven ViewModels
- **Navigation-Compose** for screen navigation
- **Kotlin Coroutines** for async work
- **OkHttp** for networking
- **Coil** for async image loading/caching
- **JUnit4** + **Robolectric** for unit tests

Dependency philosophy: keep external (production) dependencies minimal. OkHttp and Coil are the
only two non-AndroidX/non-Kotlin runtime dependencies. RSS/Atom XML parsing and article HTML
parsing are hand-rolled (`android.util.Xml.newPullParser()` and a custom mini HTML DOM/tokenizer)
rather than pulling in parsing libraries (no Jsoup). Robolectric is a **test-only** dependency,
needed because some production code calls real Android-framework APIs (`android.util.Xml`,
`org.json.JSONObject`) that require Robolectric to execute under local JVM unit tests — this is
acceptable since it never ships in the app.

## Build and Run

```bash
# First-time setup: point Gradle at your local Android SDK
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties

# Build a debug APK (day-to-day development)
./gradlew :app:assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Build the signed, minified APK that ships to users
./gradlew :app:assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk

# Run unit tests
./gradlew test

# Compile only (fast check)
./gradlew :app:compileDebugKotlin

# Install on a connected device/emulator and launch
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.newsrssreader/.MainActivity
```

If `JAVA_HOME` isn't already set to a JDK 17+ install, export it before running Gradle, e.g.:
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
```

### Release signing

The `release` build type is signed with a project-specific key. Neither the keystore nor its
passwords live in the repository — `app/build.gradle.kts` reads four Gradle properties
(`NEWSRSSREADER_RELEASE_STORE_FILE`, `_STORE_PASSWORD`, `_KEY_ALIAS`, `_KEY_PASSWORD`) that are
kept in `~/.gradle/gradle.properties`, with the keystore itself at
`~/.android/newsrssreader-release.jks`.

When those properties are absent — a fresh clone, another machine, CI — the release build does
**not** fail; it falls back to producing `app-release-unsigned.apk`, which cannot be installed.
So always confirm what you actually built before handing it to anyone:

```bash
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-release.apk   # must print CN=NewsRSSReader
```

Two consequences worth knowing:
- **The keystore is unbackuppable-by-default and irreplaceable.** Android will not install an
  update over an app signed with a different key, so losing `newsrssreader-release.jks` means no
  future build can ever update an existing install — only a manual uninstall/reinstall. Back up
  the keystore and `~/.gradle/gradle.properties` together.
- **Releases `v1.0.0`–`v1.2.0` were signed with the Android debug key**, not this one. Anyone
  still running one of those has to uninstall before a `v1.3.0`-or-later APK will install; the
  in-app updater cannot do that for them.

`build_apk.sh` deliberately still builds the *debug* APK — it is a local development
convenience (it drops the result in `~/Downloads`), not the release path.

No Android Studio project generation step is needed — this is a standard Gradle project
(`settings.gradle.kts` + `app/build.gradle.kts` + a Gradle version catalog at
`gradle/libs.versions.toml`); open it directly in Android Studio or build from the CLI.

## Architecture

### MVVM Pattern

- **Model**: `NewsItem` (RSS feed item), `ArticleContent`/`ArticleContentType` (parsed article body
  blocks) — both under `data/model/`.
- **ViewModel**: `HomeViewModel`, `CategoryViewModel`, `ArticleViewModel` — each exposes a single
  `StateFlow<UiState>` consumed via `collectAsStateWithLifecycle()`.
- **View**: Composables under `ui/`, organized by screen (`ui/home/`, `ui/category/`,
  `ui/article/`, `ui/photo/`) and shared components (`ui/components/`, with article-specific
  content blocks under `ui/components/article/`).

### Package layout

```
com.newsrssreader/
├── data/
│   ├── model/          NewsItem, ArticleContent, ArticleContentType
│   ├── network/         LentaFeedService (OkHttp client + category map), FeedParser (RSS 2.0)
│   ├── parser/          SimpleHtmlParser (DOM/tokenizer), LentaArticleParser (article body parser)
│   ├── NewsItemCache.kt  in-memory id -> NewsItem map bridging Navigation-Compose route args
│   └── ImageSaver.kt     save-to-gallery helper (MediaStore / legacy file+permission)
├── ui/
│   ├── theme/            Color.kt, Type.kt, Theme.kt — design tokens (see below)
│   ├── components/        NewsRow, NewsTop, NewsTabs, TopPanel, MenuView, Shimmer
│   │   └── article/         ParagraphBlock, SubheadingBlock, ImageBlock, QuoteBlock, AuthorBlock, InfoBoxBlock
│   ├── home/               HomeScreen + HomeViewModel
│   ├── category/           CategoryScreen + CategoryViewModel
│   ├── article/            ArticleDetailScreen + ArticleViewModel
│   └── photo/               PhotoViewerScreen (full-screen zoomable image viewer)
└── MainActivity.kt          single-activity host; owns the NavHost + MenuView overlay wiring
```

### Design tokens (not Material3 semantics)

Colors and typography are defined as a custom `AppColors`/`AppType` system (`ui/theme/`), accessed
via `AppTheme.colors.*` / `AppTheme.type.*`, rather than mapped onto Material3's `ColorScheme` /
`Typography`. This is deliberate: the app's dark-mode color behavior is non-standard (e.g. `Gray`
and `Black` both resolve to white in dark mode, while `Background` stays the same dark value in
both themes), which doesn't fit cleanly into Material3's semantic color roles. A minimal
`ColorScheme` is still fed into `MaterialTheme` (see `Theme.kt`) purely as a safety net so that any
unstyled stock Material3 component doesn't fall back to a jarring default (dynamic/purple) palette
— but `AppTheme.colors`/`AppTheme.type` are the primary way screens should read design values.

Key color tokens: `background`, `backgroundWhite`, `black`, `blackInversed`, `white`, `gray`,
`lightGrey`, `red`. See `Color.kt` for exact hex values (light/dark variants).

### Navigation

Single-Activity, Navigation-Compose `NavHost` with three routes, wired in `MainActivity.kt`:
- `"home"` — `HomeScreen`
- `"category/{key}"` — `CategoryScreen`, `key` is a Lenta.ru category slug (e.g. `"sport"`)
- `"article/{id}"` — `ArticleDetailScreen`, `id` resolves a `NewsItem` via `NewsItemCache`
  (Navigation-Compose route args can't carry complex objects, so the caller populates the cache
  before navigating and the destination reads it back)
- `"photo/{encodedUrl}"` — `PhotoViewerScreen`, the image URL is `Uri.encode()`d into the route;
  Navigation-Compose already decodes path template args once during route matching, so do **not**
  add a second manual `Uri.decode()` call when reading it back (a past bug — see git history).

The category-drawer `MenuView` is rendered as a `Box`-overlaid `AnimatedVisibility` on top of the
`NavHost`, with `menuShown` state lifted to `MainActivity`'s top-level `AppRoot()` composable. The
currently-highlighted category in the menu is **derived from the nav back stack**
(`navController.currentBackStackEntryAsState()`), not hand-tracked as separate state — this keeps
the highlight and back-button behavior correctly in sync with whatever screen is actually showing.

### Networking & parsing

- `LentaFeedService` (`data/network/`): OkHttp-based client. URL pattern:
  `https://lenta.ru/rss/{source}[/{category}]` where `source` is `top7` / `last24` / `news` (the
  "all" feed). Exposes the 15-entry category key→Russian-display-name map (`categories`), and a
  `FeedFetcher` interface (implemented by the `LentaFeedService` object) so ViewModels can accept a
  fake in tests.
- `FeedParser` (`data/network/`): hand-rolled RSS 2.0 parser using `android.util.Xml`. Deliberately
  RSS-2.0-only (no Atom/JSON) — confirmed against the sibling iOS app's real parser, which also
  only supports RSS 2.0 despite older docs suggesting otherwise. A few parity quirks are
  intentional, not bugs: only the *last* `<category>` tag wins when an item has several; only the
  *last* `<enclosure>` with a non-empty `url` wins if an item has several. `NewsItem.id` is
  deliberately a **stable hash of the item's `link`** (unlike the iOS app, which mints a fresh
  random UUID per parse) — Navigation-Compose route args need a stable key for `"article/{id}"`
  navigation to work across recompositions/back-stack entries.
- `SimpleHtmlParser` + `LentaArticleParser` (`data/parser/`): a hand-rolled HTML tokenizer/DOM (no
  Jsoup) plus block-recognition logic that turns raw Lenta.ru article HTML into a structured
  `ArticleContent` (list of `ArticleContentType` blocks). The CSS-class selectors used to recognize
  each block type (`.topic-header__rubric`, `.box-quote`, `.box-inline-topic`, etc.) were verified
  against **live** Lenta.ru markup, not just the original iOS source — Lenta.ru's HTML structure
  drifts over time, so several selectors carry an old-selector-then-new-selector fallback chain
  (e.g. category extraction, quote text extraction, related-material extraction) deliberately
  diverging from iOS's now-stale selectors to stay accurate against the live site. See the doc
  comments in `LentaArticleParser.kt` for the exact fallback order per block type.

### Design fidelity vs. deliberate deviations from iOS

This app intentionally matches the iOS app's visual design (colors, spacing, typography, layout)
closely, but a few behaviors are deliberate Android-specific improvements or platform-appropriate
adaptations rather than strict 1:1 ports — don't "fix" these back to iOS parity without checking
with whoever's driving the work:
- `NewsItem.id` is link-derived and stable (iOS: random UUID per parse) — required for Compose
  Navigation.
- Article parser selectors were updated to match *current* live Lenta.ru markup where it had
  drifted from the iOS app's (now stale) selectors.
- The photo viewer (pinch-zoom, double-tap zoom, save-to-gallery) is an Android-only addition with
  no iOS equivalent in the source app.
- News-list thumbnails **do** intentionally match iOS exactly: a fixed 60x60dp square with
  `ContentScale.Crop` (SwiftUI's `.aspectRatio(contentMode: .fill)` equivalent) — this was
  double-checked against the live iOS source after an earlier, incorrect variable-height attempt.

## Testing

Unit tests live under `app/src/test/java/` and cover the data layer and ViewModels (parsers, date
formatting, feed/category logic, ViewModel state transitions including cancellation/race handling)
— pure-UI Composables are not unit tested (there's no meaningful way to do so cheaply for this
kind of visual/gesture-heavy code); those are verified manually by installing a built APK on an
emulator or device.

```bash
./gradlew test                                          # full suite
./gradlew :app:testDebugUnitTest --tests "com.newsrssreader.data.parser.FeedParserTest"  # one class
```

## Working with feeds and categories

- Feed URLs: `https://lenta.ru/rss/{top7|last24|news}[/{category}]`.
- The category list (`LentaFeedService.categories`) must stay in sync with what Lenta.ru actually
  serves — if you're re-deriving it from another source, count the entries carefully (a past
  research pass miscounted 15 real entries as 14; verify directly against a live fetch or a
  trustworthy up-to-date reference, don't just trust an old comment).
- `NewsItem.publishedDate()`: empty string if `published` is null; `"HH:mm"` if the same calendar
  day as now; otherwise `"HH:mm, d MMMM"` with the month name always in Russian (e.g.
  `"09:55, 11 сентября"`), regardless of the device locale.
- Lenta.ru's feeds sometimes repeat the same article (same `<link>`) twice, occasionally as two
  adjacent items. Since `NewsItem.id` is derived from the link, that used to hand LazyColumn two
  identical keys and crash the list mid-scroll, so `FeedParser.parse` drops repeats
  (`distinctBy { it.id }`) — don't remove that without replacing the guarantee elsewhere.

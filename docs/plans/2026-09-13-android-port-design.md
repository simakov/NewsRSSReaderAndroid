# NewsRSSReader — Android (Jetpack Compose) Port Design

Date: 2026-09-13
Status: Approved for implementation

## Goal

Port the iOS NewsRSSReader phone app (SwiftUI, Lenta.ru RSS reader) to Android, preserving the
visual design exactly, using a minimal set of external dependencies and a modern Android
architecture (Kotlin, Jetpack Compose, MVVM). watchOS companion is out of scope.

Location: separate new repository/folder, sibling to the iOS project
(`/Volumes/Data/Apps/TestApps/NewsRSSReaderAndroid/`).

## Architecture

Single-module Compose app, MVVM, `StateFlow`-driven state (Kotlin analog of `@Published`).

```
com.newsrssreader/
├── data/
│   ├── model/          // NewsItem, ArticleContent, ArticleContentType
│   ├── network/         // LentaFeedService (OkHttp) + RSS 2.0 parser (XmlPullParser)
│   └── parser/          // LentaArticleParser (article HTML -> ArticleContent)
├── ui/
│   ├── theme/            // Color.kt, Type.kt, Theme.kt
│   ├── components/        // NewsRow, NewsTop, TabsPill, MenuDrawer, Article blocks, Shimmer
│   ├── home/               // HomeScreen + HomeViewModel
│   ├── category/           // CategoryScreen (shares HomeViewModel-style state)
│   └── article/            // ArticleDetailScreen + ArticleViewModel
└── MainActivity.kt          // single-activity, NavHost
```

Dependencies (beyond the Android SDK / Compose BOM / Navigation-Compose / Kotlin Coroutines,
which are standard platform-level deps, not "extra" third-party libraries):
- **OkHttp** — HTTP client
- **Coil** — async image loading/caching (Compose-native, analog of SDWebImage)

No XML/HTML parsing libraries (no Jsoup) — hand-rolled parsing on `XmlPullParser`, mirroring the
iOS app's own move away from FeedKit/SwiftSoup to native parsers.

## Design System

### Colors (`ui/theme/Color.kt`)

Exact values from `Assets.xcassets`, with light/dark variants. Not mapped onto Material
`ColorScheme` semantics — a custom `AppColors` data class + `CompositionLocal`, switched by
`isSystemInDarkTheme()`, because the source app's dark-mode behavior is non-standard (e.g. `Gray`
flips to white in dark mode, same as `Black`).

| Token | Light | Dark |
|---|---|---|
| Background | `#292929` | `#292929` (single value, no dark override in source) |
| BackgroundWhite | `#F3F3F3` | `#FFFFFF` |
| Black (primary text) | `#292929` | `#FFFFFF` |
| BlackInversed (article bg) | `#FFFFFF` | `#292929` |
| White | `#FFFFFF` | `#FFFFFF` |
| Gray (secondary text) | `#636363` | `#FFFFFF` |
| LightGrey (chips/panels) | `#EAEAEA` | `#FFFFFF` |
| Red (accent) | `#BB393F` | `#BB393F` |

Opacity overlays used throughout: `Gray@0.2/0.3/0.6`, `LightGrey@0.2/0.3` — apply via
`color.copy(alpha = ...)`.

### Typography

System font only (Roboto default, no custom font files — mirrors iOS's `.font(.system(...))`
usage throughout, no `Font.custom`).

| Use | Size | Weight |
|---|---|---|
| Article title | 24sp | Bold |
| Category header | 22sp | SemiBold |
| Hero title (NewsTop) | 20sp | SemiBold |
| Article subheading | 20sp | Bold |
| List row title (NewsView) | 15sp | SemiBold |
| Article lead paragraph | 17sp | Medium |
| Article body paragraph | 16sp | Regular |
| Date/meta text | 13sp | Regular |
| Tab pills | 15sp | Regular, uppercase |
| Quote author name | 15sp | SemiBold |
| Quote author description | 14sp | Regular |
| Info box text | 16sp | Medium |
| Author name (byline) | 16sp | SemiBold |
| Author job title | 12sp | Regular |
| Image caption | 14sp | Regular |
| Image credit | 12sp | Regular, italic |

Corner radii: 4dp (thumbnails, info box), 8dp (quote box), 17dp (tab pills — fully rounded).

## Screens & Navigation

Navigation-Compose graph:

```
HomeScreen (start) ──► ArticleDetailScreen ("article/{id}")
CategoryScreen ("category/{key}") ──► ArticleDetailScreen
+ custom slide-in MenuView drawer overlay (not a system NavigationDrawer — matches iOS's
  hand-rolled overlay panel triggered by the hamburger icon)
```

### HomeScreen
- **TopPanel**: dark bar (`Background` color), hamburger icon + "LENTA.RU" logo, ~44dp tall.
- **NewsTop** (hero banner, 350dp tall): background photo (crop/fill), bottom-fade gradient
  (`transparent` → `Background`, top-to-bottom, starting at center), title (White, 20sp
  SemiBold), date + first category (LightGrey, 13sp). Only renders when link+image present.
- **NewsTabs**: three uppercase pill tabs — "ГЛАВНОЕ" / "ПОСЛЕДНЕЕ" / "ВСЕ" (index 0/1/2 map to
  top7/last24/all sources). Selected: LightGrey pill background, `Background`-colored text,
  17dp corner radius, 8dp padding. Unselected: plain `Black`-colored text. 20dp spacing between
  items, 16dp outer padding.
- **List**: `LazyColumn` of `NewsRow` (title 15sp SemiBold `Black` + date 13sp `Gray` on the
  left, 60x60dp thumbnail with 4dp corner radius on the right, 10dp padding), `Divider` between
  rows.
- **Loading state**: 7 shimmering placeholder rows instead of the list (no divider between
  placeholders).

### CategoryScreen
- Category title header (22sp SemiBold, `Black`, 10dp top/leading padding).
- Same list/shimmer behavior as Home, no hero banner or tabs.

### MenuView (drawer overlay)
- Dark (`Background`) full panel, close (X) icon top-left, divider, then "Главная" + all
  categories.
- Selected item: red left bar (3x20dp) + red text (21sp SemiBold).
- Unselected item: transparent left bar + white text (20sp SemiBold).
- Categories (from `LentaFeedService`, 14 entries confirmed in source — CLAUDE.md's "15
  predefined categories" claim is stale/inaccurate; 14 is authoritative, confirmed with the user):
  russia→Россия, world→Мир, ussr→Бывший СССР, economics→Экономика, forces→Силовые структуры,
  science→Наука и техника, culture→Культура, sport→Спорт, media→Интернет и СМИ, style→Ценности,
  travel→Путешествия, life→Из жизни, realty→Среда обитания, wellness→Забота о себе,
  pobeda80→Победа (verify exact count/keys against `LentaFeedService.swift` while porting).

### ArticleDetailScreen
- Scrollable column, background `BlackInversed`.
- Title (24sp Bold, `Black`, 16dp horizontal / 16dp top / 12dp bottom padding).
- Date (13sp `Gray`, 16dp horizontal, 16dp bottom padding).
- Main image if present (scaled to fit, `Gray@0.2` background while loading).
- Body — one of three states:
  - **Loading**: 5 shimmering paragraph placeholders (3 gray bars each, last one narrower).
  - **Error**: warning triangle (48dp, Red), "Article loading error" (18sp SemiBold `Black`),
    error description (14sp `Gray`, centered).
  - **Loaded**: sequence of content blocks dispatched by `ArticleContentType`:
    - `Paragraph` — 17sp Medium (lead) or 16sp Regular, `Black`, 16dp horizontal / 8dp vertical
      padding, 4dp line spacing.
    - `Subheading` — 20sp Bold, `Black`, 16dp horizontal, 20dp top / 8dp bottom padding.
    - `Image` — async image + caption (14sp `Gray`) + credit (12sp `Gray` italic).
    - `Quote` — large red "«" glyph (36sp Bold) + text (17sp Medium `Black`) + author
      name/description, on `LightGrey@0.3` background, 8dp corner radius, 16dp padding.
    - `Author` — circular photo or placeholder icon (50dp), name (16sp SemiBold) + job title
      (12sp `Gray@0.6`), on `LightGrey@0.2` background.
    - `InfoBox` — 3dp red bars top+bottom sandwiching 16sp Medium text, `LightGrey@0.2`
      background, 4dp corner radius.
    - `RelatedMaterial` — parsed but not rendered (matches iOS).
- Share action in top bar → Android share intent (analog of `UIActivityViewController`) with
  `"{title}\n\n{link}"`.
- External link open → Chrome Custom Tabs (analog of `SFSafariViewController` reader mode).

## Data & Networking

### Models
- `NewsItem`: id, title, summary, authors, link, updated, categories, content, published,
  source, rights, image. `publishedDate()`: empty if `published` is null; `"HH:mm"` if same
  calendar day as today; else `"d.MM HH:mm"`.
  **Deviation from iOS**: iOS's `NewsItem.id` is a fresh random `UUID` per parse (not stable
  across re-fetches). Android derives a stable `id` from the item's `link` instead, since
  Navigation-Compose routes need a stable key for `"article/{id}"` navigation — confirmed with
  the user (2026-09-13).
- `ArticleContentType` (sealed class): `Paragraph(text, isLead)`, `Subheading(text)`,
  `Image(url, caption?, credit?)`, `Quote(text, authorName, authorDescription?)`,
  `Author(name, photo?, jobTitle?)`, `InfoBox(text)`,
  `RelatedMaterial(title, description?, imageUrl?, articleUrl, date?)` (unrendered).
- `ArticleContent`: title, image?, publishedDate?, category?, content: List<ArticleContentType>.

### Networking
- `LentaFeedService` singleton, OkHttp client.
- URL pattern: `https://lenta.ru/rss/{source}[/{category}]`, `source` ∈ {top7, last24,
  news(="all")}.
- Feed parsing: hand-rolled `XmlPullParser`-based RSS 2.0 parser. Source verification
  (2026-09-13) confirmed the iOS app's `LentaRSSParser.swift` only ever parses RSS 2.0 — CLAUDE.md's
  claim of Atom/JSON support is stale documentation, not actual behavior. Confirmed with the user
  to match iOS's real RSS-2.0-only parity rather than the stale doc.
- Article HTML parsing: hand-rolled parser (no Jsoup), producing `ArticleContent`.

### HomeViewModel
- `tab: StateFlow<Int>` (0/1/2) — change triggers `loadFeeds(source)` for top7/last24/all.
- `firstNews` — first feed item, shown in the hero banner; `rssFeed` — remaining items.
- `categoryFeed` / `selectedCategory` — category screen state, `.all` source filtered by
  category key.
- `isLoading` / `isShowError` — drive shimmer/error UI.

## Testing / Verification Plan

- Unit tests for the RSS 2.0 parser against fixture payloads.
- Unit tests for `NewsItem.publishedDate()` date-formatting logic (today vs. other day).
- Manual verification: launch app in Android emulator, compare each screen side-by-side against
  iOS simulator screenshots (Home, Category, Article detail, Menu, loading/shimmer, error state).

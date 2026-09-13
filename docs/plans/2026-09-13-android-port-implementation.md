# NewsRSSReader Android Port Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a native Android (Kotlin + Jetpack Compose) port of the NewsRSSReader iOS app,
reproducing its exact visual design (Lenta.ru RSS reader) with minimal external dependencies.

**Architecture:** Single-module Compose app, MVVM with `StateFlow`. Data layer (models, OkHttp
network client, hand-rolled RSS/Atom/JSON + HTML parsers) is independent of UI and fully unit
testable. UI layer is Composables driven by ViewModels, matching the SwiftUI view breakdown in
the design doc.

**Tech Stack:** Kotlin, Jetpack Compose (Material3 API surface, fully custom color/type tokens),
Navigation-Compose, Kotlin Coroutines/Flow, OkHttp, Coil, JUnit5/JUnit4 for unit tests.

**Design reference:** `docs/plans/2026-09-13-android-port-design.md` (exact colors, sizes,
spacing, screen breakdown — refer back to it for any value not repeated here).

---

## Task 0: Project scaffolding

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `app/build.gradle.kts`
- Create: `gradle.properties`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `gradle/libs.versions.toml`
- Create: `app/src/main/java/com/newsrssreader/MainActivity.kt`

**Step 1:** Generate a new Android Studio "Empty Activity" (Compose) project structure manually
(no Android Studio available in this environment — write the Gradle files by hand). Use:
- `compileSdk = 35`, `minSdk = 26`, `targetSdk = 35`
- Kotlin `2.0.x`, Compose compiler plugin (Kotlin 2.0+ uses the `org.jetbrains.kotlin.plugin.compose` plugin, no separate compose-compiler version needed)
- `applicationId = "com.newsrssreader"`

`gradle/libs.versions.toml`:
```toml
[versions]
agp = "8.6.0"
kotlin = "2.0.21"
coreKtx = "1.15.0"
lifecycle = "2.8.7"
activityCompose = "1.9.3"
composeBom = "2024.12.01"
navigationCompose = "2.8.4"
okhttp = "4.12.0"
coil = "2.7.0"
coroutines = "1.9.0"
junit = "4.13.2"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

`settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "NewsRSSReaderAndroid"
include(":app")
```

`build.gradle.kts` (root):
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```

`app/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.newsrssreader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.newsrssreader"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
}
```

`app/src/main/AndroidManifest.xml`:
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <application
        android:label="Lenta.ru"
        android:icon="@mipmap/ic_launcher"
        android:theme="@style/Theme.NewsRSSReader"
        android:usesCleartextTraffic="false">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.NewsRSSReader">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

**Step 2: Verify Gradle configuration resolves**

Run: `cd /Volumes/Data/Apps/TestApps/NewsRSSReaderAndroid && ./gradlew help`

(First run `gradle wrapper --gradle-version 8.9` if no wrapper exists yet, using a system Gradle
install, to generate `gradlew`/`gradlew.bat`/`gradle/wrapper/*`.)

Expected: `BUILD SUCCESSFUL`

**Step 3: Commit**

```bash
git add -A
git commit -m "Scaffold Android project (Gradle, Compose, deps)"
```

---

## Task 1: Design tokens — colors and typography

**Files:**
- Create: `app/src/main/java/com/newsrssreader/ui/theme/Color.kt`
- Create: `app/src/main/java/com/newsrssreader/ui/theme/Type.kt`
- Create: `app/src/main/java/com/newsrssreader/ui/theme/Theme.kt`

**Step 1: Write `Color.kt`**

```kotlin
package com.newsrssreader.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppColors(
    val background: Color,
    val backgroundWhite: Color,
    val black: Color,
    val blackInversed: Color,
    val white: Color,
    val gray: Color,
    val lightGrey: Color,
    val red: Color,
)

val LightAppColors = AppColors(
    background = Color(0xFF292929),
    backgroundWhite = Color(0xFFF3F3F3),
    black = Color(0xFF292929),
    blackInversed = Color(0xFFFFFFFF),
    white = Color(0xFFFFFFFF),
    gray = Color(0xFF636363),
    lightGrey = Color(0xFFEAEAEA),
    red = Color(0xFFBB393F),
)

val DarkAppColors = AppColors(
    background = Color(0xFF292929),
    backgroundWhite = Color(0xFFFFFFFF),
    black = Color(0xFFFFFFFF),
    blackInversed = Color(0xFF292929),
    white = Color(0xFFFFFFFF),
    gray = Color(0xFFFFFFFF),
    lightGrey = Color(0xFFFFFFFF),
    red = Color(0xFFBB393F),
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }
```

**Step 2: Write `Type.kt`**

```kotlin
package com.newsrssreader.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object AppType {
    val articleTitle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold)
    val categoryHeader = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    val heroTitle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    val subheading = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold)
    val rowTitle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    val leadParagraph = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium)
    val bodyParagraph = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal)
    val meta = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal)
    val tabPill = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal)
    val quoteAuthorName = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    val quoteAuthorDescription = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal)
    val infoBox = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium)
    val authorName = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    val authorJobTitle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal)
    val imageCaption = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal)
    val imageCredit = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal)
    val errorTitle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
}
```

**Step 3: Write `Theme.kt`**

```kotlin
package com.newsrssreader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun NewsRSSReaderTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkAppColors else LightAppColors
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(content = content)
    }
}

object AppTheme {
    val colors: AppColors
        @Composable get() = LocalAppColors.current
    val type: AppType
        @Composable get() = AppType
}
```

**Step 4: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

**Step 5: Commit**

```bash
git add app/src/main/java/com/newsrssreader/ui/theme
git commit -m "Add design tokens: colors and typography"
```

---

## Task 2: Data models

**Files:**
- Create: `app/src/main/java/com/newsrssreader/data/model/NewsItem.kt`
- Create: `app/src/main/java/com/newsrssreader/data/model/ArticleContent.kt`
- Test: `app/src/test/java/com/newsrssreader/data/model/NewsItemTest.kt`

**Step 1: Write the failing test**

```kotlin
package com.newsrssreader.data.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

class NewsItemTest {

    private fun dateAt(daysAgo: Long, hour: Int, minute: Int): Date {
        val zone = ZoneId.systemDefault()
        val dt = LocalDateTime.now(zone).minusDays(daysAgo)
            .withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        return Date.from(dt.atZone(zone).toInstant())
    }

    @Test
    fun `publishedDate returns empty string when published is null`() {
        val item = NewsItem(id = "1", title = "t", published = null)
        assertEquals("", item.publishedDate())
    }

    @Test
    fun `publishedDate returns time only for today`() {
        val item = NewsItem(id = "1", title = "t", published = dateAt(0, 14, 32))
        assertEquals("14:32", item.publishedDate())
    }

    @Test
    fun `publishedDate returns date and time for other days`() {
        val item = NewsItem(id = "1", title = "t", published = dateAt(3, 9, 5))
        val expectedDay = LocalDateTime.now().minusDays(3).dayOfMonth
        assertEquals(String.format("%d.%02d %02d:%02d",
            expectedDay,
            LocalDateTime.now().minusDays(3).monthValue,
            9, 5), item.publishedDate())
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.newsrssreader.data.model.NewsItemTest"`
Expected: FAIL (compilation error — `NewsItem` doesn't exist yet)

**Step 3: Write `NewsItem.kt`**

```kotlin
package com.newsrssreader.data.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class NewsItem(
    val id: String,
    val title: String? = null,
    val summary: String? = null,
    val authors: List<String>? = null,
    val link: String? = null,
    val updated: Date? = null,
    val categories: List<String>? = null,
    val content: String? = null,
    val published: Date? = null,
    val source: String? = null,
    val rights: String? = null,
    val image: String? = null,
) {
    fun publishedDate(): String {
        val published = this.published ?: return ""
        val now = Calendar.getInstance()
        val pubCal = Calendar.getInstance().apply { time = published }
        val sameDay = now.get(Calendar.YEAR) == pubCal.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == pubCal.get(Calendar.DAY_OF_YEAR)
        val pattern = if (sameDay) "HH:mm" else "d.MM HH:mm"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(published)
    }

    companion object {
        val sample = NewsItem(
            id = "sample",
            title = "Sample headline text for placeholder rows",
            published = Date(),
            image = null,
            link = "https://lenta.ru",
        )
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.newsrssreader.data.model.NewsItemTest"`
Expected: PASS (3 tests)

**Step 5: Write `ArticleContent.kt`** (no test — plain data holder, mirrors iOS enum 1:1)

```kotlin
package com.newsrssreader.data.model

import java.util.Date

sealed class ArticleContentType {
    data class Paragraph(val text: String, val isLead: Boolean = false) : ArticleContentType()
    data class Subheading(val text: String) : ArticleContentType()
    data class Image(val url: String, val caption: String? = null, val credit: String? = null) : ArticleContentType()
    data class Quote(val text: String, val authorName: String, val authorDescription: String? = null) : ArticleContentType()
    data class Author(val name: String, val photo: String? = null, val jobTitle: String? = null) : ArticleContentType()
    data class InfoBox(val text: String) : ArticleContentType()
    data class RelatedMaterial(
        val title: String,
        val description: String? = null,
        val imageUrl: String? = null,
        val articleUrl: String,
        val date: Date? = null,
    ) : ArticleContentType()
}

data class ArticleContent(
    val title: String,
    val image: String? = null,
    val publishedDate: Date? = null,
    val category: String? = null,
    val content: List<ArticleContentType>,
)
```

**Step 6: Commit**

```bash
git add app/src/main/java/com/newsrssreader/data/model app/src/test
git commit -m "Add NewsItem and ArticleContent models with date formatting tests"
```

---

## Task 3: Feed RSS 2.0 parser

> **Revised after source verification (2026-09-13):** the iOS app's actual `LentaRSSParser.swift`
> only ever parses RSS 2.0 — there is no Atom or JSON feed parser anywhere in the codebase,
> despite CLAUDE.md's stale claim otherwise. `LentaFeedService.getFeed` calls `LentaRSSParser`
> unconditionally. The Android port replicates RSS-2.0-only parity, confirmed with the user.
> `NewsItem.id` in iOS is a fresh random UUID per parse (not stable); the Android port instead
> derives a **stable id from the item's `link`** (e.g. a hash of the link string, or the link
> itself), confirmed with the user, since Navigation-Compose routes need a stable key.

**Files:**
- Create: `app/src/main/java/com/newsrssreader/data/network/FeedParser.kt`
- Test: `app/src/test/java/com/newsrssreader/data/network/FeedParserTest.kt`
- Test fixtures: `app/src/test/resources/rss_sample.xml`

**Exact field mapping to replicate** (verified against
`/Volumes/Data/Apps/TestApps/NewsRSSReader/NewsRSSReaderShared/Services/LentaRSSParser.swift`):

Inside each `<item>`:
- `<title>` → `NewsItem.title` (trimmed)
- `<link>` → `NewsItem.link` (trimmed)
- `<author>` → `NewsItem.authors` = single-element `listOf(author)`, or `null` if empty after trim
- `<description>` → `NewsItem.summary` (trimmed, `null` if empty)
- `<category>` → `NewsItem.categories` = single-element `listOf(category)` (only the **last**
  `<category>` tag's text wins if there are multiple — do not accumulate into a multi-element
  list; this is a deliberate parity quirk with the iOS parser, not a bug to "fix")
- `<pubDate>` → parsed with format `"EEE, dd MMM yyyy HH:mm:ss Z"`, `Locale("en", "US")` — assign
  the **same** parsed `Date` to both `NewsItem.published` and `NewsItem.updated` (iOS has no
  separate "updated" source field; both mirror `pubDate`). If parsing fails, both fields become
  `null` — no error thrown, no fallback format attempted.
- `<enclosure url="...">` → its `url` **attribute** (not element text) → `NewsItem.image`, `null`
  if empty. If multiple `<enclosure>` elements appear, the **last** one wins (overwrite, no
  MIME-type filtering, no "best" selection logic) — parity quirk, keep as-is.
- `<guid>` is **not** read/mapped by iOS at all — do not map it either.
- `NewsItem.content`, `NewsItem.rights`, `NewsItem.source` are always `null` from this parser
  (iOS leaves them `null` too — nothing in RSS 2.0 items maps to them here).
- `NewsItem.id`: **Android-specific deviation from iOS** — derive as a stable value from `link`
  (e.g. `link.hashCode().toString()`, or use `link` itself as the id if non-null; fall back to a
  random UUID only if `link` is null/blank, which should not happen in practice for real feed
  items).

No HTML-entity decoding or CDATA-specific handling needed beyond what `XmlPullParser`'s standard
text/CDATA event handling already provides by default (mirrors iOS's reliance on `XMLParser`
defaults — do not add a custom entity decoder for feed parsing).

**Step 1: Write the fixture file**

Create `rss_sample.xml` — a small (3-item) real Lenta.ru-shaped RSS 2.0 payload, each item with
`<title>`, `<link>`, `<pubDate>` (valid `"EEE, dd MMM yyyy HH:mm:ss Z"` format), `<description>`,
`<category>`, and `<enclosure url="..." type="image/jpeg" length="0"/>`. Include one item with a
missing `<enclosure>` (to test the null-image path) and one item with two `<category>` tags (to
verify last-wins behavior is intentionally preserved).

**Step 2: Write the failing test**

```kotlin
package com.newsrssreader.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FeedParserTest {
    private fun fixture(name: String) =
        javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader().readText()

    @Test
    fun `parses RSS 2 dot 0 feed items`() {
        val items = FeedParser.parse(fixture("rss_sample.xml"))
        assertEquals(3, items.size)
        assertNotNull(items.first().title)
        assertNotNull(items.first().link)
        assertNotNull(items.first().published)
        assertEquals(items.first().published, items.first().updated)
    }

    @Test
    fun `item id is stable and derived from link, not random`() {
        val items1 = FeedParser.parse(fixture("rss_sample.xml"))
        val items2 = FeedParser.parse(fixture("rss_sample.xml"))
        assertEquals(items1.first().id, items2.first().id)
    }

    @Test
    fun `missing enclosure yields null image`() {
        val items = FeedParser.parse(fixture("rss_sample.xml"))
        assertNull(items.first { it.image == null }.image)
    }

    @Test
    fun `last category tag wins when multiple are present`() {
        val items = FeedParser.parse(fixture("rss_sample.xml"))
        // Whichever fixture item has two <category> tags — assert categories has exactly 1
        // element and it equals the LAST tag's text, not the first.
        val multiCategoryItem = items.first { it.categories?.isNotEmpty() == true }
        assertEquals(1, multiCategoryItem.categories?.size)
    }
}
```

Adjust exact assertions once the fixture's specific sample text is written (the test author needs
to know which fixture item has the dual `<category>` tags and what the last one's text is).

**Step 3: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.newsrssreader.data.network.FeedParserTest"`
Expected: FAIL (`FeedParser` doesn't exist)

**Step 4: Implement `FeedParser.kt`**

Parse with `android.util.Xml.newPullParser()` (`XmlPullParser`, built into the Android SDK, no
extra dependency), implementing the exact field mapping above. Object with a single
`fun parse(xml: String): List<NewsItem>` entry point.

**Step 5: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.newsrssreader.data.network.FeedParserTest"`
Expected: PASS (4 tests)

**Step 6: Commit**

```bash
git add app/src/main/java/com/newsrssreader/data/network app/src/test
git commit -m "Add RSS 2.0 feed parser with fixture tests"
```

---

## Task 4: LentaFeedService (network client)

**Files:**
- Create: `app/src/main/java/com/newsrssreader/data/network/LentaFeedService.kt`
- Test: `app/src/test/java/com/newsrssreader/data/network/LentaFeedServiceTest.kt`

Reference `/Volumes/Data/Apps/TestApps/NewsRSSReader/NewsRSSReaderShared/Services/LentaFeedService.swift`
in the iOS repo for the exact category key→display-name map and URL construction rules before
writing this task.

**Step 1: Write `LentaFeedService.kt`**

```kotlin
package com.newsrssreader.data.network

import com.newsrssreader.data.model.NewsItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

enum class FeedSource(val path: String) {
    TOP7("top7"),
    LAST24("last24"),
    ALL("news"),
}

object LentaFeedService {
    private const val BASE_URL = "https://lenta.ru/rss"

    // Verified against LentaFeedService.swift — keep in sync if the iOS source changes.
    val categories: LinkedHashMap<String, String> = linkedMapOf(
        "russia" to "Россия",
        "world" to "Мир",
        "ussr" to "Бывший СССР",
        "economics" to "Экономика",
        "forces" to "Силовые структуры",
        "science" to "Наука и техника",
        "culture" to "Культура",
        "sport" to "Спорт",
        "media" to "Интернет и СМИ",
        "style" to "Ценности",
        "travel" to "Путешествия",
        "life" to "Из жизни",
        "realty" to "Среда обитания",
        "wellness" to "Забота о себе",
        "pobeda80" to "Победа",
    )

    private val client = OkHttpClient()

    suspend fun fetchFeed(source: FeedSource, category: String? = null): List<NewsItem> =
        withContext(Dispatchers.IO) {
            val url = buildString {
                append(BASE_URL).append('/').append(source.path)
                if (!category.isNullOrEmpty()) append('/').append(category)
            }
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@use emptyList()
                FeedParser.parse(body)
            }
        }
}
```

**Step 2: Write a test using MockWebServer-free approach (no new dependency)**

Since adding OkHttp's `mockwebserver` would be an extra test-only dependency, instead unit-test
only `FeedParser` (already covered in Task 3) and cover `LentaFeedService`'s URL-building logic
by extracting it into a small pure function and testing that in isolation:

```kotlin
package com.newsrssreader.data.network

import org.junit.Assert.assertEquals
import org.junit.Test

class LentaFeedServiceTest {
    @Test
    fun `categories map matches iOS source count and keys`() {
        // Confirmed against LentaFeedService.swift: 14 entries (CLAUDE.md's "15 predefined
        // categories" is stale/inaccurate — the user has confirmed 14 is correct, do not "fix"
        // this back to 15).
        assertEquals(14, LentaFeedService.categories.size)
        assertEquals("Россия", LentaFeedService.categories["russia"])
        assertEquals("Победа", LentaFeedService.categories["pobeda80"])
    }
}
```

**Step 3: Run test, verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.newsrssreader.data.network.LentaFeedServiceTest"`
Expected: PASS

**Step 4: Commit**

```bash
git add app/src/main/java/com/newsrssreader/data/network app/src/test
git commit -m "Add LentaFeedService network client"
```

---

## Task 5: Article HTML parser

**Files:**
- Create: `app/src/main/java/com/newsrssreader/data/parser/LentaArticleParser.kt`
- Test: `app/src/test/java/com/newsrssreader/data/parser/LentaArticleParserTest.kt`
- Test fixture: `app/src/test/resources/article_sample.html`

Reference `/Volumes/Data/Apps/TestApps/NewsRSSReader/NewsRSSReaderShared/Services/LentaArticleParser.swift`
in the iOS repo closely — this is the most complex piece of parsing logic (must recognize
paragraphs, subheadings, quotes, info boxes, images, author bylines from Lenta.ru's article HTML
structure). Port the same CSS-selector-equivalent logic using a lightweight hand-rolled HTML
tokenizer/DOM (no Jsoup) — check whether the iOS `NewsRSSReaderShared` framework has its own
native HTML parser class (CLAUDE.md mentions "Replace SwiftSoup with lightweight native HTML
parser" in recent commits) and port that same parsing approach/grammar to Kotlin rather than
reinventing it.

**Step 1:** Read the Swift HTML parser and `LentaArticleParser.swift` fully first.

**Step 2:** Save a real sample article HTML page (fetch one Lenta.ru article's HTML, trim it to
the relevant `<article>`/body section) as `article_sample.html`.

**Step 3: Write the failing test**

```kotlin
package com.newsrssreader.data.parser

import com.newsrssreader.data.model.ArticleContentType
import org.junit.Assert.assertTrue
import org.junit.Test

class LentaArticleParserTest {
    private fun fixture(name: String) =
        javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader().readText()

    @Test
    fun `parses paragraphs subheadings and quotes from article html`() {
        val html = fixture("article_sample.html")
        val content = LentaArticleParser.parse(html, title = "Test title")
        assertTrue(content.content.any { it is ArticleContentType.Paragraph })
    }
}
```

**Step 4: Run, verify fails; implement `LentaArticleParser.kt` porting the Swift logic; run again,
verify passes.**

Run: `./gradlew :app:testDebugUnitTest --tests "com.newsrssreader.data.parser.LentaArticleParserTest"`

**Step 5: Commit**

```bash
git add app/src/main/java/com/newsrssreader/data/parser app/src/test
git commit -m "Add Lenta article HTML parser"
```

---

## Task 6: HomeViewModel

**Files:**
- Create: `app/src/main/java/com/newsrssreader/ui/home/HomeViewModel.kt`
- Test: `app/src/test/java/com/newsrssreader/ui/home/HomeViewModelTest.kt`

**Step 1: Write `HomeViewModel.kt`**

```kotlin
package com.newsrssreader.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.newsrssreader.data.model.NewsItem
import com.newsrssreader.data.network.FeedSource
import com.newsrssreader.data.network.LentaFeedService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val tab: Int = 0,
    val firstNews: NewsItem? = null,
    val rssFeed: List<NewsItem> = emptyList(),
    val isLoading: Boolean = true,
    val isShowError: Boolean = false,
)

class HomeViewModel(
    private val feedService: LentaFeedService = LentaFeedService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadFeeds(FeedSource.TOP7)
    }

    fun changeTab(tab: Int) {
        _uiState.value = _uiState.value.copy(tab = tab, rssFeed = emptyList(), isLoading = true)
        val source = when (tab) {
            0 -> FeedSource.TOP7
            1 -> FeedSource.LAST24
            else -> FeedSource.ALL
        }
        loadFeeds(source)
    }

    private fun loadFeeds(source: FeedSource) {
        viewModelScope.launch {
            runCatching { feedService.fetchFeed(source) }
                .onSuccess { feed ->
                    _uiState.value = _uiState.value.copy(
                        firstNews = feed.firstOrNull(),
                        rssFeed = feed.drop(1),
                        isLoading = false,
                        isShowError = false,
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false, isShowError = true)
                }
        }
    }
}
```

**Step 2: Write a test using a fake `LentaFeedService`-shaped fake**

Since `LentaFeedService` is an `object` (singleton) in Task 4, refactor `HomeViewModel` to accept
an interface instead for testability:

```kotlin
// In LentaFeedService.kt, extract:
interface FeedFetcher {
    suspend fun fetchFeed(source: FeedSource, category: String? = null): List<NewsItem>
}
// object LentaFeedService : FeedFetcher { ... same body ... }
```

Then in `HomeViewModel`, change the constructor param type to `FeedFetcher`.

```kotlin
package com.newsrssreader.ui.home

import com.newsrssreader.data.model.NewsItem
import com.newsrssreader.data.network.FeedFetcher
import com.newsrssreader.data.network.FeedSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private class FakeFetcher(private val items: List<NewsItem>) : FeedFetcher {
        override suspend fun fetchFeed(source: FeedSource, category: String?) = items
    }

    @Test
    fun `first feed item becomes firstNews, rest become rssFeed`() = runTest {
        val items = (1..5).map { NewsItem(id = "$it", title = "Item $it") }
        val viewModel = HomeViewModel(FakeFetcher(items))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("1", viewModel.uiState.value.firstNews?.id)
        assertEquals(4, viewModel.uiState.value.rssFeed.size)
    }
}
```

Add `testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")` to
`app/build.gradle.kts` for this test (test-only dependency, does not affect the "minimal external
dependencies" goal for the shipped app).

**Step 3: Run test, verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.newsrssreader.ui.home.HomeViewModelTest"`

**Step 4: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/com/newsrssreader/ui/home app/src/main/java/com/newsrssreader/data/network app/src/test
git commit -m "Add HomeViewModel with tab/feed state and unit test"
```

---

## Task 7: Core list components — NewsRow, ShimmerPlaceholder

**Files:**
- Create: `app/src/main/java/com/newsrssreader/ui/components/NewsRow.kt`
- Create: `app/src/main/java/com/newsrssreader/ui/components/Shimmer.kt`

**Step 1: Implement `Shimmer.kt`** — a `Modifier.shimmer()` extension using
`rememberInfiniteTransition` animating a diagonal `Brush.linearGradient` mask, per the design
doc's Shimmer section (band width 0.3, alpha stops `[0.3, 1.0, 0.3]`, 1.5s linear sweep + 0.25s
delay, repeat forever, no autoreverse). Plain gray `Box` shapes underneath (analogous to
`ShimmerParagraphPlaceholder` rectangles: height 14dp, corner radius 4dp, `Gray@0.3` fill).

**Step 2: Implement `NewsRow.kt`** — `Row` with title (`AppType.rowTitle`, `colors.black`) + date
(`AppType.meta`, `colors.gray`) in a leading `Column`, `Spacer(Modifier.weight(1f))`, then a
60x60dp `AsyncImage` (Coil) with 4dp `RoundedCornerShape`, or a plain dark `Box` placeholder when
`image` is null. Outer `Modifier.padding(10.dp)`. Also add a `NewsRowPlaceholder` (or reuse
`NewsRow(NewsItem.sample)` + `.shimmer()`, matching the iOS `.redacted + .shimmering()` pattern).

**Step 3: No unit test (pure UI)** — will be verified visually in Task 12.

**Step 4: Verify compiles**

Run: `./gradlew :app:compileDebugKotlin`

**Step 5: Commit**

```bash
git add app/src/main/java/com/newsrssreader/ui/components
git commit -m "Add NewsRow and Shimmer components"
```

---

## Task 8: NewsTop (hero banner) and NewsTabs

**Files:**
- Create: `app/src/main/java/com/newsrssreader/ui/components/NewsTop.kt`
- Create: `app/src/main/java/com/newsrssreader/ui/components/NewsTabs.kt`

**Step 1: Implement `NewsTop.kt`** — `Box(Modifier.height(350.dp).clip(...))` with a Coil
`AsyncImage` (crop scale) filling it, a `Brush.verticalGradient(0f to Color.Transparent, 1f to
colors.background)` scrim `Box` on top (starting at the vertical center per the SwiftUI
`startPoint: .center`), and a bottom-aligned `Column` with title (`AppType.heroTitle`,
`colors.white`) + date/category row (`AppType.meta`, `colors.lightGrey`). Outer `Modifier.padding(16.dp)`
content padding matching SwiftUI's `.padding()`. Renders `null`/nothing when `link`/`image` are
null (guard clause, matching iOS).

**Step 2: Implement `NewsTabs.kt`** — `Row(horizontalArrangement = Arrangement.spacedBy(20.dp))`
of 3 uppercase labels ("Главное", "Последнее", "Все" — apply `.uppercase()` in code, don't
hardcode uppercase strings, per the design doc's cross-cutting note), each a clickable `Text` with
`AppType.tabPill`; selected item wrapped in `Modifier.background(colors.lightGrey,
RoundedCornerShape(17.dp)).padding(8.dp)` with `colors.background` text color, unselected plain
`colors.black` text. Outer `Modifier.padding(16.dp)`.

**Step 3: Verify compiles**

Run: `./gradlew :app:compileDebugKotlin`

**Step 4: Commit**

```bash
git add app/src/main/java/com/newsrssreader/ui/components
git commit -m "Add NewsTop hero banner and NewsTabs components"
```

---

## Task 9: MenuView drawer

**Files:**
- Create: `app/src/main/java/com/newsrssreader/ui/components/MenuView.kt`

**Step 1: Implement** — full-height `Column` on `colors.background`, top row with a close (X)
icon button (20dp leading padding), `Divider` (tinted `colors.gray`, 10dp vertical padding), then
a "Главная" item + one item per `LentaFeedService.categories` entry. Each item: `Row` with a 3x20dp
`Box` (red when selected, background-colored otherwise) + text (21sp SemiBold red when selected,
20sp SemiBold white otherwise), 4dp bottom padding, `clickable {}` to select + collapse the menu.
Present this as a full-screen overlay (e.g. `AnimatedVisibility` sliding in from the left) driven
by a `menuShown: Boolean` + `onDismiss`/`onSelectCategory` callbacks from the screen hosting it
(`MainActivity`/top-level `NavHost` wrapper), matching the iOS `ContentView`-level `menuShow`
binding.

**Step 2: Verify compiles**

Run: `./gradlew :app:compileDebugKotlin`

**Step 3: Commit**

```bash
git add app/src/main/java/com/newsrssreader/ui/components/MenuView.kt
git commit -m "Add MenuView category drawer"
```

---

## Task 10: HomeScreen and CategoryScreen

**Files:**
- Create: `app/src/main/java/com/newsrssreader/ui/components/TopPanel.kt`
- Create: `app/src/main/java/com/newsrssreader/ui/home/HomeScreen.kt`
- Create: `app/src/main/java/com/newsrssreader/ui/category/CategoryViewModel.kt`
- Create: `app/src/main/java/com/newsrssreader/ui/category/CategoryScreen.kt`

**Step 1: Implement `TopPanel.kt`** — `Row` on `colors.background`, hamburger `IconButton` (10dp
padding) + "LENTA.RU" logo image/text (120x20dp, 10dp padding), trailing `Spacer(Modifier.weight(1f))`.

**Step 2: Implement `HomeScreen.kt`** — collects `HomeViewModel.uiState`, renders `TopPanel`,
`NewsTop(firstNews)`, `NewsTabs(tab, onTabSelected = viewModel::changeTab)`, then a `LazyColumn`:
7x shimmering `NewsRow` placeholders when `isLoading`, else real rows + `HorizontalDivider()`
between them, each row navigating to `"article/{id}"` on click (pass the whole `NewsItem` via a
shared in-memory holder/SavedStateHandle-friendly repository — Navigation-Compose doesn't
serialize complex objects through routes well, so keep a simple `NewsItemCache` singleton map
keyed by id that both Home/Category screens populate and `ArticleDetailScreen` reads).

**Step 3: Implement `CategoryViewModel.kt` + `CategoryScreen.kt`** — same shape as
`HomeViewModel`/`HomeScreen` but without hero/tabs, with a category-title header
(`AppType.categoryHeader`, 10dp top+leading padding) instead, fetching
`LentaFeedService.fetchFeed(FeedSource.ALL, category = key)`.

**Step 4: Verify compiles**

Run: `./gradlew :app:compileDebugKotlin`

**Step 5: Commit**

```bash
git add app/src/main/java/com/newsrssreader/ui/components/TopPanel.kt app/src/main/java/com/newsrssreader/ui/home/HomeScreen.kt app/src/main/java/com/newsrssreader/ui/category
git commit -m "Add HomeScreen and CategoryScreen"
```

---

## Task 11: Article content block components

**Files:**
- Create: `app/src/main/java/com/newsrssreader/ui/components/article/ParagraphBlock.kt`
- Create: `app/src/main/java/com/newsrssreader/ui/components/article/SubheadingBlock.kt`
- Create: `app/src/main/java/com/newsrssreader/ui/components/article/ImageBlock.kt`
- Create: `app/src/main/java/com/newsrssreader/ui/components/article/QuoteBlock.kt`
- Create: `app/src/main/java/com/newsrssreader/ui/components/article/AuthorBlock.kt`
- Create: `app/src/main/java/com/newsrssreader/ui/components/article/InfoBoxBlock.kt`

Implement each exactly per the design doc's "Shared Article component library" section (fonts,
colors, paddings, corner radii, opacities already enumerated there — no new values to invent).
`QuoteBlock` needs the large red "«" glyph (36sp bold, offset up ~8dp) beside the quote text.
`AuthorBlock` needs a circular Coil image or a gray circle + person icon fallback.

**Step 1-N:** Implement one file at a time, verifying `./gradlew :app:compileDebugKotlin` after
each.

**Final step: Commit**

```bash
git add app/src/main/java/com/newsrssreader/ui/components/article
git commit -m "Add article content block components"
```

---

## Task 12: ArticleDetailScreen and ArticleViewModel

**Files:**
- Create: `app/src/main/java/com/newsrssreader/ui/article/ArticleViewModel.kt`
- Create: `app/src/main/java/com/newsrssreader/ui/article/ArticleDetailScreen.kt`
- Test: `app/src/test/java/com/newsrssreader/ui/article/ArticleViewModelTest.kt`

**Step 1: Write `ArticleViewModel.kt`** — takes a `NewsItem`, exposes `StateFlow<ArticleUiState>`
with `isLoading`/`content: ArticleContent?`/`error: String?`; on init, fetches the raw article
HTML via OkHttp and parses with `LentaArticleParser` (Task 5), on a background dispatcher.

**Step 2: Write a unit test** for the loading/success/error state transitions using a fake
HTML-fetch function injected via constructor (same fake-interface pattern as Task 6).

**Step 3: Run test, verify pass.**

**Step 4: Implement `ArticleDetailScreen.kt`** — `Scaffold` with a `TopAppBar` (inline title
style, back button, trailing share `IconButton` firing `Intent.ACTION_SEND`), scrollable `Column`
on `colors.blackInversed` background: title, date, main image, then dispatch over
`content.content` rendering the matching block component from Task 11 per `ArticleContentType`
subtype (`RelatedMaterial` → skip/no-op, matching iOS). Loading state: 5x shimmering paragraph
placeholders. Error state: warning icon + "Article loading error" + description, matching the
design doc's Error section exactly.

**Step 5: Verify compiles**

Run: `./gradlew :app:compileDebugKotlin`

**Step 6: Commit**

```bash
git add app/src/main/java/com/newsrssreader/ui/article app/src/test
git commit -m "Add ArticleDetailScreen with loading/error/loaded states"
```

---

## Task 13: Navigation graph and MainActivity

**Files:**
- Create: `app/src/main/java/com/newsrssreader/data/NewsItemCache.kt`
- Create: `app/src/main/java/com/newsrssreader/MainActivity.kt`

**Step 1: Implement `NewsItemCache.kt`** — a simple in-memory `object` holding a
`MutableMap<String, NewsItem>`, populated by Home/Category screens before navigating, read by
`ArticleDetailScreen`.

**Step 2: Implement `MainActivity.kt`** — `NavHost` with routes `"home"`,
`"category/{key}"`, `"article/{id}"`; wraps content in `NewsRSSReaderTheme`; hosts the `MenuView`
overlay at the top level (shared `menuShown` state + `selectedCategory` state lifted above the
`NavHost`, mirroring `ContentView`'s role in the iOS app), navigating to `"home"` or
`"category/{key}"` on menu selection.

**Step 3: Verify the app builds a debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`, `app/build/outputs/apk/debug/app-debug.apk` produced.

**Step 4: Commit**

```bash
git add app/src/main/java/com/newsrssreader/data/NewsItemCache.kt app/src/main/java/com/newsrssreader/MainActivity.kt
git commit -m "Wire up navigation graph and MainActivity"
```

---

## Task 14: Manual visual verification against iOS

**Step 1:** Start an Android emulator (`emulator -avd <name>` or via `adb`/existing running
emulator) and install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

**Step 2:** Launch the app, capture screenshots of: Home (loaded + shimmer loading state seen on
first launch/cold cache), Menu drawer open, a Category screen, an Article detail screen (with a
quote block and an info box if the sampled article has them), and the error state (can force by
temporarily pointing the base URL at an invalid host).

**Step 3:** Compare side-by-side against the iOS simulator screenshots taken during design
research (colors, spacing, font sizes, corner radii) and against the values in
`docs/plans/2026-09-13-android-port-design.md`. Note and fix any visible discrepancy.

**Step 4:** Run the full unit test suite once more as a final check.

Run: `./gradlew test`
Expected: `BUILD SUCCESSFUL`, all tests passing.

**Step 5: Final commit**

```bash
git add -A
git commit -m "Fix visual discrepancies found during manual verification"
```

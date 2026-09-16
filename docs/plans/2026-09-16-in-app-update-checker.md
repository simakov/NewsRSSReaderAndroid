# In-App Update Checker Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add an in-app update checker that queries the public GitHub Releases API for this repo,
shows a badge on the drawer's hamburger icon plus an update banner at the bottom of the category
drawer when a newer tagged release exists, and lets the user download + auto-install the new APK
from inside the app.

**Architecture:** A new `UpdateCheckService` (OkHttp GET to
`api.github.com/repos/simakov/NewsRSSReaderAndroid/releases/latest`, following the existing
`LentaFeedService`/`FeedFetcher` pattern) is compared against a new `BuildConfig.GIT_TAG` field
(populated from `git describe --tags` at build time) inside a new `UpdateViewModel`. A new
`UpdateInstaller` (pattern like `ImageSaver.kt`: a plain function-holding object taking `Context`
per call, not stored) downloads the APK to `cacheDir` with progress and triggers install via
`FileProvider` + `ACTION_VIEW`. `MenuView` gets a bottom-pinned `UpdateBanner` composable and
`TopPanel` gets a small red badge dot, both driven by `UpdateViewModel.uiState`, wired up once in
`MainActivity`/`AppRoot`.

**Tech Stack:** Kotlin, Jetpack Compose, OkHttp (existing dependency), `org.json.JSONObject`
(existing pattern from `LentaArticleParser.kt`), JUnit4 + Robolectric for tests.

---

## Notes before starting

- The GitHub repo (`simakov/NewsRSSReaderAndroid`) must be public for the unauthenticated
  `releases/latest` API call to work. Confirm this is done before manual end-to-end testing
  (Task 10) — it's not required for the automated unit tests in Tasks 3 and 5.
- **Follow-up not covered by this plan:** `.claude/skills/release/SKILL.md`'s current step order
  builds the APK (step 4) *before* tagging/pushing (step 6). Since `BuildConfig.GIT_TAG` is read
  from `git describe --tags` at build time, an APK built before the tag exists will report the
  *previous* tag, not the one it's being released under — the shipped APK would always think a
  newer version is available (itself). After this plan lands, the release skill's step order needs
  to change to tag-and-push *before* building the APK. Flag this to whoever picks that up next;
  it's a change to `.claude/skills/release/scripts/*` and `SKILL.md`, not to app code, so it's
  deliberately out of scope here.

---

### Task 1: `BuildConfig.GIT_TAG` from the current git tag

**Files:**
- Modify: `app/build.gradle.kts`
- Test: `app/src/test/java/com/newsrssreader/BuildConfigTest.kt`

**Step 1: Write the failing test**

```kotlin
package com.newsrssreader

import org.junit.Assert.assertTrue
import org.junit.Test

class BuildConfigTest {
    @Test
    fun `GIT_TAG is never blank`() {
        assertTrue(BuildConfig.GIT_TAG.isNotBlank())
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.newsrssreader.BuildConfigTest"`
Expected: FAIL — compilation error, `BuildConfig.GIT_TAG` doesn't exist yet (`buildConfig` isn't
even enabled yet, so `BuildConfig` itself may not exist as a generated class).

**Step 3: Write minimal implementation**

In `app/build.gradle.kts`, add a top-level `val` (above the `android {}` block) that shells out to
git, and reference it inside `defaultConfig`:

```kotlin
// Computed once at configuration time: the most recent reachable git tag, used to populate
// BuildConfig.GIT_TAG so a running app can compare itself against GitHub Releases' tag_name.
// Falls back to "v0.0.0" in a checkout with no tags yet (e.g. a fresh clone before the first
// release), so the build never fails just because no release has happened.
val gitTag: String = run {
    val process = ProcessBuilder("git", "describe", "--tags", "--abbrev=0")
        .directory(rootDir)
        .start()
    process.waitFor()
    val output = if (process.exitValue() == 0) {
        process.inputStream.bufferedReader().readText().trim()
    } else {
        ""
    }
    output.ifBlank { "v0.0.0" }
}
```

Then, inside `android { defaultConfig { ... } }`, add:

```kotlin
        buildConfigField("String", "GIT_TAG", "\"$gitTag\"")
```

And inside `android { buildFeatures { ... } }`, add `buildConfig = true` next to the existing
`compose = true`:

```kotlin
    buildFeatures {
        compose = true
        buildConfig = true
    }
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.newsrssreader.BuildConfigTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add app/build.gradle.kts app/src/test/java/com/newsrssreader/BuildConfigTest.kt
git commit -m "feat: expose current git tag as BuildConfig.GIT_TAG"
```

---

### Task 2: Manifest — install-package permission + FileProvider

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/file_paths.xml`

**Step 1: Add the permission and provider**

In `app/src/main/AndroidManifest.xml`, add the permission next to the existing ones, and the
`<provider>` inside `<application>` (after the closing `</activity>` tag):

```xml
    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

```xml
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
```

**Step 2: Create the path config**

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- Matches where AndroidUpdateInstaller (Task 4) writes the downloaded APK: context.cacheDir. -->
    <cache-path name="updates" path="." />
</paths>
```

**Step 3: Verify the manifest merges cleanly**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (no manifest merger error, no missing resource error for
`@xml/file_paths`).

**Step 4: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/res/xml/file_paths.xml
git commit -m "feat: add install-packages permission and FileProvider for update installs"
```

---

### Task 3: `UpdateCheckService` (GitHub Releases API client)

**Files:**
- Create: `app/src/main/java/com/newsrssreader/data/network/UpdateCheckService.kt`
- Test: `app/src/test/java/com/newsrssreader/data/network/UpdateCheckServiceTest.kt`

**Step 1: Write the failing test**

```kotlin
package com.newsrssreader.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric is required here because UpdateCheckService.parse() uses org.json.JSONObject,
// which throws RuntimeException on a plain local JVM (see CLAUDE.md's dependency philosophy
// note) — same reason LentaArticleParserTest needs it.
@RunWith(RobolectricTestRunner::class)
class UpdateCheckServiceTest {
    private val sampleJson = """
        {
          "tag_name": "v1.4.0",
          "body": "release notes text",
          "assets": [
            {"name": "other.txt", "browser_download_url": "https://example.com/other.txt"},
            {"name": "NewsRSSReader-v1.4.0.apk", "browser_download_url": "https://example.com/NewsRSSReader-v1.4.0.apk"}
          ]
        }
    """.trimIndent()

    @Test
    fun `parse extracts tag, notes, and the apk asset's download url`() {
        val release = UpdateCheckService.parse(sampleJson)
        assertEquals("v1.4.0", release?.tag)
        assertEquals("release notes text", release?.notes)
        assertEquals("https://example.com/NewsRSSReader-v1.4.0.apk", release?.downloadUrl)
    }

    @Test
    fun `parse returns null when no apk asset is present`() {
        val json = """
            {"tag_name": "v1.4.0", "body": "", "assets": [
              {"name": "source.zip", "browser_download_url": "https://example.com/source.zip"}
            ]}
        """.trimIndent()
        assertNull(UpdateCheckService.parse(json))
    }

    @Test
    fun `parse returns null when tag_name is missing`() {
        val json = """{"body": "", "assets": []}"""
        assertNull(UpdateCheckService.parse(json))
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.newsrssreader.data.network.UpdateCheckServiceTest"`
Expected: FAIL — `UpdateCheckService` doesn't exist yet.

**Step 3: Write minimal implementation**

```kotlin
package com.newsrssreader.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

data class UpdateRelease(
    val tag: String,
    val notes: String,
    val downloadUrl: String,
)

interface UpdateChecker {
    suspend fun fetchLatestRelease(): UpdateRelease?
}

object UpdateCheckService : UpdateChecker {
    // Public repo, no auth needed for a GET on releases/latest.
    private const val API_URL =
        "https://api.github.com/repos/simakov/NewsRSSReaderAndroid/releases/latest"

    private val client = OkHttpClient()

    override suspend fun fetchLatestRelease(): UpdateRelease? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(API_URL).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected response ${response.code} for $API_URL")
            }
            val body = response.body?.string() ?: return@use null
            parse(body)
        }
    }

    // Exposed (not private) so UpdateCheckServiceTest can verify parsing without a real network
    // call — same pattern as FeedParser.parse() being tested directly from FeedParserTest.
    internal fun parse(body: String): UpdateRelease? {
        val json = JSONObject(body)
        val tag = json.optString("tag_name")
        if (tag.isBlank()) return null
        val notes = json.optString("body")
        val assets = json.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val url = assets.getJSONObject(i).optString("browser_download_url")
            if (url.endsWith(".apk")) {
                return UpdateRelease(tag, notes, url)
            }
        }
        return null
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.newsrssreader.data.network.UpdateCheckServiceTest"`
Expected: PASS (3 tests)

**Step 5: Commit**

```bash
git add app/src/main/java/com/newsrssreader/data/network/UpdateCheckService.kt \
        app/src/test/java/com/newsrssreader/data/network/UpdateCheckServiceTest.kt
git commit -m "feat: add UpdateCheckService for the GitHub Releases API"
```

---

### Task 4: `UpdateInstaller` (download + trigger install)

Not unit tested, matching this project's existing convention for framework-heavy code
(`ImageSaver.kt` — MediaStore/file I/O — is also not unit tested; verified manually in Task 10).

**Files:**
- Create: `app/src/main/java/com/newsrssreader/data/UpdateInstaller.kt`

**Step 1: Write the implementation directly (no test loop for this file)**

```kotlin
package com.newsrssreader.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

interface UpdateInstaller {
    /** Downloads [url] into the app's cache dir, reporting 0f..1f via [onProgress] as bytes arrive. */
    suspend fun download(context: Context, url: String, onProgress: (Float) -> Unit): File

    /**
     * Starts the system installer for [file]. If the "install unknown apps" permission hasn't
     * been granted for this app, sends the user to the system settings screen for it instead and
     * returns without installing — the caller is expected to retry once the user comes back
     * (see UpdateViewModel.retryInstallIfNeeded, called from MainActivity.onResume).
     */
    fun requestInstall(context: Context, file: File)
}

object AndroidUpdateInstaller : UpdateInstaller {
    private val client = OkHttpClient()

    override suspend fun download(context: Context, url: String, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Unexpected response ${response.code} for $url")
                }
                val responseBody = response.body ?: throw IOException("Empty body for $url")
                val contentLength = responseBody.contentLength()
                val file = File(context.cacheDir, "update.apk")
                responseBody.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesRead += read
                            if (contentLength > 0) {
                                onProgress(bytesRead.toFloat() / contentLength)
                            }
                        }
                    }
                }
                file
            }
        }

    override fun requestInstall(context: Context, file: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }
}
```

Note: `context` here is expected to be an Activity context (e.g. `LocalContext.current` from a
composable), the same convention `ImageSaver.kt`'s `saveImageToGallery` already relies on for its
`Context` parameter.

**Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/newsrssreader/data/UpdateInstaller.kt
git commit -m "feat: add AndroidUpdateInstaller to download and install update APKs"
```

---

### Task 5: `UpdateViewModel`

**Files:**
- Create: `app/src/main/java/com/newsrssreader/ui/update/UpdateViewModel.kt`
- Test: `app/src/test/java/com/newsrssreader/ui/update/UpdateViewModelTest.kt`

**Step 1: Write the failing tests**

```kotlin
package com.newsrssreader.ui.update

import android.content.Context
import com.newsrssreader.BuildConfig
import com.newsrssreader.data.UpdateInstaller
import com.newsrssreader.data.network.UpdateChecker
import com.newsrssreader.data.network.UpdateRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

// Robolectric so RuntimeEnvironment.getApplication() can stand in for a real Context when
// exercising startDownload()/retryInstallIfNeeded() — UpdateInstaller's interface requires one.
@RunWith(RobolectricTestRunner::class)
class UpdateViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeChecker(
        private val release: UpdateRelease?,
        private val shouldThrow: Boolean = false,
    ) : UpdateChecker {
        override suspend fun fetchLatestRelease(): UpdateRelease? {
            if (shouldThrow) throw IOExceptionForTest()
            return release
        }
    }

    private class FakeInstaller(private val shouldThrowOnDownload: Boolean = false) : UpdateInstaller {
        var installRequested = false
        val progressUpdates = mutableListOf<Float>()

        override suspend fun download(context: Context, url: String, onProgress: (Float) -> Unit): File {
            if (shouldThrowOnDownload) throw IOExceptionForTest()
            onProgress(0.5f).also { progressUpdates.add(0.5f) }
            onProgress(1f).also { progressUpdates.add(1f) }
            return File("fake.apk")
        }

        override fun requestInstall(context: Context, file: File) {
            installRequested = true
        }
    }

    private class IOExceptionForTest : Exception("boom")

    @Test
    fun `a newer tag than the current build becomes an available release`() = runTest {
        val release = UpdateRelease(tag = "v999.0.0", notes = "notes", downloadUrl = "https://example.com/app.apk")
        val viewModel = UpdateViewModel(FakeChecker(release), FakeInstaller())
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(release, viewModel.uiState.value.release)
    }

    @Test
    fun `a release tagged the same as the current build is not surfaced`() = runTest {
        val release = UpdateRelease(tag = BuildConfig.GIT_TAG, notes = "notes", downloadUrl = "https://example.com/app.apk")
        val viewModel = UpdateViewModel(FakeChecker(release), FakeInstaller())
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.release)
    }

    @Test
    fun `a failed check leaves release null`() = runTest {
        val viewModel = UpdateViewModel(FakeChecker(null, shouldThrow = true), FakeInstaller())
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.release)
    }

    @Test
    fun `startDownload drives Downloading then ReadyToInstall and triggers install`() = runTest {
        val release = UpdateRelease("v999.0.0", "notes", "https://example.com/app.apk")
        val installer = FakeInstaller()
        val viewModel = UpdateViewModel(FakeChecker(release), installer)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.startDownload(RuntimeEnvironment.getApplication())
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.phase is DownloadPhase.ReadyToInstall)
        assertTrue(installer.installRequested)
        assertEquals(listOf(0.5f, 1f), installer.progressUpdates)
    }

    @Test
    fun `download failure sets Failed phase`() = runTest {
        val release = UpdateRelease("v999.0.0", "notes", "https://example.com/app.apk")
        val viewModel = UpdateViewModel(FakeChecker(release), FakeInstaller(shouldThrowOnDownload = true))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.startDownload(RuntimeEnvironment.getApplication())
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(DownloadPhase.Failed, viewModel.uiState.value.phase)
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.newsrssreader.ui.update.UpdateViewModelTest"`
Expected: FAIL — `UpdateViewModel`/`DownloadPhase` don't exist yet.

**Step 3: Write minimal implementation**

```kotlin
package com.newsrssreader.ui.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.newsrssreader.BuildConfig
import com.newsrssreader.data.AndroidUpdateInstaller
import com.newsrssreader.data.UpdateInstaller
import com.newsrssreader.data.network.UpdateChecker
import com.newsrssreader.data.network.UpdateCheckService
import com.newsrssreader.data.network.UpdateRelease
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface DownloadPhase {
    data object Idle : DownloadPhase
    data class Downloading(val progress: Float) : DownloadPhase
    data class ReadyToInstall(val file: File) : DownloadPhase
    data object Failed : DownloadPhase
}

data class UpdateUiState(
    val release: UpdateRelease? = null,
    val phase: DownloadPhase = DownloadPhase.Idle,
)

class UpdateViewModel(
    private val checker: UpdateChecker = UpdateCheckService,
    private val installer: UpdateInstaller = AndroidUpdateInstaller,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { checker.fetchLatestRelease() }
                .onSuccess { release ->
                    if (release != null && release.tag != BuildConfig.GIT_TAG) {
                        _uiState.value = _uiState.value.copy(release = release)
                    }
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    // Silent: this project's checker convention is "try again next launch",
                    // matching how a failed feed fetch doesn't alert for a background refresh.
                }
        }
    }

    fun startDownload(context: Context) {
        val release = _uiState.value.release ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(phase = DownloadPhase.Downloading(0f))
            runCatching {
                installer.download(context, release.downloadUrl) { progress ->
                    _uiState.value = _uiState.value.copy(phase = DownloadPhase.Downloading(progress))
                }
            }.onSuccess { file ->
                _uiState.value = _uiState.value.copy(phase = DownloadPhase.ReadyToInstall(file))
                installer.requestInstall(context, file)
            }.onFailure { e ->
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(phase = DownloadPhase.Failed)
            }
        }
    }

    /** Called from MainActivity.onResume(): if a download finished but install was deferred
     * (user was sent to the "install unknown apps" settings screen), retry now that they're back. */
    fun retryInstallIfNeeded(context: Context) {
        val phase = _uiState.value.phase
        if (phase is DownloadPhase.ReadyToInstall) {
            installer.requestInstall(context, phase.file)
        }
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.newsrssreader.ui.update.UpdateViewModelTest"`
Expected: PASS (5 tests)

**Step 5: Commit**

```bash
git add app/src/main/java/com/newsrssreader/ui/update/UpdateViewModel.kt \
        app/src/test/java/com/newsrssreader/ui/update/UpdateViewModelTest.kt
git commit -m "feat: add UpdateViewModel driving the check/download/install flow"
```

---

### Task 6: `TopPanel` update badge

**Files:**
- Modify: `app/src/main/java/com/newsrssreader/ui/components/TopPanel.kt`

**Step 1: Add the `showUpdateBadge` parameter and badge dot**

Add the import:

```kotlin
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment.TopEnd
```

(`Alignment` is already imported; just reference `Alignment.TopEnd` directly instead of adding a
second import — see code below.)

Change the function signature:

```kotlin
@Composable
fun TopPanel(
    onMenuClick: () -> Unit,
    onLogoClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    contentHeight: Dp = TopPanelDefaultContentHeight,
    showUpdateBadge: Boolean = false,
) {
```

Change the menu icon `Box` to overlay the badge:

```kotlin
        Box(
            modifier = Modifier
                .clickable(onClick = onMenuClick)
                .padding(TopPanelIconPadding),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menu",
                tint = AppTheme.colors.white,
            )
            if (showUpdateBadge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(8.dp)
                        .background(AppTheme.colors.red, shape = CircleShape),
                )
            }
        }
```

**Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/newsrssreader/ui/components/TopPanel.kt
git commit -m "feat: show an update-available badge on the drawer menu icon"
```

---

### Task 7: `MenuView` — scrollable list + `UpdateBanner`

**Files:**
- Modify: `app/src/main/java/com/newsrssreader/ui/components/MenuView.kt`

**Step 1: Add imports**

```kotlin
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Popup
import com.newsrssreader.data.network.UpdateRelease
import com.newsrssreader.ui.update.DownloadPhase
import com.newsrssreader.ui.update.UpdateUiState
```

**Step 2: Change `MenuView`'s signature and wrap the category list**

```kotlin
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
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) {}
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Row(
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
```

(`MenuItem` stays unchanged below.)

**Step 3: Add the `UpdateBanner` composable**

```kotlin
@Composable
private fun UpdateBanner(release: UpdateRelease, phase: DownloadPhase, onUpdateClick: () -> Unit) {
    var showNotes by remember { mutableStateOf(false) }

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

        Box(modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)) {
            Text(
                text = "Что нового",
                style = AppTheme.type.meta,
                color = AppTheme.colors.red,
                modifier = Modifier.clickable { showNotes = !showNotes },
            )
            if (showNotes) {
                Popup(alignment = Alignment.BottomStart, onDismissRequest = { showNotes = false }) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 260.dp)
                            .background(AppTheme.colors.black, shape = RoundedCornerShape(8.dp))
                            .clickable { showNotes = false }
                            .padding(12.dp),
                    ) {
                        Text(
                            text = release.notes,
                            style = AppTheme.type.meta,
                            color = AppTheme.colors.white,
                        )
                    }
                }
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
            else -> {
                Button(onClick = onUpdateClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Обновить")
                }
            }
        }
    }
}
```

**Step 4: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add app/src/main/java/com/newsrssreader/ui/components/MenuView.kt
git commit -m "feat: add UpdateBanner to the bottom of the category drawer"
```

---

### Task 8: Thread `showUpdateBadge` through `HomeScreen`/`CategoryScreen`

**Files:**
- Modify: `app/src/main/java/com/newsrssreader/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/newsrssreader/ui/category/CategoryScreen.kt`

**Step 1: `HomeScreen.kt`** — add the parameter and forward it:

```kotlin
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onMenuClick: () -> Unit,
    onArticleClick: (String) -> Unit,
    showUpdateBadge: Boolean = false,
    modifier: Modifier = Modifier,
) {
```

```kotlin
        TopPanel(
            onMenuClick = onMenuClick,
            onLogoClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
            contentHeight = HomeTopPanelHeight,
            showUpdateBadge = showUpdateBadge,
        )
```

**Step 2: `CategoryScreen.kt`** — same change:

```kotlin
fun CategoryScreen(
    categoryKey: String,
    viewModel: CategoryViewModel = viewModel { CategoryViewModel(categoryKey) },
    onMenuClick: () -> Unit,
    onArticleClick: (String) -> Unit,
    showUpdateBadge: Boolean = false,
    modifier: Modifier = Modifier,
) {
```

```kotlin
        TopPanel(
            onMenuClick = onMenuClick,
            onLogoClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
            showUpdateBadge = showUpdateBadge,
        )
```

**Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/newsrssreader/ui/home/HomeScreen.kt \
        app/src/main/java/com/newsrssreader/ui/category/CategoryScreen.kt
git commit -m "feat: thread showUpdateBadge into HomeScreen and CategoryScreen"
```

---

### Task 9: Wire `UpdateViewModel` into `MainActivity`/`AppRoot`

**Files:**
- Modify: `app/src/main/java/com/newsrssreader/MainActivity.kt`

**Step 1: Add imports**

```kotlin
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.newsrssreader.ui.update.UpdateViewModel
```

**Step 2: Hold the ViewModel at the Activity level and retry install in `onResume`**

```kotlin
class MainActivity : ComponentActivity() {
    // Held here (not just inside AppRoot's viewModel()) so onResume can reach it directly to
    // retry an install that was deferred while the user was sent to the system settings screen
    // for the "install unknown apps" permission.
    private val updateViewModel: UpdateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // ... unchanged splash screen / edge-to-edge setup above ...
        setContent {
            NewsRSSReaderTheme {
                AppRoot(updateViewModel = updateViewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateViewModel.retryInstallIfNeeded(this)
    }
}
```

**Step 3: Pass the view model into `AppRoot` and wire `MenuView`/screens**

```kotlin
@Composable
fun AppRoot(updateViewModel: UpdateViewModel = viewModel()) {
    val navController = rememberNavController()
    var menuShown by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val updateUiState by updateViewModel.uiState.collectAsStateWithLifecycle()
    val showUpdateBadge = updateUiState.release != null

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val selectedCategory = when (currentRoute) {
        "category/{key}" -> backStackEntry?.arguments?.getString("key") ?: ""
        else -> ""
    }

    BackHandler(enabled = menuShown) { menuShown = false }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    onMenuClick = { menuShown = true },
                    onArticleClick = { id -> navController.navigate("article/$id") },
                    showUpdateBadge = showUpdateBadge,
                )
            }
            composable(
                route = "category/{key}",
                arguments = listOf(navArgument("key") { type = NavType.StringType }),
            ) { entry ->
                val key = entry.arguments?.getString("key").orEmpty()
                CategoryScreen(
                    categoryKey = key,
                    onMenuClick = { menuShown = true },
                    onArticleClick = { id -> navController.navigate("article/$id") },
                    showUpdateBadge = showUpdateBadge,
                )
            }
            // ... article/{id} and photo/{encodedUrl} composables unchanged ...
        }

        AnimatedVisibility(
            visible = menuShown,
            enter = slideInHorizontally(initialOffsetX = { -it }),
            exit = slideOutHorizontally(targetOffsetX = { -it }),
        ) {
            MenuView(
                selectedCategory = selectedCategory,
                onDismiss = { menuShown = false },
                onCategorySelected = { key ->
                    menuShown = false
                    if (key == selectedCategory) {
                        // Already there — nothing to do.
                    } else if (key.isEmpty()) {
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = true }
                        }
                    } else {
                        navController.navigate("category/$key") {
                            popUpTo("home")
                        }
                    }
                },
                updateState = updateUiState,
                onUpdateClick = { updateViewModel.startDownload(context) },
            )
        }
    }
}
```

**Step 4: Verify it compiles and the app runs**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

Install and launch it (see Task 10 for the full manual check):

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.newsrssreader/.MainActivity
```

Expected: app launches normally; no badge/banner shows yet (no newer GitHub release exists yet
relative to whatever tag this build reports).

**Step 5: Commit**

```bash
git add app/src/main/java/com/newsrssreader/MainActivity.kt
git commit -m "feat: wire UpdateViewModel into AppRoot and retry installs on resume"
```

---

### Task 10: Manual end-to-end verification

This can't be fully automated (it depends on the real public GitHub repo having a release ahead
of whatever tag the test build reports), so walk through it by hand once the repo is public and
at least one `vX.Y.Z` release with an `.apk` asset exists:

1. Confirm the repo is public: open `https://api.github.com/repos/simakov/NewsRSSReaderAndroid/releases/latest`
   in a browser — it should return JSON, not a 404/auth error.
2. Build and install a debug APK from a commit whose `git describe --tags` resolves to a tag
   *older* than the latest GitHub release's tag (e.g. check out the commit right before the
   latest release tag, or temporarily create a lower-numbered local tag on HEAD — don't push it).
   ```bash
   ./gradlew :app:assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb shell am start -n com.newsrssreader/.MainActivity
   ```
3. Open the app: the hamburger icon should show the small red badge dot.
4. Open the drawer: the "Доступно обновление" banner should be pinned at the bottom, below the
   scrollable category list.
5. Tap "Что нового": a popup with the release notes text should appear; tapping inside or outside
   it should dismiss it.
6. Tap "Обновить": the button should be replaced by a progress bar + percentage that advances to
   100%, then the system package installer should open automatically.
   - If this is the first time, Android may instead show "For your security, your phone is not
     allowed to install unknown apps from this source" — confirm the flow lands on the "install
     unknown apps" settings screen for this app, toggle it on, and press back. The installer
     screen should now appear on its own (via `MainActivity.onResume` → `retryInstallIfNeeded`) —
     you should NOT have to tap "Обновить" again.
7. Confirm the install screen shows the correct new version name, install it, and relaunch — the
   badge/banner should be gone (its `BuildConfig.GIT_TAG` now matches the release it was built
   from — assuming the release skill's step order has been fixed per the note at the top of this
   plan; until then this specific check will still show a badge for itself).
8. Run the full automated suite once more to make sure nothing else regressed:
   ```bash
   ./gradlew test
   ```
   Expected: BUILD SUCCESSFUL, all tests green.

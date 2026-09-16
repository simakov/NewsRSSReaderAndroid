package com.newsrssreader.ui.update

import android.content.Context
import com.newsrssreader.BuildConfig
import com.newsrssreader.data.UpdateInstaller
import com.newsrssreader.data.network.UpdateChecker
import com.newsrssreader.data.network.UpdateRelease
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        var installRequestCount = 0
        var downloadCallCount = 0
        var canInstallResult = true
        val progressUpdates = mutableListOf<Float>()

        override suspend fun download(context: Context, url: String, onProgress: (Float) -> Unit): File {
            downloadCallCount++
            if (shouldThrowOnDownload) throw IOExceptionForTest()
            onProgress(0.5f).also { progressUpdates.add(0.5f) }
            onProgress(1f).also { progressUpdates.add(1f) }
            return File("fake.apk")
        }

        override fun requestInstall(context: Context, file: File) {
            installRequested = true
            installRequestCount++
        }

        override fun canInstall(context: Context) = canInstallResult
    }

    /**
     * An installer whose [download] suspends on a [CompletableDeferred] gate, so a test can
     * control exactly when it resolves and inspect [UpdateViewModel]'s state mid-flight — same
     * pattern as HomeViewModelTest's GatedFetcher.
     */
    private class GatedInstaller : UpdateInstaller {
        val gate = CompletableDeferred<File>()
        var downloadCallCount = 0

        override suspend fun download(context: Context, url: String, onProgress: (Float) -> Unit): File {
            downloadCallCount++
            onProgress(0.5f)
            return gate.await()
        }

        override fun requestInstall(context: Context, file: File) = Unit

        override fun canInstall(context: Context) = true
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

    @Test
    fun `calling startDownload twice back-to-back only triggers one download`() = runTest {
        val release = UpdateRelease("v999.0.0", "notes", "https://example.com/app.apk")
        val installer = FakeInstaller()
        val viewModel = UpdateViewModel(FakeChecker(release), installer)
        dispatcher.scheduler.advanceUntilIdle()

        val context = RuntimeEnvironment.getApplication()
        viewModel.startDownload(context)
        viewModel.startDownload(context)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, installer.downloadCallCount)
    }

    @Test
    fun `startDownload does nothing when there is no available release`() = runTest {
        val installer = FakeInstaller()
        val viewModel = UpdateViewModel(FakeChecker(null), installer)
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.release)

        viewModel.startDownload(RuntimeEnvironment.getApplication())
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, installer.downloadCallCount)
        assertFalse(installer.installRequested)
        assertEquals(DownloadPhase.Idle, viewModel.uiState.value.phase)
    }

    @Test
    fun `retryInstallIfNeeded requests install only when phase is ReadyToInstall`() = runTest {
        val release = UpdateRelease("v999.0.0", "notes", "https://example.com/app.apk")
        val installer = FakeInstaller()
        val viewModel = UpdateViewModel(FakeChecker(release), installer)
        dispatcher.scheduler.advanceUntilIdle()
        val context = RuntimeEnvironment.getApplication()

        // Idle: no-op.
        viewModel.retryInstallIfNeeded(context)
        assertEquals(0, installer.installRequestCount)

        viewModel.startDownload(context)
        // Downloading: no-op.
        viewModel.retryInstallIfNeeded(context)
        assertEquals(0, installer.installRequestCount)

        dispatcher.scheduler.advanceUntilIdle()
        // ReadyToInstall after the download completes: startDownload's own success path already
        // requested install once.
        assertEquals(1, installer.installRequestCount)

        // Calling again (simulating MainActivity.onResume after the settings-screen detour)
        // requests install again.
        viewModel.retryInstallIfNeeded(context)
        assertEquals(2, installer.installRequestCount)
    }

    @Test
    fun `retryInstallIfNeeded does not call requestInstall when the install permission still isn't granted`() = runTest {
        val release = UpdateRelease("v999.0.0", "notes", "https://example.com/app.apk")
        val installer = FakeInstaller()
        val viewModel = UpdateViewModel(FakeChecker(release), installer)
        dispatcher.scheduler.advanceUntilIdle()
        val context = RuntimeEnvironment.getApplication()

        viewModel.startDownload(context)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.phase is DownloadPhase.ReadyToInstall)

        // startDownload's own success path already requested install once, via canInstall==true.
        assertEquals(1, installer.installRequestCount)
        installer.installRequested = false

        // Simulate the user backing out of the Settings screen without granting the permission:
        // a subsequent passive onResume-triggered retry must not blindly re-open Settings.
        installer.canInstallResult = false
        viewModel.retryInstallIfNeeded(context)

        assertFalse(installer.installRequested)
        assertEquals(1, installer.installRequestCount)
    }

    @Test
    fun `retryInstallIfNeeded is a no-op after a failed download`() = runTest {
        val release = UpdateRelease("v999.0.0", "notes", "https://example.com/app.apk")
        val installer = FakeInstaller(shouldThrowOnDownload = true)
        val viewModel = UpdateViewModel(FakeChecker(release), installer)
        dispatcher.scheduler.advanceUntilIdle()
        val context = RuntimeEnvironment.getApplication()

        viewModel.startDownload(context)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(DownloadPhase.Failed, viewModel.uiState.value.phase)

        viewModel.retryInstallIfNeeded(context)
        assertEquals(0, installer.installRequestCount)
    }

    @Test
    fun `uiState exposes intermediate Downloading progress mid-flight`() = runTest {
        val release = UpdateRelease("v999.0.0", "notes", "https://example.com/app.apk")
        val installer = GatedInstaller()
        val viewModel = UpdateViewModel(FakeChecker(release), installer)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.startDownload(RuntimeEnvironment.getApplication())
        dispatcher.scheduler.runCurrent()

        val phase = viewModel.uiState.value.phase
        assertTrue(phase is DownloadPhase.Downloading)
        assertEquals(0.5f, (phase as DownloadPhase.Downloading).progress)

        installer.gate.complete(File("fake.apk"))
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.phase is DownloadPhase.ReadyToInstall)
        assertEquals(1, installer.downloadCallCount)
    }
}

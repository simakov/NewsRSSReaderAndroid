package com.newsrssreader.ui.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.newsrssreader.AppInfo
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
    // False in the fdroid flavor, where the F-Droid client does the updating. Injectable for the
    // same reason `checker` is: the tests exercise the update flow itself and must not depend on
    // which flavor the suite happens to be running under.
    private val updateCheckEnabled: Boolean = BuildConfig.UPDATE_CHECK_ENABLED,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    init {
        // No network call at all when self-updating is off, rather than fetching and discarding:
        // an F-Droid build should not be reaching for the GitHub API in the first place.
        if (updateCheckEnabled) viewModelScope.launch {
            runCatching { checker.fetchLatestRelease() }
                .onSuccess { release ->
                    // AppInfo.versionTag is this build's own tag, derived from the versionName
                    // literal in build.gradle.kts that the release commit bumps. That makes the
                    // comparison exact without the old, fragile requirement that the APK be built
                    // only *after* the tag already existed locally.
                    if (release != null && release.tag != AppInfo.versionTag) {
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
        // Guard set synchronously (not inside the coroutine below) so that two calls made
        // back-to-back, before the dispatcher has run either one, still only start one download.
        if (_uiState.value.phase is DownloadPhase.Downloading) return
        _uiState.value = _uiState.value.copy(phase = DownloadPhase.Downloading(0f))
        viewModelScope.launch {
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
     * (user was sent to the "install unknown apps" settings screen), retry now that they're back.
     * Only actually retries once the install permission has genuinely been granted — otherwise
     * every passive app resume (switching apps and back, pulling down notifications, etc.) would
     * blindly re-trigger the Settings redirect again. */
    fun retryInstallIfNeeded(context: Context) {
        val phase = _uiState.value.phase
        if (phase is DownloadPhase.ReadyToInstall && installer.canInstall(context)) {
            installer.requestInstall(context, phase.file)
        }
    }
}

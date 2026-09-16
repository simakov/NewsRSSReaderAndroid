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
     * (user was sent to the "install unknown apps" settings screen), retry now that they're back. */
    fun retryInstallIfNeeded(context: Context) {
        val phase = _uiState.value.phase
        if (phase is DownloadPhase.ReadyToInstall) {
            installer.requestInstall(context, phase.file)
        }
    }
}

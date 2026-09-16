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

    /** True if this app currently has permission to install packages (i.e. requestInstall
     * would actually trigger the system installer rather than redirecting to Settings). */
    fun canInstall(context: Context): Boolean
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

    override fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()
}

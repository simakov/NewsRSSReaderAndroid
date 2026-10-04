package com.newsrssreader.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import coil.imageLoader
import com.newsrssreader.data.store.CachedThenNetworkImageSource
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What [detectImageType] recognized: the file extension to use and the MIME type to share it as. */
internal data class SharedImageType(val extension: String, val mimeType: String)

/**
 * Names the format of [bytes] from its leading magic bytes, or null if it isn't one of the four
 * Lenta.ru serves. Going by content rather than by the URL's extension keeps the shared file's
 * type honest even when the URL has none.
 */
internal fun detectImageType(bytes: ByteArray): SharedImageType? {
    fun startsWith(vararg prefix: Int, offset: Int = 0) =
        bytes.size >= offset + prefix.size && prefix.indices.all { (bytes[offset + it].toInt() and 0xFF) == prefix[it] }
    return when {
        startsWith(0xFF, 0xD8, 0xFF) -> SharedImageType("jpg", "image/jpeg")
        startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> SharedImageType("png", "image/png")
        startsWith(0x47, 0x49, 0x46, 0x38) -> SharedImageType("gif", "image/gif")
        startsWith(0x52, 0x49, 0x46, 0x46) && startsWith(0x57, 0x45, 0x42, 0x50, offset = 8) ->
            SharedImageType("webp", "image/webp")
        else -> null
    }
}

/** The URL's base name, reduced to filename-safe characters and capped, plus [type]'s extension. */
internal fun shareFileName(imageUrl: String, type: SharedImageType): String {
    val base = imageUrl.substringAfterLast('/').substringBefore('?').substringBeforeLast('.')
        .replace(Regex("[^A-Za-z0-9_-]"), "_")
        .take(40)
        .ifBlank { "image" }
    return "$base.${type.extension}"
}

/**
 * Hands the original bytes of [imageUrl] to the system share sheet, which is also how a reader
 * saves it: no storage permission is needed, and nothing is re-encoded on the way.
 *
 * The bytes come from Coil's disk cache when the photo viewer has just shown them, and from the
 * network otherwise. They are written to `cacheDir/shared/` and exposed through the app's
 * [FileProvider]; that directory is emptied first, so at most one shared photo is ever left behind.
 */
suspend fun shareImage(context: Context, imageUrl: String): Boolean {
    val prepared = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = CachedThenNetworkImageSource(context).load(imageUrl) ?: return@runCatching null
            val type = detectImageType(bytes) ?: return@runCatching null
            val dir = File(context.cacheDir, "shared").apply { deleteRecursively(); mkdirs() }
            val file = File(dir, shareFileName(imageUrl, type)).apply { writeBytes(bytes) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) to type
        }.getOrNull()
    }
    if (prepared == null) {
        showToast(context, "Не удалось поделиться изображением")
        return false
    }
    val (uri, type) = prepared
    val send = Intent(Intent.ACTION_SEND).apply {
        this.type = type.mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    return true
}

/** Exposed (not private) so the photo viewer can reuse the app's Russian-UI Toast convention. */
internal fun showToast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

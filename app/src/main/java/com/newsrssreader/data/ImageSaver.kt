package com.newsrssreader.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import coil.ImageLoader
import coil.request.ImageRequest
import java.io.File
import java.io.FileOutputStream

/**
 * Downloads [imageUrl] via Coil and saves it into the device's Pictures/NewsRSSReader gallery
 * folder, using the scoped-storage MediaStore API on API 29+ and a direct public-directory write
 * (guarded by the legacy WRITE_EXTERNAL_STORAGE permission, checked by the caller before invoking
 * this) on API 26-28. Shows a Russian-language Toast reporting success/failure, matching the
 * rest of the app's UI language.
 */
suspend fun saveImageToGallery(context: Context, imageLoader: ImageLoader, imageUrl: String): Boolean {
    val bitmap = loadBitmap(context, imageLoader, imageUrl)
    if (bitmap == null) {
        showToast(context, "Не удалось сохранить изображение")
        return false
    }

    val fileName = fileNameFor(imageUrl)
    val saved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        saveViaMediaStore(context, bitmap, fileName)
    } else {
        saveViaLegacyStorage(context, bitmap, fileName)
    }

    showToast(context, if (saved) "Изображение сохранено" else "Не удалось сохранить изображение")
    return saved
}

private suspend fun loadBitmap(context: Context, imageLoader: ImageLoader, imageUrl: String): Bitmap? {
    return try {
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .allowHardware(false)
            .build()
        val result = imageLoader.execute(request)
        result.drawable?.let { drawableToBitmap(it) }
    } catch (e: Exception) {
        null
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable) {
        return drawable.bitmap
    }
    val width = drawable.intrinsicWidth.coerceAtLeast(1)
    val height = drawable.intrinsicHeight.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

private fun fileNameFor(imageUrl: String): String {
    val lastSegment = imageUrl.substringAfterLast('/').substringBefore('?').ifBlank { "image" }
    val sanitized = lastSegment.substringBeforeLast('.').take(40).ifBlank { "image" }
    return "${sanitized}_${System.currentTimeMillis()}"
}

private fun saveViaMediaStore(context: Context, bitmap: Bitmap, fileName: String): Boolean {
    return try {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/NewsRSSReader",
            )
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return false
        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        } ?: return false
        true
    } catch (e: Exception) {
        false
    }
}

private fun saveViaLegacyStorage(context: Context, bitmap: Bitmap, fileName: String): Boolean {
    return try {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "NewsRSSReader",
        )
        if (!directory.exists() && !directory.mkdirs()) {
            return false
        }
        val file = File(directory, "$fileName.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
        true
    } catch (e: Exception) {
        false
    }
}

private fun showToast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

package com.newsrssreader.data.store

import android.content.Context
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Where [BookmarkStore] gets the bytes of an image it is about to save beside a bookmark.
 *
 * An interface for the same reason `FeedFetcher` and `ArticleHtmlFetcher` are ones: it lets the
 * store's file handling be tested without a network or an image loader.
 */
internal interface BookmarkImageSource {

    /** The bytes behind [url], or null if they can't be had. */
    suspend fun load(url: String): ByteArray?

    /** Saves nothing. The store's state before [BookmarkStore.init] has run. */
    object None : BookmarkImageSource {
        override suspend fun load(url: String): ByteArray? = null
    }
}

/**
 * Takes the image from Coil's own disk cache when it is there, and downloads it only when it isn't.
 *
 * The cache hit is the normal case, not an optimization: bookmarking happens on an article the
 * reader is looking at, so its pictures were just decoded and are already on disk. Coil keys that
 * cache on the model, which for these requests is the URL string. A key that misses costs a
 * download, never a wrong image, so this needs no guarantee from Coil beyond that.
 */
internal class CachedThenNetworkImageSource(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient(),
) : BookmarkImageSource {

    override suspend fun load(url: String): ByteArray? = withContext(Dispatchers.IO) {
        fromCoilCache(url) ?: download(url)
    }

    private fun fromCoilCache(url: String): ByteArray? = runCatching {
        val cache = context.imageLoader.diskCache ?: return null
        cache.openSnapshot(url)?.use { snapshot ->
            snapshot.data.toFile().readBytes()
        }
    }.getOrNull()

    private fun download(url: String): ByteArray? = runCatching {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.bytes()
        }
    }.getOrNull()
}

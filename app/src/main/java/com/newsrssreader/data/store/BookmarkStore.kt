package com.newsrssreader.data.store

import android.content.Context
import com.newsrssreader.data.model.ArticleContent
import com.newsrssreader.data.model.NewsItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The articles the reader saved, with their text and pictures, readable with no network.
 *
 * ### Layout
 *
 * ```
 * filesDir/bookmarks/
 * ├── index.json          every bookmark's NewsItem snapshot, newest first
 * └── <article id>/
 *     ├── body.json       the parsed ArticleContent (absent if it never loaded)
 *     ├── images.json     original image URL -> the file below that holds it
 *     └── img/<n>         the image bytes
 * ```
 *
 * The split is what keeps both screens cheap: the bookmarks list needs one read of `index.json`
 * and never touches a saved body, while saving or deleting one bookmark rewrites only that
 * bookmark's directory plus the index — not every other article's text.
 *
 * ### What the in-memory snapshot says
 *
 * [bookmarks] carries each item with its `image` already pointing at the local copy when there is
 * one, so the list and the article header read from disk rather than the network whether or not
 * there is a connection. `index.json` keeps the original remote URLs, so that rewriting is a
 * presentation detail and never overwrites the snapshot itself.
 *
 * ### Failure
 *
 * Nothing here throws at a caller. An unreadable index means "no bookmarks" rather than a crash on
 * launch; an image that won't save leaves the bookmark saved and that one picture remote. A
 * bookmark is the user's own decision and is never expired on a timer, unlike [ReadStateStore]'s
 * marks.
 */
object BookmarkStore {

    private const val IndexFile = "index.json"
    private const val BodyFile = "body.json"
    private const val ImageMapFile = "images.json"
    private const val ImageDir = "img"

    data class Bookmark(
        /** The snapshot exactly as it was fetched, remote image URL and all. This is what `index.json` stores. */
        val item: NewsItem,
        val savedAt: Long,
        /** Whether the article's text was saved too; false for a reader-mode-only article. */
        val hasBody: Boolean,
        /**
         * [item] with its image pointing at the local copy, once there is one. This is what the
         * screens render and what seeds `NewsItemCache`, so a bookmark's picture comes off the disk
         * whether or not there is a connection. Computed once here rather than per read: it is on
         * the bookmarks list's scroll path.
         */
        val displayItem: NewsItem = item,
    )

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())

    /** Every bookmark, newest first. Empty until [init]'s load completes. */
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    private val _bookmarkedIds = MutableStateFlow<Set<String>>(emptySet())

    /** Just the ids, for the article screen's bookmark button. */
    val bookmarkedIds: StateFlow<Set<String>> = _bookmarkedIds.asStateFlow()

    // Test seams, set by init() in production. Same approach as ReadStateStore.writeScope.
    internal var root: File? = null
    internal var images: BookmarkImageSource = BookmarkImageSource.None
    internal var writeScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val lock = Any()

    /**
     * Points the store at its directory and loads the index. The load is asynchronous: both
     * screens that care read [bookmarks]/[bookmarkedIds] as state, so nothing has to block the
     * first frame on a file read.
     */
    fun init(context: Context) {
        val app = context.applicationContext
        root = File(app.filesDir, "bookmarks")
        images = CachedThenNetworkImageSource(app)
        writeScope.launch { load() }
    }

    fun isBookmarked(id: String): Boolean = id in _bookmarkedIds.value

    /**
     * Saves [item], with [content] when the article body has already been parsed.
     *
     * The in-memory state changes at once so the button responds to the tap, and the files are
     * written after. The write runs on [writeScope], which outlives the article screen — leaving
     * for the feed the instant after tapping must not abandon a half-copied bookmark.
     */
    fun add(item: NewsItem, content: ArticleContent? = null) {
        if (root == null || isBookmarked(item.id)) return
        val entry = Bookmark(item = item, savedAt = System.currentTimeMillis(), hasBody = content != null)
        synchronized(lock) { publish(listOf(entry) + _bookmarks.value) }

        writeScope.launch {
            val dir = directory(item.id) ?: return@launch
            dir.mkdirs()
            if (content != null) {
                writeAtomically(File(dir, BodyFile), ArticleContentJson.encode(content))
            }
            // With no parsed body, the hero image is still worth having: it is what the bookmarks
            // list shows, and an article kept for its reader-mode page has nothing else offline.
            val wanted = content?.let(ArticleContentJson::imageUrls) ?: listOfNotNull(item.image)
            val saved = saveImages(dir, wanted, existing = emptyMap())
            writeImageMap(dir, saved)
            refreshLocalImage(item.id, saved)
            persistIndex()
        }
    }

    /**
     * Fills in the body of an already-saved bookmark once the parse that was still running when it
     * was saved finishes. A no-op if the article isn't bookmarked any more, or already has a body.
     */
    fun attachBody(id: String, content: ArticleContent) {
        val existing = _bookmarks.value.firstOrNull { it.item.id == id } ?: return
        if (existing.hasBody) return
        synchronized(lock) {
            publish(_bookmarks.value.map { if (it.item.id == id) it.copy(hasBody = true) else it })
        }

        writeScope.launch {
            val dir = directory(id) ?: return@launch
            dir.mkdirs()
            writeAtomically(File(dir, BodyFile), ArticleContentJson.encode(content))
            val alreadySaved = readImageMap(dir)
            val saved = saveImages(dir, ArticleContentJson.imageUrls(content), alreadySaved)
            writeImageMap(dir, saved)
            refreshLocalImage(id, saved)
            persistIndex()
        }
    }

    fun remove(id: String) {
        if (root == null) return
        synchronized(lock) { publish(_bookmarks.value.filterNot { it.item.id == id }) }
        writeScope.launch {
            directory(id)?.deleteRecursively()
            persistIndex()
        }
    }

    /**
     * The saved text of [id], with its images pointed at the local copies, or null when this
     * bookmark has no saved body (or none is stored at all).
     *
     * Used as the article screen's fallback when the network fetch fails — see
     * `ArticleViewModel`.
     *
     * Blocking file work, and deliberately without a `withContext` of its own: the caller picks
     * the dispatcher. `ArticleViewModel` runs its whole fetch-and-parse pipeline on an injected
     * dispatcher precisely so tests can drive it on virtual time, and a hardcoded
     * `Dispatchers.IO` in here would put this one step outside that.
     */
    suspend fun body(id: String): ArticleContent? {
        val dir = directory(id) ?: return null
        val raw = runCatching { File(dir, BodyFile).takeIf(File::exists)?.readText() }.getOrNull()
            ?: return null
        val content = ArticleContentJson.decode(raw) ?: return null
        val map = readImageMap(dir)
        return ArticleContentJson.withRewrittenImages(content) { url ->
            map[url]?.let { relative -> localUri(dir, relative) }
        }
    }

    // ---- loading ----

    private suspend fun load() = withContext(Dispatchers.IO) {
        val dir = root ?: return@withContext
        val raw = runCatching { File(dir, IndexFile).takeIf(File::exists)?.readText() }.getOrNull()
            ?: return@withContext
        val parsed = runCatching { JSONObject(raw) }.getOrNull() ?: return@withContext
        val array = parsed.optJSONArray("bookmarks") ?: return@withContext

        val loaded = (0 until array.length()).mapNotNull { index ->
            val entry = array.optJSONObject(index) ?: return@mapNotNull null
            val item = entry.optJSONObject("item")?.let(NewsItemJson::decode) ?: return@mapNotNull null
            val articleDir = File(dir, item.id)
            val localImage = item.image?.let { readImageMap(articleDir)[it] }
            Bookmark(
                item = item,
                savedAt = entry.optLong("savedAt"),
                hasBody = entry.optBoolean("hasBody"),
                displayItem = localImage
                    ?.let { item.copy(image = localUri(articleDir, it)) }
                    ?: item,
            )
        }
        synchronized(lock) { publish(loaded) }
    }

    // ---- persistence ----

    // Must be called inside `lock`: the two flows are one piece of state, and a reader that saw a
    // new id in `bookmarkedIds` but the old list in `bookmarks` would render a bookmark that
    // isn't there.
    private fun publish(entries: List<Bookmark>) {
        _bookmarks.value = entries
        _bookmarkedIds.value = entries.mapTo(HashSet(entries.size)) { it.item.id }
    }

    /**
     * Writes the index from whatever is currently in memory. In-memory state is the authority here
     * (it is what the user just acted on), so a late-finishing write simply reflects the newest
     * state rather than resurrecting an older one.
     */
    private fun persistIndex() {
        val dir = root ?: return
        val entries = _bookmarks.value
        val array = JSONArray()
        entries.forEach { bookmark ->
            array.put(
                JSONObject()
                    // `item`, never `displayItem`: the stored snapshot must describe the article,
                    // not this install's filesystem, and the remote URL is the key `images.json`
                    // maps from.
                    .put("item", NewsItemJson.encode(bookmark.item))
                    .put("savedAt", bookmark.savedAt)
                    .put("hasBody", bookmark.hasBody),
            )
        }
        dir.mkdirs()
        writeAtomically(File(dir, IndexFile), JSONObject().put("bookmarks", array).toString())
    }

    /** Re-publishes [id] with its hero image pointing at the copy just saved for it. */
    private fun refreshLocalImage(id: String, saved: Map<String, String>) {
        val dir = directory(id) ?: return
        synchronized(lock) {
            val entries = _bookmarks.value.map { bookmark ->
                if (bookmark.item.id != id) return@map bookmark
                val relative = bookmark.item.image?.let { saved[it] } ?: return@map bookmark
                bookmark.copy(displayItem = bookmark.item.copy(image = localUri(dir, relative)))
            }
            publish(entries)
        }
    }

    // ---- images ----

    /**
     * Copies each of [urls] not already in [existing] into `<dir>/img/`, returning the full
     * original-URL-to-relative-path map.
     *
     * A URL that can't be fetched is left out rather than failing the save: the article keeps its
     * remote address for that one picture and everything else about the bookmark still works.
     */
    private suspend fun saveImages(
        dir: File,
        urls: List<String>,
        existing: Map<String, String>,
    ): Map<String, String> {
        val result = existing.toMutableMap()
        val imageDir = File(dir, ImageDir).apply { mkdirs() }
        var next = result.size
        urls.distinct().forEach { url ->
            if (result.containsKey(url)) return@forEach
            val bytes = runCatching { images.load(url) }.getOrNull() ?: return@forEach
            val relative = "$ImageDir/$next"
            val written = runCatching { File(imageDir, next.toString()).writeBytes(bytes) }.isSuccess
            if (written) {
                result[url] = relative
                next++
            }
        }
        return result
    }

    private fun readImageMap(dir: File): Map<String, String> {
        val raw = runCatching { File(dir, ImageMapFile).takeIf(File::exists)?.readText() }
            .getOrNull() ?: return emptyMap()
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        return json.keys().asSequence().mapNotNull { key ->
            json.optStringOrNull(key)?.let { key to it }
        }.toMap()
    }

    private fun writeImageMap(dir: File, map: Map<String, String>) {
        val json = JSONObject()
        map.forEach { (url, relative) -> json.put(url, relative) }
        writeAtomically(File(dir, ImageMapFile), json.toString())
    }

    private fun localUri(dir: File, relative: String): String = "file://${File(dir, relative).absolutePath}"

    private fun directory(id: String): File? = root?.let { File(it, id) }

    /**
     * Writes via a temporary file and a rename, so a process killed mid-write leaves the previous
     * version intact instead of a half-written one — the index in particular is read on every
     * launch, and truncating it would lose every bookmark rather than the one being saved.
     */
    private fun writeAtomically(target: File, contents: String) {
        runCatching {
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, "${target.name}.tmp")
            temp.writeText(contents)
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
        }
    }

    /** Test seam: forgets everything in memory and unbinds the store from its directory. */
    internal fun resetForTest() {
        synchronized(lock) { publish(emptyList()) }
        root = null
        images = BookmarkImageSource.None
    }

    /** Test seam: re-reads the index from disk, as a fresh process would. */
    internal suspend fun reloadForTest() = load()
}

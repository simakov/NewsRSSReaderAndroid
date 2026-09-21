package com.newsrssreader.data.store

import android.content.Context
import android.content.SharedPreferences
import android.os.Process
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Remembers which articles have been opened, so the news list can dim the ones already read.
 *
 * A process-wide singleton in the style of [com.newsrssreader.data.NewsItemCache] and
 * [com.newsrssreader.data.NewsFeedContext], but unlike those it outlives the process: it is backed
 * by SharedPreferences, with the format and the expiry rule in [ReadStateCodec].
 *
 * Reads are free. The whole set is held in memory behind [readIds] and hydrated once by [init], so
 * a list row deciding whether it is read never touches the disk — this is on the scroll path.
 *
 * A mark is not a valuable thing: losing one means a headline shows undimmed, which is why every
 * failure here is swallowed and the feature simply does nothing rather than taking a screen down
 * with it.
 */
object ReadStateStore {

    private const val PrefsName = "read_state"
    private const val MarksKey = "marks"

    private val _readIds = MutableStateFlow<Set<String>>(emptySet())

    /** The ids marked read, as a set the news screens collect. Empty until [init] has run. */
    val readIds: StateFlow<Set<String>> = _readIds.asStateFlow()

    // Written once by init() on the main thread before any marking can happen, then only read.
    @Volatile
    private var prefs: SharedPreferences? = null

    // Entries in insertion order, mirroring what is on disk. Guarded by `lock` rather than left to
    // the StateFlow because a mark is a read-modify-write of the whole list.
    private var entries: List<ReadStateCodec.Entry> = emptyList()
    private val lock = Any()

    // Overridable so tests can run the writes on a dispatcher they control, the same seam
    // `ArticleViewModel` and friends use for their IO dispatcher.
    internal var writeScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Loads the marks into memory and expires the stale ones. Called from
     * [com.newsrssreader.NewsRssReaderApplication].
     *
     * Expiry costs nothing extra: the whole list is being parsed here anyway, so filtering it as
     * it is read leaves the in-memory set immediately correct. Only writing the trimmed list back
     * is deferred, and that write runs at background thread priority — it must not compete with
     * the first feed request while the user is watching a shimmer — and is skipped entirely when
     * nothing expired, so an ordinary launch doesn't touch the disk at all.
     */
    fun init(context: Context) {
        val preferences = runCatching {
            context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
        }.getOrNull() ?: return
        prefs = preferences

        val stored = runCatching { preferences.getString(MarksKey, null) }.getOrNull().orEmpty()
        val loaded = ReadStateCodec.decode(stored)
        val pruned = ReadStateCodec.prune(loaded, today())

        synchronized(lock) { entries = pruned }
        _readIds.value = pruned.mapTo(HashSet(pruned.size)) { it.id }

        if (pruned !== loaded) {
            writeScope.launch {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                persist(ReadStateCodec.encode(pruned))
            }
        }
    }

    /**
     * Marks [id] read. A no-op when it already is, so re-opening an article costs neither a
     * recomposition nor a disk write.
     */
    fun markRead(id: String) {
        val preferences = prefs ?: return
        val serialized = synchronized(lock) {
            if (entries.any { it.id == id }) return
            val updated = ReadStateCodec.prune(entries + ReadStateCodec.Entry(id, today()), today())
            entries = updated
            ReadStateCodec.encode(updated)
        }
        _readIds.value = _readIds.value + id
        writeScope.launch { persist(serialized, preferences) }
    }

    private fun persist(serialized: String, preferences: SharedPreferences? = prefs) {
        val target = preferences ?: return
        // commit(), not apply(): this is already off the main thread, and commit() reports
        // failure, which apply() cannot.
        runCatching { target.edit().putString(MarksKey, serialized).commit() }
    }

    private fun today(): Long = LocalDate.now().toEpochDay()

    /** Test seam: drops all in-memory and stored state so each test starts from empty. */
    internal fun resetForTest() {
        synchronized(lock) { entries = emptyList() }
        _readIds.value = emptySet()
        prefs?.edit()?.remove(MarksKey)?.commit()
        prefs = null
    }
}

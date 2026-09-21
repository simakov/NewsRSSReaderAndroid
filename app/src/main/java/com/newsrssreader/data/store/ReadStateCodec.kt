package com.newsrssreader.data.store

/**
 * The on-disk format of the read-article marks, and the rule that expires them.
 *
 * Split out of [ReadStateStore] so the format and the expiry rule are testable as pure functions,
 * with no Context, no SharedPreferences and no clock involved.
 *
 * One `id,day` line per mark, where `day` is a day number counted from the epoch
 * (`LocalDate.toEpochDay`). Day granularity rather than milliseconds: nothing here needs
 * finer resolution than "how many days ago", and it halves the stored size. Ids are
 * `link.hashCode().toString()` (see `FeedParser.stableId`), so they never contain a comma or a
 * newline and need no escaping.
 *
 * Insertion order is meaningful — it is the eviction order for [MaxEntries].
 */
internal object ReadStateCodec {

    /**
     * How long a read mark lives. Lenta.ru's RSS window is measured in days, so an article whose
     * mark has expired left the feed long ago: in practice the expiry is invisible, and the point
     * of it is purely to bound the file.
     */
    const val RetentionDays = 14L

    /**
     * A ceiling on the number of marks, as a safety net rather than the primary bound — that is
     * [RetentionDays]. It only ever engages if someone opens thousands of articles inside the
     * retention window.
     */
    const val MaxEntries = 5000

    data class Entry(val id: String, val day: Long)

    fun encode(entries: List<Entry>): String =
        entries.joinToString("\n") { "${it.id},${it.day}" }

    fun decode(raw: String): List<Entry> =
        if (raw.isEmpty()) {
            emptyList()
        } else {
            raw.lineSequence().mapNotNull { line ->
                // A line that doesn't parse is skipped rather than fatal: a truncated write (the
                // process killed mid-commit, a full disk) should cost the marks it damaged, not
                // the whole history.
                val separator = line.lastIndexOf(',')
                if (separator <= 0) return@mapNotNull null
                val day = line.substring(separator + 1).toLongOrNull() ?: return@mapNotNull null
                Entry(line.substring(0, separator), day)
            }.toList()
        }

    /**
     * [entries] with expired and surplus marks removed, or the *same list instance* when there was
     * nothing to remove — callers rely on that identity to skip an otherwise pointless disk write
     * on every launch.
     *
     * Marks dated in the future are kept: that means the device clock moved backwards, and
     * dropping them would be indistinguishable from expiring them decades early.
     */
    fun prune(entries: List<Entry>, today: Long): List<Entry> {
        val oldestKept = today - RetentionDays
        val live = entries.filter { it.day >= oldestKept }
        val bounded = if (live.size > MaxEntries) live.takeLast(MaxEntries) else live
        return if (bounded.size == entries.size) entries else bounded
    }
}

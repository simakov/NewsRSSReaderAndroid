package com.newsrssreader.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests: the read-state format and its expiry rule are deliberately kept out of the
 * Android-dependent part of [ReadStateStore] so they can be pinned down without a Context, a
 * SharedPreferences or a clock.
 */
class ReadStateCodecTest {

    private val today = 20_000L

    @Test
    fun `round trips entries`() {
        val entries = listOf(
            ReadStateCodec.Entry("123", today),
            // Ids are `link.hashCode().toString()`, so a leading minus sign is ordinary input.
            ReadStateCodec.Entry("-456", today - 3),
        )
        assertEquals(entries, ReadStateCodec.decode(ReadStateCodec.encode(entries)))
    }

    @Test
    fun `decode tolerates an empty store`() {
        assertEquals(emptyList<ReadStateCodec.Entry>(), ReadStateCodec.decode(""))
    }

    @Test
    fun `decode drops lines it cannot parse instead of failing`() {
        val decoded = ReadStateCodec.decode("123,20000\ngarbage\n456,notanumber\n\n789,19999")
        assertEquals(
            listOf(ReadStateCodec.Entry("123", 20_000L), ReadStateCodec.Entry("789", 19_999L)),
            decoded,
        )
    }

    @Test
    fun `prune keeps marks inside the retention window`() {
        val entries = listOf(
            ReadStateCodec.Entry("fresh", today),
            ReadStateCodec.Entry("thirteenDaysOld", today - 13),
        )
        assertEquals(entries, ReadStateCodec.prune(entries, today))
    }

    @Test
    fun `prune drops marks older than the retention window`() {
        val entries = listOf(
            ReadStateCodec.Entry("fifteenDaysOld", today - 15),
            ReadStateCodec.Entry("fresh", today),
        )
        assertEquals(
            listOf(ReadStateCodec.Entry("fresh", today)),
            ReadStateCodec.prune(entries, today),
        )
    }

    @Test
    fun `the retention boundary itself is kept`() {
        val entries = listOf(ReadStateCodec.Entry("exactlyAtTheEdge", today - ReadStateCodec.RetentionDays))
        assertEquals(entries, ReadStateCodec.prune(entries, today))
    }

    @Test
    fun `prune tolerates a mark dated in the future`() {
        // A device whose clock was moved back would otherwise strand such an entry forever.
        val entries = listOf(ReadStateCodec.Entry("fromTomorrow", today + 1))
        assertEquals(entries, ReadStateCodec.prune(entries, today))
    }

    @Test
    fun `prune enforces the entry ceiling by dropping the oldest additions`() {
        val entries = (1..ReadStateCodec.MaxEntries + 10).map {
            ReadStateCodec.Entry("id$it", today)
        }
        val pruned = ReadStateCodec.prune(entries, today)
        assertEquals(ReadStateCodec.MaxEntries, pruned.size)
        // Insertion order is the eviction order, so the surviving window is the newest tail.
        assertEquals("id11", pruned.first().id)
        assertEquals("id${ReadStateCodec.MaxEntries + 10}", pruned.last().id)
    }

    @Test
    fun `pruning nothing returns the very same list so callers can skip the disk write`() {
        val entries = listOf(ReadStateCodec.Entry("fresh", today))
        // Reference equality, not just structural: the store uses it to decide whether to write.
        assertTrue(ReadStateCodec.prune(entries, today) === entries)
    }
}

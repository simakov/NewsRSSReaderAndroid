package com.newsrssreader.data.store

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class ReadStateStoreTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("read_state", Context.MODE_PRIVATE)
    private val today get() = LocalDate.now().toEpochDay()

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
        ReadStateStore.resetForTest()
        // Unconfined so the store's background writes have landed by the time the call returns;
        // what is being tested here is what ends up on disk, not when.
        ReadStateStore.writeScope = CoroutineScope(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        ReadStateStore.resetForTest()
    }

    private fun store(vararg entries: ReadStateCodec.Entry) {
        prefs.edit().putString("marks", ReadStateCodec.encode(entries.toList())).commit()
    }

    @Test
    fun `hydrates the stored marks on init`() {
        store(ReadStateCodec.Entry("a", today), ReadStateCodec.Entry("b", today - 1))
        ReadStateStore.init(context)
        assertEquals(setOf("a", "b"), ReadStateStore.readIds.value)
    }

    @Test
    fun `starts empty when nothing has ever been stored`() {
        ReadStateStore.init(context)
        assertEquals(emptySet<String>(), ReadStateStore.readIds.value)
    }

    @Test
    fun `marking read is visible immediately and survives a restart`() {
        ReadStateStore.init(context)
        ReadStateStore.markRead("42")
        assertTrue("42" in ReadStateStore.readIds.value)

        // Simulates process death: drop everything in memory, then load again from disk.
        ReadStateStore.resetForTest()
        assertFalse("42" in ReadStateStore.readIds.value)
        store(ReadStateCodec.Entry("42", today))
        ReadStateStore.init(context)
        assertTrue("42" in ReadStateStore.readIds.value)
    }

    @Test
    fun `marking read writes the mark through to preferences`() {
        ReadStateStore.init(context)
        ReadStateStore.markRead("42")
        assertEquals(
            listOf(ReadStateCodec.Entry("42", today)),
            ReadStateCodec.decode(prefs.getString("marks", null).orEmpty()),
        )
    }

    @Test
    fun `init expires marks past the retention window`() {
        store(
            ReadStateCodec.Entry("stale", today - ReadStateCodec.RetentionDays - 1),
            ReadStateCodec.Entry("fresh", today),
        )
        ReadStateStore.init(context)

        assertEquals(setOf("fresh"), ReadStateStore.readIds.value)
        // The trimmed list is written back, so the expired mark is gone from disk too.
        assertEquals(
            listOf(ReadStateCodec.Entry("fresh", today)),
            ReadStateCodec.decode(prefs.getString("marks", null).orEmpty()),
        )
    }

    @Test
    fun `init does not touch the disk when nothing expired`() {
        store(ReadStateCodec.Entry("fresh", today))
        // A launch with nothing to expire must not rewrite the file; removing the key and finding
        // it still absent afterwards is the only way to observe "no write happened".
        prefs.edit().remove("marks").commit()
        store(ReadStateCodec.Entry("fresh", today))
        val before = prefs.getString("marks", null)

        ReadStateStore.init(context)
        assertEquals(before, prefs.getString("marks", null))
    }

    @Test
    fun `marking an already read article changes nothing`() {
        store(ReadStateCodec.Entry("a", today - 3))
        ReadStateStore.init(context)
        ReadStateStore.markRead("a")
        // Still one entry, and still carrying its original date rather than being refreshed to
        // today — re-opening an article must not extend its retention or cost a write.
        assertEquals(
            listOf(ReadStateCodec.Entry("a", today - 3)),
            ReadStateCodec.decode(prefs.getString("marks", null).orEmpty()),
        )
    }

    @Test
    fun `marking read before init is dropped rather than crashing`() {
        // No init() has run, so there is nowhere to put the mark. It must be a silent no-op:
        // read state is a convenience, and it may never take a screen down with it.
        ReadStateStore.markRead("42")
        assertEquals(emptySet<String>(), ReadStateStore.readIds.value)
        assertNull(prefs.getString("marks", null))
    }

    @Test
    fun `a corrupt store is treated as a partial history rather than a failure`() {
        prefs.edit().putString("marks", "a,${today}\n\u0000garbled\nb,${today}").commit()
        ReadStateStore.init(context)
        assertEquals(setOf("a", "b"), ReadStateStore.readIds.value)
    }
}

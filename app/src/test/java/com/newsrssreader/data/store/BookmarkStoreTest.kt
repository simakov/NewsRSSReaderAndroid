package com.newsrssreader.data.store

import com.newsrssreader.data.model.ArticleContent
import com.newsrssreader.data.model.ArticleContentType
import com.newsrssreader.data.model.NewsItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Robolectric-backed for `org.json`, but otherwise plain file-system work against a temp folder —
 * the store's Context dependency is only there to find `filesDir` and build an image source, both
 * of which are injected here.
 */
@RunWith(RobolectricTestRunner::class)
class BookmarkStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var root: File

    /** Hands back predictable bytes per URL, and nothing at all for [failing]. */
    private class FakeImages(val failing: Set<String> = emptySet()) : BookmarkImageSource {
        val requested = mutableListOf<String>()
        override suspend fun load(url: String): ByteArray? {
            requested += url
            return if (url in failing) null else "bytes:$url".toByteArray()
        }
    }

    private var images = FakeImages()

    private val item = NewsItem(
        id = "-12345",
        title = "Заголовок",
        link = "https://lenta.ru/news/2026/09/21/x/",
        image = "https://icdn.lenta.ru/hero.jpg",
        categories = listOf("Россия"),
    )

    private val content = ArticleContent(
        title = "Заголовок",
        image = "https://icdn.lenta.ru/hero.jpg",
        content = listOf(
            ArticleContentType.Paragraph("Текст"),
            ArticleContentType.Image("https://icdn.lenta.ru/body.jpg", caption = "Подпись"),
        ),
    )

    @Before
    fun setUp() {
        root = folder.newFolder("bookmarks")
        images = FakeImages()
        BookmarkStore.resetForTest()
        BookmarkStore.root = root
        BookmarkStore.images = images
        // Unconfined so each call's file writes have landed before the assertions run.
        BookmarkStore.writeScope = CoroutineScope(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        BookmarkStore.resetForTest()
    }

    @Test
    fun `adding a bookmark makes it visible at once`() {
        BookmarkStore.add(item, content)
        assertTrue(BookmarkStore.isBookmarked(item.id))
        assertEquals(listOf(item.id), BookmarkStore.bookmarks.value.map { it.item.id })
    }

    @Test
    fun `a bookmark survives a restart`() = runTest {
        BookmarkStore.add(item, content)
        BookmarkStore.resetForTest()
        assertFalse(BookmarkStore.isBookmarked(item.id))

        BookmarkStore.root = root
        BookmarkStore.images = images
        BookmarkStore.reloadForTest()

        assertEquals(listOf(item.id), BookmarkStore.bookmarks.value.map { it.item.id })
        val restored = BookmarkStore.bookmarks.value.single()
        assertEquals(item.title, restored.item.title)
        assertEquals(item.link, restored.item.link)
        assertEquals(item.categories, restored.item.categories)
        assertTrue(restored.hasBody)
    }

    @Test
    fun `newest bookmark comes first`() {
        val older = item
        val newer = item.copy(id = "-999", title = "Позже")
        BookmarkStore.add(older, content)
        BookmarkStore.add(newer, content)
        assertEquals(listOf("-999", "-12345"), BookmarkStore.bookmarks.value.map { it.item.id })
    }

    @Test
    fun `removing a bookmark deletes its files`() {
        BookmarkStore.add(item, content)
        val dir = File(root, item.id)
        assertTrue(dir.exists())

        BookmarkStore.remove(item.id)

        assertFalse(BookmarkStore.isBookmarked(item.id))
        assertTrue(BookmarkStore.bookmarks.value.isEmpty())
        assertFalse(dir.exists())
    }

    @Test
    fun `adding the same article twice changes nothing`() {
        BookmarkStore.add(item, content)
        BookmarkStore.add(item, content)
        assertEquals(1, BookmarkStore.bookmarks.value.size)
    }

    @Test
    fun `the saved body comes back with its images pointing at local files`() = runTest {
        BookmarkStore.add(item, content)

        val body = BookmarkStore.body(item.id)!!
        val bodyImage = body.content.filterIsInstance<ArticleContentType.Image>().single()
        assertTrue("expected a file URL, got ${bodyImage.url}", bodyImage.url.startsWith("file://"))
        assertEquals(
            "bytes:https://icdn.lenta.ru/body.jpg",
            File(bodyImage.url.removePrefix("file://")).readText(),
        )
        // Everything that isn't an image URL is unchanged.
        assertEquals(content.content[0], body.content[0])
        assertEquals("Подпись", bodyImage.caption)
    }

    @Test
    fun `the hero image is served from disk once it has been saved`() {
        BookmarkStore.add(item, content)
        val saved = BookmarkStore.bookmarks.value.single()

        assertTrue(saved.displayItem.image!!.startsWith("file://"))
        // The faithful snapshot keeps the remote URL, since that is what images.json is keyed on.
        assertEquals("https://icdn.lenta.ru/hero.jpg", saved.item.image)
    }

    @Test
    fun `an image that cannot be fetched leaves the bookmark saved and that url remote`() = runTest {
        images = FakeImages(failing = setOf("https://icdn.lenta.ru/body.jpg"))
        BookmarkStore.images = images

        BookmarkStore.add(item, content)

        assertTrue(BookmarkStore.isBookmarked(item.id))
        val body = BookmarkStore.body(item.id)!!
        val bodyImage = body.content.filterIsInstance<ArticleContentType.Image>().single()
        assertEquals("https://icdn.lenta.ru/body.jpg", bodyImage.url)
        // The one that did succeed is still local.
        assertTrue(body.image!!.startsWith("file://"))
    }

    @Test
    fun `bookmarking before the body has loaded saves the hero image and nothing else`() = runTest {
        BookmarkStore.add(item, content = null)

        assertFalse(BookmarkStore.bookmarks.value.single().hasBody)
        assertNull(BookmarkStore.body(item.id))
        assertEquals(listOf("https://icdn.lenta.ru/hero.jpg"), images.requested)
    }

    @Test
    fun `attachBody fills in the text of a bookmark saved while it was still loading`() = runTest {
        BookmarkStore.add(item, content = null)
        BookmarkStore.attachBody(item.id, content)

        assertTrue(BookmarkStore.bookmarks.value.single().hasBody)
        assertEquals(2, BookmarkStore.body(item.id)!!.content.size)
        // The hero was already on disk from the add(), so it is not fetched a second time.
        assertEquals(
            listOf("https://icdn.lenta.ru/hero.jpg", "https://icdn.lenta.ru/body.jpg"),
            images.requested,
        )
    }

    @Test
    fun `attachBody is ignored for an article that is not bookmarked`() = runTest {
        BookmarkStore.attachBody(item.id, content)
        assertTrue(BookmarkStore.bookmarks.value.isEmpty())
        assertNull(BookmarkStore.body(item.id))
    }

    @Test
    fun `attachBody does not overwrite a body that is already saved`() = runTest {
        BookmarkStore.add(item, content)
        val replacement = content.copy(content = listOf(ArticleContentType.Paragraph("Другое")))
        BookmarkStore.attachBody(item.id, replacement)
        assertEquals(2, BookmarkStore.body(item.id)!!.content.size)
    }

    @Test
    fun `a corrupt index is read as no bookmarks rather than failing the launch`() = runTest {
        File(root, "index.json").writeText("{\"bookmarks\": [")
        BookmarkStore.reloadForTest()
        assertTrue(BookmarkStore.bookmarks.value.isEmpty())
    }

    @Test
    fun `an index entry without an id is skipped and the rest still load`() = runTest {
        BookmarkStore.add(item, content)
        val index = File(root, "index.json")
        index.writeText(
            index.readText().replace(
                "\"bookmarks\":[",
                "\"bookmarks\":[{\"savedAt\":1,\"hasBody\":false,\"item\":{}},",
            ),
        )
        BookmarkStore.reloadForTest()
        assertEquals(listOf(item.id), BookmarkStore.bookmarks.value.map { it.item.id })
    }

    @Test
    fun `nothing is written and nothing crashes before the store has a directory`() {
        BookmarkStore.resetForTest()
        BookmarkStore.add(item, content)
        assertTrue(BookmarkStore.bookmarks.value.isEmpty())
        assertFalse(File(root, "index.json").exists())
    }

    @Test
    fun `body is null for an article that was never bookmarked`() = runBlocking {
        assertNull(BookmarkStore.body("nope"))
    }
}

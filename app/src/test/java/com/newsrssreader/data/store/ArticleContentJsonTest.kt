package com.newsrssreader.data.store

import com.newsrssreader.data.model.ArticleContent
import com.newsrssreader.data.model.ArticleContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Date

/**
 * Robolectric-backed because [ArticleContentJson] is built on `org.json`, which is a real
 * Android-framework class with no JVM implementation — the same reason `LentaArticleParserTest`
 * needs it.
 */
@RunWith(RobolectricTestRunner::class)
class ArticleContentJsonTest {

    // Deliberately holds one of every ArticleContentType: a new block type added to the sealed
    // hierarchy without a matching encode/decode branch fails this test rather than silently
    // disappearing from saved bookmarks.
    private val everyBlockType = ArticleContent(
        title = "Заголовок",
        image = "https://icdn.lenta.ru/hero.jpg",
        publishedDate = Date(1_700_000_000_000L),
        category = "Бывший СССР",
        announce = "Краткое изложение",
        content = listOf(
            ArticleContentType.Paragraph("Первый абзац", isLead = true),
            ArticleContentType.Paragraph("Обычный абзац"),
            ArticleContentType.Subheading("Подзаголовок"),
            ArticleContentType.Image(
                url = "https://icdn.lenta.ru/body.jpg",
                caption = "Подпись",
                credit = "Фото: агентство",
            ),
            ArticleContentType.Quote(
                text = "Цитата",
                authorName = "Имя",
                authorDescription = "должность",
            ),
            ArticleContentType.Author(
                name = "Автор",
                photo = "https://icdn.lenta.ru/author.jpg",
                jobTitle = "корреспондент",
            ),
            ArticleContentType.InfoBox("Справка"),
            ArticleContentType.RelatedMaterial(
                title = "Похожий материал",
                description = "Описание",
                imageUrl = "https://icdn.lenta.ru/related.jpg",
                articleUrl = "https://lenta.ru/news/related/",
                date = Date(1_600_000_000_000L),
            ),
        ),
    )

    @Test
    fun `round trips every block type`() {
        val decoded = ArticleContentJson.decode(ArticleContentJson.encode(everyBlockType))
        assertEquals(everyBlockType, decoded)
    }

    @Test
    fun `round trips content whose optional fields are all absent`() {
        val sparse = ArticleContent(
            title = "Только заголовок",
            content = listOf(
                ArticleContentType.Image(url = "https://icdn.lenta.ru/only.jpg"),
                ArticleContentType.Quote(text = "Цитата", authorName = "Имя"),
                ArticleContentType.Author(name = "Автор"),
                ArticleContentType.RelatedMaterial(
                    title = "Материал",
                    articleUrl = "https://lenta.ru/news/x/",
                ),
            ),
        )
        assertEquals(sparse, ArticleContentJson.decode(ArticleContentJson.encode(sparse)))
    }

    @Test
    fun `decode returns null for malformed json`() {
        assertNull(ArticleContentJson.decode("{\"title\":"))
        assertNull(ArticleContentJson.decode(""))
    }

    @Test
    fun `decode skips blocks with an unknown type instead of failing`() {
        val json = ArticleContentJson.encode(everyBlockType)
            .replace("\"paragraph\"", "\"someFutureBlock\"")
        val decoded = ArticleContentJson.decode(json)
        assertEquals(
            everyBlockType.content.filterNot { it is ArticleContentType.Paragraph },
            decoded?.content,
        )
    }

    @Test
    fun `image urls can be rewritten without touching any other field`() {
        val local = ArticleContentJson.withRewrittenImages(everyBlockType) { url ->
            "file:///data/$url"
        }
        assertEquals("file:///data/https://icdn.lenta.ru/hero.jpg", local.image)
        assertEquals(
            "file:///data/https://icdn.lenta.ru/body.jpg",
            (local.content[3] as ArticleContentType.Image).url,
        )
        assertEquals(
            "file:///data/https://icdn.lenta.ru/author.jpg",
            (local.content[5] as ArticleContentType.Author).photo,
        )
        // Everything that isn't an image URL is carried through untouched.
        assertEquals(everyBlockType.title, local.title)
        assertEquals(everyBlockType.content[0], local.content[0])
    }

    @Test
    fun `image urls left unmapped keep their original value`() {
        val unchanged = ArticleContentJson.withRewrittenImages(everyBlockType) { null }
        assertEquals(everyBlockType, unchanged)
    }

    @Test
    fun `imageUrls lists every image the content references`() {
        assertEquals(
            listOf(
                "https://icdn.lenta.ru/hero.jpg",
                "https://icdn.lenta.ru/body.jpg",
                "https://icdn.lenta.ru/author.jpg",
            ),
            ArticleContentJson.imageUrls(everyBlockType),
        )
    }
}

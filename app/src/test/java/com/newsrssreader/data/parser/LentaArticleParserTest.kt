package com.newsrssreader.data.parser

import com.newsrssreader.data.model.ArticleContentType
import com.newsrssreader.data.model.NewsItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LentaArticleParserTest {
    private fun fixture(name: String) =
        javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader().readText()

    private fun sampleNewsItem() = NewsItem(id = "1", title = "RSS Title", image = "https://example.com/rss.jpg")

    @Test
    fun `parses paragraphs including lead paragraph detection`() {
        val html = fixture("article_sample.html")
        val content = LentaArticleParser.parse(html, sampleNewsItem())

        val paragraphs = content.content.filterIsInstance<ArticleContentType.Paragraph>()
        assertTrue(paragraphs.isNotEmpty())

        val lead = paragraphs.first { it.isLead }
        assertTrue(lead.text.contains("лид-абзацем"))

        val nonLead = paragraphs.first { !it.isLead && it.text.contains("обычный абзац без") }
        assertFalse(nonLead.isLead)
    }

    @Test
    fun `parses subheadings`() {
        val html = fixture("article_sample.html")
        val content = LentaArticleParser.parse(html, sampleNewsItem())

        val subheadings = content.content.filterIsInstance<ArticleContentType.Subheading>()
        assertEquals(1, subheadings.size)
        assertEquals("Подзаголовок раздела", subheadings.first().text)
    }

    @Test
    fun `parses image figure with caption and credit`() {
        val html = fixture("article_sample.html")
        val content = LentaArticleParser.parse(html, sampleNewsItem())

        val image = content.content.filterIsInstance<ArticleContentType.Image>().first()
        assertEquals("https://icdn.lenta.ru/images/2026/09/13/photo.jpg", image.url)
        assertEquals("Фото: РИА Новости", image.credit)
        assertTrue(image.caption?.contains("Основной текст подписи") == true)
        assertFalse(image.caption?.contains("Фото: РИА Новости") == true)
    }

    @Test
    fun `parses quote box`() {
        val html = fixture("article_sample.html")
        val content = LentaArticleParser.parse(html, sampleNewsItem())

        val quote = content.content.filterIsInstance<ArticleContentType.Quote>().first()
        assertTrue(quote.text.contains("цитата"))
        assertEquals("Пётр Петров", quote.authorName)
        assertEquals("пресс-секретарь компании", quote.authorDescription)
    }

    @Test
    fun `parses info box`() {
        val html = fixture("article_sample.html")
        val content = LentaArticleParser.parse(html, sampleNewsItem())

        val infoBox = content.content.filterIsInstance<ArticleContentType.InfoBox>().first()
        assertTrue(infoBox.text.contains("врезка"))
    }

    @Test
    fun `parses related material with url normalization and null date`() {
        val html = fixture("article_sample.html")
        val content = LentaArticleParser.parse(html, sampleNewsItem())

        val related = content.content.filterIsInstance<ArticleContentType.RelatedMaterial>().first()
        assertEquals("Заголовок связанного материала", related.title)
        assertEquals("Краткое описание связанного материала.", related.description)
        assertEquals("https://lenta.ru/news/2026/09/13/related/", related.articleUrl)
        assertNull(related.date)
    }

    @Test
    fun `prepends author before body content`() {
        val html = fixture("article_sample.html")
        val content = LentaArticleParser.parse(html, sampleNewsItem())

        val firstItem = content.content.first()
        assertTrue(firstItem is ArticleContentType.Author)
        val author = firstItem as ArticleContentType.Author
        assertEquals("Иван Иванов", author.name)
        assertEquals("Корреспондент отдела экономики", author.jobTitle)
        assertEquals("https://icdn.lenta.ru/images/authors/photo.jpg", author.photo)
    }

    @Test
    fun `category extracted from topic-header__rubric`() {
        val html = fixture("article_sample.html")
        val content = LentaArticleParser.parse(html, sampleNewsItem())

        assertEquals("Экономика", content.category)
    }

    @Test
    fun `title image and published date always come from news item, not html`() {
        val html = fixture("article_sample.html")
        val newsItem = sampleNewsItem()
        val content = LentaArticleParser.parse(html, newsItem)

        assertEquals(newsItem.title, content.title)
        assertEquals(newsItem.image, content.image)
        assertEquals(newsItem.published, content.publishedDate)
    }

    @Test
    fun `direct paragraph child of body is recognized even nested inside an unrecognized wrapper div is not`() {
        val html = fixture("article_sample.html")
        val content = LentaArticleParser.parse(html, sampleNewsItem())

        // The <p> inside .unrelated-block is NOT a direct child of .content-body, so it must not appear.
        val paragraphs = content.content.filterIsInstance<ArticleContentType.Paragraph>()
        assertTrue(paragraphs.none { it.text.contains("нераспознаваемого div") })
    }

    @Test
    fun `falls back to json-ld when no recognized body container exists`() {
        val html = fixture("article_jsonld_sample.html")
        val content = LentaArticleParser.parse(html, sampleNewsItem())

        assertTrue(content.content.any { it is ArticleContentType.Author })
        val author = content.content.filterIsInstance<ArticleContentType.Author>().first()
        assertEquals("Автор Ldjson", author.name)

        val paragraph = content.content.filterIsInstance<ArticleContentType.Paragraph>().first()
        assertTrue(paragraph.text.contains("Полный текст статьи"))
        assertFalse(paragraph.isLead)
    }
}

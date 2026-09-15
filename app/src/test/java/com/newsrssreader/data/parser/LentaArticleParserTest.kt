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
    fun `parses image figure with caption and credit from current picture-box markup`() {
        val html = fixture("article_sample.html")
        val content = LentaArticleParser.parse(html, sampleNewsItem())

        val image = content.content.filterIsInstance<ArticleContentType.Image>().first()
        assertEquals("https://icdn.lenta.ru/images/2026/09/13/photo.jpg", image.url)
        assertEquals("Фото: РИА Новости", image.credit)
        assertEquals("Основной текст подписи к изображению", image.caption)
    }

    @Test
    fun `image caption is null when new-shape figcaption has no caption paragraph`() {
        val html = """
            <div class="content-body">
              <figure class="picture-box">
                <img class="picture-box__image" src="https://icdn.lenta.ru/images/no-caption.jpg" />
                <figcaption class="picture-box__description">
                  <div class="description-block">
                    <p class="description-block__credits">Фото: РИА Новости</p>
                  </div>
                </figcaption>
              </figure>
            </div>
        """.trimIndent()
        val content = LentaArticleParser.parse(html, sampleNewsItem())

        val image = content.content.filterIsInstance<ArticleContentType.Image>().first()
        assertEquals("https://icdn.lenta.ru/images/no-caption.jpg", image.url)
        assertEquals("Фото: РИА Новости", image.credit)
        assertNull(image.caption)
    }

    @Test
    fun `image parses from legacy figure picture shape with caption and credit, fallback path`() {
        val html = """
            <div class="content-body">
              <figure class="picture">
                <img class="picture__image" src="https://icdn.lenta.ru/images/legacy.jpg" />
                <figcaption class="description">
                  Основной текст подписи <span class="description__credits">Фото: РИА Новости</span>
                </figcaption>
              </figure>
            </div>
        """.trimIndent()
        val content = LentaArticleParser.parse(html, sampleNewsItem())

        val image = content.content.filterIsInstance<ArticleContentType.Image>().first()
        assertEquals("https://icdn.lenta.ru/images/legacy.jpg", image.url)
        assertEquals("Фото: РИА Новости", image.credit)
        assertTrue(image.caption?.contains("Основной текст подписи") == true)
        assertFalse(image.caption?.contains("Фото: РИА Новости") == true)
    }

    @Test
    fun `image caption is null when legacy figcaption has no credits, matching iOS`() {
        val html = """
            <div class="content-body">
              <figure class="picture">
                <img class="picture__image" src="https://icdn.lenta.ru/images/no-credit.jpg" />
                <figcaption class="description">Подпись без указания автора фото</figcaption>
              </figure>
            </div>
        """.trimIndent()
        val content = LentaArticleParser.parse(html, sampleNewsItem())

        val image = content.content.filterIsInstance<ArticleContentType.Image>().first()
        assertEquals("https://icdn.lenta.ru/images/no-credit.jpg", image.url)
        assertNull(image.credit)
        assertNull(image.caption)
    }

    @Test
    fun `parses quote box from current nested content-body markup with author present`() {
        val html = fixture("article_sample.html")
        val content = LentaArticleParser.parse(html, sampleNewsItem())

        val quote = content.content.filterIsInstance<ArticleContentType.Quote>().first()
        assertTrue(quote.text.contains("цитата"))
        assertEquals("Пётр Петров", quote.authorName)
        assertEquals("пресс-секретарь компании", quote.authorDescription)
    }

    @Test
    fun `parses quote box from current nested content-body markup with no author wrapper`() {
        val html = """
            <div class="content-body">
              <div class="box-quote">
                <div class="content-body js-topic-body-content _no-mobile-padding _quotebox-body">
                  <p>Первая часть цитаты.</p>
                  <p>Вторая часть цитаты.</p>
                </div>
              </div>
            </div>
        """.trimIndent()
        val content = LentaArticleParser.parse(html, sampleNewsItem())

        val quote = content.content.filterIsInstance<ArticleContentType.Quote>().first()
        assertTrue(quote.text.contains("Первая часть цитаты."))
        assertTrue(quote.text.contains("Вторая часть цитаты."))
        assertEquals("", quote.authorName)
        assertNull(quote.authorDescription)
    }

    @Test
    fun `parses quote box from legacy box-quote__content-text shape, fallback path`() {
        val html = """
            <div class="content-body">
              <div class="box-quote">
                <div class="box-quote__content-text">Это цитата из старого шаблона.</div>
                <div class="box-quote__author-name">Пётр Петров</div>
                <div class="box-quote__author-description">пресс-секретарь компании</div>
              </div>
            </div>
        """.trimIndent()
        val content = LentaArticleParser.parse(html, sampleNewsItem())

        val quote = content.content.filterIsInstance<ArticleContentType.Quote>().first()
        assertEquals("Это цитата из старого шаблона.", quote.text)
        assertEquals("Пётр Петров", quote.authorName)
        assertEquals("пресс-секретарь компании", quote.authorDescription)
    }

    @Test
    fun `parses info box from box-note`() {
        val html = fixture("article_sample.html")
        val content = LentaArticleParser.parse(html, sampleNewsItem())

        val infoBox = content.content.filterIsInstance<ArticleContentType.InfoBox>()
            .first { it.text.contains("врезка с дополнительной") }
        assertTrue(infoBox.text.contains("врезка"))
    }

    @Test
    fun `parses info box from box-small-note`() {
        val html = fixture("article_sample.html")
        val content = LentaArticleParser.parse(html, sampleNewsItem())

        val infoBox = content.content.filterIsInstance<ArticleContentType.InfoBox>()
            .first { it.text.contains("маленькая врезка") }
        assertTrue(infoBox.text.contains("маленькая врезка"))
    }

    @Test
    fun `parses related material carousel with two items from current box-inline-topic markup`() {
        val html = fixture("article_sample.html")
        val content = LentaArticleParser.parse(html, sampleNewsItem())

        val related = content.content.filterIsInstance<ArticleContentType.RelatedMaterial>()
        assertEquals(2, related.size)

        val first = related[0]
        assertEquals("Заголовок связанного материала", first.title)
        assertEquals("Краткое описание связанного материала.", first.description)
        assertEquals("https://lenta.ru/news/2026/09/13/related/", first.articleUrl)
        assertNull(first.date)

        val second = related[1]
        assertEquals("Второй связанный материал", second.title)
        assertEquals("Описание второго связанного материала.", second.description)
        assertEquals("https://lenta.ru/news/2026/09/13/related2/", second.articleUrl)
        assertNull(second.date)
    }

    @Test
    fun `parses related material from legacy single-card shape, fallback path`() {
        val html = """
            <div class="content-body">
              <div class="box-inline-topic">
                <a class="card-inline-topic" href="/news/2026/09/13/related/">
                  <img class="card-inline-topic__image" src="https://icdn.lenta.ru/images/2026/09/13/related.jpg" />
                  <div class="card-inline-topic__rightcol">Краткое описание связанного материала.</div>
                  <div class="card-inline-topic__title">Заголовок связанного материала</div>
                  <div class="card-inline-topic__date">13 сентября 2026, 10:00</div>
                </a>
              </div>
            </div>
        """.trimIndent()
        val content = LentaArticleParser.parse(html, sampleNewsItem())

        val related = content.content.filterIsInstance<ArticleContentType.RelatedMaterial>()
        assertEquals(1, related.size)
        assertEquals("Заголовок связанного материала", related.first().title)
        assertEquals("Краткое описание связанного материала.", related.first().description)
        assertEquals("https://lenta.ru/news/2026/09/13/related/", related.first().articleUrl)
        assertNull(related.first().date)
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
    fun `category extracted from legacy topic-header__rubric, fallback path`() {
        val html = fixture("article_sample.html")
        val content = LentaArticleParser.parse(html, sampleNewsItem())

        // article_sample.html carries both the legacy rubric span AND the new common-head links;
        // the legacy selector is tried first, so it wins here.
        assertEquals("Экономика", content.category)
    }

    @Test
    fun `category extracted from current common-head__info-text rubrics link`() {
        val html = """
            <div class="common-head">
              <a class="common-head__info-text" href="/rubrics/world/">Мир</a>
              <a class="common-head__info-text" href="/news/2026/09/13/related/">13:00, 13 сентября 2026</a>
            </div>
            <div class="content-body">
              <p>Текст статьи.</p>
            </div>
        """.trimIndent()
        val content = LentaArticleParser.parse(html, sampleNewsItem())

        assertEquals("Мир", content.category)
    }

    @Test
    fun `parses announce from topic-body title-yandex div`() {
        val html = fixture("article_sample.html")
        val content = LentaArticleParser.parse(html, sampleNewsItem())

        assertEquals("Краткий анонс статьи в одну строку", content.announce)
    }

    @Test
    fun `falls back to news item summary when title-yandex div is missing`() {
        val html = """
            <div class="content-body">
              <p>Текст статьи.</p>
            </div>
        """.trimIndent()
        val newsItem = sampleNewsItem().copy(summary = "Анонс из RSS description")
        val content = LentaArticleParser.parse(html, newsItem)

        assertEquals("Анонс из RSS description", content.announce)
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

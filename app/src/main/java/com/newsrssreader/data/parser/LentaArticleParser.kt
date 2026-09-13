package com.newsrssreader.data.parser

import com.newsrssreader.data.model.ArticleContent
import com.newsrssreader.data.model.ArticleContentType
import com.newsrssreader.data.model.NewsItem
import org.json.JSONObject

/**
 * Parses Lenta.ru article HTML into structured [ArticleContent].
 *
 * Ported from the iOS `LentaArticleParser.swift` (NewsRSSReaderShared). Metadata such as title,
 * image, and published date always come from the RSS-derived [NewsItem], never from the HTML —
 * the only thing actually parsed out of the page itself is the category and the body content.
 */
object LentaArticleParser {

    fun parse(html: String, newsItem: NewsItem): ArticleContent {
        val doc = SimpleHtmlParser.parse(html)

        val category = doc.select(".topic-header__rubric").firstOrNull()?.text()

        val content = mutableListOf<ArticleContentType>()

        doc.select(".topic-authors").firstOrNull()?.let { authorBlock ->
            parseAuthor(authorBlock)?.let { content.add(it) }
        }

        val body = doc.select(".content-body").firstOrNull()
            ?: doc.select(".topic-body__content").firstOrNull()
            ?: doc.select(".topic-body").firstOrNull()
            ?: doc.select("[id^=articleBody_]").firstOrNull()

        if (body != null) {
            for (child in body.children()) {
                parseContentElement(child)?.let { content.add(it) }
            }
        }

        if (content.isEmpty()) {
            content.addAll(parseJsonLd(doc))
        }

        return ArticleContent(
            title = newsItem.title ?: "",
            image = newsItem.image,
            publishedDate = newsItem.published,
            category = category,
            content = content,
        )
    }

    private fun parseContentElement(element: com.newsrssreader.data.parser.HtmlElement): ArticleContentType? {
        return when {
            element.tag == "p" -> {
                val text = element.text()
                if (text.isEmpty()) return null
                val isLead = element.hasClass("_lead") || element.hasClass("topic-body__content-text--lead")
                ArticleContentType.Paragraph(text = text, isLead = isLead)
            }
            element.tag == "h2" || element.tag == "h3" -> {
                val text = element.text()
                if (text.isEmpty()) return null
                ArticleContentType.Subheading(text = text)
            }
            element.tag == "figure" && element.hasClass("picture") -> parseImage(element)
            element.tag == "div" && element.hasClass("box-quote") -> parseQuote(element)
            element.tag == "div" && element.hasClass("box-note") -> parseInfoBox(element)
            element.tag == "div" && element.hasClass("box-inline-topic") -> parseRelatedMaterial(element)
            else -> null
        }
    }

    private fun parseAuthor(element: com.newsrssreader.data.parser.HtmlElement): ArticleContentType.Author? {
        val name = element.select(".topic-authors__name").firstOrNull()?.text() ?: ""
        if (name.isEmpty()) return null
        val photoRaw = element.select(".topic-authors__photo").firstOrNull()?.attr("src") ?: ""
        val photo = photoRaw.ifEmpty { null }
        val jobTitle = element.select(".topic-authors__job").firstOrNull()?.text()
        return ArticleContentType.Author(name = name, photo = photo, jobTitle = jobTitle)
    }

    private fun parseImage(element: com.newsrssreader.data.parser.HtmlElement): ArticleContentType.Image? {
        val url = element.select("img.picture__image").firstOrNull()?.attr("src") ?: ""
        if (url.isEmpty()) return null

        val figcaption = element.select("figcaption.description").firstOrNull()
        val credit = figcaption?.select(".description__credits")?.firstOrNull()?.text()
        // Matches iOS: caption is only ever computed when credit is non-null/non-empty;
        // a figcaption with no `.description__credits` child yields caption = null,
        // even if the figcaption has other text.
        val caption = if (!credit.isNullOrEmpty()) {
            figcaption?.text()?.replace(credit, "")?.trim()?.takeIf { it.isNotEmpty() }
        } else {
            null
        }

        return ArticleContentType.Image(url = url, caption = caption, credit = credit)
    }

    private fun parseQuote(element: com.newsrssreader.data.parser.HtmlElement): ArticleContentType.Quote? {
        val text = element.select(".box-quote__content-text").firstOrNull()?.text() ?: ""
        if (text.isEmpty()) return null
        val authorName = element.select(".box-quote__author-name").firstOrNull()?.text() ?: ""
        val authorDescription = element.select(".box-quote__author-description").firstOrNull()?.text()
        return ArticleContentType.Quote(text = text, authorName = authorName, authorDescription = authorDescription)
    }

    private fun parseInfoBox(element: com.newsrssreader.data.parser.HtmlElement): ArticleContentType.InfoBox? {
        val text = element.select(".box-note__text").firstOrNull()?.text() ?: ""
        if (text.isEmpty()) return null
        return ArticleContentType.InfoBox(text = text)
    }

    private fun parseRelatedMaterial(element: com.newsrssreader.data.parser.HtmlElement): ArticleContentType.RelatedMaterial? {
        val card = element.select(".card-inline-topic").firstOrNull() ?: return null
        val title = card.select(".card-inline-topic__title").firstOrNull()?.text() ?: ""
        val description = card.select(".card-inline-topic__rightcol").firstOrNull()?.text()
        val imageUrl = card.select(".card-inline-topic__image").firstOrNull()?.attr("src")
        var articleUrl = card.attr("href")
        if (title.isEmpty() || articleUrl.isEmpty()) return null
        if (!articleUrl.startsWith("http")) {
            articleUrl = "https://lenta.ru$articleUrl"
        }
        // NOTE: iOS's RelatedMaterial has no real date-parsing pipeline anywhere (the field is
        // just raw text with nothing that converts it to a Date), so we leave date = null here
        // rather than attempting to parse card-inline-topic__date ourselves.
        return ArticleContentType.RelatedMaterial(
            title = title,
            description = description,
            imageUrl = imageUrl,
            articleUrl = articleUrl,
            date = null,
        )
    }

    private fun parseJsonLd(doc: com.newsrssreader.data.parser.HtmlDocument): List<ArticleContentType> {
        val scripts = doc.select("script[type=application/ld+json]")
        for (script in scripts) {
            val result = mutableListOf<ArticleContentType>()
            val jsonText = script.html()
            val json = runCatching { JSONObject(jsonText) }.getOrNull() ?: continue

            val authorObj = json.optJSONObject("author")
            val authorName = authorObj?.optString("name")
            if (!authorName.isNullOrEmpty()) {
                result.add(ArticleContentType.Author(name = authorName, photo = null, jobTitle = null))
            }

            val articleBody = json.optString("articleBody", "")
            if (articleBody.isNotEmpty()) {
                result.add(ArticleContentType.Paragraph(text = articleBody, isLead = false))
            }

            if (result.isNotEmpty()) return result
        }
        return emptyList()
    }
}

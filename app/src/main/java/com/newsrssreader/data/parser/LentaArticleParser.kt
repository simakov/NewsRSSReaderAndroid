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

        // Old template used a plain rubric span; current live markup instead exposes the
        // category as one of possibly several `a.common-head__info-text` links (the other one
        // being a timestamp link), distinguished by an href starting with "/rubrics/".
        val category = doc.select(".topic-header__rubric").firstOrNull()?.text()
            ?: doc.select("a.common-head__info-text[href^=/rubrics/]").firstOrNull()?.text()

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
                if (child.tag == "div" && child.hasClass("box-inline-topic")) {
                    // Related-material blocks are a carousel that can hold zero or more items,
                    // unlike every other block type below which yields at most one content entry.
                    content.addAll(parseRelatedMaterialList(child))
                } else {
                    parseContentElement(child)?.let { content.add(it) }
                }
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
            element.tag == "figure" && (element.hasClass("picture-box") || element.hasClass("picture")) ->
                parseImage(element)
            element.tag == "div" && element.hasClass("box-quote") -> parseQuote(element)
            element.tag == "div" && (element.hasClass("box-note") || element.hasClass("box-small-note")) ->
                parseInfoBox(element)
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
        // Current live markup: figure.picture-box / img.picture-box__image.
        // Legacy fallback: figure.picture / img.picture__image.
        val url = element.select("img.picture-box__image").firstOrNull()?.attr("src")
            ?: element.select("img.picture__image").firstOrNull()?.attr("src")
            ?: ""
        if (url.isEmpty()) return null

        val newFigcaption = element.select("figcaption.picture-box__description").firstOrNull()
        if (newFigcaption != null) {
            val credit = newFigcaption.select(".description-block__credits").firstOrNull()?.text()
            // New template has an explicit caption paragraph; unlike the legacy shape, don't
            // fall back to scraping the whole figcaption text when it's missing.
            val caption = newFigcaption.select(".description-block__caption").firstOrNull()?.text()
            return ArticleContentType.Image(url = url, caption = caption, credit = credit)
        }

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
        // Legacy shape kept as a first-try fallback; current live markup nests the quote text
        // (possibly across several <p> tags, flattened via .text()) inside a `.content-body` div
        // that lives INSIDE this box-quote element — unrelated to the top-level body-container
        // lookup in parse(), which searches from the whole document instead.
        val oldText = element.select(".box-quote__content-text").firstOrNull()?.text()
        val text = if (!oldText.isNullOrEmpty()) {
            oldText
        } else {
            element.select(".content-body").firstOrNull()?.text() ?: ""
        }
        if (text.isEmpty()) return null
        // The `.box-quote__author` wrapper may be entirely absent (interview-style quotes with
        // no attribution); name/description already default to "" / null in that case.
        val authorName = element.select(".box-quote__author-name").firstOrNull()?.text() ?: ""
        val authorDescription = element.select(".box-quote__author-description").firstOrNull()?.text()
        return ArticleContentType.Quote(text = text, authorName = authorName, authorDescription = authorDescription)
    }

    private fun parseInfoBox(element: com.newsrssreader.data.parser.HtmlElement): ArticleContentType.InfoBox? {
        val text = element.select(".box-note__text").firstOrNull()?.text()
            ?: element.select(".box-small-note__text").firstOrNull()?.text()
            ?: ""
        if (text.isEmpty()) return null
        return ArticleContentType.InfoBox(text = text)
    }

    private fun parseRelatedMaterialList(
        element: com.newsrssreader.data.parser.HtmlElement,
    ): List<ArticleContentType.RelatedMaterial> {
        // Legacy shape: a single `.card-inline-topic` card. Try it first on the whole block.
        val oldCard = element.select(".card-inline-topic").firstOrNull()
        if (oldCard != null) {
            val title = oldCard.select(".card-inline-topic__title").firstOrNull()?.text() ?: ""
            val description = oldCard.select(".card-inline-topic__rightcol").firstOrNull()?.text()
            val imageUrl = oldCard.select(".card-inline-topic__image").firstOrNull()?.attr("src")
            var articleUrl = oldCard.attr("href")
            if (title.isNotEmpty() && articleUrl.isNotEmpty()) {
                if (!articleUrl.startsWith("http")) {
                    articleUrl = "https://lenta.ru$articleUrl"
                }
                // NOTE: iOS's RelatedMaterial has no real date-parsing pipeline anywhere (the
                // field is just raw text with nothing that converts it to a Date), so we leave
                // date = null here rather than attempting to parse the date text ourselves.
                return listOf(
                    ArticleContentType.RelatedMaterial(
                        title = title,
                        description = description,
                        imageUrl = imageUrl,
                        articleUrl = articleUrl,
                        date = null,
                    ),
                )
            }
        }

        // Current live markup: a carousel of zero-or-more `.box-inline-topic__item`s.
        val items = element.select(".box-inline-topic__item")
        val result = mutableListOf<ArticleContentType.RelatedMaterial>()
        for (item in items) {
            val title = item.select(".box-inline-topic__title").firstOrNull()?.text() ?: ""
            val description = item.select(".box-inline-topic__rightcol").firstOrNull()?.text()
            val imageUrl = item.select(".box-inline-topic__image").firstOrNull()?.attr("src")
            var articleUrl = item.select(".box-inline-topic__link").firstOrNull()?.attr("href")
                ?: item.attr("href")
            if (title.isEmpty() || articleUrl.isEmpty()) continue
            if (!articleUrl.startsWith("http")) {
                articleUrl = "https://lenta.ru$articleUrl"
            }
            result.add(
                ArticleContentType.RelatedMaterial(
                    title = title,
                    description = description,
                    imageUrl = imageUrl,
                    articleUrl = articleUrl,
                    date = null,
                ),
            )
        }
        return result
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

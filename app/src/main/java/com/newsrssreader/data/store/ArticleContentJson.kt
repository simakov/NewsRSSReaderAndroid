package com.newsrssreader.data.store

import com.newsrssreader.data.model.ArticleContent
import com.newsrssreader.data.model.ArticleContentType
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

/**
 * Serializes a parsed [ArticleContent] to JSON and back, so a bookmarked article's body can be
 * kept on disk and read again without the network.
 *
 * Hand-rolled on `org.json` rather than via a serialization library, matching the project's
 * dependency philosophy — `UpdateCheckService` and `LentaArticleParser` already parse JSON the
 * same way. Each block carries a `"type"` discriminator naming its [ArticleContentType] variant.
 *
 * Forward compatibility runs one way only, and deliberately: a block whose `"type"` this build
 * does not recognize is dropped rather than treated as a corrupt file, so a bookmark written by a
 * newer build still opens (minus whatever is new about it) in an older one. `ArticleContentJsonTest`
 * holds one of every block type, so *adding* a variant to the sealed hierarchy without extending
 * this file fails the suite instead of quietly losing that variant from every saved bookmark.
 */
internal object ArticleContentJson {

    private const val TypeParagraph = "paragraph"
    private const val TypeSubheading = "subheading"
    private const val TypeImage = "image"
    private const val TypeQuote = "quote"
    private const val TypeAuthor = "author"
    private const val TypeInfoBox = "infobox"
    private const val TypeRelated = "related"

    fun encode(content: ArticleContent): String {
        val blocks = JSONArray()
        content.content.forEach { blocks.put(encodeBlock(it)) }
        return JSONObject()
            .put("title", content.title)
            .putOpt("image", content.image)
            .putOpt("publishedDate", content.publishedDate?.time)
            .putOpt("category", content.category)
            .putOpt("announce", content.announce)
            .put("content", blocks)
            .toString()
    }

    fun decode(json: String): ArticleContent? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val blocksJson = root.optJSONArray("content") ?: JSONArray()
        val blocks = (0 until blocksJson.length()).mapNotNull { index ->
            blocksJson.optJSONObject(index)?.let(::decodeBlock)
        }
        return ArticleContent(
            title = root.optString("title"),
            image = root.optStringOrNull("image"),
            publishedDate = root.optDate("publishedDate"),
            category = root.optStringOrNull("category"),
            announce = root.optStringOrNull("announce"),
            content = blocks,
        )
    }

    /**
     * Every image URL the article actually renders, in document order: the hero image, the
     * in-body images, and author portraits.
     *
     * [ArticleContentType.RelatedMaterial.imageUrl] is excluded because related material is parsed
     * but never rendered (see `ArticleContentBlock`) — saving it offline would download a file
     * nothing can ever display.
     */
    fun imageUrls(content: ArticleContent): List<String> = buildList {
        content.image?.let(::add)
        content.content.forEach { block ->
            when (block) {
                is ArticleContentType.Image -> add(block.url)
                is ArticleContentType.Author -> block.photo?.let(::add)
                else -> Unit
            }
        }
    }

    /**
     * A copy of [content] with every image URL passed through [mapping], used when opening a
     * bookmark offline to point the same content at the locally saved copies of its images.
     *
     * A null result from [mapping] leaves that URL as it was, so an image that failed to save
     * simply falls back to its remote address instead of breaking the whole article.
     */
    fun withRewrittenImages(
        content: ArticleContent,
        mapping: (String) -> String?,
    ): ArticleContent = content.copy(
        image = content.image?.let { mapping(it) ?: it },
        content = content.content.map { block ->
            when (block) {
                is ArticleContentType.Image -> block.copy(url = mapping(block.url) ?: block.url)
                is ArticleContentType.Author -> block.photo?.let { photo ->
                    block.copy(photo = mapping(photo) ?: photo)
                } ?: block
                else -> block
            }
        },
    )

    private fun encodeBlock(block: ArticleContentType): JSONObject = when (block) {
        is ArticleContentType.Paragraph -> JSONObject()
            .put("type", TypeParagraph)
            .put("text", block.text)
            .put("isLead", block.isLead)

        is ArticleContentType.Subheading -> JSONObject()
            .put("type", TypeSubheading)
            .put("text", block.text)

        is ArticleContentType.Image -> JSONObject()
            .put("type", TypeImage)
            .put("url", block.url)
            .putOpt("caption", block.caption)
            .putOpt("credit", block.credit)

        is ArticleContentType.Quote -> JSONObject()
            .put("type", TypeQuote)
            .put("text", block.text)
            .put("authorName", block.authorName)
            .putOpt("authorDescription", block.authorDescription)

        is ArticleContentType.Author -> JSONObject()
            .put("type", TypeAuthor)
            .put("name", block.name)
            .putOpt("photo", block.photo)
            .putOpt("jobTitle", block.jobTitle)

        is ArticleContentType.InfoBox -> JSONObject()
            .put("type", TypeInfoBox)
            .put("text", block.text)

        is ArticleContentType.RelatedMaterial -> JSONObject()
            .put("type", TypeRelated)
            .put("title", block.title)
            .putOpt("description", block.description)
            .putOpt("imageUrl", block.imageUrl)
            .put("articleUrl", block.articleUrl)
            .putOpt("date", block.date?.time)
    }

    private fun decodeBlock(json: JSONObject): ArticleContentType? = when (json.optString("type")) {
        TypeParagraph -> ArticleContentType.Paragraph(
            text = json.optString("text"),
            isLead = json.optBoolean("isLead"),
        )

        TypeSubheading -> ArticleContentType.Subheading(json.optString("text"))

        TypeImage -> ArticleContentType.Image(
            url = json.optString("url"),
            caption = json.optStringOrNull("caption"),
            credit = json.optStringOrNull("credit"),
        )

        TypeQuote -> ArticleContentType.Quote(
            text = json.optString("text"),
            authorName = json.optString("authorName"),
            authorDescription = json.optStringOrNull("authorDescription"),
        )

        TypeAuthor -> ArticleContentType.Author(
            name = json.optString("name"),
            photo = json.optStringOrNull("photo"),
            jobTitle = json.optStringOrNull("jobTitle"),
        )

        TypeInfoBox -> ArticleContentType.InfoBox(json.optString("text"))

        TypeRelated -> ArticleContentType.RelatedMaterial(
            title = json.optString("title"),
            description = json.optStringOrNull("description"),
            imageUrl = json.optStringOrNull("imageUrl"),
            articleUrl = json.optString("articleUrl"),
            date = json.optDate("date"),
        )

        else -> null
    }
}

/**
 * `optString` returns the empty string for an absent key, which would turn every unset optional
 * field into `""` rather than null on the way back in and break round-tripping.
 */
internal fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).ifEmpty { null }

internal fun JSONObject.optDate(key: String): Date? =
    if (isNull(key)) null else optLong(key, -1L).takeIf { it >= 0L }?.let(::Date)

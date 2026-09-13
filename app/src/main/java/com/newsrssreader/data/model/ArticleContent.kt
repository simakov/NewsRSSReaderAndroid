package com.newsrssreader.data.model

import java.util.Date

sealed class ArticleContentType {
    data class Paragraph(val text: String, val isLead: Boolean = false) : ArticleContentType()
    data class Subheading(val text: String) : ArticleContentType()
    data class Image(val url: String, val caption: String? = null, val credit: String? = null) : ArticleContentType()
    data class Quote(val text: String, val authorName: String, val authorDescription: String? = null) : ArticleContentType()
    data class Author(val name: String, val photo: String? = null, val jobTitle: String? = null) : ArticleContentType()
    data class InfoBox(val text: String) : ArticleContentType()
    data class RelatedMaterial(
        val title: String,
        val description: String? = null,
        val imageUrl: String? = null,
        val articleUrl: String,
        val date: Date? = null,
    ) : ArticleContentType()
}

data class ArticleContent(
    val title: String,
    val image: String? = null,
    val publishedDate: Date? = null,
    val category: String? = null,
    val content: List<ArticleContentType>,
)

package com.saulhdev.feeder.data.entity

import com.saulhdev.feeder.data.db.models.Article
import com.saulhdev.feeder.data.db.models.FeedItem
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class BookmarkedArticleExport(
    val uuid: String,
    val guid: String,
    val title: String,
    val plainTitle: String,
    val imageUrl: String? = null,
    val enclosureLink: String? = null,
    val plainSnippet: String,
    val description: String,
    val author: String? = null,
    val pubDate: Long,
    val link: String? = null,
    val categories: List<String> = emptyList(),
    val pinned: Boolean = false,
    // Feed information for relation
    val feedUrl: String,
    // The source's name, for a source that has to be made again to hold
    // the article: one removed while it still had saved articles is not in
    // the subscriptions file. Absent from files written before 1.0.6.
    val feedTitle: String? = null,
)

@Serializable
data class BookmarksExportContainer(
    val version: Int = 1,
    val exportDate: Long,
    val articles: List<BookmarkedArticleExport>
)

fun FeedItem.toBookmarkedExport(): BookmarkedArticleExport {
    return BookmarkedArticleExport(
        uuid = article.uuid,
        guid = article.guid,
        title = article.title,
        plainTitle = article.plainTitle,
        imageUrl = article.imageUrl,
        enclosureLink = article.enclosureLink,
        plainSnippet = article.plainSnippet,
        description = article.description,
        author = article.author,
        pubDate = article.pubDate,
        link = article.link,
        categories = article.categories,
        pinned = article.pinned,
        feedUrl = feed.url.toString(),
        feedTitle = feed.title,
    )
}

fun BookmarkedArticleExport.toArticle(feedId: Long): Article {
    return Article(
        uuid = uuid,
        guid = guid,
        title = title,
        plainTitle = plainTitle,
        imageUrl = imageUrl,
        enclosureLink = enclosureLink,
        plainSnippet = plainSnippet,
        description = description,
        author = author,
        pubDate = pubDate,
        link = link,
        feedId = feedId,
        categories = ArrayList(categories),
        pinned = pinned,
        bookmarked = true,
        firstSyncedTime = Instant.fromEpochMilliseconds(pubDate),
        primarySortTime = Instant.fromEpochMilliseconds(pubDate)
    )
}
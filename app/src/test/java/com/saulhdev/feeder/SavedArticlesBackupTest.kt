package com.saulhdev.feeder

import com.saulhdev.feeder.data.db.models.Article
import com.saulhdev.feeder.data.entity.BookmarkedArticleExport
import com.saulhdev.feeder.data.entity.BookmarksExportContainer
import com.saulhdev.feeder.manager.backup.SavedArticlesBackup
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Saved articles in the backup: put back once each, onto the source they
 * came from, without touching anything already saved.
 */
class SavedArticlesBackupTest {

    private class FakeStore(
        val sources: MutableMap<String, Long> = mutableMapOf("https://example.com/feed" to 1L),
    ) : SavedArticlesBackup.Store {
        val articles = mutableMapOf<Pair<String, Long>, Article>()
        val made = mutableListOf<Pair<String, String?>>()

        override suspend fun sourceFor(url: String, title: String?): Long? =
            sources[url] ?: run {
                if (!url.startsWith("http")) return null
                made += url to title
                (sources.size + 1L).also { sources[url] = it }
            }

        override suspend fun article(guid: String, feedId: Long) = articles[guid to feedId]

        override suspend fun write(article: Article) {
            articles[article.guid to article.feedId] = article
        }
    }

    private fun saved(guid: String, feedUrl: String = "https://example.com/feed", feedTitle: String? = null) =
        BookmarkedArticleExport(
            uuid = "uuid-$guid",
            guid = guid,
            title = "Title $guid",
            plainTitle = "Title $guid",
            plainSnippet = "",
            description = "<p>Body</p>",
            pubDate = 1_700_000_000_000,
            link = "https://example.com/$guid",
            feedUrl = feedUrl,
            feedTitle = feedTitle,
        )

    private fun container(vararg articles: BookmarkedArticleExport) =
        BookmarksExportContainer(exportDate = 0, articles = articles.toList())

    @Test
    fun `an article the phone has never seen is added, saved`() = runBlocking {
        val store = FakeStore()
        assertEquals(1, SavedArticlesBackup.restore(container(saved("a")), store))
        val article = store.articles["a" to 1L]!!
        assertTrue(article.bookmarked)
        assertEquals("uuid-a", article.uuid)
    }

    @Test
    fun `one the phone already has is saved in place, not added again`() = runBlocking {
        val store = FakeStore()
        // Fetched by a sync on the new phone, under an id of its own.
        store.articles["a" to 1L] = Article(uuid = "fetched-here", guid = "a", feedId = 1, readAt = 5)
        assertEquals(1, SavedArticlesBackup.restore(container(saved("a")), store))
        val article = store.articles["a" to 1L]!!
        assertEquals("fetched-here", article.uuid)
        assertTrue(article.bookmarked)
        assertEquals("what the phone knew is kept", 5L, article.readAt)
    }

    @Test
    fun `restoring twice changes nothing the second time`() = runBlocking {
        val store = FakeStore()
        val file = container(saved("a"), saved("b"))
        assertEquals(2, SavedArticlesBackup.restore(file, store))
        assertEquals(0, SavedArticlesBackup.restore(file, store))
        assertEquals(2, store.articles.size)
    }

    @Test
    fun `an article from a removed source keeps a source to belong to`() = runBlocking {
        val store = FakeStore()
        SavedArticlesBackup.restore(container(saved("a", "https://gone.example/rss", "Gone")), store)
        assertEquals(listOf("https://gone.example/rss" to "Gone"), store.made)
        assertTrue(store.articles.values.single().bookmarked)
    }

    @Test
    fun `an address that is not one is skipped, not fatal`() = runBlocking {
        val store = FakeStore()
        assertEquals(1, SavedArticlesBackup.restore(container(saved("x", "nonsense"), saved("a")), store))
    }

    @Test
    fun `a file from before 1_0_6, with no source names, still reads`() {
        val old = """
            {"version":1,"exportDate":1,"articles":[{"uuid":"u","guid":"g","title":"T",
            "plainTitle":"T","plainSnippet":"","description":"","pubDate":1,
            "feedUrl":"https://example.com/feed"}]}
        """.trimIndent()
        val read = SavedArticlesBackup.decode(old)
        assertEquals(null, read.articles.single().feedTitle)
    }

    @Test
    fun `what is written reads back the same`() {
        val file = container(saved("a", feedTitle = "Example"))
        val json = kotlinx.serialization.json.Json.encodeToString(BookmarksExportContainer.serializer(), file)
        assertEquals(file, SavedArticlesBackup.decode(json))
    }
}

package com.saulhdev.feeder

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Saving is reading later: the whole article is downloaded and kept. */
class ReadLaterTest {

    private fun read(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    @Test
    fun `saving here or from the account starts the download at once`() {
        val repo = read("data/repository/ArticleRepository.kt")
        assertTrue(repo.contains("if (bookmark && !it.bookmarked) onSaved?.invoke()"))
        assertTrue(repo.contains("if (ids.isNotEmpty()) onSaved?.invoke()"))
        assertTrue(read("NeoApp.kt").contains("articles.onSaved = { scheduleSavedFullText() }"))
    }

    @Test
    fun `the saved run neither cancels the after-sync run nor an earlier save`() {
        val parser = read("manager/models/FullTextParser.kt")
        // Its own unique work, appended behind a running one and never
        // replacing; and never more than one waiting (see WorkQueueTest).
        assertTrue(parser.contains("enqueueUnlessWaiting(workManager, SAVED_WORK, request)"))
        assertTrue(parser.contains("workManager.enqueueUniqueWork(name, ExistingWorkPolicy.APPEND_OR_REPLACE, request)"))
        assertTrue(parser.contains("private const val SAVED_WORK = \"FullTextSaved\""))
        // Same conditions as every other advance download.
        assertTrue(parser.contains(".setConstraints(fullTextConstraints())\n        .setInputData(workDataOf(SAVED_ONLY to true))"))
    }

    @Test
    fun `saved articles are also in the after-sync download, whatever their source`() {
        val dao = read("data/db/dao/FeedArticleDao.kt")
        assertTrue(dao.contains("WHERE Article.bookmarked = 1\n           OR ((:allFeeds OR f.fullTextByDefault = 1)"))
        assertTrue(dao.contains("@Query(\"SELECT uuid, link FROM Article WHERE bookmarked = 1\")"))
    }

    @Test
    fun `a saved article is never cleaned away`() {
        val dao = read("data/db/dao/FeedArticleDao.kt")
        assertTrue(dao.contains("WHERE feedId = :feedId AND pinned = 0 AND bookmarked = 0\n        AND pubDateV2 < :minKeptPubDate"))
    }

    @Test
    fun `it is described as reading later`() {
        val strings = File("src/main/res/values/strings.xml").readText()
        assertTrue(strings.contains("<string name=\"tour_bookmarks_title\">Read later</string>"))
        assertTrue(strings.contains("downloads the whole article and keeps it here, readable offline, until you remove it"))
    }
}

package com.saulhdev.feeder

import com.saulhdev.feeder.utils.normalizeFeedUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Adding many sources reads what is already subscribed once, not per source.
 *
 * Matching an address ignores `www.`, a trailing slash and the scheme, so it
 * cannot be an indexed query — it is a scan. Done per feed, with the caller's
 * own check doing a second one, importing a hundred feeds against a hundred
 * and nineteen subscriptions materialised some twenty thousand rows to add a
 * hundred. A browser bookmark export is larger again.
 *
 * The set built from that single read also does something the per-feed
 * version could not: it catches two spellings of the same address *within one
 * batch*. Each passed its own check against the database, neither saw the
 * other, and `Feeds.url` is uniquely indexed with REPLACE — so the second
 * quietly replaced the first and took its articles.
 */
class BulkInsertTest {

    private val repo = File(
        "src/main/java/com/saulhdev/feeder/data/repository/SourcesRepository.kt"
    ).readText()

    @Test
    fun `the batch reads what is stored exactly once`() {
        val at = repo.indexOf("suspend fun insertSources(")
        assertTrue("insertSources is gone", at > 0)
        val body = repo.substring(at, repo.indexOf("\n    }", at))
        assertEquals(
            "the batch scans the feed table more than once",
            1,
            Regex("""loadAllFeeds\(\)""").findAll(body).count(),
        )
        assertTrue(
            "nothing in the batch reads the table per feed",
            !body.contains("findSourceByUrl") && !body.contains("isSameFeedUrl"),
        )
    }

    @Test
    fun `duplicates inside one batch are caught by the same set`() {
        val at = repo.indexOf("suspend fun insertSources(")
        val body = repo.substring(at, repo.indexOf("\n    }", at))
        assertTrue(
            "the batch no longer folds each address into the set it checks",
            body.contains("taken.add(normalizeFeedUrl(feed.url))"),
        )
    }

    @Test
    fun `the batch records https, before it compares anything`() {
        val at = repo.indexOf("suspend fun insertSources(")
        val body = repo.substring(at, repo.indexOf("\n    }", at))
        val convert = body.indexOf("preferringHttps()")
        val compare = body.indexOf("loadAllFeeds()")
        assertTrue("the batch no longer converts the scheme", convert > 0)
        assertTrue("the batch compares before converting", convert < compare)
    }

    @Test
    fun `every bulk caller uses it`() {
        // The three screens that add more than one source at a time. Each had
        // its own copy of the same loop, with its own comment about REPLACE,
        // and they had already drifted apart in what they counted.
        mapOf(
            "data/FeedLibrary.kt" to "library packs",
            "data/StarterSources.kt" to "the starter list",
            "viewmodels/BookmarkImportViewModel.kt" to "bookmark import",
        ).forEach { (path, what) ->
            val text = File("src/main/java/com/saulhdev/feeder/$path").readText()
            assertTrue(
                "$what adds sources one at a time again",
                text.contains("insertSources("),
            )
            assertTrue(
                "$what still inserts per feed",
                !Regex("""\.insertSource\(""").containsMatchIn(text),
            )
        }
    }

    @Test
    fun `normalising is what makes one read enough`() {
        // The set holds normalised addresses, so the comparison is the same
        // one the per-feed check made. If these stopped agreeing, the batch
        // would admit duplicates the single version rejected.
        assertEquals(
            normalizeFeedUrl("https://www.example.com/feed/"),
            normalizeFeedUrl("http://example.com/feed"),
        )
    }
}

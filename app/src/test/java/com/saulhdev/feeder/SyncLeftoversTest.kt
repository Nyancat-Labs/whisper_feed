/*
 * This file is part of Whisper
 * Copyright (c) 2026   Whisper contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.saulhdev.feeder

import com.saulhdev.feeder.utils.FeedFetch
import com.saulhdev.feeder.utils.FeedHistoryCodec
import com.saulhdev.feeder.utils.FetchKind
import com.saulhdev.feeder.utils.IconLookup
import com.saulhdev.feeder.utils.SyncResult
import com.saulhdev.feeder.utils.articleKey
import com.saulhdev.feeder.utils.feedDigest
import com.saulhdev.feeder.utils.iconLookupDue
import com.saulhdev.feeder.utils.joinPairs
import com.saulhdev.feeder.utils.parseSettingsKey
import com.saulhdev.feeder.utils.sameArticleGroups
import com.saulhdev.feeder.utils.syncOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private const val NOW = 1_800_000_000_000L

/**
 * Four feeds subscribed twice that the address check could not see, feeds
 * that send the same bytes every time, and a home page fetched on every sync
 * to find an icon already stored.
 */
class SyncLeftoversTest {

    private fun articles(source: Long, vararg paths: String) =
        paths.map { source to "https://www.site.example/$it" }

    // --- duplicates -------------------------------------------------------

    @Test
    fun `an article's address loses what differs between two feeds of one site`() {
        val key = "site.example/2026/09/story"
        assertEquals(key, articleKey("https://www.site.example/2026/09/story/"))
        assertEquals(key, articleKey("http://site.example/2026/09/story"))
        assertEquals(key, articleKey("https://site.example/2026/09/story?utm_source=rss&utm_medium=feed"))
        assertEquals(
            "a query that picks the article stays",
            "site.example/index.php?p=123",
            articleKey("https://site.example/index.php?p=123&utm_campaign=x"),
        )
        assertEquals(articleKey("https://s.example/a?b=2&a=1"), articleKey("https://s.example/a?a=1&b=2"))
        assertNull(articleKey("mailto:someone@example.com"))
        assertNull(articleKey(""))
        assertNull(articleKey(null))
    }

    @Test
    fun `one feed subscribed twice is one group, whatever its addresses`() {
        val paths = (1..20).map { "story-$it" }.toTypedArray()
        val links = articles(1, *paths) + articles(2, *paths) + articles(3, "other-1", "other-2", "other-3", "other-4", "other-5")
        assertEquals(listOf(setOf(1L, 2L)), sameArticleGroups(links))
    }

    @Test
    fun `two sections of one paper that share a few stories are two feeds`() {
        val world = (1..20).map { "world-$it" } + (1..6).map { "shared-$it" }
        val europe = (1..20).map { "europe-$it" } + (1..6).map { "shared-$it" }
        val links = articles(1, *world.toTypedArray()) + articles(2, *europe.toTypedArray())
        assertTrue(sameArticleGroups(links).isEmpty())
    }

    @Test
    fun `a newer subscription is matched against an older one that kept more`() {
        val old = (1..60).map { "story-$it" }
        val new = (51..60).map { "story-$it" }
        val links = articles(1, *old.toTypedArray()) + articles(2, *new.toTypedArray())
        assertEquals(listOf(setOf(1L, 2L)), sameArticleGroups(links))
    }

    @Test
    fun `too few articles to judge is no match`() {
        val links = articles(1, "a", "b", "c") + articles(2, "a", "b", "c")
        assertTrue(sameArticleGroups(links).isEmpty())
    }

    @Test
    fun `a feed subscribed three times is one group of three`() {
        assertEquals(listOf(setOf(1L, 2L, 3L)), joinPairs(listOf(1L to 2L, 3L to 2L)))
        assertEquals(2, joinPairs(listOf(1L to 2L, 3L to 4L)).size)
    }

    // --- identical downloads ---------------------------------------------

    @Test
    fun `the same download under the same settings has the same fingerprint`() {
        val body = "<rss>same</rss>".toByteArray()
        val settings = parseSettingsKey(25, 7, setOf("crypto"))
        assertEquals(feedDigest(body, settings), feedDigest(body.copyOf(), settings))
        assertNotEquals(feedDigest(body, settings), feedDigest("<rss>new</rss>".toByteArray(), settings))
    }

    @Test
    fun `changing what is kept makes the same download worth reading again`() {
        val body = "<rss>same</rss>".toByteArray()
        val before = feedDigest(body, parseSettingsKey(25, 7, setOf("crypto")))
        assertNotEquals("more articles kept", before, feedDigest(body, parseSettingsKey(50, 7, setOf("crypto"))))
        assertNotEquals("a longer range", before, feedDigest(body, parseSettingsKey(25, 14, setOf("crypto"))))
        assertNotEquals("a word unblocked", before, feedDigest(body, parseSettingsKey(25, 7, emptySet())))
        assertEquals(
            "the order and case of the words do not matter",
            parseSettingsKey(25, 7, listOf("B", "a")),
            parseSettingsKey(25, 7, listOf(" a", "b", "")),
        )
    }

    @Test
    fun `an identical download reads as same, with what it cost`() {
        val clock = SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val same = FeedFetch(12 * 3_600_000L, FetchKind.Same, bytes = 137_000)
        assertEquals("12:00 same 137 KB", FeedHistoryCodec.describe(same, clock))
        assertEquals(listOf(same), FeedHistoryCodec.decode(FeedHistoryCodec.encode(listOf(same))))
        val lines = FeedHistoryCodec.summary(listOf("Android Central" to listOf(same)))
        assertEquals("still counted as a download", "One download of every feed: 137 KB (measured for 1 of 1)", lines[0])
        assertEquals(
            "ok (120 feeds, 30 unchanged, 8 identical)",
            syncOutcome(SyncResult(due = 120, unchanged = 30, identical = 8)),
        )
    }

    // --- icons ------------------------------------------------------------

    @Test
    fun `a site with no icon is asked again after a week, not every sync`() {
        assertTrue("never asked", iconLookupDue(0L, NOW))
        assertFalse(iconLookupDue(NOW - 3_600_000L, NOW))
        assertTrue(iconLookupDue(NOW - IconLookup.RETRY_AFTER_MS, NOW))
    }

    // --- wiring -----------------------------------------------------------

    private fun source(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    private val sync = source("manager/sync/RssLocalSync.kt")
    private val body = sync.substring(sync.indexOf("private suspend fun syncFeed("))

    @Test
    fun `find duplicates asks both the addresses and the articles`() {
        val repo = source("data/repository/SourcesRepository.kt")
        val groups = repo.substring(repo.indexOf("suspend fun duplicateReport()"))
        assertTrue(repo.contains("suspend fun duplicateGroups(): List<List<Feed>> = duplicateReport().map { it.feeds }"))
        assertTrue(groups.contains("normalizeFeedUrl(it.url)"))
        assertTrue(groups.contains("articleOverlap(articlesDao.loadFeedLinks()"))
        assertTrue(groups.contains(".filterValues { it >= SAME_ARTICLES_SHARE }"))
        assertTrue(groups.contains("joinPairs(sameAddress + overlap.keys)"))
        val page = source("ui/pages/SourceListPage.kt")
        assertTrue("offered without being asked", page.contains("if (!duplicatesOnly && duplicateCount > 0) {"))
        assertTrue(source("utils/Diagnostics.kt").contains("appendLine(\"Duplicates:        \${duplicates.size}\")"))
    }

    @Test
    fun `an identical download is not parsed, and the fingerprint is kept only once stored`() {
        val identical = body.indexOf("return FeedFetchResult.Identical")
        assertTrue(identical >= 0)
        assertTrue("before any parsing", identical < body.indexOf("FeedParser()"))
        val check = body.substring(body.indexOf("if (digest == FeedDigest.read(context, feedSql.id)"), identical)
        assertTrue("only when the source has articles", check.contains("articleRepo.countInFeed(feedSql.id) > 0"))
        assertTrue("old articles still go", check.contains("cleanUpFeed(articleRepo, feedSql, filesDir)"))
        val write = body.indexOf("FeedDigest.write(context, feedSql.id, digest)")
        assertTrue("after the articles are stored", write > body.indexOf("articleRepo.updateOrInsertArticle(articles)"))
        assertTrue(sync.contains("parseSettingsKey(maxFeedItemCount, getSyncDays(prefs), prefs.blockedWords.getValue())"))
    }

    @Test
    fun `the response is closed whichever way the check goes`() {
        val use = body.indexOf("val download = response.use {")
        assertTrue(use >= 0)
        assertTrue("the database is asked inside it", body.indexOf("articleRepo.countInFeed(feedSql.id) > 0") > use)
        assertFalse("no bare close left to miss", body.substringBefore("val feedParser").contains("response.close()"))
    }

    @Test
    fun `a sync reads the icon it stored instead of fetching the home page`() {
        assertTrue(body.contains("findIcon = false,"))
        val parser = source("manager/models/FeedParser.kt")
        assertTrue(parser.contains("if (findIcon) getFeedIconAtUrl(siteUrl) else null"))
        val lookup = body.substring(body.indexOf("val resolvedIcon = when {"))
        val gate = lookup.indexOf("!IconLookup.due(context, feedSql.id) -> null")
        assertTrue(gate >= 0)
        assertTrue("stored icons still come first", lookup.indexOf("storedIcon != null -> storedIcon") in 0 until gate)
        assertTrue(
            "the attempt is recorded before it is made",
            lookup.indexOf("IconLookup.tried(context, feedSql.id)") in gate until lookup.indexOf("feedParser.findSiteIcon(site)"),
        )
    }
}

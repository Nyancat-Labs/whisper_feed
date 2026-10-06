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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Silencing a source, which is now one state reached two ways.
 *
 * Hiding and switching off were separate: both kept a source's articles out
 * of the feed, and the only thing between them was whether it carried on
 * syncing. Hiding something should stop it costing data and battery, so there
 * is one state — the feed's own column — and the article menu and the sources
 * list both set it.
 *
 * Read from the DAO source, because the distinction lives in SQL and a test
 * that restated it in Kotlin would pass while the query said otherwise.
 */
class SourceVisibilityTest {

    private val dao = File("src/main/java/com/saulhdev/feeder/data/db/dao/FeedArticleDao.kt")
        .readText()

    /**
     * The SQL above a declaration, and only the SQL.
     *
     * Taking everything between `@Query` and the function caught the KDoc in
     * between — which, on the very function this tests, *explains* the clause
     * that was removed. The test read the explanation as the query and failed
     * on correct code. The triple quotes are the boundary that means
     * something.
     */
    private fun query(afterFunctionNamed: String): String {
        val at = dao.indexOf("fun $afterFunctionNamed")
        assertTrue("no function named $afterFunctionNamed", at > 0)
        val start = dao.lastIndexOf("@Query", at)
        val open = dao.indexOf("\"\"\"", start)
        val close = dao.indexOf("\"\"\"", open + 3)
        assertTrue("no SQL block above $afterFunctionNamed", open in (start + 1)..<close)
        return dao.substring(open + 3, close)
    }

    /**
     * The bug this was written for. Turning a source off took every article
     * the reader had saved from it out of Bookmarks — a switch about whether
     * to keep fetching, quietly revoking the strongest thing anybody says
     * about an article.
     */
    @Test
    fun `saved articles survive their source being switched off`() {
        val bookmarks = query("getAllBookmarkedFeedItems")
        assertTrue("the bookmarks query should still select on bookmarked",
            bookmarks.contains("bookmarked = 1"))
        assertFalse(
            "a saved article must not disappear because its source was switched off",
            bookmarks.contains("isEnabled"),
        )
    }

    /** And the feed itself still respects the switch, which is its whole job. */
    @Test
    fun `the feed excludes a source that is switched off`() {
        assertTrue(query("getAllEnabledFeedItems").contains("Feeds.isEnabled = 1"))
    }

    /**
     * The collapse is only safe because of the test above.
     *
     * While switching a source off still emptied Bookmarks of everything
     * saved from it, making "hide" mean "switch off" would have made hiding
     * do that too. These two assertions are one argument, and separating them
     * would let a later change to either quietly undo it.
     */
    @Test
    fun `hiding a source cannot cost the reader their saved articles`() {
        assertFalse(query("getAllBookmarkedFeedItems").contains("isEnabled"))
        assertTrue(query("getAllEnabledFeedItems").contains("isEnabled"))
    }

    private val sourceDao =
        File("src/main/java/com/saulhdev/feeder/data/db/dao/FeedSourceDao.kt").readText()

    private fun sourceQuery(afterFunctionNamed: String): String {
        val at = sourceDao.indexOf("fun $afterFunctionNamed")
        assertTrue("no function named $afterFunctionNamed", at > 0)
        val start = sourceDao.lastIndexOf("@Query", at)
        val quoted = sourceDao.substring(start, at)
        // These are one-line @Query("…") rather than triple-quoted blocks.
        val open = quoted.indexOf('"')
        val close = quoted.lastIndexOf('"')
        return quoted.substring(open + 1, close)
    }

    /* --------------------------------------------------- removed sources -- */

    /**
     * The rule this was built for: a bookmark lasts until it is taken back.
     *
     * Articles carry a foreign key onto their feed with onDelete = CASCADE,
     * so removing a source was what destroyed the articles saved from it.
     * Unsubscribing from a site is not the same as deciding you no longer
     * want what you kept from it.
     */
    @Test
    fun `routine cleanup never touches a saved article`() {
        val cleanup = query("getItemsToBeCleanedFromFeed")
        assertTrue(cleanup.contains("bookmarked = 0"))
        assertTrue(cleanup.contains("pinned = 0"))
    }

    @Test
    fun `clearing a source keeps what was saved from it`() {
        val clear = query("clearArticlesForFeeds")
        assertTrue(clear.contains("bookmarked = 0"))
        assertTrue(clear.contains("pinned = 0"))
    }

    /** A removed source is gone from every listing, not merely switched off. */
    @Test
    fun `a removed source is absent from the listings`() {
        listOf(
            "loadFeeds", "getAllFeeds", "loadAllFeeds", "loadFeedIds",
            "getEnabledFeeds", "getAllTags", "getAllTagsFlow",
            "loadFeedsByTag", "getFailingFeeds", "getFeedByURL",
        ).forEach { name ->
            assertTrue(
                "$name would still show a removed source",
                sourceQuery(name).contains("removedAt = 0"),
            )
        }
    }

    /** And it is not synced, or a source nobody has would still cost data. */
    @Test
    fun `a removed source is not synced`() {
        assertTrue(sourceQuery("loadFeedIfStale").contains("removedAt = 0"))
    }

    /**
     * The row is kept for the bookmarks' sake, so the query that finds them
     * must not be the one thing that filters it out.
     */
    @Test
    fun `bookmarks are not filtered by anything about the source`() {
        val bookmarks = query("getAllBookmarkedFeedItems")
        assertFalse(bookmarks.contains("removedAt"))
        assertFalse(bookmarks.contains("isEnabled"))
    }
}

package com.saulhdev.feeder

import com.saulhdev.feeder.utils.sloppyLinkToStrictURL
import com.saulhdev.feeder.utils.usableImageUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A source with no icon has no icon, rather than the address `https:`.
 *
 * `Feed.feedImage` defaults to `sloppyLinkToStrictURL("")`. `URL("")` throws,
 * the fallback retries it as `URL("https://")` — which prints back as
 * `https:` — and that is what every icon-less source stored. It is not blank, so it passed every check written
 * as `isNotBlank()`, with two results:
 *
 * - every card from such a source asked for it and OkHttp refused it with
 *   `Invalid URL host: ""` — 168 of 182 image failures in one report;
 * - the sync step that looks up a missing icon from the site read it as an
 *   icon already found, so those sources never got one and kept a monogram.
 */
class SourceIconTest {

    private fun source(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    @Test
    fun `the default is the address it was found to be`() {
        // Pinned, because the whole finding rests on it: if this default ever
        // changes, the reasoning in FeedItem.feedIconUrl needs revisiting.
        // `URL("https://")` prints itself back without the slashes.
        assertEquals("https:", sloppyLinkToStrictURL("").toString())
    }

    @Test
    fun `the default is not taken for an icon`() {
        assertNull(usableImageUrl(sloppyLinkToStrictURL("").toString()))
    }

    @Test
    fun `every reader of the icon asks whether it is usable`() {
        assertTrue(
            source("data/db/models/FeedItem.kt")
                .contains("get() = usableImageUrl(feed.feedImage.toString())"),
        )
        assertTrue(
            "the reader's header",
            source("ui/pages/ArticlePage.kt")
                .contains("iconUrl = usableImageUrl(state?.source?.feedImage?.toString())"),
        )
        assertTrue(
            "What Whisper has learned",
            source("viewmodels/LearnedViewModel.kt")
                .contains("iconUrl = usableImageUrl(feed.feedImage.toString())"),
        )
    }

    @Test
    fun `sync looks the icon up when the stored one is the placeholder`() {
        val sync = source("manager/sync/RssLocalSync.kt")
        assertTrue(sync.contains("val storedIcon = usableImageUrl(syncedFeed.feedImage.toString())"))
        assertFalse(
            "a blank check is what kept those sources on a monogram",
            sync.contains("storedIcon.isNotBlank()"),
        )
    }

    @Test
    fun `the report asks WorkManager under the name the scheduler uses`() {
        val activity = source("MainActivity.kt")
        val diagnostics = source("utils/Diagnostics.kt")
        assertFalse(
            "one name, in one place",
            activity.contains("\"feeder_periodic_3\"") || diagnostics.contains("\"feeder_periodic_3\""),
        )
        assertTrue(activity.contains("PERIODIC_SYNC_WORK"))
        assertTrue(diagnostics.contains("getWorkInfosForUniqueWorkFlow(PERIODIC_SYNC_WORK)"))
        assertTrue(diagnostics.contains("== Sync =="))
    }
}

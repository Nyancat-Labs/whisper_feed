package com.saulhdev.feeder

import com.saulhdev.feeder.manager.models.FeedParser
import com.saulhdev.feeder.manager.models.withoutTrailingJunk
import com.saulhdev.feeder.utils.FeedFetch
import com.saulhdev.feeder.utils.FeedHistory
import com.saulhdev.feeder.utils.FetchKind
import com.saulhdev.feeder.utils.HttpStatusException
import com.saulhdev.feeder.utils.errorKind
import com.saulhdev.feeder.viewmodels.refusesApps
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.SocketTimeoutException
import java.net.URL

/** Batch 7, 19 November (sheet 117, 118, 120). */
class Batch7Test {

    private fun source(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    // 117: as Darknet sends it, a caching plugin's script after </rss>.
    private val darknet = """<?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0"><channel><title>Darknet</title><link>https://www.darknet.org.uk/</link>
        <description>d</description><item><title>A tool</title><link>https://www.darknet.org.uk/a</link>
        <description>x</description></item></channel></rss>
        <script>(function(){var x=1;})();</script>"""

    private fun parse(xml: String) = runBlocking {
        FeedParser().parseRssAtom(
            URL("https://www.darknet.org.uk/feed/"),
            xml.toResponseBody("application/rss+xml; charset=utf-8".toMediaType()),
            findIcon = false,
        )
    }

    @Test
    fun `a feed with a script after its end is read`() {
        val feed = parse(darknet)
        assertEquals("Darknet", feed.title)
        assertEquals(1, feed.items?.size)
    }

    @Test
    fun `only what follows the feed's end is cut, and a sound feed is left alone`() {
        val cut = String(withoutTrailingJunk(darknet.toByteArray())!!)
        assertTrue(cut.endsWith("</rss>"))
        assertNull("nothing after", withoutTrailingJunk("<rss><channel/></rss>\n  ".toByteArray()))
        assertNull("no end at all", withoutTrailingJunk("<html><body>no</body></html>".toByteArray()))
        val atom = String(withoutTrailingJunk("<feed xmlns='x'></feed><!-- cache -->x".toByteArray())!!)
        assertTrue(atom.endsWith("</feed>"))
    }

    @Test
    fun `the diagnostics name the error in words`() {
        val unreadable = runCatching { parse("<rss><channel><title>t</title></chan") }.exceptionOrNull()!!
        assertEquals("not a readable feed", errorKind(unreadable))
        assertEquals("timed out", errorKind(FeedParser.FeedParsingError(URL("https://x.test/"), SocketTimeoutException())))
        assertEquals("IllegalStateException", errorKind(IllegalStateException("at https://x.test/a")))
    }

    @Test
    fun `a 403 is a site turning apps away, other refusals are not`() {
        assertTrue(refusesApps(FeedParser.FeedParsingError(URL("https://x.test/"), HttpStatusException(403))))
        assertFalse(refusesApps(HttpStatusException(404)))
        val refused = FeedFetch(3, FetchKind.Failed, "403")
        val offline = FeedFetch(4, FetchKind.NoNetwork)
        assertTrue(FeedHistory.refusesApps(listOf(offline, refused)))
        assertFalse(FeedHistory.refusesApps(listOf(FeedFetch(5, FetchKind.New, "2"), refused)))
        assertFalse(FeedHistory.refusesApps(listOf(FeedFetch(5, FetchKind.Failed, "503"))))
    }

    @Test
    fun `both places say the site blocks apps, with a way to check`() {
        assertTrue(source("ui/pages/SourceAddPage.kt").contains("SearchFailure.BlocksApps ->"))
        val broken = source("ui/pages/BrokenFeedsPage.kt")
        assertTrue(broken.contains("FeedHistory.refusesApps("))
        assertTrue(broken.contains("R.string.open_feed_in_browser"))
        assertTrue(broken.contains("if (!refused) when (state)"))
    }

    // 118
    @Test
    fun `no old spinning ring is left`() {
        File("src/main/java").walkTopDown().filter { it.extension == "kt" }.forEach {
            assertFalse(it.name, it.readText().contains("CircularProgressIndicator"))
        }
    }

    // 120
    @Test
    fun `every page's messages use Whisper's own box`() {
        File("src/main/java/com/saulhdev/feeder/ui/pages").listFiles()!!.forEach {
            assertFalse(it.name, Regex("""[^d]SnackbarHost\(snackbarHostState""").containsMatchIn(it.readText()))
        }
    }
}

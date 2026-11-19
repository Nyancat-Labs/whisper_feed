package com.saulhdev.feeder

import com.rometools.rome.io.SyndFeedInput
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.manager.models.OPMLParser
import com.saulhdev.feeder.manager.models.ParserToDatabase
import com.saulhdev.feeder.manager.models.writeOutputStream
import com.saulhdev.feeder.manager.sync.BreakingAlert
import com.saulhdev.feeder.manager.sync.asleepMinutes
import com.saulhdev.feeder.manager.sync.greader.isHomeNetworkHost
import com.saulhdev.feeder.utils.MarkdownLite
import com.saulhdev.feeder.utils.extensions.contentText
import com.saulhdev.feeder.utils.extensions.thumbnail
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringReader
import java.net.URL

/** Batch 6, 19 November (sheet 15, 45, 73, 111, 112, 113, 114, 115, 116). */
class Batch6Test {

    private fun source(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()
    private val strings = File("src/main/res/values/strings.xml").readText()

    // 45: as the Cyberspace feed sends it.
    private val theyLive =
        "Just watched **They Live (1988)** for the first time!<br><br>" +
            "![Zizek.jpg](https://bunker.cyberspace.online/uploads/post/a/1-b.webp)<br><br>" +
            "I&apos;m in Japan."
    private val art = "```<br>___ ___<br>|\\ \\ |\\ \\<br>```<br><br>## **CyberOS v4.3**<br>- New icon<br>- `/coin` for a coin-flip"

    @Test
    fun `Markdown is recognised, HTML and plain text are not`() {
        assertTrue(MarkdownLite.isMarkdown(theyLive))
        assertTrue(MarkdownLite.isMarkdown(art))
        assertFalse(MarkdownLite.isMarkdown("<p>Ordinary <b>HTML</b> with ![this](x.jpg) inside</p>"))
        assertFalse(MarkdownLite.isMarkdown("Just a sentence.<br>And another."))
        assertFalse(MarkdownLite.isMarkdown(null))
    }

    @Test
    fun `Markdown becomes pictures, bold, code blocks with their spacing, and lists`() {
        val html = MarkdownLite.toHtml(theyLive)
        assertTrue(html.contains("""<img src="https://bunker.cyberspace.online/uploads/post/a/1-b.webp" alt="Zizek.jpg">"""))
        assertTrue(html.contains("<strong>They Live (1988)</strong>"))
        assertTrue("entities left for the reader to decode", html.contains("I&apos;m in Japan."))
        val artHtml = MarkdownLite.toHtml(art)
        assertTrue(artHtml.contains("<pre><code>___ ___\n|\\ \\ |\\ \\</code></pre>"))
        assertTrue(artHtml.contains("<h2><strong>CyberOS v4.3</strong></h2>"))
        assertTrue(artHtml.contains("<ul><li>New icon</li><li><code>/coin</code> for a coin-flip</li></ul>"))
    }

    @Test
    fun `a Markdown link to javascript is left as text`() {
        assertFalse(MarkdownLite.toHtml("[click](javascript:alert(1)) **x**").contains("href=\"javascript"))
    }

    @Test
    fun `the photo of a Markdown post becomes the card's picture, and the summary loses the marks`() {
        val entry = SyndFeedInput().build(
            StringReader(
                """<rss version="2.0"><channel><title>Cyberspace</title><link>https://cyberspace.online/</link>
                <description>d</description><item><title>They Live by @quasimoto</title>
                <link>https://cyberspace.online/q/1</link><description><![CDATA[$theyLive]]></description></item>
                </channel></rss>""",
            ),
        ).entries.single()
        assertEquals(
            "https://bunker.cyberspace.online/uploads/post/a/1-b.webp",
            entry.thumbnail(URL("https://cyberspace.online/feed.xml")),
        )
        assertFalse(entry.contentText().contains("!["))
        assertFalse(entry.contentText().contains("**"))
    }

    // 73
    @Test
    fun `home addresses are recognised by host`() {
        assertTrue(isHomeNetworkHost("10.0.1.10"))
        assertTrue(isHomeNetworkHost("192.168.1.5"))
        assertTrue(isHomeNetworkHost("nas.local"))
        assertFalse(isHomeNetworkHost("example.com"))
        assertFalse(isHomeNetworkHost("10.0.1.10.example.com"))
        assertTrue(source("ui/pages/SourceAddPage.kt").contains("isHomeNetworkHost(hostOf(error.url))"))
    }

    // 111
    @Test
    fun `a feed that comes back empty counts as a failure`() {
        val sync = source("manager/sync/RssLocalSync.kt")
        assertTrue(sync.contains("if (fetched is FeedFetchResult.Parsed && fetched.itemsInFeed == 0) {"))
        assertTrue(sync.contains("itemsInFeed = items?.size ?: 0,"))
    }

    // 112
    @Test
    fun `sleep is told apart from a slow sign-in, and sign-in has a limit`() {
        assertEquals(55, asleepMinutes(totalMs = 3_788_000, awakeMs = 480_000))
        assertEquals(0, asleepMinutes(totalMs = 60_000, awakeMs = 61_000))
        assertTrue(source("manager/sync/greader/GoogleReaderApi.kt").contains(".callTimeout(SIGN_IN_TIMEOUT_S, TimeUnit.SECONDS)"))
    }

    // 113
    @Test
    fun `the source's choice reads Default`() {
        assertTrue(strings.contains("<string name=\"source_open_as_settings\">Default</string>"))
    }

    // 114
    @Test
    fun `a backup keeps each source's own settings, and a plain OPML gets the defaults`() = runBlocking {
        val feeds = listOf(
            Feed(title = "Paid", url = URL("https://news.example/feed"), fullTextByDefault = true,
                showInAll = false, isEnabled = false, openMode = "browser"),
            Feed(title = "Plain", url = URL("https://plain.example/feed")),
        )
        val out = ByteArrayOutputStream()
        writeOutputStream(out, mapOf("" to feeds))
        val xml = out.toString(Charsets.UTF_8.name())
        assertTrue(xml.contains("whisperOpenMode=\"browser\""))
        assertFalse("defaults are not written", Regex("Plain[^>]*whisper").containsMatchIn(xml))

        val restored = mutableListOf<Feed>()
        OPMLParser(object : ParserToDatabase<Feed> {
            override suspend fun getItem(id: String): Feed? = null
            override suspend fun saveItem(item: Feed) { restored += item }
        }).parseInputStream(ByteArrayInputStream(xml.toByteArray()))
        val paid = restored.single { it.title == "Paid" }
        assertTrue(paid.fullTextByDefault)
        assertFalse(paid.showInAll)
        assertFalse(paid.isEnabled)
        assertEquals("browser", paid.openMode)
        val plain = restored.single { it.title == "Plain" }
        assertFalse(plain.fullTextByDefault)
        assertTrue(plain.showInAll && plain.isEnabled)
        assertEquals("", plain.openMode)
    }

    // 115
    @Test
    fun `the notification carries the headline, one per update`() {
        val alert = source("manager/sync/BreakingAlert.kt")
        assertTrue(alert.contains(".setContentTitle(story.lead.contentTitle)"))
        assertFalse(alert.contains(".setContentTitle(story.lead.displayTitle)"))
        assertEquals(1, BreakingAlert.PER_UPDATE)
    }

    // 116
    @Test
    fun `a broken feed can be edited from where it is reported`() {
        assertTrue(source("ui/pages/BrokenFeedsPage.kt").contains("navController.navigate(NavRoute.SourceEdit(feed.id))"))
        assertTrue(source("ui/navigation/NavigationManager.kt").contains("composable<NavRoute.SourceEdit>"))
    }

    // 15
    @Test
    fun `Sign out is not drawn as a warning, and dialog buttons are pills`() {
        val account = source("ui/pages/AccountPage.kt")
        val signOut = account.substringAfter("R.string.account_sign_out),").substringBefore("onClick")
        assertTrue(signOut.contains("destructive = false"))
        assertFalse(source("ui/components/dialog/DialogButton.kt").contains("shape = MaterialTheme.shapes.large"))
    }
}

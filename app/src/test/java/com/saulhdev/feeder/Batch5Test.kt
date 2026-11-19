package com.saulhdev.feeder

import com.rometools.rome.io.SyndFeedInput
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.db.models.Article
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.manager.sync.BreakingAlert
import com.saulhdev.feeder.manager.sync.greader.AccountProblem
import com.saulhdev.feeder.manager.sync.greader.mentionsHomeNetwork
import com.saulhdev.feeder.manager.sync.greader.problemFrom
import com.saulhdev.feeder.ui.overlay.clusterStories
import com.saulhdev.feeder.utils.dropEmptySlots
import com.saulhdev.feeder.utils.dropRelatedBlocks
import com.saulhdev.feeder.utils.extensions.headline
import com.saulhdev.feeder.utils.extensions.looksLikeFileName
import com.saulhdev.feeder.utils.extensions.thumbnail
import com.saulhdev.feeder.utils.statedAspectRatio
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.StringReader
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URL
import kotlin.time.Instant

private const val NOW5 = 1_800_000_000_000L

private fun newsItem(id: String, title: String, source: String) = FeedItem(
    article = Article(
        uuid = id,
        title = title,
        primarySortTime = Instant.fromEpochMilliseconds(NOW5 - 30 * 60_000),
    ),
    feed = Feed(
        id = source.hashCode().toLong(),
        title = source,
        tag = "News",
        url = URL("https://${source.lowercase()}.example/feed"),
    ),
)

/** Batch 5, 19 November: reader, notifications, per-source opening (sheet 45, 52, 71, 73, 76, 80, 113). */
class Batch5Test {

    private fun source(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    // 52
    @Test
    fun `the reader's second line gives the date, not the time again`() {
        val page = source("ui/pages/ArticlePage.kt")
        assertTrue(page.contains("DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)"))
        assertFalse(page.contains("ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.SHORT)"))
    }

    // 73
    @Test
    fun `a failed connection to a home-network address says so`() {
        assertEquals(
            AccountProblem.HOME_NETWORK,
            problemFrom(ConnectException("Failed to connect to /192.168.1.20:443")),
        )
        assertEquals(
            AccountProblem.HOME_NETWORK,
            problemFrom(SocketTimeoutException("failed to connect to nas.local/10.0.0.5 (port 443) after 10000ms")),
        )
        assertEquals(AccountProblem.REFUSED, problemFrom(ConnectException("Failed to connect to rss.example.com/93.184.216.34:443")))
        assertTrue(mentionsHomeNetwork("172.16.4.1"))
        assertFalse(mentionsHomeNetwork("172.32.0.1"))
        assertFalse("a version number is not an address", mentionsHomeNetwork("v10.0.0.15"))
    }

    // 113
    @Test
    fun `a source's own choice wins, otherwise Settings`() {
        val r = FeedPreferences.OPEN_MODE_READER
        val b = FeedPreferences.OPEN_MODE_BROWSER
        assertEquals(b, FeedPreferences.openModeFor(b, r))
        assertEquals(r, FeedPreferences.openModeFor(r, b))
        assertEquals(b, FeedPreferences.openModeFor("", b))
        assertEquals(r, FeedPreferences.openModeFor("", r))
        assertEquals("an unknown value follows Settings", r, FeedPreferences.openModeFor("custom", r))
    }

    @Test
    fun `every place that opens an article asks the source first`() {
        assertEquals(2, Regex("openModeFor\\(item.feed.openMode, openMode\\)").findAll(source("ui/pages/ArticleListPage.kt")).count())
        assertTrue(source("manager/service/OverlayView.kt").contains("openModeFor(item.feed.openMode, prefs.articleOpenMode.getValue())"))
        assertTrue(source("data/db/NeoFeedDb.kt").contains("ALTER TABLE Feeds ADD COLUMN openMode TEXT NOT NULL DEFAULT ''"))
    }

    // 80
    @Test
    fun `one notification per new story, within the day's allowance`() {
        val items = listOf(
            newsItem("a", "Hurricane Delphine makes landfall in Florida", "BBC"),
            newsItem("b", "Delphine strikes Florida coast as thousands evacuate", "Reuters"),
            newsItem("c", "Florida braces as Hurricane Delphine arrives", "Guardian"),
            newsItem("d", "A quiet day at the allotment", "Blog"),
        )
        val clusters = clusterStories(items, NOW5)
        assertEquals(3, clusters.size)
        val fresh = BreakingAlert.newStories(items, clusters, told = emptySet(), allowance = 3)
        assertEquals(1, fresh.size)
        assertEquals(3, fresh.single().sources)
        assertTrue("told before", BreakingAlert.newStories(items, clusters, setOf("b"), 3).isEmpty())
        assertTrue("no allowance left", BreakingAlert.newStories(items, clusters, emptySet(), 0).isEmpty())
    }

    @Test
    fun `breaking alerts are off until switched on`() {
        val prefs = source("data/content/FeedPreferences.kt")
        val pref = prefs.substringAfter("var breakingAlerts = BooleanPref(").substringBefore(")")
        assertTrue(pref.contains("defaultValue = false"))
        assertTrue(source("ui/pages/SettingsSection.kt").contains("prefs.breakingAlerts,"))
    }

    // 45
    @Test
    fun `a picture file name is not a headline`() {
        assertTrue(looksLikeFileName("!IMG20261020165835.jpg"))
        assertTrue(looksLikeFileName("PXL_20261101_101500123.jpg"))
        assertFalse(looksLikeFileName("Headlights by @techdaddy_proxy"))
        assertFalse(looksLikeFileName("Node.js 24 is out"))
        assertEquals("Headlights at night", headline("!IMG20261020165835.jpg", "\n Headlights at night\nmore"))
        assertEquals("with no text, the title stays", "IMG_1234.jpg", headline("IMG_1234.jpg", ""))
        assertEquals("Ordinary title", headline("Ordinary title", "Body"))
    }

    private fun entry(xml: String) = SyndFeedInput().build(StringReader(xml)).entries.single()

    @Test
    fun `a photo attached without a type, or inside a media group, is found`() {
        val untyped = entry(
            """<rss version="2.0"><channel><title>t</title><link>https://p.example/</link><description>d</description>
              <item><title>x</title><link>https://p.example/1</link>
              <enclosure url="https://p.example/a/photo.jpg" length="0" type=""/></item></channel></rss>""",
        )
        assertEquals("https://p.example/a/photo.jpg", untyped.thumbnail(URL("https://p.example/feed")))
        val grouped = entry(
            """<rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/"><channel><title>t</title>
              <link>https://p.example/</link><description>d</description>
              <item><title>x</title><link>https://p.example/2</link>
              <media:group><media:content url="https://p.example/big.jpg" medium="image" width="1600" height="1200"/>
              <media:content url="https://p.example/small.jpg" medium="image" width="320" height="240"/></media:group>
              </item></channel></rss>""",
        )
        assertEquals("https://p.example/big.jpg", grouped.thumbnail(URL("https://p.example/feed")))
    }

    // 76
    @Test
    fun `BFMTV's related block goes, the article stays`() {
        val body = Jsoup.parse(
            """<p>Le député a été retrouvé mort mardi.</p>
               <h2>Sur le même sujet</h2>
               <ul><li><a href="/a">Un autre article</a> 2:31</li><li><a href="/b">Encore un</a></li></ul>
               <p>Il avait 70 ans.</p>""",
            "https://www.bfmtv.com/x",
        ).body()
        dropRelatedBlocks(body, "https://www.bfmtv.com/x")
        assertEquals("Le député a été retrouvé mort mardi. Il avait 70 ans.", body.text())
    }

    @Test
    fun `Le Figaro's opening read-also goes, a link inside a sentence stays`() {
        val body = Jsoup.parse(
            """<p><a href="https://www.lefigaro.fr/autre">Cyberattaque : ce que l'on sait</a></p>
               <p>Lire aussi : <a href="https://www.lefigaro.fr/b">La menace grandit</a></p>
               <p>Selon <a href="https://www.anssi.gouv.fr/">l'Anssi</a>, la menace est grave.</p>""",
            "https://www.lefigaro.fr/x",
        ).body()
        dropRelatedBlocks(body, "https://www.lefigaro.fr/x")
        assertEquals("Selon l'Anssi, la menace est grave.", body.text())
    }

    @Test
    fun `a heading that only sounds similar, followed by prose, keeps the prose`() {
        val body = Jsoup.parse(
            "<h2>Read more</h2><p>The rest of this story is about why the bridge closed and when it will open again.</p>",
            "https://news.example/x",
        ).body()
        dropRelatedBlocks(body, "https://news.example/x")
        assertTrue(body.text().startsWith("The rest of this story"))
    }

    // 71
    @Test
    fun `pictures keep their stated shape, and empty ad slots go`() {
        val img = Jsoup.parse("""<img src="a.jpg" width="640" height="1600">""").selectFirst("img")!!
        assertEquals(0.4f, statedAspectRatio(img)!!, 0.001f)
        assertNull(statedAspectRatio(Jsoup.parse("""<img src="a.jpg" width="1" height="1">""").selectFirst("img")!!))
        val body = Jsoup.parse(
            """<p>One</p><div class="ad-slot"><div></div></div><figure><img src="b.jpg"></figure><ins></ins><p>Two</p>""",
        ).body()
        dropEmptySlots(body)
        assertEquals(1, body.select("img").size)
        assertTrue(body.select("div, ins").isEmpty())
    }
}

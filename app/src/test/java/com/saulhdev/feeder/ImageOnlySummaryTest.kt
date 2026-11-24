package com.saulhdev.feeder

import com.rometools.rome.io.SyndFeedInput
import com.saulhdev.feeder.data.db.models.Article
import com.saulhdev.feeder.data.entity.Item
import com.saulhdev.feeder.data.entity.JsonFeed
import com.saulhdev.feeder.utils.HtmlToPlainTextConverter
import com.saulhdev.feeder.utils.extensions.asItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.StringReader
import java.net.URL

/** Szept 452: an image-only description showed "[IMG]" as the card's summary. */
class ImageOnlySummaryTest {
    // The shape of GSMArena's review descriptions: a picture with an empty alt, then an empty paragraph.
    private val gsmArena =
        """<img src="https://fdn.gsmarena.com/imgroot/reviews/26/honor-magic9-pro-max/-347x151/gsmarena_001.jpg" alt=""><p></p>"""

    private fun entry(description: String) = SyndFeedInput().build(
        StringReader(
            """<rss version="2.0"><channel><title>GSMArena.com - Latest articles</title>
            <link>https://www.gsmarena.com/</link><description>d</description>
            <item><title>Honor Magic9 Pro Max review</title><link>https://www.gsmarena.com/r-1.php</link>
            <description><![CDATA[$description]]></description></item></channel></rss>""",
        ),
    ).entries.single()

    @Test
    fun `a picture with an empty alt gives no text`() {
        assertEquals("", HtmlToPlainTextConverter().convert(gsmArena))
        assertEquals("", HtmlToPlainTextConverter().convert("""<img src="a.jpg">"""))
        assertEquals("", HtmlToPlainTextConverter().convert("""<img src="a.jpg" alt="   ">"""))
    }

    @Test
    fun `a picture with real alt text still stands in for the text, as xkcd's does`() {
        assertEquals(
            "[Rock layers]",
            HtmlToPlainTextConverter().convert("""<img src="strat.png" alt="Rock layers">"""),
        )
        // A later blank alt does not wipe out the real one before it.
        assertEquals(
            "[Rock layers]",
            HtmlToPlainTextConverter().convert("""<img src="a.png" alt="Rock layers"><img src="b.png" alt="">"""),
        )
    }

    @Test
    fun `GSMArena's review gets an empty summary, not a marker`() {
        val item = entry(gsmArena).asItem(URL("https://www.gsmarena.com/rss-news-reviews.php3"))
        assertEquals("", item.content_text)
        assertEquals("", item.summary)
        assertEquals("Honor Magic9 Pro Max review", item.title)
    }

    @Test
    fun `an article stored with an old marker loses it when parsed again`() {
        val feed = JsonFeed(title = "GSMArena", feed_url = "https://www.gsmarena.com/rss-news-reviews.php3", items = emptyList())
        val parsed = Item(id = "r-1", title = "Honor Magic9 Pro Max review", content_text = "", summary = "")
        for (old in listOf("[IMG]", "<image>")) {
            val stored = Article(guid = "r-1", feedId = 1, description = old, plainSnippet = old)
            val updated = stored.updateFromParsedEntry(parsed, "r-1", feed, 1)
            assertEquals("", updated.description)
            assertEquals("", updated.plainSnippet)
        }
        // A real description is still kept when the feed sends none.
        val kept = Article(guid = "r-1", feedId = 1, description = "Honor's new flagship")
            .updateFromParsedEntry(parsed, "r-1", feed, 1)
        assertEquals("Honor's new flagship", kept.description)
    }
}

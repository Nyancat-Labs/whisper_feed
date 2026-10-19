package com.saulhdev.feeder

import com.saulhdev.feeder.data.db.models.Article
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.viewmodels.SEARCH_HEADLINE
import com.saulhdev.feeder.viewmodels.SEARCH_NO_MATCH
import com.saulhdev.feeder.viewmodels.SEARCH_SOURCE
import com.saulhdev.feeder.viewmodels.SEARCH_SUMMARY
import com.saulhdev.feeder.viewmodels.matchesSearch
import com.saulhdev.feeder.viewmodels.searchRank
import com.saulhdev.feeder.viewmodels.siteName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URL

class SearchRankTest {

    private fun item(
        title: String,
        source: String,
        link: String,
        feedUrl: String,
        summary: String = "",
    ) = FeedItem(
        article = Article(title = title, description = summary, link = link),
        feed = Feed(title = source, url = URL(feedUrl)),
    )

    private val ars = item(
        "Anthropic gives outside AI watchdog access", "Ars Technica",
        "https://arstechnica.com/ai/2026/10/watchdog/", "https://feeds.arstechnica.com/arstechnica/index",
    )
    private val fromWired = item("A projector review", "Wired", "https://www.wired.com/review/projector/", "https://www.wired.com/feed/rss")
    private val wiredInHeadline = item(
        "Hard-wired habits are hard to break", "BBC News", "https://www.bbc.co.uk/news/1", "https://feeds.bbci.co.uk/news/rss.xml",
    )
    private val wiredInSummary = item(
        "Phones and sleep", "The Guardian", "https://www.theguardian.com/1", "https://www.theguardian.com/rss",
        summary = "As Wired reported last week, screens before bed…",
    )

    @Test
    fun `the source is found by its web address`() {
        assertEquals(SEARCH_SOURCE, ars.searchRank("arstech"))
        assertEquals(SEARCH_SOURCE, ars.searchRank("ars technica"))
        assertEquals(SEARCH_SOURCE, ars.searchRank("techn"))
        assertTrue(ars.matchesSearch("arstech"))
    }

    @Test
    fun `source, then headline, then summary`() {
        assertEquals(SEARCH_SOURCE, fromWired.searchRank("wired"))
        assertEquals(SEARCH_HEADLINE, wiredInHeadline.searchRank("wired"))
        assertEquals(SEARCH_SUMMARY, wiredInSummary.searchRank("Wired"))
        assertEquals(SEARCH_NO_MATCH, ars.searchRank("wired"))
    }

    @Test
    fun `only the site's own name is matched, not what every address shares`() {
        assertEquals("arstechnica", siteName("https://feeds.arstechnica.com/arstechnica/index"))
        assertEquals("bbc", siteName("https://www.bbc.co.uk/news/1"))
        assertEquals("bbci", siteName("https://feeds.bbci.co.uk/news/rss.xml"))
        assertEquals("soranews24", siteName("https://soranews24.com/feed/"))
        assertEquals(SEARCH_NO_MATCH, ars.searchRank("com"))
        assertEquals(SEARCH_NO_MATCH, wiredInSummary.searchRank("co"))
    }

    @Test
    fun `an article counts once, in the highest group it reaches`() {
        val both = item("Wired's best phones", "Wired", "https://www.wired.com/2/", "https://www.wired.com/feed/rss")
        assertEquals(SEARCH_SOURCE, both.searchRank("wired"))
    }

    @Test
    fun `a search is shown by rank, not in feed order`() {
        val vm = File("src/main/java/com/saulhdev/feeder/viewmodels/ArticleListViewModel.kt").readText()
        assertTrue(vm.contains("compareBy<FeedItem> { it.searchRank(terms) }.thenByDescending { it.timeMillis }"))
        // Ranked before the feed's own sort and its spreading of sources.
        assertTrue(vm.indexOf("it.searchRank(terms)") < vm.indexOf("spreadSources(sorted)"))
    }
}

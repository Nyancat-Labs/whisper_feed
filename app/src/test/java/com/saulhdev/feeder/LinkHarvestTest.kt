package com.saulhdev.feeder

import com.saulhdev.feeder.manager.discovery.LinkHarvest
import com.saulhdev.feeder.utils.registrableDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The whole of the app's discovery mechanism, which is a counting exercise.
 *
 * It has to be conservative in a specific way: a suggestion the reader did not
 * earn is worse than no suggestion, because the promise is that nothing
 * appears in this app that they did not choose. So the tests here are mostly
 * about what does *not* produce one.
 */
class LinkHarvestTest {

    private fun html(vararg hrefs: String) =
        "<html><body>" + hrefs.joinToString("") { "<a href=\"$it\">x</a>" } + "</body></html>"

    @Test
    fun `it finds the domains an article links to`() {
        val found = LinkHarvest.domainsIn(
            html("https://theverge.com/a", "https://arstechnica.com/b")
        )
        assertEquals(setOf("theverge.com", "arstechnica.com"), found)
    }

    @Test
    fun `one article linking nine times is one article`() {
        // Otherwise a single link-heavy roundup manufactures a suggestion on
        // its own, which is exactly the article least likely to mean anything.
        val found = LinkHarvest.domainsIn(
            html("https://theverge.com/a", "https://theverge.com/b", "https://theverge.com/c")
        )
        assertEquals(setOf("theverge.com"), found)
    }

    @Test
    fun `an article does not suggest the site it came from`() {
        val found = LinkHarvest.domainsIn(
            html("https://theverge.com/related", "https://arstechnica.com/x"),
            ownHost = "www.theverge.com",
        )
        assertEquals(setOf("arstechnica.com"), found)
    }

    @Test
    fun `share buttons and image hosts are never suggestions`() {
        // Every article links to these and for reasons that have nothing to do
        // with what the reader wants to follow. Counting them would give
        // everybody the same four suggestions.
        val found = LinkHarvest.domainsIn(
            html(
                "https://www.facebook.com/sharer",
                "https://twitter.com/intent/tweet",
                "https://t.co/abc",
                "https://arstechnica.com/real",
            )
        )
        assertEquals(setOf("arstechnica.com"), found)
    }

    @Test
    fun `a subscriber to a site is not offered that site`() {
        // Harvesting used to key hosts by stripping `www.` and `m.` while the
        // library half of discovery reduced them to the registrable domain, so
        // the two halves disagreed about what counts as the same publication.
        // A reader subscribed to `rss.nytimes.com` had "nytimes.com" harvested
        // as an unfollowed site and offered back to them.
        val counts = mapOf("nytimes.com" to 9)
        val subscribed = setOf(registrableDomain("rss.nytimes.com"))
        assertEquals(emptyList<Pair<String, Int>>(), LinkHarvest.candidates(counts, subscribed))
    }

    @Test
    fun `the two halves of discovery agree on what a publication is`() {
        // The concrete symptom: the library half stored a suggestion keyed
        // "nytimes.com", and on the next run the harvesting half did not
        // recognise its own subdomain candidate as the same thing and offered
        // the publication a second time.
        val alreadyOffered = setOf(registrableDomain("www.nytimes.com"))
        assertEquals(
            emptyList<Pair<String, Int>>(),
            LinkHarvest.candidates(mapOf("nytimes.com" to 9), alreadyOffered),
        )
    }

    @Test
    fun `one mention is a citation, not a pattern`() {
        val counts = mapOf("theverge.com" to 1, "arstechnica.com" to 5)
        val out = LinkHarvest.candidates(counts, alreadySubscribed = emptySet())
        assertEquals(listOf("arstechnica.com" to 5), out)
    }

    @Test
    fun `something already subscribed to is not a discovery`() {
        val counts = mapOf("arstechnica.com" to 9)
        val out = LinkHarvest.candidates(counts, alreadySubscribed = setOf("arstechnica.com"))
        assertTrue(out.isEmpty())
    }

    @Test
    fun `the most-linked comes first, because that is the strongest evidence`() {
        val counts = mapOf("a.com" to 3, "b.com" to 11, "c.com" to 7)
        val out = LinkHarvest.candidates(counts, alreadySubscribed = emptySet()).map { it.first }
        assertEquals(listOf("b.com", "c.com", "a.com"), out)
    }

    @Test
    fun `malformed html costs nothing`() {
        // Article bodies come from the open web and some of them are broken.
        // One unparseable article must not stop the pass.
        assertEquals(emptySet<String>(), LinkHarvest.domainsIn("<<<not really html"))
        assertEquals(emptySet<String>(), LinkHarvest.domainsIn(""))
    }

    @Test
    fun `a tally counts articles rather than links`() {
        val counts = mutableMapOf<String, Int>()
        LinkHarvest.tally(counts, setOf("a.com", "b.com"))
        LinkHarvest.tally(counts, setOf("a.com"))
        assertEquals(mapOf("a.com" to 2, "b.com" to 1), counts)
    }
}

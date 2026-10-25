package com.saulhdev.feeder

import com.saulhdev.feeder.utils.FeedNameSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "the potato" finding thepotato.ie, without asking any search service. */
class FeedNameSearchTest {

    @Test
    fun `a name becomes the addresses it most likely has, the phone's country first`() {
        val hosts = FeedNameSearch.candidateHosts("The Potato", "IE")
        assertEquals("thepotato.ie", hosts.first())
        assertEquals("thepotato.com", hosts[1])
        assertTrue("the hyphenated spelling too", hosts.contains("the-potato.ie"))
        assertTrue(hosts.size <= FeedNameSearch.MAX_GUESSES)
    }

    @Test
    fun `the UK is co dot uk, and America has no domain of its own to try first`() {
        assertEquals("guardian.co.uk", FeedNameSearch.candidateHosts("Guardian", "gb").first())
        assertEquals("verge.com", FeedNameSearch.candidateHosts("verge", "US").first())
        assertEquals("verge.com", FeedNameSearch.candidateHosts("verge", "").first())
    }

    @Test
    fun `nothing that cannot be a host is tried`() {
        assertEquals(emptyList<String>(), FeedNameSearch.candidateHosts("   ", "ie"))
        assertEquals(emptyList<String>(), FeedNameSearch.candidateHosts("ж", "ie"))
        assertEquals("rockpapershotgun.ie", FeedNameSearch.candidateHosts("Rock, Paper, Shotgun!", "ie").first())
    }

    @Test
    fun `an address is searched as one, a name as a name`() {
        assertTrue(FeedNameSearch.looksLikeAddress("thepotato.ie"))
        assertTrue(FeedNameSearch.looksLikeAddress("https://example.org/feed"))
        assertFalse(FeedNameSearch.looksLikeAddress("the potato"))
        assertFalse("a sentence with a full stop is still a name", FeedNameSearch.looksLikeAddress("Mr. Potato Head"))
    }

    @Test
    fun `the library answers to a name, spaces and case aside`() {
        assertTrue(FeedNameSearch.matches("the journal", "TheJournal.ie", "https://www.thejournal.ie/feed/"))
        assertTrue(FeedNameSearch.matches("arstechnica", "Ars Technica", "https://feeds.arstechnica.com/arstechnica/index"))
        assertFalse(FeedNameSearch.matches("x", "Xkcd", "https://xkcd.com/rss.xml"))
    }
}

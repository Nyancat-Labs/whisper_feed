package com.saulhdev.feeder

import com.saulhdev.feeder.manager.bookmarks.BookmarkFile
import com.saulhdev.feeder.manager.bookmarks.SafeAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

/**
 * Reading a browser's bookmark export, and deciding what is safe to fetch.
 *
 * The parser has to cope with a format that is not valid HTML — Netscape's,
 * unchanged since 1994, with unclosed tags — and the address check has to hold
 * because the thing it protects is the reader's own network.
 */
class BookmarkImportTest {

    private val export = """
        <!DOCTYPE NETSCAPE-Bookmark-file-1>
        <DL><p>
            <DT><H3>News</H3>
            <DL><p>
                <DT><A HREF="https://bbc.co.uk/news">BBC</A>
                <DT><A HREF="https://theguardian.com">Guardian</A>
                <DT><H3>UK</H3>
                <DL><p>
                    <DT><A HREF="https://telegraph.co.uk">Telegraph</A>
                </DL><p>
            </DL><p>
            <DT><H3>Shopping</H3>
            <DL><p>
                <DT><A HREF="https://amazon.co.uk">Amazon</A>
            </DL><p>
            <DT><A HREF="https://example.org/loose">Loose one</A>
        </DL><p>
    """.trimIndent()

    @Test
    fun `folders come out with their bookmarks`() {
        val folders = BookmarkFile.folders(export).associateBy { it.name }
        assertEquals(
            listOf("https://bbc.co.uk/news", "https://theguardian.com"),
            folders["News"]?.urls,
        )
        assertEquals(listOf("https://amazon.co.uk"), folders["Shopping"]?.urls)
    }

    @Test
    fun `a nested folder is its own folder, named by its path`() {
        // Two folders both called "UK" under different parents are different
        // folders, and the reader has to be able to tell which is which.
        val folders = BookmarkFile.folders(export).associateBy { it.name }
        assertEquals(listOf("https://telegraph.co.uk"), folders["News/UK"]?.urls)
    }

    @Test
    fun `a parent does not claim the bookmarks of a folder inside it`() {
        // Otherwise the count beside "News" promises work that selecting it
        // does not do, and the nested bookmarks get scanned twice.
        val news = BookmarkFile.folders(export).first { it.name == "News" }
        assertFalse("https://telegraph.co.uk" in news.urls)
    }

    @Test
    fun `bookmarks outside any folder are gathered, not lost`() {
        val loose = BookmarkFile.folders(export).firstOrNull { it.name == BookmarkFile.LOOSE }
        assertEquals(listOf("https://example.org/loose"), loose?.urls)
    }

    @Test
    fun `bookmarklets and browser pages are not websites`() {
        val html = """
            <DL><p><DT><H3>Junk</H3><DL><p>
            <DT><A HREF="javascript:alert(1)">Bookmarklet</A>
            <DT><A HREF="chrome://settings">Settings</A>
            <DT><A HREF="place:sort=8">Recent</A>
            <DT><A HREF="https://real.example">Real</A>
            </DL><p></DL><p>
        """.trimIndent()
        val junk = BookmarkFile.folders(html).first { it.name == "Junk" }
        assertEquals(listOf("https://real.example"), junk.urls)
    }

    @Test
    fun `the count is of sites, because that is what a scan costs`() {
        // Fifteen BBC bookmarks are one site to probe. Counting bookmarks
        // would tell the reader a folder costs fifteen times what it does.
        val html = """
            <DL><p><DT><H3>News</H3><DL><p>
            <DT><A HREF="https://bbc.co.uk/one">1</A>
            <DT><A HREF="https://bbc.co.uk/two">2</A>
            <DT><A HREF="https://www.bbc.co.uk/three">3</A>
            <DT><A HREF="https://theguardian.com/x">4</A>
            </DL><p></DL><p>
        """.trimIndent()
        val news = BookmarkFile.folders(html).first { it.name == "News" }
        assertEquals(4, news.urls.size)
        assertEquals(2, news.domains)
    }

    @Test
    fun `an unreadable file costs nothing`() {
        assertTrue(BookmarkFile.folders("").isEmpty())
    }

    @Test
    fun `only http and https are ever fetched`() {
        assertTrue(SafeAddress.schemeAllowed("https://example.org"))
        assertTrue(SafeAddress.schemeAllowed("http://example.org"))
        assertFalse(SafeAddress.schemeAllowed("file:///etc/passwd"))
        assertFalse(SafeAddress.schemeAllowed("ftp://example.org"))
        assertFalse(SafeAddress.schemeAllowed("not a url at all"))
    }

    @Test
    fun `the reader's own network is never probed`() {
        // A bookmark pointing at a router, or a redirect that lands on one,
        // would have the phone scanning the house from inside the firewall.
        listOf("127.0.0.1", "10.0.0.1", "192.168.1.1", "172.16.0.1", "169.254.1.1", "0.0.0.0")
            .forEach {
                assertFalse(it, SafeAddress.isPublic(InetAddress.getByName(it)))
            }
    }

    @Test
    fun `carrier-grade NAT is not somewhere to go probing either`() {
        assertFalse(SafeAddress.isPublic(InetAddress.getByName("100.64.0.1")))
    }

    @Test
    fun `IPv6 unique local addresses are private, which isSiteLocalAddress misses`() {
        assertFalse(SafeAddress.isPublic(InetAddress.getByName("fd00::1")))
        assertFalse(SafeAddress.isPublic(InetAddress.getByName("::1")))
    }

    @Test
    fun `an ordinary public address is fine`() {
        assertTrue(SafeAddress.isPublic(InetAddress.getByName("93.184.216.34")))
        assertTrue(SafeAddress.isPublic(InetAddress.getByName("2606:2800:220:1::1")))
    }
}

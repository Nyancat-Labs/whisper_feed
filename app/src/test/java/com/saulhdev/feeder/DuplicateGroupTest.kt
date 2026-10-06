package com.saulhdev.feeder

import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.viewmodels.groupLabel
import com.saulhdev.feeder.viewmodels.toSourceGroup
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.URL

private fun feed(id: Long, title: String, url: String) =
    Feed(id = id, title = title, url = URL(url))

/**
 * What a group of duplicates is called, and who is in it.
 *
 * The grouping was already computed and then flattened away, so the screen
 * could say how many sets existed and not which feed belonged to which. With a
 * hundred sources sorted by name the two halves of a pair sit a screen apart,
 * which tells the reader duplicates exist and leaves them to do the pairing.
 */
class DuplicateGroupTest {

    @Test
    fun `a group is named after the site its members share`() {
        val group = listOf(
            feed(1, "World news | The Guardian", "https://feeds.theguardian.com/world/rss"),
            feed(2, "The Guardian — World", "https://www.theguardian.com/world/rss"),
        )
        assertEquals("theguardian.com", groupLabel(group))
    }

    @Test
    fun `a feed service is not an answer`() {
        // Sixteen publications behind one FeedBurner domain are sixteen
        // publications. Naming the group "feedburner.com" would describe the
        // plumbing rather than the thing, so the shared title is used instead.
        val group = listOf(
            feed(1, "Android Authority", "https://feeds.feedburner.com/AndroidAuthority"),
            feed(2, "Android Authority", "https://www.androidauthority.com/feed"),
        )
        // The site that is not a feed service wins, being the more useful name.
        assertEquals("androidauthority.com", groupLabel(group))
    }

    @Test
    fun `when every member is behind a feed service, the title names the group`() {
        val group = listOf(
            feed(1, "TorrentFreak", "https://feeds.feedburner.com/Torrentfreak"),
            feed(2, "TorrentFreak", "https://feedburner.com/tf/rss"),
        )
        assertEquals("TorrentFreak", groupLabel(group))
    }

    @Test
    fun `a group keeps its members in the order the grouping found them`() {
        val group = listOf(
            feed(7, "NYT World", "https://rss.nytimes.com/services/xml/rss/nyt/World.xml"),
            feed(3, "NYT > World News", "https://www.nytimes.com/services/xml/rss/nyt/World.xml"),
        )
        assertEquals(listOf(7L, 3L), group.toSourceGroup().ids)
        assertEquals("nytimes.com", group.toSourceGroup().label)
    }

    @Test
    fun `a group with nothing to name it does not invent one`() {
        val group = listOf(feed(1, "", "https://feedburner.com/a"), feed(2, "", "https://feedburner.com/b"))
        assertEquals("", groupLabel(group))
    }
}

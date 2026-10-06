package com.saulhdev.feeder

import com.saulhdev.feeder.ui.overlay.isCardFaded
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Nothing sticks a card to the top of the feed.
 *
 * A clustered story used to be put into a `stickyHeader` so it stayed on
 * screen for the next few articles. The condition was positional rather than
 * editorial — it held whatever was first in the list *currently showing*, if
 * that item led any cluster — so it fired in a search, in a category, and
 * would have fired in a single-source view, where "first in the list" has
 * nothing to do with a story breaking.
 *
 * It was expensive to keep upright, too. A sticky header is drawn over the
 * list rather than in it, so every part of the card had to be opaque or it
 * became a window onto the articles sliding underneath; that was fixed twice
 * and a held card with no image was see-through still. The read-dimming needed
 * an exemption for it. It needed a Dismiss action, because being unavoidable
 * was the one thing it was reliably good at.
 *
 * What it was for stays, and was already on the card: CoverageLine draws the
 * megaphone and the source count in the card's ordinary place. The clustering
 * is untouched — it still sets emphasis, still explains itself, and a
 * dismissal still gives back the promotion.
 *
 * Checked here because the feed is assembled in two places, and restoring the
 * hold on one surface while leaving the other alone is exactly how the two
 * feeds drifted apart before.
 */
class NoStickyHoldTest {

    private fun read(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    @Test
    fun `neither surface sticks a card to the viewport`() {
        listOf(
            "ui/pages/ArticleListPage.kt" to "the app's feed",
            "ui/overlay/FeedScaffold.kt" to "the launcher feed",
        ).forEach { (path, what) ->
            val text = read(path)
            assertTrue("$what holds a card again", !text.contains("heldFeed"))
            assertTrue("$what computes a held article again", !text.contains("rememberHeldArticle"))
            assertTrue("$what stopped laying out the plain feed", text.contains("feedItems("))
        }
    }

    @Test
    fun `no sticky header is left in the feed`() {
        // stickyHeader is the mechanism, and it is the part that draws over
        // the list. If it comes back, so does every transparency bug.
        listOf(
            "ui/overlay/FeedItems.kt",
            "ui/pages/ArticleListPage.kt",
            "ui/overlay/FeedScaffold.kt",
        ).forEach { path ->
            val code = read(path).lineSequence()
                .filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") }
                .joinToString("\n")
            assertTrue("$path uses a sticky header again", !code.contains("stickyHeader"))
        }
    }

    @Test
    fun `the cards are still keyed, so a sync moves them`() {
        assertTrue(
            "the feed lost its item keys; a sync will replace the list instead of moving it",
            read("ui/overlay/FeedItems.kt").contains("key = { _, item -> item.id }"),
        )
    }

    @Test
    fun `fading now has one exemption, and it is the pin`() {
        assertTrue("a read card no longer fades", isCardFaded(faded = true, pinned = false))
        assertFalse("a pin fades for having been seen", isCardFaded(faded = true, pinned = true))
        assertFalse("an unread card fades", isCardFaded(faded = false, pinned = false))
    }

    @Test
    fun `the setting that offered the hold is gone`() {
        // Leaving a switch that no longer does anything is worse than having
        // never offered it.
        val prefs = read("data/content/FeedPreferences.kt")
        assertTrue("the sticky-top preference is still declared", !prefs.contains("stickyTop"))
        val strings = File("src/main/res/values/strings.xml").readText()
        assertTrue("the sticky-top strings are still there", !strings.contains("pref_sticky_top"))
    }

    @Test
    fun `the clustering it was built on is untouched`() {
        // The detection is the distinctive part and pays for itself through
        // CoverageLine. Only the sticking was removed.
        val card = read("ui/overlay/ArticleCard.kt")
        assertTrue("the coverage line went with the hold", card.contains("CoverageLine("))
        assertTrue(
            "the coverage line no longer shows only on the promoted card",
            card.contains("cluster?.takeIf { it.leadId == item.id }?.sources"),
        )
    }
}

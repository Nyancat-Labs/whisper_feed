/*
 * This file is part of Whisper
 * Copyright (c) 2026   Whisper contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.saulhdev.feeder

import com.saulhdev.feeder.data.db.models.Article
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.ui.overlay.FeedCardShape
import com.saulhdev.feeder.ui.overlay.FeedEmphasis
import com.saulhdev.feeder.ui.overlay.FeedEmphasisMemory
import com.saulhdev.feeder.ui.overlay.buildFeedFrame
import com.saulhdev.feeder.ui.overlay.clusterStories
import com.saulhdev.feeder.ui.overlay.feedCardShape
import com.saulhdev.feeder.ui.overlay.feedContentType
import com.saulhdev.feeder.utils.LAYOUT_CARDS
import com.saulhdev.feeder.utils.LAYOUT_LIST
import com.saulhdev.feeder.utils.frameSummary
import com.saulhdev.feeder.utils.isSlowFrame
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.URL
import kotlin.time.Instant

private const val NOW = 1_800_000_000_000L

private fun item(id: String, title: String, source: String, image: String? = "https://img.example/$id.jpg") = FeedItem(
    article = Article(
        uuid = id,
        title = title,
        imageUrl = image,
        primarySortTime = Instant.fromEpochMilliseconds(NOW - 30 * 60_000),
    ),
    feed = Feed(
        id = source.hashCode().toLong(),
        title = source,
        tag = "News",
        url = URL("https://${source.lowercase()}.example/feed"),
    ),
)

/**
 * The stutter every few cards: each reload clustered and sized the whole feed
 * on the main thread, twice for the clusters, and redrew every card on screen.
 */
class FeedFrameTest {

    @Before
    fun clean() = FeedEmphasisMemory.forget()

    @After
    fun cleanAfter() = FeedEmphasisMemory.forget()

    private val story = listOf(
        item("a", "Hurricane Delphine makes landfall in Florida", "BBC"),
        item("b", "Delphine strikes Florida coast as thousands evacuate", "Reuters"),
        item("c", "Florida braces as Hurricane Delphine arrives", "Guardian"),
        item("d", "A quiet day at the allotment", "Blog", image = null),
    )

    @Test
    fun `a frame carries its articles, their sizes, their stories and its focus together`() {
        val frame = buildFeedFrame(story, "bbc", emptyMap(), emptyMap(), emptyMap(), breaking = true, nowMs = NOW)
        assertEquals(story, frame.articles)
        assertEquals(story.size, frame.emphasis.size)
        assertEquals(clusterStories(story, NOW), frame.clusters)
        assertEquals("bbc", frame.focus)
    }

    @Test
    fun `no stories are looked for unless breaking news is on`() {
        val frame = buildFeedFrame(story, null, emptyMap(), emptyMap(), emptyMap(), breaking = false, nowMs = NOW)
        assertTrue(frame.clusters.isEmpty())
    }

    @Test
    fun `an article keeps the size it was first given across frames`() {
        val first = buildFeedFrame(story, null, emptyMap(), emptyMap(), emptyMap(), breaking = true, nowMs = NOW)
        val read = story.map { it.copy(article = it.article.copy(readAt = NOW)) }
        val second = buildFeedFrame(read, null, emptyMap(), emptyMap(), emptyMap(), breaking = true, nowMs = NOW)
        assertEquals(first.emphasis, second.emphasis)
    }

    @Test
    fun `the list files each card under the shape it will be drawn in`() {
        val emphasis = listOf(FeedEmphasis.Large, FeedEmphasis.Medium, FeedEmphasis.Small, FeedEmphasis.Large)
        story.forEachIndexed { i, it ->
            assertEquals(
                feedCardShape(i, !it.imageUrl.isNullOrBlank(), LAYOUT_CARDS, emphasis[i]),
                feedContentType(i, it, LAYOUT_CARDS, emphasis),
            )
        }
        assertEquals(FeedCardShape.Hero, feedContentType(0, story[0], LAYOUT_CARDS, emphasis))
        assertEquals("no picture, no hero", FeedCardShape.Compact, feedContentType(3, story[3], LAYOUT_CARDS, emphasis))
        assertEquals(FeedCardShape.Text, feedContentType(0, story[0], LAYOUT_LIST, emphasis))
    }

    @Test
    fun `a frame is slow when it runs past twice its deadline`() {
        val deadline = 8_333_333L
        assertFalse("one frame late is forgiven", isSlowFrame(2 * deadline, deadline))
        assertTrue(isSlowFrame(2 * deadline + 1, deadline))
        assertFalse(isSlowFrame(deadline / 2, deadline))
    }

    @Test
    fun `the trace says how many frames were slow and the worst`() {
        assertEquals("frames 600, slow 3 (worst 48ms)", frameSummary(600, 3, 48_400_000))
        assertEquals("frames 600, none slow", frameSummary(600, 0, 0))
    }

    private fun source(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    private val surfaces = listOf(
        "ui/pages/ArticleListPage.kt" to "the app feed",
        "ui/overlay/FeedScaffold.kt" to "the launcher feed",
    )

    @Test
    fun `neither surface sizes or clusters the feed while composing`() {
        surfaces.forEach { (path, what) ->
            val text = source(path)
            assertTrue("$what builds a frame", text.contains("rememberFeedFrame("))
            assertTrue(
                "$what holds it while its list moves",
                text.contains("listState.isScrollInProgress || gridState.isScrollInProgress"),
            )
            listOf("rememberFeedEmphasis(", "rememberStoryClusters(", "clusterStories(", "feedEmphasisFor(").forEach {
                assertFalse("$what calls $it during composition again", text.contains(it))
            }
            assertTrue("$what reads its sizes from the frame", text.contains("val emphasis = frame.emphasis"))
            assertTrue("$what reads its stories from the frame", text.contains("val clusters = frame.clusters"))
            assertTrue("$what tracks reading on the list it draws", text.contains("articles = frame.articles,"))
        }
    }

    @Test
    fun `a new frame waits for the list to stop, and is built off the main thread`() {
        val frame = source("ui/overlay/FeedFrame.kt")
        val effect = frame.substring(frame.indexOf("LaunchedEffect(inputs) {"))
        val wait = effect.indexOf("snapshotFlow { scrolling() }.first { !it }")
        val build = effect.indexOf("withContext(Dispatchers.Default)")
        assertTrue(wait >= 0)
        assertTrue("the wait comes before the build", build > wait)
        assertTrue(effect.substring(build).contains("buildFeedFrame("))
    }

    @Test
    fun `every feed container tells its list the card shapes`() {
        surfaces.forEach { (path, what) ->
            val text = source(path)
            assertEquals(
                "$what: both the column and the grid",
                2,
                Regex("""contentType = \{ (index|i), item ->\s+feedContentType\((index|i \+ offset), item, layout, emphasis\)""")
                    .findAll(text).count(),
            )
        }
        assertTrue(source("ui/overlay/FeedItems.kt").contains("key = { _, item -> item.id }, contentType = contentType"))
    }

    @Test
    fun `an unchanged card is not redrawn`() {
        val model = source("data/db/models/FeedItem.kt")
        assertTrue(model.contains("@Immutable\ndata class FeedItem("))
    }

    @Test
    fun `both surfaces count their frames while Debugging is on`() {
        assertTrue(source("MainActivity.kt").contains("FrameWatch.watch(window, prefs.debugging.get())"))
        val overlay = source("manager/service/OverlayView.kt")
        assertTrue(overlay.contains("FrameWatch.watch(getWindow(), prefs.debugging.get())"))
        assertTrue("and stops when the panel goes", overlay.contains("frameWatch?.cancel()"))
        val trace = source("utils/FeedTrace.kt")
        assertTrue(trace.contains("drawn == 0) return null"))
        assertTrue(trace.contains("append(frameSummary(drawn, slow, worst))"))
    }
}

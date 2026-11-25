package com.saulhdev.feeder

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.data.db.models.Article
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.ui.overlay.FEED_HEADER_KEY
import com.saulhdev.feeder.ui.overlay.FeedPlace
import com.saulhdev.feeder.ui.overlay.FeedSurface
import com.saulhdev.feeder.ui.overlay.SyncFeedPlace
import com.saulhdev.feeder.ui.overlay.feedListIndex
import com.saulhdev.feeder.ui.overlay.placeOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.URL

/**
 * Szept 479 and 481: the app's feed and the launcher panel's kept separate
 * places, so back from an article read on the panel the app showed the top
 * and newer articles, and scrolling one left the other behind.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FeedPlaceTest {
    @get:Rule
    val compose = createComposeRule()

    private val feed = Feed(id = 1, title = "XDA", url = URL("https://xda.example/feed"))
    private val all = (0 until 60).map { FeedItem(Article(uuid = "a$it", title = "Article $it"), feed) }

    @After
    fun reset() = FeedPlace.clear()

    @Composable
    private fun Surface(surface: FeedSurface, items: List<FeedItem>, state: LazyListState) {
        SyncFeedPlace(surface, items, isGrid = false, listState = state, gridState = rememberLazyStaggeredGridState())
        LazyColumn(state = state, modifier = Modifier.height(400.dp)) {
            item(key = FEED_HEADER_KEY) { Text("header", Modifier.height(100.dp)) }
            items(items, key = { it.id }) { Text(it.article.title, Modifier.height(100.dp)) }
        }
    }

    @Test
    fun `a fresh app feed waits for its articles, then opens on the article tapped in the panel`() {
        FeedPlace.record(FeedSurface.PANEL, "a40")
        var loaded by mutableStateOf(emptyList<FeedItem>())
        lateinit var app: LazyListState
        compose.setContent { app = rememberLazyListState(); Surface(FeedSurface.APP, loaded, app) }
        compose.waitForIdle()
        assertEquals(0, app.firstVisibleItemIndex)
        loaded = all
        compose.waitForIdle()
        assertEquals(feedListIndex(40, emptyList()), app.firstVisibleItemIndex)
    }

    @Test
    fun `scrolling one surface brings the other to the same article, both ways`() {
        lateinit var app: LazyListState
        lateinit var panel: LazyListState
        compose.setContent {
            app = rememberLazyListState()
            panel = rememberLazyListState()
            Column {
                Surface(FeedSurface.APP, all, app)
                Surface(FeedSurface.PANEL, all, panel)
            }
        }
        compose.waitForIdle()

        runBlocking { panel.scroll { scrollBy(2500f) } }
        compose.waitForIdle()
        val panelFirst = panel.firstVisibleItemIndex
        assertTrue(panelFirst > 10)
        assertEquals("the app follows the panel", panelFirst, app.firstVisibleItemIndex)

        runBlocking { app.scroll { scrollBy(-1200f) } }
        compose.waitForIdle()
        assertEquals("the panel follows the app", app.firstVisibleItemIndex, panel.firstVisibleItemIndex)

        runBlocking { app.scroll { scrollBy(-100_000f) } }
        compose.waitForIdle()
        assertEquals("back at the top, header and all", 0, panel.firstVisibleItemIndex)
    }

    @Test
    fun `an article this feed does not hold leaves it where it is`() {
        lateinit var app: LazyListState
        compose.setContent { app = rememberLazyListState(); Surface(FeedSurface.APP, all, app) }
        runBlocking { app.scrollToItem(20) }
        FeedPlace.record(FeedSurface.PANEL, "not-here")
        compose.waitForIdle()
        assertEquals(20, app.firstVisibleItemIndex)
    }

    @Test
    fun `the place is the top while the header shows, else the first article, past day headings`() {
        val ids = setOf("a1", "a2")
        assertEquals(FeedPlace.TOP, placeOf(0, listOf(FEED_HEADER_KEY, "a1"), ids))
        assertEquals("a1", placeOf(3, listOf("day-2026-11-25", "a1", "a2"), ids))
        assertEquals(null, placeOf(3, listOf("bookmarks-empty"), ids))
    }

    @Test
    fun `the same article again is no news, so neither side is moved for nothing`() {
        FeedPlace.record(FeedSurface.PANEL, "a5")
        val first = FeedPlace.mark.value
        FeedPlace.record(FeedSurface.APP, "a5")
        assertEquals(first, FeedPlace.mark.value)
    }

    @Test
    fun `wired into both feeds, off for search and Saved, and set by a tap in the panel`() {
        val src = { p: String -> File("src/main/java/com/saulhdev/feeder/$p").readText() }
        assertTrue(src("ui/pages/ArticleListPage.kt").contains("surface = FeedSurface.APP"))
        assertTrue(src("ui/pages/ArticleListPage.kt").contains("enabled = !searching && !showBookmarks"))
        assertTrue(src("ui/overlay/FeedScaffold.kt").contains("surface = FeedSurface.PANEL"))
        assertTrue(src("manager/service/OverlayView.kt").contains("FeedPlace.record(FeedSurface.PANEL, item.id)"))
    }
}

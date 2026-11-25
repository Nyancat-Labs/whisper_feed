package com.saulhdev.feeder

import android.app.Application
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.data.db.models.Article
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.ui.overlay.AnchorFeedOnPanelReturn
import com.saulhdev.feeder.ui.overlay.PanelReturnAnchor
import com.saulhdev.feeder.ui.overlay.feedListIndex
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.URL

/**
 * Szept 479: back from an article opened on the launcher panel, the app's feed
 * started at the top and refreshed, so the reader lost their place.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PanelReturnAnchorTest {
    @get:Rule
    val compose = createComposeRule()

    private val feed = Feed(id = 1, title = "XDA", url = URL("https://xda.example/feed"))
    private val all = (0 until 60).map { FeedItem(Article(uuid = "a$it", title = "Article $it"), feed) }

    @After
    fun reset() = PanelReturnAnchor.clear()

    private fun show(articles: () -> List<FeedItem>): () -> LazyListState {
        lateinit var list: LazyListState
        compose.setContent {
            list = rememberLazyListState()
            val items = articles()
            AnchorFeedOnPanelReturn(
                articles = items,
                isGrid = false,
                listState = list,
                gridState = rememberLazyStaggeredGridState(),
            )
            LazyColumn(state = list) {
                items(items, key = { it.id }) { Text(it.article.title, Modifier.height(100.dp)) }
            }
        }
        return { list }
    }

    @Test
    fun `a fresh feed waits for its articles, then lands on the one read`() {
        PanelReturnAnchor.leave("a40")
        var loaded by mutableStateOf(emptyList<FeedItem>())
        val list = show { loaded }
        compose.waitForIdle()
        assertEquals("still waiting, not given up", "a40", PanelReturnAnchor.articleId.value)
        loaded = all
        compose.waitForIdle()
        assertEquals(feedListIndex(40, emptyList()), list().firstVisibleItemIndex)
        assertNull("taken once", PanelReturnAnchor.articleId.value)
    }

    @Test
    fun `an article the feed does not hold leaves it at the top`() {
        PanelReturnAnchor.leave("gone")
        val list = show { all }
        compose.waitForIdle()
        assertEquals(0, list().firstVisibleItemIndex)
        assertNull(PanelReturnAnchor.articleId.value)
    }

    @Test
    fun `a newer article is not cleared by taking an older one`() {
        PanelReturnAnchor.leave("a1")
        PanelReturnAnchor.leave("a2")
        PanelReturnAnchor.take("a1")
        assertEquals("a2", PanelReturnAnchor.articleId.value)
    }

    @Test
    fun `the panel leaves it, and opening the app from its icon forgets it`() {
        val src = { p: String -> File("src/main/java/com/saulhdev/feeder/$p").readText() }
        assertTrue(src("manager/service/OverlayView.kt").contains("PanelReturnAnchor.leave(item.id)"))
        assertTrue(src("MainActivity.kt").contains("if (launchedDirectly) PanelReturnAnchor.clear()"))
        assertTrue(src("ui/pages/ArticleListPage.kt").contains("AnchorFeedOnPanelReturn("))
    }
}

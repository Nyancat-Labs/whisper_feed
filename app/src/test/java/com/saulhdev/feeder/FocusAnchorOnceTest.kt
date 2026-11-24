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
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.data.db.models.Article
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.ui.overlay.AnchorFeedOnFocusChange
import com.saulhdev.feeder.ui.overlay.feedListIndex
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.URL

/**
 * Szept 457: back from Settings, the feed jumped to the article a source was
 * once opened from, because the anchor was applied again on every visit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FocusAnchorOnceTest {
    @get:Rule
    val compose = createComposeRule()

    private val feed = Feed(id = 1, title = "GSMArena", url = URL("https://gsmarena.example/feed"))
    private val articles = (0 until 60).map { FeedItem(Article(uuid = "a$it", title = "Article $it"), feed) }

    // Where article 40 sits in the list, below the header.
    private val anchored = feedListIndex(40, emptyList())

    @Test
    fun `the anchor is applied once, not again when the feed comes back`() {
        val restore = StateRestorationTester(compose)
        lateinit var list: LazyListState
        var focus by mutableStateOf<String?>("gsmarena")
        restore.setContent {
            list = rememberLazyListState()
            AnchorFeedOnFocusChange(
                appliedFocus = focus,
                anchorId = "a40",
                articles = articles,
                isGrid = false,
                listState = list,
                gridState = rememberLazyStaggeredGridState(),
            )
            LazyColumn(state = list) {
                items(articles, key = { it.id }) { Text(it.article.title, Modifier.height(100.dp)) }
            }
        }
        compose.waitForIdle()
        assertEquals("into the source: lands on the article", anchored, list.firstVisibleItemIndex)

        // Back out of the source: the same article again, on the full feed.
        focus = null
        compose.waitForIdle()
        assertEquals(anchored, list.firstVisibleItemIndex)

        // The reader scrolls to the top, then goes to Settings and back.
        runBlocking { list.scrollToItem(0) }
        compose.waitForIdle()
        restore.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        assertEquals("back from Settings: stays at the top", 0, list.firstVisibleItemIndex)
    }

    @Test
    fun `opening the same source from the same article again still lands on it`() {
        lateinit var list: LazyListState
        var focus by mutableStateOf<String?>("gsmarena")
        compose.setContent {
            list = rememberLazyListState()
            AnchorFeedOnFocusChange(
                appliedFocus = focus,
                anchorId = "a40",
                articles = articles,
                isGrid = false,
                listState = list,
                gridState = rememberLazyStaggeredGridState(),
            )
            LazyColumn(state = list) {
                items(articles, key = { it.id }) { Text(it.article.title, Modifier.height(100.dp)) }
            }
        }
        compose.waitForIdle()
        focus = null
        compose.waitForIdle()
        runBlocking { list.scrollToItem(0) }
        focus = "gsmarena"
        compose.waitForIdle()
        assertEquals(anchored, list.firstVisibleItemIndex)
    }
}

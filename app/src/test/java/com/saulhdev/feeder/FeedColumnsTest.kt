package com.saulhdev.feeder

import com.saulhdev.feeder.utils.LAYOUT_CARDS
import com.saulhdev.feeder.utils.LAYOUT_MOSAIC
import com.saulhdev.feeder.ui.overlay.feedColumns
import com.saulhdev.feeder.ui.overlay.leadSpansRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A tablet with no article open drew one card across the whole screen, and
 * its photo at that size stuttered an older chip. Columns, by width.
 */
class FeedColumnsTest {

    @Test
    fun `a phone is unchanged`() {
        assertEquals(1, feedColumns(LAYOUT_CARDS, 411))
        assertEquals(2, feedColumns(LAYOUT_MOSAIC, 411))
    }

    @Test
    fun `a tablet upright has two columns, and three on its side`() {
        assertEquals(2, feedColumns(LAYOUT_CARDS, 800))
        assertEquals(3, feedColumns(LAYOUT_CARDS, 1280))
    }

    @Test
    fun `a foldable opened out has two columns, like a tablet upright`() {
        // Galaxy Z Fold6, Fold7 (about 656) and Pixel 10 Pro Fold: one column
        // the width of the screen, until the second column came at 600.
        assertEquals(2, feedColumns(LAYOUT_CARDS, 619))
        assertEquals(2, feedColumns(LAYOUT_CARDS, 656))
        assertEquals(2, feedColumns(LAYOUT_CARDS, 692))
        assertEquals(3, feedColumns(LAYOUT_MOSAIC, 619))
        // The widest phones stay one column.
        assertEquals(1, feedColumns(LAYOUT_CARDS, 414))
        assertEquals(1, feedColumns(LAYOUT_CARDS, 599))
    }

    @Test
    fun `there are never more than three columns of cards, or four of tiles`() {
        assertEquals(3, feedColumns(LAYOUT_CARDS, 2560))
        assertEquals(4, feedColumns(LAYOUT_MOSAIC, 2560))
        assertEquals(4, feedColumns(LAYOUT_MOSAIC, 800))
    }

    @Test
    fun `beside an open article the feed is one column again`() {
        assertEquals(1, feedColumns(LAYOUT_CARDS, 360))
        assertEquals(2, feedColumns(LAYOUT_MOSAIC, 360))
    }

    private val page = File("src/main/java/com/saulhdev/feeder/ui/pages/ArticleListPage.kt").readText()

    @Test
    fun `the feed chooses its container by columns, not by layout alone`() {
        assertTrue(page.contains("val isGridLayout = columns > 1"))
        assertTrue(page.contains("val feedWidthDp = if (articleOpenBeside) FEED_BESIDE_ARTICLE_DP else windowWidthDp"))
        assertTrue(page.contains("columns = columns,"))
        assertTrue("reading still tracked in columns", page.contains("isGrid = isGridLayout,"))
        // The first build asked whether the detail pane was expanded, which a
        // wide screen reports with no article in it: the tablet stayed in one
        // column. It is the selected article that counts.
        assertTrue(page.contains("?.takeIf { it.pane == ListDetailPaneScaffoldRole.Detail }?.contentKey != null"))
        assertTrue(page.contains("val articleOpenBeside = articleSelected &&"))
        assertTrue(!page.contains("scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded"))
    }

    @Test
    fun `a lead story is never more than two wide`() {
        assertTrue("Mosaic on a phone: both lanes", leadSpansRow(LAYOUT_MOSAIC, 2))
        assertFalse("Mosaic on a tablet: one lane of four", leadSpansRow(LAYOUT_MOSAIC, 4))
        assertFalse(leadSpansRow(LAYOUT_MOSAIC, 3))
        assertFalse("cards keep to their column", leadSpansRow(LAYOUT_CARDS, 2))
        assertTrue(page.contains("if (leadSpansRow(layout, columns) &&"))
    }

    @Test
    fun `the place in the feed survives the switch between one column and several`() {
        assertTrue(page.contains("gridState.scrollToItem(listState.firstVisibleItemIndex)"))
        assertTrue(page.contains("listState.scrollToItem(gridState.firstVisibleItemIndex)"))
    }
}

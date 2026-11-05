package com.saulhdev.feeder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Folding or unfolding a phone rebuilds the activity at the new size (review
 * 65). What the reader was doing has to come back: the feed's position, an
 * open article, the filter sheet and a search.
 *
 * Checked in the source rather than on a device, as there is no foldable to
 * test on: each piece of that state lives where a rebuild keeps it - a view
 * model, or a saveable state - and nothing on the feed's page that a rebuild
 * would lose is held in a plain remember.
 */
class FoldUnfoldTest {

    private val main = "src/main/java/com/saulhdev/feeder"
    private val page = File("$main/ui/pages/ArticleListPage.kt").readText()
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test
    fun `the activity is rebuilt, so saved state is what carries over`() {
        // If the activity handled size changes itself, the states below would
        // never be restored and this test would be checking the wrong thing.
        assertFalse(manifest.contains("screenSize|smallestScreenSize"))
    }

    @Test
    fun `the feed keeps its place`() {
        assertTrue(page.contains("rememberLazyStaggeredGridState()"))
        assertTrue(page.contains("rememberLazyListState") || page.contains("listState"))
    }

    @Test
    fun `an open article and the filter sheet come back`() {
        // The pane navigator saves which article is open beside the feed.
        assertTrue(page.contains("rememberListDetailPaneScaffoldNavigator"))
        // The sheet's state is a saveable one.
        assertTrue(page.contains("rememberStandardBottomSheetState("))
    }

    @Test
    fun `a search comes back open, with its words`() {
        // The words live in the view model; whether the field is open must
        // survive too, or a filtered feed comes back with no way to clear it.
        assertTrue(page.contains("var searching by rememberSaveable { mutableStateOf(false) }"))
        assertTrue(page.contains("var showBookmarks by rememberSaveable { mutableStateOf(false) }"))
    }

    @Test
    fun `nothing else on the feed's page is a plain remembered switch`() {
        val plain = Regex("""var \w+ by remember \{ mutableStateOf\((true|false)\) \}""").findAll(page).map { it.value }.toList()
        assertTrue("held in a plain remember: $plain", plain.isEmpty())
    }
}

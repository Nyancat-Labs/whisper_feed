package com.saulhdev.feeder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The small fixes from the design audit of 21 October 2026
 * (docs/DESIGN_AUDIT.md), held so they do not quietly come undone.
 */
class DesignAuditFixesTest {

    private fun src(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    @Test
    fun `every page keeps clear of a side navigation bar and a cutout`() {
        val frame = src("ui/components/ViewWithActionBar.kt")
        assertTrue(frame.contains("contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)"))
        assertTrue(frame.contains("start = padding.calculateStartPadding(direction)"))
        val panel = src("manager/service/OverlayView.kt")
        assertTrue("the panel uses the sides it measures", panel.contains("leftInset = with(density)"))
    }

    @Test
    fun `the last rows of Settings and Sources clear the gesture bar`() {
        assertTrue(src("ui/pages/PreferencesPage.kt").contains("bottom = paddingValues.calculateBottomPadding() + 16.dp"))
        assertTrue(src("ui/pages/SourceListPage.kt").contains("bottom = paddingValues.calculateBottomPadding() + 88.dp"))
    }

    @Test
    fun `actions TalkBack could not name now have names`() {
        assertTrue(src("ui/pages/ArticleListPage.kt").contains("contentDescription = stringResource(R.string.back_to_top)"))
        assertTrue(src("ui/pages/SourceEditPage.kt").contains("Icon(Phosphor.Plus, stringResource(R.string.sources_add_tag))"))
        assertTrue(src("ui/overlay/ArticleCard.kt").contains("clickable(onClickLabel = focusLabel)"))
    }

    @Test
    fun `a settings heading can grow with the font`() {
        val group = src("ui/components/PreferenceGroup.kt")
        assertTrue(group.contains(".heightIn(min = 48.dp)"))
        assertFalse(group.contains(".height(48.dp)"))
    }

    @Test
    fun `back is only taken when there is something of the screen's own to go back through`() {
        assertTrue(src("ui/views/ComposeWebView.kt").contains("BackHandler(enabled = canGoBack)"))
        assertFalse(src("ui/pages/ArticlePage.kt").contains("rememberNavController()"))
    }

    @Test
    fun `the syncing channel says what it is for`() {
        assertTrue(src("manager/sync/FeedSyncer.kt").contains("R.string.sync_status_channel_description"))
    }
}

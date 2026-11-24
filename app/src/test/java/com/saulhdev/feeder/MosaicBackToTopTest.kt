package com.saulhdev.feeder

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.saulhdev.feeder.ui.components.rememberScrollingUp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Reader's email, 24 November: no Back to top arrow in the Mosaic layout. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MosaicBackToTopTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `scroll direction follows the container in use, not the first one`() {
        var list by mutableStateOf(0 to 0)
        var grid by mutableStateOf(20 to 0)
        var isGrid by mutableStateOf(false)
        var up = false
        compose.setContent {
            // A plain value, as ArticleListPage's isGridLayout is: the lambda
            // captures it, so only a new lambda carries the change.
            val grided = isGrid
            val scrollingUp by rememberScrollingUp { if (grided) grid else list }
            up = scrollingUp
        }
        isGrid = true
        compose.waitForIdle()
        grid = 10 to 0 // the grid moves back up
        compose.waitForIdle()
        assertTrue("going up in the grid", up)
        grid = 15 to 0
        compose.waitForIdle()
        assertFalse("going down in the grid", up)
    }

    @Test
    fun `the app's back-to-top button watches and scrolls the grid in Mosaic`() {
        val page = File("src/main/java/com/saulhdev/feeder/ui/pages/ArticleListPage.kt").readText()
        assertTrue(page.contains("if (isGridLayout) gridState.firstVisibleItemIndex > 4 else listState.firstVisibleItemIndex > 4"))
        assertTrue(page.contains("if (isGridLayout) gridState.animateScrollToItem(0)"))
    }
}

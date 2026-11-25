package com.saulhdev.feeder

import android.app.Application
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.ui.overlay.CategoryChipRow
import com.saulhdev.feeder.ui.overlay.chipTapped
import com.saulhdev.feeder.ui.overlay.oneCategory
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * GitHub #3 (owner's decision, 25 November): the category buttons show one
 * category at a time, like tabs, instead of adding categories together.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CategoryOneAtATimeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a tap shows only that category, and the same tap again goes back to All`() {
        assertEquals(setOf("Tech"), chipTapped(emptySet(), "Tech"))
        assertEquals("Tech OR News, not both", setOf("News"), chipTapped(setOf("Tech"), "News"))
        assertEquals(emptySet<String>(), chipTapped(setOf("News"), "News"))
    }

    @Test
    fun `several saved before the change are All, not a guess`() {
        assertEquals(emptySet<String>(), oneCategory(setOf("Tech", "News")))
        assertEquals(setOf("Tech"), oneCategory(setOf("Tech")))
        assertEquals(emptySet<String>(), oneCategory(emptySet()))
    }

    @Test
    fun `on the row, choosing News after Tech leaves only News chosen`() {
        var selected by mutableStateOf(setOf("Tech"))
        compose.setContent {
            CategoryChipRow(listOf("Misc", "News", "Tech", "Cyber"), selected, { selected = it })
        }
        compose.onNodeWithText("News").performClick()
        compose.waitForIdle()
        assertEquals(setOf("News"), selected)
        compose.onNodeWithText("News").assertIsSelected()
    }

    @Test
    fun `an older selection of several shows All as chosen`() {
        compose.setContent {
            CategoryChipRow(listOf("News", "Tech"), setOf("News", "Tech"), {})
        }
        compose.onNodeWithText("All").assertIsSelected()
    }

    @Test
    fun `a chosen category at the far end of the row is scrolled into view`() {
        val many = (1..20).map { "Category $it" }
        compose.setContent {
            CategoryChipRow(many, setOf("Category 20"), {}, Modifier.width(300.dp))
        }
        compose.waitForIdle()
        compose.onNodeWithText("Category 20").assertIsDisplayed()
    }
}

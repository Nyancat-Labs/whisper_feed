package com.saulhdev.feeder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The foundations from the 27 October preview review (batch 1). */
class ReviewBatch1Test {

    private fun source(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    @Test
    fun `a large title collapses only once the page has scrolled`() {
        val bar = source("ui/components/ViewWithActionBar.kt")
        assertTrue(bar.contains("collapseOnlyWhenScrolling(scrollBehavior.nestedScrollConnection)"))
        assertTrue(bar.contains("if (contentScrolls) bar.onPreScroll(available, source) else Offset.Zero"))
        assertTrue("long titles shrink before they are cut", bar.contains("autoSize = TextAutoSize.StepBased("))
    }

    @Test
    fun `the main action is filled primary and chips are selected in secondary`() {
        val button = source("ui/components/Button.kt")
        assertTrue(button.contains("positive -> MaterialTheme.colorScheme.primary\n"))
        assertFalse(button.contains("positive -> MaterialTheme.colorScheme.primaryContainer"))
        val chips = source("ui/components/Chip.kt")
        assertTrue(chips.contains("selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer"))
        assertFalse(chips.contains("selectedContainerColor = MaterialTheme.colorScheme.primaryContainer"))
    }

    @Test
    fun `category chips look the same on the feed and on Sources`() {
        for (path in listOf("ui/overlay/CategoryChipRow.kt", "ui/pages/SourceListPage.kt")) {
            val text = source(path)
            assertTrue(path, text.contains("shape = categoryChipShape"))
            assertTrue(path, text.contains("colors = categoryChipColors()"))
        }
    }

    @Test
    fun `headings line up on pages that already have a margin`() {
        for (path in listOf("ui/pages/AboutPage.kt", "ui/pages/SourceListPage.kt")) {
            val text = source(path)
            val all = Regex("PreferenceGroupHeading\\(").findAll(text).count()
            val inside = Regex("insideMargin = true").findAll(text).count()
            assertTrue("$path: every heading inside the margin", all == inside)
        }
    }

    @Test
    fun `About's cards are full width and the list colour`() {
        val about = source("ui/pages/AboutPage.kt")
        assertFalse(about.contains(".padding(4.dp)\n                        .background("))
        assertFalse(about.contains("containerColor = MaterialTheme.colorScheme.surfaceContainer,"))
        assertTrue(about.contains("Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)"))
    }
}

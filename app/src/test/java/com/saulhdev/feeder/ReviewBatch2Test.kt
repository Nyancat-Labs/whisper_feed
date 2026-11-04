package com.saulhdev.feeder

import com.saulhdev.feeder.viewmodels.MarkReadRange.Everything
import com.saulhdev.feeder.viewmodels.MarkReadRange.OlderThanDay
import com.saulhdev.feeder.viewmodels.MarkReadRange.OlderThanHour
import com.saulhdev.feeder.viewmodels.MarkReadRange.OlderThanTwoDays
import com.saulhdev.feeder.viewmodels.markReadChoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The filter sheet, from the 27 October preview review (batch 2). */
class ReviewBatch2Test {

    private fun source(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    @Test
    fun `mark as read shows 1 h, 24 h, 48 h, then All`() {
        val counts = mapOf(Everything to 100, OlderThanHour to 90, OlderThanDay to 50, OlderThanTwoDays to 20)
        assertEquals(listOf(OlderThanHour, OlderThanDay, OlderThanTwoDays, Everything), markReadChoices(counts))
    }

    @Test
    fun `a choice that marks the same as a wider one is left out`() {
        // Nothing new in the last hour: Everything and 1 h said the same number.
        val counts = mapOf(Everything to 13928, OlderThanHour to 13928, OlderThanDay to 9000, OlderThanTwoDays to 9000)
        assertEquals(listOf(OlderThanDay, Everything), markReadChoices(counts))
    }

    @Test
    fun `nothing to mark leaves All alone`() {
        assertEquals(listOf(Everything), markReadChoices(mapOf(Everything to 0)))
        assertEquals(listOf(OlderThanHour, Everything),
            markReadChoices(mapOf(Everything to 5, OlderThanHour to 2, OlderThanDay to 0, OlderThanTwoDays to 0)))
    }

    @Test
    fun `sort order is two button groups in plain words`() {
        val sheet = source("ui/pages/SortFilterSheet.kt")
        assertFalse(sheet.contains("ChipsSwitch("))
        assertTrue(sheet.contains("options = sortDirections(sortOption)"))
        val strings = File("src/main/res/values/strings.xml").readText()
        listOf("Newest first", "Oldest first", "A–Z", "Z–A").forEach { assertTrue(it, strings.contains(">$it<")) }
    }

    @Test
    fun `every section has an icon and a card, open or closed`() {
        val sheet = source("ui/pages/SortFilterSheet.kt")
        listOf("Phosphor.Sort,", "Phosphor.Article,", "Phosphor.Rss,", "Phosphor.Hash,", "Phosphor.ThumbsDown,", "Phosphor.CheckCircle,")
            .forEach { assertTrue(it, sheet.contains(it)) }
        val block = source("ui/components/Block.kt")
        assertFalse("closed sections were bare", block.contains("Color.Transparent"))
        assertTrue(block.contains("start = 16.dp,"))
    }

    @Test
    fun `the sheet opens in one movement, below the status bar, clear of the gesture bar`() {
        val page = source("ui/pages/ArticleListPage.kt")
        assertTrue(page.contains("sheet.targetValue != SheetValue.Hidden"))
        assertTrue(page.contains("windowInsetsPadding(WindowInsets.statusBars)"))
        assertTrue(page.contains("Box(modifier = Modifier.navigationBarsPadding())"))
    }
}

package com.saulhdev.feeder

import com.saulhdev.feeder.utils.undoubleEncoded
import com.saulhdev.feeder.viewmodels.LearnedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Fixes from the 12 November results (sheet 88, 91, 105, T4). */
class Batch1112Test {

    private fun source(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    @Test
    fun `a doubly encoded picture address is decoded once`() {
        assertEquals(
            "https://qz.com/cdn-cgi/image/width=600/https://assets.qz.com/media/Michael%20Burry.jpg",
            undoubleEncoded("https://qz.com/cdn-cgi/image/width=600/https://assets.qz.com/media/Michael%2520Burry.jpg"),
        )
        assertNull("a normal address is left alone", undoubleEncoded("https://example.com/a%20b.jpg"))
        assertNull(undoubleEncoded("https://example.com/plain.jpg"))
    }

    @Test
    fun `the picture repair runs only for pictures, after a refusal`() {
        val identity = source("utils/HttpIdentity.kt")
        assertTrue(identity.contains(".addInterceptor(repairDoubleEncoding)"))
        assertTrue(identity.contains("if (response.code !in 400..499) return@Interceptor response"))
    }

    @Test
    fun `layout choices read like Settings rows, not bold`() {
        val dialog = source("ui/components/dialog/StringSelectionPrefDialogUI.kt")
        assertFalse(dialog.contains("FontWeight.Bold"))
        assertTrue(dialog.contains("style = MaterialTheme.typography.titleMedium"))
        assertTrue(dialog.contains("style = MaterialTheme.typography.bodyMedium"))
    }

    @Test
    fun `saved under is a filled band and Done is a filled button`() {
        val page = source("ui/pages/SourceAddPage.kt")
        assertTrue(page.contains(".background(MaterialTheme.colorScheme.primaryContainer"))
        assertTrue(page.contains("Button(\n                    onClick = onDone,"))
    }

    @Test
    fun `a source with only skip counts can still be forgotten`() {
        val skippedOnly = LearnedSource(1, "A", null, affinity = 0, opened = 0, seen = 12, nudge = 0f, hidden = false, faded = true)
        assertTrue(skippedOnly.hasLearned)
        val nothing = LearnedSource(2, "B", null, affinity = 0, opened = 0, seen = 0, nudge = 0f, hidden = false)
        assertFalse(nothing.hasLearned)
    }

    @Test
    fun `forgetting a source restarts its counts, not only More and Less`() {
        val vm = source("viewmodels/LearnedViewModel.kt")
        assertTrue(vm.contains("sourcesRepo.resetLearned(ids)"))
        val dao = source("data/db/dao/FeedArticleDao.kt")
        assertEquals(3, Regex("SELECT learnedResetAt FROM Feeds").findAll(dao).count())
        assertTrue("statistics keep the history", source("viewmodels/StatisticsViewModel.kt").contains("allHistory = true"))
    }

    @Test
    fun `the new column has its migration`() {
        val db = source("data/db/NeoFeedDb.kt")
        assertTrue(db.contains("version = 30,"))
        assertTrue(db.contains("ALTER TABLE Feeds ADD COLUMN learnedResetAt INTEGER NOT NULL DEFAULT 0"))
        assertTrue(db.contains("MIGRATION_29_30,"))
    }

    @Test
    fun `several sources can be forgotten at once`() {
        val page = source("ui/pages/LearnedPage.kt")
        assertTrue(page.contains("combinedClickable(onClick = onTap, onLongClick = onLongPress)"))
        assertTrue(page.contains("viewModel.forget(selected)"))
        assertTrue(page.contains("R.string.learned_faded"))
    }

    @Test
    fun `layout choices line up with the title, Material's way`() {
        val dialog = source("ui/components/dialog/StringSelectionPrefDialogUI.kt")
        assertTrue(dialog.contains("LocalMinimumInteractiveComponentSize provides 0.dp"))
        assertTrue(dialog.contains("role = Role.RadioButton"))
        assertTrue(dialog.contains("onClick = null,"))
    }

    @Test
    fun `sources with nothing learned are grouped apart`() {
        val page = source("ui/pages/LearnedPage.kt")
        assertTrue(page.contains("sources.partition { it.hasLearned }"))
        assertTrue(page.contains("R.string.learned_nothing_yet"))
    }
}

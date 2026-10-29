package com.saulhdev.feeder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Settings, stage 2 (25 October 2026): Expressive's flexible app bar, the
 * segmented list, one margin, short choices as buttons, one dialog system.
 */
class SettingsStage2Test {

    private fun read(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    @Test
    fun `large titles use the flexible app bar`() {
        val bar = read("ui/components/ViewWithActionBar.kt")
        // Drawn through TwoRowsTopAppBar since review 64, with the large
        // flexible bar's own heights and title style.
        assertTrue(bar.contains("TwoRowsTopAppBar("))
        assertTrue(bar.contains("TopAppBarDefaults.LargeFlexibleAppBarWithoutSubtitleExpandedHeight"))
        assertTrue(bar.contains("style = MaterialTheme.typography.displaySmall"))
        assertFalse(bar.contains("LargeTopAppBar("))
    }

    @Test
    fun `a group reads as one block`() {
        val shape = read("ui/theme/Shape.kt")
        assertTrue(shape.contains("val SEGMENT_INNER = CornerSize(4.dp)"))
        assertTrue(shape.contains("val SEGMENT_GAP = 2.dp"))
        assertTrue(read("ui/pages/SettingsSection.kt").contains("Modifier.height(SEGMENT_GAP)"))
    }

    @Test
    fun `every page keeps 16 at the sides`() {
        assertTrue(read("ui/pages/AboutPage.kt").contains("contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)"))
        assertTrue(read("ui/pages/SortFilterSheet.kt").contains("contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)"))
        val sources = read("ui/pages/SourceListPage.kt")
        assertTrue(sources.contains("start = 16.dp,\n                            end = 16.dp,"))
    }

    @Test
    fun `short choices are buttons, with a label for every option`() {
        val prefs = read("data/content/FeedPreferences.kt")
        listOf("overlayTheme", "appFont", "timeFormat", "readerTextSize", "articleOpenMode", "readVisibility").forEach { name ->
            val block = prefs.substringAfter("var $name = StringSelectionPref(").substringBefore("\n    )\n")
            assertTrue(name, block.contains("buttons = mapOf("))
        }
        // Five layouts are too many for one row: Feed layout keeps its list.
        assertFalse(prefs.substringAfter("var feedLayout = StringSelectionPref(").substringBefore("\n    )\n").contains("buttons ="))
        val row = read("ui/components/StringSelectionPreference.kt")
        assertTrue(row.contains("ButtonGroupDefaults.connectedLeadingButtonShapes()"))
        assertTrue(row.contains("role = Role.RadioButton"))
    }

    @Test
    fun `one dialog system`() {
        assertFalse(File("src/main/java/com/saulhdev/feeder/ui/components/dialog/BaseDialog.kt").exists())
        assertTrue(read("ui/components/dialog/StringSelectionPrefDialogUI.kt").contains("AlertDialog("))
    }
}

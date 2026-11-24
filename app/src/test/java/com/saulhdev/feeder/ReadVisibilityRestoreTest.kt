package com.saulhdev.feeder

import com.saulhdev.feeder.utils.READ_DIM
import com.saulhdev.feeder.utils.READ_HIDE
import com.saulhdev.feeder.utils.READ_KEEP
import com.saulhdev.feeder.utils.readVisibilityAfterReset
import com.saulhdev.feeder.utils.readVisibilityOnShow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReadVisibilityRestoreTest {

    @Test
    fun `showing read articles again restores fade if fade was set`() {
        assertEquals(READ_DIM, readVisibilityOnShow(READ_DIM))
        assertEquals(READ_KEEP, readVisibilityOnShow(READ_KEEP))
    }

    @Test
    fun `nothing remembered, or hide itself, comes back as keep`() {
        assertEquals(READ_KEEP, readVisibilityOnShow(""))
        assertEquals(READ_KEEP, readVisibilityOnShow(READ_HIDE))
        assertEquals(READ_KEEP, readVisibilityOnShow("nonsense"))
    }

    @Test
    fun `reset leaves keep or fade from settings alone`() {
        assertEquals(READ_DIM, readVisibilityAfterReset(READ_DIM, READ_KEEP))
        assertEquals(READ_KEEP, readVisibilityAfterReset(READ_KEEP, READ_DIM))
    }

    @Test
    fun `reset undoes the sheet's hide, back to what it was before`() {
        assertEquals(READ_DIM, readVisibilityAfterReset(READ_HIDE, READ_DIM))
        assertEquals(READ_KEEP, readVisibilityAfterReset(READ_HIDE, READ_KEEP))
        assertEquals(READ_KEEP, readVisibilityAfterReset(READ_HIDE, ""))
    }

    @Test
    fun `the sheet's reset no longer writes the default`() {
        val sheet = File("src/main/java/com/saulhdev/feeder/ui/pages/SortFilterSheet.kt").readText()
        assertTrue(!sheet.contains("readVisibilityPrefVar = prefs.readVisibility.defaultValue"))
        assertTrue(sheet.contains("readVisibilityAfterReset("))
    }

    @Test
    fun `the sheet remembers before hiding and restores on showing`() {
        val sheet = File("src/main/java/com/saulhdev/feeder/ui/pages/SortFilterSheet.kt").readText()
        assertTrue(sheet.contains("prefs.readVisibilityBeforeHide.set(readVisibility)"))
        assertTrue(sheet.contains("readVisibilityOnShow(\n"))
    }
}

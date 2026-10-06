package com.saulhdev.feeder

import com.saulhdev.feeder.utils.READ_DIM
import com.saulhdev.feeder.utils.READ_HIDE
import com.saulhdev.feeder.utils.READ_KEEP
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
    fun `the sheet remembers before hiding and restores on showing`() {
        val sheet = File("src/main/java/com/saulhdev/feeder/ui/pages/SortFilterSheet.kt").readText()
        assertTrue(sheet.contains("prefs.readVisibilityBeforeHide.set(readVisibility)"))
        assertTrue(sheet.contains("readVisibilityOnShow(\n"))
    }
}

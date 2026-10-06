/*
 * This file is part of Whisper
 * Copyright (c) 2026   Whisper contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.saulhdev.feeder

import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.utils.CategoryOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The reader's order for categories, as the sources screen sets it and every
 * chip row shows it.
 */
class CategoryOrderTest {

    private val alphabetical = listOf("Finance", "Misc", "News", "Tech")

    @Test
    fun `with no order set, the database's alphabetical order stands`() {
        assertEquals(alphabetical, CategoryOrder.arrange(alphabetical, emptyList()))
    }

    @Test
    fun `the reader's order is kept`() {
        val order = listOf("News", "Tech", "Finance", "Misc")
        assertEquals(order, CategoryOrder.arrange(alphabetical, order))
    }

    @Test
    fun `a category added since goes after the placed ones, alphabetically`() {
        val order = listOf("News", "Tech", "Finance", "Misc")
        val tags = listOf("Crypto", "Finance", "Misc", "News", "Rugby", "Tech")
        assertEquals(
            listOf("News", "Tech", "Finance", "Misc", "Crypto", "Rugby"),
            CategoryOrder.arrange(tags, order),
        )
    }

    @Test
    fun `a category no source carries any more is not shown`() {
        val order = listOf("News", "Gone", "Tech")
        assertEquals(listOf("News", "Tech", "Finance", "Misc"), CategoryOrder.arrange(alphabetical, order))
    }

    @Test
    fun `moving a chip moves only that chip`() {
        assertEquals(listOf("Misc", "News", "Finance", "Tech"), CategoryOrder.move(alphabetical, 0, 2))
        assertEquals(listOf("Tech", "Finance", "Misc", "News"), CategoryOrder.move(alphabetical, 3, 0))
        // Out of range, or nowhere, is no move.
        assertEquals(alphabetical, CategoryOrder.move(alphabetical, 0, 4))
        assertEquals(alphabetical, CategoryOrder.move(alphabetical, 2, 2))
    }

    @Test
    fun `a rename keeps its place, and a rename onto a placed name merges there`() {
        val order = listOf("News", "Tech", "Finance")
        assertEquals(listOf("News", "Technology", "Finance"), CategoryOrder.renamed(order, "Tech", "Technology"))
        assertEquals(listOf("News", "Finance"), CategoryOrder.renamed(order, "Tech", "News"))
    }

    @Test
    fun `the stored form reads back as it was written`() {
        val order = listOf("News", "Business & Economy", "Formula 1 & Motorsport")
        assertEquals(order, CategoryOrder.decode(CategoryOrder.encode(order)))
        assertEquals(emptyList<String>(), CategoryOrder.decode(""))
        // Blank lines and repeats, from a hand-edited backup, are dropped.
        assertEquals(listOf("News", "Tech"), CategoryOrder.decode("News\n\n Tech \nNews"))
    }

    @Test
    fun `the order travels with a settings backup`() {
        // Declared, so the importer accepts it, and not on the list of what
        // stays on one phone.
        assertTrue("pref_category_order" in FeedPreferences.DECLARED_KEYS)
        val backup = File("src/main/java/com/saulhdev/feeder/manager/backup/SettingsBackup.kt").readText()
        val notPortable = backup.substringAfter("NOT_PORTABLE").substringBefore(")")
        assertTrue(!notPortable.contains("pref_category_order"))
    }

    @Test
    fun `every chip row reads the one ordered list`() {
        val repo = File("src/main/java/com/saulhdev/feeder/data/repository/SourcesRepository.kt").readText()
        val flow = repo.substringAfter("fun getAllTagsFlow()").substringBefore("fun setCategoryOrder")
        assertTrue("the tag list no longer applies the reader's order", flow.contains("CategoryOrder.arrange"))
    }
}

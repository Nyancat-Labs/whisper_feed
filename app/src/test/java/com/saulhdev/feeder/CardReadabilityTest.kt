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

import com.saulhdev.feeder.ui.overlay.CARD_PANEL_GAP
import com.saulhdev.feeder.utils.shortSourceName
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Set beside another reader's feed, Whisper's read less easily: a lead
 * headline over a busy photograph, heavy headlines, a strapline after every
 * source's name, and nothing to say where one story ended.
 */
class CardReadabilityTest {

    private val cards = File("src/main/java/com/saulhdev/feeder/ui/overlay/ArticleCard.kt").readText()

    @Test
    fun `a card names the publication, not its strapline`() {
        assertEquals("GSMArena.com", shortSourceName("GSMArena.com - Latest articles"))
        assertEquals("TheJournal.ie", shortSourceName("TheJournal.ie - Read, Share and Shape the News"))
        assertEquals("Al Jazeera", shortSourceName("Al Jazeera – Breaking News, World News and Video from Al Jazeera"))
        assertEquals("DER SPIEGEL", shortSourceName("DER SPIEGEL - International"))
        assertEquals("Uncrate", shortSourceName("Uncrate | Gear For Guys"))
        assertEquals("SlashGear", shortSourceName("SlashGear - Tech, Cars, Gaming & Science News"))
    }

    @Test
    fun `when the first part is only a section, the publication is the rest`() {
        assertEquals("The Guardian", shortSourceName("Tennis | The Guardian"))
        assertEquals("Hackaday", shortSourceName("Blog – Hackaday"))
        assertEquals("The JetBrains Blog", shortSourceName("Company | The JetBrains Blog"))
        assertEquals("BreakingNews", shortSourceName("Ireland: BreakingNews"))
    }

    @Test
    fun `a name with nothing to cut is left as it is`() {
        assertEquals("The Independent", shortSourceName("The Independent"))
        assertEquals("NYT > World News", shortSourceName("NYT > World News"))
        assertEquals("Latest from TechRadar", shortSourceName("Latest from TechRadar"))
        assertEquals("Science-Fiction", shortSourceName("Science-Fiction"))
        assertEquals("a - b", shortSourceName("a - b"))
        assertEquals("X", shortSourceName("  X  "))
    }

    @Test
    fun `only the card's line is shortened`() {
        assertEquals(1, Regex("shortSourceName\\(").findAll(cards).count())
        assertTrue(cards.contains("text = shortSourceName(source),"))
    }

    @Test
    fun `the lead headline sits below its picture, like every other card's`() {
        assertFalse("no headline laid over a picture", cards.contains("fun ArticleHeroCard("))
        assertFalse(cards.contains("drawTextShade"))
        assertTrue(cards.contains("FeedCardShape.Hero    -> ArticleCard(\n            item, onClick, onBookmark, onShare, menu = menu, modifier = shapeModifier, coverage = coverage, lead = true,"))
        assertTrue(cards.contains("Modifier.fillMaxWidth().leadPicture(windowHeight)"))
        assertTrue(cards.contains("style = if (lead) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,"))
    }

    @Test
    fun `headlines are medium weight in every shape`() {
        val titles = Regex("""text = item\.contentTitle,(?:\n[^\n]*){1,6}?\n\s*fontWeight = FontWeight\.(\w+)""")
            .findAll(cards).map { it.groupValues[1] }.toList()
        assertEquals("card, compact row, text row and tile", 4, titles.size)
        assertEquals(listOf("Medium"), titles.distinct())
    }

    @Test
    fun `each story sits on a panel in Cards and Magazine, with a narrow strip between`() {
        assertEquals(3.dp, CARD_PANEL_GAP)
        assertTrue(cards.contains("val panel = if (layout == LAYOUT_CARDS || layout == LAYOUT_MAGAZINE) {"))
        assertTrue(cards.contains(".padding(vertical = CARD_PANEL_GAP)\n            .background(MaterialTheme.colorScheme.surfaceContainer)"))
        assertTrue("a read article's panel fades with it", cards.contains("modifier.then(panel).alpha(READ_ALPHA)"))
    }
}

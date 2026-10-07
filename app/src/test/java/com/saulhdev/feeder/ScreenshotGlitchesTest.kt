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

import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.data.db.models.authorName
import com.saulhdev.feeder.ui.overlay.PHOTO_DISC_ALPHA
import com.saulhdev.feeder.ui.overlay.actionsCrowdMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.pow

/** Glitches found in the Play Store screenshots, and in testing after. */
class ScreenshotGlitchesTest {

    private val card = File("src/main/java/com/saulhdev/feeder/ui/overlay/ArticleCard.kt").readText()

    // 1. "NEWS · · 2h": the source name squeezed to nothing on a narrow tile.

    @Test
    fun `a two-column Mosaic tile puts the buttons under the source line`() {
        // A tile on a 411dp phone is about 150dp inside its padding.
        assertTrue(actionsCrowdMeta(150.dp))
        assertTrue(actionsCrowdMeta(180.dp))
        // A full-width tile keeps them beside it.
        assertFalse(actionsCrowdMeta(360.dp))
    }

    @Test
    fun `the tile without a picture asks before putting buttons beside the line`() {
        assertTrue(card.contains("if (hasImage || !actionsCrowdMeta(maxWidth))"))
    }

    // 2. White icons vanishing on a white photograph.

    @Test
    fun `white on the disc is readable over a white photograph`() {
        val behind = 1.0 - PHOTO_DISC_ALPHA // white sky under the disc
        val contrast = 1.05 / (luminance(behind) + 0.05)
        assertTrue("contrast $contrast", contrast >= 3.0)
    }

    @Test
    fun `both controls on a photograph sit on a disc`() {
        assertTrue(card.contains("OnPhoto {\n                        SaveButton("))
        assertTrue(card.contains("OnPhoto { menu(Color.White) }"))
        assertFalse("the thin fade is gone", card.contains("Brush.verticalGradient"))
    }

    // 3. "Zhiye Liu , Tuesday": a feed's author ending in a space.

    @Test
    fun `an author name is trimmed and a blank one is none`() {
        assertEquals("Zhiye Liu", authorName("Zhiye Liu "))
        assertEquals("Zhiye Liu", authorName("  Zhiye Liu\n"))
        assertNull(authorName("   "))
        assertNull(authorName(null))
    }

    @Test
    fun `the reader cleans stored names too`() {
        val reader = File("src/main/java/com/saulhdev/feeder/ui/pages/ArticlePage.kt").readText()
        assertTrue(reader.contains("val author = authorName(state?.article?.author)"))
        assertFalse(reader.contains("unicodeWrap(state?.article?.author"))
    }

    // 4. The save button jumping 12dp sideways as the "⋮" menu opened.

    @Test
    fun `the menu button and its menu are one child of the action row`() {
        // An open DropdownMenu is a child of the layout it is written in. Loose
        // in the card's overlapping action row, it was a third child, and the
        // row's negative spacing moved the save button beside it.
        val menu = File("src/main/java/com/saulhdev/feeder/ui/overlay/ArticleMenu.kt").readText()
        val start = menu.indexOf("fun ArticleOverflowMenu(")
        val body = menu.substring(start, menu.indexOf("\n}\n", start) + 3)
        val box = body.indexOf("    Box(modifier = modifier) {\n")
        assertTrue("wrapped in a box", box > 0)
        assertTrue(box < body.indexOf("IconButton(") && body.indexOf("IconButton(") < body.indexOf("DropdownMenu("))
        // The box closes after the menu and the dialog, at the function's end.
        assertTrue(body.trimEnd().endsWith("explain = false }\n    }\n}"))
    }

    // 5. "Where to Send?" over the share sheet, in English on every phone.

    @Test
    fun `the share sheet is titled in the reader's language`() {
        val offenders = File("src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { f -> Regex("""createChooser\([^)]*"""").containsMatchIn(f.readText()) }
            .map { it.path }
            .toList()
        assertEquals("a chooser titled in English: $offenders", emptyList<String>(), offenders)
    }

    private fun luminance(channel: Double): Double {
        val c = if (channel <= 0.03928) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
        return c // grey: R = G = B, so the weights sum to 1
    }
}

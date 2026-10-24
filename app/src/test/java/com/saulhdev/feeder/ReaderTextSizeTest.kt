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

import androidx.compose.material3.Typography
import androidx.compose.ui.unit.sp
import com.saulhdev.feeder.ui.theme.scaled
import com.saulhdev.feeder.utils.READER_TEXT_DEFAULT
import com.saulhdev.feeder.utils.READER_TEXT_LARGER
import com.saulhdev.feeder.utils.READER_TEXT_SMALL
import com.saulhdev.feeder.utils.readerTextScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Article text size, asked for twice; the second time on 16 October 2026. */
class ReaderTextSizeTest {

    @Test
    fun `each step is larger than the one before, and Default changes nothing`() {
        val steps = listOf("small", "default", "large", "larger").map(::readerTextScale)
        assertEquals(steps.sorted(), steps)
        assertEquals(1f, readerTextScale(READER_TEXT_DEFAULT))
        // A value saved by some later version falls back to Default.
        assertEquals(1f, readerTextScale("enormous"))
    }

    @Test
    fun `the whole scale moves together, line heights with it`() {
        val base = Typography()
        val large = base.scaled(readerTextScale(READER_TEXT_LARGER))
        assertEquals(base.bodyLarge.fontSize.value * 1.3f, large.bodyLarge.fontSize.value, 0.01f)
        assertEquals(base.bodyLarge.lineHeight.value * 1.3f, large.bodyLarge.lineHeight.value, 0.01f)
        assertEquals(base.headlineMedium.fontSize.value * 1.3f, large.headlineMedium.fontSize.value, 0.01f)
        val small = base.scaled(readerTextScale(READER_TEXT_SMALL))
        assertTrue(small.bodyLarge.fontSize < base.bodyLarge.fontSize)
        assertEquals(16.sp, base.bodyLarge.fontSize)
    }

    @Test
    fun `Default hands back the same scale`() {
        val base = Typography()
        assertSame(base, base.scaled(1f))
    }

    /** The article only: the top bar and the feed keep the app's own size. */
    @Test
    fun `the reader scales its article, inside the top bar's scaffold`() {
        val page = File("src/main/java/com/saulhdev/feeder/ui/pages/ArticlePage.kt").readText()
        val content = page.indexOf(") { paddingValues ->")
        val scaled = page.indexOf("typography = MaterialTheme.typography.scaled(readerTextScale(textSize))")
        assertTrue("scaled inside the scaffold's content, below the top bar", scaled > content && content > 0)
        val settings = File("src/main/java/com/saulhdev/feeder/ui/pages/SettingsSection.kt").readText()
        assertTrue(settings.substringAfter("SettingsSection.READING -> listOf(").contains("prefs.readerTextSize,"))
    }
}

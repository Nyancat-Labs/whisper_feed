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

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * "1 sources", "Restored 1 settings": a count in front of a plural noun is a
 * plurals resource, so one of anything reads as one.
 */
class CountWordsTest {

    @Test
    fun `no plain string puts a count in front of a plural noun`() {
        val strings = File("src/main/res/values/strings.xml").readText()
        // A count, then a word ending in s: "%1$d sources". The two that
        // carry a fixed number above one are allowed by name.
        val allowed = setOf("source_pin_full", "stats_by_day_caption")
        val offenders = Regex("""<string name="([^"]+)">[^<]*%\d\${'$'}d [a-z]+s\b""")
            .findAll(strings)
            .map { it.groupValues[1] }
            .filter { it !in allowed }
            .toList()
        assertEquals("these want plurals: $offenders", emptyList<String>(), offenders)
    }
}

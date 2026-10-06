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

import com.saulhdev.feeder.manager.models.isThinExtraction
import com.saulhdev.feeder.manager.sync.greader.AccountTally
import com.saulhdev.feeder.manager.sync.greader.accountSummary
import com.saulhdev.feeder.utils.SyncResult
import com.saulhdev.feeder.utils.seconds
import com.saulhdev.feeder.utils.slowestOf
import com.saulhdev.feeder.utils.syncOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A slow sync says where its minutes went: each step of an account sync,
 * and the few feeds it waited longest for. On 29 September syncs took eight
 * minutes and the report could say only that they had.
 */
class SyncTimingTest {

    @Test
    fun `the slowest feeds are named, slowest first, and only if slow`() {
        val times = listOf("BizToc" to 41_000L, "BBC" to 900L, "Techmeme" to 12_500L, "RTÉ" to 3_200L, "Wired" to 5_000L)
        assertEquals(listOf("BizToc" to 41_000L, "Techmeme" to 12_500L, "Wired" to 5_000L), slowestOf(times))
        assertEquals(emptyList<Pair<String, Long>>(), slowestOf(listOf("BBC" to 900L, "RTÉ" to 2_999L)))
    }

    @Test
    fun `the history line carries them`() {
        val line = syncOutcome(SyncResult(due = 112, slowest = listOf("BizToc" to 41_000L, "Techmeme" to 7_200L)))
        assertEquals("ok (112 feeds, slowest BizToc 41s, Techmeme 7.2s)", line)
        // A failed run says why, not who was slow.
        assertFalse(syncOutcome(SyncResult(due = 5, error = "IOException", slowest = listOf("A" to 9_000L))).contains("slowest"))
    }

    @Test
    fun `an account sync says how long each step took`() {
        val summary = accountSummary(
            AccountTally(serverFeeds = 128, matched = 11_365, steps = listOf("feeds" to 95_000L, "matching" to 4_200L, "read state" to 800L))
        )
        assertEquals("128 feeds on the server; 11365 articles matched; took feeds 95s, matching 4.2s, read state 0.8s", summary)
        assertFalse(accountSummary(AccountTally(serverFeeds = 1)).contains("took"))
    }

    @Test
    fun `seconds read as a person would say them`() {
        assertEquals("0.4s", seconds(400))
        assertEquals("9.9s", seconds(9_949))
        assertEquals("45s", seconds(45_900))
        assertTrue(seconds(499_000) == "499s")
    }

    @Test
    fun `a page that gives less than the feed is not the article`() {
        // Techmeme, 29 September: the feed carried a paragraph and a picture,
        // the page gave "11:45 AM ET, Sep 29, 2026", and that replaced them.
        assertTrue(isThinExtraction(extractedChars = 25, feedChars = 340))
        assertFalse(isThinExtraction(extractedChars = 4_800, feedChars = 340))
        // A feed with no text of its own takes whatever the page gives.
        assertFalse(isThinExtraction(extractedChars = 25, feedChars = 0))
    }
}

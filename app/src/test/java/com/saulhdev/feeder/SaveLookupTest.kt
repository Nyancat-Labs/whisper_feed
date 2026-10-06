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

import com.saulhdev.feeder.manager.sync.greader.AccountTally
import com.saulhdev.feeder.manager.sync.greader.SAVE_LOOKUP_TRIES
import com.saulhdev.feeder.manager.sync.greader.SaveLookup
import com.saulhdev.feeder.manager.sync.greader.accountSummary
import com.saulhdev.feeder.manager.sync.greader.planSaveLookups
import com.saulhdev.feeder.manager.sync.greader.saveLookupDue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A save the server does not have is looked for five times over about a day,
 * and then let go, instead of a thousand items of its feed on every sync for
 * ever. It stays saved on the phone either way.
 */
class SaveLookupTest {

    private val hour = 60L * 60 * 1000
    private val start = 1_800_000_000_000L

    @Test
    fun `a save is looked for at once, then an hour, three, six and twelve later`() {
        assertTrue(saveLookupDue(null, start))
        val waits = mutableListOf<Long>()
        var record = SaveLookup(tries = 1, lastAt = start)
        while (record.tries < SAVE_LOOKUP_TRIES) {
            // The first moment, hour by hour, that the next look is due.
            var at = record.lastAt
            while (!saveLookupDue(record, at)) at += hour
            waits += (at - record.lastAt) / hour
            record = SaveLookup(record.tries + 1, at)
        }
        assertEquals(listOf(1L, 3L, 6L, 12L), waits)
    }

    @Test
    fun `a sync in between does not look again`() {
        val record = SaveLookup(tries = 2, lastAt = start)
        assertFalse(saveLookupDue(record, start + 2 * hour))
        assertTrue(saveLookupDue(record, start + 3 * hour))
    }

    @Test
    fun `each look counts, and the fifth lets the save go`() {
        var records = emptyMap<String, SaveLookup>()
        var now = start
        repeat(SAVE_LOOKUP_TRIES - 1) {
            val after = planSaveLookups(setOf("a"), looked = setOf("a"), before = records, now = now)
            assertEquals(setOf("a"), after.keep)
            assertTrue(after.givenUp.isEmpty())
            records = after.records
            now += 24 * hour
        }
        val last = planSaveLookups(setOf("a"), looked = setOf("a"), before = records, now = now)
        assertEquals(setOf("a"), last.givenUp)
        assertTrue(last.keep.isEmpty())
        assertTrue("a let-go save keeps no record", last.records.isEmpty())
    }

    @Test
    fun `a save not due this time is held with its record as it was`() {
        val before = mapOf("a" to SaveLookup(2, start))
        val after = planSaveLookups(setOf("a"), looked = emptySet(), before = before, now = start + hour)
        assertEquals(setOf("a"), after.keep)
        assertEquals(SaveLookup(2, start), after.records["a"])
    }

    @Test
    fun `a save that was found, or no longer waits, loses its record`() {
        val before = mapOf("found" to SaveLookup(3, start), "held" to SaveLookup(1, start))
        val after = planSaveLookups(setOf("held"), looked = setOf("held"), before = before, now = start + hour)
        assertEquals(setOf("held"), after.records.keys)
    }

    @Test
    fun `the sync summary says how many were let go`() {
        assertTrue(accountSummary(AccountTally(savesNotFound = 2)).contains("2 saves not on the server"))
        assertFalse(accountSummary(AccountTally()).contains("not on the server"))
    }
}

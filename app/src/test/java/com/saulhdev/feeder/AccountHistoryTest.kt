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

import com.saulhdev.feeder.manager.sync.greader.Subscription
import com.saulhdev.feeder.manager.sync.greader.SubscriptionCategory
import com.saulhdev.feeder.manager.sync.greader.isCatchAllFolder
import com.saulhdev.feeder.manager.sync.service.SyncOutcome
import com.saulhdev.feeder.manager.sync.service.toSyncResult
import com.saulhdev.feeder.utils.SyncResult
import com.saulhdev.feeder.utils.syncOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.saulhdev.feeder.utils.SyncLog
import java.io.File
import java.io.IOException

/**
 * The first night with a FreshRSS account: two "Sync now" runs that left no
 * line in the history, and the server's catch-all folder turning up as a
 * category.
 */
class AccountHistoryTest {

    @Test
    fun `the server's folder for no folder is not a category`() {
        listOf("Uncategorized", "uncategorised", " UNCATEGORIZED ").forEach { assertTrue(it, isCatchAllFolder(it)) }
        listOf("Tech", "News", "Uncategorized stuff").forEach { assertFalse(it, isCatchAllFolder(it)) }
        val sub = Subscription(
            categories = listOf(
                SubscriptionCategory(id = "user/-/label/Uncategorized"),
                SubscriptionCategory(id = "user/-/label/Tech"),
            ),
        )
        assertEquals(listOf("Tech"), sub.folders)
    }

    @Test
    fun `every outcome reads as a history line`() {
        val feeds = SyncResult(due = 40, unchanged = 10, identical = 5)
        assertEquals("ok (40 feeds, 10 unchanged, 5 identical)", syncOutcome(SyncOutcome.Success(feeds = feeds).toSyncResult()))
        assertEquals("ok", syncOutcome(SyncOutcome.Success().toSyncResult()))
        assertEquals("failed: signed out", syncOutcome(SyncOutcome.SignedOut.toSyncResult()))
        assertEquals("failed: IOException", syncOutcome(SyncOutcome.Failed(IOException("x")).toSyncResult()))
    }

    private fun source(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    @Test
    fun `Sync now goes through the worker, which keeps the network and writes the line`() {
        // Run on the screen, it lost the network when the reader switched
        // apps and said no server could be found.
        val vm = source("viewmodels/AccountViewModel.kt")
        val sync = vm.substring(vm.indexOf("fun syncNow()"), vm.indexOf("init {"))
        assertTrue(sync.contains("requestFeedSync(feedId = ID_ALL, forceNetwork = true, origin = SyncLog.ORIGIN_ACCOUNT)"))
        assertFalse(vm.contains("dispatcher"))
        assertTrue(SyncLog.ORIGIN_ACCOUNT in SyncLog.ASKED_ORIGINS)
        assertFalse("Battery Saver does not hold it", SyncLog.ORIGIN_ACCOUNT in SyncLog.AUTOMATIC_ORIGINS)
        val worker = source("manager/sync/FeedSyncer.kt")
        assertTrue(worker.contains("val foreground = runInForeground(origin, dataBlocked, onScreen)"))
        assertTrue(com.saulhdev.feeder.manager.sync.runInForeground(SyncLog.ORIGIN_ACCOUNT, dataBlocked = false, onScreen = false))
    }

    @Test
    fun `the worker tells the account screen what went wrong`() {
        val worker = source("manager/sync/FeedSyncer.kt")
        assertTrue(worker.contains("output = accountProblemData(problemFrom(outcome.cause), outcome.cause?.message)"))
        assertTrue(worker.contains("output = accountProblemData(AccountProblem.SIGNED_OUT, null)"))
        assertTrue(worker.contains("true  -> Result.success(output)"))
        assertTrue(worker.contains("false -> Result.failure(output)"))
        val vm = source("viewmodels/AccountViewModel.kt")
        assertTrue(vm.contains("getWorkInfosForUniqueWorkFlow(oneTimeSyncName(ID_ALL))"))
        assertTrue(vm.contains("info.outputData.getString(ACCOUNT_PROBLEM_KEY)"))
        assertTrue("an old result stays quiet", vm.contains("if (!seenActive) return@collect"))
        assertTrue(worker.contains("oneTimeSyncName(feedId),"))
    }

    @Test
    fun `a category taken from the catch-all folder is cleared on the next sync`() {
        val service = source("manager/sync/service/GoogleReaderService.kt")
        assertTrue(service.contains("tag.isEmpty() && existing.tags.isNotEmpty() && existing.tags.all(::isCatchAllFolder)"))
        assertTrue(service.contains("sources.updateSource(existing.copy(tag = \"\"))"))
    }
}

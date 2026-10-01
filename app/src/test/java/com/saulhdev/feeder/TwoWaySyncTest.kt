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

import com.saulhdev.feeder.data.db.models.MappedArticle
import com.saulhdev.feeder.manager.sync.greader.GoogleReaderApi
import com.saulhdev.feeder.manager.sync.greader.Outbox
import com.saulhdev.feeder.manager.sync.greader.collectPages
import com.saulhdev.feeder.manager.sync.greader.planSubscriptions
import com.saulhdev.feeder.manager.sync.greader.readChanges
import com.saulhdev.feeder.manager.sync.greader.rememberAfter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Sync that only ever went one way: the server's feeds came down, nothing
 * went up, and every read made in Whisper was undone by the next sync.
 */
class TwoWaySyncTest {

    // --- the outbox -------------------------------------------------------

    @Test
    fun `the opposite change replaces the first`() {
        val o = Outbox().withRead(listOf("a", "b"), true).withRead(listOf("a"), false)
        assertEquals(setOf("b"), o.read)
        assertEquals(setOf("a"), o.unread)
        val back = Outbox().withRead(listOf("a"), false).withRead(listOf("a"), true)
        assertEquals(setOf("a"), back.read)
        assertEquals("and the other way round", emptySet<String>(), back.unread)
        val s = Outbox().withStar("x", true).withStar("x", false)
        assertEquals(emptySet<String>(), s.star)
        assertEquals(setOf("x"), s.unstar)
    }

    @Test
    fun `what was sent leaves, and what came in meanwhile stays`() {
        val sent = Outbox(read = setOf("a"), star = setOf("s"))
        val now = sent.withRead(listOf("b"), true)
        assertEquals(Outbox(read = setOf("b")), now.without(sent))
        assertTrue(Outbox().isEmpty)
        assertEquals(setOf("a", "s"), sent.pending)
    }

    // --- feeds, both ways -------------------------------------------------

    @Test
    fun `the first sync sends each side what it lacks`() {
        val plan = planSubscriptions(
            local = setOf("bbc", "verge"),
            server = setOf("freshrss", "verge"),
            lastLocal = emptySet(),
            everOnServer = emptySet(),
        )
        assertEquals(setOf("bbc"), plan.subscribe)
        assertEquals(setOf("freshrss"), plan.addLocal)
        assertTrue("nothing removed on a first sync", plan.unsubscribe.isEmpty())
    }

    @Test
    fun `a feed removed here is removed from the server`() {
        val plan = planSubscriptions(
            local = setOf("verge"),
            server = setOf("bbc", "verge"),
            lastLocal = setOf("bbc", "verge"),
            everOnServer = setOf("bbc", "verge"),
        )
        assertEquals(setOf("bbc"), plan.unsubscribe)
        assertTrue("and not added back", plan.addLocal.isEmpty())
    }

    @Test
    fun `a feed removed on the server stays here, and is not sent back`() {
        val plan = planSubscriptions(
            local = setOf("bbc", "verge"),
            server = setOf("verge"),
            lastLocal = setOf("bbc", "verge"),
            everOnServer = setOf("bbc", "verge"),
        )
        assertTrue(plan.subscribe.isEmpty())
        assertTrue(plan.unsubscribe.isEmpty())
        assertTrue(plan.addLocal.isEmpty())
    }

    @Test
    fun `new on either side goes to the other`() {
        val plan = planSubscriptions(
            local = setOf("bbc", "verge", "new-here"),
            server = setOf("bbc", "verge", "new-there"),
            lastLocal = setOf("bbc", "verge"),
            everOnServer = setOf("bbc", "verge"),
        )
        assertEquals(setOf("new-here"), plan.subscribe)
        assertEquals(setOf("new-there"), plan.addLocal)
    }

    /** Three syncs in a row, with what each remembers carried to the next. */
    @Test
    fun `a failed change is tried again, and never turned into its opposite`() {
        var lastLocal = setOf("bbc", "verge")
        var ever = setOf("bbc", "verge")

        // Removed here; the server refuses the removal this time.
        var local = setOf("verge")
        var server = setOf("bbc", "verge")
        var plan = planSubscriptions(local, server, lastLocal, ever)
        assertEquals(setOf("bbc"), plan.unsubscribe)
        rememberAfter(plan, local, server, lastLocal, ever, subscribed = emptySet(), unsubscribed = emptySet())
            .let { (l, e) -> lastLocal = l; ever = e }

        // Next sync: tried again, not taken for a feed new on the server.
        plan = planSubscriptions(local, server, lastLocal, ever)
        assertEquals(setOf("bbc"), plan.unsubscribe)
        assertTrue(plan.addLocal.isEmpty())
        rememberAfter(plan, local, server, lastLocal, ever, subscribed = emptySet(), unsubscribed = setOf("bbc"))
            .let { (l, e) -> lastLocal = l; ever = e }

        // Gone from the server. Added back here later: sent again.
        server = setOf("verge")
        local = setOf("verge", "bbc")
        plan = planSubscriptions(local, server, lastLocal, ever)
        assertEquals(setOf("bbc"), plan.subscribe)
    }

    @Test
    fun `a feed the server dropped, removed here and added back, is sent again`() {
        // Dropped on the server: kept here, not sent back.
        var lastLocal = setOf("bbc")
        var ever = setOf("bbc")
        var local = setOf("bbc")
        val server = emptySet<String>()
        var plan = planSubscriptions(local, server, lastLocal, ever)
        assertTrue(plan.subscribe.isEmpty())
        rememberAfter(plan, local, server, lastLocal, ever, emptySet(), emptySet()).let { (l, e) -> lastLocal = l; ever = e }
        // Removed here.
        local = emptySet()
        plan = planSubscriptions(local, server, lastLocal, ever)
        rememberAfter(plan, local, server, lastLocal, ever, emptySet(), emptySet()).let { (l, e) -> lastLocal = l; ever = e }
        // Added back: the reader wants it, so the server gets it.
        local = setOf("bbc")
        assertEquals(setOf("bbc"), planSubscriptions(local, server, lastLocal, ever).subscribe)
    }

    @Test
    fun `a subscribe that did not go through is sent again`() {
        val local = setOf("bbc")
        val server = emptySet<String>()
        val plan = planSubscriptions(local, server, emptySet(), emptySet())
        val (lastLocal, ever) = rememberAfter(plan, local, server, emptySet(), emptySet(), emptySet(), emptySet())
        assertEquals(setOf("bbc"), planSubscriptions(local, server, lastLocal, ever).subscribe)
    }

    // --- read state -------------------------------------------------------

    private fun a(uuid: String, remote: String, read: Boolean) =
        MappedArticle(uuid = uuid, remoteId = remote, readAt = if (read) 1L else 0L, bookmarked = false)

    @Test
    fun `the server's unread list applies only to what is not waiting to go`() {
        val mapped = listOf(
            a("readHere", "1", read = true),
            a("readHereWaiting", "2", read = true),
            a("unreadHere", "3", read = false),
            a("readThere", "4", read = false),
        )
        val (toRead, toUnread) = readChanges(mapped, serverUnread = setOf("1", "2", "3"), complete = true, waiting = setOf("readHereWaiting"))
        assertEquals(listOf("readHere"), toUnread)
        assertEquals(listOf("readThere"), toRead)
    }

    @Test
    fun `absence means read only in a whole, non-empty list`() {
        val mapped = listOf(a("x", "9", read = false))
        assertTrue("partial list", readChanges(mapped, setOf("1"), complete = false, waiting = emptySet()).first.isEmpty())
        assertTrue("empty list", readChanges(mapped, emptySet(), complete = true, waiting = emptySet()).first.isEmpty())
        assertEquals(listOf("x"), readChanges(mapped, setOf("1"), complete = true, waiting = emptySet()).first)
    }

    // --- paging -----------------------------------------------------------

    @Test
    fun `pages are followed to the end, and a cap says it was not the end`() = runBlocking {
        val pages = mapOf(null to (listOf(1, 2) to "b"), "b" to (listOf(3) to null))
        val all = collectPages<Int>(maxPages = 10) { c -> pages.getValue(c) }
        assertEquals(listOf(1, 2, 3), all.items)
        assertTrue(all.complete)
        val capped = collectPages<Int>(maxPages = 1) { c -> pages.getValue(c) }
        assertFalse(capped.complete)
        val loop = collectPages<Int>(maxPages = 10) { _ -> listOf(1) to "same" }
        assertEquals("a repeated continuation ends it", 2, loop.items.size)
    }

    @Test
    fun `a continuation is read from both kinds of page`() {
        val ids = GoogleReaderApi.itemRefsAdapter.fromJson("""{"itemRefs":[{"id":"1"}],"continuation":"next"}""")
        assertEquals("next", ids?.continuation)
        val contents = GoogleReaderApi.streamContentsAdapter.fromJson("""{"items":[],"continuation":"c2"}""")
        assertEquals("c2", contents?.continuation)
    }

    // --- wiring -----------------------------------------------------------

    private fun source(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    @Test
    fun `changes go up before anything comes down`() {
        val sync = source("manager/sync/service/GoogleReaderService.kt").substringAfter("override suspend fun sync(")
        val order = listOf(
            "pushChanges(auth, token, matchedOnly = true)",
            "syncSubscriptions(auth, token, retryRefused)",
            "syncFeeds(context = context, feedId = ID_ALL, forceNetwork = forceNetwork)",
            "mapRemoteIds(auth)",
            "requeueSavesOnce()",
            "val saves = step(\"saves lookup\") { mapPendingSaves(auth) }",
            "val push = step(\"sending\") { pushChanges(auth, token, saves.keep) }",
            "pullReadState(auth)",
        ).map { sync.indexOf(it) }
        assertTrue(order.toString(), order.all { it >= 0 } && order == order.sorted())
        assertTrue("only once they are sent", sync.contains("if (push.ok) {\n                val (read, unread) = step(\"read state\") { pullReadState(auth) }"))
    }

    @Test
    fun `every read and save the reader makes is queued, and nothing from the server is`() {
        val repo = source("data/repository/ArticleRepository.kt")
        listOf("suspend fun markRead(", "suspend fun markOpened(", "suspend fun markAllRead(", "suspend fun unmarkRead(").forEach {
            val body = repo.substring(repo.indexOf(it)).substringBefore("\n    }\n")
            assertTrue(it, body.contains("onReadChanged?.invoke("))
        }
        assertTrue(repo.substring(repo.indexOf("suspend fun bookmarkArticle(")).substringBefore("\n    }\n").contains("onStarredChanged?.invoke("))
        val fromServer = repo.substring(repo.indexOf("suspend fun applyServerRead(")).substringBefore("\n    }\n")
        assertFalse("no echo", fromServer.contains("onReadChanged") || fromServer.contains("tally("))
        val app = source("NeoApp.kt")
        assertTrue(app.contains("if (account.isSignedIn) GoogleReaderState.updateOutbox(this) { it.withRead(ids, read) }"))
    }

    @Test
    fun `newly matched articles keep what the reader did here`() {
        val service = source("manager/sync/service/GoogleReaderService.kt")
        val map = service.substring(service.indexOf("private suspend fun mapRemoteIds(")).substringBefore("\n    }\n")
        assertTrue(map.contains(".withRead(newlyMapped.filter { it.readAt != 0L"))
        assertTrue(map.contains("acc.withStar(a.uuid, true)"))
        assertTrue("the first match waits for Wi-Fi", map.contains("if (last == 0L && !isUnmetered(context))"))
        assertTrue("and later ones ask only for what is new", map.contains("if (last > 0) last - MAP_OVERLAP_MS"))
    }

    @Test
    fun `switched-off feeds count as feeds, and a new account starts clean`() {
        assertTrue(source("manager/sync/service/GoogleReaderService.kt").contains("val local = sources.getAllSubscriptions()"))
        val account = source("data/content/SyncAccount.kt")
        assertEquals(2, Regex("GoogleReaderState.clear\\(appContext\\)").findAll(account).count())
    }
}

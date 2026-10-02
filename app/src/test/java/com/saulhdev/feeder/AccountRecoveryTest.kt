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

import com.saulhdev.feeder.manager.sync.greader.matchFeeds
import com.saulhdev.feeder.manager.sync.greader.planSubscriptions
import com.saulhdev.feeder.manager.sync.greader.titleKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The first night against a live server: 29 feeds copied both ways because
 * their addresses differed, three syncs at once, and fifty-megabyte retries.
 */
class AccountRecoveryTest {

    @Test
    fun `the same address is the same feed`() {
        val m = matchFeeds(mapOf("verge/rss" to "The Verge"), mapOf("verge/rss" to "The Verge"), emptyMap())
        assertEquals(mapOf("verge/rss" to "verge/rss"), m.serverAs)
        assertTrue(m.newAliases.isEmpty())
    }

    @Test
    fun `a different address with the same title is the same feed, and is remembered`() {
        val m = matchFeeds(
            local = mapOf("pocket-lint.com/rss.phtml" to "Pocket-lint"),
            server = mapOf("pocket-lint.com/feed" to "Pocket-lint "),
            aliases = emptyMap(),
        )
        assertEquals("pocket-lint.com/rss.phtml", m.serverAs["pocket-lint.com/feed"])
        assertEquals(mapOf("pocket-lint.com/feed" to "pocket-lint.com/rss.phtml"), m.newAliases)
    }

    @Test
    fun `a remembered pairing holds after a rename`() {
        val m = matchFeeds(
            local = mapOf("a/rss" to "My name for it"),
            server = mapOf("a/feed" to "Publisher's name"),
            aliases = mapOf("a/feed" to "a/rss"),
        )
        assertEquals("a/rss", m.serverAs["a/feed"])
    }

    @Test
    fun `a feed with the same title as a matched one is still sent up`() {
        // The live server was missing the second of two Guardian feeds: taken
        // for a copy of the first and held back, when it was another section.
        val m = matchFeeds(
            local = mapOf("guardian/world" to "The Guardian", "guardian/uk" to "The Guardian"),
            server = mapOf("guardian/world" to "The Guardian"),
            aliases = emptyMap(),
        )
        assertEquals(mapOf("guardian/world" to "guardian/world"), m.serverAs)
        val plan = planSubscriptions(setOf("guardian/world", "guardian/uk"), m.serverAs.values.toSet(), emptySet(), emptySet())
        assertEquals(setOf("guardian/uk"), plan.subscribe)
    }

    @Test
    fun `a spare server feed with a matched title comes down, rather than vanishing`() {
        val m = matchFeeds(
            local = mapOf("npr/1" to "NPR"),
            server = mapOf("npr/1" to "NPR", "npr/2" to "NPR"),
            aliases = emptyMap(),
        )
        assertEquals(mapOf("npr/1" to "npr/1", "npr/2" to "npr/2"), m.serverAs)
    }

    @Test
    fun `two sections with one name pair up by address first`() {
        val m = matchFeeds(
            local = mapOf("guardian/world" to "The Guardian", "guardian/europe" to "The Guardian"),
            server = mapOf("guardian/europe" to "The Guardian", "feeds.guardian/world" to "The Guardian"),
            aliases = emptyMap(),
        )
        assertEquals("guardian/europe", m.serverAs["guardian/europe"])
        assertEquals("guardian/world", m.serverAs["feeds.guardian/world"])
    }

    @Test
    fun `among feeds with one title, the address that ends alike is the pair`() {
        // Sorted order alone would pair the server's world feed with the
        // Europe one, which sorts first.
        val m = matchFeeds(
            local = mapOf("theguardian.com/europe/rss" to "The Guardian", "theguardian.com/world/rss" to "The Guardian"),
            server = mapOf("feeds.theguardian.com/theguardian/world/rss" to "The Guardian"),
            aliases = emptyMap(),
        )
        assertEquals("theguardian.com/world/rss", m.serverAs["feeds.theguardian.com/theguardian/world/rss"])
    }

    @Test
    fun `the report says which feeds the server lacks, and why`() {
        assertTrue(service.contains("val missing = (localKeys - remoteAs.keys - subscribed).map { key ->"))
        assertTrue(service.contains("key !in plan.subscribe -> MissingFeed(title, MissingKind.REMOVED_THERE)"))
        assertTrue(service.contains("else -> MissingFeed(title, MissingKind.REFUSED, refusals[key]?.status ?: 0)"))
        val diagnostics = source("utils/Diagnostics.kt")
        assertTrue(diagnostics.contains("appendLine(\"Not on the server: \${missing.size}\")"))
        assertTrue(diagnostics.contains("MissingKind.REFUSED -> \"refused by the server\" + refusalCode(feed.status) + \", kept on this phone\""))
    }

    @Test
    fun `new on the server stays new`() {
        val m = matchFeeds(mapOf("a" to "A"), mapOf("a" to "A", "b" to "B"), emptyMap())
        assertEquals("b", m.serverAs["b"])
    }

    @Test
    fun `titles compare without case, spacing or trailing marks`() {
        assertEquals(titleKey("The  Verge -"), titleKey("the verge"))
        assertEquals("", titleKey("   "))
    }

    private fun source(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()
    private val service = source("manager/sync/service/GoogleReaderService.kt")

    @Test
    fun `one account sync at a time`() {
        assertTrue(service.contains("return accountLock.withLock { syncLocked(auth, forceNetwork, retryRefused, trace) }"))
        assertTrue(service.contains("private val accountLock = Mutex()"))
    }

    @Test
    fun `being stopped is not failing`() {
        val body = service.substring(service.indexOf("private suspend fun syncLocked("))
        val cancel = body.indexOf("} catch (e: CancellationException) {\n            // Android stopping the work")
        assertTrue(cancel >= 0)
        assertTrue("before the catch-all", cancel < body.indexOf("} catch (t: Throwable) {"))
        assertTrue(body.substring(cancel).substringBefore("} catch (t: Throwable)").contains("throw e"))
    }

    @Test
    fun `the first match is small, capped, and kept as it goes`() {
        assertTrue(service.contains("const val MAP_FIRST_WINDOW_MS = 2 * DAY_MS"))
        assertTrue(service.contains("while (pages < MAP_MAX_PAGES) {"))
        val map = service.substring(service.indexOf("private suspend fun mapRemoteIds(")).substringBefore("\n    }\n")
        val loop = map.substring(map.indexOf("while (pages < MAP_MAX_PAGES)"))
        assertTrue("matched per page", loop.substringBefore("if (next == null").contains("articles.attachRemoteId(link, remoteId)"))
        assertTrue("and the next match starts from here", map.contains("GoogleReaderState.setMappedAt(context, startedAt)\n        Log.i"))
    }

    @Test
    fun `feeds are compared in Whisper's keys, with the pairings kept`() {
        assertTrue(service.contains("val plan = planSubscriptions(localKeys, remoteAs.keys, lastLocal, everOnServer)"))
        assertTrue(service.contains("val localKeys = localByKey.keys\n"))
        assertTrue(service.contains("(oldAliases + match.newAliases).filterKeys { it in remoteByKey }"))
    }

    @Test
    fun `extra copies go, the oldest stays, and only for the filter that is sure`() {
        val vm = source("viewmodels/SourceListViewModel.kt")
        assertTrue(vm.contains("_duplicateGroups.value.flatMap { it.ids.sorted().drop(1) }.toSet()"))
        val page = source("ui/pages/SourceListPage.kt")
        assertTrue(page.contains("onRemoveExtra = if (sameSite) null else viewModel::removeDuplicateCopies,"))
    }
}

package com.saulhdev.feeder

import com.saulhdev.feeder.manager.sync.greader.AccountTally
import com.saulhdev.feeder.manager.sync.greader.accountSummary
import com.saulhdev.feeder.manager.sync.greader.plus
import com.saulhdev.feeder.manager.sync.service.refusalCode
import com.saulhdev.feeder.utils.SyncResult
import com.saulhdev.feeder.utils.syncOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * "Did it all get there?" After moving 114 feeds to a server, the only answer
 * was a shell on the server and a command to count them.
 */
class AccountTallyTest {

    @Test
    fun `a quiet sync still says what the server has`() {
        assertEquals(
            "110 feeds on the server; 1247 articles matched",
            accountSummary(AccountTally(serverFeeds = 110, matched = 1247)),
        )
    }

    @Test
    fun `everything that happened is named`() {
        val t = AccountTally(
            serverFeeds = 114, feedsSent = 4, feedsRefused = 1, feedsRemoved = 2, feedsAdded = 3, matched = 50,
            readSent = 5, savedSent = 1, changesKept = 2, readHere = 7, unreadHere = 1, savedHere = 2,
        )
        assertEquals(
            "114 feeds on the server, 4 sent, 1 refused, 2 removed, 3 added here; 50 articles matched; " +
                "sent 5 read, 1 saved; 2 changes kept; received 7 read, 1 unread, 2 saved",
            accountSummary(t),
        )
    }

    @Test
    fun `the history line carries it after the feed counts, and not on a failure`() {
        val ok = SyncResult(due = 114, unchanged = 20, account = AccountTally(serverFeeds = 110, matched = 9))
        assertEquals("ok (114 feeds, 20 unchanged); 110 feeds on the server; 9 articles matched", syncOutcome(ok))
        assertFalse(syncOutcome(ok.copy(error = "IOException")).contains("on the server"))
    }

    @Test
    fun `a refusal says what the server answered`() {
        assertEquals(" (400)", refusalCode(400))
        assertEquals(" (no answer)", refusalCode(0))
        assertEquals("", refusalCode(null))
    }

    private fun source(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    @Test
    fun `the sync counts what it did and keeps it for the account screen`() {
        val service = source("manager/sync/service/GoogleReaderService.kt")
        assertTrue(service.contains("feedsRefused = missing.count { it.kind == MissingKind.REFUSED },"))
        assertTrue(service.contains("serverFeeds = (remoteAs.keys + subscribed - unsubscribed).size,"))
        assertTrue(service.contains("if (done) accepted[i] += chunk.size else ok = false"))
        assertTrue(service.contains("AccountTallyStore.write(context, tally, account.lastSync)"))
        val page = source("ui/pages/AccountPage.kt")
        assertTrue(page.contains("state.tally?.let { tally ->"))
        val account = source("data/content/SyncAccount.kt")
        assertEquals("cleared on sign in and sign out", 2, Regex("AccountTallyStore.clear\\(appContext\\)").findAll(account).count())
    }

    @Test
    fun `Data sources always has a way back`() {
        val page = source("ui/pages/SourceListPage.kt")
        assertTrue(page.contains("showBackButton = true,"))
        assertFalse(page.contains("showBackButton = selecting"))
        assertTrue(page.contains("onBackAction = if (selecting) viewModel::clearSelection else null,"))
    }
}

/** Coming back to Data sources brought the keyboard back with it. */
class SourceSearchFocusTest {
    private val page = File("src/main/java/com/saulhdev/feeder/ui/pages/SourceListPage.kt").readText()

    @Test
    fun `the search field lets go of focus whenever the list is left`() {
        val pane = page.substring(page.indexOf("listPane = {")).substringBefore("ViewWithActionBar(")
        assertTrue(pane.contains("LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { focusManager.clearFocus() }"))
        assertTrue(pane.contains("if (sourceId.longValue != -1L) focusManager.clearFocus()"))
        assertTrue(pane.contains("DisposableEffect(Unit) { onDispose { focusManager.clearFocus() } }"))
    }
}

/**
 * A feed the site keeps from the server stays on the phone, said plainly,
 * and is offered to the server once a week rather than on every sync.
 */
class RefusedFeedTest {
    private val now = 1_000_000_000_000L
    private val day = 24 * 60 * 60_000L

    @Test
    fun `a feed never refused is sent`() {
        assertTrue(com.saulhdev.feeder.manager.sync.greader.refusedDueForRetry(null, now, asked = false))
    }

    @Test
    fun `a refused feed waits a week`() {
        val r = com.saulhdev.feeder.manager.sync.greader.Refusal(at = now - 6 * day, status = 400)
        assertFalse(com.saulhdev.feeder.manager.sync.greader.refusedDueForRetry(r, now, asked = false))
        assertTrue(com.saulhdev.feeder.manager.sync.greader.refusedDueForRetry(r, now + day, asked = false))
    }

    @Test
    fun `Sync now tries it straight away, so a fix on the server can be tested`() {
        val r = com.saulhdev.feeder.manager.sync.greader.Refusal(at = now, status = 400)
        assertTrue(com.saulhdev.feeder.manager.sync.greader.refusedDueForRetry(r, now, asked = true))
    }

    private fun source(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    @Test
    fun `only the account screen's Sync now asks`() {
        val worker = source("manager/sync/FeedSyncer.kt")
        assertTrue(worker.contains("retryRefused = origin == SyncLog.ORIGIN_ACCOUNT,"))
        val service = source("manager/sync/service/GoogleReaderService.kt")
        assertTrue(service.contains(".filter { refusedDueForRetry(refusals[it], now, retryRefused) }"))
        assertTrue("forgotten once taken", service.contains("subscribed += key\n                        refusals.remove(key)"))
        assertTrue("and once on the server or gone", service.contains(".filterKeys { it in plan.subscribe }"))
    }

    @Test
    fun `the screen explains it, not in the error colour`() {
        val page = source("ui/pages/AccountPage.kt")
        val group = page.substring(page.indexOf("private fun MissingGroup(")).substringBefore("\n}\n")
        assertFalse(group.contains("colorScheme.error"))
        assertTrue(page.contains("heading = R.string.account_phone_only_title,"))
        val strings = File("src/main/res/values/strings.xml").readText()
        assertTrue(strings.contains("They still update here as normal, but reading them will not sync to your other devices."))
    }
}

/**
 * 163 reads came down at five past eleven; two quiet syncs later the screen
 * said nothing had happened. Today's totals keep it in view.
 */
class DayTotalsTest {
    private val t = AccountTally(readSent = 1, readHere = 28, savedHere = 2, feedsSent = 1)

    @Test
    fun `syncs on one day add up`() {
        val day = null.plus(t, "2026-09-26")
            .plus(AccountTally(readHere = 163), "2026-09-26")
            .plus(AccountTally(), "2026-09-26")
        assertEquals(1, day.readSent)
        assertEquals(191, day.readHere)
        assertEquals(2, day.savedHere)
        assertEquals(1, day.feedsSent)
        assertTrue(day.sentAny && day.receivedAny && day.feedsChanged)
    }

    @Test
    fun `a new day starts from nothing`() {
        val next = null.plus(t, "2026-09-26").plus(AccountTally(readHere = 3), "2026-09-27")
        assertEquals("2026-09-27", next.day)
        assertEquals(3, next.readHere)
        assertEquals(0, next.readSent)
    }

    @Test
    fun `a quiet day shows nothing`() {
        val quiet = null.plus(AccountTally(serverFeeds = 114, matched = 1575), "2026-09-26")
        assertFalse(quiet.sentAny || quiet.receivedAny || quiet.feedsChanged)
    }

    @Test
    fun `the day is the local date`() {
        assertTrue(Regex("\\d{4}-\\d{2}-\\d{2}").matches(com.saulhdev.feeder.manager.sync.greader.dayKey(0L)))
    }

    @Test
    fun `the screen shows today under the last sync whenever there was one today`() {
        val page = File("src/main/java/com/saulhdev/feeder/ui/pages/AccountPage.kt").readText()
        assertTrue(page.contains("today?.let { day ->"))
        assertFalse("shown with zeros too", page.contains("today?.takeIf"))
        val store = File("src/main/java/com/saulhdev/feeder/manager/sync/greader/AccountTally.kt").readText()
        assertTrue(store.contains("val totals = today(context, at).plus(tally, dayKey(at))"))
        assertTrue("yesterday's is not today's", store.contains("?.takeIf { it == dayKey(nowMs) } ?: return@runCatching null"))
    }
}

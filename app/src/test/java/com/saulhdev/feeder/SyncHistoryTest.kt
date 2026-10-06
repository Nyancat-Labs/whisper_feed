package com.saulhdev.feeder

import com.saulhdev.feeder.utils.ImageTrace
import com.saulhdev.feeder.utils.SyncEntry
import com.saulhdev.feeder.utils.SyncHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every sync says what started it.
 *
 * The morning's syncs at 06:41 and 08:07 looked identical to a scheduled one,
 * a panel one and a pull to refresh, so the one question the charging test
 * turned on - which of them ran off the charger - had to be inferred. The
 * worker now records each run with its origin, and every request has to name
 * one: the parameter has no default, which is how five callers nobody had
 * listed were found.
 */
class SyncHistoryTest {

    private fun entry(start: Long, origin: String = "scheduled") =
        SyncEntry(start, 0L, origin, "running", "plugged in, Wi-Fi")

    @Test
    fun `newest first, and never more than the limit`() {
        var list = emptyList<SyncEntry>()
        repeat(SyncHistory.MAX_ENTRIES + 5) { list = SyncHistory.start(list, entry(it.toLong() + 1)) }
        assertEquals(SyncHistory.MAX_ENTRIES, list.size)
        assertEquals("the latest is at the top", (SyncHistory.MAX_ENTRIES + 5).toLong(), list.first().start)
    }

    @Test
    fun `a run is finished in place`() {
        val list = SyncHistory.start(SyncHistory.start(emptyList(), entry(1)), entry(2, "panel"))
        val done = SyncHistory.finish(list, id = 1, end = 20, outcome = "stopped: came off the charger")
        val first = done.single { it.start == 1L }
        assertEquals(20L, first.end)
        assertEquals("stopped: came off the charger", first.outcome)
        assertEquals("the other run is untouched", "running", done.single { it.start == 2L }.outcome)
    }

    @Test
    fun `a run trimmed away is not invented when it finishes`() {
        val list = SyncHistory.start(emptyList(), entry(5))
        assertEquals(list, SyncHistory.finish(list, id = 99, end = 100, outcome = "ok"))
    }

    @Test
    fun `it survives being written down and read back`() {
        val list = listOf(
            SyncEntry(3, 9, "pull to refresh", "ok", "on battery, mobile data"),
            SyncEntry(1, 0, "scheduled", "running", "plugged in, Wi-Fi"),
        )
        assertEquals(list, SyncHistory.decode(SyncHistory.encode(list)))
        assertEquals(emptyList<SyncEntry>(), SyncHistory.decode(""))
        assertEquals("a damaged line is skipped, not fatal", 1, SyncHistory.decode("garbage\n" + SyncHistory.encode(list.take(1))).size)
    }

    @Test
    fun `every sync request names its origin`() {
        val main = File("src/main/java/com/saulhdev/feeder")
        val calls = main.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            Regex("""(?<!fun )requestFeedSync\(([^)]*)\)""").findAll(f.readText()).map { f.name to it.groupValues[1] }
        }.toList()
        assertTrue("there are callers", calls.size >= 7)
        calls.forEach { (file, args) -> assertTrue("$file: requestFeedSync($args)", args.contains("origin")) }

        val activity = File(main, "MainActivity.kt").readText()
        assertTrue("the schedule too", activity.contains("SyncLog.ORIGIN_KEY to SyncLog.ORIGIN_SCHEDULED"))
        val syncer = File(main, "manager/sync/FeedSyncer.kt").readText()
        assertTrue("and the panel", syncer.contains("origin: String = SyncLog.ORIGIN_PANEL,"))
        assertTrue("a stopped run records why", syncer.contains("stopReasonName(stopReason)"))
        assertTrue("where the worker is allowed to ask", syncer.contains("Build.VERSION.SDK_INT >= Build.VERSION_CODES.S"))
    }

    @Test
    fun `a port is not a status, and a redirect is`() {
        val timeout = ImageTrace.reasonOf(java.net.SocketTimeoutException("failed to connect to host:443"))
        assertTrue("named by what it is: $timeout", timeout.startsWith("SocketTimeoutException"))
        assertTrue("not a status: $timeout", !timeout.startsWith("http"))
        assertEquals("http 301", ImageTrace.reasonOf(RuntimeException("HTTP 301: Moved Permanently")))
        assertEquals("http 403", ImageTrace.reasonOf(RuntimeException("Unexpected code Response{code=403}")))
    }
}

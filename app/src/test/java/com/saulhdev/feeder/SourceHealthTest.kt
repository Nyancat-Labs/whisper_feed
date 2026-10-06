package com.saulhdev.feeder

import com.saulhdev.feeder.ui.components.SourceHealth
import com.saulhdev.feeder.ui.components.sourceHealth
import org.junit.Assert.assertEquals
import org.junit.Test

private const val DAY = 24L * 60 * 60 * 1000
private const val NOW = 1_800_000_000_000L
private const val STALE_SINCE = NOW - 3 * DAY

/**
 * A feed that has never worked is not the same as one that has stopped.
 *
 * `lastSync` used to default to the moment the row was created, so importing
 * an OPML stamped every feed in it — including the dead ones — with the time
 * of the import. Those feeds then looked freshly synced, and the "Not
 * updating" badge waits three days, so a source that could not be fetched at
 * all was invisible for three days and then blamed on the publisher.
 *
 * Six of twenty feeds on a test device were in that state, and the only
 * evidence was six rows sharing a timestamp to the millisecond in a
 * diagnostics report.
 */
class SourceHealthTest {

    @Test
    fun `a feed that has never synced says so straight away`() {
        // The case that was invisible. No waiting three days for it.
        assertEquals(
            SourceHealth.NeverUpdated,
            sourceHealth(lastSyncMs = 0L, isEnabled = true, staleSince = STALE_SINCE),
        )
    }

    @Test
    fun `a feed synced a moment ago is fine`() {
        assertEquals(
            SourceHealth.Fine,
            sourceHealth(lastSyncMs = NOW - 60_000, isEnabled = true, staleSince = STALE_SINCE),
        )
    }

    @Test
    fun `a feed that has stopped is reported as stopped, not as never`() {
        // It worked once; that is the publisher's end, not the address.
        assertEquals(
            SourceHealth.NotUpdating,
            sourceHealth(lastSyncMs = NOW - 5 * DAY, isEnabled = true, staleSince = STALE_SINCE),
        )
    }

    @Test
    fun `a quiet feed inside the window is not accused of anything`() {
        assertEquals(
            SourceHealth.Fine,
            sourceHealth(lastSyncMs = NOW - 2 * DAY, isEnabled = true, staleSince = STALE_SINCE),
        )
    }

    @Test
    fun `a disabled source says nothing at all`() {
        // It is not fetching because it was told not to. A red line against
        // every switched-off source would make the badge worthless.
        assertEquals(
            SourceHealth.Fine,
            sourceHealth(lastSyncMs = 0L, isEnabled = false, staleSince = STALE_SINCE),
        )
        assertEquals(
            SourceHealth.Fine,
            sourceHealth(lastSyncMs = NOW - 9 * DAY, isEnabled = false, staleSince = STALE_SINCE),
        )
    }

    @Test
    fun `a feed failing repeatedly is reported even though it synced today`() {
        // The reported gap. Ten feeds were failing, the app had the count for
        // every one of them, and Data sources said nothing was wrong — because
        // it asked only how long ago the last success was, and a feed failing
        // since breakfast succeeded this morning.
        assertEquals(
            SourceHealth.NotUpdating,
            sourceHealth(
                lastSyncMs = NOW - 60_000,
                isEnabled = true,
                staleSince = STALE_SINCE,
                consecutiveFailures = 5,
            ),
        )
    }

    @Test
    fun `one or two failures are still noise`() {
        // Servers have bad days. The same three the Broken feeds screen uses,
        // so the two screens cannot disagree about what counts as broken.
        for (failures in 0..2) {
            assertEquals(
                "$failures failures should not raise a badge",
                SourceHealth.Fine,
                sourceHealth(NOW - 60_000, isEnabled = true, staleSince = STALE_SINCE, consecutiveFailures = failures),
            )
        }
    }

    @Test
    fun `a disabled source is still silent however badly it was failing`() {
        assertEquals(
            SourceHealth.Fine,
            sourceHealth(NOW - 60_000, isEnabled = false, staleSince = STALE_SINCE, consecutiveFailures = 9),
        )
    }

    @Test
    fun `never updated outranks failing, being the more specific answer`() {
        assertEquals(
            SourceHealth.NeverUpdated,
            sourceHealth(lastSyncMs = 0L, isEnabled = true, staleSince = STALE_SINCE, consecutiveFailures = 9),
        )
    }

    @Test
    fun `an unset threshold accuses nobody`() {
        // One frame on a cold screen before "now" has been worked out.
        assertEquals(
            SourceHealth.Fine,
            sourceHealth(lastSyncMs = NOW - 9 * DAY, isEnabled = true, staleSince = 0L),
        )
    }

    @Test
    fun `never synced outranks an unset threshold`() {
        // It needs no comparison to a clock, so it is answerable immediately.
        assertEquals(
            SourceHealth.NeverUpdated,
            sourceHealth(lastSyncMs = 0L, isEnabled = true, staleSince = 0L),
        )
    }
}

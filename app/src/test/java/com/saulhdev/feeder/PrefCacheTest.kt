package com.saulhdev.feeder

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.saulhdev.feeder.data.content.BooleanPref
import com.saulhdev.feeder.data.content.PrefCache
import com.saulhdev.feeder.data.content.StringPref
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.Check
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The preference cache, which exists so that reading a setting is a field load
 * rather than a thread waiting on a coroutine dispatched to the IO pool.
 *
 * Worth testing at this level because the failure it prevents is invisible in
 * the normal case: fifty blocking reads are all fast while nothing else is
 * happening, and only become an ANR on the one occasion the IO pool is busy —
 * during a sync, which is exactly when somebody is looking at the screen.
 *
 * A real DataStore over a temporary file, not a fake. The behaviour under test
 * is what the cache does relative to the store, so a fake store would be
 * testing the fake.
 */
class PrefCacheTest {

    private fun store(): Pair<DataStore<Preferences>, File> {
        val file = File.createTempFile("prefs", ".preferences_pb").also { it.delete() }
        return PreferenceDataStoreFactory.create(produceFile = { file }) to file
    }

    private fun boolPref(ds: DataStore<Preferences>, key: String, default: Boolean) =
        BooleanPref(
            titleId = 0,
            icon = Phosphor.Check,
            key = booleanPreferencesKey(key),
            dataStore = ds,
            defaultValue = default,
        )

    private fun stringPref(ds: DataStore<Preferences>, key: String, default: String) =
        StringPref(
            titleId = 0,
            icon = Phosphor.Check,
            key = stringPreferencesKey(key),
            dataStore = ds,
            defaultValue = default,
        )

    @Test
    fun `a warm cache answers with the stored value`() {
        val (ds, _) = store()
        runBlocking { ds.edit { it[booleanPreferencesKey("pure_black")] = true } }
        PrefCache.start(ds)

        val pref = boolPref(ds, "pure_black", default = false)
        assertEquals(true, pref.peek())
        assertEquals(true, pref.peekOrDefault())
        assertEquals(true, pref.getValue())
    }

    @Test
    fun `an unset preference reads as its default, not as absent`() {
        // The distinction that matters: peek returns null only when the cache
        // is cold, never because a key has not been written. A preference
        // nobody has touched has a value — the one it was declared with.
        val (ds, _) = store()
        PrefCache.start(ds)

        val pref = stringPref(ds, "never_written", default = "mosaic")
        assertEquals("mosaic", pref.peek())
        assertEquals("mosaic", pref.peekOrDefault())
    }

    @Test
    fun `a write is visible to the next read`() {
        val (ds, _) = store()
        PrefCache.start(ds)
        val pref = stringPref(ds, "layout", default = "cards")
        assertEquals("cards", pref.getValue())

        pref.setValue("mosaic")

        // setValue waits for the write, so the store is authoritative
        // immediately — and the cache is too, because the write records what
        // it committed rather than waiting for the collector to be scheduled.
        assertEquals("mosaic", runBlocking { pref.get().first() })
        assertEquals("mosaic", pref.getValue())
    }

    /**
     * Read-after-write, through the cache alone.
     *
     * This is what the test above was really asserting and could not, because
     * getValue falls back to the store when the cache has no answer — so a
     * stale cache was hidden by the fallback everywhere except the one case
     * where the cache held a *previous* value. peek is the cache with no
     * fallback, which is where the race was visible, and where it is not any
     * more.
     */
    @Test
    fun `a write is visible through the cache with no fallback`() {
        val (ds, _) = store()
        PrefCache.start(ds)
        val pref = stringPref(ds, "layout", default = "cards")
        assertEquals("cards", pref.getValue())

        repeat(20) { n ->
            val value = if (n % 2 == 0) "mosaic" else "list"
            pref.setValue(value)
            assertEquals(value, pref.peek())
        }
    }

    @Test
    fun `a cold cache falls back rather than inventing a default`() {
        // getValue may be called before start() — a worker on a cold process,
        // say. It must read the file rather than answer with the default,
        // because a sync that runs on the wrong network setting is worse than
        // one that waits a millisecond.
        val (ds, _) = store()
        runBlocking { ds.edit { it[booleanPreferencesKey("wifi_only")] = true } }

        val pref = boolPref(ds, "wifi_only", default = false)
        // Not asserting peek() is null here: the cache is a process-wide
        // object and another test may have warmed it. What must hold either
        // way is that the answer is the stored one.
        assertEquals(true, pref.getValue())
    }

    @Test
    fun `peekOrDefault never blocks and never throws`() {
        // The property the composition helper depends on. Called on whatever
        // thread Compose is on, it must always return something.
        val (ds, _) = store()
        val pref = stringPref(ds, "font", default = "sans")
        assertTrue(pref.peekOrDefault().isNotEmpty())
    }

    @Test
    fun `two preferences over one store do not see each other's values`() {
        val (ds, _) = store()
        runBlocking {
            ds.edit {
                it[stringPreferencesKey("a")] = "one"
                it[stringPreferencesKey("b")] = "two"
            }
        }
        PrefCache.start(ds)
        assertEquals("one", stringPref(ds, "a", "x").peek())
        assertEquals("two", stringPref(ds, "b", "x").peek())
        assertNull(
            "a key in neither the store nor the cache has no reason to appear",
            stringPref(ds, "c", "x").peek()?.takeIf { it != "x" },
        )
    }
}

/*
 * This file is part of Neo Feed
 * Copyright (c) 2022   Neo Feed Team
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
package com.saulhdev.feeder.data.content

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.saulhdev.feeder.ui.navigation.NavRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class StringPref(
    @StringRes titleId: Int,
    @StringRes summaryId: Int = -1,
    val icon: ImageVector,
    val key: Preferences.Key<String>,
    val dataStore: DataStore<Preferences>,
    val defaultValue: String = "",
    val onClick: (() -> Unit)? = null,
    val route: NavRoute? = null,
) : PrefDelegate<String>(titleId, summaryId, dataStore, key, defaultValue)

class StringSelectionPref(
    @StringRes titleId: Int,
    @StringRes summaryId: Int = -1,
    val icon: ImageVector,
    val key: Preferences.Key<String>,
    val dataStore: DataStore<Preferences>,
    val defaultValue: String = "",
    val entries: Map<String, String>
) : PrefDelegate<String>(titleId, summaryId, dataStore, key, defaultValue)

class StringSetPref(
    @StringRes titleId: Int,
    @StringRes summaryId: Int = -1,
    val icon: ImageVector,
    val key: Preferences.Key<Set<String>>,
    val dataStore: DataStore<Preferences>,
    val defaultValue: Set<String> = emptySet(),
    val route: NavRoute? = null,
    val onClick: (() -> Unit)? = null,
) : PrefDelegate<Set<String>>(titleId, summaryId, dataStore, key, defaultValue)

/**
 * A stored number with no settings row of its own.
 *
 * [titleId] and [icon] are required by [PrefDelegate] for preferences the
 * settings screen renders; this is for values the app keeps but never shows,
 * so they default to something harmless rather than making every caller
 * invent one.
 */
class LongPref(
    @StringRes titleId: Int = -1,
    @StringRes summaryId: Int = -1,
    val key: Preferences.Key<Long>,
    val dataStore: DataStore<Preferences>,
    val defaultValue: Long = 0L,
) : PrefDelegate<Long>(titleId, summaryId, dataStore, key, defaultValue)

class BooleanPref(
    @StringRes titleId: Int,
    @StringRes summaryId: Int = -1,
    val icon: ImageVector,
    val key: Preferences.Key<Boolean>,
    val dataStore: DataStore<Preferences>,
    val defaultValue: Boolean = false,
) : PrefDelegate<Boolean>(titleId, summaryId, dataStore, key, defaultValue)

class FloatPref(
    @StringRes titleId: Int,
    @StringRes summaryId: Int = -1,
    val icon: ImageVector,
    val key: Preferences.Key<Float>,
    val dataStore: DataStore<Preferences>,
    val defaultValue: Float = 0f,
    val minValue: Float,
    val maxValue: Float,
    val steps: Int,
    val specialOutputs: ((Float) -> String) = Float::toString,
) : PrefDelegate<Float>(titleId, summaryId, dataStore, key, defaultValue)

/**
 * Every preference's current value, in memory, so reading one costs nothing.
 *
 * `getValue()` used to be `runBlocking(Dispatchers.IO) { flow.first() }` at
 * every one of its fifty-odd call sites. That is not fifty disk reads —
 * DataStore keeps the loaded file in memory and serves later collections from
 * it — but it is fifty occasions on which a thread stops dead until a
 * coroutine dispatched to the IO pool comes back. On the main thread that is
 * the shape of an ANR: not slow in the normal case, and arbitrarily slow on
 * the one occasion the IO pool is busy, which for this app is during a sync,
 * which is exactly when somebody is looking at the screen.
 *
 * One collector keeps this snapshot current. Reads take the volatile
 * reference, so they are a field load and a map lookup, on any thread, with no
 * dispatch and nothing to wait for.
 *
 * The blocking path stays for the window before the first value arrives — see
 * [prime], which closes that window at startup — because returning a default
 * that is not the reader's setting is worse than a pause nobody will see.
 */
internal object PrefCache {

    /**
     * Keyed by store rather than held as one value.
     *
     * The app has exactly one DataStore, so a single snapshot would work and
     * was what this held first. It was wrong anyway: global mutable state
     * belonging to no particular store is state that a second store silently
     * corrupts, and the first test written against it did exactly that —
     * warmed the cache from one store and read it back through another.
     *
     * Keying it costs a hash lookup and makes the thing testable, which is the
     * same property as being able to reason about it.
     */
    private val snapshots = ConcurrentHashMap<DataStore<Preferences>, Preferences>()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** The last known state of that file, or null before its first read. */
    fun snapshot(dataStore: DataStore<Preferences>): Preferences? = snapshots[dataStore]

    /**
     * Fills the cache once, blocking, and then keeps it filled.
     *
     * Called from the preferences singleton's constructor, which runs before
     * any screen is composed. One read at startup in place of every read after
     * it — and it is the read that would otherwise have happened during the
     * first composition, where the frame budget is already spent.
     */
    fun start(dataStore: DataStore<Preferences>) {
        // containsKey explicitly: `in` on a ConcurrentHashMap resolves to
        // containsValue, which would have primed on every call.
        if (!snapshots.containsKey(dataStore)) {
            runCatching {
                runBlocking { snapshots[dataStore] = dataStore.data.first() }
            }
        }
        scope.launch { dataStore.data.collect { snapshots[dataStore] = it } }
    }

    /**
     * Takes the state a write just committed, without waiting to be told.
     *
     * The collector below is how the cache normally learns, and it learns
     * whenever the coroutine it runs on next gets scheduled — which is not
     * before the line after a write. So `setValue(x)` followed immediately by
     * `getValue()` could return the old value: the store was authoritative
     * and the cache, which is what peek reads, had not caught up.
     *
     * That is a race a test found only because adding an unrelated file
     * changed the order the suite ran in, and it is the kind that in the app
     * shows as a setting that did not take until the screen was reopened.
     * Recording it here closes the window; the collector still runs, for
     * writes made anywhere else.
     */
    fun record(dataStore: DataStore<Preferences>, preferences: Preferences) {
        snapshots[dataStore] = preferences
    }
}

abstract class PrefDelegate<T>(
    @StringRes var titleId: Int,
    @StringRes var summaryId: Int = -1,
    private val dataStore: DataStore<Preferences>,
    private val key: Preferences.Key<T>,
    private val defaultValue: T
) : ReadWriteProperty<Any?, T> {

    /**
     * The value without waiting for anything, or null if it is not known yet.
     *
     * For composition, where blocking is never acceptable and a wrong first
     * frame is: a caller with nothing to show falls back to the flow, which
     * will deliver the real value on the frame after next.
     */
    fun peek(): T? = PrefCache.snapshot(dataStore)?.let { it[key] ?: defaultValue }

    /**
     * The value if it is known, the declared default if it is not.
     *
     * For composition, where waiting is not on offer. Reachable as the default
     * only before the cache is warm, which is before the app has drawn
     * anything — and one frame of a default beats a stalled first frame. The
     * flow replaces it on the frame after.
     */
    fun peekOrDefault(): T = peek() ?: defaultValue

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = getValue()

    fun getValue(): T {
        peek()?.let { return it }
        // Only before the cache is warm. No Dispatchers.IO: DataStore does its
        // own file work on its own scope, so dispatching here only added a
        // second thread to wait for, and made a main-thread read depend on the
        // IO pool having a free thread.
        return runBlocking { get().firstOrNull() ?: defaultValue }
    }

    override operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) =
        setValue(value)

    /**
     * Writes and waits for the write to land.
     *
     * A real disk write, so never call it from the main thread — use [set].
     * Kept blocking for workers and for the places that must know the value is
     * stored before they go on.
     */
    fun setValue(value: T) {
        runBlocking { write(value) }
    }

    /**
     * Writes without waiting.
     *
     * What a tap should call. The cache updates from the collector when the
     * write lands, so anything reading this preference sees the new value
     * without either of them blocking.
     */
    fun set(value: T) {
        writeScope.launch { write(value) }
    }

    fun get(): Flow<T> {
        return dataStore.data.map { it[key] ?: defaultValue }
    }

    private suspend fun write(value: T) {
        // The result of the edit, not a second read: DataStore hands back the
        // Preferences it just committed, so this is the state of the file
        // rather than a guess at it.
        val written = dataStore.edit { it[key] = value }
        PrefCache.record(dataStore, written)
    }

    private companion object {
        /**
         * Outlives any screen on purpose: a preference written as a screen
         * closes must still be written.
         */
        val writeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }
}

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
package com.saulhdev.feeder.data.content

import com.saulhdev.feeder.manager.sync.greader.AccountTallyStore
import com.saulhdev.feeder.manager.sync.greader.GoogleReaderState
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.File

/**
 * The one sync account, if there is one.
 *
 * **One at a time, deliberately.** Multiple accounts would mean an `accountId`
 * on every feed and article, a migration, and a decision about what the feed
 * shows when two accounts disagree — for a case almost nobody has. One account
 * means the local database simply *is* that account's, and every sync is a
 * reconciliation between it and the server. If two ever turn out to be wanted,
 * that is a schema change made deliberately rather than one carried from the
 * start.
 *
 * Kept out of DataStore, which every other preference uses, because this holds
 * a credential. Each value is sealed by [AccountCipher] with a key of its own
 * in the Android Keystore before it is written to a plain preferences file, so
 * the file alone, from a backup or an unlocked bootloader, gives nothing away.
 * The backup rules leave the file out anyway: the key never travels, and a
 * restored file could not be opened. The rest of the app's settings are not
 * secrets and do not need this.
 *
 * Until September 2026 the account lived in an EncryptedSharedPreferences file,
 * from a library Google has since deprecated. It is moved out once, at the
 * first start after the update, and the old file and its key are deleted.
 *
 * The token rather than the password is stored where the server allows it —
 * ClientLogin returns one that can be revoked server-side, which a password
 * cannot.
 */
class SyncAccount internal constructor(
    context: Context,
    private val cipher: AccountCipher,
    private val legacy: LegacyAccountStore,
) {

    constructor(context: Context) : this(context, keystoreCipher(), EncryptedLegacyStore(context, LEGACY_FILE))

    private val appContext = context.applicationContext

    /**
     * Whether the store could not be read, or could not be written, and the
     * account was lost. The account screen says so, so a reader signed out
     * this way knows why.
     */
    var wasReset: Boolean = false
        private set

    /**
     * The sealed values, or, once the Keystore has refused to seal, the plain
     * values in memory for as long as the app runs: never written down
     * unsealed.
     */
    @Volatile
    private var prefs: SharedPreferences = appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    @Volatile
    private var sealing = true

    /**
     * What has been opened already. A Keystore operation takes milliseconds,
     * and the token is read for every request of a sync.
     */
    private val opened = java.util.concurrent.ConcurrentHashMap<String, String>()

    init {
        if (prefs.getBoolean(KEY_UNSEALABLE, false)) {
            // Last run kept the account in memory; this one starts without it.
            wasReset = true
            prefs.edit().remove(KEY_UNSEALABLE).apply()
        }
        moveFromLegacy()
        checkOpens()
    }

    /**
     * Moves the account out of the old store, once.
     *
     * An old store that will not open is deleted, as it was before: a Keystore
     * that lost its key, known on some phones after a system update. One that
     * opens but whose values cannot be sealed again is kept for the next start,
     * and the account is held in memory for this one.
     */
    private fun moveFromLegacy() {
        if (!File(appContext.filesDir.parentFile, "shared_prefs/$LEGACY_FILE.xml").exists()) return
        if (prefs.getString(KEY_TOKEN, null) == null) {
            val old = try {
                val store = legacy.open()
                Moved(
                    SEALED_KEYS.associateWith { store.getString(it, "").orEmpty() },
                    store.getLong(KEY_LAST_SYNC, 0L),
                )
            } catch (e: Exception) {
                Log.w(TAG, "The old account store could not be read; starting signed out", e)
                wasReset = true
                null
            }
            if (old != null && old.values[KEY_TOKEN].orEmpty().isNotEmpty()) {
                try {
                    val sealed = old.values.mapValues { (name, value) -> cipher.seal(value, name) }
                    val written = prefs.edit()
                        .apply { sealed.forEach { (name, value) -> putString(name, value) } }
                        .putLong(KEY_LAST_SYNC, old.lastSync)
                        .commit()
                    check(written) { "The new account store was not written" }
                } catch (e: Exception) {
                    Log.w(TAG, "The account could not be sealed again; keeping the old store for now", e)
                    toMemory()
                    prefs.edit()
                        .apply { old.values.forEach { (name, value) -> putString(name, value) } }
                        .putLong(KEY_LAST_SYNC, old.lastSync)
                        .apply()
                    return
                }
            }
        }
        appContext.deleteSharedPreferences(LEGACY_FILE)
        runCatching { legacy.forgetKey() }
            .onFailure { Log.w(TAG, "The old account key could not be deleted", it) }
    }

    private class Moved(val values: Map<String, String>, val lastSync: Long)

    /**
     * Opens every stored value once, at start, so a key the Keystore has lost
     * shows now, as a reset the account screen explains, rather than as a
     * sync that quietly finds itself signed out.
     */
    private fun checkOpens() {
        if (!sealing) return
        for (name in SEALED_KEYS) {
            val stored = prefs.getString(name, null) ?: continue
            try {
                cipher.open(stored, name)
            } catch (e: Exception) {
                reset(e)
                return
            }
        }
    }

    private fun reset(e: Exception) {
        Log.w(TAG, "The account store could not be read; starting it again, signed out", e)
        wasReset = true
        opened.clear()
        prefs.edit().clear().apply()
        runCatching { cipher.forget() }
    }

    private fun toMemory() {
        runCatching { cipher.forget() }
        // Plain, and only a flag: the next start says why it is signed out.
        prefs.edit().clear().putBoolean(KEY_UNSEALABLE, true).apply()
        prefs = MemoryPreferences()
        sealing = false
    }

    private fun read(name: String): String {
        opened[name]?.let { return it }
        val stored = prefs.getString(name, null) ?: return ""
        if (!sealing) return stored
        return try {
            cipher.open(stored, name).also { opened[name] = it }
        } catch (e: Exception) {
            reset(e)
            ""
        }
    }

    private fun write(values: Map<String, String>) {
        opened.clear()
        if (sealing) {
            try {
                val sealed = values.mapValues { (name, value) -> cipher.seal(value, name) }
                prefs.edit().apply { sealed.forEach { (name, value) -> putString(name, value) } }.apply()
                return
            } catch (e: Exception) {
                Log.w(TAG, "The Keystore will not seal the account; keeping it in memory", e)
                toMemory()
            }
        }
        prefs.edit().apply { values.forEach { (name, value) -> putString(name, value) } }.apply()
    }

    /** Where the account lives — a FreshRSS instance, Miniflux, Inoreader. */
    val serverUrl: String get() = read(KEY_SERVER)

    val username: String get() = read(KEY_USER)

    /** The ClientLogin token. Revocable server-side, unlike a password. */
    val authToken: String get() = read(KEY_TOKEN)

    /** When the last successful sync finished, for the status line. */
    var lastSync: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    val isSignedIn: Boolean get() = authToken.isNotEmpty() && serverUrl.isNotEmpty()

    fun signIn(server: String, user: String, token: String) {
        write(mapOf(KEY_SERVER to server.trimEnd('/'), KEY_USER to user, KEY_TOKEN to token))
        lastSync = 0L
        // Another account's memory of which feeds it had would decide what
        // happens to this one's. Starts empty, which is a first sync.
        GoogleReaderState.clear(appContext)
        AccountTallyStore.clear(appContext)
    }

    /**
     * Forgets the account. Subscriptions and articles stay.
     *
     * Signing out is not "throw away my reading" — the local database is the
     * source of truth and works perfectly well without a server, which is the
     * whole point of the local-first arrangement. Someone who wants the
     * articles gone can remove the sources.
     */
    fun signOut() {
        opened.clear()
        prefs.edit().clear().apply()
        GoogleReaderState.clear(appContext)
        AccountTallyStore.clear(appContext)
    }

    internal companion object {
        private const val TAG = "SyncAccount"

        /** Named in the backup rules, which leave it out of every route. */
        const val FILE = "whisper_account_sealed"

        /** The old EncryptedSharedPreferences file, also named in the backup rules. */
        const val LEGACY_FILE = "whisper_account"

        const val KEY_SERVER = "server_url"
        const val KEY_USER = "username"
        const val KEY_TOKEN = "auth_token"
        const val KEY_LAST_SYNC = "last_sync"
        private const val KEY_UNSEALABLE = "keystore_refused"

        private val SEALED_KEYS = listOf(KEY_SERVER, KEY_USER, KEY_TOKEN)
    }
}

/**
 * Preferences that last as long as the app runs, and no longer: the account's
 * last resort when the Keystore will not seal anything at all.
 */
private class MemoryPreferences : SharedPreferences {
    private val values = HashMap<String, Any>()

    override fun getAll(): Map<String, *> = synchronized(values) { HashMap(values) }
    override fun getString(key: String?, defValue: String?): String? = get(key) as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        get(key) as? MutableSet<String> ?: defValues

    override fun getInt(key: String?, defValue: Int): Int = get(key) as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = get(key) as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = get(key) as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = get(key) as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = get(key) != null
    override fun edit(): SharedPreferences.Editor = Changes()
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private fun get(key: String?): Any? = synchronized(values) { values[key] }

    private inner class Changes : SharedPreferences.Editor {
        private val puts = HashMap<String, Any?>()
        private var clearFirst = false

        private fun put(key: String?, value: Any?): SharedPreferences.Editor {
            if (key != null) puts[key] = value
            return this
        }

        override fun putString(key: String?, value: String?) = put(key, value)
        override fun putStringSet(key: String?, values: MutableSet<String>?) = put(key, values?.toMutableSet())
        override fun putInt(key: String?, value: Int) = put(key, value)
        override fun putLong(key: String?, value: Long) = put(key, value)
        override fun putFloat(key: String?, value: Float) = put(key, value)
        override fun putBoolean(key: String?, value: Boolean) = put(key, value)
        override fun remove(key: String?) = put(key, null)

        override fun clear(): SharedPreferences.Editor {
            clearFirst = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            synchronized(values) {
                if (clearFirst) values.clear()
                puts.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
            }
        }
    }
}

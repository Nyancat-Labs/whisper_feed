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
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

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
 * a credential. `EncryptedSharedPreferences` puts it behind a key in the
 * Android keystore, so it is not readable from a backup or an unlocked
 * bootloader in the way a plain preferences file is. The rest of the app's
 * settings are not secrets and do not need this.
 *
 * The token rather than the password is stored where the server allows it —
 * ClientLogin returns one that can be revoked server-side, which a password
 * cannot.
 */
class SyncAccount(context: Context) {

    private val appContext = context.applicationContext

    /**
     * Whether the store could not be read and was started again, empty. The
     * account screen says so, so a reader signed out this way knows why.
     */
    var wasReset: Boolean = false
        private set

    private val prefs: SharedPreferences = openStore()

    /**
     * The encrypted store, and what to do when the Keystore will not open it.
     *
     * Backups leave this file out, so a restore cannot cause that; but a
     * Keystore that loses or refuses its key, known on some phones after a
     * system update, threw here with nothing around it, and the app stopped
     * at start, every start, until its data was cleared. Now the file and its
     * key are deleted and the store starts again, signed out. Should even a
     * new one fail, the account is kept in memory for as long as the app
     * runs: never written down unencrypted.
     */
    private fun openStore(): SharedPreferences = try {
        encryptedStore()
    } catch (e: Exception) {
        Log.w(TAG, "The account store could not be read; starting it again, signed out", e)
        wasReset = true
        runCatching { appContext.deleteSharedPreferences(FILE) }
        runCatching {
            java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                .deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        }
        try {
            encryptedStore()
        } catch (again: Exception) {
            Log.w(TAG, "The Keystore will not open a new store either; keeping the account in memory", again)
            MemoryPreferences()
        }
    }

    private fun encryptedStore(): SharedPreferences = EncryptedSharedPreferences.create(
        appContext,
        FILE,
        MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /** Where the account lives — a FreshRSS instance, Miniflux, Inoreader. */
    var serverUrl: String
        get() = prefs.getString(KEY_SERVER, "").orEmpty()
        private set(value) = prefs.edit().putString(KEY_SERVER, value).apply()

    var username: String
        get() = prefs.getString(KEY_USER, "").orEmpty()
        private set(value) = prefs.edit().putString(KEY_USER, value).apply()

    /** The ClientLogin token. Revocable server-side, unlike a password. */
    var authToken: String
        get() = prefs.getString(KEY_TOKEN, "").orEmpty()
        private set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    /** When the last successful sync finished, for the status line. */
    var lastSync: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    val isSignedIn: Boolean get() = authToken.isNotEmpty() && serverUrl.isNotEmpty()

    fun signIn(server: String, user: String, token: String) {
        serverUrl = server.trimEnd('/')
        username = user
        authToken = token
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
        prefs.edit().clear().apply()
        GoogleReaderState.clear(appContext)
        AccountTallyStore.clear(appContext)
    }

    private companion object {
        const val TAG = "SyncAccount"

        /** Named in the backup rules, which leave it out of every route. */
        const val FILE = "whisper_account"

        const val KEY_SERVER = "server_url"
        const val KEY_USER = "username"
        const val KEY_TOKEN = "auth_token"
        const val KEY_LAST_SYNC = "last_sync"
    }
}

/**
 * Preferences that last as long as the app runs, and no longer: the account's
 * last resort when the Keystore will not open an encrypted store at all.
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

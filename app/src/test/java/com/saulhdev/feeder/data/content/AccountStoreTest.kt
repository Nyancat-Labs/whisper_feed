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

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * The account store: sealed values in a plain file, the move out of the old
 * EncryptedSharedPreferences file, and a Keystore that loses its key or will
 * not work at all, none of which may stop the app.
 *
 * Robolectric has no Android Keystore, so the cipher here is the app's own
 * [GcmCipher] with a key made in software. The default constructor, with the
 * real Keystore, is tested too: on Robolectric it is the worst phone there is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AccountStoreTest {

    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    /** A cipher over one software key, which [forget] replaces, as the Keystore would. */
    private class SoftwareCipher(start: SecretKey, fresh: () -> SecretKey) : AccountCipher {
        var key = start
        var forgotten = 0
        private val inner = GcmCipher({ key }, { key = fresh(); forgotten++ })
        override fun seal(plain: String, name: String) = inner.seal(plain, name)
        override fun open(sealed: String, name: String) = inner.open(sealed, name)
        override fun forget() = inner.forget()
    }

    private fun cipher() = SoftwareCipher(newKey(), ::newKey)

    private object Refusing : AccountCipher {
        override fun seal(plain: String, name: String): String = throw java.security.KeyStoreException("refused")
        override fun open(sealed: String, name: String): String = throw java.security.KeyStoreException("refused")
        override fun forget() = Unit
    }

    /** The old store, as a plain file in its place, since only its values matter here. */
    private inner class OldStore(private val opens: Boolean = true) : LegacyAccountStore {
        var keyForgotten = 0
        override fun open(): SharedPreferences {
            if (!opens) throw java.security.GeneralSecurityException("the old key is gone")
            return context.getSharedPreferences(SyncAccount.LEGACY_FILE, Context.MODE_PRIVATE)
        }
        override fun forgetKey() {
            keyForgotten++
        }
    }

    private fun writeOldStore() {
        context.getSharedPreferences(SyncAccount.LEGACY_FILE, Context.MODE_PRIVATE).edit()
            .putString(SyncAccount.KEY_SERVER, "https://rss.example.org/api/greader.php")
            .putString(SyncAccount.KEY_USER, "reader")
            .putString(SyncAccount.KEY_TOKEN, "old-secret-token")
            .putLong(SyncAccount.KEY_LAST_SYNC, 1_800_000_000_000L)
            .commit()
    }

    private val prefsDir get() = context.filesDir.resolveSibling("shared_prefs")

    private fun oldFile() = File(prefsDir, "${SyncAccount.LEGACY_FILE}.xml")

    private fun filesHolding(vararg secrets: String): List<String> =
        prefsDir.listFiles().orEmpty()
            .filter { file -> secrets.any { file.readText().contains(it) } }
            .map { it.name }

    // --- the store ---------------------------------------------------------

    @Test
    fun `a first start is signed out, with nothing to explain`() {
        val account = SyncAccount(context, cipher(), OldStore())
        assertFalse(account.isSignedIn)
        assertFalse(account.wasReset)
    }

    @Test
    fun `an account signed in is there at the next start`() {
        val cipher = cipher()
        SyncAccount(context, cipher, OldStore()).apply {
            signIn("https://rss.example.org/api/greader.php/", "reader", "token")
            lastSync = 42L
        }
        val next = SyncAccount(context, cipher, OldStore())
        assertTrue(next.isSignedIn)
        assertEquals("https://rss.example.org/api/greader.php", next.serverUrl)
        assertEquals("reader", next.username)
        assertEquals("token", next.authToken)
        assertEquals(42L, next.lastSync)
        next.signOut()
        assertFalse(SyncAccount(context, cipher, OldStore()).isSignedIn)
    }

    @Test
    fun `nothing is written down unsealed`() {
        SyncAccount(context, cipher(), OldStore())
            .signIn("https://rss.private.example", "somebody", "secret-token")
        val written = filesHolding("secret-token", "rss.private.example", "somebody")
        assertEquals(written.toString(), 0, written.size)
        assertTrue(File(prefsDir, "${SyncAccount.FILE}.xml").isFile)
    }

    @Test
    fun `a value sealed under one name does not open under another`() {
        val cipher = cipher()
        val sealed = cipher.seal("token", SyncAccount.KEY_TOKEN)
        assertEquals("token", cipher.open(sealed, SyncAccount.KEY_TOKEN))
        assertThrows(Exception::class.java) { cipher.open(sealed, SyncAccount.KEY_USER) }
        // And the same value sealed twice is two different strings.
        assertNotEquals(sealed, cipher.seal("token", SyncAccount.KEY_TOKEN))
    }

    @Test
    fun `a key the Keystore lost starts the store again, signed out, and says why`() {
        SyncAccount(context, cipher(), OldStore()).signIn("https://rss.example.org", "reader", "token")
        // A different key, as after the system update that loses one.
        val other = cipher()
        val account = SyncAccount(context, other, OldStore())
        assertFalse(account.isSignedIn)
        assertTrue(account.wasReset)
        assertEquals(1, other.forgotten)
        // And the new store works.
        account.signIn("https://rss.example.org", "reader", "token")
        assertTrue(SyncAccount(context, other, OldStore()).isSignedIn)
    }

    @Test
    fun `a Keystore that will not seal keeps the account in memory, and the next start says why`() {
        val account = SyncAccount(context, Refusing, OldStore())
        account.signIn("https://rss.example.org", "reader", "secret-token")
        assertTrue(account.isSignedIn)
        assertEquals("secret-token", account.authToken)
        assertEquals(0, filesHolding("secret-token", "rss.example.org").size)

        val next = SyncAccount(context, Refusing, OldStore())
        assertFalse(next.isSignedIn)
        assertTrue(next.wasReset)
        // Said once, not at every start after.
        assertFalse(SyncAccount(context, Refusing, OldStore()).wasReset)
    }

    @Test
    fun `the app starts with the real Keystore missing altogether`() {
        val account = SyncAccount(context)
        assertFalse(account.isSignedIn)
        account.signIn("https://rss.example.org", "reader", "secret-token")
        assertTrue(account.isSignedIn)
        assertEquals(0, filesHolding("secret-token").size)
    }

    // --- the move from the old store ---------------------------------------

    @Test
    fun `the old store's account moves over, and the old file and key go`() {
        writeOldStore()
        val old = OldStore()
        val cipher = cipher()
        val account = SyncAccount(context, cipher, old)
        assertTrue(account.isSignedIn)
        assertFalse(account.wasReset)
        assertEquals("https://rss.example.org/api/greader.php", account.serverUrl)
        assertEquals("reader", account.username)
        assertEquals("old-secret-token", account.authToken)
        assertEquals(1_800_000_000_000L, account.lastSync)
        assertFalse(oldFile().exists())
        assertEquals(1, old.keyForgotten)
        assertEquals(0, filesHolding("old-secret-token").size)
        // Once only.
        assertTrue(SyncAccount(context, cipher, old).isSignedIn)
        assertEquals(1, old.keyForgotten)
    }

    @Test
    fun `an old store that will not open is deleted, and the reader is told`() {
        writeOldStore()
        val old = OldStore(opens = false)
        val account = SyncAccount(context, cipher(), old)
        assertFalse(account.isSignedIn)
        assertTrue(account.wasReset)
        assertFalse(oldFile().exists())
        assertEquals(1, old.keyForgotten)
    }

    @Test
    fun `an old store that cannot be sealed again is kept for the next start`() {
        writeOldStore()
        val refused = SyncAccount(context, Refusing, OldStore())
        // Signed in for this run, from memory.
        assertTrue(refused.isSignedIn)
        assertEquals("old-secret-token", refused.authToken)
        assertTrue(oldFile().exists())

        val old = OldStore()
        val next = SyncAccount(context, cipher(), old)
        assertTrue(next.isSignedIn)
        assertEquals("old-secret-token", next.authToken)
        assertFalse(oldFile().exists())
        assertEquals(1, old.keyForgotten)
    }

    @Test
    fun `an old store signed out is simply deleted`() {
        context.getSharedPreferences(SyncAccount.LEGACY_FILE, Context.MODE_PRIVATE).edit()
            .putLong(SyncAccount.KEY_LAST_SYNC, 5L).commit()
        val old = OldStore()
        val account = SyncAccount(context, cipher(), old)
        assertFalse(account.isSignedIn)
        assertFalse(account.wasReset)
        assertFalse(oldFile().exists())
        assertEquals(1, old.keyForgotten)
    }
}

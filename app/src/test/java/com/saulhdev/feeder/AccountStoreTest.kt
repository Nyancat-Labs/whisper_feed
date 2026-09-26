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

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.saulhdev.feeder.data.content.SyncAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A Keystore that will not open the account store no longer stops the app.
 *
 * Robolectric has no Android Keystore at all, which makes it the worst phone
 * there is for this: both the store and a new one fail to open. That used to
 * throw from the constructor, at start.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AccountStoreTest {

    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `the app starts signed out, and says why`() {
        val account = SyncAccount(context)
        assertFalse(account.isSignedIn)
        assertTrue(account.wasReset)
    }

    @Test
    fun `signing in still works, for as long as the app runs`() {
        val account = SyncAccount(context)
        account.signIn("https://rss.example.org/api/greader.php/", "reader", "token")
        assertTrue(account.isSignedIn)
        assertEquals("https://rss.example.org/api/greader.php", account.serverUrl)
        account.lastSync = 42L
        assertEquals(42L, account.lastSync)
        account.signOut()
        assertFalse(account.isSignedIn)
        assertEquals(0L, account.lastSync)
    }

    @Test
    fun `nothing is written down unencrypted in its place`() {
        SyncAccount(context).signIn("https://rss.example.org", "reader", "secret-token")
        val prefsDir = context.filesDir.resolveSibling("shared_prefs")
        val written = prefsDir.listFiles().orEmpty().filter { it.readText().contains("secret-token") }
        assertEquals(written.map { it.name }.toString(), 0, written.size)
    }
}

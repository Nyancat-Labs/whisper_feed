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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 4 October: a password manager put the username into the server address,
 * the first text field on the screen, and the password nowhere useful.
 * Android has no autofill type for a server address, so the address now has
 * a step of its own and is not on screen when the login is filled.
 */
class TwoStepSignInTest {

    private val page = File("src/main/java/com/saulhdev/feeder/ui/pages/AccountPage.kt").readText()

    @Test
    fun `the address and the login are never on screen together`() {
        val form = page.substringAfter("if (!addressDone) {")
        val addressStep = form.substringBefore("} else {")
        val loginStep = form.substringAfter("} else {")
        assertTrue(addressStep.contains("value = server,"))
        assertTrue("no login fields beside the address", !addressStep.contains("value = username,"))
        assertTrue("no login fields beside the address", !addressStep.contains("value = password,"))
        assertTrue(loginStep.contains("value = username,"))
        assertTrue(loginStep.contains("value = password,"))
        assertTrue("no address field beside the login", !loginStep.substringBefore("viewModel.signIn(").contains("value = server,"))
    }

    @Test
    fun `the login fields are named for autofill`() {
        assertEquals(1, Regex("contentType = ContentType.Username").findAll(page).count())
        assertEquals(1, Regex("contentType = ContentType.Password").findAll(page).count())
    }

    @Test
    fun `moving on corrects the address, and it can be changed`() {
        val step = page.substringAfter("fun toCredentials() {").substringBefore("\n    }\n")
        assertTrue(step.contains("normalisedServerUrl(server)"))
        assertTrue(step.contains("addressDone = true"))
        assertTrue(page.contains("TextButton(onClick = { addressDone = false })"))
        assertTrue(page.contains("KeyboardActions(onNext = { toCredentials() })"))
    }
}

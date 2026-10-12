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

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Google Play's News and Magazines policy, which turned 1.0.2 down on
 * 12 October 2026: a news app must show, in the app, a clearly labelled
 * contact section with an email address or a phone number.
 */
class PlayNewsPolicyTest {

    private val about = File("src/main/java/com/saulhdev/feeder/ui/pages/AboutPage.kt").readText()
    private val strings = File("src/main/res/values/strings.xml").readText()

    @Test
    fun `About has a section called Contact us, near the top`() {
        assertTrue(strings.contains("<string name=\"about_contact\">Contact us</string>"))
        val contact = about.indexOf("heading = stringResource(id = R.string.about_contact)")
        val team = about.indexOf("heading = stringResource(id = R.string.about_whisper_team)")
        assertTrue("no Contact us heading", contact > 0)
        assertTrue("Contact us comes after the rest of the page", contact < team)
    }

    @Test
    fun `it gives an email address that opens the mail app`() {
        assertTrue(about.contains("internal const val CONTACT_EMAIL = \"hej@nyancatlabs.com\""))
        assertTrue(about.contains("ContactRow(R.string.about_contact_email, CONTACT_EMAIL, \"mailto:\$CONTACT_EMAIL\")"))
        assertTrue(about.contains("Modifier.clickable { context.launchView(row.url) }"))
    }

    @Test
    fun `Settings says where the contact details are`() {
        val prefs = File("src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt").readText()
        val entry = prefs.substring(prefs.indexOf("var about = StringPref("))
        assertTrue(entry.substring(0, entry.indexOf(")")).contains("summaryId = R.string.about_summary"))
        assertTrue(strings.contains("<string name=\"about_summary\">Version, contact us and licences</string>"))
    }

    @Test
    fun `the links under the app's name wrap rather than scroll off the screen`() {
        // The website was the fourth link in a row that scrolled sideways,
        // so on a phone it could not be seen without swiping.
        val header = about.substring(about.indexOf("fun AboutPage()"), about.indexOf("R.string.about_contact)"))
        assertTrue(header.contains("FlowRow("))
        assertTrue("a sideways-scrolling row again", !about.contains("LazyRow("))
    }
}

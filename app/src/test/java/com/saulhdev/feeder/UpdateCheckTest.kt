package com.saulhdev.feeder

import com.saulhdev.feeder.utils.UpdateCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Telling a copy installed from GitHub that a newer one is out.
 *
 * Play and F-Droid say so themselves, and a copy signed with any other key
 * cannot take our APK as an update, so only ours, installed by hand, hears it.
 */
class UpdateCheckTest {

    private val ours = UpdateCheck.OUR_KEY_SHA256
    private val google = "9C".repeat(32)
    private val fdroid = "A8DDE08993251FDF4A3599C2C4FF53CFCE880C19263E200803045C8116AB716B"

    @Test
    fun `our key matches the one the README and handover give`() {
        val handover = File("../docs/HANDOVER.md").takeIf { it.exists() } ?: File("docs/HANDOVER.md")
        val written = Regex("""Our key \(SHA-256\):\*\* `([0-9A-F:]+)`""")
            .find(handover.readText())!!.groupValues[1].replace(":", "")
        assertEquals(written, ours)
    }

    @Test
    fun `a browser download signed with our key is a GitHub install`() {
        assertTrue(UpdateCheck.isGitHubInstall(listOf(ours), "com.google.android.packageinstaller"))
        assertTrue("adb names no installer", UpdateCheck.isGitHubInstall(listOf(ours), null))
        assertTrue("case does not matter", UpdateCheck.isGitHubInstall(listOf(ours.lowercase()), null))
    }

    @Test
    fun `Play and F-Droid copies are left to their stores`() {
        assertFalse(UpdateCheck.isGitHubInstall(listOf(google), "com.android.vending"))
        assertFalse(UpdateCheck.isGitHubInstall(listOf(fdroid), "org.fdroid.fdroid"))
        // F-Droid's APK installed by hand: our file would not install over it.
        assertFalse(UpdateCheck.isGitHubInstall(listOf(fdroid), "com.google.android.packageinstaller"))
    }

    @Test
    fun `an updater that installed our own APK already says when there is a new one`() {
        assertFalse(UpdateCheck.isGitHubInstall(listOf(ours), "dev.imranr.obtainium"))
        assertFalse(UpdateCheck.isGitHubInstall(listOf(ours), "com.looker.droidify"))
    }

    @Test
    fun `versions compare by number, not by text`() {
        assertTrue(UpdateCheck.isNewer("v1.0.6", "1.0.5"))
        assertTrue(UpdateCheck.isNewer("v1.0.10", "1.0.9"))
        assertTrue(UpdateCheck.isNewer("1.1", "1.0.9"))
        assertFalse(UpdateCheck.isNewer("v1.0.5", "1.0.5"))
        assertFalse(UpdateCheck.isNewer("v1.0.4", "1.0.5"))
        assertFalse("1.0 is 1.0.0", UpdateCheck.isNewer("v1.0", "1.0.0"))
    }

    @Test
    fun `a preview build is the release it was made from`() {
        assertFalse(UpdateCheck.isNewer("v1.0.5", "1.0.5-9f7c2a6"))
        assertTrue(UpdateCheck.isNewer("v1.0.6", "1.0.5-9f7c2a6-dirty"))
    }

    @Test
    fun `nothing that is not a version counts`() {
        assertFalse(UpdateCheck.isNewer("", "1.0.5"))
        assertFalse(UpdateCheck.isNewer("latest", "1.0.5"))
        assertFalse(UpdateCheck.isNewer("v2.0.0", "unknown"))
    }

    @Test
    fun `the tag is read from GitHub's answer and checked`() {
        assertEquals("v1.0.6", UpdateCheck.tagFrom("""{"tag_name":"v1.0.6","html_url":"x"}"""))
        assertEquals("1.0.6", UpdateCheck.tagFrom("""{"tag_name":" 1.0.6 "}"""))
        // It ends up in an address Whisper opens.
        assertNull(UpdateCheck.tagFrom("""{"tag_name":"v1.0.6/../../evil"}"""))
        assertNull(UpdateCheck.tagFrom("""{"message":"API rate limit exceeded"}"""))
        assertNull(UpdateCheck.tagFrom("not json"))
    }

    @Test
    fun `the release page is on our repository`() {
        assertEquals(
            "https://github.com/Nyancat-Labs/whisper_feed/releases/tag/v1.0.6",
            UpdateCheck.releasePage("v1.0.6"),
        )
    }

    @Test
    fun `each version is announced once`() {
        assertTrue(UpdateCheck.shouldNotify("v1.0.6", "1.0.5", ""))
        assertFalse(UpdateCheck.shouldNotify("v1.0.6", "1.0.5", "v1.0.6"))
        assertTrue("a later one is new again", UpdateCheck.shouldNotify("v1.0.7", "1.0.5", "v1.0.6"))
        assertFalse("not once it is installed", UpdateCheck.shouldNotify("v1.0.6", "1.0.6", ""))
    }
}

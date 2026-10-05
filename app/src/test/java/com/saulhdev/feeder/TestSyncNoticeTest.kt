package com.saulhdev.feeder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The test notice has to be the real notice, and must not count as one.
 */
class TestSyncNoticeTest {

    private val watchdog =
        File("src/main/java/com/saulhdev/feeder/manager/sync/SyncWatchdog.kt").readText()

    private fun body(name: String): String {
        val start = watchdog.indexOf("fun $name(")
        assertTrue("no $name", start > 0)
        var depth = 0
        var end = watchdog.indexOf('{', start)
        do {
            when (watchdog[end]) { '{' -> depth++; '}' -> depth-- }
            end++
        } while (depth > 0)
        return watchdog.substring(start, end)
    }

    @Test
    fun `the test and the real notice share their text, channel and tap`() {
        val test = body("sendTest")
        val check = body("check")
        listOf(
            "STUCK_NOTIFICATION_ID",
            "text = stuckText(context, prefs)",
            "tap = Intent(context, MainActivity::class.java)",
        ).forEach {
            assertTrue("the test notice lost $it", test.contains(it))
            assertTrue("the real notice lost $it", check.contains(it))
        }
        assertEquals("one channel for both", 1, Regex("createNotificationChannel").findAll(watchdog).count())
    }

    @Test
    fun `a test records nothing, so the real notice is not held back`() {
        val test = body("sendTest")
        assertFalse(test.contains("state.edit"))
        assertFalse(test.contains("KEY_STUCK_AT"))
        assertTrue("says when notifications are off", test.contains("if (!canNotify(context)) return false"))
    }

    @Test
    fun `the button shows only while debugging is on, and only in a test build`() {
        val page = File("src/main/java/com/saulhdev/feeder/ui/pages/PreferencesPage.kt").readText()
        assertTrue(page.contains("prefs.testSyncNotice.takeIf { debugging && BuildConfig.DEV_TOOLS }"))
        val strings = File("src/main/res/values/strings.xml").readText()
        assertTrue(strings.contains("<string name=\"sync_stuck_test_title\">Test: %1\$s</string>"))
    }
}

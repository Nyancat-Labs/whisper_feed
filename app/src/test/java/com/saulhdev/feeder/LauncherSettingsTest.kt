package com.saulhdev.feeder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * "Open launcher settings" put the reader on their desktop: a launcher's
 * launch intent is its home screen.
 */
class LauncherSettingsTest {

    private val link = File("src/main/java/com/saulhdev/feeder/manager/service/LauncherLink.kt").readText()
    private val body = link.substring(link.indexOf("fun settingsIntent(")).substringBefore("\n    }\n")

    @Test
    fun `the settings screen is asked for, not the home screen`() {
        assertFalse(body.contains("getLaunchIntentForPackage"))
        assertTrue(body.contains("Intent(Intent.ACTION_APPLICATION_PREFERENCES).setPackage(packageName)"))
        assertTrue(link.contains("\"app.lawnchair.ui.preferences.PreferenceActivity\""))
    }

    @Test
    fun `only a screen the launcher lets others open is used, with Android's page for it last`() {
        assertTrue(body.contains("pm.resolveActivity(intent, 0)?.activityInfo?.exported == true"))
        assertTrue(body.contains("Settings.ACTION_APPLICATION_DETAILS_SETTINGS"))
        val page = File("src/main/java/com/saulhdev/feeder/ui/pages/LauncherPage.kt").readText()
        assertTrue(page.contains("runCatching { context.startActivity(it) }"))
    }
}

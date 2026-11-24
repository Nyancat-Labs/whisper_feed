package com.saulhdev.feeder

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsService
import androidx.core.net.toUri
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.manager.models.OPMLParser
import com.saulhdev.feeder.manager.models.ParserToDatabase
import com.saulhdev.feeder.manager.models.writeOutputStream
import com.saulhdev.feeder.utils.extensions.openOutside
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL

/** Szept 461: Reader, a browser tab inside Whisper, or the browser app. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class InAppTabTest {
    private val url = "https://www.gsmarena.com/review-1.php"

    @Test
    fun `the tab is a choice of its own, for a source and in Settings`() {
        val t = FeedPreferences.OPEN_MODE_TAB
        assertEquals(t, FeedPreferences.openModeFor(t, FeedPreferences.OPEN_MODE_READER))
        assertEquals(t, FeedPreferences.openModeFor("", t))
        assertTrue(FeedPreferences.opensOutside(t))
        assertTrue(FeedPreferences.opensOutside(FeedPreferences.OPEN_MODE_BROWSER))
        assertFalse(FeedPreferences.opensOutside(FeedPreferences.OPEN_MODE_READER))
        assertEquals("browser kept its meaning", "browser", FeedPreferences.OPEN_MODE_BROWSER)
    }

    @Test
    fun `a backup keeps a source's tab choice`() = runBlocking {
        val out = ByteArrayOutputStream()
        writeOutputStream(out, mapOf("" to listOf(Feed(title = "One", url = URL("https://one.example/feed"), openMode = "tab"))))
        val restored = mutableListOf<Feed>()
        OPMLParser(object : ParserToDatabase<Feed> {
            override suspend fun getItem(id: String): Feed? = null
            override suspend fun saveItem(item: Feed) { restored += item }
        }).parseInputStream(ByteArrayInputStream(out.toByteArray()))
        assertEquals("tab", restored.single().openMode)
    }

    @Test
    fun `with a browser that offers Custom Tabs, the page opens in a tab`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        offerCustomTabs()
        assertTrue(activity.openOutside(url, FeedPreferences.OPEN_MODE_TAB))
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(url, started.dataString)
        assertTrue("a Custom Tabs intent", started.hasExtra(CustomTabsIntent.EXTRA_SESSION))
    }

    @Test
    fun `without one, or with Browser chosen, the browser app opens as before`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        assertTrue(activity.openOutside(url, FeedPreferences.OPEN_MODE_TAB))
        assertFalse(shadowOf(activity).nextStartedActivity.hasExtra(CustomTabsIntent.EXTRA_SESSION))

        offerCustomTabs()
        assertTrue(activity.openOutside(url, FeedPreferences.OPEN_MODE_BROWSER))
        assertFalse(shadowOf(activity).nextStartedActivity.hasExtra(CustomTabsIntent.EXTRA_SESSION))
    }

    @Test
    fun `a link that is not a web page is refused in a tab too`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        offerCustomTabs()
        assertFalse(activity.openOutside("intent://evil#Intent;end", FeedPreferences.OPEN_MODE_TAB))
    }

    @Test
    fun `Android lets Whisper see browsers that offer Custom Tabs`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.support.customtabs.action.CustomTabsService"))
    }

    /** A browser, as Robolectric's package manager sees one, with a Custom Tabs service. */
    private fun offerCustomTabs() {
        val pm = shadowOf(org.robolectric.RuntimeEnvironment.getApplication().packageManager)
        val browser = "com.example.browser"
        val view = ResolveInfo().apply {
            activityInfo = android.content.pm.ActivityInfo().apply {
                packageName = browser; name = "$browser.Main"
                applicationInfo = android.content.pm.ApplicationInfo().apply { packageName = browser }
            }
            filter = IntentFilter(Intent.ACTION_VIEW).apply { addCategory(Intent.CATEGORY_BROWSABLE); addDataScheme("http") }
        }
        pm.addResolveInfoForIntent(Intent(Intent.ACTION_VIEW, "http://".toUri()), view)
        pm.addResolveInfoForIntent(Intent(Intent.ACTION_VIEW, "https://".toUri()), view)
        val service = ResolveInfo().apply {
            serviceInfo = ServiceInfo().apply { packageName = browser; name = "$browser.Tabs" }
        }
        val action = Intent(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION).setPackage(browser)
        pm.addResolveInfoForIntent(action, service)
        pm.addServiceIfNotPresent(ComponentName(browser, "$browser.Tabs"))
        pm.addIntentFilterForService(ComponentName(browser, "$browser.Tabs"),
            IntentFilter(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION))
    }
}

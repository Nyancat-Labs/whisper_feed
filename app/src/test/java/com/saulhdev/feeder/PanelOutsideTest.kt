package com.saulhdev.feeder

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.utils.extensions.openOutside
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * Szept 474 and 477: from the launcher panel, a browser tab or the browser app
 * closed onto Lawnchair, which had gone to the home screen. The panel now
 * starts Whisper's feed and the page together, the page on top, so closing it
 * lands on the feed, and the feed is not drawn on the way in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PanelOutsideTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val url = "https://www.gsmarena.com/review-1.php"
    private fun src(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    @Test
    fun `from the panel, the feed and the page start together, the page on top`() {
        for (mode in listOf(FeedPreferences.OPEN_MODE_TAB, FeedPreferences.OPEN_MODE_BROWSER)) {
            assertTrue(app.openOutside(url, mode, beneath = MainActivity.underTabIntent(app)))
            val shadow = shadowOf(app)
            // Robolectric hands back the most recent start first.
            val all = generateSequence { shadow.nextStartedActivity }.toList().reversed()
            assertEquals(2, all.size)
            val (first, second) = all
            assertEquals("the feed first, beneath", MainActivity::class.java.name, first.component?.className)
            assertTrue("in its own task", first.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
            assertEquals("the page on top", url, second.dataString)
            assertEquals("the page joins the feed's task", 0, second.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    @Test
    fun `a link that is not a web page starts nothing, not even the feed`() {
        assertFalse(app.openOutside("intent://evil#Intent;end", FeedPreferences.OPEN_MODE_TAB, beneath = MainActivity.underTabIntent(app)))
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test
    fun `the feed beneath shows no splash, and the panel uses it`() {
        val intent = MainActivity.underTabIntent(app)
        assertTrue(intent.getBooleanExtra(MainActivity.EXTRA_UNDER_TAB, false))
        assertTrue(src("MainActivity.kt").contains("val launchedDirectly = intent?.data == null && !intent.hasExtra(EXTRA_UNDER_TAB)"))
        val overlay = src("manager/service/OverlayView.kt")
        assertTrue(overlay.contains("context.openOutside(item.link, mode, beneath = MainActivity.underTabIntent(context))"))
        // The read timer starts in the panel; the feed's resume settles it.
        assertTrue(overlay.contains("BrowserReadTimer.left(item.id)"))
    }
}

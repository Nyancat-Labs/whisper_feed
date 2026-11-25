package com.saulhdev.feeder

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.saulhdev.feeder.data.content.FeedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Szept 474: from the launcher panel, a browser tab or the browser app closed
 * onto Lawnchair, which had gone to the home screen. The panel now asks the
 * app to open it, so closing it lands on Whisper's feed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PanelOutsideTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private fun src(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    @Test
    fun `the panel's request carries the article and how to open it`() {
        for (mode in listOf(FeedPreferences.OPEN_MODE_TAB, FeedPreferences.OPEN_MODE_BROWSER)) {
            val intent = MainActivity.outsideIntent(context, "a1", mode)
            assertEquals(MainActivity::class.java.name, intent.component?.className)
            assertEquals(OutsideRequest("a1", mode), OutsideRequest.from(intent))
        }
    }

    @Test
    fun `anything else opens nothing outside`() {
        assertNull(OutsideRequest.from(null))
        assertNull("an ordinary start", OutsideRequest.from(Intent(context, MainActivity::class.java)))
        assertNull("the reader is not outside", OutsideRequest.from(MainActivity.outsideIntent(context, "a1", FeedPreferences.OPEN_MODE_READER)))
        assertNull("an unknown mode", OutsideRequest.from(MainActivity.outsideIntent(context, "a1", "evil")))
        assertNull("no article", OutsideRequest.from(MainActivity.outsideIntent(context, " ", FeedPreferences.OPEN_MODE_TAB)))
    }

    @Test
    fun `the app opens it, from the database, once, and times the read`() {
        val main = src("MainActivity.kt")
        // The link is looked up, never taken from the intent: the activity is
        // exported, and only an article in the feed may be opened this way.
        assertTrue(main.contains("articles.getArticleById(request.articleId).first()?.link"))
        // Not reopened when the system recreates the activity from Recents.
        assertTrue(main.contains("if (savedInstanceState == null) pendingOutside = OutsideRequest.from(intent)"))
        // Opened after the earlier trip is settled, so this one is not settled at once.
        val resume = main.substringAfter("override fun onResume()")
        assertTrue(resume.indexOf("BrowserReadTimer.settle()") < resume.indexOf("openPendingOutside"))
        assertTrue(main.contains("BrowserReadTimer.left(request.articleId)\n            openOutside(link, request.mode)"))
        // No splash on the way to the tab.
        assertTrue(main.contains("val launchedDirectly = intent?.data == null && OutsideRequest.from(intent) == null"))

        val overlay = src("manager/service/OverlayView.kt")
        assertTrue(overlay.contains("context.safeStartActivity(MainActivity.outsideIntent(context, item.id, mode))"))
        assertTrue("the panel no longer opens it itself", !overlay.contains("context.openOutside("))
    }
}

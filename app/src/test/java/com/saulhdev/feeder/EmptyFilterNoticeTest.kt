package com.saulhdev.feeder

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The "no duplicates" message survives the filter disarming itself.
 *
 * The effect that notices an empty result does two things in order: it turns
 * the filter off, so the full list comes straight back rather than leaving an
 * empty screen for as long as a snackbar lasts, and then it says why. The
 * trouble is that the filter's state is one of the effect's own keys, so the
 * first act cancels the coroutine the second was running in — and the message
 * went down with it, every time.
 *
 * From the outside that is a menu entry that does nothing: the filter runs,
 * finds nothing, puts the list back exactly as it was, and the one sentence
 * explaining why is destroyed by the line that put the list back. It was
 * reported as the feature not working.
 *
 * Six other snackbars in this app sit in bare LaunchedEffects and are fine,
 * because they suspend on the message *first* and change their key afterwards.
 * This is the only one that had to do it the other way round, so this is the
 * only one that needs saying out loud.
 */
class EmptyFilterNoticeTest {

    private val source = File(
        "src/main/java/com/saulhdev/feeder/ui/pages/SourceListPage.kt"
    ).readText()

    @Test
    fun `the source is actually being read`() {
        assertTrue(source.contains("setDuplicatesOnly(false)"))
    }

    @Test
    fun `the empty-result message is launched outside the effect that disarms the filter`() {
        // The window between disarming and the message. If showSnackbar is
        // called there without a scope of its own, it is cancelled before it
        // is seen.
        val disarm = source.indexOf("viewModel.setDuplicatesOnly(false)")
        assertTrue("the disarm call moved", disarm > 0)
        val after = source.substring(disarm, minOf(disarm + 1200, source.length))
        val snackbar = after.indexOf("showSnackbar")
        assertTrue("no message follows the disarm", snackbar > 0)

        val between = after.substring(0, snackbar)
        assertTrue(
            "showSnackbar must be launched in the screen's scope, or disarming " +
                    "the filter cancels it before the reader sees it",
            between.contains("scope.launch"),
        )
    }
}

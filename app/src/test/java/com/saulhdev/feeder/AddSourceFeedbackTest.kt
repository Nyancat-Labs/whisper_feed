package com.saulhdev.feeder

import com.saulhdev.feeder.manager.models.FeedParser
import com.saulhdev.feeder.ui.pages.hostOf
import com.saulhdev.feeder.utils.HttpStatusException
import com.saulhdev.feeder.viewmodels.isNetworkFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/** Adding a source: saying why a search failed, and that filing it saved (9 November). */
class AddSourceFeedbackTest {

    private val page = File("src/main/java/com/saulhdev/feeder/ui/pages/SourceAddPage.kt").readText()
    private val url = URL("https://076.io")

    @Test
    fun `no answer from the site is a network failure, even wrapped by the parser`() {
        assertTrue(isNetworkFailure(FeedParser.FeedParsingError(url, UnknownHostException("076.io"))))
        assertTrue(isNetworkFailure(FeedParser.FeedParsingError(url, ConnectException())))
        assertTrue(isNetworkFailure(SocketTimeoutException()))
    }

    @Test
    fun `an answer that is not a feed is not a network failure`() {
        assertFalse(isNetworkFailure(FeedParser.FeedParsingError(url, HttpStatusException(404))))
        assertFalse(isNetworkFailure(IllegalStateException("not a feed")))
    }

    @Test
    fun `the unreachable message names the site`() {
        assertEquals("076.io", hostOf("https://076.io"))
        assertEquals("example.com", hostOf("https://www.example.com/feed"))
        assertEquals("BBC", hostOf("BBC"))
    }

    @Test
    fun `offline is checked before searching`() {
        assertTrue(page.contains("if (!whisperHasNetwork(context))"))
        assertTrue(page.contains("SearchFailure.Offline"))
    }

    @Test
    fun `the search box has no fill to cut around its label`() {
        assertTrue(page.contains("OutlinedTextFieldDefaults.colors("))
        assertFalse(page.contains("TextFieldDefaults.colors(".let { " $it" }))
    }

    @Test
    fun `choosing a category says it saved, and Done leaves`() {
        assertTrue(page.contains("R.string.add_feed_filed"))
        assertTrue(page.contains("R.string.add_feed_done"))
        assertTrue(page.contains("leadingIcon = if (selected)"))
    }

    @Test
    fun `an added feed's card is not greyed out as disabled`() {
        assertFalse(page.contains("enabled = !alreadyAdded"))
    }

    @Test
    fun `leaving Sources makes a removal final, so its undo message does not come back`() {
        val sources = File("src/main/java/com/saulhdev/feeder/ui/pages/SourceListPage.kt").readText()
        val dispose = sources.substring(sources.indexOf("DisposableEffect(Unit) {"))
            .substringBefore("\n    }\n")
        assertTrue(dispose.contains("viewModel.forgetDeleted()"))
        assertTrue(dispose.contains("viewModel.forgetDeletedSources()"))
    }
}

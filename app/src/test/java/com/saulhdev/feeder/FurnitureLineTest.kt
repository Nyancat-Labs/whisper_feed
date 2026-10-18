package com.saulhdev.feeder

import com.saulhdev.feeder.utils.dropFurnitureLines
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FurnitureLineTest {

    private fun cleaned(html: String) = Jsoup.parse(html).body().also(::dropFurnitureLines).text()

    @Test
    fun `labels left by emptied ad slots and sign-up boxes go`() {
        // Darknet, 404 Media, New Scientist, JetBrains, The Portugal News, Lifehacker.
        val text = cleaned(
            "<p>First.</p><div><span>Advertisement</span></div><p>Second.</p>" +
                "<h3>Sign up to newsletter</h3><p>Sign up to Health Check</p><p>Subscriber-only newsletter</p>" +
                "<p>Subscribe to JetBrains Blog updates</p><h2>Related articles</h2>" +
                "<a><span>Add as a preferred source on Google</span></a><p>Third.</p>"
        )
        assertEquals("First. Second. Third.", text)
    }

    @Test
    fun `a sentence that mentions them stays`() {
        val text = cleaned(
            "<p>The advertisement ran for a week before it was pulled.</p>" +
                "<p>Sign up to vote by Friday, the council said, or lose your say in the matter.</p>"
        )
        assertTrue(text.contains("The advertisement ran"))
        assertTrue(text.contains("Sign up to vote by Friday"))
    }

    @Test
    fun `a gated story keeps the line that says why it stops`() {
        assertTrue(cleaned("<p>Sign up for free access to this post</p>").contains("free access"))
    }

    @Test
    fun `a line holding a picture is not furniture`() {
        assertFalse(
            Jsoup.parse("<p><img src=\"https://example.com/ad.png\">Advertisement</p>").body()
                .also(::dropFurnitureLines).select("img").isEmpty()
        )
    }
}

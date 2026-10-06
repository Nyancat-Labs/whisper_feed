package com.saulhdev.feeder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Article prose is set ragged right, one piece per word.
 *
 * The reader justified its paragraphs and switched automatic hyphenation on
 * to keep the rivers out of them. On a phone's forty-character measure that
 * meant every line stretched by a different amount and words broken wherever
 * the line needed it — "automot-ive", "Activ-ision", "Actu-ally" — so the
 * reader had to reassemble words as well as follow gaps that changed size
 * down the page. It was reported as disorienting.
 *
 * Read from the source rather than rendered, because nothing that runs in a
 * unit test can see how a paragraph was set — and the two settings are one
 * word each, which is exactly the kind of change that gets made back.
 */
class ReaderProseTest {

    /**
     * The declaration only, never its comment.
     *
     * The KDoc above `bodyStyle` explains at length why justification was
     * wrong, so it contains every word this test is looking for. Searching
     * the file would pass on the prose while the code said the opposite.
     */
    private val style: String = File(
        "src/main/java/com/saulhdev/feeder/utils/HtmlToComposable.kt"
    ).readText()
        .substringAfter("private fun bodyStyle()")
        .substringBefore("fun LazyListScope.htmlFormattedText")

    @Test
    fun `prose is not justified`() {
        assertFalse("justification needs a wider measure than a phone has", style.contains("Justify"))
        assertTrue(style.contains("TextAlign.Start"))
    }

    @Test
    fun `words are not broken across lines`() {
        assertFalse(
            "hyphenation was only ever there to prop up the justification",
            style.contains("Hyphens.Auto"),
        )
        assertTrue(style.contains("Hyphens.None"))
    }

    @Test
    fun `whole-paragraph line breaking stays`() {
        // The part that was never the problem: it is what keeps the ragged
        // edge shallow rather than saw toothed.
        assertTrue(style.contains("LineBreak.Paragraph"))
    }
}

package com.saulhdev.feeder

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The ampersand repair does not claim to be a security measure.
 *
 * It was called `xmlSafe`, which read as though it hardened the parse. It does
 * not: it escapes an `&` that is not already starting an entity, and nothing
 * else — no DOCTYPE handling, no external entities, no expansion limits. A
 * name that claims safety is worse than no guard at all, because the next
 * person to add a parser reaches for it and stops looking.
 *
 * The hardening it implied now exists where it belongs, on the parser. The
 * packs it reads ship inside the APK, so nothing hostile can be in them and it
 * changes nothing today — the point is that the helper is one import away from
 * being pointed at a file somebody sent, and at that moment the defaults are
 * what would apply.
 */
class XmlHardeningTest {

    private fun read(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    @Test
    fun `nothing is named as though it made a parse safe`() {
        // Production code only, and the call rather than the word. Both
        // source files name the old one in a comment explaining why it
        // changed, and this test has to quote it to look for it — the history
        // is the useful part, and a check that forbade writing it down would
        // delete the reason along with the name.
        val sources = File("src/main").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("xmlSafe(") }
            .map { it.name }
            .toList()
        assertTrue("xmlSafe is back: $sources", sources.isEmpty())
    }

    @Test
    fun `the repair says what it does`() {
        val parser = read("manager/models/OPMLParser.kt")
        assertTrue(
            "the ampersand repair lost its name",
            parser.contains("fun escapingBareAmpersands("),
        )
    }

    @Test
    fun `the parser that reads packs refuses to fetch anything`() {
        val library = read("data/FeedLibrary.kt")
        assertTrue(
            "the pack parser is built with defaults again",
            !library.contains("SAXParserFactory.newInstance().newSAXParser()"),
        )
        listOf(
            "disallow-doctype-decl",
            "external-general-entities",
            "external-parameter-entities",
        ).forEach {
            assertTrue("the pack parser no longer disables $it", library.contains(it))
        }
        assertTrue("XInclude is enabled again", library.contains("isXIncludeAware = false"))
    }

    @Test
    fun `one unknown feature does not lose the others`() {
        // A parser implementation that refuses a feature throws, and setting
        // them in one block would drop the rest with it.
        val library = read("data/FeedLibrary.kt")
        val at = library.indexOf("fun hardenedSaxParser")
        assertTrue("the hardened parser is gone", at > 0)
        val body = library.substring(at, library.indexOf("\n    }", at))
        assertTrue(
            "the features are no longer set one at a time",
            body.contains("forEach") && body.contains("runCatching"),
        )
    }
}

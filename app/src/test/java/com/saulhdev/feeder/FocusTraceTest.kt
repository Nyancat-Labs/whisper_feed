package com.saulhdev.feeder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The focus trace has to survive minification, and must not carry content.
 *
 * Two ways for a diagnostic like this to be worse than nothing. It can be
 * written with `Log.d`, which proguard-rules.pro strips outright — leaving a
 * trace that exists only in the debug build, which is the one build where the
 * question it answers cannot come up. Or it can log what somebody typed: a
 * category, a search term, a server address are all theirs, and this goes into
 * a file they send to a stranger.
 */
class FocusTraceTest {

    private val source = File(
        "src/main/java/com/saulhdev/feeder/ui/components/FocusTrace.kt"
    ).readText()

    private val code = source
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")

    @Test
    fun `the trace file is actually being read`() {
        assertTrue("FocusTrace.kt not found", source.contains("fun Modifier.traceFocus"))
    }

    @Test
    fun `it is written with a call R8 does not strip`() {
        // proguard-rules.pro lists Log.d, Log.v and Log.i under
        // -assumenosideeffects. Log.println is not on that list.
        assertTrue("the trace must use Log.println", code.contains("Log.println"))
        val stripped = listOf("Log.d(", "Log.v(", "Log.i(")
            .filter { code.contains(it) }
        assertEquals("these calls are removed by R8", emptyList<String>(), stripped)
    }

    @Test
    fun `the rules it relies on are still in proguard`() {
        // If the strip rules were ever removed, Log.d would be fine again and
        // the reasoning above would be stale rather than wrong — worth knowing.
        val rules = File("proguard-rules.pro").readText()
        assertTrue(rules.contains("-assumenosideeffects class android.util.Log"))
        assertTrue(rules.contains("public static int d(java.lang.String, java.lang.String)"))
    }

    @Test
    fun `it logs the field's name and never its contents`() {
        // The only interpolation in the message is the field name passed in by
        // the call site, which is a literal like "add category".
        val interpolations = Regex("""\$\{?(\w+)""").findAll(code).map { it.groupValues[1] }.toSet()
        val allowed = setOf("field")
        assertEquals(
            "the trace interpolates something other than the field name",
            emptySet<String>(),
            interpolations - allowed,
        )
    }

    @Test
    fun `it is off unless the reader turned debugging on`() {
        assertTrue(
            "the trace must be gated on the debugging preference",
            code.contains("prefs.debugging"),
        )
    }
}

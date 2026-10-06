package com.saulhdev.feeder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * No flow is combined through an untyped array.
 *
 * `combine` has typed overloads for up to five flows, where the transform
 * takes named parameters and the compiler checks that there are the right
 * number of them. Past five it falls back to `Array<Any?>`, and so does any
 * call that chooses to take one — at which point the positions are checked by
 * nobody.
 *
 * That cost a working app. A flow was removed from the middle of a
 * five-element list and the two reads below it went on pointing at 4 and 5;
 * five flows, index 5, an ArrayIndexOutOfBoundsException on a background
 * worker on every single launch. It compiled, it passed lint, and it passed
 * every test here, because nothing in this suite builds that view model — the
 * first thing that knew was a phone.
 *
 * So the array form is banned outright rather than used carefully. If a sixth
 * flow is ever genuinely needed, combine two combines, or make a data class:
 * either keeps the compiler counting.
 */
class CombineArityTest {

    private val mainSources = File("src/main/java")

    private fun kotlinFiles(): List<File> =
        mainSources.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun withoutComments(source: String): String =
        source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""//[^\n]*"""), "")

    @Test
    fun `the sources are actually being read`() {
        assertTrue("no Kotlin sources found under $mainSources", kotlinFiles().size > 50)
    }

    @Test
    fun `no combine reads its values out of an array`() {
        // The signature of the mistake: a transform that subscripts rather
        // than naming its parameters.
        val indexed = Regex("""\)\s*\{\s*(\w+)\s*->[\s\S]{0,600}?\1\[\d+\]""")
        val offenders = kotlinFiles().filter { file ->
            val code = withoutComments(file.readText())
            code.contains("combine(") && indexed.containsMatchIn(code)
        }.map { it.name }

        assertEquals(
            "these combine an array instead of named parameters: $offenders",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `the feed's own combine names every flow it reads`() {
        // Named because this is the one that broke, and because it is the flow
        // behind the first screen the app shows: when it throws, there is no
        // app at all.
        val code = withoutComments(
            File(
                mainSources,
                "com/saulhdev/feeder/viewmodels/ArticleListViewModel.kt",
            ).readText()
        )
        assertTrue(
            "the feed list must not index a combine array",
            !code.contains("values["),
        )
    }
}

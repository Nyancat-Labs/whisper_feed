/*
 * This file is part of Whisper
 * Copyright (c) 2026   Whisper contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.saulhdev.feeder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 5 October: F-Droid's build of 1.0.0 failed on line 158 of the build file.
 * Before building, fdroidserver's `remove_signing_keys` deletes the
 * `signingConfigs { }` block and every line that is only a `signingConfig`
 * assignment. The preview's assignment ran over three lines; its first was
 * deleted and `"release" else "debug")` was left on its own.
 *
 * This is that deletion, as fdroidserver writes it, run over the real file.
 */
class FdroidBuildTest {

    private val comment = Regex("""[ ]*//""")
    private val block = Regex("""^[\t ]*signingConfigs[ \t]*\{[ \t]*$""")
    private val lineMatches = listOf(
        Regex("""^[\t ]*signingConfig\s*[= ]\s*[^ ]*$"""),
        Regex(""".*android\.signingConfigs\.[^{]*$"""),
        Regex(""".*release\.signingConfig *= *.*"""),
    )

    private fun removeSigningKeys(lines: List<String>): List<String> {
        val out = mutableListOf<String>()
        var opened = 0
        for (line in lines) {
            if (comment.matchesAt(line, 0)) {
                out += line
                continue
            }
            if (opened > 0) {
                opened += line.count { it == '{' } - line.count { it == '}' }
                continue
            }
            if (block.matches(line)) {
                opened += 1
                continue
            }
            if (lineMatches.any { it.matches(line) }) continue
            out += line
        }
        return out
    }

    private val buildFile = File("build.gradle.kts").readLines()

    @Test
    fun `the build file still holds together once F-Droid takes the keys out`() {
        val cleaned = removeSigningKeys(buildFile).joinToString("\n")
        // Comments and strings aside, every bracket opened is closed.
        val code = cleaned.lines()
            .map { it.substringBefore("//") }
            .joinToString("\n")
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex(""""(?:[^"\\\n]|\\.)*""""), "\"\"")
        assertEquals("( and )", code.count { it == '(' }, code.count { it == ')' })
        assertEquals("{ and }", code.count { it == '{' }, code.count { it == '}' })
        assertTrue("the keys block is gone", !cleaned.contains(Regex("""^\s*signingConfigs\s*\{""", RegexOption.MULTILINE)))
    }

    @Test
    fun `every line that sets a signing key is a whole statement`() {
        buildFile.filter { it.trimStart().startsWith("signingConfig ") || it.trimStart().startsWith("signingConfig=") }
            .forEach { line -> assertEquals(line, line.count { it == '(' }, line.count { it == ')' }) }
    }
}

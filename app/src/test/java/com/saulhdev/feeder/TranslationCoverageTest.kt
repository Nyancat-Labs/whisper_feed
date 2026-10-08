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
 * A language ships whole or not at all.
 *
 * Until 1.0.3 the app carried 28 languages from Neo Feed, none above 13% of
 * the strings, so every one of them showed a few translated words on screens
 * that were otherwise English.
 */
class TranslationCoverageTest {

    private val res = File("src/main/res")
    private val nameRe = Regex("""<(?:string|plurals|string-array) name="([^"]+)"""")

    private fun names(file: File) = nameRe.findAll(file.readText()).map { it.groupValues[1] }.toSet()

    private val english: Set<String> by lazy {
        val text = File(res, "values/strings.xml").readText()
        val fixed = Regex("""<string name="([^"]+)"[^>]*translatable="false"""").findAll(text).map { it.groupValues[1] }.toSet()
        names(File(res, "values/strings.xml")) - fixed
    }

    /** values-de, values-pt-rBR and so on; not values-night or values-v27. */
    private val languages: List<File> by lazy {
        res.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.matches(Regex("""values-[a-z]{2,3}(-r[A-Z]{2})?""")) }
            .sortedBy { it.name }
    }

    @Test
    fun `every language that ships covers nearly all of the app`() {
        languages.forEach { dir ->
            val have = File(dir, "strings.xml").takeIf { it.isFile }?.let(::names).orEmpty() intersect english
            val share = have.size * 100 / english.size
            assertTrue("${dir.name} covers $share% of the strings", share >= MIN_COVERAGE)
        }
    }

    @Test
    fun `the build ships exactly the languages the app has`() {
        val build = File("build.gradle.kts").readText()
        val filters = Regex("""localeFilters \+= listOf\(([^)]*)\)""").find(build)?.groupValues?.get(1)
            ?.split(",")?.map { it.trim().trim('"') }?.filter { it.isNotEmpty() }?.toSet()
        assertTrue("no localeFilters in the build", filters != null)
        val shipped = languages.map { it.name.removePrefix("values-").replace("-r", "-") }.toSet()
        assertEquals("a language in res/ must be in localeFilters, and only those", shipped + "en", filters)
    }

    private companion object {
        const val MIN_COVERAGE = 90
    }
}

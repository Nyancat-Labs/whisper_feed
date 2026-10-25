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
package com.saulhdev.feeder.utils

/**
 * Finding a feed from a name rather than an address: "the potato" to
 * thepotato.ie (owner, 25 October 2026).
 *
 * No search service is asked. A third party would see every name searched
 * for, and Whisper sends nothing anywhere unless the reader turns it on. So
 * the name is turned into the addresses a site of that name most likely
 * has, the phone's own country first, and each is checked the way a typed
 * address already is. The only sites contacted are the ones being looked for.
 * A name that is not its address ("Irish Independent", independent.ie) is
 * not found this way; the built-in library, searched by name, covers some.
 */
object FeedNameSearch {

    /** How many guessed addresses one search may try. */
    const val MAX_GUESSES = 6

    /** Something with a dot, a slash or a scheme was meant as an address. */
    fun looksLikeAddress(text: String): Boolean {
        val t = text.trim()
        return t.contains("://") || t.contains('/') || (t.contains('.') && !t.contains(' '))
    }

    /** The name as a host would spell it, or null if nothing usable is left. */
    private fun label(words: List<String>, joiner: String): String? =
        words.joinToString(joiner).takeIf { it.length in 2..63 }

    private fun words(name: String): List<String> =
        name.lowercase()
            .replace("&", " and ")
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotEmpty() }
            // Ascii only: a host with other letters needs punycode, and a
            // wrong guess at that is a request for nothing.
            .filter { word -> word.all { it in 'a'..'z' || it in '0'..'9' } }

    /** The country's own domain, where it is not simply its code. */
    private fun countryDomain(country: String): String? = when (val c = country.lowercase()) {
        "" -> null
        "gb", "uk" -> "co.uk"
        "au" -> "com.au"
        "nz" -> "co.nz"
        "jp" -> "co.jp"
        "br" -> "com.br"
        "za" -> "co.za"
        "us" -> null // .com is the American default
        else -> c.takeIf { it.length == 2 && it.all(Char::isLetter) }
    }

    /**
     * The hosts to try for [name], most likely first: the name run together
     * on the country's domain and on .com, then the common others, then the
     * hyphenated spelling on the first two.
     */
    fun candidateHosts(name: String, country: String): List<String> {
        val w = words(name)
        if (w.isEmpty()) return emptyList()
        val joined = label(w, "") ?: return emptyList()
        val hyphened = if (w.size > 1) label(w, "-") else null
        val local = countryDomain(country)
        val first = listOfNotNull(local, "com")
        val hosts = buildList {
            first.forEach { add("$joined.$it") }
            listOf("org", "net", "co.uk").forEach { add("$joined.$it") }
            hyphened?.let { h -> first.forEach { add("$h.$it") } }
        }
        return hosts.distinct().take(MAX_GUESSES)
    }

    /** Whether a library entry answers to [name], spaces and case aside. */
    fun matches(name: String, title: String, url: String): Boolean {
        val q = name.lowercase().filter { it.isLetterOrDigit() }
        if (q.length < 2) return false
        return title.lowercase().filter { it.isLetterOrDigit() }.contains(q) ||
            url.lowercase().filter { it.isLetterOrDigit() }.contains(q)
    }
}

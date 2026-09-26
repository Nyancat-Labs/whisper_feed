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
package com.saulhdev.feeder.manager.sync.greader

/**
 * The address to sign in to, from whatever the reader typed.
 *
 * Every other client in this app rewrites `http` to `https` on its way to the
 * socket, and this one deliberately does not. Not because upgrading a
 * credentialed address is dangerous — it is the same host either way, checked
 * against the system trust store, so nobody without a real certificate for
 * that name can be on the other end — but because a password is the wrong
 * thing to be quietly routed somewhere the reader did not agree to. They
 * should see what happened.
 *
 * So the correction is made here instead: at sign-in, in the field, before the
 * password is sent. The reader watches `http://` become `https://` and the
 * stored address is the corrected one, so it is right the next time too.
 *
 * Doing nothing was not an option worth keeping. Cleartext is refused at the
 * platform level, so an `http://` address never connects at all — it fails
 * with a network error that says nothing about the scheme, and the reader is
 * left to guess between a typo, a firewall, and a server that is down.
 *
 * A missing scheme is filled in for the same reason. `freshrss.example.com/…`
 * is what people paste out of their own browser's address bar.
 */
fun normalisedServerUrl(typed: String): String {
    val trimmed = typed.trim()
    if (trimmed.isEmpty()) return ""

    val scheme = trimmed.substringBefore("://", missingDelimiterValue = "")
    return when {
        scheme.equals("https", ignoreCase = true) ->
            "https://" + trimmed.substringAfter("://")

        scheme.equals("http", ignoreCase = true) ->
            "https://" + trimmed.substringAfter("://")

        // Anything else with a scheme is left exactly as typed. `ftp://` is
        // not a sync server and pretending it might be, by rewriting it into
        // one, would turn a clear mistake into a confusing one.
        scheme.isNotEmpty() -> trimmed

        else -> "https://$trimmed"
    }
}

/** Where FreshRSS keeps the API, under the address of its web interface. */
const val FRESHRSS_API_PATH = "/api/greader.php"

/**
 * The addresses to try signing in to, in order.
 *
 * FreshRSS answers the protocol at `/api/greader.php`, not at the address
 * people know it by, and nobody remembers the path: the web address is what a
 * password manager keeps and what the browser shows. Miniflux, Inoreader and
 * BazQux answer at the address itself. So the address as given comes first,
 * and then, unless it already names the API, the same address with the
 * FreshRSS path added - after dropping `/i`, the path of FreshRSS's own pages,
 * which is what gets copied out of the address bar.
 */
fun serverCandidates(address: String): List<String> {
    val given = address.trim().trimEnd('/')
    if (given.isEmpty()) return emptyList()
    if (given.endsWith(".php", ignoreCase = true)) return listOf(given)
    val site = given.removeSuffix("/i").removeSuffix("/api")
    return listOf(given, site + FRESHRSS_API_PATH).distinct()
}

/**
 * Signs in at the first of [candidates] that accepts, returning that address
 * with the result.
 *
 * The next address is tried only when the server answered: one that cannot be
 * reached, or whose certificate is wrong, will not do better at another path
 * on the same host. When none accepts, the failure reported is the one that
 * says most - "wrong password" from the API rather than "nothing here" from
 * the path that was only a guess.
 */
suspend fun signInAtAny(
    candidates: List<String>,
    attempt: suspend (String) -> GoogleReaderApi.AuthResult,
): Pair<String, GoogleReaderApi.AuthResult> {
    val failures = mutableListOf<Pair<String, GoogleReaderApi.AuthResult.Failed>>()
    for (address in candidates) {
        when (val result = attempt(address)) {
            is GoogleReaderApi.AuthResult.Success -> return address to result
            is GoogleReaderApi.AuthResult.Failed -> {
                failures += address to result
                if (result.problem !in SERVER_ANSWERED) break
            }
        }
    }
    return failures.firstOrNull { it.second.problem !in NOTHING_THERE }
        ?: failures.firstOrNull()
        ?: ("" to GoogleReaderApi.AuthResult.Failed(AccountProblem.BAD_ADDRESS))
}

private val NOTHING_THERE = setOf(AccountProblem.NOT_FOUND, AccountProblem.NO_TOKEN)

private val SERVER_ANSWERED = NOTHING_THERE + setOf(
    AccountProblem.MOVED,
    AccountProblem.CREDENTIALS,
    AccountProblem.SERVER_ERROR,
    AccountProblem.UNKNOWN,
)

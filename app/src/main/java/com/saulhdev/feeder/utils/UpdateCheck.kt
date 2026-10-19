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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The decisions behind telling a GitHub install that a new version is out,
 * kept apart from Android so they can be tested on their own.
 *
 * Play and F-Droid tell their users about updates. An APK downloaded from
 * GitHub Releases hears nothing and stays on the version it came with.
 */
object UpdateCheck {

    /** The newest release that is not a pre-release, as GitHub's API gives it. */
    const val LATEST_RELEASE_API =
        "https://api.github.com/repos/Nyancat-Labs/whisper_feed/releases/latest"

    private const val RELEASE_PAGE = "https://github.com/Nyancat-Labs/whisper_feed/releases/tag/"

    /**
     * Our signing key, SHA-256, as on the GitHub releases and Obtainium.
     *
     * Play installs carry Google's key and F-Droid's carry F-Droid's. Only a
     * copy signed with this one can take the APK the notice points at as an
     * update, so only such a copy is told about it: anyone else would be
     * sent to a file Android refuses to install over what they have.
     */
    const val OUR_KEY_SHA256 =
        "D45452456C89FEAF2DD5A49010EF149370A409B63A1531027BD4CAA82F88B164"

    /**
     * Apps that install and update Whisper themselves, and so already say
     * when a new version is out. A second notice from Whisper would only
     * repeat theirs. IzzyOnDroid serves our own APK, so its clients appear
     * here even though the key matches.
     */
    val UPDATERS = setOf(
        "com.android.vending", // Google Play
        "org.fdroid.fdroid", // F-Droid
        "org.fdroid.basic",
        "com.looker.droidify", // Droid-ify
        "com.machiav3lli.fdroid", // Neo Store
        "dev.imranr.obtainium", // Obtainium
        "dev.imranr.obtainium.fdroid",
        "in.sunilpaulmathew.izzyondroid",
        "com.aurora.store", // Aurora Store
        "app.accrescent.client", // Accrescent
    )

    /**
     * Whether this copy came from GitHub by hand: signed with our key and
     * installed by something other than an app store or updater. A browser
     * download is installed by the system's package installer, and
     * `adb install` names no installer at all; both count.
     */
    fun isGitHubInstall(signerSha256: Collection<String>, installer: String?): Boolean =
        signerSha256.any { it.equals(OUR_KEY_SHA256, ignoreCase = true) } &&
            installer !in UPDATERS

    /** A release tag we understand, "v1.0.6" or "1.0.6"; anything else is ignored. */
    private val TAG = Regex("""v?(\d+(?:\.\d+){0,3})""")

    /** The numbers of a version or tag, or null if it is not one. */
    fun versionNumbers(version: String): List<Int>? {
        // A preview build is "1.0.5-9f7c2a6": the part before the build is
        // the release it was made from.
        val match = TAG.matchEntire(version.trim().substringBefore('-')) ?: return null
        return match.groupValues[1].split('.').map { it.toIntOrNull() ?: return null }
    }

    /** Whether [tag] is a later release than [installed]. */
    fun isNewer(tag: String, installed: String): Boolean {
        val latest = versionNumbers(tag) ?: return false
        val current = versionNumbers(installed) ?: return false
        for (i in 0 until maxOf(latest.size, current.size)) {
            val a = latest.getOrElse(i) { 0 }
            val b = current.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /**
     * The tag out of GitHub's answer, or null.
     *
     * Only a tag shaped like a version is kept. It ends up in an address
     * Whisper opens, so it is checked rather than trusted.
     */
    fun tagFrom(json: String): String? = runCatching {
        // kotlinx rather than org.json, whose classes are stubs in a unit test.
        Json.parseToJsonElement(json).jsonObject["tag_name"]?.jsonPrimitive?.content?.trim()
            ?.takeIf { TAG.matches(it) }
    }.getOrNull()

    /** The release page for a tag [tagFrom] accepted. */
    fun releasePage(tag: String): String = RELEASE_PAGE + tag

    /**
     * Whether to raise a notification for [tag]: a newer version, and not
     * one already announced. Once per version, so a reader who dismisses it
     * is not asked again every day.
     */
    fun shouldNotify(tag: String, installed: String, alreadyNotified: String): Boolean =
        isNewer(tag, installed) && tag != alreadyNotified
}

/*
 * This file is part of Neo Feed
 * Copyright (c) 2022   Neo Feed Team
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

import android.content.Context
import android.os.Build
import android.text.BidiFormatter
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.entity.SORT_CHRONOLOGICAL
import com.saulhdev.feeder.data.entity.SORT_SOURCE
import com.saulhdev.feeder.data.entity.SORT_TITLE
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format.char
import java.net.MalformedURLException
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

const val THEME_FOLLOW_SYSTEM = "auto_system"
const val THEME_LIGHT = "light"
const val THEME_DARK = "dark"

/** Values written by the old five-entry list, kept only so they can be read. */
const val LEGACY_THEME_SYSTEM_BLACK = "auto_system_black"
const val LEGACY_THEME_BLACK = "black"

fun getThemes(context: Context): Map<String, String> {
    // Three modes. "Black" used to be a fourth and fifth entry — "Black" and
    // "Automatically (from system; black)" — which crossed two independent
    // questions into one list: whether to be dark, and how dark. It is a
    // separate switch now, so the list only answers the first.
    return mapOf(
        THEME_FOLLOW_SYSTEM to context.resources.getString(R.string.theme_auto_system),
        THEME_LIGHT to context.resources.getString(R.string.theme_light),
        THEME_DARK to context.resources.getString(R.string.theme_dark),
    )
}

fun getFonts(context: Context): Map<String, String> {
    return mapOf(
        "inter" to context.resources.getString(R.string.font_inter),
        "system" to context.resources.getString(R.string.font_system),
    )
}

const val LAYOUT_CARDS = "cards"
const val LAYOUT_MAGAZINE = "magazine"
const val LAYOUT_LIST = "list"
const val LAYOUT_MOSAIC = "mosaic"

/** What happens to an article once it has been read. See getReadVisibility. */
const val READ_KEEP = "keep"
const val READ_DIM = "dim"
const val READ_HIDE = "hide"

/**
 * How much an article that has been read should get out of the way.
 *
 * Keep is the default and the safe one: a read article already loses weight,
 * so it shrinks and sinks without disappearing. Hide is the traditional
 * reader's behaviour and belongs to people who treat a feed as an inbox — it
 * is offered, not assumed, because on a surface people scroll idly an article
 * that vanishes is indistinguishable from one that was never there.
 */
fun getReadVisibility(context: Context): Map<String, String> {
    return mapOf(
        READ_KEEP to context.resources.getString(R.string.read_visibility_keep),
        READ_DIM to context.resources.getString(R.string.read_visibility_dim),
        READ_HIDE to context.resources.getString(R.string.read_visibility_hide),
    )
}

/**
 * What "Show articles you have read" turns back on to: the way they were
 * shown before the sheet hid them. It always came back as Keep, so somebody
 * who faded read articles found them unfaded after hiding them once.
 */
fun readVisibilityOnShow(remembered: String): String =
    remembered.takeIf { it == READ_KEEP || it == READ_DIM } ?: READ_KEEP

fun getFeedLayouts(context: Context): Map<String, String> {
    return mapOf(
        LAYOUT_CARDS to context.resources.getString(R.string.layout_cards),
        LAYOUT_MAGAZINE to context.resources.getString(R.string.layout_magazine),
        LAYOUT_LIST to context.resources.getString(R.string.layout_list),
        LAYOUT_MOSAIC to context.resources.getString(R.string.layout_mosaic),
    )
}

fun getSortingOptions(context: Context): Map<String, String> {
    return mapOf(
        SORT_CHRONOLOGICAL to context.resources.getString(R.string.sorting_chronological),
        SORT_TITLE to context.resources.getString(R.string.sorting_title),
        SORT_SOURCE to context.resources.getString(R.string.sorting_source),
    )
}

fun getSyncFrequency(context: Context): Map<String, String> {
    return mapOf(
        "0.5" to context.resources.getString(R.string.sync_half_hour_minutes),
        "1" to context.resources.getString(R.string.sync_one_hour),
        "2" to context.resources.getString(R.string.sync_two_hours),
        "3" to context.resources.getString(R.string.sync_three_hours),
        "6" to context.resources.getString(R.string.sync_six_hours)
    )
}

fun getSyncRange(context: Context): Map<String, String> {
    return mapOf(
        "1d" to context.resources.getString(R.string.sync_range_one_day),
        "2d" to context.resources.getString(R.string.sync_range_two_days),
        "3d" to context.resources.getString(R.string.sync_range_three_days),
        "1w" to context.resources.getString(R.string.sync_range_one_week),
        "1m" to context.resources.getString(R.string.sync_range_one_month)
    )
}

fun getSyncDays(prefs: FeedPreferences): Int {
    return when (prefs.syncRange.getValue()) {
        "1d" -> 1
        "2d" -> 2
        "3d" -> 3
        "1w" -> 7
        "1m" -> 30
        else -> 7
    }
}

fun getItemsPerFeed(): Map<String, String> {
    return mapOf(
        "5" to "5",
        "25" to "25",
        "50" to "50",
        "100" to "100",
        "200" to "200"
    )
}

fun getBackgroundOptions(context: Context): Map<String, String> {
    return mapOf(
        "theme" to context.resources.getString(R.string.background_theme_option),
        "light" to context.resources.getString(R.string.theme_light),
        "dark" to context.resources.getString(R.string.theme_dark)
    )
}

/**
 * Ensures a url is valid, having a scheme and everything. It turns
 * 'google.com' into 'https://google.com' for example.
 *
 * https, not http, and the difference is a bug rather than a preference.
 *
 * This is what the add-a-source search box runs on whatever is typed, so
 * somebody entering `collider.com/feed` — which is how anybody types an
 * address — had a subscription stored as `http://collider.com/feed`. Four of
 * them appeared on one test device in a single afternoon, all added by hand
 * that day, and all listed by the "Feeds still on http" screen as though the
 * publisher were at fault.
 *
 * What hid it is a change made in this app in September. Plaintext is refused
 * outright, so an http address should have failed loudly and been noticed
 * immediately; instead [com.saulhdev.feeder.manager.bookmarks.UpgradeToHttps]
 * rewrites the scheme before the socket opens, the fetch succeeds over https,
 * and the wrong address is quietly written down. A guard against one problem
 * became the thing that concealed another.
 *
 * Nothing is lost by the new default. A host that genuinely speaks only http
 * cannot be fetched by this app either way — the upgrade interceptor tries
 * https regardless of what is stored — so an http default could only ever
 * produce an address that is wrong about a feed that works, which is precisely
 * what it did.
 */
fun sloppyLinkToStrictURL(url: String): URL = try {
    // If no exception, it's valid
    URL(url)
} catch (_: MalformedURLException) {
    URL("https://$url")
}

/**
 * Returns a URL but does not guarantee that it accurately represents the input string if the input string is an invalid URL.
 * This is used to ensure that migrations to versions where Feeds have URL and not strings don't crash.
 */
fun sloppyLinkToStrictURLNoThrows(url: String): URL = try {
    sloppyLinkToStrictURL(url)
} catch (_: MalformedURLException) {
    sloppyLinkToStrictURL("")
}

/**
 * On error, this method simply returns the original link. It does *not* throw exceptions.
 */
fun relativeLinkIntoAbsoluteOrNull(base: URL, link: String?): String? = try {
    // If no exception, it's valid
    if (link != null) {
        relativeLinkIntoAbsoluteOrThrow(base, link).toString()
    } else {
        null
    }
} catch (_: MalformedURLException) {
    link
}

/**
 * On error, this method simply returns the original link. It does *not* throw exceptions.
 */
fun relativeLinkIntoAbsolute(base: URL, link: String): String = try {
    // If no exception, it's valid
    relativeLinkIntoAbsoluteOrThrow(base, link).toString()
} catch (_: MalformedURLException) {
    link
}

/**
 * On error, throws MalformedURLException.
 */
@Throws(MalformedURLException::class)
fun relativeLinkIntoAbsoluteOrThrow(base: URL, link: String): URL = try {
    // If no exception, it's valid
    URL(link)
} catch (_: MalformedURLException) {
    URL(base, link)
}

private val regexImgSrc = """img.*?src=(["'])((?!data).*?)\1""".toRegex(RegexOption.DOT_MATCHES_ALL)

fun naiveFindImageLink(text: String?): String? =
    if (text != null) {
        val imgLink = regexImgSrc.find(text)?.groupValues?.get(2)
        if (imgLink?.contains("twitter_icon", ignoreCase = true) == true) {
            null
        } else {
            imgLink
        }
    } else {
        null
    }

fun String.urlEncode(): String =
    URLEncoder.encode(this, "UTF-8")

fun String.urlDecode(): String =
    URLDecoder.decode(this, "UTF-8")

fun Context.unicodeWrap(text: String): String =
    BidiFormatter.getInstance(getLocale()).unicodeWrap(text)

fun Context.getLocale(): Locale =
    resources.configuration.locales[0]

val FILE_DATETIME_FORMAT = LocalDateTime.Format {
    date(LocalDate.Formats.ISO);
    char('T'); hour(); char('_'); minute(); char('_'); second()
}

object Android {
    fun sdk(sdk: Int): Boolean {
        return Build.VERSION.SDK_INT >= sdk
    }
}

/*
 * This file is part of Neo Feed
 * Copyright (c) 2022   Saul Henriquez <henriquez.saul@gmail.com>
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
package com.saulhdev.feeder.data.content

import android.content.Context
import android.widget.Toast
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.entity.SORT_CHRONOLOGICAL
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.ListDashes
import com.saulhdev.feeder.ui.icons.phosphor.Prohibit
import com.saulhdev.feeder.utils.getFeedLayouts
import com.saulhdev.feeder.utils.LAYOUT_CARDS
import com.saulhdev.feeder.ui.icons.phosphor.Asterisk
import com.saulhdev.feeder.ui.icons.phosphor.BookBookmark
import com.saulhdev.feeder.ui.icons.phosphor.BracketsSquare
import com.saulhdev.feeder.ui.icons.phosphor.Browser
import com.saulhdev.feeder.ui.icons.phosphor.Bug
import com.saulhdev.feeder.ui.icons.phosphor.CaretDown
import com.saulhdev.feeder.ui.icons.phosphor.CaretUp
import com.saulhdev.feeder.ui.icons.phosphor.Circle
import com.saulhdev.feeder.ui.icons.phosphor.Clock
import com.saulhdev.feeder.ui.icons.phosphor.ArrowCounterClockwise
import com.saulhdev.feeder.ui.icons.phosphor.CloudArrowDown
import com.saulhdev.feeder.ui.icons.phosphor.CloudArrowUp
import com.saulhdev.feeder.utils.READ_KEEP
import com.saulhdev.feeder.utils.getReadVisibility
import com.saulhdev.feeder.ui.icons.phosphor.EyeSlash
import com.saulhdev.feeder.ui.icons.phosphor.FunnelSimple
import com.saulhdev.feeder.ui.icons.phosphor.BookOpenUser
import com.saulhdev.feeder.ui.icons.phosphor.Graph
import com.saulhdev.feeder.ui.icons.phosphor.Hash
import com.saulhdev.feeder.ui.icons.phosphor.Info
import com.saulhdev.feeder.ui.icons.phosphor.Sort
import com.saulhdev.feeder.ui.icons.phosphor.Sparkle
import com.saulhdev.feeder.ui.icons.phosphor.Plus
import com.saulhdev.feeder.ui.icons.phosphor.Megaphone
import com.saulhdev.feeder.ui.icons.phosphor.PaintRoller
import com.saulhdev.feeder.ui.icons.phosphor.SubtractSquare
import com.saulhdev.feeder.ui.icons.phosphor.Swatches
import com.saulhdev.feeder.ui.icons.phosphor.Power
import com.saulhdev.feeder.ui.icons.phosphor.WifiHigh
import com.saulhdev.feeder.ui.navigation.NavRoute
import com.saulhdev.feeder.utils.Diagnostics
import com.saulhdev.feeder.manager.sync.SyncWatchdog
import com.saulhdev.feeder.utils.getItemsPerFeed
import com.saulhdev.feeder.utils.getSortingOptions
import com.saulhdev.feeder.utils.getSyncFrequency
import com.saulhdev.feeder.utils.getSyncRange
import com.saulhdev.feeder.utils.LEGACY_THEME_BLACK
import com.saulhdev.feeder.utils.LEGACY_THEME_SYSTEM_BLACK
import com.saulhdev.feeder.utils.THEME_DARK
import com.saulhdev.feeder.utils.THEME_FOLLOW_SYSTEM
import com.saulhdev.feeder.utils.getFonts
import com.saulhdev.feeder.utils.getThemes
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class FeedPreferences private constructor(val context: Context) : KoinComponent {
    private val dataStore: DataStore<Preferences> by inject()

    /**
     * Moves anyone off the retired theme values.
     *
     * "black" and "auto_system_black" are no longer entries, so a preference
     * still holding one would leave the theme dialog with nothing selected.
     * Each maps onto its base mode plus the pure-black switch, which is what
     * they always meant.
     */
    private fun migrateRetiredThemeValues() {
        CoroutineScope(Dispatchers.IO).launch {
            when (overlayTheme.getValue()) {
                LEGACY_THEME_BLACK -> {
                    overlayTheme.setValue(THEME_DARK)
                    pureBlack.setValue(true)
                }

                LEGACY_THEME_SYSTEM_BLACK -> {
                    overlayTheme.setValue(THEME_FOLLOW_SYSTEM)
                    pureBlack.setValue(true)
                }
            }
        }
    }
    /* Theme */
    var overlayTheme = StringSelectionPref(
        titleId = R.string.pref_ovr_theme,
        icon = Phosphor.PaintRoller,
        key = OVERLAY_THEME,
        dataStore = dataStore,
        defaultValue = "auto_system",
        entries = getThemes(context)
    )

    /**
     * True black rather than dark grey, for OLED screens.
     *
     * Separate from the theme rather than two more entries in its list. "Dark"
     * and "Black" answered two different questions — whether to be dark, and
     * how dark — and crossing them meant the list had to carry a "follow
     * system, but black" entry as well. One switch alongside three modes says
     * the same thing without the combinatorics.
     */
    val pureBlack = BooleanPref(
        titleId = R.string.pref_pure_black,
        summaryId = R.string.pref_pure_black_summary,
        icon = Phosphor.Circle,
        key = PURE_BLACK,
        dataStore = dataStore,
        defaultValue = false
    )

    /**
     * Wallpaper-derived theming, off by default.
     *
     * Whisper ships looking like Whisper: the brand palette is the default and
     * Material You is opt-in for anyone who would rather the app follow their
     * wallpaper. See OverlayTheme for how the brand scheme is generated.
     */
    val dynamicColor = BooleanPref(
        titleId = R.string.pref_dynamic_color,
        summaryId = R.string.pref_dynamic_color_summary,
        icon = Phosphor.Swatches,
        key = OVERLAY_DYNAMIC_THEME,
        dataStore = dataStore,
        defaultValue = false
    )

    /**
     * Typeface. Inter is the brand face and the default; "System default" hands
     * typography back to the device, which also respects a user's own font
     * choice on OEMs that offer one.
     */
    var appFont = StringSelectionPref(
        titleId = R.string.pref_app_font,
        icon = Phosphor.BracketsSquare,
        key = APP_FONT,
        dataStore = dataStore,
        defaultValue = "inter",
        entries = getFonts(context)
    )

    var feedLayout = StringSelectionPref(
        titleId = R.string.pref_feed_layout,
        icon = Phosphor.ListDashes,
        key = FEED_LAYOUT,
        dataStore = dataStore,
        defaultValue = LAYOUT_CARDS,
        entries = getFeedLayouts(context),
    )

    /* Glance row */

    /**
     * The strip of status chips above the category filters.
     *
     * Off by default: the weather chip is the only part of the app that talks to
     * a third party at all, so it is something a user opts into rather than
     * something they have to discover and turn off.
     */
    val glanceEnabled = BooleanPref(
        titleId = R.string.pref_glance_row,
        summaryId = R.string.pref_glance_row_summary,
        icon = Phosphor.Clock,
        key = GLANCE_ENABLED,
        dataStore = dataStore,
        defaultValue = false
    )

    /** Display name of the chosen place, empty until one is picked. */
    val glancePlaceName = StringPref(
        titleId = R.string.pref_glance_place,
        icon = Phosphor.Clock,
        key = GLANCE_PLACE_NAME,
        dataStore = dataStore,
        defaultValue = "",
        route = NavRoute.GlanceLocation
    )

    /** "lat,lon" for the chosen place. Stored as text to avoid a float pref pair. */
    val glancePlaceCoords = StringPref(
        titleId = R.string.pref_glance_place,
        icon = Phosphor.Clock,
        key = GLANCE_PLACE_COORDS,
        dataStore = dataStore,
        defaultValue = ""
    )

    var overlayTransparency = FloatPref(
        titleId = R.string.pref_transparency,
        icon = Phosphor.SubtractSquare,
        key = OVERLAY_OPACITY,
        dataStore = dataStore,
        defaultValue = 1f,
        maxValue = 1f,
        minValue = 0f,
        steps = 100,
        specialOutputs = { "${(it * 100).roundToInt()}%" }
    )

    /**
     * Where tapping an article takes you.
     *
     * Replaces the old openInBrowser/offlineReader boolean pair, which encoded
     * three modes across two switches and required the user to guess the
     * combination. "Offline reader" was the worst of it: it selected the built-in
     * reader but read as if it controlled caching, which it never did — articles
     * are cached at sync time regardless of this setting (see RssLocalSync).
     */
    var articleOpenMode = StringSelectionPref(
        titleId = R.string.pref_article_open_mode,
        icon = Phosphor.Browser,
        key = ARTICLE_OPEN_MODE,
        dataStore = dataStore,
        defaultValue = OPEN_MODE_READER,
        entries = mapOf(
            OPEN_MODE_READER to context.getString(R.string.article_open_reader),
            OPEN_MODE_BROWSER to context.getString(R.string.article_open_browser),
        )
    )

    /**
     * Prefetch the full article for every feed, not one feed at a time.
     *
     * The per-source switch stays: this is the blanket answer for people who
     * always want the whole article, and turning it on means never visiting
     * forty source screens to say so. It only widens — a source with its own
     * switch on still prefetches when this is off.
     */
    var fullTextForAllFeeds = BooleanPref(
        titleId = R.string.pref_full_text_all_feeds,
        summaryId = R.string.pref_full_text_all_feeds_summary,
        icon = Phosphor.CloudArrowDown,
        key = FULL_TEXT_ALL_FEEDS,
        dataStore = dataStore,
        defaultValue = false
    )

    /**
     * Whether full articles are downloaded in advance over mobile data too.
     *
     * Off by default. The advance download is the heaviest thing Whisper
     * does - a report showed 265 pages, about 29 MB, in one morning on mobile
     * data, for articles that mostly go unread - and the reader loses nothing
     * by it waiting for Wi-Fi: opening an article fetches its page on the
     * spot, on any network. Covers the per-source switch as well as the
     * blanket one.
     */
    var fullTextOnMobile = BooleanPref(
        titleId = R.string.pref_full_text_on_mobile,
        summaryId = R.string.pref_full_text_on_mobile_summary,
        icon = Phosphor.CloudArrowDown,
        key = FULL_TEXT_ON_MOBILE,
        dataStore = dataStore,
        defaultValue = false
    )

    /**
     * Volume keys page the feed instead of changing the volume.
     *
     * Off by default — an app quietly taking over the volume keys is a
     * surprise, and this one only pays off for people who read one-handed.
     * See VolumeScroll for why this rather than a motion gesture.
     */
    /**
     * What becomes of an article once it has been opened.
     *
     * Saved and pinned articles are never hidden by this, whatever it is set
     * to: those are the two ways a reader has said "keep this", and a setting
     * about tidying away what is finished should not throw away what was
     * deliberately kept.
     */
    /**
     * Whether scrolling past an article marks it read, and how slowly.
     *
     * Off by default. This is a surface people scroll idly and come back to,
     * not an inbox, and a reader who has not asked for this and finds forty
     * articles marked has no way to tell that is what happened.
     */
    /**
     * Clears the unread count in one go.
     *
     * The undo is not here: it is offered on the feed, which is where the
     * change is visible and where a reader who did not mean it will be
     * looking. A dialog here would ask them to confirm something they cannot
     * see, which is the weaker of the two safety nets.
     */
    /**
     * Give a story several sources are covering the biggest slot.
     *
     * Off by default. This is the one thing in the weighting that infers
     * rather than counts — everything else is a tally of something that
     * happened, while this is a judgement that two headlines are about the
     * same event, and it will occasionally be wrong in public. A reader who
     * did not ask for it and finds the wrong article filling the screen has no
     * way to know why.
     *
     * It reads sources filed under a "News" category and no others: the same
     * burst across five review sites is a product launch, and across five
     * football sites a transfer window. Neither wants this treatment, and the
     * reader's own categories are a judgement already made rather than one
     * guessed at here.
     */
    var breakingNews = BooleanPref(
        titleId = R.string.pref_breaking_news,
        summaryId = R.string.pref_breaking_news_summary,
        icon = Phosphor.Megaphone,
        key = BREAKING_NEWS,
        dataStore = dataStore,
        defaultValue = false
    )

    /**
     * Fade sources whose articles the reader consistently passes over.
     *
     * Off by default, like every other rule that acts on something the app
     * inferred rather than something the reader said. This one dims part of
     * the feed on the strength of a guess, and a reader who never turned it on
     * would be entitled to read that as a rendering bug. What it is doing, and
     * to which sources, is on the Learned screen either way.
     */
    var dimSkipped = BooleanPref(
        titleId = R.string.pref_dim_skipped,
        summaryId = R.string.pref_dim_skipped_summary,
        icon = Phosphor.EyeSlash,
        key = DIM_SKIPPED,
        dataStore = dataStore,
        defaultValue = false
    )


    /**
     * Where backups are written, as a persisted tree Uri, or empty.
     *
     * A `StringPref` holding a Uri rather than a type of its own: the picker
     * hands back a string, DataStore stores strings, and the one place that
     * needs it back as a Uri can say so.
     */
    var backupFolder = StringPref(
        titleId = R.string.pref_backup,
        summaryId = R.string.pref_backup_summary,
        icon = Phosphor.CloudArrowUp,
        key = BACKUP_FOLDER,
        dataStore = dataStore,
        route = NavRoute.Backup
    )

    /**
     * Whether Android may include this app in a phone-to-phone transfer.
     *
     * Read by [com.saulhdev.feeder.manager.backup.PlatformBackupAgent] when
     * the system asks, rather than by anything in the app: `allowBackup` is a
     * manifest attribute with no runtime switch, so what a preference can
     * change is not whether a backup runs but whether anything is handed over
     * when it does.
     *
     * A direct copy to the reader's next phone, with no server in it. Off
     * until asked for all the same.
     */
    var platformBackupDevice = BooleanPref(
        titleId = R.string.pref_platform_device,
        summaryId = R.string.pref_platform_device_summary,
        icon = Phosphor.CloudArrowUp,
        key = PLATFORM_BACKUP_DEVICE,
        dataStore = dataStore,
        defaultValue = false,
    )

    /**
     * Whether Android may put this app's data in the reader's cloud backup.
     *
     * A separate question from the transfer above, because it has a different
     * answer for a lot of people: this one puts the subscription list and
     * reading history in Google's hands, and the app's whole claim is that
     * nothing leaves the phone unasked.
     */
    var platformBackupCloud = BooleanPref(
        titleId = R.string.pref_platform_cloud,
        summaryId = R.string.pref_platform_cloud_summary,
        icon = Phosphor.CloudArrowUp,
        key = PLATFORM_BACKUP_CLOUD,
        dataStore = dataStore,
        defaultValue = false,
    )

    /** When the last automatic backup succeeded, for the status line. */
    var backupLastRun = StringPref(
        titleId = R.string.pref_backup,
        icon = Phosphor.CloudArrowUp,
        key = BACKUP_LAST_RUN,
        dataStore = dataStore,
    )

    var account = StringPref(
        titleId = R.string.pref_account,
        summaryId = R.string.pref_account_summary,
        icon = Phosphor.CloudArrowUp,
        key = ACCOUNT,
        dataStore = dataStore,
        route = NavRoute.Account
    )

    var learned = StringPref(
        titleId = R.string.pref_learned,
        summaryId = R.string.pref_learned_summary,
        icon = Phosphor.BookOpenUser,
        key = LEARNED,
        dataStore = dataStore,
        route = NavRoute.Learned
    )

    /**
     * Whether the welcome panes have been shown.
     *
     * Not a setting anybody sees — a fact the app remembers. It is stamped
     * true without showing anything when the app first starts on an install
     * that already has sources, which is how an upgrade is told from a fresh
     * install without a version number to compare against: somebody who has
     * been using this for a year must not be welcomed to it.
     */
    /**
     * Whether the full-text articles cached before the mix-up were discarded.
     *
     * For a while the reader could write one article's page into another
     * article's file: the id came from the navigation argument and changed the
     * instant a card was tapped, while the link came from a database flow that
     * was still delivering the article read before it. The pair existed only
     * for a frame, and it was long enough to fetch the wrong page.
     *
     * The fetch skips a file that already exists, so those never corrected
     * themselves — the reader kept showing the previous article under the
     * right headline for as long as the file survived. Nothing distinguishes a
     * wrong one from a right one by looking at it, so the only honest repair
     * is to drop them all and let them be fetched again.
     *
     * Once, and only for installs that predate the fix. The files are written
     * when somebody opens an article, so the set is small and the cost is a
     * re-fetch of what they have already read.
     */
    var fullTextPurged = BooleanPref(
        titleId = R.string.pref_show_tour,
        icon = Phosphor.Info,
        key = FULL_TEXT_PURGED,
        dataStore = dataStore,
        defaultValue = false,
    )

    var onboardingSeen = BooleanPref(
        titleId = R.string.pref_show_tour,
        icon = Phosphor.Info,
        key = ONBOARDING_SEEN,
        dataStore = dataStore,
        defaultValue = false,
    )

    /**
     * Whether the guided tour has run.
     *
     * Separate from [onboardingSeen] because the two wait for different
     * things. The welcome panes can be shown to an empty app; the tour points
     * at real controls holding real articles, so it has to wait for a first
     * sync — which on a new install is minutes after the welcome, not seconds.
     */
    var tourSeen = BooleanPref(
        titleId = R.string.pref_show_tour,
        icon = Phosphor.Info,
        key = TOUR_SEEN,
        dataStore = dataStore,
        defaultValue = false,
    )

    /**
     * Whether Whisper has asked, once, to be allowed notifications.
     *
     * Asked after the welcome and the tour rather than on first launch, and
     * never again: a refusal is an answer. Nothing but the sync-problems
     * notice and the silent "Syncing" line ever uses it.
     */
    var notificationPermissionAsked = BooleanPref(
        titleId = R.string.pref_show_tour,
        icon = Phosphor.Info,
        key = NOTIFICATION_PERMISSION_ASKED,
        dataStore = dataStore,
        defaultValue = false,
    )

    /**
     * The feeds the reader has subscribed to.
     *
     * It used to be reachable only from a dropdown behind the feed's overflow
     * button, which is a strange place for the second most important screen in
     * the app — and a place nobody looks for it, because a three-dot menu next
     * to a title usually means "settings".
     */
    var sources = StringPref(
        titleId = R.string.title_sources,
        summaryId = R.string.pref_sources_summary,
        icon = Phosphor.Graph,
        key = SOURCES_ROUTE,
        dataStore = dataStore,
        route = NavRoute.Sources
    )

    /** Feeds that have gone quiet, and the offer to find where they went. */
    var brokenFeeds = StringPref(
        titleId = R.string.pref_broken_feeds,
        summaryId = R.string.pref_broken_feeds_summary,
        icon = Phosphor.ArrowCounterClockwise,
        key = BROKEN_FEEDS,
        dataStore = dataStore,
        route = NavRoute.BrokenFeeds
    )

    /** What the reader's own reading looks like, from what is already stored. */
    var statistics = StringPref(
        titleId = R.string.pref_statistics,
        summaryId = R.string.pref_statistics_summary,
        icon = Phosphor.Graph,
        key = STATISTICS,
        dataStore = dataStore,
        route = NavRoute.Statistics
    )

    /** The bundled directory, for somebody who does not know what to follow yet. */
    var feedLibrary = StringPref(
        titleId = R.string.pref_feed_library,
        summaryId = R.string.pref_feed_library_summary,
        icon = Phosphor.BookBookmark,
        key = FEED_LIBRARY,
        dataStore = dataStore,
        route = NavRoute.FeedLibrary
    )

    /** Subscriptions still stored with an http address, and the offer to move them. */
    var insecureFeeds = StringPref(
        titleId = R.string.pref_insecure_feeds,
        summaryId = R.string.pref_insecure_feeds_summary,
        icon = Phosphor.Prohibit,
        key = INSECURE_FEEDS,
        dataStore = dataStore,
        route = NavRoute.InsecureFeeds
    )

    /** The websites the reader already keeps, turned into feeds. */
    var importBookmarks = StringPref(
        titleId = R.string.pref_import_bookmarks,
        summaryId = R.string.pref_import_bookmarks_summary,
        icon = Phosphor.CloudArrowDown,
        key = IMPORT_BOOKMARKS,
        dataStore = dataStore,
        route = NavRoute.BookmarkImport
    )

    /** Sites the reader's own reading keeps pointing at. */
    var suggestions = StringPref(
        titleId = R.string.pref_suggestions,
        summaryId = R.string.pref_suggestions_summary,
        icon = Phosphor.Sparkle,
        key = SUGGESTIONS,
        dataStore = dataStore,
        route = NavRoute.Suggestions
    )


    /** How to put Whisper on a launcher's left-most page. */
    var launcherSetup = StringPref(
        titleId = R.string.pref_launcher,
        summaryId = R.string.pref_launcher_summary,
        icon = Phosphor.Info,
        key = LAUNCHER_SETUP,
        dataStore = dataStore,
        route = NavRoute.Launcher
    )

    /** The row that puts the tour back. */
    var showTour = StringPref(
        titleId = R.string.pref_show_tour,
        summaryId = R.string.pref_show_tour_summary,
        icon = Phosphor.Info,
        key = SHOW_TOUR,
        dataStore = dataStore,
        onClick = {
            CoroutineScope(Dispatchers.IO).launch {
                tourSeen.setValue(false)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.pref_show_tour_queued),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    )

    var markReadOnScroll = FloatPref(
        titleId = R.string.pref_mark_read_on_scroll,
        summaryId = R.string.pref_mark_read_on_scroll_summary,
        icon = Phosphor.Clock,
        key = MARK_READ_ON_SCROLL,
        dataStore = dataStore,
        defaultValue = 0f,
        minValue = 0f,
        maxValue = 10f,
        // Nine intermediate marks over nought to ten is ten intervals of
        // exactly one second. It was twenty, which Compose reads as
        // twenty-one intervals — 0.476s each — so the slider could not be set
        // to a whole number of seconds and reported things like "6.2s".
        steps = 9,
        // A list of five fixed options was both arbitrary and ungrammatical
        // at one of them. The honest shape for a number nobody knows the right
        // value of is the number itself.
        // Short enough to sit in a quarter of the row. It used to be a whole
        // sentence — "After 5.7 seconds on screen, once scrolled past" — which
        // is a summary rather than a value, and it squeezed the track it was
        // meant to be labelling down to nothing. The sentence is in the
        // summary above, where a sentence belongs.
        specialOutputs = {
            if (it < 0.25f) context.getString(R.string.mark_read_off)
            else context.getString(R.string.mark_read_seconds_short, it)
        }
    )

    var readVisibility = StringSelectionPref(
        titleId = R.string.pref_read_visibility,
        summaryId = R.string.pref_read_visibility_summary,
        icon = Phosphor.EyeSlash,
        key = READ_VISIBILITY,
        dataStore = dataStore,
        defaultValue = READ_KEEP,
        entries = getReadVisibility(context)
    )

    /**
     * Keep or Fade, whichever was set when the filter sheet hid read articles,
     * so turning them back on there restores it. No row of its own.
     */
    var readVisibilityBeforeHide = StringPref(
        titleId = R.string.pref_read_visibility,
        icon = Phosphor.EyeSlash,
        key = READ_VISIBILITY_BEFORE_HIDE,
        dataStore = dataStore,
        defaultValue = READ_KEEP,
    )

    var volumeKeyScroll = BooleanPref(
        titleId = R.string.pref_volume_key_scroll,
        summaryId = R.string.pref_volume_key_scroll_summary,
        icon = Phosphor.CaretDown,
        key = VOLUME_KEY_SCROLL,
        dataStore = dataStore,
        defaultValue = false
    )

    var removeDuplicates = BooleanPref(
        titleId = R.string.pref_remove_duplicates,
        icon = Phosphor.FunnelSimple,
        key = REMOVE_DUPLICATES,
        dataStore = dataStore,
        defaultValue = true
    )

    /*SAVE UTILITY PREF*/
    var showBookmarks = BooleanPref(
        titleId = R.string.title_bookmarks,
        icon = Phosphor.BookBookmark,
        key = SHOW_BOOKMARKS,
        dataStore = dataStore,
        defaultValue = false
    )

    /* Sync */
    var syncOnlyOnWifi = BooleanPref(
        titleId = R.string.pref_sync_wifi,
        icon = Phosphor.WifiHigh,
        key = SYNC_ON_WIFI,
        dataStore = dataStore,
        defaultValue = true
    )

    /**
     * Scheduled syncs wait for the charger.
     *
     * A second switch rather than a third entry on one "when to sync" list,
     * and the missing entry is why: a list of "Any time / On Wi-Fi / On Wi-Fi
     * while charging" has nothing to offer the reader on unlimited data who
     * does not want forty feeds fetched off the battery. The two conditions
     * are independent and all four combinations are real, which is exactly
     * when two switches beat a list. (Contrast [articleOpenMode], where two
     * booleans encoded three modes and the fourth combination meant nothing.)
     *
     * Off by default. On, this is a stricter guard than the
     * battery-not-low one the scheduled sync already carries: not-low holds
     * most of the time, charging holds for a few hours a night and for some
     * people not every night. A phone that is rarely plugged in can therefore
     * open the panel to a feed a day and a half old — which is why the summary
     * says what it costs and names pull-to-refresh as the way round it, and
     * why the source list's never-updated and not-updating marks had to exist
     * before this could ship. See MainActivity.configurePeriodicSync.
     */
    var syncOnlyWhenCharging = BooleanPref(
        titleId = R.string.pref_sync_charging,
        summaryId = R.string.pref_sync_charging_summary,
        icon = Phosphor.Power,
        key = SYNC_ONLY_CHARGING,
        dataStore = dataStore,
        defaultValue = false
    )

    var syncFrequency = StringSelectionPref(
        titleId = R.string.pref_sync_frequency,
        icon = Phosphor.Clock,
        key = SYNC_FREQUENCY,
        dataStore = dataStore,
        defaultValue = "1",
        entries = getSyncFrequency(context)
    )

    var syncRange = StringSelectionPref(
        titleId = R.string.pref_sync_range,
        icon = Phosphor.Clock,
        key = SYNC_RANGE,
        dataStore = dataStore,
        defaultValue = "1w",
        entries = getSyncRange(context)
    )

    var itemsPerFeed = StringSelectionPref(
        titleId = R.string.pref_items_per_feed,
        icon = Phosphor.Hash,
        key = ITEMS_PER_FEED,
        dataStore = dataStore,
        defaultValue = "25",
        entries = getItemsPerFeed()
    )

    var blockedWords = StringSetPref(
        titleId = R.string.pref_blocked_words,
        summaryId = R.string.pref_blocked_words_summary,
        icon = Phosphor.Hash,
        key = BLOCKED_WORDS,
        dataStore = dataStore,
        defaultValue = emptySet(),
        route = NavRoute.BlockedWords,
    )

    /* Others */
    var enabledPlugins = StringSetPref(
        titleId = R.string.title_plugin_list,
        icon = Phosphor.Hash,
        key = PLUGINS,
        dataStore = dataStore,
        defaultValue = setOf()
    )

    var about = StringPref(
        titleId = R.string.title_about,
        icon = Phosphor.Info,
        key = ABOUT,
        dataStore = get(),
        route = NavRoute.About
    )

    /**
     * Writes a diagnostics report to Downloads.
     *
     * Deliberately reachable from the phone alone: this app is developed and
     * tested on device, where adb needs a network that is not always available,
     * and an app can read its own logcat without any permission. See Diagnostics.
     */
    /**
     * The same report, handed to the share sheet instead of to Downloads.
     *
     * Export is right for this phone and useless to anyone else's: it leaves a
     * file in Downloads and expects the person to find it, attach it and send
     * it, and most people stop before the end of that sentence. A tester gets
     * the report into whatever they already use to talk to us, in one tap.
     *
     * No note dialog. Whatever they type in their mail or messaging app is the
     * note, and asking twice for the same sentence is how a report gets
     * abandoned. The subject line carries the version and the device so a
     * report is identifiable before it is opened.
     */
    var reportProblem = StringPref(
        titleId = R.string.report_problem,
        summaryId = R.string.report_problem_summary,
        icon = Phosphor.Megaphone,
        key = REPORT_PROBLEM,
        dataStore = dataStore,
        onClick = {
            if (!Diagnostics.begin(context)) return@StringPref
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { Diagnostics.share(context, note = "") }
                    .also { Diagnostics.end() }
                    .onFailure {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.diagnostics_failed),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
            }
        }
    )

    var exportDiagnostics = StringPref(
        titleId = R.string.pref_export_diagnostics,
        summaryId = R.string.pref_export_diagnostics_summary,
        icon = Phosphor.Bug,
        key = EXPORT_DIAGNOSTICS,
        dataStore = dataStore,
        onClick = {
            if (!Diagnostics.begin(context)) return@StringPref
            CoroutineScope(Dispatchers.IO).launch {
                val location = try {
                    Diagnostics.export(context)
                } finally {
                    Diagnostics.end()
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        location?.let { context.getString(R.string.diagnostics_saved, it) }
                            ?: context.getString(R.string.diagnostics_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    )

    /**
     * Detailed logs: the performance and read-tracking traces, for a
     * diagnostics report. In the store builds, so a reader who reports a
     * problem can be asked to turn it on; named for what it is for, where it
     * used to say "Extensive logcat printing".
     */
    var debugging = BooleanPref(
        titleId = R.string.pref_detailed_logs,
        summaryId = R.string.pref_detailed_logs_summary,
        defaultValue = false,
        icon = Phosphor.Bug,
        key = DEBUG,
        dataStore = dataStore,
    )

    /**
     * Shows the stuck-sync notice on demand. Listed only while [debugging] is
     * on: it is for checking the notice, not something to find by accident.
     */
    var testSyncNotice = StringPref(
        titleId = R.string.pref_test_sync_notice,
        summaryId = R.string.pref_test_sync_notice_summary,
        icon = Phosphor.Megaphone,
        key = TEST_SYNC_NOTICE,
        dataStore = dataStore,
        onClick = {
            CoroutineScope(Dispatchers.IO).launch {
                val shown = SyncWatchdog.sendTest(context, this@FeedPreferences)
                if (!shown) withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.test_notice_blocked, Toast.LENGTH_LONG).show()
                }
            }
        }
    )

    /* Sort & Filter */
    /**
     * Categories the feed is narrowed to — an INCLUDE list, empty meaning "All".
     *
     * Deliberately separate from [tagsFilter], which the filter sheet uses as an
     * EXCLUDE list ("hide these"). Both were previously the same preference read
     * with opposite meanings: the article query treated it as an include list
     * while processArticles excluded exactly what that query had selected, so
     * choosing any category reliably produced an empty feed. Selecting a
     * category to browse and muting a category are different intentions and now
     * have different preferences.
     */
    var categoryFilter = StringSetPref(
        titleId = R.string.all_categories,
        icon = Phosphor.Hash,
        key = CATEGORY_FILTER,
        dataStore = dataStore,
        defaultValue = emptySet(),
    )

    var sourcesFilter = StringSetPref(
        titleId = R.string.title_sources,
        icon = Phosphor.Info,
        key = FILTER_SOURCES,
        dataStore = dataStore,
        defaultValue = emptySet(),
    )

    /**
     * Sources the user has asked never to see again, by feed id.
     *
     * Separate from [sourcesFilter] on purpose, even though both end up hiding
     * a source's articles. That one is the filter sheet's scratchpad — set
     * while browsing, reset by the Reset button, and expected to be temporary.
     * This is an answer to "hide this source", which should survive a filter
     * reset and be undone deliberately, from the sources screen.
     */
    /**
     * Sources the reader wants at the top of their list, at most
     * [com.saulhdev.feeder.viewmodels.MAX_PINNED_SOURCES] of them.
     *
     * Chosen over a drag-to-reorder handle, and the reason is worth keeping.
     * A hand-made order has no meaning while the list is sorted by name —
     * name ordering is already total — so a handle either does nothing in
     * three of the four sorts or silently overrides the sort that was picked.
     * Both are controls that lie about what they do. A short pinned list
     * composes with every sort instead: these few sit on top, everything else
     * keeps whatever order was asked for.
     *
     * Feed ids as strings, because DataStore has no set of longs and every
     * other multi-value preference here is already a string set.
     */
    /**
     * The reader's order for categories, one name to a line; empty is
     * alphabetical. See CategoryOrder. Travels with a settings backup, since
     * it describes the reader's categories rather than this phone.
     */
    var categoryOrder = StringPref(
        titleId = R.string.manage_categories,
        icon = Phosphor.Hash,
        key = CATEGORY_ORDER,
        dataStore = dataStore,
        defaultValue = "",
    )

    var pinnedSources = StringSetPref(
        titleId = R.string.title_sources,
        icon = Phosphor.Asterisk,
        key = PINNED_SOURCES,
        dataStore = dataStore,
        defaultValue = emptySet(),
    )

    /**
     * Sources hidden before hiding and switching off became one thing.
     *
     * Kept only so the ids can be carried over once, then cleared. Nothing
     * reads it to decide anything; see NeoApp's migration.
     */
    var hiddenSources = StringSetPref(
        titleId = R.string.title_sources,
        icon = Phosphor.EyeSlash,
        key = HIDDEN_SOURCES,
        dataStore = dataStore,
        defaultValue = emptySet(),
    )

    /**
     * "More like this" and "Less like this", recorded as they are given.
     *
     * Stored as `feedId:score` strings, because DataStore has no map type and a
     * string set is what every other multi-value preference here already uses.
     * Nothing reads this back yet — Milestone 5 will. It is written from the
     * start anyway so that the menu entries do something real from the day they
     * appear, rather than being two buttons that lie until the ranking arrives.
     */
    /**
     * When the reader last pressed "Forget everything", in epoch millis.
     *
     * Read counts are the other half of what the Learned screen shows, and the
     * larger half — they carry ArticleWeight.HABIT_MAX, where More/Less
     * carries 0.35 at full tilt. Clearing them by unmarking articles is not an
     * option: read state is a record of what happened, and "forget what you
     * have learned" does not mean "mark a month of articles unread". So the
     * habit window starts here instead. Nothing is deleted; the counting
     * simply begins again, and the articles stay read.
     *
     * 0 means never reset, which leaves the plain 30-day window in force.
     */
    /**
     * When the scheduled backup last found its destination gone, or 0.
     *
     * Backups stop silently by design: a folder that has been deleted or had
     * its permission revoked will not come back on its own, so the worker
     * gives up rather than failing daily for ever. What was missing is anybody
     * being told. Backups are the thing nobody looks at until something has
     * already gone wrong, and "it stopped six months ago" is the worst moment
     * to find out.
     *
     * Cleared by the next successful write, so it cannot linger after a fix.
     */
    var backupStoppedAt = LongPref(
        key = BACKUP_STOPPED_AT,
        dataStore = dataStore,
        defaultValue = 0L,
    )

    var learnedResetAt = LongPref(
        key = LEARNED_RESET_AT,
        dataStore = dataStore,
        defaultValue = 0L,
    )

    var sourceAffinity = StringSetPref(
        titleId = R.string.title_sources,
        icon = Phosphor.Info,
        key = SOURCE_AFFINITY,
        dataStore = dataStore,
        defaultValue = emptySet(),
    )

    var tagsFilter = StringSetPref(
        titleId = R.string.source_tags,
        icon = Phosphor.Info,
        key = FILTER_TAGS,
        dataStore = dataStore,
        defaultValue = emptySet(),
    )

    /**
     * How the source list was last ordered.
     *
     * Kept in DataStore rather than in the view model, which is what held it
     * before: a view model survives a rotation and nothing else, so the order
     * reset every time the screen was left. Somebody who sorts a hundred
     * sources by category means it, and means it tomorrow.
     *
     * Stored as the enum's name. An unknown value — an order removed in a
     * later version, or a downgrade — falls back to Title rather than
     * throwing, which is the whole reason this is not an ordinal.
     */
    var sourcesSort = StringPref(
        titleId = R.string.sorting_order,
        icon = Phosphor.Sort,
        key = SOURCES_SORT,
        dataStore = dataStore,
        defaultValue = "Title",
    )

    /** Which way round that order runs. */
    var sourcesSortAsc = BooleanPref(
        titleId = R.string.sort_ascending,
        icon = Phosphor.Sort,
        key = SOURCES_SORT_ASC,
        dataStore = dataStore,
        defaultValue = true,
    )

    var sortingFilter = StringSelectionPref(
        titleId = R.string.sorting_order,
        icon = Phosphor.Info,
        key = FILTER_SORT,
        dataStore = dataStore,
        defaultValue = SORT_CHRONOLOGICAL,
        entries = getSortingOptions(context),
    )

    var sortingAsc = BooleanPref(
        titleId = R.string.sorting_order,
        defaultValue = false,
        icon = Phosphor.CaretUp,
        key = FILTER_SORT_ASC,
        dataStore = dataStore,
    )

    init {
        // Before anything reads a preference, and before any screen composes.
        // One read here in place of the fifty-odd blocking round trips that
        // getValue() used to make; everything after this is a field load.
        PrefCache.start(dataStore)
        migrateRetiredThemeValues()
    }

    companion object {
        /**
         * Every key declared below, with its type: what a settings file may
         * put back (see SettingsBackup.import). Filled by the declarations
         * themselves, so a setting added here can be restored without anybody
         * remembering a second list. First in the object, so it exists before
         * the first key is declared.
         */
        private val declared = LinkedHashMap<String, PrefType>()

        /** The keys this version of the app knows, by name, with their types. */
        val DECLARED_KEYS: Map<String, PrefType> get() = declared

        private fun booleanKey(name: String) = booleanPreferencesKey(name).also { declared[name] = PrefType.BOOLEAN }
        private fun floatKey(name: String) = floatPreferencesKey(name).also { declared[name] = PrefType.FLOAT }
        private fun longKey(name: String) = longPreferencesKey(name).also { declared[name] = PrefType.LONG }
        private fun stringKey(name: String) = stringPreferencesKey(name).also { declared[name] = PrefType.STRING }
        private fun stringSetKey(name: String) = stringSetPreferencesKey(name).also { declared[name] = PrefType.STRING_SET }

        val prefsModule = module {
            singleOf(::FeedPreferences)
            singleOf(::provideDataStore)
        }

        private fun provideDataStore(context: Context): DataStore<Preferences> {
            return PreferenceDataStoreFactory.create(
                produceFile = {
                    context.preferencesDataStoreFile("neo_feed")
                },
                migrations = listOf(
                    SharedPreferencesMigration(
                        context,
                        "com.saulhdev.neofeed.prefs"
                    )
                )
            )
        }

        val OVERLAY_THEME = stringKey("pref_overlay_theme")
        val OVERLAY_DYNAMIC_THEME = booleanKey("pref_dynamic_theme")
        val APP_FONT = stringKey("pref_app_font")
        val PURE_BLACK = booleanKey("pref_pure_black")
        val GLANCE_ENABLED = booleanKey("pref_glance_enabled")
        val GLANCE_PLACE_NAME = stringKey("pref_glance_place_name")
        val GLANCE_PLACE_COORDS = stringKey("pref_glance_place_coords")
        val OVERLAY_OPACITY = floatKey("pref_overlay_opacity")
        val ARTICLE_OPEN_MODE = stringKey("pref_article_open_mode")
        val FEED_LAYOUT = stringKey("pref_feed_layout")
        val CATEGORY_FILTER = stringSetKey("pref_category_filter")
        val EXPORT_DIAGNOSTICS = stringKey("pref_export_diagnostics")
        val REPORT_PROBLEM = stringKey("pref_report_problem")

        /** Open the tapped article in Whisper's own reader, using cached content. */
        const val OPEN_MODE_READER = "reader"

        /** Hand the article's URL to the device's default browser. */
        const val OPEN_MODE_BROWSER = "browser"
        val REMOVE_DUPLICATES = booleanKey("pref_remove_duplicates")
        val VOLUME_KEY_SCROLL = booleanKey("pref_volume_key_scroll")
        val READ_VISIBILITY = stringKey("pref_read_visibility")
        val READ_VISIBILITY_BEFORE_HIDE = stringKey("pref_read_visibility_before_hide")
        val MARK_READ_ON_SCROLL = floatKey("pref_mark_read_dwell_seconds")
        val LEARNED = stringKey("pref_learned")
        val STATISTICS = stringKey("pref_statistics")
        val ACCOUNT = stringKey("pref_account")
        val BACKUP_FOLDER = stringKey("pref_backup_folder")
        val BACKUP_LAST_RUN = stringKey("pref_backup_last_run")

        // Spelled out again in PlatformBackupAgent, which the system builds
        // before the preference graph is worth loading. If either name changes
        // here it has to change there, which is why both say so.
        val PLATFORM_BACKUP_DEVICE = booleanKey("pref_platform_backup_device")
        val PLATFORM_BACKUP_CLOUD = booleanKey("pref_platform_backup_cloud")
        val ONBOARDING_SEEN = booleanKey("pref_onboarding_seen")
        val FULL_TEXT_PURGED = booleanKey("pref_full_text_purged")
        val TOUR_SEEN = booleanKey("pref_tour_seen")
        val NOTIFICATION_PERMISSION_ASKED = booleanKey("pref_notification_permission_asked")
        val SHOW_TOUR = stringKey("pref_show_tour")
        val LAUNCHER_SETUP = stringKey("pref_launcher_setup")
        val SOURCES_ROUTE = stringKey("pref_sources_route")
        val SOURCES_SORT = stringKey("pref_sources_sort")
        val SOURCES_SORT_ASC = booleanKey("pref_sources_sort_asc")
        val SUGGESTIONS = stringKey("pref_suggestions")
        val IMPORT_BOOKMARKS = stringKey("pref_import_bookmarks")
        val BROKEN_FEEDS = stringKey("pref_broken_feeds")
        val INSECURE_FEEDS = stringKey("pref_insecure_feeds")
        val FEED_LIBRARY = stringKey("pref_feed_library")
        val BREAKING_NEWS = booleanKey("pref_breaking_news")
        val DIM_SKIPPED = booleanKey("pref_dim_skipped")

        val FULL_TEXT_ALL_FEEDS = booleanKey("pref_full_text_all_feeds")
        val FULL_TEXT_ON_MOBILE = booleanKey("pref_full_text_on_mobile")
        val SHOW_BOOKMARKS = booleanKey("pref_show_bookmarks")
        val SYNC_ON_WIFI = booleanKey("pref_sync_only_wifi")
        val SYNC_ONLY_CHARGING = booleanKey("pref_sync_only_charging")
        val SYNC_FREQUENCY = stringKey("pref_sync_frequency")
        val SYNC_RANGE = stringKey("pref_sync_range")
        val ITEMS_PER_FEED = stringKey("pref_items_per_feed")
        val BLOCKED_WORDS = stringSetKey("pref_blocked_words")
        val PLUGINS = stringSetKey("pref_enabled_plugins")
        val ABOUT = stringKey("pref_about")
        val DEBUG = booleanKey("pref_debugging")
        val TEST_SYNC_NOTICE = stringKey("pref_test_sync_notice")

        // Filter & Sort
        val FILTER_SOURCES = stringSetKey("filter_sources")
        // The stored name stays as it was written. Renaming a DataStore key
        // is not a rename, it is a deletion plus an empty new setting, and
        // nobody's pinned sources are worth tidier spelling.
        val PINNED_SOURCES = stringSetKey("pref_favourite_sources")
        val CATEGORY_ORDER = stringKey("pref_category_order")
        val HIDDEN_SOURCES = stringSetKey("pref_hidden_sources")
        val LEARNED_RESET_AT = longKey("pref_learned_reset_at")
        val BACKUP_STOPPED_AT = longKey("pref_backup_stopped_at")
        val SOURCE_AFFINITY = stringSetKey("pref_source_affinity")
        val FILTER_TAGS = stringSetKey("filter_tags")
        val FILTER_SORT = stringKey("filter_sorting")
        val FILTER_SORT_ASC = booleanKey("filter_sorting_ascending")
    }
}

/**
 * A preference's type, as a settings file names it. DataStore is typed and JSON
 * is not, so the type travels with each value; see SettingsBackup.
 */
enum class PrefType(val tag: String) {
    BOOLEAN("boolean"),
    INT("int"),
    LONG("long"),
    FLOAT("float"),
    STRING("string"),
    STRING_SET("stringSet"),
}

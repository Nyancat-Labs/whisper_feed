package com.saulhdev.feeder.ui.pages

import androidx.compose.runtime.Composable

/**
 * The app's home screen — the feed, and only the feed.
 *
 * Settings and Data sources used to be the second and third pages of a pager
 * behind a navigation bar, which spent a permanent strip of screen on two
 * destinations that are visited rarely. They are ordinary destinations now,
 * reached from the feed's overflow menu, so the feed gets the whole window.
 *
 * A modal dialog used to open over this on every launch, demanding the
 * "Display over other apps" permission. The overlay never needed it — the
 * launcher hosts that window and passes its own token — so the app opened by
 * asking for the most alarming permission Android has, for nothing.
 *
 * @param pageIndex retained so existing `Main(page)` deep links still resolve;
 *   there is only one page now, so it is ignored.
 */
@Composable
fun MainPage(@Suppress("UNUSED_PARAMETER") pageIndex: Int = 0) {
    ArticleListPage()
}

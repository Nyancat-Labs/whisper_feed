# Design audit, 21 October 2026

Whisper checked against what Google asks of an Android app today, read from
Google's own pages on the day (each rule below names its source), and
against the code as it stands on main (commit 6937dba8 and after).

The point is the redesign: build it on this list rather than bolt the list on
afterwards. Each finding says what the rule is, whether it is enforced or
only recommended, what Whisper does, and when to fix it.

**Enforced** means Android or Play makes it happen whatever the app wants.
**Recommended** means Google's quality guidelines ask for it; nothing breaks
without it, but Play's rankings and badges lean on it.

---

## What Whisper already meets

- **Target SDK 37, compile 37.** Play's floor is 36 from 31 August 2026.
  ([target-sdk](https://developer.android.com/google/play/requirements/target-sdk))
- **Edge-to-edge is on** in the app (`enableEdgeToEdge`, transparent bars),
  as Android 15+ enforces. The gaps are in how insets are used; see below.
- **Predictive back is opted into** in the manifest
  (`enableOnBackInvokedCallback="true"`). Again, gaps below.
- **Themed icon**: the adaptive icon has a `<monochrome>` layer.
  ([adaptive icons](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive))
- **SplashScreen API** (core-splashscreen) is used.
- **Notification channels**, three of them, each separate, and the runtime
  notification permission asked once, after onboarding, never nagged.
- **Text in sp** on Material's type scale; no dp-sized text.
- **Line length**: the reader's column is capped at about 75 characters,
  inside Google's 45–75. ([core quality, Line_Length](https://developer.android.com/docs/quality-guidelines/core-app-quality))
- **Adaptive basics**: list-and-detail panes on the feed and the source list
  (material3-adaptive), feed columns by width, no orientation lock. Android
  17 removes the opt-out for apps that do lock, so not locking matters now.
- **Dynamic colour** supported (wallpaper colours on Android 12+), with the
  brand palette as the default and a pure-black option.
- **Accessibility minimum size** left on everywhere (never overridden).

---

## Misses worth fixing soon (small, correctness)

Candidates for 1.0.6 or the release after; none needs a design decision.

**Status, 21 October:** fixed on main for 1.0.6: 1, 2, 3, 5, 7 (web view and
reader), 8, and the unused library entries of 9. Item 4 needed no change
(see it). Item 6 and the style flags of 9 deliberately left; see each.

1. **Side insets are thrown away in landscape.** Most pages replace the
   Scaffold's padding with their own `start/end = 16dp`, so in landscape the
   navigation bar and a camera cutout can sit over content. The launcher
   panel receives left/right insets and never applies them.
   Rule: edge-to-edge, enforced from Android 15 with no opt-out from 16.
   ([setup-e2e](https://developer.android.com/develop/ui/compose/system/setup-e2e))
   Where: `AccountPage.kt:119`, `BackupPage.kt:209`, `LearnedPage.kt:88` and
   the other 16dp pages; `ArticlePage.kt:304`; `OverlayView.kt:449`.
2. **No bottom inset on Settings and Sources.** The last row (and on Sources
   the area under the add button) can sit under the gesture bar.
   `PreferencesPage.kt:197`, `SourceListPage.kt:637`.
3. **Four actions with no spoken label** (TalkBack says "button", or
   nothing). Rule: core quality, Accessibility (recommended).
   - the app's back-to-top button (`ArticleListPage.kt:518`; the panel's has one)
   - "add tag" on the source editor (`SourceEditPage.kt:415`)
   - tapping the reader's lead picture (`ArticlePage.kt:331`)
   - tapping a card's source name to narrow the feed (`ArticleCard.kt:753`)
4. **The source-name tap on cards is about 20dp tall**, under the 48dp
   minimum. It narrows the feed, so a small target here means mis-taps on
   the headline above it. `ArticleCard.kt:752`.
   *On a closer look:* Compose itself widens a small target's touch area to
   48dp where nothing nearer competes for the touch, so the target is
   larger than it looks. Making it taller would add height to every card.
   Left as it is, with a spoken label added; worth a check by hand.
5. **Section headings in Settings are a fixed 48dp tall**, so a heading that
   wraps at large font sizes is cut off. Should be a minimum, not a height.
   `PreferenceGroup.kt:86`. Rule: font scaling to 200% (Android 14+).
6. **Page titles are one line**, so at large font sizes "What Whisper has
   learned" ends in "…" (the owner's own screenshot shows it at normal
   size). `ViewWithActionBar.kt:104`.
   *Left for the redesign:* the large title and the collapsed bar draw the
   same text, so two lines would cut the collapsed one in half. Expressive's
   flexible app bars handle long titles; moved there.
7. **Three back handlers block the system's back animation**, and one is
   wrong (but harmless in practice; see the end of this item): the reader's own handler (`ArticlePage.kt:175`) makes a new,
   empty navigation controller, so its check is always "nothing to go back
   to" and it closes the app. Also the web view (`ComposeWebView.kt:87`) and
   onboarding (`OnboardingPage.kt:138`) take back unconditionally. Rule:
   predictive back, on by default for apps targeting 36+ on Android 16.
   ([predictive back](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture))
   **Checked on the owner's Pixel, 21 October:** from the feed, back returns
   to the feed as it should; the feed's own navigation answers first. So no
   reader is losing their place. What remains is that the handler is wrong
   in its own terms and stops Android's back animation; fix with the rest.
8. **The "Syncing" notification channel's description repeats its name**
   ("Sync status" / "Sync status"), so Android's notification settings say
   nothing about what it is for. `FeedSyncer.kt:341`.
9. **Leftovers to clear** (the library entries are gone; the style flags are
   left until they can be checked on an Android 8–14 phone, since the
   launcher panel's window uses the same theme): the old translucent
   status/navigation flags in
   `styles.xml:68` (contradict edge-to-edge), and the deprecated
   `accompanist-systemuicontroller` still listed in the version catalogue
   (`libs.versions.toml:88`), though nothing uses it.

## With the settings split (before the redesign)

10. **One margin for every page.** Today: 16dp on most, 12 on About, 8 on
    Sources, Add source and the filter sheet. The owner spotted About;
    the rest are the same problem. Pick 16 and hold to it.
11. **One kind of dialog.** Two systems are in use: Material's `AlertDialog`
    on Backup, Learned, Categories and Sources, and a hand-made `BaseDialog`
    on Settings and the source editor. Keep Material's.
12. **Help tidied** as already planned: diagnostics behind one row.
13. **Settings at tablet width**: the categories as a list on the left and
    the chosen page on the right, the same pane scaffold the feed already
    uses. Rule: adaptive quality, Tier 2 (recommended; Play shows a badge
    and ranks such apps higher).
    ([adaptive tiers](https://developer.android.com/docs/quality-guidelines/adaptive-app-quality))

## With the Expressive redesign

Material 3 Expressive is in **beta** (material3 1.5.0-beta01, 7 October
2026); stable is still 1.4.0.

**Decided 21 October: not waiting.** The owner asked whether waiting was
needed; it is not. 1.5.0-beta02 depends only on stable Compose 1.12, so the
rest of the stack stays stable, and the Expressive APIs left experimental
between April and July. Whisper moved to it the same day (BOM 2026.09.00,
material3 pinned ahead of it). One source change: the slider with a custom
track now needs a `SliderState`. `ListItem`'s old form is deprecated; the
rows are rebuilt in the settings split. Back to the BOM's version when
1.5.0 is stable. ([releases](https://developer.android.com/jetpack/androidx/releases/compose-material3))

14. **`MaterialExpressiveTheme` and its motion scheme**, replacing the two
    hand-written springs (picture viewer, swipe) that copy its values.
15. **Expressive components** where Whisper hand-rolls its own: the button
    rows (`ChipsSwitch`) become button groups; the round top-bar buttons
    become standard icon buttons; flexible app bars for the large titles.
16. **Predictive back with progress**: the filter sheet, search and
    selection shrinking with the gesture rather than vanishing at the end.
17. **Tablets and keyboards (Tier 2)**: the filter sheet not full width on
    large screens; a navigation rail where the bottom of the screen is far
    away; Tab and arrow-key focus, Esc to close, hover states. The adaptive
    navigation library is already declared and unused.

## Decide in the open (where a rule and Calm disagree)

- **Read articles fade to 55%, text included.** That very likely takes
  their text below the 4.5:1 contrast Google asks for small text. Calm
  wants read articles quieter. Options: keep as is (it is deliberate);
  fade only the picture and keep text at a readable 70%; or fade nothing
  and mark read another way. Owner's call.
  **Decided 25 October:** fade the picture to about half, text to 80%
  (75% asked for; 80% is the nearest that passes in the light theme).
- **Two splash screens.** Android's own, then Whisper's animated one on a
  cold launch. Google says to migrate custom splashes to the system one.
  The animated one is part of the brand; it could stay as long as it is
  short. Owner's call.
  **Decided 25 October:** keep it, short: its minimum went from 700ms to
  300ms, and it leaves as soon as the feed is ready.
- **Per-app language** (choose Whisper's language apart from the phone's):
  nothing to offer until translations exist (later in the order). Add the
  locale list with the first translation.

## Checked and not applicable

- **16 KB page size** (Play, from 1 February 2027): only affects native
  libraries. Whisper has none today; the on-device AI models would.
- **Mandatory Play accessibility rules**: none found in Google's pages.

## How this was checked

Google's pages were read on 21 October 2026, each with its own "last
updated" date (most 16–20 October 2026). The code was read at commit
6937dba8. Findings with a file and line were read in the code; finding 7's
reader case was confirmed by hand. Nothing here was tested on a device; the
fixes should be.

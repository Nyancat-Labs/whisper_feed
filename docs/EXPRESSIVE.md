# The Expressive redesign — design brief

Draft for the owner, 25 October 2026. Nothing here is built yet; this is what
to agree before any code. Where a choice is the owner's, it says so.

**Direction (decided 25 October):** Material 3 Expressive as Google specifies
it, as closely as possible: its components, shape scale, motion scheme and
colour roles, rather than hand-built look-alikes. Our own touches only where
the spec leaves the choice open. Calm stays the brief for the feed: Expressive
is used for feel (motion, shape, feedback), not for more colour or more
things on screen.

**Library:** `material3` 1.5.0-beta02, already in the app (21 October). Back
to the Compose BOM's version when 1.5.0 is stable.

**Release:** the settings work and the small pieces below go into 1.0.6; the
whole look is 1.1.0 (decided 21 October). Each section says which.

---

## 1. Foundations (1.1.0, first, because everything else rests on them)

### Theme and motion
- `MaterialExpressiveTheme` in place of `MaterialTheme`, with
  `MotionScheme.expressive()`. This alone brings the springier press
  feedback, ripples and component motion Android 16's own apps have.
- Our two hand-written springs (picture viewer, swipe) switch to the
  scheme's `defaultSpatialSpec` / `fastSpatialSpec`, which they copy today.
- Reduced motion (animation scale 0) keeps working: the scheme respects it,
  and our own `reducedMotion()` checks stay.

### Shapes
One scale, Material's, used by kind of element rather than chosen per screen:

| Element | Shape |
|---|---|
| Chips, small buttons | full (pill) |
| Cards, settings groups, menus | large (16dp) |
| Sheets, dialogs | extra large (28dp) |
| Pictures inside cards | medium (12dp) |
| Settings rows inside a group | large outside, extra small (4dp) between rows: the Expressive "segmented list" |

### Colour
- Unchanged: brand palette by default, wallpaper colours by choice, pure
  black. The tertiary role is kept for accents (badges, statistics), never
  for buttons (fixed 25 October).
- Read articles: text 80%, picture about half (decided 25 October).

### Type
- Unchanged sizes. Headlines SemiBold (§8.1).

## 2. Loading and progress (1.1.0)

Google's rule: nothing under 200ms, the **loading indicator** from 200ms to
5s, a **progress indicator** beyond that.

| Case | Component | Notes |
|---|---|---|
| Pull to refresh | `PullToRefreshDefaults.LoadingIndicator` (contained) | Standard 48dp, not shrunk. Fills and turns with the pull, then morphs while syncing. Replaces today's spinner, which blends in. |
| First sync, empty feed | `CircularWavyProgressIndicator`, large, centred | The hollow wavy ring. Determinate when sources are counted (3 of 40), indeterminate before. |
| First sync, empty feed | Skeleton cards under it | Grey shapes of the chosen layout, so the feed's shape is there before the articles. Only while empty and syncing. |
| New articles after a sync | `animateItem()` on the list | They slide in and push the others down, instead of the list jumping. |
| Reader loading full text | Loading indicator, centred in the text area | Today: a spinner. |
| Opening the feed | Nothing | Read from the phone in a few hundredths of a second: an indicator would only flash. |

## 3. The feed (1.1.0)

- **Top bar:** the title row and its round buttons become standard Expressive
  icon buttons (sizes and shapes from the spec). The filter button is already
  sliders, filled while narrowing (25 October).
- **Card menu (⋮):** the Expressive menu: rounder (16dp), darker container,
  the article's source as its header, grouped as decided in §8.3. Not
  interested already has its own icon (thumbs-down, 25 October).
- **Category chips:** unchanged in shape; selection animates with the
  scheme's spring.
- **Back to top:** the standard small FAB, appearing with the scheme's
  spatial spring.
- **Swipe for Not interested:** unchanged behaviour; its spring from the
  scheme.
- **Undo message:** the standard snackbar, now fixed in place (25 October).
- **Save and ⋮ on List rows:** they take two 48dp touch targets, which
  narrows List's headlines, most of all in List with pictures. Re-evaluate
  once the Expressive icon-button sizes are in (owner, 25 October). If still
  cramped: move Save into the ⋮ menu in the two List layouts only.
- **Panel (Lawnchair):** the same, through the same components.

## 4. Filter sheet (1.1.0)

- Sort order as **connected button groups** with plain labels (§8.4).
- Predictive back: the sheet shrinks with the gesture rather than vanishing
  at the end.
- Reset (neutral) and Apply (filled) as a button pair, unchanged in meaning.
- On a tablet: not full width (adaptive Tier 2).

## 5. Reader (1.1.0)

- Feed layout now has five choices (List with pictures added 25 October):
  as buttons, likely a vertical group rather than one row.
- Text size (Small / Default / Large / Larger) moves from a dialog to a
  connected button group in Settings → Reading.
- The comments button: unchanged, at the end.
- Top bar: **flexible app bar** that collapses as you read.

## 6. Settings (stage 2: the parts marked 1.0.6 go first)

- **Flexible app bars** (large title that collapses). This also stops the
  top page's title collapsing when everything already fits. *1.0.6*
- **Segmented groups:** each page's rows in rounded groups, outer corners
  large, inner corners small. *1.0.6*
- **One 16dp margin** on every page (Sources, Add source and the filter sheet
  are 8 today; About 12). *1.0.6*
- **Short choices as connected button groups,** not dialogs: Feed layout,
  Article times, Article text size, Theme. *1.0.6*
- **One dialog system:** Material's `AlertDialog` everywhere; the hand-made
  `BaseDialog` goes. *1.0.6*
- **Master switch** heading pages that are off until turned on: a large
  pill switch (Backup, Account; AI summaries when it exists). *1.1.0*
- **A sample card on Appearance** that changes as the settings do, so a
  layout or text size is seen before leaving Settings. *1.1.0*
- **Tablet:** the section list on the left, the page on the right, the pane
  scaffold the feed already uses (adaptive Tier 2). *1.1.0*

## 7. Everything else (1.1.0)

- **Buttons:** the three tones (positive, neutral, destructive) built 25
  October, moved onto the Expressive button shapes (they change shape when
  pressed).
- **Sources, Add source, Learned, Statistics, Backup, About:** inherit the
  groups, margins, app bars and dialogs above; no screen-specific design.
- **Onboarding and tour:** the welcome panes use the same shapes; the tour's
  highlight takes the large shape.
- **Keyboard and tablets:** focus order, Esc closes, hover states (Tier 2).

## 8. Owner's calls (decided 25 October 2026)

1. **Headline weight: SemiBold**, Expressive's emphasized style. Inter
   SemiBold to be bundled (about 0.3MB). A comparison build first, checked
   side by side on two phones before it is final.
2. **Calm and Expressive, as a preset** in 1.1.0. One step in onboarding:
   two buttons over a sample card that changes as you choose. Not a
   separate look but a starting point for ordinary settings, all in
   Appearance afterwards:
   - *Calm* (the default): no tint, today's look.
   - *Expressive*: cards tinted lightly from their picture or the site's
     icon (Android's Palette library, worked out once per picture and
     cached), the emphasized headline, livelier card shapes.
   - Appearance gets a tint switch and a strength slider; subtle even at
     full strength.
3. **Card menu, grouped by Material's rule:**
   - Share · Pin to the top
   - More like this · Less like this · Why is this here?
   - Not interested · Hide source
   with the source's name as the header.
4. **Sort order as connected button groups:** Sort by (Chronological ·
   Title · Source), then the direction labelled for what you will see:
   *Newest first · Oldest first* for dates, *A–Z · Z–A* for names. Not
   "Ascending / Descending".

## 9. Order of work

1. 1.0.6: Settings items marked *1.0.6* (§6). Then release 1.0.6.
2. 1.1.0: foundations (§1), loading (§2), then screen by screen: feed, filter
   sheet, reader, settings' remaining items, the rest.
3. Each step: a preview build, checked on the owner's Pixel before the next.

## Sources

Material 3 guidelines (m3.material.io), read 25 October 2026: loading
indicator, progress indicators, menus, button groups, app bars, shape and
motion. The design audit (`docs/DESIGN_AUDIT.md`, items 10 to 17) is folded
into §6 and §7.

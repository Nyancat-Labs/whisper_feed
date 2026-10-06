# Prompt — Whisper onboarding and first-run tour

Hand this to a session as the brief. It assumes the repo as it stands and
names the real files.

---

## What to build

Two things, in this order, and the second is worth more than the first:

1. **A welcome screen** on first launch — three panes, the supplied artwork,
   and a Get Started button.
2. **A spotlight tour** of the feed — dim the screen, cut a lit hole over one
   real control at a time, one sentence beside it. The same idea as
   GuideTrain's `apps/web/src/components/Tour.tsx`, translated to Compose.

---

## The translation from GuideTrain, which is the hard part

GuideTrain's tour finds its targets with `document.querySelector(selector)`
and measures them with `getBoundingClientRect()`. Compose has neither. Do not
try to fake a selector system.

**The Compose equivalent:**

- An enum of tour targets — `TourTarget.Chips`, `TourTarget.Filter`, and so on.
- A `CompositionLocal` holding a `MutableMap<TourTarget, Rect>`.
- A `Modifier.tourTarget(TourTarget.X)` that calls `onGloballyPositioned` and
  writes `it.boundsInRoot()` into that map. Attach it to the controls being
  spotlighted; it costs one modifier per target and nothing when no tour is
  running.
- The overlay reads the rect for the current step and draws.

**Drawing the hole.** One full-screen `Canvas` over everything:

```
drawRect(scrim)                                   // the whole screen
drawRoundRect(Color.Transparent, blendMode = BlendMode.Clear)   // the hole
```

The Canvas needs `Modifier.graphicsLayer(compositingStrategy =
CompositingStrategy.Offscreen)` or `BlendMode.Clear` will punch through to
black instead of to the app. This is the single detail most likely to be got
wrong.

**Blocking taps.** Put a `pointerInput` on the scrim that consumes everything.
Keep GuideTrain's rule: *look, don't touch.* Advancing is always the tooltip's
own button, never a tap on the spotlighted control — the tour never has to
guess whether the real interaction happened the way it expected.

**Carry these over as-is**, they are all earned:

- Wait a beat (~250ms) before measuring, so a step that opens something
  animating in is spotlighted where it ends up rather than where it started.
- If a target has no rect after that, advance rather than stall — a control
  that stopped existing must not strand the tour pointing at nothing.
- Place the tooltip below the target, above it if there is no room below, and
  near the bottom of the screen if the target is too big for either. Clamp so
  it is never off-screen.
- Back button skips, as Escape does there.
- Skip counts the same as finishing. The point is "do not show this again",
  not "made it to the end".
- Dots, a counter, Skip and Next, with the last button saying Got it.

---

## What to spotlight

Six steps. Each is a control that already exists:

| Step | Target | Roughly |
|---|---|---|
| 1 | The glance row | Weather, sunset, and how much is waiting |
| 2 | The category chips | Everything, or one subject at a time |
| 3 | Any article card's ⋮ | More like this, less like this, pin, and why an article is the size it is |
| 4 | The bookmarks toggle | Saved articles, always offline |
| 5 | The filter button | Sort, mute a source, hide what you have read |
| 6 | The ⋮ in the header | Settings — layout, what gets prefetched, what Whisper has learned |

Six is already at the limit. Cut before adding.

The feed must have articles in it before this runs, or the tour points at an
empty screen. Either seed a first sync before it starts, or run it after the
first sync completes.

---

## The welcome screen

Three panes, swipeable, with the artwork already in
`docs/brand/07_production/`:

- `onboarding_bg_dark.png` / `onboarding_bg_light.png` — 1080×2400, pick by
  the rendered surface's luminance, as `articlePlaceholder()` in
  `ArticleCard.kt` already does. Text sits over the upper third, which is the
  calm part of both.
- `lockup_horizontal_notag_light.png` / `_dark.png` for the mark.

Copy from the brand board, which is settled:

> **Curate. Read. Breathe.**
> A calmer, more focused way to follow the world.
> [ Get Started ]

Then a pane about adding sources, and a pane about the launcher page.

---

## The launcher pane — offered, not led with

An earlier draft of this brief said the Lawnchair setup step was the thing
that actually mattered and should be built even if the rest slipped. That was
right when Whisper was a Discover replacement and wrong now that it is a
reader that also renders on a launcher page — see ROADMAP.md, "What this is
now". Most people installing this will not have Lawnchair, and a first run
that opens with setup instructions for a launcher they do not use tells them
they have downloaded the wrong app.

So: **one pane, after the tour, and only when it is relevant.**

- Detect whether Whisper is the selected provider — the overlay service being
  bound is the signal.
- Show the pane only when Lawnchair is installed and Whisper is not yet its
  provider. Somebody without Lawnchair should never see it.
- Say plainly what has to happen, in order, and offer a button that opens
  Lawnchair's settings where the platform allows it.
- Dismissible, and reachable again from Settings afterwards.

Frame it as something extra the app can do, not a step that was missed.

---

## Rules

- **Never on an upgrade.** Store `onboardingSeen` as a `BooleanPref` in
  `FeedPreferences` alongside everything else — not SharedPreferences, not a
  file. Existing installs must never see it.
- **Replayable.** A "Show the tour again" row in Settings, next to
  "What Whisper has learned". GuideTrain's `?` button, in this app's idiom.
- **App only, not the launcher overlay.** The overlay is a window in the
  launcher's process with no Activity and no back stack; a modal tour there is
  a bad idea for reasons that have already cost this project time. The tour
  teaches the same controls, since both surfaces share them.
- **Do not trap TalkBack.** The scrim must be `clearAndSetSemantics {}` and the
  tooltip a live region, or the accessibility pass in M8 will have to undo
  this. Skip must be reachable by keyboard and by screen reader.
- **Honour reduced motion.** No animated hole for someone who has asked the
  system for less of it.
- **Tests.** The step machine is pure logic: advancing, skipping, a missing
  target, the last step. Test it. The suite runs on every push.

---

## Where the code goes

```
ui/onboarding/TourTarget.kt      the enum and the CompositionLocal
ui/onboarding/TourOverlay.kt     the scrim, the hole, the tooltip
ui/onboarding/TourSteps.kt       the six steps and their copy
ui/pages/OnboardingPage.kt       the three welcome panes
```

`ArticleListPage.kt` hosts the overlay and owns which step is current, exactly
as `BodyExplorer.tsx` owns it in GuideTrain — the overlay itself should know
nothing about Whisper's screens beyond a target and a sentence.

Every string in `values/strings.xml`. The app is translated into fifteen
languages and onboarding is the worst possible place for hardcoded English.

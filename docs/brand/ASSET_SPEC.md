# Whisper — brand asset specification

Everything the app and its supporting surfaces need, in one list.

Derived from the **Whisper Brand Toolkit** board, the production artwork
supplied separately, and the Android platform requirements for each surface.
Every row says what to export, at what size, in which light/dark variants, and
where it ends up.

- **Status** — `HAVE` = supplied and usable · `PARTIAL` = supplied but not in a
  usable form · `NEED` = not supplied
- **Priority** — `P0` blocks shipping · `P1` needed for planned milestones ·
  `P2` polish / store / web

---

## 0. Decisions

Six questions were open; all are answered. Recorded here because each one
changes what gets exported.

| # | Decision |
|---|---|
| D1 | **The three curved wave bands are the mark**, per the named asset pack. This is the third design: it replaced a straight-tapered-bar mark, which had replaced the concept board's single leaf/"S" form. The concept-board crops in `01_logos/` – `06_ui_mockups/` show the first of those and are historical reference only. |
| D2 | **The board's "SUBTLE GRADIENT" is presentation-only.** Not a brand token, not used in-app. Available for store and marketing backgrounds; exact stops still to be captured if a Play feature graphic is ever made. |
| D3 | **Launcher icon = gradient symbol on near-white** (board concept "02 Light"). Background sampled from the board tile: `#FBFCFE`. |
| D4 | **Inter is bundled**, with an in-app font setting so the user can change it. |
| D5 | **"Knowledge travels further when it's quiet."** does not ship. Held as reserve copy — see §15. |
| D6 | **Distribution is staged:** GitHub Releases now → F-Droid next → Play Store long term. All three sets of assets are specified in §8. |

### Consequence of D3 worth stating plainly

The gradient symbol on a near-white background is the lowest-contrast of the
four board concepts, and the pale Sky-blue bottom bar is its weakest element.
This is a deliberate choice, recorded so it is not rediscovered as a bug. If it
proves illegible at 48 dp on a busy wallpaper, the fallback is board concept
"01 Default" — white symbol on Cobalt — which needs no new artwork.

### Not an asset problem, but blocks real-world install

Lawnchair whitelists feed providers by **package name and signing
certificate**. A Whisper installed from any store still needs the
`/lawnchairdebug` override until `com.nyancatlabs.whisper` is added to Lawnchair's
`FeedBridge.kt` upstream. That is a pull request, not an export.

---

## 1. Universal rules

Apply to every raster deliverable below.

- **PNG-24 with a real alpha channel**, sRGB. No background matte, no white
  fill behind transparent areas, no palette/8-bit PNGs.
- **No pre-applied padding** unless a row explicitly asks for it. Android
  applies its own insets; padding baked into the file gets doubled.
- **No pre-applied corner rounding or shadows** on icon layers. The system
  masks and shadows adaptive icons itself.
- **Square assets must be exactly square**, centred on the true canvas centre.
- Name files exactly as the `Export as` column says. Lower case, underscores.
- Where a row lists `_light` / `_dark`, the suffix describes **the background
  the asset sits on**, not the colour of the asset:
  - `_light` = for use **on light backgrounds** → dark navy artwork
  - `_dark` = for use **on dark backgrounds** → white artwork
- **An SVG always beats a PNG.** Any row marked *(SVG covers this)* disappears
  entirely if the vector master exists.

### Suggested delivery structure

```
whisper_assets/
  vector/        symbol.svg, symbol_flat.svg, wordmark.svg, lockups/*.svg
  icon/          icon_monochrome.png
  logos/         logo_*.png, lockup_*.png, wordmark_*.png
  splash/        splash_icon.png, splash_branding_*.png
  onboarding/    onboarding_bg_*.png
  placeholders/  placeholder_article_*.png, empty_*.png
  store/         feature_graphic.png, screenshots/
  web/           favicon_*.png
```

---

## 2. Vector masters — the highest-value items

One file here removes a dozen rows below.

| Priority | Status | Export as | Format | Variants | Notes |
|---|---|---|---|---|---|
| P1 | NEED | `symbol_flat.svg` | SVG, square viewBox, art centred, **single flat fill** | one | The raster `symbol_navy_flat.png` / `symbol_white_flat.png` now cover the monochrome icon layer and the notification icon, so this is no longer blocking — but it would still remove every rasterisation step and give a clean 24 dp notification glyph and favicons at any size. |
| P1 | NEED | `symbol.svg` | SVG, gradients intact | one | The full-colour mark as vector. Replaces the 1254 px raster for the launcher foreground, splash and About page, and scales without softening. |
| P1 | NEED | `wordmark.svg` | SVG, outlined text | one | "Whisper" with letterforms converted to paths, so there is no font dependency. |
| P1 | NEED | `lockup_horizontal.svg`, `lockup_stacked.svg` | SVG, outlined text | one each | Symbol + wordmark, correctly spaced. Recolourable per variant. |

---

## 3. Launcher icon — Android adaptive icon

Board section: **APP ICON CONCEPTS**. Decision **D3**: concept "02 Light".

The board shows four finished tiles. **Android cannot use finished tiles.** It
composes three independent square layers and applies its own mask, which
differs per launcher (circle, squircle, rounded square, teardrop). Concepts 01,
03 and 04 are the same three layers under different system themes — do not
export them.

### Geometry, stated once

Each layer is a **108 × 108 dp** square. Of that:

- the outer **18 dp on every edge is always cropped** — it exists only for
  parallax and masking;
- the central **72 × 72 dp** is what a mask can show;
- the recommended **safe zone is a 66 dp diameter circle** at the centre —
  keep all artwork inside it.

In percentages of the exported square: **artwork must fit within the centre
61%**, measured as a circle.

| Priority | Status | Asset | Size | Variants | Style rules |
|---|---|---|---|---|---|
| P0 | **HAVE** | `symbol_gradient_01.png` → `ic_launcher_foreground` | 1254×1254 RGBA supplied; downscaled to 432/324/216/162/108 | one | The gradient symbol on full transparency. Cropped to alpha bounds and centred in the safe circle at build time — no manual padding wanted. Superseded by `symbol.svg` when that exists. |
| P0 | **HAVE** | background | — | one | Flat `#FBFCFE`, defined as a colour resource. **No file needed.** |
| P0 | **HAVE** | `symbol_navy_flat.png` → `ic_launcher_monochrome` | 1254×1254 RGBA supplied | one | A true single flat shape — verified to have no interior translucency, so it tints cleanly. Android discards its colour and tints the alpha. Same centre-61% circle. |

Legacy square/round PNG icons are **not** required — `minSdk` is 26, so the
adaptive icon is used on every supported release.

---

## 4. Notification icon

Not on the board. The app currently ships upstream Neo Feed's **Phosphor bell**
— the most visibly wrong asset in the project.

| Priority | Status | Export as | Size | Variants | Style rules |
|---|---|---|---|---|---|
| P0 | **HAVE** | `symbol_white_flat.png` → `ic_notification.png` ×5 | 24, 36, 48, 72, 96 px generated | one | Shipping. It is legible at 24 dp, but the gaps between the bands come down to roughly a pixel, so it is close to its limit. |
| P2 | NEED | `symbol_small.svg` | 24 × 24 dp viewBox | one | An optical variant for small sizes — same mark with wider gaps and slightly heavier bands — rather than a straight scale-down of the full-size artwork. Improves the notification icon and the 16 px favicon. |

---

## 5. Logos and lockups

Board sections: **PRIMARY LOGO**, **SYMBOL MARK**, **ALTERNATE LOGO LOCKUPS** —
all of which show the retired leaf mark (**D1**). The rows below are what the
three-bar mark needs.

Every lockup needs **both** variants — the app has light and dark themes and
follows the system setting.

| Priority | Status | Export as | Size | Variants | Used for |
|---|---|---|---|---|---|
| P1 | HAVE | `symbol_gradient.png` | 1254×1254 RGBA | gradient | launcher foreground, About page, splash |
| P1 | HAVE | `symbol_gradient_alt.png` | 1254×1254 RGBA | gradient, alternate cut | alternate |
| P1 | HAVE | `symbol_navy_flat.png` | 1254×1254 RGBA | flat navy | monochrome icon layer; mark on light surfaces |
| P1 | HAVE | `symbol_white_flat.png` | 1254×1254 RGBA | flat white | notification icon; splash; mark on dark surfaces |
| P1 | HAVE | `lockup_horizontal_light.png` / `_dark.png` | 2172×724 RGBA | both | app bar, splash branding |
| P1 | HAVE | `lockup_stacked_light.png` / `_dark.png` | 1122×1402 RGBA | both | onboarding, About |
| P1 | HAVE | `wordmark_light.png` / `wordmark_dark.png` | 2172×724 RGBA | both | docs, footers |

Tagline handling: every lockup in the pack carries the tagline. A
**without-tagline** horizontal lockup is still wanted for the app bar, where the
tagline is unreadable at that height.

---

## 6. Splash screen

Board mockup: **SPLASH SCREEN**. Not yet implemented.

Uses the Android 12+ `SplashScreen` API, which has its own geometry, unrelated
to the launcher icon.

| Priority | Status | Export as | Size | Variants | Style rules |
|---|---|---|---|---|---|
| P1 | HAVE | `symbol_white_flat.png` → `splash_icon.png` | 288 × 288 dp canvas → **1152×1152 px**, RGBA | one | Artwork must fit inside a **192 dp diameter circle** at the centre of the 288 dp canvas; with an icon background colour set, a **160 dp circle**. Generated from the supplied file when the splash is built. |
| P1 | HAVE | `wordmark_light.png` / `wordmark_dark.png` → `splash_branding_*` | max 200 × 80 dp → **800×320 px**, RGBA | both | The wordmark pinned at the bottom of the splash, scaled to the box. |
| P1 | NEED | background colour | two hex values | light + dark | Single flat colour per theme. The board reads as Cobalt; the dark-theme value is unconfirmed. |

Splash copy: "Whisper" / "Your feeds, your focus." Both already in
`strings.xml`. The third board line does not ship (**D5**).

---

## 7. Onboarding

Board mockup: **ONBOARDING**. Not yet implemented.

| Priority | Status | Export as | Size | Variants | Style rules |
|---|---|---|---|---|---|
| P1 | NEED | `onboarding_bg_dark.png` | 1080 × 2400 px, RGB | dark | The navy wave/mesh gradient. **The only asset in this document that cannot be derived from anything else** — not geometric, no vector equivalent to reconstruct. A mesh gradient or layered SVG would be better still. |
| P1 | NEED | `onboarding_bg_light.png` | 1080 × 2400 px, RGB | light | Light-theme equivalent. The board's header band is close to the right treatment. |
| P2 | NEED | `wave_header.png` | 2400 × 800 px, RGBA | light + dark | The decorative wave from the board header, if reused on any in-app header. Transparent above the wave. |

Onboarding copy, from the board: "Curate. Read. Breathe." /
"A calmer, more focused way to follow the world." / "Get Started"

---

## 8. Distribution — staged (D6)

### 8a. GitHub Releases — near term

| Priority | Status | Asset | Notes |
|---|---|---|---|
| P1 | — | signed APK | Already produced per build. Needs a release signing key, not a debug one. |
| P2 | NEED | `readme_banner.png` — 1280 × 320 px, RGBA | Repo header. Must read on both GitHub themes, so avoid pure white and pure black artwork. |

No store artwork required.

### 8b. F-Droid — intermediate

F-Droid builds from source and takes the launcher icon out of the APK, so
there is no separate store icon. Metadata follows the Fastlane layout at
`fastlane/metadata/android/en-US/`.

| Priority | Status | Asset | Size | Variants |
|---|---|---|---|---|
| P1 | NEED | `phoneScreenshots/1..8.png` | 1080 × 2400 px | **light and dark sets** — F-Droid shows them in order, so lead with the minus-one feed |
| P2 | NEED | `featureGraphic.png` | 1024 × 500 px, RGB no alpha | one |
| P1 | NEED | `short_description.txt` | ≤80 characters | — |
| P1 | NEED | `full_description.txt` | ≤4000 characters | — |
| P1 | — | `changelogs/<versionCode>.txt` | ≤500 characters each | generated per release |

Also required, not artwork: reproducible builds, no proprietary dependencies,
and a metadata pull request to `fdroiddata`.

### 8c. Play Store — long term

| Priority | Status | Asset | Size | Style rules |
|---|---|---|---|---|
| P2 | NEED | `play_icon.png` | **512 × 512 px**, 32-bit PNG | Full-bleed square. **No rounded corners, no shadow** — Play applies its own mask. A flattened composite of §3's foreground over `#FBFCFE`, so generatable once §3 is settled. |
| P2 | NEED | `feature_graphic.png` | **1024 × 500 px**, RGB, no alpha | Text must survive cropping on small screens. The D2 gradient is the intended background here. |
| P2 | NEED | screenshots | 1080 × 2400 px, 2–8 of them | Real device captures, not mockups. |

Also required, not artwork: a Play developer account, a hosted privacy policy,
target-API compliance, and a permissions declaration for
`SYSTEM_ALERT_WINDOW`.

---

## 9. Web / favicons

| Priority | Status | Export as | Size | Variants | Style rules |
|---|---|---|---|---|---|
| P2 | HAVE | `symbol_navy_flat.png` / `symbol_white_flat.png` | 512 × 512 px generated | both | 16 / 32 / 48 / 180 (apple-touch) / 192 / 512 generate from these. At 16 px the band gaps close up; `symbol_small.svg` (§4) would fix that. |

---

## 10. In-app imagery

Not on the board, but the app renders these today with upstream placeholders.

| Priority | Status | Export as | Size | Variants | Used for |
|---|---|---|---|---|---|
| P1 | NEED | `placeholder_article_light.png` / `_dark.png` | 1280 × 720 px (16:9) | both | Shown on every feed card whose article has no image — currently a flat grey rectangle, and it appears constantly on a real feed. |
| P2 | NEED | `empty_feed_light.png` / `_dark.png` | 600 × 600 px, RGBA | both | "No articles found" state |
| P2 | NEED | `empty_bookmarks_light.png` / `_dark.png` | 600 × 600 px, RGBA | both | "No bookmarks" state |

UI glyphs — search, settings, bookmark, refresh, the bottom-nav icons in the
board mockup — are **not** brand assets. The app uses the Phosphor icon set
already, matching upstream. Nothing to supply unless you want them replaced.

---

## 11. Colour — supplied, no action needed

Board section: **COLOR PALETTE**. Already in `app/src/main/res/values/colors.xml`
and used as the Material 3 seed.

| Name | Hex | Role in the app |
|---|---|---|
| Cobalt | `#2563EB` | Material 3 seed colour; splash background |
| Indigo | `#4F46E5` | secondary accent |
| Sky | `#7DD3FC` | tertiary accent |
| Slate | `#475569` | body text, muted UI |
| Soft | `#E5E7EB` | surfaces, dividers |
| *(icon ground)* | `#FBFCFE` | launcher icon background (**D3**, sampled from the board tile) |

The board's "SUBTLE GRADIENT" is presentation-only (**D2**) and is not a token.

---

## 12. Typography — bundled (D4)

Board section: **TYPOGRAPHY (GOOGLE FONTS)**.

| Family | Weights | Action |
|---|---|---|
| Inter | Bold, Medium, Regular, Light | **Bundled** in the APK (~400 KB). Nothing to export — Inter is on Google Fonts under the SIL Open Font License. |

Plus an in-app setting, `Appearance → Font`:

| Option | Notes |
|---|---|
| Inter | default |
| System default | inherits the device font and the user's font-family setting |
| A serif | for article reading; family to be chosen |

---

## 13. Priority summary

**Nothing is blocking.** The named asset pack cleared every P0 item: the
launcher icon ships all three layers from supplied artwork, and the
notification icon is the real mark rather than another project's bell.

**What would help most next, in order:**

1. **`onboarding_bg_dark.png`** — still the only asset in this document with no
   derivable source.
2. **`placeholder_article_light/dark.png`** — seen on every feed card whose
   article has no image, which on a real feed is constant.
3. **A horizontal lockup without the tagline** — for the app bar, where the
   tagline cannot be read at that height.
4. **Vector masters** (§2) — nothing needs them now, but they would remove every
   rasterisation step and fix the small-size cases.

---

## 14. What the repo holds today

In `docs/brand/07_production/` — the named asset pack, RGBA, all verified clean
(~1.5 px antialias band, no interior translucency, no stray pixels):

| File | Covers |
|---|---|
| `symbol_gradient.png` | §3 launcher foreground — **shipping** |
| `symbol_navy_flat.png` | §3 monochrome layer — **shipping**; §9 favicons |
| `symbol_white_flat.png` | §4 notification icon — **shipping**; §6 splash icon |
| `symbol_gradient_alt.png` | §5 alternate |
| `lockup_horizontal_light.png`, `_dark.png` | §5 |
| `lockup_stacked_light.png`, `_dark.png` | §5, §7 |
| `wordmark_light.png`, `_dark.png` | §5, §6 splash branding |

In `docs/brand/01_logos/` … `06_ui_mockups/` — crops from the concept board,
68–423 px, RGB with **no alpha**. **Historical reference only.** They show the
retired leaf mark (**D1**) and are too small and too opaque to ship regardless.

---

## 15. Reserve copy

Written for the brand but not shipping. Kept so it is not lost.

| Line | Origin |
|---|---|
| "Knowledge travels further when it's quiet." | board splash mockup (**D5**) |
| "Less noise. More signal. A calmer you." | board header |
| "Clean feeds. Clearer thinking. Brighter days." | board header |
| "An Android RSS reader for a brighter tomorrow" | board header |
| "A calmer, smarter way to stay informed" | board footer |
| "Android · RSS · Personal · Focused · Always yours" | board footer |

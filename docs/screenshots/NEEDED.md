# Screenshots the README is waiting for

The README has a gallery with these filenames in it. Until the files exist,
GitHub renders a broken-image icon for each, so the gallery is commented out —
uncomment it once the first three are here.

**These must be real screenshots from a real device.** There are UI mockups in
`docs/brand/06_ui_mockups/`, drawn before the interface existed, and they must
not be used here: a concept board presented as a screenshot is a claim about
what the app looks like that the app cannot keep.

## What is still missing

Only one, and it is the one that matters most:

| File | What to capture |
|---|---|
| `02-overlay.png` | The **launcher panel** — swipe right from the Lawnchair home screen. It is the only image that shows the thing no other reader does, and the README currently says "still to come" in its place. |

## Already in

`01-feed-cards`, `03-feed-mosaic`, `04-what-whisper-learned` and `05-sources`.
All four were sterilised before committing: the status bar's notification icons
painted out in each, and the place name removed from the glance chip in the two
feed shots. Half-size, which is crisp at the width the README renders them and
keeps the whole set around a megabyte.

Kept deliberately, and worth knowing if these are ever replaced: the real
subscription list, the real reading counts, and the clock and battery. A
learned-scores screen full of zeroes proves nothing, and a blank status bar
looks doctored.

## Practical notes

- **Portrait, one device, one theme per image.** A gallery of mixed devices
  looks like a gallery of different apps.
- **Use plausible real feeds** — the BBC, the Guardian, Ars Technica. A feed
  called "Test Feed 1" makes the app look unfinished.
- **Have a few articles read** so the dimming is visible, and one bookmarked.
- **Turn the glance row on** for at least the first shot; it is distinctive and
  it is off by default, so nobody discovers it from a screenshot without it.
- **No personal data.** Whatever is on screen is public forever once this repo
  is cloned. Check the place name in the glance row, and the notification
  shade.
- Trim the status bar clutter if you like, but do not fake it.

## Sizing

Straight off the device is fine — GitHub scales them. If they are enormous
(over ~1 MB each) run them through `oxipng` or `pngquant` first; a README that
takes ten seconds to load is a README nobody scrolls.

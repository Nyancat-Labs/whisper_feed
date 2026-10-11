# The README's screenshots

The README's gallery is the Play Store set, the eight styled images made for
the listing on 6 October 2026, at half size (675 × 1200). Half size is crisp
at the width the README draws them and keeps the set under 2.5 MB.

| File | Play caption |
|---|---|
| `01-calm.png` | Your news, calmly. |
| `02-discover.png` | Swipe-right news, your feed. The launcher panel. |
| `03-sizes.png` | Big stories get more room. |
| `04-layouts.png` | Four layouts to choose from. |
| `05-list.png` | The whole list at a glance. |
| `06-reader.png` | Read without the clutter. |
| `07-catch-up.png` | Catch up in one tap. |
| `08-dark.png` | Easy on the eyes at night. |

They replace the first four, taken by hand, which showed the
source cards before they led with the name. When the Play set is redone,
redo these from it, so the README and the listing show the same app.

**These must be real screenshots from a real device.** There are UI mockups in
`docs/brand/06_ui_mockups/`, drawn before the interface existed, and they must
not be used here: a concept board presented as a screenshot is a claim about
what the app looks like that the app cannot keep.

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

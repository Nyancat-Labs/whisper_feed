# Nyancat Labs

The company's symbol, as supplied on 4 October 2026. Whisper is published by
Nyancat Labs; these are the company's marks, not Whisper's.

| File | What it is |
|---|---|
| `symbol_navy.jpg` | 1024 px, on the navy ground. The About page's circle uses this, shrunk to 192 px as `res/drawable-nodpi/nyancat_labs_symbol.webp`, because it reads the same in both themes. |
| `symbol_light.jpg` | 1024 px, on the light ground. |
| `symbol_transparent_1024.png` | 1024 px, transparent. |
| `symbol_transparent_512.png` | 512 px, transparent. |
| `symbol_transparent_64.png` | 64 px, transparent. |
| `symbol_transparent_32.png` | 32 px, transparent, favicon size. |
| `lockup.png` | The full lockup: symbol, "NYANCAT LABS" and "Think Design Develop". Dark lettering, for light grounds. |

## The full pack

`pack/` is the complete asset pack as supplied on 5 October 2026, in its own
folders: `00_Master_Reference` (the approved masters and `ARTWORK_QA.txt`) and
`01_Logos` (app icons at 32-1024 px, a favicon, and every lockup - primary
horizontal, stacked, symbol only, wordmark only, wordmark with tagline - in
navy, white and transparent, at small, medium, large and master sizes). One
file was left out: a copy of `Stacked_Master_Navy.png` named "Name clash",
identical byte for byte.

Worth knowing before using it:

- **The SVG and PDF masters are pictures inside a wrapper**, not drawn
  shapes: each holds one embedded PNG. They scale no better than the PNG
  they contain.
- **`ARTWORK_QA.txt` fails `Wordmark_Only_Medium_Transparent.png`**: its
  corner is not clear - pixel (3, 4) is navy at about 20% opacity - so it
  can show a faint mark on a coloured ground. The other sizes pass.


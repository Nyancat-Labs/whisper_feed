#!/usr/bin/env python3
"""
Writes docs/ICONS.md: every icon and image in the app, where it came from,
and every place the app uses it (owner, 20 November 2026: "if we change the
pin icon, we know all its locations").

    python3 -I scripts/icon-index.py [@phosphor-icons/core package folder ...]

With package folders (npm pack @phosphor-icons/core@2.1.1 and @1.4.9,
unpacked), each icon's drawing is compared number for number with
Phosphor's own, in every weight and version given, so a home-made icon in
the Phosphor folder shows up as one. The icons that came with the fork are
Phosphor 1.4.x drawings; the ones added since are 2.1.1. Without packages,
the source column keeps what the files say about themselves.

IconIndexTest checks the index is in step with the code (which icons exist,
and which files use each); line numbers are a convenience and drift between
runs, so re-run this after moving code about.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MAIN = os.path.join(ROOT, "app", "src", "main")
JAVA = os.path.join(MAIN, "java")
RES = os.path.join(MAIN, "res")
ICONS = os.path.join(JAVA, "com", "saulhdev", "feeder", "ui", "icons", "phosphor")
OUT = os.path.join(ROOT, "docs", "ICONS.md")

NUM = re.compile(r"-?(?:\d+\.\d*|\.\d+|\d+)(?:[eE]-?\d+)?")
COMMAND = re.compile(
    r"^\s*(moveTo|lineTo|horizontalLineTo|verticalLineTo|curveTo|reflectiveCurveTo|quadTo|"
    r"reflectiveQuadTo|arcTo|moveToRelative|lineToRelative|horizontalLineToRelative|"
    r"verticalLineToRelative|curveToRelative|reflectiveCurveToRelative|quadToRelative|"
    r"reflectiveQuadToRelative|arcToRelative)\((.*)\)\s*$"
)


def rel(path):
    return os.path.relpath(path, ROOT)


def numbers_from_text(text):
    return [round(float(n), 2) for n in NUM.findall(text)]


def drawing_of_kotlin(source):
    """The icon's drawing as one list of numbers, booleans as 0/1."""
    strings = re.findall(r'addPathNodes\(\s*"([^"]*)"', source)
    if strings:
        return [n for s in strings for n in numbers_from_text(s)]
    out = []
    for line in source.splitlines():
        m = COMMAND.match(line)
        if not m:
            continue
        args = m.group(2).replace("true", "1").replace("false", "0")
        out += numbers_from_text(args.replace("f", ""))
    return out


NAMES = set()


def kebab(name):
    return re.sub(r"(?<!^)(?=[A-Z])", "-", name).lower()


def phosphor_drawings(package, found):
    version = re.search(r'"version":\s*"([^"]+)"', open(os.path.join(package, "package.json")).read()).group(1)
    assets = os.path.join(package, "assets")
    for weight in sorted(os.listdir(assets)):
        folder = os.path.join(assets, weight)
        for name in sorted(os.listdir(folder)):
            if not name.endswith(".svg"):
                continue
            svg = open(os.path.join(folder, name), encoding="utf-8").read()
            paths = re.findall(r'\sd="([^"]*)"', svg)
            nums = tuple(n for d in paths for n in numbers_from_text(d))
            found.setdefault(nums, f"{version} {name[:-4]} ({weight})")
            NAMES.add(name[:-4])
    return found


def sources():
    for base, _, files in os.walk(JAVA):
        if base.startswith(ICONS):
            continue
        for f in files:
            if f.endswith(".kt") or f.endswith(".java"):
                yield os.path.join(base, f)


def resource_files():
    for base, _, files in os.walk(RES):
        for f in files:
            if f.endswith(".xml"):
                yield os.path.join(base, f)
    yield os.path.join(MAIN, "AndroidManifest.xml")


def usages(pattern, files):
    rx = re.compile(pattern)
    hits = []
    for path in files:
        for i, line in enumerate(open(path, encoding="utf-8"), 1):
            if rx.search(line):
                hits.append(f"{rel(path)}:{i}")
    return hits


def main():
    package = sys.argv[1:] or None
    known = {}
    for folder in package or []:
        phosphor_drawings(folder, known)
    code = list(sources())
    xml = list(resource_files())

    icon_rows = []
    for f in sorted(os.listdir(ICONS)):
        if not f.endswith(".kt"):
            continue
        src = open(os.path.join(ICONS, f), encoding="utf-8").read()
        m = re.search(r"val Phosphor\.(\w+)", src)
        if not m:
            continue
        name = m.group(1)
        drawing = tuple(drawing_of_kotlin(src))
        if package:
            match = known.get(drawing)
            builder = re.search(r'name = "([^"]+)"', src)
            names = {kebab(name), builder.group(1).lower() if builder else ""}
            if match:
                origin = f"Phosphor {match}, exact"
            elif names & NAMES or (name.endswith("Fill") and kebab(name[:-4]) in NAMES):
                origin = "Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1"
            else:
                origin = "**not Phosphor**: no Phosphor icon of that name or drawing"
        else:
            origin = "Phosphor (says so)" if "@phosphor-icons/core" in src else "unchecked"
        used = usages(rf"Phosphor\.{name}\b", code)
        icon_rows.append((name, f, origin, used))

    image_rows = []
    seen = {}
    for base, _, files in os.walk(RES):
        folder = os.path.basename(base)
        if not (folder.startswith("drawable") or folder.startswith("mipmap")):
            continue
        for f in files:
            stem, ext = os.path.splitext(f)
            kind = "mipmap" if folder.startswith("mipmap") else "drawable"
            seen.setdefault((kind, stem), []).append(f"{folder}/{f}")
    for (kind, stem), places in sorted(seen.items()):
        used = usages(rf"R\.{kind}\.{stem}\b", code) + usages(rf"@{kind}/{stem}\b", xml)
        image_rows.append((kind, stem, sorted(places), used))

    lines = [
        "# Icons and images",
        "",
        "Every icon and image in the app, where it came from, and every place the",
        "app uses it. Written by `scripts/icon-index.py`; `IconIndexTest` fails",
        "when this is out of step with the code. To change an icon everywhere,",
        "change its file (one place); to swap which icon a place shows, the",
        "places are listed below. Line numbers drift: re-run the script after",
        "moving code about.",
        "",
        "    python3 -I scripts/icon-index.py <@phosphor-icons/core 2.1.1> <@phosphor-icons/core 1.4.9>",
        "",
        "## Icons (app/src/main/java/com/saulhdev/feeder/ui/icons/phosphor)",
        "",
        "Phosphor Icons are MIT-licensed (see ATTRIBUTION.md). The source column",
        "compares each drawing number for number with Phosphor's, in every weight.",
        "",
    ]
    for name, f, origin, used in icon_rows:
        lines.append(f"### Phosphor.{name}")
        lines.append(f"- File: `{f}`")
        lines.append(f"- Source: {origin}")
        if used:
            lines.append(f"- Used in ({len(used)}):")
            lines += [f"  - `{u}`" for u in used]
        else:
            lines.append("- Used in: nowhere")
        lines.append("")
    lines += [
        "## Images (app/src/main/res)",
        "",
        "Whisper's own artwork (brand marks, weather and Glance icons) unless the",
        "name says otherwise. Each density folder holds a copy of the same image.",
        "",
    ]
    for kind, stem, places, used in image_rows:
        lines.append(f"### {kind}/{stem}")
        lines.append("- Files: " + ", ".join(f"`{p}`" for p in places))
        if used:
            lines.append(f"- Used in ({len(used)}):")
            lines += [f"  - `{u}`" for u in used]
        else:
            lines.append("- Used in: nowhere (or only by the system, e.g. the launcher icon)")
        lines.append("")
    open(OUT, "w", encoding="utf-8").write("\n".join(lines))
    unused = [n for n, _, _, u in icon_rows if not u]
    foreign = [n for n, _, o, _ in icon_rows if o.startswith("**not")]
    print(f"{len(icon_rows)} icons, {len(image_rows)} images; unused icons: {unused}; not Phosphor: {foreign}")


if __name__ == "__main__":
    main()

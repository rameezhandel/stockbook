#!/usr/bin/env python3
"""Generates Stockbook's app icon.

The icon is committed, so this only needs running when the mark changes — it
exists so the design is reproducible and reviewable as code rather than as an
opaque PNG.

    python3 ios/tools/make_app_icon.py

Design notes
------------
Every colour is a Nocturne token; the handoff is explicit that no hard-coded
hexes belong in this app, and that applies to its icon too.

The mark is a ledger seen face on: a cover, a brighter spine down its left
edge, and three entry lines across it.

Two earlier attempts are worth recording, because both failed in ways that only
showed up once rendered. Three bars alone read as a sort or align glyph. Adding
a detached vertical bar beside them fixed that but produced a legible letter
"F" — a vertical stroke with horizontals running right off it is a letterform
before it is an object. Enclosing the entries inside a solid cover removes the
reading entirely: there is no stroke to mistake for a stem.

The accent is filled here rather than outlined. Inside the app nothing is
filled with it — but an icon needs mass to survive being 60pt on a busy home
screen, and a hairline would simply vanish.

It was chosen over the wordmark's `shapes` glyph in an accent-outlined tile
because that tile is a 1px hairline: correct at 38pt inside the app, invisible
at the 60pt an icon is actually seen at. Solid shapes survive the shrink, which
is the only thing an icon has to do.

No rounded corners or masking here — iOS applies its own superellipse, and
baking one in produces a visible double-corner.
"""

from PIL import Image, ImageDraw
import math
import pathlib

# Nocturne tokens
BG = (0x16, 0x18, 0x26)          # --color-bg
ACCENT_900 = (0x2B, 0x27, 0x41)  # --color-accent-900
ACCENT_300 = (0xD2, 0xCE, 0xFD)  # --color-accent-300
ACCENT = (0x91, 0x84, 0xD9)      # --color-accent
ACCENT_700 = (0x5D, 0x52, 0x94)  # --color-accent-700

SIZE = 1024
SUPERSAMPLE = 4  # drawn large and downscaled, for clean edges on the bar caps


def gradient(size: int) -> Image.Image:
    """`linear-gradient(155deg, accent-900, bg)` — the same ground the
    "Sold today" stat card uses, so the icon and the app open on one another."""
    angle = math.radians(155)
    dx, dy = math.sin(angle), -math.cos(angle)

    image = Image.new("RGB", (size, size))
    pixels = image.load()
    extent = abs(dx) * size + abs(dy) * size
    origin = min(0.0, dx * size) + min(0.0, dy * size)

    for y in range(size):
        for x in range(size):
            t = ((x * dx + y * dy) - origin) / extent
            t = max(0.0, min(1.0, t))
            pixels[x, y] = tuple(
                round(start + (end - start) * t)
                for start, end in zip(ACCENT_900, BG)
            )
    return image


def draw_mark(image: Image.Image, scale: int) -> None:
    draw = ImageDraw.Draw(image)

    cover_w, cover_h = 604, 664
    radius = 64
    spine_w = 132

    left = (SIZE - cover_w) // 2
    top = (SIZE - cover_h) // 2

    def box(x0, y0, x1, y1, r, fill):
        draw.rounded_rectangle(
            [x0 * scale, y0 * scale, x1 * scale, y1 * scale],
            radius=r * scale, fill=fill,
        )

    # Cover, then the spine over its left edge — drawn as a rounded rect of the
    # same radius and clipped by overdrawing, so both outer corners stay true.
    box(left, top, left + cover_w, top + cover_h, radius, ACCENT_700)
    box(left, top, left + spine_w + radius, top + cover_h, radius, ACCENT)
    box(left + spine_w, top, left + spine_w + radius, top + cover_h, 0, ACCENT_700)

    # Entries across the cover.
    entry_left = left + spine_w + 76
    entry_h = 62
    entry_gap = 74
    entry_widths = [332, 268, 196]
    block = len(entry_widths) * entry_h + (len(entry_widths) - 1) * entry_gap
    entry_top = top + (cover_h - block) // 2

    for index, width in enumerate(entry_widths):
        y0 = entry_top + index * (entry_h + entry_gap)
        box(entry_left, y0, entry_left + width, y0 + entry_h,
            entry_h // 2, ACCENT_300)


def main() -> None:
    # The ground is smooth, so it is computed once at final size; only the mark
    # needs supersampling, and only its own layer pays for it.
    icon = gradient(SIZE)

    mark = Image.new("RGBA", (SIZE * SUPERSAMPLE, SIZE * SUPERSAMPLE), (0, 0, 0, 0))
    draw_mark(mark, SUPERSAMPLE)
    icon.paste(mark.resize((SIZE, SIZE), Image.LANCZOS), (0, 0),
               mark.resize((SIZE, SIZE), Image.LANCZOS))

    out = pathlib.Path(__file__).resolve().parents[1] / \
        "Stockbook/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    icon.save(out, "PNG")
    print(f"wrote {out} ({out.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()

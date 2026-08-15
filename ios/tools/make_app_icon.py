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

The mark is a ledger: a spine on the left, and three entries beside it running
bright to dim down the accent ramp — a stock list trailing off. The spine is
what makes it a *book* rather than the sort/align glyph that three bars alone
read as.

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

    spine_width = 104
    spine_gap = 68
    bar_height = 112
    bar_gap = 76
    widths = [408, 300, 196]
    colors = [ACCENT_300, ACCENT, ACCENT_700]

    block_height = len(widths) * bar_height + (len(widths) - 1) * bar_gap
    block_width = spine_width + spine_gap + widths[0]
    top = (SIZE - block_height) // 2
    left = (SIZE - block_width) // 2

    # The spine: full height of the entries it holds.
    draw.rounded_rectangle(
        [left * scale, top * scale,
         (left + spine_width) * scale, (top + block_height) * scale],
        radius=(spine_width // 2) * scale,
        fill=ACCENT,
    )

    bar_left = left + spine_width + spine_gap
    for index, (width, color) in enumerate(zip(widths, colors)):
        y0 = top + index * (bar_height + bar_gap)
        draw.rounded_rectangle(
            [bar_left * scale, y0 * scale,
             (bar_left + width) * scale, (y0 + bar_height) * scale],
            radius=(bar_height // 2) * scale,
            fill=color,
        )


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

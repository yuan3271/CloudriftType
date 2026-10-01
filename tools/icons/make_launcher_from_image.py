#!/usr/bin/env python3
"""Turns a supplied launcher artwork PNG into Android adaptive icon layers.

The supplied image is the finished icon on a plain page: a rounded square tile whose blue
backdrop must be kept exactly as drawn. The only thing to remove is the page colour outside
the tile's rounded corners. So:

  1. crop the tile out of the page
  2. flood fill the page colour from the corners to punch the rounded corners transparent,
     leaving the blue backdrop and the artwork untouched
  3. also derive a silhouette (blue keyed out) for the themed/monochrome layer
  4. report the backdrop colours so the vector background layer can match

Usage:
    python3 tools/icons/make_launcher_from_image.py <source.png>
"""

from __future__ import annotations

import colorsys
import pathlib
import sys
from collections import deque

from PIL import Image, ImageFilter

ROOT = pathlib.Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res"
FOREGROUND = RES / "drawable-xxxhdpi/ic_launcher_foreground.png"
MONOCHROME = RES / "drawable-xxxhdpi/ic_launcher_monochrome.png"
PREVIEW = ROOT / "tools/design/launcher-cutout-preview.png"

# Adaptive icon canvas is 108dp; at xxxhdpi that is 4x.
CANVAS = 432
# The tile is drawn at 72dp, its artwork then sits comfortably inside the mask. Filling the
# whole 108dp canvas instead made the launcher mask crop it, which read as a zoomed icon.
SAFE = 288
# The bleed ring is a blurred copy of the tile flattened onto this blue.
BLEND_COLOUR = (79, 140, 253)
BLEED_BLUR = 48

# Saturation is the cleanest separator here: the backdrop is a saturated blue, while the
# cloud and the keyboard are near white / very pale blue.
SATURATION_KEEP = 0.20
SATURATION_DROP = 0.38
MIN_VALUE = 0.55
# Sum of absolute channel differences that still counts as "the page plate".
PAGE_TOLERANCE = 14
# Below this the "subject" is really tile edge residue rather than artwork.
EDGE_THRESHOLD = 90


def crop_tile(image: Image.Image) -> Image.Image:
    """Finds the blue tile inside the light page background."""
    rgb = image.convert("RGB")
    width, height = rgb.size
    pixels = rgb.load()
    left, top, right, bottom = width, height, 0, 0
    step = 2
    for y in range(0, height, step):
        for x in range(0, width, step):
            r, g, b = pixels[x, y]
            _, saturation, value = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if saturation > 0.2 and value > 0.4:
                left = min(left, x)
                right = max(right, x)
                top = min(top, y)
                bottom = max(bottom, y)
    if right <= left or bottom <= top:
        raise SystemExit("could not locate the tile in the source image")
    return rgb.crop((left, top, right + 1, bottom + 1))


def cut_out_page(tile: Image.Image) -> Image.Image:
    """Keeps every pixel of the tile and clears only the page colour in the corners."""
    rgb = tile.convert("RGB")
    out = rgb.convert("RGBA")
    page = flood_fill_page(rgb)
    alpha = Image.new("L", rgb.size, 255)
    alpha_pixels = alpha.load()
    width, height = rgb.size
    for y in range(height):
        row = page[y]
        for x in range(width):
            if row[x]:
                alpha_pixels[x, y] = 0
    out.putalpha(alpha)
    return out


def key_out_backdrop(tile: Image.Image) -> Image.Image:
    """Silhouette only: drops the page *and* the blue backdrop, for the monochrome layer."""
    rgb = tile.convert("RGB")
    out = Image.new("RGBA", rgb.size)
    source = rgb.load()
    target = out.load()
    width, height = rgb.size
    page = flood_fill_page(rgb)
    for y in range(height):
        for x in range(width):
            r, g, b = source[x, y]
            _, saturation, value = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if page[y][x]:
                # The page plate only touches the rounded corners, and filling from the
                # corners cannot reach the cloud because the blue tile walls it off.
                alpha = 0.0
            elif value < MIN_VALUE:
                alpha = 0.0
            elif saturation <= SATURATION_KEEP:
                alpha = 1.0
            elif saturation >= SATURATION_DROP:
                alpha = 0.0
            else:
                span = SATURATION_DROP - SATURATION_KEEP
                alpha = (SATURATION_DROP - saturation) / span
            target[x, y] = (r, g, b, int(round(alpha * 255)))
    return clean_edges(out)


def clean_edges(image: Image.Image) -> Image.Image:
    """Drops the faint ring left by the tile's anti-aliased border.

    Those pixels sit between the blue tile and the page, so their saturation lands mid range
    and they survive as a ghost outline. They are far weaker than anything in the subject, so
    a threshold plus a one pixel erosion removes them without touching the artwork.
    """
    alpha = image.getchannel("A").point(lambda value: 0 if value < EDGE_THRESHOLD else value)
    alpha = alpha.filter(ImageFilter.MinFilter(3))
    cleaned = image.copy()
    cleaned.putalpha(alpha)
    return cleaned


def flood_fill_page(rgb: Image.Image) -> list[list[bool]]:
    """Marks pixels reachable from the corners that match the page colour."""
    width, height = rgb.size
    pixels = rgb.load()
    corner = pixels[0, 0]
    marked = [[False] * width for _ in range(height)]
    queue: deque[tuple[int, int]] = deque(
        [(0, 0), (width - 1, 0), (0, height - 1), (width - 1, height - 1)]
    )
    while queue:
        x, y = queue.popleft()
        if x < 0 or y < 0 or x >= width or y >= height or marked[y][x]:
            continue
        r, g, b = pixels[x, y]
        if abs(r - corner[0]) + abs(g - corner[1]) + abs(b - corner[2]) > PAGE_TOLERANCE:
            continue
        marked[y][x] = True
        queue.append((x + 1, y))
        queue.append((x - 1, y))
        queue.append((x, y + 1))
        queue.append((x, y - 1))
    return marked


def place_in_safe_zone(subject: Image.Image) -> Image.Image:
    """Scales the subject so its longest side fills the safe zone, then centres it."""
    bbox = subject.getbbox()
    if bbox is None:
        raise SystemExit("the keyed subject is empty")
    trimmed = subject.crop(bbox)
    scale = SAFE / max(trimmed.width, trimmed.height)
    resized = trimmed.resize(
        (max(1, round(trimmed.width * scale)), max(1, round(trimmed.height * scale))),
        Image.LANCZOS,
    )
    canvas = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    canvas.paste(
        resized,
        ((CANVAS - resized.width) // 2, (CANVAS - resized.height) // 2),
    )
    return canvas


def fill_canvas(tile: Image.Image) -> Image.Image:
    """Places the finished tile inside the 72dp safe zone of the 108dp canvas.

    The supplied image is the *masked* result, so it has to appear at its own scale. Filling
    the whole canvas with it pushes the artwork into the 18dp bleed ring, and a launcher mask
    only shows the middle two thirds - which reads as the icon being zoomed and cropped.

    The ring around the tile is a heavily blurred copy of the tile, so the bleed continues the
    backdrop gradient instead of showing a hard edge.
    """
    source = tile.convert("RGBA")
    resized = source.resize((SAFE, SAFE), Image.LANCZOS)
    bleed = source.resize((CANVAS, CANVAS), Image.LANCZOS)
    # Flatten before blurring: blurring over transparency would darken the ring.
    backdrop = Image.new("RGB", (CANVAS, CANVAS), BLEND_COLOUR)
    backdrop.paste(bleed, (0, 0), bleed)
    canvas = backdrop.filter(ImageFilter.GaussianBlur(BLEED_BLUR)).convert("RGBA")
    inset = (CANVAS - SAFE) // 2
    canvas.paste(resized, (inset, inset), resized)
    return canvas


def report_background(tile: Image.Image) -> None:
    width, height = tile.size
    points = {
        "top": (width // 2, int(height * 0.06)),
        "upper-left": (int(width * 0.10), int(height * 0.16)),
        "lower-left wave": (int(width * 0.12), int(height * 0.86)),
        "bottom": (width // 2, int(height * 0.94)),
        "right wave": (int(width * 0.92), int(height * 0.80)),
    }
    for label, (x, y) in points.items():
        r, g, b = tile.getpixel((x, y))
        print(f"  {label:<16} #{r:02X}{g:02X}{b:02X}")


def write_preview(foreground: Image.Image, monochrome: Image.Image) -> None:
    """Foreground and silhouette over light and dark plates, so stray keying shows up."""
    gap = 24
    board = Image.new("RGB", (CANVAS * 4 + gap * 3, CANVAS), (18, 21, 27))
    for index, (layer, plate) in enumerate(
        (
            (foreground, (242, 244, 250)),
            (foreground, (24, 28, 38)),
            (monochrome, (242, 244, 250)),
            (monochrome, (24, 28, 38)),
        )
    ):
        background = Image.new("RGB", (CANVAS, CANVAS), plate)
        background.paste(layer, (0, 0), layer)
        board.paste(background, (index * (CANVAS + gap), 0))
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    board.save(PREVIEW)
    print(f"wrote {PREVIEW.relative_to(ROOT)}")


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    source = pathlib.Path(sys.argv[1])
    if not source.exists():
        print(f"! missing {source}", file=sys.stderr)
        return 1

    image = Image.open(source)
    print(f"source {image.size[0]}x{image.size[1]}")
    tile = crop_tile(image)
    print(f"tile {tile.width}x{tile.height}")
    print("background samples:")
    report_background(tile)

    foreground = fill_canvas(cut_out_page(tile))
    FOREGROUND.parent.mkdir(parents=True, exist_ok=True)
    foreground.save(FOREGROUND)
    print(f"wrote {FOREGROUND.relative_to(ROOT)} ({foreground.width}x{foreground.height})")

    monochrome = place_in_safe_zone(key_out_backdrop(tile))
    monochrome.save(MONOCHROME)
    print(f"wrote {MONOCHROME.relative_to(ROOT)} ({monochrome.width}x{monochrome.height})")

    write_preview(foreground, monochrome)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

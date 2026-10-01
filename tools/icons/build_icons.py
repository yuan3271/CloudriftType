#!/usr/bin/env python3
"""Generate the 云隙 icon set.

Why this file exists
--------------------
The keyboard originally shipped Material Symbols, which reads as "stock Google" next to a
HyperOS / HarmonyOS style surface. There is no drop-in alternative: Xiaomi never open
sourced a UI icon library (only the MiSans typeface), and HarmonyOS Symbol is proprietary -
the OpenHarmony app repositories only carry ~28 system-settings glyphs, nowhere near enough
for an input method. The domestic open icon sets (TDesign, Arco, Ant Design) are all
geometric outline styles, which is the opposite of the soft, filled, heavily rounded look
asked for.

So the set is authored here instead, following the HyperOS / HarmonyOS grid rules:

  * 24 x 24 grid, live area 2..22, so every glyph keeps a 2dp optical margin
  * 2.0 stroke weight for outline glyphs, everything else solid
  * round caps and round joins everywhere - no square terminals
  * corners rounded to at least 2.5 so nothing reads as sharp
  * one geometry per concept: no perspective, no gradients, no thin details

Because the geometry is computed rather than typed by hand, the generated Compose code and
the review sheet in tools/design cannot drift apart.

Usage:
    python3 tools/icons/build_icons.py
"""

from __future__ import annotations

import math
import pathlib
from dataclasses import dataclass, field

ROOT = pathlib.Path(__file__).resolve().parents[2]
KOTLIN_OUT = ROOT / "app/src/main/java/com/yuan3271/cloudrift/ui/icons/CloudriftIcons.kt"
PREVIEW_OUT = ROOT / "tools/design/icons-preview.svg"
FOREGROUND_OUT = ROOT / "app/src/main/res/drawable/ic_launcher_foreground.xml"
MONOCHROME_OUT = ROOT / "app/src/main/res/drawable/ic_launcher_monochrome.xml"
FOREGROUND_LEGACY = ROOT / "app/src/main/res/drawable/ic_launcher_foreground_legacy.xml"
BACKGROUND_OUT = ROOT / "app/src/main/res/drawable/ic_launcher_background.xml"
PREVIEW_LAUNCHER_OUT = ROOT / "tools/design/launcher-preview.svg"

# Launcher gradient. Deeper at the bottom so the mark reads as floating in daylight.
BACKGROUND_TOP = "#5397FC"
BACKGROUND_BOTTOM = "#4374F0"
# The picked design has a soft hill across the lower third of the tile.
BACKGROUND_WAVE = "#4D97FC"
BACKGROUND_WAVE_ALPHA = "0.45"
BACKGROUND_WAVE_PATH = "M0,76 C28,62 60,84 108,66 L108,108 L0,108 Z"

# Which candidate is written into the app resources. The sheet renders all of them.
# "image" means the artwork arrives as a PNG and only the background layer is generated here;
# tools/icons/make_launcher_from_image.py produces the foreground. The user asked to keep the
# original blue backdrop, so the background layer reproduces those sampled colours.
LAUNCHER_VARIANT = "image"

# Shallow rift: crosses the cloud from lower-left to upper-right.
RIFT_SLOPE = 0.25
RIFT_LOWER_AT_X0 = 18.2
RIFT_GAP = 2.6

# Steep rift: splits the cloud into two diagonal wedges.
STEEP_SLOPE = 0.75
STEEP_LOWER_AT_X0 = 20.6
STEEP_GAP = 2.4

GRID = 24.0
STROKE = 2.0


# --------------------------------------------------------------------------------------
# geometry helpers - everything is emitted as SVG path data on the 24 unit grid
# --------------------------------------------------------------------------------------


def _n(value: float) -> str:
    return f"{value:.2f}".rstrip("0").rstrip(".")


def point(x: float, y: float) -> str:
    return f"{_n(x)},{_n(y)}"


def line(x1: float, y1: float, x2: float, y2: float) -> str:
    return f"M{point(x1, y1)} L{point(x2, y2)}"


def circle(cx: float, cy: float, r: float) -> str:
    return (
        f"M{point(cx - r, cy)} "
        f"a{_n(r)},{_n(r)} 0 1,0 {_n(2 * r)},0 "
        f"a{_n(r)},{_n(r)} 0 1,0 {_n(-2 * r)},0 Z"
    )


def ring(cx: float, cy: float, r: float) -> str:
    """A stroked circle: same as [circle] but without closing the path twice."""
    return (
        f"M{point(cx - r, cy)} "
        f"a{_n(r)},{_n(r)} 0 1,0 {_n(2 * r)},0 "
        f"a{_n(r)},{_n(r)} 0 1,0 {_n(-2 * r)},0"
    )


def arc(cx: float, cy: float, r: float, start: float, end: float) -> str:
    """Arc on a circle. Angles in degrees, clockwise on screen (y grows downward)."""
    x0 = cx + r * math.cos(math.radians(start))
    y0 = cy + r * math.sin(math.radians(start))
    x1 = cx + r * math.cos(math.radians(end))
    y1 = cy + r * math.sin(math.radians(end))
    delta = (end - start) % 360
    large = 1 if delta > 180 else 0
    return f"M{point(x0, y0)} A{_n(r)},{_n(r)} 0 {large},1 {point(x1, y1)}"


def round_rect(x: float, y: float, w: float, h: float, r: float) -> str:
    return (
        f"M{point(x + r, y)} H{_n(x + w - r)} "
        f"A{_n(r)},{_n(r)} 0 0,1 {point(x + w, y + r)} V{_n(y + h - r)} "
        f"A{_n(r)},{_n(r)} 0 0,1 {point(x + w - r, y + h)} H{_n(x + r)} "
        f"A{_n(r)},{_n(r)} 0 0,1 {point(x, y + h - r)} V{_n(y + r)} "
        f"A{_n(r)},{_n(r)} 0 0,1 {point(x + r, y)} Z"
    )


def poly_round(points: list[tuple[float, float]], radius: float) -> str:
    """Closed polygon with every corner replaced by an arc of [radius]."""
    count = len(points)
    parts: list[str] = []
    for index in range(count):
        prev = points[(index - 1) % count]
        cur = points[index]
        nxt = points[(index + 1) % count]

        def unit(a: tuple[float, float], b: tuple[float, float]) -> tuple[float, float]:
            dx, dy = b[0] - a[0], b[1] - a[1]
            length = math.hypot(dx, dy) or 1.0
            return dx / length, dy / length

        u_prev = unit(cur, prev)
        u_next = unit(cur, nxt)
        # Interior angle between the two edges; clamp so near-straight corners stay stable.
        cosine = max(-1.0, min(1.0, u_prev[0] * u_next[0] + u_prev[1] * u_next[1]))
        theta = math.acos(cosine)
        if theta < 0.05 or abs(math.pi - theta) < 0.05:
            parts.append(f"L{point(*cur)}")
            continue
        trim = radius / math.tan(theta / 2)
        max_trim = min(
            math.dist(cur, prev) / 2,
            math.dist(cur, nxt) / 2,
        )
        trim = min(trim, max_trim)
        start = (cur[0] + u_prev[0] * trim, cur[1] + u_prev[1] * trim)
        end = (cur[0] + u_next[0] * trim, cur[1] + u_next[1] * trim)
        cross = u_prev[0] * u_next[1] - u_prev[1] * u_next[0]
        sweep = 1 if cross < 0 else 0
        if not parts:
            parts.append(f"M{point(*start)}")
        else:
            parts.append(f"L{point(*start)}")
        parts.append(f"A{_n(radius)},{_n(radius)} 0 0,{sweep} {point(*end)}")
    parts.append("Z")
    return " ".join(parts)


def rays(cx: float, cy: float, inner: float, outer: float, count: int, offset: float = 0.0) -> str:
    parts = []
    for index in range(count):
        angle = math.radians(offset + index * 360.0 / count)
        parts.append(
            line(
                cx + inner * math.cos(angle),
                cy + inner * math.sin(angle),
                cx + outer * math.cos(angle),
                cy + outer * math.sin(angle),
            )
        )
    return " ".join(parts)


def arrow_head(tip: tuple[float, float], direction: float, size: float = 3.4, spread: float = 34.0) -> str:
    """A chevron whose point sits at [tip] and which opens away from [direction]."""
    back = direction + 180
    arms = []
    for offset in (back + spread, back - spread):
        angle = math.radians(offset)
        arms.append(
            line(tip[0], tip[1], tip[0] + size * math.cos(angle), tip[1] + size * math.sin(angle))
        )
    return " ".join(arms)


def point_on(cx: float, cy: float, r: float, angle: float) -> tuple[float, float]:
    return (
        cx + r * math.cos(math.radians(angle)),
        cy + r * math.sin(math.radians(angle)),
    )


@dataclass
class Shape:
    data: str
    stroke: bool = True
    width: float = STROKE


@dataclass
class Icon:
    name: str
    label: str
    shapes: list[Shape] = field(default_factory=list)


def stroke(data: str, width: float = STROKE) -> Shape:
    return Shape(data, stroke=True, width=width)


def solid(data: str) -> Shape:
    return Shape(data, stroke=False)


# --------------------------------------------------------------------------------------
# the set
# --------------------------------------------------------------------------------------


def build() -> list[Icon]:
    icons: list[Icon] = []

    # --- voice -------------------------------------------------------------------
    icons.append(
        Icon(
            "Mic",
            "Mic",
            [
                stroke(round_rect(9, 2.6, 6, 11.2, 3)),
                stroke(arc(12, 11.4, 6.4, 20, 160)),
                stroke(line(12, 17.8, 12, 21.2)),
                stroke(line(8.6, 21.2, 15.4, 21.2)),
            ],
        )
    )
    icons.append(
        Icon(
            "Stop",
            "Stop",
            [solid(round_rect(7, 7, 10, 10, 3.2))],
        )
    )
    icons.append(
        Icon(
            "Waveform",
            "Waveform",
            [
                stroke(line(4, 10, 4, 14)),
                stroke(line(8, 6.5, 8, 17.5)),
                stroke(line(12, 8.8, 12, 15.2)),
                stroke(line(16, 5, 16, 19)),
                stroke(line(20, 10, 20, 14)),
            ],
        )
    )

    # --- editing -----------------------------------------------------------------
    icons.append(
        Icon(
            "Backspace",
            "Backspace",
            [
                stroke(
                    poly_round(
                        [(9.6, 4.6), (19.4, 4.6), (21.6, 6.8), (21.6, 17.2),
                         (19.4, 19.4), (9.6, 19.4), (7.4, 17.2), (2.6, 12.0),
                         (7.4, 6.8)],
                        1.6,
                    )
                ),
                stroke(line(11.2, 9.6, 16.8, 14.4)),
                stroke(line(16.8, 9.6, 11.2, 14.4)),
            ],
        )
    )
    icons.append(Icon("Shift", "Shift", [
        stroke(poly_round([(12, 3.6), (20, 11.6), (15.4, 11.6), (15.4, 20.4),
                           (8.6, 20.4), (8.6, 11.6), (4, 11.6)], 1.8)),
    ]))
    icons.append(Icon("Return", "Return", [
        stroke("M20,5.6 V13 A1.6,1.6 0 0 1 18.4,14.6 H4.6"),
        stroke(line(9, 10, 4.4, 14.6)),
        stroke(line(9, 19.2, 4.4, 14.6)),
    ]))
    icons.append(Icon("Close", "Close", [
        stroke(line(6.8, 6.8, 17.2, 17.2)),
        stroke(line(17.2, 6.8, 6.8, 17.2)),
    ]))
    icons.append(Icon("Check", "Check", [
        stroke("M5.6,12.6 L10.2,17.2 L18.4,6.8"),
    ]))
    icons.append(Icon("Refresh", "Refresh", [
        stroke(arc(12, 12, 7.4, 250, 560)),
        # Head sits on the arc's end point, pointing the way the arc travels.
        stroke(arrow_head(point_on(12, 12, 7.4, 200), direction=-70)),
    ]))
    icons.append(Icon("Send", "Send", [
        solid(poly_round([(20.6, 3.4), (10.6, 13.6), (3.6, 10.6), (20.6, 3.4)], 1.4)
              if False else
              "M20.6,3.4 L3.6,10.6 L10.8,13.2 L13.4,20.4 Z"),
    ]))

    # --- navigation --------------------------------------------------------------
    icons.append(Icon("ArrowBack", "ArrowBack", [
        stroke(line(20, 12, 4.4, 12)),
        stroke(line(10.4, 5.6, 4, 12)),
        stroke(line(10.4, 18.4, 4, 12)),
    ]))
    icons.append(Icon("ExpandMore", "ExpandMore", [
        stroke(line(5.6, 9.2, 12, 15.6)),
        stroke(line(18.4, 9.2, 12, 15.6)),
    ]))
    icons.append(Icon("More", "More", [
        solid(circle(5.6, 12, 1.9)),
        solid(circle(12, 12, 1.9)),
        solid(circle(18.4, 12, 1.9)),
    ]))

    # --- keyboard chrome ---------------------------------------------------------
    icons.append(Icon("KeyboardHide", "KeyboardHide", [
        stroke(line(5.6, 7.6, 12, 14)),
        stroke(line(18.4, 7.6, 12, 14)),
        stroke(line(4.8, 19, 19.2, 19)),
    ]))
    icons.append(Icon("Keyboard", "Keyboard", [
        # Fewer, bigger elements: the six-key version collapsed into a dark blob at 20dp.
        stroke(round_rect(2.7, 6.0, 18.6, 12.0, 3.0), width=1.9),
        solid(circle(7.0, 10.0, 1.15)),
        solid(circle(12.0, 10.0, 1.15)),
        solid(circle(17.0, 10.0, 1.15)),
        stroke(line(9.0, 14.6, 15.0, 14.6), width=1.9),
    ]))

    # --- system ------------------------------------------------------------------
    icons.append(Icon("Globe", "Globe", [
        stroke(ring(12, 12, 8.6)),
        stroke("M12,3.4 C8.6,6.6 8.6,17.4 12,20.6 C15.4,17.4 15.4,6.6 12,3.4 Z"),
        stroke(line(3.6, 12, 20.4, 12)),
    ]))
    icons.append(Icon("Settings", "Settings", [
        stroke(line(3.6, 7.4, 20.4, 7.4)),
        stroke(line(3.6, 12, 20.4, 12)),
        stroke(line(3.6, 16.6, 20.4, 16.6)),
        solid(circle(9.2, 7.4, 2.5)),
        solid(circle(15.4, 12, 2.5)),
        solid(circle(9.2, 16.6, 2.5)),
    ]))
    icons.append(Icon("Sun", "Sun", [
        stroke(ring(12, 12, 4.4)),
        stroke(rays(12, 12, 7.4, 9.8, 8, offset=-90)),
    ]))
    icons.append(Icon("Moon", "Moon", [
        solid(
            "M20.4,14.6 A8.6,8.6 0 1,1 9.4,3.6 "
            "A6.9,6.9 0 0,0 20.4,14.6 Z"
        ),
    ]))
    icons.append(Icon("AutoMode", "AutoMode", [
        stroke(ring(12, 12, 8.4)),
        solid(arc(12, 12, 8.4, -90, 90) + f" L{point(12, 12)} Z"),
    ]))
    icons.append(Icon("Cloud", "Cloud", [
        solid(
            "M7.4,19.4 A4.6,4.6 0 0 1 7.0,10.3 A5.4,5.4 0 0 1 17.2,9.4 "
            "A4.0,4.0 0 0 1 16.8,19.4 Z"
        ),
    ]))
    icons.append(Icon("Info", "Info", [
        stroke(ring(12, 12, 8.6)),
        stroke(line(12, 10.8, 12, 17)),
        solid(circle(12, 7.4, 1.35)),
    ]))
    icons.append(Icon("Key", "Key", [
        stroke(ring(15.4, 8.6, 4.4)),
        stroke(line(12.4, 11.6, 4.4, 19.6)),
        stroke(line(6.8, 17.2, 9.6, 20)),
    ]))
    icons.append(Icon("Palette", "Palette", [
        solid(
            "M12,3.2 A8.8,8.8 0 0 0 3.2,12 A8.8,8.8 0 0 0 12,20.8 "
            "C14.0,20.8 14.8,19.4 14.0,18.2 C13.2,17.0 14.2,15.6 15.8,15.6 "
            "H18.0 A2.8,2.8 0 0 0 20.8,12.8 C20.8,7.4 16.9,3.2 12,3.2 Z"
        ),
        stroke(circle(8.4, 9.2, 1.5)),
        stroke(circle(8.0, 14.2, 1.5)),
        stroke(circle(12.6, 7.0, 1.5)),
        stroke(circle(16.4, 10.6, 1.5)),
    ]))
    icons.append(Icon("Spellcheck", "Spellcheck", [
        stroke("M3.2,14.8 L7.6,4.6 L12.0,14.8"),
        stroke(line(5.0, 11.2, 10.2, 11.2)),
        stroke("M13.6,18.6 L16.4,21.4 L21.8,13.8"),
    ]))
    icons.append(Icon("Clipboard", "Clipboard", [
        stroke(round_rect(4.6, 4.4, 14.8, 16.6, 2.8)),
        solid(round_rect(9.0, 2.6, 6.0, 3.6, 1.5)),
        stroke(line(8.6, 11.0, 15.4, 11.0)),
        stroke(line(8.6, 14.6, 15.4, 14.6)),
        stroke(line(8.6, 18.2, 12.6, 18.2)),
    ]))

    return icons


def app_mark_cloud() -> str:
    """A three lobe cloud as one nonZero path: overlapping subpaths merge into a silhouette."""
    return " ".join(
        [
            round_rect(2.1, 12.7, 19.8, 7.5, 3.75),
            circle(9.2, 11.2, 5.7),
            circle(16.6, 10.7, 4.5),
        ]
    )


def app_mark_keyboard() -> str:
    """Candidate B: cloud over a keyboard row."""
    return " ".join([app_mark_cloud(), round_rect(5.4, 21.4, 13.2, 1.6, 0.8)])


# ---------------------------------------------------------------------------------------
# Candidate E: the mark the user picked - cloud, an S shaped rift cut through it, and a
# keyboard below whose keys are punched out so the背景 gradient shows through.
# ---------------------------------------------------------------------------------------

RIFT_GAP_E = 2.0


def mark_cloud() -> str:
    """Three overlapping subpaths; nonZero fill turns them into one silhouette."""
    return " ".join(
        [
            circle(14.4, 8.4, 5.6),
            circle(7.6, 11.0, 4.4),
            round_rect(3.2, 9.0, 17.6, 6.4, 3.2),
        ]
    )


def _rift_curve(offset: float) -> str:
    """S shaped sweep from the lower left up to the right edge."""
    return (
        f"M-60,{17.0 + offset:.2f} "
        f"C-20,{15.6 + offset:.2f} 2,{13.4 + offset:.2f} 12,{10.5 + offset:.2f} "
        f"C22,{7.6 + offset:.2f} 60,{6.2 + offset:.2f} 260,{5.5 + offset:.2f}"
    )


def rift_regions() -> tuple[str, str]:
    """Complementary regions either side of the rift, used as clip paths."""
    upper = (
        "M-60,-60 L260,-60 L260,5.50 "
        "C60,6.20 22,7.60 12,10.50 C2,13.40 -20,15.60 -60,17.00 Z"
    )
    lower = (
        f"M-60,{17.0 + RIFT_GAP_E:.2f} "
        f"C-20,{15.6 + RIFT_GAP_E:.2f} 2,{13.4 + RIFT_GAP_E:.2f} "
        f"12,{10.5 + RIFT_GAP_E:.2f} "
        f"C22,{7.6 + RIFT_GAP_E:.2f} 60,{6.2 + RIFT_GAP_E:.2f} "
        f"260,{5.5 + RIFT_GAP_E:.2f} L260,260 L-60,260 Z"
    )
    return upper, lower


def mark_keyboard_punch() -> str:
    """One evenOdd path: the keyboard body with every key punched out as a hole."""
    parts = [round_rect(5.8, 16.2, 12.4, 6.0, 2.0)]
    key_width = 2.15
    gap = 0.6
    row_x = [6.8, 6.8 + key_width + gap, 6.8 + 2 * (key_width + gap), 6.8 + 3 * (key_width + gap)]
    for x in row_x:
        parts.append(round_rect(x, 17.2, key_width, 1.3, 0.45))
    wide = 4.9
    parts.append(round_rect(6.8, 18.9, key_width, 1.3, 0.45))
    parts.append(round_rect(6.8 + key_width + gap, 18.9, wide, 1.3, 0.45))
    parts.append(round_rect(6.8 + key_width + gap + wide + gap, 18.9, key_width, 1.3, 0.45))
    return " ".join(parts)


# Candidate C: the character 云 drawn as three strokes (二 + 厶). A character mark is the
# most legible option at 40dp and says "Chinese input" immediately.
YUN_STROKES: list[str] = [
    line(3.6, 5.6, 20.4, 5.6),
    line(6.0, 11.0, 18.0, 11.0),
    "M15.8,14.4 L8.6,17.8 L16.6,21.2",
]

# The character sits inside the safe zone with room to breathe, and is nudged up because the
# lower strokes carry more visual weight than the top horizontal.
GLYPH_SCALE = 2.9
GLYPH_OPTICAL_SHIFT = -4.0


def rift_half_planes(
    slope: float = RIFT_SLOPE,
    lower_at_x0: float = RIFT_LOWER_AT_X0,
    gap: float = RIFT_GAP,
) -> tuple[str, str]:
    lower = lower_at_x0
    upper = lower - gap
    far_left, far_right = -60.0, 260.0

    def y_on(intercept: float, x: float) -> float:
        return intercept - slope * x

    upper_region = " ".join(
        [
            f"M{point(far_left, far_left)}",
            f"L{point(far_right, far_left)}",
            f"L{point(far_right, y_on(upper, far_right))}",
            f"L{point(far_left, y_on(upper, far_left))}",
            "Z",
        ]
    )
    lower_region = " ".join(
        [
            f"M{point(far_left, y_on(lower, far_left))}",
            f"L{point(far_right, y_on(lower, far_right))}",
            f"L{point(far_right, far_right)}",
            f"L{point(far_left, far_right)}",
            "Z",
        ]
    )
    return upper_region, lower_region


# --------------------------------------------------------------------------------------
# emitters
# --------------------------------------------------------------------------------------


def kotlin_string(value: str) -> str:
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"') + '"'


def write_kotlin(icons: list[Icon]) -> None:
    lines = [
        "package com.yuan3271.cloudrift.ui.icons",
        "",
        "import androidx.compose.ui.graphics.Color",
        "import androidx.compose.ui.graphics.SolidColor",
        "import androidx.compose.ui.graphics.StrokeCap",
        "import androidx.compose.ui.graphics.StrokeJoin",
        "import androidx.compose.ui.graphics.vector.ImageVector",
        "import androidx.compose.ui.graphics.vector.addPathNodes",
        "import androidx.compose.ui.unit.dp",
        "",
        "/**",
        " * GENERATED FILE - edit `tools/icons/build_icons.py` instead.",
        " *",
        " * 云隙 icon set, drawn to the HyperOS / HarmonyOS grid rules: a 24 unit board with a",
        " * 2 unit optical margin, 2 unit round capped strokes, and solid shapes for the glyphs",
        " * that need to read at a glance (mic state, moon, cloud).",
        " *",
        " * Nothing here is derived from a third party icon library: Xiaomi publishes no open",
        " * UI icon set and HarmonyOS Symbol is proprietary, so the geometry is authored in the",
        " * generator and reviewed through `tools/design/icons-preview.svg`.",
        " */",
        "object CloudriftIcons {",
        "",
    ]
    for icon in icons:
        lines.append(f"    /** {icon.label} */")
        lines.append(f"    val {icon.name}: ImageVector = build(")
        lines.append(f'        name = "{icon.name}",')
        lines.append("        shapes = listOf(")
        for shape in icon.shapes:
            factory = "stroked" if shape.stroke else "filled"
            if shape.stroke:
                lines.append(
                    f"            {factory}({kotlin_string(shape.data)}, {shape.width}f),"
                )
            else:
                lines.append(f"            {factory}({kotlin_string(shape.data)}),")
        lines.append("        ),")
        lines.append("    )")
        lines.append("")
    lines += [
        "    private fun build(name: String, shapes: List<Shape>): ImageVector {",
        "        val builder = ImageVector.Builder(",
        "            name = name,",
        "            defaultWidth = 24.dp,",
        "            defaultHeight = 24.dp,",
        "            viewportWidth = GRID,",
        "            viewportHeight = GRID,",
        "        )",
        "        for (shape in shapes) {",
        "            builder.addPath(",
        "                pathData = addPathNodes(shape.data),",
        "                fill = if (shape.stroke) null else SolidColor(Color.Black),",
        "                stroke = if (shape.stroke) SolidColor(Color.Black) else null,",
        "                strokeLineWidth = shape.width,",
        "                strokeLineCap = StrokeCap.Round,",
        "                strokeLineJoin = StrokeJoin.Round,",
        "            )",
        "        }",
        "        return builder.build()",
        "    }",
        "",
        "    private data class Shape(val data: String, val stroke: Boolean, val width: Float)",
        "",
        "    private fun stroked(data: String, width: Float) = Shape(data, stroke = true, width = width)",
        "",
        "    private fun filled(data: String) = Shape(data, stroke = false, width = 0f)",
        "",
        "    private const val GRID = 24f",
        "}",
        "",
    ]
    KOTLIN_OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {KOTLIN_OUT.relative_to(ROOT)} ({len(icons)} icons)")


def write_preview(icons: list[Icon]) -> None:
    columns = 6
    cell = 120
    glyph = 60
    rows = max((len(icons) + columns - 1) // columns, columns)
    size = columns * cell
    parts = [
        '<?xml version="1.0" encoding="UTF-8"?>',
        "<!-- GENERATED by tools/icons/build_icons.py - design review sheet. -->",
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" '
        f'viewBox="0 0 {size} {size}">',
        f'  <rect width="{size}" height="{size}" fill="#12151b"/>',
        f'  <g fill="#e4e7f2" stroke="#e4e7f2" stroke-width="{STROKE}" '
        f'stroke-linecap="round" stroke-linejoin="round" '
        f'font-family="Helvetica" font-size="11" text-anchor="middle">',
    ]
    for index, icon in enumerate(icons):
        column = index % columns
        row = index // columns
        x = column * cell + (cell - glyph) / 2
        y = row * cell + 20
        parts.append(
            f'    <svg x="{x}" y="{y}" width="{glyph}" height="{glyph}" viewBox="0 0 24 24">'
        )
        for shape in icon.shapes:
            if shape.stroke:
                parts.append(
                    f'      <path fill="none" stroke-width="{shape.width}" d="{shape.data}"/>'
                )
            else:
                parts.append(f'      <path stroke="none" d="{shape.data}"/>')
        parts.append("    </svg>")
        parts.append(
            f'    <text x="{column * cell + cell // 2}" y="{row * cell + 20 + glyph + 18}" '
            f'stroke="none">{icon.label}</text>'
        )
    parts += ["  </g>", "</svg>", ""]
    PREVIEW_OUT.write_text("\n".join(parts), encoding="utf-8")
    print(f"wrote {PREVIEW_OUT.relative_to(ROOT)}")


def write_launcher() -> None:
    scale = GLYPH_SCALE if LAUNCHER_VARIANT == "glyph" else 72.0 / 24.0
    offset = (108.0 - 24.0 * scale) / 2.0
    offset_y = offset + (GLYPH_OPTICAL_SHIFT if LAUNCHER_VARIANT == "glyph" else 0.0)
    cloud = app_mark_cloud()
    shallow = rift_half_planes()
    steep = rift_half_planes(STEEP_SLOPE, STEEP_LOWER_AT_X0, STEEP_GAP)

    # A layer is either a filled path, a stroked path, or a filled path clipped by a region.
    # One description feeds both the VectorDrawable and the SVG review sheet so they cannot
    # drift apart.
    fill = lambda d: ("fill", d, None)  # noqa: E731
    stroke = lambda d: ("stroke", d, None)  # noqa: E731
    clipped = lambda d, clip: ("fill", d, clip)  # noqa: E731
    punched = lambda d: ("evenodd", d, None)  # noqa: E731

    cloud_upper, cloud_lower = rift_regions()

    candidates: dict[str, tuple[str, list]] = {
        "cloudkey": (
            "E · 云隙 + 键盘",
            [
                clipped(mark_cloud(), cloud_upper),
                clipped(mark_cloud(), cloud_lower),
                punched(mark_keyboard_punch()),
            ],
        ),
        "rift": (
            "A · 云隙（浅裂缝）",
            [clipped(cloud, shallow[0]), clipped(cloud, shallow[1])],
        ),
        "diagonal": (
            "B · 云隙（斜切）",
            [clipped(cloud, steep[0]), clipped(cloud, steep[1])],
        ),
        "keyboard": (
            "C · 云 + 键位行",
            [fill(app_mark_keyboard())],
        ),
        "glyph": (
            "D · 云字标",
            [stroke(d) for d in YUN_STROKES],
        ),
    }

    header = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!-- GENERATED by tools/icons/build_icons.py -->\n"
    )
    BACKGROUND_OUT.write_text(
        header
        + '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        + '    xmlns:aapt="http://schemas.android.com/aapt"\n'
        + '    android:width="108dp"\n'
        + '    android:height="108dp"\n'
        + '    android:viewportWidth="108"\n'
        + '    android:viewportHeight="108">\n'
        + '    <path android:pathData="M0,0h108v108h-108z">\n'
        + '        <aapt:attr name="android:fillColor">\n'
        + '            <gradient\n'
        + '                android:type="linear"\n'
        + '                android:startX="0"\n'
        + '                android:startY="0"\n'
        + '                android:endX="0"\n'
        + '                android:endY="108">\n'
        + f'                <item android:offset="0" android:color="#FF{BACKGROUND_TOP[1:]}" />\n'
        + f'                <item android:offset="1" android:color="#FF{BACKGROUND_BOTTOM[1:]}" />\n'
        + "            </gradient>\n"
        + "        </aapt:attr>\n"
        + "    </path>\n"
        # Only the picked design carries the hill; the other candidates keep a clean gradient.
        + (
            '    <path\n'
            f'        android:fillColor="#{BACKGROUND_WAVE[1:]}"\n'
            f'        android:fillAlpha="{BACKGROUND_WAVE_ALPHA}"\n'
            f'        android:pathData="{BACKGROUND_WAVE_PATH}" />\n'
            if LAUNCHER_VARIANT in ("cloudkey", "image")
            else ""
        )
        + "</vector>\n",
        encoding="utf-8",
    )
    print(f"wrote {BACKGROUND_OUT.relative_to(ROOT)}")

    def vector_body(layers: list) -> str:
        out: list[str] = []
        for kind, data, clip in layers:
            attributes = {
                "stroke": (
                    'android:strokeColor="#FFFFFFFF" android:strokeWidth="2.2" '
                    'android:strokeLineCap="round" android:strokeLineJoin="round"'
                ),
                # Keys are punched out of the keyboard body so the background shows through.
                "evenodd": 'android:fillColor="#FFFFFFFF" android:fillType="evenOdd"',
            }.get(kind, 'android:fillColor="#FFFFFFFF"')
            if clip:
                out.append(
                    "        <group>\n"
                    f'            <clip-path android:pathData="{clip}" />\n'
                    f'            <path {attributes} android:pathData="{data}" />\n'
                    "        </group>\n"
                )
            else:
                out.append(f'        <path {attributes} android:pathData="{data}" />\n')
        return "".join(out)

    variant_entry = candidates.get(LAUNCHER_VARIANT)
    if variant_entry is not None:
        label, layers = variant_entry
        mark_vector = (
            header
            + '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            + '    android:width="108dp"\n'
            + '    android:height="108dp"\n'
            + '    android:viewportWidth="108"\n'
            + '    android:viewportHeight="108">\n'
            + "    <group\n"
            + f'        android:scaleX="{scale:.4f}"\n'
            + f'        android:scaleY="{scale:.4f}"\n'
            + f'        android:translateX="{offset:.4f}"\n'
            + f'        android:translateY="{offset_y:.4f}">\n'
            + vector_body(layers)
            + "    </group>\n"
            + "</vector>\n"
        )
        FOREGROUND_OUT.write_text(mark_vector, encoding="utf-8")
        MONOCHROME_OUT.write_text(mark_vector, encoding="utf-8")
        print(f"wrote {FOREGROUND_OUT.relative_to(ROOT)}  (variant: {label})")
        print(f"wrote {MONOCHROME_OUT.relative_to(ROOT)}")
    else:
        # The artwork arrived as a PNG (tools/icons/make_launcher_from_image.py builds the
        # foreground from it), so only the background layer belongs in this script. Stale
        # vector layers would collide with the PNG of the same name.
        for stale in (FOREGROUND_OUT, MONOCHROME_OUT, FOREGROUND_LEGACY):
            if stale.exists():
                stale.unlink()
        print("foreground/monochrome come from make_launcher_from_image.py")

    cell = 210
    preview_parts = [
        '<?xml version="1.0" encoding="UTF-8"?>',
        "<!-- GENERATED by tools/icons/build_icons.py - launcher candidate review. -->",
        f'<svg xmlns="http://www.w3.org/2000/svg" width="960" height="520" viewBox="0 0 960 520">',
        '  <rect width="960" height="520" fill="#12151b"/>',
        "  <defs>",
        '    <linearGradient id="bg" x1="0" y1="0" x2="0" y2="1">',
        f'      <stop offset="0" stop-color="{BACKGROUND_TOP}" />',
        f'      <stop offset="1" stop-color="{BACKGROUND_BOTTOM}" />',
        "    </linearGradient>",
    ]
    for name, (_, layer_set) in candidates.items():
        for index, (_, _, clip) in enumerate(layer_set):
            if clip:
                preview_parts.append(
                    f'    <clipPath id="{name}{index}"><path d="{clip}" /></clipPath>'
                )
    preview_parts.append('    <clipPath id="squircle">'
                         '<rect x="0" y="0" width="216" height="216" rx="52" /></clipPath>')
    preview_parts.append("  </defs>")

    def render_tile(x: float, y: float, size: float, name: str) -> str:
        _, layer_set = candidates[name]
        tile_scale = GLYPH_SCALE if name == "glyph" else 72.0 / 24.0
        tile_offset = (108.0 - 24.0 * tile_scale) / 2.0
        tile_offset_y = tile_offset + (GLYPH_OPTICAL_SHIFT if name == "glyph" else 0.0)
        body = []
        for index, (kind, data, clip) in enumerate(layer_set):
            attributes = {
                "stroke": (
                    'fill="none" stroke="#ffffff" stroke-width="2.2" '
                    'stroke-linecap="round" stroke-linejoin="round"'
                ),
                "evenodd": 'fill="#ffffff" fill-rule="evenodd"',
            }.get(kind, 'fill="#ffffff"')
            if clip:
                body.append(
                    f'<g clip-path="url(#{name}{index})"><path {attributes} d="{data}" /></g>'
                )
            else:
                body.append(f'<path {attributes} d="{data}" />')
        return (
            f'  <g clip-path="url(#squircle)" transform="translate({x},{y}) '
            f'scale({size / 216:.4f})">\n'
            '    <rect width="216" height="216" fill="url(#bg)" />\n'
            f'    <g transform="translate({tile_offset:.2f},{tile_offset_y:.2f}) '
            f'scale({tile_scale})">{"".join(body)}</g>\n'
            "  </g>\n"
        )

    for index, name in enumerate(candidates):
        column = index % 4
        preview_parts.append(render_tile(20 + column * 235, 20, 210, name))
    for index, (_, (candidate_label, _)) in enumerate(candidates.items()):
        column = index % 4
        preview_parts.append(
            f'  <text x="{20 + column * 235}" y="258" fill="#9aa3b8" '
            f'font-family="Helvetica" font-size="14">{candidate_label}</text>'
        )
    preview_parts.append(
        '  <text x="20" y="300" fill="#6f7787" font-family="Helvetica" font-size="13">'
        "同一方案在 96 / 72 / 56 / 44dp 下的辨识度</text>"
    )
    featured = LAUNCHER_VARIANT if LAUNCHER_VARIANT in candidates else "cloudkey"
    for index, size in enumerate((96, 72, 56, 44)):
        preview_parts.append(render_tile(20 + index * 130, 320, size, featured))
    preview_parts.append("</svg>\n")
    PREVIEW_LAUNCHER_OUT.write_text("\n".join(preview_parts), encoding="utf-8")
    print(f"wrote {PREVIEW_LAUNCHER_OUT.relative_to(ROOT)}")


def main() -> int:
    icons = build()
    write_kotlin(icons)
    write_preview(icons)
    write_launcher()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

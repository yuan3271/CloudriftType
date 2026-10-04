#!/usr/bin/env python3
"""键鼠兼容面板的设计稿（平板 + 外接键鼠）。

这块面板不是新画的一套东西，它就是**横屏那台悬浮键盘的框**里换了两行内容：

| 悬浮键盘 | 键鼠兼容面板 |
| --- | --- |
| 24dp 圆角卡片、10dp / 6dp 外边距 | 同左（`KeyboardSurface` 的 floating 分支） |
| 顶部 20dp 把手：中间一根小白条＝拖走，右端 `More` 图标＝改宽度 | 同左（`FloatingGripStrip`） |
| 工具栏 + 候选栏 + 按键 | 候选词面板 + 工具面板，**两张分开的卡片**，各自带一条把手 |

所以这张图里的图标、把手、手势都不是这里画的，而是**从仓库里取来的真几何**：

* 图标路径直接 `import` `tools/icons/build_icons.py` 的 `build()`（Mic / Clipboard / Globe /
  More / Keyboard），在这里把同一份 path 数据栅格化——图里的麦克风就是 `CloudriftIcons.Mic`；
* 把手的小白条、缩放手柄的形状、候选胶囊的三档底色、卡片的圆角与边距，都按 `ui/` 里那几份
  Compose 代码的量手抄；
* 符号键写的是键盘上同一个键的字（`KeyboardLayouts.symbolKeyLabel`：中文 `符`、英文 `?123`）。

字体是 STHeiti（设备上是 MiSans / 系统字体），所以文字形状会有出入；尺寸、层次、间距、圆角是
按代码 1:1 画的。验收以真机为准，这里用来看"是不是同一种设计语言"。

Usage:
    python3 tools/design/render_compat_panel.py
"""

from __future__ import annotations

import importlib.util
import math
import pathlib
import re
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "tools" / "design" / "compat-panel-preview.png"

FONT_CJK = "/System/Library/Fonts/STHeiti Medium.ttc"
FONT_LATIN = "/System/Library/Fonts/Helvetica.ttc"
FONT_EMOJI = "/System/Library/Fonts/Apple Color Emoji.ttc"

# 一块平板横屏，单位 dp。面板的尺寸按 1:1 画进去。
BOARD_W, BOARD_H = 1024.0, 640.0
S = 1.5


# --------------------------------------------------------------------------------------
# 仓库里的图标：直接取 build_icons.py 的几何，在这里栅格化
# --------------------------------------------------------------------------------------


def _load_icons():
    path = ROOT / "tools" / "icons" / "build_icons.py"
    spec = importlib.util.spec_from_file_location("cloudrift_build_icons", path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return {icon.name: icon for icon in module.build()}


ICONS = _load_icons()

_TOKEN = re.compile(r"([MmLlHhVvCcAaZz])|(-?\d*\.?\d+)")


def _tokens(data: str) -> list:
    out: list = []
    for match in _TOKEN.finditer(data):
        out.append(match.group(1) if match.group(1) else float(match.group(2)))
    return out


def _arc(x1, y1, rx, ry, phi_deg, large_arc, sweep, x2, y2) -> list[tuple[float, float]]:
    """SVG 的端点式圆弧 → 采样点（按规范做中心参数化）。"""
    phi = math.radians(phi_deg)
    cos_p, sin_p = math.cos(phi), math.sin(phi)
    dx2, dy2 = (x1 - x2) / 2.0, (y1 - y2) / 2.0
    x1p = cos_p * dx2 + sin_p * dy2
    y1p = -sin_p * dx2 + cos_p * dy2
    rx, ry = abs(rx), abs(ry)
    if rx == 0 or ry == 0:
        return [(x2, y2)]
    lam = x1p * x1p / (rx * rx) + y1p * y1p / (ry * ry)
    if lam > 1:
        factor = math.sqrt(lam)
        rx, ry = rx * factor, ry * factor
    den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
    num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
    coef = math.sqrt(max(0.0, num / den)) if den else 0.0
    if large_arc == sweep:
        coef = -coef
    cxp = coef * rx * y1p / ry
    cyp = -coef * ry * x1p / rx
    cx = cos_p * cxp - sin_p * cyp + (x1 + x2) / 2.0
    cy = sin_p * cxp + cos_p * cyp + (y1 + y2) / 2.0

    def angle(ux, uy, vx, vy):
        norm = math.hypot(ux, uy) * math.hypot(vx, vy) or 1.0
        value = max(-1.0, min(1.0, (ux * vx + uy * vy) / norm))
        result = math.acos(value)
        return -result if (ux * vy - uy * vx) < 0 else result

    ux, uy = (x1p - cxp) / rx, (y1p - cyp) / ry
    vx, vy = (-x1p - cxp) / rx, (-y1p - cyp) / ry
    theta = angle(1, 0, ux, uy)
    delta = angle(ux, uy, vx, vy)
    if not sweep and delta > 0:
        delta -= 2 * math.pi
    elif sweep and delta < 0:
        delta += 2 * math.pi
    steps = max(8, int(abs(delta) / (math.pi / 16)) + 1)
    points = []
    for index in range(steps + 1):
        t = theta + delta * index / steps
        points.append(
            (
                cos_p * rx * math.cos(t) - sin_p * ry * math.sin(t) + cx,
                sin_p * rx * math.cos(t) + cos_p * ry * math.sin(t) + cy,
            )
        )
    return points


def _polylines(data: str) -> list[list[tuple[float, float]]]:
    toks = _tokens(data)
    out: list[list[tuple[float, float]]] = []
    poly: list[tuple[float, float]] = []
    cur = (0.0, 0.0)
    start = (0.0, 0.0)
    cmd = "M"
    index = 0

    def take() -> float:
        nonlocal index
        value = toks[index]
        index += 1
        return float(value)

    while index < len(toks):
        if isinstance(toks[index], str):
            cmd = toks[index]
            index += 1
        upper = cmd.upper()
        relative = cmd.islower()

        def point(x, y):
            return (x + cur[0], y + cur[1]) if relative else (x, y)

        if upper == "M":
            x, y = point(take(), take())
            if poly:
                out.append(poly)
            poly = [(x, y)]
            cur = start = (x, y)
            cmd = "l" if relative else "L"
        elif upper == "L":
            x, y = point(take(), take())
            poly.append((x, y))
            cur = (x, y)
        elif upper == "H":
            x = take()
            x = x + cur[0] if relative else x
            poly.append((x, cur[1]))
            cur = (x, cur[1])
        elif upper == "V":
            y = take()
            y = y + cur[1] if relative else y
            poly.append((cur[0], y))
            cur = (cur[0], y)
        elif upper == "C":
            x1, y1, x2, y2, x, y = (take() for _ in range(6))
            if relative:
                x1 += cur[0]; y1 += cur[1]
                x2 += cur[0]; y2 += cur[1]
                x += cur[0]; y += cur[1]
            for step in range(1, 17):
                t = step / 16
                u = 1 - t
                poly.append(
                    (
                        u ** 3 * cur[0] + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t ** 3 * x,
                        u ** 3 * cur[1] + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t ** 3 * y,
                    )
                )
            cur = (x, y)
        elif upper == "A":
            rx, ry, rot, large, sweep, x, y = (take() for _ in range(7))
            x, y = point(x, y)
            poly.extend(_arc(cur[0], cur[1], rx, ry, rot, int(large), int(sweep), x, y)[1:])
            cur = (x, y)
        elif upper == "Z":
            poly.append(start)
            out.append(poly)
            poly = []
            cur = start
        else:
            raise ValueError(f"unsupported path command {cmd!r}")
    if poly:
        out.append(poly)
    return out


def icon(name: str, size_dp: float, color) -> Image.Image:
    """把仓库里的图标几何栅格化成一张 RGBA 小图（4 倍超采样后缩小）。"""
    supersample = 4
    size = max(8, int(round(size_dp * S * supersample)))
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    scale = size / 24.0
    for shape in ICONS[name].shapes:
        for line in _polylines(shape.data):
            points = [(x * scale, y * scale) for x, y in line]
            if shape.stroke:
                width = max(1, int(round(shape.width * scale)))
                radius = width / 2
                for a, b in zip(points, points[1:]):
                    draw.line([a, b], fill=color, width=width)
                for x, y in points:
                    draw.ellipse([x - radius, y - radius, x + radius, y + radius], fill=color)
            else:
                draw.polygon(points, fill=color)
    return img.resize((max(1, int(round(size_dp * S))), max(1, int(round(size_dp * S)))), Image.LANCZOS)


# --------------------------------------------------------------------------------------
# 调色板：与 theme/Color.kt 的两套 scheme 一字不差
# --------------------------------------------------------------------------------------


def hexa(value: str) -> tuple[int, int, int, int]:
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4)) + (255,)  # type: ignore[return-value]


LIGHT = {
    "surface": "#FAF8FF", "onSurface": "#191C23", "onSurfaceVariant": "#43474E",
    "primary": "#3F5B8F", "onPrimary": "#FFFFFF",
    "primaryContainer": "#DBE1FF", "onPrimaryContainer": "#001B3D",
    "surfaceContainerLowest": "#FFFFFF", "surfaceContainer": "#EEEDF4",
    "surfaceContainerHigh": "#E8E7EF", "surfaceContainerHighest": "#E2E2E9",
    "appBg": "#FFFFFF", "appLine": "#E3E5EA", "appText": "#20242B", "dim": "#8A8F98",
}

DARK = {
    "surface": "#111318", "onSurface": "#E2E2E9", "onSurfaceVariant": "#C3C6CF",
    "primary": "#AFC6FF", "onPrimary": "#0A305F",
    "primaryContainer": "#264777", "onPrimaryContainer": "#DBE1FF",
    "surfaceContainerLowest": "#0C0E13", "surfaceContainer": "#1D2024",
    "surfaceContainerHigh": "#282A2F", "surfaceContainerHighest": "#33353A",
    "appBg": "#16181D", "appLine": "#2A2E35", "appText": "#C9CCD3", "dim": "#7D828A",
}


def px(dp: float) -> float:
    return dp * S


def font(size_dp: float, cjk: bool = False) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(FONT_CJK if cjk else FONT_LATIN, int(round(px(size_dp))))


def text(draw, xy, label, size_dp, fill, cjk=False, anchor="la") -> None:
    draw.text(xy, label, font=font(size_dp, cjk), fill=fill, anchor=anchor)


def emoji(draw, xy, label) -> None:
    size = 32  # Apple Color Emoji 只认几个固定字号
    draw.text(xy, label, font=ImageFont.truetype(FONT_EMOJI, size), anchor="mm", embedded_color=True)


def card(draw, box, fill, radius: float) -> None:
    """平贴的一块：整个界面里没有阴影（悬浮键盘的卡片也是平贴的）。"""
    x0, y0, x1, y1 = box
    draw.rounded_rectangle([x0, y0, x1, y1], radius=px(radius), fill=fill)


# --------------------------------------------------------------------------------------
# 面板：与 ui/CompatPanel.kt、ui/FloatingGrip.kt、ui/KeyboardToolbar.kt 的量一一对应
# --------------------------------------------------------------------------------------

GRIP_H = 20.0
CANDIDATE_H = 44.0
TOOLBAR_H = 46.0
CARD_RADIUS = 24.0
PANEL_GAP = 6.0


def grip_strip(img: Image.Image, draw, palette, x, y, w) -> None:
    """FloatingGripStrip（showResizeHandle = false）：只有中间那根 40×4 的把手，抓它拖着走。

    颜色是 `onSurfaceVariant` 45%（不是原来那条白条）：白条压在浅色的 `surfaceContainer` 上几乎
    看不见，浅色模式等于没有提示。
    """
    color = {k: hexa(v) for k, v in palette.items()}
    bar_w, bar_h = 40.0, 4.0
    tint = tuple(color["onSurfaceVariant"])[:3] + (115,)
    card(
        draw,
        [px(x + (w - bar_w) / 2), px(y + GRIP_H / 2 - bar_h / 2),
         px(x + (w + bar_w) / 2), px(y + GRIP_H / 2 + bar_h / 2)],
        tint, radius=bar_h / 2,
    )


def candidate_card(img, draw, palette, x, y, w) -> float:
    color = {k: hexa(v) for k, v in palette.items()}
    height = GRIP_H + CANDIDATE_H
    card(draw, [px(x), px(y), px(x + w), px(y + height)], color["surfaceContainer"],
         CARD_RADIUS)
    grip_strip(img, draw, palette, x, y, w)

    row_y = y + GRIP_H
    pills = [
        ("你好", "primaryContainer", "onPrimaryContainer", 1),
        ("拟合", "surfaceContainerHighest", "onSurface", -1),
        ("你", "surfaceContainerHigh", "onSurface", -1),
        ("内", "surfaceContainerHigh", "onSurface", -1),
        ("拟", "surfaceContainerHigh", "onSurface", -1),
    ]
    cx = x + 6
    for label, bg, fg, dim_from in pills:
        pill_w = 12 * 2 + 12 * len(label)
        card(draw, [px(cx), px(row_y + 7), px(cx + pill_w), px(row_y + CANDIDATE_H - 7)],
             color[bg], 14.0)
        if dim_from > 0:
            text(draw, (px(cx + 12), px(row_y + CANDIDATE_H / 2)), label[:dim_from], 14.5,
                 color[fg], cjk=True, anchor="lm")
            head = draw.textlength(label[:dim_from], font=font(14.5, cjk=True))
            text(draw, (px(cx + 12) + head, px(row_y + CANDIDATE_H / 2)), label[dim_from:], 14.5,
                 tuple(color[fg])[:3] + (110,), cjk=True, anchor="lm")
        else:
            text(draw, (px(cx + 12), px(row_y + CANDIDATE_H / 2)), label, 14.5, color[fg],
                 cjk=True, anchor="lm")
        cx += pill_w + 6
    chevron = icon("ExpandMore", 20.0, color["onSurfaceVariant"])
    img.paste(chevron, (int(px(x + w - 30)), int(px(row_y + CANDIDATE_H / 2 - 10))), chevron)
    return height


def toolbar_size(layout: str, symbols: str) -> tuple[float, float]:
    """工具面板这个独立窗口的尺寸：宽度就是这一排的宽度（不留空），高度是把手 + 那一排。"""
    chip_h = 30.0
    button = 38.0
    lang_w = 16 + 6 + len(layout) * 16 + 20
    sym_w = 20 + len(symbols) * 16
    gap = 2.0
    pad = 6.0
    del chip_h
    return pad + lang_w + gap + sym_w + gap + button + gap + button + pad, GRIP_H + TOOLBAR_H


def toolbar_card(img, draw, palette, x, y, layout="中", symbols="符", page=False) -> tuple[float, float]:
    """工具面板**自己一个窗口**，宽度收紧到这一排（工具栏不留空）。返回 (宽, 高)。"""
    color = {k: hexa(v) for k, v in palette.items()}
    chip_h = 30.0
    button = 38.0
    lang_w = 16 + 6 + len(layout) * 16 + 20
    sym_w = 20 + len(symbols) * 16
    gap = 2.0
    pad = 6.0
    w, height = toolbar_size(layout, symbols)
    card(draw, [px(x), px(y), px(x + w), px(y + height)], color["surfaceContainer"],
         CARD_RADIUS)
    grip_strip(img, draw, palette, x, y, w)

    row_y = y + GRIP_H
    row_center = row_y + TOOLBAR_H / 2

    # 语言胶囊（LanguageChip：地球 + 当前语言）
    card(draw, [px(x + pad), px(row_center - chip_h / 2), px(x + pad + lang_w), px(row_center + chip_h / 2)],
         color["surfaceContainerHigh"], 50.0)
    globe = icon("Globe", 16.0, color["onSurfaceVariant"])
    img.paste(globe, (int(px(x + pad + 10)), int(px(row_center - 8))), globe)
    text(draw, (px(x + pad + 10 + 16 + 6), px(row_center)), layout, 13, color["onSurface"],
         cjk=True, anchor="lm")

    # 符号胶囊（同一颗 ToolbarChip，只是没有图标）
    sym_x = x + pad + lang_w + gap
    if page:
        card(draw, [px(sym_x), px(row_center - chip_h / 2), px(sym_x + sym_w), px(row_center + chip_h / 2)],
             color["primaryContainer"], 50.0)
        sym_fg = color["onPrimaryContainer"]
    else:
        card(draw, [px(sym_x), px(row_center - chip_h / 2), px(sym_x + sym_w), px(row_center + chip_h / 2)],
             color["surfaceContainerHigh"], 50.0)
        sym_fg = color["onSurface"]
    text(draw, (px(sym_x + sym_w / 2), px(row_center)), symbols, 13, sym_fg, cjk=True, anchor="mm")

    # 右端：语音（唯一的实心键）+ 剪贴板，与 KeyboardToolbar 同一套 38dp 圆钮
    clip_x = x + w - pad - button
    mic_x = clip_x - gap - button
    card(draw, [px(mic_x), px(row_center - button / 2), px(mic_x + button), px(row_center + button / 2)],
         color["primary"], 50.0)
    mic = icon("Mic", 20.0, color["onPrimary"])
    img.paste(mic, (int(px(mic_x + 9)), int(px(row_center - 10))), mic)
    clipboard = icon("Clipboard", 20.0, color["onSurfaceVariant"])
    img.paste(clipboard, (int(px(clip_x + 9)), int(px(row_center - 10))), clipboard)
    return w, height


def sheet_card(img, draw, palette, x, y, w) -> float:
    """符号页展开：第三张卡片，夹在候选与工具之间。"""
    color = {k: hexa(v) for k, v in palette.items()}
    rows, cols = 3, 6
    cell_h, gap = 44.0, 6.0
    body = rows * cell_h + (rows - 1) * gap + 8
    height = GRIP_H + body
    card(draw, [px(x), px(y), px(x + w), px(y + height)], color["surfaceContainer"],
         CARD_RADIUS)
    grip_strip(img, draw, palette, x, y, w)
    top = y + GRIP_H

    rail_w = 46.0
    for index, label in enumerate(["全角", "半角", "表情"]):
        cell_y = top + 4 + index * (body - 8) / 3
        cell_height = (body - 8) / 3 - gap
        if index == 0:
            card(draw, [px(x + 4), px(cell_y), px(x + rail_w - 4), px(cell_y + cell_height)],
                 color["primaryContainer"], 12.0)
        text(draw, (px(x + rail_w / 2), px(cell_y + cell_height / 2)), label, 11,
             color["onPrimaryContainer"] if index == 0 else color["onSurfaceVariant"],
             cjk=True, anchor="mm")

    grid = [
        ["（）", "【】", "《》", "“”", "、", "。"],
        [",", ".", "?", "!", ";", ":"],
        ["😀", "🙂", "😍", "👍", "🎉", "❤"],
    ]
    grid_x = x + rail_w
    cell_w = (w - rail_w - 8) / cols
    for row, glyphs in enumerate(grid):
        for col, glyph in enumerate(glyphs):
            cx0 = grid_x + col * cell_w
            cy0 = top + 4 + row * (cell_h + gap)
            card(draw, [px(cx0 + 2), px(cy0), px(cx0 + cell_w - 2), px(cy0 + cell_h)],
                 color["surfaceContainerHigh"], 12.0)
            if row == 2:
                emoji(draw, (px(cx0 + cell_w / 2), px(cy0 + cell_h / 2)), glyph)
            else:
                text(draw, (px(cx0 + cell_w / 2), px(cy0 + cell_h / 2)), glyph, 13,
                     color["onSurface"], cjk=True, anchor="mm")
    return height


def app_page(img, draw, palette) -> tuple[float, float]:
    """平板上的正文与光标；返回光标位置（dp）。"""
    color = {k: hexa(v) for k, v in palette.items()}
    draw.rectangle([0, 0, px(BOARD_W), px(BOARD_H)], fill=color["appBg"])
    text(draw, (px(64), px(48)), "会议记录", 19, color["appText"], cjk=True)
    lines = [(64, 96, 520), (64, 128, 600), (64, 160, 232), (64, 192, 660)]
    caret = (0.0, 0.0)
    for index, (x, y, width) in enumerate(lines):
        draw.rounded_rectangle([px(x), px(y), px(x + width), px(y + 10)], radius=px(5),
                               fill=color["appLine"])
        if index == 2:
            draw.line([px(x + width), px(y - 3), px(x + width), px(y + 13)],
                      fill=color["primary"], width=max(1, int(px(1.2))))
            caret = (x + width, y + 13)
    return caret


def scene(palette: dict, kind: str, label: str) -> Image.Image:
    color = {k: hexa(v) for k, v in palette.items()}
    img = Image.new("RGBA", (int(px(BOARD_W)), int(px(BOARD_H + 40))), color["surface"])
    draw = ImageDraw.Draw(img)
    caret_x, caret_y = app_page(img, draw, palette)

    panel_w = 372.0
    tool_w, tool_h = toolbar_size("中", "符")
    # 两个窗口各有各的位置：候选词窗口跟着光标（贴在光标那一行下面），工具窗口默认停在底部。
    tool_x, tool_y = (BOARD_W - tool_w) / 2, BOARD_H - 14.0 - tool_h
    candidate_x = min(max(caret_x - 16.0, 10.0), BOARD_W - panel_w - 10.0)
    candidate_y = caret_y + 10.0

    if kind == "default":
        candidate_card(img, draw, palette, candidate_x, candidate_y, panel_w)
        toolbar_card(img, draw, palette, tool_x, tool_y)
    elif kind == "expanded":
        # 符号面板挂在候选词面板**底下**（同一列），再往下才是工具窗口。
        sheet_h = GRIP_H + 148.0
        sheet_y = candidate_y + GRIP_H + CANDIDATE_H + PANEL_GAP
        candidate_card(img, draw, palette, candidate_x, candidate_y, panel_w)
        sheet_card(img, draw, palette, candidate_x, sheet_y, panel_w)
        toolbar_card(img, draw, palette, tool_x, min(sheet_y + sheet_h + PANEL_GAP, BOARD_H - 14.0 - tool_h), page=True)
    elif kind == "moved":
        # 工具窗口被拖到屏幕另一角：它是独立窗口，动它不会带着候选词一起走。
        candidate_card(img, draw, palette, candidate_x, candidate_y, panel_w)
        moved_w, _ = toolbar_size("En", "?123")
        tx, ty = BOARD_W - moved_w - 40.0, caret_y + 130.0
        toolbar_card(img, draw, palette, tx, ty, symbols="?123", layout="En")
        text(draw, (px(tx), px(ty - 18)), "工具窗口被单独拖到这儿", 11,
             color["onSurfaceVariant"], cjk=True)

    text(draw, (px(24), px(BOARD_H + 10)), label, 12.5, color["onSurfaceVariant"], cjk=True)
    return img


def main() -> None:
    columns = [
        scene(LIGHT, "default", "① 浅色 · 两块面板各是一个窗口：候选在上、工具在下，都只有一根把手，没有阴影"),
        scene(DARK, "expanded", "② 深色 · 点「符」展开符号页（候选窗口里再叠一张卡片），再点一次收起"),
        scene(LIGHT, "moved", "③ 工具窗口可以单独拖到任意位置；英文布局时符号那颗写着 ?123"),
    ]
    gap = int(px(18))
    width = sum(c.width for c in columns) + gap * (len(columns) + 1)
    height = max(c.height for c in columns) + gap * 2
    sheet = Image.new("RGBA", (width, height), hexa("#FFFFFF"))
    x = gap
    for column in columns:
        sheet.paste(column, (x, gap))
        x += column.width + gap
    OUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(OUT)
    print(f"wrote {OUT.relative_to(ROOT)} ({sheet.width}x{sheet.height})")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Builds the emoji asset the keyboard's 表情 page scrolls through.

Source: iamcal/emoji-data (MIT) - every standard emoji, in its Unicode group.

Why a table instead of the platform's own picker: an IME draws its emoji on its own canvas, and
the canvas needs a *list* - one asset, one loader, no dependency on a vendor library or on the
device having that emoji font. The groups in the source are the Unicode standard ones, which is
what the page shows in the rail: 表情 / 人物 / 动物 / 食物 / 出行 / 活动 / 物品 / 符号 / 旗帜.

Filtered out on purpose:

  * the ``Component`` group (skin tones and hair pieces: they only mean something after another
    emoji, and a keyboard that offers them on their own produces invisible output),
  * ``non_qualified`` spellings (the same emoji without a variation selector; both would look
    identical on screen and collide as lazy-list keys).

Output: ``<emoji>\t<Unicode group>`` one per line, ordered by the standard group order. The
Chinese labels live in the app (`EmojiCatalog`), because the asset is a copy of the standard
data while the labels are UI text.

Usage: python3 tools/dictgen/build_emoji.py raw/emoji_data.json app/src/main/assets/emoji.txt
"""

from __future__ import annotations

import json
import pathlib
import sys

# The Unicode emoji groups, in the order emoji-test.txt lists them. The rail shows them in this
# order, so the order lives here rather than following whatever the JSON happens to say.
GROUP_ORDER = [
    "Smileys & Emotion",
    "People & Body",
    "Animals & Nature",
    "Food & Drink",
    "Travel & Places",
    "Activities",
    "Objects",
    "Symbols",
    "Flags",
]

SKIPPED_GROUPS = {"Component"}


def emoji_of(unified: str) -> str | None:
    """``1F441-FE0F-200D-1F5E8`` -> the string a keyboard types."""
    code_points = [int(part, 16) for part in unified.split("-") if part]
    if not code_points:
        return None
    # A lone skin-tone modifier (or a lone hair component) would draw on its own.
    if all(0x1F3FB <= code <= 0x1F3FF for code in code_points):
        return None
    return "".join(chr(code) for code in code_points)


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        return 2
    source, target = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])

    entries = json.loads(source.read_text(encoding="utf-8"))
    grouped: dict[str, list[tuple[int, str]]] = {group: [] for group in GROUP_ORDER}
    skipped = 0
    for entry in entries:
        group = entry.get("category") or ""
        if group in SKIPPED_GROUPS or group not in grouped:
            skipped += 1
            continue
        emoji = emoji_of(entry.get("unified") or "")
        if not emoji:
            skipped += 1
            continue
        grouped[group].append((entry.get("sort_order") or 0, emoji))

    seen: set[str] = set()
    lines = [
        "# Emoji inventory for the 表情 page of 云隙输入.",
        "# Source: iamcal/emoji-data (MIT) - grouped by the Unicode emoji groups.",
        "# Groups: " + ", ".join(GROUP_ORDER),
    ]
    rows = 0
    for group in GROUP_ORDER:
        for _, emoji in sorted(grouped[group]):
            # 一个 emoji 只能属于一组：LazyVerticalGrid 的 key 是它本身，重复会让那一项抛异常。
            if emoji in seen or "\t" in emoji or "\n" in emoji:
                continue
            seen.add(emoji)
            lines.append(f"{emoji}\t{group}")
            rows += 1

    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text("\n".join(lines) + "\n", encoding="utf-8")

    counts = ", ".join(f"{group}={len(grouped[group])}" for group in GROUP_ORDER)
    print(f"emoji: {rows} 个（跳过 {skipped}），{target.stat().st_size / 1024:.0f} KiB")
    print(f"  {counts}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

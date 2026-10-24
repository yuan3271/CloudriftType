#!/usr/bin/env python3
"""Build the runtime set of characters that should never appear as Chinese candidates.

OpenCC's TSCharacters table maps traditional keys to one or more simplified values. A key that
is not also listed as one of its own values is traditional-only for this keyboard's purposes.
Keeping those characters in a compact asset lets the pinyin engine remove 樓/妳/裡-style
candidates without shipping a converter or maintaining a hand-written list.

Usage: python3 tools/dictgen/build_traditional_filter.py
"""

from __future__ import annotations

import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
SOURCE = ROOT / "tools/dictgen/opencc/TSCharacters.txt"
OUTPUT = ROOT / "app/src/main/assets/traditional_chars.txt"

# Common Taiwan/contextual variants that OpenCC deliberately treats as valid in some simplified
# contexts and therefore does not place in TSCharacters. For a candidate list, none of them should
# be offered when 你/它/他/你 are the intended simplified forms.
MANUAL_TRADITIONAL = set("妳牠祂祢")


def main() -> int:
    traditional: set[str] = set(MANUAL_TRADITIONAL)
    for raw in SOURCE.read_text(encoding="utf-8").splitlines():
        if not raw or raw.startswith("#"):
            continue
        key, separator, values = raw.partition("\t")
        if not separator or len(key) != 1:
            continue
        simplified = values.split()
        # Some entries preserve the key as an accepted simplified form too. Those characters
        # are not safe to reject globally.
        if key not in simplified:
            traditional.add(key)

    OUTPUT.write_text("".join(sorted(traditional)), encoding="utf-8")
    print(f"traditional-only characters: {len(traditional):,} -> {OUTPUT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""把 clean/extra_bigrams.txt 的日常搭配补进**已生成**的 pinyin_bigrams.txt。

和 apply_curated_words.py 同样的理由：完整重生成需要 fetch_corpora.sh 拉过的语料，
本机没有时重跑 build_bigram.py 会把表做小。这里只做增量合并（取较大分数）。

一行：左词<TAB>右词<TAB>分数（1..1000，与表里同量级）。

Usage:
    python3 tools/dictgen/apply_curated_bigrams.py [--dry-run]
"""

from __future__ import annotations

import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
EXTRAS = ROOT / "tools/dictgen/clean/extra_bigrams.txt"
TABLE = ROOT / "app/src/main/assets/pinyin_bigrams.txt"


def load_extras() -> dict[tuple[str, str], int]:
    extra: dict[tuple[str, str], int] = {}
    if not EXTRAS.exists():
        return extra
    for line in EXTRAS.read_text(encoding="utf-8").splitlines():
        if line.startswith("#") or line.count("\t") != 2:
            continue
        left, right, raw = line.split("\t")
        raw = raw.strip()
        if raw.isdigit():
            extra[(left.strip(), right.strip())] = max(
                extra.get((left.strip(), right.strip()), 0), int(raw),
            )
    return extra


def main() -> int:
    dry_run = "--dry-run" in sys.argv
    extra = load_extras()
    rows: list[list] = []
    for line in TABLE.read_text(encoding="utf-8").splitlines():
        parts = line.split("\t")
        if len(parts) == 3 and parts[2].isdigit():
            rows.append([parts[0], parts[1], int(parts[2])])
    index = {(left, right): i for i, (left, right, _) in enumerate(rows)}

    added = 0
    updated = 0
    for (left, right), score in sorted(extra.items()):
        position = index.get((left, right))
        if position is None:
            rows.append([left, right, score])
            added += 1
        elif rows[position][2] < score:
            rows[position][2] = score
            updated += 1

    print(f"extra bigrams: {len(extra)}，新增 {added}，提分 {updated}")
    if dry_run:
        return 0
    ordered = sorted(rows, key=lambda r: (r[0], -r[2], r[1]))
    if rows == ordered:
        print("按 (左词, -分数) 排序")
        rows = ordered
    else:
        print("原表不是按 (左词, -分数) 排序，保持原顺序并在末尾追加")
    TABLE.write_text("".join(f"{r[0]}\t{r[1]}\t{r[2]}\n" for r in rows), encoding="utf-8")
    print(f"写入 {TABLE}，共 {len(rows)} 行")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

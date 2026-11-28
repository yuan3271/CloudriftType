#!/usr/bin/env python3
"""Builds the 3-unit table behind the 联想 strip.

Why a second table
------------------
The bigram table answers "what follows this unit". The strip still showed generic next words after a
specific phrase (今天 → 天气 is right, but after 北京的 the answer should be 天气 too). A trigram
answers "what follows this *pair* of units", which is what the cursor's preceding text often is: two
units, not one.

Only 联想 uses it. The sentence decoder keeps using the bigram table, so nothing that is already
measured (全拼/首字母/九键 benchmarks) moves.

Format
------
    <left1>\t<left2>\t<right>\t<score>

The app looks it up by joining the first two columns, because the cursor context is text, not units:
the longest suffix of the preceding text that is such a join wins (see PinyinEngine.associations).

Licensing: same sources as build_bigram.py (Tatoeba cmn, CC BY 2.0 FR; the project's own corpus,
MIT). No sentence text is shipped, only counts.

Usage:
    python3 tools/dictgen/build_trigram.py
"""

from __future__ import annotations

import argparse
import bz2
import math
import pathlib
import sys
from collections import Counter, defaultdict

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import build_bigram as bg  # noqa: E402  (path first, then the shared helpers)

ASSETS = bg.ASSETS
RAW = bg.RAW
BOUNDARY = bg.BOUNDARY
HAN = bg.HAN

# One trigram row is ~40 bytes in the asset and three small objects in memory; 40k keeps the
# keyboard's heap where the bigram table put it.
MAX_TRIGRAMS = 40_000
# A triple seen once in 89k sentences is a coincidence. The hand-written corpus arrives with
# DAILY_WEIGHT (600), so one occurrence there passes on its own.
MIN_COUNT = 2
DISCOUNT = 0.75


def count_trigrams(words: dict[str, int], floor: int, tables) -> tuple[Counter, Counter]:
    """(u1, u2, u3) counts and (u1, u2) totals, both weighted the same way."""
    triples: Counter[tuple[str, str, str]] = Counter()
    contexts: Counter[tuple[str, str]] = Counter()

    external = bg.TATOEBA.exists()
    for path in sorted(RAW.glob("corpus_*.txt")):
        text = bg.to_simplified(path.read_text(encoding="utf-8", errors="ignore"), tables)
        weight = bg.DAILY_WEIGHT if external else 1
        for line in text.splitlines():
            if line.startswith("#"):
                continue
            for run in HAN.findall(line):
                units = bg.segment(run, words, floor)
                for left, middle, right in zip(units, units[1:], units[2:]):
                    # 三连和"左文出现次数"必须用同一个权重，否则概率会算出 >1（自撰语料 600 倍
                    # 权重下，三连除以没加权的次数会被夹到 1000，等于说它必然发生）。
                    triples[(left, middle, right)] += weight
                    contexts[(left, middle)] += weight
        print(f"  {path.name}: 累计 {len(triples):,} 个三连")

    if external:
        seen: set[str] = set()
        with bz2.open(bg.TATOEBA, "rt", encoding="utf-8", errors="ignore") as handle:
            for line in handle:
                parts = line.rstrip("\n").split("\t")
                if len(parts) < 3 or not bg.usable_sentence(parts[2]):
                    continue
                sentence = bg.to_simplified(parts[2], tables)
                if sentence in seen:
                    continue
                seen.add(sentence)
                for run in HAN.findall(sentence):
                    units = bg.segment(run, words, floor)
                    for left, middle, right in zip(units, units[1:], units[2:]):
                        triples[(left, middle, right)] += 1
                        contexts[(left, middle)] += 1
        print(f"  Tatoeba: {len(seen):,} 句，累计 {len(triples):,} 个三连")

    # 三个单元都是单字的三连是最脏的一类（我|很|好 到处都是），和 build_bigram.py 的
    # --char-pairs 同一个口径：默认不收。
    triples = Counter({
        triple: count for triple, count in triples.items()
        if not all(len(unit) == 1 for unit in triple)
    })
    return triples, contexts


def score_triple(count: int, context: int) -> int:
    """P(right | left1, left2) 的绝对折扣，映射到 1..1000，与 bigram 表同一把尺子。"""
    if context <= 0:
        return 0
    discounted = max(count - DISCOUNT, 0.0)
    probability = discounted / context
    if probability <= 0:
        return 1
    return max(1, min(1000, int(round(1000 + 200 * math.log10(probability)))))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default=str(ASSETS / "pinyin_trigrams.txt"))
    args = parser.parse_args()

    words, _ = bg.load_words()
    floor = min(words.values()) if words else 1
    tables = bg.load_t2s()

    triples, contexts = count_trigrams(words, floor, tables)
    rows = []
    for (left, middle, right), count in triples.items():
        if count < MIN_COUNT:
            continue
        score = score_triple(count, contexts[(left, middle)])
        if score <= 0:
            continue
        rows.append((score, count, left, middle, right))
    rows.sort(key=lambda row: (-row[0], -row[1], row[2], row[3], row[4]))
    kept = rows[:MAX_TRIGRAMS]
    out = pathlib.Path(args.out)
    out.write_text(
        "".join(f"{left}\t{middle}\t{right}\t{score}\n" for score, _, left, middle, right in kept),
        encoding="utf-8",
    )
    keys = {(left, middle) for _, _, left, middle, _ in kept}
    print(f"  {len(rows):,} 个三连过筛，写 {len(kept):,} 行 / {len(keys):,} 个左文 -> {out.name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

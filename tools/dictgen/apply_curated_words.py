#!/usr/bin/env python3
"""把 curated 词（net_words + prominent_words）补进**已生成**的 pinyin_words.txt。

为什么需要这个脚本：完整的词表由 build_dict.py 生成，但它的几份语料（HSK、Tatoeba、
院校/行政区划、THUOCL 之外的表）只有在 tools/dictgen/fetch_corpora.sh 拉过之后才在本地。
没有这些语料时重新跑生成器会**缩小**词表，所以这里只做增量：给指定词补上读音与分数。

两份输入：
- clean/net_words.txt：补覆盖，保底分与 build_dict.py 的 net words 一致（661）；
- clean/prominent_words.txt：词<TAB>分数，补排序（压过同音词）。

读音优先取 clean/pypinyin_phrases.txt 的词级读音；没有时用 pinyin_chars.txt 里每个字
权重最高的读音。生成结果会打印出来，供人工核对多音字。

Usage:
    python3 tools/dictgen/apply_curated_words.py [--dry-run]
"""

from __future__ import annotations

import math
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app/src/main/assets"
NET_WORDS = ROOT / "tools/dictgen/clean/net_words.txt"
PROMINENT = ROOT / "tools/dictgen/clean/prominent_words.txt"
PHRASES = ROOT / "tools/dictgen/clean/pypinyin_phrases.txt"
CHARS = ASSETS / "pinyin_chars.txt"
WORDS = ASSETS / "pinyin_words.txt"

MAX_WORD_LENGTH = 8
NET_FLOOR = round(math.log10(2000 + 10) * 200)  # 与 build_dict.py 的 score_of(2000, 1.0) 一致

# 逐字首选读音拼不对的少数词（多音字）。生成器有训练读音模型，这里只有字表兜底，
# 所以把实测错的几条写死；新增词时若读音可疑，先看 --dry-run 的输出。
READING_OVERRIDES = {
    "青桔": "qingju",
    "飞行模式": "feixingmoshi",
}


def is_han(word: str) -> bool:
    return all("\u4e00" <= ch <= "\u9fff" for ch in word)


def load_phrases() -> dict[str, str]:
    readings: dict[str, str] = {}
    if not PHRASES.exists():
        return readings
    for line in PHRASES.read_text(encoding="utf-8", errors="ignore").splitlines():
        if "\t" not in line:
            continue
        word, reading = line.split("\t", 1)
        reading = reading.replace(" ", "").strip().lower()
        if word and reading and reading.isascii() and reading.isalpha():
            readings.setdefault(word, reading)
    return readings


def load_char_primary() -> dict[str, str]:
    best: dict[str, tuple[int, str]] = {}
    for line in CHARS.read_text(encoding="utf-8", errors="ignore").splitlines():
        parts = line.split("\t")
        if len(parts) != 3:
            continue
        syllable, chars, weight = parts[0], parts[1], parts[2]
        try:
            value = int(weight)
        except ValueError:
            continue
        for ch in chars:
            if ch not in best or value > best[ch][0]:
                best[ch] = (value, syllable)
    return {ch: syllable for ch, (_, syllable) in best.items()}


def load_curated() -> dict[str, int]:
    wanted: dict[str, int] = {}
    if NET_WORDS.exists():
        for line in NET_WORDS.read_text(encoding="utf-8", errors="ignore").splitlines():
            word = line.strip()
            if line.startswith("#") or not word:
                continue
            if 2 <= len(word) <= MAX_WORD_LENGTH and is_han(word):
                wanted[word] = max(wanted.get(word, 0), NET_FLOOR)
    if PROMINENT.exists():
        for line in PROMINENT.read_text(encoding="utf-8", errors="ignore").splitlines():
            if line.startswith("#") or "\t" not in line:
                continue
            word, _, raw = line.partition("\t")
            word = word.strip()
            digit = raw.strip().split()[0] if raw.strip() else ""
            if 2 <= len(word) <= MAX_WORD_LENGTH and is_han(word) and digit.isdigit():
                wanted[word] = max(wanted.get(word, 0), int(digit))
    return wanted


def main() -> int:
    dry_run = "--dry-run" in sys.argv
    curated = load_curated()
    phrases = load_phrases()
    char_primary = load_char_primary()

    rows = []
    for line in WORDS.read_text(encoding="utf-8").splitlines():
        parts = line.split("\t")
        if len(parts) != 3:
            continue
        rows.append([parts[0], parts[1], int(parts[2])])

    seen: dict[str, int] = {}
    for index, (_, word, score) in enumerate(rows):
        if word not in seen or score > rows[seen[word]][2]:
            seen[word] = index

    added: list[tuple[str, str, int]] = []
    updated: list[tuple[str, str, int, int]] = []
    skipped: list[str] = []
    for word, floor in sorted(curated.items()):
        reading = READING_OVERRIDES.get(word) or phrases.get(word)
        if not reading:
            chars = [char_primary.get(ch) for ch in word]
            if any(ch is None for ch in chars):
                skipped.append(word)
                continue
            reading = "".join(chars)
        if not reading.isascii() or not reading.isalpha():
            skipped.append(word)
            continue
        if word in seen:
            index = seen[word]
            old = rows[index][2]
            if old < floor:
                rows[index][2] = floor
                updated.append((word, rows[index][0], old, floor))
            continue
        added.append((reading, word, floor))

    print(f"curated: {len(curated)} 词，新增 {len(added)}，提分 {len(updated)}，跳过 {len(skipped)}")
    for word, reading, floor in added:
        print(f"  + {reading:16s} {word} {floor}")
    for word, reading, old, floor in updated:
        print(f"  ^ {reading:16s} {word} {old} -> {floor}")
    if skipped:
        print("  跳过（缺字或读音异常）:", " ".join(skipped))

    if dry_run:
        return 0

    for reading, word, floor in added:
        rows.append([reading, word, floor])
    # 生成器的输出按 (读音, -分数, 词) 排列；原文件就是这个顺序时保持它，否则按原顺序追加。
    if rows == sorted(rows, key=lambda r: (r[0], -r[2], r[1])):
        print("顺序未变")
    else:
        print("注意：原文件不是按 (读音, -分数) 排序，保持原顺序并在末尾追加新词")
    WORDS.write_text("".join(f"{r[0]}\t{r[1]}\t{r[2]}\n" for r in rows), encoding="utf-8")
    print(f"写入 {WORDS}，共 {len(rows)} 行")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Turns mozillazg/phrase-pinyin-data into the word -> reading table build_dict.py consumes.

Why a second phrase table when pypinyin already has one: they are the same kind of data at two
sizes. pypinyin ships the 47k phrases a reader actually meets; phrase-pinyin-data's
``large_pinyin.txt`` carries 41 万条 - the 汉语大词典 headwords and their readings. That is the
"更广的词库" half of the problem: 词级读音 decides which syllables a word is filed under, and a
word nobody states the reading for is left to the cartesian product's guesswork.

Where the two disagree the *smaller* table wins: it is the one every tuning decision so far was
made against, and 朝阳 zhāo/cháo 这种两读词两张表都说得对，不该在合并时丢掉一条
(所以一个字可能有**多条**读音，engine 端逐条进候选，见 build_dict.readings_for)。

Both files are MIT (see NOTICE.md); this script only normalises the shape - tones dropped,
ü -> v - which is the same normalisation the engine's syllable table uses.

Output: one line per (word, reading), ``<word>\\t<syllable> <syllable> ...``, so the reader can
check the syllable count against the word.

Usage: python3 tools/dictgen/prepare_phrase_pinyin.py raw/phrase_pinyin_large.txt \
    clean/phrase_pinyin_large.txt
"""

from __future__ import annotations

import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

from build_dict import HAN, MAX_WORD_LENGTH, normalize_syllable  # noqa: E402


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        return 2
    source, target = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])

    readings: dict[str, list[str]] = {}
    rows = 0
    skipped = 0
    for raw_line in source.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        word, sep, rest = line.partition(":")
        if not sep:
            skipped += 1
            continue
        word = word.strip()
        # 少数行带行尾注释（`地藏: dì zàng #…`）；注释不参与读音。
        value = rest.split("#")[0].strip()
        syllables: list[str] = []
        ok = True
        for piece in value.split():
            syllable = normalize_syllable(piece)
            if syllable is None:
                ok = False
                break
            syllables.append(syllable)
        if (
            not ok
            or not word
            or not (1 <= len(word) <= MAX_WORD_LENGTH)
            or not HAN.match(word)
            or len(syllables) != len(word)
        ):
            skipped += 1
            continue
        joined = " ".join(syllables)
        bucket = readings.setdefault(word, [])
        if joined not in bucket:
            bucket.append(joined)
            rows += 1

    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("w", encoding="utf-8") as out:
        for word in sorted(readings):
            for reading in readings[word]:
                out.write(f"{word}\t{reading}\n")

    size = target.stat().st_size
    multi = sum(1 for values in readings.values() if len(values) > 1)
    print(
        f"phrase-pinyin: {len(readings):,} 词条 / {rows:,} 条读音"
        f"（两读以上 {multi}，跳过 {skipped}），{size / 1024:.0f} KiB -> {target}",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Turns pypinyin's phrases_dict.json into the word -> reading table build_dict.py consumes.

Why a word-level table at all: pinyin-data (and every per-character source) cannot say which
reading a *word* uses. Reading 银行 out of its characters is xíng + yín, so the generator only
finds háng by taking the cartesian product of every reading and hoping the ranking sorts it out.
That is guesswork the engine then pays for with its alternate-reading penalty, and for words where
the uncommon reading is the correct one (重庆 chóngqìng, 长处 chángchù, 音乐 yīnyuè) it costs
score the word should have had. pypinyin's phrase table states the reading directly, so the word
enters the lattice with the right syllables and no penalty.

pypinyin is MIT licensed (see NOTICE.md); the table is its phrases_dict.json.

Output: one word per line, ``<word>\t<syllable> <syllable> ...`` - one syllable per character, so
the reader can check the count against the word, in the same tone-less shape the engine's syllable
table uses (ü -> v, tones dropped by decomposing the character).

Usage: python3 tools/dictgen/prepare_pypinyin.py raw/pypinyin_phrases_dict.json clean/pypinyin_phrases.txt
"""

from __future__ import annotations

import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

from build_dict import HAN, MAX_WORD_LENGTH, normalize_syllable  # noqa: E402


def reading_of(entry: list[list[str]]) -> list[str] | None:
    """One syllable per character, most likely reading first (pypinyin orders them that way)."""
    syllables: list[str] = []
    for options in entry:
        syllable = normalize_syllable(options[0]) if options else None
        if syllable is None:
            return None
        syllables.append(syllable)
    return syllables


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        return 2
    source, target = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])

    phrases: dict[str, list[list[str]]] = json.loads(source.read_text(encoding="utf-8"))
    rows: dict[str, list[str]] = {}
    skipped = 0
    for word, entry in phrases.items():
        if not (1 <= len(word) <= MAX_WORD_LENGTH) or not HAN.match(word):
            skipped += 1
            continue
        if len(entry) != len(word):
            skipped += 1
            continue
        reading = reading_of(entry)
        if reading is None:
            skipped += 1
            continue
        rows[word] = reading

    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("w", encoding="utf-8") as out:
        for word in sorted(rows):
            out.write(f"{word}\t{' '.join(rows[word])}\n")

    size = target.stat().st_size
    print(f"pypinyin: {len(rows):,} 词条读音（跳过 {skipped}），{size / 1024:.0f} KiB -> {target}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

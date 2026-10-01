#!/usr/bin/env python3
"""Builds the bundled Chinese dictionary from MIT licensed sources only.

| Source | Licence | What it contributes |
| --- | --- | --- |
| jieba `dict.txt` | MIT | broad word list with corpus frequency |
| THUOCL (THUNLP) | MIT | domain vocabulary: IT, 医学, 法律, 成语 ... |
| pinyin-data (mozillazg) | MIT | per character readings, ordered by commonness |

Everything here is deliberately MIT so the app can stay MIT. 雾凇拼音 was evaluated and
rejected: it is GPL-3.0-only and some of its upstream corpora are CC BY-SA or unlicensed.

Output:
    app/src/main/assets/pinyin_words.txt  reading<TAB>word<TAB>score
    app/src/main/assets/pinyin_chars.txt  syllable<TAB>chars<TAB>syllableWeight

Usage: python3 tools/dictgen/build_dict.py
"""

from __future__ import annotations

import json
import math
import pathlib
import re
import sys
import unicodedata
from collections import defaultdict

ROOT = pathlib.Path(__file__).resolve().parents[2]
TOOLS = ROOT / "tools/dictgen"
ASSETS = ROOT / "app/src/main/assets"

JIEBA = TOOLS / "raw_jieba_dict.txt"
PYINYIN_DATA = TOOLS / "clean/pinyin_data.txt"
THUOCL_DIR = TOOLS / "clean"
# Optional: HSK vocabulary with a corpus rank per word (MIT, drkameleon/complete-hsk-vocabulary).
# It is the only conversational-frequency source in the build, which is what keeps 怎么样 above
# 简体字 and 今天 above 几天 in the decoder.
HSK = TOOLS / "raw/hsk_complete.json"

THUOCL_FILES = [
    ("THUOCL_IT.txt", 0.95),
    ("THUOCL_medical.txt", 0.9),
    ("THUOCL_law.txt", 0.9),
    ("THUOCL_caijing.txt", 0.9),
    ("THUOCL_chengyu.txt", 1.0),
    ("THUOCL_food.txt", 0.85),
    ("THUOCL_animal.txt", 0.85),
    ("THUOCL_poem.txt", 0.8),
    ("THUOCL_lishimingren.txt", 0.8),
    ("THUOCL_car.txt", 0.85),
    # Place names shipped with THUOCL but unused until now: 地名 are exactly the kind of word
    # an IME is expected to know and a corpus list under-represents.
    ("THUOCL_diming.txt", 0.85),
]

MAX_WORD_LENGTH = 8
MAX_COMBINATIONS = 4
MAX_WORDS_PER_READING = 12
MAX_CHARS_PER_SYLLABLE = 100
# Charged per character that had to use a non primary reading. On the log scale below this is
# roughly "four times rarer", enough for the primary reading to win a tie without hiding words
# that are genuinely read the other way.
ALTERNATE_READING_PENALTY = 120
# Multiplied into the score of a word that only a THUOCL domain list has, so a term that is
# frequent inside its own tiny corpus does not outrank everyday words in sentence decoding.
THUOCL_ONLY_DISCOUNT = 0.75
# Sentence decoding needs breadth more than it needs a short list: every extra word is another
# path through the lattice. 200k rows covers the jieba corpus almost whole (348k entries, of which
# the two character floor and the Han filter already drop a large share) and keeps the asset near
# 5 MB.
MAX_WORD_ROWS = 200_000

HAN = re.compile(r"^[\u3400-\u4DBF\u4E00-\u9FFF\uF900-\uFAFF]+$")
SYLLABLE = re.compile(r"^[a-z]+$")


def load_readings(path: pathlib.Path) -> dict[str, list[str]]:
    """pinyin-data lists readings comma separated, most common first."""
    readings: dict[str, list[str]] = {}
    with path.open(encoding="utf-8") as handle:
        for line in handle:
            if not line.startswith("U+"):
                continue
            code, _, rest = line.partition(":")
            if not rest:
                continue
            try:
                char = chr(int(code.strip()[2:], 16))
            except ValueError:
                continue
            values = rest.split("#")[0].strip()
            ordered: list[str] = []
            for value in values.split(","):
                syllable = normalize_syllable(value.strip())
                if syllable and syllable not in ordered:
                    ordered.append(syllable)
            if ordered:
                readings[char] = ordered
    return readings


def normalize_syllable(value: str) -> str | None:
    """Strips tone marks and unifies ü, so the result matches our syllable table.

    Deleting the accented vowel outright would turn "měi" into "mi" and silently corrupt the
    whole table, so the tone is removed by decomposing the character instead.
    """
    if not value:
        return None
    prepared = value.lower().replace("u:", "v")
    for source in "üǖǘǚǜ":
        prepared = prepared.replace(source, "v")
    decomposed = unicodedata.normalize("NFD", prepared)
    ascii_only = "".join(ch for ch in decomposed if unicodedata.category(ch) != "Mn")
    stripped = re.sub(r"[0-9\s]", "", unicodedata.normalize("NFC", ascii_only))
    return stripped if stripped and SYLLABLE.match(stripped) else None


def score_of(frequency: int, source_weight: float) -> int:
    """Log compresses the huge frequency spread so no single corpus dominates the ranking."""
    return max(1, int(round(math.log10(frequency + 10) * 200 * source_weight)))


def load_jieba() -> dict[str, int]:
    words: dict[str, int] = {}
    with JIEBA.open(encoding="utf-8") as handle:
        for line in handle:
            parts = line.split()
            if len(parts) < 2:
                continue
            word = parts[0]
            try:
                frequency = int(parts[1])
            except ValueError:
                continue
            if 1 <= len(word) <= MAX_WORD_LENGTH and HAN.match(word):
                words[word] = max(words.get(word, 0), score_of(frequency, 1.0))
    return words


def load_hsk() -> dict[str, int]:
    """HSK vocabulary -> corpus rank (1 = the most frequent word in that corpus).

    The one conversational source in the build. Without it the news-heavy corpus puts 简体字 above
    怎么样 and 几天 above 今天, which is exactly what sentence and 首字母 candidates then repeat.
    """
    if not HSK.exists():
        return {}
    with HSK.open(encoding="utf-8") as handle:
        entries = json.load(handle)
    ranks: dict[str, int] = {}
    for entry in entries:
        word = entry.get("simplified")
        rank = entry.get("frequency")
        if not word or not isinstance(rank, int):
            continue
        if 2 <= len(word) <= MAX_WORD_LENGTH:
            ranks.setdefault(word, rank)
    return ranks


def hsk_bonus(rank: int) -> int:
    """How much a conversational rank is worth on the log-frequency score scale."""
    if rank <= 500:
        return 260
    if rank <= 1500:
        return 200
    if rank <= 3000:
        return 140
    if rank <= 6000:
        return 80
    return 0


def load_thuocl() -> dict[str, int]:
    words: dict[str, int] = {}
    for filename, source_weight in THUOCL_FILES:
        path = THUOCL_DIR / filename
        if not path.exists():
            print(f"! missing {filename}", file=sys.stderr)
            continue
        with path.open(encoding="utf-8") as handle:
            for line in handle:
                parts = line.replace("\t", " ").split()
                if len(parts) < 2:
                    continue
                word = parts[0]
                try:
                    frequency = int(parts[1])
                except ValueError:
                    continue
                if not (2 <= len(word) <= MAX_WORD_LENGTH) or not HAN.match(word):
                    continue
                words[word] = max(words.get(word, 0), score_of(frequency, source_weight))
    return words


def readings_for(word: str, char_readings: dict[str, list[str]]) -> list[tuple[str, int]]:
    """Cartesian product of per character readings, capped, primary first.

    Returns (reading, alternates) pairs: ``alternates`` counts how many characters used a reading
    that is not their most common one. pinyin-data lists rare readings too (支 is zhī but also qí),
    and taking those as equals used to invent readings like "qichi" for 支持, which then won
    sentence decoding over the real "zhichi". The count lets the caller charge for the guess
    instead of banning it - genuinely polyphonic words such as 音乐 need the alternate.
    """
    combinations: list[tuple[list[str], int]] = [([], 0)]
    for char in word:
        options = char_readings.get(char)
        if not options:
            return []
        take = options[:2]
        if len(combinations) * len(take) > MAX_COMBINATIONS:
            take = options[:1]
        combinations = [
            (prefix + [option], alternates + (1 if index > 0 else 0))
            for prefix, alternates in combinations
            for index, option in enumerate(take)
        ]
    result: list[tuple[str, int]] = []
    seen: set[str] = set()
    for combination, alternates in combinations[:MAX_COMBINATIONS]:
        reading = "".join(combination)
        if reading in seen:
            continue
        seen.add(reading)
        result.append((reading, alternates))
    return result


def main() -> int:
    for path in (JIEBA, PYINYIN_DATA):
        if not path.exists():
            print(f"! missing {path}", file=sys.stderr)
            return 1

    char_readings = load_readings(PYINYIN_DATA)
    print(f"pinyin-data: {len(char_readings)} characters")

    jieba = load_jieba()
    thuocl = load_thuocl()
    hsk = load_hsk()
    print(f"jieba: {len(jieba)} words, THUOCL: {len(thuocl)} words, HSK: {len(hsk)} words")

    word_scores: dict[str, int] = dict(jieba)
    # Domain vocabulary complements the corpus list rather than overriding it. A word that only a
    # domain list knows is treated as rarer than its own corpus count suggests: 一汽 is frequent in
    # a car corpus and 机电 in an IT one, and without the discount both outrank 一起 and 几点 -
    # which is fine for a lookup list but wrong for sentence decoding.
    for word, score in thuocl.items():
        if word not in word_scores:
            word_scores[word] = int(score * THUOCL_ONLY_DISCOUNT)
        else:
            word_scores[word] = max(word_scores[word], int(score * 0.9))

    # Conversational frequency, on top of the corpus lists. `frequency` in that file is a corpus
    # rank (today 155, 怎么 128, 怎么样 1367, 简体字 57431), so it is applied in tiers: the words a
    # person says out loud beat the words a news corpus happens to repeat.
    for word, rank in hsk.items():
        bonus = hsk_bonus(rank)
        if bonus:
            word_scores[word] = word_scores.get(word, 0) + bonus

    by_reading: dict[str, list[tuple[str, int]]] = defaultdict(list)
    # A character's own frequency as a standalone word is the right signal for ordering single
    # syllable candidates. Summing the words it appears in is not: 尼 shows up in 印尼, 索尼,
    # 罗马尼亚... and would outrank 你, which is what users actually type.
    standalone: dict[str, int] = defaultdict(int)
    derived: dict[str, int] = defaultdict(int)
    syllable_weight: dict[str, int] = defaultdict(int)
    char_reading_of: dict[str, str] = {}

    def hit(char: str, weight: int) -> None:
        derived[char] = derived.get(char, 0) + weight
        primary = char_readings[char][0]
        char_reading_of.setdefault(char, primary)
        syllable_weight[primary] = syllable_weight.get(primary, 0) + weight

    for word, score in word_scores.items():
        readings = readings_for(word, char_readings)
        if not readings:
            continue
        # Longer words contribute less per character so common single characters do not get
        # buried under one very frequent idiom.
        per_char = max(1, score // max(1, len(word)))
        for index, char in enumerate(word):
            # A character is a candidate for its syllable mostly when it *starts* a word, so
            # leading positions count fully and trailing ones are discounted.
            hit(char, per_char if index == 0 else max(1, per_char // 3))
        if len(word) == 1:
            standalone[word] = max(standalone[word], score)
            continue
        for reading, alternates in readings:
            by_reading[reading].append((word, max(1, score - alternates * ALTERNATE_READING_PENALTY)))

    rows: list[tuple[str, str, int]] = []
    for reading, entries in by_reading.items():
        entries.sort(key=lambda item: (-item[1], len(item[0]), item[0]))
        for word, score in entries[:MAX_WORDS_PER_READING]:
            rows.append((reading, word, score))

    # Greedy fill by score, bounded both per reading and in total. Readings that never win a
    # slot simply fall back to single character conversion, which is what a rare reading wants.
    rows.sort(key=lambda row: (-row[2], len(row[1]), row[1]))
    chosen: list[tuple[str, str, int]] = []
    per_reading: dict[str, int] = defaultdict(int)
    for row in rows:
        if len(chosen) >= MAX_WORD_ROWS:
            break
        reading = row[0]
        if per_reading[reading] >= MAX_WORDS_PER_READING:
            continue
        per_reading[reading] += 1
        chosen.append(row)

    with (ASSETS / "pinyin_words.txt").open("w", encoding="utf-8") as out:
        for reading, word, score in sorted(chosen):
            out.write(f"{reading}\t{word}\t{score}\n")

    # Rank-normalise the derived signal so it becomes a tiebreaker inside one magnitude band.
    ranked = sorted(derived.items(), key=lambda item: -item[1])
    total = max(1, len(ranked))
    derived_rank = {
        char: int(round((1 - index / total) * 999))
        for index, (char, _) in enumerate(ranked)
    }

    by_syllable: dict[str, list[tuple[str, int]]] = defaultdict(list)
    for char in derived:
        weight = standalone.get(char, 0) * 1000 + derived_rank.get(char, 0)
        by_syllable[char_reading_of[char]].append((char, weight))

    with (ASSETS / "pinyin_chars.txt").open("w", encoding="utf-8") as out:
        for syllable in sorted(by_syllable):
            entries = by_syllable[syllable]
            entries.sort(key=lambda item: (-item[1], item[0]))
            chars = "".join(char for char, _ in entries[:MAX_CHARS_PER_SYLLABLE])
            out.write(f"{syllable}\t{chars}\t{syllable_weight.get(syllable, 1)}\n")

    words_size = (ASSETS / "pinyin_words.txt").stat().st_size
    chars_size = (ASSETS / "pinyin_chars.txt").stat().st_size
    print(f"words: {len(chosen)} rows, {words_size / 1024:.0f} KiB")
    print(f"chars: {sum(len(v) for v in by_syllable.values())} rows, {chars_size / 1024:.0f} KiB")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

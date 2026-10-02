#!/usr/bin/env python3
"""Turns Unicode's Unihan database into the one table build_dict.py wants.

Why
---
Everything the dictionary knows about a character's *reading* comes from pinyin-data, which lists
readings in their order of commonness but says nothing about how often each one is actually used.
That is enough for 了 (le, then liǎo) and wrong for the rest: 乐 is filed under "le" only, so
typing "yue" does not offer it at all; 谁 is filed under "shui", so "shei" - how the word is
pronounced in speech - has no character of its own; "dei" has no 得 and "huan" no 还.

Unihan supplies exactly that missing number:

| Field | What it is |
| --- | --- |
| `kHanyuPinlu` | how often each reading occurs in 《现代汉语频率词典》 - a corpus count per reading |
| `kMandarin` | the customary reading of the character |

`build_dict.py` uses them to place a character under the readings it is actually read with, not
just under its first one. See `SECOND_READING_SHARE` there for the threshold.

Licensing
---------
Unihan is published by the Unicode Consortium under the Unicode License v3 (permissive: use,
modify and redistribute with attribution, no endorsement claim). The zip is fetched by
`fetch_corpora.sh` and stays out of the repository; only the columns below are shipped.

Usage:
    python3 tools/dictgen/prepare_unihan.py raw/Unihan.zip clean/unihan_readings.txt
"""

from __future__ import annotations

import pathlib
import re
import sys
import unicodedata
import zipfile

READINGS_MEMBER = "Unihan_Readings.txt"
# `le(30101) liǎo(654) liào(19)` - accented syllables, one count each.
PINLU_ENTRY = re.compile(r"([^\s()]+)\((\d+)\)")


def to_syllable(value: str) -> str | None:
    """Tone-marked pinyin to the tone-less syllable the dictionary is keyed by."""
    if not value:
        return None
    prepared = value.lower().replace("u:", "v")
    for source in "üǖǘǚǜ":
        prepared = prepared.replace(source, "v")
    decomposed = unicodedata.normalize("NFD", prepared)
    ascii_only = "".join(ch for ch in decomposed if unicodedata.category(ch) != "Mn")
    stripped = re.sub(r"[0-9\s]", "", unicodedata.normalize("NFC", ascii_only))
    return stripped if stripped and stripped.isascii() and stripped.isalpha() else None


def parse(archive: pathlib.Path) -> list[tuple[str, str, int, str]]:
    """(character, syllable, count, kind) rows; kind is `pinlu` or `mandarin`."""
    rows: list[tuple[str, str, int, str]] = []
    with zipfile.ZipFile(archive) as bundle:
        with bundle.open(READINGS_MEMBER) as handle:
            for raw in handle:
                parts = raw.decode("utf-8").rstrip("\n").split("\t")
                if len(parts) != 3 or not parts[0].startswith("U+"):
                    continue
                character = chr(int(parts[0][2:], 16))
                field, value = parts[1], parts[2]
                if field == "kHanyuPinlu":
                    seen: dict[str, int] = {}
                    for match in PINLU_ENTRY.finditer(value):
                        syllable = to_syllable(match.group(1))
                        if not syllable:
                            continue
                        # The same syllable turns up twice when it has two tones (得 dé/de);
                        # as far as the dictionary is concerned they are one reading.
                        seen[syllable] = seen.get(syllable, 0) + int(match.group(2))
                    rows.extend(
                        (character, syllable, count, "pinlu")
                        for syllable, count in sorted(seen.items(), key=lambda item: -item[1])
                    )
                elif field == "kMandarin":
                    syllable = to_syllable(value.split()[0])
                    if syllable:
                        rows.append((character, syllable, 0, "mandarin"))
    return rows


def main() -> int:
    if len(sys.argv) != 3:
        print("用法: prepare_unihan.py <Unihan.zip> <out.txt>", file=sys.stderr)
        return 2
    archive = pathlib.Path(sys.argv[1])
    target = pathlib.Path(sys.argv[2])
    if not archive.exists():
        print(f"! missing {archive}（跑 tools/dictgen/fetch_corpora.sh 获取）", file=sys.stderr)
        return 1

    rows = parse(archive)
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("w", encoding="utf-8") as out:
        for character, syllable, count, kind in rows:
            out.write(f"{character}\t{syllable}\t{count}\t{kind}\n")
    characters = len({row[0] for row in rows})
    readings = sum(1 for row in rows if row[3] == "pinlu")
    print(f"Unihan: {characters} 字 / {readings} 条读音频率 -> {target.name}（Unicode License v3）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

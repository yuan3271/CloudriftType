#!/usr/bin/env python3
"""Builds the unit-to-unit association table behind both 整句解码 and 联想.

Why this file exists
--------------------
Association ("联想") is not a lookup problem, it is a language-model problem: given the text
so far, what is the probability of the next unit? The decoder therefore needs P(next | prev),
and a frequency list cannot supply it - 向河北 and 我想装 are made of real words, they simply
never occur in that position.

What it does
------------
Counts adjacent unit pairs in the training corpus, keeps the ones with enough support, and
writes their score as an integer so the Kotlin decoder can add it to a path's cost.

The table has two readers: the sentence decoder adds a pair's score to a path's cost, and the 联想
strip lists a word's successors after it is committed.

Licensing
---------
Two corpora build the table. The backbone is this project's own everyday text in
tools/dictgen/raw/corpus_*.txt (MIT, written for this project) - small, but the only text that
reflects how the keyboard is actually used. On top of it comes **Tatoeba's cmn export** (89k
human-written sentences, CC BY 2.0 FR: attribution only, no share-alike and no non-commercial
clause), which is what finally gives the pair counts enough support to be worth discounting.
`--classics` additionally reads the public-domain Gutenberg novels (18th/19th century).

No text is ever shipped, only the counts derived from it; `tools/dictgen/fetch_corpora.sh`
re-downloads the Tatoeba snapshot. The word list that drives segmentation is the MIT asset built
by build_dict.py, and the traditional -> simplified normalisation comes from OpenCC under
tools/dictgen/opencc (Apache-2.0). See NOTICE.md.

Usage:
    python3 tools/dictgen/build_bigram.py                  # modern colloquial only
    python3 tools/dictgen/build_bigram.py --classics       # add the public domain novels
    python3 tools/dictgen/build_bigram.py --variant-sweep  # writes A/B variants under tools/tune
"""

from __future__ import annotations

import argparse
import bz2
import math
import pathlib
import re
from collections import Counter
from dataclasses import dataclass

ROOT = pathlib.Path(__file__).resolve().parents[2]
RAW = ROOT / "tools/dictgen/raw"
OPENCC = ROOT / "tools/dictgen/opencc"
ASSETS = ROOT / "app/src/main/assets"
SWEEP_DIR = ROOT / "tools/tune/bigrams"

HAN = re.compile(r"[\u4e00-\u9fff]+")
HAN_CHAR = re.compile(r"[\u4e00-\u9fff]")
# Sentences only survive if they are Chinese and punctuation - a Latin name ("Tom 跑得比 Bob
# 快") would otherwise be segmented into characters that never belong together.
SENTENCE_PUNCT = frozenset("，。！？、；：“”‘’（）《》〈〉「」『』…—～·　")

# Tatoeba's cmn export: human-written everyday sentences, CC BY 2.0 FR. Optional at build time so
# the script still runs on a checkout that has not fetched it.
TATOEBA = RAW / "tatoeba_cmn_sentences.tsv.bz2"
# One Tatoeba occurrence counts once; the hand-written corpus is scaled up instead (see
# DAILY_WEIGHT), because the thing being expressed is "how many occurrences is a hand-written
# one worth", and a fractional Tatoeba count would break the absolute discounting below 1.
TATOEBA_WEIGHT = 1.0
# How many occurrences one *hand-written* one is worth. The two corpora differ in size by ~500x,
# so mixing raw counts lets Tatoeba's generic distribution outvote the keyboard's own opinion
# (今天|天气 lost to 今天|是, and 联想 dropped to 8/14). Weighting the hand-written corpus is how
# the deliberate part of the corpus keeps its say: for a context it knows, it holds
# DAILY_WEIGHT/(DAILY_WEIGHT + Tatoeba count) of the probability mass, and for a context it does
# not know, Tatoeba carries it alone - which is the behaviour wanted in both cases.
DAILY_WEIGHT = 600.0
# A pair seen once in 89k sentences is a coincidence, not a collocation. The hand-written corpus
# is the opposite - one occurrence is all the evidence there is - so the floor is per source.
TATOEBA_MIN_COUNT = 2
# Longest sentence kept. Tatoeba reaches 40+ characters, and a long translated sentence is a lot
# of extra pairs for very little signal about what people type next.
MAX_TATOEBA_CHARS = 30

# One unit never exceeds this many characters, which is what keeps the segmentation DP small.
MAX_WORD_LENGTH = 6
# The asset is loaded into a HashMap on every device that opens the keyboard, so the table is
# capped well below what the corpus could support: hundreds of thousands of pairs would cost tens
# of megabytes of heap on an IME process for a shrinking return in accuracy.
MAX_BIGRAMS = 60_000
# A pair has to be seen this many times (after the per-source weighting below) to be shipped. One
# is right for the self-authored corpus, which is small and where a single occurrence is still
# the only evidence there is; the classical texts are dense enough to afford a higher threshold.
MIN_PAIR_COUNT = 1

# Absolute discounting constant for the conditional model. 0.75 is the value the Kneser-Ney
# literature keeps landing on, and it is what stops a continuation seen once from looking certain.
DISCOUNT = 0.75

# Every unit costs this much, which is what stops the splitter from preferring single characters.
# Same idea as jieba's log(frequency) - log(total) score.
UNIT_COST = 6.0

# The everyday corpus is the default source, so it needs no weight of its own. When --classics is
# on, this is what keeps an everyday collocation (马上|就到) from being drowned out by a classical
# formula (孔明|曰) that happens to appear hundreds of times in a novel.
# (The weight itself is defined with the Tatoeba constants above - the same lever keeps the
# hand-written lines in proportion with whichever larger corpus is switched on.)


# --------------------------------------------------------------- traditional -> simplified


def load_t2s() -> tuple[dict[str, str], dict[str, str], frozenset[str], int]:
    """OpenCC's t2s tables, as (phrases, chars, phrase initials, longest phrase)."""
    phrases: dict[str, str] = {}
    for raw in (OPENCC / "TSPhrases.txt").read_text(encoding="utf-8").splitlines():
        if not raw or raw.startswith("#"):
            continue
        key, _, value = raw.partition("\t")
        parts = value.split()
        if key and parts:
            phrases[key] = parts[0]

    chars: dict[str, str] = {}
    for raw in (OPENCC / "TSCharacters.txt").read_text(encoding="utf-8").splitlines():
        if not raw or raw.startswith("#"):
            continue
        key, _, value = raw.partition("\t")
        parts = value.split()
        if len(key) == 1 and parts:
            chars[key] = parts[0]

    initials = frozenset(key[0] for key in phrases)
    longest = max((len(key) for key in phrases), default=1)
    return phrases, chars, initials, longest


def to_simplified(text: str, tables: tuple[dict[str, str], dict[str, str], frozenset[str], int]) -> str:
    """Greedy longest-match traditional -> simplified, phrase first then character."""
    phrases, chars, initials, longest = tables
    if not phrases and not chars:
        return text
    out: list[str] = []
    index = 0
    size = len(text)
    while index < size:
        matched = 0
        if text[index] in initials:
            upper = min(longest, size - index)
            for length in range(upper, 1, -1):
                piece = text[index : index + length]
                if piece in phrases:
                    out.append(phrases[piece])
                    matched = length
                    break
        if matched:
            index += matched
            continue
        char = text[index]
        out.append(chars.get(char, char))
        index += 1
    return "".join(out)


# --------------------------------------------------------------- corpus -> unit pairs


def load_words() -> tuple[dict[str, int], int]:
    """word -> best score from the generated word list, plus the maximum score seen."""
    words: dict[str, int] = {}
    best = 1
    with (ASSETS / "pinyin_words.txt").open(encoding="utf-8") as handle:
        for line in handle:
            parts = line.rstrip("\n").split("\t")
            if len(parts) != 3:
                continue
            word, score = parts[1], int(parts[2])
            if word not in words or score > words[word]:
                words[word] = score
            best = max(best, score)
    return words, best


def segment(text: str, words: dict[str, int], floor: int) -> list[str]:
    """Best-scoring split of a Han run into dictionary words (dynamic programming).

    Words unknown to the list are kept as single characters, which is how the corpus contributes
    pairs like 喝|杯|水 without inventing vocabulary.
    """
    n = len(text)
    best = [0.0] * (n + 1)
    back = [0] * (n + 1)
    for end in range(1, n + 1):
        best[end] = float("-inf")
        for length in range(1, min(MAX_WORD_LENGTH, end) + 1):
            piece = text[end - length : end]
            score = words.get(piece)
            if score is None:
                if length > 1:
                    continue
                score = floor
            candidate = best[end - length] + math.log(score + 1) - UNIT_COST
            if candidate > best[end]:
                best[end] = candidate
                back[end] = length
    out: list[str] = []
    position = n
    while position > 0:
        length = back[position]
        out.append(text[position - length : position])
        position -= length
    return out[::-1]


def usable_sentence(text: str) -> bool:
    """True for a sentence the association model should learn from: Chinese only, sane length."""
    if not 2 <= len(HAN_CHAR.findall(text)) <= MAX_TATOEBA_CHARS:
        return False
    return all(HAN_CHAR.match(char) or char in SENTENCE_PUNCT for char in text)


def count_tatoeba_pairs(
    words: dict[str, int],
    floor: int,
    tables: tuple[dict[str, str], dict[str, str], frozenset[str], int],
    keep_char_pairs: bool,
) -> Counter[tuple[str, str]]:
    """Adjacent-unit counts from the Tatoeba cmn export, raw (not yet weighted)."""
    pairs: Counter[tuple[str, str]] = Counter()
    if not TATOEBA.exists():
        print(f"! missing {TATOEBA.name}（跑 tools/dictgen/fetch_corpora.sh 获取）", file=sys.stderr)
        return pairs
    seen: set[str] = set()
    with bz2.open(TATOEBA, "rt", encoding="utf-8", errors="ignore") as handle:
        for line in handle:
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 3 or not usable_sentence(parts[2]):
                continue
            sentence = to_simplified(parts[2], tables)
            # Translations are contributed by many people; the same sentence counts once.
            if sentence in seen:
                continue
            seen.add(sentence)
            for run in HAN.findall(sentence):
                units = segment(run, words, floor)
                for left, right in zip(units, units[1:]):
                    if not keep_char_pairs and len(left) == 1 and len(right) == 1:
                        continue
                    pairs[(left, right)] += 1
    print(f"  Tatoeba: {len(seen):,} 句，{len(pairs):,} 个词对（≥{TATOEBA_MIN_COUNT} 次才计入）")
    return Counter({pair: count for pair, count in pairs.items() if count >= TATOEBA_MIN_COUNT})


@dataclass(frozen=True)
class Sources:
    """Which corpora to read and how loud each one is."""

    classics: bool = False
    tatoeba: bool = True
    tatoeba_weight: float = TATOEBA_WEIGHT
    daily_weight: float = DAILY_WEIGHT
    char_pairs: bool = True


def build_pairs(
    words: dict[str, int],
    floor: int,
    tables: tuple[dict[str, str], dict[str, str], frozenset[str], int],
    sources: Sources,
) -> tuple[Counter[tuple[str, str]], frozenset[tuple[str, str]]]:
    """Weighted pair counts, plus the pairs the hand-written corpus itself contributed.

    The second value matters because the hand-written corpus is the only source that encodes how
    this keyboard is meant to be used: its pairs must survive both the min-count filter and the
    MAX_BIGRAMS cap even when a large corpus would outrank them on raw counts.
    """
    pairs: Counter[tuple[str, str]] = Counter()
    curated: set[tuple[str, str]] = set()
    paths = sorted(RAW.glob("corpus_*.txt"))
    if sources.classics:
        paths += sorted(RAW.glob("novel_*.txt"))
    # The weight only exists to keep the hand-written lines in proportion with a much larger
    # corpus; with no larger corpus there is nothing to weigh them against.
    external = sources.classics or sources.tatoeba
    for path in paths:
        text = to_simplified(path.read_text(encoding="utf-8", errors="ignore"), tables)
        is_curated = path.name.startswith("corpus_")
        weight = sources.daily_weight if (external and is_curated) else 1
        sentences = 0
        for line in text.splitlines():
            if line.startswith("#"):
                continue
            for run in HAN.findall(line):
                units = segment(run, words, floor)
                sentences += 1
                for left, right in zip(units, units[1:]):
                    # Both sides being a single character is the noisiest class of pair (的|了 is
                    # everywhere and says little), so it is opt-in.
                    if not sources.char_pairs and len(left) == 1 and len(right) == 1:
                        continue
                    pairs[(left, right)] += weight
                    if is_curated:
                        curated.add((left, right))
        print(f"  {path.name}: {sentences:,} 句，累计 {len(pairs):,} 个词对")
    if sources.tatoeba:
        weight = int(round(sources.tatoeba_weight))
        extra = count_tatoeba_pairs(words, floor, tables, sources.char_pairs)
        for pair, count in extra.items():
            pairs[pair] += count * weight
    return pairs, frozenset(curated)


# --------------------------------------------------------------- scoring


def score_pair(
    count: int,
    left_total: int,
    pair_total: int,
    mode: str,
    unigram: float = 0.0,
    singletons: int = 0,
) -> int:
    """Association strength of one pair, on the 1..1000 scale the decoder adds to its path cost.

    `joint` is the historical formula (share of all pairs seen), kept only so the benchmark can
    compare against it. `conditional` is the bigram language-model quantity P(right | left),
    smoothed with absolute discounting (the Kneser-Ney family): a left unit seen once has exactly
    one continuation in the corpus, and the raw maximum-likelihood estimate would call that
    continuation certain (P = 1, so 一世|的 scored a full 1000). Discounting the observed counts
    and backing off to the unigram distribution reserves probability mass for the continuations
    that were simply not in the corpus, which is what keeps rare pairs from looking certain.

    The result is mapped onto 1..1000 with a log scale, so the decoder's additive cost can mix it
    with word scores: p = 1 maps to 1000, p = 0.01 to 600, and anything rarer than 1e-5 to 1.
    """
    if mode == "joint":
        ratio = math.log10(count + 1) / math.log10(pair_total + 1)
        return max(1, int(round(ratio * 1000)))
    if left_total <= 0:
        return 0
    discounted = max(count - DISCOUNT, 0.0)
    probability = (discounted + DISCOUNT * singletons * unigram) / left_total
    if probability <= 0:
        return 1
    return max(1, min(1000, int(round(1000 + 200 * math.log10(probability)))))


def write_table(
    path: pathlib.Path,
    pairs: Counter[tuple[str, str]],
    mode: str,
    min_count: int,
    always_keep: frozenset[tuple[str, str]] = frozenset(),
) -> list[tuple[tuple[str, str], int, int]]:
    # Statistics come from the *unfiltered* counts: a continuation that the min-count filter drops
    # still happened, and the discount has to account for it.
    left_totals: Counter[str] = Counter()
    right_totals: Counter[str] = Counter()
    singletons: Counter[str] = Counter()
    for (left, right), count in pairs.items():
        left_totals[left] += count
        right_totals[right] += count
        if count == 1:
            singletons[left] += 1
    tail_total = sum(right_totals.values()) or 1

    kept = [
        (pair, count) for pair, count in pairs.items()
        if count >= min_count or pair in always_keep
    ]
    kept.sort(key=lambda item: -item[1])
    # The hand-written pairs are never what the cap evicts; the large corpus fills what is left.
    head = [item for item in kept if item[0] in always_keep]
    tail = [item for item in kept if item[0] not in always_keep]
    kept = head + tail[: max(0, MAX_BIGRAMS - len(head))]

    pair_total = sum(count for _, count in kept) or 1

    rows = [
        (
            (left, right),
            count,
            score_pair(
                count,
                left_totals[left],
                pair_total,
                mode,
                unigram=right_totals[right] / tail_total,
                singletons=singletons[left],
            ),
        )
        for (left, right), count in kept
    ]
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as out:
        for (left, right), _, score in rows:
            out.write(f"{left}\t{right}\t{score}\n")
    return rows


SAMPLES = [("我", "想"), ("喝", "水"), ("再见", "吧"), ("今天", "天气"), ("知道", "了"), ("孔明", "曰")]


def report(name: str, path: pathlib.Path, pairs: Counter[tuple[str, str]], rows: list, min_count: int) -> None:
    size = path.stat().st_size
    print(f"{name}: 保留 {len(rows):,} 对（count >= {min_count}），{size / 1024:.0f} KiB")
    print("  样例: " + "  ".join(f"{a}|{b}={pairs.get((a, b), 0)}" for a, b in SAMPLES))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default=str(ASSETS / "pinyin_bigrams.txt"))
    parser.add_argument("--classics", dest="classics", action="store_true", default=False)
    parser.add_argument("--no-classics", dest="classics", action="store_false")
    # Tatoeba is on by default: it is permissively licensed *and* modern, unlike the classics.
    parser.add_argument("--tatoeba", dest="tatoeba", action="store_true", default=True)
    parser.add_argument("--no-tatoeba", dest="tatoeba", action="store_false")
    parser.add_argument("--tatoeba-weight", type=float, default=TATOEBA_WEIGHT)
    parser.add_argument("--daily-weight", type=float, default=DAILY_WEIGHT)
    # Character-to-character pairs are what 联想 needs most: after a lone 我 the next thing typed
    # is usually another single character (想, 觉得's 觉...). They cost a little accuracy in the
    # sentence decoder, which is why the sweep still measures both.
    parser.add_argument("--char-pairs", dest="char_pairs", action="store_true", default=True)
    parser.add_argument("--no-char-pairs", dest="char_pairs", action="store_false")
    parser.add_argument("--score", choices=("joint", "conditional"), default="conditional")
    parser.add_argument("--min-count", type=int, default=MIN_PAIR_COUNT)
    parser.add_argument("--variant-sweep", action="store_true")
    args = parser.parse_args()

    words, best_word_score = load_words()
    floor = max(1, best_word_score // 50)
    tables = load_t2s()
    print(f"词表: {len(words):,} 词，单字兜底分 {floor}")
    print(f"繁简表: {len(tables[0])} 词组 / {len(tables[1])} 单字（OpenCC，Apache-2.0）")

    if not args.variant_sweep:
        sources = Sources(
            classics=args.classics,
            tatoeba=args.tatoeba,
            tatoeba_weight=args.tatoeba_weight,
            daily_weight=args.daily_weight,
            char_pairs=args.char_pairs,
        )
        pairs, curated = build_pairs(words, floor, tables, sources)
        rows = write_table(pathlib.Path(args.out), pairs, args.score, args.min_count, curated)
        report("shipped", pathlib.Path(args.out), pairs, rows, args.min_count)
        print(f"  自撰语料词对 {len(curated):,} 个（全部保留，不受词对上限影响）")
        return 0

    # A sweep of the design space, so the engine can be tuned against the benchmark instead of
    # against a feeling: what do the classics add, does the conditional score help, do character
    # pairs earn their bytes, how loud should Tatoeba be next to the hand-written corpus.
    variants: list[tuple[str, Sources | None]] = [
        ("no-bigram", None),
        ("daily-conditional", Sources(tatoeba=False, char_pairs=False)),
        ("daily-chars-conditional", Sources(tatoeba=False)),
        ("tatoeba-dw100", Sources(daily_weight=100)),
        ("tatoeba-dw300", Sources(daily_weight=300)),
        ("tatoeba-dw600", Sources(daily_weight=600)),
        ("tatoeba-dw2000", Sources(daily_weight=2000)),
        ("classics-conditional", Sources(tatoeba=False, classics=True, char_pairs=False)),
        ("classics-chars-conditional", Sources(tatoeba=False, classics=True)),
    ]
    for name, sources in variants:
        if sources is None:
            SWEEP_DIR.mkdir(parents=True, exist_ok=True)
            (SWEEP_DIR / "no-bigram.txt").write_text("", encoding="utf-8")
            print("no-bigram: 空表（无联想的基线）")
            continue
        pairs, curated = build_pairs(words, floor, tables, sources)
        rows = write_table(
            SWEEP_DIR / f"{name}.txt", pairs, "conditional", args.min_count, curated,
        )
        report(name, SWEEP_DIR / f"{name}.txt", pairs, rows, args.min_count)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

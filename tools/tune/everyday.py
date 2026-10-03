#!/usr/bin/env python3
"""量 `./tools/tune/score.sh` 量不到的两件事：日常词可打性、读音对应的准确率。

为什么另开一张台
----------------
`score.sh` 跑的两张 Kotlin 基准（573 句自撰语料 + 31 句手挑）用的都是常见词，对下面两件事
**完全不敏感**：把词表从 20 万行翻到 30 万行、把 HSK 保底分翻倍、给两万多个日常词抬分——
四张表的数字一个都不动。可"日常词够不够大、读音对不对"又恰恰是用户在意的，所以这里用资产
自己的数据量它们：

  1. **日常词可打性**：Tatoeba 日常句子里出现过（>= [MIN_COUNT] 次）的词，在自己的读音下
     能不能排到第一。量的是"这个词在词表里够不够靠前"，与 `pinyin_words.txt` 一一对应。
     样本来自 `tools/dictgen/raw/tatoeba_cmn_sentences.tsv.bz2`（CC BY 2.0 FR，跑
     `tools/dictgen/fetch_corpora.sh` 获取）；没抓到这个文件时只跑第 2 项。
  2. **读音对应准确率**：HSK 3.0 的 11,092 条自带拼音（`raw/hsk30.csv`，MIT），而构建过程
     **故意不用它的拼音**（没有音节分隔，切错一个字就定错整张表），所以它是一份干净的留出集：
     用词表给每个词定的音去对它的拼音，对不对一目了然。
  3. **读音留出实验**（`phrase-pinyin-data` 的大表在本地时）：把 41 万条词级读音按 9:1 切开，
     用 90% 训练、10% 测试，比较"逐字笛卡尔积"与"自训练模型"的**词级读音准确率**。第 2 项量
     的是"词有没有排到该读音的第一位"，这一项量的是"给这个词定的音对不对"，两件事。

Usage:
    python3 tools/tune/everyday.py
"""

from __future__ import annotations

import argparse
import collections
import csv
import pathlib
import random
import re
import sys
import unicodedata

ROOT = pathlib.Path(__file__).resolve().parents[2]
TOOLS = ROOT / "tools/dictgen"
ASSETS = ROOT / "app/src/main/assets"
sys.path.insert(0, str(TOOLS))

import build_dict  # noqa: E402  （复用它的切词与读音工具）

JIEBA = TOOLS / "raw_jieba_dict.txt"
TATOEBA = TOOLS / "raw/tatoeba_cmn_sentences.tsv.bz2"
HSK30 = TOOLS / "raw/hsk30.csv"
WORDS = ASSETS / "pinyin_words.txt"

MIN_COUNT = 3


def load_word_table(path: pathlib.Path) -> tuple[dict[str, tuple[int, str]], dict[str, list[str]]]:
    """词 -> (最高分, 该词最高分所在的读音)，以及 读音 -> 词（按分数降序）。"""
    best: dict[str, tuple[int, str]] = {}
    by_reading: dict[str, list[tuple[int, str]]] = collections.defaultdict(list)
    with path.open(encoding="utf-8") as handle:
        for line in handle:
            reading, word, score = line.rstrip("\n").split("\t")
            value = int(score)
            by_reading[reading].append((value, word))
            if word not in best or value > best[word][0]:
                best[word] = (value, reading)
    order = {
        reading: [word for _, word in sorted(entries, key=lambda item: (-item[0], item[1]))]
        for reading, entries in by_reading.items()
    }
    return best, order


def everyday_words() -> list[str]:
    """固定在 jieba 词表上的日常词样本，这样"哪一版资产"不会改变样本本身。

    用当前资产切词会让样本随改动变化（词表一变，切出来的词就变），两次测量就没法比。jieba 的
    词表（仓库里有，MIT）与发布词表无关，拿它切 Tatoeba 得到的样本是固定的。
    """
    if not TATOEBA.exists():
        print(f"! {TATOEBA.name} 不在本地（跑 fetch_corpora.sh），跳过日常词可打性")
        return []
    vocabulary: set[str] = set()
    with JIEBA.open(encoding="utf-8") as handle:
        for line in handle:
            word = line.split()[0] if line.split() else ""
            if 2 <= len(word) <= 4 and build_dict.HAN.match(word):
                vocabulary.add(word)
    counts = build_dict.load_tatoeba_words(vocabulary)
    return [word for word, count in counts.items() if count >= MIN_COUNT and len(word) >= 2]


def syllable_set(char_readings: dict[str, list[str]]) -> set[str]:
    syllables: set[str] = set()
    for options in char_readings.values():
        syllables.update(options)
    return syllables


def hsk_ground_truth(syllables: set[str]) -> list[tuple[str, str]]:
    """HSK 3.0 的 `àihào` -> `aihao`。用音节表最大匹配切开，切不动或音字数不吻合的丢掉。"""
    if not HSK30.exists():
        print(f"! {HSK30.name} 不在本地（跑 fetch_corpora.sh），跳过读音留出集")
        return []
    longest = max(map(len, syllables))
    rows: list[tuple[str, str]] = []
    with HSK30.open(encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            word = (row.get("Simplified") or "").strip()
            pinyin = (row.get("Pinyin") or "").strip()
            if not word or not pinyin or not build_dict.HAN.match(word):
                continue
            prepared = pinyin.lower().replace("u:", "v").replace("ü", "v")
            prepared = unicodedata.normalize("NFD", prepared)
            prepared = "".join(ch for ch in prepared if unicodedata.category(ch) != "Mn")
            prepared = re.sub(r"[0-9\s]", "", unicodedata.normalize("NFC", prepared))
            parts: list[str] = []
            index = 0
            while index < len(prepared):
                for end in range(min(len(prepared), index + longest), index, -1):
                    if prepared[index:end] in syllables:
                        parts.append(prepared[index:end])
                        index = end
                        break
                else:
                    parts = []
                    break
            if len(parts) == len(word):
                rows.append((word, "".join(parts)))
    return rows


def reading_split_eval(char_readings: dict[str, list[str]]) -> None:
    """9:1 切开 41 万条词级读音，比较笛卡尔积与自训练模型的词级读音准确率。

    这条是自训练读音的**直接证据**：第 2 项量"排位"，这一项量"定音对不对"。用的是
    `build_dict.TrainedReadings` 本人，所以量的就是线上那套打分。
    """
    path = build_dict.PHRASE_PINYIN_LARGE
    if not path.exists():
        print(f"! {path.name} 不在本地（跑 fetch_corpora.sh），跳过读音留出实验")
        return
    pairs: list[tuple[str, list[str]]] = []
    with path.open(encoding="utf-8") as handle:
        for line in handle:
            word, _, value = line.rstrip("\n").partition("\t")
            syllables = value.split()
            if word and build_dict.HAN.match(word) and len(syllables) == len(word):
                pairs.append((word, syllables))
    random.Random(7).shuffle(pairs)
    cut = int(len(pairs) * 0.9)
    train, test = pairs[:cut], pairs[cut:]

    model = build_dict.TrainedReadings()
    for word, syllables in train:
        for index, (char, syllable) in enumerate(zip(word, syllables)):
            if syllable not in char_readings.get(char, ()):
                continue
            model.unigram[char][syllable] = model.unigram[char].get(syllable, 0) + 1
            if index:
                key = (word[index - 1], char)
                model.previous[key][syllable] = model.previous[key].get(syllable, 0) + 1
            if index + 1 < len(word):
                key = (char, word[index + 1])
                model.following[key][syllable] = model.following[key].get(syllable, 0) + 1
    # 与 train_readings.py 同一条剪枝：只留下"改变了结论"的上下文。
    for table, char_at in ((model.previous, 1), (model.following, 0)):
        for key in list(table):
            counts = table[key]
            char = key[char_at]
            edge = model.unigram.get(char)
            if sum(counts.values()) < 3 or not edge:
                del table[key]
                continue
            best = max(counts.items(), key=lambda item: item[1])[0]
            if best == max(edge.items(), key=lambda item: item[1])[0]:
                del table[key]

    cartesian = trained = total = 0
    for word, syllables in test:
        if not all(char in char_readings for char in word):
            continue
        gold = "".join(syllables)
        total += 1
        raw = build_dict.readings_for(word, char_readings, {}, {})
        if raw and raw[0][0] == gold:
            cartesian += 1
        guess = model.decode(word, char_readings)
        if guess is not None and guess[0] == gold:
            trained += 1
    if total:
        print(f"读音留出实验（训练 {len(train)} / 测试 {total} 词，不重叠）：")
        print(f"  逐字笛卡尔积首选 {cartesian}/{total}（{cartesian / total:.1%}）")
        print(f"  自训练模型       {trained}/{total}（{trained / total:.1%}）")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--words", type=pathlib.Path, default=WORDS, help="要量的词表资产")
    parser.add_argument("--split-eval", action="store_true", help="跑一遍读音留出实验（要 41 万表在本地）")
    args = parser.parse_args()
    best, order = load_word_table(args.words)
    print(f"资产：{args.words}")
    print(f"词表：{len(best)} 个词 / {len(order)} 个读音")

    sample = everyday_words()
    if sample:
        top1 = sum(
            1
            for word in sample
            if word in best and order[best[word][1]] and order[best[word][1]][0] == word
        )
        print(f"日常词可打性：{top1}/{len(sample)} 在自己的读音下排第一（{top1 / len(sample):.1%}）")

    char_readings = build_dict.load_readings(build_dict.PYINYIN_DATA)
    rows = hsk_ground_truth(syllable_set(char_readings))
    if rows:
        correct = sum(1 for word, reading in rows if order.get(reading) and word in order[reading][:1])
        reachable = sum(1 for word, reading in rows if order.get(reading) and word in order[reading])
        print(f"读音留出集（HSK 3.0，{len(rows)} 词）：")
        print(f"  首选正确 {correct}/{len(rows)}（{correct / len(rows):.1%}）")
        print(f"  排进该读音的候选 {reachable}/{len(rows)}（{reachable / len(rows):.1%}）")

    if args.split_eval:
        reading_split_eval(char_readings)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

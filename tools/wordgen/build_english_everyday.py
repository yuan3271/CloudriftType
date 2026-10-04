#!/usr/bin/env python3
"""从 Tatoeba 的英文句子训一张「日常英文词表」。

为什么需要它
------------
`EnglishWords` 现在是两张手写表（`CORE` 技术 / 键盘 / 常用词、`EVERYDAY` 手挑的日常词）＋一张
课标表（小初高到高考 3,997 词）。它们回答的是"课本收过哪些词""手挑了什么词"，都不是
"真人写句子时到底在用哪些词"。于是 `appointment`、`insurance`、`actually`、`anyway` 这类
**天天会打、但不是课本词**的词，要么不在表里，要么排在课本词后面。

这份表把顺序换成**统计**：Tatoeba 的英文句子（志愿者撰写的日常句子）分词数数，按出现次数
从多到少排。产物只含词与出现次数，**不含任何句子原文**——与中文那边的 Tatoeba 日常词层
（`build_dict.py` 的 `TATOEBA_FLOOR_*`）同一个口径、同一份许可（CC BY 2.0 FR，见 NOTICE.md）。

口径
----
- 一个"词"：以字母开头，后面是字母 / 撇号 / 连字符（`don't`、`e-mail` 算一个词）；
- 丢掉单字母与超过 [MAX_LEN] 个字母的串（补全的是一个词，不是一串字母）；
- 出现次数低于 [MIN_COUNT] 的丢掉——一次是噪声；
- **人名丢掉**：Tatoeba 的英文句子是志愿者写的，里面全是 `Tom` / `Sami` / `Layla` 这类人物
  （`tom` 出现 41 万次，其中只有 9 次是小写）。判据是"小写开头至少占 [MIN_LOWER_SHARE]%"，
  另外两类网开一面：`I'm` / `Let's` / `Where's` 这类缩写（永远大写开头，但天天要打）与
  [ALLOWED_ALWAYS_CAPITAL] 里那几个现代生活词。这一条滤掉 200 多个，几乎全是人名与地名；
- **训练 / 留出按句子 id 切 9:1**，留出的那一成只用来量指标，不参与数数（与
  `tools/tune/everyday.py --split-eval` 同一个口径）。

指标（构建时打印，也写进 NOTICE.md / PLAN.md）
-------------------------------------------
1. **句子覆盖率**：留出集里有多少个词次能在词表里被补全（改前 / 改后）；
2. **前缀命中率**：日常词里最常用的那 1,000 个（人名已滤掉），取前 4 个字母当输入，按补全的
   既有规则（按表顺序取前 48 条）扫一遍，词本身能不能进候选（改前 / 改后）。

用法：
    tools/wordgen/fetch_words.sh          # 抓原始数据（含 Tatoeba eng）
    python3 tools/wordgen/build_english_everyday.py
"""

from __future__ import annotations

import argparse
import bz2
import pathlib
import re
import sys
from collections import Counter

ROOT = pathlib.Path(__file__).resolve().parents[2]
RAW = ROOT / "tools" / "wordgen" / "raw"
SENTENCES = RAW / "tatoeba_eng_sentences.tsv.bz2"
OUT = ROOT / "app/src/main/java/com/yuan3271/cloudrift/engine/english/EnglishEverydayWords.kt"
WORDS_KT = ROOT / "app/src/main/java/com/yuan3271/cloudrift/engine/english/EnglishWords.kt"
SCHOOL_KT = ROOT / "app/src/main/java/com/yuan3271/cloudrift/engine/english/EnglishSchoolWords.kt"

MIN_COUNT = 4

MAX_LEN = 16

CANDIDATE_LIMIT = 48

SPLIT = 10

# 小写开头的占比低于这个数就当成专名（人名 / 地名）丢掉。
MIN_LOWER_SHARE = 5

# 缩写永远写在句首时也大写，可它们是天天要打的：词干在这张表里的 `X'...` 一律保留。
CONTRACTION_STEMS = {
    "i", "you", "he", "she", "it", "we", "they", "that", "there", "these", "those",
    "what", "who", "where", "how", "when", "why", "let",
    "don", "doesn", "didn", "isn", "aren", "wasn", "weren", "can", "couldn", "wouldn",
    "shouldn", "won", "hasn", "haven", "hadn", "ain", "mustn", "needn",
}

# 语料里几乎总写大写、但确实是日常要打的词（品牌 / 技术）。这是这份表里**唯一**手挑的部分，
# 就这几个，别的都靠统计。
ALLOWED_ALWAYS_CAPITAL = {"facebook", "youtube", "wifi", "instagram", "whatsapp"}

WORD = re.compile(r"[A-Za-z][A-Za-z'\-]*")


def sentences() -> tuple[Counter[str], Counter[str], Counter[str]]:
    """按 9:1 切句子，返回（训练集词频，训练集里小写开头的词频，留出集词频）。"""
    if not SENTENCES.exists():
        sys.exit(f"! missing {SENTENCES}（跑 tools/wordgen/fetch_words.sh）")
    train: Counter[str] = Counter()
    written_lower: Counter[str] = Counter()
    held: Counter[str] = Counter()
    with bz2.open(SENTENCES, "rt", encoding="utf-8", errors="ignore") as handle:
        for line in handle:
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 3 or parts[1] != "eng":
                continue
            bucket = held if int(parts[0]) % SPLIT == 0 else train
            for token in WORD.findall(parts[2]):
                word = token.lower().strip("'-")
                if len(word) < 2 or len(word) > MAX_LEN:
                    continue
                # 句子开头的词一定是大写，所以"小写开头"只在句中才有意义——判人名够用了。
                if bucket is train and token[0].islower():
                    written_lower[word] += 1
                bucket[word] += 1
    return train, written_lower, held


def is_keepable(word: str, total: int, lower: int) -> bool:
    """是不是"日常会打的那个词"，而不是 Tatoeba 里的人名。"""
    if word in ALLOWED_ALWAYS_CAPITAL:
        return True
    if "'" in word and word.split("'")[0] in CONTRACTION_STEMS:
        return True
    return lower * 100 >= total * MIN_LOWER_SHARE


def kept(train: Counter[str], written_lower: Counter[str], top: int) -> list[str]:
    return [
        word
        for word, count in train.most_common()
        if count >= MIN_COUNT and is_keepable(word, count, written_lower[word])
    ][:top]


def read_kotlin_list(path: pathlib.Path, name: str) -> list[str]:
    """把 Kotlin 里 `val NAME: List<String> = \"\"\"...\"\"\"` 那一块读成词表。"""
    text = path.read_text(encoding="utf-8")
    match = re.search(rf"val {name}\b.*?\"\"\"(.*?)\"\"\"", text, re.S)
    if not match:
        sys.exit(f"! 在 {path.name} 里找不到 {name}")
    return [word for word in match.group(1).split() if word]


def layers() -> tuple[list[str], list[str], list[str]]:
    """三张现成的表，顺序与 `EnglishWords.ALL` 一致。"""
    return (
        read_kotlin_list(WORDS_KT, "CORE"),
        read_kotlin_list(WORDS_KT, "EVERYDAY"),
        read_kotlin_list(SCHOOL_KT, "ALL"),
    )


def combined(core: list[str], everyday: list[str], new: list[str], school: list[str]) -> list[str]:
    """改前 = CORE + EVERYDAY + 课标；改后 = 中间插进语料训出来的日常词。去重留第一次出现。"""
    return list(dict.fromkeys(core + everyday + new + school))


def coverage(words: list[str], held: Counter[str]) -> float:
    """留出集里有多少个**词次**能在词表里被补全（大小写无关）。"""
    known = set(words)
    total = sum(held.values())
    hit = sum(count for word, count in held.items() if word in known)
    return hit / total


def prefix_hits(words: list[str], samples: list[str]) -> float:
    """取前 4 个字母当输入，词本身能不能进那 48 条候选（按表顺序扫，与英文补全同一条路）。"""
    limited = [word for word in samples if len(word) >= 5][:1000]
    if not limited:
        return 0.0
    hit = 0
    for word in limited:
        prefix = word[:4]
        found = 0
        for candidate in words:
            if candidate.startswith(prefix) and candidate != prefix:
                if candidate == word:
                    hit += 1
                    break
                found += 1
                if found >= CANDIDATE_LIMIT:
                    break
    return hit / len(limited)


def write(words: list[str], out: pathlib.Path) -> None:
    body = "\n".join(
        "        " + " ".join(words[index : index + 10])
        for index in range(0, len(words), 10)
    )
    out.write_text(
        f'''// GENERATED FILE - edit tools/wordgen/build_english_everyday.py instead.
package com.yuan3271.cloudrift.engine.english

/**
 * Tatoeba 英文句子训出来的日常词，共 {len(words):,} 个，按出现次数从多到少。
 *
 * 来源：[Tatoeba](https://tatoeba.org) 英文句子导出（`eng_sentences.tsv.bz2`，CC BY 2.0 FR）。
 * 只统计每个词出现了多少次，**句子原文不入库、不随应用分发**；单字母、超过 {MAX_LEN} 个字母的串
 * 与出现次数少于 {MIN_COUNT} 次的词都不收。
 *
 * 手写表回答的是"课本收过哪些词""手挑了什么词"，这一张回答的是"真人写句子时到底在用哪些词"
 * ——`appointment`、`insurance`、`actually`、`anyway` 这类天天会打、又不是课本词的就是它补的。
 * 放在 [EnglishWords.EVERYDAY] 之后、课标表之前：日常词先补，课本词垫底。
 *
 * 重新生成：`tools/wordgen/fetch_words.sh` 之后跑
 * `python3 tools/wordgen/build_english_everyday.py`。
 */
internal object EnglishEverydayWords {{

    val ALL: List<String> = """
{body}
    """.trimIndent()
        .split(Regex("\\\\s+"))
        .filter {{ it.isNotBlank() }}
}}
''',
        encoding="utf-8",
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--top", type=int, default=4000, help="最多收多少个词（默认 4000）")
    parser.add_argument("--out", type=pathlib.Path, default=OUT)
    parser.add_argument("--no-write", action="store_true", help="只打印指标，不写文件")
    parser.add_argument("--sweep", action="store_true", help="扫一遍 top 取值下的指标")
    args = parser.parse_args()

    train, written_lower, held = sentences()
    core, everyday, school = layers()
    before = combined(core, everyday, [], school)
    # 指标 2 的样本固定成"最常见的一千个日常词"（人名已滤掉），这样改前 / 改后、不同 --top
    # 之间比的是同一批词。
    popular = kept(train, written_lower, 1000)
    print(f"Tatoeba eng：训练 {sum(train.values()):,} 词次 / 留出 {sum(held.values()):,} 词次")
    print(f"改前词表 {len(before):,} 词：留出集覆盖率 {coverage(before, held):.4f}，"
          f"前缀命中率 {prefix_hits(before, popular):.4f}")

    if args.sweep:
        for top in (2000, 3000, 4000, 6000, 8000):
            words = combined(core, everyday, kept(train, written_lower, top), school)
            print(f"  top={top:<5} 合计 {len(words):,} 词：覆盖率 {coverage(words, held):.4f}，"
                  f"前缀命中率 {prefix_hits(words, popular):.4f}")
        return 0

    fresh = kept(train, written_lower, args.top)
    print(f"新训练出的日常词：{len(fresh):,} 个（出现 >= {MIN_COUNT} 次）")
    merged = combined(core, everyday, fresh, school)
    print(f"合计 {len(merged):,} 词：留出集覆盖率 {coverage(merged, held):.4f}，"
          f"前缀命中率 {prefix_hits(merged, popular):.4f}")
    if not args.no_write:
        write(fresh, args.out)
        print(f"写好了 {args.out.relative_to(ROOT)}：{len(fresh):,} 个词")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""自训练「拼音对应」：从词→拼音的对齐数据里学出一个字在上下文里的读音。

为什么需要它
------------
`build_dict.py` 给一个词定音只有两条路：词级读音表里有就照抄，没有就把每个字的读音做**笛卡尔
积**。后者是猜，而且猜得很粗——`一幢` 会拼成 `yichuang`（幢 的首读音是 chuáng，可它更常读
zhuàng），`万佛峡` 会拼成 `wanfuxia`（佛 的首读音是 fú，可 `fó` 才是常用音），`一日看尽长安花`
的 `长安` 会拼成 `zhangan`。词级读音表覆盖不到的词，当前 188,536 个里有 100,484 个。

本脚本把「拼音对应」做成**训练**出来的东西：拿仓库里那些 MIT 许可的「词 → 拼音」对齐数据当
训练集（pypinyin 47,102 条 ＋ phrase-pinyin-data 411,569 条 ＋ HSK 2.0 的 numeric 拼音），统计
两件事：

  1. 字 → 读音的边缘分布 ``P(读音 | 字)``；
  2. 上下文分布 ``P(读音 | 字, 前字)`` 与 ``P(读音 | 字, 后字)``——**只在它真的改变结论时
     保留**，否则表会大得没有意义（40 万个上下文里绝大多数跟边缘分布一致）。

  `地产` 靠后字（`地`+`产`）才对，`银行` 靠前字（`银`+`行`）才对，只有并上两个方向，
  多音字才既认名字也认词组。

`build_dict.py` 用这份模型给"没有词级读音"的词逐字解码（首选读音有先验、上下文强证据可以推翻
它），产出候选读音与"用了几次非首选读音"的计数——异读惩罚与丢弃规则照旧生效，改变的只是
**往哪边猜**。

产物 `clean/trained_readings.txt` 入库（几十 KB），离线可复现。训练集里那张 41 万的大表按约定
不入库（见 `fetch_corpora.sh`）；缺它时只用 pypinyin 的 47k 训练，代码路径完全一样，只是覆盖
率低一些。

许可：训练数据全部是 MIT（见 `NOTICE.md`），产物只含统计量，不含任何上游原文。

Usage:
    python3 tools/dictgen/train_readings.py
"""

from __future__ import annotations

import argparse
import json
import pathlib
import re
import sys
import unicodedata
from collections import Counter, defaultdict

ROOT = pathlib.Path(__file__).resolve().parents[2]
TOOLS = ROOT / "tools/dictgen"
CLEAN = TOOLS / "clean"

# 训练集（全部 MIT，见 NOTICE.md）。两张词级读音表同形：`词<TAB>音节 音节`。
PYPINYIN_PHRASES = CLEAN / "pypinyin_phrases.txt"
PHRASE_PINYIN_LARGE = CLEAN / "phrase_pinyin_large.txt"
# HSK 2.0 的词条带 `cheng2 li4` 形式的数字标调拼音，是第三份对齐数据（可选）。
HSK_COMPLETE = TOOLS / "raw/hsk_complete.json"
# 逐字读音表：训练出来的读音必须是这个字**确实列出来过**的，否则模型会自己发明音。
PINYIN_DATA = CLEAN / "pinyin_data.txt"

OUTPUT = CLEAN / "trained_readings.txt"

# 一个上下文（前字，字）要改变结论、进表，至少要见过这么多次。低于它的上下文大概率是噪声
# （`的` 什么都跟），保留下来只会把边缘分布掀翻。
MIN_CONTEXT = 3
# 边缘分布至少要见这么多次才认为这个字"有读数"，否则宁可退回笛卡尔积。
MIN_CHAR = 2
# 上下文分布里首选读音至少要占这么大比例，才允许它**推翻**边缘分布的首选。
CONTEXT_CERTAIN = 0.6

HAN = re.compile(r"^[\u3400-\u4DBF\u4E00-\u9FFF\uF900-\uFAFF]+$")
SYLLABLE = re.compile(r"^[a-z]+$")


def normalize_syllable(value: str) -> str | None:
    """去掉声调、统一 ü，得到与词表一致的音节写法。"""
    if not value:
        return None
    prepared = value.lower().replace("u:", "v")
    for source in "üǖǘǚǜ":
        prepared = prepared.replace(source, "v")
    decomposed = unicodedata.normalize("NFD", prepared)
    ascii_only = "".join(ch for ch in decomposed if unicodedata.category(ch) != "Mn")
    stripped = re.sub(r"[0-9\s]", "", unicodedata.normalize("NFC", ascii_only))
    return stripped if stripped and SYLLABLE.match(stripped) else None


def load_char_readings() -> dict[str, list[str]]:
    """pinyin-data 的逐字读音（首读音在前），训练出的读音只能从这里面挑。"""
    readings: dict[str, list[str]] = {}
    with PINYIN_DATA.open(encoding="utf-8") as handle:
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
            ordered: list[str] = []
            for value in rest.split("#")[0].strip().split(","):
                syllable = normalize_syllable(value.strip())
                if syllable and syllable not in ordered:
                    ordered.append(syllable)
            if ordered:
                readings[char] = ordered
    return readings


def load_alignment(path: pathlib.Path) -> list[tuple[str, list[str]]]:
    """读 ``词<TAB>音节 音节`` 形状的对齐表，只保留"字数 == 音节数"的词。"""
    pairs: list[tuple[str, list[str]]] = []
    with path.open(encoding="utf-8") as handle:
        for line in handle:
            word, _, value = line.rstrip("\n").partition("\t")
            syllables = value.split()
            if not word or not HAN.match(word) or len(syllables) != len(word):
                continue
            pairs.append((word, syllables))
    return pairs


def load_hsk_alignment() -> list[tuple[str, list[str]]]:
    """HSK 2.0 的 ``cheng2 li4`` -> 逐字对齐（`numeric` 用空格分隔，正好一个字一个音）。"""
    if not HSK_COMPLETE.exists():
        print(f"! missing {HSK_COMPLETE.name}（可选，跳过）", file=sys.stderr)
        return []
    entries = json.loads(HSK_COMPLETE.read_text(encoding="utf-8"))
    pairs: list[tuple[str, list[str]]] = []
    for entry in entries:
        word = entry.get("simplified") or ""
        forms = entry.get("forms") or []
        if not word or not forms:
            continue
        numeric = (forms[0].get("transcriptions") or {}).get("numeric") or ""
        syllables = [syllable for part in numeric.split() if (syllable := normalize_syllable(part))]
        if len(syllables) == len(word) and HAN.match(word):
            pairs.append((word, syllables))
    return pairs


def train(
    alignments: list[tuple[str, list[str]]],
    char_readings: dict[str, list[str]],
) -> tuple[dict[str, Counter[str]], dict[tuple[str, str], Counter[str]], dict[tuple[str, str], Counter[str]]]:
    """统计边缘分布与上下文分布，读音限定在该字确实列出来过的音节里。"""
    unigram: dict[str, Counter[str]] = defaultdict(Counter)
    previous: dict[tuple[str, str], Counter[str]] = defaultdict(Counter)
    following: dict[tuple[str, str], Counter[str]] = defaultdict(Counter)
    for word, syllables in alignments:
        for index, (char, syllable) in enumerate(zip(word, syllables)):
            if syllable not in char_readings.get(char, ()):
                continue
            unigram[char][syllable] += 1
            if index:
                previous[(word[index - 1], char)][syllable] += 1
            if index + 1 < len(word):
                following[(char, word[index + 1])][syllable] += 1
    return unigram, previous, following


def prune(
    unigram: dict[str, Counter[str]],
    context: dict[tuple[str, str], Counter[str]],
    char_at: int,
) -> dict[tuple[str, str], Counter[str]]:
    """只保留"改变了结论"的上下文。

    条件分布跟边缘分布选同一个读音时，它没有信息量——留着只是把同一句话写两遍。真正有用的是
    `(银, 行) -> hang`（边缘里 `行` 更常是 xíng）、`(地, 产) -> di`（边缘里 `地` 更常是 de）
    这类。要求支持数够、且首选读音与边缘首选不同。
    """
    kept: dict[tuple[str, str], Counter[str]] = {}
    for key, counts in context.items():
        total = sum(counts.values())
        if total < MIN_CONTEXT:
            continue
        # 前字上下文的键是 (前, 字)，后字上下文的键是 (字, 后)——判定的是"这个字"。
        char = key[char_at]
        best, _ = counts.most_common(1)[0]
        edge = unigram.get(char)
        edge_best = edge.most_common(1)[0][0] if edge else None
        if best == edge_best:
            continue
        kept[key] = counts
    return kept


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=pathlib.Path, default=OUTPUT)
    args = parser.parse_args()

    char_readings = load_char_readings()
    print(f"pinyin-data: {len(char_readings)} 个字有读音")

    alignments = load_alignment(PYPINYIN_PHRASES)
    print(f"pypinyin 对齐: {len(alignments)} 词")
    if PHRASE_PINYIN_LARGE.exists():
        large = load_alignment(PHRASE_PINYIN_LARGE)
        alignments += large
        print(f"phrase-pinyin 对齐: {len(large)} 词（large 表）")
    else:
        print(f"! {PHRASE_PINYIN_LARGE.name} 不在本地（跑 fetch_corpora.sh），只用 pypinyin 训练")
    hsk = load_hsk_alignment()
    alignments += hsk
    print(f"HSK 2.0 对齐: {len(hsk)} 词")

    unigram, previous, following = train(alignments, char_readings)
    kept_previous = prune(unigram, previous, char_at=1)
    kept_following = prune(unigram, following, char_at=0)
    print(
        f"训练：{len(unigram)} 个字有边缘分布，"
        f"前字上下文保留 {len(kept_previous)} / {len(previous)}，"
        f"后字上下文保留 {len(kept_following)} / {len(following)}",
    )

    lines: list[str] = [
        "# 自训练拼音对应（见 tools/dictgen/train_readings.py）。",
        "# c<TAB>字<TAB>音节<TAB>次数           P(音节|字) 的边缘计数",
        "# b<TAB>前字<TAB>字<TAB>音节<TAB>次数     P(音节|字,前字)，只留改变结论的",
        "# n<TAB>字<TAB>后字<TAB>音节<TAB>次数     P(音节|字,后字)，只留改变结论的",
    ]
    written_chars = 0
    for char in sorted(unigram):
        total = sum(unigram[char].values())
        if total < MIN_CHAR:
            continue
        written_chars += 1
        for syllable, count in sorted(unigram[char].items(), key=lambda item: (-item[1], item[0])):
            lines.append(f"c\t{char}\t{syllable}\t{count}")
    written_contexts = 0
    for tag, kept in (("b", kept_previous), ("n", kept_following)):
        for key in sorted(kept):
            written_contexts += 1
            counts = kept[key]
            first, second = key
            for syllable, count in sorted(counts.items(), key=lambda item: (-item[1], item[0])):
                lines.append(f"{tag}\t{first}\t{second}\t{syllable}\t{count}")

    args.out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    size = args.out.stat().st_size
    print(
        f"写出 {args.out.relative_to(ROOT)}：{written_chars} 个字 / {written_contexts} 个上下文，"
        f"{size / 1024:.0f} KiB",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

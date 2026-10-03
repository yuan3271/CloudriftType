#!/usr/bin/env python3
"""把「小初高（到高考）」的英文词表生成成 Kotlin 源文件。

数据来源：KyleBing/english-vocabulary（BSD-3-Clause，见 NOTICE.md）
  - 小学：人教 PEP 三~六年级词表的 word 字段（jsonl）
  - 初中 / 高中：仓库根目录 tsv 的第一列词头

生成物 app/src/main/java/com/yuan3271/cloudrift/engine/english/EnglishSchoolWords.kt
随应用一起编译，因此这里只取词头、只留「一个词」：
  - 词组（pencil box、get up）丢掉——补全的是一个词，不是短语；
  - 只保留小写字母、连字符与撇号（o'clock、t-shirt），其余（a.m.、括号）丢弃；
  - 全部转小写：引擎按用户打出的前缀匹配，大小写由 EnglishWords.matchCase 跟打出来的走。

跑之前先执行 tools/wordgen/fetch_words.sh 把原始数据抓到 tools/wordgen/raw/。
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RAW = ROOT / "tools" / "wordgen" / "raw"
OUT = (
    ROOT
    / "app/src/main/java/com/yuan3271/cloudrift/engine/english/EnglishSchoolWords.kt"
)

PRIMARY = [
    "pep_primary3.jsonl",
    "pep_primary4.jsonl",
    "pep_primary5.jsonl",
    "pep_primary6.jsonl",
]
TABLES = ["junior.tsv", "senior.tsv"]

# 一个词：以小写字母开头，后面是字母 / 连字符 / 撇号。
WORD_RE = re.compile(r"^[a-z][a-z'\-]*$")


def load_words() -> tuple[list[str], dict[str, int]]:
    if not RAW.is_dir():
        sys.exit(f"缺少原始数据目录 {RAW}，先跑 tools/wordgen/fetch_words.sh")

    words: set[str] = set()
    counts: dict[str, int] = {}

    def add(raw: str, source: str) -> None:
        word = raw.strip().lower()
        if not WORD_RE.match(word):
            return
        counts[source] = counts.get(source, 0) + 1
        words.add(word)

    for name in PRIMARY:
        path = RAW / name
        if not path.exists():
            sys.exit(f"缺少 {path}，先跑 tools/wordgen/fetch_words.sh")
        for line in path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line:
                continue
            try:
                entry = json.loads(line)
            except json.JSONDecodeError:
                continue
            add(str(entry.get("word", "")), name)

    for name in TABLES:
        path = RAW / name
        if not path.exists():
            sys.exit(f"缺少 {path}，先跑 tools/wordgen/fetch_words.sh")
        for line in path.read_text(encoding="utf-8").splitlines():
            if not line.strip():
                continue
            add(line.split("\t", 1)[0], name)

    return sorted(words), counts


def wrap(words: list[str], indent: str = "        ", width: int = 96) -> str:
    lines: list[str] = []
    current = indent
    for word in words:
        piece = word if current.strip() == "" else " " + word
        if len(current) + len(piece) > width and current.strip():
            lines.append(current)
            current = indent + word
        else:
            current += piece
    if current.strip():
        lines.append(current)
    return "\n".join(lines)


def render(words: list[str], counts: dict[str, int]) -> str:
    breakdown = "\n".join(
        f" *   - {name}：{counts[name]} 词" for name in PRIMARY + TABLES
    )
    return f"""// GENERATED FILE - edit tools/wordgen/build_english_words.py instead.
package com.yuan3271.cloudrift.engine.english

/**
 * 小初高（一直覆盖到高考）的课程标准词汇，共 {len(words)} 个词。
 *
 * 来源：KyleBing/english-vocabulary（BSD-3-Clause，见 NOTICE.md）：
 *   - 小学：人教 PEP 三~六年级
 *   - 初中 / 高中：中考 / 高考词汇表
 *
 * 只收「一个词」：词组、带括号或句点的条目都丢掉（补全的是一个词，不是短语）。
 * 全部小写；用户打出什么大小写由 [EnglishWords.matchCase] 跟着还原。各来源词数：
{breakdown}
 *
 * 重新生成：
 *   tools/wordgen/fetch_words.sh
 *   python3 tools/wordgen/build_english_words.py
 */
internal object EnglishSchoolWords {{

    val ALL: List<String> = \"\"\"
{wrap(words)}
    \"\"\".trimIndent()
        .split(Regex(\"\\\\s+\"))
        .filter {{ it.isNotBlank() }}
}}
"""


def main() -> int:
    words, counts = load_words()
    if len(words) < 3000:
        sys.exit(f"只解析出 {len(words)} 个词，数据可能不完整，拒绝生成")
    OUT.write_text(render(words, counts), encoding="utf-8")
    print(f"写入 {OUT.relative_to(ROOT)}：{len(words)} 词")
    for name, count in counts.items():
        print(f"  {name}: {count}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Builds the bundled Chinese dictionary from MIT licensed sources only.

| Source | Licence | What it contributes |
| --- | --- | --- |
| jieba `dict.txt` | MIT | broad word list with corpus frequency |
| THUOCL (THUNLP) | MIT | domain vocabulary: IT, 医学, 法律, 饮食 ... |
| ivankra/hsk30 | MIT | 日常词汇表：HSK 3.0 的 1.1 万条，按等级分档 |
| drkameleon/complete-hsk-vocabulary | MIT | 口语词频（HSK 2.0 语料频次） |
| mozillazg/phrase-pinyin-data | MIT | 41 万条**词级读音**（可选，见 --phrase-readings） |
| pinyin-data (mozillazg) | MIT | per character readings, ordered by commonness |
| pypinyin (mozillazg) | MIT | per *word* readings, which is the one thing per-character data cannot say |
| Unihan (Unicode) | Unicode License v3 | how often each *reading* of a character is used (`kHanyuPinlu`, `kMandarin`) |
| hugg95/university-data | MIT | 全国普通高等学校名单（院校名） |
| mumuy/data_location | MIT | 省 / 市 / 区县名（GB/T 2260 行政区划） |

成语表（chinese-xinhua 的 idiom.json、THUOCL 的成语表、5 万成语表）**不进词表**：一是用户
要的是"日常词汇表"，二是实测确实是负收益（见 CHENGYU 一节的历史记录）。词表要的是"人们天天
打的词"，成语是另一件事。

The last table is what `tools/dictgen/fetch_corpora.sh` + `prepare_pypinyin.py` produce in
`clean/pypinyin_phrases.txt`. Without it a word's reading is the cartesian product of its
characters, so 银行 is found as yínháng only because the ranking happens to prefer háng there;
for 重庆/长处/音乐 the uncommon reading *is* the right one and the product costs the word the
alternate-reading penalty.

Everything here is deliberately MIT so the app can stay MIT. 雾凇拼音 was evaluated and
rejected: it is GPL-3.0-only and some of its upstream corpora are CC BY-SA or unlicensed.

Output:
    app/src/main/assets/pinyin_words.txt  reading<TAB>word<TAB>score
    app/src/main/assets/pinyin_chars.txt  syllable<TAB>chars<TAB>syllableWeight

Usage: python3 tools/dictgen/build_dict.py
"""

from __future__ import annotations

import argparse
import csv

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
# Word -> reading, from pypinyin (MIT). Optional at build time: without it every word falls back
# to the per-character product exactly as before.
PYPINYIN_PHRASES = TOOLS / "clean/pypinyin_phrases.txt"
# 第二张词级读音表：phrase-pinyin-data 的 large 表（MIT，41 万条），由 prepare_phrase_pinyin.py
# 生成。默认**不加载**，`--phrase-readings large` 打开，基准测试决定它值不值这份体积。
PHRASE_PINYIN_LARGE = TOOLS / "clean/phrase_pinyin_large.txt"
THUOCL_DIR = TOOLS / "clean"
# Optional: HSK vocabulary with a corpus rank per word (MIT, drkameleon/complete-hsk-vocabulary).
# It is the only conversational-frequency source in the build, which is what keeps 怎么样 above
# 简体字 and 今天 above 几天 in the decoder.
HSK = TOOLS / "raw/hsk_complete.json"
# The self-authored modern colloquial corpus, also the training text of the association model. It
# is the only source in the build that reflects how people *talk* rather than how a news corpus
# writes, so it also nudges word frequencies: 没事 is said far more often than 美食, but jieba (a
# news corpus) has them the other way round, which is exactly what "meishi" used to show.
COLLOQUIAL_FILES = sorted((TOOLS / "raw").glob("corpus_*.txt"))
COLLOQUIAL_WEIGHT = 0.5
MAX_COLLOQUIAL_WORD = 4
# AISHELL-1's transcripts (Apache-2.0) as an optional *word frequency* source: 141k modern
# sentences, read aloud rather than typed, which makes it a much larger - and much less
# conversational - sample of the same thing the colloquial corpus measures. Off by default
# (weight 0); `--aishell-words` turns it on and the benchmark decides whether it earns a place.
AISHELL_TEXT = TOOLS / "raw/aishell_ner_transcript.txt"
# The corpus only re-ranks words that are already common; it must not *discover* words. It is a few
# thousand characters, far too little to judge a rare word, and letting it promote one pushes a
# low-scoring entry into the shipped table where it can change a whole sentence decode (真不错
# entered "zhenbucuo" that way and made 这个手机真不错 score worse than 这个首急诊不错).
COLLOQUIAL_MIN_SCORE = 400

THUOCL_FILES = [
    ("THUOCL_IT.txt", 0.95),
    ("THUOCL_medical.txt", 0.9),
    ("THUOCL_law.txt", 0.9),
    ("THUOCL_caijing.txt", 0.9),
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
# A reading belongs to the word that spells it with primary readings. pinyin-data carries rare and
# archaic readings too (半 is listed bàn,pàn), so the cartesian product above invents readings a
# word does not have: 半点 reached "pandian" through 半=pàn and then outranked 盘点, which is the
# wrong answer in front of the right one for anyone who simply types the word.
#
# A charge for the guess is not enough to stop that. The alternate reading is the *rarer* one, but
# the word carrying it can still be the more common word, and then it wins on frequency alone
# (半点 is more common than 盘点). So when some word already spells these exact syllables with
# primary readings, an entry that needed a guess is dropped: that reading is taken, and the guess
# is not how anyone reaches the word. A reading no primary-reading word spells - 进行's "jinhang",
# 银行's "yinhang" - has nothing to compete with and keeps its entry, so an accent that cannot tell
# xing from hang still reaches the word.
# Multiplied into the score of a word that only a THUOCL domain list has, so a term that is
# frequent inside its own tiny corpus does not outrank everyday words in sentence decoding.
THUOCL_ONLY_DISCOUNT = 0.75
# Unihan (Unicode License v3), prepared by prepare_unihan.py: how often each *reading* of a
# character is used in 《现代汉语频率词典》. pinyin-data lists a character's readings in order of
# commonness but carries no counts, and ordering alone is not enough to see that 乐 is yuè in
# 音乐, that 谁 is shéi in speech, or that 得 is děi in 得走了 - so those characters were simply
# absent from the syllable the typist presses.
UNIHAN_READINGS = TOOLS / "clean/unihan_readings.txt"
# 专名（院校 / 行政区划）：名字该被认识，但不该靠语料频率排序——`岳阳楼区`、`清华大学` 这类词
# 在新闻语料里的出现次数跟"有没有人打它"没关系。所以单列成固定权重的来源：
#   (文件名, JSON 里的键（None = 值是 {代码: 名字} 的字典）, 权重, 名义频次)
EXTRA_NAMED_LISTS = [
    ("raw/university_data.json", "university", 0.9, 3000),
    ("raw/area_list.json", None, 1.0, 5000),
]
# 词条语料（不是专名，也不是频率表）：新华字典的词条/成语（**带拼音**，等于多一份词级读音），
# 以及一份 5 万条的成语表。三份都来自 MIT / Apache-2.0 的数据集，见 NOTICE.md。
EXTRA_WORD_LISTS = [
    # 网络常用词（本项目自撰，MIT）：哔哩哔哩、微信、淘宝这类词进不了新闻语料，但天天有人打。
    ("clean/net_words.txt", None, 1.0, 2000),
]

# 新华字典的词语表（`raw/ci.json`，pwxcoo/chinese-xinhua，MIT）**不进词表**。
#
# 它有 26 万条词条，但键是 `ci` 而不是 `word`——写成 `word` 的那一版每一条都读出空串，于是它
# 从来没有真正进过词表（唯一线索是构建日志里"词条语料: 92 个"）。把键修正之后实测是明确的
# 负收益：广谱回归台全拼 453 → 421、首字母 82 → 70，并撞坏两条既有测试。原因和成语表一样——
# 词典词条按"词典收录"排序，不代表有人打；它们把字符权重和候选位次一起搅乱了。词表要的是
# 日常词汇，不是词典收词。

# 日常词汇表：HSK 3.0（ivankra/hsk30，MIT，11,092 条）。
#
# 等级就是"多日常"的现成刻度：一级是"每天都会说"，七级往上偏阅读。它做两件事——补**覆盖**
# （jieba 里没有的日常词）与补**排序**（新闻语料里 阿姨/方便面 这种词并不显眼）。
# 等级 -> **保底分**（不是加分）。加分会让"日常词"整体上浮，把语料已经排好的次序掀掉：美食 是
# 三级，加 270 分直接从 603 冲到 873，压过了同一读音下更口语的 没事（648），`meishi` 的首选
# 当场退回新闻语料的偏好。保底只做一件事——保证这个词**在词表里、排得进候选**，不去动已经比
# 它高的词。
HSK30 = TOOLS / "raw/hsk30.csv"
HSK30_FLOOR = {1: 480, 2: 400, 3: 330, 4: 270, 5: 220, 6: 180, 7: 140}
# A second reading earns its own entry in the character table when the frequency dictionary shows
# it carrying at least this share of the character's occurrences (血 is xiě in 14% of them, 觉 is
# jiào in 15%). Below that the reading exists but is rare enough that the primary placement is
# what a typist wants - which is exactly why pinyin-data lists them in an order at all.
SECOND_READING_SHARE = 0.05
# And it sits this much lower than the same character's primary placement, so the syllable's own
# characters keep the front of the list (乐 must not push 月 aside under "yue").
SECOND_READING_DIVISOR = 4
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


def load_hsk30() -> dict[str, int]:
    """日常词汇表（HSK 3.0，ivankra/hsk30，MIT）-> 词 : 等级保底分。

    表里带的拼音**不用**：`àihào` 这种写法没有音节分隔，切错一个字就会给整张表定错音；读音交给
    pypinyin / phrase-pinyin-data 那两张词级读音表。这里只要词形与等级。
    """
    if not HSK30.exists():
        print(f"! missing {HSK30.name}（跑 tools/dictgen/fetch_corpora.sh 获取）", file=sys.stderr)
        return {}
    bonus: dict[str, int] = {}
    with HSK30.open(encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            word = (row.get("Simplified") or "").strip()
            try:
                level = int((row.get("Level") or "").strip())
            except ValueError:
                continue
            if not (2 <= len(word) <= MAX_WORD_LENGTH) or not HAN.match(word):
                continue
            value = HSK30_FLOOR.get(level, 0)
            if value:
                bonus[word] = max(bonus.get(word, 0), value)
    return bonus


def load_colloquial(vocabulary: set[str]) -> dict[str, int]:
    """How often each known word is actually said in the self-authored colloquial corpus.

    Segmenting by longest match against the words the build already knows is enough here: the
    corpus is a few thousand characters of everyday speech, and the only thing wanted from it is
    "does this word occur, and does it occur more than once".
    """
    counts: dict[str, int] = defaultdict(int)
    for path in COLLOQUIAL_FILES:
        if not path.exists():
            print(f"! missing {path.name}", file=sys.stderr)
            continue
        text = "".join(
            line for line in path.read_text(encoding="utf-8").splitlines()
            if not line.startswith("#")
        )
        index = 0
        while index < len(text):
            for length in range(MAX_COLLOQUIAL_WORD, 0, -1):
                token = text[index:index + length]
                if token in vocabulary:
                    counts[token] += 1
                    index += length
                    break
            else:
                index += 1
    return counts


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


def load_phrase_readings(paths: list[pathlib.Path]) -> dict[str, list[str]]:
    """word -> 词级读音（「空格分开的音节」），一个字可以有多条：朝阳 zhāo yáng 与 cháo yáng
    都对，合并时不该丢掉任何一条。

    读的是 ``<word>\\t<syllable> <syllable>`` 形状的表，两张表同形：pypinyin（47k，基准一直
    跑的就是它）与 phrase-pinyin-data 的 large 表（41 万条，[PHRASE_PINYIN_LARGE]）。
    """
    readings: dict[str, list[str]] = {}
    for path in paths:
        if not path.exists():
            print(
                f"! missing {path.name}（跑 tools/dictgen/fetch_corpora.sh 生成）",
                file=sys.stderr,
            )
            continue
        with path.open(encoding="utf-8") as handle:
            for line in handle:
                word, _, value = line.rstrip("\n").partition("\t")
                syllables = value.split()
                if not word or len(syllables) != len(word):
                    continue
                joined = " ".join(syllables)
                bucket = readings.setdefault(word, [])
                if joined not in bucket:
                    bucket.append(joined)
    return readings


def load_coverage_words() -> set[str]:
    """large 表里的**词形**（不要读音，读音已经在 [load_phrase_readings] 里了）。

    `--word-coverage` 用它给词表补覆盖：一张 41 万条的词表里，jieba 与新华字典都没有的词就是
    "以前根本打不出来"的那一批。
    """
    if not PHRASE_PINYIN_LARGE.exists():
        print(f"! missing {PHRASE_PINYIN_LARGE.name}（跑 fetch_corpora.sh 生成）", file=sys.stderr)
        return set()
    words: set[str] = set()
    with PHRASE_PINYIN_LARGE.open(encoding="utf-8") as handle:
        for line in handle:
            word = line.split("\t", 1)[0].strip()
            if 2 <= len(word) <= MAX_WORD_LENGTH and HAN.match(word):
                words.add(word)
    return words


def attested_readings(phrase_readings: dict[str, list[str]]) -> dict[str, set[str]]:
    """Which readings pypinyin ever gives one character, across the whole phrase table.

    This is the evidence a second reading needs before the cartesian product may use it. 半 is
    listed bàn,pàn, and 最 is zuì,cuō, but no word in a 47k word table reads them that way - so
    "pandian" and "cuohou" were invented for 半点 and 最后 and only made the table look fuzzy.
    重 really is chóng in 重庆 and 行 really is háng in 银行, and both show up here because
    pypinyin states them for a word; that is the difference the product needs to see.
    """
    attested: dict[str, set[str]] = defaultdict(set)
    for word, readings in phrase_readings.items():
        for reading in readings:
            syllables = reading.split(" ")
            if len(syllables) != len(word):
                continue
            for char, syllable in zip(word, syllables):
                attested[char].add(syllable)
    return attested


def readings_for(
    word: str,
    char_readings: dict[str, list[str]],
    phrase_readings: dict[str, list[str]],
    attested: dict[str, set[str]],
    allow_alternates: bool = True,
) -> list[tuple[str, int]]:
    """Cartesian product of per character readings, capped, primary first.

    Returns (reading, alternates) pairs: ``alternates`` counts how many characters used a reading
    that is not their most common one. pinyin-data lists rare readings too (支 is zhī but also qí),
    and taking those as equals used to invent readings like "qichi" for 支持, which then won
    sentence decoding over the real "zhichi". The count lets the caller charge for the guess
    instead of banning it - genuinely polyphonic words such as 音乐 need the alternate.

    A character's second reading is only offered when [attested] shows pypinyin using it for some
    word: an unattested one is a rare or archaic reading that no word is spelled with, and the
    product can only turn it into noise. 音乐 is stated by pypinyin and skips this entirely.

    A word in pypinyin's table skips the product entirely: its reading is stated, so it enters the
    lattice with the right syllables and no penalty. 医学's 乐 is yuè only because the word says
    so; guessing it costs score the word should have had.
    """
    stated = phrase_readings.get(word)
    if stated:
        return [(reading.replace(" ", ""), 0) for reading in stated]
    # 专名（院校 / 行政区划）只用本音：`中国人民大学` 的 `大` 不该被异读拼成 dàixué，
    # `内蒙古自治区` 的 `内` 更不该读成 nà。名字怎么念没有歧义，异读只会造出假读音。
    if not allow_alternates:
        primary = [char_readings.get(char, [])[:1] for char in word]
        if any(not options for options in primary):
            return []
        return [("".join(options[0] for options in primary), 0)]
    combinations: list[tuple[list[str], int]] = [([], 0)]
    for char in word:
        options = char_readings.get(char)
        if not options:
            return []
        used = attested.get(char, ())
        take = [option for index, option in enumerate(options[:2]) if index == 0 or option in used]
        if not take:
            take = options[:1]
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


def load_aishell_words(vocabulary: set[str]) -> dict[str, int]:
    """How often each known word occurs in AISHELL-1's transcripts.

    Longest match against the words the build already knows, exactly like [load_colloquial]: the
    transcripts are only evidence about words that are already candidates, never a source of new
    vocabulary. The entity brackets ("(北京)", "[舒淇]") are annotation, so they are dropped and
    what they mark is kept.
    """
    counts: dict[str, int] = defaultdict(int)
    if not AISHELL_TEXT.exists():
        print(f"! missing {AISHELL_TEXT.name}", file=sys.stderr)
        return counts
    with AISHELL_TEXT.open(encoding="utf-8", errors="ignore") as handle:
        for line in handle:
            _, _, text = line.rstrip("\n").partition(" ")
            if not text:
                continue
            text = text.translate(str.maketrans({"(": "", ")": "", "[": "", "]": "", "<": "", ">": ""}))
            index = 0
            while index < len(text):
                for length in range(MAX_WORD_LENGTH, 0, -1):
                    token = text[index:index + length]
                    if token in vocabulary:
                        counts[token] += 1
                        index += length
                        break
                else:
                    index += 1
    return counts


def load_second_readings() -> dict[str, list[str]]:
    """(kept below load_named_lists on purpose: the named lists are read first in main)"""
    return _load_second_readings()


def load_named_lists() -> dict[str, int]:
    """院校名与行政区划名 -> 分数。见 [EXTRA_NAMED_LISTS]。"""
    names: dict[str, int] = {}
    for filename, key, weight, nominal in EXTRA_NAMED_LISTS:
        path = TOOLS / filename
        if not path.exists():
            print(f"! missing {filename}（跑 tools/dictgen/fetch_corpora.sh 获取）", file=sys.stderr)
            continue
        data = json.loads(path.read_text(encoding="utf-8"))
        items = data.get(key, []) if key else [{"name": name} for name in data.values()]
        for item in items:
            word = item.get("name") if isinstance(item, dict) else item
            if not word or not (2 <= len(word) <= MAX_WORD_LENGTH) or not HAN.match(word):
                continue
            names[word] = max(names.get(word, 0), score_of(nominal, weight))
    return names


def load_word_lists() -> dict[str, int]:
    """词条语料（新华字典词条、网络常用词）-> 分数。

    与 [load_named_lists] 分开：这些是**普通词条**，只是补覆盖（新华字典的词语里有不少 jieba
    没有的），所以分数压得比日常词低，靠的是"能打出来"而不是"排得靠前"。
    """
    words: dict[str, int] = {}
    for filename, key, weight, nominal in EXTRA_WORD_LISTS:
        path = TOOLS / filename
        if not path.exists():
            print(f"! missing {filename}（跑 tools/dictgen/fetch_corpora.sh 获取）", file=sys.stderr)
            continue
        score = score_of(nominal, weight)
        if key is None:
            for line in path.read_text(encoding="utf-8", errors="ignore").splitlines():
                if line.startswith("#"):
                    continue
                word = line.strip().split("\t")[0].split(" ")[0]
                if 2 <= len(word) <= MAX_WORD_LENGTH and HAN.match(word):
                    words[word] = max(words.get(word, 0), score)
            continue
        for entry in json.loads(path.read_text(encoding="utf-8")):
            word = (entry.get(key) or "").strip()
            if 2 <= len(word) <= MAX_WORD_LENGTH and HAN.match(word):
                words[word] = max(words.get(word, 0), score)
    return words


def _load_second_readings() -> dict[str, list[str]]:
    """character -> the readings beyond its first one that deserve a candidate slot.

    Two things make a reading worth one: the frequency dictionary says people read the character
    that way at least [SECOND_READING_SHARE] of the time, or Unihan's `kMandarin` names it the
    customary reading (谁 is shéi there and shuí in pinyin-data's order, and "shei" had no
    character at all before this).
    """
    if not UNIHAN_READINGS.exists():
        print(
            f"! missing {UNIHAN_READINGS.name}（跑 tools/dictgen/fetch_corpora.sh 生成）",
            file=sys.stderr,
        )
        return {}
    counts: dict[str, dict[str, int]] = defaultdict(lambda: defaultdict(int))
    customary: dict[str, str] = {}
    with UNIHAN_READINGS.open(encoding="utf-8") as handle:
        for line in handle:
            parts = line.rstrip("\n").split("\t")
            if len(parts) != 4:
                continue
            character, syllable, count, kind = parts[0], parts[1], int(parts[2]), parts[3]
            if kind == "mandarin":
                customary[character] = syllable
            else:
                counts[character][syllable] = max(counts[character][syllable], count)
    order: dict[str, list[str]] = {}
    for character, readings in counts.items():
        total = sum(readings.values())
        if total <= 0:
            continue
        chosen = [
            syllable
            for syllable, count in sorted(readings.items(), key=lambda item: -item[1])
            if count / total >= SECOND_READING_SHARE
        ]
        marked = customary.get(character)
        if marked and marked not in chosen:
            chosen.append(marked)
        if chosen:
            order[character] = chosen
    return order


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--aishell-words",
        type=float,
        default=0.0,
        help="AISHELL-1 转写文本的词频权重（0 = 关闭；基准测试用 -tools/tune/score.sh 判定）",
    )
    parser.add_argument(
        "--phrase-readings",
        choices=("base", "large"),
        default="base",
        help="词级读音表：base = 只用 pypinyin（47k），large = 再并上 phrase-pinyin-data 的 41 万条",
    )
    parser.add_argument(
        "--word-coverage",
        type=float,
        default=0.0,
        help="把 large 表里 jieba 不认识的词形按这个名义频次补进词表（0 = 关闭）",
    )
    parser.add_argument(
        "--max-rows",
        type=int,
        default=MAX_WORD_ROWS,
        help=f"词表行数上限（默认 {MAX_WORD_ROWS}，改大=资产更大）",
    )
    args = parser.parse_args()
    for path in (JIEBA, PYINYIN_DATA):
        if not path.exists():
            print(f"! missing {path}", file=sys.stderr)
            return 1

    char_readings = load_readings(PYINYIN_DATA)
    print(f"pinyin-data: {len(char_readings)} characters")

    jieba = load_jieba()
    thuocl = load_thuocl()
    hsk = load_hsk()
    phrase_paths = [PYPINYIN_PHRASES]
    if args.phrase_readings == "large":
        phrase_paths.append(PHRASE_PINYIN_LARGE)
    phrases = load_phrase_readings(phrase_paths)
    attested = attested_readings(phrases)
    print(
        f"jieba: {len(jieba)} words, THUOCL: {len(thuocl)} words, HSK: {len(hsk)} words, "
        f"词级读音: {len(phrases)} 词 / {sum(len(v) for v in phrases.values())} 条"
        f"（{args.phrase_readings}）",
    )

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

    # 专名（院校 / 行政区划）走自己的固定档：jieba 不认识的院校名、区县名照样要能打出来。
    named = load_named_lists()
    for word, score in named.items():
        word_scores[word] = max(word_scores.get(word, 0), score)
    print(f"专名表: {len(named)} 个（院校 / 省市区县）")

    # 词条语料（新华字典词条/成语 + 5 万成语表）：只补覆盖与读音，不参与"谁更常用"的排序。
    listed = load_word_lists()
    for word, score in listed.items():
        word_scores[word] = max(word_scores.get(word, 0), score)
    print(f"词条语料: {len(listed)} 个（新华字典词条、网络常用词）")

    # 覆盖层（可选）：large 表里 jieba / 新华字典都没收的词。名义频次压得比词条语料还低——
    # 它们的作用是"能打出来"，不是"排在前面"。
    if args.word_coverage > 0:
        coverage_score = score_of(int(args.word_coverage), 0.6)
        covered = 0
        for word in load_coverage_words():
            if word in word_scores:
                continue
            word_scores[word] = coverage_score
            covered += 1
        print(f"覆盖层: 新增 {covered} 个词（名义频次 {args.word_coverage:g} -> {coverage_score} 分）")

    # Conversational frequency, on top of the corpus lists. `frequency` in that file is a corpus
    # rank (today 155, 怎么 128, 怎么样 1367, 简体字 57431), so it is applied in tiers: the words a
    # person says out loud beat the words a news corpus happens to repeat.
    for word, rank in hsk.items():
        bonus = hsk_bonus(rank)
        if bonus:
            word_scores[word] = word_scores.get(word, 0) + bonus

    # 日常词汇表（HSK 3.0）：按等级给保底分。它盖住的是"会说不一定会写"的那批日常词，同时把
    # jieba 与 THUOCL 都没有的日常词补进词表——用户要的正是这一层，而不是成语。
    daily = load_hsk30()
    for word, floor in daily.items():
        word_scores[word] = max(word_scores.get(word, 0), floor)
    print(f"日常词汇表: {len(daily)} 个词（HSK 3.0 等级保底）")

    # Spoken frequency is not news frequency. A word that turns up in the everyday corpus at all
    # gets a nudge on the same log scale, which is what keeps 没事 in front of 美食 - the news
    # corpus has them the other way round and no amount of corpus size would fix that.
    for word, count in load_colloquial(set(word_scores)).items():
        if word_scores.get(word, 0) < COLLOQUIAL_MIN_SCORE:
            continue
        word_scores[word] = word_scores[word] + score_of(count, COLLOQUIAL_WEIGHT)

    # AISHELL-1 的词频（可选）：同一件事的更大样本，但它是"读出来的书面语"，不是"打出来的话"，
    # 所以默认关闭；打开时也用同一个 COLLOQUIAL_MIN_SCORE 闸门，只重排本来就常见的词。
    if args.aishell_words > 0:
        counts = load_aishell_words(set(word_scores))
        touched = 0
        for word, count in counts.items():
            if word_scores.get(word, 0) < COLLOQUIAL_MIN_SCORE:
                continue
            bonus = score_of(count, args.aishell_words)
            if bonus:
                word_scores[word] += bonus
                touched += 1
        print(f"AISHELL 词频: {len(counts)} 个词出现，{touched} 个词加权（权重 {args.aishell_words}）")

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
        readings = readings_for(
            word, char_readings, phrases, attested, allow_alternates=word not in named,
        )
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
            by_reading[reading].append(
                (word, max(1, score - alternates * ALTERNATE_READING_PENALTY), alternates),
            )

    rows: list[tuple[str, str, int]] = []
    for reading, entries in by_reading.items():
        primary_best = max((score for _, score, alternates in entries if alternates == 0), default=0)
        if primary_best > 0:
            entries = [entry for entry in entries if entry[2] == 0]
        entries.sort(key=lambda item: (-item[1], len(item[0]), item[0]))
        for word, score, _ in entries[:MAX_WORDS_PER_READING]:
            rows.append((reading, word, score))

    # Greedy fill by score, bounded both per reading and in total. Readings that never win a
    # slot simply fall back to single character conversion, which is what a rare reading wants.
    rows.sort(key=lambda row: (-row[2], len(row[1]), row[1]))
    chosen: list[tuple[str, str, int]] = []
    per_reading: dict[str, int] = defaultdict(int)
    for row in rows:
        if len(chosen) >= args.max_rows:
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

    # 次读音：同一个字在两个音节下都是候选。Unihan 说得出"这个字真的会这么念"（乐 yuè、血 xiě、
    # 得 děi、谁 shéi），而 pinyin-data 只给首读音，字表因此只在一个音节里见过它——"yue" 里没有
    # 乐、"shei" 下一个字都没有，就是这条漏的。降权放置，首读音那一份排位不受影响。
    second_readings = load_second_readings()
    placed = 0
    for char, syllables in second_readings.items():
        if char not in derived:
            continue
        primary = char_reading_of[char]
        known = char_readings[char]
        weight = (standalone.get(char, 0) * 1000 + derived_rank.get(char, 0)) // SECOND_READING_DIVISOR
        for syllable in syllables:
            # 只放 pinyin-data 自己列出来的读音：Unihan 在这里的作用是"有多常用"，不是引入一个
            # 构建里其它环节复现不了的读音。
            if syllable == primary or syllable not in known:
                continue
            by_syllable[syllable].append((char, weight))
            placed += 1
    print(f"Unihan: {placed} 条次读音归位（{len(second_readings)} 字有读音频率）")

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

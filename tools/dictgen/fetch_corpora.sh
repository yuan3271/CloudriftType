#!/bin/sh
# Fetches the third-party corpora that tools/dictgen consumes but does not ship.
#
#   Tatoeba 中文句子 (cmn)   CC BY 2.0 FR   -> raw/tatoeba_cmn_sentences.tsv.bz2
#   pypinyin 词组拼音表       MIT            -> raw/pypinyin_phrases_dict.json
#   phrase-pinyin-data       MIT            -> raw/phrase_pinyin.txt / raw/phrase_pinyin_large.txt
#   HSK 3.0 日常词汇表        MIT            -> raw/hsk30.csv
#   iamcal/emoji-data        MIT            -> raw/emoji_data.json
#   AISHELL-1 转写文本        Apache-2.0     -> raw/aishell_ner_transcript.txt
#   Unihan 数据库             Unicode 许可    -> raw/Unihan.zip
#
# Tatoeba feeds the association model (build_bigram.py): 89k human-written everyday sentences,
# which is what the hand-written corpus in raw/corpus_*.txt cannot supply at that scale. pypinyin
# feeds build_dict.py: a word's reading as a *word*, not as the product of its characters.
# AISHELL-1's transcripts (141k sentences, read aloud) feed the same association model as a
# second pool of modern sentences; they are measured by --variant-sweep and shipped off, see
# NOTICE.md. Unihan's kHanyuPinlu/kMandarin decide which syllables a character is filed under
# (prepare_unihan.py -> clean/unihan_readings.txt).
#
# Both files stay out of the repository - only the counts and the cleaned reading table derived
# from them are shipped, with the attribution recorded in NOTICE.md. pypinyin is pinned: the
# wheel is the unit that carries the data file, and an unpinned version would silently change the
# generated assets.
#
# Usage: tools/dictgen/fetch_corpora.sh
set -e

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
RAW="$ROOT/tools/dictgen/raw"
CLEAN="$ROOT/tools/dictgen/clean"
PYPINYIN_VERSION=0.55.0
mkdir -p "$RAW" "$CLEAN"

TATOEBA="$RAW/tatoeba_cmn_sentences.tsv.bz2"
if [ ! -f "$TATOEBA" ]; then
  echo "↓ Tatoeba cmn sentences (CC BY 2.0 FR)"
  curl -sSL -o "$TATOEBA" \
    https://downloads.tatoeba.org/exports/per_language/cmn/cmn_sentences.tsv.bz2
fi

# AISHELL-1 itself is distributed with 15 GB of audio; the transcripts alone are published by
# Alibaba-NLP/AISHELL-NER under the same Apache-2.0 terms (openslr SLR33 states them). GITHUB_PROXY
# exists for networks that cannot reach raw.githubusercontent.com - point it at any mirror, e.g.
# GITHUB_PROXY=https://gh-proxy.com/.
AISHELL="$RAW/aishell_ner_transcript.txt"
if [ ! -f "$AISHELL" ]; then
  echo "↓ AISHELL-1 transcripts (Apache-2.0)"
  curl -sSL -o "$AISHELL" \
    "${GITHUB_PROXY:-}https://raw.githubusercontent.com/Alibaba-NLP/AISHELL-NER/main/data/aishell_ner_transcript.txt"
fi

UNIHAN="$RAW/Unihan.zip"
if [ ! -f "$UNIHAN" ]; then
  echo "↓ Unihan readings (Unicode License v3)"
  curl -sSL -o "$UNIHAN" https://www.unicode.org/Public/UCD/latest/ucd/Unihan.zip
fi

# 专名（院校 / 行政区划），两张 MIT 表；只有生成词表时需要，不随仓库分发。
UNIVERSITY="$RAW/university_data.json"
if [ ! -f "$UNIVERSITY" ]; then
  echo "↓ 全国普通高等学校名单 (MIT, hugg95/university-data)"
  curl -sSL -o "$UNIVERSITY" \
    "${GITHUB_PROXY:-}https://raw.githubusercontent.com/hugg95/university-data/master/data.json"
fi

AREA="$RAW/area_list.json"
if [ ! -f "$AREA" ]; then
  echo "↓ 省市区县 (MIT, mumuy/data_location)"
  curl -sSL -o "$AREA" \
    "${GITHUB_PROXY:-}https://raw.githubusercontent.com/mumuy/data_location/master/list.json"
fi

PYPINYIN_JSON="$RAW/pypinyin_phrases_dict.json"
if [ ! -f "$PYPINYIN_JSON" ]; then
  echo "↓ pypinyin $PYPINYIN_VERSION phrases (MIT)"
  TMP=$(mktemp -d)
  python3 -m pip download "pypinyin==$PYPINYIN_VERSION" --no-deps -d "$TMP" \
    -i https://pypi.tuna.tsinghua.edu.cn/simple >/dev/null
  unzip -p "$TMP"/pypinyin-*.whl pypinyin/phrases_dict.json > "$PYPINYIN_JSON"
  rm -rf "$TMP"
fi

python3 "$ROOT/tools/dictgen/prepare_pypinyin.py" "$PYPINYIN_JSON" \
  "$CLEAN/pypinyin_phrases.txt"

python3 "$ROOT/tools/dictgen/prepare_unihan.py" "$UNIHAN" \
  "$CLEAN/unihan_readings.txt"

# 日常词汇表（HSK 3.0，ivankra/hsk30，MIT）：1.1 万条按等级分档，词表里最"日常"的那一层。
HSK30="$RAW/hsk30.csv"
if [ ! -f "$HSK30" ]; then
  echo "↓ HSK 3.0 日常词汇表 (MIT, ivankra/hsk30)"
  curl -sSL -o "$HSK30" \
    "${GITHUB_PROXY:-}https://raw.githubusercontent.com/ivankra/hsk30/master/hsk30.csv"
fi

# 词级读音表（phrase-pinyin-data，MIT）。large 表**只在使用 --phrase-readings large 时需要**，
# 因此不进仓库：7 MB 换一个默认关闭的开关不划算，要用就先跑这个脚本。
for name in pinyin large_pinyin; do
  out="$RAW/phrase_$([ "$name" = large_pinyin ] && echo pinyin_large || echo pinyin).txt"
  [ -f "$out" ] || curl -sSL -o "$out" \
    "${GITHUB_PROXY:-}https://raw.githubusercontent.com/mozillazg/phrase-pinyin-data/master/$name.txt"
done
python3 "$ROOT/tools/dictgen/prepare_phrase_pinyin.py" "$RAW/phrase_pinyin_large.txt" \
  "$CLEAN/phrase_pinyin_large.txt"

# 自训练拼音对应（clean/trained_readings.txt，入库）：只用 pypinyin 的 47k 也能跑，并上这张
# 41 万的大表覆盖率更好。改了训练集或阈值就重跑这一句，产物会覆盖发布用的模型表。
python3 "$ROOT/tools/dictgen/train_readings.py"

# 表情表（iamcal/emoji-data，MIT）：Unicode 标准分组，生成 assets/emoji.txt。
EMOJI="$RAW/emoji_data.json"
if [ ! -f "$EMOJI" ]; then
  echo "↓ emoji 数据 (MIT, iamcal/emoji-data)"
  curl -sSL -o "$EMOJI" \
    "${GITHUB_PROXY:-}https://raw.githubusercontent.com/iamcal/emoji-data/master/emoji.json"
fi
python3 "$ROOT/tools/dictgen/build_emoji.py" "$EMOJI" "$ROOT/app/src/main/assets/emoji.txt"

# 古典诗词（chinese-poetry，公共领域原文）：只在 build_bigram.py --classics 时参与搭配统计。
for spec in "chinese-poetry:全唐诗/唐诗三百首.json:poetry_tang.json" "chinese-poetry:宋词/宋词三百首.json:poetry_song.json"; do
  repo=${spec%%:*}; rest=${spec#*:}; path=${rest%%:*}; out=${rest##*:}
  [ -f "$RAW/$out" ] || curl -sSL -o "$RAW/$out" "${GITHUB_PROXY:-}https://raw.githubusercontent.com/$repo/master/$path"
done

echo "snapshot sha256:"
shasum -a 256 "$TATOEBA" "$PYPINYIN_JSON" "$AISHELL" "$UNIHAN" "$UNIVERSITY" "$AREA" \
  "$HSK30" "$EMOJI" "$RAW/phrase_pinyin_large.txt"

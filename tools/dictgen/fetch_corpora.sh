#!/bin/sh
# Fetches the third-party corpora that tools/dictgen consumes but does not ship.
#
#   Tatoeba 中文句子 (cmn)   CC BY 2.0 FR   -> raw/tatoeba_cmn_sentences.tsv.bz2
#   pypinyin 词组拼音表       MIT            -> raw/pypinyin_phrases_dict.json
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

echo "snapshot sha256:"
shasum -a 256 "$TATOEBA" "$PYPINYIN_JSON" "$AISHELL" "$UNIHAN"

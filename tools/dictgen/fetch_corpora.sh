#!/bin/sh
# Fetches the third-party corpora that tools/dictgen consumes but does not ship.
#
#   Tatoeba 中文句子 (cmn)   CC BY 2.0 FR   -> raw/tatoeba_cmn_sentences.tsv.bz2
#   pypinyin 词组拼音表       MIT            -> raw/pypinyin_phrases_dict.json
#
# Tatoeba feeds the association model (build_bigram.py): 89k human-written everyday sentences,
# which is what the hand-written corpus in raw/corpus_*.txt cannot supply at that scale. pypinyin
# feeds build_dict.py: a word's reading as a *word*, not as the product of its characters.
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

echo "snapshot sha256:"
shasum -a 256 "$TATOEBA" "$PYPINYIN_JSON"

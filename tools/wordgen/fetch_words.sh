#!/usr/bin/env bash
# 抓取英文词表原始数据到 tools/wordgen/raw/（该目录不入版本库，只用于离线生成）。
#
# 数据来源：KyleBing/english-vocabulary（BSD-3-Clause，见 NOTICE.md）
#   小学：人教 PEP 三~六年级（每册一份 jsonl，字段里的 word 就是词头）
#   初中/高中：仓库根目录的 tsv（第一列是词头，后面是释义）
#
# 用法：tools/wordgen/fetch_words.sh
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
RAW="$HERE/raw"
mkdir -p "$RAW"

REPO="KyleBing/english-vocabulary"
API="https://api.github.com/repos/$REPO/contents"
# contents 接口：请求 raw 表示直接返回文件内容，而不是 base64 包装。
ACCEPT="Accept: application/vnd.github.raw+json"

fetch() {
    local path="$1" out="$2"
    echo "  -> $out"
    curl -fsSL -H "$ACCEPT" "$API/$path" -o "$RAW/$out"
}

echo "小学（人教版 PEP 三~六年级）"
fetch "full_line_jsonl/simple/%E6%AD%A3%E5%BA%8F/%E4%BA%BA%E6%95%99%E5%B0%8F%E5%AD%A6%E4%B8%89%E5%B9%B4%E7%BA%A7.jsonl" pep_primary3.jsonl
fetch "full_line_jsonl/simple/%E6%AD%A3%E5%BA%8F/%E4%BA%BA%E6%95%99%E5%B0%8F%E5%AD%A6%E5%9B%9B%E5%B9%B4%E7%BA%A7.jsonl" pep_primary4.jsonl
fetch "full_line_jsonl/simple/%E6%AD%A3%E5%BA%8F/%E4%BA%BA%E6%95%99%E5%B0%8F%E5%AD%A6%E4%BA%94%E5%B9%B4%E7%BA%A7.jsonl" pep_primary5.jsonl
fetch "full_line_jsonl/simple/%E6%AD%A3%E5%BA%8F/%E4%BA%BA%E6%95%99%E5%B0%8F%E5%AD%A6%E5%85%AD%E5%B9%B4%E7%BA%A7.jsonl" pep_primary6.jsonl

echo "初中 / 高中"
fetch "1%20%E5%88%9D%E4%B8%AD-%E4%B9%B1%E5%BA%8F.txt" junior.tsv
fetch "2%20%E9%AB%98%E4%B8%AD-%E4%B9%B1%E5%BA%8F.txt" senior.tsv

echo "完成：$RAW"

#!/bin/bash
# search.sh — 知识库搜索辅助脚本
# 用法：
#   ./search.sh topic <keyword> [keyword2...]     — 按 frontmatter topic 字段搜索
#   ./search.sh fulltext <keyword> [keyword2...]  — 按正文全文搜索
#   ./search.sh domain <domain-value>             — 按 domain 字段过滤
#   ./search.sh stale <days>                      — 找超过 N 天未更新的页面
#   ./search.sh broken-refs                       — 快速扫描断链
#
# 所有命令需在项目根目录（wiki实践/）下执行

set -euo pipefail
WIKI_DIR="wiki"

usage() {
  echo "Usage: $0 {topic|fulltext|domain|stale|broken-refs} [args...]"
  exit 1
}

[ $# -lt 1 ] && usage

MODE="$1"
shift

case "$MODE" in
  topic)
    [ $# -lt 1 ] && echo "Error: topic requires at least one keyword" && exit 1
    for keyword in "$@"; do
      grep -rl "topic:.*$keyword" "$WIKI_DIR/" 2>/dev/null || true
      grep -rl "^  - $keyword$" "$WIKI_DIR/" 2>/dev/null || true
    done | sort -u
    ;;

  fulltext)
    [ $# -lt 1 ] && echo "Error: fulltext requires at least one keyword" && exit 1
    for keyword in "$@"; do
      grep -rl "$keyword" "$WIKI_DIR/" 2>/dev/null | while read f; do
        count=$(grep -c "$keyword" "$f" 2>/dev/null || echo 0)
        echo "$count $f"
      done
    done | sort -t' ' -k1 -rn | awk '{print $2}' | sort -u
    ;;

  domain)
    [ $# -lt 1 ] && echo "Error: domain requires a value" && exit 1
    grep -rl "^domain: $1$" "$WIKI_DIR/" 2>/dev/null || echo "(no results)"
    ;;

  stale)
    DAYS="${1:-90}"
    CUTOFF=$(date -v-${DAYS}d +%Y-%m-%d 2>/dev/null || date -d "$DAYS days ago" +%Y-%m-%d 2>/dev/null || echo "2026-01-01")
    echo "=== version older than $CUTOFF ==="
    find "$WIKI_DIR/" -name "*.md" -not -name "index.md" -not -name "log.md" | while read f; do
      version=$(grep "^version:" "$f" 2>/dev/null | head -1 | awk '{print $2}')
      [ -z "$version" ] && continue
      if [[ "$version" < "$CUTOFF" ]]; then
        id=$(grep "^id:" "$f" 2>/dev/null | head -1 | awk '{print $2}')
        echo "STALE: $f (id: $id, version: $version)"
      fi
    done
    ;;

  broken-refs)
    echo "=== [[wikilink]] broken ==="
    grep -roh '\[\[[^]]*\]\]' "$WIKI_DIR/" | sed 's/\[\[//;s/\]\]//;s/|.*//' | sort -u | while read id; do
      case "$id" in wikilink|example|placeholder) continue ;; esac
      grep -rl "^id: $id$" "$WIKI_DIR/" >/dev/null 2>&1 || echo "BROKEN: [[$id]]"
    done
    echo ""
    echo "=== frontmatter ref broken ==="
    for f in $(find "$WIKI_DIR/" -name "*.md"); do
      for field in see_also supersedes superseded_by; do
        grep "^$field:" "$f" 2>/dev/null | grep -o '\[.*\]' | tr -d '[]' | tr ',' '\n' | sed 's/^ *//' | while read target; do
          [ -z "$target" ] && continue
          grep -rl "^id: $target$" "$WIKI_DIR/" >/dev/null 2>&1 || \
            echo "BROKEN_REF: $f -> $field: '$target'"
        done
      done
    done
    ;;

  *)
    usage
    ;;
esac

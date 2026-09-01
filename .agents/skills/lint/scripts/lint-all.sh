#!/bin/bash
# lint-all.sh — Wiki 知识库完整健康检查
# 在项目根目录（wiki实践/）下执行：bash .agents/skills/lint/scripts/lint-all.sh
#
# 输出格式：每个检查项以 "=== P{级别} #{编号} {名称} ===" 开头
# Agent 解析此输出生成结构化报告

set -uo pipefail
WIKI_DIR="knowledge/wiki"
SCHEMA_DIR="knowledge/schema"
GLOSSARY="$SCHEMA_DIR/glossary.md"

# 颜色（终端显示用，不影响 Agent 解析）
RED='\033[0;31m'
YELLOW='\033[0;33m'
GREEN='\033[0;32m'
NC='\033[0m'

echo "========================================"
echo "  Wiki 健康检查 — $(date +%Y-%m-%d)"
echo "========================================"
echo ""

# ============================================================
# P0 — 必修
# ============================================================

echo "=== P0 #1 断链检查（body [[wikilink]]）==="
grep -roh '\[\[[^]]*\]\]' "$WIKI_DIR/" 2>/dev/null | \
  sed 's/\[\[//;s/\]\]//;s/|.*//' | sort -u | while read id; do
  # 跳过文档中的占位示例
  case "$id" in wikilink|example|placeholder) continue ;; esac
  [ -z "$id" ] && continue
  grep -rl "^id: $id$" "$WIKI_DIR/" >/dev/null 2>&1 || echo "  BROKEN: [[$id]]"
done
echo ""

echo "=== P0 #1b 断链检查（frontmatter see_also/supersedes）==="
for f in $(find "$WIKI_DIR/" -name "*.md"); do
  for field in see_also supersedes superseded_by; do
    grep "^$field:" "$f" 2>/dev/null | grep -o '\[.*\]' | tr -d '[]' | tr ',' '\n' | sed 's/^ *//' | while read target; do
      [ -z "$target" ] && continue
      grep -rl "^id: $target$" "$WIKI_DIR/" >/dev/null 2>&1 || \
        echo "  BROKEN_REF: $f -> $field: '$target'"
    done
  done
done
echo ""

echo "=== P0 #2 supersedes 对称性 ==="
for f in $(find "$WIKI_DIR/" -name "*.md"); do
  grep "^supersedes:" "$f" 2>/dev/null | grep -o '\[.*\]' | tr -d '[]' | tr ',' '\n' | sed 's/^ *//' | while read target; do
    [ -z "$target" ] && continue
    target_file=$(grep -rl "^id: $target$" "$WIKI_DIR/" 2>/dev/null | head -1)
    [ -z "$target_file" ] && continue
    source_id=$(grep "^id:" "$f" | head -1 | awk '{print $2}')
    grep "superseded_by:" "$target_file" 2>/dev/null | grep -q "$source_id" || \
      echo "  ASYMMETRIC: $f (id:$source_id) supersedes '$target', but $target_file missing superseded_by"
  done
  grep "^superseded_by:" "$f" 2>/dev/null | grep -o '\[.*\]' | tr -d '[]' | tr ',' '\n' | sed 's/^ *//' | while read target; do
    [ -z "$target" ] && continue
    target_file=$(grep -rl "^id: $target$" "$WIKI_DIR/" 2>/dev/null | head -1)
    [ -z "$target_file" ] && continue
    source_id=$(grep "^id:" "$f" | head -1 | awk '{print $2}')
    grep "supersedes:" "$target_file" 2>/dev/null | grep -q "$source_id" || \
      echo "  ASYMMETRIC: $f (id:$source_id) superseded_by '$target', but $target_file missing supersedes"
  done
done
echo ""

echo "=== P0 #3 conflict 超期（>14天）==="
FOURTEEN_AGO=$(date -v-14d +%Y-%m-%d 2>/dev/null || date -d "14 days ago" +%Y-%m-%d 2>/dev/null || echo "2026-01-01")
for f in $(grep -rl "^status: conflict$" "$WIKI_DIR/" 2>/dev/null); do
  version=$(grep "^version:" "$f" 2>/dev/null | head -1 | awk '{print $2}')
  [ -z "$version" ] && version="unknown"
  if [[ "$version" < "$FOURTEEN_AGO" ]] || [ "$version" = "unknown" ]; then
    id=$(grep "^id:" "$f" 2>/dev/null | head -1 | awk '{print $2}')
    echo "  OVERDUE_CONFLICT: $f (id: $id, since: $version)"
  fi
done
echo ""

echo "=== P0 #4 sources 为空 ==="
for f in $(find "$WIKI_DIR/" -name "*.md" -not -name "index.md" -not -name "log.md"); do
  # 检查是否有 sources 字段且非空
  has_sources=$(grep -c "^sources:" "$f" 2>/dev/null || echo 0)
  if [ "$has_sources" -eq 0 ]; then
    # 检查是否为 stub（status: deprecated 且有 superseded_by）
    if grep -q "^status: deprecated$" "$f" 2>/dev/null; then continue; fi
    id=$(grep "^id:" "$f" 2>/dev/null | head -1 | awk '{print $2}')
    [ -z "$id" ] && continue
    echo "  NO_SOURCES: $f (id: $id)"
  fi
done
echo ""

echo "=== P0 #5 过时数据（version > 90 天）==="
NINETY_AGO=$(date -v-90d +%Y-%m-%d 2>/dev/null || date -d "90 days ago" +%Y-%m-%d 2>/dev/null || echo "2026-02-01")
for f in $(find "$WIKI_DIR/" -name "*.md" -not -name "index.md" -not -name "log.md"); do
  # 只检查含数据的页面类型（analysis, portfolio, entity）
  type=$(grep "^type:" "$f" 2>/dev/null | head -1 | awk '{print $2}')
  case "$type" in
    analysis|portfolio|entity)
      version=$(grep "^version:" "$f" 2>/dev/null | head -1 | awk '{print $2}')
      [ -z "$version" ] && continue
      if [[ "$version" < "$NINETY_AGO" ]]; then
        id=$(grep "^id:" "$f" 2>/dev/null | head -1 | awk '{print $2}')
        echo "  STALE: $f (id: $id, version: $version, type: $type)"
      fi
      ;;
  esac
done
echo ""

# ============================================================
# P1 — 重要
# ============================================================

echo "=== P1 #6 孤岛页面 ==="
for f in $(find "$WIKI_DIR/" -name "*.md" -not -name "index.md" -not -name "log.md"); do
  id=$(grep "^id:" "$f" 2>/dev/null | head -1 | awk '{print $2}')
  [ -z "$id" ] && continue
  # overview 作为顶层入口，豁免孤岛检查
  [ "$id" = "overview" ] && continue
  # 检查是否有其他文件链入（[[id]] 或 see_also 包含）
  body_refs=$(grep -rl "\[\[$id" "$WIKI_DIR/" 2>/dev/null | grep -v "$f" | wc -l | tr -d ' ')
  see_also_refs=$(grep -rl "see_also:.*$id" "$WIKI_DIR/" 2>/dev/null | grep -v "$f" | wc -l | tr -d ' ')
  total=$((body_refs + see_also_refs))
  [ "$total" -eq 0 ] && echo "  ORPHAN: $f (id: $id)"
done
echo ""

echo "=== P1 #7 see_also 为空 ==="
for f in $(find "$WIKI_DIR/" -name "*.md" -not -name "index.md" -not -name "log.md"); do
  # 跳过 stub
  grep -q "^status: deprecated$" "$f" 2>/dev/null && continue

  see_also=$(grep "^see_also:" "$f" 2>/dev/null | head -1)
  if [ -z "$see_also" ] || echo "$see_also" | grep -q "see_also: \[\]$"; then
    id=$(grep "^id:" "$f" 2>/dev/null | head -1 | awk '{print $2}')
    [ -z "$id" ] && continue
    echo "  EMPTY_SEE_ALSO: $f (id: $id)"
  fi
done
echo ""

echo "=== P1 #8 index 未收录 ==="
for f in $(find "$WIKI_DIR/" -name "*.md" -not -name "index.md" -not -name "log.md"); do
  id=$(grep "^id:" "$f" 2>/dev/null | head -1 | awk '{print $2}')
  [ -z "$id" ] && continue
  grep -q "$id" "$WIKI_DIR/index.md" 2>/dev/null || echo "  NOT_INDEXED: $f (id: $id)"
done
echo ""

echo "=== P1 #9 summary 空泛 ==="
for f in $(find "$WIKI_DIR/" -name "*.md" -not -name "index.md" -not -name "log.md"); do
  summary=$(grep "^summary:" "$f" 2>/dev/null | head -1 | sed 's/^summary: *//')
  [ -z "$summary" ] && continue
  len=${#summary}
  if [ "$len" -le 10 ] || echo "$summary" | grep -qE '(关于|说明|介绍|描述|概述)'; then
    id=$(grep "^id:" "$f" 2>/dev/null | head -1 | awk '{print $2}')
    echo "  WEAK_SUMMARY: $f (id: $id, summary: '$summary')"
  fi
done
echo ""

echo "=== P1 #10 超长文件（>300行）==="
find "$WIKI_DIR/" -name "*.md" | while read f; do
  lines=$(wc -l < "$f" | tr -d ' ')
  [ "$lines" -gt 300 ] && echo "  LONG: $f ($lines lines)"
done
echo ""

# ============================================================
# P2 — 建议
# ============================================================

echo "=== P2 #11 术语未链接 ==="
if [ -f "$GLOSSARY" ]; then
  # 提取所有 glossary 术语 id
  TERMS=$(grep "^### " "$GLOSSARY" | sed 's/### //;s/ —.*//')
  for f in $(find "$WIKI_DIR/" -name "*.md" -not -name "index.md" -not -name "log.md"); do
    for term in $TERMS; do
      # 检查正文是否包含该术语但未链接
      # 将 kebab-case 转为可能出现的形式
      if grep -q "$term" "$f" 2>/dev/null; then
        # 已经有 [[term 形式的链接则跳过
        grep -q "\[\[$term" "$f" 2>/dev/null && continue
        id=$(grep "^id:" "$f" 2>/dev/null | head -1 | awk '{print $2}')
        echo "  UNLINKED_TERM: $f (id: $id) mentions '$term' without [[wikilink]]"
      fi
    done
  done
fi
echo ""

echo "=== P2 #12 单向 see_also ==="
for f in $(find "$WIKI_DIR/" -name "*.md"); do
  source_id=$(grep "^id:" "$f" 2>/dev/null | head -1 | awk '{print $2}')
  [ -z "$source_id" ] && continue
  grep "^see_also:" "$f" 2>/dev/null | grep -o '\[.*\]' | tr -d '[]' | tr ',' '\n' | sed 's/^ *//' | while read target; do
    [ -z "$target" ] && continue
    target_file=$(grep -rl "^id: $target$" "$WIKI_DIR/" 2>/dev/null | head -1)
    [ -z "$target_file" ] && continue
    grep "see_also:" "$target_file" 2>/dev/null | grep -q "$source_id" || \
      echo "  ONE_WAY: $f (id:$source_id) see_also '$target', but reverse missing"
  done
done
echo ""

echo "=== P2 #13 draft 超期（>14天）==="
for f in $(grep -rl "^status: draft$" "$WIKI_DIR/" 2>/dev/null); do
  version=$(grep "^version:" "$f" 2>/dev/null | head -1 | awk '{print $2}')
  [ -z "$version" ] && version="unknown"
  if [[ "$version" < "$FOURTEEN_AGO" ]] || [ "$version" = "unknown" ]; then
    id=$(grep "^id:" "$f" 2>/dev/null | head -1 | awk '{print $2}')
    echo "  STALE_DRAFT: $f (id: $id, version: $version)"
  fi
done
echo ""

echo "=== P2 #14 缺失 domain/topic ==="
for f in $(find "$WIKI_DIR/" -name "*.md" -not -name "index.md" -not -name "log.md"); do
  id=$(grep "^id:" "$f" 2>/dev/null | head -1 | awk '{print $2}')
  [ -z "$id" ] && continue
  has_domain=$(grep -c "^domain:" "$f" 2>/dev/null || echo 0)
  has_topic=$(grep -c "^topic:" "$f" 2>/dev/null || echo 0)
  issues=""
  [ "$has_domain" -eq 0 ] && issues="domain"
  [ "$has_topic" -eq 0 ] && issues="${issues:+$issues, }topic"
  [ -n "$issues" ] && echo "  MISSING_FIELDS: $f (id: $id) — missing: $issues"
done
echo ""

echo "=== P2 #15 高频断链（被引用≥3次但未创建）==="
grep -roh '\[\[[^]]*\]\]' "$WIKI_DIR/" 2>/dev/null | \
  sed 's/\[\[//;s/\]\]//;s/|.*//' | sort | uniq -c | sort -rn | while read count id; do
  [ -z "$id" ] && continue
  [ "$count" -lt 3 ] && continue
  grep -rl "^id: $id$" "$WIKI_DIR/" >/dev/null 2>&1 || \
    echo "  HIGH_FREQ_BROKEN: [[$id]] referenced $count times — consider creating page"
done
echo ""

# ============================================================
# 统计
# ============================================================

echo "========================================"
echo "  统计汇总"
echo "========================================"
echo "总页面数: $(find "$WIKI_DIR/" -name '*.md' -not -name 'index.md' -not -name 'log.md' | wc -l | tr -d ' ')"
echo "active: $(grep -rl 'status: active' "$WIKI_DIR/" 2>/dev/null | wc -l | tr -d ' ')"
echo "draft: $(grep -rl 'status: draft' "$WIKI_DIR/" 2>/dev/null | wc -l | tr -d ' ')"
echo "deprecated: $(grep -rl 'status: deprecated' "$WIKI_DIR/" 2>/dev/null | wc -l | tr -d ' ')"
echo "conflict: $(grep -rl 'status: conflict' "$WIKI_DIR/" 2>/dev/null | wc -l | tr -d ' ')"
TOTAL_LINKS=$(grep -roh '\[\[[^]]*\]\]' "$WIKI_DIR/" 2>/dev/null | wc -l | tr -d ' ')
echo "总 wikilink 数: $TOTAL_LINKS"
echo "glossary 术语数: $(grep -c '^### ' "$GLOSSARY" 2>/dev/null || echo 0)"
echo "========================================"

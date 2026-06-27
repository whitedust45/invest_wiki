#!/bin/bash
# save-raw.sh — 保存原始素材到 knowledge/raw/ 目录
# 用法：
#   ./save-raw.sh paste "<title>" < content.txt    — 从 stdin 读取粘贴文本
#   ./save-raw.sh url "<url>" "<title>"            — 生成规范文件名（不下载，需 Agent 配合抓取）
#   ./save-raw.sh check "<filename>"               — 验证文件名是否符合规范
#
# 在项目根目录（wiki实践/）下执行

set -euo pipefail
RAW_DIR="knowledge/raw"

TODAY=$(date +%Y-%m-%d)

slugify() {
  # 将标题转为 kebab-case slug（≤5 词）
  echo "$1" | tr '[:upper:]' '[:lower:]' | \
    sed 's/[^a-z0-9 -]//g' | \
    tr ' ' '-' | \
    sed 's/--*/-/g; s/^-//; s/-$//' | \
    awk -F'-' '{for(i=1;i<=NF && i<=5;i++) printf "%s%s",$i,(i<NF&&i<5?"-":""); print ""}'
}

validate_filename() {
  local name="$1"
  if echo "$name" | grep -qE '^[a-z0-9][a-z0-9-]*\.[a-z]+$'; then
    echo "VALID: $name"
    return 0
  else
    echo "INVALID: $name (must be kebab-case, lowercase, with extension)"
    return 1
  fi
}

case "${1:-}" in
  paste)
    TITLE="${2:-untitled}"
    SLUG=$(slugify "$TITLE")
    FILENAME="${TODAY}-${SLUG}.md"
    FILEPATH="${RAW_DIR}/${FILENAME}"

    mkdir -p "$RAW_DIR"
    cat > "$FILEPATH"
    echo "Saved: $FILEPATH"
    validate_filename "$FILENAME"
    ;;

  url)
    URL="${2:-}"
    TITLE="${3:-}"
    [ -z "$URL" ] && echo "Error: URL required" && exit 1

    # 从 URL 提取 domain
    DOMAIN=$(echo "$URL" | sed -E 's|https?://||;s|/.*||;s|www\.||;s|\.com$||;s|\.org$||;s|\.net$||')
    DOMAIN_SLUG=$(echo "$DOMAIN" | tr '.' '-' | tr '[:upper:]' '[:lower:]')

    if [ -n "$TITLE" ]; then
      SLUG=$(slugify "$TITLE")
    else
      SLUG="article"
    fi

    # 判断扩展名
    if echo "$URL" | grep -qE '\.(pdf|xlsx|docx|pptx)(\?|$)'; then
      EXT=$(echo "$URL" | grep -oE '\.(pdf|xlsx|docx|pptx)' | tail -1 | sed 's/\.//')
    else
      EXT="md"
    fi

    FILENAME="${TODAY}-${DOMAIN_SLUG}-${SLUG}.${EXT}"
    FILEPATH="${RAW_DIR}/${FILENAME}"

    mkdir -p "$RAW_DIR"
    echo "Target: $FILEPATH"
    echo "NOTE: 本脚本仅生成目标文件名，不执行下载。"
    echo "Agent 应使用 curl/wget 抓取内容后写入上述路径。"
    echo "若为 PDF 等二进制文件，直接下载；若为网页，提取正文后保存为 .md"
    validate_filename "$FILENAME"
    ;;

  check)
    FILENAME="${2:-}"
    [ -z "$FILENAME" ] && echo "Error: filename required" && exit 1
    validate_filename "$FILENAME"
    ;;

  *)
    echo "Usage: $0 {paste|url|check} [args...]"
    echo ""
    echo "Commands:"
    echo "  paste <title>          Save stdin to knowledge/raw/ as dated markdown"
    echo "  url <url> [title]      Generate filename for URL-sourced content"
    echo "  check <filename>       Validate filename against naming convention"
    exit 1
    ;;
esac

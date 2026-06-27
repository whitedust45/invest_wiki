---
name: ingest
description: |
  摄入新素材 — 阅读提取 + 交叉引用 + 冲突检测 + glossary 维护。
  触发词：ingest、摄入、新素材、URL、粘贴文章、研报、播客笔记、个人复盘、保存到 raw、加入知识库。
---

# /ingest — 素材摄入

## 触发

用户提供新的原始素材（文章、研报、书籍章节、播客笔记、个人复盘等）时触发。

## 模式

| 模式 | 触发条件 | 行为差异 |
|------|---------|---------|
| **标准模式**（默认） | 正常提供素材 | 完整八步流程，含讨论环节 |
| **快速模式** | 用户说"快速摄入"/"不用讨论" | 跳过步骤 2（讨论），自动完成所有写入 |
| **批量模式** | 一次提供 3+ 篇素材 | 逐篇标准/快速处理，最后合并输出一份汇总 |

## 输入方式

1. **文件路径**：用户指定 `knowledge/raw/` 下的已有文件
2. **粘贴文本**：用户直接贴入内容 → Agent 保存到 `knowledge/raw/`
3. **URL**：用户给出链接 → Agent 必须先抓取内容、再写入 `knowledge/raw/`,具体方式见下文「URL 抓取指引」

### URL 抓取指引（任何 Agent 通用）

收到 URL 后,按以下优先级选择抓取方式,目标是把可读正文落到 `knowledge/raw/<规范文件名>.md`(或保留原扩展名)：

1. **优先使用平台原生抓取工具**
   若运行环境提供 `WebFetch` / `web_fetch` / `fetch` / `mcp__*__fetch` 等工具,
   直接调用,prompt 写"提取页面正文,转为 Markdown,保留标题层级、列表、引用、代码块"。
   抓到的 Markdown 写入 `knowledge/raw/`。

2. **回退到 shell 命令**
   ```bash
   # ---- HTML 网页 ----
   curl -sSL --max-time 30 -A "Mozilla/5.0" "<URL>" -o knowledge/raw/.tmp.html
   # 优先 pandoc 转 Markdown
   pandoc -f html -t gfm --wrap=none knowledge/raw/.tmp.html -o "knowledge/raw/<生成的文件名>.md"
   # 没有 pandoc 时,退而用 readability-cli / html2text / w3m -dump 任一可用工具

   # ---- 纯文本 / Markdown / RSS 直链 ----
   curl -sSL --max-time 30 "<URL>" -o "knowledge/raw/<生成的文件名>.md"

   # ---- PDF 链接 ----
   curl -sSL --max-time 30 "<URL>" -o "knowledge/raw/<生成的文件名>.pdf"
   # 后续步骤通过 pdf skill 或 pdftotext 提取文本
   ```

3. **文件名生成**
   抓取前先调用脚本生成规范文件名（脚本会打印 `Target: knowledge/raw/...`，不下载）：
   ```bash
   bash .coco/skills/ingest/scripts/save-raw.sh url "{URL}" "{title}"
   ```
   再把抓取结果写入 `Target` 行给出的路径，确保命名与「knowledge/raw/ 文件命名规则」一致。

4. **失败处理**
   出现以下情况立即中止抓取,并提示用户改用「粘贴文本」方式:
   - HTTP 403 / 401 / 需要登录
   - 抓到的内容明显是 JS 壳子(几乎无正文)
   - `--max-time` 超时
   - 内容为付费墙片段

### knowledge/raw/ 文件命名规则

执行 `bash .coco/skills/ingest/scripts/save-raw.sh` 完成文件保存（或手动按以下规则）：

| 输入方式 | 命令 | knowledge/raw/ 文件名格式 | 示例 |
|---------|------|----------------|------|
| 文件路径 | （直接复制到 `knowledge/raw/`，保持原名） | 保持原文件名不变 | `knowledge/raw/intelligent-investor-ch20.pdf` |
| 粘贴文本 | `... save-raw.sh paste "{title}" < content.txt` | `{YYYY-MM-DD}-{short-title}.md` | `knowledge/raw/2026-05-13-margin-of-safety-notes.md` |
| URL | `... save-raw.sh url "{URL}" "{title}"`（先生成文件名再抓取写入） | `{YYYY-MM-DD}-{source-domain}-{short-title}.{ext}` | `knowledge/raw/2026-05-13-goldman-china-internet.pdf` |

**命名约束**：
- 全小写 kebab-case
- `short-title` ≤ 5 个单词，取自内容标题的关键词
- 日期取素材发布日期（未知则取摄入当天）
- 二进制文件保留原扩展名；纯文本统一用 `.md`

## 工作流程

### 1. 阅读与理解
- **前置**：若输入为 URL,先按上文「URL 抓取指引」完成抓取并落盘到 `knowledge/raw/`,再读取该文件
- 通读全文，识别核心论点、关键数据、重要概念
- 标注与已有知识的关联点
- 识别新术语（后续步骤 5 用到）

### 2. 与用户讨论（快速模式跳过）
- 列出 3-5 个关键发现
- 询问用户重点关注哪些方面
- 确认理解无误后进入写入阶段

### 3. 创建来源摘要页
- 路径：`knowledge/wiki/sources/{YYYY-MM-DD}-{short-title}.md`
- 按 `knowledge/schema/frontmatter.md` 填写完整 frontmatter
- 内容包括：摘要、关键论点、重要数据、与现有知识的关系

### 4. 更新/创建实体页和概念页

操作分三种情况，具体规范见 `reference/update-rules.md`：

| 情况 | 操作 |
|------|------|
| **新实体** | 在 `knowledge/wiki/entities/` 创建新页面 |
| **新概念** | 在 `knowledge/wiki/concepts/` 创建新页面 |
| **已有页面需更新** | 按增量更新规范修改（见 reference） |

### 5. Glossary 维护（新增步骤）

扫描素材中出现的术语，对照 `knowledge/schema/glossary.md`：

| 情况 | 操作 |
|------|------|
| 术语已在 glossary 中 | 无需操作，确认 wiki 页面 `topic` 字段引用了它 |
| 术语不在 glossary 但属于核心投资概念 | 在 glossary 对应分类下新增条目 |
| 术语过于小众或一次性 | 不入 glossary，放 `tags` 字段即可 |

**新增 glossary 条目格式**：
```markdown
### {kebab-case-id} — {中文名} / {英文名}
**同义词**：<列出所有可能的叫法>
**定义**：<一句话>
**相关页**：[[id]]（待建 / 已建）
```

### 6. 冲突检测（关键步骤）

对每条新事实，检查是否与已有 wiki 页面矛盾：

- **一致**：加强原有论述，补充新来源到 `sources` 数组
- **补充**：新角度/新数据，追加到已有页面对应章节
- **矛盾**：
  - 在两个页面都标注冲突来源
  - 矛盾严重时设 `status: conflict`
  - 在 log 中记录
  - 提醒用户做判断
  - 生成冲突对比表（参见 reference/update-rules.md）

### 7. 更新索引和日志
- 更新 `knowledge/wiki/index.md`：新增/修改条目的 id + summary
- 追加 `knowledge/wiki/log.md`：记录摄入了什么、影响了哪些页面
- 更新 index "待创建页面"表：检查新建页面是否消化了已有断链

### 8. 输出摘要

```markdown
## 摄入完成

**来源**：{标题}
**模式**：标准 / 快速 / 批量

**新建页面**：
- [[new-page-1]] — 类型 + 一句话说明
- [[new-page-2]] — ...

**更新页面**：
- [[existing-page-1]] — 补充了什么（+version 更新）
- [[existing-page-2]] — ...

**Glossary 变更**：
- 新增：{term-id}（如有）
- 无变更

**冲突发现**：
- {描述冲突内容}（如有）
- 无冲突

**待补充方向**：
- {建议后续可以补充的相关素材}
```

## 约束

- 来源摘要页的 `sources` 必须填真实来源，禁止捏造
- 数据必标时间点（"2026Q1 营收 xxx 亿"而非"营收 xxx 亿"）
- 事实与观点分离：数据用引用格式，个人判断标"观点"
- 单次摄入涉及的页面数不设上限，宁多勿漏
- 每个新建/更新的页面都必须有 ≥1 条 `[[wikilink]]` 指向相关页面

## 回退指引

如果摄入错误需要回退：

1. 删除新建的 wiki 页面文件
2. `git diff` 查看已修改的页面 → 恢复改动
3. 从 `knowledge/wiki/index.md` 删除对应条目
4. 在 `knowledge/wiki/log.md` 追加 `[日期] update | 回退：{原因}`
5. 如果更新了 glossary → 恢复

## 辅助文件

- `.agents/skills/ingest/scripts/save-raw.sh` — 保存原始素材到 knowledge/raw/ 的脚本
- `reference/update-rules.md` — 已有页面增量更新的具体规范

# /curate 治理规则细则

`SKILL.md` 中的五种模式各自的写入规范、模板与示例。任何 curate 操作落盘前都应对照本文件确认格式。

## 通用约束（所有模式）

- `version` 必须更新为操作当天日期
- 任何修改正文的操作都必须在变更点留 `[YYYY-MM-DD 类型：原因]` 标注
- 至少 1 条 `[[wikilink]]` 指向新来源、继任页或上下文
- 写完后追加 `wiki/log.md`：`## [YYYY-MM-DD] curate | {一句话摘要}`

---

## §1 修正模式（数据/事实错误，页面整体仍有效）

### 适用场景

- 某条数据错了（数字、日期、比例、引用页码等）
- 某个判断需要更正但页面框架仍然成立
- lint 报 P0 #5「过时数据 90 天未更新」时获得了新数据源

### 写入模板

```markdown
（章节标题）
- ~~2026Q1 营收 1500 亿元，同比+12%~~ — [[2026-04-report-tencent]]
- **[2026-05-30 更正]** 2026Q1 营收实际为 1480 亿元（原 1500 亿为初步数据），同比+10.4% — [[2026-05-30-tencent-final-report]]
```

### Frontmatter 变化

```yaml
version: 2026-05-30          # 必须更新
sources:                     # 在数组末尾追加新来源，旧来源保留
  - type: article
    title: 2026Q1 初步财报
    date: 2026-04-15
  - type: article             # ← 新增
    title: 2026Q1 终稿财报
    date: 2026-05-30
```

### 何时不能用修正模式

- 整页结论被推翻 → 走 §2 退役
- 没有新来源支撑 → 标 `status: conflict`，不强行修正
- 来源本身被证伪 → 删除线 + 标注，但不删除 source 条目

### 反向引用是否需要联动

修正一条事实/数据后，必须用 grep 列出所有反向引用页面，并按下表判断是否需要同步修正：

| 反向引用类型 | 是否联动 | 处理方式 |
|------|---------|---------|
| 仅 `see_also` 列表引用 | ❌ 不动 | 关系性引用，与具体数字无关 |
| 正文 wikilink 但仅描述性提及（不含数字/事实） | ❌ 不动 | 例："详见 [[X]]"、"另一种思路见 [[X]]" |
| 正文复述了被修正的具体数字/事实 | ✅ 必须同步 | 在反向引用页同样位置加 `[YYYY-MM-DD 同步修正]` 标注 |
| 反向引用页处于 `supersedes` 链上游 | ✅ 同步更新 | 链上节点都需对照检查是否仍成立 |
| 引用方是 `wiki/index.md` 的 summary | ⚠️ 视情况 | 若 summary 含具体数字则同步；仅描述则不动 |

**操作要求**：
- 联动修正必须保持术语和日期标注一致
- 联动页同样需更新 `version` 字段
- 在主操作页的 log 条目中显式列出"反向引用核查"小节，写明检查了哪些页面、是否动了


---

## §2 退役模式（整页过时或被新版替代）

### 适用场景

- 季度估值快照过期
- 公司论点根本性变化（如商业模式改变）
- 策略/组合调整后的旧版
- 同主题被新页面更全面覆盖

### 退役页面写入模板

页面顶部加 deprecation 提示块（紧跟 frontmatter 后）：

```markdown
> ⚠️ **[2026-05-30 已退役]** 本页已被 [[new-page-id]] 取代。
> 退役原因：{一句话}
> 历史内容保留供溯源，请优先查阅新版。
```

### Frontmatter 变化

```yaml
status: deprecated
version: 2026-05-30
superseded_by: [new-page-id]    # 必填（除非无继任者）
```

新版页面同步：

```yaml
status: active
supersedes: [old-page-id]       # 双向对称
```

### 索引同步

`wiki/index.md` 中：
- 旧条目加 `[deprecated → [[new-page-id]]]` 后缀
- 或迁移到「归档」区（如 index 有该结构）

### 极简 stub 选项

若旧页内容已无溯源价值（如重复页、临时页），可降级为 stub，仅保留三字段：

```yaml
---
id: old-page-id
status: deprecated
superseded_by: [new-page-id]
---

> 本页已合并到 [[new-page-id]]。
```

---

## §3 解冲突模式（status: conflict 收尾）

### 前置

- 当前页 `status: conflict` 或正文有 `## ⚠️ 观点冲突` 区块
- 用户已经做出判断（哪一方采信）

### 三种收尾方式

| 用户判断 | 操作 |
|---------|------|
| 采信 A，否定 B | A 留主线，B 内容降级到「历史观点」章节 + 加证伪标注；status 恢复 active |
| A、B 各对一半 | 新建合并版页面 → A、B 两旧页都退役指向合并版 |
| 暂时无法判断 | 保留 conflict，但在 log 中明确"等待 X 数据/X 时点验证" |

### 采信一方的写入模板

```markdown
## 历史观点冲突（已收敛）

**[2026-05-30 收敛]** 经 {新数据源 / 用户判断} 后，采信 A 方观点（见正文）。
B 方观点保留如下供参考：

### B 方原观点（已被新证据修正）
（原 B 方内容）— [[old-source-b]]

**修正依据**：[[new-source]]
```

frontmatter：
```yaml
status: active                # 从 conflict 恢复
version: 2026-05-30
```

### 拆为合并版

新建合并版页面后，A、B 两旧页：
```yaml
status: deprecated
superseded_by: [merged-page-id]
```

合并版：
```yaml
supersedes: [old-page-a, old-page-b]
```

---

## §4 删除模式（软删除为主，硬删除需授权）

### §4.1 软删除条目（默认）

适用：单条事实被证伪、单段论述无效，但页面其他部分仍有效。

```markdown
（章节标题）
- 论点一（仍有效）
- ~~论点二（已被证伪）~~ **[2026-05-30 删除：{原因}]** — [[evidence-source]]
- 论点三（仍有效）
```

frontmatter：仅更新 `version`。`sources` 中相关来源**不删除**，可在原 source 条目后加 `note: 部分内容已修正`。

### §4.2 软删除整页（不退役但隐藏）

极少使用，仅适用：
- 误生成的页面（来源捏造、AI 幻觉）
- 用户主动撤回的草稿且不想留 deprecated 标记

```yaml
status: draft
version: 2026-05-30
```

正文顶部加：
```markdown
> ⚠️ **[2026-05-30 撤回]** 本页内容已被作者撤回（原因：{}）。
> 不删除文件以保留 git 历史，但已从索引移除。
```

`wiki/index.md` 中移除该条目。

### §4.3 硬删除

**前置全部满足才允许**：
1. 用户明确说"删文件"/"硬删"/"彻底删除"/"rm 掉"
2. 通过 grep 确认该页 id 未被任何页面 `see_also` / `supersedes` / `[[wikilink]]` 引用
3. 该页未在 `schema/glossary.md` 被引用

执行前先列清单给用户：

```
即将硬删除以下文件：
- wiki/concepts/old-page.md（id: old-page）

确认无反向引用：✅
git 历史保留：✅

确认执行硬删除？（输入"确认"继续，否则取消）
```

执行后必须在 log.md 详细记录：

```markdown
## [2026-05-30] curate | 硬删除 old-page

- 删除文件：wiki/concepts/old-page.md
- 删除原因：{}
- 反向引用确认：无
- git commit hash：{若已提交}
- 已从 index.md 移除
```

---

## §5 批量同步模式（一个事实影响多页）

### 适用场景

- 公司更名 / 实体页 id 变更
- 某个核心数据被全局修正（如年报口径变化）
- glossary 术语改名

### 标准流程

#### Step 1：影响面扫描

```bash
bash .coco/skills/query/scripts/search.sh fulltext {keyword}
bash .coco/skills/query/scripts/search.sh topic {keyword}
```

汇总所有命中页面，生成清单：

```
影响范围（共 N 页）：
1. [[page-a]] — 命中位置：正文 §2、frontmatter sources
2. [[page-b]] — 命中位置：正文 §1
3. ...
```

#### Step 2：用户预览与授权

把清单完整呈现给用户，让用户：
- 勾选哪些要批量改、哪些保留原状
- 确认替换规则（例：`旧名 → 新名`，是否仅替换 wikilink 形式）

#### Step 3：分批执行

- 每页都按对应模式（修正/退役/...）单独处理
- 每页都更新 version
- log.md 中用一条汇总记录 + 子项列表：

```markdown
## [2026-05-30] curate | 批量同步：A 公司更名为 B

- 主因：A 公司于 2026-05-25 正式更名为 B
- 影响页面（共 4 页）：
  - [[a-company]] → 重命名为 [[b-company]]，原页降级为 stub
  - [[a-company-2025-analysis]] → 修正引用
  - [[overview]] → 修正引用
  - [[index.md]] → 同步条目
- 新来源：[[2026-05-25-company-rename-announcement]]
```

#### Step 4：跑 lint 验证

批量同步后强烈建议立即跑 `bash .coco/skills/lint/scripts/lint-all.sh` 验证：
- 无断链
- supersedes 双向对称
- 索引一致

---

## 模式选择决策树

```
用户/lint 触发 curate
        │
        ▼
   是否有具体目标页？
   ├─ 否 → /query 定位 → 回到本流程
   └─ 是 → 继续
        │
        ▼
   修改的是个别条目还是整页？
   ├─ 个别条目 ────┬─ 有新来源 → §1 修正
   │              ├─ 无来源但确证错 → §4.1 软删条目
   │              └─ 仅证伪未确证新值 → §1 标注但不替换值
   │
   └─ 整页 ───┬─ 有继任者 → §2 退役
              ├─ 多版本矛盾 → §3 解冲突
              ├─ 误生成/撤回 → §4.2/§4.3 删除
              └─ 涉及 ≥3 页 → §5 批量同步
```

---

## 常见反模式（不要这么做）

| 反模式 | 问题 | 正确做法 |
|--------|------|---------|
| 直接 `Edit` 把旧数据替换为新数据 | 丢失溯源、lint 看不到变更 | §1 软删除 + 标注 |
| `status: deprecated` 但不填 `superseded_by` | 后续查询无法跳转 | 必填，或显式记录"无继任者" |
| 硬删除前不查反向引用 | 制造大量断链 | 必须先 grep 全库 |
| 静默改 frontmatter 不更 version | lint 与查询时序错乱 | 任何变更都更 version |
| 解冲突时只留 A 删 B | 失去对比价值 | B 降级保留或转入「历史观点」 |
| 修正没新来源就强改 | 等于把一个错误换成另一个未证伪 | 标 `conflict`，等来源 |

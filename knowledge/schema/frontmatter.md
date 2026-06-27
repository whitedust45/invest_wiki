# Frontmatter Schema

知识库所有 `wiki/` 层文件的 frontmatter 规范。**唯一真源**。

`raw/` 层文件不受此规范约束，保留原始格式。
`index.md` 和 `log.md` 为结构性文件，不强制遵循本规范，仅需基础 frontmatter。
改名后的 stub 文件（`status: deprecated` 且仅做重定向）只需 `id` + `status` + `superseded_by` 三个字段。

## 完整字段定义

```yaml
---
id: margin-of-safety                        # 必填，全局唯一，= 文件名去掉 .md
title: 安全边际 / Margin of Safety           # 必填，可读标题
type: concept                               # 必填，受控枚举
domain: methodology                         # 必填，投资领域
topic: [value-investing, risk-management]   # 必填，至少 1 项
tags: [格雷厄姆, 1949, 经典]                # 可选，自由标签
status: active                              # 必填
version: 2026-05-13                         # 必填
effective_date: 2026-05-13                  # 分析/组合类推荐填
supersedes: []                              # 必填（可为空数组）
superseded_by: []                           # 必填（可为空数组）
see_also: [intrinsic-value, dcf-model]      # 可选
sources:                                    # 必填（至少 1 项）
  - type: book
    title: The Intelligent Investor
    author: Benjamin Graham
    chapter: "Chapter 20"
summary: ≤80字的一句话概括                    # 必填
---
```

## 字段详解

### `id` (必填)

- **类型**：`kebab-case` 字符串，全局唯一
- **构成**：**等于文件名去掉 `.md` 后缀**（详见 `schema/naming.md`）
- **用途**：作为 `[[wikilink]]` 锚点；文件改名不破坏引用
- **约束**：只含小写字母、数字、短横线；禁用下划线、驼峰、中文
- **唯一性**：不同目录下的文件也不允许 id 重复。如遇潜在冲突，用 type 前缀或 qualifier 区分
- **示例**：
  - `margin-of-safety` ✅（concepts 下的概念页）
  - `berkshire-hathaway` ✅（entities 下的实体页）
  - `2026-05-13-intelligent-investor-ch20` ✅（sources 下的摘要页）
  - `china-internet-valuation-202605` ✅（analyses 下的分析页）
  - `安全边际` ❌（中文）
  - `MarginOfSafety` ❌（驼峰）

### `title` (必填)

- **类型**：可读字符串（中文/英文/中英混合都行）
- **约束**：不含换行、不超过 80 字符
- **建议**：中英双语格式 `中文名 / English Name`
- **示例**：`安全边际 / Margin of Safety`

### `type` (必填，受控枚举)

| 值 | 含义 | 所在目录 |
|---|------|---------|
| `entity` | 实体页（公司、基金、投资人物） | `wiki/entities/` |
| `concept` | 概念/方法论页（估值模型、投资原则、心理偏误） | `wiki/concepts/` |
| `source` | 来源摘要页 | `wiki/sources/` |
| `analysis` | 分析页（行业研究、估值推演、复盘） | `wiki/analyses/` |
| `portfolio` | 组合/策略页（持仓逻辑、配置策略） | `wiki/portfolios/` |
| `overview` | 全局概览（仅 overview.md 使用） | `wiki/` |

### `domain` (必填，受控枚举)

| 值 | 含义 | 典型内容 |
|---|------|---------|
| `methodology` | 投资方法论 | 价值投资、趋势跟踪、量化 |
| `valuation` | 估值相关 | DCF、PE、PB、EV/EBITDA |
| `macro` | 宏观经济 | 利率、通胀、货币政策、周期 |
| `industry` | 行业研究 | 消费、科技、金融、医药 |
| `company` | 个股/公司研究 | 财务分析、竞争格局 |
| `psychology` | 行为金融与投资心理 | 偏误、情绪、纪律 |
| `strategy` | 投资策略与组合管理 | 配置、再平衡、仓位 |
| `risk` | 风险管理 | 回撤、对冲、分散 |
| `history` | 投资历史与案例 | 大师传记、危机复盘 |

**当一个页面跨多个 domain 时**：选最主要的那个作为 `domain`，其他用 `topic` 补充。

### `topic` (必填，至少 1 项)

- **类型**：受控词表数组，元素来自 `schema/glossary.md` 的术语 id
- **与 domain 的关系**：domain 是大分类（必须是枚举值），topic 是具体术语（来自 glossary）
- **示例**：`[value-investing, margin-of-safety]`

### `tags` (可选)

- **类型**：自由字符串数组
- **与 topic 的区别**：topic 是受控词表（术语），tags 是自由补充（人名、年份、标签）
- **示例**：`[格雷厄姆, 1949, 经典]`

### `status` (必填，受控枚举)

| 值 | 含义 | 查询行为 |
|---|------|---------|
| `active` | 当前有效 | 正常召回 |
| `deprecated` | 已过时/被取代 | 追 `superseded_by` 找新版 |
| `conflict` | 多来源矛盾、待厘清 | 并列呈现所有观点 |
| `draft` | 草稿 | 默认不召回 |
| `thesis-changed` | 论点已变化（用于实体页） | 召回时标注论点变更历史 |

### `version` (必填)

- **类型**：日期 `YYYY-MM-DD` 或语义版本号
- **含义**：内容最后实质性修改的版本标识
- **注意**：不等于文件修改时间；仅在内容有实质性变化时更新

### `effective_date` (分析/组合类推荐)

- **类型**：`YYYY-MM-DD`
- **含义**：分析成立的时间点，或组合调整的日期
- **用途**：查询时标注时效性（"此分析基于 2026-05-13 的数据"）

### `supersedes` / `superseded_by` (双向，必填)

- **类型**：`id` 数组，可为空数组 `[]`
- **语义**：A `supersedes: [B]` → A 取代 B（A 是新版）
- **约束**：双向**必须对称**（lint 强制检查）
  - A `supersedes: [B]` ↔ B `superseded_by: [A]`
- **何时使用**：
  - 定期更新的分析（如季度估值）
  - 公司论点变化时的新版实体页
  - 策略/组合调整时的新版

### `see_also` (可选，推荐)

- **类型**：`id` 数组
- **用途**：查询时沿此字段扩展相关内容（≤ 2 跳，每跳最多追 3 个最相关的）
- **推荐**：每个页面至少 1 项

### `sources` (必填，至少 1 项)

溯源命脉。回答时必须从这里取来源，**禁止捏造**。

#### `type: book`（书籍）
```yaml
- type: book
  title: The Intelligent Investor
  author: Benjamin Graham
  chapter: "Chapter 20"
  page: "302-315"              # 可选
```

#### `type: article`（文章/研报）
```yaml
- type: article
  title: 文章标题
  author: 作者
  url: https://...             # 如有
  publisher: 发布机构           # 可选（如 Goldman Sachs）
  date: 2026-05-01
```

#### `type: podcast`（播客/视频）
```yaml
- type: podcast
  title: 节目标题
  host: 主持人
  guest: 嘉宾                  # 可选
  url: https://...
  timestamp: "12:30-18:45"     # 可选
  date: 2026-04-20
```

#### `type: data`（数据来源）
```yaml
- type: data
  provider: Wind/Bloomberg/Yahoo Finance/雪球
  metric: PE-TTM               # 具体指标
  date: 2026-05-01             # 数据截止日期
```

#### `type: personal`（个人思考/复盘）
```yaml
- type: personal
  context: 2026年Q1持仓复盘     # 背景说明
  date: 2026-04-01
```

#### `type: dialogue`（对话讨论）
```yaml
- type: dialogue
  context: 讨论主题一句话描述
  date: 2026-05-14
  participants: [用户, Agent]
  trigger: "用户最初触发讨论的那句话"
  rounds: 4                      # 可选，追问轮数
  depth: refined                 # 可选：refined / raw / seed
```

### `summary` (必填)

- **类型**：≤ 80 字的一句话
- **用途**：在 index.md 的 summary 列出现，让 Agent 扫索引判断相关性
- **写作要求**：具体、有信息量、含关键数据或核心结论
- **示例**：
  - ✅ `安全边际是买入价格低于内在价值的差额，是价值投资的核心防御机制`
  - ✅ `腾讯2026Q1营收1800亿，游戏+广告双驱动，PE-TTM 18x低于5年均值`
  - ❌ `关于安全边际的说明`
  - ❌ `腾讯公司介绍`

## 最小可用 frontmatter（草稿页）

```yaml
---
id: xxx-xxx
title: ...
type: concept
domain: methodology
topic: [xxx]
status: draft
version: 2026-05-13
supersedes: []
superseded_by: []
sources:
  - type: personal
    context: 初始思考
    date: 2026-05-13
summary: ...
---
```

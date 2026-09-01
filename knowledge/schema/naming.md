# 命名规范

知识库所有 `wiki/` 层文件和 `id` 的命名规则。**唯一真源**。

`raw/` 层的旧文件名不强制迁移，保留原名即可。

## 核心规则（强制）

1. **kebab-case**：只用小写字母、数字、短横线 `-`；禁用下划线、驼峰、空格
2. **纯英文文件名**：文件名必须英文（标题可以中文，但文件名英文）
3. **语义化命名**：文件名应能让人一眼看出内容主题
4. **无扩展冗余**：不要 `-doc.md`、`-note.md` 这种冗余后缀
5. **单文件上限**：正文 ≤ 300 行；超过应拆分为多个页面

## 类型特定命名

### entity（实体页）
`wiki/entities/{entity-name}.md`

示例：
- `wiki/entities/berkshire-hathaway.md`
- `wiki/entities/warren-buffett.md`
- `wiki/entities/bridgewater-associates.md`
- `wiki/entities/tencent.md`
- `wiki/entities/sp500-index.md`

### concept（概念页）
`wiki/concepts/{concept-slug}.md`

示例：
- `wiki/concepts/margin-of-safety.md`
- `wiki/concepts/dcf-model.md`
- `wiki/concepts/mean-reversion.md`
- `wiki/concepts/loss-aversion.md`
- `wiki/concepts/kelly-criterion.md`

### source（来源摘要页）
`wiki/sources/{YYYY-MM-DD}-{short-title}.md`

示例：
- `wiki/sources/2026-05-13-intelligent-investor-ch20.md`
- `wiki/sources/2026-04-01-dalio-principles.md`
- `wiki/sources/2026-03-15-fed-rate-decision.md`

### analysis（分析页）
`wiki/analyses/{analysis-topic}[-{YYYYMM}].md`

带日期后缀的用于会定期更新的分析（如季度估值），纯主题的用于一次性分析：

示例：
- `wiki/analyses/china-internet-valuation-202605.md`（定期更新，带时间戳）
- `wiki/analyses/portfolio-review-2026q1.md`（按季度）
- `wiki/analyses/compare-growth-vs-value.md`（一次性对比分析）

### portfolio（组合/策略页）
`wiki/portfolios/{strategy-name}.md`

示例：
- `wiki/portfolios/core-value-holdings.md`
- `wiki/portfolios/macro-hedge-strategy.md`
- `wiki/portfolios/rebalance-2026q2.md`

## id 构造规则

**id = 文件名去掉 `.md` 后缀**（这是唯一规则，与 frontmatter.md 中的定义一致）：

| 文件路径 | id |
|---------|-----|
| `wiki/concepts/margin-of-safety.md` | `margin-of-safety` |
| `wiki/entities/berkshire-hathaway.md` | `berkshire-hathaway` |
| `wiki/analyses/china-internet-valuation-202605.md` | `china-internet-valuation-202605` |
| `wiki/sources/2026-05-13-intelligent-investor-ch20.md` | `2026-05-13-intelligent-investor-ch20` |
| `wiki/portfolios/core-value-holdings.md` | `core-value-holdings` |

### id 唯一性约束

id 全局唯一，**不同目录下也不能重名**。冲突解决策略：

| 冲突场景 | 解决方式 |
|---------|---------|
| concept 和 entity 都想叫 `sp500` | entity 用 `sp500-index`，concept 用 `sp500-valuation` |
| 同一公司的多版分析 | 用时间后缀区分：`tencent-thesis-202601` vs `tencent-thesis-202605` |
| 同名概念不同语境 | 用 qualifier 区分：`momentum-factor` vs `momentum-psychology` |

## 受控词表（slug 来源）

slug 部分的词汇应尽量来自 `schema/glossary.md` 的术语 id。未登录术语可临时用，但应及时补充到词表。

## 禁用模式

| 反例 | 问题 |
|------|------|
| `安全边际.md` | 中文文件名 |
| `MarginOfSafety.md` | 驼峰 |
| `margin_of_safety.md` | 下划线 |
| `margin of safety.md` | 空格 |
| `margin-of-safety-note.md` | 冗余后缀 |
| `投资笔记-巴菲特年信.md` | 中文 + 无结构 |

## 变更与迁移

- 改名用 `git mv` 保留历史（如果用了 git）
- 改名后在老位置放 stub：
  （stub 是唯一允许使用最小 frontmatter 的场景，只需 id + status + superseded_by）
  ```markdown
  ---
  id: old-id
  status: deprecated
  superseded_by: [new-id]
  ---
  # Moved
  This file moved to [[new-id]].
  ```
- lint 在报告里提示哪些 stub 超过 30 天可以安全删除
- 所有引用 `[[old-id]]` 的页面需要更新为 `[[new-id]]`

# Lint 检查项判定标准

每个检查项的详细判定规则、阈值设定依据和修复优先级说明。

## P0 — 必修

### #1 断链

**判定**：`[[id]]` 在所有 wiki 页面的 frontmatter 中找不到 `^id: {id}$`

**阈值**：任何断链都是 P0

**常见原因**：
- 页面改名后引用未更新
- 引用了"待创建"页面但忘记加入 index 的待创建表
- Typo（如 `margin-of-safty`）

**修复策略**：
- Typo → 修正拼写
- 改名 → 更新所有 `[[old-id]]` → `[[new-id]]`
- 未创建 → 加入 index 待创建表，标注被引用次数

### #2 supersedes 不对称

**判定**：A 的 `supersedes` 包含 B，但 B 的 `superseded_by` 不包含 A（或反向）

**阈值**：任何不对称都是 P0

**自动修复逻辑**：
```
if A.supersedes contains B:
  ensure B.superseded_by contains A
if A.superseded_by contains B:
  ensure B.supersedes contains A
```

### #3 conflict 超期

**判定**：`status: conflict` 且 `version` 距今 > 14 天

**阈值**：14 天（两周内用户应做出判断）

**不自动修复原因**：冲突解决需要投资判断，不是机械操作

**建议呈现**：列出冲突的两方观点和来源，方便用户快速决策

### #4 sources 为空

**判定**：文件无 `sources:` 字段，或有字段但下面没有列表项

**豁免**：
- `index.md`、`log.md`（结构性文件）
- `status: deprecated` 的 stub 文件

**阈值**：任何非豁免页面无来源都是 P0

### #5 过时数据

**判定**：`type` 为 analysis/portfolio/entity 且 `version` 距今 > 90 天

**为什么只检查这三类**：
- concept 类通常不含时效性数据
- source 类是对固定文本的摘要，不会"过时"
- 而 entity（公司数据）、analysis（市场分析）、portfolio（持仓）都含时效性信息

**阈值**：90 天（一个季度）

**不自动修复原因**：需要新的数据源输入才能更新

---

## P1 — 重要

### #6 孤岛页面

**判定**：没有任何其他文件通过以下方式引用该页面：
- body 中的 `[[id]]` 或 `[[id|显示文本]]`
- frontmatter 中的 `see_also`、`supersedes`、`superseded_by`

**豁免**：`overview.md`（作为顶层入口，可以只被 index 引用）

**自动修复逻辑**：
1. 读取孤岛页面的 `topic` 字段
2. 找其他有相同 topic 的页面
3. 将孤岛页面加入那些页面的 `see_also`

### #7 see_also 为空

**判定**：`see_also` 字段缺失或为 `[]`

**豁免**：stub 文件

**自动修复逻辑**：
1. 扫描正文中的 `[[wikilink]]` 目标
2. 将正文引用的 id 作为 `see_also` 候选
3. 过滤掉 `supersedes`/`superseded_by` 中已包含的

### #8 index 未收录

**判定**：`knowledge/wiki/` 下存在有 `id` 字段的 .md 文件，但 `index.md` 中不包含该 id

**豁免**：stub 文件

**自动修复逻辑**：
1. 从页面 frontmatter 提取 id、title、domain、summary
2. 确定所属 index 表格（按 type 归类）
3. 追加一行到对应表格

### #9 summary 空泛

**判定规则**：
- 长度 ≤ 10 字 → 空泛
- 包含以下泛化词 → 空泛：关于、说明、介绍、描述、概述、相关内容
- 格式为 "{实体名}的{泛化词}" → 空泛

**好 summary 的特征**：
- 包含具体数据或结论
- 能让人不读全文就知道核心信息
- ≥ 20 字，≤ 80 字

### #10 超长文件

**判定**：`wc -l` > 300 行（含 frontmatter）

**阈值依据**：
- Obsidian 阅读体验在 200-300 行时最佳
- Agent 处理长文件 token 消耗显著增加
- 超长通常意味着可以按子主题拆分

---

## P2 — 建议

### #11 术语未链接

**判定**：正文中出现了 `knowledge/schema/glossary.md` 中注册的术语 id（或其同义词），但该处未用 `[[wikilink]]` 包裹

**注意**：
- 只检查**首次出现**的位置（一篇文中同一术语只需链接一次）
- frontmatter 中的 topic/see_also 不算"正文出现"

**自动修复逻辑**：
1. 找到正文中术语首次出现的位置
2. 替换为 `[[term-id|原文本]]`
3. 如原文本就是 term-id 的英文形式，简化为 `[[term-id]]`

### #12 单向 see_also

**判定**：A 的 `see_also` 包含 B，但 B 的 `see_also` 不包含 A

**与 P0 #2 的区别**：see_also 的对称性是"推荐"而非"强制"（不像 supersedes 那样必须对称）

**自动修复逻辑**：将 A 的 id 追加到 B 的 `see_also` 数组

### #13 draft 超期

**判定**：`status: draft` 且 `version` 距今 > 14 天

**阈值**：14 天（两周足够完善一个草稿）

**不自动修复原因**：可能草稿确实还没完成，需用户确认

### #14 缺失 domain/topic

**判定**：非结构性文件缺少 `domain` 或 `topic` 字段

**不自动修复原因**：归类需要理解内容语义

### #15 高频断链

**判定**：同一个不存在的 id 被 `[[wikilink]]` 引用 ≥ 3 次

**阈值**：3 次（说明这个概念在知识库中频繁出现，值得创建独立页面）

**建议格式**：
```
HIGH_FREQ_BROKEN: [[term-id]] referenced N times
  Referenced by: page-a, page-b, page-c
  Suggested type: concept/entity (based on naming pattern)
```

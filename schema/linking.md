# 链接与引用规范

`wiki/` 层文件之间的相互引用规则。**唯一真源**。

## `[[wikilink]]` 语法

用 **id 而非路径** 引用其他文档：

```markdown
安全边际的核心逻辑见 [[margin-of-safety]]，与 DCF 估值的关系见 [[dcf-model]]。
```

**为什么用 id 不用路径**：
- 文件改名/移动时，`[[id]]` 不破裂
- Agent 检索时更易识别为可追溯锚点
- lint 可以统一扫 `\[\[([^\]]+)\]\]` 正则检查断链

## 显示文本

```markdown
[[margin-of-safety|安全边际]]
[[berkshire-hathaway|伯克希尔]]
```

`|` 后是显示文本（可选），前面是 id。

## frontmatter 中的引用字段

### `see_also`（同级相关文档）

- **语义**：主题相关，但不是取代关系
- **对称性**：推荐对称（A see_also B 时 B 也 see_also A），不强制
- **lint** 会提示单向引用

```yaml
see_also: [intrinsic-value, dcf-model, moat]
```

### `supersedes` / `superseded_by`（版本取代关系）

- **语义**：A supersedes B → A 是最新版
- **对称性**：**必须双向对称**（强制 lint 规则）
- **查询行为**：命中 deprecated 文档 → 自动追 superseded_by 找新版

```yaml
# china-internet-valuation-202605.md（新版）
status: active
supersedes: [china-internet-valuation-202601]
superseded_by: []

# china-internet-valuation-202601.md（旧版）
status: deprecated
supersedes: []
superseded_by: [china-internet-valuation-202605]
```

### 典型场景：投资论点演进

投资一家公司的论点会随时间变化。用 `supersedes` 记录论点演进：

```yaml
# tencent-thesis-202605.md
supersedes: [tencent-thesis-202601]
# → 上一版的买入理由、估值假设已过时，新版反映最新认知
```

## 跨层引用

### 项目内路径一律优先使用相对路径

在 `wiki实践/` 仓库内部，只要引用目标文件也位于本仓库内，就应**优先使用相对路径**，不要写工作机绝对路径。

- 适用场景：Markdown 链接、命令示例、脚本说明、文档中的文件引用。  
- 原因：仓库可迁移、不同机器可复用、不会把个人本地目录结构泄露进知识库。  
- 例外：若正文是在记录**仓库外原始来源文件**的 provenance（例如本机下载目录下的原始 HTML/PDF 路径），可以保留绝对路径作为事实记录。  

示例：

```markdown
✅ [脚本](../../scripts/ic_im_roll_discount_stress.py)
✅ 运行：python3 scripts/ic_im_roll_discount_stress.py
❌ 运行：python3 /Users/name/.../wiki实践/scripts/ic_im_roll_discount_stress.py
```

### wiki → raw（允许）

wiki 文档可以引用 raw 层的原文作为来源。使用相对路径（wiki 文件位于 `wiki/` 子目录下，`raw/` 是其兄弟目录）：

```markdown
原文详见 [研报全文](../raw/2026-05-Goldman-China-Internet.pdf)。
```

> **Obsidian 注意**：Obsidian 默认支持相对路径链接。请在 设置 → 文件与链接 → 新建笔记存放位置 中确认使用相对路径模式。如使用"基于仓库根目录"的绝对路径模式，则写为 `raw/2026-05-Goldman-China-Internet.pdf`。

### raw → wiki（禁止）

raw 层是不可变原文，不该引用会变化的 wiki 层。

### wiki → wiki（主要场景）

大多数 `[[id]]` 都是 wiki 内部引用。

## 正文中的术语链接

正文提到核心概念时，**首次出现**应加 `[[wikilink]]`：

```markdown
巴菲特强调 [[margin-of-safety|安全边际]] 是投资中最重要的概念。
公司的 [[moat|护城河]] 决定了长期盈利能力的可持续性。
```

lint 会扫描正文：出现 glossary 里的术语但没加 `[[]]` → 提示补链。

## 断链处理

- lint 用正则扫所有 wikilink
- 每个 id 去 wiki 文件里 grep `^id: {id}$`
- 没找到 → 断链，进 lint 报告
- 处理方式：
  - 可能是 id 改名？改成 `[[new-id]]`
  - 可能是页面未创建？加入 index.md 的"待创建"列表

## 引用密度要求

好的 wiki 页应该**密集交叉引用**：

- `see_also` 至少 1 项
- 正文至少 2 处 `[[wikilink]]` 到相关页面
- entity 类必须链接到相关 concept 页
- analysis 类必须链接到所分析的 entity 页

**反例**（孤岛文档）：
```yaml
see_also: []
# 正文也没有任何 [[wikilink]]
```

**正面示例**：
```markdown
---
see_also: [margin-of-safety, intrinsic-value, moat]
---

## 估值分析

[[tencent|腾讯]] 当前 PE-TTM 约 18x，低于历史均值 25x。
按 [[dcf-model|DCF 模型]] 估算内在价值约 450 港元，
当前价格 380 港元提供约 15% 的 [[margin-of-safety|安全边际]]。
其在社交和游戏领域的 [[moat|护城河]] 仍然稳固。
```

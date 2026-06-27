---
name: query
description: |
  知识库查询 — 三层定位 + 交叉验证 + 结构化输出 + 归档判断。
  触发词：query、查一下、查询知识库、找一下、PE 是多少、谁说过、检索 wiki、知识库里有没有、相关页面。
---

# /query — 投资知识库查询

## 触发

用户提出关于投资理念、公司分析、估值方法、宏观判断等问题时触发。

## 工作流程

### 1. 问题归类

- 识别领域：宏观 / 行业 / 个股 / 方法论 / 心理 / 策略
- 抽取 3-5 核心关键词
- 用 `schema/glossary.md` 扩展同义词（如"护城河"→ moat, competitive advantage, 竞争优势）
- 判断问题类型：事实型 / 对比型 / 观点型 / 时效型

### 2. 定位（L1 + L2 + L3 全跑，不是 fallback 关系）

三层全跑后合并去重为原始候选池。脚本路径：`.coco/skills/query/scripts/search.sh`（脚本会自动 cd 到项目根，cwd 不限）。

| 层 | 操作 | 候选集 |
|---|------|-------|
| L1 索引扫描 | 读 `wiki/index.md`，按 summary 和 domain 列筛选 | A |
| L2 topic 匹配 | `bash .coco/skills/query/scripts/search.sh topic {keyword}` | B |
| L3 全文搜索 | `bash .coco/skills/query/scripts/search.sh fulltext {keyword}` | C |

合并 A ∪ B ∪ C 去重 → 原始候选池。

### 3. 候选裁剪（性能保护）

原始候选池应用以下裁剪规则：

| 候选池大小 | 策略 |
|-----------|------|
| ≤ 10 页 | 全部深读 |
| 11-20 页 | 按相关度排序，取 Top 12；其余仅读 frontmatter summary |
| > 20 页 | 要求用户缩窄问题范围，或提供 domain/type 过滤条件 |

**相关度排序信号**（权重递减）：
1. L1 命中（index summary 直接相关）
2. L2 命中（topic 精确匹配）
3. L3 命中次数（关键词出现频率）
4. status=active 优先于 draft
5. version 日期越新越优先

### 4. 候选深读

- Read 每个入围候选的完整 wiki 页
- 沿 `see_also` 递归扩展 **≤ 2 跳**（每跳最多追 3 个最相关的）
- `status: deprecated` → 追 `superseded_by` 找新版，**不读旧版正文**
- `status: conflict` → 读全部分支，不择一
- 必要时回溯 `raw/` 层原文补充细节（优先读 sources 指向的段落）

### 5. 交叉验证

每个硬事实（数字/日期/比例/排名）：
- ≥2 source 一致 → **high**
- 单 source → **medium**（标"待确认"）
- 多源矛盾 → **conflict**（并列所有说法 + 来源，不择一）

### 6. 结构化输出

```markdown
## 答案
（核心回答，简洁明了）

## 置信度
high / medium / low（说明原因）

## 来源
- [[source-id]] — 具体引用位置
- ...

## 相关知识
- [[related-id-1]] — 一句话说明关联
- [[related-id-2]] — ...

## 时效性说明
（如涉及市场数据/估值，标注数据截止时间）
```

### 7. 归档判断

满足以下**任一条件**时，将答案归档为新的 analysis 页面：

| 条件 | 说明 |
|------|------|
| 涉及 ≥3 个 wiki 页的交叉引用 | 答案综合了多方信息，有独立价值 |
| 包含原创对比/推演 | Agent 在回答中做了不在任何单一来源中的推导 |
| 用户明确要求保存 | 用户说"这个记下来" |
| 时效性分析 | 涉及时间敏感的市场判断，值得作为时间快照 |

归档时：
- 存入 `wiki/analyses/`
- 按 `schema/frontmatter.md` 填写完整 frontmatter
- 更新 `wiki/index.md`
- 追加 `wiki/log.md`

## 未命中处理

```markdown
## 未找到直接答案

**最接近的候选**：
- [[candidate-1]] — 相关度说明
- [[candidate-2]] — ...

**建议**：
- 可以补充的素材方向（具体书/文章推荐）
- 可以提出的更精确问题
- 建议执行 `/ingest` 补充的素材类型
```

## 边界情况

| 场景 | 处理方式 |
|------|---------|
| 问题涉及实时市场数据 | 标注"知识库数据截止于 {date}"，建议用户补充最新数据 |
| 问题超出知识库范围 | 明确告知未覆盖，建议素材摄入方向 |
| 问题有多种合理解读 | 列出可能解读，分别回答或请用户澄清 |
| 命中多个版本（supersedes 链） | 默认展示最新版，注明"历史版本见 [[old-id]]" |
| 候选池为空 | 不瞎编，返回未命中模板 + glossary 同义词扩展建议 |
| 问题跨多个 domain | 分 domain 各定位一轮，合并结果 |

## 辅助脚本

- `scripts/search.sh` — 候选定位（topic 搜索、全文搜索、相关度排序）

详见 `reference/search-patterns.md` 了解不同查询类型的最优搜索模式。

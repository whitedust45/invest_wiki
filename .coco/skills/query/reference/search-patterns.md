# 查询模式参考

不同类型问题的最优搜索策略。Agent 在 Step 1 归类后参考本文件选择搜索方式。

## 查询类型 → 搜索策略

### 事实型（"X 的 PE 是多少？"）

```
L1: index.md 找 entity/analysis 类
L2: topic 搜 entity name
L3: fulltext grep 具体指标名
```
- 候选池通常小（1-5），不需裁剪
- 关注 `effective_date` 确认时效

### 对比型（"A 和 B 哪个估值更低？"）

```
L1: index.md 找两个 entity + 相关 analysis
L2: topic 搜两个实体名
L3: fulltext grep 估值指标关键词
```
- 候选池可能较大 → 先按 entity 分组，每组取 Top 3
- 可能触发归档（产生新对比分析）

### 方法论型（"DCF 怎么做？"）

```
L1: index.md 找 concept 类
L2: topic 精确匹配 glossary 术语 id
L3: fulltext grep 术语的同义词
```
- 优先读 concept 页，再读引用该概念的 analysis 页作为实例
- 沿 see_also 扩展有效

### 时效型（"最近有什么变化？"）

```
L1: index.md 按 effective_date 倒序
L2: 无（太泛）
L3: fulltext grep 时间关键词（2026Q1, 最近, 近期）
```
- 按 version 字段排序，取最新的 5-10 页
- 必须标注数据截止时间

### 观点型（"你怎么看 X？"）

```
L1: index.md 找 analysis + concept 类
L2: topic 匹配
L3: fulltext grep 相关论点关键词
```
- 需要综合多个来源 → see_also 扩展到 2 跳
- 明确区分"事实"vs"个人观点"
- 高概率触发归档

### 跨领域型（"利率对科技股估值的影响"）

```
L1: index.md 找 macro + industry + valuation 三个 domain
L2: topic 搜 interest-rate + industry 相关术语
L3: fulltext grep "利率" + "科技" + "估值"
```
- 分 domain 各搜一轮，合并后裁剪
- 候选池易超 20 → 主动建议用户缩窄

## 同义词扩展速查

Agent 做同义词扩展时，先查 `schema/glossary.md` 的"同义词"字段。常见扩展模式：

| 用户可能说 | 扩展为 |
|-----------|--------|
| 安全边际 | margin-of-safety, MOS, 安全垫 |
| 护城河 | moat, competitive advantage, 竞争优势 |
| 估值 | valuation, PE, PB, DCF, EV/EBITDA |
| 巴菲特 | warren-buffett, berkshire-hathaway |
| 定投 | dollar-cost-averaging, DCA |
| 分散 | diversification, 分散投资 |
| 回撤 | drawdown, max drawdown, MDD |

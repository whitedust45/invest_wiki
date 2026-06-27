---
id: kelly-criterion-vs-value-investing
title: 凯利公式与价值投资的关系 / Kelly Criterion vs. Value Investing
type: analysis
domain: methodology
topic: [kelly-criterion, value-investing, margin-of-safety]
tags: [仓位管理, 安全边际, 复利, 方法论对比]
status: active
version: 2026-05-15
effective_date: 2026-05-15
supersedes: []
superseded_by: []
see_also: [kelly-criterion, value-investing, overview, personal-position-sizing-framework]
sources:
  - type: article
    title: "A New Interpretation of Information Rate"
    author: John L. Kelly Jr.
    url: https://www.princeton.edu/~wbialek/rome/refs/kelly_56.pdf
    publisher: Bell System Technical Journal
    date: 1956-07-01
  - type: article
    title: "Understanding the Kelly Criterion"
    author: Edward O. Thorp
    url: https://econpapers.repec.org/RePEc:wsi:wschap:9789814293501_0036
    publisher: World Scientific
    date: 2011-01-01
  - type: article
    title: "The Kelly Criterion: You Don’t Know the Half of It"
    author: Alon Bochman
    url: https://rpc.cfainstitute.org/blogs/enterprising-investor/2018/the-kelly-criterion-you-dont-know-the-half-of-it
    publisher: CFA Institute
    date: 2018-06-14
  - type: article
    title: Value Investing Definition, How It Works, Strategies, and Risks
    author: Adam Hayes
    url: https://www.investopedia.com/terms/v/valueinvesting.asp
    publisher: Investopedia
    date: 2025-07-14
  - type: article
    title: 致伯克希尔·哈撒韦公司股东
    author: Warren E. Buffett
    date: 2025-02-22
summary: 价值投资回答“为什么有优势”，凯利公式回答“既然有优势该下多大”，两者应通过安全边际连接起来
---

# 凯利公式与价值投资的关系 / Kelly Criterion vs. Value Investing

## 核心结论

[[kelly-criterion|凯利公式]] 和 [[value-investing|价值投资]] 不是竞争关系，而是上下游关系：

1. [[value-investing|价值投资]] 回答：**为什么这笔机会可能有优势**。  
2. [[kelly-criterion|凯利公式]] 回答：**既然有优势，该押多大**。  
3. [[margin-of-safety|安全边际]] 则是两者之间最重要的连接器：它既约束买入价格，也约束凯利输入的保守程度。  

所以，真正成熟的做法不是在“价值投资”和“凯利公式”之间二选一，而是先用前者寻找 edge，再用后者决定下注深度。

## 两者分别解决什么问题

### 价值投资解决的是“识别错误定价”

[[value-investing|价值投资]] 的核心不是买便宜，而是判断价格是否低于 [[intrinsic-value|内在价值]]。它关注的是：

- 企业值多少钱；  
- 市场为什么会错；  
- 这个错价是否足够大，能留下 [[margin-of-safety|安全边际]]。  

它的强项在于建立判断锚点，把投资从“价格波动游戏”拉回到价值和生意本身。

### 凯利公式解决的是“如何分配资本”

[[kelly-criterion|凯利公式]] 并不告诉你哪个公司更好，也不告诉你估值模型该怎么建。它关心的是：

- 若你真的有 edge，应该下多大；  
- 怎样在长期重复机会中提高复利速度；  
- 怎样避免因为仓位过头，让正确观点也死在波动里。  

所以它的强项不是发现机会，而是把机会转译成资本配置动作。

## 为什么很多人把两者错配了

最常见的两种错法是：

### 错法一：只有价值判断，没有仓位纪律

这类投资者也许能看出一家公司被低估，却会在执行上犯两个典型错误：

- 低估就一把梭，忽略 [[drawdown|回撤]] 和相关性；  
- 即使 thesis 正确，也因为仓位太重，先在波动里被迫离场。  

这说明：只靠 [[value-investing|价值投资]]，并不能自动得到良好的 [[position-sizing|仓位管理]]。

### 错法二：没有价值判断，却迷信凯利公式

这类做法更危险：

- 先主观假设自己“胜率很高”；  
- 再把这种主观乐观塞进 [[kelly-criterion|凯利公式]]；  
- 最后用一个看似精确的数字，给重仓找数学外衣。  

这时公式并没有提升严谨性，只是把偏见数字化了。

## 安全边际是两者的桥

[[margin-of-safety|安全边际]] 常被理解为“价格低于价值的缓冲区”，但对凯利公式来说，它还有第二层作用：**参数安全边际**。

也就是说，真正把凯利用在价值投资里时，至少有两层保守：

1. **买价层面保守**：只在价格明显低于价值时行动。  
2. **参数层面保守**：即使很喜欢这个机会，也低估胜率、高估亏损、降低仓位。  

这两层保守叠加之后，凯利才更接近价值投资者能长期执行的工具，而不是催化冲动重仓的借口。

## 价值投资者最适合怎样使用凯利

一个更自然的顺序是：

### 第一步：先用价值投资筛机会

- 是否真的便宜，而不是表面倍数低？  
- 便宜是因为市场情绪，还是因为基本面坏了？  
- 是否具备足够厚的 [[margin-of-safety|安全边际]]？  

### 第二步：再用凯利思维定深度

- 如果这是高把握、高安全边际、低相关性的机会，仓位可以更高；  
- 如果只是“也许便宜”、催化不明、流动性差，仓位就必须保守。  

这时即便你不显式代公式，也已经在按凯利精神做事：让资本更多流向最有优势的机会。

### 第三步：最后用组合约束收口

- 单一头寸上限；  
- 风格暴露上限；  
- 最大可承受 [[drawdown|回撤]]；  
- [[rebalance|再平衡]] 与 thesis 失效时的退出纪律。  

这一步是把“理论上该下多大”变成“现实里能承受多大”。

## 芒格—巴菲特版本为什么尤其适合半凯利

如果按芒格—巴菲特式的 [[value-investing|价值投资]] 去理解，你会发现它和 Half Kelly 特别兼容：

- 它强调高质量生意与长期复利，而不是频繁下注；  
- 它强调少犯大错，而不是把每次机会都压到增长率极限；  
- 它承认世界充满不确定性，因此更重视纠错、现金储备和生存。  

这套思想和“把满凯利再打折执行”的精神几乎是同方向的：都在反对过度自信。

## 一个最简整合框架

如果只保留一句可执行的话，我会这样整合：

> 先用 [[value-investing|价值投资]] 找到“错价 + 安全边际”的机会，再用 [[kelly-criterion|凯利公式]] 或其折扣版决定仓位，并始终让 [[drawdown|回撤]] 承受力高于理论最优冲动。

展开成 4 步就是：

1. 先判价值，不先判价格波动；  
2. 先找安全边际，不先找重仓理由；  
3. 先算理论上限，不直接执行满凯利；  
4. 先保护组合生存，不把“最优增长率”当最高目标。  

## 对当前知识库的价值

- 它把 [[value-investing]] 与 [[kelly-criterion]] 从并列概念变成上下游链路。  
- 它把 [[margin-of-safety]] 的含义从“买价折扣”扩展到“参数保守化”。  
- 它也为 [[personal-position-sizing-framework]] 提供了方法论根基：先有价值判断，再有仓位纪律。  

## 时效性说明

本分析于 2026-05-15 基于 Kelly、Thorp、CFA Institute、Investopedia 与巴菲特公开文本整理。其方法论判断具长期参考价值，但任何具体仓位执行仍须结合账户规模、市场结构与个人风险承受力调整。

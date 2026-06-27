---
id: kelly-criterion
title: 凯利公式 / Kelly Criterion
type: concept
domain: risk
topic: [kelly-criterion, position-sizing, drawdown]
tags: [仓位管理, 数学公式, 风险预算]
status: active
version: 2026-05-23
supersedes: []
superseded_by: []
see_also: [position-sizing, diversification, overview, drawdown, risk-reward, antifragile, convexity, barbell-strategy, cross-asset-carry-momentum, 2026-05-15-investopedia-kelly-criterion, 2026-05-21-taleb-barbell-guide, kelly-criterion-in-practice, kelly-criterion-vs-value-investing, personal-position-sizing-framework, when-not-to-use-kelly-criterion]
sources:
  -
    type: article
    title: "Kelly Criterion Explained"
    author: Will Kenton
    url: https://www.investopedia.com/terms/k/kellycriterion.asp
    publisher: Investopedia
    date: 2025-08-28
  -
    type: article
    title: "A New Interpretation of Information Rate"
    author: John L. Kelly Jr.
    url: https://www.princeton.edu/~wbialek/rome/refs/kelly_56.pdf
    publisher: Bell System Technical Journal
    date: 1956-07-01
  -
    type: article
    title: "Understanding the Kelly Criterion"
    author: Edward O. Thorp
    url: https://econpapers.repec.org/RePEc:wsi:wschap:9789814293501_0036
    publisher: World Scientific
    date: 2011-01-01
  -
    type: article
    title: "The Kelly Criterion: You Don’t Know the Half of It"
    author: Alon Bochman
    url: https://rpc.cfainstitute.org/blogs/enterprising-investor/2018/the-kelly-criterion-you-dont-know-the-half-of-it
    publisher: CFA Institute
    date: 2018-06-14
  -
    type: article
    title: "Practical Implementation of the Kelly Criterion: Optimal Growth Rate, Number of Trades, and Rebalancing Frequency for Equity Portfolios"
    author: Andrea Carta, Claudio Conversano
    url: https://www.frontiersin.org/journals/applied-mathematics-and-statistics/articles/10.3389/fams.2020.577050/full
    publisher: Frontiers in Applied Mathematics and Statistics
    date: 2020-10-08
  -
    type: dialogue
    context: "关于凯利公式、edge、对数增长与分数凯利的连续讨论"
    date: 2026-05-18
    participants: [用户, Agent]
    trigger: "接下来我想跟你讨论一个观点就是对于凯利公式，有一种观点认为在投资市场上做投资，很多时候其实就是混沌的"
    rounds: 0
    depth: refined
summary: 凯利公式不是单纯靠胜率定仓位的公式，而是围绕 edge、对数增长与分数凯利展开的仓位框架
---

# 凯利公式 / Kelly Criterion

## 定义

[[kelly-criterion|凯利公式]] 是一种根据胜率与平均盈亏比，估算单笔机会应投入多少资金的仓位方法。它解决的是“该下多大”，而不是“该不该买”。

在因子组合里，[[momentum|动量]] 和 [[carry|Carry]] 这类信号也可以被理解为 edge 的来源，但一旦 edge 不稳定、相关性升高或存在强平约束，凯利结果必须大幅打折。

## 公式直觉

- 胜率越高，可承受的仓位通常越高。  
- 盈亏比越好，可接受的投入比例通常也越高。  
- 如果算出来的比例很低甚至为负，意味着这不是一个值得重仓的机会。  

## 公式与目标函数

- 二元下注场景的经典写法常记作：`f* = (bp - q) / b`。其中 `f*` 是最优下注比例，`p` 是获胜概率，`q = 1 - p`，`b` 是净赔率。  
- 投资/交易里更常见的简化写法是 `K = W - (1 - W) / R`：`W` 是胜率，`R` 是平均盈利 ÷ 平均亏损。  
- **[2026-05-15 补充]** Kelly 1956 年原始论文真正优化的不是“单次期望收益”，而是长期重复下注下的 **财富对数增长率**（log wealth growth）。这也是凯利公式最容易被讲浅、却最重要的一层。  

## [2026-05-18 理论中轴] 用三句话抓住凯利公式

1. **凯利真正关心的不是“胜率高不高”，而是 edge 是否为正。** 若净赔率为 `b`，则盈亏平衡胜率 `p* = 1 / (1 + b)`；只有当真实胜率高于这条线时，机会才值得下注。  
2. **凯利真正优化的不是单期期望收益，而是长期对数增长。** 因为财富演化是乘法过程，所以它天然会惩罚过度波动与深回撤。  
3. **现实里 Full Kelly 更像理论上限，Fractional Kelly 才更接近可执行解。** 因为市场中的 edge 往往很薄、参数又只能估计，半凯利或更低仓位通常是在为不确定性预留安全边际。  

如果想继续看“为什么深回撤会伤害复利效率”，直接跳到 [[drawdown]]；如果想看这些理论最后如何落实为组合中的实际权重控制，则继续看 [[position-sizing]]。  

如果把视角从单笔下注扩展到整体组合，[[antifragile|反脆弱]]、[[convexity|凸性]] 与 [[barbell-strategy|杠铃策略]] 提醒我们：在肥尾世界里，问题不只是“某个 edge 下该押多大”，还包括“整个暴露结构会不会在极端情景中非线性变形”。

## 为什么它厉害

- 它把“我有没有优势”转成“我该押多大”，因此是把认知优势转成资金纪律的桥梁。  
- 在“机会可重复、优势可估计、赔率可量化”的前提下，它追求的是长期复利速度，而不是短期胜率好看。  
- 它天然惩罚“优势很小却下很重”的行为，因为过度下注会显著伤害长期增长。  

## Full Kelly、Half Kelly 与 Fractional Kelly

- **Full Kelly**：理论上，当你的概率、赔率和分布假设都正确时，Full Kelly 给出长期复利增长最快的仓位。  
- **Half Kelly / Fractional Kelly**：现实中更常见。**[2026-05-15 补充]** Thorp、CFA Institute 与后续实务研究都强调，投资者往往会把理论仓位打五折甚至四分之一，原因不是“不懂公式”，而是要对抗参数估计误差、相关性突变和心理承受上限。  
- 一个极重要的实践结论是：**下注过头通常比下注保守更危险**。少押一点，通常只是增长慢一些；多押一点，可能让回撤和接近归零风险急剧放大。  

## 适用前提

- 你对胜率、赔率或收益分布至少有一个保守且可复核的估计。  
- 机会具有一定重复性，而不是一次性、不可复盘的豪赌。  
- 资金不会因为一次错误就被永久摧毁，且你能持续参与下一轮。  
- 你承认模型只是近似，现实里存在流动性、跳空、尾部风险与相关性飙升。  

## 常见误用

- 把凯利公式当成“选股器”使用。它解决的是仓位，不是判断标的质量。  
- 用过度乐观的胜率和盈亏比输入公式，再把算出来的大仓位当成“数学证明”。  
- 对高波动、低流动性、强相关资产直接套 Full Kelly。  
- 忽略尾部风险、制度变化或风格切换，误把历史样本当成稳定真理。  

## 实战上更可执行的用法

1. 先做研究，确认这是不是一个有优势的机会，而不是先套公式。  
2. 用保守参数估计胜率与盈亏比，最好用区间而不是单点。  
3. 先算出 Full Kelly，只把它当作“理论上限”。  
4. 再按自己的回撤承受力、流动性与组合相关性，降到 Half Kelly、Quarter Kelly，甚至更低。  
5. 最后叠加组合约束：单一头寸上限、行业暴露、杠杆上限、再平衡规则。  

## 组合层面的补充理解

**[2026-05-15 补充]** 2020 年的实证研究把凯利框架推进到股票组合层面：在不做空、不加杠杆的约束下，凯利组合常常比传统均值-方差切点组合更集中、预期增长更高，但波动和回撤也更大。研究里 Full Kelly 的长期终值优势明显，但 Half Kelly 的回撤体验通常更可执行；而过度下注则会带来接近毁灭性的回撤。  

## 与仓位管理的关系

- [[kelly-criterion|凯利公式]] 是 [[position-sizing|仓位管理]] 的一种数学表达。  
- 它适合帮助投资者把“判断强弱”转成“资金占比”。  
- 但它不能替代研究、估值和组合层风险控制，也不能脱离 [[risk-reward|风险收益比]] 的现实约束单独使用。  
- 若想看“仓位过大为什么不仅危险，而且会伤害长期复利斜率”，可直接对照 [[position-sizing]] 与 [[drawdown]] 两页一起理解。  

## 使用提醒

- 凯利公式强依赖你对胜率和盈亏比的估计，而这两个输入往往最容易被高估。  
- 理论最优不等于现实最优；回撤承受能力、流动性与个人约束都必须一起考虑。  
- 若忽视 [[diversification|分散化]]，单用凯利公式可能把仓位推得过重。  
- **[2026-05-15 补充]** 如果你本身已经依赖 [[margin-of-safety|安全边际]] 做决策，那么最自然的凯利用法不是“算出满仓理由”，而是把安全边际思想继续用于参数输入：低估胜率、高估亏损、降低仓位。  

## 与现有知识的关系

- 它是 [[position-sizing]] 页里最典型的公式化延伸。  
- 它也与 [[drawdown|回撤]] 直接相关：仓位稍大，净值波动和回撤就会迅速放大。  
- 它与 [[risk-reward|风险收益比]] 直接相连，因为赔率/盈亏比本来就是凯利输入的一部分。  
- 它与 [[value-investing|价值投资]] 也并不冲突：前者回答“下多大”，后者回答“为什么这笔交易可能有优势”。  

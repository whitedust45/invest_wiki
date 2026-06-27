---
id: kelly-criterion-in-practice
title: 凯利公式的实战化框架 / Kelly Criterion in Practice
type: analysis
domain: strategy
topic: [kelly-criterion, position-sizing, drawdown]
tags: [仓位管理, 半凯利, 复利, 回撤控制]
status: active
version: 2026-05-18
effective_date: 2026-05-15
supersedes: []
superseded_by: []
see_also: [kelly-criterion, position-sizing, 2026-05-15-investopedia-kelly-criterion, overview, when-not-to-use-kelly-criterion, tiered-position-sizing-template]
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
    title: "Practical Implementation of the Kelly Criterion: Optimal Growth Rate, Number of Trades, and Rebalancing Frequency for Equity Portfolios"
    author: Andrea Carta, Claudio Conversano
    url: https://www.frontiersin.org/journals/applied-mathematics-and-statistics/articles/10.3389/fams.2020.577050/full
    publisher: Frontiers in Applied Mathematics and Statistics
    date: 2020-10-08
  - type: article
    title: "Kelly Criterion Explained: Optimize Betting and Investing Strategies"
    author: Will Kenton
    url: https://www.investopedia.com/terms/k/kellycriterion.asp
    publisher: Investopedia
    date: 2025-08-28
  - type: dialogue
    context: "关于凯利公式、50%胜率直觉、edge、对数增长与分数凯利的连续讨论"
    date: 2026-05-18
    participants: [用户, Agent]
    trigger: "接下来我想跟你讨论一个观点就是对于凯利公式，有一种观点认为在投资市场上做投资，很多时候其实就是混沌的"
    rounds: 0
    depth: refined
summary: 将凯利公式转译成可执行的仓位纪律：用 edge、对数增长与分数凯利解释为什么实盘应把 Full Kelly 视为理论上限
---

# 凯利公式的实战化框架 / Kelly Criterion in Practice

## 核心结论

[[kelly-criterion|凯利公式]] 最值得吸收的，不是“那个公式本身”，而是背后的三层纪律：

1. **先确认优势，再决定下注大小**：凯利只能放大已有优势，不能替代研究。  
2. **先承认估计误差，再谈最优仓位**：现实中的概率、赔率和相关性几乎一定估错，所以 Full Kelly 更适合作为理论上限。  
3. **先保证活着，再追求复利最快**：若仓位大到让你扛不住 [[drawdown|回撤]]，理论最优也没有现实意义。  

## 为什么凯利公式常被误用

入门材料往往把凯利公式讲成“胜率 + 盈亏比 = 最优仓位”，这没有错，但容易让人忽略真正关键的前提。

- Kelly 1956 年原始论文的核心目标是**长期对数财富增长最大化**，不是单次收益最大化。  
- 这意味着它适合处理“可重复、可迭代”的机会，而不是一次性豪赌。  
- 也意味着它天然会惩罚过度下注：仓位一旦超过真实最优值，长期增长反而会恶化。  

所以，[[kelly-criterion|凯利公式]] 的正确定位是：它是 [[position-sizing|仓位管理]] 的数学框架，不是选股框架，不是预测框架，也不是风险消失术。

## 一个更适合投资者的三层框架

### 1. 优势层：这笔机会到底有没有 edge

在把公式写出来之前，先回答三个问题：

1. 这笔投资的优势来自哪里：[[value-investing|价值投资]] 的低估、[[mean-reversion|均值回归]]、[[momentum|动量]]，还是信息差？  
2. 这个优势是一次性的，还是有可能重复出现？  
3. 这个优势是否已经被市场结构、流动性或制度变化削弱？  

如果这三问答不清，凯利公式只能把模糊判断包装成精确数字。

### 2. 参数层：输入值必须保守，而不是漂亮

凯利法最危险的地方，不是公式复杂，而是输入太容易被高估。

- 胜率估高一点，仓位就会显著变大。  
- 盈亏比看得太乐观，仓位也会被抬高。  
- 若忽略极端行情和相关性上升，实际风险会比模型假设大很多。  

因此，比较靠谱的做法不是追求“最准参数”，而是像使用 [[margin-of-safety|安全边际]] 一样处理输入：

- 低估胜率；  
- 高估潜在亏损；  
- 把最好情况和最坏情况拉开看；  
- 对自己最自信的判断，反而要额外打折。  

### 3. 执行层：实盘仓位应该是打折后的凯利

**观点**：对大多数个人投资者和主观投资场景，Full Kelly 更像“理论上限”，而不是建议直接执行的仓位。

更可执行的顺序通常是：

1. 先算出理论 Full Kelly；  
2. 再降到 Half Kelly、Quarter Kelly，甚至更低；  
3. 最后再叠加组合上限、流动性、相关性和回撤承受力。  

这个顺序的本质是：先让公式给出方向，再让现实约束决定最终仓位。

## 为什么半凯利通常比满凯利更适合现实

Thorp、CFA Institute 的实践讨论，以及 2020 年的组合实证研究，实际上都指向同一个结论：**现实世界的主要问题不是“押得不够多”，而是“在自以为有把握时押得太多”。**

半凯利常见的好处有三个：

- 对参数误差更宽容；  
- 对心理承受力更友好；  
- 在牺牲一部分理论增长率的同时，通常能明显改善回撤体验。  

换句话说，Half Kelly 的意义不是“更胆小”，而是“更承认自己会错”。

## 组合层面的真正问题

把凯利公式用于投资时，最大误区之一是只盯单笔机会，而忽略组合。

- 单一头寸再优，也可能和其他持仓高度相关。  
- 多个“看起来都有 edge”的机会，可能在同一类极端行情里同时失效。  
- 当组合已经很集中时，再机械提高某一头寸的凯利权重，可能会让整体 [[drawdown|回撤]] 失控。  

因此，[[kelly-criterion|凯利公式]] 真正该和这些概念一起用：

- [[diversification|分散化]]：避免单一判断失误伤到全局；  
- [[risk-reward|风险收益比]]：确认赔率结构是否足够好；  
- [[position-sizing|仓位管理]]：把单笔判断转成组合内权重；  
- [[rebalance|再平衡]]：避免价格波动把权重漂移到失控位置。  

## 一个适合个人投资者的执行模板

如果把它压缩成最小可执行版本，我会用下面这套顺序：

### A. 先判断能不能做

- 是否真的有优势，而不是只觉得便宜？  
- 下行风险是否已经想清楚？  
- 亏损场景是否会伤到整体账户结构？  

### B. 再判断值得下多大

- 用保守参数算理论 Full Kelly；  
- 默认只取 1/2 或 1/4；  
- 若标的波动极大、流动性差、相关性高，再继续打折。  

若连这些折扣都难以安心执行，就说明问题已经不在公式，而在场景本身；这时应直接参考 [[when-not-to-use-kelly-criterion]]，而不是继续勉强优化比例。  

### C. 最后加组合约束

- 单一头寸上限；  
- 单一行业上限；  
- 最大可承受 [[drawdown|回撤]]；  
- 若触发再平衡或 thesis 失效，则自动减仓。  

## 对价值投资者尤其重要的一点

凯利公式和 [[value-investing|价值投资]] 并不冲突，但两者解决的问题不同：

- [[value-investing|价值投资]] 回答“为什么这笔机会可能有优势”；  
- [[kelly-criterion|凯利公式]] 回答“既然有优势，该下多大”；  
- [[margin-of-safety|安全边际]] 则提醒你：不仅买价要保守，输入凯利公式的参数也要保守。  

所以对价值投资者来说，最危险的不是不会算凯利，而是把主观乐观错当成数学严谨。

## 对当前知识库的价值

- 它把 [[kelly-criterion]] 从“公式定义页”推进成“可执行框架”。  
- 它把 [[position-sizing]]、[[drawdown]]、[[diversification]]、[[risk-reward]] 串成了一条完整的仓位纪律链路。  
- 它也把 [[margin-of-safety]] 与凯利公式接上：安全边际不只体现在买价，也体现在参数和仓位折扣。  

## 时效性说明

本分析于 2026-05-15 基于 Kelly 1956、Thorp、CFA Institute、Frontiers 2020 与 Investopedia 的公开材料整理而成。其方法论价值较长期，但具体仓位参数必须随策略类型、市场结构、流动性与个人回撤承受能力动态调整。

## [2026-05-18 对话沉淀] 从“50% 胜率直觉”到 edge、对数增长与分数凯利

**核心论点**：这轮讨论最值得保留的，不是“很多优秀策略胜率接近 50%”这个表面现象本身，而是其背后的更深结构：在竞争充分的市场里，可持续优势往往很薄，所以理论重心不应放在“胜率是否高于 50%”，而应放在“胜率相对盈亏平衡胜率究竟高出多少”，以及“这点 edge 在长期复利与参数误差面前还能留下多少真实增长”。

### 1. “胜率在 50% 附近”是常见现象，但不是理论本体

- 这轮讨论先从一个很强的经验观察出发：在量化交易等高竞争场景里，很多优秀策略的样本胜率确实常常在 50% 附近徘徊。  
- 更严谨的表述不是“重复交易的胜率天然收敛到 50%”，而是：**成熟市场会把可持续优势压缩得很薄**。因此，样本胜率看起来常常只比随机高一点，甚至表面上并不显著偏离 50%。  
- 真正应当防止的误读是：把“常在 50% 附近”偷换成“可以直接按 50% 处理”。在 [[kelly-criterion|凯利公式]] 里，50% 附近的微小偏差本身可能就是全部 alpha 所在。  

### 2. 对凯利来说，真正的核心量不是 p，而是 edge = p - p*

- 若净赔率为 `b`，则盈亏平衡胜率为 `p* = 1 / (1 + b)`。只有当真实胜率 `p` 高于 `p*` 时，这笔机会才具有正优势。  
- 因此，胜率本身没有脱离赔率独立存在的意义。50% 只有在赚亏对称、即 `b = 1` 时才恰好是分界线；一旦赔率结构不同，真正的生死线就不再是 50%。  
- 把经典二项式凯利公式 `f* = (bp - q) / b` 改写后，可以得到 `f* = ((b + 1) / b) (p - p*)`。这意味着最优仓位本质上是 **edge 的线性函数**，而不是原始胜率数字的函数。  
- 因而，在讨论仓位时，比起问“这个策略胜率是不是 50% 上下”，更有意义的问题是：**它相对 [[risk-reward|盈亏比/赔率]] 所隐含的盈亏平衡线，究竟多出了多少优势。**  

### 3. 凯利最大化的不是单期期望收益，而是长期对数增长

- 这轮讨论进一步把凯利的目标函数讲清楚：投资和交易的财富演化本质上是乘法过程，而不是加法过程。  
- 若财富按 `W_T = W_0 * Π(1 + r_t)` 演化，则长期平均增长率对应的是 `E[log(1 + r)]`，而不是单期的 `E[r]`。这就是为什么 [[kelly-criterion|凯利公式]] 最大化的是**期望对数收益**。  
- 这也解释了为什么“单期期望收益为正”并不等于“长期一定赚钱”。同样的算术平均收益，波动更大的路径会产生更低的几何增长率。一个常用近似是：`长期复利增长 ≈ 平均收益 - 1/2 × 方差`。  
- 从这个角度看，[[drawdown|回撤]] 不是单纯的心理问题，而是复利效率问题：大回撤不仅难受，更会实质性削弱资金曲线的长期斜率。  

### 4. 小 edge 世界里，分数凯利不是胆小，而是理性修正

- 如果市场里的真实优势只是很薄的一层，那么参数误差通常会和优势本身处于同一量级。此时最危险的不是“没有优势”，而是“把优势高估了”。  
- 由于凯利最优仓位与 edge 成正比，高估 edge 会直接把仓位推高；而对数增长对过度下注极其敏感，所以 **满凯利更像理论上限，而不是默认执行值**。  
- 因而，Half Kelly、Quarter Kelly 的意义，不是更保守、更胆小，而是承认现实世界里我们面对的是“估计过的凯利”，不是“已知真值的凯利”。  
- 用一句话概括就是：**低估优势，损失的是效率；高估优势，损失的是生存。**  

### 5. 对当前仓位框架的直接启发

这轮讨论把本页原有的三层框架又向前推进了一步：

1. **优势层**：不要执着于“胜率是否显著高于 50%”，而要追问 edge 究竟来自哪里，以及它是否只是略高于盈亏平衡线。  
2. **参数层**：把输入值写成保守区间，而不是漂亮的点估计；尤其在高频、量化、短线场景里，微小误差就足以毁掉薄 edge。  
3. **执行层**：把 Full Kelly 当作理论上限，再由 [[drawdown|回撤]]、流动性、相关性与心理承受力决定最终折扣。  

### 与知识库的关联

- 这段对话沉淀实质上补强了 [[kelly-criterion|凯利公式]] 页中“目标函数是长期对数增长”这一点，使它不只是一句定义，而成为理解仓位纪律的中轴。  
- 它也补强了 [[risk-reward|风险收益比]] 页的地位：赔率不是配角，而是决定盈亏平衡线 `p*` 的核心变量。  
- 同时，它把 [[drawdown|回撤]] 与 [[position-sizing|仓位管理]] 进一步接上：大回撤不是附带的不舒服，而是复利数学意义上的增长伤害。  

### 开放问题

- 若把这套理解推进到多资产组合层，edge、相关性与再平衡频率应如何共同进入一个更完整的组合凯利框架？  
- 若用于主观投资而非量化交易，赔率与真实亏损分布的可估性边界到底在哪里？  
- 若未来专门展开一页关于 [[drawdown|回撤]] 的理论分析，可以继续把“大回撤为何会伤害复利效率”独立成文。  

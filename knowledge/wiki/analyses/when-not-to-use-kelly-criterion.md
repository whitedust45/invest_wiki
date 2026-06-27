---
id: when-not-to-use-kelly-criterion
title: 凯利公式不适用的场景 / When Not to Use the Kelly Criterion
type: analysis
domain: risk
topic: [kelly-criterion, drawdown, diversification]
tags: [失败模式, 参数误差, 流动性, 杠杆, 相关性]
status: active
version: 2026-05-15
effective_date: 2026-05-15
supersedes: []
superseded_by: []
see_also: [kelly-criterion, kelly-criterion-in-practice, overview]
sources:
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
summary: 当赔率不可估、流动性差、相关性高或存在强平约束时，凯利公式容易把偏见和杠杆一起放大
---

# 凯利公式不适用的场景 / When Not to Use the Kelly Criterion

## 核心结论

[[kelly-criterion|凯利公式]] 最危险的时候，不是你不会算，而是你在**不该用它的时候还硬要用它**。  

它适合的是：优势可重复、赔率大致可估、没有立即死亡约束的场景。  
它不适合的是：参数几乎不可估、波动路径会先杀死你、或者一旦错了就没有下一轮的场景。

## 场景一：赔率与胜率根本不可估

这是最常见、也最容易被忽视的误用来源。

- 某些主观投资机会，看起来“逻辑很顺”，但其实无法给出相对靠谱的胜率与赔率估计。  
- 一旦输入本身只是情绪化判断，[[kelly-criterion|凯利公式]] 就不会提升严谨性，只会把偏见数字化。  

典型信号是：

- 你无法说清价值区间；  
- 你无法说明最坏情况会亏多少；  
- 你只是“非常有感觉”。  

这类场景更适合退回 [[tiered-position-sizing-template|分档仓位法模板]]，让 [[position-sizing|仓位管理]] 回到更朴素、更可执行的分档表达，而不是强行把主观自信写成数学结果。

## 场景二：低流动性、跳空风险大

凯利公式默认你可以按近似连续、可执行的价格调整仓位，但现实并不总给你这个条件。

- 小票、冷门资产、深度不足的市场，可能在你最想减仓时根本出不掉。  
- 跳空和流动性蒸发会让“理论最大亏损”瞬间失真。  

这意味着：你以为自己在按赔率下注，实际上是在拿一个随时会断掉的流动性假设下注。

## 场景三：存在强平、补保证金或杠杆约束

这一类场景里，最大的风险不是长期增长率，而是**你可能没有资格活到长期**。

- 期货、融资融券或高杠杆账户，一旦先遭遇大波动，可能会先触发强平。  
- 这时即使观点最终正确，也因为路径过于凶险而提前出局。  

所以，在存在明确保证金约束的场景下，[[drawdown|回撤]] 与生存性必须压过凯利最优；否则你优化的是一个自己根本活不到的终局。

## 场景四：组合内高度相关，却被当成独立机会

这在多头组合里特别常见：

- 你以为自己持有的是 4 个不同机会；  
- 实际上它们都押在同一个流动性环境、同一个风格因子或同一个宏观叙事上。  

此时若分别按 [[kelly-criterion|凯利公式]] 计算并叠加仓位，看起来每笔都合理，合起来却可能极度危险。

这也是为什么 [[diversification|分散化]] 不能只看持仓数量，而要看失效来源是否独立。

## 场景五：账户心理承受力显著低于理论回撤

哪怕公式算得没错，若你本人扛不住那种净值波动，结果仍然会很差。

- Full Kelly 在很多场景下理论增长更高；  
- 但如果它让你在中途频繁怀疑、减仓、反复破坏纪律，那么实盘结果往往远差于一个更保守的 Half Kelly。  

因此，心理承受力不是“软因素”，它本身就是执行层的硬约束。

## 场景六：一次性大机会，而不是可重复机会

Kelly 的底层逻辑更适合可重复下注、可长期迭代的框架。  
如果机会本身是一次性的、极难复盘的、未来不具备重复性，那么用它来追求“长期对数财富增长最优”会显得基础不稳。

这类场景更适合：

- 先做上限约束；  
- 再做情景分析；  
- 最后保守表达，而不是追求某个精确最优比例。  

## 场景七：把凯利当成重仓许可证

这是最典型的认知陷阱。

当一个投资者非常喜欢某笔机会时，很容易走入这条错误路径：

1. 先主观认定自己 edge 很大；  
2. 再填入一个乐观参数；  
3. 最后让凯利公式“证明”自己可以重仓。  

这不是风控，而是给冲动加学术包装。

## 一个最小识别清单

如果出现以下任意两条，我就默认**不该直接用凯利公式**：

- 无法可靠估赔率；  
- 流动性差；  
- 存在强平/补保证金约束；  
- 组合相关性高；  
- 心理承受力明显跟不上理论回撤；  
- 机会不具备可重复性；  
- 我正在特别想为重仓寻找理由。  

这时更合理的动作通常是：

- 降到 Half Kelly 以下；  
- 退回 [[tiered-position-sizing-template|分档仓位法模板]]；  
- 或者干脆只保留 [[personal-position-sizing-framework|个人仓位管理框架]] 里的低档仓位表达。  

## 对当前知识库的价值

- 它把“凯利公式很强”这件事补齐成“凯利公式何时根本不该上场”。  
- 它把 [[kelly-criterion-in-practice]] 中的执行提醒，进一步固化成防错清单。  
- 它也为未来处理高杠杆、低流动性、强相关资产时提供了明确刹车器。  

## 时效性说明

本页形成于 2026-05-15，目的是给自己的仓位系统补上“禁用条件”。它属于方法论与风险控制框架，不构成投资建议。

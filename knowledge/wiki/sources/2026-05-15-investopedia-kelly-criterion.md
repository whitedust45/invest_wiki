---
id: 2026-05-15-investopedia-kelly-criterion
title: 凯利公式导读 / Kelly Criterion Primer
type: source
domain: risk
topic: [kelly-criterion, position-sizing]
tags: [Investopedia, 仓位, 数学公式]
status: active
version: 2026-05-15
effective_date: 2025-08-28
supersedes: []
superseded_by: []
see_also: [kelly-criterion, position-sizing, diversification, overview, kelly-criterion-in-practice]
sources:
  - type: article
    title: "Kelly Criterion Explained\u003A Optimize Betting and Investing Strategies"
    author: Will Kenton
    url: https://www.investopedia.com/terms/k/kellycriterion.asp
    publisher: Investopedia
    date: 2025-08-28
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
summary: 该文将凯利公式定义为基于胜率和盈亏比确定仓位比例的工具，强调其适用边界
---

# 凯利公式导读 / Kelly Criterion Primer

## 摘要

这篇导读把凯利公式放回它最适合的位置：它不是预测工具，也不是选股框架，而是一种“仓位该下多大”的数学方法。文章最有价值的提醒是，公式给出的理论最优，并不等于现实中就该照单全收。

## 关键论点

- 凯利公式的目标是为单笔机会计算较优的资金投入比例。  
- 它依赖两个关键输入：胜率和平均盈亏比。  
- 它解决的是 [[position-sizing|仓位管理]]，而不是资产选择。  
- 最大风险不是公式本身，而是你对输入参数和现实约束过度自信。  

## 补充视角

**[2026-05-15 补充]** 这篇 Investopedia 文章适合作为入门定义，但如果要把 [[kelly-criterion|凯利公式]] 真正用到投资里，还需要补三层理解：  

- **原始目标函数**：Kelly 1956 讨论的核心不是“提高单笔期望收益”，而是长期重复下注下的对数财富增长最大化。  
- **实务折中方案**：Thorp 与 CFA Institute 的实践者视角都强调，现实里更常用 Half Kelly 或更低倍数，因为投资世界的参数几乎总是带误差。  
- **组合层代价**：2020 年的股票组合研究表明，凯利框架可以提升长期终值，但通常伴随更集中的持仓、更大的波动与更深的 [[drawdown|回撤]]。  

## 与现有知识的关系

- 为 [[kelly-criterion]] 提供较清晰的定义。  
- 与 [[position-sizing]] 是直接上下游关系。  
- 也提醒 [[diversification|分散化]] 的必要性：数学最优不应替代组合层的风险控制。  
- 它与 [[margin-of-safety|安全边际]] 的结合点在于：对输入参数也要保守，而不是只对买价保守。  

## 原文定位

- 提取后的正文保存在 [raw/2026-05-15-investopedia-kelly-criterion.md](../../raw/2026-05-15-investopedia-kelly-criterion.md)。

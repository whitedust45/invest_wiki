---
id: position-sizing
title: 仓位管理 / Position Sizing
type: concept
domain: risk
topic: [position-sizing, diversification, drawdown]
tags: [组合管理, 风险控制, 权重分配]
status: active
version: 2026-05-23
supersedes: []
superseded_by: []
see_also: [kelly-criterion, diversification, margin-of-safety, moat, overview, rebalance, asset-allocation, loss-aversion, drawdown, risk-reward, barbell-strategy, cross-asset-carry-momentum, personal-hybrid-barbell-matrix, ic-im-roll-discount-strategy, 2026-05-15-investopedia-kelly-criterion, 2026-05-21-taleb-barbell-guide, 2026-05-14-thedecisionlab-loss-aversion, 2026-05-14-investor-gov-asset-allocation-guide, kelly-criterion-in-practice, personal-position-sizing-framework, tiered-position-sizing-template]
sources:
  - type: personal
    context: 基于 glossary 定义与当前知识库结构补建风险管理基础概念页
    date: 2026-05-14
  - type: dialogue
    context: 关于凯利公式、回撤与复利效率之间关系的连续讨论
    date: 2026-05-18
    participants: [用户, Agent]
    trigger: 接下来我想跟你讨论一个观点就是对于凯利公式，有一种观点认为在投资市场上做投资，很多时候其实就是混沌的
    rounds: 0
    depth: refined
summary: 仓位管理不仅把判断转成可控风险敞口，也是在控制回撤对长期复利效率的侵蚀
---

# 仓位管理 / Position Sizing

## 定义

[[position-sizing|仓位管理]] 是决定单一投资在整体组合中应占多大比例的方法。它不是简单地“买不买”，而是把研究结论、风险判断和组合约束转化为实际权重分配。

对于 [[momentum|动量]]、[[carry|Carry]] 这类因子暴露，仓位管理尤其重要，因为它们的收益来源会在不同市场状态下表现出完全不同的尾部风险。

## 为什么重要

- 再好的投资想法，如果仓位过大，也可能因为短期波动或判断失误造成致命伤害。  
- 组合长期收益不仅取决于选中了什么，也取决于每个判断押了多大。  
- 明确仓位规则能减少情绪化加仓、摊平或追涨带来的失控决策。  

## [2026-05-18 补充视角] 仓位管理的目标不只是控风险，也是保护复利斜率

**[2026-05-18 via 对话沉淀]** 这轮关于 [[kelly-criterion|凯利公式]] 和 [[drawdown|回撤]] 的讨论，可以把 [[position-sizing|仓位管理]] 的目标再说得更明确一些：仓位管理不只是避免单笔判断失误“亏太多”，更是在控制回撤对长期复利效率的侵蚀。

- 仓位过重时，短期波动和判断误差会被迅速放大，并通过更深的 [[drawdown|回撤]] 拉低后续资金恢复效率。  
- 因此，仓位管理不是单纯在回答“我有多看好这笔机会”，而是在回答“这笔机会值得我拿多少资本效率去交换”。  
- 从长期资金曲线角度看，好的仓位管理会主动牺牲一部分表面的上涨弹性，换取更稳定的复利斜率、更低的深回撤概率，以及更高的系统可执行性。  
- 这也是为什么同样的研究质量，仓位纪律不同，最终账户结果会差很多：研究决定你押什么，[[position-sizing|仓位管理]] 决定你能不能把优势真正保留下来。  

如果想看这一点背后的公式化表达，可回到 [[kelly-criterion]]；如果想看仓位失控之后为什么会以非线性方式伤害资金曲线，则继续看 [[drawdown]]。  

## 决定仓位时常看的维度

- 对企业质量和判断把握的信心程度  
- 下行风险与可能的永久性损失  
- 与其他持仓的相关性  
- 流动性、波动性与组合整体回撤承受能力  

## 与现有知识的关系

- [[position-sizing|仓位管理]] 与 [[diversification|分散化]] 共同决定组合风险结构。  
- [[margin-of-safety|安全边际]] 越充分，通常越能支持更高权重，但并不意味着可以无限放大仓位。  
- 如果一家企业同时具备较强 [[moat|护城河]] 和较清晰的价值判断，仓位上限才更有讨论意义。  
- [[kelly-criterion|凯利公式]] 则提供了一种把胜率和盈亏比转成仓位建议的公式化方法。  
- 在组合层面，它还要与 [[asset-allocation|资产配置]]、[[rebalance|再平衡]]、[[loss-aversion|损失厌恶]]、[[drawdown|回撤]] 与 [[risk-reward|风险收益比]] 一起理解。  
- 在 [[barbell-strategy|杠铃策略]] 中，仓位管理尤其关键：右端高凸性暴露必须足够小，才能保证连续亏损时不破坏左端保命结构。  

## 当前知识库中的用法

这一页用于沉淀“多大仓位才配得上多大把握”的组合纪律。后续组合页若记录具体持仓，应说明初始仓位、加减仓条件，以及单一头寸的上限，而不是只记录标的名称。

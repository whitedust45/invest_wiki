---
id: risk-reward
title: 风险收益比 / Risk-Reward Ratio
type: concept
domain: risk
topic: [risk-reward, position-sizing, drawdown]
tags: [盈亏比, 赔率, 交易框架]
status: active
version: 2026-05-23
supersedes: []
superseded_by: []
see_also: [position-sizing, drawdown, kelly-criterion, convexity, antifragile, barbell-strategy, overview]
sources:
  - type: personal
    context: 基于 glossary 与全库 lint 清理补建风险评估基础概念页
    date: 2026-05-15
  - type: personal
    context: 基于 Taleb 主线补充“静态赔率 vs 凸性”的桥接视角
    date: 2026-05-23
summary: 风险收益比衡量一笔机会的潜在收益相对潜在损失是否划算，是仓位与执行纪律的重要输入
---

# 风险收益比 / Risk-Reward Ratio

## 定义

[[risk-reward|风险收益比]] 指一笔投资或交易中，潜在可获得收益与潜在可能损失之间的比例关系。它关注的不是“会不会赚钱”，而是“值不值得下注这么多”。

## 为什么重要

- [[risk-reward|风险收益比]] 能帮助把模糊的主观判断转成更可比较的机会评估。  
- 即使胜率不高，只要赔率足够好，机会仍可能值得考虑。  
- 它常与 [[position-sizing|仓位管理]] 和 [[drawdown|回撤]] 控制一起使用。  

## [2026-05-23 补充视角] 静态赔率与凸性不是一回事

**[2026-05-23 补充]** [[risk-reward|风险收益比]] 更像是在某个时点对“潜在赚多少 / 潜在亏多少”做静态截图；而 [[convexity|凸性]] 关心的是：当冲击真正放大时，这个赚亏结构会不会发生非线性变化。

- 一个机会即使纸面上是 `3:1`，也可能在极端情景下暴露出很差的负凸性：平时看起来赔率不错，真正出事时亏损却被迅速放大。  
- 反过来，一些表面上平时胜率低、频繁小亏的结构，之所以仍值得存在，恰恰是因为它们具备正 [[convexity|凸性]]：极端情景来临时，收益放大得远快于平时损耗。  
- 因此，[[risk-reward|风险收益比]] 更适合作为**第一层筛选**：先看这笔机会静态上划不划算；而 [[convexity|凸性]] 更适合作为**第二层筛选**：再看这笔机会在尾部情景里会不会变形。  

## 与现有知识的关系

- [[position-sizing|仓位管理]] 要把风险收益比、胜率和相关性一起转成实际权重。  
- [[kelly-criterion|凯利公式]] 是把赔率和胜率进一步公式化的一种做法。  
- [[convexity|凸性]] 则把“赔率”从静态比例推进到分布形状：不仅看赚亏比是多少，还看它会不会在极端情景中非线性放大。  
- [[antifragile|反脆弱]] 进一步追问：这个收益结构是否能在波动和混乱上升时变得更有利，而不是只在正常情景中看起来划算。  
- [[barbell-strategy|杠铃策略]] 则把这个问题再往上提一层：不是只比较单笔机会划不划算，而是先把整体暴露结构设计成能活下来、能等到少数大赔率事件。  
- 若下行空间判断失真，再漂亮的风险收益比也只是错觉。  

## 使用提醒

- 风险收益比只是输入，不是完整决策本身。  
- 低概率高赔率的机会，依然要考虑执行成本、时间成本和心理承受能力。  
- 最好的风险收益比，也不值得用会导致致命回撤的仓位去赌。  

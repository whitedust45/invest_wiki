---
id: drawdown
title: 回撤 / Drawdown
type: concept
domain: risk
topic: [drawdown, position-sizing, diversification]
tags: [最大回撤, 风险控制, 净值波动]
status: active
version: 2026-05-23
supersedes: []
superseded_by: []
see_also: [position-sizing, diversification, kelly-criterion, risk-reward, antifragile, convexity, barbell-strategy, personal-hybrid-barbell-matrix, overview, ic-im-roll-discount-strategy, 2026-05-21-taleb-barbell-guide]
sources:
  - type: personal
    context: 基于 glossary 与全库 lint 清理补建风险管理基础概念页
    date: 2026-05-15
  - type: dialogue
    context: 关于凯利公式、对数增长与回撤如何伤害复利效率的连续讨论
    date: 2026-05-18
    participants: [用户, Agent]
    trigger: 接下来我想跟你讨论一个观点就是对于凯利公式，有一种观点认为在投资市场上做投资，很多时候其实就是混沌的
    rounds: 0
    depth: refined
summary: 回撤不仅衡量最坏阶段会亏多少，也衡量复利效率会被破坏到什么程度
---

# 回撤 / Drawdown

## 定义

[[drawdown|回撤]] 指组合、策略或单个资产的净值，从某个阶段高点下跌到后续低点的幅度。它反映的不是长期收益，而是“中途最难熬的时候会有多痛”。

## 为什么重要

- [[drawdown|回撤]] 往往比年化收益更直接地决定投资者能否坚持执行。  
- 同样的收益率，若回撤显著更深，策略的可持有性通常会更差。  
- 它是连接 [[position-sizing|仓位管理]]、[[diversification|分散化]] 与心理承受能力的关键指标。  

## [2026-05-18 补充视角] 回撤不是纯心理问题，而是复利效率问题

**[2026-05-18 via 对话沉淀]** 这轮关于 [[kelly-criterion|凯利公式]] 的讨论，最值得补到 [[drawdown|回撤]] 页里的一个判断是：回撤之所以重要，不只是因为它“难熬”，更因为它会直接伤害长期复利。

- 财富增长是乘法过程，而不是加法过程。净值一旦从高点出现较深回撤，后续恢复所需的涨幅会以非线性的方式上升。  
- 例如，回撤 10% 需要约 11.1% 才能回本；回撤 20% 需要 25%；回撤 50% 则需要 100%。因此，大回撤不是简单的“账面波动更大”，而是把后续复利斜率压得更低。  
- 这也是为什么在 [[kelly-criterion|凯利公式]] 框架里，过度下注会显著恶化长期增长：仓位过大不仅放大短期波动，更会通过更深的回撤吞噬几何增长率。  
- 从这个角度看，[[drawdown|回撤]] 不只是心理承受能力指标，也是**资本效率指标**：如果一个策略为了多拿一点理论收益，却付出了过深的回撤，它在长期复利上的真实表现可能反而更差。  

### 一个更实用的理解框架

在实盘里，可以把 [[drawdown|回撤]] 理解成三层问题：

1. **痛苦层**：我主观上能不能扛住？  
2. **纪律层**：这样的回撤会不会让我中途破坏系统？  
3. **复利层**：这次回撤会不会让后续资金恢复所需的时间与收益要求变得过高？  

如果一个仓位方案只在第一层上“勉强能忍”，却在第三层上严重伤害复利，那么它依然不是好方案。

如果想继续看“哪些动作最直接决定回撤会不会失控”，优先接着读 [[position-sizing]]；如果想理解背后的公式化仓位逻辑，则回到 [[kelly-criterion]]。  

## 与现有知识的关系

- [[position-sizing|仓位管理]] 直接影响单一判断出错时会造成多深回撤。  
- [[diversification|分散化]] 常被用来降低组合层面的极端回撤。  
- [[kelly-criterion|凯利公式]] 若使用过激，最常见的副作用就是回撤被迅速放大。  
- 若一笔机会的 [[risk-reward|风险收益比]] 看起来很好，却需要承受自己无法忍受的回撤，它在现实中依然不可执行。  
- 在 Taleb 主线里，[[antifragile|反脆弱]]、[[convexity|凸性]] 与 [[barbell-strategy|杠铃策略]] 都可以看成是围绕“避免致命回撤，并在极端事件中保留收益弹性”的结构设计。  

## 使用提醒

- 只看最大回撤还不够，还应看回撤持续时间和修复速度。  
- 不同资产与策略的回撤分布差异很大，不能机械横比。  
- 真正可执行的投资框架，必须让回撤处在自己能承受的范围内。  

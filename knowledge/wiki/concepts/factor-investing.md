---
id: factor-investing
title: 因子投资 / Factor Investing
type: concept
domain: methodology
topic: [factor-investing, momentum, carry, value-investing]
tags: [因子, 量化, 横截面, 收益来源]
status: active
version: 2026-06-04
supersedes: []
superseded_by: []
see_also: [momentum, carry, value-investing, mean-reversion, diversification, cross-asset-carry-momentum, ic-im-roll-discount-strategy, overview]
sources:
  - type: dialogue
    context: 系统学习动量与 carry 两个因子时引出的“什么是因子”总纲
    date: 2026-06-04
    participants: [用户, Agent]
    trigger: 我最近又学了两个新的因子：动量和carry，我希望详细学习一下这两个因子
    rounds: 1
    depth: refined
  - type: article
    title: Value and Momentum Everywhere
    author: Asness, Moskowitz, Pedersen
    publisher: Journal of Finance
    date: 2013
summary: 因子是可被定价的横截面/时序收益来源，判定看持续性、普适性、稳健性与可解释性
---

# 因子投资 / Factor Investing

## 定义

[[factor-investing|因子]] 不是某个具体策略，而是一种**可被系统性捕捉、可被定价的收益来源**。因子投资就是把收益拆解到若干个这样的来源上，再有意识地去暴露或规避它们。

## 判定一个东西算不算“因子”

常用四把尺子：

1. **持续性**：长期、跨时段都能观察到。
2. **普适性**：不只在一个国家、一种资产里成立。
3. **稳健性**：换不同定义、参数仍然有效。
4. **可解释性**：有风险补偿故事或行为金融故事。

## 几个代表性因子

- [[value-investing|价值]]：买便宜（低估值）的资产。
- [[momentum|动量]]：买过去强的、卖过去弱的。
- [[carry|Carry]]：买高票息/高利差的资产。
- 质量、低波动、规模等。

其中 [[momentum|动量]] 与 [[carry|Carry]] 是 AQR、Asness 等人反复在“跨资产”维度实证过的两个代表（见 *Value and Momentum Everywhere*、*Carry*）。

## 为什么要组合多个因子

不同因子之间相关性低，甚至负相关：

- [[value-investing|价值]] 与 [[momentum|动量]] 长期负相关，所以经典做法是两者同时配。
- [[carry|Carry]] 与 [[momentum|动量]] 互补：平稳期靠 carry 吃票息，趋势反转期靠动量避险。

通过 [[diversification|分散]] 多个低相关因子，组合的夏普比通常显著高于任何单一因子。因子组合也常和 [[mean-reversion|均值回归]] 思路混用：价值偏离需要回归过程兑现，动量则处理趋势仍在延续的阶段。

## 与价值投资框架的关系

因子学派把 [[value-investing|价值投资]] 也视为“价值因子”的系统化、横截面版本。区别在于：

- 价值投资偏**自下而上**研究单个企业的内在价值与 [[margin-of-safety|安全边际]]。
- 因子投资偏**横截面规则化**地暴露某个特征，不深究单个标的的故事。

两者不冲突，可视为同一收益来源的不同实现粒度。

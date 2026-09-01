---
id: elasticity-gap
title: 弹性差 / Elasticity Gap
type: concept
domain: risk
topic: [elasticity-gap, mean-reversion, position-sizing]
tags: [龟龟投资法, 仓位管理, 波动率, 市场位置]
status: active
version: 2026-07-05
supersedes: []
superseded_by: []
see_also: [guigu-position-management-framework, position-sizing, mean-reversion, diversification]
sources:
  - type: personal
    context: 用户提供的龟龟投资法仓位管理1截图素材，详见来源页 2026-07-05-guigu-position-management-elasticity-gap
    date: 2026-07-05
summary: 弹性差是在不同市场位置切换高低波动资产，以利用均值回归放大收益或减少损失
---

# 弹性差 / Elasticity Gap

## 定义

弹性差是龟龟仓位管理中的一个执行概念：在市场偏低时提高组合弹性，在市场偏高时降低组合弹性，从 [[mean-reversion|均值回归]] 中利用不同资产波动率带来的收益差或损失缓冲。

## 基本逻辑

市场低位时，高波动、高弹性股票在回归过程中可能放大收益；市场高位时，低波动、现金流稳定或类固收资产能减少回撤。

因此仓位调整不是简单“加仓或减仓”，而是先调整组合弹性：

```text
市场低位：低弹性 -> 高弹性
市场高位：高弹性 -> 低弹性 -> 现金 / 固收
```

## 与仓位管理的关系

[[guigu-position-management-framework]] 将弹性差放在四条原则的第一层：市场位置先决定组合弹性方向，随后才由商业模式、价格和同源风险决定具体个股仓位。

这与 [[position-sizing]] 的传统风险控制互补：传统仓位管理强调“不亏致命”，弹性差强调“在合适市场位置让组合承担合适弹性”。

## 边界

弹性差不能替代基本面判断。低位提高弹性仍然要通过商业模式和价格检查；高位降低弹性也不等于机械清仓，而是优先从高波动资产切换到更稳的优质资产。

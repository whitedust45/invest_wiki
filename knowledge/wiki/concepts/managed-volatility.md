---
id: managed-volatility
title: 管理波动率 / Managed Volatility
type: concept
domain: strategy
topic: [managed-volatility, position-sizing, drawdown, risk-reward]
tags: [vol-targeting, volatility timing, dynamic leverage, leveraged ETF, vovo]
status: active
version: 2026-07-02
supersedes: []
superseded_by: []
see_also: [position-sizing, drawdown, risk-reward, momentum, qld-qqq-120ma-tactical-strategy, cross-asset-carry-momentum]
sources:
  - type: article
    title: Alpha Generation and Risk Smoothing using Managed Volatility
    author: Tony Cooper
    url: https://financialfactory.com/wp-content/uploads/2014/01/alphagenerationandrisksmoothingusingmanagedvolatility.pdf
    publisher: Double-Digit Numerics
    date: 2010-08-06
summary: 管理波动率用预测波动率动态缩放风险暴露，低波动时提高杠杆、高波动时降杠杆以平滑回撤
---

# 管理波动率 / Managed Volatility

## 定义

[[managed-volatility|管理波动率]] 是一种组合风险覆盖层：先预测未来短期波动率，再按目标风险水平动态调整仓位或杠杆。它不直接预测市场方向，而是把“波动率更可预测”转化为 [[position-sizing|仓位管理]] 规则。

核心动作很简单：

- 低波动阶段：允许更高风险暴露或更高杠杆；
- 高波动阶段：自动降仓或降杠杆；
- 目标：在不显著提高组合目标波动的前提下，减少波动率拖累、峰度和 [[drawdown|回撤]]。

## 核心机制

Tony Cooper 在 [[2010-08-06-alpha-generation-managed-volatility]] 中把杠杆 ETF 的长期复利收益近似拆成两部分：

- 杠杆放大的收益项；
- 随杠杆平方增加的波动率拖累项。

这说明固定高杠杆的主要风险不是“杠杆 ETF 天生不能长期持有”，而是当标的进入高波动环境时，波动拖累和深回撤会非线性放大。管理波动率的目标就是在高波动阶段提前降低 `k`，避免落到收益曲线右侧的危险区域。

## 三个代表策略

| 策略 | 杠杆规则 | 优点 | 主要问题 |
|---|---|---|---|
| CVS 恒定波动率策略 | `k = c / sigma` | 目标波动清晰、可解释、杠杆较温和 | 理论上不是最优杠杆 |
| OVS 最优波动率策略 | `k = c / sigma^2` | 更贴近收益/方差最优直觉 | 杠杆变化更剧烈，参数更难提前确定 |
| OVPMS 波动率加均值策略 | `k = c * m(sigma) / sigma^2` | 尝试利用收益与波动率之间的关系 | 更依赖模型与历史校准，过拟合风险更高 |

论文最终偏好 CVS，不是因为它回测收益最高，而是因为它更稳、更容易执行，并且更适合作为产品或个人纪律中的风险目标。

## 与趋势策略的关系

[[momentum|动量/趋势]] 解决“什么时候在场”，[[managed-volatility|管理波动率]] 解决“在场时押多大”。例如 [[qld-qqq-120ma-tactical-strategy]] 用 QQQ 是否站上 120 日均线决定是否持有 QLD，而管理波动率可以进一步决定 QLD 仓位是否满配、半配或暂缓。

因此，两者不是替代关系：

- 均线信号偏方向过滤；
- 波动率覆盖偏仓位缩放；
- 若组合使用杠杆 ETF，最好同时关心趋势、波动率、交易成本和最大仓位上限。

## 使用边界

- 管理波动率不能把熊市变成牛市，只能在高波动阶段减少风险暴露。
- 预测模型、调仓频率、交易成本和税务都会影响实盘结果。
- 若信号微小变化就频繁调仓，会产生过高摩擦，实务上常需要缓冲带或分档执行。
- 对个人组合而言，它更适合做成低频闸门：估值/趋势决定方向，波动率决定仓位强弱。

## 与现有知识的关系

- [[position-sizing]]：管理波动率本质上是把波动率输入转成仓位大小。
- [[drawdown]]：高波动阶段降杠杆的直接目标是控制深回撤和复利效率损失。
- [[risk-reward]]：静态赔率好不代表可以高杠杆，波动率会改变实际风险收益结构。
- [[cross-asset-carry-momentum]]：已有 vol-targeting 思路是管理波动率在因子组合中的应用。

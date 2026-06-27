---
id: carry
title: Carry 因子 / Carry
type: concept
domain: methodology
topic: [carry, factor-investing, momentum]
tags: [因子投资, 跨资产, 利差, 期限结构, 贴水]
status: active
version: 2026-06-04
supersedes: []
superseded_by: []
see_also: [factor-investing, momentum, mean-reversion, cross-asset-carry-momentum, ic-im-roll-discount-strategy, dividend, asset-allocation, overview]
sources:
  - type: dialogue
    context: 系统学习动量与 carry 两个因子
    date: 2026-06-04
    participants: [用户, Agent]
    trigger: 我最近又学了两个新的因子：动量和carry，我希望详细学习一下这两个因子
    rounds: 1
    depth: refined
  - type: article
    title: Carry
    author: Koijen, Moskowitz, Pedersen, Vrugt
    publisher: Journal of Financial Economics
    date: 2018
summary: Carry 是“价格不变时仅靠持有资产就能拿到的收益率”，平稳期吃利差、危机时易大亏，与动量互补
---

# Carry 因子 / Carry

## 定义

[[carry|Carry]] 指**假设价格什么都不变，仅靠持有一项资产本身就能拿到的收益率**。一句口诀：**Carry = 价格不动时，你赚的是什么。**

它把任意资产的总收益拆成两块：

```text
总收益 = Carry（持有票息） + 价格变化（资本利得/损失）
```

正 carry 资产，相当于在赌“价格不要跌得比 carry 还多”。如果 carry 来自期货贴水或利差收敛，它也常隐含某种 [[mean-reversion|均值回归]] 假设：价格、利差或基差最终不会无限偏离。

## 各资产类别里的 Carry

| 资产 | Carry 的具体形式 |
|------|------------------|
| 外汇 (FX) | 高息货币与低息货币的利率差（经典 carry trade，如借日元买澳元） |
| 股票 | [[dividend|股息]] 率 + 预期分红增长（有时用 E/P 作 proxy） |
| 债券 | 期限利差 + 滚动收益（roll-down，沿收益率曲线滑下来的资本利得） |
| 商品 | 期货曲线斜率：现货溢价（backwardation）为正 carry，期货溢价（contango）为负 carry |
| 股指期货 | **基差贴水**即正 carry——见 [[ic-im-roll-discount-strategy|IC/IM 滚贴水策略]] |
| 波动率 | VIX 期限结构斜率 |
| 信用债 | 信用利差 |

## 为什么 Carry 会存在（解释）

- **风险补偿派**：高 carry 资产通常承担尾部风险（FX carry 在危机时暴跌，因为高息货币多为新兴市场）。
- **资金/流动性派**：carry 是流动性提供者收取的“过路费”。
- **跨期套利失败派**：未抛补利率平价（UIP）长期失效，这是 FX carry 长期存在的根本原因。

## 收益与风险特征

- 长期能赚钱，但形态典型是“**捡硬币 in front of a steamroller**”：常年小赚 + 偶尔大亏。
- 与 [[momentum|动量]] 互补：carry 在风平浪静时吃票息，动量在趋势反转时砍仓避险。
- 危机时 carry 一般和股票等风险资产同向下跌，因为它本质上是一个 risk-on 仓位。
- 换手率通常低于动量，对交易成本不敏感。

## 与本知识库已有内容的接口

中证 500 / 1000 股指期货的“贴水”在因子语境里就是**正 carry**：

- 期货价格 < 现货 → 持有到收敛 = 赚到贴水。
- [[ic-im-roll-discount-strategy|IC/IM 滚贴水策略]] 可以重新理解为“中证宽基的 carry 策略”：**收 carry，承担基差扩大的风险**。
- 用 [[margin-of-safety|安全边际]]、[[position-sizing|仓位管理]] 约束的，正是 carry 那段“偶尔大亏”的尾部。

## 使用提醒

- Carry 高 ≠ 划算，要先问“它在补偿什么风险”。
- 跨资产分散收 carry（FX + 商品 + 债券 + 股指）比单一市场更稳。
- 它是“收益来源”而非“择时信号”，应放进 [[factor-investing|因子投资]] 框架与 [[asset-allocation|资产配置]] 一起看。

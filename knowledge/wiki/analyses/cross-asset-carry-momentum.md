---
id: cross-asset-carry-momentum
title: 跨资产 Carry-Momentum 组合 / Cross-Asset Carry-Momentum
type: analysis
domain: methodology
topic: [factor-investing, carry, momentum, diversification, asset-allocation]
tags: [因子组合, vol-targeting, 缓冲带, AQR, 风险平价]
status: active
version: 2026-06-04
effective_date: 2026-06-04
supersedes: []
superseded_by: []
see_also: [factor-investing, carry, momentum, diversification, asset-allocation, ic-im-roll-discount-strategy, position-sizing, kelly-criterion, overview]
sources:
  - type: dialogue
    context: 系统学习动量与 carry 后，展开跨资产 carry-momentum 组合、vol-targeting 与缓冲带
    date: 2026-06-04
    participants: [用户, Agent]
    trigger: 跨资产的carry-momentum
    rounds: 3
    depth: refined
  - type: article
    title: Value and Momentum Everywhere
    author: Asness, Moskowitz, Pedersen
    publisher: Journal of Finance
    date: 2013
  - type: article
    title: Momentum Has Its Moments
    author: Barroso, Santa-Clara
    publisher: Journal of Financial Economics
    date: 2015
summary: 跨资产同时暴露 carry 与 momentum 两个低相关因子，用倒波动加权 + 组合层 vol-targeting 控尾部，并把缓冲带迟滞思想迁移到 IC/IM 离散操作
---

# 跨资产 Carry-Momentum 组合 / Cross-Asset Carry-Momentum

> **定位**：本页是因子组合的构建框架笔记，并把其中可迁移的原则落到个人 [[ic-im-roll-discount-strategy|IC/IM 滚贴水策略]]。框架部分为公开方法论，迁移到 IC/IM 的部分标记为**观点（待实盘验证）**。

## 一、为什么 Carry 与 Momentum 凑一对

二者收益形态镜像互补：

| | [[carry|Carry]] | [[momentum|Momentum]] |
|---|---|---|
| 平稳期（低波动） | 稳吃票息 ✅ | 无趋势、来回挨刀 ❌ |
| 趋势反转 / 危机 | 与风险资产同跌 ❌ | 已砍仓/反手，反而赚 ✅ |
| 信号 | 看当前利差 | 看过去价格 |

Carry 像"卖保险收保费"，Momentum 像"危机时买保险"。叠加后单因子的尾部被部分对冲，组合夏普比通常高于任一单因子。这是 [[factor-investing|因子投资]] + [[diversification|分散]] 的典型体现。

## 二、横跨哪些资产

每个大类里**同时**算两个信号：股指、国债（各国 10Y 期货）、外汇（G10）、商品（原油/铜/农产品/黄金）。"everywhere" 即同一因子在 4 大类、几十个市场反复验证。

这类框架本质上是 [[asset-allocation|资产配置]] 的因子版本：先定义要暴露哪些收益来源，再用 [[position-sizing|仓位管理]] 控制每个来源的风险贡献。若要把信号强弱转成更激进的头寸，也可以参考 [[kelly-criterion|凯利公式]]，但实务上通常会被波动率目标和回撤约束大幅打折。

## 三、组合权重四层

1. **方向**：carry 正→多、负→空；momentum 过去 12-1 月收益正→多、负→空。
2. **倒波动加权**（risk parity 灵魂）：$w_i \propto \text{信号}_i / \sigma_i$，让各头寸风险贡献相等，避免商品波动淹没债券。
3. **组合层 vol-targeting**：把整组合缩放到固定目标年化波动（如 10%），波动升高自动降杠杆——削减尾部的关键。
4. **两因子配比**（可选）：默认 carry / momentum 各占一半风险预算（ERC）；动态调整易过拟合，等权已很强。

## 四、Vol-Targeting：体系的安全带

$$
\text{敞口缩放系数} = \frac{\sigma_{\text{target}}}{\hat\sigma_{\text{当前}}}
$$

- **σ 估计**：滚动 std（慢、有鬼影）< EWMA（主流，λ≈0.94）< GARCH（精细但易过拟合）。实务首选 EWMA。
- **为何救命**：① 波动聚集——今天波动飙升预示明天仍高，提前降杠杆；② 打断"危机→波动暴涨→硬扛→爆"的正反馈。Barroso & Santa-Clara (2015) 证明给动量加 vol-targeting 可显著提升夏普、降低最大回撤。
- **代价**：高波动但反弹初期会踏空；换手成本高；对跳空式黑天鹅反应滞后；目标波动是主观风险偏好参数。

## 五、缓冲带降换手

解决 vol-targeting"信号微动→仓位天天调"的副作用。核心：**偏离超过带宽才动，带内不动。**

- **目标仓位缓冲带**：偏离目标 $\pm b$ 才交易，且**只调到带边缘而非中心**（少交易关键）。
- **信号迟滞带（hysteresis）**：进出用不同阈值（如波动分位 >80% 进减仓、<65% 才解除），避免单阈值上下抖动反复开关。
- **带宽随成本/波动缩放**：$b \propto (\text{成本}/\text{波动})^{1/3}$，成本越高带越宽。

## 六、迁移到 IC/IM（观点，待实盘验证）

个人是手动、低频、单市场，不该复刻机构连续调仓，而应抽取两条原则离散化：

1. **风险指标抬升就降敞口**（事后闸门，你已有）。
2. **同样估值下高波动少下注**（前瞻闸门，待补）。

具体迁移：
- **前瞻闸门**：中证 500/1000 近 20 日波动率冲到过去一年高分位时，即使风险度未到阈值，也暂停加 IM 并提前预留补资。
- **波动决定加仓尺寸**：保留"PB 决定要不要加"，让"波动决定加多激进"——极低估但极高波动时放慢节奏而非无脑上满 1 手 IM。
- **缓冲带迟滞阈值**：把现有单阈值改为进出不同线，减少临界点反复操作。详见 [[ic-im-roll-discount-strategy]] 的"前瞻闸门与缓冲带"一节。

> 说明：本页含个人推演，不构成投资建议；迁移结论尚未经实盘检验。

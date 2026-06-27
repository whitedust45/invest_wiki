---
id: 2022-06-22-huatai-ic-index-enhancement-whitepaper-part-1
title: 华泰期货 IC 指增白皮书（上篇）/ Huatai IC Index Enhancement Whitepaper Part 1
type: source
domain: strategy
topic: [carry, factor-investing, position-sizing, rebalance]
tags: [华泰期货, 股指期货, IC, 中证500, 贴水, 指数增强]
status: active
version: 2026-06-23
effective_date: 2022-06-22
supersedes: []
superseded_by: []
see_also: [ic-im-roll-discount-strategy]
sources:
  - type: article
    title: "IC指增白皮书（上篇）"
    author: 高天越
    url: https://www.7hcn.com/article/424370-1.html
    publisher: 七禾网
    original_publisher: 华泰期货
    date: 2022-06-22
summary: 华泰期货用 Q&A 方式系统解释 IC 吃贴水的机制、基差来源、主要风险与建仓展期框架
---

# 华泰期货 IC 指增白皮书（上篇）

## 摘要

这篇文章系统化解释如何利用中证 500 股指期货 IC 的长期贴水构建指数增强。上篇重点在策略原理与交易细节：为什么多 IC 可能获得相对中证 500 指数的增强收益，为什么到期基差会收敛，基差长期存在的逻辑是什么，以及初次建仓、展期、分红处理、资金预留等执行问题。

## 关键论点

- IC 吃贴水的本质不是无风险套利，而是在持有中证 500 beta 的同时，承接对冲需求带来的负基差补偿。  
- 白皮书将“基差”定义为期货价格减现货指数价格，因此 IC 贴水时基差通常为负；“基差走扩”指折价加强，“基差缩窄”指折价减弱。  
- 到期收敛来自中金所现金交割制度，但交割结算价使用最后交易日最后 2 小时均价，因此到期日收盘价不必然等于指数收盘价；文章建议从交易连续性角度在周五上午前完成展期。  
- 年化基差率可用自然日或交易日折年，文章采用自然日口径；用于比较合约时，需要注意分红预期对不同月份合约基差的影响。  
- 建仓与展期有两类思路：简单机械法偏向当月/当季合约；增强法根据年化基差水平在近远月之间切换，利用基差均值回归，但执行复杂度和流动性要求更高。  
- 主要风险不是“贴水不收敛”本身，而是指数大幅下跌、基差波动/消失、保证金与交易执行细节。  

## 与现有知识的关系

- 支撑 [[ic-im-roll-discount-strategy]] 中“贴水是增强项，不是上杠杆主理由”的定位。  
- 强化 [[carry]] 和 [[factor-investing]] 视角：IC 贴水可以被理解为承担特定风险后的正 carry，但 carry 收益不消除 beta 回撤与保证金风险。  
- 对 [[rebalance]] 和 [[position-sizing]] 的贡献在于：展期规则、资金预留和合约选择都必须服务于“不被迫离场”。  

## 需注意的口径

- 本文发表于 2022-06-22，核心机制仍有参考价值，但具体贴水水平、手续费、保证金、限仓和流动性状态需要用当前数据重算。  
- 该页保存的是可访问网页副本，不等同于华泰期货官网 PDF 原件；来源链为七禾网转载，原始来源标注为华泰期货。  

## 原文定位

- 本地网页副本：[raw/2022-06-22-7hcn-ic-zeng-bai-pi-shu-shang.html](../../raw/2022-06-22-7hcn-ic-zeng-bai-pi-shu-shang.html)  
- 在线来源：[七禾网：IC指增白皮书（上篇）](https://www.7hcn.com/article/424370-1.html)  

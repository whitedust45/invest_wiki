---
id: 2026-07-11-bigquant-steady-momentum
title: 缓步上涨选股策略 / BigQuant Steady Momentum
type: source
domain: strategy
topic: [momentum, kelly-criterion]
tags: [BigQuant, 低波动, 尾盘改造]
status: active
version: 2026-07-11
effective_date: 2026-04-01
supersedes: []
superseded_by: []
see_also: [a-share-tail-short-term-strategy-suite, momentum, kelly-criterion]
sources:
  - type: article
    title: 缓步上涨选股策略
    author: bq93t66l
    publisher: BigQuant
    url: https://bigquant.com/wiki/doc/ONzADZLe0z
    date: 2026-04-01
summary: BigQuant 的稳定上涨低波动策略披露胜率62.09%、盈亏比1.81，原始口径与本地尾盘Top3改造不同
---

# 缓步上涨选股策略

## 来源事实

页面披露：原策略使用连续上涨、低换手和低平均振幅筛选，2021 年至页面更新时累计收益 269.85%，胜率 62.09%，盈亏比 1.81，原始持股数为 5、调仓周期为 20 日。

按 `f = p - (1-p)/b`，`p=0.6209`、`b=1.81`，全凯利约为 `41.15%`。

## 本地改造

[[a-share-tail-short-term-strategy-suite]] 保留低波动趋势思路，改为尾盘信号、最多 3 只、-4% 止损、+8% 止盈及最长 10 日。上述网页指标不适用于改造后的版本；改造版本待 Windows + 通达信复测。

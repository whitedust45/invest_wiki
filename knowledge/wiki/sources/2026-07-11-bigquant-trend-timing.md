---
id: 2026-07-11-bigquant-trend-timing
title: 日频趋势跟踪与市场择时 / BigQuant Trend Timing
type: source
domain: strategy
topic: [momentum, kelly-criterion]
tags: [BigQuant, EMA, MACD, RSI, 市场择时]
status: active
version: 2026-07-11
effective_date: 2026-07-11
supersedes: []
superseded_by: []
see_also: [a-share-tail-short-term-strategy-suite, momentum, kelly-criterion]
sources:
  - type: article
    title: 日频趋势跟踪量化策略设计与回测
    publisher: BigQuant Cowork
    url: https://bigquant.com/cowork/chat/share/ed7226b5-2e98-49b9-8260-dc2fc5f73d87
    date: 2026-07-11
summary: BigQuant 页面披露含市场择时趋势策略胜率50.37%、盈亏比2.32，但原始Top10和较长持有期不同于本地改造
---

# 日频趋势跟踪与市场择时

## 来源事实

页面使用市场多头过滤、EMA20/60、MACD、RSI 与布林中轨；其披露的 2024 年至今样本胜率为 50.37%、盈亏比为 2.32。

按 `f = p - (1-p)/b`，全凯利约为 `28.99%`。

## 本地改造

[[a-share-tail-short-term-strategy-suite]] 改为尾盘买入、最多 3 只、-5% 止损、+10% 止盈、MACD 转弱或最多 10 日退出。来源原始持仓数和持有期不同，因此不把其统计外推为本地版本结果。

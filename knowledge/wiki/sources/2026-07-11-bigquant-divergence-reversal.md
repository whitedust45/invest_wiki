---
id: 2026-07-11-bigquant-divergence-reversal
title: 周期与背驰趋势反转 / BigQuant Divergence Reversal
type: source
domain: strategy
topic: [mean-reversion, momentum, kelly-criterion]
tags: [BigQuant, MACD背驰, 市场择时, 中证800]
status: active
version: 2026-07-11
effective_date: 2020-03-03
supersedes: []
superseded_by: []
see_also: [a-share-tail-short-term-strategy-suite, mean-reversion, kelly-criterion]
sources:
  - type: article
    title: 黑科技选股系列之一：基于周期与背驰的趋势反转策略
    author: 杨勇
    publisher: 安信证券研究中心（BigQuant 页面摘要）
    url: https://bigquant.com/square/paper/1053c0ae-581d-418a-843d-e0eb5bf3478e
    date: 2020-03-03
summary: 周期与MACD背驰反转来源披露胜率69.82%、盈亏比1.65，原始中证800与15日出场不等于本地尾盘Top3改造
---

# 周期与背驰趋势反转

## 来源事实

来源以周期分析过滤系统风险，并以价格低点下移而 MACD 背驰确认反转；页面披露 15 日固定出场策略胜率 69.82%、盈亏比 1.65。

按 `f = p - (1-p)/b`，全凯利约为 `51.53%`。

## 本地改造

[[a-share-tail-short-term-strategy-suite]] 使用日线局部低点和 DIF 的可复现近似，改为尾盘买入、最多 3 只、-5% 止损、+8% 止盈、MA5 跌破或最多 10 日退出。改造版本待 Windows + 通达信复测。

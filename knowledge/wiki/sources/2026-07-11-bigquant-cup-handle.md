---
id: 2026-07-11-bigquant-cup-handle
title: 杯柄形态识别与交易 / BigQuant Cup Handle
type: source
domain: strategy
topic: [momentum, kelly-criterion]
tags: [BigQuant, 杯柄突破, 量价形态]
status: active
version: 2026-07-11
effective_date: 2018-09-04
supersedes: []
superseded_by: []
see_also: [a-share-tail-short-term-strategy-suite, momentum, kelly-criterion]
sources:
  - type: article
    title: 基于杯柄形态的识别与交易探索
    publisher: 华创证券（BigQuant 页面转载）
    url: https://bigquant.com/wiki/doc/zp4T0qT2RP
    date: 2018-09-04
summary: 杯柄突破来源披露突破后持有30日胜率53.89%、盈亏比1.51，本地尾盘Top3和最长10日改造尚待复测
---

# 杯柄形态识别与交易

## 来源事实

来源以先涨、回撤成杯、杯上半部短柄缩量和突破为核心。其 2009 年 4 月至 2018 年 8 月统计中，突破后持有 30 个交易日的胜率为 53.89%、盈亏比为 1.51。

按 `f = p - (1-p)/b`，全凯利约为 `23.35%`。

## 本地改造

[[a-share-tail-short-term-strategy-suite]] 只用通达信日线 OHLCV 近似识别杯柄，并限制尾盘买入、最多 3 只、-6% 止损、+12% 止盈、跌破柄部低点或最多 10 日退出。原始 30 日期限与改造不同，网页统计不可外推。

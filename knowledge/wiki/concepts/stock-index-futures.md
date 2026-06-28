---
id: stock-index-futures
title: 股指期货 / Stock Index Futures
type: concept
domain: risk
topic: [stock-index-futures, position-sizing]
tags: [衍生品, 杠杆, 保证金, IC, IM]
status: active
version: 2026-06-27
effective_date: 2026-06-27
supersedes: []
superseded_by: []
see_also: [stock-index-futures-contract-rules, ic-im-roll-discount-strategy]
sources:
  - type: article
    title: 中证500股指期货合约表
    url: http://www.cffex.com.cn/cn/zz500.html
    publisher: 中国金融期货交易所
    date: 2026-06-27
  - type: article
    title: 中证1000股指期货合约表
    url: http://www.cffex.com/cn/zz1000.html
    publisher: 中国金融期货交易所
    date: 2026-06-27
summary: 股指期货以股票指数为标的，通过保证金交易放大名义敞口，核心风险在杠杆和移仓
---

# 股指期货 / Stock Index Futures

股指期货是以股票指数为标的的期货合约。它不直接买入一篮子股票，而是通过合约乘数和保证金制度获得指数名义敞口，因此需要和 [[position-sizing|仓位管理]]、现金池、保证金风险度一起看。

在本知识库里，股指期货主要用于理解 [[ic-im-roll-discount-strategy|IC/IM 滚贴水长期持有策略]]。具体到 IC/IM 的合约乘数、到期月份、最后交易日、交割日和仪表盘计算口径，见 [[stock-index-futures-contract-rules]]。

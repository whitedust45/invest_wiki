---
id: a-share-tail-short-term-strategy-suite
title: A股尾盘短线四策略系统 / A-share Tail Short-term Strategy Suite
type: portfolio
domain: strategy
topic: [momentum, mean-reversion, kelly-criterion, position-sizing, drawdown]
tags: [A股, 尾盘买入, 通达信, 短线, 最多三只]
status: draft
version: 2026-07-11
effective_date: 2026-07-11
supersedes: []
superseded_by: []
see_also: [short-term-momentum-brick-indicator-system, kelly-criterion-in-practice, 2026-07-11-bigquant-steady-momentum, 2026-07-11-bigquant-trend-timing, 2026-07-11-bigquant-divergence-reversal, 2026-07-11-bigquant-cup-handle]
sources:
  - type: article
    title: 缓步上涨选股策略
    publisher: BigQuant
    url: https://bigquant.com/wiki/doc/ONzADZLe0z
    date: 2026-04-01
  - type: article
    title: 日频趋势跟踪量化策略设计与回测
    publisher: BigQuant Cowork
    url: https://bigquant.com/cowork/chat/share/ed7226b5-2e98-49b9-8260-dc2fc5f73d87
    date: 2026-07-11
  - type: article
    title: 黑科技选股系列之一：基于周期与背驰的趋势反转策略
    publisher: BigQuant
    url: https://bigquant.com/square/paper/1053c0ae-581d-418a-843d-e0eb5bf3478e
    date: 2020-03-03
  - type: article
    title: 基于杯柄形态的识别与交易探索
    publisher: BigQuant
    url: https://bigquant.com/wiki/doc/zp4T0qT2RP
    date: 2018-09-04
summary: 四个来源策略统一改造成尾盘买入、最多三只和最长十日的A股日线系统，待Windows通达信复测
---

# A股尾盘短线四策略系统

> 状态：`draft`。本页是代码实现与来源口径的执行说明，不是投资建议；本机未运行真实通达信回测。

## 共同约束

- 用户提供可扫描的股票池，实际持仓最多 3 只，等权补足。
- 信号日在尾盘形成，以收盘价近似成交；A 股 T+1，次日起可按退出规则卖出。
- 每个策略最长持有 10 个交易日；费用默认佣金双边万分之一、卖出印花税万分之五、单边滑点万分之五，均可由 CLI 覆盖。
- 同一日止盈和止损同时触及时，日线回放按止损优先。

## 策略清单

| ID | 入场逻辑 | 退出 |
| --- | --- | --- |
| `steady_momentum` | 10日稳定上涨、短均线多头、低振幅和稳定成交额 | -4%、+8%、跌破MA5或10日 |
| `trend_confirmation` | 市场MA20>MA60，EMA/MACD/RSI/布林共同确认 | -5%、+10%、MACD转弱或10日 |
| `macd_divergence` | 市场多头，价格新低而DIF抬高并有向上确认 | -5%、+8%、跌破MA5或10日 |
| `cup_handle_breakout` | 上涨后杯柄、柄部缩量、尾盘突破柄部高点 | -6%、+12%、跌破柄部低点或10日 |

## 来源与凯利筛选

- [[2026-07-11-bigquant-steady-momentum]]：来源全凯利约 41.15%。
- [[2026-07-11-bigquant-trend-timing]]：来源全凯利约 28.99%。
- [[2026-07-11-bigquant-divergence-reversal]]：来源全凯利约 51.53%。
- [[2026-07-11-bigquant-cup-handle]]：来源全凯利约 23.35%。

这些是来源页面按其原始股票池、持仓数、期限和交易规则得到的历史统计，**不是**本系统改造后的胜率、盈亏比或凯利。改造后结果必须在 Windows + 通达信上单独回测。

## 代码与运行

- 实时扫描：`modules/short_term/strategies/tail.py`。
- 历史回测：`modules/short_term/backtest_tail.py`。
- 回测产物：`trades.csv`、`report.json`、`report.md`。
- Windows 使用步骤见 [[short-term-momentum-brick-indicator-system]] 的通达信运行边界和 `modules/short_term/README.md`。

## 数据限制

日线无法还原盘中止盈、止损的先后顺序、历史 ST/停牌/退市、历史指数成分和涨跌停制度变化；报告会逐次披露这些偏差，不把来源页指标或本机离线测试当作真实历史回测。

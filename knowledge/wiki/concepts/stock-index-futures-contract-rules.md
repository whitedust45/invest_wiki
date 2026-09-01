---
id: stock-index-futures-contract-rules
title: 股指期货合约规则 / Stock Index Futures Contract Rules
type: concept
domain: risk
topic: [stock-index-futures, position-sizing, carry]
tags: [中金所, IC, IM, 保证金, 移仓]
status: active
version: 2026-06-28
effective_date: 2026-06-27
supersedes: []
superseded_by: []
see_also: [stock-index-futures, ic-im-roll-discount-strategy, personal-hybrid-barbell-execution-dashboard]
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
  - type: dialogue
    context: 仪表盘 IC/IM 持仓分析自审，确认期货账户权益、占用保证金和名义敞口应由流水与日级合约点位推导
    date: 2026-06-28
    participants: [用户, Agent]
summary: IC/IM 合约乘数均为每点200元，最后交易日和交割日为到期月第三个周五并遇假日顺延
---

# 股指期货合约规则 / Stock Index Futures Contract Rules

> 本页记录仪表盘使用的 IC/IM [[stock-index-futures|股指期货]]合约基础规则。它只提供规则事实与计算口径，不构成投资建议。

## 核心事实

| 品种 | 标的 | 合约乘数 | 合约月份 | 最后交易日 / 交割日 | 最低交易保证金 | 交割方式 |
|---|---|---:|---|---|---:|---|
| IC | 中证500指数 | 每点 200 元 | 当月、下月及随后两个季月 | 到期月份第三个周五，遇国家法定假日顺延；交割日期同最后交易日 | 合约价值的 8% | 现金交割 |
| IM | 中证1000指数 | 每点人民币 200 元 | 当月、下月及随后两个季月 | 到期月份第三个星期五，遇国家法定假日顺延；交割日期同最后交易日 | 合约价值的 8% | 现金交割 |

事实来源：中金所 IC 合约表与 IM 合约表，抓取时间 2026-06-27。

## 仪表盘计算口径

### 名义敞口

```text
名义敞口（万元） = 指数点位 × 合约乘数 × 手数 / 10000
```

默认合约乘数取 200 元/点，但页面允许手动改写，以便应对规则变化或特殊记录口径。

### 风险度与杠杆

```text
期货风险度 = 占用保证金 / 期货账户权益
杠杆比例 = 名义敞口 / 期货账户权益
保证金率 = 占用保证金 / 名义敞口
```

[2026-06-28 更正] 仪表盘不再把占用保证金、期货账户权益和名义敞口作为核心参数手动录入；它们应由 IC/IM 流水、开仓保证金率和日级合约点位推导。若占用保证金大于 0 且期货账户权益为 0，仍应视为风险度不可计算且处于极危状态。

## 移仓提醒

仪表盘解析 `IC2607` / `IM2607` 这类合约代码：

1. `IC` / `IM` 识别品种。
2. `26` 识别年份为 2026。
3. `07` 识别到期月份为 7 月。
4. 优先使用估值 JSON 或官方合约信息中的交割日。
5. 若缺少官方日期，则按到期月份第三个周五推算理论交割日，并提示“法定假日顺延需复核”。

实践上，移仓不是单纯日历提醒，还应结合 [[carry|贴水 / carry]]、成交流动性、保证金风险度、[[position-sizing|仓位管理]] 和个人现金池安全垫。

## 与个人框架的关系

本规则页服务于 [[ic-im-roll-discount-strategy]] 和 [[personal-hybrid-barbell-execution-dashboard]]。在个人混合杠铃框架里，IC/IM 是增强仓，不是生存仓；合约规则只解决“怎么算”和“何时到期”，是否开仓仍应由估值、现金池和风险度共同决定。

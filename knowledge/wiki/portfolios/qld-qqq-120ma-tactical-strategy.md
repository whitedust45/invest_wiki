---
id: qld-qqq-120ma-tactical-strategy
title: QLD/QQQ 120 日均线战术策略 / QLD-QQQ 120MA Tactical Strategy
type: portfolio
domain: strategy
topic: [momentum, position-sizing, drawdown, risk-reward]
tags: [QLD, QQQ, Nasdaq-100, 杠杆ETF, 120日均线, 趋势跟踪]
status: draft
version: 2026-06-23
effective_date: 2026-06-23
supersedes: []
superseded_by: []
see_also: [personal-hybrid-barbell-matrix]
sources:
  - type: dialogue
    context: 讨论 QLD 在 QQQ 站上 120 日均线后买入、跌破后卖出的独立战术策略
    date: 2026-06-23
    participants: [用户, Agent]
    trigger: 再帮我补充一个策略，QLD在QQQ站上120日均线之后买入，跌破时卖出的策略
    rounds: 2
    depth: seed
  - type: article
    title: QLD Ultra QQQ
    publisher: ProShares
    url: https://www.proshares.com/our-etfs/leveraged-and-inverse/qld
    date: 2026-06-23
  - type: article
    title: Compounding Effects in Leveraged ETFs: Beyond the Volatility Drag Paradigm
    author: Chung-Han Hsieh, Jow-Ran Chang, Hui Hsiang Chen
    publisher: arXiv
    url: https://arxiv.org/abs/2504.20116
    date: 2025-04-28
  - type: article
    title: Detailed study of a moving average trading rule
    author: Fernando F. Ferreira, A. Christian Silva, Ju-Yi Yen
    publisher: arXiv
    url: https://arxiv.org/abs/1907.00212
    date: 2019-06-29
summary: 用 QQQ 的 120 日均线过滤趋势，只在中期上升趋势中持有 2 倍杠杆 QLD，跌破则退出
---

# QLD/QQQ 120 日均线战术策略

## 定位

本页记录一个独立的 Nasdaq-100 战术策略：用 QQQ 的 120 日均线判断中期趋势，只在趋势向上时持有 QLD，趋势破坏时退出。它不是 [[personal-hybrid-barbell-matrix]] 中 QQQ 长期右尾仓的简单替代，也不是纯买入并持有 QLD / TQQQ，而是一个带趋势过滤的杠杆 ETF 策略。

由于用户提到的原始论文尚未定位，本页暂设为 `draft`。当前只先记录策略规则、理论依据与待验证问题，后续补到原论文后再决定是否升为 `active`。

关联概念：本策略同时依赖 [[momentum|趋势/动量]]、[[position-sizing|仓位管理]]、[[drawdown|回撤控制]] 与 [[risk-reward|风险收益比]]，不能只按历史收益排序来判断优劣。

## 核心规则

| 项目 | 规则 |
|---|---|
| 信号标的 | QQQ |
| 交易标的 | QLD |
| 买入条件 | QQQ 收盘价站上 120 日均线 |
| 卖出条件 | QQQ 收盘价跌破 120 日均线 |
| 执行方式 | 默认次日执行，避免盘中假突破 |
| 空仓资金 | 现金、货币基金或短债类工具 |
| 策略目标 | 在 Nasdaq-100 中期上升趋势中放大收益，同时减少下跌 / 震荡期杠杆损耗 |

## 策略逻辑

QLD 是 ProShares 发行的 2 倍 Nasdaq-100 杠杆 ETF，目标是提供 Nasdaq-100 单日收益的 2 倍。官方说明同时强调，超过单日的持有结果可能显著偏离 2 倍目标，且标的指数的涨跌幅、波动率和持有期都会影响实际结果。

因此，本策略的关键不是长期无条件持有 QLD，而是：

1. 用 QQQ 的 120 日均线过滤中期趋势；
2. 只在趋势向上时承受 QLD 的 2 倍暴露；
3. 在趋势破坏时退出，减少杠杆 ETF 在熊市和震荡市中的路径损耗；
4. 用系统规则替代主观判断，避免在下跌中把战术杠杆误当成长持仓。

这与 [[momentum|动量 / 趋势]] 框架一致：不预测底部和顶部，而是等趋势确认后参与，并在趋势失效时退出。

## 与买入持有 QLD / TQQQ 的区别

| 方案 | 优点 | 主要问题 |
|---|---|---|
| 买入持有 QLD | 能完整吃到 Nasdaq-100 长期牛市的 2 倍弹性 | 熊市和震荡市中回撤大，路径损耗明显 |
| 买入持有 TQQQ | 右尾弹性更强 | 3 倍杠杆对波动和回撤更敏感，深跌后修复难度极高 |
| QQQ 120MA -> QLD | 用趋势过滤减少极端回撤和震荡损耗 | 均线滞后，震荡市会反复止损和踏空 |

## 执行细节（待回测验证）

默认执行口径：

1. 使用 QQQ 的复权收盘价计算 120 日简单移动平均线。  
2. 当 QQQ 收盘价从下方站上 120 日均线，次一交易日买入 QLD。  
3. 当 QQQ 收盘价从上方跌破 120 日均线，次一交易日卖出 QLD。  
4. 空仓时资金进入现金、货币基金或短债类工具，不反手做空。  
5. 不因盘中突破/跌破执行，避免被日内噪音触发。  

待定参数：

- 使用简单移动平均线（SMA）还是指数移动平均线（EMA）。  
- 是否需要 1-3 个交易日确认，减少假突破。  
- 是否设置缓冲带，如站上均线 1% 才买、跌破 1% 才卖。  
- 是否限制 QLD 在总组合中的最大占比。  
- 空仓资金使用现金、短债还是并入 [[personal-hybrid-barbell-matrix|个人混合杠铃矩阵]] 的国债逆回购安全端。  

## 风险与失效信号

- **均线滞后**：120 日均线确认趋势时，市场可能已经涨过一段；跌破时也可能已经出现明显回撤。  
- **震荡反复**：如果 QQQ 围绕 120 日均线上下震荡，策略会反复买卖，产生滑点、税费和心理消耗。  
- **杠杆 ETF 路径依赖**：QLD 的长期收益取决于 Nasdaq-100 的收益路径、波动率和趋势持续性，不等于简单 2 倍长期收益。  
- **极端跳空风险**：跌破信号可能发生在大幅下跌之后，次日卖出无法避免隔夜跳空损失。  
- **过度拟合风险**：120 日参数可能来自历史回测表现，必须避免把单一历史最优参数当作永久规律。  

## 与总组合的关系

本策略应作为独立策略评估，不应自动替代 [[personal-hybrid-barbell-matrix]] 中的 QQQ 右尾仓。若未来将其纳入总组合，需要先明确：

- QLD 策略仓是否从 QQQ 右尾预算中扣除；
- QLD 策略仓的最大组合占比；
- 空仓时资金是否回流到国债逆回购安全端；
- SPY put 左尾保险是否覆盖该策略带来的美股科技杠杆风险；
- 是否会和原有 QQQ 长持仓形成过度同源暴露。

## 待补充

- 用户提到的原始论文标题、作者、链接或 PDF。  
- QLD / TQQQ / QQQ 120 日均线策略的长期回测，包括收益、最大回撤、换手、税费和滑点。  
- 120 日均线与 100 / 150 / 200 日均线的敏感性比较。  
- 与买入持有 QQQ、买入持有 QLD、买入持有 TQQQ 的完整对照。  

## 当前结论

**观点**：如果原始论文和复核回测成立，这个策略的价值不在于“杠杆更高”，而在于用 QQQ 的中期趋势过滤来选择何时承受 QLD 杠杆暴露。它本质上是一个趋势跟踪 + 杠杆 ETF 的战术策略，必须用仓位上限和退出规则约束，不能被误用成长期无条件持有 QLD。

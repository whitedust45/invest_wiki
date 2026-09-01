---
description: 操作日志，按时间倒序记录所有变更
updated: 2026-07-11
---

# 操作日志

> 本文件是结构性日志，不遵循 wiki 页面的完整 frontmatter 规范。
> 格式：`## [YYYY-MM-DD] {操作类型} | {标题}`
> 操作类型：`init` / `ingest` / `query` / `lint` / `update` / `curate` / `think`
> 快速查看：`grep "^## \[" log.md | tail -10`

## [2026-07-11] ingest | A股尾盘短线四策略来源与回测程序

- 新增来源页：[[2026-07-11-bigquant-steady-momentum]]、[[2026-07-11-bigquant-trend-timing]]、[[2026-07-11-bigquant-divergence-reversal]]、[[2026-07-11-bigquant-cup-handle]]，逐页记录公开链接、原始胜率/盈亏比、凯利计算与改造差异。
- 新增 [[a-share-tail-short-term-strategy-suite]]：统一四策略为尾盘入场、最多三只、次日起可操作、最长十日的 A 股日线执行框架。
- 新增 `tail` 策略扫描器和 `backtest-tail` 回测入口；回测默认佣金双边万分之一、卖出印花税万分之五、单边滑点万分之五，报告输出 CSV、JSON、Markdown。
- 口径：来源指标不外推到改造版本；Mac 仅通过纯逻辑测试，真实通达信回测待 Windows 环境运行。

## [2026-07-11] update | 砖型图连续评分与历史回测

- 将候选基础权重调整为砖型 28、黄白趋势 17、前日 J 15、低 J 十字星 5、板块 12、相对强度 10、量能 13，总计 100 分。
- 黄白趋势改为四项连续线性评分；前日 J 以 12 为斜率分水岭连续降分；仅前日实体不超过 1.5% 且 J<12 时固定获得 5 分十字星确认。
- 新增通达信双引擎历史评分回测，可选择起止日期，逐日只统计全局正分 Top 5，重点输出 Top 3 的后续 1、2、3、5 日表现。
- 回测报告明确披露当前板块成分替代历史成分、收盘价近似尾盘成交及历史特殊涨跌停识别限制；待 Windows 通达信历史数据实测。
- 回测新增 `--workers`：仅并行没有 TQ 调用的逐日评分，并用板块反向成员表、日期索引和历史K线窗口减少重复扫描与复制；TQ 取数和原生公式保持单线程。
- Python 严格砖型图的红柱恢复门槛从昨日绿柱的 `2/3` 上调至 `3/4`；砖型分从该门槛的 0 分连续升至比值 `2.0` 的 28 分满分。
- 回测新增独立的“双引擎共振组合”：仅保留同日原生 `ZHUAN` 和 Python 严格信号共同命中的股票，再单独重排 Top 1、Top 3、Top 5，不与双引擎并集组合混算。

## [2026-07-10] query | 通达信砖型图候选排序框架

- 新增 [[tdx-brick-candidate-ranking-framework]]：
  - 明确砖型图只作为入池触发器，后续增加市场状态、板块领导力、个股相对强度、流动性、尾盘结构和组合约束五层判断
  - 形成板块领导力 30%、相对强度 25%、流动性 20%、尾盘结构 15%、砖型图强度 10% 的待回测初始排序草案
  - 固化“硬性否决优先、每板块先留一只、第一名分差不足则降低仓位或放弃”的选择纪律
  - 标记为 `draft`，具体阈值和权重须在日线数据质量验证后，按后续信号日志与次日收益分布校准

## [2026-07-10] update | 砖型图候选排序实现口径确认

- 更新 [[tdx-brick-candidate-ranking-framework]]：
  - 确认不自动定义大盘环境，由用户自行决定当日是否开仓
  - 固定初始权重为板块 25、个股相对强度 20、流动性 15、尾盘结构 15、砖型图强度 25；风险项最多另扣 30 分
  - 确认每板块展示全部公式候选并标记前 3 名；硬过滤失败的候选保留展示但固定为 0 分
  - 确认 14:40 封涨停、数据缺失、严格砖型图不成立及未收复放量阴线为归零条件；放量阴线按 5 日回看、1.5 倍均额和 60% 实体初始口径执行

## [2026-07-10] update | 双引擎并集候选评分

- 更新 [[tdx-brick-candidate-ranking-framework]]：
  - 候选池定义为原生 `ZHUAN` 与 Python 砖型图命中的并集，`shared / native_only / python_only` 仅展示来源，不参与加分或归零
  - 删除“Python 严格砖型图不成立即归零”的口径；日线完整的原生单边候选继续接受其余四项评分
  - 对不满足“昨绿今红”的单边候选，仅将砖型图项记为 0 分，保留其余因子评分和风险提示

## [2026-07-10] update | 通达信候选 Markdown 日报

- 筛选器正常运行直接输出并按日期写入 `data/tdx-brick-selector/reports/YYYY-MM-DD.md`，不再生成正常运行用 JSON。
- 总表按最终分数全局排序；同一股票只留最高评分的一行，合并展示全部命中的概念板块。
- 增加股票名称和板块名称解析，报告列出来源、五项因子、风险扣分和归零原因。
- 主动手工核对工具也只保留 `YYYY-MM-DD.compare.md`，内部临时 JSON 解析后立即删除。

## [2026-07-10] think | A股牛市高弹性仓统一退出框架

- 新增 [[a-share-bull-market-high-beta-exit-framework]]：
  - 将东方财富、恒生电子、赢时胜定位为同一笔牛市高弹性仓，按市场状态而非个股分别退出
  - 确认以 2014 年至今为历史分位数基准；成交、两融、估值和情绪的前 3 项至少满足 2 项时进入高潮观察区
  - 确认最终全卖条件：等权组合创新 60 日高后，连续两日收盘跌破 10 日均线，且第 2 日成交额不低于 20 日均量
  - 标记为 `draft`，待回测统一数据口径、信号误卖成本与回撤规避效果

## [2026-07-10] update | 通达信砖型图定时运行与主动核对

- 新增 Windows PowerShell 运行器和任务计划安装器：工作日 14:40 自动启动或复用 `TdxW.exe`，每 30 秒预检，最晚等待至 14:55；超时不补跑。
- 定时结果与日志按日写入 `data/tdx-brick-selector/`，任务只在当前用户登录时运行，不关闭通达信。
- 新增主动核对脚本：可实际运行双引擎，并将用户从通达信软件导出的选股代码与原生/Python 结果做交集和差集比较。

## [2026-07-10] update | 通达信原生公式标识与参数

- 原生公式默认名更新为 `ZHUAN`，默认参数为 `14,28,57,114,3,21`。
- 原生运行和 `--debug-native` 使用相同的公式参数，避免调试请求与正式筛选请求不一致。

## [2026-07-10] update | 通达信砖型图尾盘筛选器实现

- 记录用户提供的砖型图原始公式，原生通达信选股公式名称为 `砖`。
- 固化 14:40 运行、概念板块涨幅前五、板块成分股取数和原生/Python 双引擎对照口径。
- 新增 `tools/dashboard/tdx_brick_selector.py`，当前仍需在 Windows 通达信客户端实测数据返回和公式结果差异。

## [2026-07-09] update | 高分红白酒分红口径修正与 20 万初始配置草案

- 更新 [[high-dividend-cashflow-watchlist]]：
  - 按巨潮公告修正五粮液、泸州老窖 2025 年中期 + 年度分红口径
  - 明确五粮液 2025 年中期 2.578 元/股、年度预案 2.578 元/股，合计税前 5.156 元/股
  - 明确泸州老窖 2025 年中期 1.358 元/股、年度预案 4.417 元/股，合计税前 5.775 元/股
  - 记录用户 20 万扣除现金垫后的高分红初始配置草案，并标注不是下单指令
- 更新 `apps/dashboard/data/dividend-watchlist-defaults.json`：
  - 修正五粮液、泸州老窖默认分红字段和按 2026-07-09 11:18 行情计算的税前股息率

## [2026-07-08] think | 尾盘绿转红超短动量系统沉淀

- 新增 [[short-term-momentum-brick-indicator-system]]：
  - 将同花顺砖型图指标转化为个人 A 股隔夜超短动量系统草案
  - 记录尾盘绿转红、红柱长度超过昨日绿柱三分之二、强概念板块和强势股过滤、+3% 半仓止盈、尾盘转绿退出、-3% 硬止损等规则
  - 明确单票 33%、最多 3 只、最多 2 只同一主线，以及指数走弱、强板块尾盘回落、炸板率高和强势股亏钱效应明显时不开仓
  - 标记为 `draft`，待后续用回测或实盘日志验证 +3% 触发率、隔夜低开损失、后半仓右尾贡献和状态过滤有效性
- 更新 `knowledge/wiki/index.md`：收录该策略页

## [2026-07-06] update | 新增静态价值型烟蒂选股 Skill

- 新增 `.agents/skills/static-cigar-butt-stock-decision/SKILL.md`：
  - 将静态价值型烟蒂策略落成个股/批量过滤器
  - 明确采用“存量资产垫筛 → 资产质量折价筛 → 正现金流入筛 → 资产兑现逻辑筛”的漏斗模式
  - 输出 `PASS-SPECIAL / PASS-SMALL / WAIT-PRICE / WAIT-DATA / NEAR-T2 / NOT-CIGAR-BUTT / REJECT` 七档
- 新增引用文件：
  - `.agents/skills/static-cigar-butt-stock-decision/references/framework.md`：资产垫天梯、折价规则、正现金流和一票否决条件
  - `.agents/skills/static-cigar-butt-stock-decision/references/templates.md`：单股、批量和年报阅读输出模板
- 更新 `AGENTS.md`：技能清单补入 `/static-cigar-butt-stock-decision`

## [2026-07-05] curate | 拆分 2026-06 操作日志归档

- 新增 [[log-archive-2026-06]]：归档 2026-06 的知识库与仪表盘操作记录
- 主 `wiki/log.md` 只保留 2026-07 活跃记录和历史归档入口，降低 lint 的超长文件提示
- 更新 `knowledge/wiki/index.md`：收录 2026-06 操作日志归档页

## [2026-07-05] ingest | 静态价值型烟蒂投资框架截图吸纳

- 整理 raw 图片：
  - `knowledge/raw/assets/static-value-cigar-butt-framework/asset-cushion/`：保留 7 张“因子一：存量资产垫”截图，统一命名为 `static-cigar-butt-factor-01-asset-cushion-XX.png`
  - `knowledge/raw/assets/static-value-cigar-butt-framework/positive-cash-inflow/`：保留 5 张“因子二：正现金流入”截图，统一命名为 `static-cigar-butt-factor-02-positive-cash-inflow-XX.png`
- 新增来源页：
  - [[2026-07-05-static-cigar-butt-factor-asset-cushion]]：逐图摘录存量资产垫、T0/T1/T2 资产垫天梯和案例口径
  - [[2026-07-05-static-cigar-butt-factor-positive-cash-inflow]]：逐图摘录正现金流入、资产垫防消耗逻辑和正反案例
- 新增方法页：
  - [[static-value-cigar-butt-framework]]：将两组素材吸纳为独立的静态价值型烟蒂投资框架
  - [[cigar-butt-investing]]：补建烟蒂投资上位概念，承接格雷厄姆式静态资产折价路径
  - [[asset-cushion]]：沉淀存量资产垫概念，强调可变现资产扣除真实负债后的静态安全边际
  - [[positive-cash-inflow]]：沉淀正现金流入概念，强调等待资产兑现期间不能继续消耗资产垫
- 更新现有页面：
  - [[value-investing]]、[[margin-of-safety]]、[[intrinsic-value]]：补充格雷厄姆式烟蒂路径和资产负债表安全边际
  - [[distributable-cash-balance]]：区分高分红现金流仓的可支配现金结余与烟蒂策略的正现金流入
  - [[high-dividend-cashflow-watchlist]]：补充高分红观察池与静态烟蒂策略的边界，避免混用股息率和资产垫
- 更新 `knowledge/schema/glossary.md`：新增 `asset-cushion`、`positive-cash-inflow`、`cigar-butt-investing`
- 更新 `knowledge/wiki/index.md`：收录新增 source / concept / portfolio 页面
- 来源口径：当前使用用户提供的本地 raw 截图路径作为真实来源；截图中的个股案例数字均按素材观点记录，未作为当前市场事实二次核验

## [2026-07-05] ingest | 龟龟投资法截图体系吸纳

- 新增来源页：
  - [[2026-07-05-guigu-opening-basic-methodology]]：逐图摘录开篇三步构建顺序
  - [[2026-07-05-guigu-stock-factor-business-model]]：逐图摘录商业模式六问
  - [[2026-07-05-guigu-stock-factor-look-through-return]]：逐图摘录穿透回报率
  - [[2026-07-05-guigu-stock-factor-distributable-cash]]：逐图摘录可支配现金结余
  - [[2026-07-05-guigu-position-management-elasticity-gap]]：逐图摘录仓位管理四原则
- 新增方法页：
  - [[guigu-cashflow-defensive-investing-framework]]：将龟龟框架吸纳为高股息现金流仓的完整子系统
  - [[guigu-business-model-screen]]：沉淀商业模式六问
  - [[look-through-return-rate]]：沉淀穿透回报率估值锚
  - [[distributable-cash-balance]]：沉淀真实可支配现金视角
  - [[guigu-position-management-framework]]：沉淀市场位置、弹性差、商业模式上限和同源风险仓位规则
  - [[elasticity-gap]]：补建弹性差概念页，避免断链
- 更新现有页面：
  - [[fcf]]、[[dividend]]：接入可支配现金结余与穿透回报率
  - [[moat-evaluation-template]]、[[high-dividend-cashflow-watchlist]]：接入龟龟商业模式六问和三因子买入前审查
  - [[personal-hybrid-barbell-execution-dashboard]]：补充半年生活费、30%生活费现金流、高赔率后置的顺序校验
  - [[personal-position-sizing-framework]]、[[position-sizing]]：接入龟龟弹性差与商业模式仓位上限
- 更新 `knowledge/schema/glossary.md`：新增 `business-model-quality`、`look-through-return-rate`、`distributable-cash-balance`、`elasticity-gap`
- 更新 `knowledge/wiki/index.md`：收录新增 source / concept / portfolio 页面
- 来源口径：当前使用用户提供的本地 raw 截图路径作为真实来源，未补写外部视频 URL

## [2026-07-05] update | 高分红现金流观察池沉淀并接入仪表盘候选项

- 新增 [[high-dividend-cashflow-watchlist]]：
  - 将用户给出的泸州老窖、五粮液、中国平安、中国移动、招商银行等 22 家公司整理为 28 个可交易证券候选项
  - 按核心质量现金流、质量型消费制造、周期/资源现金流、港股高息/特殊资产、B股折价池等分层
  - 明确三处名称/代码修正：中关村科技租赁 HK:01601、伊泰B股 B:900948、建发股份 600153
- 更新 `apps/dashboard/`：
  - 高分红录入下拉新增完整预设候选池
  - 保留历史流水记忆优先级，用户录入过的代码/名称配对仍会优先展示
  - 将标的录入从原生 `datalist` 升级为自绘候选选择器，名称在前、代码在后，选择候选时强绑定名称和代码
  - 同名 A/H 标的必须选中具体市场条目，避免名称自动猜错代码
- 新增 `.agents/skills/guigu-cashflow-stock-decision/SKILL.md`：
  - 将龟龟策略落成高分红现金流公司的个股/批量决策过滤器
  - 明确采用“商业模式筛 → 穿透回报率筛 → 可支配现金结余筛 → 仓位管理筛”的漏斗模式
  - 输出 `PASS-CORE / PASS-SMALL / WAIT-PRICE / WAIT-DATA / REJECT` 五档

## [2026-07-02] update | QLD/QQQ 120MA 策略补入 2006-2026 回测

- 更新 [[qld-qqq-120ma-tactical-strategy]]：
  - 主策略收敛为“QQQ 在 120 日均线上方持有 QLD；跌破后卖出并持现金”
  - 补入 Yahoo Finance adjusted close 回测口径：2006-06-22 至 2026-07-01，覆盖 2008 金融危机
  - 记录核心结果：主策略 CAGR 18.2%、最大回撤 -47.1%、终值 28.52x；2008 年收益 -18.2%、最大回撤 -21.9%
  - 将 [[managed-volatility]] 定位为增强研究模块，而非主策略本体
- 更新 `knowledge/wiki/index.md` 摘要

## [2026-07-02] ingest | Alpha Generation and Risk Smoothing using Managed Volatility

- 新增 raw 原文：`knowledge/raw/2026-07-02-financialfactory-alpha-generation-and-risk-smoothing.pdf`
- 新增来源页：[[2010-08-06-alpha-generation-managed-volatility]]
- 新增概念页：[[managed-volatility]]
  - 沉淀 Tony Cooper 的管理波动率框架：预测波动率、动态缩放杠杆、低波动加风险暴露、高波动降杠杆
  - 明确 CVS / OVS / OVPMS 三类策略，以及作者最终偏好 CVS 的原因
- 更新 [[qld-qqq-120ma-tactical-strategy]]：补充该论文与 QLD/QQQ 120MA 策略的关系，明确“均线决定是否在场，波动率决定押多大”
- 更新 `knowledge/schema/glossary.md` 与 `knowledge/wiki/index.md`

## 历史归档

- 2026-06 历史日志见 [[log-archive-2026-06]]。
- 2026-05 历史日志见 [[log-archive-2026-05]]。

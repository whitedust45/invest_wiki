---
id: log-archive-2026-05
title: 2026-05 操作日志归档 / May 2026 Operation Log Archive
type: source
domain: history
topic: [asset-allocation]
tags: [操作日志, 归档, 2026-05]
status: active
version: 2026-06-23
effective_date: 2026-05-31
supersedes: []
superseded_by: []
see_also: [overview, log-archive-2026-05-early]
sources:
  - type: personal
    context: 2026-05 操作日志从 wiki/log.md 拆分归档
    date: 2026-06-23
summary: 归档 2026-05 的知识库操作日志，降低主日志长度并保留历史变更轨迹
---

# 2026-05 操作日志归档

> 本页从 `wiki/log.md` 拆分而来，只保留 2026-05 的历史操作记录。

## [2026-05-23] lint | 修补 P2 级别知识网络问题

- 修复 P2 #11 术语未链接：为 Taleb 主线相关页面补入 [[antifragile]]、[[convexity]]、[[barbell-strategy]]、[[asset-allocation]] 等正文 wikilink
- 修复 P2 #12 单向 `see_also`：补齐 [[antifragile]]、[[convexity]]、[[barbell-strategy]]、[[2026-05-21-taleb-barbell-guide]] 与 [[drawdown]]、[[kelly-criterion]]、[[position-sizing]]、[[risk-reward]]、[[asset-allocation]]、[[diversification]]、[[overview]] 等页面的反向关系
- 复跑 lint 验证：P2 项已清零；当前仅剩 P1 超长文件提示

## [2026-05-21] ingest | 塔勒布杠铃策略完整指南

- 新增来源页：[[2026-05-21-taleb-barbell-guide]]
- 新增概念页：[[barbell-strategy]]
- 更新概念页：[[asset-allocation]]，补入“杠铃策略是资产配置的一种极端表达”
- 更新 `schema/glossary.md`：新增术语 `barbell-strategy`
- 更新 `wiki/index.md`：收录新增来源页与概念页

## [2026-05-21] update | 补建反脆弱概念并接入 overview

- 新增概念页：[[antifragile]]
- 更新 `schema/glossary.md`：新增术语 `antifragile`
- 更新总览页：[[overview]]，补入 “反脆弱 → 杠铃策略 → 回撤” 的 Taleb 主线
- 更新 `wiki/index.md`：收录 [[antifragile]]

## [2026-05-22] update | 补建凸性概念并升级 Taleb 主线

- 新增概念页：[[convexity]]
- 更新 `schema/glossary.md`：新增术语 `convexity`
- 更新概念页：[[antifragile]]、[[barbell-strategy]]，补入 `[[convexity]]` 作为桥梁概念
- 更新总览页：[[overview]]，将 Taleb 主线升级为 “反脆弱 → 凸性 → 杠铃策略 → 回撤”
- 更新 `wiki/index.md`：收录 [[convexity]]

## [2026-05-18] update | overview 接入风险与仓位主线入口

- 更新总览页：[[overview]]
- 在“方法论框架 / 能力圈 / 推荐阅读路径”中显式接入 [[kelly-criterion]]、[[drawdown]]、[[position-sizing]] 三页
- 将风险管理入口从散列术语提升为一条可顺读的主线：凯利公式 → 回撤 → 仓位管理

## [2026-05-18] update | 仓位管理与凯利概念页补强理论中轴

- 更新概念页：[[position-sizing]]、[[kelly-criterion]]
- 为 [[position-sizing]] 补入“仓位管理不仅控风险，也是在控制回撤对复利斜率的侵蚀”的视角
- 为 [[kelly-criterion]] 新增“三句话抓住凯利公式”的理论中轴，压缩解释 edge、对数增长与分数凯利

## [2026-05-18] update | 回撤页补入“复利效率”视角

- 更新概念页：[[drawdown]]
- 追加“回撤不是纯心理问题，而是复利效率问题”的理论补充，并与 [[kelly-criterion]]、[[position-sizing]] 的关系重新接上
- 明确记录深回撤为何会非线性抬高回本要求，从而伤害长期几何增长率

## [2026-05-18] update | 凯利公式实战页追加对话沉淀

- 更新分析页：[[kelly-criterion-in-practice]]
- 将本轮关于“50% 胜率直觉、edge、对数增长与分数凯利”的连续讨论增量沉淀到现有凯利实战页
- 明确区分“胜率常在 50% 附近”这一经验现象与“真正核心量是相对盈亏平衡线的 edge”这一理论主轴
- 补强凯利目标函数为何是长期对数增长、以及为什么小 edge 世界里应把 Full Kelly 视作理论上限

## [2026-05-18] update | 新增我的 A 股好公司清单

- 新增组合页：[[a-share-good-companies-list]]
- 将十只核心标的进一步整理成一页候选池总表，按“核心跟踪层 / 防御底仓层 / 优质但等待赔率层 / 结构复杂观察层”分层
- 显式记录每家公司的护城河来源，避免只记住“公司不错”而忘记“到底好在哪”
- 更新 `wiki/index.md`：收录新增组合页

## [2026-05-18] update | 新增护城河评估模板并升级好公司清单

- 新增组合页：[[moat-evaluation-template]]
- 将护城河判断从“来源层”升级为“来源 + 财务体现 + 资产负债表韧性 + 失效信号 + 仓位表达”的六字段母版
- 更新 [[a-share-good-companies-list]]：新增“财务体现 / 资产负债表特征”列，并补充“资产负债表优势型”与“财务报表层怎么读护城河”章节
- 更新 `wiki/index.md`：收录新增模板页

## [2026-05-18] update | 十家公司页统一接入护城河模板卡片

- 更新 10 个公司实体页：[[china-ping-an]]、[[wuliangye]]、[[luzhou-laojiao]]、[[fusen-home]]、[[china-state-construction]]、[[china-merchants-bank]]、[[youngor]]、[[jiangsu-guotai]]、[[c-d-inc]]、[[meihua-biotech]]
- 为每个公司页统一补充“护城河来源 / 财务体现 / 关键验证指标 / 失效信号 / 组合定位 / 一句结论”模板段落
- 让单个公司页也可以脱离总表独立阅读，不必每次回到 [[a-share-good-companies-list]] 才能理解质量判断

## [2026-05-15] update | IC/IM 策略补充参数总表与自然语言模板

- 更新 [[personal-position-sizing-framework]]：为策略脚本默认参数增加可视化汇总表，集中展示当前值与含义
- 更新 [[ic-im-roll-discount-strategy]]：补充自然语言参数模板、场景映射与最小追问规则
- 更新 `CLAUDE.md`：加入 IC/IM 对话的自然语言参数提取速记，统一 Agent 的后台调用口径

## [2026-05-18] ingest | 十只核心标的公司页拆分沉淀

- 基于 `raw/assets/Kimi_Agent_十只股票深度研究报告/` 的研报正文与配套 CSV，新增 10 个 A 股公司实体页：[[china-ping-an]]、[[wuliangye]]、[[luzhou-laojiao]]、[[fusen-home]]、[[china-state-construction]]、[[china-merchants-bank]]、[[youngor]]、[[jiangsu-guotai]]、[[c-d-inc]]、[[meihua-biotech]]
- 将十只标的从“横向比较素材”进一步拆成“可单独引用的公司页”，补齐公司研究层入口
- 更新 `wiki/index.md`：收录新增实体页，便于后续 `/query` 直接召回公司级知识

## [2026-05-15] lint | 十只核心标的研究报告摄入后复检

- 运行 `.coco/skills/lint/scripts/lint-all.sh`
- 为本轮新增页面补齐反向 `see_also` 与正文术语链接：[[dividend]]、[[a-share-ten-core-stocks-202605]]、[[2026-05-15-ten-core-stocks-research-report]]
- 当前无 P0 / P2 问题；仅保留一条历史性 P1 提示：[[ic-im-roll-discount-strategy]] 正文 308 行，暂未拆分

## [2026-05-15] ingest | 十只核心标的研究报告吸收

- 新增来源页：[[2026-05-15-ten-core-stocks-research-report]]
- 新增分析页：[[a-share-ten-core-stocks-202605]]
- 新增概念页：[[dividend]]
- 更新概念页：[[moat]]、[[roe]]，补充基于十只A股样本的横向理解
- 更新 `schema/glossary.md`：将 dividend 标记为已建
- 更新 `wiki/index.md`：收录新增来源页、分析页与概念页

## [2026-05-15] update | 凯利公式资料深挖与实务化补充

- 更新概念页：[[kelly-criterion]]，补充原始目标函数（最大化长期对数财富增长）、Full Kelly vs. Half Kelly、组合层应用与常见误用
- 更新来源页：[[2026-05-15-investopedia-kelly-criterion]]，明确其作为入门材料的定位，并补充 Kelly 1956、Thorp、CFA Institute 与 Frontiers 的外部视角
- 更新 `wiki/index.md`：提升凯利公式与对应来源页的 summary 信息密度

## [2026-05-15] update | 新增凯利公式实战分析页

- 新增分析页：[[kelly-criterion-in-practice]]
- 将凯利公式从概念定义扩展为可执行框架：强调优势识别、参数保守化、半凯利与组合约束
- 更新 `wiki/index.md`：收录凯利公式实战分析页

## [2026-05-15] update | 新增个人仓位框架与凯利/价值投资关系分析

- 新增组合页：[[personal-position-sizing-framework]]
- 新增分析页：[[kelly-criterion-vs-value-investing]]
- 将“先有价值判断，再有仓位纪律”的链路落盘：价值投资负责识别 edge，凯利公式负责决定下注深度，安全边际负责约束两者
- 更新 `wiki/index.md`：收录新增组合页与分析页

## [2026-05-15] update | 新增分档仓位模板与凯利禁用场景页

- 新增组合页：[[tiered-position-sizing-template]]
- 新增分析页：[[when-not-to-use-kelly-criterion]]
- 将“仓位分层执行语言”与“凯利公式不该上场的条件”独立落盘，补齐仓位系统的执行层与刹车层
- 更新 `wiki/index.md`：收录新增组合页与分析页

## [2026-05-15] lint | Wiki 健康检查

- 运行 `.coco/skills/lint/scripts/lint-all.sh`
- P0 / P1 检查无新增阻塞问题
- 仍存在若干历史性的 P2 提示（术语未链接、单向 see_also），本轮未扩大处理范围

## [2026-05-15] update | IC/IM 滚贴水长期持有策略沉淀

- 新增组合页：[[ic-im-roll-discount-strategy]]
- 新增可复用脚本：`scripts/ic_im_roll_discount_stress.py`
- 将“100 万起步、IC 底仓、极低估时单次加 IM、绝不爆仓优先”的讨论沉淀为长期执行框架
- 更新 `wiki/index.md`：收录组合页条目

## [2026-05-15] update | 压力测试脚本增强与相对路径规则固化

- 为 `scripts/ic_im_roll_discount_stress.py` 增加 PB 百分位触发判断与交互式输入模式
- 统一仓库内文件引用优先使用相对路径，并将该规则写入 `CLAUDE.md` 与 `schema/linking.md`
- 更新 [[ic-im-roll-discount-strategy]]：补充 PB 百分位触发规则与相对路径运行示例

## [2026-05-15] update | 压力测试脚本增加极简决策模式

- 为 `scripts/ic_im_roll_discount_stress.py` 增加 `--decision-mode`，用于只输出“能否加 IM”与“需要额外补资多少”
- 在 `CLAUDE.md` 中补充规则：相关对话里优先由 Agent 在后台主动调用脚本，而不是默认要求用户手动执行
- 更新 [[ic-im-roll-discount-strategy]]：补充极简决策模式与 Agent 使用方式说明

## [2026-05-15] update | 压力测试脚本增加未建仓信号模式

- 为 `scripts/ic_im_roll_discount_stress.py` 增加 `--entry-signal-mode`，用于判断等待区 / 观察区 / 执行区
- 将“未建仓时先判断是否应启动策略，再讨论加仓与补资”的逻辑沉淀到 [[ic-im-roll-discount-strategy]]
- 在 `CLAUDE.md` 中补充规则：用户明确未建仓时，Agent 优先调用未建仓信号模式

## [2026-05-15] update | 压力测试脚本增加 Agent 自动模式

- 为 `scripts/ic_im_roll_discount_stress.py` 增加 `--auto-brief-mode`，按是否提供 `current-drop` 自动识别未建仓 / 已持有 IC 场景
- 在 `CLAUDE.md` 中补充规则：用户直接用自然语言给参数时，Agent 优先尝试自动模式并返回简洁看板
- 更新 [[ic-im-roll-discount-strategy]]：补充自动模式示例与对话触发说明

## [2026-05-15] update | 压力测试脚本接入个人默认阈值配置

- 让 `scripts/ic_im_roll_discount_stress.py` 默认从 [[personal-position-sizing-framework]] 读取观察区 / 执行区 / IM 候选阈值
- 更新 [[ic-im-roll-discount-strategy]]：明确以后优先改知识库中的个人配置，而不是直接改脚本
- 在 `CLAUDE.md` 中补充规则：知识库默认值优先，用户临时参数覆盖默认值

## [2026-05-15] update | 全库清理交叉链接与 see_also

- 新增概念页：[[fcf]]、[[drawdown]]、[[narrative-fallacy]]、[[risk-reward]]
- 更新 `schema/glossary.md`：将以上 4 个术语从“待建”改为“已建”
- 全库补齐正文术语 wikilink，清理 lint 报出的 `UNLINKED_TERM`
- 补齐分析页、实体页与巴菲特来源页之间的反向 `see_also` 关系
- 更新 `wiki/index.md`：收录新增概念页

## [2026-05-15] lint | 全库清理后复检

- 再次运行 `.coco/skills/lint/scripts/lint-all.sh`
- P0 / P1 / P2 检查项全部清空
- 当前统计：55 个页面，673 个 wikilink，42 个 glossary 术语

## [2026-05-15] update | 基于本地浏览器渲染重校巴菲特股东信 2020-2023

- 按用户要求，本地打开 2020-2023 雪球 HTML，等待页面自动渲染后重新提取正文
- 修正 4 份 raw 摘录的提取方式与关键摘录：`raw/2026-05-15-buffett-2020-shareholder-letter.md`、`raw/2026-05-15-buffett-2021-shareholder-letter.md`、`raw/2026-05-15-buffett-2022-shareholder-letter.md`、`raw/2026-05-15-buffett-2023-shareholder-letter.md`
- 更新 4 份来源页，补充基于浏览器渲染复核后的关键信息：[[2026-05-15-buffett-2020-shareholder-letter]]、[[2026-05-15-buffett-2021-shareholder-letter]]、[[2026-05-15-buffett-2022-shareholder-letter]]、[[2026-05-15-buffett-2023-shareholder-letter]]
- 更新分析页：[[buffett-shareholder-letters-2020-2024]]
- 追加更新实体/概念页：[[berkshire-hathaway]]、[[warren-buffett]]、[[charlie-munger]]、[[capital-allocation]]、[[buyback]]、[[insurance-float]]、[[gaap-vs-operating-earnings]]、[[intrinsic-value]]
- 更新 `wiki/index.md`：同步修正 2020-2023 来源页摘要

## [2026-05-15] ingest | 巴菲特股东信 2015-2018 批量摄入

- 新增 raw 摘录：`raw/2026-05-15-buffett-2015-shareholder-letter.md`、`raw/2026-05-15-buffett-2016-shareholder-letter.md`、`raw/2026-05-15-buffett-2017-shareholder-letter.md`、`raw/2026-05-15-buffett-2018-shareholder-letter.md`
- 新增来源摘要：[[2026-05-15-buffett-2015-shareholder-letter]]、[[2026-05-15-buffett-2016-shareholder-letter]]、[[2026-05-15-buffett-2017-shareholder-letter]]、[[2026-05-15-buffett-2018-shareholder-letter]]
- 新增分析页：[[buffett-shareholder-letters-2015-2018]]
- 更新实体页：[[berkshire-hathaway]]、[[warren-buffett]]
- 更新概念页：[[capital-allocation]]、[[insurance-float]]、[[intrinsic-value]]、[[gaap-vs-operating-earnings]]
- 更新 `wiki/index.md`：收录 2015-2018 来源页与分析页
- 更新 [[overview]]：把 2015-2018 股东信补进巴菲特主线的前史层

## [2026-05-15] update | 基于本地浏览器渲染重校巴菲特股东信 2015-2018

- 按用户要求，本地打开 2015-2018 雪球 HTML，等待页面完成渲染后重新提取正文
- 修正 4 份 raw 摘录的提取说明与关键摘录：`raw/2026-05-15-buffett-2015-shareholder-letter.md`、`raw/2026-05-15-buffett-2016-shareholder-letter.md`、`raw/2026-05-15-buffett-2017-shareholder-letter.md`、`raw/2026-05-15-buffett-2018-shareholder-letter.md`
- 更新 4 份来源页，补充基于浏览器渲染复核后的关键结论：[[2026-05-15-buffett-2015-shareholder-letter]]、[[2026-05-15-buffett-2016-shareholder-letter]]、[[2026-05-15-buffett-2017-shareholder-letter]]、[[2026-05-15-buffett-2018-shareholder-letter]]
- 更新分析页：[[buffett-shareholder-letters-2015-2018]]
- 追加更新实体/概念页：[[berkshire-hathaway]]、[[warren-buffett]]、[[capital-allocation]]、[[insurance-float]]、[[gaap-vs-operating-earnings]]、[[intrinsic-value]]

## [2026-05-15] ingest | 价值投资 / 再平衡 / 查理·芒格补建

- 新增 raw 摘录：`raw/2026-05-15-investopedia-value-investing.md`、`raw/2026-05-15-investopedia-rebalancing.md`、`raw/2026-05-15-investopedia-charlie-munger.md`
- 新增来源摘要：[[2026-05-15-investopedia-value-investing]]、[[2026-05-15-investopedia-rebalancing]]、[[2026-05-15-investopedia-charlie-munger]]
- 新增实体页：[[charlie-munger]]
- 新增概念页：[[value-investing]]、[[rebalance]]
- 更新来源页：[[2026-05-15-buffett-2024-shareholder-letter]]、[[2026-05-14-buffett-2025-shareholder-letter]]
- 更新实体页：[[warren-buffett]]
- 更新概念页：[[asset-allocation]]
- 更新分析页：[[buffett-shareholder-letters-2020-2024]]
- 更新 `schema/glossary.md`：将 value-investing、rebalance 标记为已建
- 更新 `wiki/index.md`：收录新增来源页、实体页与概念页
- 更新 [[overview]]：把价值投资、再平衡与芒格纳入顶层阅读路径

## [2026-05-15] ingest | 成长投资 / 动量 / 逆向投资补建

- 新增 raw 摘录：`raw/2026-05-15-investopedia-growth-investing.md`、`raw/2026-05-15-investopedia-momentum-investing.md`、`raw/2026-05-15-investopedia-contrarian-investing.md`
- 新增来源摘要：[[2026-05-15-investopedia-growth-investing]]、[[2026-05-15-investopedia-momentum-investing]]、[[2026-05-15-investopedia-contrarian-investing]]
- 新增概念页：[[growth-investing]]、[[momentum]]、[[contrarian]]
- 更新 `schema/glossary.md`：将 growth-investing、momentum、contrarian 标记为已建
- 更新 `wiki/index.md`：收录新增来源页与概念页
- 更新 [[overview]]：加入策略对照阅读路径

## [2026-05-15] ingest | 均值回归 / 凯利公式 / 定投补建

- 新增 raw 摘录：`raw/2026-05-15-investopedia-mean-reversion.md`、`raw/2026-05-15-investopedia-kelly-criterion.md`、`raw/2026-05-15-investopedia-dollar-cost-averaging.md`
- 新增来源摘要：[[2026-05-15-investopedia-mean-reversion]]、[[2026-05-15-investopedia-kelly-criterion]]、[[2026-05-15-investopedia-dollar-cost-averaging]]
- 新增概念页：[[mean-reversion]]、[[kelly-criterion]]、[[dollar-cost-averaging]]
- 更新概念页：[[position-sizing]]、[[asset-allocation]]、[[momentum]]、[[contrarian]]
- 更新 `schema/glossary.md`：将 mean-reversion、kelly-criterion、dollar-cost-averaging 标记为已建
- 更新 `wiki/index.md`：收录新增来源页与概念页
- 更新 [[overview]]：扩充策略对照与组合阅读路径

## [2026-05-15] ingest | 巴菲特股东信 2020-2024 批量摄入

- 新增 raw 摘录：`raw/2026-05-15-buffett-2024-shareholder-letter.md`、`raw/2026-05-15-buffett-2023-shareholder-letter.md`、`raw/2026-05-15-buffett-2022-shareholder-letter.md`、`raw/2026-05-15-buffett-2021-shareholder-letter.md`、`raw/2026-05-15-buffett-2020-shareholder-letter.md`
- 新增来源摘要：[[2026-05-15-buffett-2024-shareholder-letter]]、[[2026-05-15-buffett-2023-shareholder-letter]]、[[2026-05-15-buffett-2022-shareholder-letter]]、[[2026-05-15-buffett-2021-shareholder-letter]]、[[2026-05-15-buffett-2020-shareholder-letter]]
- 新增分析页：[[buffett-shareholder-letters-2020-2024]]
- 新增概念页：[[buyback]]、[[gaap-vs-operating-earnings]]
- 更新实体页：[[berkshire-hathaway]]、[[warren-buffett]]
- 更新概念页：[[capital-allocation]]、[[insurance-float]]
- 更新 `schema/glossary.md`：将 buyback、gaap-vs-operating-earnings 标记为已建
- 更新 `wiki/index.md`：收录新增来源页、分析页与概念页
- 更新 [[overview]]：加入 2020-2024 股东信主线与素材簇阅读路径

## 早期归档

- 2026-05-14 至 2026-05-13 的初始化期日志见 [[log-archive-2026-05-early]]。

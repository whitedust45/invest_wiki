---
id: ic-im-roll-discount-operations-manual
title: IC/IM 滚贴水执行手册 / IC-IM Operations Manual
type: portfolio
domain: strategy
topic: [position-sizing, drawdown, rebalance]
tags: [股指期货, IC, IM, 脚本, PB百分位, 执行手册]
status: active
version: 2026-06-23
effective_date: 2026-06-23
supersedes: []
superseded_by: []
see_also: [ic-im-roll-discount-strategy, personal-position-sizing-framework, personal-hybrid-barbell-execution-dashboard]
sources:
  - type: dialogue
    context: IC/IM 滚贴水策略脚本、自然语言参数模板与 Agent 后台调用方式沉淀
    date: 2026-05-15
    participants: [用户, Agent]
    trigger: 压力测试脚本增加自然语言参数模板与自动模式
    rounds: 4
    depth: refined
  - type: personal
    context: 从 IC/IM 主策略页拆分执行手册，降低主文档长度并集中维护脚本说明
    date: 2026-06-23
summary: 集中维护 IC/IM 滚贴水策略的脚本调用、自然语言参数提取和 Agent 后台执行口径
---

# IC/IM 滚贴水执行手册

## 定位

本页是 [[ic-im-roll-discount-strategy]] 的执行层。主策略页负责说明为什么做、什么时候做、风险边界是什么；本页只维护脚本调用、自然语言参数提取和对话中的默认执行口径，服务于 [[position-sizing|仓位管理]]、[[drawdown|回撤]] 控制与 [[rebalance|再平衡]] 纪律。

## 配套脚本

可复用脚本位于：[`tools/investing/ic_im_roll_discount_stress.py`](../../../tools/investing/ic_im_roll_discount_stress.py)

脚本会默认读取 [`knowledge/wiki/portfolios/personal-position-sizing-framework.md`](personal-position-sizing-framework.md) 里的个人阈值配置。

也就是说，如果以后想调整：

- 观察区阈值
- IC 开仓阈值
- IM 候选阈值
- IM 优先阈值

优先改的是知识库里的个人配置，而不是脚本源码。

若在项目根目录 `wiki实践/` 下执行，优先使用相对路径：

```bash
python3 tools/investing/ic_im_roll_discount_stress.py
```

若希望把 PB 百分位触发和资金安全垫一起算：

```bash
python3 tools/investing/ic_im_roll_discount_stress.py \
  --current-drop 0.20 \
  --pb-percentile 18 \
  --pb-add-threshold 20
```

若希望直接进入交互式输入：

```bash
python3 tools/investing/ic_im_roll_discount_stress.py --interactive
```

若只想得到一句“现在能不能加 IM、要补多少钱”的结论，可使用极简决策模式：

```bash
python3 tools/investing/ic_im_roll_discount_stress.py \
  --decision-mode \
  --current-drop 0.20 \
  --pb-percentile 18 \
  --pb-add-threshold 20
```

若还没建仓，只想知道现在属于等待区 / 观察区 / 执行区，可使用未建仓信号模式：

```bash
python3 tools/investing/ic_im_roll_discount_stress.py \
  --entry-signal-mode \
  --ic-pb-percentile 28 \
  --pb-percentile 18
```

若希望让 Agent 按给定数据自动判断“未建仓/已持有 IC”并输出一句简洁看板，可使用自动模式：

```bash
python3 tools/investing/ic_im_roll_discount_stress.py \
  --auto-brief-mode \
  --ic-pb-percentile 89.8 \
  --pb-percentile 89.61
```

若想临时覆盖知识库中的默认阈值，也可以只在命令里覆盖个别参数：

```bash
python3 tools/investing/ic_im_roll_discount_stress.py \
  --auto-brief-mode \
  --ic-pb-percentile 32 \
  --pb-percentile 21 \
  --ic-open-threshold 28
```

## 输入与输出

脚本支持输入：

- IC / IM 点位
- 合约乘数
- 保证金率
- 初始总资金
- 初始期货账户资金
- 当前已跌幅与 PB 百分位
- IM 加仓的 PB 百分位阈值
- 准备在哪个跌幅位置加 IM
- 你希望在“加完后再跌多少”这个压力测试下仍然保持什么最大风险度

脚本会输出：

- 当前 PB 百分位是否满足加 IM 的估值触发
- 当前是否处于等待区 / 观察区 / 执行区
- 是否允许开第一手 IC
- 是否允许把 IM 纳入单次加仓候选
- 自动判断当前属于“未建仓”还是“已持有 IC”场景，并输出简洁行动结论
- 当前状态下只持有 IC / 若加 1 手 IM 的风险对比
- 初始 1 手 IC 的压力测试表
- 加 1 手 IM 需要额外补充多少资金
- 加完 IM 后继续下跌时，还需再补多少才能把风险度拉回目标线

## 自然语言参数模板与提取模式

当你在对话里直接给出自然语言时，Agent 不应先让你手动拼命令，而应优先把句子拆成脚本参数，再在后台调用脚本。

| 用户常见表达 | 建议模式 | 参数提取示例 | 应返回的核心结论 |
|---|---|---|---|
| `IC 8536，IM 8683，IC 的 PB 百分位 89.80%，IM 的 PB 百分位 89.61%，我还没建仓` | `--auto-brief-mode` | `ic_points=8536`；`im_points=8683`；`ic_pb_percentile=89.80`；`pb_percentile=89.61`；`current_drop=None` | 当前属于等待区 / 观察区 / 执行区；能不能开第一手 IC；IM 是否进入候选 |
| `我还没建仓，IC PB 28，IM PB 18` | `--entry-signal-mode` 或 `--auto-brief-mode` | `ic_pb_percentile=28`；`pb_percentile=18` | 第一手 IC 是否进入执行区；IM 是否已经进入候选区 |
| `IC 已有底仓，现在跌了 20%，IM PB 18，能不能加 IM？` | `--decision-mode` 或 `--auto-brief-mode` | `current_drop=0.20`；`pb_percentile=18` | 是否满足 IM 单次加仓条件；建议额外补资多少 |
| `这个位置要不要补保证金？` | `--auto-brief-mode`，若已知跌幅 | 至少需要 `current_drop`；若同时给出 `pb_percentile`，顺带判断是否能加 IM | 当前仅持有 IC 的风险度；补到目标风险度约需补多少 |
| `IC 还没到 30，但已经到 35 了，要开始准备吗？` | `--entry-signal-mode` | `ic_pb_percentile=35`；其余缺失就最少追问 | 当前是观察区还是执行区；离开仓阈值还差几个百分点 |
| `IM PB 已经到 9 了，但我还没有 IC 底仓` | `--entry-signal-mode` 或 `--auto-brief-mode` | `pb_percentile=9`；`current_drop=None`；`ic_pb_percentile` 若未知则补问 | IM 已进优先区，但动作顺序仍应先判断 IC 是否允许启动 |

提取时的默认规则：

1. “还没建仓 / 空仓 / 尚未开始”视为未建仓场景，`current_drop=None`。
2. “已有 IC / 持有底仓 / 现在跌了 X%”视为已持有 IC 场景，优先提取 `current_drop`。
3. 句子里只有一个“PB 百分位”默认映射为 `pb_percentile`，即 IM 的估值触发字段；若同时提到 IC，则显式拆成 `ic_pb_percentile` 与 `pb_percentile`。
4. 用户临时说“阈值改成 25 / 风险度按 60% 算”，视为覆盖知识库默认值，优先映射到 `pb_add_threshold`、`rebalance_risk` 等 CLI 参数。
5. 信息不够时只追问最小缺口：未建仓至少要有 `ic_pb_percentile` 与 `pb_percentile`；已持有 IC 至少要有 `current_drop` 与 `pb_percentile`。

## 对话中的使用方式

这类脚本的理想使用方式，不是你手动记命令，而是：

1. 你在对话里提到 IC / IM、PB 百分位、加仓、补保证金、不能爆仓等主题。
2. 若你还没建仓，Agent 优先走“未建仓信号模式”；若你已持有 IC 底仓，Agent 再走“加 IM 决策模式”；若只给了自然语言参数，Agent 默认优先尝试“自动模式”。
3. 若缺少关键参数，Agent 先追问最少量信息，如当前点位、PB 百分位、当前已跌幅。
4. Agent 在后台调用脚本，再把结论直接翻译成可执行动作。

也就是说，脚本是 Agent 的计算器，而不是要求用户自己背命令。

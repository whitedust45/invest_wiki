---
id: personal-position-sizing-framework
title: 个人仓位管理框架 / Personal Position Sizing Framework
type: portfolio
domain: strategy
topic: [position-sizing, kelly-criterion, drawdown, rebalance]
tags: [半凯利, 组合纪律, 回撤控制, 执行规则]
status: active
version: 2026-07-05
effective_date: 2026-05-15
supersedes: []
superseded_by: []
see_also: [position-sizing, kelly-criterion, overview, tiered-position-sizing-template, guigu-position-management-framework, kelly-criterion-vs-value-investing, personal-hybrid-barbell-matrix, personal-hybrid-barbell-execution-dashboard, ic-im-roll-discount-operations-manual]
sources:
  - type: dialogue
    context: 围绕凯利公式实战化与个人仓位规则的延伸沉淀
    date: 2026-05-15
    participants: [用户, Agent]
    trigger: 接着写1和2
    rounds: 1
    depth: refined
  - type: article
    title: "Understanding the Kelly Criterion"
    author: Edward O. Thorp
    url: https://econpapers.repec.org/RePEc:wsi:wschap:9789814293501_0036
    publisher: World Scientific
    date: 2011-01-01
  - type: article
    title: "The Kelly Criterion: You Don’t Know the Half of It"
    author: Alon Bochman
    url: https://rpc.cfainstitute.org/blogs/enterprising-investor/2018/the-kelly-criterion-you-dont-know-the-half-of-it
    publisher: CFA Institute
    date: 2018-06-14
  - type: personal
    context: 用户提供的龟龟投资法仓位管理1截图素材，补充市场弹性和商业模式上限
    date: 2026-07-05
summary: 把凯利、安全边际与回撤约束落成个人仓位纪律：理论满凯利只作上限，实盘默认半凯利以下执行
---

# 个人仓位管理框架 / Personal Position Sizing Framework

> **定位**：这是一套面向我自己投资执行的默认仓位纪律。它不负责回答“买什么”，而是把 [[value-investing|价值判断]]、[[kelly-criterion|凯利公式]] 与 [[drawdown|回撤]] 承受力转成可执行的仓位规则。

## 核心原则

1. **先有 edge，再谈仓位**：没有明确优势的机会，不配讨论加仓比例。  
2. **满凯利只是理论上限**：实盘默认不用 Full Kelly，而是把它当作风险天花板。  
3. **先保组合生存，再追求复利最快**：任何仓位安排都不能把账户推到无法继续参与下一轮的状态。  
4. **价格不是唯一变量，相关性和流动性同样决定仓位**。  

## 默认决策顺序

### 1. 先判断值不值得做

- 是否符合 [[value-investing|价值投资]] 的主框架，或者至少有可验证的优势来源？  
- 是否能说清楚 [[margin-of-safety|安全边际]] 来自哪里，而不是只觉得“跌很多了”？  
- 是否存在会导致 thesis 快速失效的关键风险？  

若这三问答不清，默认**不开仓或只观察**。

### 2. 再判断理论仓位上限

- 若能估算胜率与赔率，则先用 [[kelly-criterion|凯利公式]] 算理论 Full Kelly。  
- 若不能可靠估算，则不强行套公式，退回到 [[tiered-position-sizing-template|分档仓位法模板]]。  
- 不论哪种情况，理论上限都必须再经过 [[drawdown|回撤]] 与组合相关性检验。  

### 3. 最后才决定实盘执行仓位

我的默认规则是：

```text
实盘执行仓位 = min(
  0.5 × 理论满凯利,
  单一头寸上限,
  组合相关性调整后上限,
  回撤承受力允许的上限
)
```

**观点**：对自己最有把握的想法，也默认先从 Half Kelly 以下开始，而不是直接奔着“最优”去。

## 两套可执行方法

### 方法 A：凯利折扣法

适用于你确实能对胜率和赔率给出相对保守估计的场景。

- 第一步：算理论 Full Kelly。  
- 第二步：默认打五折；若波动高、流动性差、相关性强，再打到四分之一。  
- 第三步：叠加组合硬约束。  

这套方法的好处是：它强迫我把“有把握”翻译成可以复核的概率和赔率，而不是只靠感觉下重注。

### 方法 B：分档仓位法

适用于主观投资、赔率难以精确量化、但我仍需要执行纪律的场景。

一个默认版本是：

- **观察状态**：只跟踪，不建仓。  
- **试错仓**：只开目标执行仓位的 1/3。  
- **标准仓**：满足 thesis 更清楚、价格更有利或验证增强后，加到目标执行仓位的 2/3。  
- **完成仓**：只有在 thesis、估值与组合约束同时通过时，才加满到既定执行仓位。  

这样做的意义，不是“分批一定更赚钱”，而是降低一次性判断错误对账户的伤害。

## 我的组合硬约束

## 供策略脚本读取的默认参数

> 这部分是给 `tools/investing/ic_im_roll_discount_stress.py` 读取的个人默认阈值。以后如果我要调整这套期货策略的观察区 / 执行区，不优先改脚本，而是优先改这里。

<!-- strategy-script-defaults:start -->
entry_watch_threshold: 40
ic_open_threshold: 30
pb_add_threshold: 20
im_priority_threshold: 10
rebalance_risk: 0.55
post_add_max_risk: 0.70
post_add_stress_drop: 0.20
<!-- strategy-script-defaults:end -->

当前个人默认阈值汇总如下：

| 参数 | 当前值 | 含义 |
|---|---:|---|
| `entry_watch_threshold` | 40 | 未建仓观察区上限；高于它默认继续等待 |
| `ic_open_threshold` | 30 | 第一手 IC 启动阈值；低于等于它才进入底仓执行区 |
| `pb_add_threshold` | 20 | IM 单次加仓候选阈值；低于等于它才值得做补资测算 |
| `im_priority_threshold` | 10 | IM 极低估优先区阈值；低于等于它时可视为最优先关注窗口 |
| `rebalance_risk` | 0.55 | 补资后的目标风险度；默认把风险度拉回 55% |
| `post_add_max_risk` | 0.70 | 加 1 手 IM 后做压力测试时允许的最大风险度 |
| `post_add_stress_drop` | 0.20 | 计算保守加仓安全垫时，默认假设“加完后再跌 20%” |

以下约束优先级高于单笔想法强弱：

### 1. 单一头寸上限

- 即使某笔机会看起来极优，也不能让单一头寸大到足以主导整个组合命运。  
- 一旦头寸上涨导致权重漂移，也要通过 [[rebalance|再平衡]] 或停止加仓来管理暴露。  

### 2. 同源风险上限

- 多个看似不同的持仓，若本质上押的是同一个宏观假设、风格因子或流动性环境，就不应分别按“独立机会”计算仓位。  
- 相关性高时，组合应像面对一个更大的单一头寸一样保守。  

### 3. 最大回撤优先级高于增长率

- 若某个仓位方案会把我推到无法承受的 [[drawdown|回撤]]，即使它在数学上更接近“增长最优”，也不采用。  
- 我的规则不是追求理论最优，而是追求**能长期执行的次优**。  

## 触发加仓与减仓的条件

### 允许加仓

- thesis 更清楚，而不是只因为价格波动；  
- [[margin-of-safety|安全边际]] 变得更厚，而基本面没有同步恶化；  
- 组合其余头寸没有让整体风险结构恶化。  

### 强制减仓

- thesis 被破坏；  
- 组合相关性骤然上升，导致风险集中；  
- 头寸已大到即使基本面没变，我也无法舒服持有；  
- 权重漂移过大，需要用 [[rebalance|再平衡]] 恢复纪律。  

## 龟龟仓位管理补充

**[2026-07-05 via [[2026-07-05-guigu-position-management-elasticity-gap]]]**
[[guigu-position-management-framework|龟龟仓位管理框架]] 可以作为本页的个股执行补丁，尤其适合高分红现金流仓。它把仓位拆成四层：

1. 市场位置决定组合弹性：低位时可提高高弹性资产比例，高位时转向低波动、固收或现金。
2. 商业模式决定单股仓位上限：越稳定、越可理解、越弱周期，越能承受高权重。
3. 价格决定实际仓位：好公司也要等到穿透回报率足够高，才配得上仓位推升。
4. 同源风险合并限制：地产、金融、白酒、资源等同源风险不能按多个独立机会分别加仓。

这与本页原有逻辑并不冲突：凯利和回撤约束回答“总风险不能多大”，龟龟框架回答“在当前市场位置和商业模式质量下，哪类个股更配得上这部分风险预算”。

## 一张最小执行卡片

每次下单前，我默认问自己 5 个问题：

1. 这笔机会的 edge 到底是什么？  
2. 如果我错了，错在哪一层：价值、时间、流动性，还是叙事？  
3. 理论满凯利是多少，打折后实际应该是多少？  
4. 这笔仓位和现有组合的相关性有多高？  
5. 如果明天先亏 20%，我还能不能舒服持有？  

只要第 5 题答案是否定的，仓位就说明显偏大。

## 对当前知识库的价值

- 它把 [[kelly-criterion-in-practice]] 从方法论进一步推进成个人执行纪律。  
- 它把 [[position-sizing]]、[[drawdown]]、[[rebalance]] 与 [[value-investing]] 接成一个闭环：研究、定价、仓位、风控不再是分离动作。  
- 它也为未来具体策略页提供母版：以后无论是现货、ETF 还是衍生品，都应先经过这套框架再决定仓位。  
- 需要更细的执行语言时，可以继续下钻到 [[tiered-position-sizing-template]]；遇到凯利不该上的场景，则应参考 [[when-not-to-use-kelly-criterion]]。  

## 时效性说明

本页是 2026-05-15 形成的个人默认仓位规则，属于方法论与执行纪律，不构成投资建议。后续若账户规模、策略类型或回撤承受能力发生变化，应优先更新本页而不是临场破例。

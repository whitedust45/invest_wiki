---
id: tiered-position-sizing-template
title: 分档仓位法模板 / Tiered Position Sizing Template
type: portfolio
domain: strategy
topic: [position-sizing, drawdown, rebalance, margin-of-safety]
tags: [观察仓, 试错仓, 标准仓, 完成仓, 超配仓]
status: active
version: 2026-05-15
effective_date: 2026-05-15
supersedes: []
superseded_by: []
see_also: [personal-position-sizing-framework, position-sizing, overview, kelly-criterion-in-practice]
sources:
  - type: dialogue
    context: 围绕凯利公式、价值投资与个人仓位体系继续拆解为分档执行模板
    date: 2026-05-15
    participants: [用户, Agent]
    trigger: 1+2一起
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
summary: 将个人仓位纪律拆成观察仓、试错仓、标准仓、完成仓与超配仓五档，降低一次性押注错误的伤害
---

# 分档仓位法模板 / Tiered Position Sizing Template

> **定位**：这页不是讨论“某个标的值不值得买”，而是把 [[personal-position-sizing-framework|个人仓位管理框架]] 进一步拆成可执行的档位模板。它适合主观投资、赔率难精确量化、但我又不想靠拍脑袋下仓位的场景。

## 核心思路

分档仓位法的本质，是承认三个现实：

1. 我很少会在第一次判断时就完全看对。  
2. 就算 thesis 最终正确，价格路径也常常先吓人。  
3. 真正伤账户的，通常不是“少赚一点”，而是过早压太大。  

因此，[[position-sizing|仓位管理]] 不应只回答“买不买”，还应回答“现在配得上哪一档”。

## 五档模板

### 1. 观察仓：0%

**定位**：有兴趣，但还不配拿真钱表达观点。  

适用条件：

- thesis 仍然模糊；  
- [[margin-of-safety|安全边际]] 还不够厚；  
- 或者虽然看起来便宜，但我说不清市场到底错在哪。  

允许动作：

- 持续跟踪；  
- 更新估值区间；  
- 不以“怕踏空”为理由先上车。  

### 2. 试错仓：目标执行仓位的 1/4 到 1/3

**定位**：我愿意为这个判断付出一点真钱，但只愿意花学费，不愿意交大额学费。  

适用条件：

- 已经存在初步 edge；  
- 价格开始接近我认可的价值区间；  
- 但核心不确定性仍未充分解决。  

适用场景：

- 首次建仓；  
- thesis 方向大致认可，但催化与时间路径不清；  
- 高波动品种里想先测试自己持有体验。  

### 3. 标准仓：目标执行仓位的 1/2 到 2/3

**定位**：这是默认的主力执行层，而不是一上来就追求的终点。  

适用条件：

- thesis 已较清楚；  
- [[margin-of-safety|安全边际]] 足够；  
- 与现有组合的相关性在可接受范围内。  

如果我对一个机会有明确正面判断，但还没到“可以高集中表达”的程度，通常默认停在这一档。

### 4. 完成仓：目标执行仓位的 100%

**定位**：我认为这笔机会已经达到当前规则下应有的满配权重，但这里的“满配”仍然是受 [[drawdown|回撤]] 与组合约束限制后的满配，不是情绪上的满仓。  

适用条件：

- 价值判断清晰；  
- 安全边际厚；  
- 组合其余部分没有形成高相关拥挤；  
- 即使短期先逆向波动，我也能舒服持有。  

### 5. 超配仓：仅在极少数场景使用

**定位**：这不是常态，而是异常强机会下的例外表达。  

进入超配前必须额外满足：

- 不是单靠便宜，而是“便宜 + thesis 清楚 + 风险结构友好”；  
- 与现有组合低相关；  
- 即使错了，也不会让整体账户伤筋动骨；  
- 已经通过 [[kelly-criterion|凯利公式]] 的折扣检验，且仍未突破我的组合硬上限。  

**观点**：超配仓不应成为追求刺激的常规选项，而应是极少数高质量机会下的罕见动作。

## 什么时候升级档位

从低档升到高档，不能只因为价格下跌，更不能只因为我“更想买了”。默认需要满足以下至少一类条件：

1. thesis 更清楚；  
2. 价值判断被新增事实验证；  
3. 价格更便宜，但基本面没有同步恶化；  
4. 组合其他风险暴露下降，为这笔机会腾出了空间。  

也就是说，升级仓位应该是“信息质量提升”或“赔率改善”的结果，而不是情绪放大后的冲动补仓。

## 什么时候降级档位

从高档降到低档，最重要的触发器有四类：

1. thesis 被破坏；  
2. 组合相关性上升，让这笔机会不再像原来那样独立；  
3. 权重漂移过大，需要用 [[rebalance|再平衡]] 恢复结构；  
4. 我发现自己已经无法舒服持有。  

最后这一条尤其重要：如果一笔仓位大到让我每天盯盘、反复自我说服，那它通常已经超出了应有档位。

## 一个最小默认表

下面这张表可以作为没有特殊说明时的执行基线：

| 档位 | 占目标执行仓位 | 默认含义 |
|---|---:|---|
| 观察仓 | 0% | 只跟踪，不建仓 |
| 试错仓 | 25% - 33% | 用小成本验证判断与持有体验 |
| 标准仓 | 50% - 67% | 默认主力执行层 |
| 完成仓 | 100% | 在组合约束内的满配 |
| 超配仓 | >100%，但受硬上限约束 | 极少数异常强机会下的例外 |

## 与凯利公式的关系

这套模板并不是替代 [[kelly-criterion|凯利公式]]，而是在很多主观投资场景里，对凯利的一种“可执行翻译”：

- 如果我能估算赔率和胜率，先算理论 Full Kelly；  
- 再打折得到目标执行仓位；  
- 最后用分档模板决定当前处于哪一档。  

因此，分档仓位法是对凯利的执行层补丁，而不是对立面。

## 对当前知识库的价值

- 它把 [[personal-position-sizing-framework]] 中的“分档仓位法”从一段原则，展开成可直接复用的模板。  
- 它把 [[position-sizing]]、[[drawdown]] 与 [[rebalance]] 变成日常可操作语言。  
- 它也为未来任何具体标的或策略提供统一档位语言：以后不只说“要不要买”，而说“它现在处在哪一档”。  

## 时效性说明

本页形成于 2026-05-15，属于个人执行模板，不构成投资建议。未来若账户规模、波动承受力或策略偏好变化，应优先更新本页的档位定义与比例。

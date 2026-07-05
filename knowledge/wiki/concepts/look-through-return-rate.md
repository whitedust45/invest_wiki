---
id: look-through-return-rate
title: 穿透回报率 / Look-through Return Rate
type: concept
domain: valuation
topic: [look-through-return-rate, dividend, buyback, intrinsic-value]
tags: [龟龟投资法, 股东回报, 高股息, 注销回购, 估值锚]
status: active
version: 2026-07-05
supersedes: []
superseded_by: []
see_also: [dividend, buyback, fcf, intrinsic-value, distributable-cash-balance, guigu-business-model-screen, high-dividend-cashflow-watchlist]
sources:
  - type: personal
    context: 用户提供的龟龟投资法选股因子2截图素材，详见来源页 2026-07-05-guigu-stock-factor-look-through-return
    date: 2026-07-05
summary: 穿透回报率衡量企业利润中能确定以分红或注销回购形式回到股东账户的比例
---

# 穿透回报率 / Look-through Return Rate

## 定义

穿透回报率衡量一家企业赚到的钱，有多少能确定穿透到股东账户。它不等同于会计利润率，也不等同于静态股息率，而是关注分红、注销回购等确定股东回报相对于当前市值或买入价格的真实收益。

## 基本公式

龟龟素材中最核心的可执行口径是：

```text
穿透回报金额 = 保守净利润 × 公司公告最低股息支付率 + 注销型回购金额
穿透回报率 = 穿透回报金额 / 当前市值
```

如果公司没有公告最低股息支付率，可退而使用 5-10 年稳定派息金额或稳定派息率做线性外推。若两者都没有，穿透回报率的不确定性就明显上升。

## 哪些算穿透回报

优先计入：

- 现金分红；
- 注销型回购；
- 管理层明确且可执行的现金回报承诺。

谨慎或不直接计入：

- 留存在公司账上的现金；
- 资本开支；
- 并购；
- 股权激励型回购；
- 需要市场重新估值才可能体现的内部投资。

原因是防守型投资者需要确定回报，而不是依赖市场未来是否认可公司账上资产。

## 现金流校验

穿透回报率必须和 [[distributable-cash-balance|可支配现金结余]] 一起看。若公司分红来自贷款、出售资产或牺牲必要再投资，静态穿透回报率就会高估真实收益。

因此高股息公司至少要同时满足：

- 经营现金流能覆盖资本开支和财务费用；
- 分红后现金储备仍安全；
- 有息负债不会把股息变成“贷款分红”；
- 商业模式足够简单，未来利润能保守估计。

## 收益率门槛

龟龟素材给出的经验门槛是：

- 穿透回报率至少 3%；
- 最好达到 5% 以上；
- 同时要高于货币无风险利率约 1%。

这意味着高股息买入并不是“只要有股息就买”，而是要把价格压到足够提供可见现金回报的位置。

## 与现有概念的关系

- 与 [[dividend]] 的区别：股息是分配形式，穿透回报率是股东最终到手收益相对价格的估值口径。
- 与 [[intrinsic-value]] 的关系：穿透回报率提供一种现金流防守仓的简化估值锚。
- 与 [[buyback]] 的关系：只有注销型回购更接近穿透回报，股权激励型回购通常只是现金支出。
- 与 [[guigu-business-model-screen]] 的关系：商业模式越简单透明，保守净利润越容易估计，穿透回报率越可靠。

## 实战意义

对 [[high-dividend-cashflow-watchlist]] 中的候选股，本页可以作为“买入价格是否足够低”的硬约束。好公司如果穿透回报率不足，也应继续等待价格，而不是因为商业模式好就高仓位买入。

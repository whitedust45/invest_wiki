---
id: 2010-08-06-alpha-generation-managed-volatility
title: Alpha Generation and Risk Smoothing using Managed Volatility
type: source
domain: strategy
topic: [managed-volatility, position-sizing, drawdown, risk-reward]
tags: [Tony Cooper, leveraged ETF, volatility timing, volatility drag, vovo, dynamic leverage]
status: active
version: 2026-07-02
effective_date: 2010-08-06
supersedes: []
superseded_by: []
see_also: [managed-volatility, qld-qqq-120ma-tactical-strategy, momentum, drawdown, position-sizing]
sources:
  - type: article
    title: Alpha Generation and Risk Smoothing using Managed Volatility
    author: Tony Cooper
    url: https://financialfactory.com/wp-content/uploads/2014/01/alphagenerationandrisksmoothingusingmanagedvolatility.pdf
    publisher: Double-Digit Numerics
    date: 2010-08-06
summary: Tony Cooper提出用预测波动率动态调整杠杆，在不提高目标波动的前提下争取杠杆上行并降低回撤
---

# Alpha Generation and Risk Smoothing using Managed Volatility

## 素材信息

- 作者：Tony Cooper
- 日期：2010-08-06
- 原文：`knowledge/raw/2026-07-02-financialfactory-alpha-generation-and-risk-smoothing.pdf`
- 主题：[[managed-volatility|管理波动率]]、杠杆 ETF、波动率拖累、动态杠杆、回撤控制

## 核心问题

这篇论文的出发点是：股票收益很难预测，但市场波动率相对更容易预测。作者试图把“收益不可预测、波动可预测”转化为可执行策略：通过动态调整杠杆，让组合在低波动时承受更多风险暴露，在高波动时自动降杠杆，从而减少波动率拖累和深回撤。

这不是普通技术形态择时，也不是单纯买入并持有杠杆 ETF。作者强调，策略由复利收益公式和次日波动率预测推导，核心是 [[position-sizing|仓位管理]] 与风险暴露缩放。

## 关键论点

1. **杠杆 ETF 的长期收益不只取决于标的涨跌，也取决于标的波动率。**  
   作者用近似式说明杠杆复利收益中存在波动率拖累项：杠杆带来的收益近似随 `k` 增加，但波动拖累近似随 `k^2` 增加。因此过高杠杆会在高波动环境中迅速伤害复利。

2. **最优杠杆与收益/方差比相关。**  
   在简化条件下，作者得到近似最优杠杆 `k = mu / sigma^2`。这与 [[kelly-criterion|凯利公式]] 和 Merton 组合问题有相同直觉：收益预期越高可承受越高仓位，波动率越高则仓位应非线性下降。

3. **管理波动率的核心不是预测方向，而是预测风险状态。**  
   论文使用 EGARCH(1,1) 预测次日波动率，并据此调整次日杠杆。作者认为波动率比收益更可预测，因此可以先把收益写成波动率函数，再通过杠杆缩放提高组合效率。

4. **波动率的波动率本身有成本。**  
   作者把波动率随时间变化称为 vovo。静态股债配置或固定杠杆会让投资者承受忽高忽低的风险，迫使长期配置更保守；若能把剩余风险平滑化，就可能在同等心理/制度风险预算下承受更高平均风险暴露。

5. **作者比较了三类主要策略。**  
   - CVS：恒定波动率策略，杠杆 `k = c / sigma`，目标是每日组合波动率接近固定值。
   - OVS：最优波动率策略，杠杆 `k = c / sigma^2`，理论更接近最优，但杠杆波动更大且参数更难提前设定。
   - OVPMS：在 OVS 基础上估计“收益随波动率变化”的函数，再决定杠杆。

6. **作者最终更偏好 CVS。**  
   尽管 OVPMS 在回测风险调整收益上更强，作者更偏好 CVS，因为它的目标波动率更容易解释和写入产品规则，杠杆通常低于 3，破产风险更低，并且在极端波动口径和峰度口径下更稳。

## 对个人策略的启发

- 对 [[qld-qqq-120ma-tactical-strategy|QLD/QQQ 120 日均线策略]]：120MA 处理“是否在趋势中”，这篇论文处理“在趋势中应该承受多大杠杆”。二者可以组合，但不能混为一谈。
- 对 [[personal-hybrid-barbell-matrix|个人混合杠铃矩阵]]：右尾仓不应只靠固定比例买 QQQ/QLD，还可以引入波动率闸门，在高波动阶段降低杠杆暴露。
- 对 [[ic-im-roll-discount-strategy|IC/IM 滚贴水策略]]：论文强化了已有“同样低估时，高波动少下注”的原则；估值决定是否值得买，波动率决定买多激进。

## 风险与限制

- 回测使用 1950-2009 等历史指数数据，未来市场结构可能不同。
- EGARCH 预测和临近收盘调仓在实盘中存在交易成本、滑点、税费和执行时点问题。
- 论文中的 OVPMS+ 使用全样本参数，作者也明确提示这属于校准意义上的数据窥探，不能直接当作真实历史可得信号。
- 动态杠杆不能把熊市变成牛市。论文显示在 2000-2009 的 S&P 500 熊市阶段，策略相对指数更好，但仍可能亏钱。

## 关系索引

- 直接概念：[[managed-volatility]]
- 关联策略：[[qld-qqq-120ma-tactical-strategy]]、[[cross-asset-carry-momentum]]
- 风险概念：[[drawdown]]、[[position-sizing]]、[[risk-reward]]

---
id: guigu-position-management-framework
title: 龟龟仓位管理框架 / Guigui Position Management Framework
type: portfolio
domain: risk
topic: [position-sizing, mean-reversion, diversification, elasticity-gap]
tags: [龟龟投资法, 仓位管理, 弹性差, 市场位置, 同源风险]
status: active
version: 2026-07-05
effective_date: 2026-07-05
supersedes: []
superseded_by: []
see_also: [personal-position-sizing-framework, tiered-position-sizing-template, position-sizing, mean-reversion, diversification, guigu-cashflow-defensive-investing-framework]
sources:
  - type: personal
    context: 用户提供的龟龟投资法仓位管理1截图素材，详见来源页 2026-07-05-guigu-position-management-elasticity-gap
    date: 2026-07-05
summary: 龟龟仓位管理用市场位置、个股弹性、商业模式上限、价格和同源风险决定实际仓位
---

# 龟龟仓位管理框架

> **定位**：这页是 [[personal-position-sizing-framework]] 的龟龟投资法补丁。它更适合长期个股投资，尤其适合高股息现金流仓从“候选”走到“实际仓位”的过程。

## 四条核心原则

### 1. 市场位置决定组合弹性

龟龟框架认为市场长期存在 [[mean-reversion|均值回归]]。当市场显著低于长期均值时，应提高组合弹性；当市场显著高于均值时，应降低组合弹性。

这里的“弹性”主要来自所持公司股价波动率和业务弹性。高弹性公司在低位回归中放大收益，低弹性公司在高位回撤中减少损失。

### 2. 商业模式决定个股仓位上限

商业模式越稳定、越能理解、变量越少，单股仓位上限越高。风险不是单纯股价下跌，而是自己无法预测、无法判断的基本面变化。

因此，[[guigu-business-model-screen|商业模式筛选]] 不只是选股工具，也是仓位上限工具。

### 3. 价格决定实际仓位

好商业模式只是决定仓位上限，不等于任何价格都能买到上限。实际仓位取决于价格是否足够低，以及 [[look-through-return-rate|穿透回报率]] 是否足够高。

龟龟素材中的经验门槛是：推升到较高仓位时，往往要求价格接近 5 年低位，并且穿透回报率达到 6%-7% 以上。若绝对回报率不足，即使企业优秀，也不应推升过多仓位。

### 4. 同源风险要合并限制

多个公司看似不同，如果本质押注同一行业、同一宏观变量、同一政策风险，就要合并计算仓位。持有地产链仓位过高时，就不应继续把另一个地产股当成独立机会。

这与 [[diversification|分散化]] 的区别在于：分散不是看名字数量，而是看风险来源数量。

## 弹性差的执行顺序

市场位置高时，降低弹性的顺序可以是：

```text
高波动率股票 -> 优质低波动率股票 -> 固收 / 现金等价物 -> 降低整体仓位
```

市场位置低时，反向提高弹性，但前提是仍然通过商业模式和价格检查。

## 与个人分档仓位法的关系

[[tiered-position-sizing-template]] 解决“这笔机会现在在哪一档”；龟龟仓位管理进一步补充“当前市场环境下，应优先给哪类资产弹性”。

可以把两者合并成一个顺序：

1. 先判断市场位置，决定组合整体弹性方向。
2. 再用商业模式决定单股最高可承受仓位。
3. 再用价格和穿透回报率决定当前档位。
4. 最后合并同源风险，检查是否超出组合上限。

## 对我现有框架的价值

我已有的 [[personal-position-sizing-framework]] 更偏凯利、安全边际和回撤约束；龟龟仓位管理补上了两个具体执行变量：

- 在市场高低位之间动态切换个股弹性；
- 用商业模式质量作为单股仓位上限，而不是只凭主观喜欢程度加仓。

这能防止两个错误：

- 低位时全持低波动资产，导致错失均值回归弹性；
- 高位时仍持高波动资产，导致回撤放大。

## 最小执行卡片

每次给高股息或个股仓位升级前，至少问：

1. 当前市场位置偏高、偏低还是中性？
2. 这家公司属于高弹性还是低弹性？
3. 商业模式是否足以支持这个仓位上限？
4. 当前价格是否给到 6%-7% 以上的保守穿透回报？
5. 这笔仓位和现有持仓是否押同一个风险？

如果第 3 或第 5 题答不清，即使价格便宜，也不应该升到高仓位。

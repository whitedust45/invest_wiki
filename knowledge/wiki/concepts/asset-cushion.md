---
id: asset-cushion
title: 存量资产垫 / Asset Cushion
type: concept
domain: valuation
topic: [asset-cushion, cigar-butt-investing, intrinsic-value, margin-of-safety]
tags: [静态价值型烟蒂, 清算价值, 资产负债表, 低估值]
status: active
version: 2026-07-05
supersedes: []
superseded_by: []
see_also: [cigar-butt-investing, static-value-cigar-butt-framework, positive-cash-inflow, intrinsic-value, margin-of-safety, value-investing]
sources:
  - type: personal
    context: 用户提供的静态价值型烟蒂因子1截图素材，详见来源页 2026-07-05-static-cigar-butt-factor-asset-cushion
    date: 2026-07-05
summary: 存量资产垫用当前可变现资产扣除真实负债后的价值衡量烟蒂股的静态安全边际
---

# 存量资产垫 / Asset Cushion

## 定义

[[asset-cushion|存量资产垫]] 是 [[cigar-butt-investing|烟蒂投资]] 中的第一道过滤器：从公司当前资产负债表出发，判断可变现资产在扣除真实负债后，是否已经接近或大于当前市值。

它衡量的是偏清算视角的 [[intrinsic-value|内在价值]]，不是企业未来成长价值。

## 核心问题

分析存量资产垫时，要回答三个问题：

1. 资产端哪些可以较确定地变成现金；
2. 负债端哪些必须真实偿付，哪些只是经营性占款或会计科目；
3. 扣除真实负债后的可变现资产，是否相对市值形成足够 [[margin-of-safety|安全边际]]。

## 资产垫天梯

截图素材给出一个新手可用的量化排序：

| 等级 | 粗略口径 | 含义 |
|---|---|---|
| T0 | 现金等价物 - 总负债 > 总市值 | 最强资产垫，假设即刻清算也有较强保护 |
| T1 | 现金等价物 - 有息负债 > 总市值 | 现金资产足以覆盖金融负债并超过市值，但仍需理解经营性负债 |
| T2 | 流动资产 - 总负债 > 总市值 | 需要判断应收、存货、待售资产的折价和兑现风险 |
| 传统定义 | 流动资产 - 有息负债 > 总市值 | 口径更宽，容易高估资产垫 |

这个天梯只是入门排序，不应被当成机械公式。真正重要的是理解资产和负债的本质。

## 为什么优先看现金等价物

现金等价物、短期理财、定期存款等通常比应收账款、存货、固定资产更容易兑现。固定资产、应收账款、存货即使账面金额很高，也可能需要打折、等待、诉讼或承担处置成本。

因此，静态烟蒂更偏好“真钱在手”的资产垫，而不是只靠账面净资产支撑的低 PB。

## 典型误判

- 把无法出售或出售折价很大的固定资产按账面价值计算；
- 把难以回收的应收账款视为现金；
- 忽略即将到期的有息负债、租赁负债或表外义务；
- 把经营性负债一律当作无需偿还，从而高估清算价值；
- 只看低 PB，而不拆资产质量。

## 与正现金流入的关系

[[asset-cushion|存量资产垫]] 是“现在已经有的安全边际”，[[positive-cash-inflow|正现金流入]] 是“未来不要把安全边际烧掉”。

如果资产垫很强但现金流持续为负，企业会在等待市场修复或资产兑现时不断消耗清算价值，最终变成价值陷阱。

## 与现有框架的关系

- 对 [[value-investing|价值投资]]：它是格雷厄姆式烟蒂股路径，而不是优质企业长期复利路径。
- 对 [[intrinsic-value|内在价值]]：它提供一种静态、资产负债表驱动的估值锚。
- 对 [[margin-of-safety|安全边际]]：它要求安全边际来自可验证资产，而不是未来故事。
- 对 [[static-value-cigar-butt-framework|静态价值型烟蒂投资框架]]：它是第一关，未通过则不进入后续现金流和兑现逻辑判断。

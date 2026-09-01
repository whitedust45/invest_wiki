---
name: static-cigar-butt-stock-decision
description: 静态价值型烟蒂个股/批量过滤器。用于用户要求“用静态烟蒂/烟蒂投资/存量资产垫/正现金流入/特殊资产/清算价值/低 PB 资产折价”审查公司、判断是否符合烟蒂框架、批量筛选资产型低估候选或阅读年报做资产垫穿透时。
---

# 静态价值型烟蒂个股决策

## 定位

本 skill 只做静态价值型烟蒂框架下的个股/批量过滤，不给投资建议、不输出下单指令、不替代组合阶段判断。

目标是判断一家公司是否具备：

1. 当前可确认的 [[asset-cushion|存量资产垫]]；
2. 等待兑现期间不继续消耗资产垫的 [[positive-cash-inflow|正现金流入]]；
3. 能让资产价值回到股东手中的兑现路径。

它与 `.agents/skills/guigu-cashflow-stock-decision/` 分工不同：龟龟 skill 筛高分红现金流公司；本 skill 筛资产折价、清算价值、特殊资产和深度价值机会。

## 必读来源

执行前至少读取：

- `knowledge/wiki/portfolios/static-value-cigar-butt-framework.md`
- `knowledge/wiki/concepts/cigar-butt-investing.md`
- `knowledge/wiki/concepts/asset-cushion.md`
- `knowledge/wiki/concepts/positive-cash-inflow.md`
- `knowledge/wiki/sources/2026-07-05-static-cigar-butt-factor-asset-cushion.md`
- `knowledge/wiki/sources/2026-07-05-static-cigar-butt-factor-positive-cash-inflow.md`
- `knowledge/wiki/portfolios/high-dividend-cashflow-watchlist.md`

若用户问到某家公司，按 `/query` 的知识库检索规则查实体页、候选池页、历史分析页。涉及价格、市值、股本、现金、总负债、有息债务、受限资产、应收、存货、经营现金流、分红、回购、资产处置等数据时，必须联网、读取用户提供年报，或引用本地可信页面，并标注日期和置信度。

## 核心漏斗

```text
候选公司
  -> 存量资产垫筛
  -> 资产质量折价筛
  -> 正现金流入筛
  -> 资产兑现逻辑筛
  -> 执行档位
```

卡在哪一关就停在哪一关。不要因为公司“好”、股息率高、PB 低，就跳过资产质量折价。

详细规则见 [framework.md](references/framework.md)。

## 数据与边界

必须分离“事实数据”“框架判断”“观点”。所有市场数据都要标注时间点。

同一公司 A/H/B 多证券必须分开计算，不能混用市值、价格、分红税费、币种。穿透到资产垫时要说明财报币种、市场币种和汇率假设。

年报优先级高于三方摘要；三方数据只能做初筛。用户提供年报时，优先逐项读资产负债表、现金流量表、附注和受限资产/应收账款/存货/借款/资产减值说明。

最终输出必须提醒：本结论是静态烟蒂框架过滤结果，不是投资建议或下单指令；是否执行仍需回到 dashboard、现金垫、组合风险和用户自己的决策。

## 执行档位

只输出以下档位之一：

- `PASS-SPECIAL`：资产垫硬、现金流不烧钱、兑现路径明确，可进入特殊资产候选；不是买入建议。
- `PASS-SMALL`：资产垫或兑现路径有瑕疵，但风险可上限化，只适合小仓候选；不是买入建议。
- `WAIT-PRICE`：公司有资产/现金流价值，但当前价格未进入烟蒂区。
- `WAIT-DATA`：缺关键数据，如受限资金、有息债务、应收质量、资产折价、经营现金流或兑现路径。
- `NEAR-T2`：账面接近 T2，但依赖应收、存货、物业、股权投资等折价判断；通常继续观察或等价格。
- `NOT-CIGAR-BUTT`：好公司或现金流公司，但不符合静态烟蒂，例如资产垫远低于市值。
- `REJECT`：资产垫不足、持续烧钱、负债/治理/资产质量不可上限化。

## 输出模板

单股和批量模板见 [templates.md](references/templates.md)。批量模式只做漏斗筛选和优先级，不写完整研报。

## 写入规则

用户要求沉淀、产生高价值新判断、或结论影响观察池时：

- 写入 `knowledge/wiki/analyses/` 或更新相关 `entities/`、`portfolios/` 页面；
- 更新 `knowledge/wiki/index.md` 和 `knowledge/wiki/log.md`；
- 若要加入或移出 dashboard 候选项，必须先说明代码、名称、市场和原因，得到用户确认后再改 `apps/dashboard/`。

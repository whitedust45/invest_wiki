---
description: 操作日志，按时间倒序记录所有变更
updated: 2026-06-26
---

# 操作日志

> 本文件是结构性日志，不遵循 wiki 页面的完整 frontmatter 规范。
> 格式：`## [YYYY-MM-DD] {操作类型} | {标题}`
> 操作类型：`init` / `ingest` / `query` / `lint` / `update` / `curate`
> 快速查看：`grep "^## \[" log.md | tail -10`

## [2026-06-26] update | IC/IM 估值贴水打开页面自动刷新

- 新增 `tools/dashboard/serve_dashboard.py`：
  - 在原静态页面基础上提供本地 API `/api/ic-im-valuation`
  - API 会实时运行 `tools/dashboard/update_ic_im_valuation.py`，更新并返回 IC/IM 估值与贴水 JSON
  - 默认服务地址仍为 `http://127.0.0.1:8775/apps/dashboard/index.html`
- 更新 `apps/dashboard/`：
  - 页面打开时优先请求本地 API 自动刷新 IC/IM 估值和贴水
  - 若 API 不可用，自动回退读取已有 `data/ic-im-valuation.json`
  - 静态资源版本号更新为 `20260626-auto-icim`

## [2026-06-26] update | 仪表盘两轮体验审查与修复

- 第一轮审查并修复：
  - 压缩总览首屏密度，减少大 hero 对首屏空间的占用
  - 将 IC/IM 贴水明细改为默认折叠，保留摘要和换月提醒优先展示
  - 优化持仓估值工具条的主次按钮层级，突出“同步全部”
- 第二轮审查并修复：
  - 手机端 KPI 与估值数值改为两列展示，减少纵向滚动
  - 提升底部导航文字可读性，并给当前模块图标增加选中底色
  - 区分顶部“重算今日”和“清空”的视觉层级，降低误操作感
  - 已在 390px 手机宽度验证无横向溢出，控制台无错误

## [2026-06-26] update | 持仓价格同步扩展到美股

- 更新 `apps/dashboard/`：
  - 持仓估值的批量按钮从“同步全部A股”改为“同步全部”
  - 当前价格单位从“元/单位”简化为“元”
  - QQQ / QLD 等普通美股 ETF 支持通过本地价格 JSON 同步价格
  - 美股价格使用人民币折算价，避免当前市值估算口径与“万元”单位冲突
- 更新 `tools/dashboard/update_position_quotes.py`：
  - 支持读取 Yahoo chart 最新日收盘价，并用 USD/CNY 折算为人民币元
  - `position-quotes.json` 可同时包含 A 股和美股价格；历史日线模式自动跳过非 A 股代码

## [2026-06-26] update | 持仓估值新增批量同步与单位标注

- 更新 `apps/dashboard/`：
  - 各模块“持仓估值”新增“同步全部A股”按钮，批量同步当前模块中可识别的 A 股价格
  - 当前价格输入框标注单位“元/单位”，当前市值输入框标注单位“万元”
  - 压缩价格和市值输入框宽度，降低持仓估值表横向占用
  - 静态资源版本号更新为 `20260626-sync-all`

## [2026-06-26] update | 仪表盘新增每日净值收益率曲线

- 更新 `apps/dashboard/`：
  - 历史趋势升级为“总资产金额 / 净值累计收益率”双曲线
  - 支持日 / 周 / 月三个展示维度，底层仍保存每日快照
  - 页面打开后自动检查并补齐缺失日期快照；“重算今日”用于覆盖当天快照
  - 收益率按总资产净值法计算，`转入 / 转出` 作为外部现金流剔除，`买入 / 卖出` 视为标的仓位变化
  - 新增 `内部划入 / 内部划出` 动作，用于维护现金余额时记录账户内部换仓，不计入外部现金流
  - A 股历史收盘价使用腾讯日线接口 `appstock/app/fqkline/get`，口径参考 `mpquant/Ashare`；暂未自动取价资产沿用上一条可用估值
- 更新 `tools/dashboard/update_position_quotes.py`：
  - 新增 `--history-days`，可生成 `apps/dashboard/data/position-history.json` 作为 A 股日收盘历史保底数据

## [2026-06-26] update | 仪表盘新增持仓估值层

- 更新 `apps/dashboard/`：
  - 新增独立持仓估值数据层：投资流水记录历史交易，持仓估值记录当前价格、市值和浮盈亏
  - 总资产、资产结构、模块金额和报表优先使用当前市值；未估值标的回退到流水净投入
  - 各模块新增“持仓估值”面板，可维护当前价格、当前市值和估值备注
  - 持仓估值新增 A 股“同步价格”入口，参考 OpenStock 与 `mpquant/Ashare` 的数据源分层思路；当前采用新浪 A 股行情主源、腾讯行情备用源做 best-effort 同步
  - 新增 `tools/dashboard/update_position_quotes.py`，可生成 `apps/dashboard/data/position-quotes.json`，页面支持读取本地价格 JSON 更新估值层
  - 同步价格只更新当前价格和数据来源，不覆盖手工市值；手工市值仍保持最高优先级
  - 报表页新增“持仓估值总表”，集中展示模块、标的、数量、净投入、当前市值、浮盈亏和更新时间
  - 示例数据新增持仓估值样例，清空数据时同步清除估值层
  - 静态资源版本号更新为 `20260626-positions-json`

## [2026-06-26] update | 仪表盘升级为财富工作台与报表页

- 更新 `apps/dashboard/`：
  - 总览升级为净值工作台，新增账户树、风险清单、目标进度和更强的阶段摘要
  - 底部导航新增“报表”页，集中展示资产结构、目标差距、历史趋势、策略矩阵、标的排行和最近流水
  - 模块页改为工作区布局：左侧录入与持仓分布，右侧投资流水
  - 投资流水新增搜索、动作筛选、分类筛选、CSV 导出和 JSON 导出
  - 静态资源版本号更新为 `20260626-wealth`

## [2026-06-24] update | 仪表盘新增标的记忆功能

- 更新 `apps/dashboard/`：
  - 录入表单从本地投资流水中自动提取“标的代码 + 标的名称”配对
  - 标的代码和标的名称输入框新增历史下拉建议
  - 选择代码时自动带出名称，选择名称时自动带出代码，例如 `000568` 与 `泸州老窖`
  - 记忆数据仍存放在本地 `localStorage` 账本中，不引入外部服务

## [2026-06-24] update | 仪表盘补充 IC/IM 估值与贴水数据展示

- 更新 `apps/dashboard/`：
  - 页面打开后自动读取本地 `data/ic-im-valuation.json`
  - 总览估值区新增数据来源卡，展示中证指数、东方财富兜底、PE 历史和 xags 贴水来源
  - IC/IM 模块新增“估值与贴水数据”面板，展示 IC/IM 的 PE、PB、PE 分位、PB 分位、近远月年化贴水和交割提醒
  - IC/IM 模块新增贴水明细表，展示 IC/IM 共 8 行合约数据
  - HTML 静态资源加入版本号，避免浏览器缓存旧版 `app.js`

## [2026-06-24] update | 高分红模块分类口径简化

- 更新 `apps/dashboard/`：
  - 高分红模块下拉分类改为：现金、高分红股票、类现金、债券
  - 旧分类自动兼容映射：硬现金→现金，国债逆回购→类现金，白酒/五粮液/泸州老窖/其他A股高分红→高分红股票
  - 示例数据改为新分类口径，避免把具体股票名称固定成分类

## [2026-06-24] update | 策略仪表盘补充模块图像与差异化图标

- 更新 `apps/dashboard/`：
  - 总览四张策略卡新增模块小图标和迷你 SVG 缩略图
  - 高分红模块新增“现金流瀑布”视觉图
  - QQQ 模块新增“右尾趋势仪表”视觉图，体现 120 日均线纪律
  - 深度Put 模块新增“黑天鹅保护曲线”视觉图，体现保费与极端保护
  - IC/IM 模块新增“贴水与保证金雷达”视觉图，体现 PB、资金池和期限梯度
- 两轮审查后优化：
  - 第一轮补齐总览策略卡缩略图和移动端视觉卡单列规则
  - 第二轮压缩宽屏视觉图高度，减少个人工具使用时的滚动负担

## [2026-06-24] update | 策略仪表盘升级为五模块差异化工作台

- 更新 `apps/dashboard/`：
  - 底部导航改为五个模块：总览、高分红、QQQ、深度Put、IC/IM
  - 将旧模块流水兼容迁移到新模块：`cashflow -> dividend`、`growth -> qqq/put`、`futures -> ic`
  - 高分红模块突出股息覆盖、白酒仓位和流动安全垫
  - QQQ 模块突出 5% 起步线、10% 目标线和 QLD 120 日均线策略
  - 深度Put 模块突出年度保险预算、预算使用率和合约备忘
  - IC/IM 模块突出资金池、PB 分位、风险度和换月提醒，并支持模块内读取估值 JSON
- 已用示例数据做两轮审查，并据审查结果优化录入标题、模块入口和移动端五栏布局

## [2026-06-23] update | 策略仪表盘升级为四模块个人投资账本

- 重构 `apps/dashboard/`：
  - 改成底部四栏导航：总览、防守现金流、右尾保险、期货增强
  - 新增本地投资流水账，支持按模块新增、编辑、删除每笔投资记录
  - 总览页从模块流水自动汇总总资产、资产结构、股息/利息收入、期货风险和动作建议
  - 保留估值 JSON 读取、IC/IM 贴水表和移仓提醒
- 视觉改造为移动端优先的个人 App 风格，并保留桌面多栏展示

## [2026-06-23] update | IC/IM 估值 JSON 增强东方财富兜底与移仓提醒

- 更新 `tools/dashboard/update_ic_im_valuation.py`：
  - 当前 PB 缺失时尝试东方财富 `push2` 行情字段 `f167/f152` 作为 best-effort 兜底
  - 东方财富接口失败时不中断主流程，并在 JSON 中记录失败信息
  - 在贴水合约数据中新增理论交割日、合约级移仓提醒和品种级最近合约提醒
- 更新 `apps/dashboard/`：
  - 估值卡片显示当前 PE/PB 来源
  - 贴水表新增交割日和移仓提醒列
  - 移仓提醒口径：距理论交割日 10 天进入观察窗口，5 天进入强提醒
- 更新 `apps/dashboard/data/README.md`：补充东方财富兜底与移仓提醒口径

## [2026-06-23] ingest | 华泰期货 IC 指增白皮书上下篇

- 保存网页副本：
  - `raw/2022-06-22-7hcn-ic-zeng-bai-pi-shu-shang.html`
  - `raw/2022-07-06-sina-ic-zeng-bai-pi-shu-xia.html`
- 新增来源页：[[2022-06-22-huatai-ic-index-enhancement-whitepaper-part-1]]、[[2022-07-06-huatai-ic-index-enhancement-whitepaper-part-2]]
- 更新 [[ic-im-roll-discount-strategy]]：补充华泰 IC 指增白皮书对贴水本质、展期、近远月选择、资产阶段和跨品种迁移的外部参照
- 更新 `wiki/index.md`：收录新增来源页

## [2026-06-27] ingest | IC/IM 合约规则与仪表盘口径

- 新增概念页：[[stock-index-futures]]、[[stock-index-futures-contract-rules]]
  - 记录中金所 IC/IM 合约乘数、合约月份、最后交易日、交割日、保证金和现金交割规则
  - 明确仪表盘名义敞口、杠杆比例、风险度、保证金率和移仓提醒的计算口径
- 更新 `schema/glossary.md`：新增术语 `stock-index-futures`
- 更新 `wiki/index.md`：收录新增概念页并刷新 updated 日期
- 规则来源：中金所 IC 合约表与 IM 合约表，抓取时间 2026-06-27

## [2026-06-23] update | 拆分超长文档与新增策略仪表盘网页

- 拆分 [[personal-hybrid-barbell-matrix]] 的执行层内容到 [[personal-hybrid-barbell-execution-dashboard]]
- 拆分 [[ic-im-roll-discount-strategy]] 的脚本与自然语言参数说明到 [[ic-im-roll-discount-operations-manual]]
- 将 2026-05 历史操作日志归档到 [[log-archive-2026-05]]，主日志只保留近期变更
- 新增根目录策略网页：`apps/dashboard/`，用于手工填数后自动计算阶段、缺口和操作提示
- 新增 `tools/dashboard/update_ic_im_valuation.py`：从中证指数官网公开接口生成 IC/IM 估值 JSON；前端可读取当前 PE/PB 与 PE 分位，PB 分位仅在本地历史 CSV 可用时覆盖手工输入
- 扩展同一脚本的贴水抓取：复用 `xags.stephenslab.top/basis/` 的公开代理接口，写入 IC/IM 各合约贴水、年化贴水和剩余天数；前端估值区新增贴水表

## [2026-06-23] ingest | QLD/QQQ 120 日均线战术策略

- 新增组合页：[[qld-qqq-120ma-tactical-strategy]]
  - 记录 QQQ 站上 120 日均线后买入 QLD、跌破后卖出的独立战术策略
  - 状态设为 draft：用户提到的原始论文尚未补齐，当前以 ProShares 官方 QLD 说明、杠杆 ETF 复利效应论文与移动均线规则论文作背景来源
  - 明确该策略不是 [[personal-hybrid-barbell-matrix]] 中 QQQ 长期右尾仓的简单替代，后续需单独回测和设定仓位上限
- 更新 `wiki/index.md`：收录新增组合页
- 更新 [[personal-hybrid-barbell-matrix]]：see_also 补入新策略页

## [2026-06-23] update | 个人混合杠铃矩阵执行层补强

- 更新组合页：[[personal-hybrid-barbell-matrix]]
  - 新增“三张执行表”：生活现金流表、组合风险仪表盘、极端行情动作卡
  - 新增“从 0 开始的构建顺序”：先硬现金，再高分红 + QQQ，再期货专项资金池，最后引入 IC/IM 与 SPY put
  - 明确 600 万终局配置不能机械等比例缩小，IC/IM 与 SPY put 因最小有效规模和执行成本应后置
  - 追加 dialogue 来源，version / effective_date 更新为 2026-06-23
- 更新 `wiki/index.md`：刷新 [[personal-hybrid-barbell-matrix]] 摘要与 updated 日期

## [2026-06-18] ingest | Berkshire 官方股东信补强核心价投主线

- 新增 raw 官方素材：`raw/assets/berkshire-official/` 下 12 份 Berkshire 官方股东信/年度报告原始文件（1983、1984、1986、1992、1995、1996、1999、2001、2008、2011、2014、2019）
- 新增来源页：[[2026-06-18-berkshire-official-shareholder-letter-archive]]
- 新增分析页：[[buffett-shareholder-letters-core-value-investing]]
- 更新基础概念页：[[value-investing]]、[[intrinsic-value]]、[[margin-of-safety]]、[[fcf]]、[[moat]]、[[buyback]]、[[insurance-float]]、[[capital-allocation]]
- 更新 `wiki/index.md`：收录新增 source / analysis，并将 updated 改为 2026-06-18
- lint 修复：将日志中误写为 wikilink 的 `index.md` 改为普通路径；将 [[overview]] 从超期 draft 提升为 active

## [2026-06-04] update | 跨资产 carry-momentum 框架与 IC/IM 前瞻闸门迁移

- 新增分析页：[[cross-asset-carry-momentum]]（因子组合四层构建 + vol-targeting + 缓冲带，含迁移到 IC/IM 的观点）
- 更新组合页：[[ic-im-roll-discount-strategy]]，新增"前瞻闸门与缓冲带（观点，待实盘验证）"一节；see_also 补 [[cross-asset-carry-momentum]]；补 dialogue 来源；version / effective_date 改为 2026-06-04
- 更新 `wiki/index.md`：分析表收录 [[cross-asset-carry-momentum]]
- 承接 [2026-06-04] ingest 的因子主题，把"动量+carry"从概念延伸到组合构建与个人操作迁移

## [2026-06-04] ingest | 动量与 Carry 两个因子系统学习沉淀

- 新增概念页：[[factor-investing]]（“什么是因子”总纲）、[[carry]]（Carry 因子）
- 更新概念页：[[momentum]]，新增“作为因子的动量”一节（横截面/时序定义、动量崩盘、与 carry 互补），并补 topic / see_also / version
- 更新组合页：[[ic-im-roll-discount-strategy]]，see_also 补入 [[carry]]、[[factor-investing]]，正文增加“贴水即正 carry”的因子视角说明
- 更新 `schema/glossary.md`：新增术语 `factor-investing`、`carry`，并扩充 `momentum` 同义词
- 更新 `wiki/index.md`：收录 [[factor-investing]]、[[carry]]，刷新 momentum 摘要，updated 改为 2026-06-04

## [2026-06-01] curate | 修正 personal-hybrid-barbell-matrix 资金分配

- 模式：§1 修正模式（数据更正，页面整体仍有效）
- 主操作页面：[[personal-hybrid-barbell-matrix]]
  - SPY put 年度预算 10 万 → 12 万（占总组合 1.7% → 2%）
  - IC / IM 滚贴水 200 万 → 198 万（差额从结构收益仓挤出）
  - 资金结构表用删除线 + 加粗保留旧数据，附 [2026-06-01 更正] 说明段
  - frontmatter 新增 type: personal 来源条目（2026 年初回测复盘）
  - version / effective_date 同步更新为 2026-06-01
- 连锁更新：
  - `wiki/index.md` 补入此前漏收的 personal-hybrid-barbell-matrix 条目（顺手修复）
  - `wiki/index.md` updated 字段同步为 2026-06-01
- 反向引用核查：
  - [[ic-im-roll-discount-strategy]] §结合个人组合 段落仅文字引用，未提具体数字，无需联动
  - [[barbell-strategy]] §与个人版本的关系 同上
- 待跟进：
  - 下一轮 lint 关注：本次为本框架首次实质性修订，可作为 supersedes 链测试样本（暂未拆新版页面，故不启用 supersedes）

## 历史归档

- 2026-05 历史日志见 [[log-archive-2026-05]]。

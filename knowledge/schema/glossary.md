# 术语表（Glossary）

知识库受控词表。`frontmatter.topic` 字段从这里取值。

**维护原则**：新术语入库时，先在这里登录再用；同义词（中英文、缩写）要列全，这是查询同义词扩展的依据。

**格式**：
```
### {canonical-id} — {中文名} / {英文名}
**同义词**：<中英对照、缩写、口语化叫法>
**定义**：<一句话说清>
**相关页**：[[id]]（待建 / 已建）
```

> 每条术语都必须包含以上四个字段。`相关页` 在对应 wiki 页面建好后更新为"已建"。

---

## 估值与定价

### intrinsic-value — 内在价值 / Intrinsic Value

**同义词**：内在价值、真实价值、公允价值、Fair Value

**定义**：基于未来现金流折现得出的企业真实价值，独立于市场价格

**相关页**：[[intrinsic-value]]（已建）

### margin-of-safety — 安全边际 / Margin of Safety

**同义词**：MOS、安全垫

**定义**：买入价格低于内在价值的差额，是价值投资的核心防御机制

**相关页**：[[margin-of-safety]]（已建）

### dcf-model — 现金流折现 / DCF Model

**同义词**：DCF、Discounted Cash Flow、折现现金流模型

**定义**：将企业未来自由现金流按折现率折算为现值的估值方法

**相关页**：[[dcf-model]]（已建）

### pe-ratio — 市盈率 / P/E Ratio

**同义词**：PE、市盈率、Price-to-Earnings、PE-TTM、PE-Forward

**定义**：股价除以每股收益，衡量市场对每单位盈利的定价

**相关页**：[[pe-ratio]]（待建）

### pb-ratio — 市净率 / P/B Ratio

**同义词**：PB、市净率、Price-to-Book

**定义**：股价除以每股净资产，衡量市场对净资产的溢价程度

**相关页**：[[pb-ratio]]（待建）

### ev-ebitda — EV/EBITDA

**同义词**：企业价值倍数、EV/EBITDA

**定义**：企业价值除以息税折旧摊销前利润，跨资本结构可比的估值指标

**相关页**：[[ev-ebitda]]（待建）

### fcf — 自由现金流 / Free Cash Flow

**同义词**：FCF、自由现金流

**定义**：经营现金流减去资本支出后可自由分配给股东和债权人的现金

**相关页**：[[fcf]]（已建）

### gaap-vs-operating-earnings — GAAP 与经营利润 / GAAP vs. Operating Earnings

**同义词**：GAAP利润与经营利润、会计利润与经营利润、Operating Earnings

**定义**：区分会计口径下的总利润与主营经营现实的分析框架，常用于识别持仓波动和减值对报表的扭曲

**相关页**：[[gaap-vs-operating-earnings]]（已建）

### look-through-return-rate — 穿透回报率 / Look-through Return Rate

**同义词**：穿透回报、穿透收益率、股东穿透回报、Look-through Return

**定义**：衡量企业利润中能通过现金分红、注销型回购等方式确定回到股东账户的真实回报率

**相关页**：[[look-through-return-rate]]（已建）

### distributable-cash-balance — 可支配现金结余 / Distributable Cash Balance

**同义词**：可支配现金、真实可支配现金、现金型利润、真钱白银结余

**定义**：从真实现金进出判断企业利润中可用于分红、回购或留存配置的现金结余

**相关页**：[[distributable-cash-balance]]（已建）

---

## 投资方法论

### value-investing — 价值投资 / Value Investing

**同义词**：价投、格雷厄姆式投资

**定义**：寻找市场价格低于内在价值的证券，以安全边际买入并长期持有

**相关页**：[[value-investing]]（已建）

### growth-investing — 成长投资 / Growth Investing

**同义词**：成长股投资

**定义**：投资于营收/利润高速增长的公司，愿意为增长支付溢价

**相关页**：[[growth-investing]]（已建）

### momentum — 动量 / Momentum

**同义词**：趋势跟踪、Trend Following、动量策略、动量因子

**定义**：基于价格趋势延续的假设，买入上涨趋势中的资产

**相关页**：[[momentum]]（已建）

### factor-investing — 因子投资 / Factor Investing

**同义词**：因子、Factor、多因子、Factor Investing

**定义**：把收益拆解到可被定价的系统性来源（如价值、动量、carry）并主动暴露或规避

**相关页**：[[factor-investing]]（已建）

### managed-volatility — 管理波动率 / Managed Volatility

**同义词**：Managed Volatility、vol-targeting、volatility targeting、波动率目标、波动率管理、动态杠杆、volatility timing

**定义**：用预测波动率动态缩放仓位或杠杆，低波动时提高风险暴露、高波动时降敞口以平滑回撤

**相关页**：[[managed-volatility]]（已建）

### carry — Carry 因子 / Carry

**同义词**：Carry、套息、利差收益、持有收益、贴水（股指期货语境）

**定义**：假设价格不变时，仅靠持有资产本身就能拿到的收益率（利差、股息、期限结构斜率、基差贴水）

**相关页**：[[carry]]（已建）

### mean-reversion — 均值回归 / Mean Reversion

**同义词**：均值回复、回归均值

**定义**：价格/估值偏离长期均值后倾向于向均值回归的统计现象

**相关页**：[[mean-reversion]]（已建）

### contrarian — 逆向投资 / Contrarian Investing

**同义词**：逆向、反向操作

**定义**：在市场恐慌时买入、狂热时卖出，与市场情绪反向操作

**相关页**：[[contrarian]]（已建）

### asset-allocation — 资产配置 / Asset Allocation

**同义词**：配置、大类资产配置、AA

**定义**：在不同资产类别（股/债/商品/现金）间分配资金的决策

**相关页**：[[asset-allocation]]（已建）

### rebalance — 再平衡 / Rebalancing

**同义词**：再平衡、调仓

**定义**：定期或基于阈值将组合比例恢复到目标配置的操作

**相关页**：[[rebalance]]（已建）

### stock-index-futures — 股指期货 / Stock Index Futures

**同义词**：股指期货、指数期货、IC、IM、中证500股指期货、中证1000股指期货、Stock Index Futures

**定义**：以股票指数为标的、按合约乘数放大名义敞口并采用保证金交易的期货合约

**相关页**：[[stock-index-futures]]（已建）

### kelly-criterion — 凯利公式 / Kelly Criterion

**同义词**：Kelly、凯利准则

**定义**：根据赔率和胜率计算最优下注比例的数学公式

**相关页**：[[kelly-criterion]]（已建）

### dollar-cost-averaging — 定投 / Dollar-Cost Averaging

**同义词**：DCA、定期定额投资、定投策略

**定义**：以固定金额定期买入资产，通过摊平成本降低择时风险的投资方法

**相关页**：[[dollar-cost-averaging]]（已建）

### capital-allocation — 资本配置 / Capital Allocation

**同义词**：资本配置、资金分配、资本再投资、Capital Deployment

**定义**：管理层在留存收益、分红、回购、并购与证券投资之间分配资本以最大化长期每股价值的能力

**相关页**：[[capital-allocation]]（已建）

### barbell-strategy — 杠铃策略 / Barbell Strategy

**同义词**：杠铃策略、Taleb Barbell、Barbell Portfolio、杠铃式配置

**定义**：一种把大部分资金放在极保守端、少量资金放在极激进端，主动回避中间风险暴露的配置方法

**相关页**：[[barbell-strategy]]（已建）

### antifragile — 反脆弱 / Antifragile

**同义词**：反脆弱、Antifragile、从波动中受益

**定义**：一种不仅能承受波动和冲击，而且会因波动、不确定性与混乱上升而受益的结构特征

**相关页**：[[antifragile]]（已建）

### convexity — 凸性 / Convexity

**同义词**：凸性、Convexity、正凸性、非线性收益结构

**定义**：指收益或损失对冲击并非线性响应，而会出现“下行有限、上行加速”或相反的非线性放大特征

**相关页**：[[convexity]]（已建）

### elasticity-gap — 弹性差 / Elasticity Gap

**同义词**：弹性套利、组合弹性、市场位置弹性、Elasticity Gap

**定义**：根据市场位置在高波动和低波动资产之间切换，以利用均值回归中不同弹性带来的收益差或损失缓冲

**相关页**：[[elasticity-gap]]（已建）

---

## 公司分析

### moat — 护城河 / Economic Moat

**同义词**：经济护城河、竞争优势、Competitive Advantage

**定义**：企业抵御竞争对手侵蚀利润的持久结构性优势

**相关页**：[[moat]]（已建）

### roe — 净资产收益率 / ROE

**同义词**：ROE、Return on Equity

**定义**：净利润除以股东权益，衡量公司利用自有资本创造利润的效率

**相关页**：[[roe]]（已建）

### roic — 投入资本回报率 / ROIC

**同义词**：ROIC、Return on Invested Capital

**定义**：税后营业利润除以投入资本，衡量企业全部投入资本的回报效率

**相关页**：[[roic]]（待建）

### capex — 资本支出 / Capital Expenditure

**同义词**：CapEx、资本开支

**定义**：用于购建固定资产或无形资产的支出

**相关页**：[[capex]]（待建）

### buyback — 回购 / Share Buyback

**同义词**：股票回购、Share Repurchase

**定义**：公司用自有资金在市场买回自家股票并注销，提升每股价值

**相关页**：[[buyback]]（已建）

### dividend — 股息 / Dividend

**同义词**：分红、派息、Dividend Yield（股息率）

**定义**：公司将部分利润以现金形式分配给股东

**相关页**：[[dividend]]（已建）

### business-model-quality — 商业模式质量 / Business Model Quality

**同义词**：商业模式、好生意、商业模式筛选、Business Quality

**定义**：企业赚钱方式在可理解性、现金回收、资本开支、定价权、竞争结构和周期性上的综合质量

**相关页**：[[guigu-business-model-screen]]（已建）

### insurance-float — 保险浮存金 / Insurance Float

**同义词**：浮存金、保险浮存金、Float

**定义**：保险公司先收保费后理赔所形成的可投资资金，其质量取决于承保纪律与赔付控制

**相关页**：[[insurance-float]]（已建）

---

## 宏观经济

### interest-rate — 利率 / Interest Rate

**同义词**：基准利率、Fed Funds Rate、联邦基金利率

**定义**：央行设定的基准借贷成本，影响所有资产的折现率

**相关页**：[[interest-rate]]（待建）

### inflation — 通胀 / Inflation

**同义词**：通货膨胀、CPI、物价上涨

**定义**：一般物价水平的持续上升，侵蚀货币购买力

**相关页**：[[inflation]]（待建）

### business-cycle — 经济周期 / Business Cycle

**同义词**：景气循环、繁荣-衰退周期

**定义**：经济活动在扩张与收缩间交替的周期性波动

**相关页**：[[business-cycle]]（待建）

### credit-cycle — 信用周期 / Credit Cycle

**同义词**：信贷周期、杠杆周期

**定义**：信贷条件在宽松与紧缩间交替的周期

**相关页**：[[credit-cycle]]（待建）

### qe — 量化宽松 / Quantitative Easing

**同义词**：QE、印钞、放水

**定义**：央行通过购买债券等方式向市场注入流动性的非常规货币政策

**相关页**：[[qe]]（待建）

---

## 行为金融与心理

### loss-aversion — 损失厌恶 / Loss Aversion

**同义词**：亏损厌恶

**定义**：人对等额损失的痛苦感约为等额收益快乐感的 2-2.5 倍

**相关页**：[[loss-aversion]]（已建）

### anchoring — 锚定效应 / Anchoring

**同义词**：锚定偏误

**定义**：过度依赖最先接触到的信息（"锚"）做判断的心理偏误

**相关页**：[[anchoring]]（待建）

### confirmation-bias — 确认偏误 / Confirmation Bias

**同义词**：确认偏差、选择性接收

**定义**：倾向于寻找和接受支持已有观点的信息，忽略反面证据

**相关页**：[[confirmation-bias]]（已建）

### disposition-effect — 处置效应 / Disposition Effect

**同义词**：卖盈持亏

**定义**：倾向于过早卖出盈利头寸、过久持有亏损头寸的行为偏误

**相关页**：[[disposition-effect]]（待建）

### fomo — FOMO / Fear of Missing Out

**同义词**：害怕错过、追涨

**定义**：因担心错过上涨行情而冲动买入的心理

**相关页**：[[fomo]]（待建）

### narrative-fallacy — 叙事谬误 / Narrative Fallacy

**同义词**：故事化偏误

**定义**：人倾向于为随机事件构造因果叙事，高估可解释性

**相关页**：[[narrative-fallacy]]（已建）

---

## 风险管理

### position-sizing — 仓位管理 / Position Sizing

**同义词**：仓位、头寸管理

**定义**：决定单一投资在整体组合中占比的方法论

**相关页**：[[position-sizing]]（已建）

### drawdown — 回撤 / Drawdown

**同义词**：最大回撤、Max Drawdown、MDD

**定义**：从历史最高点到最低点的跌幅，衡量最坏亏损情境

**相关页**：[[drawdown]]（已建）

### diversification — 分散化 / Diversification

**同义词**：分散投资、不把鸡蛋放一个篮子

**定义**：通过持有相关性低的多种资产降低组合整体风险

**相关页**：[[diversification]]（已建）

### risk-reward — 风险收益比 / Risk-Reward Ratio

**同义词**：盈亏比、赔率

**定义**：潜在收益与潜在损失的比值，用于评估交易的值博率

**相关页**：[[risk-reward]]（已建）

### tail-risk — 尾部风险 / Tail Risk

**同义词**：黑天鹅风险、极端风险

**定义**：发生概率极低但影响极大的极端事件风险

**相关页**：[[tail-risk]]（待建）

---

## 维护说明

- 新增术语时：必须包含 **同义词 + 定义 + 相关页** 三个字段（加上标题行共四行结构）
- 每次 ingest 入库新文档后，lint 会扫正文关键词和本词表的匹配度
- 术语 id 就是 `frontmatter.topic` 的受控词表条目
- 词表应随知识库成长持续扩充
- 对应 wiki 页面建好后，将"待建"改为"已建"

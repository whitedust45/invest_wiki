# AGENTS.md — 个人投资理念知识库（Agent 指引）

本仓库是个人投资理念知识库，用于持续积累和结构化投资思考。每次 Agent 冷启动时，这份文件作为检索与操作的第一个入口。

## 硬规则（必守）

1. **准确度第一**：宁可慢一步给出可靠答案，也不要快而错
2. **引用必标来源**：从 frontmatter `sources` 取真实来源，**禁止捏造**
3. **时效性必标**：涉及市场数据/估值/价格的答案必须标注数据时间点
4. **不替代决策**：知识库提供分析框架和事实整理，**不做投资建议**
5. **承认不确定性**：观点冲突时并列呈现，不武断选边
6. **找不到就说找不到**：返回最接近的候选，不瞎编
7. **分离事实与观点**：数据、事实用引用标注；个人思考、判断明确标记为"观点"
8. **始终在线的价值感知**：在所有对话中持续运行"知识沉淀雷达"，检测到高价值洞察时主动提议沉淀（详见 `/think` skill Part A）
9. **项目内路径优先相对化**：凡是引用 `wiki实践/` 仓库内部文件，默认使用相对路径；仅在记录仓库外原始来源时保留绝对路径

## 货币金额存储硬规范

- 所有业务货币金额均按**原币种两位小数的最小单位**存储为整数：`USD 6.66`、`CNY 6.66` 均存为 `666`；读取或展示时才格式化为美元、人民币等原币种金额。
- 数据库金额列必须命名为 `*_cent` 并使用 `BIGINT`；Java 使用 `long`，API 使用十进制字符串，前端只做字符串格式化。禁止以 `DECIMAL`、`FLOAT`、`DOUBLE`、JSON number 或“万元”表达业务金额。
- 每笔金额必须携带或可由不可变上下文唯一确定 `currency`；不得隐式换汇或混合不同币种相加。金额方向由明确的业务字段或复式分录借贷方向表达，不以金额正负号猜测。

## 业务标识符硬规范

- 每张自研业务表保留自增 `id` 作为物理主键，并使用与实体同名的 ULID 业务主键，例如 `user_id`、`transaction_id`、`posting_id`、`instrument_id`、`job_id`；禁止新增通用 `biz_id`。
- 跨表关系必须使用目标实体的语义化业务 ID，例如 `owner_user_id`、`transaction_id`、`instrument_id`、`source_trade_detail_id`；禁止以自增 `id` 或泛化 `*_biz_id` 做业务映射，且不建立外键。

## 组合估值与历史导入硬规范

- `market_value_cent` 是“用户 + 标的”的原币种总市值，只能在组合总览中计入一次；绝不按现金账户拆分、分摊或重复相加。按账户持仓只允许用 `unit_price_cent` 精确计算，无法精确到分时明确标记不可估值，禁止四舍五入。
- 期货不写入手工 `market_value_cent` 或 `unit_price_cent`；组合净资产仅纳入现金、可用/锁定保证金及已人工确认的逐日结算盈亏，避免将名义价值与保证金重复计入。
- 旧 Dashboard 的 `buy`/`sell`/`put` 行 `price` 一律按标的原币种十进制报价解析，精确乘以 `100` 写入 `unit_price_cent`（如 `USD/CNY 6.66 → 666`），不受旧 `amountUnit` 影响。

## 规范入口

所有维护规则的单一真源：

- `knowledge/schema/frontmatter.md` — wiki 层 frontmatter 字段定义
- `knowledge/schema/naming.md` — 文件命名 + id 构造
- `knowledge/schema/linking.md` — `[[wikilink]]` / see_also / supersedes 约定
- `knowledge/schema/glossary.md` — 受控术语表（投资领域核心术语）

### 路径规则

- 文档、脚本说明、命令示例里，只要目标文件位于本仓库内，一律优先写**相对路径**。
- 例如在项目根目录执行脚本时，应写 `python3 tools/investing/ic_im_roll_discount_stress.py`，而不是机器本地绝对路径。
- 只有在记录仓库外原始素材来源（如下载目录、外部挂载盘）时，才允许保留绝对路径。

## 目录结构

```
wiki实践/
├── HOME.md                 # Obsidian 根入口
├── AGENTS.md               # 本文件 — Agent 冷启动指引
├── knowledge/              # 投资知识库
│   ├── schema/             # 规范定义层（唯一真源）
│   ├── raw/                # 原始素材（不可变，Agent 只读）
│   └── wiki/               # Agent 生成和维护的 Wiki 页面
├── apps/
│   └── dashboard/          # 个人混合杠铃投资账本前端
├── services/
│   └── sync/               # 后续 Gitee 私库同步服务
├── tools/
│   ├── dashboard/          # 仪表盘估值/行情/测试工具
│   └── investing/          # 投资策略计算器
├── docs/
│   ├── reviews/            # 产品/代码评审记录
│   └── designs/            # 设计文档
└── .agents/skills/         # Agent 技能定义
```

## Agent 始终在线行为

> **以下行为不需要用户触发，Agent 在每轮对话中自动执行。**

### 知识沉淀雷达

Agent 在所有对话中持续感知以下高价值信号：
- 用户产生了原创推理链（串联 ≥2 个概念得出新结论）
- 用户修正/进化了之前的认知
- 对话中产生了新的分析框架或跨领域关联
- 用户阐述了带量化判断的投资逻辑
- 现实事件验证或推翻了已有论点

**触发阈值**：累计 ≥2 个信号，或 1 个高密度信号（含量化+完整推理链）。

**行为**：在对话自然间隙轻量提议沉淀，同一对话最多提议 2 次，被拒绝后不纠缠。

完整规范见 `.agents/skills/think/SKILL.md` Part A。

## 检索流程（准确度优先，每一步都跑）

```
1. 问题归类
   - 识别领域（宏观/行业/个股/策略/心理/方法论）
   - 抽取 3-5 核心关键词
   - 用 knowledge/schema/glossary.md 扩展同义词（如"护城河"→ moat, competitive advantage）

2. 定位（L1 + L2 + L3 全跑，不是 fallback 关系）
   - L1: 读 knowledge/wiki/index.md → 按 summary 和 domain 筛选 → 候选集 A
   - L2: 按 frontmatter topic 字段匹配 glossary 术语 → 候选集 B
   - L3: 全文 grep 关键词查漏 → 候选集 C
   - 合并 A ∪ B ∪ C 去重 → 候选池（宁多勿漏）

3. 候选深读（按裁剪规则筛选后深读）
   - Read 每个候选 wiki 页
   - 沿 see_also 递归扩展 ≤ 2 跳（每跳最多追 3 个最相关的）
   - status=deprecated → 追 superseded_by 找新版
   - status=conflict → 读全部分支，不择一
   - 必要时回溯 knowledge/raw/ 层原文补充细节

4. 交叉验证（核心）
   每个硬事实（数字/日期/比例/排名）：
   - ≥2 source 一致 → high
   - 单 source → medium（标"待确认"）
   - 多源矛盾 → conflict（并列所有说法 + 来源，不择一）

5. 结构化输出
   ## 答案 / ## 置信度 / ## 来源 / ## 相关知识 / ## 时效性说明
```

完整规范见 `.agents/skills/query/SKILL.md`。

## Wiki 类别说明

| 类别 | 路径 | 内容说明 | id 示例 |
|------|------|----------|---------|
| 实体 | `knowledge/wiki/entities/` | 公司/基金/投资人物专页 | `berkshire-hathaway` |
| 概念 | `knowledge/wiki/concepts/` | 方法论、估值模型、行为金融 | `margin-of-safety` |
| 来源 | `knowledge/wiki/sources/` | 每份素材的摘要 | `2026-05-13-intelligent-investor-ch20` |
| 分析 | `knowledge/wiki/analyses/` | 对比、估值推演、复盘 | `china-internet-valuation-202605` |
| 组合 | `knowledge/wiki/portfolios/` | 持仓逻辑、配置策略 | `core-value-holdings` |

## 操作指令

- **摄入**：用户提供新素材 → Agent 阅读、讨论、提取、整合到 wiki、更新 index 和 log
- **思考**：用户抛出投资想法 → Agent 苏格拉底式追问深化 → 沉淀到 wiki
- **感知**：Agent 始终在线 → 对话中检测到高价值洞察 → 主动提议沉淀
- **查询**：用户提问 → Agent 走检索流程、结构化回答、高价值答案归档
- **校验**：定期让 Agent 做健康检查（矛盾、孤页、过时、断链、密度不足）
- **复盘**：买卖决策后归档到 analyses/，标注当时逻辑和事后验证

### 策略脚本主动调用规则

- 当对话明显进入 `IC / IM / PB 百分位 / 加仓 / 补保证金 / 不爆仓 / 滚贴水` 这类主题时，Agent 应优先把 `tools/investing/ic_im_roll_discount_stress.py` 当作后台计算器使用。
- 若用户明确表示“还没建仓”，Agent 应优先调用**未建仓信号模式**，先判断等待区 / 观察区 / 执行区，而不是直接进入加仓测算。
- 若用户直接在自然语言里给出参数（如“IC 8536、IM 8683、PB 89，还没建仓”），Agent 默认优先尝试 `--auto-brief-mode`，把自然语言参数映射成脚本参数，再返回简洁结论。
- 脚本的默认阈值优先从 `knowledge/wiki/portfolios/personal-position-sizing-framework.md` 读取；若用户临时指定了阈值参数，再以用户当次输入覆盖。
- 若缺少运行所需的最小参数，Agent 先追问最少量信息，再自行调用脚本，不把“你去运行脚本”当作默认交互方式。
- 面向用户的最终输出应是结论、条件和建议补资金额，而不是命令本身；命令仅作为可选补充。

#### 自然语言参数提取速记

- `还没建仓 / 空仓 / 尚未开始` → 未建仓场景，优先走 `--entry-signal-mode` 或 `--auto-brief-mode`，并将 `current_drop` 视为缺省。  
- `已有 IC / 持有底仓 / 跌了 20%` → 已持有 IC 场景，优先提取 `current_drop`，再判断是否进入 `--decision-mode` 或 `--auto-brief-mode`。  
- `IC 8536，IM 8683，IC PB 89.8，IM PB 89.6，我还没建仓` → 映射为 `ic_points=8536`、`im_points=8683`、`ic_pb_percentile=89.8`、`pb_percentile=89.6`。  
- `IC 已有底仓，IM PB 18，现在跌了 20%` → 映射为 `current_drop=0.20`、`pb_percentile=18`。  
- `阈值按 25 算 / 风险度按 60% 算` → 视为覆盖知识库默认参数，分别映射到 `pb_add_threshold=25`、`rebalance_risk=0.60` 等 CLI 参数。  
- 信息不足时只追问最小缺口：未建仓至少补齐 `ic_pb_percentile` + `pb_percentile`；已持有 IC 至少补齐 `current_drop` + `pb_percentile`。

## 技能清单

- `/query` — 知识库查询（三层定位 + 候选裁剪 + 交叉验证 + 结构化输出 + 归档判断）
- `/ingest` — 添加/更新知识（阅读 + 讨论 + 交叉引用 + glossary 维护 + 冲突检测）
- `/lint` — 定期体检（16 项检查 + 一键脚本 + 分级自动修复）
- `/think` — 对话式思考沉淀（主动追问 + 被动感知 + 知识库增量写入）
- `/curate` — 知识治理（修正过时数据 + 退役失效页 + 解冲突 + 软删除，与 /lint 闭环）
- `/guigu-cashflow-stock-decision` — 龟龟现金流个股/批量决策过滤器（三因子漏斗 + 仓位管理 + 执行档位）
- `/static-cigar-butt-stock-decision` — 静态价值型烟蒂个股/批量过滤器（存量资产垫 + 资产质量折价 + 正现金流入 + 兑现路径）

### 快捷操作

| 命令 | 说明 |
|------|------|
| `bash .agents/skills/lint/scripts/lint-all.sh` | 一键健康检查 |
| `bash .agents/skills/query/scripts/search.sh topic <词>` | 按 topic 搜索 |
| `bash .agents/skills/query/scripts/search.sh fulltext <词>` | 全文搜索 |
| `bash .agents/skills/query/scripts/search.sh stale 90` | 找 90 天未更新页面 |
| `bash .agents/skills/ingest/scripts/save-raw.sh check <文件名>` | 验证 raw 文件名规范 |

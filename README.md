# 个人投资理念知识库与投资管理系统

这是一个面向个人长期投资的私有项目：以 Markdown 知识库沉淀研究与复盘，以微信小程序和 Java 后端承载可审计的投资账本、策略与市场数据。

项目不提供交易、券商 API、自动下单、支付或投资顾问服务；其用途是帮助个人记录事实、复核规则、识别风险与持续迭代投资体系。

## 项目组成

| 模块 | 作用 |
| --- | --- |
| `knowledge/` | 原始资料、结构化 Wiki、术语表、来源与链接规范 |
| `apps/dashboard/` | 旧版本地 Dashboard，仅作为业务迁移参考 |
| `new_project/` | 新版微信小程序、Java 后端与本地/云部署工件 |
| `tools/` | 行情、估值、压力测试和策略计算工具 |
| `docs/` | 架构设计、规格、评审与发布门禁 |

## 核心设计原则

- **事实可追溯**：账本使用不可变复式分录；纠错以冲正和替代事实完成，不物理删除历史。
- **金额精确**：所有业务金额均以原币种最小单位整数保存，例如 `USD 6.66` 保存为 `666`；字段统一为 `*_cent BIGINT`。
- **币种隔离**：CNY、USD 等币种分别核算与展示，不做隐式换汇或合并总资产。
- **数据可信**：行情遵循“可靠导入优先、自动数据源补缺”，同时保留来源、更新时间和降级原因。
- **边界明确**：首期采用 Java DDD 模块化单体，账本、组合、市场、策略、报表和平台能力按限界上下文划分。

## 当前能力

- 现金账户、现货 FIFO、分红/利息、公司行为；
- IC/IM 期货保证金、逐日结算与移仓；
- 买入型期权开平仓与无价值到期；
- 手工估值、券商对账、JSON/SQLite 历史导入；
- 高分红、QQQ/QLD、IC/IM、深度 Put 四类策略；
- 原币种独立的总览、市场、报表、审计导出与加密快照恢复；
- 微信小程序本地模拟器验证与 Java 后端本地运行闭环。

## 技术栈

- 前端：微信原生小程序、TypeScript、WXML、WXSS。
- 后端：Java 21、Spring Boot、MyBatis、Flyway、Spring Security。
- 基础设施：MySQL、Redis、MinIO、XXL-JOB、Docker Compose。
- 工程协作：使用 Codex 与 GPT-5 系列模型辅助架构审查、代码实现、测试与文档维护；投资规则、资金口径、生产凭据和发布决策由项目负责人确认。

## 快速入口

- [知识库入口](HOME.md)
- [知识库规范与 Agent 指引](AGENTS.md)
- [新版小程序与后端说明](new_project/README.md)
- [微信小程序 + Java DDD 实施规划](docs/designs/wechat-miniprogram-java-ddd-implementation-plan.md)
- [Dashboard 功能迁移矩阵](docs/designs/dashboard-miniprogram-feature-equivalence.md)
- [云发布门禁](new_project/infra/RELEASE_GATE.md)

## 发布状态

新实现已完成本地运行、后端测试、小程序类型检查与开发者工具模拟器验证。正式发布前仍须完成真实微信登录、备案 HTTPS 域名、生产级文件扫描、对象存储权限与加密配置、数据源授权核验、隐私声明和真机回归。

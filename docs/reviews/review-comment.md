# 评审清单 // reviewer→fixer protocol

格式: AI-to-AI。每个 OPEN 项含 `loc/bug/fix/accept`。FIXED 项压成单行(id+verdict),不展开。
ID 跨轮稳定。status: OPEN | FIXED | PARTIAL | REGRESSED。
路径=仓库相对路径。base=`apps/dashboard/`。

## FIXED (浏览器实测确认)
- P0-1 二次确认: confirmDanger() 加于 清空/删除/示例(有数据时)。
- P0-2 金额自动算: quantity*price/10000→amount,手工可覆盖。实测 1000*50→5.00。
- P0-3 单位+回显: label「数量(股/份)/价格(元)/金额(万元)」+ field-hint「≈50000元」。
- P1-4 术语 tooltip: termDefinitions(16词)+termLabel/termHelp。实测总览 9term+2help,点击展开,opacity=1,文案正确。
- P1-5 空账本引导: onboardingPanel 3步卡片+按钮(去设置/载入示例/录第一笔)。实测空账本渲染,有数据时隐藏。
- P1-7 toast: showToast 覆盖 保存/新增/删除/清空/示例/导出/清除/重算。
- P2-10 导航默认: 一级 data-subpage full→overview/charts。实测点高分红→「模块概览」。
- P2-11 失败指引: localServiceGuidePanel 三步引导(启动服务/打开入口/更新价格JSON)+可复制命令+「暂时收起」。R4 补 localServiceGuideDismissed 持久标志。实测状态机: 显示→收起→重渲染保持隐藏→新失败可重显。
- P2-13 编辑滚动高亮: focusEntryFormForEdit() scrollIntoView+edit-highlight(1.8s)。CSS 已确认。
- P1-6 必填校验: 按动作校验,buy 缺数量/价格被拦、补齐放行(实测 blocked→放行)。
- P1-8 PB手动优先(R5方向已返工): applyValuation 仅 source==="auto" 才覆盖; manual 保留; 加「用自动值 X%」采纳按钮。实测: 手填25读JSON(88)保留25✅ / auto被88覆盖✅ / 点采纳 manual→auto 取88✅。

## FIXED 续 (P2-9 + P4 信任层,本轮 reviewer 收口)
- P2-9 手机版二级导航: mobileSectionNav() 移动端每 tab 渲染区块跳转条(overview:现金/风险/参数/估值/图表/动作; reports:图表/矩阵/排行/估值/流水; module:概览/录入/分布/估值/流水),点击 scrollIntoView 到对应区块。reviewer 实测(index.html 移动视口): 点「参数」scrollY 0→2007 核心参数区块 top 归 0✅; 点「动作」scrollY→7519 decision-panel 入视口✅。
- P4-1 marginRisk 权益0极危: calculate() usedMargin>0 且 futuresEquity<=0 → marginRisk=Infinity + marginRiskInvalid=true,classify 判 status-danger,UI「权益为0,极危」。test_dashboard_core.mjs 断言覆盖。
- P4-2 写失败告警: writeLocalStorage() 统一入口包 try/catch,失败 showToast error「写入失败…请立即导出全部数据」+ 返回 false,调用方不再假成功。测试 stub 抛错验证。
- P4-3 完整备份/恢复: 顶部「导出全部/导入全部」(桌面+移动),JSON 含 ledger.entries/settings + positionValuations + history + valuation + historyView + schemaVersion;导入先校验后二次确认,非法文件不破坏现有数据。
- P4-4 核心计算测试(Node 零依赖): tools/dashboard/test_dashboard_core.mjs 从 app.js 抽取纯函数执行。覆盖 ratio/gap/金额自算/summarizeLedger/calculate(权益0边界)/detectStage/externalFlowOnDate/NAV 复合。本轮 reviewer 追加多端同步纯函数: validateFullBackupPayload(校验/抛错/默认值/historyView归一) + comparableBackupPayload(等价稳定/差异敏感) + backupHasData(各数据分支) + 冲突真值表(clean/conflict/单侧)。改坏 backupHasData→测试变红→还原→全绿,验证有效。
- P2-12 高分红分类: WONTFIX(用户决策不拆分,维持 normalizeDividendBucket 聚合口径)。

## 新需求(用户已定方案,fixer 可执行)
### P3-1 SQLite 本地持久化(镜像备份模式) [status: FIXED 实测]
- 用户决策: 纯本地不上云; SQLite; 架构=B 镜像备份(localStorage 主存,SQLite 做持久化备份+跨浏览器同步); 痛点=怕清缓存/换浏览器丢数据。
- 约束(硬性):
  - localStorage 保持为主数据源。双击 HTML(file:// 或无服务)时,读写全部走 localStorage,功能不降级。
  - 仅在 services/sync/src/main.py 服务可用时,额外把数据镜像到 SQLite。服务不可用则静默跳过(不报错、不阻塞)。
  - 数据不出本机。SQLite 文件落在仓库本地(建议 `apps/dashboard/data/ledger.db`,并加入 .gitignore 避免误提交个人财务数据)。
- 后端(services/sync/src/main.py):
  - 用 Python 标准库 sqlite3(无第三方依赖)。
  - 加 API: `GET /api/ledger`(读最新快照) / `POST /api/ledger`(写入快照,整体 upsert)。可选 `GET /api/ledger/backups` 列历史。
  - 表结构建议: snapshots(id, created_at, payload_json) 追加式存储,保留历史版本(顺带满足防误删); 另存一个 current 指针或取最新行为当前态。
- 前端(app.js):
  - 现有 load/save(loadLedger/saveLedger 等)封装一层: 写本地后,若服务可用则异步 POST 镜像; 不可用则跳过。
  - 页面加载时: 若服务可用且 SQLite 有数据而 localStorage 为空(如换了浏览器),提示"检测到本地备份,是否恢复"。冲突时以用户选择为准,默认不静默覆盖 localStorage。
  - 镜像失败不弹错(避免噪音),仅在手动触发的备份/恢复操作给 toast。
- accept:
  1. 无服务双击 HTML: 全功能可用,数据进 localStorage。
  2. 开服务后录入: 数据自动镜像进 ledger.db(可用 sqlite3 命令行查到行)。
  3. 换浏览器/清缓存后开服务打开: 能从 SQLite 恢复账本。
  4. ledger.db 在 .gitignore 内,不被 git 跟踪。
- 非目标: 多端实时同步(纯本地做不到,不做); 云存储(用户明确拒绝)。
- 主体进度: fixer 已实现后端(sqlite3/snapshots 表/GET+POST+backups)+前端(mirrorLedgerSnapshot/offerLedgerBackupRestore)。reviewer 实测: 镜像✅(录4笔→ledger.db 4快照,字段金额正确)、恢复✅(清 localStorage+sessionStorage 后刷新,46.2万数据完整恢复)。遗留子缺陷见 P3-1a。

### P3-1a 恢复提示去重过度,边界场景漏触发 [status: FIXED 实测]
- fix: offerLedgerBackupRestore() 去重改为幂等——L519 只在 `=== "cancelled"` 才跳过; L526 仅用户点取消时写 cancelled; L529 恢复成功清标志。
- reviewer 实测(R7 base=`8cd486ac`): ①注入残留旧标志"1"+仅清 localStorage→刷新→仍成功恢复3笔(五粮液24/国债利息0.8/国债买入30),标志被清。②点取消→写入 cancelled→同会话二次调用不再弹(confirm 仅1次)。全 accept 通过。
- 顺带验证 P1-6 校验生效: 债券 buy 缺数量/价格被拦(blocked,localStorage 不增); 补齐数量价格后放行(2→3笔)。

## 整改顺序
全部 OPEN 项已清零。P0×3/P1×5/P2(10/11/13)/P1-6/P1-8/P3-1/P3-1a/P4-1~4/P2-9 均 FIXED 实测。P2-12 WONTFIX。

## LOG
- R1 base=`35254327`: 建 13 项(P0×3/P1×5/P2×5) 全 OPEN。
- R2 base=`f57a4e50`: P0-1/2/3 + P1-7 FIXED。
- R3 base=`f7b70785`: P1-4/5 + P2-10/11/13 FIXED(实测确认)。
- R4 base=`1f1105e8`: P2-11 dismiss 持久化增强。实测通过。
- R4+ 校正: P1-6 误记修正回 OPEN。9 FIXED / 4 OPEN。
- R5 base=`67ce1612`: app.js +105/-12, styles.css +80。fixer 自行做 P1-8(pbSourceBadge 手工/自动标签+覆盖toast)。实测 badge 渲染显示「手工」。但方向=自动优先,与用户决策(手动优先)冲突,标记需返工。
- R5+ 用户定口径: P1-6 需校验 / P1-8 手动优先 / P2-9 手机版加 / P2-12 不拆(WONTFIX)。已写入 OPEN 区供 fixer 执行。另提数据库议题 Q1(待澄清)。
- R5++ 数据库定方案: 用户选 纯本地 + SQLite + B镜像备份模式(localStorage 主存,服务可用时镜像到 ledger.db),痛点=怕丢数据。Q1 转为 P3-1 规格(含约束/API/accept),交 fixer 实现。
- R6 base=`6ae3850e`: fixer 实现 P3-1(后端 sqlite3 + 前端镜像/恢复)。reviewer 端到端实测: 镜像✅ + 恢复✅(46.2万数据完整回来)。发现子缺陷 P3-1a(恢复提示 sessionStorage 去重过度,清 localStorage 但 sessionStorage 残留时漏触发),已记录交 fixer 小修。
- R7 base=`8cd486ac`: fixer 修 P3-1a(去重改幂等,只认 cancelled)+ 已实现 P1-6 校验。reviewer 实测: P3-1a 全 accept 通过(残留标志不再误挡恢复 / 取消后不再重弹); P1-6 买入缺数量价格被拦、补齐放行。当前仅 P1-8(返工手动优先)/ P2-9(手机版)待办。
- R8(同 base `8cd486ac`): reviewer 复验 P1-8 返工。fixer 已把 R5「自动优先」改为「手动优先」(applyValuation 仅 source==auto 覆盖)+ 加「用自动值」采纳按钮。实测全 accept: 手填25读JSON保留25 / auto被覆盖 / 点采纳 manual→auto。P1-8 FIXED。剩唯一 OPEN=P2-9(手机版)。
- PM审查(R8后): 以产品经理视角做成熟度体检。结论=体验层合格(8/10)但信任层不及格(数据可信4/10、正确性3/10)。用户定位=长期严肃账本,拍板补 4 项→新增 P4-1(修风险度误导bug)/P4-2(写失败告警)/P4-3(完整备份恢复)/P4-4(核心计算轻量测试)。按序执行,测试用 Node 原生零依赖。当前 OPEN: P2-9 + P4-1~4。
- R9(迭代收口轮): fixer 已自报 P3-2(SQLite 备份面板增强)+ P4-1~4 全部完成(见 review-comment.agent.md)。reviewer 本轮做: ①云同步真实双向往返测试(只动 syncMeta 元数据,推送→拉回→还原全通过); ②清技术债(删死代码 serve_dashboard.py,前端只用 main.py 超集; 清 requirements.txt 过时注释 + 删空占位 routes/.gitkeep); ③收敛三套 skill 目录(.agents 为真源,合并 .coco 更全内容 + 迁入 curate,删 .claude/.coco + CLAUDE.md,统一内部路径); ④补多端同步纯函数测试(见 P4-4); ⑤实测 P2-9 移动跳转。全部 OPEN 清零。遗留: .claude/a.txt(用户投资思考草稿)待用户定夺归属。

# 评审清单 // reviewer→fixer protocol

格式: AI-to-AI。每个 OPEN 项含 `loc/bug/fix/accept`。FIXED 项压成单行(id+verdict),不展开。
ID 跨轮稳定。status: OPEN | FIXED | PARTIAL | REGRESSED。
路径=仓库相对路径。base=`hybrid-barbell-dashboard/`。

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

## OPEN (用户已定口径 2026-06-27,fixer 可执行)

### P1-6 必填校验缺失 [status: OPEN | 决策:需要校验]
- 用户决策: 需要校验。按动作类型设必填。
- loc: `app.js` entryForm 提交校验 (当前仅 日期/金额 required)
- fix: 提交时按动作校验必填,缺失则阻止提交+高亮+toast。建议规则: buy/sell→标的(symbol或name)+数量+价格+金额; deposit/withdraw→金额; dividend/interest→标的+金额; margin/roll→金额; internal_in/out→金额。
- accept: 缺关键字段时阻止提交并高亮对应字段; 不同动作必填项不同。

### P1-8 PB手填被JSON静默覆盖 [status: OPEN | 决策:手动优先]
- 用户决策: 手动优先。手填 PB 后,读取 JSON/API 不得覆盖手工值。
- ⚠️ fixer 当前实现方向相反: R5 做成"自动 JSON 覆盖手工值+toast 提示"(pbSourceBadge 已加,实测显示「手工」标签)。需改为手动优先。
- loc: `app.js` 读取估值 JSON / 本地 API 回填 settings.icPb/imPb; pbSourceBadge / icPbSource / imPbSource
- fix: 当 icPbSource/imPbSource === "manual" 时,JSON/API 的 pb_percentile 不覆盖,仅在 source 为 auto 或值为空时才回填。保留 badge 区分手工/自动。可加"用自动值"按钮供用户主动采纳。
- accept: 手填 PB 后点读取 JSON,手工值保持不变,badge 仍显示「手工」; 用户主动点采纳才变自动。

### P2-9 双端信息架构割裂 [status: OPEN | 决策:手机版加]
- 用户决策: 手机版要加(二级导航/锚点)。
- loc: `index.html`(移动) + `app.js` 移动端渲染 + `styles.css`
- fix: 移动版各 tab 内加子区块锚点跳转或分段折叠,对齐桌面二级子页(概览/录入/分布/估值/流水)。
- accept: 移动版各 tab 内可快速跳转到子区块。

### P2-12 高分红分类强制合并 [status: OPEN | 决策:不拆分]
- 用户决策: 不用单独拆出来,保留现聚合口径。
- 结论: 维持 normalizeDividendBucket 现状(白酒/五粮液等并入「高分红股票」)。本项关闭,不整改。
- status 实际可置为 WONTFIX。

## 新需求(用户已定方案,fixer 可执行)
### P3-1 SQLite 本地持久化(镜像备份模式) [status: OPEN | 决策:已定]
- 用户决策: 纯本地不上云; SQLite; 架构=B 镜像备份(localStorage 主存,SQLite 做持久化备份+跨浏览器同步); 痛点=怕清缓存/换浏览器丢数据。
- 约束(硬性):
  - localStorage 保持为主数据源。双击 HTML(file:// 或无服务)时,读写全部走 localStorage,功能不降级。
  - 仅在 serve_dashboard.py 服务可用时,额外把数据镜像到 SQLite。服务不可用则静默跳过(不报错、不阻塞)。
  - 数据不出本机。SQLite 文件落在仓库本地(建议 `hybrid-barbell-dashboard/data/ledger.db`,并加入 .gitignore 避免误提交个人财务数据)。
- 后端(serve_dashboard.py):
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

### P3-1a 恢复提示去重过度,边界场景漏触发 [status: OPEN]
- loc: `app.js` offerLedgerBackupRestore() — `if (sessionStorage.getItem(ledgerBackupPromptKey)) return;`(约 L519)
- bug: 用 sessionStorage 标志"本会话已提示过",导致同标签页内 localStorage 被清空但 sessionStorage 仍在时(DevTools 手删、部分清理插件只清 localStorage、或同会话二次清空),刷新后不再提示恢复,页面显示空账本,用户误以为数据已丢(实际 SQLite 仍有)。reviewer 实测复现: 仅清 localStorage→刷新→无恢复提示→空账本; 同时清 sessionStorage 才正常。
- 影响面: 真实换浏览器/换设备不受影响(新会话 sessionStorage 本就空),属次要 bug,但易造成"数据丢了"的错觉。
- fix: 去重不要用"提示过就不再提示"。改为幂等触发——只要 localStorage 为空且 SQLite 有数据就提示恢复; 仅当用户"明确点了取消"才在本会话内不再弹(可保留 sessionStorage 但只在取消分支写入,accept 分支或未操作不写)。
- accept: 清空 localStorage(不论 sessionStorage 是否清)后刷新,只要 SQLite 有备份即触发恢复提示; 用户点取消后本会话不再重复弹; 换会话仍会再次提示。
- 非目标: 不改变"localStorage 主存"架构; 恢复仍需用户确认,不静默覆盖。

## 整改顺序
P1-8(改手动优先,修正 R5 方向) > P1-6(按动作校验) > P3-1a(恢复去重 bug,小修) > P2-9(手机版二级导航)。P3-1 主体已完成。P2-12 关闭(WONTFIX)。

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

# IC/IM 估值数据

推荐用带本地 API 的服务启动页面：

```bash
python3 services/sync/src/main.py
```

然后访问：

```text
http://127.0.0.1:8775/apps/dashboard/index-desktop.html
```

手机版入口仍是：

```text
http://127.0.0.1:8775/apps/dashboard/index.html
```

如果你是双击 HTML 以 `file://` 方式打开页面，浏览器可能限制本地 API 和 JSON 读取。页面会显示“本地服务启动引导”，复制上面的启动命令后在项目根目录运行，再打开本地入口 URL。

该服务会提供：

```text
/api/ic-im-valuation
/api/ledger
/api/ledger/backups
/api/sync/health
/api/sync/status
/api/sync/data/dashboardState
```

页面打开时会优先请求这个本地 API，实时运行 `tools/dashboard/update_ic_im_valuation.py` 更新 IC/IM 估值与贴水，并写回 `apps/dashboard/data/ic-im-valuation.json`。如果本地 API 不可用，页面会回退读取已有 JSON。

`/api/ledger` 是统一本地服务保留的 SQLite 镜像备份接口：页面仍以浏览器 `localStorage` 为主存；只有通过 `python3 services/sync/src/main.py` 打开时，保存账本后才会异步追加一份快照到：

```text
apps/dashboard/data/ledger.db
```

`总览 → 核心参数 → 本地备份` 面板可以查看最近 SQLite 快照、手动立即镜像、恢复指定历史快照。`GET /api/ledger` 默认读取最新快照，也支持 `GET /api/ledger?id=<snapshot_id>` 读取指定快照；`GET /api/ledger/backups` 返回最近快照列表。

如果换浏览器或清缓存后本地账本为空，页面会在检测到 SQLite 备份时提示是否恢复；已有 `localStorage` 数据时不会静默覆盖。双击 HTML 以 `file://` 打开仍完全走 `localStorage`，SQLite 备份不可用也不会影响记账。

顶部全局操作区提供“导出全部 / 导入全部 / 云同步”。完整备份 JSON 包含：

- `ledger.entries` 与 `ledger.settings`
- `positionValuations`
- `history`
- `valuation`
- `historyView`

导入完整备份会先校验结构并二次确认，非法文件不会覆盖现有数据。

云同步通过 `services/sync` 访问 Gitee 私有数据仓库。前端只保存 `APP_ACCESS_KEY`，不保存 Gitee token。启动时如果本地与远端都存在且不一致，会显示选择面板，由用户决定“拉取远端覆盖本地 / 推送本地覆盖远端 / 暂不处理”，不会静默覆盖。

在已配置 `APP_ACCESS_KEY` 且当前远端版本与上次确认的同步基线一致时，页面保存流水、持仓估值、历史快照、IC/IM 估值或历史视图设置后，会自动延迟推送完整状态到 Gitee。如果自动推送前检测到 Gitee 远端也发生变化，页面会暂停自动推送并显示冲突选择面板。

核心计算测试使用零依赖 Node 脚本：

```bash
node tools/dashboard/test_dashboard_core.mjs
```

该脚本覆盖风险度极危边界、阶段判断、账本汇总、金额自动计算和净值收益率外部现金流剔除。

运行以下命令更新前端读取的 JSON：

```bash
python3 tools/dashboard/update_ic_im_valuation.py
```

默认输出：

```text
apps/dashboard/data/ic-im-valuation.json
```

口径：

- 当前 PE / PB：中证指数官网 `data-service/indexValuation`
- 当前 PB 兜底：若中证官网当前 PB 缺失，脚本会尝试读取东方财富 `push2` 行情字段 `f167/f152`
  - 这是 best-effort 兜底；如果东方财富接口断连或返回空值，脚本不中断
  - 东方财富只用于当前 PB/PE 展示兜底，不用于 PB 历史分位
- PE 历史分位：中证指数官网 `perf/indexCsiDsPe`
- PB 历史分位：仅在本地提供 PB 历史 CSV 时计算
- IC/IM 贴水：`xags.stephenslab.top/basis/` 页面使用的公开代理接口
  - 合约列表：`/api/proxy/contracts?node=zzgz_qh`、`/api/proxy/contracts?node=im_qh`
  - 期货行情：`/api/proxy/sina?list=...`
  - 现货行情：腾讯行情接口
  - 贴水 = 现货指数点位 - 期货价格
  - 年化贴水 = 贴水 / 期货价格 / 剩余天数 * 365
- 移仓换月提醒：
  - 距理论交割日 `<= 10` 个自然日：进入移仓观察窗口
  - 距理论交割日 `<= 5` 个自然日：强提醒，优先完成移仓检查
  - 交割日按第三个周五估算；如遇交易所节假日顺延，以中金所公告为准

PB 历史 CSV 放在：

```text
apps/dashboard/data/history/000905_pb.csv
apps/dashboard/data/history/000852_pb.csv
```

CSV 至少需要一列 `pb` 或 `pb_lf`。如果没有 PB 历史 CSV，页面只展示当前 PB/PE，不覆盖手工填写的 IC/IM PB 百分位。

默认会抓取贴水数据。若只想更新估值、不抓贴水，可运行：

```bash
python3 tools/dashboard/update_ic_im_valuation.py --no-include-basis
```

# 持仓价格数据

运行以下命令生成前端读取的价格 JSON：

```bash
python3 tools/dashboard/update_position_quotes.py 000858 000568 600887 600153 600036 002818 002091 601668 600177 600873 601318 600938 600941 601225 000651 600690 512890 520890 159545 513630 159117 QQQ QLD SPY
```

默认输出：

```text
apps/dashboard/data/position-quotes.json
```

口径：

- 数据源参考 `mpquant/Ashare` 的实用分层思路
- A 股主源：新浪 A 股行情 `hq.sinajs.cn`
- A 股备用源：腾讯行情 `qt.gtimg.cn`
- 美股：Yahoo chart 最新日收盘价，并用 USD/CNY 折算为人民币元
- 页面读取该 JSON 后，只更新持仓估值层的 `当前价格 / 数据来源 / 更新时间`
- 若持仓估值里已经手工填写了 `当前市值`，手工市值仍保持最高优先级，不会被价格同步覆盖
- QQQ / QLD 等普通美股 ETF 可通过本地 JSON 同步人民币折算价；期权权利金、期货保证金等仍建议继续手工填写当前市值
- `watchlist-data/20260626/watchlist-quotes.json` 已补充进 `position-quotes.json`：16 只 A 股 + 5 只 ETF 的 2026-06-26 收盘快照可直接用于持仓价格同步。

如果需要同时生成 A 股日收盘价历史 JSON：

```bash
python3 tools/dashboard/update_position_quotes.py 000568 000858 --history-days 260
```

默认历史输出：

```text
apps/dashboard/data/position-history.json
```

网页端的收益率曲线会在打开时自动补齐缺失日期：

- A 股：直接读取腾讯日线 `appstock/app/fqkline/get` 的前复权日收盘价
- 关注列表补充数据：缺少腾讯日线历史的标的，会先用 `watchlist-data/20260626/watchlist-quotes.json` 的 2026-06-26 单日收盘快照补一条记录；已有同日腾讯日线的不覆盖
- 现金 / 类现金 / 债券：按流水日期推导余额
- 暂未自动取价的资产：沿用上一条可用估值
- 收益率按总资产净值法计算，`转入 / 转出` 作为外部现金流剔除
- `买入 / 卖出` 是标的自身仓位变化；如果同时维护现金余额，用 `内部划出 / 内部划入` 记录现金侧变化，避免总资产重复计算

# IC/IM 估值数据

推荐用带本地 API 的服务启动页面：

```bash
python3 scripts/serve_dashboard.py
```

然后访问：

```text
http://127.0.0.1:8775/hybrid-barbell-dashboard/index-desktop.html
```

手机版入口仍是：

```text
http://127.0.0.1:8775/hybrid-barbell-dashboard/index.html
```

如果你是双击 HTML 以 `file://` 方式打开页面，浏览器可能限制本地 API 和 JSON 读取。页面会显示“本地服务启动引导”，复制上面的启动命令后在项目根目录运行，再打开本地入口 URL。

该服务会提供：

```text
/api/ic-im-valuation
/api/ledger
/api/ledger/backups
```

页面打开时会优先请求这个本地 API，实时运行 `scripts/update_ic_im_valuation.py` 更新 IC/IM 估值与贴水，并写回 `hybrid-barbell-dashboard/data/ic-im-valuation.json`。如果本地 API 不可用，页面会回退读取已有 JSON。

`/api/ledger` 是本地 SQLite 镜像备份接口：页面仍以浏览器 `localStorage` 为主存；只有通过 `python3 scripts/serve_dashboard.py` 打开时，保存账本后才会异步追加一份快照到：

```text
hybrid-barbell-dashboard/data/ledger.db
```

如果换浏览器或清缓存后本地账本为空，页面会在检测到 SQLite 备份时提示是否恢复；已有 `localStorage` 数据时不会静默覆盖。双击 HTML 以 `file://` 打开仍完全走 `localStorage`，SQLite 备份不可用也不会影响记账。

运行以下命令更新前端读取的 JSON：

```bash
python3 scripts/update_ic_im_valuation.py
```

默认输出：

```text
hybrid-barbell-dashboard/data/ic-im-valuation.json
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
hybrid-barbell-dashboard/data/history/000905_pb.csv
hybrid-barbell-dashboard/data/history/000852_pb.csv
```

CSV 至少需要一列 `pb` 或 `pb_lf`。如果没有 PB 历史 CSV，页面只展示当前 PB/PE，不覆盖手工填写的 IC/IM PB 百分位。

默认会抓取贴水数据。若只想更新估值、不抓贴水，可运行：

```bash
python3 scripts/update_ic_im_valuation.py --no-include-basis
```

# 持仓价格数据

运行以下命令生成前端读取的价格 JSON：

```bash
python3 scripts/update_position_quotes.py 000568 000858 QQQ QLD SPY
```

默认输出：

```text
hybrid-barbell-dashboard/data/position-quotes.json
```

口径：

- 数据源参考 `mpquant/Ashare` 的实用分层思路
- A 股主源：新浪 A 股行情 `hq.sinajs.cn`
- A 股备用源：腾讯行情 `qt.gtimg.cn`
- 美股：Yahoo chart 最新日收盘价，并用 USD/CNY 折算为人民币元
- 页面读取该 JSON 后，只更新持仓估值层的 `当前价格 / 数据来源 / 更新时间`
- 若持仓估值里已经手工填写了 `当前市值`，手工市值仍保持最高优先级，不会被价格同步覆盖
- QQQ / QLD 等普通美股 ETF 可通过本地 JSON 同步人民币折算价；期权权利金、期货保证金等仍建议继续手工填写当前市值

如果需要同时生成 A 股日收盘价历史 JSON：

```bash
python3 scripts/update_position_quotes.py 000568 000858 --history-days 260
```

默认历史输出：

```text
hybrid-barbell-dashboard/data/position-history.json
```

网页端的收益率曲线会在打开时自动补齐缺失日期：

- A 股：直接读取腾讯日线 `appstock/app/fqkline/get` 的前复权日收盘价
- 现金 / 类现金 / 债券：按流水日期推导余额
- 暂未自动取价的资产：沿用上一条可用估值
- 收益率按总资产净值法计算，`转入 / 转出` 作为外部现金流剔除
- `买入 / 卖出` 是标的自身仓位变化；如果同时维护现金余额，用 `内部划出 / 内部划入` 记录现金侧变化，避免总资产重复计算

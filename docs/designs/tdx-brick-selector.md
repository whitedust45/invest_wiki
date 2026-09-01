# 通达信砖型图尾盘筛选器设计

## 状态与范围

- 状态：规格已批准，脚本与 Windows 定时运行器已实现，待 Windows 通达信实测。
- 目标：在 Windows 当前用户会话中，于每个工作日 14:40 自动启动或复用通达信，筛选涨幅前列概念板块中的砖型图候选。
- 非目标：不扫描全 A 股、不创建或修改通达信公式、不下单、不写入仪表盘 SQLite、不自动登录通达信、不补跑错过的尾盘任务。

## 运行前提

- 通达信根目录默认是 `F:\new_tdx64`，可用 `TDX_PATH` 或命令行参数覆盖。
- 目录内必须存在 `PYPlugins\sys\tqcenter.py`，通达信客户端必须已启动并登录。
- 通达信公式管理器中已有选股公式，名称精确为 `砖`。
- 批量成分股日线必须已在通达信客户端下载；缺失日线会被识别为无效数据，不会产生 Python 假信号。

## 交付形态

超短策略实现位于 `modules/short_term/strategies/brick.py`，并由 `modules/short_term/registry.py` 注册；由 `--engine` 选择引擎：

- `native`：原样调用通达信原生选股公式 `砖`。
- `python`：由 Python 从通达信日线计算砖型图与严格入选条件。
- `both`：依次执行两种引擎，并列出共有、仅原生和仅 Python 的证券代码，用于 Windows 实机对照。

默认 `--engine both`、`--concept-limit 5`、`--run-time 14:40`、`--bar-count 60`。脚本只写标准输出；`--json` 输出可机读 JSON，`--output` 可选地写入指定文件。

## Windows 定时运行

- `modules/short_term/schedules/run_tdx_brick_selector.ps1`：周一至周五 14:40 的运行器。未检测到 `F:\new_tdx64\TdxW.exe` 时自动启动，每 30 秒预检一次，最晚等待至 14:55；未就绪则当天失败且不补跑。
- `modules/short_term/schedules/install_tdx_brick_selector_task.ps1`：一次性创建或更新当前用户的 Windows 任务计划 `TDX Brick Selector 14:40`。任务仅在用户登录时运行，忽略并发新实例，不关闭通达信。
- 结果写入 `data/tdx-brick-selector/runs/YYYY-MM-DD.json`；过程日志写入 `data/tdx-brick-selector/logs/YYYY-MM-DD.log`。

在 Windows 项目根目录执行一次安装：

```powershell
powershell -ExecutionPolicy Bypass -File modules\short_term\schedules\install_tdx_brick_selector_task.ps1
```

主动核对当天软件选股结果：

```powershell
python modules\short_term\verify_brick.py --manual-file C:\path\to\tdx-selected-codes.txt
```

主动核对脚本会实际运行双引擎筛选，并在 `data/tdx-brick-selector/manual/` 保存筛选结果与软件手工结果的交集、仅软件命中、仅原生/Python 命中。

## 数据流程

1. 调用 `tq.initialize(__file__)`，再调用 `tq.refresh_cache(market='AG')`。
2. 调用 `tq.get_sector_list(list_type=1)` 获取全部板块后，只保留 `8805` 前缀的概念板块代码，不依赖代码连续编号假设。
3. 批量调用 `tq.get_market_data(period='1d', count=2, dividend_type='none')` 获取板块日线；按最新两根 K 线的 `close / previous_close - 1` 计算截至运行时点的涨跌幅，取前 `--concept-limit` 个板块。
4. 对入选板块调用 `tq.get_stock_list_in_sector()`，合并并去重成分股；输出每只股票对应的入选概念板块。
5. 原生引擎按批次调用 `tq.formula_process_mul_xg(formula_name='砖', stock_list=...)`，按返回结果中每只证券的 `XG` 最新值为 `1` 判定命中；若 TQ 返回直接命中代码列表也支持提取。Python 引擎批量取得成分股 `--bar-count` 根不复权日线后计算指标。
6. 记录每个阶段耗时、数据时间、入选板块、成分股数量和逐项错误。若板块或股票的最新 K 线不属于当日，脚本以错误退出，不将盘后或陈旧数据伪装为 14:40 信号。

## Python 严格信号

Python 版本使用用户提供的砖型图计算链：

```text
VAR1A = (HHV(HIGH,4) - CLOSE) / (HHV(HIGH,4) - LLV(LOW,4)) * 100 - 90
VAR2A = SMA(VAR1A,4,1) + 100
VAR3A = (CLOSE - LLV(LOW,4)) / (HHV(HIGH,4) - LLV(LOW,4)) * 100
VAR4A = SMA(VAR3A,6,1)
VAR5A = SMA(VAR4A,6,1) + 100
砖型图 = max(VAR5A - VAR2A - 4, 0)
```

其中 `SMA(X,N,1)` 按 `SMA_t = (X_t + (N - 1) * SMA_{t-1}) / N` 递推，首个可用值以当期 `X` 初始化。最近四根 K 线高低区间为零的证券不生成 Python 信号，并在输出中记录原因。

最终 Python 候选必须同时满足：

```text
brick[t] > brick[t-1]                         # 今日严格红柱
brick[t-1] < brick[t-2]                       # 昨日严格绿柱
brick[t] - brick[t-1] > (brick[t-2] - brick[t-1]) * 2 / 3
```

原生引擎不解释或替换 `砖` 的已保存公式正文。两引擎结果不同属于应报告的对照结果，不被静默合并或掩盖。

## 输出与失败处理

默认终端表格及 JSON 都包含：运行时间、引擎、板块代码与涨跌幅、股票代码、所属入选概念板块、原生/ Python 命中状态、Python 的最近三期砖型图数值、红绿柱长度、错误信息、各阶段耗时。

- 缺少通达信目录、插件或客户端连接：明确失败，不降级到 Tushare 或免费行情源。
- 原生公式调用错误：保留 Python 结果与原始错误；`native` 单独运行时返回失败状态。
- 单个板块或证券数据缺失：记录后继续处理其他对象；若无法计算板块前 N，则整体失败。
- 原生批量结果过大：按配置的批次大小顺序执行并汇总，默认批次为 200 个证券。

## 测试与知识沉淀

- 新增不依赖 Windows/通达信的单元测试，覆盖 SMA 递推、零区间跳过、严格绿转红、红柱长度阈值、概念板块及成分股去重、两种引擎结果对照。
- 更新 `knowledge/wiki/portfolios/short-term-momentum-brick-indicator-system.md`：原样保存用户提供的原始公式，记录原生公式名 `砖`、14:40 执行时间、概念板块前五范围，以及 Python 严格信号与原生公式结果可能不同的边界。
- 更新 `knowledge/wiki/log.md`，保留此次公式来源与实现设计的可追溯记录。

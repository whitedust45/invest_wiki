import {request} from "../../utils/api";

type Currency = "CNY" | "USD";

interface PortfolioPositionResponse {
  cashAccountId: string;
  instrumentId: string;
  currency: Currency;
  quantity: string;
  marketValueCent: string | null;
  costCent: string | null;
  unrealizedPnlCent: string | null;
  valuationStatus: string;
}

interface PortfolioCurrencyResponse {
  currency: Currency;
  cashCent: string;
  marginCent: string;
  marketValueCent: string | null;
  netAssetCent: string | null;
  positions: PortfolioPositionResponse[];
  asOf: string;
  sourceLedgerVersion: string;
  valuationStatus: string;
}

interface PortfolioSummaryResponse { items: PortfolioCurrencyResponse[]; }
interface CashAccountResponse {
  accountId: string;
  displayName: string;
  currency: Currency;
  status: "ACTIVE" | "DISABLED";
}
interface CashAccountListResponse { items: CashAccountResponse[]; }
interface AllocationSliceResponse { instrumentId: string; marketValueCent: string; shareBasisPoints: number; }
interface AllocationCurrencyResponse {
  currency: Currency; valuationStatus: string; marketValueCent: string | null; slices: AllocationSliceResponse[];
}
interface AllocationResponse { items: AllocationCurrencyResponse[]; }
interface HistoryPointResponse {
  dailySnapshotId: string; currency: Currency; asOfDate: string; netAssetCent: string; cashCent: string;
  marketValueCent: string; sourceLedgerVersion: string; calculatedAt: string;
}
interface HistoryChartPointResponse { dailySnapshotId: string; asOfDate: string; netAssetBasisPoints: number; }
interface HistoryResponse { items: HistoryPointResponse[]; chartPoints: HistoryChartPointResponse[]; }
interface StrategyCardResponse {
  strategyKey: string; displayName: string; currency: Currency; activeRuleVersionId: string | null;
  inputAt: string | null; status: string; message: string;
}
interface StrategyListResponse { items: StrategyCardResponse[]; }
interface StrategyWorkspaceResponse {
  strategy: StrategyCardResponse;
  availableActions: string[];
  latestEvaluation?: { status: string; result: { signals?: Array<{explanation?: string}>; missingOrStaleFields?: string[] } } | null;
}
interface TransactionResponse {
  transactionId: string; transactionType: string; occurredOn: string; currency: Currency | null; ledgerVersion: string;
  sourceType: string; importExportFileId: string | null;
}
interface TransactionPageResponse { items: TransactionResponse[]; nextCursor: string | null; }
interface InstrumentResponse { instrumentId: string; symbol: string; displayName: string; }
interface InstrumentPageResponse { items: InstrumentResponse[]; nextCursor: string | null; }
interface SnapshotWriteResponse { persistedCount: number; skippedUnvaluedCurrencyCount: number; }

interface ReportPositionDisplay extends PortfolioPositionResponse {
  displayName: string;
  marketValueDisplay: string;
  costDisplay: string;
  unrealizedPnlDisplay: string;
  valuationStatusLabel: string;
}
interface ReportCurrencyDisplay extends PortfolioCurrencyResponse {
  cashDisplay: string;
  marginDisplay: string;
  marketValueDisplay: string;
  netAssetDisplay: string;
  valuationStatusLabel: string;
  positions: ReportPositionDisplay[];
}
interface ReportHistoryDisplay extends HistoryPointResponse { netAssetDisplay: string; cashDisplay: string; marketValueDisplay: string; }
interface ReportStrategyDisplay extends StrategyCardResponse { statusLabel: string; inputLabel: string; }
interface ReportStrategyInsight {
  strategyKey: string;
  displayName: string;
  currency: Currency;
  statusLabel: string;
  explanation: string;
  missingFields: string[];
  nextStep: string;
}
interface ReportTransactionDisplay extends TransactionResponse { typeLabel: string; currencyLabel: string; }
interface ReportAllocationSliceDisplay extends AllocationSliceResponse {
  displayName: string;
  marketValueDisplay: string;
  shareDisplay: string;
  sharePercent: number;
}
interface ReportAllocationCurrencyDisplay extends AllocationCurrencyResponse {
  valuationStatusLabel: string;
  slices: ReportAllocationSliceDisplay[];
}
interface AccountTreeNode {
  accountId: string;
  displayName: string;
  currency: Currency;
  status: string;
  cashDisplay: string;
  marginDisplay: string;
  netAssetDisplay: string;
  valuationStatusLabel: string;
  positionCount: number;
}

interface ReportsPageData {
  loading: boolean;
  recordingSnapshot: boolean;
  errorMessage: string;
  lastSnapshotMessage: string;
  currencyReports: ReportCurrencyDisplay[];
  accountTree: AccountTreeNode[];
  cnyHistory: ReportHistoryDisplay[];
  usdHistory: ReportHistoryDisplay[];
  cnyChartPoints: HistoryChartPointResponse[];
  usdChartPoints: HistoryChartPointResponse[];
  allocations: ReportAllocationCurrencyDisplay[];
  strategies: ReportStrategyDisplay[];
  strategyInsights: ReportStrategyInsight[];
  transactions: ReportTransactionDisplay[];
}

interface ReportsPageMethods {
  loadReports(): Promise<void>;
  recordTodaySnapshot(): Promise<void>;
  drawHistoryCharts(): void;
}

Page<ReportsPageData, ReportsPageMethods>({
  data: {
    loading: true,
    recordingSnapshot: false,
    errorMessage: "",
    lastSnapshotMessage: "",
    currencyReports: [],
    accountTree: [],
    cnyHistory: [],
    usdHistory: [],
    cnyChartPoints: [],
    usdChartPoints: [],
    allocations: [],
    strategies: [],
    strategyInsights: [],
    transactions: []
  },

  onShow() {
    this.getTabBar()?.setData({selected: 4});
    void this.loadReports();
  },

  async loadReports() {
    this.setData({loading: true, errorMessage: ""});
    const range = reportDateRange();
    try {
      const [portfolio, allocation, strategies, cnyHistory, usdHistory, transactions, instruments, accounts] = await Promise.all([
        request<PortfolioSummaryResponse>("/api/v1/portfolio/summary", "GET"),
        request<AllocationResponse>("/api/v1/reports/allocation", "GET"),
        request<StrategyListResponse>("/api/v1/strategies", "GET"),
        request<HistoryResponse>(`/api/v1/reports/portfolio-history?currency=CNY&from=${range.from}&to=${range.to}&limit=366`, "GET"),
        request<HistoryResponse>(`/api/v1/reports/portfolio-history?currency=USD&from=${range.from}&to=${range.to}&limit=366`, "GET"),
        request<TransactionPageResponse>("/api/v1/ledger/transactions?limit=30", "GET"),
        request<InstrumentPageResponse>("/api/v1/market/instruments?limit=100", "GET"),
        request<CashAccountListResponse>("/api/v1/ledger/accounts", "GET")
      ]);
      const workspaces = await Promise.all(strategies.items.map((item) =>
        request<StrategyWorkspaceResponse>(`/api/v1/strategies/${item.strategyKey}/workspace`, "GET")));
      const accountViews = await Promise.all(accounts.items.map(async (account) => {
        const view = await request<PortfolioCurrencyResponse>(
          `/api/v1/portfolio/positions?currency=${account.currency}&accountId=${account.accountId}`, "GET");
        return accountTreeNode(account, view);
      }));
      const labels: Record<string, string> = {};
      instruments.items.forEach((instrument) => { labels[instrument.instrumentId] = `${instrument.symbol} · ${instrument.displayName}`; });
      this.setData({
        currencyReports: portfolio.items.map((item) => currencyDisplay(item, labels)),
        accountTree: accountViews,
        allocations: allocation.items.map((item) => allocationDisplay(item, labels)),
        cnyHistory: cnyHistory.items.map(historyDisplay),
        usdHistory: usdHistory.items.map(historyDisplay),
        cnyChartPoints: cnyHistory.chartPoints,
        usdChartPoints: usdHistory.chartPoints,
        strategies: strategies.items.map(strategyDisplay),
        strategyInsights: workspaces.map(strategyInsight),
        transactions: transactions.items.map(transactionDisplay)
      });
      this.drawHistoryCharts();
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({errorMessage: problem.message || "报表读取失败，请检查登录和本地服务。"});
    } finally {
      this.setData({loading: false});
    }
  },

  async recordTodaySnapshot() {
    this.setData({recordingSnapshot: true, errorMessage: "", lastSnapshotMessage: ""});
    try {
      const response = await request<SnapshotWriteResponse>("/api/v1/reports/portfolio-history", "POST");
      const suffix = response.skippedUnvaluedCurrencyCount > 0
        ? `；${response.skippedUnvaluedCurrencyCount} 个币种因未估值未写入` : "";
      this.setData({lastSnapshotMessage: `已追加 ${response.persistedCount} 个同币种快照${suffix}`});
      await this.loadReports();
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({errorMessage: problem.message || "今日快照未写入。"});
    } finally {
      this.setData({recordingSnapshot: false});
    }
  },

  drawHistoryCharts() {
    wx.nextTick(() => {
      drawHistoryChart("cny-history-chart", this.data.cnyChartPoints, "#0f766e", "#d9ebe5");
      drawHistoryChart("usd-history-chart", this.data.usdChartPoints, "#315c97", "#e4ecfb");
    });
  }
});

/** Coordinates are server-provided basis points; the client never parses or calculates monetary strings. */
function drawHistoryChart(canvasId: string, points: HistoryChartPointResponse[], stroke: string, fill: string): void {
  const context = wx.createCanvasContext(canvasId);
  const width = 680;
  const height = 220;
  const left = 26;
  const right = width - 18;
  const top = 18;
  const bottom = height - 28;
  context.clearRect(0, 0, width, height);
  context.setStrokeStyle("#e8e6df");
  context.setLineWidth(1);
  [0.25, 0.5, 0.75].forEach((ratio) => {
    const y = top + (bottom - top) * ratio;
    context.beginPath();
    context.moveTo(left, y);
    context.lineTo(right, y);
    context.stroke();
  });
  if (points.length === 0) {
    context.draw();
    return;
  }
  const coordinates = points.map((point, index) => ({
    x: points.length === 1 ? (left + right) / 2 : left + (right - left) * index / (points.length - 1),
    y: bottom - (bottom - top) * point.netAssetBasisPoints / 10_000
  }));
  context.setFillStyle(fill);
  context.beginPath();
  context.moveTo(coordinates[0].x, bottom);
  coordinates.forEach((point) => context.lineTo(point.x, point.y));
  context.lineTo(coordinates[coordinates.length - 1].x, bottom);
  context.closePath();
  context.fill();
  context.setStrokeStyle(stroke);
  context.setLineWidth(3);
  context.beginPath();
  coordinates.forEach((point, index) => {
    if (index === 0) context.moveTo(point.x, point.y);
    else context.lineTo(point.x, point.y);
  });
  context.stroke();
  context.setFillStyle(stroke);
  [coordinates[0], coordinates[coordinates.length - 1]].forEach((point) => {
    context.beginPath();
    context.arc(point.x, point.y, 4, 0, Math.PI * 2);
    context.fill();
  });
  context.draw();
}

function currencyDisplay(value: PortfolioCurrencyResponse, labels: Record<string, string>): ReportCurrencyDisplay {
  return {
    ...value,
    cashDisplay: formatMinorUnit(value.cashCent, value.currency),
    marginDisplay: formatMinorUnit(value.marginCent, value.currency),
    marketValueDisplay: value.marketValueCent === null ? "待估值" : formatMinorUnit(value.marketValueCent, value.currency),
    netAssetDisplay: value.netAssetCent === null ? "暂不展示" : formatMinorUnit(value.netAssetCent, value.currency),
    valuationStatusLabel: valuationStatusLabel(value.valuationStatus),
    positions: value.positions.map((position) => ({...position,
      displayName: labels[position.instrumentId] || position.instrumentId,
      marketValueDisplay: position.marketValueCent === null ? "未估值或用户级总市值" : formatMinorUnit(position.marketValueCent, position.currency),
      costDisplay: position.costCent === null ? "成本不可用" : formatMinorUnit(position.costCent, position.currency),
      unrealizedPnlDisplay: position.unrealizedPnlCent === null ? "暂不计算" : formatMinorUnit(position.unrealizedPnlCent, position.currency),
      valuationStatusLabel: valuationStatusLabel(position.valuationStatus)}))
  };
}

function accountTreeNode(account: CashAccountResponse, value: PortfolioCurrencyResponse): AccountTreeNode {
  return {accountId: account.accountId, displayName: account.displayName, currency: account.currency,
    status: account.status === "ACTIVE" ? "启用" : "已停用", cashDisplay: formatMinorUnit(value.cashCent, value.currency),
    marginDisplay: formatMinorUnit(value.marginCent, value.currency),
    netAssetDisplay: value.netAssetCent === null ? "暂不展示" : formatMinorUnit(value.netAssetCent, value.currency),
    valuationStatusLabel: valuationStatusLabel(value.valuationStatus), positionCount: value.positions.length};
}

function historyDisplay(value: HistoryPointResponse): ReportHistoryDisplay {
  return {...value, netAssetDisplay: formatMinorUnit(value.netAssetCent, value.currency),
    cashDisplay: formatMinorUnit(value.cashCent, value.currency),
    marketValueDisplay: formatMinorUnit(value.marketValueCent, value.currency)};
}

function allocationDisplay(value: AllocationCurrencyResponse, labels: Record<string, string>): ReportAllocationCurrencyDisplay {
  return {...value, valuationStatusLabel: valuationStatusLabel(value.valuationStatus), slices: value.slices.map((slice) => ({
    ...slice,
    displayName: labels[slice.instrumentId] || slice.instrumentId,
    marketValueDisplay: formatMinorUnit(slice.marketValueCent, value.currency),
    shareDisplay: `${(slice.shareBasisPoints / 100).toFixed(2)}%`,
    sharePercent: slice.shareBasisPoints / 100
  }))};
}

function strategyDisplay(value: StrategyCardResponse): ReportStrategyDisplay {
  return {...value, statusLabel: strategyStatusLabel(value.status),
    inputLabel: value.inputAt ? value.inputAt.replace("T", " ").replace("Z", " UTC") : "暂无有效输入"};
}

/** The server owns evaluation and all financial arithmetic; this view only renders its explicit findings. */
function strategyInsight(value: StrategyWorkspaceResponse): ReportStrategyInsight {
  const result = value.latestEvaluation?.result;
  const signal = result?.signals?.[0]?.explanation || value.strategy.message;
  const missingFields = (result?.missingOrStaleFields || []).map(missingFieldLabel);
  return {strategyKey: value.strategy.strategyKey, displayName: value.strategy.displayName,
    currency: value.strategy.currency, statusLabel: strategyStatusLabel(value.strategy.status), explanation: signal,
    missingFields, nextStep: availableActionLabel(value.availableActions)};
}

function missingFieldLabel(value: string): string {
  const labels: Record<string, string> = {active_rule: "活动规则", active_rule_body: "活动规则内容",
    strategy_ledger_facts: "策略归属账本事实", instrument_configuration: "标的/合约配置",
    reference_nav: "USD 参考净值", market_snapshot: "已确认市场快照", currency_mismatch: "原币种一致性"};
  return labels[value] || value;
}

function availableActionLabel(actions: string[]): string {
  if (actions.includes("CREATE_RULE_VERSION")) return "先建立可追溯的规则版本";
  if (actions.includes("CREATE_CASH_ACCOUNT")) return "先创建对应原币种现金账户";
  if (actions.includes("CREATE_INSTRUMENT")) return "先配置标的或合约主数据";
  if (actions.includes("RECORD_MARGIN")) return "核对保证金与市场快照后，再进入策略工作区记录事实";
  if (actions.includes("RECORD_OPTION")) return "在策略工作区核对预算与到期梯度";
  return "查看策略工作区的完整规则、输入与评估记录";
}

function transactionDisplay(value: TransactionResponse): ReportTransactionDisplay {
  return {...value, typeLabel: transactionTypeLabel(value.transactionType), currencyLabel: value.currency || "非货币事实"};
}

function formatMinorUnit(value: string, currency: Currency): string {
  const negative = value.startsWith("-");
  const digits = (negative ? value.slice(1) : value).replace(/^0+(?=\d)/, "") || "0";
  const padded = digits.length < 2 ? `0${digits}` : digits;
  return `${currency} ${negative ? "-" : ""}${padded.slice(0, -2) || "0"}.${padded.slice(-2)}`;
}

function valuationStatusLabel(value: string): string {
  const labels: Record<string, string> = {
    NO_OPEN_POSITION: "无未平仓持仓", MANUAL: "估值完整", MANUAL_TOTAL_UNALLOCATED: "用户级总市值未分摊",
    PARTIALLY_UNVALUED: "部分持仓待估值", UNVALUED: "待估值", MANUAL_UNIT_PRICE: "按单位价格估值",
    EXPIRED: "估值已过期", PRECISION_UNAVAILABLE: "估值精度不可表示", FUTURES_SETTLEMENT_ONLY: "期货仅纳入结算"
  };
  return labels[value] || value;
}

function strategyStatusLabel(value: string): string {
  const labels: Record<string, string> = {IN_RANGE: "符合规则", WATCH: "需要关注", BLOCKED: "等待前置条件",
    DATA_STALE: "数据过期", CROSS_CURRENCY_UNVALUED: "跨币种不可估值"};
  return labels[value] || value;
}

function transactionTypeLabel(value: string): string {
  const labels: Record<string, string> = {
    EXTERNAL_FUNDING: "外部入金", EXTERNAL_WITHDRAWAL: "外部出金", INTERNAL_TRANSFER: "账户内调拨", FEE: "独立费用",
    TRADE_BUY: "现货买入", TRADE_SELL: "现货卖出", DIVIDEND: "现金分红", INTEREST: "利息收入",
    CORPORATE_ACTION: "公司行为", FUTURES_MARGIN: "期货保证金", FUTURES_OPEN: "期货开仓", FUTURES_CLOSE: "期货平仓",
    FUTURES_DAILY_SETTLEMENT: "期货逐日结算", OPTION_OPEN: "期权买入开仓", OPTION_CLOSE: "期权卖出平仓",
    OPTION_EXPIRE: "期权到期无价值核销", REVERSAL: "冲正"
  };
  return labels[value] || value;
}

function reportDateRange(): {from: string; to: string} {
  const end = new Date();
  const start = new Date(end.getFullYear() - 1, end.getMonth(), end.getDate());
  return {from: dateText(start), to: dateText(end)};
}

function dateText(value: Date): string {
  const month = String(value.getMonth() + 1).padStart(2, "0");
  const day = String(value.getDate()).padStart(2, "0");
  return `${value.getFullYear()}-${month}-${day}`;
}

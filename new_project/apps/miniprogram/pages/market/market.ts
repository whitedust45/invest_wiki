import {request} from "../../utils/api";
import {newIdempotencyKey} from "../../utils/idempotency";

type AssetType = "EQUITY" | "ETF" | "INDEX" | "FUTURE" | "OPTION";
type Currency = "CNY" | "USD";

interface InstrumentResponse {
  instrumentId: string;
  market: string;
  exchange: string;
  symbol: string;
  displayName: string;
  assetType: AssetType;
  nativeCurrency: Currency;
  status: "ACTIVE";
  tushareCode: string | null;
  underlyingInstrumentId: string | null;
}

interface InstrumentPageResponse { items: InstrumentResponse[]; nextCursor: string | null; }
interface SyncRunResponse { marketSyncRunId: string; tradingDate: string; runType: string; status: string; completedAt: string | null; }
interface SnapshotAccepted { marketSnapshotSubmissionId: string; marketSyncRunId: string; status: "QUEUED"; }
interface MarketAttemptResponse {
  marketSyncAttemptId: string; attemptNo: number; triggerType: string; status: string; sourceName: string | null;
  errorCode: string | null; errorSummary: string | null; startedAt: string; completedAt: string | null;
}
interface MarketRunOverviewResponse {
  marketSyncRunId: string; tradingDate: string; runType: string; status: string; triggeredBy: string; startedAt: string;
  completedAt: string | null; attempts: MarketAttemptResponse[];
}
interface MarketQuoteResponse {
  quoteSnapshotId: string; instrumentId: string; symbol: string; displayName: string; currency: Currency; priceCent: string;
  prevCloseCent: string | null; quoteTime: string; sourceName: string;
}
interface MarketMetricResponse {
  dailyMetricId: string; instrumentId: string; symbol: string; displayName: string; tradeDate: string; metricName: string;
  valueDecimal: string | null; valueCent: string | null; currency: Currency | null; sourceName: string;
}
interface MarketBasisResponse {
  basisSnapshotId: string; underlyingInstrumentId: string; underlyingSymbol: string; futureInstrumentId: string;
  futureSymbol: string; productCode: string; tradeDate: string; spotPricePoints: string; futurePricePoints: string;
  basisPoints: string; annualizedBasisDecimal: string | null; maturityDate: string | null; daysLeft: number | null;
  sourceName: string;
}
interface MarketSourceEventResponse {
  marketSourceEventId: string; instrumentId: string | null; symbol: string | null; sourceName: string | null;
  eventType: string; severity: string; errorCode: string | null; errorSummary: string; createdAt: string;
}
interface MarketOverviewResponse {
  latestRun: MarketRunOverviewResponse | null; quotes: MarketQuoteResponse[]; metrics: MarketMetricResponse[];
  basis: MarketBasisResponse[]; sourceEvents: MarketSourceEventResponse[];
}
interface MarketQuoteDisplay extends MarketQuoteResponse { priceDisplay: string; previousDisplay: string; }
interface MarketMetricDisplay extends MarketMetricResponse { valueDisplay: string; }
interface MarketBasisDisplay extends MarketBasisResponse { annualizedBasisDisplay: string; maturityDisplay: string; }
interface MarketEventDisplay extends MarketSourceEventResponse { severityLabel: string; eventLabel: string; }
interface MarketOverviewDisplay {
  latestRun: MarketRunOverviewResponse | null; runStatusLabel: string; quotes: MarketQuoteDisplay[];
  metrics: MarketMetricDisplay[]; basis: MarketBasisDisplay[]; sourceEvents: MarketEventDisplay[];
}

interface MarketPageData {
  creating: boolean;
  assetTypes: AssetType[];
  assetTypeIndex: number;
  currencies: Currency[];
  currencyIndex: number;
  market: string;
  exchange: string;
  symbol: string;
  displayName: string;
  maturityDate: string;
  futureProducts: string[];
  futureProductIndex: number;
  futureMultiplierCent: string;
  optionRights: string[];
  optionRightIndex: number;
  optionUnderlyingInstrumentId: string;
  optionStrikePriceCent: string;
  optionMultiplier: string;
  tushareCode: string;
  futureUnderlyingInstrumentId: string;
  instruments: InstrumentResponse[];
  loadingInstruments: boolean;
  marketOverview: MarketOverviewDisplay | null;
  loadingMarketOverview: boolean;
  snapshotJson: string;
  submittingSnapshot: boolean;
  syncRun: SyncRunResponse | null;
  createdInstrument: InstrumentResponse | null;
  errorMessage: string;
}

interface MarketPageMethods {
  onAssetTypeChange(event: WechatMiniprogram.PickerChange): void;
  onCurrencyChange(event: WechatMiniprogram.PickerChange): void;
  onMarketInput(event: WechatMiniprogram.Input): void;
  onExchangeInput(event: WechatMiniprogram.Input): void;
  onSymbolInput(event: WechatMiniprogram.Input): void;
  onDisplayNameInput(event: WechatMiniprogram.Input): void;
  onMaturityDateChange(event: WechatMiniprogram.PickerChange): void;
  onFutureProductChange(event: WechatMiniprogram.PickerChange): void;
  onFutureMultiplierInput(event: WechatMiniprogram.Input): void;
  onOptionRightChange(event: WechatMiniprogram.PickerChange): void;
  onOptionUnderlyingInput(event: WechatMiniprogram.Input): void;
  onOptionStrikeInput(event: WechatMiniprogram.Input): void;
  onOptionMultiplierInput(event: WechatMiniprogram.Input): void;
  onTushareCodeInput(event: WechatMiniprogram.Input): void;
  onFutureUnderlyingInput(event: WechatMiniprogram.Input): void;
  onSnapshotJsonInput(event: WechatMiniprogram.Input): void;
  createInstrument(): Promise<void>;
  loadInstruments(): Promise<void>;
  loadMarketOverview(): Promise<void>;
  submitSnapshot(): Promise<void>;
  refreshSyncRun(): Promise<void>;
  copyInstrumentId(): void;
  goLedger(): void;
  instrumentPayload(): Record<string, unknown> | null;
}

Page<MarketPageData, MarketPageMethods>({
  data: {
    creating: false,
    assetTypes: ["EQUITY", "ETF", "INDEX", "FUTURE", "OPTION"],
    assetTypeIndex: 1,
    currencies: ["CNY", "USD"],
    currencyIndex: 1,
    market: "US",
    exchange: "NASDAQ",
    symbol: "",
    displayName: "",
    maturityDate: today(),
    futureProducts: ["IC", "IM"],
    futureProductIndex: 0,
    futureMultiplierCent: "",
    optionRights: ["PUT", "CALL"],
    optionRightIndex: 0,
    optionUnderlyingInstrumentId: "",
    optionStrikePriceCent: "",
    optionMultiplier: "",
    tushareCode: "",
    futureUnderlyingInstrumentId: "",
    instruments: [],
    loadingInstruments: false,
    marketOverview: null,
    loadingMarketOverview: false,
    snapshotJson: "",
    submittingSnapshot: false,
    syncRun: null,
    createdInstrument: null,
    errorMessage: ""
  },

  onShow() {
    this.getTabBar()?.setData({selected: 3});
    void this.loadInstruments();
    void this.loadMarketOverview();
  },

  onAssetTypeChange(event) {
    this.setData({assetTypeIndex: Number(event.detail.value), createdInstrument: null, errorMessage: ""});
  },

  onCurrencyChange(event) {
    this.setData({currencyIndex: Number(event.detail.value), createdInstrument: null, errorMessage: ""});
  },

  onMarketInput(event) {
    this.setData({market: event.detail.value, createdInstrument: null, errorMessage: ""});
  },

  onExchangeInput(event) {
    this.setData({exchange: event.detail.value, createdInstrument: null, errorMessage: ""});
  },

  onSymbolInput(event) {
    this.setData({symbol: event.detail.value, createdInstrument: null, errorMessage: ""});
  },

  onDisplayNameInput(event) {
    this.setData({displayName: event.detail.value, createdInstrument: null, errorMessage: ""});
  },

  onMaturityDateChange(event) {
    if (typeof event.detail.value !== "string") {
      return;
    }
    this.setData({maturityDate: event.detail.value, createdInstrument: null, errorMessage: ""});
  },

  onFutureProductChange(event) {
    this.setData({futureProductIndex: Number(event.detail.value), createdInstrument: null, errorMessage: ""});
  },

  onFutureMultiplierInput(event) {
    this.setData({futureMultiplierCent: event.detail.value, createdInstrument: null, errorMessage: ""});
  },

  onOptionRightChange(event) {
    this.setData({optionRightIndex: Number(event.detail.value), createdInstrument: null, errorMessage: ""});
  },

  onOptionUnderlyingInput(event) {
    this.setData({optionUnderlyingInstrumentId: event.detail.value, createdInstrument: null, errorMessage: ""});
  },

  onOptionStrikeInput(event) {
    this.setData({optionStrikePriceCent: event.detail.value, createdInstrument: null, errorMessage: ""});
  },

  onOptionMultiplierInput(event) {
    this.setData({optionMultiplier: event.detail.value, createdInstrument: null, errorMessage: ""});
  },

  onTushareCodeInput(event) { this.setData({tushareCode: event.detail.value, errorMessage: ""}); },
  onFutureUnderlyingInput(event) { this.setData({futureUnderlyingInstrumentId: event.detail.value, errorMessage: ""}); },
  onSnapshotJsonInput(event) { this.setData({snapshotJson: event.detail.value, errorMessage: ""}); },

  async createInstrument() {
    const payload = this.instrumentPayload();
    if (!payload) {
      return;
    }
    this.setData({creating: true, errorMessage: ""});
    try {
      const instrument = await request<InstrumentResponse>("/api/v1/market/instruments", "POST", payload,
        {"Idempotency-Key": newIdempotencyKey()});
      wx.setStorageSync("investment.tradeInstrumentId", instrument.instrumentId);
      this.setData({createdInstrument: instrument});
      await this.loadInstruments();
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({errorMessage: problem.message || "标的创建失败，请检查字段后重试"});
    } finally {
      this.setData({creating: false});
    }
  },

  async loadInstruments() {
    this.setData({loadingInstruments: true});
    try {
      const response = await request<InstrumentPageResponse>("/api/v1/market/instruments?limit=100", "GET");
      this.setData({instruments: response.items});
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({errorMessage: problem.message || "标的列表读取失败"});
    } finally {
      this.setData({loadingInstruments: false});
    }
  },

  async loadMarketOverview() {
    this.setData({loadingMarketOverview: true});
    try {
      const overview = await request<MarketOverviewResponse>("/api/v1/market/overview", "GET");
      this.setData({marketOverview: marketOverviewDisplay(overview)});
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({errorMessage: problem.message || "行情工作台读取失败"});
    } finally {
      this.setData({loadingMarketOverview: false});
    }
  },

  async submitSnapshot() {
    let payload: object;
    try {
      payload = JSON.parse(this.data.snapshotJson) as object;
    } catch (_) {
      this.setData({errorMessage: "快照内容必须是符合接口约定的 JSON 对象"});
      return;
    }
    this.setData({submittingSnapshot: true, errorMessage: ""});
    try {
      const accepted = await request<SnapshotAccepted>("/api/v1/market/snapshot-submissions", "POST", payload);
      wx.showToast({title: "快照已入队", icon: "success"});
      const syncRun = await request<SyncRunResponse>(`/api/v1/market/sync-runs/${accepted.marketSyncRunId}`, "GET");
      this.setData({syncRun});
      await this.loadMarketOverview();
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({errorMessage: problem.message || "行情快照提交失败"});
    } finally {
      this.setData({submittingSnapshot: false});
    }
  },

  async refreshSyncRun() {
    const syncRun = this.data.syncRun;
    if (!syncRun) return;
    try {
      this.setData({syncRun: await request<SyncRunResponse>(`/api/v1/market/sync-runs/${syncRun.marketSyncRunId}`, "GET")});
      await this.loadMarketOverview();
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({errorMessage: problem.message || "同步状态读取失败"});
    }
  },

  copyInstrumentId() {
    const instrumentId = this.data.createdInstrument?.instrumentId;
    if (!instrumentId) {
      return;
    }
    wx.setClipboardData({data: instrumentId});
  },

  goLedger() {
    wx.switchTab({url: "/pages/ledger/ledger"});
  },

  instrumentPayload(): Record<string, unknown> | null {
    const assetType = this.data.assetTypes[this.data.assetTypeIndex];
    const market = this.data.market.trim();
    const exchange = this.data.exchange.trim();
    const symbol = this.data.symbol.trim();
    const displayName = this.data.displayName.trim();
    if (!market || !exchange || !symbol || !displayName) {
      this.setData({errorMessage: "请填写市场、交易所、代码和标的名称"});
      return null;
    }
    const payload: Record<string, unknown> = {
      market,
      exchange,
      symbol,
      displayName,
      assetType,
      nativeCurrency: this.data.currencies[this.data.currencyIndex]
    };
    const tushareCode = this.data.tushareCode.trim();
    if (tushareCode) payload.tushareCode = tushareCode;
    if (assetType === "FUTURE") {
      const contractMultiplierCent = this.data.futureMultiplierCent.trim();
      const underlyingInstrumentId = this.data.futureUnderlyingInstrumentId.trim();
      if (!this.data.maturityDate || !/^[1-9][0-9]*$/.test(contractMultiplierCent)
          || !/^[0-9A-HJKMNP-TV-Z]{26}$/.test(underlyingInstrumentId)) {
        this.setData({errorMessage: "期货必须填写到期日、指数标的 ID 和正整数合约乘数最小单位"});
        return null;
      }
      payload.maturityDate = this.data.maturityDate;
      payload.future = {
        productCode: this.data.futureProducts[this.data.futureProductIndex],
        contractMultiplierCent
      };
      payload.underlyingInstrumentId = underlyingInstrumentId;
    }
    if (assetType === "OPTION") {
      const underlyingInstrumentId = this.data.optionUnderlyingInstrumentId.trim();
      const strikePriceCent = this.data.optionStrikePriceCent.trim();
      const contractMultiplier = this.data.optionMultiplier.trim();
      if (!this.data.maturityDate || !/^[0-9A-HJKMNP-TV-Z]{26}$/.test(underlyingInstrumentId)
          || !/^[1-9][0-9]*$/.test(strikePriceCent) || !/^[1-9][0-9]*$/.test(contractMultiplier)) {
        this.setData({errorMessage: "期权须填写到期日、标的 ID、行权价最小单位与正整数合约乘数"});
        return null;
      }
      payload.maturityDate = this.data.maturityDate;
      payload.option = {
        underlyingInstrumentId,
        optionRight: this.data.optionRights[this.data.optionRightIndex],
        strikePriceCent,
        contractMultiplier
      };
    }
    return payload;
  }
});

function today(): string {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${now.getFullYear()}-${month}-${day}`;
}

function marketOverviewDisplay(value: MarketOverviewResponse): MarketOverviewDisplay {
  return {
    latestRun: value.latestRun,
    runStatusLabel: value.latestRun ? marketRunStatus(value.latestRun.status) : "尚无行情运行",
    quotes: value.quotes.map((quote) => ({...quote, priceDisplay: formatMinorUnit(quote.priceCent, quote.currency),
      previousDisplay: quote.prevCloseCent === null ? "无昨收" : formatMinorUnit(quote.prevCloseCent, quote.currency)})),
    metrics: value.metrics.map((metric) => ({...metric, valueDisplay: metric.valueDecimal !== null ? metric.valueDecimal
      : metric.valueCent !== null && metric.currency !== null ? formatMinorUnit(metric.valueCent, metric.currency) : "无值"})),
    basis: value.basis.map((basis) => ({...basis,
      annualizedBasisDisplay: basis.annualizedBasisDecimal === null ? "未提供" : basis.annualizedBasisDecimal,
      maturityDisplay: basis.maturityDate === null ? "未提供" : `${basis.maturityDate}${basis.daysLeft === null ? "" : ` · ${basis.daysLeft} 天`}`})),
    sourceEvents: value.sourceEvents.map((event) => ({...event, severityLabel: severityLabel(event.severity),
      eventLabel: event.symbol || event.sourceName || "全局来源"}))
  };
}

function formatMinorUnit(value: string, currency: Currency): string {
  const negative = value.startsWith("-");
  const digits = (negative ? value.slice(1) : value).replace(/^0+(?=\d)/, "") || "0";
  const padded = digits.length < 2 ? `0${digits}` : digits;
  return `${currency} ${negative ? "-" : ""}${padded.slice(0, -2) || "0"}.${padded.slice(-2)}`;
}

function marketRunStatus(value: string): string {
  const labels: Record<string, string> = {QUEUED: "排队中", RUNNING: "刷新中", SUCCEEDED: "已完成", FAILED: "失败"};
  return labels[value] || value;
}

function severityLabel(value: string): string {
  const labels: Record<string, string> = {INFO: "提示", WARN: "关注", WARNING: "关注", ERROR: "失败"};
  return labels[value] || value;
}

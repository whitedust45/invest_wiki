import {request} from "../../utils/api";
import {newIdempotencyKey} from "../../utils/idempotency";
import {runtimeConfig} from "../../config";

type Currency = "CNY" | "USD";
type ValuationMode = "UNIT_PRICE" | "MARKET_VALUE";
type SubmissionState = "IDLE" | "SUBMITTING" | "RETRYABLE_ERROR" | "SUCCESS";

interface MeResponse {
  userId: string;
  role: "ADMIN";
  sessionExpiresAt: string;
}

interface ManualValuationCreateRequest {
  instrumentId: string;
  valuationDate: string;
  currency: Currency;
  unitPriceCent?: string;
  marketValueCent?: string;
  validUntil?: string;
  note?: string;
}

interface ManualValuationResponse {
  manualValuationId: string;
  valuationStatus: "ACTIVE" | "EXPIRED";
}

interface CashAccount {
  accountId: string;
  displayName: string;
  currency: Currency;
  status: "ACTIVE" | "DISABLED";
}

interface CashAccountListResponse {
  items: CashAccount[];
}

interface ReconciliationPositionInput {
  instrumentId: string;
  quantity: string;
}

interface ReconciliationCreateRequest {
  cashAccountId: string;
  reconciliationDate: string;
  brokerCashCent: string;
  positions: ReconciliationPositionInput[];
  discrepancyReason?: string;
}

interface ReconciliationResponse {
  reconciliationId: string;
  status: "MATCHED" | "NEEDS_REVIEW";
  cashDifferenceCent: string;
  currency: Currency;
}

interface ReconciliationListResponse {
  items: ReconciliationResponse[];
  nextCursor: string | null;
}

interface PortfolioPositionResponse {
  cashAccountId: string;
  instrumentId: string;
  currency: Currency;
  quantity: string;
  marketValueCent: string | null;
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

interface PortfolioSummaryResponse {
  items: PortfolioCurrencyResponse[];
}

interface StrategyCardResponse {
  strategyKey: string;
  displayName: string;
  currency: Currency;
  activeRuleVersionId: string | null;
  inputAt: string | null;
  status: string;
  message: string;
}

interface StrategyListResponse { items: StrategyCardResponse[]; }
interface LocalSeedResponse {
  seedName: string;
  createdAccounts: number;
  createdInstruments: number;
  createdTransactions: number;
  createdEvaluations: number;
  currencies: Currency[];
}

interface PortfolioPositionDisplay extends PortfolioPositionResponse {
  positionKey: string;
  marketValueDisplay: string;
  valuationStatusLabel: string;
}

interface PortfolioCurrencyDisplay {
  currency: Currency;
  cashDisplay: string;
  marginDisplay: string;
  marketValueDisplay: string;
  netAssetDisplay: string;
  positions: PortfolioPositionDisplay[];
  asOf: string;
  sourceLedgerVersion: string;
  valuationStatusLabel: string;
}

interface OverviewStrategyDisplay extends StrategyCardResponse {
  statusLabel: string;
  statusClass: string;
  inputLabel: string;
  icon: string;
}

interface OverviewPageData {
  loading: boolean;
  userId: string;
  sessionExpiresAt: string;
  portfolioItems: PortfolioCurrencyDisplay[];
  portfolioError: string;
  strategies: OverviewStrategyDisplay[];
  localDevelopment: boolean;
  seedingLocalData: boolean;
  localSeedMessage: string;
  valuationInstrumentId: string;
  valuationDate: string;
  valuationCurrencyIndex: number;
  valuationCurrencies: Currency[];
  valuationModeIndex: number;
  valuationModes: ValuationMode[];
  valuationModeLabels: string[];
  valuationAmountCent: string;
  valuationValidUntil: string;
  valuationNote: string;
  valuationSubmissionState: SubmissionState;
  valuationPendingIdempotencyKey: string;
  valuationLastId: string;
  valuationLastStatus: string;
  reconciliationAccounts: CashAccount[];
  reconciliationAccountNames: string[];
  reconciliationAccountIndex: number;
  reconciliationDate: string;
  reconciliationBrokerCashCent: string;
  reconciliationPositions: ReconciliationPositionInput[];
  reconciliationReason: string;
  reconciliationSubmissionState: SubmissionState;
  reconciliationPendingIdempotencyKey: string;
  reconciliationLastId: string;
  reconciliationLastStatus: string;
  reconciliationLastCashDifferenceCent: string;
  reconciliationHistory: ReconciliationResponse[];
  errorMessage: string;
}

interface OverviewPageMethods {
  loadCurrentUser(): Promise<void>;
  loadPortfolioSummary(): Promise<void>;
  loadStrategies(): Promise<void>;
  seedLocalTestData(): void;
  runLocalTestSeed(): Promise<void>;
  loadCashAccounts(): Promise<void>;
  loadReconciliations(): Promise<void>;
  onValuationInstrumentIdInput(event: WechatMiniprogram.Input): void;
  onValuationDateChange(event: WechatMiniprogram.PickerChange): void;
  onValuationCurrencyChange(event: WechatMiniprogram.PickerChange): void;
  onValuationModeChange(event: WechatMiniprogram.PickerChange): void;
  onValuationAmountInput(event: WechatMiniprogram.Input): void;
  onValuationValidUntilInput(event: WechatMiniprogram.Input): void;
  onValuationNoteInput(event: WechatMiniprogram.Input): void;
  submitValuation(): Promise<void>;
  retryValuation(): Promise<void>;
  valuationPayload(): ManualValuationCreateRequest | null;
  resetValuationState(patch: Partial<OverviewPageData>): void;
  onReconciliationAccountChange(event: WechatMiniprogram.PickerChange): void;
  onReconciliationDateChange(event: WechatMiniprogram.PickerChange): void;
  onReconciliationBrokerCashInput(event: WechatMiniprogram.Input): void;
  onReconciliationPositionInstrumentInput(event: WechatMiniprogram.Input): void;
  onReconciliationPositionQuantityInput(event: WechatMiniprogram.Input): void;
  addReconciliationPosition(): void;
  removeReconciliationPosition(event: WechatMiniprogram.TouchEvent): void;
  onReconciliationReasonInput(event: WechatMiniprogram.Input): void;
  submitReconciliation(): Promise<void>;
  retryReconciliation(): Promise<void>;
  reconciliationPayload(): ReconciliationCreateRequest | null;
  resetReconciliationState(patch: Partial<OverviewPageData>): void;
}

Page<OverviewPageData, OverviewPageMethods>({
  data: {
    loading: true,
    userId: "",
    sessionExpiresAt: "",
    portfolioItems: [],
    portfolioError: "",
    strategies: [],
    localDevelopment: isLocalDevelopment(),
    seedingLocalData: false,
    localSeedMessage: "",
    valuationInstrumentId: "",
    valuationDate: today(),
    valuationCurrencyIndex: 0,
    valuationCurrencies: ["CNY", "USD"],
    valuationModeIndex: 0,
    valuationModes: ["UNIT_PRICE", "MARKET_VALUE"],
    valuationModeLabels: ["按单位价格估值", "按总市值估值"],
    valuationAmountCent: "",
    valuationValidUntil: "",
    valuationNote: "",
    valuationSubmissionState: "IDLE",
    valuationPendingIdempotencyKey: "",
    valuationLastId: "",
    valuationLastStatus: "",
    reconciliationAccounts: [],
    reconciliationAccountNames: [],
    reconciliationAccountIndex: 0,
    reconciliationDate: today(),
    reconciliationBrokerCashCent: "",
    reconciliationPositions: [],
    reconciliationReason: "",
    reconciliationSubmissionState: "IDLE",
    reconciliationPendingIdempotencyKey: "",
    reconciliationLastId: "",
    reconciliationLastStatus: "",
    reconciliationLastCashDifferenceCent: "",
    reconciliationHistory: [],
    errorMessage: ""
  },

  onShow() {
    this.getTabBar()?.setData({selected: 0});
    this.loadCurrentUser();
  },

  async loadCurrentUser() {
    if (!wx.getStorageSync("investment.accessToken")) {
      wx.reLaunch({url: "/pages/login/login"});
      return;
    }
    this.setData({loading: true, errorMessage: ""});
    try {
      const response = await request<MeResponse>("/api/v1/me", "GET");
      this.setData({userId: response.userId, sessionExpiresAt: response.sessionExpiresAt});
    } catch (error) {
      wx.removeStorageSync("investment.accessToken");
      const problem = error as {message?: string};
      this.setData({errorMessage: problem.message || "会话不可用，请重新登录"});
      return;
    }
    try {
      await Promise.all([this.loadCashAccounts(), this.loadReconciliations(), this.loadPortfolioSummary(), this.loadStrategies()]);
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({errorMessage: problem.message || "部分总览数据暂时无法读取，请稍后刷新。"});
    } finally {
      this.setData({loading: false});
    }
  },

  async loadCashAccounts() {
    const response = await request<CashAccountListResponse>("/api/v1/ledger/accounts", "GET");
    const accounts = response.items.filter((account) => account.status === "ACTIVE");
    this.setData({
      reconciliationAccounts: accounts,
      reconciliationAccountNames: accounts.map((account) => `${account.displayName} · ${account.currency}`),
      reconciliationAccountIndex: 0
    });
  },

  async loadReconciliations() {
    const response = await request<ReconciliationListResponse>("/api/v1/portfolio/reconciliations?limit=10", "GET");
    this.setData({reconciliationHistory: response.items});
  },

  async loadPortfolioSummary() {
    try {
      const response = await request<PortfolioSummaryResponse>("/api/v1/portfolio/summary", "GET");
      this.setData({portfolioItems: response.items.map(portfolioDisplay), portfolioError: ""});
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({portfolioItems: [], portfolioError: problem.message || "组合读模型暂时不可用，请稍后刷新。"});
    }
  },

  async loadStrategies() {
    const response = await request<StrategyListResponse>("/api/v1/strategies", "GET");
    this.setData({strategies: response.items.map(strategyDisplay)});
  },

  seedLocalTestData() {
    if (!this.data.localDevelopment || this.data.seedingLocalData) return;
    wx.showModal({
      title: "写入本地完整测试路径",
      content: "仅本地 Spring profile 可用，且只在当前账户账本为空时写入 23 笔受校验的测试交易。不会清空、覆盖或修改已有数据。",
      confirmText: "写入测试数据",
      success: (result) => {
        if (result.confirm) void this.runLocalTestSeed();
      }
    });
  },

  async runLocalTestSeed() {
    this.setData({seedingLocalData: true, errorMessage: "", localSeedMessage: ""});
    try {
      const result = await request<LocalSeedResponse>("/api/v1/development/strategy-test-seed", "POST", {
        seedSet: "LEGACY_FULL_PATH"
      });
      this.setData({localSeedMessage: `已写入 ${result.createdAccounts} 个账户、${result.createdInstruments} 个标的、${result.createdTransactions} 笔交易和 ${result.createdEvaluations} 份评估。`});
      await Promise.all([this.loadCashAccounts(), this.loadReconciliations(), this.loadPortfolioSummary(), this.loadStrategies()]);
      wx.showToast({title: "本地测试数据已就绪", icon: "success"});
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({errorMessage: problem.message || "本地测试数据未写入；请确认账本为空且服务使用 local profile。"});
    } finally {
      this.setData({seedingLocalData: false});
    }
  },

  onValuationInstrumentIdInput(event) {
    this.resetValuationState({valuationInstrumentId: event.detail.value});
  },

  onValuationDateChange(event) {
    if (typeof event.detail.value === "string") {
      this.resetValuationState({valuationDate: event.detail.value});
    }
  },

  onValuationCurrencyChange(event) {
    this.resetValuationState({valuationCurrencyIndex: Number(event.detail.value)});
  },

  onValuationModeChange(event) {
    this.resetValuationState({valuationModeIndex: Number(event.detail.value)});
  },

  onValuationAmountInput(event) {
    this.resetValuationState({valuationAmountCent: event.detail.value});
  },

  onValuationValidUntilInput(event) {
    this.resetValuationState({valuationValidUntil: event.detail.value});
  },

  onValuationNoteInput(event) {
    this.resetValuationState({valuationNote: event.detail.value});
  },

  async submitValuation() {
    const payload = this.valuationPayload();
    if (!payload) {
      return;
    }
    const key = this.data.valuationPendingIdempotencyKey || newIdempotencyKey();
    this.setData({
      valuationSubmissionState: "SUBMITTING",
      valuationPendingIdempotencyKey: key,
      valuationLastId: "",
      valuationLastStatus: "",
      errorMessage: ""
    });
    try {
      const result = await request<ManualValuationResponse>("/api/v1/portfolio/manual-valuations", "POST", payload,
        {"Idempotency-Key": key});
      this.setData({
        valuationSubmissionState: "SUCCESS",
        valuationPendingIdempotencyKey: "",
        valuationLastId: result.manualValuationId,
        valuationLastStatus: result.valuationStatus
      });
      await this.loadPortfolioSummary();
      wx.showToast({title: "估值已记录", icon: "success"});
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({
        valuationSubmissionState: "RETRYABLE_ERROR",
        errorMessage: problem.message || "网络或服务异常，未自动重试。请确认后手动重试。"
      });
    }
  },

  async retryValuation() {
    if (this.data.valuationSubmissionState === "RETRYABLE_ERROR" && this.data.valuationPendingIdempotencyKey) {
      await this.submitValuation();
    }
  },

  valuationPayload(): ManualValuationCreateRequest | null {
    const instrumentId = this.data.valuationInstrumentId.trim();
    const amountCent = this.data.valuationAmountCent.trim();
    const validUntil = this.data.valuationValidUntil.trim();
    if (!/^[0-9A-HJKMNP-TV-Z]{26}$/.test(instrumentId)) {
      this.setData({errorMessage: "请填写有效的 26 位标的 ID"});
      return null;
    }
    if (!/^[1-9][0-9]*$/.test(amountCent)) {
      this.setData({errorMessage: "估值金额必须以原币种最小单位填写正整数"});
      return null;
    }
    if (validUntil && !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,3})?Z$/.test(validUntil)) {
      this.setData({errorMessage: "有效期需为 ISO UTC 时间，例如 2026-08-01T00:00:00Z，或留空"});
      return null;
    }
    const payload: ManualValuationCreateRequest = {
      instrumentId,
      valuationDate: this.data.valuationDate,
      currency: this.data.valuationCurrencies[this.data.valuationCurrencyIndex]
    };
    if (this.data.valuationModes[this.data.valuationModeIndex] === "UNIT_PRICE") {
      payload.unitPriceCent = amountCent;
    } else {
      payload.marketValueCent = amountCent;
    }
    if (validUntil) {
      payload.validUntil = validUntil;
    }
    const note = this.data.valuationNote.trim();
    if (note) {
      payload.note = note;
    }
    return payload;
  },

  resetValuationState(patch: Partial<OverviewPageData>) {
    this.setData({
      ...patch,
      valuationSubmissionState: "IDLE",
      valuationPendingIdempotencyKey: "",
      valuationLastId: "",
      valuationLastStatus: "",
      errorMessage: ""
    });
  },

  onReconciliationAccountChange(event) {
    this.resetReconciliationState({reconciliationAccountIndex: Number(event.detail.value)});
  },

  onReconciliationDateChange(event) {
    if (typeof event.detail.value === "string") {
      this.resetReconciliationState({reconciliationDate: event.detail.value});
    }
  },

  onReconciliationBrokerCashInput(event) {
    this.resetReconciliationState({reconciliationBrokerCashCent: event.detail.value});
  },

  onReconciliationPositionInstrumentInput(event) {
    const index = Number(event.currentTarget.dataset.index);
    const positions = this.data.reconciliationPositions.map((position, current) => current === index
      ? {...position, instrumentId: event.detail.value} : position);
    this.resetReconciliationState({reconciliationPositions: positions});
  },

  onReconciliationPositionQuantityInput(event) {
    const index = Number(event.currentTarget.dataset.index);
    const positions = this.data.reconciliationPositions.map((position, current) => current === index
      ? {...position, quantity: event.detail.value} : position);
    this.resetReconciliationState({reconciliationPositions: positions});
  },

  addReconciliationPosition() {
    this.resetReconciliationState({reconciliationPositions: [...this.data.reconciliationPositions,
      {instrumentId: "", quantity: ""}]});
  },

  removeReconciliationPosition(event) {
    const index = Number(event.currentTarget.dataset.index);
    this.resetReconciliationState({reconciliationPositions: this.data.reconciliationPositions
      .filter((_, current) => current !== index)});
  },

  onReconciliationReasonInput(event) {
    this.resetReconciliationState({reconciliationReason: event.detail.value});
  },

  async submitReconciliation() {
    const payload = this.reconciliationPayload();
    if (!payload) {
      return;
    }
    const key = this.data.reconciliationPendingIdempotencyKey || newIdempotencyKey();
    this.setData({
      reconciliationSubmissionState: "SUBMITTING",
      reconciliationPendingIdempotencyKey: key,
      reconciliationLastId: "",
      reconciliationLastStatus: "",
      reconciliationLastCashDifferenceCent: "",
      errorMessage: ""
    });
    try {
      const result = await request<ReconciliationResponse>("/api/v1/portfolio/reconciliations", "POST", payload,
        {"Idempotency-Key": key});
      this.setData({
        reconciliationSubmissionState: "SUCCESS",
        reconciliationPendingIdempotencyKey: "",
        reconciliationLastId: result.reconciliationId,
        reconciliationLastStatus: result.status,
        reconciliationLastCashDifferenceCent: result.cashDifferenceCent
      });
      try {
        await this.loadReconciliations();
      } catch {
        // 对账事实已由幂等写入确认；列表刷新失败不应把成功提交伪装为失败。
      }
      wx.showToast({title: result.status === "MATCHED" ? "对账一致" : "发现待复核差异", icon: "success"});
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({
        reconciliationSubmissionState: "RETRYABLE_ERROR",
        errorMessage: problem.message || "网络或服务异常，未自动重试。请确认后手动重试。"
      });
    }
  },

  async retryReconciliation() {
    if (this.data.reconciliationSubmissionState === "RETRYABLE_ERROR"
        && this.data.reconciliationPendingIdempotencyKey) {
      await this.submitReconciliation();
    }
  },

  reconciliationPayload(): ReconciliationCreateRequest | null {
    const account = this.data.reconciliationAccounts[this.data.reconciliationAccountIndex];
    const brokerCashCent = this.data.reconciliationBrokerCashCent.trim();
    if (!account) {
      this.setData({errorMessage: "请先创建并选择启用中的现金账户"});
      return null;
    }
    if (!/^(?:0|[1-9][0-9]*)$/.test(brokerCashCent)) {
      this.setData({errorMessage: "券商现金必须以原币种最小单位填写非负整数"});
      return null;
    }
    const positions = this.data.reconciliationPositions.map((position) => ({
      instrumentId: position.instrumentId.trim(),
      quantity: position.quantity.trim()
    }));
    if (positions.some((position) => !/^[0-9A-HJKMNP-TV-Z]{26}$/.test(position.instrumentId)
        || !/^[1-9][0-9]*(?:\.[0-9]{1,8})?$/.test(position.quantity))) {
      this.setData({errorMessage: "每一行持仓都须填写 26 位标的 ID 与正数量（最多 8 位小数）"});
      return null;
    }
    const ids = new Set(positions.map((position) => position.instrumentId));
    if (ids.size !== positions.length) {
      this.setData({errorMessage: "券商持仓快照不得重复同一标的"});
      return null;
    }
    const payload: ReconciliationCreateRequest = {
      cashAccountId: account.accountId,
      reconciliationDate: this.data.reconciliationDate,
      brokerCashCent,
      positions
    };
    const reason = this.data.reconciliationReason.trim();
    if (reason) {
      payload.discrepancyReason = reason;
    }
    return payload;
  },

  resetReconciliationState(patch: Partial<OverviewPageData>) {
    this.setData({
      ...patch,
      reconciliationSubmissionState: "IDLE",
      reconciliationPendingIdempotencyKey: "",
      reconciliationLastId: "",
      reconciliationLastStatus: "",
      reconciliationLastCashDifferenceCent: "",
      errorMessage: ""
    });
  }
});

function today(): string {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${now.getFullYear()}-${month}-${day}`;
}

function portfolioDisplay(value: PortfolioCurrencyResponse): PortfolioCurrencyDisplay {
  return {
    currency: value.currency,
    cashDisplay: formatMinorUnit(value.cashCent, value.currency),
    marginDisplay: formatMinorUnit(value.marginCent, value.currency),
    marketValueDisplay: value.marketValueCent === null ? "暂不展示" : formatMinorUnit(value.marketValueCent, value.currency),
    netAssetDisplay: value.netAssetCent === null ? "暂不展示" : formatMinorUnit(value.netAssetCent, value.currency),
    positions: value.positions.map((position) => ({
      ...position,
      positionKey: `${position.cashAccountId}:${position.instrumentId}`,
      marketValueDisplay: position.marketValueCent === null ? "未按账户分摊或待估值"
        : formatMinorUnit(position.marketValueCent, position.currency),
      valuationStatusLabel: valuationStatusLabel(position.valuationStatus)
    })),
    asOf: value.asOf,
    sourceLedgerVersion: value.sourceLedgerVersion,
    valuationStatusLabel: valuationStatusLabel(value.valuationStatus)
  };
}

function strategyDisplay(value: StrategyCardResponse): OverviewStrategyDisplay {
  const icons: Record<string, string> = {
    HIGH_DIVIDEND: "息", QQQ_GROWTH: "长", IC_IM: "期", DEEP_PUT: "护"
  };
  const statuses: Record<string, {label: string; className: string}> = {
    IN_RANGE: {label: "区间内", className: "strategy-good"},
    WATCH: {label: "观察", className: "strategy-watch"},
    BLOCKED: {label: "等待数据", className: "strategy-blocked"},
    DATA_STALE: {label: "数据过期", className: "strategy-blocked"},
    CROSS_CURRENCY_UNVALUED: {label: "币种待确认", className: "strategy-blocked"}
  };
  const status = statuses[value.status] || {label: value.status, className: "strategy-blocked"};
  return {...value, icon: icons[value.strategyKey] || "策", statusLabel: status.label, statusClass: status.className,
    inputLabel: value.inputAt ? value.inputAt.replace("T", " ").replace("Z", " UTC") : "尚无评估输入"};
}

function isLocalDevelopment(): boolean {
  return /^http:\/\/(?:127\.0\.0\.1|localhost)(?::\d+)?(?:\/|$)/.test(runtimeConfig.apiBaseUrl);
}

function formatMinorUnit(value: string, currency: Currency): string {
  const negative = value.startsWith("-");
  const digits = (negative ? value.slice(1) : value).replace(/^0+(?=\d)/, "") || "0";
  const padded = digits.length < 2 ? `0${digits}` : digits;
  const integer = padded.slice(0, -2) || "0";
  return `${currency} ${negative ? "-" : ""}${integer}.${padded.slice(-2)}`;
}

function valuationStatusLabel(status: string): string {
  const labels: Record<string, string> = {
    NO_OPEN_POSITION: "无未平仓持仓",
    MANUAL: "手工估值已覆盖",
    MANUAL_UNIT_PRICE: "按手工单价估值",
    MANUAL_TOTAL_UNALLOCATED: "用户级总市值，未分摊到账户",
    PARTIALLY_UNVALUED: "部分持仓未估值",
    UNVALUED: "待估值",
    EXPIRED: "估值已过期",
    PRECISION_UNAVAILABLE: "单价与数量无法精确换算为分",
    FUTURES_SETTLEMENT_ONLY: "期货仅按手工逐日结算"
  };
  return labels[status] || status;
}

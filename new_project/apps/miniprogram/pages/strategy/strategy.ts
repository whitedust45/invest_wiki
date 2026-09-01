import {request} from "../../utils/api";

type Currency = "CNY" | "USD";
type StrategyKey = "HIGH_DIVIDEND" | "QQQ_GROWTH" | "IC_IM" | "DEEP_PUT";
type StrategyStatus = "IN_RANGE" | "WATCH" | "BLOCKED" | "DATA_STALE" | "CROSS_CURRENCY_UNVALUED";

interface StrategyCardResponse {
  strategyKey: StrategyKey;
  displayName: string;
  currency: Currency;
  activeRuleVersionId: string | null;
  inputAt: string | null;
  status: StrategyStatus;
  message: string;
}

interface StrategyListResponse {
  items: StrategyCardResponse[];
}

interface StrategyWorkspaceResponse {
  strategy: StrategyCardResponse;
  availableActions: string[];
  activeRule?: RuleVersionHistoryResponse | null;
  latestEvaluation?: EvaluationResponse | null;
}

interface EvaluationResponse {
  strategyEvaluationId: string | null;
  asOfAt: string;
  status: StrategyStatus;
  result: StrategyEvaluationResult;
}

interface StrategyEvaluationResult {
  currency?: Currency;
  missingOrStaleFields?: string[];
  signals?: Array<{explanation?: string}>;
  [key: string]: unknown;
}

interface EvaluationPageResponse {
  items: EvaluationResponse[];
  nextCursor: string | null;
}

interface RuleVersionHistoryResponse {
  strategyRuleVersionId: string;
  ruleVersion: string;
  rule: Record<string, unknown>;
  status: string;
  createdAt: string;
}

interface RuleVersionPageResponse { items: RuleVersionHistoryResponse[]; nextCursor: string | null; }

interface MetricRow { label: string; value: string; }
interface RuleField { key: string; label: string; hint: string; value: string; editable: boolean; }

interface EvaluationDisplay extends EvaluationResponse {
  stateLabel: string;
  inputLabel: string;
  explanation: string;
  metricRows: MetricRow[];
  missingFields: string[];
}

interface StrategyDisplay extends StrategyCardResponse {
  icon: string;
  subtitle: string;
  stateLabel: string;
  stateClass: string;
  inputLabel: string;
}

interface StrategyDetail {
  strategyKey: StrategyKey;
  displayName: string;
  currency: Currency;
  activeRuleVersionId: string | null;
  status: StrategyStatus;
  message: string;
  inputAt: string | null;
  inputLabel: string;
  availableActions: string[];
  evaluationMessage: string;
  metricRows: MetricRow[];
  missingFields: string[];
  ruleSummary: string;
  ruleHistory: RuleVersionHistoryResponse[];
  evaluationHistory: EvaluationDisplay[];
}

interface StrategyPageData {
  loading: boolean;
  submitting: boolean;
  errorMessage: string;
  cards: StrategyDisplay[];
  selectedKey: StrategyKey;
  detail: StrategyDetail | null;
  referenceNavCent: string;
  referenceNavAsOf: string;
  referenceNavValidUntil: string;
  ruleDraftVersion: string;
  ruleDraftFields: RuleField[];
}

interface StrategyPageMethods {
  loadStrategies(): Promise<void>;
  selectStrategy(event: WechatMiniprogram.TouchEvent): Promise<void>;
  selectStrategyByKey(key: StrategyKey): Promise<void>;
  createInitialRule(): Promise<void>;
  onRuleDraftVersionInput(event: WechatMiniprogram.Input): void;
  onRuleFieldInput(event: WechatMiniprogram.Input): void;
  saveRuleVersion(): Promise<void>;
  onReferenceNavInput(event: WechatMiniprogram.Input): void;
  onReferenceNavAsOfChange(event: WechatMiniprogram.PickerChange): void;
  onReferenceNavValidUntilChange(event: WechatMiniprogram.PickerChange): void;
  recordReferenceNav(): Promise<void>;
  evaluateSelected(): Promise<void>;
  openLedger(): void;
}

const strategyPresentation: Record<StrategyKey, Pick<StrategyDisplay, "icon" | "subtitle">> = {
  HIGH_DIVIDEND: {icon: "现金流", subtitle: "现金流覆盖、现金垫与分散约束"},
  QQQ_GROWTH: {icon: "成长", subtitle: "5% / 10% / 12% 区间与 QLD 上限"},
  IC_IM: {icon: "期货", subtitle: "保证金、PB 分位、压力与移仓事实"},
  DEEP_PUT: {icon: "保险", subtitle: "年度预算、到期梯度与归零核销"}
};

const initialRules: Record<StrategyKey, {ruleVersion: string; rule: Record<string, string>}> = {
  HIGH_DIVIDEND: {
    ruleVersion: "legacy-high-dividend-v1",
    rule: {
      annual_expense_cent: "12000000",
      annual_expense_currency: "CNY",
      minimum_dividend_coverage_percent: "100",
      cash_buffer_months: "6"
    }
  },
  QQQ_GROWTH: {
    ruleVersion: "legacy-qqq-growth-v1",
    rule: {
      starter_percent: "5",
      target_percent: "10",
      upper_percent: "12",
      qld_max_share_percent: "35",
      moving_average_days: "120"
    }
  },
  IC_IM: {
    ruleVersion: "legacy-ic-im-v1",
    rule: {
      minimum_pool_cent: "100000000",
      minimum_pool_currency: "CNY",
      pb_entry_percentile: "30",
      stress_drop_percent: "20",
      margin_warning_percent: "60",
      roll_window_days: "10"
    }
  },
  DEEP_PUT: {
    ruleVersion: "legacy-deep-put-v1",
    rule: {
      budget_min_percent: "0.5",
      budget_max_percent: "2",
      expiry_warning_days: "30"
    }
  }
};

Page<StrategyPageData, StrategyPageMethods>({
  data: {
    loading: true,
    submitting: false,
    errorMessage: "",
    cards: [],
    selectedKey: "HIGH_DIVIDEND",
    detail: null,
    referenceNavCent: "",
    referenceNavAsOf: today(),
    referenceNavValidUntil: todayPlusDays(1),
    ruleDraftVersion: "",
    ruleDraftFields: []
  },

  onShow() {
    this.getTabBar()?.setData({selected: 2});
    void this.loadStrategies();
  },

  async loadStrategies() {
    this.setData({loading: true, errorMessage: ""});
    try {
      const response = await request<StrategyListResponse>("/api/v1/strategies", "GET");
      const cards = response.items.map(displayCard);
      const selected = cards.find((item) => item.strategyKey === this.data.selectedKey) || cards[0];
      this.setData({loading: false, cards, selectedKey: selected ? selected.strategyKey : "HIGH_DIVIDEND"});
      if (selected) {
        await this.selectStrategyByKey(selected.strategyKey);
      }
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({loading: false, cards: [], detail: null,
        errorMessage: problem.message || "策略工作区暂时无法读取，请检查登录和本地服务。"});
    }
  },

  async selectStrategy(event) {
    const key = event.currentTarget.dataset.key as StrategyKey;
    await this.selectStrategyByKey(key);
  },

  async selectStrategyByKey(key: StrategyKey) {
    this.setData({selectedKey: key, errorMessage: ""});
    try {
      const [workspace, rules] = await Promise.all([
        request<StrategyWorkspaceResponse>(`/api/v1/strategies/${key}/workspace`, "GET"),
        request<RuleVersionPageResponse>(`/api/v1/strategies/${key}/rule-versions?limit=5`, "GET")
      ]);
      let history: EvaluationResponse[] = [];
      try {
        const page = await request<EvaluationPageResponse>(`/api/v1/strategies/${key}/evaluations?limit=5`, "GET");
        history = page.items;
      } catch (_) {
        // The workspace remains usable even when an older service has no history endpoint yet.
      }
      const latestEvaluation = workspace.latestEvaluation || history[0] || null;
      const evaluationMessage = explanation(latestEvaluation) || workspace.strategy.message;
      const activeRule = workspace.activeRule || rules.items.find(
        (rule) => rule.strategyRuleVersionId === workspace.strategy.activeRuleVersionId);
      const rule = activeRule ? activeRule.rule : initialRules[key].rule;
      this.setData({detail: {
        ...workspace.strategy,
        availableActions: workspace.availableActions,
        inputLabel: workspace.strategy.inputAt ? formatInputAt(workspace.strategy.inputAt) : "尚无评估输入",
        evaluationMessage,
        metricRows: latestEvaluation ? metricRows(key, latestEvaluation.result) : [],
        missingFields: latestEvaluation ? missingFields(latestEvaluation.result) : [],
        ruleSummary: activeRule ? summarize(activeRule.rule) : "尚无已启用规则内容",
        ruleHistory: rules.items,
        evaluationHistory: history.map((item) => ({...item, stateLabel: stateLabel(item.status),
          inputLabel: formatInputAt(item.asOfAt), explanation: explanation(item) || stateLabel(item.status),
          metricRows: metricRows(key, item.result), missingFields: missingFields(item.result)}))
      }, ruleDraftVersion: `${key.toLowerCase().replace("_", "-")}-${today()}`,
        ruleDraftFields: ruleFieldsFor(key, rule)});
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({detail: null, errorMessage: problem.message || "策略详情暂时无法读取。"});
    }
  },

  async createInitialRule() {
    const key = this.data.selectedKey;
    const initial = initialRules[key];
    this.setData({submitting: true, errorMessage: ""});
    try {
      await request(`/api/v1/strategies/${key}/rule-versions`, "POST", initial);
      wx.showToast({title: "初始规则已启用", icon: "success"});
      await this.loadStrategies();
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({errorMessage: problem.message || "规则没有写入，请确认当前规则版本。"});
    } finally {
      this.setData({submitting: false});
    }
  },

  onRuleDraftVersionInput(event) {
    this.setData({ruleDraftVersion: event.detail.value});
  },

  onRuleFieldInput(event) {
    const key = event.currentTarget.dataset.key as string;
    if (!key) return;
    this.setData({ruleDraftFields: this.data.ruleDraftFields.map((field) => field.key === key
      ? {...field, value: event.detail.value} : field)});
  },

  async saveRuleVersion() {
    const detail = this.data.detail;
    const ruleVersion = this.data.ruleDraftVersion.trim();
    if (!detail) return;
    if (!ruleVersion || ruleVersion.length > 64) {
      this.setData({errorMessage: "规则版本号不能为空且不能超过 64 个字符"});
      return;
    }
    const rule: Record<string, string> = {};
    for (const field of this.data.ruleDraftFields) {
      const value = field.value.trim();
      if (!validRuleValue(field.key, value)) {
        this.setData({errorMessage: `规则“${field.label}”格式无效：${field.hint}`});
        return;
      }
      rule[field.key] = value;
    }
    this.setData({submitting: true, errorMessage: ""});
    try {
      await request(`/api/v1/strategies/${detail.strategyKey}/rule-versions`, "POST", {
        ruleVersion,
        expectedActiveRuleVersionId: detail.activeRuleVersionId || undefined,
        rule
      });
      wx.showToast({title: "新规则版本已启用", icon: "success"});
      await this.loadStrategies();
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({errorMessage: problem.message || "规则版本未写入；请先刷新策略状态。"});
    } finally {
      this.setData({submitting: false});
    }
  },

  onReferenceNavInput(event) {
    this.setData({referenceNavCent: event.detail.value});
  },

  onReferenceNavAsOfChange(event) {
    this.setData({referenceNavAsOf: event.detail.value as string});
  },

  onReferenceNavValidUntilChange(event) {
    this.setData({referenceNavValidUntil: event.detail.value as string});
  },

  async recordReferenceNav() {
    const key = this.data.selectedKey;
    const referenceNavCent = this.data.referenceNavCent.trim();
    if (key !== "QQQ_GROWTH" && key !== "DEEP_PUT") {
      this.setData({errorMessage: "只有 USD 策略需要策略参考净值。"});
      return;
    }
    if (!/^[1-9][0-9]*$/.test(referenceNavCent)) {
      this.setData({errorMessage: "参考净值必须按 USD 最小单位填写正整数，例如 USD 6.66 填 666。"});
      return;
    }
    if (this.data.referenceNavValidUntil < this.data.referenceNavAsOf) {
      this.setData({errorMessage: "有效截止日不能早于参考净值日期。"});
      return;
    }
    this.setData({submitting: true, errorMessage: ""});
    try {
      await request(`/api/v1/strategies/${key}/reference-nav`, "POST", {
        referenceNavCent,
        currency: "USD",
        asOfAt: `${this.data.referenceNavAsOf}T00:00:00.000Z`,
        validUntil: `${this.data.referenceNavValidUntil}T23:59:59.999Z`
      });
      wx.showToast({title: "参考净值已记录", icon: "success"});
      await this.selectStrategyByKey(key);
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({errorMessage: problem.message || "参考净值没有写入。"});
    } finally {
      this.setData({submitting: false});
    }
  },

  async evaluateSelected() {
    const key = this.data.selectedKey;
    this.setData({submitting: true, errorMessage: ""});
    try {
      const response = await request<EvaluationResponse>(`/api/v1/strategies/${key}/evaluations`, "POST", {
        asOfDate: today()
      });
      const evaluationMessage = response.result.signals && response.result.signals[0] && response.result.signals[0].explanation
        ? response.result.signals[0].explanation : stateLabel(response.status);
      if (this.data.detail) {
        this.setData({"detail.status": response.status, "detail.inputAt": response.asOfAt,
          "detail.inputLabel": formatInputAt(response.asOfAt), "detail.evaluationMessage": evaluationMessage,
          "detail.metricRows": metricRows(key, response.result), "detail.missingFields": missingFields(response.result)});
      }
      wx.showToast({title: stateLabel(response.status), icon: "none"});
      await this.loadStrategies();
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({errorMessage: problem.message || "评估没有完成，请检查本地数据快照。"});
    } finally {
      this.setData({submitting: false});
    }
  },

  openLedger() {
    wx.setStorageSync("investment.strategyDraftKey", this.data.selectedKey);
    wx.switchTab({url: "/pages/ledger/ledger"});
  }
});

function displayCard(item: StrategyCardResponse): StrategyDisplay {
  return {
    ...item,
    ...strategyPresentation[item.strategyKey],
    stateLabel: stateLabel(item.status),
    stateClass: stateClass(item.status),
    inputLabel: item.inputAt ? formatInputAt(item.inputAt) : "尚无评估输入"
  };
}

function stateLabel(status: StrategyStatus): string {
  const labels: Record<StrategyStatus, string> = {
    IN_RANGE: "区间内",
    WATCH: "观察",
    BLOCKED: "等待数据",
    DATA_STALE: "数据过期",
    CROSS_CURRENCY_UNVALUED: "币种未估值"
  };
  return labels[status];
}

function stateClass(status: StrategyStatus): string {
  if (status === "IN_RANGE") return "state-good";
  if (status === "WATCH") return "state-watch";
  return "state-blocked";
}

function formatInputAt(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "输入时间待确认" : `${date.getMonth() + 1}/${date.getDate()} ${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
}

function explanation(value: EvaluationResponse | null): string {
  return value && value.result.signals && value.result.signals[0] && value.result.signals[0].explanation
    ? value.result.signals[0].explanation : "";
}

function metricRows(strategyKey: StrategyKey, result: StrategyEvaluationResult): MetricRow[] {
  const currency = result.currency || (strategyKey === "HIGH_DIVIDEND" || strategyKey === "IC_IM" ? "CNY" : "USD");
  const labels = metricLabels(strategyKey);
  return Object.entries(labels).flatMap(([key, label]) => {
    const value = result[key];
    if (value === undefined || value === null || value === "") {
      return [];
    }
    return [{label, value: displayMetric(key, String(value), currency)}];
  });
}

function metricLabels(strategyKey: StrategyKey): Record<string, string> {
  if (strategyKey === "HIGH_DIVIDEND") {
    return {
      annual_expense_cent: "年度支出",
      trailing_income_cent: "近 12 个月分红/利息",
      income_coverage_percent: "收入覆盖率",
      cash_buffer_cent: "策略现金垫",
      required_cash_buffer_months: "要求现金垫"
    };
  }
  if (strategyKey === "QQQ_GROWTH") {
    return {
      reference_nav_cent: "USD 参考净值",
      qqq_market_value_cent: "QQQ 市值",
      qld_market_value_cent: "QLD 市值",
      allocation_percent: "QQQ/QLD 配置",
      qld_share_percent: "QLD 占比",
      moving_average_days: "均线输入周期"
    };
  }
  if (strategyKey === "IC_IM") {
    return {
      pool_cent: "策略资金池",
      available_margin_cent: "可用保证金",
      locked_margin_cent: "锁定保证金",
      margin_risk_percent: "保证金风险度",
      ic_pb_percentile: "IC PB 分位",
      im_pb_percentile: "IM PB 分位",
      ic_annualized_basis: "IC 年化贴水源值",
      im_annualized_basis: "IM 年化贴水源值",
      nearest_maturity_days: "最近到期天数"
    };
  }
  return {
    reference_nav_cent: "USD 参考净值",
    trailing_premium_cent: "近 12 个月保费",
    premium_budget_percent: "保费预算占比",
    open_put_quantity: "在持 Put 数量",
    nearest_expiry_date: "最近到期日"
  };
}

function displayMetric(key: string, value: string, currency: Currency): string {
  if (key.endsWith("_cent")) {
    return formatMinorUnit(value, currency);
  }
  if (key.endsWith("_percent")) {
    return `${value}%`;
  }
  if (key.endsWith("_months")) {
    return `${value} 个月`;
  }
  if (key.endsWith("_days")) {
    return `${value} 天`;
  }
  return value;
}

function missingFields(result: StrategyEvaluationResult): string[] {
  return (result.missingOrStaleFields || []).map((value) => missingFieldLabel(value));
}

function missingFieldLabel(value: string): string {
  const labels: Record<string, string> = {
    active_rule: "活动规则",
    active_rule_body: "活动规则内容",
    strategy_ledger_facts: "策略归属账务事实",
    instrument_configuration: "标的/合约配置",
    reference_nav: "USD 参考净值",
    market_snapshot: "已确认市场快照"
  };
  return labels[value] || value;
}

function ruleFieldsFor(strategyKey: StrategyKey, source: Record<string, unknown>): RuleField[] {
  const definitions: Record<StrategyKey, Array<Pick<RuleField, "key" | "label" | "hint" | "editable">>> = {
    HIGH_DIVIDEND: [
      {key: "annual_expense_cent", label: "年度支出（CNY 最小单位）", hint: "正整数，例如 CNY 12,000,000 填 12000000", editable: true},
      {key: "annual_expense_currency", label: "支出原币种", hint: "固定 CNY", editable: false},
      {key: "minimum_dividend_coverage_percent", label: "最低收入覆盖率", hint: "正数，例如 100", editable: true},
      {key: "cash_buffer_months", label: "现金垫月数", hint: "正整数，例如 6", editable: true}
    ],
    QQQ_GROWTH: [
      {key: "starter_percent", label: "起步配置比例", hint: "正数，例如 5", editable: true},
      {key: "target_percent", label: "目标配置比例", hint: "正数，例如 10", editable: true},
      {key: "upper_percent", label: "上限配置比例", hint: "正数，例如 12", editable: true},
      {key: "qld_max_share_percent", label: "QLD 最大占比", hint: "正数，例如 35", editable: true},
      {key: "moving_average_days", label: "均线周期（天）", hint: "正整数，例如 120", editable: true}
    ],
    IC_IM: [
      {key: "minimum_pool_cent", label: "最低资金池（CNY 最小单位）", hint: "正整数，例如 CNY 1,000,000 填 100000000", editable: true},
      {key: "minimum_pool_currency", label: "资金池原币种", hint: "固定 CNY", editable: false},
      {key: "pb_entry_percentile", label: "PB 观察阈值", hint: "正数，例如 30", editable: true},
      {key: "stress_drop_percent", label: "压力跌幅", hint: "正数，例如 20", editable: true},
      {key: "margin_warning_percent", label: "保证金风险阈值", hint: "正数，例如 60", editable: true},
      {key: "roll_window_days", label: "移仓窗口（天）", hint: "正整数，例如 10", editable: true}
    ],
    DEEP_PUT: [
      {key: "budget_min_percent", label: "年度保费预算下限", hint: "正数，例如 0.5", editable: true},
      {key: "budget_max_percent", label: "年度保费预算上限", hint: "正数，例如 2", editable: true},
      {key: "expiry_warning_days", label: "到期预警天数", hint: "正整数，例如 30", editable: true}
    ]
  };
  return definitions[strategyKey].map((definition) => ({...definition,
    value: String(source[definition.key] ?? initialRules[strategyKey].rule[definition.key] ?? "")}));
}

function validRuleValue(key: string, value: string): boolean {
  if (key.endsWith("_currency")) return value === "CNY";
  if (key.endsWith("_cent") || key.endsWith("_days") || key.endsWith("_months")) {
    return /^[1-9][0-9]*$/.test(value);
  }
  if (key.endsWith("_percent")) {
    return /^[1-9][0-9]*(?:\.[0-9]{1,4})?$/.test(value);
  }
  return false;
}

function summarize(rule: Record<string, unknown>): string {
  return Object.keys(rule).sort().map((key) => `${key}: ${String(rule[key])}`).join("\n");
}

function formatMinorUnit(amountCent: string, currency: Currency): string {
  const normalized = amountCent.replace(/^0+(?=\d)/, "") || "0";
  const padded = normalized.length < 2 ? `0${normalized}` : normalized;
  return `${currency} ${padded.slice(0, -2) || "0"}.${padded.slice(-2)}`;
}

function today(): string {
  const value = new Date();
  return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, "0")}-${String(value.getDate()).padStart(2, "0")}`;
}

function todayPlusDays(days: number): string {
  const value = new Date();
  value.setDate(value.getDate() + days);
  return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, "0")}-${String(value.getDate()).padStart(2, "0")}`;
}

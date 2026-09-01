import {request} from "../../utils/api";
import {newIdempotencyKey} from "../../utils/idempotency";
import {runtimeConfig} from "../../config";

type Currency = "CNY" | "USD";
type StrategyKey = "HIGH_DIVIDEND" | "QQQ_GROWTH" | "IC_IM" | "DEEP_PUT";
type TradeType = "TRADE_BUY" | "TRADE_SELL";
type CashFlowType = "EXTERNAL_FUNDING" | "EXTERNAL_WITHDRAWAL" | "INTERNAL_TRANSFER" | "FEE" | "DIVIDEND" | "INTEREST" | "FUTURES_MARGIN";
type CorporateActionType = "STOCK_SPLIT" | "REVERSE_SPLIT" | "STOCK_DIVIDEND";
type SubmissionState = "IDLE" | "PREVIEWED" | "SUBMITTING" | "RETRYABLE_ERROR" | "SUCCESS";

interface CashAccount {
  accountId: string;
  displayName: string;
  accountKind: "ASSET_CASH";
  currency: Currency;
  status: "ACTIVE" | "DISABLED";
  version: string;
}

interface CashAccountListResponse {
  items: CashAccount[];
}

interface PreviewPosting {
  accountCode: string;
  displayName: string;
  postingSide: "DEBIT" | "CREDIT";
  amountCent: string;
  currency: Currency;
}

interface TransactionPreviewResponse {
  draftHash: string;
  currency: Currency | null;
  postings: PreviewPosting[];
  tradeDetails: Array<{instrumentId: string; positionEffect: "OPEN" | "CLOSE"; quantity: string}>;
  accountProvisioning: string[];
  validationWarnings: string[];
  proposedOperationGroupKey?: string | null;
}

interface PreviewPostingView extends PreviewPosting {
  amountDisplay: string;
  sideLabel: string;
}

interface SpotTransactionCreateRequest {
  transactionType: TradeType;
  occurredOn: string;
  cashAccountId: string;
  instrumentId: string;
  quantity: string;
  unitPriceCent: string;
  feeCent?: string;
  note?: string;
  strategyKey?: StrategyKey;
}

interface LedgerTransactionResponse {
  transactionId: string;
}

interface TransactionSummary {
  transactionId: string;
  transactionType: string;
  occurredOn: string;
  currency: Currency | null;
  ledgerVersion: string;
}

interface TransactionListResponse {
  items: TransactionSummary[];
  nextCursor: string | null;
}

interface TransactionSummaryView extends TransactionSummary {
  typeLabel: string;
}

interface TransactionDetailResponse {
  transactionId: string;
  transactionType: string;
  occurredOn: string;
  strategyKey: StrategyKey | null;
  sourceType: string;
  importExportFileId: string | null;
  correctionRootTransactionId: string;
  reversalOfTransactionId: string | null;
  revisionNo: number;
  ledgerVersion: string;
  note: string | null;
  correctable: boolean;
  postings: Array<{
    postingId: string;
    accountId: string;
    postingNo: number;
    postingSide: "DEBIT" | "CREDIT";
    amountCent: string;
    currency: Currency;
  }>;
  tradeDetails: Array<{
    tradeDetailId: string;
    detailNo: number;
    instrumentId: string;
    positionEffect: "OPEN" | "CLOSE";
    quantity: string;
    unitPriceCent: string | null;
    pricePoints: string | null;
    contractMultiplierCent: string | null;
    deliveryDate: string | null;
    feeCent: string;
    optionContractMultiplier: string | null;
  }>;
  corporateAction: {
    corporateActionId: string;
    instrumentId: string;
    actionType: string;
    effectiveOn: string;
    ratioNumerator: string;
    ratioDenominator: string;
  } | null;
}

interface TransactionDetailView extends TransactionDetailResponse {
  typeLabel: string;
  postingViews: Array<TransactionDetailResponse["postings"][number] & {amountDisplay: string; sideLabel: string}>;
  tradeDetailViews: Array<TransactionDetailResponse["tradeDetails"][number] & {feeDisplay: string}>;
  correctionStatusLabel: string;
  replaceable: boolean;
}

interface LedgerSnapshotResponse {
  ledgerSnapshotId: string;
  asOfDate: string;
  sourceLedgerVersion: string;
  contentSha256Hex: string;
  createdAt: string;
}

interface LedgerSnapshotListResponse {
  items: LedgerSnapshotResponse[];
}

interface LedgerSnapshotRestoreResponse {
  ledgerSnapshotId: string;
  restoredAccountCount: number;
  restoredTransactionCount: number;
  targetLedgerVersion: string;
}

interface CorrectionResponse {
  reversalTransactionIds: string[];
  replacementTransactionIds: string[];
  correctionRootTransactionIds: string[];
  ledgerVersion: string;
}

interface CashFlowCreateRequest {
  transactionType: CashFlowType;
  occurredOn: string;
  cashAccountId: string;
  destinationAccountId?: string;
  amountCent: string;
  instrumentId?: string;
  taxWithheldCent?: string;
  entitlementDate?: string;
  perShareAmountCent?: string;
  marginDirection?: "IN" | "OUT";
  note?: string;
  strategyKey?: StrategyKey;
}

interface CorporateActionCreateRequest {
  transactionType: "CORPORATE_ACTION";
  occurredOn: string;
  note?: string;
  corporateAction: {
    actionType: CorporateActionType;
    instrumentId: string;
    ratioNumerator: string;
    ratioDenominator: string;
  };
  strategyKey?: StrategyKey;
}

interface FuturesTransactionCreateRequest {
  transactionType: "FUTURES_OPEN" | "FUTURES_CLOSE" | "FUTURES_DAILY_SETTLEMENT";
  occurredOn: string;
  cashAccountId: string;
  instrumentId: string;
  quantity?: string;
  pricePoints?: string;
  settlementPricePoints?: string;
  initialMarginCent?: string;
  feeCent?: string;
  note?: string;
  strategyKey?: StrategyKey;
}

interface FuturesRollLegRequest {
  cashAccountId: string;
  instrumentId: string;
  quantity: string;
  pricePoints: string;
  initialMarginCent?: string;
  feeCent?: string;
}

interface FuturesRollCreateRequest {
  transactionType: "FUTURES_ROLL";
  occurredOn: string;
  futuresRoll: {
    closeLeg: FuturesRollLegRequest;
    openLeg: FuturesRollLegRequest;
  };
  note?: string;
  strategyKey: "IC_IM";
}

interface FuturesRollResponse {
  operationGroupKey: string;
  transactions: LedgerTransactionResponse[];
}

interface OptionTransactionCreateRequest {
  transactionType: "OPTION_OPEN" | "OPTION_CLOSE" | "OPTION_EXPIRE";
  occurredOn: string;
  cashAccountId: string;
  instrumentId: string;
  quantity: string;
  unitPriceCent?: string;
  feeCent?: string;
  expiryOutcome?: "WORTHLESS";
  note?: string;
  strategyKey?: StrategyKey;
}

interface LedgerPageData {
  loading: boolean;
  loadingTransactions: boolean;
  loadingTransactionDetail: boolean;
  correctingTransaction: boolean;
  correctionTargetTransactionId: string;
  correctionTargetType: string;
  creating: boolean;
  previewing: boolean;
  submitting: boolean;
  exportingFormat: "" | "JSON" | "CSV";
  loadingSnapshots: boolean;
  creatingSnapshot: boolean;
  restoringSnapshotId: string;
  snapshots: LedgerSnapshotResponse[];
  accounts: CashAccount[];
  transactions: TransactionSummaryView[];
  transactionNextCursor: string | null;
  transactionSearch: string;
  transactionAccountFilterIndex: number;
  transactionAccountFilterIds: string[];
  transactionAccountFilterNames: string[];
  transactionTypeFilterIndex: number;
  transactionTypeFilterValues: string[];
  transactionTypeFilterLabels: string[];
  transactionFrom: string;
  transactionTo: string;
  selectedTransaction: TransactionDetailView | null;
  tradeAccounts: CashAccount[];
  tradeAccountNames: string[];
  displayName: string;
  currencyIndex: number;
  currencies: Currency[];
  tradeAccountIndex: number;
  selectedTradeCurrency: Currency | "";
  tradeTypeIndex: number;
  tradeTypes: TradeType[];
  cashFlowTypeIndex: number;
  cashFlowTypes: CashFlowType[];
  cashFlowLabels: string[];
  cashFlowSourceIndex: number;
  cashFlowDestinationIndex: number;
  cashFlowOccurredOn: string;
  cashFlowAmountCent: string;
  cashFlowInstrumentId: string;
  cashFlowTaxWithheldCent: string;
  cashFlowEntitlementDate: string;
  cashFlowPerShareAmountCent: string;
  marginDirectionIndex: number;
  marginDirections: Array<"IN" | "OUT">;
  marginDirectionLabels: string[];
  cashFlowNote: string;
  cashFlowPreviewPostings: PreviewPostingView[];
  cashFlowSubmissionState: SubmissionState;
  cashFlowPendingIdempotencyKey: string;
  cashFlowLastTransactionId: string;
  cashFlowPreviewing: boolean;
  cashFlowSubmitting: boolean;
  corporateActionTypeIndex: number;
  corporateActionTypes: CorporateActionType[];
  corporateActionLabels: string[];
  corporateActionOccurredOn: string;
  corporateActionInstrumentId: string;
  corporateActionRatioNumerator: string;
  corporateActionRatioDenominator: string;
  corporateActionNote: string;
  corporateActionSubmissionState: SubmissionState;
  corporateActionPendingIdempotencyKey: string;
  corporateActionLastTransactionId: string;
  corporateActionPreviewing: boolean;
  corporateActionSubmitting: boolean;
  futuresAccountIndex: number;
  futuresTransactionTypeIndex: number;
  futuresTransactionTypes: Array<"FUTURES_OPEN" | "FUTURES_CLOSE" | "FUTURES_DAILY_SETTLEMENT">;
  futuresTransactionLabels: string[];
  futuresOccurredOn: string;
  futuresInstrumentId: string;
  futuresQuantity: string;
  futuresPricePoints: string;
  futuresInitialMarginCent: string;
  futuresFeeCent: string;
  futuresNote: string;
  futuresPreviewPostings: PreviewPostingView[];
  futuresAccountProvisioning: string[];
  futuresValidationWarnings: string[];
  futuresSubmissionState: SubmissionState;
  futuresPendingIdempotencyKey: string;
  futuresLastTransactionId: string;
  futuresPreviewing: boolean;
  futuresSubmitting: boolean;
  futuresRollCloseAccountIndex: number;
  futuresRollOpenAccountIndex: number;
  futuresRollOccurredOn: string;
  futuresRollCloseInstrumentId: string;
  futuresRollCloseQuantity: string;
  futuresRollClosePricePoints: string;
  futuresRollCloseFeeCent: string;
  futuresRollOpenInstrumentId: string;
  futuresRollOpenQuantity: string;
  futuresRollOpenPricePoints: string;
  futuresRollOpenInitialMarginCent: string;
  futuresRollOpenFeeCent: string;
  futuresRollNote: string;
  futuresRollPreviewPostings: PreviewPostingView[];
  futuresRollAccountProvisioning: string[];
  futuresRollValidationWarnings: string[];
  futuresRollSubmissionState: SubmissionState;
  futuresRollPendingIdempotencyKey: string;
  futuresRollProposedOperationGroupKey: string;
  futuresRollLastOperationGroupKey: string;
  futuresRollPreviewing: boolean;
  futuresRollSubmitting: boolean;
  optionAccountIndex: number;
  optionTransactionTypeIndex: number;
  optionTransactionTypes: Array<"OPTION_OPEN" | "OPTION_CLOSE" | "OPTION_EXPIRE">;
  optionTransactionLabels: string[];
  optionOccurredOn: string;
  optionInstrumentId: string;
  optionQuantity: string;
  optionUnitPriceCent: string;
  optionFeeCent: string;
  optionNote: string;
  optionPreviewPostings: PreviewPostingView[];
  optionAccountProvisioning: string[];
  optionValidationWarnings: string[];
  optionSubmissionState: SubmissionState;
  optionPendingIdempotencyKey: string;
  optionLastTransactionId: string;
  optionPreviewing: boolean;
  optionSubmitting: boolean;
  occurredOn: string;
  instrumentId: string;
  quantity: string;
  unitPriceCent: string;
  feeCent: string;
  note: string;
  previewPostings: PreviewPostingView[];
  accountProvisioning: string[];
  validationWarnings: string[];
  submissionState: SubmissionState;
  pendingIdempotencyKey: string;
  lastTransactionId: string;
  errorMessage: string;
  strategyKey: StrategyKey | "";
}

interface LedgerPageMethods {
  loadAccounts(): Promise<void>;
  loadTransactions(append?: boolean): Promise<void>;
  loadMoreTransactions(): Promise<void>;
  onTransactionSearchInput(event: WechatMiniprogram.Input): void;
  onTransactionAccountFilterChange(event: WechatMiniprogram.PickerChange): void;
  onTransactionTypeFilterChange(event: WechatMiniprogram.PickerChange): void;
  onTransactionFromChange(event: WechatMiniprogram.PickerChange): void;
  onTransactionToChange(event: WechatMiniprogram.PickerChange): void;
  applyTransactionFilters(): Promise<void>;
  clearTransactionFilters(): Promise<void>;
  downloadLedgerExport(event: {currentTarget: {dataset: {format?: string}}}): void;
  loadSnapshots(): Promise<void>;
  createSnapshot(): Promise<void>;
  downloadSnapshot(event: {currentTarget: {dataset: {snapshotId?: string}}}): void;
  restoreSnapshot(event: {currentTarget: {dataset: {snapshotId?: string}}}): void;
  loadTransactionDetail(event: {currentTarget: {dataset: {transactionId?: string}}}): Promise<void>;
  closeTransactionDetail(): void;
  correctSelectedTransaction(): void;
  submitReversal(): Promise<void>;
  beginReplacement(): void;
  cancelReplacement(): void;
  submitReplacementAware(payload: object, key: string): Promise<LedgerTransactionResponse>;
  onDisplayNameInput(event: WechatMiniprogram.Input): void;
  onCurrencyChange(event: WechatMiniprogram.PickerChange): void;
  createCashAccount(): Promise<void>;
  onTradeAccountChange(event: WechatMiniprogram.PickerChange): void;
  onTradeTypeChange(event: WechatMiniprogram.PickerChange): void;
  onCashFlowTypeChange(event: WechatMiniprogram.PickerChange): void;
  onCashFlowSourceChange(event: WechatMiniprogram.PickerChange): void;
  onCashFlowDestinationChange(event: WechatMiniprogram.PickerChange): void;
  onCashFlowOccurredOnChange(event: WechatMiniprogram.PickerChange): void;
  onCashFlowAmountInput(event: WechatMiniprogram.Input): void;
  onCashFlowInstrumentIdInput(event: WechatMiniprogram.Input): void;
  onCashFlowTaxInput(event: WechatMiniprogram.Input): void;
  onCashFlowEntitlementDateChange(event: WechatMiniprogram.PickerChange): void;
  onCashFlowPerShareAmountInput(event: WechatMiniprogram.Input): void;
  onMarginDirectionChange(event: WechatMiniprogram.PickerChange): void;
  onCashFlowNoteInput(event: WechatMiniprogram.Input): void;
  previewCashFlow(): Promise<void>;
  confirmCashFlow(): void;
  retryCashFlow(): Promise<void>;
  onCorporateActionTypeChange(event: WechatMiniprogram.PickerChange): void;
  onCorporateActionOccurredOnChange(event: WechatMiniprogram.PickerChange): void;
  onCorporateActionInstrumentIdInput(event: WechatMiniprogram.Input): void;
  onCorporateActionRatioNumeratorInput(event: WechatMiniprogram.Input): void;
  onCorporateActionRatioDenominatorInput(event: WechatMiniprogram.Input): void;
  onCorporateActionNoteInput(event: WechatMiniprogram.Input): void;
  previewCorporateAction(): Promise<void>;
  confirmCorporateAction(): void;
  retryCorporateAction(): Promise<void>;
  onFuturesAccountChange(event: WechatMiniprogram.PickerChange): void;
  onFuturesTransactionTypeChange(event: WechatMiniprogram.PickerChange): void;
  onFuturesOccurredOnChange(event: WechatMiniprogram.PickerChange): void;
  onFuturesInstrumentIdInput(event: WechatMiniprogram.Input): void;
  onFuturesQuantityInput(event: WechatMiniprogram.Input): void;
  onFuturesPricePointsInput(event: WechatMiniprogram.Input): void;
  onFuturesInitialMarginInput(event: WechatMiniprogram.Input): void;
  onFuturesFeeInput(event: WechatMiniprogram.Input): void;
  onFuturesNoteInput(event: WechatMiniprogram.Input): void;
  previewFuturesOpen(): Promise<void>;
  confirmFuturesOpen(): void;
  retryFuturesOpen(): Promise<void>;
  onFuturesRollCloseAccountChange(event: WechatMiniprogram.PickerChange): void;
  onFuturesRollOpenAccountChange(event: WechatMiniprogram.PickerChange): void;
  onFuturesRollOccurredOnChange(event: WechatMiniprogram.PickerChange): void;
  onFuturesRollCloseInstrumentIdInput(event: WechatMiniprogram.Input): void;
  onFuturesRollCloseQuantityInput(event: WechatMiniprogram.Input): void;
  onFuturesRollClosePricePointsInput(event: WechatMiniprogram.Input): void;
  onFuturesRollCloseFeeInput(event: WechatMiniprogram.Input): void;
  onFuturesRollOpenInstrumentIdInput(event: WechatMiniprogram.Input): void;
  onFuturesRollOpenQuantityInput(event: WechatMiniprogram.Input): void;
  onFuturesRollOpenPricePointsInput(event: WechatMiniprogram.Input): void;
  onFuturesRollOpenInitialMarginInput(event: WechatMiniprogram.Input): void;
  onFuturesRollOpenFeeInput(event: WechatMiniprogram.Input): void;
  onFuturesRollNoteInput(event: WechatMiniprogram.Input): void;
  previewFuturesRoll(): Promise<void>;
  confirmFuturesRoll(): void;
  retryFuturesRoll(): Promise<void>;
  onOptionAccountChange(event: WechatMiniprogram.PickerChange): void;
  onOptionTransactionTypeChange(event: WechatMiniprogram.PickerChange): void;
  onOptionOccurredOnChange(event: WechatMiniprogram.PickerChange): void;
  onOptionInstrumentIdInput(event: WechatMiniprogram.Input): void;
  onOptionQuantityInput(event: WechatMiniprogram.Input): void;
  onOptionUnitPriceInput(event: WechatMiniprogram.Input): void;
  onOptionFeeInput(event: WechatMiniprogram.Input): void;
  onOptionNoteInput(event: WechatMiniprogram.Input): void;
  previewOption(): Promise<void>;
  confirmOption(): void;
  retryOption(): Promise<void>;
  onOccurredOnChange(event: WechatMiniprogram.PickerChange): void;
  onInstrumentIdInput(event: WechatMiniprogram.Input): void;
  onQuantityInput(event: WechatMiniprogram.Input): void;
  onUnitPriceInput(event: WechatMiniprogram.Input): void;
  onFeeInput(event: WechatMiniprogram.Input): void;
  onNoteInput(event: WechatMiniprogram.Input): void;
  previewTrade(): Promise<void>;
  confirmTrade(): void;
  retryTrade(): Promise<void>;
  goMarket(): void;
  clearStrategyContext(): void;
  resetPreview(patch: Partial<LedgerPageData>): void;
  tradePayload(): SpotTransactionCreateRequest | null;
  submitTrade(): Promise<void>;
  resetCashFlowPreview(patch: Partial<LedgerPageData>): void;
  cashFlowPayload(): CashFlowCreateRequest | null;
  submitCashFlow(): Promise<void>;
  resetCorporateActionPreview(patch: Partial<LedgerPageData>): void;
  corporateActionPayload(): CorporateActionCreateRequest | null;
  submitCorporateAction(): Promise<void>;
  resetFuturesOpenPreview(patch: Partial<LedgerPageData>): void;
  futuresOpenPayload(): FuturesTransactionCreateRequest | null;
  submitFuturesOpen(): Promise<void>;
  resetFuturesRollPreview(patch: Partial<LedgerPageData>): void;
  futuresRollPayload(): FuturesRollCreateRequest | null;
  submitFuturesRoll(): Promise<void>;
  resetOptionPreview(patch: Partial<LedgerPageData>): void;
  optionPayload(): OptionTransactionCreateRequest | null;
  submitOption(): Promise<void>;
}

Page<LedgerPageData, LedgerPageMethods>({
  data: {
    loading: true,
    loadingTransactions: true,
    loadingTransactionDetail: false,
    correctingTransaction: false,
    correctionTargetTransactionId: "",
    correctionTargetType: "",
    creating: false,
    previewing: false,
    submitting: false,
    exportingFormat: "",
    loadingSnapshots: false,
    creatingSnapshot: false,
    restoringSnapshotId: "",
    snapshots: [],
    accounts: [],
    transactions: [],
    transactionNextCursor: null,
    transactionSearch: "",
    transactionAccountFilterIndex: 0,
    transactionAccountFilterIds: [""],
    transactionAccountFilterNames: ["全部现金账户"],
    transactionTypeFilterIndex: 0,
    transactionTypeFilterValues: ["", "EXTERNAL_FUNDING", "EXTERNAL_WITHDRAWAL", "INTERNAL_TRANSFER", "FEE",
      "DIVIDEND", "INTEREST", "TRADE_BUY", "TRADE_SELL", "CORPORATE_ACTION", "FUTURES_MARGIN",
      "FUTURES_OPEN", "FUTURES_CLOSE", "FUTURES_DAILY_SETTLEMENT", "OPTION_OPEN", "OPTION_CLOSE",
      "OPTION_EXPIRE", "REVERSAL"],
    transactionTypeFilterLabels: ["全部动作", "外部入金", "外部出金", "账户内调拨", "独立费用", "现金分红",
      "利息收入", "现货买入", "现货卖出", "公司行为", "期货保证金", "期货开仓", "期货平仓",
      "期货逐日结算", "期权买入开仓", "期权卖出平仓", "期权到期无价值核销", "冲正"],
    transactionFrom: "",
    transactionTo: "",
    selectedTransaction: null,
    tradeAccounts: [],
    tradeAccountNames: [],
    displayName: "",
    currencyIndex: 0,
    currencies: ["CNY", "USD"],
    tradeAccountIndex: 0,
    selectedTradeCurrency: "",
    tradeTypeIndex: 0,
    tradeTypes: ["TRADE_BUY", "TRADE_SELL"],
    cashFlowTypeIndex: 0,
    cashFlowTypes: ["EXTERNAL_FUNDING", "EXTERNAL_WITHDRAWAL", "INTERNAL_TRANSFER", "FEE", "DIVIDEND", "INTEREST", "FUTURES_MARGIN"],
    cashFlowLabels: ["外部入金", "外部出金", "账户内调拨", "独立费用", "现金分红", "利息收入", "期货保证金"],
    cashFlowSourceIndex: 0,
    cashFlowDestinationIndex: 0,
    cashFlowOccurredOn: today(),
    cashFlowAmountCent: "",
    cashFlowInstrumentId: "",
    cashFlowTaxWithheldCent: "0",
    cashFlowEntitlementDate: today(),
    cashFlowPerShareAmountCent: "",
    marginDirectionIndex: 0,
    marginDirections: ["IN", "OUT"],
    marginDirectionLabels: ["转入可用保证金", "从可用保证金转回现金"],
    cashFlowNote: "",
    cashFlowPreviewPostings: [],
    cashFlowSubmissionState: "IDLE",
    cashFlowPendingIdempotencyKey: "",
    cashFlowLastTransactionId: "",
    cashFlowPreviewing: false,
    cashFlowSubmitting: false,
    corporateActionTypeIndex: 0,
    corporateActionTypes: ["STOCK_SPLIT", "REVERSE_SPLIT", "STOCK_DIVIDEND"],
    corporateActionLabels: ["拆股", "并股", "送股"],
    corporateActionOccurredOn: today(),
    corporateActionInstrumentId: "",
    corporateActionRatioNumerator: "",
    corporateActionRatioDenominator: "",
    corporateActionNote: "",
    corporateActionSubmissionState: "IDLE",
    corporateActionPendingIdempotencyKey: "",
    corporateActionLastTransactionId: "",
    corporateActionPreviewing: false,
    corporateActionSubmitting: false,
    futuresAccountIndex: 0,
    futuresTransactionTypeIndex: 0,
    futuresTransactionTypes: ["FUTURES_OPEN", "FUTURES_CLOSE", "FUTURES_DAILY_SETTLEMENT"],
    futuresTransactionLabels: ["开多仓", "平多仓", "手工逐日结算"],
    futuresOccurredOn: today(),
    futuresInstrumentId: "",
    futuresQuantity: "",
    futuresPricePoints: "",
    futuresInitialMarginCent: "",
    futuresFeeCent: "0",
    futuresNote: "",
    futuresPreviewPostings: [],
    futuresAccountProvisioning: [],
    futuresValidationWarnings: [],
    futuresSubmissionState: "IDLE",
    futuresPendingIdempotencyKey: "",
    futuresLastTransactionId: "",
    futuresPreviewing: false,
    futuresSubmitting: false,
    futuresRollCloseAccountIndex: 0,
    futuresRollOpenAccountIndex: 0,
    futuresRollOccurredOn: today(),
    futuresRollCloseInstrumentId: "",
    futuresRollCloseQuantity: "",
    futuresRollClosePricePoints: "",
    futuresRollCloseFeeCent: "0",
    futuresRollOpenInstrumentId: "",
    futuresRollOpenQuantity: "",
    futuresRollOpenPricePoints: "",
    futuresRollOpenInitialMarginCent: "",
    futuresRollOpenFeeCent: "0",
    futuresRollNote: "",
    futuresRollPreviewPostings: [],
    futuresRollAccountProvisioning: [],
    futuresRollValidationWarnings: [],
    futuresRollSubmissionState: "IDLE",
    futuresRollPendingIdempotencyKey: "",
    futuresRollProposedOperationGroupKey: "",
    futuresRollLastOperationGroupKey: "",
    futuresRollPreviewing: false,
    futuresRollSubmitting: false,
    optionAccountIndex: 0,
    optionTransactionTypeIndex: 0,
    optionTransactionTypes: ["OPTION_OPEN", "OPTION_CLOSE", "OPTION_EXPIRE"],
    optionTransactionLabels: ["买入开仓", "卖出平仓", "到期无价值核销"],
    optionOccurredOn: today(),
    optionInstrumentId: "",
    optionQuantity: "",
    optionUnitPriceCent: "",
    optionFeeCent: "0",
    optionNote: "",
    optionPreviewPostings: [],
    optionAccountProvisioning: [],
    optionValidationWarnings: [],
    optionSubmissionState: "IDLE",
    optionPendingIdempotencyKey: "",
    optionLastTransactionId: "",
    optionPreviewing: false,
    optionSubmitting: false,
    occurredOn: today(),
    instrumentId: "",
    quantity: "",
    unitPriceCent: "",
    feeCent: "0",
    note: "",
    previewPostings: [],
    accountProvisioning: [],
    validationWarnings: [],
    submissionState: "IDLE",
    pendingIdempotencyKey: "",
    lastTransactionId: "",
    errorMessage: "",
    strategyKey: ""
  },

  onShow() {
    this.getTabBar()?.setData({selected: 1});
    const storedInstrumentId = wx.getStorageSync("investment.tradeInstrumentId") as string;
    if (!this.data.instrumentId && storedInstrumentId) {
      this.setData({instrumentId: storedInstrumentId});
    }
    const strategyKey = wx.getStorageSync("investment.strategyDraftKey") as StrategyKey;
    if (isStrategyKey(strategyKey)) {
      this.setData({strategyKey});
    }
    this.loadAccounts();
    this.loadTransactions();
    this.loadSnapshots();
  },

  clearStrategyContext() {
    wx.removeStorageSync("investment.strategyDraftKey");
    this.setData({strategyKey: ""});
  },

  async loadAccounts() {
    if (!wx.getStorageSync("investment.accessToken")) {
      wx.reLaunch({url: "/pages/login/login"});
      return;
    }
    this.setData({loading: true, errorMessage: ""});
    try {
      const response = await request<CashAccountListResponse>("/api/v1/ledger/accounts", "GET");
      const tradeAccounts = response.items.filter((account) => account.status === "ACTIVE");
      const tradeAccountIndex = tradeAccounts.length === 0 ? 0
        : Math.min(this.data.tradeAccountIndex, tradeAccounts.length - 1);
      const cashFlowSourceIndex = tradeAccounts.length === 0 ? 0
        : Math.min(this.data.cashFlowSourceIndex, tradeAccounts.length - 1);
      const cashFlowDestinationIndex = tradeAccounts.length === 0 ? 0
        : Math.min(this.data.cashFlowDestinationIndex, tradeAccounts.length - 1);
      const futuresAccountIndex = tradeAccounts.length === 0 ? 0
        : Math.min(this.data.futuresAccountIndex, tradeAccounts.length - 1);
      const futuresRollCloseAccountIndex = tradeAccounts.length === 0 ? 0
        : Math.min(this.data.futuresRollCloseAccountIndex, tradeAccounts.length - 1);
      const futuresRollOpenAccountIndex = tradeAccounts.length === 0 ? 0
        : Math.min(this.data.futuresRollOpenAccountIndex, tradeAccounts.length - 1);
      this.setData({
        accounts: response.items,
        transactionAccountFilterIds: ["", ...response.items.map((account) => account.accountId)],
        transactionAccountFilterNames: ["全部现金账户", ...response.items.map((account) =>
          `${account.displayName} · ${account.currency}`)],
        transactionAccountFilterIndex: Math.min(this.data.transactionAccountFilterIndex, response.items.length),
        tradeAccounts,
        tradeAccountNames: tradeAccounts.map((account) => `${account.displayName} · ${account.currency}`),
        tradeAccountIndex,
        cashFlowSourceIndex,
        cashFlowDestinationIndex,
        futuresAccountIndex,
        futuresRollCloseAccountIndex,
        futuresRollOpenAccountIndex,
        selectedTradeCurrency: tradeAccounts[tradeAccountIndex]?.currency || ""
      });
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({errorMessage: problem.message || "现金账户暂时无法读取"});
    } finally {
      this.setData({loading: false});
    }
  },

  async loadTransactions(append = false) {
    if (!wx.getStorageSync("investment.accessToken")) {
      return;
    }
    if (append && !this.data.transactionNextCursor) {
      return;
    }
    this.setData({loadingTransactions: true});
    try {
      const response = await request<TransactionListResponse>(
        `/api/v1/ledger/transactions?${transactionQuery(this.data, append ? this.data.transactionNextCursor : null)}`,
        "GET");
      const incoming = response.items.map((item) => ({...item, typeLabel: transactionTypeLabel(item.transactionType)}));
      this.setData({transactions: append ? [...this.data.transactions, ...incoming] : incoming,
        transactionNextCursor: response.nextCursor});
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({errorMessage: problem.message || "账务流水暂时无法读取"});
    } finally {
      this.setData({loadingTransactions: false});
    }
  },

  async loadMoreTransactions() {
    await this.loadTransactions(true);
  },

  onTransactionSearchInput(event) {
    this.setData({transactionSearch: event.detail.value});
  },

  onTransactionAccountFilterChange(event) {
    this.setData({transactionAccountFilterIndex: Number(event.detail.value)});
  },

  onTransactionTypeFilterChange(event) {
    this.setData({transactionTypeFilterIndex: Number(event.detail.value)});
  },

  onTransactionFromChange(event) {
    this.setData({transactionFrom: event.detail.value as string});
  },

  onTransactionToChange(event) {
    this.setData({transactionTo: event.detail.value as string});
  },

  async applyTransactionFilters() {
    if (this.data.transactionFrom && this.data.transactionTo
      && this.data.transactionFrom > this.data.transactionTo) {
      this.setData({errorMessage: "流水起始日期不能晚于结束日期"});
      return;
    }
    await this.loadTransactions();
  },

  async clearTransactionFilters() {
    this.setData({transactionSearch: "", transactionAccountFilterIndex: 0, transactionTypeFilterIndex: 0,
      transactionFrom: "", transactionTo: "", transactionNextCursor: null});
    await this.loadTransactions();
  },

  downloadLedgerExport(event) {
    const format = event.currentTarget.dataset.format;
    if ((format !== "JSON" && format !== "CSV") || this.data.exportingFormat) {
      return;
    }
    const token = wx.getStorageSync("investment.accessToken") as string;
    if (!token) {
      wx.reLaunch({url: "/pages/login/login"});
      return;
    }
    this.setData({exportingFormat: format, errorMessage: ""});
    wx.downloadFile({
      url: `${runtimeConfig.apiBaseUrl}/api/v1/ledger/exports?format=${format}`,
      header: {Authorization: `Bearer ${token}`},
      success: (response) => {
        if (response.statusCode !== 200) {
          this.setData({errorMessage: `账本导出失败（HTTP ${response.statusCode}）`});
          return;
        }
        wx.openDocument({filePath: response.tempFilePath, showMenu: true,
          success: () => wx.showToast({title: `${format} 已生成`, icon: "success"}),
          fail: () => wx.showToast({title: "已下载，可在微信文件中查看", icon: "none"})});
      },
      fail: (error) => {
        const message = error && typeof error.errMsg === "string" ? error.errMsg : "网络异常";
        this.setData({errorMessage: `账本导出失败：${message}`});
      },
      complete: () => this.setData({exportingFormat: ""})
    });
  },

  async loadSnapshots() {
    if (!wx.getStorageSync("investment.accessToken")) {
      return;
    }
    this.setData({loadingSnapshots: true});
    try {
      const response = await request<LedgerSnapshotListResponse>("/api/v1/ledger/snapshots?limit=20", "GET");
      this.setData({snapshots: response.items});
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({errorMessage: problem.message || "账本快照暂时无法读取"});
    } finally {
      this.setData({loadingSnapshots: false});
    }
  },

  async createSnapshot() {
    if (this.data.creatingSnapshot || this.data.restoringSnapshotId) {
      return;
    }
    this.setData({creatingSnapshot: true, errorMessage: ""});
    try {
      const snapshot = await request<LedgerSnapshotResponse>("/api/v1/ledger/snapshots", "POST", undefined,
        {"Idempotency-Key": newIdempotencyKey()});
      wx.showToast({title: `快照已就绪（版本 ${snapshot.sourceLedgerVersion}）`, icon: "success"});
      await this.loadSnapshots();
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({errorMessage: problem.message || "无法创建账本快照"});
    } finally {
      this.setData({creatingSnapshot: false});
    }
  },

  downloadSnapshot(event) {
    const snapshotId = event.currentTarget.dataset.snapshotId;
    if (!snapshotId || !/^[0-9A-HJKMNP-TV-Z]{26}$/.test(snapshotId)) {
      this.setData({errorMessage: "快照标识无效"});
      return;
    }
    const token = wx.getStorageSync("investment.accessToken") as string;
    if (!token) {
      wx.reLaunch({url: "/pages/login/login"});
      return;
    }
    wx.downloadFile({
      url: `${runtimeConfig.apiBaseUrl}/api/v1/ledger/snapshots/${snapshotId}/download`,
      header: {Authorization: `Bearer ${token}`},
      success: (response) => {
        if (response.statusCode !== 200) {
          this.setData({errorMessage: `快照下载失败（HTTP ${response.statusCode}）`});
          return;
        }
        wx.openDocument({filePath: response.tempFilePath, showMenu: true,
          success: () => wx.showToast({title: "快照已下载", icon: "success"}),
          fail: () => wx.showToast({title: "已下载，可在微信文件中查看", icon: "none"})});
      },
      fail: (error) => {
        const message = error && typeof error.errMsg === "string" ? error.errMsg : "网络异常";
        this.setData({errorMessage: `快照下载失败：${message}`});
      }
    });
  },

  restoreSnapshot(event) {
    const snapshotId = event.currentTarget.dataset.snapshotId;
    if (!snapshotId || !/^[0-9A-HJKMNP-TV-Z]{26}$/.test(snapshotId) || this.data.restoringSnapshotId) {
      return;
    }
    wx.showModal({
      title: "受控恢复确认",
      content: "恢复仅在服务端判定当前账本完全为空时才会执行，并会以新的导入事实写入。已有任一账户或交易都会被拒绝，绝不会覆盖或删除现有记录。",
      confirmText: "确认恢复",
      confirmColor: "#B45309",
      success: async (modal) => {
        if (!modal.confirm) return;
        this.setData({restoringSnapshotId: snapshotId, errorMessage: ""});
        try {
          const result = await request<LedgerSnapshotRestoreResponse>(
            `/api/v1/ledger/snapshots/${snapshotId}/restore`, "POST", undefined,
            {"Idempotency-Key": newIdempotencyKey()});
          wx.showToast({title: `已恢复 ${result.restoredTransactionCount} 条事实`, icon: "success"});
          await Promise.all([this.loadAccounts(), this.loadTransactions(), this.loadSnapshots()]);
        } catch (error) {
          const problem = error as {message?: string};
          this.setData({errorMessage: problem.message || "恢复被服务端拒绝或失败"});
        } finally {
          this.setData({restoringSnapshotId: ""});
        }
      }
    });
  },

  async loadTransactionDetail(event) {
    const transactionId = event.currentTarget.dataset.transactionId;
    if (!transactionId || !/^[0-9A-HJKMNP-TV-Z]{26}$/.test(transactionId)) {
      this.setData({errorMessage: "交易标识无效，无法读取审计明细"});
      return;
    }
    this.setData({loadingTransactionDetail: true, selectedTransaction: null, errorMessage: ""});
    try {
      const detail = await request<TransactionDetailResponse>(`/api/v1/ledger/transactions/${transactionId}`, "GET");
      const postingViews = detail.postings.map((posting) => ({
        ...posting,
        amountDisplay: formatMinorUnit(posting.amountCent, posting.currency),
        sideLabel: posting.postingSide === "DEBIT" ? "借方" : "贷方"
      }));
      const fallbackCurrency = detail.postings[0]?.currency || "CNY";
      this.setData({
        selectedTransaction: {
          ...detail,
          typeLabel: transactionTypeLabel(detail.transactionType),
          postingViews,
          tradeDetailViews: detail.tradeDetails.map((tradeDetail) => ({
            ...tradeDetail,
            feeDisplay: formatMinorUnit(tradeDetail.feeCent, fallbackCurrency)
          })),
          correctionStatusLabel: detail.correctable ? "可冲正或更正" : "已冲正/不可再次冲正",
          replaceable: detail.correctable && isReplacementSupported(detail.transactionType)
        }
      });
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({errorMessage: problem.message || "账务明细暂时无法读取"});
    } finally {
      this.setData({loadingTransactionDetail: false});
    }
  },

  closeTransactionDetail() {
    this.setData({selectedTransaction: null});
  },

  correctSelectedTransaction() {
    const transaction = this.data.selectedTransaction;
    if (!transaction || !transaction.correctable || this.data.correctingTransaction) {
      return;
    }
    wx.showModal({
      title: "确认冲正账务事实",
      content: "冲正会追加一条反向分录，不会删除原始交易。若要改写金额、账户或标的，请在冲正后使用上方录入区提交正确的新事实；当前版本会保留完整审计链。",
      confirmText: "确认冲正",
      confirmColor: "#a33a35",
      success: (result) => {
        if (result.confirm) {
          void this.submitReversal();
        }
      }
    });
  },

  async submitReversal() {
    const transaction = this.data.selectedTransaction;
    if (!transaction || !transaction.correctable) {
      return;
    }
    this.setData({correctingTransaction: true, errorMessage: ""});
    try {
      const result = await request<CorrectionResponse>(
        `/api/v1/ledger/transactions/${transaction.transactionId}/corrections`, "POST", undefined,
        {"Idempotency-Key": newIdempotencyKey()});
      this.setData({correctingTransaction: false, selectedTransaction: null});
      wx.showToast({title: "冲正事实已追加", icon: "success"});
      await this.loadTransactions();
      if (result.reversalTransactionIds.length !== 1) {
        this.setData({errorMessage: "冲正结果的审计链异常，请刷新后核对"});
      }
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({correctingTransaction: false, errorMessage: problem.message || "冲正失败；原始交易没有被删除"});
    }
  },

  beginReplacement() {
    const transaction = this.data.selectedTransaction;
    if (!transaction || !transaction.replaceable || this.data.correctingTransaction) {
      return;
    }
    this.setData({
      correctionTargetTransactionId: transaction.transactionId,
      correctionTargetType: transaction.typeLabel,
      strategyKey: transaction.strategyKey || "",
      selectedTransaction: null,
      errorMessage: ""
    });
    wx.showToast({title: "请录入替代事实并先预览", icon: "none"});
  },

  cancelReplacement() {
    this.setData({correctionTargetTransactionId: "", correctionTargetType: "", errorMessage: ""});
  },

  async submitReplacementAware(payload: object, key: string): Promise<LedgerTransactionResponse> {
    if (!this.data.correctionTargetTransactionId) {
      return request<LedgerTransactionResponse>("/api/v1/ledger/transactions", "POST", payload,
        {"Idempotency-Key": key});
    }
    const correction = await request<CorrectionResponse>(
      `/api/v1/ledger/transactions/${this.data.correctionTargetTransactionId}/corrections`, "POST",
      {replacement: payload}, {"Idempotency-Key": key});
    const replacementTransactionId = correction.replacementTransactionIds[0];
    if (!replacementTransactionId) {
      throw new Error("更正未返回替代交易标识；原始交易未被静默删除");
    }
    return {transactionId: replacementTransactionId};
  },

  onDisplayNameInput(event) {
    this.setData({displayName: event.detail.value});
  },

  onCurrencyChange(event) {
    this.setData({currencyIndex: Number(event.detail.value)});
  },

  async createCashAccount() {
    const displayName = this.data.displayName.trim();
    if (!displayName) {
      this.setData({errorMessage: "请填写账户名称"});
      return;
    }
    const currency = this.data.currencies[this.data.currencyIndex];
    this.setData({creating: true, errorMessage: ""});
    try {
      const account = await request<CashAccount>("/api/v1/ledger/accounts", "POST", {
        displayName,
        currency
      }, {"Idempotency-Key": newIdempotencyKey()});
      const accounts = [...this.data.accounts, account];
      const tradeAccounts = accounts.filter((item) => item.status === "ACTIVE");
      const tradeAccountIndex = Math.max(0, tradeAccounts.findIndex((item) => item.accountId === account.accountId));
      this.setData({
        accounts,
        tradeAccounts,
        tradeAccountNames: tradeAccounts.map((item) => `${item.displayName} · ${item.currency}`),
        tradeAccountIndex,
        cashFlowSourceIndex: tradeAccountIndex,
        cashFlowDestinationIndex: tradeAccountIndex,
        futuresAccountIndex: tradeAccountIndex,
        futuresRollCloseAccountIndex: tradeAccountIndex,
        futuresRollOpenAccountIndex: tradeAccountIndex,
        selectedTradeCurrency: account.currency,
        displayName: ""
      });
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({errorMessage: problem.message || "账户创建失败，请稍后重试"});
    } finally {
      this.setData({creating: false});
    }
  },

  onTradeAccountChange(event) {
    const tradeAccountIndex = Number(event.detail.value);
    this.resetPreview({
      tradeAccountIndex,
      selectedTradeCurrency: this.data.tradeAccounts[tradeAccountIndex]?.currency || ""
    });
  },

  onTradeTypeChange(event) {
    this.resetPreview({tradeTypeIndex: Number(event.detail.value)});
  },

  onCashFlowTypeChange(event) {
    this.resetCashFlowPreview({cashFlowTypeIndex: Number(event.detail.value)});
  },

  onCashFlowSourceChange(event) {
    this.resetCashFlowPreview({cashFlowSourceIndex: Number(event.detail.value)});
  },

  onCashFlowDestinationChange(event) {
    this.resetCashFlowPreview({cashFlowDestinationIndex: Number(event.detail.value)});
  },

  onCashFlowOccurredOnChange(event) {
    if (typeof event.detail.value !== "string") {
      return;
    }
    this.resetCashFlowPreview({cashFlowOccurredOn: event.detail.value});
  },

  onCashFlowAmountInput(event) {
    this.resetCashFlowPreview({cashFlowAmountCent: event.detail.value});
  },

  onCashFlowInstrumentIdInput(event) {
    this.resetCashFlowPreview({cashFlowInstrumentId: event.detail.value});
  },

  onCashFlowTaxInput(event) {
    this.resetCashFlowPreview({cashFlowTaxWithheldCent: event.detail.value});
  },

  onCashFlowEntitlementDateChange(event) {
    if (typeof event.detail.value !== "string") {
      return;
    }
    this.resetCashFlowPreview({cashFlowEntitlementDate: event.detail.value});
  },

  onCashFlowPerShareAmountInput(event) {
    this.resetCashFlowPreview({cashFlowPerShareAmountCent: event.detail.value});
  },

  onMarginDirectionChange(event) {
    this.resetCashFlowPreview({marginDirectionIndex: Number(event.detail.value)});
  },

  onCashFlowNoteInput(event) {
    this.resetCashFlowPreview({cashFlowNote: event.detail.value});
  },

  onOccurredOnChange(event) {
    if (typeof event.detail.value !== "string") {
      return;
    }
    this.resetPreview({occurredOn: event.detail.value});
  },

  onInstrumentIdInput(event) {
    this.resetPreview({instrumentId: event.detail.value});
  },

  onQuantityInput(event) {
    this.resetPreview({quantity: event.detail.value});
  },

  onUnitPriceInput(event) {
    this.resetPreview({unitPriceCent: event.detail.value});
  },

  onFeeInput(event) {
    this.resetPreview({feeCent: event.detail.value});
  },

  onNoteInput(event) {
    this.resetPreview({note: event.detail.value});
  },

  async previewTrade() {
    const payload = this.tradePayload();
    if (!payload) {
      return;
    }
    this.setData({previewing: true, errorMessage: "", lastTransactionId: ""});
    try {
      const preview = await request<TransactionPreviewResponse>("/api/v1/ledger/transactions/preview", "POST", payload);
      this.setData({
        previewPostings: preview.postings.map((posting) => ({
          ...posting,
          amountDisplay: formatMinorUnit(posting.amountCent, posting.currency),
          sideLabel: posting.postingSide === "DEBIT" ? "借方" : "贷方"
        })),
        accountProvisioning: preview.accountProvisioning,
        validationWarnings: preview.validationWarnings,
        submissionState: "PREVIEWED",
        pendingIdempotencyKey: ""
      });
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({errorMessage: problem.message || "预览失败，请检查输入后重试"});
    } finally {
      this.setData({previewing: false});
    }
  },

  confirmTrade() {
    if (this.data.submissionState !== "PREVIEWED") {
      return;
    }
    wx.showModal({
      title: "确认记账",
      content: "确认后将追加不可修改的账务事实。请核对预览分录与金额。",
      confirmText: "确认提交",
      success: (result) => {
        if (result.confirm) {
          void this.submitTrade();
        }
      }
    });
  },

  async retryTrade() {
    if (this.data.submissionState !== "RETRYABLE_ERROR" || !this.data.pendingIdempotencyKey) {
      return;
    }
    await this.submitTrade();
  },

  goMarket() {
    wx.switchTab({url: "/pages/market/market"});
  },

  async previewCashFlow() {
    const payload = this.cashFlowPayload();
    if (!payload) {
      return;
    }
    this.setData({cashFlowPreviewing: true, errorMessage: "", cashFlowLastTransactionId: ""});
    try {
      const preview = await request<TransactionPreviewResponse>("/api/v1/ledger/transactions/preview", "POST", payload);
      this.setData({
        cashFlowPreviewPostings: preview.postings.map((posting) => ({
          ...posting,
          amountDisplay: formatMinorUnit(posting.amountCent, posting.currency),
          sideLabel: posting.postingSide === "DEBIT" ? "借方" : "贷方"
        })),
        cashFlowSubmissionState: "PREVIEWED",
        cashFlowPendingIdempotencyKey: ""
      });
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({errorMessage: problem.message || "资金操作预览失败，请检查输入后重试"});
    } finally {
      this.setData({cashFlowPreviewing: false});
    }
  },

  confirmCashFlow() {
    if (this.data.cashFlowSubmissionState !== "PREVIEWED") {
      return;
    }
    wx.showModal({
      title: "确认资金操作",
      content: "确认后将追加不可修改的账务事实。请核对预览分录与金额。",
      confirmText: "确认提交",
      success: (result) => {
        if (result.confirm) {
          void this.submitCashFlow();
        }
      }
    });
  },

  async retryCashFlow() {
    if (this.data.cashFlowSubmissionState !== "RETRYABLE_ERROR" || !this.data.cashFlowPendingIdempotencyKey) {
      return;
    }
    await this.submitCashFlow();
  },

  onCorporateActionTypeChange(event) {
    this.resetCorporateActionPreview({corporateActionTypeIndex: Number(event.detail.value)});
  },

  onCorporateActionOccurredOnChange(event) {
    if (typeof event.detail.value === "string") {
      this.resetCorporateActionPreview({corporateActionOccurredOn: event.detail.value});
    }
  },

  onCorporateActionInstrumentIdInput(event) {
    this.resetCorporateActionPreview({corporateActionInstrumentId: event.detail.value});
  },

  onCorporateActionRatioNumeratorInput(event) {
    this.resetCorporateActionPreview({corporateActionRatioNumerator: event.detail.value});
  },

  onCorporateActionRatioDenominatorInput(event) {
    this.resetCorporateActionPreview({corporateActionRatioDenominator: event.detail.value});
  },

  onCorporateActionNoteInput(event) {
    this.resetCorporateActionPreview({corporateActionNote: event.detail.value});
  },

  onFuturesAccountChange(event) {
    this.resetFuturesOpenPreview({futuresAccountIndex: Number(event.detail.value)});
  },

  onFuturesTransactionTypeChange(event) {
    this.resetFuturesOpenPreview({futuresTransactionTypeIndex: Number(event.detail.value)});
  },

  onFuturesOccurredOnChange(event) {
    if (typeof event.detail.value === "string") {
      this.resetFuturesOpenPreview({futuresOccurredOn: event.detail.value});
    }
  },

  onFuturesInstrumentIdInput(event) {
    this.resetFuturesOpenPreview({futuresInstrumentId: event.detail.value});
  },

  onFuturesQuantityInput(event) {
    this.resetFuturesOpenPreview({futuresQuantity: event.detail.value});
  },

  onFuturesPricePointsInput(event) {
    this.resetFuturesOpenPreview({futuresPricePoints: event.detail.value});
  },

  onFuturesInitialMarginInput(event) {
    this.resetFuturesOpenPreview({futuresInitialMarginCent: event.detail.value});
  },

  onFuturesFeeInput(event) {
    this.resetFuturesOpenPreview({futuresFeeCent: event.detail.value});
  },

  onFuturesNoteInput(event) {
    this.resetFuturesOpenPreview({futuresNote: event.detail.value});
  },

  async previewFuturesOpen() {
    const payload = this.futuresOpenPayload();
    if (!payload) {
      return;
    }
    this.setData({futuresPreviewing: true, errorMessage: "", futuresLastTransactionId: ""});
    try {
      const preview = await request<TransactionPreviewResponse>("/api/v1/ledger/transactions/preview", "POST", payload);
      this.setData({
        futuresPreviewPostings: preview.postings.map((posting) => ({
          ...posting,
          amountDisplay: formatMinorUnit(posting.amountCent, posting.currency),
          sideLabel: posting.postingSide === "DEBIT" ? "借方" : "贷方"
        })),
        futuresAccountProvisioning: preview.accountProvisioning,
        futuresValidationWarnings: preview.validationWarnings,
        futuresSubmissionState: "PREVIEWED",
        futuresPendingIdempotencyKey: ""
      });
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({errorMessage: problem.message || "期货开仓预览失败，请检查保证金、合约和交易日"});
    } finally {
      this.setData({futuresPreviewing: false});
    }
  },

  confirmFuturesOpen() {
    if (this.data.futuresSubmissionState !== "PREVIEWED") {
      return;
    }
    wx.showModal({
      title: futuresConfirmationTitle(this.data.futuresTransactionTypes[this.data.futuresTransactionTypeIndex]),
      content: futuresConfirmationContent(this.data.futuresTransactionTypes[this.data.futuresTransactionTypeIndex]),
      confirmText: futuresConfirmationText(this.data.futuresTransactionTypes[this.data.futuresTransactionTypeIndex]),
      success: (result) => {
        if (result.confirm) {
          void this.submitFuturesOpen();
        }
      }
    });
  },

  async retryFuturesOpen() {
    if (this.data.futuresSubmissionState === "RETRYABLE_ERROR" && this.data.futuresPendingIdempotencyKey) {
      await this.submitFuturesOpen();
    }
  },

  onFuturesRollCloseAccountChange(event) {
    this.resetFuturesRollPreview({futuresRollCloseAccountIndex: Number(event.detail.value)});
  },

  onFuturesRollOpenAccountChange(event) {
    this.resetFuturesRollPreview({futuresRollOpenAccountIndex: Number(event.detail.value)});
  },

  onFuturesRollOccurredOnChange(event) {
    if (typeof event.detail.value === "string") {
      this.resetFuturesRollPreview({futuresRollOccurredOn: event.detail.value});
    }
  },

  onFuturesRollCloseInstrumentIdInput(event) {
    this.resetFuturesRollPreview({futuresRollCloseInstrumentId: event.detail.value});
  },

  onFuturesRollCloseQuantityInput(event) {
    this.resetFuturesRollPreview({futuresRollCloseQuantity: event.detail.value});
  },

  onFuturesRollClosePricePointsInput(event) {
    this.resetFuturesRollPreview({futuresRollClosePricePoints: event.detail.value});
  },

  onFuturesRollCloseFeeInput(event) {
    this.resetFuturesRollPreview({futuresRollCloseFeeCent: event.detail.value});
  },

  onFuturesRollOpenInstrumentIdInput(event) {
    this.resetFuturesRollPreview({futuresRollOpenInstrumentId: event.detail.value});
  },

  onFuturesRollOpenQuantityInput(event) {
    this.resetFuturesRollPreview({futuresRollOpenQuantity: event.detail.value});
  },

  onFuturesRollOpenPricePointsInput(event) {
    this.resetFuturesRollPreview({futuresRollOpenPricePoints: event.detail.value});
  },

  onFuturesRollOpenInitialMarginInput(event) {
    this.resetFuturesRollPreview({futuresRollOpenInitialMarginCent: event.detail.value});
  },

  onFuturesRollOpenFeeInput(event) {
    this.resetFuturesRollPreview({futuresRollOpenFeeCent: event.detail.value});
  },

  onFuturesRollNoteInput(event) {
    this.resetFuturesRollPreview({futuresRollNote: event.detail.value});
  },

  async previewFuturesRoll() {
    const payload = this.futuresRollPayload();
    if (!payload) {
      return;
    }
    this.setData({futuresRollPreviewing: true, errorMessage: "", futuresRollLastOperationGroupKey: ""});
    try {
      const preview = await request<TransactionPreviewResponse>("/api/v1/ledger/transactions/preview", "POST", payload);
      this.setData({
        futuresRollPreviewPostings: preview.postings.map((posting) => ({
          ...posting,
          amountDisplay: formatMinorUnit(posting.amountCent, posting.currency),
          sideLabel: posting.postingSide === "DEBIT" ? "借方" : "贷方"
        })),
        futuresRollAccountProvisioning: preview.accountProvisioning,
        futuresRollValidationWarnings: preview.validationWarnings,
        futuresRollSubmissionState: "PREVIEWED",
        futuresRollPendingIdempotencyKey: "",
        futuresRollProposedOperationGroupKey: preview.proposedOperationGroupKey || ""
      });
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({errorMessage: problem.message || "移仓预览失败，请检查两腿合约、FIFO 持仓、保证金和交易日"});
    } finally {
      this.setData({futuresRollPreviewing: false});
    }
  },

  confirmFuturesRoll() {
    if (this.data.futuresRollSubmissionState !== "PREVIEWED") {
      return;
    }
    wx.showModal({
      title: "确认期货移仓",
      content: "确认后会在同一事务中平掉旧合约并开立新合约，任一腿校验失败则两条事实均不会写入。请核对两腿合约、点位、手数、保证金和手续费。",
      confirmText: "确认移仓",
      success: (result) => {
        if (result.confirm) {
          void this.submitFuturesRoll();
        }
      }
    });
  },

  async retryFuturesRoll() {
    if (this.data.futuresRollSubmissionState === "RETRYABLE_ERROR" && this.data.futuresRollPendingIdempotencyKey) {
      await this.submitFuturesRoll();
    }
  },

  onOptionAccountChange(event) {
    this.resetOptionPreview({optionAccountIndex: Number(event.detail.value)});
  },

  onOptionTransactionTypeChange(event) {
    this.resetOptionPreview({optionTransactionTypeIndex: Number(event.detail.value)});
  },

  onOptionOccurredOnChange(event) {
    if (typeof event.detail.value === "string") {
      this.resetOptionPreview({optionOccurredOn: event.detail.value});
    }
  },

  onOptionInstrumentIdInput(event) {
    this.resetOptionPreview({optionInstrumentId: event.detail.value});
  },

  onOptionQuantityInput(event) {
    this.resetOptionPreview({optionQuantity: event.detail.value});
  },

  onOptionUnitPriceInput(event) {
    this.resetOptionPreview({optionUnitPriceCent: event.detail.value});
  },

  onOptionFeeInput(event) {
    this.resetOptionPreview({optionFeeCent: event.detail.value});
  },

  onOptionNoteInput(event) {
    this.resetOptionPreview({optionNote: event.detail.value});
  },

  async previewOption() {
    const payload = this.optionPayload();
    if (!payload) {
      return;
    }
    this.setData({optionPreviewing: true, errorMessage: "", optionLastTransactionId: ""});
    try {
      const preview = await request<TransactionPreviewResponse>("/api/v1/ledger/transactions/preview", "POST", payload);
      this.setData({
        optionPreviewPostings: preview.postings.map((posting) => ({
          ...posting,
          amountDisplay: formatMinorUnit(posting.amountCent, posting.currency),
          sideLabel: posting.postingSide === "DEBIT" ? "借方" : "贷方"
        })),
        optionAccountProvisioning: preview.accountProvisioning,
        optionValidationWarnings: preview.validationWarnings,
        optionSubmissionState: "PREVIEWED",
        optionPendingIdempotencyKey: ""
      });
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({errorMessage: problem.message || "期权预览失败，请检查合约、数量、日期和可用资金"});
    } finally {
      this.setData({optionPreviewing: false});
    }
  },

  confirmOption() {
    if (this.data.optionSubmissionState !== "PREVIEWED") {
      return;
    }
    const type = this.data.optionTransactionTypes[this.data.optionTransactionTypeIndex];
    wx.showModal({
      title: optionConfirmationTitle(type),
      content: optionConfirmationContent(type),
      confirmText: optionConfirmationText(type),
      success: (result) => {
        if (result.confirm) {
          void this.submitOption();
        }
      }
    });
  },

  async retryOption() {
    if (this.data.optionSubmissionState === "RETRYABLE_ERROR" && this.data.optionPendingIdempotencyKey) {
      await this.submitOption();
    }
  },

  async previewCorporateAction() {
    const payload = this.corporateActionPayload();
    if (!payload) {
      return;
    }
    this.setData({corporateActionPreviewing: true, errorMessage: "", corporateActionLastTransactionId: ""});
    try {
      await request<TransactionPreviewResponse>("/api/v1/ledger/transactions/preview", "POST", payload);
      this.setData({corporateActionSubmissionState: "PREVIEWED", corporateActionPendingIdempotencyKey: ""});
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({errorMessage: problem.message || "公司行为预览失败，请检查生效日、持仓与比例"});
    } finally {
      this.setData({corporateActionPreviewing: false});
    }
  },

  confirmCorporateAction() {
    if (this.data.corporateActionSubmissionState !== "PREVIEWED") {
      return;
    }
    wx.showModal({
      title: "确认公司行为",
      content: "确认后将追加不可修改的公司行为事实，并按生效日重放持仓批次。该操作不产生现金分录。",
      confirmText: "确认提交",
      success: (result) => {
        if (result.confirm) {
          void this.submitCorporateAction();
        }
      }
    });
  },

  async retryCorporateAction() {
    if (this.data.corporateActionSubmissionState === "RETRYABLE_ERROR"
        && this.data.corporateActionPendingIdempotencyKey) {
      await this.submitCorporateAction();
    }
  },

  resetPreview(patch: Partial<LedgerPageData>) {
    this.setData({
      ...patch,
      previewPostings: [],
      accountProvisioning: [],
      validationWarnings: [],
      submissionState: "IDLE",
      pendingIdempotencyKey: "",
      lastTransactionId: "",
      errorMessage: ""
    });
  },

  resetCashFlowPreview(patch: Partial<LedgerPageData>) {
    this.setData({
      ...patch,
      cashFlowPreviewPostings: [],
      cashFlowSubmissionState: "IDLE",
      cashFlowPendingIdempotencyKey: "",
      cashFlowLastTransactionId: "",
      errorMessage: ""
    });
  },

  resetCorporateActionPreview(patch: Partial<LedgerPageData>) {
    this.setData({
      ...patch,
      corporateActionSubmissionState: "IDLE",
      corporateActionPendingIdempotencyKey: "",
      corporateActionLastTransactionId: "",
      errorMessage: ""
    });
  },

  resetFuturesOpenPreview(patch: Partial<LedgerPageData>) {
    this.setData({
      ...patch,
      futuresPreviewPostings: [],
      futuresAccountProvisioning: [],
      futuresValidationWarnings: [],
      futuresSubmissionState: "IDLE",
      futuresPendingIdempotencyKey: "",
      futuresLastTransactionId: "",
      errorMessage: ""
    });
  },

  resetFuturesRollPreview(patch: Partial<LedgerPageData>) {
    this.setData({
      ...patch,
      futuresRollPreviewPostings: [],
      futuresRollAccountProvisioning: [],
      futuresRollValidationWarnings: [],
      futuresRollSubmissionState: "IDLE",
      futuresRollPendingIdempotencyKey: "",
      futuresRollProposedOperationGroupKey: "",
      futuresRollLastOperationGroupKey: "",
      errorMessage: ""
    });
  },

  resetOptionPreview(patch: Partial<LedgerPageData>) {
    this.setData({
      ...patch,
      optionPreviewPostings: [],
      optionAccountProvisioning: [],
      optionValidationWarnings: [],
      optionSubmissionState: "IDLE",
      optionPendingIdempotencyKey: "",
      optionLastTransactionId: "",
      errorMessage: ""
    });
  },

  tradePayload(): SpotTransactionCreateRequest | null {
    const cashAccount = this.data.tradeAccounts[this.data.tradeAccountIndex];
    const instrumentId = this.data.instrumentId.trim();
    const quantity = this.data.quantity.trim();
    const unitPriceCent = this.data.unitPriceCent.trim();
    const feeCent = this.data.feeCent.trim();
    if (!cashAccount) {
      this.setData({errorMessage: "请先创建并选择启用中的现金账户"});
      return null;
    }
    if (!/^[0-9A-HJKMNP-TV-Z]{26}$/.test(instrumentId)) {
      this.setData({errorMessage: "请填写有效的 26 位标的 ID"});
      return null;
    }
    if (!/^[1-9][0-9]*(?:\.[0-9]{1,8})?$/.test(quantity)) {
      this.setData({errorMessage: "数量必须是正数，且最多保留 8 位小数"});
      return null;
    }
    if (!/^[1-9][0-9]*$/.test(unitPriceCent)) {
      this.setData({errorMessage: "成交单价必须以最小货币单位填写正整数"});
      return null;
    }
    if (!/^(?:0|[1-9][0-9]*)$/.test(feeCent)) {
      this.setData({errorMessage: "手续费必须以最小货币单位填写非负整数"});
      return null;
    }
    const payload: SpotTransactionCreateRequest = {
      transactionType: this.data.tradeTypes[this.data.tradeTypeIndex],
      occurredOn: this.data.occurredOn,
      cashAccountId: cashAccount.accountId,
      instrumentId,
      quantity,
      unitPriceCent,
      feeCent
    };
    const note = this.data.note.trim();
    if (note) {
      payload.note = note;
    }
    if (this.data.strategyKey) {
      payload.strategyKey = this.data.strategyKey;
    }
    return payload;
  },

  async submitTrade() {
    const payload = this.tradePayload();
    if (!payload) {
      return;
    }
    const key = this.data.pendingIdempotencyKey || newIdempotencyKey();
    this.setData({submitting: true, submissionState: "SUBMITTING", pendingIdempotencyKey: key, errorMessage: ""});
    try {
      const transaction = await this.submitReplacementAware(payload, key);
      this.setData({
        submitting: false,
        submissionState: "SUCCESS",
        pendingIdempotencyKey: "",
        previewPostings: [],
        accountProvisioning: [],
        validationWarnings: [],
        lastTransactionId: transaction.transactionId,
        correctionTargetTransactionId: "",
        correctionTargetType: ""
      });
      wx.showToast({title: "记账成功", icon: "success"});
      void this.loadTransactions();
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({
        submitting: false,
        submissionState: "RETRYABLE_ERROR",
        errorMessage: problem.message || "网络或服务异常，未自动重试。请确认后手动重试。"
      });
    }
  },

  cashFlowPayload(): CashFlowCreateRequest | null {
    const cashAccount = this.data.tradeAccounts[this.data.cashFlowSourceIndex];
    const transactionType = this.data.cashFlowTypes[this.data.cashFlowTypeIndex];
    const amountCent = this.data.cashFlowAmountCent.trim();
    if (!cashAccount) {
      this.setData({errorMessage: "请先创建并选择启用中的现金账户"});
      return null;
    }
    if (!/^[1-9][0-9]*$/.test(amountCent)) {
      this.setData({errorMessage: "金额必须以最小货币单位填写正整数"});
      return null;
    }
    const payload: CashFlowCreateRequest = {
      transactionType,
      occurredOn: this.data.cashFlowOccurredOn,
      cashAccountId: cashAccount.accountId,
      amountCent
    };
    if (transactionType === "INTERNAL_TRANSFER") {
      const destination = this.data.tradeAccounts[this.data.cashFlowDestinationIndex];
      if (!destination || destination.accountId === cashAccount.accountId) {
        this.setData({errorMessage: "调拨必须选择不同的目标现金账户"});
        return null;
      }
      if (destination.currency !== cashAccount.currency) {
        this.setData({errorMessage: "账户内调拨的两端现金账户必须是同一币种"});
        return null;
      }
      payload.destinationAccountId = destination.accountId;
    }
    if (transactionType === "FUTURES_MARGIN") {
      if (cashAccount.currency !== "CNY") {
        this.setData({errorMessage: "CFFEX 期货保证金只能使用人民币现金账户"});
        return null;
      }
      payload.marginDirection = this.data.marginDirections[this.data.marginDirectionIndex];
    }
    if (transactionType === "DIVIDEND" || transactionType === "INTEREST") {
      const taxWithheldCent = this.data.cashFlowTaxWithheldCent.trim();
      if (!/^(?:0|[1-9][0-9]*)$/.test(taxWithheldCent)) {
        this.setData({errorMessage: "代扣税必须以最小货币单位填写非负整数"});
        return null;
      }
      payload.taxWithheldCent = taxWithheldCent;
      if (transactionType === "DIVIDEND") {
        const instrumentId = this.data.cashFlowInstrumentId.trim();
        if (!/^[0-9A-HJKMNP-TV-Z]{26}$/.test(instrumentId)) {
          this.setData({errorMessage: "现金分红必须填写有效的 26 位标的 ID"});
          return null;
        }
        payload.instrumentId = instrumentId;
        payload.entitlementDate = this.data.cashFlowEntitlementDate;
        const perShareAmountCent = this.data.cashFlowPerShareAmountCent.trim();
        if (perShareAmountCent) {
          if (!/^[1-9][0-9]*$/.test(perShareAmountCent)) {
            this.setData({errorMessage: "每股分红必须以最小货币单位填写正整数，留空则不做每股核验"});
            return null;
          }
          payload.perShareAmountCent = perShareAmountCent;
        }
      }
    }
    const note = this.data.cashFlowNote.trim();
    if (note) {
      payload.note = note;
    }
    if (this.data.strategyKey) {
      payload.strategyKey = this.data.strategyKey;
    }
    return payload;
  },

  async submitCashFlow() {
    const payload = this.cashFlowPayload();
    if (!payload) {
      return;
    }
    if (this.data.correctionTargetTransactionId && !isReplacementSupported(payload.transactionType)) {
      this.setData({errorMessage: "当前更正闭环仅支持资金、现货和公司行为；期货保证金请先仅冲正，再重新录入。"});
      return;
    }
    const key = this.data.cashFlowPendingIdempotencyKey || newIdempotencyKey();
    this.setData({
      cashFlowSubmitting: true,
      cashFlowSubmissionState: "SUBMITTING",
      cashFlowPendingIdempotencyKey: key,
      errorMessage: ""
    });
    try {
      const transaction = await this.submitReplacementAware(payload, key);
      this.setData({
        cashFlowSubmitting: false,
        cashFlowSubmissionState: "SUCCESS",
        cashFlowPendingIdempotencyKey: "",
        cashFlowPreviewPostings: [],
        cashFlowLastTransactionId: transaction.transactionId,
        correctionTargetTransactionId: "",
        correctionTargetType: ""
      });
      wx.showToast({title: "资金操作成功", icon: "success"});
      void this.loadTransactions();
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({
        cashFlowSubmitting: false,
        cashFlowSubmissionState: "RETRYABLE_ERROR",
        errorMessage: problem.message || "网络或服务异常，未自动重试。请确认后手动重试。"
      });
    }
  }
  ,

  corporateActionPayload(): CorporateActionCreateRequest | null {
    const instrumentId = this.data.corporateActionInstrumentId.trim();
    const ratioNumerator = this.data.corporateActionRatioNumerator.trim();
    const ratioDenominator = this.data.corporateActionRatioDenominator.trim();
    if (!/^[0-9A-HJKMNP-TV-Z]{26}$/.test(instrumentId)) {
      this.setData({errorMessage: "请填写有效的 26 位标的 ID"});
      return null;
    }
    if (!/^[1-9][0-9]*$/.test(ratioNumerator) || !/^[1-9][0-9]*$/.test(ratioDenominator)) {
      this.setData({errorMessage: "公司行为比例的分子和分母必须为正整数"});
      return null;
    }
    const payload: CorporateActionCreateRequest = {
      transactionType: "CORPORATE_ACTION",
      occurredOn: this.data.corporateActionOccurredOn,
      corporateAction: {
        actionType: this.data.corporateActionTypes[this.data.corporateActionTypeIndex],
        instrumentId,
        ratioNumerator,
        ratioDenominator
      }
    };
    const note = this.data.corporateActionNote.trim();
    if (note) {
      payload.note = note;
    }
    if (this.data.strategyKey) {
      payload.strategyKey = this.data.strategyKey;
    }
    return payload;
  },

  async submitCorporateAction() {
    const payload = this.corporateActionPayload();
    if (!payload) {
      return;
    }
    const key = this.data.corporateActionPendingIdempotencyKey || newIdempotencyKey();
    this.setData({
      corporateActionSubmitting: true,
      corporateActionSubmissionState: "SUBMITTING",
      corporateActionPendingIdempotencyKey: key,
      errorMessage: ""
    });
    try {
      const transaction = await this.submitReplacementAware(payload, key);
      this.setData({
        corporateActionSubmitting: false,
        corporateActionSubmissionState: "SUCCESS",
        corporateActionPendingIdempotencyKey: "",
        corporateActionLastTransactionId: transaction.transactionId,
        correctionTargetTransactionId: "",
        correctionTargetType: ""
      });
      wx.showToast({title: "公司行为已记账", icon: "success"});
      void this.loadTransactions();
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({
        corporateActionSubmitting: false,
        corporateActionSubmissionState: "RETRYABLE_ERROR",
        errorMessage: problem.message || "网络或服务异常，未自动重试。请确认后手动重试。"
      });
    }
  },

  futuresOpenPayload(): FuturesTransactionCreateRequest | null {
    const cashAccount = this.data.tradeAccounts[this.data.futuresAccountIndex];
    const transactionType = this.data.futuresTransactionTypes[this.data.futuresTransactionTypeIndex];
    const instrumentId = this.data.futuresInstrumentId.trim();
    const quantity = this.data.futuresQuantity.trim();
    const pricePoints = this.data.futuresPricePoints.trim();
    const initialMarginCent = this.data.futuresInitialMarginCent.trim();
    const feeCent = this.data.futuresFeeCent.trim();
    if (!cashAccount) {
      this.setData({errorMessage: "请先创建并选择启用中的人民币现金账户"});
      return null;
    }
    if (cashAccount.currency !== "CNY") {
      this.setData({errorMessage: "CFFEX 期货操作只能使用人民币现金账户"});
      return null;
    }
    if (!/^[0-9A-HJKMNP-TV-Z]{26}$/.test(instrumentId)) {
      this.setData({errorMessage: "请填写有效的 26 位期货合约标的 ID"});
      return null;
    }
    if (!/^[1-9][0-9]*(?:\.[0-9]{1,8})?$/.test(pricePoints)) {
      this.setData({errorMessage: transactionType === "FUTURES_DAILY_SETTLEMENT"
        ? "结算点位必须为正数，最多保留 8 位小数" : "成交点位必须为正数，最多保留 8 位小数"});
      return null;
    }
    if (transactionType !== "FUTURES_DAILY_SETTLEMENT" && !/^[1-9][0-9]*$/.test(quantity)) {
      this.setData({errorMessage: "期货开仓或平仓数量必须为正整数手数"});
      return null;
    }
    if (transactionType !== "FUTURES_DAILY_SETTLEMENT" && !/^(?:0|[1-9][0-9]*)$/.test(feeCent)) {
      this.setData({errorMessage: "手续费必须以人民币最小单位填写非负整数"});
      return null;
    }
    if (transactionType === "FUTURES_OPEN" && !/^[1-9][0-9]*$/.test(initialMarginCent)) {
      this.setData({errorMessage: "初始保证金必须以人民币最小单位填写正整数"});
      return null;
    }
    const payload: FuturesTransactionCreateRequest = {
      transactionType,
      occurredOn: this.data.futuresOccurredOn,
      cashAccountId: cashAccount.accountId,
      instrumentId
    };
    if (transactionType === "FUTURES_DAILY_SETTLEMENT") {
      payload.settlementPricePoints = pricePoints;
    } else {
      payload.quantity = quantity;
      payload.pricePoints = pricePoints;
      payload.feeCent = feeCent;
    }
    if (transactionType === "FUTURES_OPEN") {
      payload.initialMarginCent = initialMarginCent;
    }
    const note = this.data.futuresNote.trim();
    if (note) {
      payload.note = note;
    }
    if (this.data.strategyKey) {
      payload.strategyKey = this.data.strategyKey;
    }
    return payload;
  },

  async submitFuturesOpen() {
    const payload = this.futuresOpenPayload();
    if (!payload) {
      return;
    }
    if (this.data.correctionTargetTransactionId) {
      this.setData({errorMessage: "期货事实当前只支持审计冲正；请取消“更正并替代”后再录入新的期货事实。"});
      return;
    }
    const key = this.data.futuresPendingIdempotencyKey || newIdempotencyKey();
    this.setData({
      futuresSubmitting: true,
      futuresSubmissionState: "SUBMITTING",
      futuresPendingIdempotencyKey: key,
      errorMessage: ""
    });
    try {
      const transaction = await request<LedgerTransactionResponse>("/api/v1/ledger/transactions", "POST", payload,
        {"Idempotency-Key": key});
      this.setData({
        futuresSubmitting: false,
        futuresSubmissionState: "SUCCESS",
        futuresPendingIdempotencyKey: "",
        futuresPreviewPostings: [],
        futuresAccountProvisioning: [],
        futuresValidationWarnings: [],
        futuresLastTransactionId: transaction.transactionId
      });
      wx.showToast({title: futuresSuccessMessage(payload.transactionType), icon: "success"});
      void this.loadTransactions();
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({
        futuresSubmitting: false,
        futuresSubmissionState: "RETRYABLE_ERROR",
        errorMessage: problem.message || "网络或服务异常，未自动重试。请确认后手动重试。"
      });
    }
  },

  futuresRollPayload(): FuturesRollCreateRequest | null {
    const closeAccount = this.data.tradeAccounts[this.data.futuresRollCloseAccountIndex];
    const openAccount = this.data.tradeAccounts[this.data.futuresRollOpenAccountIndex];
    const closeInstrumentId = this.data.futuresRollCloseInstrumentId.trim();
    const closeQuantity = this.data.futuresRollCloseQuantity.trim();
    const closePricePoints = this.data.futuresRollClosePricePoints.trim();
    const closeFeeCent = this.data.futuresRollCloseFeeCent.trim();
    const openInstrumentId = this.data.futuresRollOpenInstrumentId.trim();
    const openQuantity = this.data.futuresRollOpenQuantity.trim();
    const openPricePoints = this.data.futuresRollOpenPricePoints.trim();
    const openInitialMarginCent = this.data.futuresRollOpenInitialMarginCent.trim();
    const openFeeCent = this.data.futuresRollOpenFeeCent.trim();
    if (this.data.strategyKey !== "IC_IM") {
      this.setData({errorMessage: "期货移仓必须从 IC/IM 策略工作区进入，才能固定归属到该策略"});
      return null;
    }
    if (!closeAccount || !openAccount || closeAccount.currency !== "CNY" || openAccount.currency !== "CNY") {
      this.setData({errorMessage: "移仓的平仓与开仓腿均必须使用启用中的人民币现金账户"});
      return null;
    }
    const requiredUlid = [closeInstrumentId, openInstrumentId];
    if (!requiredUlid.every((value) => /^[0-9A-HJKMNP-TV-Z]{26}$/.test(value))) {
      this.setData({errorMessage: "请分别填写有效的 26 位旧合约和新合约标的 ID"});
      return null;
    }
    const lotCount = /^[1-9][0-9]*$/;
    if (!lotCount.test(closeQuantity) || !lotCount.test(openQuantity)) {
      this.setData({errorMessage: "移仓的平仓与开仓数量必须为正整数手数"});
      return null;
    }
    const pricePoints = /^[1-9][0-9]*(?:\.[0-9]{1,8})?$/;
    if (!pricePoints.test(closePricePoints) || !pricePoints.test(openPricePoints)) {
      this.setData({errorMessage: "移仓的两腿成交点位必须为正数，最多保留 8 位小数"});
      return null;
    }
    if (!/^[1-9][0-9]*$/.test(openInitialMarginCent)) {
      this.setData({errorMessage: "新合约初始保证金必须以人民币最小单位填写正整数"});
      return null;
    }
    const fee = /^(?:0|[1-9][0-9]*)$/;
    if (!fee.test(closeFeeCent) || !fee.test(openFeeCent)) {
      this.setData({errorMessage: "移仓两腿手续费必须以人民币最小单位填写非负整数"});
      return null;
    }
    const payload: FuturesRollCreateRequest = {
      transactionType: "FUTURES_ROLL",
      occurredOn: this.data.futuresRollOccurredOn,
      strategyKey: "IC_IM",
      futuresRoll: {
        closeLeg: {
          cashAccountId: closeAccount.accountId,
          instrumentId: closeInstrumentId,
          quantity: closeQuantity,
          pricePoints: closePricePoints,
          feeCent: closeFeeCent
        },
        openLeg: {
          cashAccountId: openAccount.accountId,
          instrumentId: openInstrumentId,
          quantity: openQuantity,
          pricePoints: openPricePoints,
          initialMarginCent: openInitialMarginCent,
          feeCent: openFeeCent
        }
      }
    };
    const note = this.data.futuresRollNote.trim();
    if (note) {
      payload.note = note;
    }
    return payload;
  },

  async submitFuturesRoll() {
    const payload = this.futuresRollPayload();
    if (!payload) {
      return;
    }
    if (this.data.correctionTargetTransactionId) {
      this.setData({errorMessage: "期货移仓当前只支持审计冲正；请取消“更正并替代”后再录入新的移仓事实。"});
      return;
    }
    const key = this.data.futuresRollPendingIdempotencyKey || newIdempotencyKey();
    this.setData({
      futuresRollSubmitting: true,
      futuresRollSubmissionState: "SUBMITTING",
      futuresRollPendingIdempotencyKey: key,
      errorMessage: ""
    });
    try {
      const result = await request<FuturesRollResponse>("/api/v1/ledger/transactions", "POST", payload,
        {"Idempotency-Key": key});
      this.setData({
        futuresRollSubmitting: false,
        futuresRollSubmissionState: "SUCCESS",
        futuresRollPendingIdempotencyKey: "",
        futuresRollPreviewPostings: [],
        futuresRollAccountProvisioning: [],
        futuresRollValidationWarnings: [],
        futuresRollLastOperationGroupKey: result.operationGroupKey
      });
      wx.showToast({title: "期货移仓已记账", icon: "success"});
      void this.loadTransactions();
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({
        futuresRollSubmitting: false,
        futuresRollSubmissionState: "RETRYABLE_ERROR",
        errorMessage: problem.message || "网络或服务异常，移仓未自动重试。请确认后手动重试。"
      });
    }
  },

  optionPayload(): OptionTransactionCreateRequest | null {
    const cashAccount = this.data.tradeAccounts[this.data.optionAccountIndex];
    const transactionType = this.data.optionTransactionTypes[this.data.optionTransactionTypeIndex];
    const instrumentId = this.data.optionInstrumentId.trim();
    const quantity = this.data.optionQuantity.trim();
    const unitPriceCent = this.data.optionUnitPriceCent.trim();
    const feeCent = this.data.optionFeeCent.trim();
    if (!cashAccount) {
      this.setData({errorMessage: "请先创建并选择启用中的现金账户"});
      return null;
    }
    if (!/^[0-9A-HJKMNP-TV-Z]{26}$/.test(instrumentId)) {
      this.setData({errorMessage: "请填写有效的 26 位期权合约标的 ID"});
      return null;
    }
    if (!/^[1-9][0-9]*(?:\.[0-9]{1,8})?$/.test(quantity)) {
      this.setData({errorMessage: "期权数量必须为正数，且最多保留 8 位小数"});
      return null;
    }
    if (transactionType !== "OPTION_EXPIRE" && !/^[1-9][0-9]*$/.test(unitPriceCent)) {
      this.setData({errorMessage: "期权权利金单价必须以最小货币单位填写正整数"});
      return null;
    }
    if (transactionType !== "OPTION_EXPIRE" && !/^(?:0|[1-9][0-9]*)$/.test(feeCent)) {
      this.setData({errorMessage: "手续费必须以最小货币单位填写非负整数"});
      return null;
    }
    const payload: OptionTransactionCreateRequest = {
      transactionType,
      occurredOn: this.data.optionOccurredOn,
      cashAccountId: cashAccount.accountId,
      instrumentId,
      quantity
    };
    if (transactionType === "OPTION_EXPIRE") {
      payload.expiryOutcome = "WORTHLESS";
    } else {
      payload.unitPriceCent = unitPriceCent;
      payload.feeCent = feeCent;
    }
    const note = this.data.optionNote.trim();
    if (note) {
      payload.note = note;
    }
    if (this.data.strategyKey) {
      payload.strategyKey = this.data.strategyKey;
    }
    return payload;
  },

  async submitOption() {
    const payload = this.optionPayload();
    if (!payload) {
      return;
    }
    if (this.data.correctionTargetTransactionId) {
      this.setData({errorMessage: "期权事实当前只支持审计冲正；请取消“更正并替代”后再录入新的期权事实。"});
      return;
    }
    const key = this.data.optionPendingIdempotencyKey || newIdempotencyKey();
    this.setData({
      optionSubmitting: true,
      optionSubmissionState: "SUBMITTING",
      optionPendingIdempotencyKey: key,
      errorMessage: ""
    });
    try {
      const transaction = await request<LedgerTransactionResponse>("/api/v1/ledger/transactions", "POST", payload,
        {"Idempotency-Key": key});
      this.setData({
        optionSubmitting: false,
        optionSubmissionState: "SUCCESS",
        optionPendingIdempotencyKey: "",
        optionPreviewPostings: [],
        optionAccountProvisioning: [],
        optionValidationWarnings: [],
        optionLastTransactionId: transaction.transactionId
      });
      wx.showToast({title: optionSuccessMessage(payload.transactionType), icon: "success"});
      void this.loadTransactions();
    } catch (error) {
      const problem = error as {message?: string};
      this.setData({
        optionSubmitting: false,
        optionSubmissionState: "RETRYABLE_ERROR",
        errorMessage: problem.message || "网络或服务异常，未自动重试。请确认后手动重试。"
      });
    }
  }
});

function today(): string {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${now.getFullYear()}-${month}-${day}`;
}

function formatMinorUnit(amountCent: string, currency: Currency): string {
  const normalized = amountCent.replace(/^0+(?=\d)/, "") || "0";
  const padded = normalized.length < 2 ? `0${normalized}` : normalized;
  const integer = padded.slice(0, -2) || "0";
  return `${currency} ${integer}.${padded.slice(-2)}`;
}

/** Query construction carries only filter strings; monetary values never leave the server as query parameters. */
function transactionQuery(data: LedgerPageData, cursor: string | null): string {
  const values: Array<[string, string]> = [["limit", "30"]];
  const accountId = data.transactionAccountFilterIds[data.transactionAccountFilterIndex];
  const transactionType = data.transactionTypeFilterValues[data.transactionTypeFilterIndex];
  const search = data.transactionSearch.trim();
  if (cursor) values.push(["cursor", cursor]);
  if (accountId) values.push(["accountId", accountId]);
  if (transactionType) values.push(["transactionType", transactionType]);
  if (search) values.push(["search", search]);
  if (data.transactionFrom) values.push(["from", data.transactionFrom]);
  if (data.transactionTo) values.push(["to", data.transactionTo]);
  return values.map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`).join("&");
}

function transactionTypeLabel(transactionType: string): string {
  const labels: Record<string, string> = {
    EXTERNAL_FUNDING: "外部入金",
    EXTERNAL_WITHDRAWAL: "外部出金",
    INTERNAL_TRANSFER: "账户内调拨",
    FEE: "独立费用",
    TRADE_BUY: "现货买入",
    TRADE_SELL: "现货卖出",
    DIVIDEND: "现金分红",
    INTEREST: "利息收入",
    CORPORATE_ACTION: "公司行为",
    FUTURES_MARGIN: "期货保证金",
    FUTURES_OPEN: "期货开仓",
    FUTURES_CLOSE: "期货平仓",
    FUTURES_DAILY_SETTLEMENT: "期货逐日结算",
    OPTION_OPEN: "期权买入开仓",
    OPTION_CLOSE: "期权卖出平仓",
    OPTION_EXPIRE: "期权到期无价值核销"
  };
  return labels[transactionType] || transactionType;
}

function isReplacementSupported(transactionType: string): boolean {
  return transactionType === "TRADE_BUY" || transactionType === "TRADE_SELL"
    || transactionType === "EXTERNAL_FUNDING" || transactionType === "EXTERNAL_WITHDRAWAL"
    || transactionType === "INTERNAL_TRANSFER" || transactionType === "FEE" || transactionType === "DIVIDEND"
    || transactionType === "INTEREST" || transactionType === "CORPORATE_ACTION";
}

function isStrategyKey(value: string): value is StrategyKey {
  return value === "HIGH_DIVIDEND" || value === "QQQ_GROWTH" || value === "IC_IM" || value === "DEEP_PUT";
}

function futuresConfirmationTitle(transactionType: FuturesTransactionCreateRequest["transactionType"]): string {
  if (transactionType === "FUTURES_OPEN") {
    return "确认期货开仓";
  }
  if (transactionType === "FUTURES_CLOSE") {
    return "确认期货平仓";
  }
  return "确认手工逐日结算";
}

function futuresConfirmationContent(transactionType: FuturesTransactionCreateRequest["transactionType"]): string {
  if (transactionType === "FUTURES_OPEN") {
    return "确认后会锁定可用保证金并追加不可修改的开仓事实。请核对合约、点位、保证金和手续费。";
  }
  if (transactionType === "FUTURES_CLOSE") {
    return "确认后会释放对应锁定保证金，并按上次结算点位确认已实现损益。请核对合约、点位和手续费。";
  }
  return "确认后会以填写的结算点位相对每个批次的上次结算点位确认损益，并更新结算基线。零损益也会追加不可变结算事实。";
}

function futuresConfirmationText(transactionType: FuturesTransactionCreateRequest["transactionType"]): string {
  return transactionType === "FUTURES_DAILY_SETTLEMENT" ? "确认结算"
    : transactionType === "FUTURES_OPEN" ? "确认开仓" : "确认平仓";
}

function futuresSuccessMessage(transactionType: FuturesTransactionCreateRequest["transactionType"]): string {
  return transactionType === "FUTURES_DAILY_SETTLEMENT" ? "期货逐日结算已记账"
    : transactionType === "FUTURES_OPEN" ? "期货开仓已记账" : "期货平仓已记账";
}

function optionConfirmationTitle(transactionType: OptionTransactionCreateRequest["transactionType"]): string {
  if (transactionType === "OPTION_OPEN") {
    return "确认期权买入开仓";
  }
  if (transactionType === "OPTION_CLOSE") {
    return "确认期权卖出平仓";
  }
  return "确认期权无价值到期核销";
}

function optionConfirmationContent(transactionType: OptionTransactionCreateRequest["transactionType"]): string {
  if (transactionType === "OPTION_OPEN") {
    return "确认后会以权利金和手续费建立不可修改的多头期权成本批次。请核对合约、数量和权利金。";
  }
  if (transactionType === "OPTION_CLOSE") {
    return "确认后会按 FIFO 消费已持有的多头期权批次，并确认已实现损益。请核对合约、数量、权利金和手续费。";
  }
  return "你确认该期权在到期日无行权价值。提交后将核销该合约全部剩余成本，且不能通过本操作行权或指派。";
}

function optionConfirmationText(transactionType: OptionTransactionCreateRequest["transactionType"]): string {
  return transactionType === "OPTION_EXPIRE" ? "确认无价值核销"
    : transactionType === "OPTION_OPEN" ? "确认开仓" : "确认平仓";
}

function optionSuccessMessage(transactionType: OptionTransactionCreateRequest["transactionType"]): string {
  return transactionType === "OPTION_EXPIRE" ? "期权无价值核销已记账"
    : transactionType === "OPTION_OPEN" ? "期权开仓已记账" : "期权平仓已记账";
}

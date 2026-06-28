#!/usr/bin/env node
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const appSource = readFileSync(resolve(root, "apps/dashboard/app.js"), "utf8");

function extractFunction(name) {
  const start = appSource.indexOf(`function ${name}(`);
  if (start < 0) throw new Error(`Missing function ${name}`);
  const bodyStart = appSource.indexOf("{", start);
  let depth = 0;
  let quote = "";
  let lineComment = false;
  let blockComment = false;
  for (let index = bodyStart; index < appSource.length; index += 1) {
    const char = appSource[index];
    const next = appSource[index + 1];
    const prev = appSource[index - 1];
    if (lineComment) {
      if (char === "\n") lineComment = false;
      continue;
    }
    if (blockComment) {
      if (char === "*" && next === "/") {
        blockComment = false;
        index += 1;
      }
      continue;
    }
    if (quote) {
      if (char === quote && prev !== "\\") quote = "";
      continue;
    }
    if (char === "/" && next === "/") {
      lineComment = true;
      index += 1;
      continue;
    }
    if (char === "/" && next === "*") {
      blockComment = true;
      index += 1;
      continue;
    }
    if (char === "\"" || char === "'" || char === "`") {
      quote = char;
      continue;
    }
    if (char === "{") depth += 1;
    if (char === "}") {
      depth -= 1;
      if (depth === 0) return appSource.slice(start, index + 1);
    }
  }
  throw new Error(`Unclosed function ${name}`);
}

const functions = [
  "safeAmount",
  "settingNumber",
  "normalizeSettings",
  "writeLocalStorage",
  "ratio",
  "gap",
  "classify",
  "targetProgressMeta",
  "quantityImpact",
  "entryBalanceImpact",
  "entryCashImpact",
  "entryMarginImpact",
  "entryFuturesNotionalImpact",
  "futuresProductFromSymbol",
  "futuresContractPrice",
  "futuresFrontContract",
  "futuresOneLotNotional",
  "futuresExposureReference",
  "deriveFuturesState",
  "futuresHoldingRows",
  "isCashPoolEntry",
  "isPositionEntry",
  "entryIncomeImpact",
  "positionKey",
  "optionalNumber",
  "enrichPosition",
  "buildPositionsFromEntries",
  "buildPositions",
  "sumPositionsBy",
  "sumBuckets",
  "sumModule",
  "summarizeLedger",
  "calculate",
  "futuresAccountRiskMeta",
  "futuresRiskActionText",
  "futuresStressLossForRows",
  "futuresAddLotCandidates",
  "detectStage",
  "weightedChildren",
  "defensiveChildren",
  "qqqChildren",
  "futuresChildren",
  "putChildren",
  "pushAllocation",
  "allocationPlanForAmount",
  "allocationPlan",
  "amountFromQuantityPrice",
  "externalFlowOnDate",
  "buildDailySnapshot",
  "normalizeDividendBucket",
  "normalizeEntry",
  "normalizeLedgerForStorage",
  "validateFullBackupPayload",
  "comparableBackupPayload",
  "backupHasData"
].map(extractFunction).join("\n\n");

const core = new Function(`
const defaultFuturesMultiplier = 200;
const defaultSettings = {
  annualExpense: 12,
  newMoney: 1,
  icPb: 50,
  imPb: 50,
  icPbSource: "manual",
  imPbSource: "manual",
  manualTotalAssets: 0
};
const balanceActions = {
  buy: 1,
  deposit: 1,
  internal_in: 1,
  margin: 1,
  sell: -1,
  withdraw: -1,
  internal_out: -1,
  fee: -1,
  expire: -1,
  futures_deposit: 1,
  dividend: 0,
  interest: 0,
  roll: 0
};
let __toasts = [];
let localStorage = { setItem() {} };
function showToast(message, type = "info") {
  __toasts.push({ message, type });
}
function setLocalStorage(storage) {
  localStorage = storage;
}
function takeToasts() {
  const next = __toasts;
  __toasts = [];
  return next;
}
function format(value) {
  if (Math.abs(value) >= 100) return value.toFixed(0);
  if (Math.abs(value) >= 10) return value.toFixed(1);
  return value.toFixed(2).replace(/\\.00$/, "");
}
function yuan(value) {
  if (!Number.isFinite(value)) return "-";
  return \`\${format(value)} 万\`;
}
let loadPositionValuations = () => ({});
let loadValuation = () => globalThis.__dashboardTestValuation || null;
let loadLedger = () => ({ entries: [], settings: { ...defaultSettings } });
let summarizeLedgerAtDate = () => globalThis.__dashboardTestSummary;
${functions}
return {
  ratio,
  writeLocalStorage,
  setLocalStorage,
  takeToasts,
  gap,
  classify,
  targetProgressMeta,
  summarizeLedger,
  calculate,
  detectStage,
  deriveFuturesState,
  futuresHoldingRows,
  futuresExposureReference,
  futuresAccountRiskMeta,
  futuresRiskActionText,
  futuresStressLossForRows,
  futuresAddLotCandidates,
  allocationPlanForAmount,
  amountFromQuantityPrice,
  externalFlowOnDate,
  buildDailySnapshot,
  validateFullBackupPayload,
  comparableBackupPayload,
  backupHasData
};
`)();

assert.equal(core.ratio(5, 0), 0, "ratio keeps zero denominator stable");
assert.equal(core.gap(10, 12), 0, "gap floors at zero");
assert.equal(core.targetProgressMeta(4, 6).label, "差 2 万", "target marker explains remaining gap for minimum targets");
assert.equal(core.targetProgressMeta(8, 6).label, "达标", "target marker marks achieved minimum targets");
assert.equal(core.targetProgressMeta(8, 6, "max").label, "超 2 万", "target marker handles maximum risk limits");
assert.equal(core.targetProgressMeta(4, 6, "max").label, "安全", "target marker marks safe maximum risk limits");
assert.equal(core.amountFromQuantityPrice(1000, 50), 5, "amount = quantity * price / 10000");
assert.equal(core.amountFromQuantityPrice(1, 8600, 200), 172, "futures notional includes multiplier");
assert.equal(core.amountFromQuantityPrice(0, 50), null, "invalid amount input returns null");
assert.equal(core.writeLocalStorage("ok", "1", "测试数据"), true, "writeLocalStorage returns true on success");
core.setLocalStorage({ setItem() { throw new Error("quota"); } });
const originalConsoleError = console.error;
console.error = () => {};
try {
  assert.equal(core.writeLocalStorage("fail", "1", "测试数据"), false, "writeLocalStorage returns false on failure");
} finally {
  console.error = originalConsoleError;
}
const storageToasts = core.takeToasts();
assert.equal(storageToasts.at(-1).type, "error", "write failure emits error toast");
assert.match(storageToasts.at(-1).message, /写入失败/, "write failure explains persistence failure");

const ledger = {
  settings: { annualExpense: 12, futuresEquity: 0, usedMargin: 10, icPb: 25, imPb: 18, hasIc: true },
  entries: [
    { module: "cash", bucket: "现金池", action: "deposit", symbol: "", amount: 6, date: "2026-06-27" },
    { module: "dividend", bucket: "高分红股票", action: "buy", symbol: "000858", name: "五粮液", quantity: 1500, price: 160, amount: 24, fee: 0, date: "2026-06-27" },
    { module: "dividend", bucket: "高分红股票", action: "dividend", symbol: "000858", name: "五粮液", amount: 1, fee: 0, date: "2026-06-27" },
    { module: "dividend", bucket: "债券", action: "interest", symbol: "019547", amount: 0.8, date: "2026-06-27" }
  ]
};
const summary = core.summarizeLedger(ledger);
assert.equal(summary.cashBalance, -16.2, "cash pool reflects deposit, buy, dividend, and interest cash movements");
assert.equal(Number(summary.totalAssets.toFixed(2)), 6.8, "summarizeLedger includes global cash and ex-dividend cost basis");
assert.equal(summary.moduleTotals.dividend, 23, "dividend reduces position cost basis");
assert.equal(summary.usedMargin, 0, "legacy futures settings no longer feed derived margin state");
assert.equal(summary.futuresEquity, 0, "legacy futures settings no longer feed derived equity state");
assert.equal(summary.hasIc, false, "IC底仓状态 is derived from open futures lots");

globalThis.__dashboardTestValuation = {
  indexes: {
    IC: {
      basis: {
        contracts: [{ contract: "IC2607", future: 8700 }]
      }
    },
    IM: {
      basis: {
        contracts: [{ contract: "IM2607", future: 8600 }]
      }
    }
  }
};
const exposureReference = core.futuresExposureReference(globalThis.__dashboardTestValuation);
assert.equal(exposureReference.ic, 174, "1-lot IC reference exposure uses latest futures price");
assert.equal(exposureReference.im, 172, "1-lot IM reference exposure uses latest futures price");
assert.equal(exposureReference.icIm, 346, "IC+IM reference exposure is a reference line, not a target");

const futuresSummary = core.summarizeLedger({
  settings: { ...ledger.settings, futuresEquity: 55, usedMargin: 0 },
  entries: [
    ...ledger.entries,
    { module: "ic", bucket: "IC/IM资金池", action: "futures_deposit", symbol: "期货资金池", name: "专项资金", amount: 55, fee: 0, date: "2026-06-27" },
    { module: "ic", bucket: "IC", action: "buy", symbol: "IC2607", name: "中证500股指期货", quantity: 1, price: 8600, multiplier: 200, amount: 172, margin: 20.64, fee: 0.02, date: "2026-06-27" }
  ]
});
assert.equal(Number(futuresSummary.usedMargin.toFixed(2)), 20.88, "IC/IM used margin is marked from current notional and recorded margin rate");
assert.equal(futuresSummary.futuresNotional, 174, "IC/IM notional comes from latest daily futures price");
assert.equal(Number(futuresSummary.futuresEquity.toFixed(2)), 56.98, "futures equity is funding plus mark-to-market PnL minus fees");
assert.equal(Number(futuresSummary.futuresState.totalPnl.toFixed(2)), 2, "daily mark-to-market PnL is derived from current futures price");
assert.equal(Number(futuresSummary.futuresState.fees.toFixed(2)), 0.02, "IC/IM trading fees reduce futures account equity");
assert.equal(Number(futuresSummary.cashBalance.toFixed(2)), -71.2, "IC/IM notional and trading fees do not consume global cash after funding transfer");
assert.equal(Number(futuresSummary.totalAssets.toFixed(2)), 8.78, "IC/IM mark-to-market PnL enters total assets, not notional exposure");
assert.equal(futuresSummary.moduleTotals.ic, 55, "IC/IM account funding stays in asset distribution while notional stays out");
assert.equal(futuresSummary.hasIc, true, "IC底仓状态 is derived from open IC lots");
const futuresRows = core.futuresHoldingRows(futuresSummary);
assert.equal(futuresRows.length, 1, "IC/IM holding analysis shows open contracts, not cash-pool pseudo positions");
assert.equal(futuresRows[0].symbol, "IC2607", "futures holding row keeps contract symbol");
assert.equal(futuresRows[0].quantity, 1, "futures holding row keeps net lots");
assert.equal(futuresRows[0].currentNotional, 174, "futures holding row shows daily marked notional");
assert.equal(Number(futuresRows[0].marginRate.toFixed(2)), 12, "futures holding row shows margin rate");

globalThis.__dashboardTestValuation = null;
const noEquityFuturesSummary = core.summarizeLedger({
  settings: ledger.settings,
  entries: [
    { module: "ic", bucket: "IC", action: "buy", symbol: "IC2607", name: "中证500股指期货", quantity: 1, price: 8600, multiplier: 200, amount: 172, margin: 20.64, fee: 0, date: "2026-06-27" }
  ]
});
const calc = core.calculate(noEquityFuturesSummary);
assert.equal(calc.marginRisk, Infinity, "margin risk is extreme when used margin exists and equity is zero");
assert.equal(calc.marginRiskInvalid, true, "margin risk invalid flag is set");
assert.equal(core.classify(calc.marginRisk, 50, 70, true), "status-danger", "invalid margin risk is danger");

const safeCalc = core.calculate({ ...summary, usedMargin: 10, futuresEquity: 100 });
assert.equal(safeCalc.marginRisk, 10, "normal margin risk remains percentage");
const safeRiskMeta = core.futuresAccountRiskMeta({ ...summary, usedMargin: 10, futuresEquity: 100 }, safeCalc);
assert.equal(safeRiskMeta.statusText, "安全区", "futures account structure reports risk status instead of margin target");
assert.equal(safeRiskMeta.topUpToWatch, 0, "safe futures risk has no equity top-up gap");
assert.doesNotMatch(core.futuresRiskActionText(safeRiskMeta), /目标|超/, "used margin action text avoids target/excess wording");
const dangerCalc = core.calculate({ ...summary, usedMargin: 80, futuresEquity: 100 });
const dangerRiskMeta = core.futuresAccountRiskMeta({ ...summary, usedMargin: 80, futuresEquity: 100 }, dangerCalc);
assert.equal(dangerRiskMeta.statusText, "危险区", "futures risk above 70% is marked as danger");
assert.equal(Number(dangerRiskMeta.topUpToWatch.toFixed(2)), 45.45, "top-up gap is derived from used margin / 55% risk line");
assert.match(core.futuresRiskActionText(dangerRiskMeta), /需补权益/, "unsafe futures risk reports equity top-up, not margin excess");

globalThis.__dashboardTestValuation = {
  indexes: {
    IC: { basis: { contracts: [{ contract: "IC2607", future: 4221.64 }] } },
    IM: { basis: { contracts: [{ contract: "IM2607", future: 4180.97 }] } }
  }
};
const twiceStressedFutures = {
  ...summary,
  futuresEquity: 12.1008,
  usedMargin: 10.131936,
  futuresNotional: 84.4328,
  futuresState: {
    openPositions: [{
      symbol: "IC2607",
      product: "IC",
      quantity: 1,
      avgPrice: 8615.6,
      currentPrice: 4221.64,
      multiplier: 200,
      currentNotional: 84.4328,
      usedMargin: 10.131936,
      unrealizedPnl: -87.8792,
      priceSource: "valuation"
    }]
  }
};
const addOneCandidates = core.futuresAddLotCandidates(twiceStressedFutures, core.calculate(twiceStressedFutures));
const addIc = addOneCandidates.find((item) => item.product === "IC");
const addIm = addOneCandidates.find((item) => item.product === "IM");
assert.equal(Number(addIc.riskNoTopUp.toFixed(1)), 167.5, "adding one IC without top-up shows immediate risk blow-up");
assert.equal(Number(addIm.riskNoTopUp.toFixed(1)), 166.7, "adding one IM without top-up shows immediate risk blow-up");
assert.equal(Number(addIc.topUpToWatch.toFixed(2)), 24.74, "adding one IC reports current 55% top-up");
assert.equal(Number(addIm.topUpToWatch.toFixed(2)), 24.57, "adding one IM reports current 55% top-up");
assert.equal(Number(addIc.recommendedTopUp.toFixed(2)), 44.83, "recommended IC top-up keeps risk <=70% after another 20% drop");
assert.equal(Number(addIm.recommendedTopUp.toFixed(2)), 44.56, "recommended IM top-up keeps risk <=70% after another 20% drop");
globalThis.__dashboardTestValuation = null;

const allocationData = {
  ...summary,
  totalAssets: 100,
  annualExpense: 12,
  hardCash: 6,
  reverseRepo: 0,
  highDividend: 10,
  qqq: 5,
  futuresPool: 0,
  spyPutBudget: 0,
  usedMargin: 0,
  futuresEquity: 0
};
const allocationCalc = core.calculate(allocationData);
const allocation = core.allocationPlanForAmount(allocationData, allocationCalc, 10);
assert.equal(allocation[0].name, "高分红 / 防守现金流", "cash allocation starts with defensive cashflow bucket when liquidity gap exists");
assert.equal(allocation[0].children[0].name, "国债逆回购 / 短债", "aggressive defensive split still keeps a bond sleeve");
assert.equal(Number(allocation[0].children[0].amount.toFixed(2)), 1.8, "liquidity phase uses 30% bond sleeve");
assert.equal(Number(allocation[0].children[1].amount.toFixed(2)), 4.2, "liquidity phase uses 70% high dividend stocks");

const stage0Calc = core.calculate({ ...summary, hardCash: 1, reverseRepo: 0 });
assert.equal(core.detectStage({ ...summary, hardCash: 1, reverseRepo: 0 }, stage0Calc).id, 0, "stage 0 requires hard cash first");
assert.equal(core.externalFlowOnDate([
  { date: "2026-06-27", action: "deposit", amount: 10 },
  { date: "2026-06-27", action: "withdraw", amount: 3 },
  { date: "2026-06-27", action: "internal_in", amount: 99 }
], "2026-06-27"), 7, "external flow excludes internal transfers");

globalThis.__dashboardTestSummary = {
  totalAssets: 110,
  moduleTotals: { dividend: 110 },
  positions: []
};
const snapshot = core.buildDailySnapshot(
  "2026-06-27",
  { entries: [{ date: "2026-06-27", action: "deposit", amount: 5 }] },
  {},
  { totalAssets: 100, nav: 1, positions: [] }
);
assert.equal(snapshot.dailyReturn, 0.05, "daily return strips external cash flow");
assert.equal(snapshot.nav, 1.05, "NAV compounds daily return");

// --- 多端云同步：备份校验 + 冲突判定纯函数 ---
const sampleBackup = {
  ledger: {
    entries: [{ module: "dividend", bucket: "白酒", action: "buy", symbol: "000858", amount: 24 }],
    settings: { annualExpense: 12 }
  },
  positionValuations: { "000858": { price: 160 } },
  history: [{ date: "2026-06-27", nav: 1 }],
  valuation: { ic: { pb: 25 } },
  historyView: "week"
};

const normalizedBackup = core.validateFullBackupPayload(sampleBackup);
assert.equal(normalizedBackup.ledger.entries[0].bucket, "高分红股票", "validateFullBackupPayload normalizes dividend buckets");
assert.equal(normalizedBackup.ledger.settings.annualExpense, 12, "validateFullBackupPayload keeps provided settings");
assert.equal(normalizedBackup.ledger.settings.icPb, 50, "validateFullBackupPayload fills default settings");
assert.equal(normalizedBackup.historyView, "week", "validateFullBackupPayload keeps valid historyView");
const strippedSettings = core.validateFullBackupPayload({ ledger: { entries: [], settings: { annualExpense: 12, futuresEquity: 99, usedMargin: 88, hasIc: true } } }).ledger.settings;
assert.equal("futuresEquity" in strippedSettings, false, "futures equity is not persisted as a core setting");
assert.equal("usedMargin" in strippedSettings, false, "used margin is not persisted as a core setting");
assert.equal("hasIc" in strippedSettings, false, "IC position state is not persisted as a core setting");

assert.equal(core.validateFullBackupPayload({ ledger: { entries: [], settings: {} }, historyView: "year" }).historyView, "day", "invalid historyView falls back to day");
assert.deepEqual(core.validateFullBackupPayload({ ledger: { entries: [], settings: {} } }).positionValuations, {}, "missing positionValuations defaults to empty object");
assert.throws(() => core.validateFullBackupPayload(null), /JSON 对象/, "null payload is rejected");
assert.throws(() => core.validateFullBackupPayload({}), /缺少 ledger/, "missing ledger is rejected");
assert.throws(() => core.validateFullBackupPayload({ ledger: { entries: {}, settings: {} } }), /entries 必须是数组/, "non-array entries rejected");
assert.throws(() => core.validateFullBackupPayload({ ledger: { entries: [], settings: {} }, positionValuations: [] }), /positionValuations 必须是对象/, "array positionValuations rejected");
assert.throws(() => core.validateFullBackupPayload({ ledger: { entries: [], settings: {} }, history: {} }), /history 必须是数组/, "non-array history rejected");

// comparableBackupPayload 是冲突检测 same 判定的基础：同语义 → 同串，异语义 → 异串
const comparableA = core.comparableBackupPayload(sampleBackup);
const reorderedSettings = { ...sampleBackup, ledger: { ...sampleBackup.ledger, settings: { annualExpense: 12 } } };
assert.equal(core.comparableBackupPayload(reorderedSettings), comparableA, "comparable payload is stable for equivalent data");
const mutatedBackup = { ...sampleBackup, ledger: { ...sampleBackup.ledger, entries: [...sampleBackup.ledger.entries, { module: "cash", action: "deposit", amount: 5 }] } };
assert.notEqual(core.comparableBackupPayload(mutatedBackup), comparableA, "comparable payload changes when entries differ");

// backupHasData 是 clean(单侧) vs conflict(双侧) 判定的基础
const emptyBackup = { ledger: { entries: [], settings: {} }, positionValuations: {}, history: [], valuation: null };
assert.equal(core.backupHasData(emptyBackup), false, "empty backup has no data");
assert.equal(core.backupHasData({ ...emptyBackup, ledger: { entries: [{ action: "deposit", amount: 1 }], settings: {} } }), true, "entries count as data");
assert.equal(core.backupHasData({ ...emptyBackup, positionValuations: { x: {} } }), true, "position valuations count as data");
assert.equal(core.backupHasData({ ...emptyBackup, history: [{ date: "2026-06-27" }] }), true, "history counts as data");
assert.equal(core.backupHasData({ ...emptyBackup, valuation: { ic: {} } }), true, "valuation counts as data");

// 冲突判定真值表（对应 app.js scheduleRemoteSyncCheck 的 status 推导）
function syncStatus(localPayload, remotePayload) {
  const same = core.comparableBackupPayload(localPayload) === core.comparableBackupPayload(remotePayload);
  if (same) return "clean";
  const localHasData = core.backupHasData(localPayload);
  const remoteHasData = core.backupHasData(remotePayload);
  return localHasData && remoteHasData ? "conflict" : "clean";
}
assert.equal(syncStatus(sampleBackup, sampleBackup), "clean", "identical local/remote is clean");
assert.equal(syncStatus(sampleBackup, mutatedBackup), "conflict", "both sides with differing data is conflict");
assert.equal(syncStatus(emptyBackup, sampleBackup), "clean", "one-sided data is not a conflict");
assert.equal(syncStatus(sampleBackup, emptyBackup), "clean", "one-sided data is not a conflict (reverse)");

console.log("dashboard core tests passed");

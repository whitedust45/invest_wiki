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
  "writeLocalStorage",
  "ratio",
  "gap",
  "classify",
  "quantityImpact",
  "entryBalanceImpact",
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
  "detectStage",
  "amountFromQuantityPrice",
  "externalFlowOnDate",
  "buildDailySnapshot"
].map(extractFunction).join("\n\n");

const core = new Function(`
const defaultSettings = {
  annualExpense: 12,
  newMoney: 1,
  icPb: 50,
  imPb: 50,
  icPbSource: "manual",
  imPbSource: "manual",
  hasIc: false,
  futuresEquity: 0,
  usedMargin: 0,
  futuresNotional: 0,
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
let loadPositionValuations = () => ({});
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
  summarizeLedger,
  calculate,
  detectStage,
  amountFromQuantityPrice,
  externalFlowOnDate,
  buildDailySnapshot
};
`)();

assert.equal(core.ratio(5, 0), 0, "ratio keeps zero denominator stable");
assert.equal(core.gap(10, 12), 0, "gap floors at zero");
assert.equal(core.amountFromQuantityPrice(1000, 50), 5, "amount = quantity * price / 10000");
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
    { module: "dividend", bucket: "现金", action: "deposit", symbol: "现金", amount: 6, date: "2026-06-27" },
    { module: "dividend", bucket: "高分红股票", action: "buy", symbol: "000858", name: "五粮液", quantity: 1500, price: 160, amount: 24, fee: 0, date: "2026-06-27" },
    { module: "dividend", bucket: "债券", action: "interest", symbol: "019547", amount: 0.8, date: "2026-06-27" }
  ]
};
const summary = core.summarizeLedger(ledger);
assert.equal(summary.totalAssets, 30, "summarizeLedger totals market values by bucket");
assert.equal(summary.moduleTotals.dividend, 30, "summarizeLedger totals by module");
assert.equal(summary.usedMargin, 10, "settings are preserved in summary");

const calc = core.calculate(summary);
assert.equal(calc.marginRisk, Infinity, "margin risk is extreme when used margin exists and equity is zero");
assert.equal(calc.marginRiskInvalid, true, "margin risk invalid flag is set");
assert.equal(core.classify(calc.marginRisk, 50, 70, true), "status-danger", "invalid margin risk is danger");

const safeCalc = core.calculate({ ...summary, usedMargin: 10, futuresEquity: 100 });
assert.equal(safeCalc.marginRisk, 10, "normal margin risk remains percentage");

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

console.log("dashboard core tests passed");

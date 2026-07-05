const legacyStorageKey = "hybrid-barbell-dashboard-v1";
const historyKey = "hybrid-barbell-dashboard-history-v1";
const valuationKey = "hybrid-barbell-dashboard-valuation-v1";
const ledgerKey = "hybrid-barbell-dashboard-ledger-v1";
const positionValuationKey = "hybrid-barbell-dashboard-position-valuation-v1";
const historyViewKey = "hybrid-barbell-dashboard-history-view-v1";
const ledgerBackupPromptKey = "hybrid-barbell-dashboard-ledger-backup-prompted-v1";
const syncAccessKeyKey = "hybrid-barbell-dashboard-sync-access-key-v1";
const syncLastShaKey = "hybrid-barbell-dashboard-sync-last-sha-v1";
const syncLastComparableKey = "hybrid-barbell-dashboard-sync-last-comparable-v1";
const ledgerApiPath = "/api/ledger";
const remoteSyncStatePath = "/api/sync/data/dashboardState";

const chartColors = ["#0f6a7a", "#c1742f", "#2f6b45", "#8b4f7d", "#b9493c", "#59616d", "#d2a23a"];

const moduleConfigs = {
  overview: {
    title: "总览"
  },
  reports: {
    title: "报表"
  },
  cash: {
    title: "现金池"
  },
  dividend: {
    title: "高分红",
    module: "dividend",
    description: "记录现金、高分红股票、类现金和债券，核心看生活现金流覆盖。",
    buckets: ["现金", "高分红股票", "类现金", "债券"],
    primaryMetric: "现金流底座",
    accent: "income",
    icon: "income",
    entryTitle: "记录一笔现金流资产",
    symbolPlaceholder: "如 000568 / 000858 / 红利ETF / 现金",
    notePlaceholder: "股息来源、买入理由、资产类别和仓位纪律",
    focus: ["股息覆盖", "6-12个月现金垫", "高分红股票与A股同源风险"]
  },
  qqq: {
    title: "QQQ",
    module: "qqq",
    description: "记录 QQQ 和 QLD，核心看 5% 起步线、10% 目标线与 120 日均线策略。",
    buckets: ["QQQ", "QLD", "现金等待", "再平衡"],
    primaryMetric: "右尾成长仓",
    accent: "growth",
    icon: "trend",
    entryTitle: "记录一笔 QQQ / QLD",
    symbolPlaceholder: "如 QQQ / QLD",
    notePlaceholder: "买入位置、120日均线状态、再平衡原因",
    focus: ["当前总资产 5% 起步", "10%-12% 目标区", "QLD 只做趋势策略"]
  },
  put: {
    title: "深度Put",
    module: "put",
    description: "记录 SPY 深度虚值 Put，核心看年度保险预算、到期梯度和保费消耗。",
    buckets: ["SPY put", "现金等待", "保险预算", "到期归零"],
    primaryMetric: "黑天鹅保险",
    accent: "protection",
    icon: "shield",
    entryTitle: "记录一笔深度虚值 Put",
    symbolPlaceholder: "如 SPY 2027-12 P300",
    notePlaceholder: "行权价、到期日、Delta、保险目的",
    focus: ["年度预算 0.5%-2%", "深度虚值不摊大饼", "归零视为保险成本"]
  },
  ic: {
    title: "IC/IM",
    module: "ic",
    description: "记录 IC/IM 资金池、保证金、合约和移仓，核心看 PB 分位与爆仓压力。",
    buckets: ["IC/IM资金池", "IC", "IM", "保证金", "移仓", "补资"],
    primaryMetric: "股指期货增强",
    accent: "futures",
    icon: "futures",
    entryTitle: "记录一笔 IC/IM 动作",
    symbolPlaceholder: "如 IC2609 / IM2609",
    notePlaceholder: "PB分位、贴水、保证金、移仓说明",
    focus: ["资金池 100 万起步", "PB<=30 才评估第一手 IC", "风险度与移仓日提醒"]
  }
};

const instrumentPresetCandidates = {
  dividend: [
    { symbol: "000568", name: "泸州老窖", market: "A股", category: "核心质量现金流" },
    { symbol: "000858", name: "五粮液", market: "A股", category: "核心质量现金流" },
    { symbol: "600036", name: "招商银行", market: "A股", category: "核心质量现金流" },
    { symbol: "HK:03968", name: "招商银行", market: "港股", category: "核心质量现金流" },
    { symbol: "601318", name: "中国平安", market: "A股", category: "核心质量现金流" },
    { symbol: "HK:02318", name: "中国平安", market: "港股", category: "核心质量现金流" },
    { symbol: "600941", name: "中国移动", market: "A股", category: "核心质量现金流" },
    { symbol: "HK:00941", name: "中国移动", market: "港股", category: "核心质量现金流" },
    { symbol: "600938", name: "中国海油", market: "A股", category: "核心质量现金流" },
    { symbol: "HK:00883", name: "中国海洋石油", market: "港股", category: "核心质量现金流" },
    { symbol: "600690", name: "海尔智家", market: "A股", category: "质量型消费制造" },
    { symbol: "HK:06690", name: "海尔智家", market: "港股", category: "质量型消费制造" },
    { symbol: "000333", name: "美的集团", market: "A股", category: "质量型消费制造" },
    { symbol: "HK:00300", name: "美的集团", market: "港股", category: "质量型消费制造" },
    { symbol: "601225", name: "陕西煤业", market: "A股", category: "周期/资源现金流" },
    { symbol: "601668", name: "中国建筑", market: "A股", category: "周期/工程现金流" },
    { symbol: "600153", name: "建发股份", market: "A股", category: "周期/供应链现金流" },
    { symbol: "600887", name: "伊利股份", market: "A股", category: "消费/贸易现金流" },
    { symbol: "600177", name: "雅戈尔", market: "A股", category: "消费/贸易现金流" },
    { symbol: "002091", name: "江苏国泰", market: "A股", category: "消费/贸易现金流" },
    { symbol: "002818", name: "富森美", market: "A股", category: "消费/资产现金流" },
    { symbol: "HK:00506", name: "中国食品", market: "港股", category: "港股高息/特殊资产" },
    { symbol: "HK:06049", name: "保利物业", market: "港股", category: "港股高息/特殊资产" },
    { symbol: "HK:00882", name: "天津发展", market: "港股", category: "港股高息/特殊资产" },
    { symbol: "HK:01601", name: "中关村科技租赁", market: "港股", category: "港股高息/特殊资产" },
    { symbol: "B:900905", name: "老凤祥B", market: "B股", category: "B股折价池" },
    { symbol: "B:900948", name: "伊泰B股", market: "B股", category: "B股折价池" },
    { symbol: "B:200429", name: "粤高速B", market: "B股", category: "B股折价池" }
  ],
  qqq: [
    { symbol: "QQQ", name: "Invesco QQQ Trust", market: "美股", category: "右尾成长仓" },
    { symbol: "QLD", name: "ProShares Ultra QQQ", market: "美股", category: "120日均线策略" }
  ],
  put: [
    { symbol: "SPY", name: "SPDR S&P 500 ETF Trust", market: "美股", category: "深度Put底层标的" }
  ]
};

const actionLabels = {
  buy: "买入",
  sell: "卖出",
  dividend: "分红",
  interest: "利息",
  deposit: "转入",
  withdraw: "转出",
  futures_deposit: "入金/补资",
  expire: "到期归零",
  internal_in: "内部划入",
  internal_out: "内部划出",
  fee: "费用",
  margin: "保证金",
  roll: "移仓"
};

const moduleActions = {
  dividend: ["buy", "sell", "dividend", "interest"],
  qqq: ["buy", "sell", "dividend"],
  put: ["buy", "sell", "expire"],
  ic: ["futures_deposit", "buy", "sell", "margin", "roll"]
};

const moduleActionLabels = {
  ic: {
    buy: "开仓/加仓",
    sell: "平仓/减仓",
    futures_deposit: "入金/补资",
    margin: "保证金调整",
    roll: "移仓"
  },
  put: {
    sell: "卖出/平仓",
    expire: "到期归零"
  }
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

const defaultFuturesMultiplier = 200;
const ledgerPageSize = 10;

const defaultSettings = {
  annualExpense: 12,
  newMoney: 1,
  icPb: 50,
  imPb: 50,
  icPbSource: "manual",
  imPbSource: "manual",
  manualTotalAssets: 0
};

let currentTab = "overview";
let currentSubpage = "full";
let editingId = null;
let dailyHistorySyncing = false;
const ledgerFilters = {};
let ledgerMirrorTimer = null;
let pendingLedgerMirror = null;
let ledgerBackupState = {
  status: "idle",
  backups: [],
  message: "尚未读取 SQLite 备份状态"
};
let localServiceGuideVisible = window.location.protocol === "file:";
let localServiceGuideReason = window.location.protocol === "file:" ? "file" : "";
let localServiceGuideDismissed = false;
let localPositionHistoryCache = null;
let remoteSyncTimer = null;
let remoteSyncInFlight = false;
let remoteSyncState = {
  status: "idle",
  message: "尚未检查云同步",
  remotePayload: null,
  sha: null,
  checkedAt: null
};

const appRoot = document.querySelector("#appRoot");
const pageTitle = document.querySelector("#pageTitle");

const dashboardServiceCommand = "python3 services/sync/src/main.py";
const positionQuotesCommand = "python3 tools/dashboard/update_position_quotes.py 000858 000568 600887 600153 600036 002818 002091 601668 600177 600873 601318 600938 600941 601225 000651 600690 512890 520890 159545 513630 159117 QQQ QLD SPY";
const dashboardServiceBaseUrl = "http://127.0.0.1:8775/apps/dashboard/";

const subpageLabels = {
  overview: {
    full: "全部总览",
    snapshot: "净值快照",
    cash: "现金池",
    risk: "账户风险",
    settings: "核心参数",
    charts: "图表追踪",
    actions: "缺口动作"
  },
  reports: {
    full: "全部总览",
    charts: "图表总览",
    matrix: "策略矩阵",
    leaderboard: "标的排行",
    valuation: "估值总表",
    activity: "最近流水"
  },
  module: {
    full: "全部总览",
    overview: "模块概览",
    entry: "记录录入",
    buckets: "持仓分布",
    add: "加仓测算",
    valuation: "持仓估值",
    ledger: "投资流水"
  }
};

function today() {
  return new Date().toISOString().slice(0, 10);
}

function parseDate(value) {
  const match = String(value || "").match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (!match) return null;
  return new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
}

function formatDate(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function addDays(value, days) {
  const date = typeof value === "string" ? parseDate(value) : new Date(value);
  if (!date) return today();
  date.setDate(date.getDate() + days);
  return formatDate(date);
}

function compareDate(a, b) {
  return String(a || "").localeCompare(String(b || ""));
}

function dateRange(start, end) {
  const startDate = parseDate(start);
  const endDate = parseDate(end);
  if (!startDate || !endDate || startDate > endDate) return [];
  const result = [];
  const cursor = new Date(startDate);
  while (cursor <= endDate) {
    result.push(formatDate(cursor));
    cursor.setDate(cursor.getDate() + 1);
  }
  return result;
}

function makeId() {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

function yuan(value) {
  if (!Number.isFinite(value)) return "-";
  return `${format(value)} 万`;
}

function wanFromYuan(value) {
  const num = Number(value);
  return Number.isFinite(num) ? num / 10000 : 0;
}

function yuanFromWan(value) {
  return safeAmount(value) * 10000;
}

function yuanText(value) {
  if (!Number.isFinite(value)) return "-";
  return `${chartValue(value)} 元`;
}

function yuanTextFromWan(value) {
  return yuanText(yuanFromWan(value));
}

function pct(value) {
  if (!Number.isFinite(value)) return "-";
  return `${format(value)}%`;
}

function format(value) {
  if (Math.abs(value) >= 100) return value.toFixed(0);
  if (Math.abs(value) >= 10) return value.toFixed(1);
  return value.toFixed(2).replace(/\.00$/, "");
}

function chartValue(value) {
  if (!Number.isFinite(value)) return "0";
  if (Math.abs(value) >= 100) return value.toFixed(0);
  if (Math.abs(value) >= 10) return value.toFixed(1);
  return value.toFixed(2);
}

function displayNumber(value) {
  if (value === null || value === undefined || value === "" || !Number.isFinite(Number(value))) return "-";
  return chartValue(Number(value));
}

function displayTradeDate(value) {
  const text = String(value || "");
  if (/^\d{8}$/.test(text)) return `${text.slice(0, 4)}-${text.slice(4, 6)}-${text.slice(6, 8)}`;
  return text || "-";
}

function displayDateTime(value) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleString("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  });
}

function showToast(message, type = "info") {
  let host = document.querySelector("#toastHost");
  if (!host) {
    host = document.createElement("div");
    host.id = "toastHost";
    host.className = "toast-host";
    document.body.appendChild(host);
  }
  const item = document.createElement("div");
  item.className = `toast toast-${type}`;
  item.textContent = message;
  host.appendChild(item);
  requestAnimationFrame(() => item.classList.add("is-visible"));
  window.setTimeout(() => {
    item.classList.remove("is-visible");
    window.setTimeout(() => item.remove(), 180);
  }, 2400);
}

function confirmDanger(message) {
  return window.confirm(message);
}

function writeLocalStorage(key, value, label = "本地数据") {
  try {
    localStorage.setItem(key, value);
    return true;
  } catch (error) {
    console.error(`Failed to write ${key}`, error);
    showToast(`写入失败：${label} 未保存。存储空间不足或被浏览器限制，请立即导出全部数据。`, "error");
    return false;
  }
}

function dashboardServiceUrl() {
  const entry = isDesktopMode() ? "index-desktop.html" : "index.html";
  const currentOriginLooksUsable = ledgerServiceAvailable()
    && window.location.origin
    && (window.location.port === "8775" || ledgerBackupState.status === "ready");
  if (currentOriginLooksUsable) {
    return `${window.location.origin}/apps/dashboard/${entry}`;
  }
  return `${dashboardServiceBaseUrl}${entry}`;
}

function fileMode() {
  return window.location.protocol === "file:";
}

function localServiceGuideTitle() {
  if (fileMode()) return "当前是 file:// 直开";
  if (localServiceGuideReason === "valuation") return "IC/IM 估值读取失败";
  if (localServiceGuideReason === "quotes") return "价格 JSON 读取失败";
  return "本地服务启动引导";
}

function localServiceGuidePanel() {
  if (localServiceGuideDismissed) return "";
  if (!localServiceGuideVisible && !fileMode()) return "";
  const serviceUrl = dashboardServiceUrl();
  const detail = fileMode()
    ? "浏览器直开本地 HTML 时，API 与部分 JSON 读取可能被限制。请在项目根目录启动本地服务后，从 http://127.0.0.1:8775 访问页面。"
    : "本地数据读取失败时，先启动服务或更新 JSON，再回到页面重新读取。浏览器不能直接替你启动 Python 进程，因此这里提供可复制命令。";
  return `
    <section class="panel local-service-guide" id="localServiceGuide">
      <div class="section-head">
        <h2>${escapeHtml(localServiceGuideTitle())}</h2>
        <span>本地服务 / JSON 兜底</span>
      </div>
      <p>${escapeHtml(detail)}</p>
      <div class="service-guide-grid">
        <article>
          <strong>1. 启动本地服务</strong>
          <code>${escapeHtml(dashboardServiceCommand)}</code>
          <button type="button" data-action="copy-service-command">复制启动命令</button>
        </article>
        <article>
          <strong>2. 打开本地入口</strong>
          <code>${escapeHtml(serviceUrl)}</code>
          <div class="service-guide-actions">
            <button type="button" data-action="open-service-url">打开入口</button>
            <button type="button" data-action="copy-service-url">复制 URL</button>
          </div>
        </article>
        <article>
          <strong>3. 更新价格 JSON</strong>
          <code>${escapeHtml(positionQuotesCommand)}</code>
          <button type="button" data-action="copy-price-command">复制价格命令</button>
        </article>
      </div>
      <p class="notice">IC/IM 估值可由服务自动刷新；持仓价格 JSON 需要先运行价格脚本。若暂时不启动服务，可回到“总览 - IC/IM 估值”手工导入 JSON。</p>
      <div class="inline-actions">
        <button type="button" data-tab="overview" data-subpage="full">去导入 JSON</button>
        <button type="button" data-action="dismiss-service-guide">暂时收起</button>
      </div>
    </section>
  `;
}

function renderLocalServiceGuide() {
  const existing = document.querySelector("#localServiceGuide");
  if (existing) existing.remove();
  const html = localServiceGuidePanel();
  if (html) appRoot.insertAdjacentHTML("afterbegin", html);
}

function showLocalServiceGuide(reason) {
  localServiceGuideVisible = true;
  localServiceGuideReason = reason || localServiceGuideReason || "manual";
  localServiceGuideDismissed = false;
  renderLocalServiceGuide();
}

async function copyText(text, successMessage) {
  try {
    if (!navigator.clipboard || !window.isSecureContext) throw new Error("clipboard unavailable");
    await navigator.clipboard.writeText(text);
    showToast(successMessage, "success");
  } catch {
    window.prompt("复制以下内容", text);
    showToast("已打开复制提示", "info");
  }
}

function focusEntryFormForEdit() {
  window.requestAnimationFrame(() => {
    const form = document.querySelector("#entryForm");
    if (!form) return;
    form.scrollIntoView({ behavior: "smooth", block: "start" });
    form.classList.add("edit-highlight");
    window.setTimeout(() => form.classList.remove("edit-highlight"), 1800);
  });
}

function numberFromForm(form, name) {
  const value = Number(form.elements[name] ? form.elements[name].value : 0);
  return Number.isFinite(value) ? value : 0;
}

function safeAmount(value) {
  const num = Number(value || 0);
  return Number.isFinite(num) ? num : 0;
}

function settingNumber(value, fallback) {
  const num = Number(value);
  return Number.isFinite(num) ? num : fallback;
}

function normalizeSettings(settings) {
  const raw = { ...defaultSettings, ...(settings || {}) };
  return {
    annualExpense: settingNumber(raw.annualExpense, defaultSettings.annualExpense),
    newMoney: settingNumber(raw.newMoney, defaultSettings.newMoney),
    icPb: settingNumber(raw.icPb, defaultSettings.icPb),
    imPb: settingNumber(raw.imPb, defaultSettings.imPb),
    icPbSource: raw.icPbSource === "auto" ? "auto" : "manual",
    imPbSource: raw.imPbSource === "auto" ? "auto" : "manual",
    manualTotalAssets: settingNumber(raw.manualTotalAssets, defaultSettings.manualTotalAssets)
  };
}

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

function loadLegacySettings() {
  try {
    const raw = localStorage.getItem(legacyStorageKey);
    if (!raw) return {};
    const legacy = JSON.parse(raw);
    return {
      annualExpense: safeAmount(legacy.annualExpense),
      newMoney: safeAmount(legacy.newMoney),
      icPb: safeAmount(legacy.icPb) || 50,
      imPb: safeAmount(legacy.imPb) || 50,
      icPbSource: "manual",
      imPbSource: "manual",
      manualTotalAssets: safeAmount(legacy.totalAssets)
    };
  } catch {
    return {};
  }
}

function normalizeEntry(entry) {
  const next = { ...entry };
  if (next.module === "cashflow") next.module = "dividend";
  if (next.module === "growth") next.module = next.bucket === "SPY put" ? "put" : "qqq";
  if (next.module === "futures") next.module = "ic";
  if (next.module === "dividend") next.bucket = normalizeDividendBucket(next.bucket);
  return next;
}

function normalizeDividendBucket(bucket) {
  if (bucket === "硬现金") return "现金";
  if (bucket === "国债逆回购") return "类现金";
  if (bucket === "高分红" || bucket === "白酒" || bucket === "五粮液" || bucket === "泸州老窖" || bucket === "其他A股高分红") return "高分红股票";
  if (bucket === "现金" || bucket === "高分红股票" || bucket === "类现金" || bucket === "债券") return bucket;
  return bucket || "高分红股票";
}

function loadLedger() {
  try {
    const raw = localStorage.getItem(ledgerKey);
    if (raw) {
      const parsed = JSON.parse(raw);
      return {
        entries: Array.isArray(parsed.entries) ? parsed.entries.map(normalizeEntry) : [],
        settings: normalizeSettings(parsed.settings || {})
      };
    }
  } catch {
    // Fall through to a clean ledger.
  }
  return {
    entries: [],
    settings: normalizeSettings(loadLegacySettings())
  };
}

function ledgerServiceAvailable() {
  return window.location.protocol === "http:" || window.location.protocol === "https:";
}

function normalizeLedgerForStorage(ledger) {
  return {
    entries: Array.isArray(ledger.entries) ? ledger.entries.map(normalizeEntry) : [],
    settings: normalizeSettings(ledger.settings || {})
  };
}

function saveLedger(ledger, options = {}) {
  const next = normalizeLedgerForStorage(ledger);
  const saved = writeLocalStorage(ledgerKey, JSON.stringify(next), "账本");
  if (!saved) return false;
  if (!options.skipMirror) scheduleLedgerMirror(next);
  if (!options.skipRemoteSync) scheduleAutoRemoteSync("ledger");
  return true;
}

function localLedgerStorageEmpty() {
  return !localStorage.getItem(ledgerKey) && !localStorage.getItem(legacyStorageKey);
}

async function fetchWithTimeout(url, options = {}, timeoutMs = 1200) {
  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...options, signal: controller.signal });
  } finally {
    window.clearTimeout(timer);
  }
}

function scheduleLedgerMirror(ledger) {
  if (!ledgerServiceAvailable()) return;
  pendingLedgerMirror = normalizeLedgerForStorage(ledger);
  if (ledgerMirrorTimer) window.clearTimeout(ledgerMirrorTimer);
  ledgerMirrorTimer = window.setTimeout(() => {
    const payload = pendingLedgerMirror;
    pendingLedgerMirror = null;
    ledgerMirrorTimer = null;
    mirrorLedgerSnapshot(payload);
  }, 300);
}

async function postLedgerSnapshot(ledger, timeoutMs = 1800) {
  const response = await fetchWithTimeout(ledgerApiPath, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(normalizeLedgerForStorage(ledger)),
    keepalive: true
  }, timeoutMs);
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.json();
}

async function mirrorLedgerSnapshot(ledger) {
  if (!ledgerServiceAvailable() || !ledger) return;
  try {
    const payload = await postLedgerSnapshot(ledger);
    if (payload && payload.snapshot) {
      const backup = {
        id: payload.snapshot.id,
        created_at: payload.snapshot.created_at,
        entries_count: Array.isArray(ledger.entries) ? ledger.entries.length : 0
      };
      ledgerBackupState = {
        ...ledgerBackupState,
        status: "ready",
        backups: [backup, ...ledgerBackupState.backups.filter((item) => item.id !== backup.id)].slice(0, 8),
        message: `刚刚已自动镜像 #${payload.snapshot.id}`
      };
      renderLedgerBackupPanel();
    }
  } catch {
    // SQLite is only a best-effort local mirror; localStorage remains primary.
  }
}

async function fetchLatestLedgerSnapshot() {
  if (!ledgerServiceAvailable()) return null;
  try {
    const response = await fetchWithTimeout(`${ledgerApiPath}?ts=${Date.now()}`, { cache: "no-store" }, 1200);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const payload = await response.json();
    if (!payload || payload.empty || !payload.snapshot || !payload.snapshot.ledger) return null;
    return payload.snapshot;
  } catch {
    return null;
  }
}

async function offerLedgerBackupRestore() {
  if (!localLedgerStorageEmpty()) return;
  if (sessionStorage.getItem(ledgerBackupPromptKey) === "cancelled") return;
  const snapshot = await fetchLatestLedgerSnapshot();
  if (!snapshot || !snapshot.ledger) return;
  const ledger = normalizeLedgerForStorage(snapshot.ledger);
  const count = ledger.entries.length;
  const createdAt = displayDateTime(snapshot.created_at);
  if (!window.confirm(`检测到本机 SQLite 账本备份（${createdAt}，${count} 笔流水）。是否恢复到当前浏览器？`)) {
    sessionStorage.setItem(ledgerBackupPromptKey, "cancelled");
    return;
  }
  sessionStorage.removeItem(ledgerBackupPromptKey);
  if (!saveLedger(ledger)) return;
  render();
  showToast(`已从 SQLite 备份恢复 ${count} 笔流水`, "success");
}

async function fetchLedgerBackups(limit = 8) {
  if (!ledgerServiceAvailable()) throw new Error("local service unavailable");
  const response = await fetchWithTimeout(`/api/ledger/backups?limit=${limit}&ts=${Date.now()}`, { cache: "no-store" }, 1800);
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  const payload = await response.json();
  if (!payload || payload.ok !== true || !Array.isArray(payload.backups)) throw new Error("invalid backup payload");
  return payload.backups;
}

async function fetchLedgerSnapshotById(snapshotId) {
  const response = await fetchWithTimeout(`${ledgerApiPath}?id=${encodeURIComponent(snapshotId)}&ts=${Date.now()}`, { cache: "no-store" }, 1800);
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  const payload = await response.json();
  if (!payload || payload.empty || !payload.snapshot || !payload.snapshot.ledger) throw new Error("snapshot unavailable");
  return payload.snapshot;
}

function latestBackupLabel() {
  const latest = ledgerBackupState.backups[0];
  if (!latest) return "暂无快照";
  return `#${latest.id} · ${displayDateTime(latest.created_at)}`;
}

function ledgerBackupRows() {
  if (ledgerBackupState.status === "loading") return `<p class="notice">正在读取 SQLite 快照...</p>`;
  if (ledgerBackupState.status === "error") return `<p class="notice">${escapeHtml(ledgerBackupState.message)}</p>`;
  if (!ledgerBackupState.backups.length) return `<p class="notice">还没有 SQLite 快照。通过本地服务打开页面后，保存账本会自动生成。</p>`;
  return `
    <div class="backup-list">
      ${ledgerBackupState.backups.map((backup) => `
        <div class="backup-row">
          <div>
            <strong>#${escapeHtml(backup.id)}</strong>
            <span>${escapeHtml(displayDateTime(backup.created_at))} · ${escapeHtml(String(backup.entries_count || 0))} 笔流水</span>
          </div>
          <button class="small-action" type="button" data-action="restore-ledger-backup" data-backup-id="${escapeHtml(backup.id)}">恢复</button>
        </div>
      `).join("")}
    </div>
  `;
}

function ledgerBackupPanel(ledger) {
  const serviceReady = ledgerServiceAvailable();
  const mirrorText = serviceReady ? latestBackupLabel() : "未连接";
  const message = serviceReady
    ? ledgerBackupState.message
    : "当前不是本地服务入口；双击 HTML 或普通静态服务不会写入 SQLite。";
  return `
    <section class="backup-panel" id="ledgerBackupPanel">
      <div class="section-head">
        <h2>本地备份</h2>
        <span>localStorage 主存 / SQLite 镜像</span>
      </div>
      <div class="backup-summary">
        <article>
          <span>浏览器账本</span>
          <strong>${escapeHtml(String(ledger.entries.length))} 笔</strong>
          <small>当前浏览器 localStorage</small>
        </article>
        <article>
          <span>SQLite 镜像</span>
          <strong>${escapeHtml(mirrorText)}</strong>
          <small>${escapeHtml(message)}</small>
        </article>
      </div>
      <div class="inline-actions backup-actions">
        <button type="button" data-action="manual-ledger-backup">立即镜像备份</button>
        <button type="button" data-action="refresh-ledger-backups">刷新备份列表</button>
        <button type="button" data-action="open-service-url">打开本地服务入口</button>
      </div>
      ${ledgerBackupRows()}
      <p class="notice">推荐从 <code>${escapeHtml(dashboardServiceUrl())}</code> 打开页面。无服务时仍可正常记账，但不会更新 SQLite 镜像。</p>
    </section>
  `;
}

function renderLedgerBackupPanel() {
  const panel = document.querySelector("#ledgerBackupPanel");
  const summary = document.querySelector("#overviewSettingsSummary");
  const ledger = loadLedger();
  if (panel) panel.outerHTML = ledgerBackupPanel(ledger);
  if (summary) summary.outerHTML = overviewSettingsSummaryPanel(ledger);
}

async function refreshLedgerBackups(options = {}) {
  if (!ledgerServiceAvailable()) {
    ledgerBackupState = {
      status: "error",
      backups: [],
      message: "未连接本地服务；请先运行启动命令并从本地服务入口打开。"
    };
    renderLedgerBackupPanel();
    return;
  }
  ledgerBackupState = { ...ledgerBackupState, status: "loading", message: "正在读取 SQLite 快照..." };
  renderLedgerBackupPanel();
  try {
    const backups = await fetchLedgerBackups();
    ledgerBackupState = {
      status: "ready",
      backups,
      message: backups.length ? `已读取 ${backups.length} 个最近快照` : "SQLite 已连接，但暂无快照"
    };
    renderLedgerBackupPanel();
    if (!options.silent) showToast("已刷新 SQLite 备份列表", "success");
  } catch {
    ledgerBackupState = {
      status: "error",
      backups: [],
      message: "读取 SQLite 备份失败；请确认使用 python3 services/sync/src/main.py 启动。"
    };
    renderLedgerBackupPanel();
    if (!options.silent) {
      showLocalServiceGuide("ledger");
      showToast("读取 SQLite 备份失败", "error");
    }
  }
}

async function manualLedgerBackup() {
  if (!ledgerServiceAvailable()) {
    showLocalServiceGuide("ledger");
    ledgerBackupState = {
      status: "error",
      backups: [],
      message: "未连接本地服务，无法写入 SQLite。"
    };
    renderLedgerBackupPanel();
    showToast("请先启动本地服务再手动备份", "error");
    return;
  }
  ledgerBackupState = { ...ledgerBackupState, status: "loading", message: "正在写入 SQLite 快照..." };
  renderLedgerBackupPanel();
  try {
    await postLedgerSnapshot(loadLedger(), 3500);
    showToast("已写入 SQLite 快照", "success");
    await refreshLedgerBackups({ silent: true });
  } catch {
    ledgerBackupState = {
      status: "error",
      backups: ledgerBackupState.backups,
      message: "写入 SQLite 失败；请确认本地服务仍在运行。"
    };
    renderLedgerBackupPanel();
    showLocalServiceGuide("ledger");
    showToast("SQLite 手动备份失败", "error");
  }
}

async function restoreLedgerBackup(snapshotId) {
  if (!snapshotId) return;
  let snapshot;
  try {
    snapshot = await fetchLedgerSnapshotById(snapshotId);
  } catch {
    showToast("读取该 SQLite 快照失败", "error");
    return;
  }
  const ledger = normalizeLedgerForStorage(snapshot.ledger);
  if (!confirmDanger(`确认用 SQLite 快照 #${snapshot.id} 覆盖当前浏览器账本？当前 localStorage 会被替换，SQLite 历史仍保留。`)) return;
  if (!saveLedger(ledger)) return;
  render();
  await refreshLedgerBackups({ silent: true });
  showToast(`已恢复快照 #${snapshot.id}，共 ${ledger.entries.length} 笔流水`, "success");
}

function saveSettings(partial) {
  const ledger = loadLedger();
  ledger.settings = normalizeSettings({ ...ledger.settings, ...partial });
  return saveLedger(ledger);
}

function loadPositionValuations() {
  try {
    const raw = localStorage.getItem(positionValuationKey);
    return raw ? JSON.parse(raw) : {};
  } catch {
    return {};
  }
}

function savePositionValuations(valuations, options = {}) {
  const saved = writeLocalStorage(positionValuationKey, JSON.stringify(valuations), "持仓估值");
  if (saved && !options.skipRemoteSync) scheduleAutoRemoteSync("positionValuations");
  return saved;
}

function positionKey(module, symbol, name) {
  const code = String(symbol || "").trim();
  const title = String(name || "").trim();
  return `${module || "unknown"}::${(code || title || "未命名").toUpperCase()}`;
}

function quantityImpact(entry) {
  const quantity = safeAmount(entry.quantity);
  if (entry.action === "buy") return quantity;
  if (entry.action === "sell" || entry.action === "expire") return -quantity;
  return 0;
}

function entryBalanceImpact(entry) {
  const amount = safeAmount(entry.amount);
  const fee = safeAmount(entry.fee);
  if (entry.module === "ic" && (entry.action === "buy" || entry.action === "sell" || entry.action === "roll")) return 0;
  if (entry.action === "buy") return amount + fee;
  if (entry.action === "sell") return -amount;
  if (entry.action === "dividend") return -amount;
  if (entry.action === "expire") return -amount;
  if (entry.action === "roll") return fee > 0 ? fee : 0;
  const sign = balanceActions[entry.action] ?? 0;
  return sign * amount;
}

function entryCashImpact(entry) {
  const amount = safeAmount(entry.amount);
  const fee = safeAmount(entry.fee);
  if (entry.action === "deposit") return amount;
  if (entry.action === "withdraw") return -amount;
  if (entry.module === "ic" && (entry.action === "buy" || entry.action === "sell" || entry.action === "roll")) return 0;
  if (entry.action === "buy") return -(amount + fee);
  if (entry.action === "sell") return amount - fee;
  if (entry.action === "dividend" || entry.action === "interest") return amount;
  if (entry.action === "futures_deposit") return -amount;
  if (entry.action === "roll") return -fee;
  return 0;
}

function entryMarginImpact(entry) {
  if (entry.module !== "ic") return 0;
  return safeAmount(entry.margin);
}

function entryFuturesNotionalImpact(entry) {
  if (entry.module !== "ic") return 0;
  if (entry.action === "buy") return safeAmount(entry.amount);
  if (entry.action === "sell") return -safeAmount(entry.amount);
  return 0;
}

function futuresProductFromSymbol(symbol) {
  const match = String(symbol || "").trim().toUpperCase().match(/^(IC|IM)\d{4}$/);
  return match ? match[1] : "";
}

function futuresContractPrice(payload, symbol) {
  const normalized = String(symbol || "").trim().toUpperCase();
  const product = futuresProductFromSymbol(normalized);
  const contracts = product && payload && payload.indexes && payload.indexes[product] && payload.indexes[product].basis
    ? payload.indexes[product].basis.contracts || []
    : [];
  const contract = contracts.find((item) => String(item.contract || "").toUpperCase() === normalized);
  const price = contract ? Number(contract.future) : NaN;
  return Number.isFinite(price) && price > 0 ? price : null;
}

function futuresFrontContract(payload, product) {
  const key = String(product || "").trim().toUpperCase();
  const contracts = key && payload && payload.indexes && payload.indexes[key] && payload.indexes[key].basis
    ? payload.indexes[key].basis.contracts || []
    : [];
  return contracts.find((item) => Number.isFinite(Number(item.future)) && Number(item.future) > 0) || null;
}

function futuresOneLotNotional(payload, product) {
  const contract = futuresFrontContract(payload, product);
  const price = contract ? Number(contract.future) : 0;
  return price > 0 ? amountFromQuantityPrice(1, price, defaultFuturesMultiplier) || 0 : 0;
}

function futuresExposureReference(payload) {
  const source = payload || loadValuation();
  const ic = futuresOneLotNotional(source, "IC");
  const im = futuresOneLotNotional(source, "IM");
  return {
    ic,
    im,
    icIm: ic + im,
    icContract: (futuresFrontContract(source, "IC") || {}).contract || "",
    imContract: (futuresFrontContract(source, "IM") || {}).contract || ""
  };
}

function futuresExposureReferenceText(reference) {
  if (!reference || (!reference.ic && !reference.icIm)) return "读取估值 JSON 后显示 1手参考敞口";
  const icText = reference.ic ? `1手IC ${yuan(reference.ic)}` : "1手IC -";
  const comboText = reference.icIm ? `1手IC+1手IM ${yuan(reference.icIm)}` : "1手IC+1手IM -";
  return `参考：${icText} / ${comboText}`;
}

function deriveFuturesState(entries, valuation = loadValuation()) {
  const positions = new Map();
  let accountFunding = 0;
  let fees = 0;
  let realizedPnl = 0;

  entries.forEach((entry) => {
    if (entry.module !== "ic") return;
    const action = entry.action;
    const amount = safeAmount(entry.amount);
    const fee = safeAmount(entry.fee);
    if (fee > 0) fees += fee;
    if (action === "futures_deposit") {
      accountFunding += amount;
      return;
    }
    if (action !== "buy" && action !== "sell") return;

    const symbol = String(entry.symbol || "").trim().toUpperCase();
    const quantity = safeAmount(entry.quantity);
    const price = safeAmount(entry.price);
    const multiplier = safeAmount(entry.multiplier) || defaultFuturesMultiplier;
    if (!symbol || quantity <= 0 || price <= 0 || multiplier <= 0) return;

    const notional = amountFromQuantityPrice(quantity, price, multiplier) || amount;
    const entryMargin = safeAmount(entry.margin);
    const entryMarginRate = notional > 0 && entryMargin > 0 ? entryMargin / notional : 0;
    const current = positions.get(symbol) || {
      symbol,
      product: futuresProductFromSymbol(symbol),
      quantity: 0,
      avgPrice: 0,
      multiplier,
      marginRate: 0
    };

    if (action === "buy") {
      const existingNotional = amountFromQuantityPrice(current.quantity, current.avgPrice, current.multiplier) || 0;
      const nextQuantity = current.quantity + quantity;
      current.avgPrice = nextQuantity > 0
        ? ((current.avgPrice * current.quantity) + (price * quantity)) / nextQuantity
        : 0;
      current.quantity = nextQuantity;
      current.multiplier = multiplier;
      current.marginRate = existingNotional + notional > 0
        ? ((existingNotional * current.marginRate) + (notional * entryMarginRate)) / (existingNotional + notional)
        : entryMarginRate;
      positions.set(symbol, current);
      return;
    }

    const closeQuantity = Math.min(quantity, current.quantity);
    if (closeQuantity > 0) {
      realizedPnl += ((price - current.avgPrice) * closeQuantity * current.multiplier) / 10000;
      current.quantity -= closeQuantity;
    }
    if (current.quantity > 0) positions.set(symbol, current);
    else positions.delete(symbol);
  });

  let notional = 0;
  let usedMargin = 0;
  let unrealizedPnl = 0;
  let hasIc = false;
  let hasIm = false;
  const openPositions = [];

  positions.forEach((position) => {
    if (position.quantity <= 0) return;
    const currentPrice = futuresContractPrice(valuation, position.symbol) || position.avgPrice;
    const currentNotional = amountFromQuantityPrice(position.quantity, currentPrice, position.multiplier) || 0;
    const openPnl = ((currentPrice - position.avgPrice) * position.quantity * position.multiplier) / 10000;
    notional += currentNotional;
    usedMargin += currentNotional * position.marginRate;
    unrealizedPnl += openPnl;
    hasIc = hasIc || position.product === "IC";
    hasIm = hasIm || position.product === "IM";
    openPositions.push({
      ...position,
      currentPrice,
      currentNotional,
      usedMargin: currentNotional * position.marginRate,
      unrealizedPnl: openPnl,
      priceSource: futuresContractPrice(valuation, position.symbol) ? "valuation" : "entry"
    });
  });

  return {
    accountFunding,
    fees,
    realizedPnl,
    unrealizedPnl,
    totalPnl: realizedPnl + unrealizedPnl,
    equity: accountFunding + realizedPnl + unrealizedPnl - fees,
    usedMargin,
    notional,
    hasIc,
    hasIm,
    openPositions
  };
}

function futuresHoldingRows(data) {
  const state = data.futuresState || { openPositions: [] };
  return (state.openPositions || [])
    .slice()
    .sort((a, b) => String(a.symbol || "").localeCompare(String(b.symbol || "")))
    .map((position) => ({
      symbol: position.symbol,
      product: position.product || futuresProductFromSymbol(position.symbol),
      quantity: safeAmount(position.quantity),
      avgPrice: safeAmount(position.avgPrice),
      currentPrice: safeAmount(position.currentPrice),
      multiplier: safeAmount(position.multiplier) || defaultFuturesMultiplier,
      currentNotional: safeAmount(position.currentNotional),
      usedMargin: safeAmount(position.usedMargin),
      marginRate: ratio(safeAmount(position.usedMargin), safeAmount(position.currentNotional)),
      unrealizedPnl: safeAmount(position.unrealizedPnl),
      priceSource: position.priceSource || "entry"
    }));
}

function isCashPoolEntry(entry) {
  return entry.module === "cash" || entry.action === "deposit" || entry.action === "withdraw";
}

function isPositionEntry(entry) {
  if (isCashPoolEntry(entry)) return false;
  if (entry.module === "ic" && (entry.action === "buy" || entry.action === "sell")) return false;
  if (entry.action === "margin" || entry.action === "roll" || entry.action === "fee") return false;
  return true;
}

function entryIncomeImpact(entry) {
  if (entry.action !== "dividend" && entry.action !== "interest") return 0;
  const cutoff = new Date();
  cutoff.setFullYear(cutoff.getFullYear() - 1);
  const entryDate = new Date(entry.date || "1970-01-01");
  return entryDate >= cutoff ? safeAmount(entry.amount) : 0;
}

function buildPositionsFromEntries(entries, valuations = loadPositionValuations(), enrich = enrichPosition) {
  const byKey = new Map();
  entries.forEach((entry) => {
    if (!isPositionEntry(entry)) return;
    const symbol = String(entry.symbol || "").trim();
    const name = String(entry.name || "").trim();
    const key = positionKey(entry.module, symbol, name);
    const isIncomeOnly = entry.action === "interest";
    const existing = byKey.get(key) || {
      key,
      module: entry.module || "",
      bucket: entry.bucket || "未分类",
      symbol,
      name,
      quantity: 0,
      netInvestment: 0,
      entryCount: 0,
      latestDate: ""
    };
    if (!isIncomeOnly) {
      existing.bucket = entry.bucket || existing.bucket;
      if (symbol) existing.symbol = symbol;
      if (name) existing.name = name;
      existing.quantity += quantityImpact(entry);
      existing.netInvestment += entryBalanceImpact(entry);
      existing.entryCount += 1;
    }
    if (String(entry.date || "") > String(existing.latestDate || "")) existing.latestDate = entry.date || "";
    byKey.set(key, existing);
  });

  return Array.from(byKey.values())
    .map((position) => enrich(position, valuations[position.key] || {}))
    .filter((position) => Math.abs(position.netInvestment) > 0 || Math.abs(position.quantity) > 0 || position.hasManualValuation);
}

function buildPositions(ledger = loadLedger()) {
  return buildPositionsFromEntries(ledger.entries, loadPositionValuations(), enrichPosition);
}

function enrichPosition(position, valuation) {
  const currentPrice = optionalNumber(valuation.currentPrice);
  const manualMarketValue = optionalNumber(valuation.marketValue);
  const priceMarketValue = currentPrice !== null && position.quantity > 0 ? (position.quantity * currentPrice) / 10000 : null;
  const marketValue = manualMarketValue !== null ? manualMarketValue : priceMarketValue !== null ? priceMarketValue : position.netInvestment;
  const unrealizedPnl = marketValue - position.netInvestment;
  return {
    ...position,
    currentPrice,
    manualMarketValue,
    marketValue,
    marketValueSource: manualMarketValue !== null ? "manual" : priceMarketValue !== null ? "price" : "cost",
    unrealizedPnl,
    unrealizedPnlPct: ratio(unrealizedPnl, Math.abs(position.netInvestment)),
    valuationNote: String(valuation.note || ""),
    valuationUpdatedAt: valuation.updatedAt || "",
    valuationSource: valuation.source || "",
    hasManualValuation: currentPrice !== null || manualMarketValue !== null || Boolean(valuation.note)
  };
}

function optionalNumber(value) {
  if (value === "" || value === null || value === undefined) return null;
  const num = Number(value);
  return Number.isFinite(num) ? num : null;
}

function sumPositionsBy(positions, field) {
  const result = {};
  positions.forEach((position) => {
    const key = position[field] || "未分类";
    result[key] = (result[key] || 0) + position.marketValue;
  });
  return result;
}

function sumBuckets(entries) {
  const result = {};
  entries.forEach((entry) => {
    const bucket = entry.bucket || "未分类";
    result[bucket] = (result[bucket] || 0) + entryBalanceImpact(entry);
  });
  return result;
}

function sumModule(entries, module) {
  return entries
    .filter((entry) => entry.module === module)
    .reduce((sum, entry) => sum + entryBalanceImpact(entry), 0);
}

function summarizeLedger(ledger = loadLedger()) {
  const costBuckets = sumBuckets(ledger.entries);
  const positions = buildPositions(ledger);
  const buckets = sumPositionsBy(positions, "bucket");
  const moduleTotals = sumPositionsBy(positions, "module");
  const settings = { ...defaultSettings, ...ledger.settings };
  const cashBalance = ledger.entries.reduce((sum, entry) => sum + entryCashImpact(entry), 0);
  const hardCash = cashBalance + (buckets["现金"] || 0) + (buckets["硬现金"] || 0);
  const reverseRepo = (buckets["类现金"] || 0) + (buckets["国债逆回购"] || 0);
  const whiteLiquor = 0;
  const highDividend =
    (buckets["高分红股票"] || 0) +
    (buckets["高分红"] || 0) +
    (buckets["白酒"] || 0) +
    (buckets["五粮液"] || 0) +
    (buckets["泸州老窖"] || 0) +
    (buckets["其他A股高分红"] || 0);
  const otherAHighDividend = highDividend;
  const qqq = (buckets["QQQ"] || 0) + (buckets["QLD"] || 0);
  const spyPutBudget = buckets["SPY put"] || 0;
  const futuresPool = buckets["IC/IM资金池"] || 0;
  const futuresState = deriveFuturesState(ledger.entries);
  const futuresNetPnl = futuresState.totalPnl - futuresState.fees;
  const derivedTotal = cashBalance + Object.values(buckets).reduce((sum, value) => sum + value, 0) + futuresNetPnl;
  const totalAssets = derivedTotal > 0 ? derivedTotal : settings.manualTotalAssets;
  const annualDividend = ledger.entries.reduce((sum, entry) => sum + entryIncomeImpact(entry), 0);
  const investedCost = positions.reduce((sum, position) => sum + position.netInvestment, 0);
  const unrealizedPnl = positions.reduce((sum, position) => sum + position.unrealizedPnl, 0) + futuresNetPnl;

  return {
    totalAssets,
    cashBalance,
    annualExpense: settings.annualExpense,
    hardCash,
    reverseRepo,
    highDividend,
    annualDividend,
    qqq,
    newMoney: settings.newMoney,
    futuresPool,
    futuresEquity: futuresState.equity,
    usedMargin: futuresState.usedMargin,
    futuresNotional: futuresState.notional,
    spyPutBudget,
    whiteLiquor,
    otherAHighDividend,
    icPb: settings.icPb,
    imPb: settings.imPb,
    hasIc: futuresState.hasIc,
    futuresState,
    bucketTotals: buckets,
    costBucketTotals: costBuckets,
    positions,
    investedCost,
    unrealizedPnl,
    unrealizedPnlPct: ratio(unrealizedPnl, Math.abs(investedCost)),
    moduleTotals: {
      dividend: moduleTotals.dividend || 0,
      qqq: moduleTotals.qqq || 0,
      put: moduleTotals.put || 0,
      ic: moduleTotals.ic || 0
    },
    moduleCostTotals: {
      dividend: sumModule(ledger.entries, "dividend"),
      qqq: sumModule(ledger.entries, "qqq"),
      put: sumModule(ledger.entries, "put"),
      ic: sumModule(ledger.entries, "ic")
    }
  };
}

function ratio(part, total) {
  return total > 0 ? (part / total) * 100 : 0;
}

function gap(target, current) {
  return Math.max(target - current, 0);
}

function classify(value, goodLine, warnLine, reverse = false) {
  if (reverse) {
    if (value <= goodLine) return "status-good";
    if (value <= warnLine) return "status-warn";
    return "status-danger";
  }
  if (value >= goodLine) return "status-good";
  if (value >= warnLine) return "status-warn";
  return "status-danger";
}

function qqqStatus(qqqPct) {
  if (qqqPct < 5) return "起步不足";
  if (qqqPct < 10) return "建设区";
  if (qqqPct <= 12) return "目标区";
  if (qqqPct <= 15) return "漂移区";
  return "过重";
}

function targetProgressMeta(current, target, mode = "min") {
  const currentValue = safeAmount(current);
  const targetValue = safeAmount(target);
  if (targetValue <= 0) {
    return {
      current: currentValue,
      target: 0,
      fill: currentValue > 0 ? 100 : 0,
      marker: 0,
      label: "暂无阶段目标",
      statusClass: "target-neutral",
      hasTarget: false
    };
  }
  const scale = Math.max(currentValue, targetValue, 1);
  const reached = mode === "max" ? currentValue <= targetValue : currentValue >= targetValue;
  const diff = Math.abs(currentValue - targetValue);
  return {
    current: currentValue,
    target: targetValue,
    fill: Math.min(100, Math.max(0, ratio(currentValue, scale))),
    marker: Math.min(100, Math.max(0, ratio(targetValue, scale))),
    label: reached ? (mode === "max" ? "安全" : "达标") : (mode === "max" ? `超 ${yuan(diff)}` : `差 ${yuan(diff)}`),
    statusClass: reached ? "target-ok" : "target-missing",
    hasTarget: true
  };
}

function targetTrack(current, target, mode = "min", className = "target-track") {
  const meta = targetProgressMeta(current, target, mode);
  return `
    <span class="${className}">
      <span class="track-fill" style="width:${meta.fill}%"></span>
      ${meta.hasTarget ? `<i class="target-marker" style="left:${meta.marker}%"></i>` : ""}
    </span>
  `;
}

function referenceTrack(current, references, className = "target-track") {
  const validReferences = (references || []).filter((item) => safeAmount(item.value) > 0);
  const scale = Math.max(safeAmount(current), ...validReferences.map((item) => safeAmount(item.value)), 1);
  return `
    <span class="${className}">
      <span class="track-fill" style="width:${Math.min(100, Math.max(0, ratio(safeAmount(current), scale)))}%"></span>
      ${validReferences.map((item, index) => (
        `<i class="target-marker reference-marker reference-marker-${index + 1}" title="${escapeHtml(item.label)} ${escapeHtml(yuan(item.value))}" style="left:${Math.min(100, Math.max(0, ratio(safeAmount(item.value), scale)))}%"></i>`
      )).join("")}
    </span>
  `;
}

function targetText(current, target, mode = "min") {
  const meta = targetProgressMeta(current, target, mode);
  if (!meta.hasTarget) return meta.label;
  return `目标 ${yuan(meta.target)} · ${meta.label}`;
}

function moduleTargetInfo(key, data, calc, current) {
  if (key === "dividend") {
    if (calc.cashGap6 > 0) return { target: current + calc.cashGap6, mode: "min", text: "阶段目标：补足 6 个月硬现金" };
    if (calc.liquidGap12 > 0) return { target: current + calc.liquidGap12, mode: "min", text: "阶段目标：补足 12 个月流动安全垫" };
    if (calc.highDividendGap30 > 0) return { target: current + calc.highDividendGap30, mode: "min", text: "阶段目标：高分红股票接近 30%" };
    return { target: current, mode: "min", text: "阶段目标已达成" };
  }
  if (key === "qqq") {
    const targetPct = calc.qqqPct < 5 ? 0.05 : 0.10;
    return { target: data.totalAssets * targetPct, mode: "min", text: `阶段目标：${targetPct === 0.05 ? "5% 起步线" : "10% 目标线"}` };
  }
  if (key === "put") {
    return { target: calc.putBudgetTarget, mode: "min", text: calc.putBudgetTarget > 0 ? "阶段目标：年度保险预算" : "阶段目标：暂不启用" };
  }
  if (key === "ic") {
    if (calc.futuresTopUpGap > 0) return { target: current + calc.futuresTopUpGap, mode: "min", text: "阶段目标：补回 55% 风险度以内" };
    return { target: data.futuresPool < 100 ? 100 : current, mode: "min", text: data.futuresPool < 100 ? "阶段目标：期货资金池 100 万" : "阶段目标已达成" };
  }
  return { target: 0, mode: "min", text: "暂无阶段目标" };
}

function bucketTargetInfo(module, bucket, current, data, calc) {
  if (module === "dividend") {
    if (bucket === "现金") return { target: calc.monthlyExpense * 6, mode: "min", text: "6 个月硬现金" };
    if (bucket === "类现金") return { target: Math.max(data.annualExpense - data.hardCash, 0), mode: "min", text: "补足 12 个月流动垫" };
    if (bucket === "高分红股票") return { target: data.totalAssets * 0.30, mode: "min", text: "高分红股票 30%" };
    if (bucket === "债券") return { target: data.totalAssets >= 100 ? data.totalAssets * 0.10 : 0, mode: "min", text: data.totalAssets >= 100 ? "阶段债券缓冲 10%" : "暂不设硬目标" };
  }
  if (module === "qqq") {
    if (bucket === "QQQ") return { target: data.totalAssets * (calc.qqqPct < 5 ? 0.05 : 0.10), mode: "min", text: "右尾仓阶段目标" };
    if (bucket === "QLD") return { target: Math.max(data.qqq * 0.35, 0), mode: "max", text: "QLD 不超过右尾仓 35%" };
    if (bucket === "现金等待") return { target: 0, mode: "min", text: "趋势未确认时等待" };
  }
  if (module === "put") {
    if (bucket === "SPY put" || bucket === "保险预算") return { target: calc.putBudgetTarget, mode: "min", text: "年度保险预算" };
    if (bucket === "到期归零") return { target: 0, mode: "max", text: "保险成本归档" };
  }
  return { target: 0, mode: "min", text: "暂无阶段目标" };
}

function calculate(data) {
  const monthlyExpense = data.annualExpense / 12;
  const liquidAssets = data.hardCash + data.reverseRepo;
  const hardMonths = monthlyExpense > 0 ? data.hardCash / monthlyExpense : 0;
  const liquidMonths = monthlyExpense > 0 ? liquidAssets / monthlyExpense : 0;
  const divCoverage = ratio(data.annualDividend, data.annualExpense);
  const qqqPct = ratio(data.qqq, data.totalAssets);
  const highDividendPct = ratio(data.highDividend, data.totalAssets);
  const putPct = ratio(data.spyPutBudget, data.totalAssets);
  const futuresPoolPct = ratio(data.futuresPool, data.totalAssets);
  const safetyPct = ratio(liquidAssets + data.highDividend, data.totalAssets);
  const sameSourcePct = ratio(data.whiteLiquor + data.otherAHighDividend + data.futuresNotional, data.totalAssets);
  const marginRiskInvalid = data.usedMargin > 0 && data.futuresEquity <= 0;
  const marginRisk = marginRiskInvalid ? Infinity : ratio(data.usedMargin, data.futuresEquity);
  const futuresLeverage = data.futuresEquity > 0 ? data.futuresNotional / data.futuresEquity : 0;
  const futuresMarginRate = ratio(data.usedMargin, data.futuresNotional);
  const cashGap6 = gap(monthlyExpense * 6, data.hardCash);
  const liquidGap12 = gap(data.annualExpense, liquidAssets);
  const divGap = gap(data.annualExpense * 1.2, data.annualDividend);
  const qqqGap5 = gap(data.totalAssets * 0.05, data.qqq);
  const qqqGap10 = gap(data.totalAssets * 0.10, data.qqq);
  const highDividendGap30 = gap(data.totalAssets * 0.30, data.highDividend);
  const futuresGap = gap(100, data.futuresPool);
  const futuresTopUpGap = data.usedMargin > 0 ? gap(data.usedMargin / 0.55, data.futuresEquity) : 0;
  const putBudgetTarget = data.totalAssets >= 300 ? data.totalAssets * 0.015 : data.totalAssets >= 200 ? data.totalAssets * 0.005 : 0;
  const putBudgetGap = gap(putBudgetTarget, data.spyPutBudget);

  return {
    monthlyExpense,
    liquidAssets,
    hardMonths,
    liquidMonths,
    divCoverage,
    qqqPct,
    highDividendPct,
    putPct,
    futuresPoolPct,
    safetyPct,
    sameSourcePct,
    marginRisk,
    marginRiskInvalid,
    futuresLeverage,
    futuresMarginRate,
    cashGap6,
    liquidGap12,
    divGap,
    qqqGap5,
    qqqGap10,
    highDividendGap30,
    futuresGap,
    futuresTopUpGap,
    putBudgetTarget,
    putBudgetGap
  };
}

function marginRiskText(calc) {
  return calc.marginRiskInvalid ? "权益为0，极危" : pct(calc.marginRisk);
}

function marginRiskHint(calc) {
  return calc.marginRiskInvalid ? "占用保证金 > 0 但账户权益为 0，风险度不可计算" : "占用保证金 / 账户权益";
}

function futuresAccountRiskMeta(data, calc = calculate(data)) {
  const equity = safeAmount(data.futuresEquity);
  const usedMargin = safeAmount(data.usedMargin);
  const availableEquity = equity - usedMargin;
  const watchLine = 55;
  const defenseLine = 70;
  const requiredEquityForWatch = usedMargin > 0 ? usedMargin / (watchLine / 100) : 0;
  const requiredEquityForDefense = usedMargin > 0 ? usedMargin / (defenseLine / 100) : 0;
  const topUpToWatch = usedMargin > 0 ? gap(requiredEquityForWatch, equity) : 0;
  const topUpToDefense = usedMargin > 0 ? gap(requiredEquityForDefense, equity) : 0;
  const marginRisk = calc.marginRiskInvalid ? Infinity : safeAmount(calc.marginRisk);
  let statusText = "未开仓";
  let statusClass = "risk-status-neutral";
  if (calc.marginRiskInvalid) {
    statusText = "极危";
    statusClass = "risk-status-danger";
  } else if (usedMargin > 0 && marginRisk <= watchLine) {
    statusText = "安全区";
    statusClass = "risk-status-good";
  } else if (usedMargin > 0 && marginRisk <= defenseLine) {
    statusText = "观察区";
    statusClass = "risk-status-warn";
  } else if (usedMargin > 0) {
    statusText = "危险区";
    statusClass = "risk-status-danger";
  }
  return {
    equity,
    usedMargin,
    availableEquity,
    watchLine,
    defenseLine,
    requiredEquityForWatch,
    requiredEquityForDefense,
    topUpToWatch,
    topUpToDefense,
    marginRisk,
    statusText,
    statusClass
  };
}

function futuresRiskActionText(meta) {
  if (meta.usedMargin <= 0) return "未开仓，无保证金占用";
  if (!Number.isFinite(meta.marginRisk)) return "权益为 0，需先补权益或修正流水";
  if (meta.marginRisk > meta.defenseLine) {
    return `需补权益 ${yuan(meta.topUpToDefense)} 回到 70% 防守线，补 ${yuan(meta.topUpToWatch)} 回到 55% 观察线`;
  }
  if (meta.marginRisk > meta.watchLine) return `需补权益 ${yuan(meta.topUpToWatch)} 回到 55% 观察线`;
  return `当前无需补资；55% 观察线所需权益 ${yuan(meta.requiredEquityForWatch)}`;
}

function futuresRiskTrack(meta, className = "bucket-track") {
  const fill = Number.isFinite(meta.marginRisk) ? Math.min(100, Math.max(0, meta.marginRisk)) : 100;
  const watchMarker = Math.min(100, Math.max(0, meta.watchLine));
  const defenseMarker = Math.min(100, Math.max(0, meta.defenseLine));
  return `
    <span class="${className} futures-risk-track">
      <span class="track-fill ${escapeHtml(meta.statusClass)}" style="width:${fill}%"></span>
      <i class="target-marker reference-marker" title="55% 观察线" style="left:${watchMarker}%"></i>
      <i class="target-marker reference-marker-2" title="70% 防守线" style="left:${defenseMarker}%"></i>
    </span>
  `;
}

function futuresStressLossForRows(rows, stressDrop) {
  return (rows || []).reduce((sum, row) => {
    const notional = safeAmount(row.currentNotional) || amountFromQuantityPrice(safeAmount(row.quantity), safeAmount(row.currentPrice), safeAmount(row.multiplier) || defaultFuturesMultiplier) || 0;
    return sum + notional * stressDrop;
  }, 0);
}

function futuresAddLotCandidates(data, calc = calculate(data), valuation = loadValuation(), stressDrop = 0.20) {
  const marginRate = calc.futuresMarginRate > 0 ? calc.futuresMarginRate / 100 : 0.12;
  const currentEquity = safeAmount(data.futuresEquity);
  const currentUsedMargin = safeAmount(data.usedMargin);
  const existingStressLoss = futuresStressLossForRows(futuresHoldingRows(data), stressDrop);
  const watchRisk = 0.55;
  const defenseRisk = 0.70;
  return ["IC", "IM"].map((product) => {
    const contract = futuresFrontContract(valuation, product);
    const future = contract ? Number(contract.future) : 0;
    const notional = future > 0 ? amountFromQuantityPrice(1, future, defaultFuturesMultiplier) || 0 : 0;
    const addedMargin = notional * marginRate;
    const nextUsedMargin = currentUsedMargin + addedMargin;
    const riskNoTopUp = currentEquity > 0 ? ratio(nextUsedMargin, currentEquity) : Infinity;
    const topUpToDefense = nextUsedMargin > 0 ? gap(nextUsedMargin / defenseRisk, currentEquity) : 0;
    const topUpToWatch = nextUsedMargin > 0 ? gap(nextUsedMargin / watchRisk, currentEquity) : 0;
    const stressLoss = existingStressLoss + notional * stressDrop;
    const stressedUsedMargin = nextUsedMargin * (1 - stressDrop);
    const stressedEquityWithoutTopUp = currentEquity - stressLoss;
    const stressTopUpToDefense = stressedUsedMargin > 0 ? gap(stressedUsedMargin / defenseRisk, stressedEquityWithoutTopUp) : 0;
    const stressTopUpToWatch = stressedUsedMargin > 0 ? gap(stressedUsedMargin / watchRisk, stressedEquityWithoutTopUp) : 0;
    return {
      product,
      contract: contract ? contract.contract || "" : "",
      future,
      notional,
      marginRate: marginRate * 100,
      addedMargin,
      nextUsedMargin,
      currentEquity,
      riskNoTopUp,
      topUpToDefense,
      topUpToWatch,
      stressDrop: stressDrop * 100,
      stressLoss,
      stressedUsedMargin,
      stressTopUpToDefense,
      stressTopUpToWatch,
      recommendedTopUp: stressTopUpToDefense,
      reserveAfterImmediateWatch: Math.max(stressTopUpToDefense - topUpToWatch, 0)
    };
  });
}

function detectStage(data, calc) {
  if (calc.hardMonths < 6) {
    return { id: 0, name: "阶段 0：先补硬现金", reason: "硬现金不足 6 个月生活支出" };
  }
  if (calc.qqqPct < 5) {
    return { id: 1, name: "阶段 1：高分红 + QQQ 起步", reason: "QQQ 还没到当前总资产 5%" };
  }
  if (calc.liquidMonths < 12 || calc.divCoverage < 100) {
    return { id: 2, name: "阶段 2：补生活现金流", reason: "流动安全垫或股息覆盖还没稳定" };
  }
  if (data.futuresPool < 100) {
    return { id: 3, name: "阶段 3：补期货专项资金池", reason: "IC/IM 专项资金池还没到 100 万起步线" };
  }
  if (data.icPb <= 30) {
    return { id: 4, name: "阶段 4：可评估第一手 IC", reason: "资金池与 IC 估值条件同时接近执行区" };
  }
  return { id: 4, name: "阶段 4：等待 IC 估值", reason: "资金条件具备，但 IC PB 百分位还没进执行区" };
}

function weightedChildren(amount, rows) {
  return rows
    .map(([name, weight, reason]) => ({
      name,
      weight,
      amount: amount * weight / 100,
      reason
    }))
    .filter((item) => item.amount > 0);
}

function defensiveChildren(amount, mode) {
  if (mode === "hard_cash") {
    return weightedChildren(amount, [["现金 / 货币基金", 100, "硬现金未满 6 个月，先补生存底线"]]);
  }
  if (mode === "liquidity") {
    return weightedChildren(amount, [
      ["国债逆回购 / 短债", 30, "明显激进版：保留一部分 12 个月流动性缓冲"],
      ["高分红股票", 70, "底线之后更快转向现金流资产"]
    ]);
  }
  if (mode === "cashflow") {
    return weightedChildren(amount, [
      ["高分红股票", 90, "优先提高未来股息现金流和防守权益仓"],
      ["国债逆回购 / 短债", 10, "保留少量再行动能力"]
    ]);
  }
  return weightedChildren(amount, [
    ["现金 / 货币基金", 5, "成熟期仍保留少量即时流动性"],
    ["国债逆回购 / 短债", 15, "成熟期保留等待机会和补保证金能力"],
    ["高分红股票", 80, "其余进入防守现金流资产"]
  ]);
}

function qqqChildren(amount) {
  return weightedChildren(amount, [["QQQ", 100, "先补长期右尾仓；QLD 只在 120 日均线策略确认后另行执行"]]);
}

function futuresChildren(amount, mode) {
  if (mode === "top_up") {
    return weightedChildren(amount, [["补保证金 / 期货账户权益", 100, "风险度超过观察线时先修复账户安全垫"]]);
  }
  return weightedChildren(amount, [
    ["期货账户资金", 55, "对应第一手 IC 起步账户"],
    ["备用补资池", 45, "给后续下跌和 IM 候选保留缓冲"]
  ]);
}

function putChildren(amount) {
  return weightedChildren(amount, [["年度保险预算池", 100, "具体合约仍按到期日、行权价和 Delta 另行选择"]]);
}

function pushAllocation(items, total, name, amount, reason, children = []) {
  const value = Math.max(amount, 0);
  if (value <= 0) return 0;
  items.push({
    name,
    weight: ratio(value, total),
    amount: value,
    reason,
    children
  });
  return value;
}

function allocationPlanForAmount(data, calc, amount) {
  const total = Math.max(amount, 0);
  const items = [];
  let remaining = total;
  const take = (gapValue) => Math.min(remaining, Math.max(gapValue, 0));
  if (total <= 0) return items;

  if (remaining > 0 && calc.futuresTopUpGap > 0 && (calc.marginRiskInvalid || calc.marginRisk > 57)) {
    const value = take(calc.futuresTopUpGap);
    remaining -= pushAllocation(items, total, "IC/IM", value, "期货风险度先于新增配置，优先补回 55% 风险度以内", futuresChildren(value, "top_up"));
  }
  if (remaining > 0 && calc.cashGap6 > 0) {
    const value = take(calc.cashGap6);
    remaining -= pushAllocation(items, total, "高分红 / 防守现金流", value, `硬现金距 6 个月生活费还差 ${yuan(calc.cashGap6)}`, defensiveChildren(value, "hard_cash"));
  }
  if (remaining > 0 && calc.qqqGap5 > 0) {
    const value = take(calc.qqqGap5);
    remaining -= pushAllocation(items, total, "QQQ / QLD", value, `右尾仓距总资产 5% 起步线还差 ${yuan(calc.qqqGap5)}`, qqqChildren(value));
  }
  if (remaining > 0 && calc.liquidGap12 > 0) {
    const value = take(calc.liquidGap12);
    remaining -= pushAllocation(items, total, "高分红 / 防守现金流", value, `现金 + 逆回购距 12 个月安全垫还差 ${yuan(calc.liquidGap12)}`, defensiveChildren(value, "liquidity"));
  }
  if (remaining > 0 && (calc.highDividendGap30 > 0 || calc.divGap > 0)) {
    const value = calc.highDividendGap30 > 0 ? take(calc.highDividendGap30) : remaining;
    remaining -= pushAllocation(items, total, "高分红 / 防守现金流", value, "高分红股票仍未形成足够防守现金流主体", defensiveChildren(value, "cashflow"));
  }
  if (remaining > 0 && calc.futuresGap > 0 && calc.liquidGap12 <= 0) {
    const value = take(calc.futuresGap);
    remaining -= pushAllocation(items, total, "IC/IM", value, `期货专项资金池距 100 万还差 ${yuan(calc.futuresGap)}`, futuresChildren(value, "pool"));
  }
  if (remaining > 0 && calc.putBudgetGap > 0) {
    const value = take(calc.putBudgetGap);
    remaining -= pushAllocation(items, total, "SPY Put", value, `年度保险预算目标 ${yuan(calc.putBudgetTarget)}，当前还未补满`, putChildren(value));
  }
  if (remaining > 0) {
    remaining -= pushAllocation(items, total, "高分红 / 防守现金流", remaining, "硬缺口已满足，剩余资金先进入防守现金流待机层", defensiveChildren(remaining, "mature"));
  }
  return items;
}

function allocationPlan(data, calc, stage) {
  void stage;
  return allocationPlanForAmount(data, calc, data.newMoney);
}

function buildActions(data, calc, stage) {
  const actions = [stage.reason];
  if (calc.cashGap6 > 0) actions.push(`优先补硬现金 ${yuan(calc.cashGap6)}，未满 6 个月前不启动杠杆。`);
  if (calc.qqqPct < 5 && calc.cashGap6 === 0) actions.push(`QQQ 口径按当前总资产计算；还差 ${yuan(calc.qqqGap5)} 到 5% 起步线。`);
  if (calc.divGap > 0) actions.push(`过去 12 个月股息/利息距 1.2 倍年支出还差 ${yuan(calc.divGap)}。`);
  if (data.totalAssets < 200) actions.push("SPY 深度虚值 put 暂缓预算化，先把现金流和期货资金池做出来。");
  else if (data.totalAssets < 300) actions.push("SPY put 可小额制度化，年度预算以总资产 0.5% - 1% 为上限。");
  else actions.push("SPY put 可进入正式保险层，年度预算约总资产 1.5% - 2%。");

  const icMissing = [];
  if (calc.hardMonths < 6) icMissing.push("硬现金不足 6 个月");
  if (calc.liquidMonths < 12) icMissing.push("现金 + 逆回购不足 12 个月");
  if (data.futuresPool < 100) icMissing.push("IC/IM 资金池不足 100 万");
  if (data.icPb > 30) icMissing.push("IC PB 百分位高于 30%");
  actions.push(icMissing.length === 0 ? "第一手 IC 的前置条件基本满足，下一步应复算保证金安全垫。" : `第一手 IC 仍暂缓，缺口：${icMissing.join("、")}。`);

  if (data.hasIc && data.imPb <= 20) actions.push("IM 已进入候选区，但仍必须按“加完后再跌 20%”复算额外补资。");
  else if (!data.hasIc && data.imPb <= 20) actions.push("IM 虽进入候选区，但没有 IC 底仓前不直接跳到 IM。");
  else actions.push("IM 暂不新增，只跟踪 PB 百分位和期货风险度。");

  if (calc.marginRiskInvalid) actions.push("期货账户占用保证金大于 0，但权益为 0；风险度不可计算，必须先修正权益或补资。");
  else if (calc.marginRisk > 70) actions.push("期货风险度已超过 70%，若不补资应优先缩减 IM 或降低敞口。");
  else if (calc.marginRisk > 57) actions.push("期货风险度已超过 57% 观察线，应准备小额补资。");
  if (calc.sameSourcePct > 55) actions.push("A 股同源风险偏高，高分红股票和股指期货敞口需要合并看待。");
  return actions;
}

const termDefinitions = {
  "PB 分位": "市净率在历史区间中的相对位置，越低通常代表估值越便宜。",
  "PB分位": "市净率在历史区间中的相对位置，越低通常代表估值越便宜。",
  "IC PB 分位": "IC 指数市净率在历史区间中的相对位置，低分位才进入开仓评估。",
  "IM PB 分位": "IM 指数市净率在历史区间中的相对位置，低分位才进入加仓评估。",
  "年化贴水": "期货价格低于现货价格形成的持有收益，按剩余天数折算成年化比例。",
  "同源风险": "看似不同的资产实际暴露在同一市场或行业风险上，压力时可能同时下跌。",
  "风险度": "占用保证金除以账户权益，用来观察期货账户的安全垫。",
  "期货风险度": "占用保证金除以账户权益，用来观察期货账户的安全垫。",
  "净投入": "外部转入和买卖流水推导出的历史成本，不等同于当前市值。",
  "内部划入/划出": "组合内部现金和资产之间调拨，不计为外部现金流。",
  "右尾": "少量仓位暴露于长期大涨机会，用来获取非线性上行收益。",
  "右尾权重": "QQQ/QLD 等成长仓占总资产的比例。",
  "右尾仓位": "QQQ/QLD 等成长仓的当前暴露，用来保留长期上行机会。",
  "120 日均线": "过去 120 个交易日价格均值，用作趋势是否成立的参考线。",
  Delta: "期权价格对标的价格变化的敏感度，绝对值越大，方向暴露越强。",
  "移仓窗口": "临近交割前把旧合约换到新合约的操作观察期。"
};

function termLabel(label) {
  const text = String(label || "");
  const definition = termDefinitions[text];
  if (!definition) return escapeHtml(text);
  return `<span class="term" tabindex="0" aria-label="${escapeHtml(`${text}：${definition}`)}">${escapeHtml(text)}<span class="term-tip">${escapeHtml(definition)}</span></span>`;
}

function termHelp(term) {
  const definition = termDefinitions[term];
  if (!definition) return "";
  return `<span class="term-help" tabindex="0" aria-label="${escapeHtml(`${term}：${definition}`)}">?<span class="term-tip">${escapeHtml(definition)}</span></span>`;
}

function closeTermTips(except = null) {
  document.querySelectorAll(".term.is-open, .term-help.is-open").forEach((element) => {
    if (element !== except) element.classList.remove("is-open");
  });
}

function handleTermToggle(event) {
  const term = event.target.closest(".term, .term-help");
  if (!term) {
    closeTermTips();
    return false;
  }
  if (!term.querySelector(".term-tip")) return false;
  event.preventDefault();
  event.stopPropagation();
  const shouldOpen = !term.classList.contains("is-open");
  closeTermTips(term);
  term.classList.toggle("is-open", shouldOpen);
  return true;
}

function metric(label, value, hint, status = "") {
  return `
    <article class="metric ${status}">
      <div class="label">${termLabel(label)}</div>
      <div class="value">${escapeHtml(value)}</div>
      <div class="hint">${escapeHtml(hint)}</div>
    </article>
  `;
}

function row(a, b, c, d) {
  return `<tr><td>${escapeHtml(a)}</td><td>${escapeHtml(b)}</td><td>${escapeHtml(c)}</td><td>${escapeHtml(d)}</td></tr>`;
}

function allocationRows(items) {
  if (!items.length) return row("暂无可分配金额", "-", "-", "现金池为负或下一笔新钱为 0");
  return items.map((item) => {
    const parent = `<tr class="allocation-parent"><td>${escapeHtml(item.name)}</td><td>${escapeHtml(pct(item.weight))}</td><td>${escapeHtml(yuan(item.amount))}</td><td>${escapeHtml(item.reason)}</td></tr>`;
    const children = (item.children || []).map((child) => (
      `<tr class="allocation-child"><td>↳ ${escapeHtml(child.name)}</td><td>${escapeHtml(pct(child.weight))}</td><td>${escapeHtml(yuan(child.amount))}</td><td>${escapeHtml(child.reason)}</td></tr>`
    )).join("");
    return parent + children;
  }).join("");
}

function allocationAdviceTable(title, subtitle, items) {
  return `
    <div class="table-wrap allocation-advice">
      <h2>${escapeHtml(title)}</h2>
      <p class="notice">${escapeHtml(subtitle)}</p>
      <table>
        <thead><tr><th>方向</th><th>比例</th><th>金额</th><th>原因</th></tr></thead>
        <tbody>${allocationRows(items)}</tbody>
      </table>
    </div>
  `;
}

function moduleIcon(type, label = "") {
  const title = label ? `<title>${escapeHtml(label)}</title>` : "";
  const common = `viewBox="0 0 48 48" aria-hidden="true" focusable="false"`;
  if (type === "income") {
    return `<svg class="module-icon-svg icon-income" ${common}>${title}<path d="M8 17h32v19H8z" /><path d="M13 17v-5h22v5" /><circle cx="24" cy="27" r="5" /><path d="M14 24h4M30 24h4" /></svg>`;
  }
  if (type === "trend") {
    return `<svg class="module-icon-svg icon-trend" ${common}>${title}<path d="M8 36h32" /><path d="M10 31l8-9 7 5 12-15" /><path d="M31 12h6v6" /><path d="M13 36V24M23 36V27M33 36V17" /></svg>`;
  }
  if (type === "shield") {
    return `<svg class="module-icon-svg icon-shield" ${common}>${title}<path d="M24 7l15 6v10c0 10-6 16-15 19C15 39 9 33 9 23V13z" /><path d="M16 29c5-1 8-5 10-13 2 8 5 12 10 13" /></svg>`;
  }
  if (type === "futures") {
    return `<svg class="module-icon-svg icon-futures" ${common}>${title}<circle cx="24" cy="24" r="16" /><path d="M24 11v26M12 24h24" /><path d="M16 16l16 16M32 16L16 32" /></svg>`;
  }
  return `<svg class="module-icon-svg" ${common}>${title}<rect x="10" y="10" width="28" height="28" rx="7" /><path d="M17 24h14" /></svg>`;
}

function isDesktopMode() {
  return document.body.classList.contains("desktop-browser");
}

function subpageGroup(tab) {
  if (tab === "overview") return "overview";
  if (tab === "reports") return "reports";
  return "module";
}

function normalizeSubpage(tab, subpage) {
  const labels = subpageLabels[subpageGroup(tab)] || {};
  return labels[subpage] ? subpage : "full";
}

function activeSubpage() {
  return isDesktopMode() ? normalizeSubpage(currentTab, currentSubpage) : "full";
}

function subpageLabel(tab, subpage = activeSubpage()) {
  const labels = subpageLabels[subpageGroup(tab)] || {};
  return labels[subpage] || labels.full || "全部总览";
}

function mobileSectionNav(tab) {
  if (isDesktopMode()) return "";
  const itemsByGroup = {
    overview: [
      ["现金", ".cash-pool-panel"],
      ["风险", ".dashboard-split"],
      ["参数", ".overview-settings"],
      ["估值", ".valuation-band"],
      ["图表", ".chart-grid"],
      ["动作", ".decision-panel"]
    ],
    reports: [
      ["图表", ".report-chart-grid"],
      ["矩阵", ".report-matrix-panel"],
      ["排行", ".leaderboard-panel"],
      ["估值", ".position-overview-panel"],
      ["流水", ".recent-activity-panel"]
    ],
    module: [
      ["概览", ".module-dashboard"],
      ["录入", ".entry-panel"],
      ["分布", ".bucket-panel"],
      ...(tab === "ic" ? [["加仓", ".futures-add-panel"]] : []),
      ["估值", ".position-panel"],
      ["流水", ".module-ledger-panel"]
    ]
  };
  const items = itemsByGroup[subpageGroup(tab)] || [];
  return `
    <nav class="mobile-section-nav" aria-label="当前页区块跳转">
      ${items.map(([label, selector]) => `<button type="button" data-scroll-target="${escapeHtml(selector)}">${escapeHtml(label)}</button>`).join("")}
    </nav>
  `;
}

function updateNavigationState() {
  const subpage = activeSubpage();
  document.querySelectorAll(".nav-item").forEach((button) => {
    button.classList.toggle("is-active", button.dataset.tab === currentTab);
  });
  document.querySelectorAll(".desktop-nav-group").forEach((group) => {
    group.classList.toggle("is-open", group.dataset.navGroup === currentTab);
  });
  document.querySelectorAll(".desktop-subitem").forEach((button) => {
    button.classList.toggle("is-active", button.dataset.tab === currentTab && normalizeSubpage(currentTab, button.dataset.subpage || "full") === subpage);
  });
}

function setActiveTab(tab, subpage = "full") {
  currentTab = tab;
  currentSubpage = normalizeSubpage(tab, subpage);
  editingId = null;
  updateNavigationState();
  render();
  window.scrollTo({ top: 0, left: 0, behavior: "auto" });
}

function render() {
  const config = moduleConfigs[currentTab] || moduleConfigs.overview;
  currentSubpage = normalizeSubpage(currentTab, currentSubpage);
  updateNavigationState();
  const subpage = activeSubpage();
  const isFullOverview = subpage === "full";
  appRoot.classList.toggle("overview-layout", currentTab === "overview" && isFullOverview);
  appRoot.classList.toggle("module-overview-layout", currentTab !== "overview" && currentTab !== "reports" && isFullOverview);
  appRoot.classList.toggle("report-overview-layout", currentTab === "reports" && isFullOverview);
  pageTitle.textContent = isDesktopMode() && subpage !== "full" ? `${config.title} - ${subpageLabel(currentTab, subpage)}` : config.title;
  if (currentTab === "overview") renderOverview(subpage);
  else if (currentTab === "reports") renderReports(subpage);
  else renderLedgerModule(config, subpage);
  renderLocalServiceGuide();
}

function moduleSummaryRows(data, calc) {
  return [
    {
      key: "dividend",
      icon: "income",
      title: "高分红现金流",
      value: data.moduleTotals.dividend,
      share: ratio(data.moduleTotals.dividend, data.totalAssets),
      hint: `股息覆盖 ${pct(calc.divCoverage)}`
    },
    {
      key: "qqq",
      icon: "trend",
      title: "QQQ / QLD",
      value: data.moduleTotals.qqq,
      share: ratio(data.moduleTotals.qqq, data.totalAssets),
      hint: qqqStatus(calc.qqqPct)
    },
    {
      key: "put",
      icon: "shield",
      title: "深度虚值 Put",
      value: data.moduleTotals.put,
      share: ratio(data.moduleTotals.put, data.totalAssets),
      hint: `年度预算 ${pct(calc.putPct)}`
    },
    {
      key: "ic",
      icon: "futures",
      title: "IC/IM 增强",
      value: data.futuresEquity || data.futuresPool,
      share: ratio(data.futuresEquity || data.futuresPool, data.totalAssets),
      hint: `风险度 ${marginRiskText(calc)}`
    }
  ].map((item) => {
    const target = moduleTargetInfo(item.key, data, calc, item.value);
    return {
      ...item,
      targetValue: target.target,
      targetMode: target.mode,
      targetHint: target.text,
      targetMeta: targetProgressMeta(item.value, target.target, target.mode)
    };
  });
}

function assetAccountTree(data, calc) {
  return `
    <section class="account-tree panel">
      <div class="section-head">
        <h2>资产账户树</h2>
        <span>按策略模块看仓位</span>
      </div>
      <div class="account-list">
        ${moduleSummaryRows(data, calc).map((item) => `
          <button class="account-row account-${escapeHtml(item.key)}" type="button" data-tab="${escapeHtml(item.key)}">
            <span class="account-icon">${moduleIcon(item.icon, item.title)}</span>
            <span class="account-main">
              <strong>${escapeHtml(item.title)}</strong>
              <small>${escapeHtml(`${item.hint} · ${item.targetHint}`)}</small>
              ${targetTrack(item.value, item.targetValue, item.targetMode, "account-track")}
            </span>
            <span class="account-number">
              <strong>${escapeHtml(yuan(item.value))}</strong>
              <small class="${escapeHtml(item.targetMeta.statusClass)}">${escapeHtml(`${pct(item.share)} · ${item.targetMeta.label}`)}</small>
            </span>
          </button>
        `).join("")}
      </div>
    </section>
  `;
}

function targetProgressPanel(data, calc) {
  const items = [
    ["硬现金", calc.hardMonths, 6, `${format(calc.hardMonths)} 月 / 6 月`],
    ["流动安全垫", calc.liquidMonths, 12, `${format(calc.liquidMonths)} 月 / 12 月`],
    ["股息覆盖", calc.divCoverage, 120, `${pct(calc.divCoverage)} / 120%`],
    ["QQQ 目标", calc.qqqPct, 10, `${pct(calc.qqqPct)} / 10%`],
    ["期货资金池", data.futuresPool, 100, `${yuan(data.futuresPool)} / 100 万`]
  ];
  return `
    <section class="panel target-panel">
      <div class="section-head">
        <h2>目标进度</h2>
        <span>当前值 / 个人目标</span>
      </div>
      <div class="target-list">
        ${items.map(([label, current, target, text]) => {
          const width = Math.min(100, Math.max(0, ratio(current, target)));
          return `
            <div class="target-row">
              <div><strong>${termLabel(label)}</strong><span>${escapeHtml(text)}</span></div>
              <div class="target-track"><span style="width:${width}%"></span></div>
            </div>
          `;
        }).join("")}
      </div>
    </section>
  `;
}

function riskChecklist(data, calc) {
  const checks = [
    {
      label: "硬现金底线",
      status: calc.hardMonths >= 6 ? "good" : "warn",
      text: calc.hardMonths >= 6 ? "已覆盖 6 个月生活支出" : `还差 ${yuan(calc.cashGap6)} 到 6 个月硬现金`
    },
    {
      label: "右尾仓位",
      status: calc.qqqPct <= 15 && calc.qqqPct >= 5 ? "good" : "warn",
      text: calc.qqqPct < 5 ? `QQQ 距 5% 起步线还差 ${yuan(calc.qqqGap5)}` : qqqStatus(calc.qqqPct)
    },
    {
      label: "期货风险度",
      status: calc.marginRisk <= 57 ? "good" : calc.marginRisk <= 70 ? "warn" : "danger",
      text: calc.marginRiskInvalid ? marginRiskHint(calc) : calc.marginRisk > 0 ? `当前风险度 ${marginRiskText(calc)}` : "尚未从 IC/IM 流水和日级行情推导出风险度"
    },
    {
      label: "同源风险",
      status: calc.sameSourcePct <= 45 ? "good" : calc.sameSourcePct <= 60 ? "warn" : "danger",
      text: `A股同源风险 ${pct(calc.sameSourcePct)}`
    },
    {
      label: "保险预算",
      status: data.totalAssets < 200 || calc.putPct <= 2 ? "good" : "danger",
      text: data.totalAssets < 200 ? "总资产不足时 Put 暂不预算化" : `Put 保费占比 ${pct(calc.putPct)}`
    }
  ];
  return `
    <section class="panel risk-panel">
      <div class="section-head">
        <h2>风险清单</h2>
        <span>先保活，再增强</span>
      </div>
      <div class="risk-list">
        ${checks.map((item) => `
          <div class="risk-item risk-${escapeHtml(item.status)}">
            <span></span>
            <div><strong>${termLabel(item.label)}</strong><small>${escapeHtml(item.text)}</small></div>
          </div>
        `).join("")}
      </div>
    </section>
  `;
}

function cashPoolPanel(ledger, data) {
  const calc = calculate(data);
  const distributableCash = Math.max(data.cashBalance, 0);
  const cashAdvice = allocationPlanForAmount(data, calc, distributableCash);
  const cashEntries = ledger.entries
    .filter((entry) => isCashPoolEntry(entry))
    .slice()
    .sort((a, b) => String(b.date || "").localeCompare(String(a.date || "")))
    .slice(0, 5);
  const rows = cashEntries.map((entry) => `
    <tr>
      <td>${escapeHtml(entry.date || "-")}</td>
      <td>${escapeHtml(actionLabels[entry.action] || entry.action || "-")}</td>
      <td>${escapeHtml(yuanTextFromWan(safeAmount(entry.amount)))}</td>
      <td>${escapeHtml(entry.note || "-")}</td>
    </tr>
  `).join("");
  return `
    <section class="panel cash-pool-panel">
      <div class="section-head">
        <h2>全局现金池</h2>
        <span>统一转账入口</span>
      </div>
      <div class="cash-pool-grid">
        <article>
          <span>当前现金池</span>
          <strong>${escapeHtml(yuan(data.cashBalance))}</strong>
          <small>${escapeHtml(yuanTextFromWan(data.cashBalance))}</small>
        </article>
        <article>
          <span>记账规则</span>
          <strong>买入自动扣款</strong>
          <small>转入/转出只在这里登记；模块买入会自动划走现金。</small>
        </article>
      </div>
      ${allocationAdviceTable("现金池分配提示", `按当前现金池 ${yuan(distributableCash)} 做缺口优先分配；高分红/防守现金流采用明显激进子资产配方。`, cashAdvice)}
      <form id="cashTransferForm" class="cash-transfer-form" novalidate>
        <label>日期<input type="date" name="date" value="${escapeHtml(today())}" required /></label>
        <label>动作<select name="action"><option value="deposit">转入</option><option value="withdraw">转出</option></select></label>
        <label>金额（元）<input type="number" name="amountYuan" min="0" step="0.01" required /></label>
        <label class="entry-note">备注<input name="note" placeholder="工资、补资、取出等" /></label>
        <div class="form-error" data-entry-errors hidden></div>
        <div class="form-actions"><button type="submit">记录转账</button></div>
      </form>
      ${rows ? `<div class="table-wrap cash-transfer-history"><table><thead><tr><th>日期</th><th>动作</th><th>金额</th><th>备注</th></tr></thead><tbody>${rows}</tbody></table></div>` : `<p class="notice">还没有全局转账记录。</p>`}
    </section>
  `;
}

function recentActivity(entries, limit = 8) {
  const rows = entries
    .slice()
    .sort((a, b) => String(b.date || "").localeCompare(String(a.date || "")))
    .slice(0, limit)
    .map((entry) => `
      <tr>
        <td>${escapeHtml(entry.date || "-")}</td>
        <td>${escapeHtml((moduleConfigs[entry.module] || {}).title || entry.module || "-")}</td>
        <td>${escapeHtml(actionLabels[entry.action] || entry.action || "-")}</td>
        <td>${escapeHtml(entry.symbol || entry.name || "-")}</td>
        <td>${escapeHtml(yuan(safeAmount(entry.amount)))}</td>
      </tr>
    `).join("");
  return `
    <section class="panel recent-activity-panel">
      <div class="section-head">
        <h2>最近流水</h2>
        <span>${escapeHtml(String(Math.min(entries.length, limit)))} / ${escapeHtml(String(entries.length))} 笔</span>
      </div>
      ${rows ? `<div class="table-wrap"><table><thead><tr><th>日期</th><th>模块</th><th>动作</th><th>标的</th><th>金额</th></tr></thead><tbody>${rows}</tbody></table></div>` : `<p class="notice">还没有流水。</p>`}
    </section>
  `;
}

function keepAppChildren(nodes) {
  appRoot.replaceChildren(...nodes.filter(Boolean));
}

function pruneOverviewSubpage(subpage) {
  if (subpage === "full") return;
  const children = Array.from(appRoot.children);
  const [hero, onboarding, cash, split, settings, target, metrics, strategy, valuation, charts, work, decision] = children;
  const keepMap = {
    snapshot: [hero, metrics, strategy],
    cash: [cash],
    risk: [split, target],
    settings: [settings],
    charts: [valuation, charts],
    actions: [work, decision]
  };
  keepAppChildren(keepMap[subpage] || children);
}

function pruneReportsSubpage(subpage) {
  if (subpage === "full") return;
  const hero = appRoot.children[0];
  const main = appRoot.querySelector(".report-main");
  if (!main) return;
  const [charts, matrix, leaderboard, valuation, activity] = Array.from(main.children);
  const keepMap = {
    charts,
    matrix,
    leaderboard,
    valuation,
    activity
  };
  keepAppChildren([hero, keepMap[subpage]].filter(Boolean));
}

function pruneModuleSubpage(config, subpage) {
  if (subpage === "full") return;
  const hero = appRoot.querySelector(".module-hero");
  const dashboard = appRoot.querySelector(".module-dashboard");
  const summary = appRoot.querySelector(".module-overview-grid");
  const entry = appRoot.querySelector(".entry-panel");
  const buckets = appRoot.querySelector(".bucket-panel");
  const add = appRoot.querySelector(".futures-add-panel");
  const valuation = appRoot.querySelector(".position-panel");
  const ledger = appRoot.querySelector(".module-ledger-panel");
  const icDataPanel = config.module === "ic" && dashboard ? dashboard.querySelector(".data-panel") : null;
  const keepMap = {
    overview: [hero, dashboard, summary],
    entry: [entry],
    buckets: [buckets],
    add: config.module === "ic" ? [add] : [hero, dashboard, summary],
    valuation: [valuation, icDataPanel],
    ledger: [ledger]
  };
  keepAppChildren(keepMap[subpage] || [hero, dashboard, summary]);
}

function onboardingPanel(ledger, data) {
  if (ledger.entries.length > 0 || data.totalAssets > 0) return "";
  return `
    <section class="panel onboarding-panel">
      <div class="section-head">
        <h2>从这里开始</h2>
        <span>空账本引导</span>
      </div>
      <div class="onboarding-steps">
        <article>
          <strong>1. 先设年生活支出</strong>
          <p>这决定硬现金、流动安全垫和股息覆盖的目标线。</p>
          <button type="button" data-tab="overview" data-subpage="settings">去设置参数</button>
        </article>
        <article>
          <strong>2. 用示例看完整效果</strong>
          <p>示例会生成持仓、估值和流水，便于理解页面结构。</p>
          <button type="button" data-action="load-sample-data">载入示例</button>
        </article>
        <article>
          <strong>3. 录入第一笔资产</strong>
          <p>先从现金或高分红开始，后续再补 QQQ、Put、IC/IM。</p>
          <button type="button" data-tab="dividend" data-subpage="entry">录第一笔</button>
        </article>
      </div>
    </section>
  `;
}

function renderOverview(subpage = "full") {
  const ledger = loadLedger();
  const data = summarizeLedger(ledger);
  const calc = calculate(data);
  const stage = detectStage(data, calc);
  const allocations = allocationPlan(data, calc, stage);
  const actions = buildActions(data, calc, stage);
  const compactSettings = isDesktopMode() && subpage === "full";

  appRoot.innerHTML = `
    <section class="wealth-hero">
      <div class="wealth-hero-main">
        <span>个人混合杠铃净值（当前市值）</span>
        <strong>${escapeHtml(yuan(data.totalAssets))}</strong>
        <p>${escapeHtml(stage.name)}，${escapeHtml(stage.reason)}。总资产优先使用持仓估值，未估值标的回退到流水${termLabel("净投入")}。</p>
        <div class="wealth-actions">
          <button type="button" data-tab="reports">打开报表</button>
          <button type="button" data-tab="dividend" data-subpage="entry">记录投资</button>
        </div>
      </div>
      <div class="wealth-kpis">
        <div><span>过去12个月收入</span><strong>${escapeHtml(yuan(data.annualDividend))}</strong><small>股息 / 利息到账</small></div>
        <div><span>${termLabel("净投入")}</span><strong>${escapeHtml(yuan(data.investedCost))}</strong><small>历史流水推导成本</small></div>
        <div><span>浮盈亏</span><strong>${escapeHtml(yuan(data.unrealizedPnl))}</strong><small>${escapeHtml(pct(data.unrealizedPnlPct))}</small></div>
        <div><span>防守资产</span><strong>${escapeHtml(pct(calc.safetyPct))}</strong><small>现金 + 类现金 + 高分红</small></div>
        <div><span>${termLabel("右尾权重")}</span><strong>${escapeHtml(pct(calc.qqqPct))}</strong><small>${escapeHtml(qqqStatus(calc.qqqPct))}</small></div>
        <div><span>${termLabel("期货风险度")}</span><strong>${escapeHtml(marginRiskText(calc))}</strong><small>${escapeHtml(marginRiskHint(calc))}</small></div>
      </div>
    </section>

    ${mobileSectionNav("overview")}

    <div class="overview-onboarding">${onboardingPanel(ledger, data)}</div>

    ${cashPoolPanel(ledger, data)}

    <section class="dashboard-split">
      ${assetAccountTree(data, calc)}
      ${riskChecklist(data, calc)}
    </section>

    <section class="panel overview-settings">
      <div class="section-head">
        <h2>核心参数</h2>
          <span id="saveState">${compactSettings ? "摘要 / 备份状态" : "本地保存"}</span>
      </div>
        ${compactSettings ? overviewSettingsSummaryPanel(ledger) : `${settingsForm(ledger.settings)}${ledgerBackupPanel(ledger)}`}
    </section>

    ${targetProgressPanel(data, calc)}

    <section class="metrics-grid">
      ${[
        metric("硬现金", `${format(calc.hardMonths)} 月`, "目标 >= 6 月", classify(calc.hardMonths, 6, 3)),
        metric("流动安全垫", `${format(calc.liquidMonths)} 月`, "现金 + 逆回购目标 >= 12 月", classify(calc.liquidMonths, 12, 6)),
        metric("股息覆盖", pct(calc.divCoverage), "过去12个月收入 / 年支出", classify(calc.divCoverage, 120, 80)),
        metric("QQQ 权重", pct(calc.qqqPct), qqqStatus(calc.qqqPct), calc.qqqPct > 15 ? "status-danger" : calc.qqqPct >= 5 ? "status-good" : "status-warn"),
        metric("Put 预算", pct(calc.putPct), "年度保险费 / 当前总资产", classify(calc.putPct, 0.5, 2)),
        metric("期货风险度", marginRiskText(calc), marginRiskHint(calc), classify(calc.marginRisk, 50, 70, true)),
        metric("同源风险", pct(calc.sameSourcePct), "高分红股票 + 期货名义", classify(calc.sameSourcePct, 45, 60, true))
      ].join("")}
    </section>

    <section class="strategy-grid">
      ${overviewStrategyCards(data, calc).join("")}
    </section>

    <section class="valuation-band panel">
      <div class="section-head">
        <h2>IC/IM 估值</h2>
        <div class="valuation-tools">
          <span id="valuationStatus">未读取</span>
          <button id="loadValuationJson" type="button">读取估值 JSON</button>
          <label class="file-button">导入 JSON<input id="valuationFile" type="file" accept="application/json,.json" /></label>
        </div>
      </div>
      <div class="valuation-grid" id="valuationCards"></div>
    </section>

    <section class="chart-grid">
      <article class="chart-panel">
        <div class="chart-title"><h3>资产结构</h3><span>当前快照</span></div>
        <canvas id="allocationPie" width="520" height="320"></canvas>
        <div class="legend" id="pieLegend"></div>
      </article>
      <article class="chart-panel">
        <div class="chart-title"><h3>目标差距</h3><span>当前值 / 目标值</span></div>
        <canvas id="gapBar" width="720" height="320"></canvas>
      </article>
      <article class="chart-panel chart-panel-wide">
        <div class="chart-title">
          <div><h3>总资产 / 净值收益率</h3><span id="historyCount">0 条快照</span></div>
          ${historyPeriodControls()}
        </div>
        <canvas id="historyLine" width="1120" height="320"></canvas>
        <p class="notice">净值收益率只剔除全局现金池的外部转入 / 转出；买入、卖出、分红和费用会自动影响现金池。</p>
      </article>
    </section>

    <section class="work-grid">
      <div class="table-wrap">
        <h2>缺口</h2>
        <table>
          <thead><tr><th>项目</th><th>目标</th><th>当前</th><th>缺口</th></tr></thead>
          <tbody>
            ${[
              row("硬现金", "6 个月支出", `${format(calc.hardMonths)} 月`, yuan(calc.cashGap6)),
              row("流动安全垫", "12 个月支出", `${format(calc.liquidMonths)} 月`, yuan(calc.liquidGap12)),
              row("税后股息", "年支出 1.2 倍", yuan(data.annualDividend), yuan(calc.divGap)),
              row("QQQ 起步线", "当前总资产 5%", pct(calc.qqqPct), yuan(calc.qqqGap5)),
              row("QQQ 目标线", "当前总资产 10%", pct(calc.qqqPct), yuan(calc.qqqGap10)),
              row("IC/IM 资金池", "100 万起步线", yuan(data.futuresPool), yuan(calc.futuresGap))
            ].join("")}
          </tbody>
        </table>
      </div>
      ${allocationAdviceTable("下一笔钱", `按下一笔新钱 ${yuan(Math.max(data.newMoney, 0))} 生成真实操作提示；先补硬缺口，再进入增强和保险。`, allocations)}
    </section>

    <section class="panel decision-panel">
      <h2>动作</h2>
      <ol>${actions.map((item) => `<li>${escapeHtml(item)}</li>`).join("")}</ol>
      <p class="notice">本页只复现你的个人规则与压力检查，不构成投资建议；涉及估值、保证金率和期权价格时仍需用最新数据复核。</p>
    </section>
  `;

  pruneOverviewSubpage(subpage);
  renderValuation();
  renderCharts(data, calc);
}

function reportMatrixRows(data, calc) {
  const rows = [
    ["现金流底座", yuan(data.highDividend), pct(calc.highDividendPct), calc.divCoverage >= 100 ? "股息接近覆盖生活" : `股息覆盖 ${pct(calc.divCoverage)}`],
    ["右尾成长", yuan(data.qqq), pct(calc.qqqPct), qqqStatus(calc.qqqPct)],
    ["黑天鹅保险", yuan(data.spyPutBudget), pct(calc.putPct), data.totalAssets >= 200 ? "可制度化预算" : "暂缓预算化"],
    ["期货增强", yuan(data.futuresEquity || data.futuresPool), pct(ratio(data.futuresEquity || data.futuresPool, data.totalAssets)), data.futuresPool >= 100 ? `风险度 ${marginRiskText(calc)}` : `资金池还差 ${yuan(calc.futuresGap)}`],
    ["同源风险", yuan(data.otherAHighDividend + data.futuresNotional), pct(calc.sameSourcePct), calc.sameSourcePct <= 45 ? "压力可控" : "需要合并观察"]
  ];
  return rows.map((item) => row(item[0], item[1], item[2], item[3])).join("");
}

function historyPeriodControls() {
  const current = historyViewPeriod();
  const options = [
    ["day", "日"],
    ["week", "周"],
    ["month", "月"]
  ];
  return `
    <div class="segmented-control" aria-label="收益率曲线维度">
      ${options.map(([value, label]) => `<button type="button" data-history-period="${value}" class="${current === value ? "is-active" : ""}">${label}</button>`).join("")}
    </div>
  `;
}

function instrumentLeaderboard(positions) {
  const rows = positions
    .slice()
    .sort((a, b) => Math.abs(b.marketValue) - Math.abs(a.marketValue))
    .slice(0, 8)
    .map((item) => row(item.symbol || "-", item.name || "-", yuan(item.marketValue), `${item.entryCount} 笔 / ${yuan(item.unrealizedPnl)}`))
    .join("");
  return `
    <section class="panel leaderboard-panel">
      <div class="section-head">
        <h2>标的排行</h2>
        <span>按当前市值排序</span>
      </div>
      ${rows ? `<div class="table-wrap"><table><thead><tr><th>代码</th><th>名称</th><th>当前市值</th><th>流水 / 浮盈亏</th></tr></thead><tbody>${rows}</tbody></table></div>` : `<p class="notice">还没有可估值持仓。</p>`}
    </section>
  `;
}

function positionValuationOverview(positions) {
  const rows = positions
    .slice()
    .sort((a, b) => Math.abs(b.marketValue) - Math.abs(a.marketValue))
    .map((position) => `
      <tr>
        <td>${escapeHtml((moduleConfigs[position.module] || {}).title || position.module || "-")}</td>
        <td>${escapeHtml(position.symbol || "-")}<small>${escapeHtml(position.name || "")}</small></td>
        <td>${escapeHtml(chartValue(position.quantity))}</td>
        <td>${escapeHtml(yuan(position.netInvestment))}</td>
        <td>${escapeHtml(yuan(position.marketValue))}</td>
        <td class="${escapeHtml(position.unrealizedPnl > 0 ? "pnl-good" : position.unrealizedPnl < 0 ? "pnl-danger" : "")}"><strong>${escapeHtml(yuan(position.unrealizedPnl))}</strong><small>${escapeHtml(pct(position.unrealizedPnlPct))}</small></td>
        <td>${escapeHtml(position.valuationUpdatedAt ? displayDateTime(position.valuationUpdatedAt) : "未估值")}</td>
      </tr>
    `).join("");
  return `
    <section class="panel position-overview-panel">
      <div class="section-head">
        <h2>持仓估值总表</h2>
        <span>市值 / 成本 / 浮盈亏</span>
      </div>
      ${rows ? `<div class="table-wrap"><table class="position-overview-table"><thead><tr><th>模块</th><th>标的</th><th>数量</th><th>${termLabel("净投入")}</th><th>当前市值</th><th>浮盈亏</th><th>更新时间</th></tr></thead><tbody>${rows}</tbody></table></div>` : `<p class="notice">还没有可估值持仓。</p>`}
    </section>
  `;
}

function renderReports(subpage = "full") {
  const ledger = loadLedger();
  const data = summarizeLedger(ledger);
  const calc = calculate(data);
  const stage = detectStage(data, calc);
  const actions = buildActions(data, calc, stage);

  appRoot.innerHTML = `
    <section class="wealth-hero report-hero">
      <div class="wealth-hero-main">
        <span>组合报表（当前市值）</span>
        <strong>${escapeHtml(yuan(data.totalAssets))}</strong>
        <p>把净值、资产结构、目标偏离、风险清单、持仓估值和流水集中复盘。</p>
      </div>
      <div class="wealth-kpis">
        <div><span>阶段</span><strong>${escapeHtml(stage.name.replace("：", " "))}</strong><small>${escapeHtml(stage.reason)}</small></div>
        <div><span>现金流覆盖</span><strong>${escapeHtml(pct(calc.divCoverage))}</strong><small>过去12个月收入 / 年支出</small></div>
        <div><span>浮盈亏</span><strong>${escapeHtml(yuan(data.unrealizedPnl))}</strong><small>${escapeHtml(pct(data.unrealizedPnlPct))}</small></div>
        <div><span>流水总数</span><strong>${escapeHtml(String(ledger.entries.length))}</strong><small>本地账本记录</small></div>
      </div>
    </section>

    ${mobileSectionNav("reports")}

    <section class="report-layout">
      <div class="report-main">
        <section class="chart-grid report-chart-grid">
          <article class="chart-panel">
            <div class="chart-title"><h3>资产结构</h3><span>当前快照</span></div>
            <canvas id="allocationPie" width="520" height="320"></canvas>
            <div class="legend" id="pieLegend"></div>
          </article>
          <article class="chart-panel">
            <div class="chart-title"><h3>目标差距</h3><span>当前值 / 目标值</span></div>
            <canvas id="gapBar" width="720" height="320"></canvas>
          </article>
          <article class="chart-panel chart-panel-wide">
            <div class="chart-title">
              <div><h3>总资产 / 净值收益率</h3><span id="historyCount">0 条快照</span></div>
              ${historyPeriodControls()}
            </div>
            <canvas id="historyLine" width="1120" height="320"></canvas>
            <p class="notice">净值收益率只剔除“转入 / 转出”外部现金流；若同时维护现金余额，买入资产时用“内部划出”扣减现金，卖出资产后用“内部划入”增加现金。</p>
          </article>
        </section>

        <section class="panel report-matrix-panel">
          <div class="section-head">
            <h2>策略矩阵</h2>
            <span>金额 / 权重 / 状态</span>
          </div>
          <div class="table-wrap">
            <table>
              <thead><tr><th>层</th><th>金额</th><th>权重</th><th>状态</th></tr></thead>
              <tbody>${reportMatrixRows(data, calc)}</tbody>
            </table>
          </div>
        </section>

        ${instrumentLeaderboard(data.positions)}
        ${positionValuationOverview(data.positions)}
        ${recentActivity(ledger.entries, 10)}
      </div>
      <aside class="report-side">
        ${assetAccountTree(data, calc)}
        ${targetProgressPanel(data, calc)}
        ${riskChecklist(data, calc)}
        <section class="panel decision-panel">
          <h2>动作清单</h2>
          <ol>${actions.slice(0, 6).map((item) => `<li>${escapeHtml(item)}</li>`).join("")}</ol>
        </section>
      </aside>
    </section>
  `;

  pruneReportsSubpage(subpage);
  renderCharts(data, calc);
}

function overviewStrategyCards(data, calc) {
  const cards = [
    {
      key: "dividend",
      icon: "income",
      title: "高分红现金流",
      value: yuan(data.highDividend),
      meta: `股息覆盖 ${pct(calc.divCoverage)}`,
      status: calc.divCoverage >= 120 ? "稳定" : calc.divCoverage >= 80 ? "接近" : "建设",
      hint: calc.divGap > 0 ? `距 1.2 倍年支出还差 ${yuan(calc.divGap)}` : "股息已经能覆盖生活支出的安全线。"
    },
    {
      key: "qqq",
      icon: "trend",
      title: "QQQ / QLD 右尾",
      value: pct(calc.qqqPct),
      meta: `当前 ${yuan(data.qqq)}`,
      status: qqqStatus(calc.qqqPct),
      hint: calc.qqqGap5 > 0 ? `先补到当前总资产 5%，还差 ${yuan(calc.qqqGap5)}` : "保持在 10%-12% 目标区附近，不因短期涨跌频繁折腾。"
    },
    {
      key: "put",
      icon: "shield",
      title: "深度虚值 Put",
      value: pct(calc.putPct),
      meta: `预算 ${yuan(data.spyPutBudget)}`,
      status: data.totalAssets >= 300 ? "正式保险层" : data.totalAssets >= 200 ? "小额制度化" : "暂缓",
      hint: data.totalAssets < 200 ? "先让现金流和期货资金池成形；Put 只做极端风险预算。" : "年度保费按总资产比例上限管理，归零视为保险成本。"
    },
    {
      key: "ic",
      icon: "futures",
      title: "IC/IM 增强",
      value: yuan(data.futuresEquity || data.futuresPool),
      meta: `风险度 ${marginRiskText(calc)} · IC PB ${pct(data.icPb)}`,
      status: data.futuresPool >= 100 && data.icPb <= 30 ? "可复算" : "等待",
      hint: calc.futuresGap > 0 ? `资金池距 100 万还差 ${yuan(calc.futuresGap)}` : "资金池够了也只在低估区评估第一手 IC。"
    }
  ];
  return cards.map((card) => `
    <article class="strategy-card strategy-${escapeHtml(card.key)}">
      <div class="strategy-card-head">
        <div class="strategy-title">${moduleIcon(card.icon, card.title)}<h3>${escapeHtml(card.title)}</h3></div>
        <span>${escapeHtml(card.status)}</span>
      </div>
      ${strategyMiniVisual(card.key)}
      <strong>${escapeHtml(card.value)}</strong>
      <p>${escapeHtml(card.meta)}</p>
      <small>${escapeHtml(card.hint)}</small>
    </article>
  `);
}

function strategyMiniVisual(key) {
  const maps = {
    dividend: `<path d="M18 76V50h24v26M66 76V34h24v42M114 76V22h24v54" /><path d="M20 26c24 10 44 8 64-6s36-10 54 4" />`,
    qqq: `<path d="M18 72C38 68 42 38 62 46s30 26 48-2 30-34 48-18" /><path d="M18 58C50 56 82 50 116 42s34-8 44-6" />`,
    put: `<path d="M18 72H66L96 48L144 26L164 22" /><path d="M18 24c34 8 56 22 78 44s42 18 70 8" /><path d="M66 18V76" />`,
    ic: `<path d="M22 28h46v24h44v24h50" /><circle cx="22" cy="28" r="5" /><circle cx="68" cy="52" r="5" /><circle cx="112" cy="76" r="5" /><path d="M26 86h132" />`
  };
  return `
    <svg class="strategy-spark" viewBox="0 0 180 96" aria-hidden="true" focusable="false">
      <rect x="0" y="0" width="180" height="96" rx="14" />
      <path class="spark-grid" d="M18 76H162M18 52H162M18 28H162" />
      <g>${maps[key] || maps.dividend}</g>
    </svg>
  `;
}

function settingsForm(settings) {
  const s = { ...defaultSettings, ...settings };
  return `
    <form id="settingsForm" class="settings-grid">
      <label>年生活支出（万元）<input type="number" name="annualExpense" min="0" step="0.1" value="${escapeHtml(s.annualExpense)}" /></label>
      <label>下一笔新钱（万元）<input type="number" name="newMoney" min="0" step="0.1" value="${escapeHtml(s.newMoney)}" /></label>
      <label>手工总资产兜底（万元）<input type="number" name="manualTotalAssets" min="0" step="0.1" value="${escapeHtml(s.manualTotalAssets)}" /></label>
      <label>IC PB 百分位（%） ${termHelp("PB 分位")} ${pbSourceBadge(s.icPbSource)} ${adoptPbButton("ic", s.icPbSource)}<input type="number" name="icPb" min="0" max="100" step="0.1" value="${escapeHtml(s.icPb)}" /></label>
      <label>IM PB 百分位（%） ${termHelp("PB 分位")} ${pbSourceBadge(s.imPbSource)} ${adoptPbButton("im", s.imPbSource)}<input type="number" name="imPb" min="0" max="100" step="0.1" value="${escapeHtml(s.imPb)}" /></label>
    </form>
  `;
}

function overviewSettingsSummaryPanel(ledger) {
  const s = { ...defaultSettings, ...ledger.settings };
  const data = summarizeLedger(ledger);
  const calc = calculate(data);
  const serviceReady = ledgerServiceAvailable();
  const mirrorText = serviceReady ? latestBackupLabel() : "未连接";
  const mirrorHint = serviceReady
    ? ledgerBackupState.message
    : "当前入口不会写入 SQLite，完整备份操作在核心参数页。";
  return `
    <div class="settings-summary" id="overviewSettingsSummary">
      <div class="settings-summary-grid">
        <article class="settings-summary-card">
          <span>年生活支出</span>
          <strong>${escapeHtml(format(s.annualExpense))} 万</strong>
          <small>硬现金目标 ${escapeHtml(format(s.annualExpense / 2))} 万</small>
        </article>
        <article class="settings-summary-card">
          <span>下一笔新钱</span>
          <strong>${escapeHtml(format(s.newMoney))} 万</strong>
          <small>用于缺口动作测算</small>
        </article>
        <article class="settings-summary-card">
          <span>PB 分位</span>
          <strong>IC ${escapeHtml(pct(s.icPb))} / IM ${escapeHtml(pct(s.imPb))}</strong>
          <small>IC ${escapeHtml(s.icPbSource === "auto" ? "自动" : "手工")} · IM ${escapeHtml(s.imPbSource === "auto" ? "自动" : "手工")}</small>
        </article>
        <article class="settings-summary-card">
          <span>期货安全</span>
          <strong>${escapeHtml(marginRiskText(calc))}</strong>
          <small>权益 ${escapeHtml(format(data.futuresEquity))} 万 / 保证金 ${escapeHtml(format(data.usedMargin))} 万</small>
        </article>
        <article class="settings-summary-card">
          <span>浏览器账本</span>
          <strong>${escapeHtml(String(ledger.entries.length))} 笔</strong>
          <small>localStorage 主存</small>
        </article>
        <article class="settings-summary-card">
          <span>SQLite 镜像</span>
          <strong>${escapeHtml(mirrorText)}</strong>
          <small>${escapeHtml(mirrorHint)}</small>
        </article>
      </div>
      <div class="inline-actions settings-summary-actions">
        <button type="button" data-tab="overview" data-subpage="settings">编辑核心参数</button>
        <button type="button" data-action="manual-ledger-backup">立即镜像备份</button>
        <button type="button" data-action="open-service-url">打开本地服务入口</button>
      </div>
      <p class="notice">全部总览只展示关键参数和备份状态；完整编辑、刷新列表和恢复快照放在“核心参数”子页。</p>
    </div>
  `;
}

function pbSourceBadge(source) {
  const isAuto = source === "auto";
  return `<span class="source-badge ${isAuto ? "source-auto" : "source-manual"}">${isAuto ? "自动 JSON" : "手工"}</span>`;
}

function latestPbPercentile(type) {
  const payload = loadValuation();
  const key = type === "ic" ? "IC" : "IM";
  const value = payload && payload.indexes && payload.indexes[key] ? payload.indexes[key].pb_percentile : null;
  return typeof value === "number" ? value : null;
}

function adoptPbButton(type, source) {
  const value = latestPbPercentile(type);
  if (source === "auto" || value === null) return "";
  return `<button class="inline-mini-action" type="button" data-action="adopt-pb" data-pb-type="${escapeHtml(type)}" data-pb-value="${escapeHtml(value)}">用自动值 ${escapeHtml(pct(value))}</button>`;
}

function getLedgerFilter(module) {
  if (!ledgerFilters[module]) ledgerFilters[module] = { search: "", action: "", bucket: "", page: 1 };
  return ledgerFilters[module];
}

function filterEntries(entries, filter) {
  const keyword = String(filter.search || "").trim().toLowerCase();
  return entries.filter((entry) => {
    if (filter.action && entry.action !== filter.action) return false;
    if (filter.bucket && entry.bucket !== filter.bucket) return false;
    if (!keyword) return true;
    return [entry.symbol, entry.name, entry.note, entry.bucket, actionLabels[entry.action]]
      .some((value) => String(value || "").toLowerCase().includes(keyword));
  });
}

function ledgerPageInfo(module, filteredEntries) {
  const filter = getLedgerFilter(module);
  const total = filteredEntries.length;
  const totalPages = Math.max(1, Math.ceil(total / ledgerPageSize));
  const page = Math.min(Math.max(Number(filter.page) || 1, 1), totalPages);
  filter.page = page;
  const start = total > 0 ? (page - 1) * ledgerPageSize : 0;
  const pageEntries = filteredEntries.slice(start, start + ledgerPageSize);
  return {
    page,
    totalPages,
    start,
    end: start + pageEntries.length,
    total,
    pageEntries
  };
}

function ledgerCountText(entries, filteredEntries, pageInfo) {
  if (!filteredEntries.length) return `显示 0 / ${entries.length} 笔`;
  const range = `${pageInfo.start + 1}-${pageInfo.end}`;
  if (filteredEntries.length === entries.length) return `显示 ${range} / ${entries.length} 笔`;
  return `显示 ${range} / ${filteredEntries.length} 笔，全部 ${entries.length} 笔`;
}

function ledgerFilterControls(config, entries, filteredEntries, pageInfo) {
  const filter = getLedgerFilter(config.module);
  const actions = Array.from(new Set(entries.map((entry) => entry.action).filter(Boolean)));
  return `
    <div class="ledger-tools">
      <div class="ledger-filter-grid">
        <label>搜索<input id="ledgerSearch" name="search" value="${escapeHtml(filter.search)}" placeholder="代码 / 名称 / 备注" autocomplete="off" /></label>
        <label>动作<select id="ledgerActionFilter" name="action">
          <option value="">全部动作</option>
          ${actions.map((action) => `<option value="${escapeHtml(action)}" ${filter.action === action ? "selected" : ""}>${escapeHtml(entryActionLabel(config.module, action))}</option>`).join("")}
        </select></label>
        <label>分类<select id="ledgerBucketFilter" name="bucket">
          <option value="">全部分类</option>
          ${config.buckets.map((bucket) => `<option value="${escapeHtml(bucket)}" ${filter.bucket === bucket ? "selected" : ""}>${escapeHtml(bucket)}</option>`).join("")}
        </select></label>
      </div>
      <div class="ledger-tool-actions">
        <button type="button" data-action="clear-filters" data-module="${escapeHtml(config.module)}">清除</button>
        <button type="button" data-action="export-csv" data-module="${escapeHtml(config.module)}">导出 CSV</button>
        <button type="button" data-action="export-json" data-module="${escapeHtml(config.module)}">导出 JSON</button>
      </div>
      <p class="ledger-count">${escapeHtml(ledgerCountText(entries, filteredEntries, pageInfo))}</p>
    </div>
  `;
}

function moduleEntries(module) {
  return loadLedger().entries
    .filter((entry) => entry.module === module)
    .sort((a, b) => String(b.date || "").localeCompare(String(a.date || "")));
}

function renderLedgerTableArea(config) {
  const entries = moduleEntries(config.module);
  const filteredEntries = filterEntries(entries, getLedgerFilter(config.module));
  const pageInfo = ledgerPageInfo(config.module, filteredEntries);
  return filteredEntries.length ? `${ledgerTable(pageInfo.pageEntries)}${ledgerPagination(config.module, pageInfo)}` : emptyFilteredState(config, entries.length);
}

function emptyFilteredState(config, total) {
  if (total > 0) {
    return `
      <div class="empty-state">
        <div class="empty-mark"></div>
        <h3>没有匹配流水</h3>
        <p>当前筛选条件下没有结果，可以清除筛选后再看完整的 ${escapeHtml(config.title)} 流水。</p>
      </div>
    `;
  }
  return emptyState(config);
}

function updateLedgerTableArea(module) {
  const config = moduleConfigs[module];
  const area = document.querySelector("#ledgerTableArea");
  const count = document.querySelector(".ledger-count");
  if (!config || !area) return;
  const entries = moduleEntries(module);
  const filteredEntries = filterEntries(entries, getLedgerFilter(module));
  const pageInfo = ledgerPageInfo(module, filteredEntries);
  area.innerHTML = filteredEntries.length ? `${ledgerTable(pageInfo.pageEntries)}${ledgerPagination(module, pageInfo)}` : emptyFilteredState(config, entries.length);
  if (count) count.textContent = ledgerCountText(entries, filteredEntries, pageInfo);
}

function positionsForModule(module, data = summarizeLedger()) {
  return data.positions
    .filter((position) => position.module === module)
    .sort((a, b) => Math.abs(b.marketValue) - Math.abs(a.marketValue));
}

function positionValuationPanel(config, positions) {
  return `
    <section class="panel position-panel">
      <div class="section-head">
        <h2>持仓估值</h2>
        <div class="position-tools">
          <span>价格变动只更新这里，不改历史流水</span>
          <button type="button" data-action="sync-module-prices" data-module="${escapeHtml(config.module)}">同步全部</button>
          <button id="loadPositionQuotesJson" type="button">读取价格 JSON</button>
        </div>
      </div>
      ${positions.length ? `
        <div class="position-table-wrap">
          <table class="position-table">
            <thead>
              <tr><th>标的</th><th>数量</th><th>${termLabel("净投入")}</th><th>当前价格<br><small>元</small></th><th>当前市值<br><small>万元</small></th><th>浮盈亏</th><th>来源</th><th>更新时间</th><th>备注</th><th>操作</th></tr>
            </thead>
            <tbody>
              ${positions.map((position) => positionValuationRow(position)).join("")}
            </tbody>
          </table>
        </div>
        <p class="notice">当前市值留空时，会用“数量 × 当前价格 / 10000”估算；两者都为空时回退到流水净投入。A 股可直接同步；QQQ、QLD 等美股从本地价格 JSON 读取人民币折算价；期权权利金仍建议手工填当前市值（万元）。</p>
      ` : `<p class="notice">先录入买入、转入或保证金流水后，这里会自动生成可估值的持仓。</p>`}
    </section>
  `;
}

function positionValuationRow(position) {
  const valueClass = position.unrealizedPnl > 0 ? "pnl-good" : position.unrealizedPnl < 0 ? "pnl-danger" : "";
  const sourceText = {
    manual: "手工市值",
    price: "价格估算",
    cost: "净投入回退"
  }[position.marketValueSource] || "净投入回退";
  const sourceDisplay = position.valuationSource ? `${sourceText} / ${position.valuationSource}` : sourceText;
  const canSync = canSyncPosition(position);
  return `
    <tr data-position-key="${escapeHtml(position.key)}">
      <td>
        <strong>${escapeHtml(position.symbol || position.name || "-")}</strong>
        <small>${escapeHtml(position.name || position.bucket || "-")}</small>
      </td>
      <td>${escapeHtml(chartValue(position.quantity))}</td>
      <td>${escapeHtml(yuan(position.netInvestment))}</td>
      <td>
        <label class="unit-input compact-unit-input">
          <input class="compact-input" type="number" step="0.0001" data-position-field="currentPrice" value="${escapeHtml(position.currentPrice ?? "")}" placeholder="价格" />
          <span>元</span>
        </label>
      </td>
      <td>
        <label class="unit-input compact-unit-input">
          <input class="compact-input" type="number" step="0.0001" data-position-field="marketValue" value="${escapeHtml(position.manualMarketValue ?? "")}" placeholder="${escapeHtml(format(position.marketValue))}" />
          <span>万元</span>
        </label>
        <small>${escapeHtml(sourceText)}</small>
      </td>
      <td class="${escapeHtml(valueClass)}">
        <strong>${escapeHtml(yuan(position.unrealizedPnl))}</strong>
        <small>${escapeHtml(pct(position.unrealizedPnlPct))}</small>
      </td>
      <td>${escapeHtml(sourceDisplay)}</td>
      <td>${escapeHtml(position.valuationUpdatedAt ? displayDateTime(position.valuationUpdatedAt) : "-")}</td>
      <td><input class="compact-input" data-position-field="note" value="${escapeHtml(position.valuationNote)}" placeholder="估值口径" /></td>
      <td>${canSync ? `<button class="small-action" type="button" data-action="sync-position-price" data-position-key="${escapeHtml(position.key)}">同步价格</button>` : `<span class="muted-pill">手工估值</span>`}</td>
    </tr>
  `;
}

function moduleBucketOverviewPanel(config, data, totals) {
  if (config.module === "ic") {
    const calc = calculate(data);
    const riskMeta = futuresAccountRiskMeta(data, calc);
    const availablePct = riskMeta.equity > 0 ? ratio(riskMeta.availableEquity, riskMeta.equity) : 0;
    const exposureReference = futuresExposureReference();
    const rows = [
      {
        label: "期货账户权益",
        value: riskMeta.equity,
        hint: "入金 + 已实现盈亏 + 盯市盈亏 - 费用",
        detail: futuresRiskActionText(riskMeta),
        statusClass: riskMeta.statusClass
      },
      {
        label: "占用保证金",
        value: riskMeta.usedMargin,
        hint: "按当前名义敞口和录入保证金率推导；这是风控读数，不是资产配置项",
        detail: `${riskMeta.statusText} · 风险度 ${marginRiskText(calc)} · 观察线 55% / 防守线 70%`,
        statusClass: riskMeta.statusClass,
        track: "risk"
      },
      {
        label: "可用权益",
        value: riskMeta.availableEquity,
        hint: "账户权益 - 占用保证金",
        detail: riskMeta.usedMargin > 0 ? `占账户权益 ${pct(availablePct)}；越厚越能承受波动` : "未开仓，全部权益可用",
        statusClass: riskMeta.statusClass
      },
      {
        label: "名义敞口",
        value: data.futuresNotional,
        hint: `风险暴露，不计入资产本金；${futuresExposureReferenceText(exposureReference)}`,
        detail: "当前风险暴露",
        statusClass: "target-neutral",
        references: [
          { label: exposureReference.icContract ? `1手IC ${exposureReference.icContract}` : "1手IC", value: exposureReference.ic },
          { label: "1手IC+1手IM", value: exposureReference.icIm }
        ]
      }
    ];
    return `
      <section class="panel bucket-panel">
        <div class="section-head">
          <h2>期货账户结构</h2>
          <button type="button" data-tab="${escapeHtml(config.module)}" data-subpage="valuation">查看持仓</button>
        </div>
        <div class="bucket-stack">
          ${rows.map((item) => {
            return `
              <div class="bucket-row">
                <div><strong>${escapeHtml(item.label)}</strong><span>${escapeHtml(item.hint)}</span></div>
                <div><strong>${escapeHtml(yuan(item.value))}</strong><span class="${escapeHtml(item.statusClass || "target-neutral")}">${escapeHtml(item.detail || "")}</span></div>
                ${item.track === "risk" ? futuresRiskTrack(riskMeta, "bucket-track") : item.references ? referenceTrack(item.value, item.references, "bucket-track") : ""}
              </div>
            `;
          }).join("")}
        </div>
      </section>
    `;
  }
  return `
    <section class="panel bucket-panel">
      <div class="section-head">
        <h2>持仓分布</h2>
        <button type="button" data-tab="${escapeHtml(config.module)}" data-subpage="buckets">查看分布</button>
      </div>
      <div class="bucket-stack">
        ${config.buckets.map((bucket) => {
          const value = data.bucketTotals[bucket] || 0;
          const share = ratio(value, Math.max(totals.balance, 1));
          const target = bucketTargetInfo(config.module, bucket, value, data, calculate(data));
          const meta = targetProgressMeta(value, target.target, target.mode);
          return `
            <div class="bucket-row">
              <div><strong>${escapeHtml(bucket)}</strong><span>${escapeHtml(bucketHint(config.module, bucket))}</span></div>
              <div><strong>${escapeHtml(yuan(value))}</strong><span class="${escapeHtml(meta.statusClass)}">${escapeHtml(`${pct(share)} · ${target.text} · ${meta.label}`)}</span></div>
              ${targetTrack(value, target.target, target.mode, "bucket-track")}
            </div>
          `;
        }).join("")}
      </div>
    </section>
  `;
}

function futuresPnlClass(value) {
  return value > 0 ? "pnl-good" : value < 0 ? "pnl-danger" : "";
}

function futuresPriceSourceText(source) {
  return source === "valuation" ? "日级行情" : "开仓价兜底";
}

function futuresPositionTable(rows, compact = false) {
  if (!rows.length) return `<p class="notice">还没有 IC/IM 开仓持仓；资金池和补资记录会进入账户权益，但不会伪装成合约持仓。</p>`;
  const tableRows = rows.map((rowData) => `
    <tr>
      <td>${escapeHtml(rowData.symbol || "-")}<small>${escapeHtml(rowData.product || "")}</small></td>
      <td>${escapeHtml(chartValue(rowData.quantity))}</td>
      <td>${escapeHtml(chartValue(rowData.avgPrice))}</td>
      <td>${escapeHtml(chartValue(rowData.currentPrice))}<small>${escapeHtml(futuresPriceSourceText(rowData.priceSource))}</small></td>
      <td>${escapeHtml(yuan(rowData.currentNotional))}</td>
      ${compact ? "" : `<td>${escapeHtml(yuan(rowData.usedMargin))}<small>${escapeHtml(pct(rowData.marginRate))}</small></td>`}
      <td class="${escapeHtml(futuresPnlClass(rowData.unrealizedPnl))}">${escapeHtml(yuan(rowData.unrealizedPnl))}</td>
    </tr>
  `).join("");
  return `
    <div class="table-wrap">
      <table class="overview-mini-table futures-position-table">
        <thead><tr><th>合约</th><th>净手数</th><th>持仓均价</th><th>盯市价</th><th>名义敞口</th>${compact ? "" : "<th>占用保证金</th>"}<th>盯市盈亏</th></tr></thead>
        <tbody>${tableRows}</tbody>
      </table>
    </div>
  `;
}

function futuresPositionSummaryPanel(data) {
  const rows = futuresHoldingRows(data);
  return `
    <section class="panel">
      <div class="section-head">
        <h2>期货持仓分析</h2>
        <button type="button" data-tab="ic" data-subpage="valuation">查看持仓</button>
      </div>
      ${futuresPositionTable(rows, true)}
      <p class="notice">这里展示合约净手数和名义敞口；期货名义敞口不是资产本金，不进入普通持仓估值表。</p>
    </section>
  `;
}

function futuresAddOneSuggestionPanel(data, calc = calculate(data)) {
  const candidates = futuresAddLotCandidates(data, calc).filter((item) => item.notional > 0);
  const rows = candidates.map((item) => `
    <tr>
      <td>${escapeHtml(item.product)}<small>${escapeHtml(item.contract || "近月合约")}</small></td>
      <td>${escapeHtml(chartValue(item.future))}</td>
      <td>${escapeHtml(yuan(item.notional))}</td>
      <td>${escapeHtml(yuan(item.addedMargin))}<small>${escapeHtml(pct(item.marginRate))}</small></td>
      <td>${escapeHtml(pct(item.riskNoTopUp))}</td>
      <td>${escapeHtml(yuan(item.topUpToDefense))}<small>当下 ≤70%</small></td>
      <td>${escapeHtml(yuan(item.topUpToWatch))}<small>当下 ≤55%</small></td>
      <td><strong>${escapeHtml(yuan(item.recommendedTopUp))}</strong><small>加完再跌 ${escapeHtml(pct(item.stressDrop))} 后 ≤70%</small></td>
    </tr>
  `).join("");
  return `
    <section class="panel futures-add-panel">
      <div class="section-head">
        <h2>加一手资金测算</h2>
        <span>默认推荐稳健压力线</span>
      </div>
      ${rows ? `
        <div class="table-wrap">
          <table class="overview-mini-table">
            <thead><tr><th>品种</th><th>点位</th><th>新增名义</th><th>新增保证金</th><th>不补资风险度</th><th>最低补资</th><th>当下55%</th><th>建议补资</th></tr></thead>
            <tbody>${rows}</tbody>
          </table>
        </div>
        <p class="notice">建议补资口径：买入 1 手后，再假设期货继续下跌 20%，期货风险度仍不超过 70%。这是资金安全垫测算，不代表必须交易。</p>
      ` : `<p class="notice">读取 IC/IM 估值 JSON 后，才能按近月合约测算再买 1 手需要补多少权益。</p>`}
    </section>
  `;
}

function futuresPositionValuationPanel(data) {
  const rows = futuresHoldingRows(data);
  const state = data.futuresState || {};
  return `
    <section class="panel position-panel futures-position-panel">
      <div class="section-head">
        <h2>期货持仓分析</h2>
        <div class="position-tools">
          <span>由 IC/IM 流水和日级估值 JSON 推导</span>
          <button type="button" data-action="load-valuation-json">读取估值 JSON</button>
        </div>
      </div>
      <div class="backup-summary">
        <article><span>账户权益</span><strong>${escapeHtml(yuan(data.futuresEquity))}</strong><small>入金 + 盈亏 - 费用</small></article>
        <article><span>占用保证金</span><strong>${escapeHtml(yuan(data.usedMargin))}</strong><small>当前风险度 ${escapeHtml(marginRiskText(calculate(data)))}</small></article>
        <article><span>名义敞口</span><strong>${escapeHtml(yuan(data.futuresNotional))}</strong><small>风险暴露，不是本金</small></article>
        <article><span>总盯市盈亏</span><strong>${escapeHtml(yuan(state.totalPnl || 0))}</strong><small>已实现 + 未实现</small></article>
      </div>
      ${futuresPositionTable(rows, false)}
      <p class="notice">若合约未在最新估值 JSON 中出现，盯市价会回退到开仓价并标记为“开仓价兜底”；临近移仓或已过期合约应优先更新流水。</p>
    </section>
  `;
}

function modulePositionSummaryPanel(config, positions) {
  const rows = positions.slice(0, 6).map((position) => `
    <tr>
      <td>${escapeHtml(position.symbol || position.name || "-")}<small>${escapeHtml(position.name || position.bucket || "")}</small></td>
      <td>${escapeHtml(yuan(position.marketValue))}</td>
      <td class="${escapeHtml(position.unrealizedPnl > 0 ? "pnl-good" : position.unrealizedPnl < 0 ? "pnl-danger" : "")}">${escapeHtml(yuan(position.unrealizedPnl))}<small>${escapeHtml(pct(position.unrealizedPnlPct))}</small></td>
    </tr>
  `).join("");
  return `
    <section class="panel">
      <div class="section-head">
        <h2>持仓估值摘要</h2>
        <button type="button" data-tab="${escapeHtml(config.module)}" data-subpage="valuation">查看估值</button>
      </div>
      ${rows ? `<div class="table-wrap"><table class="overview-mini-table"><thead><tr><th>标的</th><th>市值</th><th>浮盈亏</th></tr></thead><tbody>${rows}</tbody></table></div>` : `<p class="notice">还没有可估值持仓。</p>`}
    </section>
  `;
}

function moduleLedgerSummaryPanel(config, entries) {
  const rows = entries.slice(0, 6).map((entry) => `
    <tr>
      <td>${escapeHtml(entry.date || "-")}</td>
      <td>${escapeHtml(actionLabels[entry.action] || entry.action || "-")}</td>
      <td>${escapeHtml(entry.symbol || entry.name || "-")}</td>
      <td>${escapeHtml(yuan(safeAmount(entry.amount)))}</td>
    </tr>
  `).join("");
  return `
    <section class="panel">
      <div class="section-head">
        <h2>最近流水</h2>
        <button type="button" data-tab="${escapeHtml(config.module)}" data-subpage="ledger">查看流水</button>
      </div>
      ${rows ? `<div class="table-wrap"><table class="overview-mini-table"><thead><tr><th>日期</th><th>动作</th><th>标的</th><th>金额</th></tr></thead><tbody>${rows}</tbody></table></div>` : `<p class="notice">还没有流水。</p>`}
    </section>
  `;
}

function moduleHeroValue(config, data, totals) {
  if (config.module === "ic") return data.futuresEquity || data.futuresPool;
  return totals.balance;
}

function moduleHeroNote(config, data, totals) {
  if (config.module === "ic") {
    return `账户权益 ${yuan(data.futuresEquity)}，名义敞口 ${yuan(data.futuresNotional)}，盯市盈亏 ${yuan((data.futuresState || {}).totalPnl || 0)}`;
  }
  return `净投入 ${yuan(totals.cost)}，浮盈亏 ${yuan(totals.balance - totals.cost)}`;
}

function moduleHeroShare(config, data, totals) {
  return ratio(moduleHeroValue(config, data, totals), data.totalAssets);
}

function renderLedgerModuleOverview(config, data, calc, entries, filteredEntries, modulePositions, totals, editing) {
  const pageInfo = ledgerPageInfo(config.module, filteredEntries);
  const valuationPanel = config.module === "ic" ? futuresPositionValuationPanel(data) : positionValuationPanel(config, modulePositions);
  appRoot.innerHTML = `
    <section class="module-hero module-${escapeHtml(config.accent || config.module)}">
      <div>
        <div class="module-hero-title">${moduleIcon(config.icon, config.title)}<span>${escapeHtml(config.description)}</span></div>
        <strong>${escapeHtml(yuan(moduleHeroValue(config, data, totals)))}</strong>
        <p class="module-hero-note">${escapeHtml(moduleHeroNote(config, data, totals))}</p>
        <div class="overview-actions">
          <button type="button" data-tab="${escapeHtml(config.module)}" data-subpage="entry">记录一笔</button>
          <button type="button" data-tab="${escapeHtml(config.module)}" data-subpage="ledger">查看流水</button>
        </div>
      </div>
      <div class="hero-grid">
        <div><span>流水笔数</span><strong>${entries.length}</strong></div>
        <div><span>过去12个月收入</span><strong>${escapeHtml(yuan(totals.income))}</strong></div>
        <div><span>占总资产</span><strong>${escapeHtml(pct(moduleHeroShare(config, data, totals)))}</strong></div>
      </div>
    </section>

    ${moduleFocusPanel(config, data, calc, entries, totals)}

    <section class="module-workbench">
      <div class="module-side">
        <section class="panel entry-panel">
          <div class="section-head">
            <h2>${editing ? "编辑记录" : escapeHtml(config.entryTitle || `${config.title}记录`)}</h2>
            ${editing ? '<button type="button" data-action="cancel-edit">取消编辑</button>' : ""}
          </div>
          ${entryForm(config, editing)}
        </section>
        ${moduleBucketOverviewPanel(config, data, totals)}
        ${config.module === "ic" ? futuresAddOneSuggestionPanel(data, calc) : ""}
      </div>
      <section class="panel module-ledger-panel">
        <div class="section-head">
          <h2>投资流水</h2>
          <span>${entries.length} 笔</span>
        </div>
        ${ledgerFilterControls(config, entries, filteredEntries, pageInfo)}
        <div id="ledgerTableArea">${filteredEntries.length ? `${ledgerTable(pageInfo.pageEntries)}${ledgerPagination(config.module, pageInfo)}` : emptyFilteredState(config, entries.length)}</div>
      </section>
    </section>

    <div class="module-full-width-panel">
      ${valuationPanel}
    </div>
  `;
}

function renderLedgerModule(config, subpage = "full") {
  const ledger = loadLedger();
  const data = summarizeLedger(ledger);
  const calc = calculate(data);
  const entries = moduleEntries(config.module);
  const filteredEntries = filterEntries(entries, getLedgerFilter(config.module));
  const pageInfo = ledgerPageInfo(config.module, filteredEntries);
  const modulePositions = positionsForModule(config.module, data);
  const totals = {
    balance: data.moduleTotals[config.module] || 0,
    cost: data.moduleCostTotals[config.module] || 0,
    income: entries.reduce((sum, entry) => sum + entryIncomeImpact(entry), 0)
  };
  const editing = editingId ? ledger.entries.find((entry) => entry.id === editingId) : null;

  if (isDesktopMode() && subpage === "full") {
    renderLedgerModuleOverview(config, data, calc, entries, filteredEntries, modulePositions, totals, editing);
    return;
  }

  appRoot.innerHTML = `
    <section class="module-hero module-${escapeHtml(config.accent || config.module)}">
      <div>
        <div class="module-hero-title">${moduleIcon(config.icon, config.title)}<span>${escapeHtml(config.description)}</span></div>
        <strong>${escapeHtml(yuan(moduleHeroValue(config, data, totals)))}</strong>
        <p class="module-hero-note">${escapeHtml(moduleHeroNote(config, data, totals))}</p>
      </div>
      <div class="hero-grid">
        <div><span>流水笔数</span><strong>${entries.length}</strong></div>
        <div><span>过去12个月收入</span><strong>${escapeHtml(yuan(totals.income))}</strong></div>
        <div><span>占总资产</span><strong>${escapeHtml(pct(moduleHeroShare(config, data, totals)))}</strong></div>
      </div>
    </section>

    ${mobileSectionNav(config.module)}

    ${moduleFocusPanel(config, data, calc, entries, totals)}

    <section class="module-workbench">
      <div class="module-side">
        <section class="panel entry-panel">
          <div class="section-head">
            <h2>${editing ? "编辑记录" : escapeHtml(config.entryTitle || `${config.title}记录`)}</h2>
            ${editing ? '<button type="button" data-action="cancel-edit">取消编辑</button>' : ""}
          </div>
          ${entryForm(config, editing)}
        </section>
        ${moduleBucketOverviewPanel(config, data, totals)}
        ${config.module === "ic" ? futuresAddOneSuggestionPanel(data, calc) : ""}
        ${config.module === "ic" ? futuresPositionValuationPanel(data) : positionValuationPanel(config, modulePositions)}
      </div>
      <section class="panel module-ledger-panel">
        <div class="section-head">
          <h2>投资流水</h2>
          <span>${entries.length} 笔</span>
        </div>
        ${ledgerFilterControls(config, entries, filteredEntries, pageInfo)}
        <div id="ledgerTableArea">${filteredEntries.length ? `${ledgerTable(pageInfo.pageEntries)}${ledgerPagination(config.module, pageInfo)}` : emptyFilteredState(config, entries.length)}</div>
      </section>
    </section>
  `;

  pruneModuleSubpage(config, subpage);
}

function moduleFocusPanel(config, data, calc, entries, totals) {
  const focusItems = (config.focus || []).map((item) => `<li>${escapeHtml(item)}</li>`).join("");
  if (config.module === "dividend") {
    const incomeRows = entries
      .filter((entry) => entry.action === "dividend" || entry.action === "interest")
      .slice(0, 5)
      .map((entry) => row(entry.date || "-", entry.symbol || entry.name || "-", yuan(safeAmount(entry.amount)), entry.note || "-"))
      .join("");
    return `
      <section class="module-dashboard dividend-dashboard">
        ${moduleVisual("dividend", data, calc)}
        ${metric("股息覆盖", pct(calc.divCoverage), "过去12个月收入 / 年生活支出", classify(calc.divCoverage, 120, 80))}
        ${metric("高分红股票", yuan(data.highDividend), "股票标的统一归到这一类", data.highDividend > 0 ? "status-good" : "status-warn")}
        ${metric("流动安全垫", `${format(calc.liquidMonths)} 月`, "现金 + 类现金", classify(calc.liquidMonths, 12, 6))}
        <article class="focus-card">
          <h3>这个模块只问三件事</h3>
          <ol>${focusItems}</ol>
        </article>
        <article class="focus-card focus-wide">
          <h3>最近现金流</h3>
          ${incomeRows ? `<table><thead><tr><th>日期</th><th>来源</th><th>金额</th><th>备注</th></tr></thead><tbody>${incomeRows}</tbody></table>` : `<p class="notice">还没有分红或利息流水；先把每笔到账记进来。</p>`}
        </article>
      </section>
    `;
  }
  if (config.module === "qqq") {
    const qld = data.bucketTotals["QLD"] || 0;
    const qqq = data.bucketTotals["QQQ"] || 0;
    const qldPct = ratio(qld, data.qqq);
    return `
      <section class="module-dashboard qqq-dashboard">
        ${moduleVisual("qqq", data, calc)}
        ${metric("右尾权重", pct(calc.qqqPct), qqqStatus(calc.qqqPct), calc.qqqPct > 15 ? "status-danger" : calc.qqqPct >= 5 ? "status-good" : "status-warn")}
        ${metric("距 10% 目标", yuan(calc.qqqGap10), "目标按当前总资产计算", calc.qqqGap10 <= 0 ? "status-good" : "status-warn")}
        ${metric("QLD 占比", pct(qldPct), "QLD 只服务 120 日线趋势仓", qldPct <= 35 ? "status-good" : "status-warn")}
        <article class="focus-card">
          <h3>${termLabel("120 日均线")}纪律</h3>
          <ol>${focusItems}</ol>
        </article>
        <article class="focus-card focus-wide allocation-strip">
          <h3>QQQ / QLD 拆分</h3>
          ${miniBars([
            ["QQQ", qqq, data.qqq],
            ["QLD", qld, data.qqq],
            ["现金等待", data.bucketTotals["现金等待"] || 0, Math.max(data.qqq + (data.bucketTotals["现金等待"] || 0), 1)]
          ])}
        </article>
      </section>
    `;
  }
  if (config.module === "put") {
    const annualBudgetLow = data.totalAssets * 0.005;
    const annualBudgetHigh = data.totalAssets >= 300 ? data.totalAssets * 0.02 : data.totalAssets * 0.01;
    const consumedPct = annualBudgetHigh > 0 ? ratio(data.spyPutBudget, annualBudgetHigh) : 0;
    return `
      <section class="module-dashboard put-dashboard">
        ${moduleVisual("put", data, calc)}
        ${metric("当前保费", yuan(data.spyPutBudget), "记录为年度黑天鹅保险预算", data.spyPutBudget > 0 ? "status-good" : "status-warn")}
        ${metric("预算区间", `${yuan(annualBudgetLow)} - ${yuan(annualBudgetHigh)}`, "随总资产自动变化", "status-good")}
        ${metric("预算使用", pct(consumedPct), "当前保费 / 年度上限", consumedPct <= 100 ? "status-good" : "status-danger")}
        <article class="focus-card">
          <h3>保险层纪律</h3>
          <ol>${focusItems}</ol>
        </article>
        <article class="focus-card focus-wide">
          <h3>合约备忘</h3>
          <p class="notice">备注里建议固定记录：到期日、行权价、${termLabel("Delta")}、距离现价跌幅。这个模块不追求盈利，只确认极端下跌时组合还能活。</p>
        </article>
      </section>
    `;
  }
  if (config.module === "ic") {
    const valuation = loadValuation();
    const rollText = latestRollText(valuation);
    const icReady = data.futuresPool >= 100 && data.icPb <= 30 && calc.hardMonths >= 6;
    return `
      <section class="module-dashboard ic-dashboard">
        ${moduleVisual("ic", data, calc)}
        ${metric("资金池", yuan(data.futuresPool), "100 万起步线", data.futuresPool >= 100 ? "status-good" : "status-warn")}
        ${metric("账户权益", yuan(data.futuresEquity), "期货资金池 + 日级盯市盈亏 - 费用", data.futuresEquity > 0 ? "status-good" : data.usedMargin > 0 ? "status-danger" : "status-warn")}
        ${metric("盯市盈亏", yuan((data.futuresState || {}).totalPnl || 0), "按最新 IC/IM 日级合约点位推导", ((data.futuresState || {}).totalPnl || 0) >= 0 ? "status-good" : "status-warn")}
        ${metric("名义敞口", yuan(data.futuresNotional), "指数点位 × 乘数 × 手数", data.futuresNotional > 0 ? "status-good" : "status-warn")}
        ${metric("杠杆比例", `${format(calc.futuresLeverage)}x`, "名义敞口 / 期货账户权益", calc.futuresLeverage <= 2 ? "status-good" : calc.futuresLeverage <= 4 ? "status-warn" : "status-danger")}
        ${metric("期货风险度", marginRiskText(calc), marginRiskHint(calc), classify(calc.marginRisk, 50, 70, true))}
        ${metric("保证金率", pct(calc.futuresMarginRate), "占用保证金 / 名义敞口", calc.futuresMarginRate <= 20 ? "status-good" : "status-warn")}
        ${metric("IC PB 分位", pct(data.icPb), "低于 30 才进入执行区", classify(data.icPb, 30, 50, true))}
        <article class="focus-card">
          <h3>开仓前置条件</h3>
          <ol>${focusItems}</ol>
        </article>
        <article class="focus-card focus-wide">
          <h3>${icReady ? "可以复算第一手 IC" : "继续等待"}</h3>
          <p class="notice">${icReady ? escapeHtml("资金池、估值和现金垫接近要求；下一步必须复算加完后再跌 20% 的补资压力。") : `没有同时满足资金池、${termLabel("PB 分位")}和现金垫之前，这里只记录观察、补资和${termLabel("移仓窗口")}。`}</p>
          <p class="notice">${escapeHtml(rollText)}</p>
          <div class="inline-actions">
            <button id="loadValuationJson" type="button">读取估值 JSON</button>
          </div>
        </article>
        ${icValuationPanel(valuation)}
      </section>
    `;
  }
  return "";
}

function icValuationPanel(payload) {
  if (!payload || !payload.indexes) {
    return `
      <article class="data-panel data-panel-wide">
        <div class="section-head">
          <h3>估值与贴水数据</h3>
          <span>等待读取</span>
        </div>
        <p class="notice">本地已有估值 JSON。页面会尝试自动读取；如果没有显示，点击上方“读取估值 JSON”。</p>
      </article>
    `;
  }
  const cards = ["IC", "IM"].map((key) => {
    const item = payload.indexes[key] || {};
    const nearest = ((item.basis || {}).contracts || [])[0] || {};
    const furthest = ((item.basis || {}).contracts || []).slice(-1)[0] || {};
    const roll = (item.basis || {}).roll_notice || {};
    return `
      <article class="data-card">
        <div class="data-card-head">
          <strong>${escapeHtml(key)} ${escapeHtml(item.name || "")}</strong>
          <span>${escapeHtml(displayTradeDate(item.trade_date || payload.trade_date))}</span>
        </div>
        <div class="data-kpi-grid">
          <div><span>PE</span><strong>${escapeHtml(displayNumber(item.pe))}</strong></div>
          <div><span>PB</span><strong>${escapeHtml(displayNumber(item.pb))}</strong></div>
          <div><span>PE分位</span><strong>${escapeHtml(typeof item.pe_percentile === "number" ? pct(item.pe_percentile) : "-")}</strong></div>
          <div><span>${termLabel("PB分位")}</span><strong>${escapeHtml(typeof item.pb_percentile === "number" ? pct(item.pb_percentile) : "手工")}</strong></div>
        </div>
        <div class="data-subgrid">
          <div><span>近月合约</span><strong>${escapeHtml(nearest.contract || "-")}</strong><small>${nearest.annualized_basis_pct === undefined ? "-" : `${escapeHtml(pct(nearest.annualized_basis_pct))} ${termLabel("年化贴水")}`}</small></div>
          <div><span>远月合约</span><strong>${escapeHtml(furthest.contract || "-")}</strong><small>${furthest.annualized_basis_pct === undefined ? "-" : `${escapeHtml(pct(furthest.annualized_basis_pct))} ${termLabel("年化贴水")}`}</small></div>
          <div><span>${termLabel("移仓窗口")}</span><strong>${escapeHtml(roll.contract || "-")}</strong><small>${escapeHtml(roll.message || "-")}</small></div>
        </div>
        <p class="valuation-note">PB 来源：${escapeHtml(valuationSourceLabel(item.pb_source))}；PE 来源：${escapeHtml(valuationSourceLabel(item.pe_source))}；PB 历史分位${item.pb_percentile_manual_required ? "仍需手工兜底" : "已自动读取"}。</p>
      </article>
    `;
  }).join("");
  return `
    <article class="data-panel data-panel-wide">
      <div class="section-head">
        <h3>估值与贴水数据</h3>
        <span>${escapeHtml(displayDateTime(payload.generated_at))}</span>
      </div>
      <div class="data-source-strip">
        <span>估值：${escapeHtml((payload.source || {}).current_valuation || "-")}</span>
        <span>PE历史：${escapeHtml((payload.source || {}).pe_history || "-")}</span>
        <span>贴水：${escapeHtml((payload.source || {}).basis || "-")}</span>
      </div>
      <div class="data-card-grid">${cards}</div>
      ${icBasisDetailTable(payload)}
    </article>
  `;
}

function icBasisDetailTable(payload) {
  const rows = ["IC", "IM"].flatMap((key) => {
    const item = (payload.indexes || {})[key] || {};
    return (((item.basis || {}).contracts || [])).map((contract) => `
      <tr>
        <td>${escapeHtml(key)}</td>
        <td>${escapeHtml(contract.contract || "-")}</td>
        <td>${escapeHtml(displayNumber(contract.spot))}</td>
        <td>${escapeHtml(displayNumber(contract.future))}</td>
        <td>${escapeHtml(displaySigned(contract.basis))}</td>
        <td>${escapeHtml(contract.annualized_basis_pct === null || contract.annualized_basis_pct === undefined ? "-" : pct(contract.annualized_basis_pct))}</td>
        <td>${escapeHtml(contract.delivery_date || "-")}</td>
        <td>${escapeHtml(contract.days_left ?? "-")}</td>
      </tr>
    `);
  }).join("");
  if (!rows) return `<p class="notice">贴水合约明细未生成。</p>`;
  return `
    <div class="basis-table-wrap module-basis-wrap">
      <table class="basis-table module-basis-table">
        <thead><tr><th>品种</th><th>合约</th><th>现货</th><th>期货</th><th>贴水</th><th>${termLabel("年化贴水")}</th><th>交割日</th><th>剩余天数</th></tr></thead>
        <tbody>${rows}</tbody>
      </table>
    </div>
  `;
}

function moduleVisual(module, data, calc) {
  if (module === "dividend") {
    const divPct = Math.min(100, Math.max(0, calc.divCoverage));
    return `
      <article class="visual-card visual-income">
        <div class="visual-head">${moduleIcon("income", "高分红")}<div><h3>现金流瀑布</h3><p>生活现金垫先兜底，股息再抬高舒适度。</p></div></div>
        <svg class="visual-svg" viewBox="0 0 420 220" role="img" aria-label="高分红现金流示意图">
          <rect class="svg-bg" x="0" y="0" width="420" height="220" rx="18" />
          <path class="svg-grid" d="M42 172H378M42 128H378M42 84H378" />
          <rect class="svg-bar income-a" x="58" y="122" width="54" height="58" rx="8" />
          <rect class="svg-bar income-b" x="138" y="94" width="54" height="86" rx="8" />
          <rect class="svg-bar income-c" x="218" y="${180 - Math.min(112, divPct)}" width="54" height="${Math.min(112, divPct)}" rx="8" />
          <path class="svg-line income-line" d="M56 92C112 86 132 64 174 74S246 110 286 82s64-38 92-22" />
          <circle class="svg-dot" cx="378" cy="60" r="7" />
          <text x="58" y="199">现金</text><text x="138" y="199">逆回购</text><text x="218" y="199">股息</text>
          <text class="svg-big" x="304" y="118">${escapeHtml(pct(calc.divCoverage))}</text>
          <text class="svg-small" x="304" y="142">股息覆盖</text>
        </svg>
      </article>
    `;
  }
  if (module === "qqq") {
    const qqqPct = Math.min(16, Math.max(0, calc.qqqPct));
    return `
      <article class="visual-card visual-growth">
        <div class="visual-head">${moduleIcon("trend", "QQQ")}<div><h3>右尾趋势仪表</h3><p>QQQ 是长期右尾，QLD 只在趋势确认后加速。</p></div></div>
        <svg class="visual-svg" viewBox="0 0 420 220" role="img" aria-label="QQQ 趋势与120日均线示意图">
          <rect class="svg-bg" x="0" y="0" width="420" height="220" rx="18" />
          <path class="svg-grid" d="M42 172H378M42 128H378M42 84H378" />
          <path class="svg-ma" d="M44 150C92 146 124 139 162 136S232 130 274 112s68-30 104-26" />
          <path class="svg-line growth-line" d="M44 164C82 158 102 112 136 128s55 38 84 8 42-62 82-52 52-10 76-34" />
          <circle class="svg-dot" cx="378" cy="50" r="7" />
          <rect class="range-track" x="58" y="184" width="300" height="12" rx="6" />
          <rect class="range-fill" x="58" y="184" width="${Math.min(300, (qqqPct / 16) * 300)}" height="12" rx="6" />
          <text x="58" y="211">0%</text><text x="146" y="211">5%</text><text x="240" y="211">10%</text><text x="326" y="211">16%</text>
          <text class="svg-big" x="66" y="76">${escapeHtml(pct(calc.qqqPct))}</text>
          <text class="svg-small" x="66" y="100">当前右尾仓</text>
          <text class="svg-label" x="272" y="132">120日均线</text>
        </svg>
      </article>
    `;
  }
  if (module === "put") {
    const used = Math.min(100, Math.max(0, calc.putPct * 50));
    return `
      <article class="visual-card visual-protection">
        <div class="visual-head">${moduleIcon("shield", "深度Put")}<div><h3>黑天鹅保护曲线</h3><p>保费可归零，但极端下跌时组合不能归零。</p></div></div>
        <svg class="visual-svg" viewBox="0 0 420 220" role="img" aria-label="深度虚值 Put 保护曲线示意图">
          <rect class="svg-bg" x="0" y="0" width="420" height="220" rx="18" />
          <path class="svg-grid" d="M42 172H378M42 128H378M42 84H378" />
          <path class="put-area" d="M54 176L136 176L206 132L302 78L372 62L372 176Z" />
          <path class="svg-line put-line" d="M54 176H136L206 132L302 78L372 62" />
          <path class="svg-ma" d="M54 62C124 76 168 104 204 132s80 40 168 44" />
          <line class="strike-line" x1="136" y1="48" x2="136" y2="180" />
          <text class="svg-label" x="105" y="42">行权价</text>
          <text class="svg-big" x="248" y="142">${escapeHtml(pct(calc.putPct))}</text>
          <text class="svg-small" x="248" y="166">保费 / 总资产</text>
          <rect class="range-track" x="58" y="190" width="300" height="10" rx="5" />
          <rect class="range-fill put-fill" x="58" y="190" width="${Math.min(300, used * 3)}" height="10" rx="5" />
        </svg>
      </article>
    `;
  }
  if (module === "ic") {
    const poolPct = Math.min(100, Math.max(0, data.futuresPool));
    return `
      <article class="visual-card visual-futures">
        <div class="visual-head">${moduleIcon("futures", "IC/IM")}<div><h3>贴水与保证金雷达</h3><p>低估、贴水、保证金安全垫必须一起看。</p></div></div>
        <svg class="visual-svg" viewBox="0 0 420 220" role="img" aria-label="IC IM 期货贴水和资金池示意图">
          <rect class="svg-bg" x="0" y="0" width="420" height="220" rx="18" />
          <path class="svg-grid" d="M42 172H378M42 128H378M42 84H378" />
          <path class="basis-ladder" d="M68 72H158V104H220V136H288V168H360" />
          <circle class="svg-dot" cx="68" cy="72" r="6" /><circle class="svg-dot" cx="158" cy="104" r="6" /><circle class="svg-dot" cx="220" cy="136" r="6" /><circle class="svg-dot" cx="288" cy="168" r="6" />
          <rect class="risk-ring" x="72" y="132" width="72" height="44" rx="10" />
          <rect class="range-track" x="176" y="184" width="178" height="12" rx="6" />
          <rect class="range-fill futures-fill" x="176" y="184" width="${Math.min(178, (poolPct / 100) * 178)}" height="12" rx="6" />
          <text class="svg-big" x="62" y="112">${escapeHtml(pct(data.icPb))}</text>
          <text class="svg-small" x="62" y="128">IC PB</text>
          <text class="svg-big" x="176" y="166">${escapeHtml(yuan(data.futuresPool))}</text>
          <text class="svg-small" x="176" y="181">资金池 / 100万</text>
          <text class="svg-label" x="296" y="62">近月</text><text class="svg-label" x="322" y="164">远月</text>
        </svg>
      </article>
    `;
  }
  return "";
}

function miniBars(items) {
  return `<div class="mini-bars">${items.map(([label, value, total]) => {
    const width = Math.min(100, Math.max(0, ratio(value, total || 1)));
    return `
      <div class="mini-bar">
        <div><span>${escapeHtml(label)}</span><strong>${escapeHtml(yuan(value))}</strong></div>
        <div class="mini-bar-track"><span style="width:${width}%"></span></div>
      </div>
    `;
  }).join("")}</div>`;
}

function latestRollText(payload) {
  const notices = [];
  if (payload && payload.indexes) {
    ["IC", "IM"].forEach((key) => {
      const notice = payload.indexes[key] && payload.indexes[key].basis && payload.indexes[key].basis.roll_notice;
      if (notice && notice.message) notices.push(`${key} ${notice.contract || ""}：${notice.message}`);
    });
  }
  return notices.length ? notices.join("；") : "尚未读取本地估值 JSON；读取后这里会显示最近合约换月提醒。";
}

function bucketHint(module, bucket) {
  if (module === "dividend") {
    if (bucket === "现金") return "生活底线，不追收益";
    if (bucket === "类现金") return "逆回购、货基等流动缓冲";
    if (bucket === "债券") return "稳态收益和波动缓冲";
    if (bucket === "高分红股票") return "所有高分红股票统一记录";
    return "现金流资产";
  }
  if (module === "qqq") {
    if (bucket === "QLD") return "120日均线策略仓";
    if (bucket === "现金等待") return "等待趋势信号";
    return "长期美股右尾";
  }
  if (module === "put") {
    if (bucket === "到期归零") return "保险成本归档";
    return "极端风险保险";
  }
  if (module === "ic") {
    if (bucket === "IC/IM资金池") return "不开仓时先攒池子";
    if (bucket === "移仓") return "换月动作记录";
    return "杠杆增强敞口";
  }
  return "当前账面余额";
}

function moduleStats(entries) {
  return {
    balance: entries.reduce((sum, entry) => sum + entryBalanceImpact(entry), 0),
    income: entries.reduce((sum, entry) => sum + entryIncomeImpact(entry), 0)
  };
}

function getInstrumentMemory(module = "") {
  const bySymbol = new Map();
  const presets = instrumentPresetCandidates[module] || [];
  presets.forEach((item, index) => {
    const symbol = String(item.symbol || "").trim();
    const name = String(item.name || "").trim();
    if (!symbol || !name) return;
    bySymbol.set(symbol.toUpperCase(), {
      symbol,
      name,
      module,
      market: item.market || "",
      category: item.category || "",
      date: "",
      order: index,
      preset: true
    });
  });
  loadLedger().entries.forEach((entry, index) => {
    const symbol = String(entry.symbol || "").trim();
    const name = String(entry.name || "").trim();
    if (!symbol || !name) return;
    bySymbol.set(symbol.toUpperCase(), {
      symbol,
      name,
      module: entry.module || "",
      market: "",
      category: entry.bucket || "",
      date: entry.date || "",
      order: index,
      preset: false
    });
  });
  return Array.from(bySymbol.values()).sort((a, b) => {
    const aInModule = a.module === module ? 0 : 1;
    const bInModule = b.module === module ? 0 : 1;
    if (aInModule !== bInModule) return aInModule - bInModule;
    if (a.preset !== b.preset) return a.preset ? 1 : -1;
    if (a.preset && b.preset) return a.order - b.order;
    return b.order - a.order;
  });
}

function instrumentDisplayParts(item) {
  return [item.symbol, item.market, item.category].filter(Boolean).join(" · ");
}

function instrumentSearchText(item) {
  return [item.name, item.symbol, item.market, item.category].filter(Boolean).join(" ").toLowerCase();
}

function instrumentSuggestionList(module, field) {
  const memory = getInstrumentMemory(module);
  if (!memory.length) return "";
  const rows = memory.map((item) => {
    const title = field === "name" ? item.name : item.symbol;
    const meta = field === "name"
      ? instrumentDisplayParts(item)
      : [item.name, item.market, item.category].filter(Boolean).join(" · ");
    return `
      <button class="instrument-suggestion" type="button" data-action="choose-instrument" data-symbol="${escapeHtml(item.symbol)}" data-name="${escapeHtml(item.name)}" data-search="${escapeHtml(instrumentSearchText(item))}">
        <strong>${escapeHtml(title)}</strong>
        <span>${escapeHtml(meta)}</span>
      </button>
    `;
  }).join("");
  return `
    <div class="instrument-menu" data-instrument-menu="${escapeHtml(field)}" hidden>
      ${rows}
      <div class="instrument-empty" data-instrument-empty hidden>没有匹配的标的</div>
    </div>
  `;
}

function findInstrumentMemory(value, field, module = "") {
  const text = String(value || "").trim();
  if (!text) return null;
  const normalized = field === "symbol" ? text.toUpperCase() : text;
  const matches = getInstrumentMemory(module).filter((item) => {
    if (field === "symbol") return item.symbol.toUpperCase() === normalized;
    return item.name === normalized;
  });
  if (field === "name" && matches.length !== 1) return null;
  return matches[0] || null;
}

function entryActionLabel(module, action) {
  return (moduleActionLabels[module] && moduleActionLabels[module][action]) || actionLabels[action] || action;
}

function entryActionOptions(module, currentAction = "") {
  const actions = moduleActions[module] || Object.keys(actionLabels);
  const merged = actions.includes(currentAction) || !currentAction ? actions : [currentAction, ...actions];
  return merged.map((action) => [action, entryActionLabel(module, action)]);
}

function thirdFriday(year, month) {
  const date = new Date(year, month - 1, 1);
  let fridayCount = 0;
  while (date.getMonth() === month - 1) {
    if (date.getDay() === 5) {
      fridayCount += 1;
      if (fridayCount === 3) return new Date(date);
    }
    date.setDate(date.getDate() + 1);
  }
  return null;
}

function parseFuturesContract(symbol) {
  const match = String(symbol || "").trim().toUpperCase().match(/^(IC|IM)(\d{2})(\d{2})$/);
  if (!match) return null;
  const year = 2000 + Number(match[2]);
  const month = Number(match[3]);
  if (month < 1 || month > 12) return null;
  const delivery = thirdFriday(year, month);
  if (!delivery) return null;
  return {
    product: match[1],
    year,
    month,
    deliveryDate: formatDate(delivery)
  };
}

function daysUntil(dateText) {
  const date = parseDate(dateText);
  const now = parseDate(today());
  if (!date || !now) return null;
  return Math.ceil((date.getTime() - now.getTime()) / 86400000);
}

function futuresContractHint(symbol) {
  const parsed = parseFuturesContract(symbol);
  if (!parsed) return "填 IC2607 / IM2607 后自动推算理论交割日";
  const left = daysUntil(parsed.deliveryDate);
  const leftText = left === null ? "" : `，距今 ${left} 天`;
  return `${parsed.product} ${parsed.year}年${String(parsed.month).padStart(2, "0")}月合约，理论交割日 ${parsed.deliveryDate}${leftText}；法定假日顺延需复核`;
}

function futuresContractName(symbol) {
  const parsed = parseFuturesContract(symbol);
  if (!parsed) return "";
  return parsed.product === "IC" ? "中证500股指期货" : "中证1000股指期货";
}

function amountWanFromEntryForm(form) {
  if (form.elements.amountYuan) return wanFromYuan(form.elements.amountYuan.value);
  return numberFromForm(form, "amount");
}

function feeWanFromEntryForm(form) {
  if (form.elements.feeYuan) return wanFromYuan(form.elements.feeYuan.value);
  return numberFromForm(form, "fee");
}

function marginWanFromEntryForm(form) {
  if (form.elements.marginYuan) return wanFromYuan(form.elements.marginYuan.value);
  return numberFromForm(form, "margin");
}

function marginRatioText(amount, margin) {
  if (safeAmount(amount) <= 0 || safeAmount(margin) <= 0) return "填保证金后自动计算保证金比例";
  return `保证金比例 ${pct(ratio(safeAmount(margin), safeAmount(amount)))}`;
}

function entryForm(config, entry = null) {
  const isFutures = config.module === "ic";
  const data = entry || {
    id: "",
    date: today(),
    bucket: config.buckets[0],
    symbol: "",
    name: "",
    action: "buy",
    quantity: "",
    price: "",
    amount: "",
    fee: "",
    margin: "",
    multiplier: isFutures ? defaultFuturesMultiplier : "",
    note: ""
  };
  const derivedName = isFutures ? futuresContractName(data.symbol) : "";
  const displayName = data.name || derivedName;
  const autoName = Boolean(isFutures && derivedName && !data.name);
  const actionOptions = entryActionOptions(config.module, data.action);
  const amountYuan = data.amount ? chartValue(yuanFromWan(data.amount)) : "";
  const feeYuan = data.fee ? chartValue(yuanFromWan(data.fee)) : "";
  const marginYuan = data.margin ? chartValue(yuanFromWan(data.margin)) : "";
  const quantityLabel = isFutures ? "手数" : "数量（股/份）";
  const priceLabel = isFutures ? "指数点位" : "价格（元）";
  const amountHint = data.amount ? `${escapeHtml(yuan(data.amount))}` : "买卖按数量 × 价格自动计算；分红/利息手填";
  const contractHint = isFutures ? futuresContractHint(data.symbol || "") : "";
  return `
    <form id="entryForm" class="entry-form" data-module="${escapeHtml(config.module)}" data-id="${escapeHtml(data.id || "")}" novalidate>
      <label>日期<input type="date" name="date" value="${escapeHtml(data.date || today())}" required /></label>
      <label>分类<select name="bucket">${config.buckets.map((bucket) => `<option value="${escapeHtml(bucket)}" ${bucket === data.bucket ? "selected" : ""}>${escapeHtml(bucket)}</option>`).join("")}</select></label>
      <label>动作<select name="action">${actionOptions.map(([value, label]) => `<option value="${escapeHtml(value)}" ${value === data.action ? "selected" : ""}>${escapeHtml(label)}</option>`).join("")}</select></label>
      <label class="instrument-combobox">标的名称<input name="name" data-instrument-field="name" value="${escapeHtml(displayName)}" data-auto-name="${autoName ? "true" : "false"}" placeholder="先选名称，代码会自动带出" autocomplete="off" />${instrumentSuggestionList(config.module, "name")}</label>
      <label class="instrument-combobox">标的代码<input name="symbol" data-instrument-field="symbol" value="${escapeHtml(data.symbol || "")}" placeholder="${escapeHtml(config.symbolPlaceholder || "如 QQQ / IC2609")}" autocomplete="off" />${instrumentSuggestionList(config.module, "symbol")}</label>
      <label>${quantityLabel}<input type="number" name="quantity" min="1" step="1" value="${escapeHtml(data.quantity || "")}" /></label>
      <label>${priceLabel}<input type="number" name="price" step="0.01" value="${escapeHtml(data.price || "")}" /></label>
      ${isFutures ? `<label>乘数（元/点）<input type="number" name="multiplier" min="1" step="1" value="${escapeHtml(data.multiplier || defaultFuturesMultiplier)}" /><small class="field-hint" data-contract-hint>${escapeHtml(contractHint)}</small></label>` : ""}
      <label>金额（元）<input type="number" name="amountYuan" step="0.01" value="${escapeHtml(amountYuan)}" required /><small class="field-hint" data-amount-preview>${amountHint}</small></label>
      ${isFutures ? `<label>保证金（元）<input type="number" name="marginYuan" min="0" step="0.01" value="${escapeHtml(marginYuan)}" /><small class="field-hint" data-margin-preview>${escapeHtml(marginRatioText(data.amount, data.margin))}</small></label>` : ""}
      <label>费用（元）<input type="number" name="feeYuan" step="0.01" value="${escapeHtml(feeYuan)}" /></label>
      <label class="entry-note">备注<input name="note" value="${escapeHtml(data.note || "")}" placeholder="${escapeHtml(config.notePlaceholder || "买入理由、移仓说明、分红记录")}" /></label>
      <div class="form-error" data-entry-errors hidden></div>
      <div class="form-actions">
        <button type="submit">${entry ? "保存修改" : "新增记录"}</button>
      </div>
    </form>
  `;
}

function ledgerTable(entries) {
  return `
    <div class="table-wrap">
      <table class="ledger-table">
        <thead>
          <tr><th>日期</th><th>分类</th><th>动作</th><th>标的</th><th>数量</th><th>价格</th><th>金额</th><th>保证金</th><th>保证金率</th><th>费用</th><th>备注</th><th>操作</th></tr>
        </thead>
        <tbody>
          ${entries.map((entry) => `
            <tr>
              <td>${escapeHtml(entry.date || "-")}</td>
              <td>${escapeHtml(entry.bucket || "-")}</td>
              <td>${escapeHtml(actionLabels[entry.action] || entry.action || "-")}</td>
              <td>${escapeHtml(entry.symbol || entry.name || "-")}</td>
              <td>${escapeHtml(entry.quantity || "-")}</td>
              <td>${escapeHtml(entry.price ? chartValue(Number(entry.price)) : "-")}</td>
              <td>${escapeHtml(yuan(safeAmount(entry.amount)))}</td>
              <td>${escapeHtml(entry.margin ? yuanTextFromWan(safeAmount(entry.margin)) : "-")}</td>
              <td>${escapeHtml(entry.margin && entry.amount ? pct(ratio(safeAmount(entry.margin), safeAmount(entry.amount))) : "-")}</td>
              <td>${escapeHtml(entry.fee ? yuanTextFromWan(safeAmount(entry.fee)) : "-")}</td>
              <td>${escapeHtml(entry.note || "-")}</td>
              <td class="row-actions">
                <button type="button" data-action="edit-entry" data-id="${escapeHtml(entry.id)}">编辑</button>
                <button type="button" data-action="delete-entry" data-id="${escapeHtml(entry.id)}">删除</button>
              </td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    </div>
  `;
}

function ledgerPagination(module, pageInfo) {
  if (pageInfo.totalPages <= 1) return "";
  return `
    <nav class="ledger-pagination" aria-label="投资流水分页">
      <button type="button" data-action="ledger-page-prev" data-module="${escapeHtml(module)}" ${pageInfo.page <= 1 ? "disabled" : ""}>上一页</button>
      <span>第 ${escapeHtml(String(pageInfo.page))} / ${escapeHtml(String(pageInfo.totalPages))} 页</span>
      <button type="button" data-action="ledger-page-next" data-module="${escapeHtml(module)}" ${pageInfo.page >= pageInfo.totalPages ? "disabled" : ""}>下一页</button>
    </nav>
  `;
}

function emptyState(config) {
  return `
    <div class="empty-state">
      <div class="empty-mark"></div>
      <h3>添加你的第一笔记录</h3>
      <p>${escapeHtml(config.description)}</p>
    </div>
  `;
}

function fitCanvas(canvas) {
  const ratioValue = window.devicePixelRatio || 1;
  const rect = canvas.getBoundingClientRect();
  canvas.width = Math.max(1, Math.floor(rect.width * ratioValue));
  canvas.height = Math.max(1, Math.floor(rect.height * ratioValue));
  const ctx = canvas.getContext("2d");
  ctx.setTransform(ratioValue, 0, 0, ratioValue, 0, 0);
  return { ctx, width: rect.width, height: rect.height };
}

function drawEmpty(ctx, width, height, message) {
  ctx.clearRect(0, 0, width, height);
  ctx.fillStyle = "#faf8f3";
  ctx.fillRect(0, 0, width, height);
  ctx.fillStyle = "#657069";
  ctx.font = "14px system-ui, sans-serif";
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  ctx.fillText(message, width / 2, height / 2);
}

function buildPieData(data) {
  const items = [
    { label: "现金", value: data.hardCash },
    { label: "类现金", value: data.reverseRepo },
    { label: "高分红股票", value: data.highDividend },
    { label: "QQQ/QLD", value: data.qqq },
    { label: "深度Put", value: data.spyPutBudget },
    { label: "IC/IM账户权益", value: data.futuresEquity || data.futuresPool }
  ].filter((item) => item.value > 0);
  const used = items.reduce((sum, item) => sum + item.value, 0);
  const other = Math.max(data.totalAssets - used, 0);
  if (other > 0) items.push({ label: "其他/未分配", value: other });
  return items;
}

function drawPie(data) {
  const canvas = document.querySelector("#allocationPie");
  const legend = document.querySelector("#pieLegend");
  if (!canvas || !legend) return;
  const { ctx, width, height } = fitCanvas(canvas);
  const items = buildPieData(data);
  const total = items.reduce((sum, item) => sum + item.value, 0);
  if (total <= 0) {
    legend.innerHTML = "";
    drawEmpty(ctx, width, height, "添加流水后显示结构");
    return;
  }

  ctx.clearRect(0, 0, width, height);
  ctx.fillStyle = "#faf8f3";
  ctx.fillRect(0, 0, width, height);
  const radius = Math.min(width, height) * 0.32;
  const cx = width / 2;
  const cy = height / 2;
  let angle = -Math.PI / 2;
  items.forEach((item, index) => {
    const slice = (item.value / total) * Math.PI * 2;
    ctx.beginPath();
    ctx.moveTo(cx, cy);
    ctx.arc(cx, cy, radius, angle, angle + slice);
    ctx.closePath();
    ctx.fillStyle = chartColors[index % chartColors.length];
    ctx.fill();
    angle += slice;
  });
  ctx.beginPath();
  ctx.arc(cx, cy, radius * 0.56, 0, Math.PI * 2);
  ctx.fillStyle = "#ffffff";
  ctx.fill();
  ctx.fillStyle = "#161b1f";
  ctx.font = "700 22px system-ui, sans-serif";
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  ctx.fillText(yuan(total), cx, cy - 8);
  ctx.fillStyle = "#657069";
  ctx.font = "12px system-ui, sans-serif";
  ctx.fillText("已归类资产", cx, cy + 18);
  legend.innerHTML = items.map((item, index) => {
    const share = pct((item.value / total) * 100);
    const color = chartColors[index % chartColors.length];
    return `<span class="legend-item"><span class="legend-dot" style="background:${color}"></span>${escapeHtml(item.label)} ${escapeHtml(share)}</span>`;
  }).join("");
}

function drawBar(data, calc) {
  const canvas = document.querySelector("#gapBar");
  if (!canvas) return;
  const { ctx, width, height } = fitCanvas(canvas);
  const items = [
    { label: "现金", current: data.hardCash, target: calc.monthlyExpense * 6 },
    { label: "流动垫", current: calc.liquidAssets, target: data.annualExpense },
    { label: "股息", current: data.annualDividend, target: data.annualExpense * 1.2 },
    { label: "QQQ", current: data.qqq, target: data.totalAssets * 0.1 },
    { label: "Put", current: data.spyPutBudget, target: data.totalAssets * 0.01 },
    { label: "期货池", current: data.futuresPool, target: 100 }
  ];
  const max = Math.max(1, ...items.flatMap((item) => [item.current, item.target]));
  const pad = { left: 50, right: 18, top: 22, bottom: 54 };
  const chartW = width - pad.left - pad.right;
  const chartH = height - pad.top - pad.bottom;
  const groupW = chartW / items.length;
  const barW = Math.min(28, groupW * 0.22);

  ctx.clearRect(0, 0, width, height);
  ctx.fillStyle = "#faf8f3";
  ctx.fillRect(0, 0, width, height);
  ctx.strokeStyle = "#ddd8cf";
  ctx.lineWidth = 1;
  ctx.fillStyle = "#657069";
  ctx.font = "12px system-ui, sans-serif";
  ctx.textAlign = "right";
  ctx.textBaseline = "middle";
  for (let i = 0; i <= 4; i += 1) {
    const y = pad.top + chartH * (i / 4);
    const value = max * (1 - i / 4);
    ctx.beginPath();
    ctx.moveTo(pad.left, y);
    ctx.lineTo(width - pad.right, y);
    ctx.stroke();
    ctx.fillText(chartValue(value), pad.left - 8, y);
  }
  items.forEach((item, index) => {
    const x = pad.left + groupW * index + groupW / 2;
    const currentH = (item.current / max) * chartH;
    const targetH = (item.target / max) * chartH;
    ctx.fillStyle = "#0f6a7a";
    ctx.fillRect(x - barW - 3, pad.top + chartH - currentH, barW, currentH);
    ctx.fillStyle = "#d2a23a";
    ctx.fillRect(x + 3, pad.top + chartH - targetH, barW, targetH);
    ctx.fillStyle = "#161b1f";
    ctx.font = "12px system-ui, sans-serif";
    ctx.textAlign = "center";
    ctx.textBaseline = "top";
    ctx.fillText(item.label, x, pad.top + chartH + 14);
  });
}

function loadHistory() {
  try {
    const raw = localStorage.getItem(historyKey);
    const parsed = raw ? JSON.parse(raw) : [];
    if (!Array.isArray(parsed)) return [];
    return parsed
      .map((point) => {
        const date = point.date || (point.ts ? String(point.ts).slice(0, 10) : "");
        if (!date) return null;
        const totalAssets = safeAmount(point.totalAssets);
        const nav = Number.isFinite(Number(point.nav)) ? Number(point.nav) : null;
        return {
          date,
          ts: point.ts || `${date}T00:00:00.000Z`,
          label: point.label || date.slice(5),
          totalAssets,
          netFlow: safeAmount(point.netFlow),
          dailyReturn: Number.isFinite(Number(point.dailyReturn)) ? Number(point.dailyReturn) : 0,
          nav: nav || 1,
          cumulativeReturn: Number.isFinite(Number(point.cumulativeReturn)) ? Number(point.cumulativeReturn) : (nav || 1) - 1,
          moduleTotals: point.moduleTotals || {},
          positions: Array.isArray(point.positions) ? point.positions : []
        };
      })
      .filter(Boolean)
      .sort((a, b) => compareDate(a.date, b.date));
  } catch {
    return [];
  }
}

function saveHistory(history, options = {}) {
  const deduped = [];
  const byDate = new Map();
  history
    .filter((point) => point && point.date)
    .sort((a, b) => compareDate(a.date, b.date))
    .forEach((point) => byDate.set(point.date, point));
  byDate.forEach((point) => deduped.push(point));
  const saved = writeLocalStorage(historyKey, JSON.stringify(deduped.slice(-2500)), "历史快照");
  if (saved && !options.skipRemoteSync) scheduleAutoRemoteSync("history");
  return saved;
}

function historyViewPeriod() {
  const value = localStorage.getItem(historyViewKey);
  return value === "week" || value === "month" ? value : "day";
}

function setHistoryViewPeriod(period, options = {}) {
  const saved = writeLocalStorage(historyViewKey, period, "历史视图设置");
  if (saved && !options.skipRemoteSync) scheduleAutoRemoteSync("historyView");
  return saved;
}

function externalFlowOnDate(entries, date) {
  return entries.reduce((sum, entry) => {
    if (entry.date !== date) return sum;
    if (entry.action === "deposit") return sum + safeAmount(entry.amount);
    if (entry.action === "withdraw") return sum - safeAmount(entry.amount);
    return sum;
  }, 0);
}

function earliestLedgerDate(ledger) {
  const dates = ledger.entries.map((entry) => entry.date).filter(Boolean).sort(compareDate);
  return dates[0] || today();
}

function ashareSymbolsInLedger(ledger) {
  const symbols = new Set();
  ledger.entries.forEach((entry) => {
    const symbol = String(entry.symbol || "").trim();
    if (marketCodeForAshare(symbol)) symbols.add(symbol);
  });
  return Array.from(symbols);
}

async function loadLocalPositionHistoryPayload() {
  if (localPositionHistoryCache !== null) return localPositionHistoryCache;
  try {
    const response = await fetch(`./data/position-history.json?ts=${Date.now()}`);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    localPositionHistoryCache = await response.json();
  } catch {
    localPositionHistoryCache = {};
  }
  return localPositionHistoryCache;
}

function normalizeCloseHistoryRows(rows, source = "本地历史JSON") {
  if (!Array.isArray(rows)) return [];
  return rows
    .map((row) => ({
      date: row.date,
      close: Number(row.close),
      source: row.source || source
    }))
    .filter((row) => row.date && Number.isFinite(row.close) && row.close > 0)
    .sort((a, b) => compareDate(a.date, b.date));
}

function mergeCloseHistoryRows(primaryRows, overrideRows) {
  const merged = new Map();
  normalizeCloseHistoryRows(primaryRows).forEach((row) => merged.set(row.date, row));
  normalizeCloseHistoryRows(overrideRows).forEach((row) => merged.set(row.date, row));
  return Array.from(merged.values()).sort((a, b) => compareDate(a.date, b.date));
}

async function fetchTencentDailyHistory(symbol, endDate, count) {
  const code = marketCodeForAshare(symbol);
  if (!code) return [];
  const url = `https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param=${code},day,,${endDate},${Math.max(count, 5)},qfq`;
  const response = await fetch(url);
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  const payload = await response.json();
  const data = payload && payload.data && payload.data[code] ? payload.data[code] : {};
  const rows = data.qfqday || data.day || [];
  return rows
    .map((row) => ({
      date: row[0],
      close: Number(row[2]),
      source: "腾讯日线"
    }))
    .filter((row) => row.date && Number.isFinite(row.close) && row.close > 0)
    .sort((a, b) => compareDate(a.date, b.date));
}

async function fetchAshareCloseHistory(symbols, startDate, endDate) {
  const days = Math.max(dateRange(startDate, endDate).length + 20, 40);
  const localPayload = await loadLocalPositionHistoryPayload();
  const localHistories = localPayload && localPayload.histories ? localPayload.histories : {};
  const result = {};
  await Promise.all(symbols.map(async (symbol) => {
    const localRows = normalizeCloseHistoryRows(localHistories[symbol], "本地历史JSON");
    try {
      result[symbol] = mergeCloseHistoryRows(localRows, await fetchTencentDailyHistory(symbol, endDate, days));
    } catch {
      result[symbol] = localRows;
    }
  }));
  return result;
}

function closeOnOrBefore(rows, date) {
  if (!Array.isArray(rows) || rows.length === 0) return null;
  let matched = null;
  rows.forEach((row) => {
    if (compareDate(row.date, date) <= 0) matched = row;
  });
  return matched;
}

function snapshotPositionChanged(entries, position, date) {
  return entries.some((entry) => entry.date === date && positionKey(entry.module, entry.symbol, entry.name) === position.key && entry.action !== "dividend" && entry.action !== "interest");
}

function previousSnapshotPosition(previousSnapshot, key) {
  if (!previousSnapshot || !Array.isArray(previousSnapshot.positions)) return null;
  return previousSnapshot.positions.find((position) => position.key === key) || null;
}

function enrichPositionForSnapshot(position, valuation, closeHistory, previousSnapshot, entries, date) {
  const close = closeOnOrBefore(closeHistory[position.symbol], date);
  if (close && position.quantity > 0) {
    const marketValue = (position.quantity * close.close) / 10000;
    const unrealizedPnl = marketValue - position.netInvestment;
    return {
      ...position,
      currentPrice: close.close,
      manualMarketValue: null,
      marketValue,
      marketValueSource: "history-close",
      unrealizedPnl,
      unrealizedPnlPct: ratio(unrealizedPnl, Math.abs(position.netInvestment)),
      valuationNote: `${close.source} ${close.date}`,
      valuationUpdatedAt: close.date,
      valuationSource: close.source,
      hasManualValuation: true
    };
  }

  const previous = previousSnapshotPosition(previousSnapshot, position.key);
  if (previous && position.quantity > 0 && !snapshotPositionChanged(entries, position, date)) {
    const marketValue = safeAmount(previous.marketValue);
    const unrealizedPnl = marketValue - position.netInvestment;
    return {
      ...position,
      currentPrice: optionalNumber(previous.currentPrice),
      manualMarketValue: null,
      marketValue,
      marketValueSource: "carry-forward",
      unrealizedPnl,
      unrealizedPnlPct: ratio(unrealizedPnl, Math.abs(position.netInvestment)),
      valuationNote: "沿用上一日估值",
      valuationUpdatedAt: previous.valuationUpdatedAt || previous.date || "",
      valuationSource: previous.valuationSource || "沿用估值",
      hasManualValuation: true
    };
  }

  return enrichPosition(position, valuation);
}

function summarizeLedgerAtDate(ledger, date, closeHistory, previousSnapshot) {
  const entries = ledger.entries.filter((entry) => !entry.date || compareDate(entry.date, date) <= 0);
  const valuations = loadPositionValuations();
  const positions = buildPositionsFromEntries(entries, valuations, (position, valuation) =>
    enrichPositionForSnapshot(position, valuation, closeHistory, previousSnapshot, entries, date)
  );
  const bucketTotals = sumPositionsBy(positions, "bucket");
  const moduleTotals = sumPositionsBy(positions, "module");
  const totalAssets = Object.values(bucketTotals).reduce((sum, value) => sum + value, 0);
  return {
    totalAssets,
    bucketTotals,
    moduleTotals: {
      dividend: moduleTotals.dividend || 0,
      qqq: moduleTotals.qqq || 0,
      put: moduleTotals.put || 0,
      ic: moduleTotals.ic || 0
    },
    positions
  };
}

function buildDailySnapshot(date, ledger, closeHistory, previousSnapshot) {
  const data = summarizeLedgerAtDate(ledger, date, closeHistory, previousSnapshot);
  const netFlow = externalFlowOnDate(ledger.entries, date);
  const previousAssets = previousSnapshot ? safeAmount(previousSnapshot.totalAssets) : 0;
  const dailyReturn = previousSnapshot && previousAssets > 0
    ? (data.totalAssets - previousAssets - netFlow) / previousAssets
    : 0;
  const previousNav = previousSnapshot ? safeAmount(previousSnapshot.nav) || 1 : 1;
  const nav = previousNav * (1 + dailyReturn);
  return {
    date,
    ts: `${date}T00:00:00.000Z`,
    label: date.slice(5),
    totalAssets: data.totalAssets,
    netFlow,
    dailyReturn,
    nav,
    cumulativeReturn: nav - 1,
    moduleTotals: data.moduleTotals,
    positions: data.positions.map((position) => ({
      key: position.key,
      module: position.module,
      symbol: position.symbol,
      name: position.name,
      quantity: position.quantity,
      currentPrice: position.currentPrice,
      marketValue: position.marketValue,
      valuationSource: position.valuationSource,
      valuationUpdatedAt: position.valuationUpdatedAt
    }))
  };
}

async function syncDailyHistory(options = {}) {
  if (dailyHistorySyncing) return loadHistory();
  dailyHistorySyncing = true;
  try {
    const ledger = loadLedger();
    if (ledger.entries.length === 0 && summarizeLedger(ledger).totalAssets <= 0) return loadHistory();
    let history = loadHistory();
    if (options.forceToday) history = history.filter((point) => point.date !== today());
    const last = history[history.length - 1] || null;
    const startDate = options.forceToday ? today() : last ? addDays(last.date, 1) : earliestLedgerDate(ledger);
    const endDate = today();
    const dates = dateRange(startDate, endDate);
    if (dates.length === 0) return history;

    const closeHistory = await fetchAshareCloseHistory(ashareSymbolsInLedger(ledger), dates[0], endDate);
    let previousSnapshot = history[history.length - 1] || null;
    dates.forEach((date) => {
      const snapshot = buildDailySnapshot(date, ledger, closeHistory, previousSnapshot);
      history.push(snapshot);
      previousSnapshot = snapshot;
    });
    if (!saveHistory(history)) return loadHistory();
    history = loadHistory();
    if (options.render !== false) render();
    return history;
  } finally {
    dailyHistorySyncing = false;
  }
}

function aggregateHistory(history, period) {
  if (period === "day") return history;
  const groups = new Map();
  history.forEach((point) => {
    const date = parseDate(point.date);
    if (!date) return;
    let key = point.date.slice(0, 7);
    if (period === "week") {
      const monday = new Date(date);
      const day = monday.getDay() || 7;
      monday.setDate(monday.getDate() - day + 1);
      key = formatDate(monday);
    }
    groups.set(key, point);
  });
  return Array.from(groups.values()).sort((a, b) => compareDate(a.date, b.date));
}

function historySummary(history) {
  if (history.length === 0) return "0 条快照";
  const latest = history[history.length - 1];
  return `${history.length} 条快照 / 最新 ${latest.date} / 净值 ${format(latest.nav)} / 累计 ${pct(latest.cumulativeReturn * 100)}`;
}

function drawDualSeries(ctx, points, pad, chartW, chartH, key, color, min, max) {
  const span = Math.max(max - min, 0.000001);
  ctx.beginPath();
  points.forEach((point, index) => {
    const value = Number(point[key] || 0);
    const x = points.length === 1 ? pad.left + chartW / 2 : pad.left + (chartW * index) / (points.length - 1);
    const y = pad.top + chartH - ((value - min) / span) * chartH;
    if (index === 0) ctx.moveTo(x, y);
    else ctx.lineTo(x, y);
  });
  ctx.strokeStyle = color;
  ctx.lineWidth = 2.5;
  ctx.stroke();
}

function drawLine(history) {
  const canvas = document.querySelector("#historyLine");
  if (!canvas) return;
  const { ctx, width, height } = fitCanvas(canvas);
  if (history.length === 0) {
    drawEmpty(ctx, width, height, "打开页面后自动生成每日快照");
    return;
  }
  const period = historyViewPeriod();
  const points = aggregateHistory(history, period);
  const pad = { left: 54, right: 58, top: 42, bottom: 48 };
  const chartW = width - pad.left - pad.right;
  const chartH = height - pad.top - pad.bottom;
  const assetValues = points.map((point) => Number(point.totalAssets || 0));
  const returnValues = points.map((point) => Number(point.cumulativeReturn || 0) * 100);
  const assetMin = Math.min(...assetValues, 0);
  const assetMax = Math.max(...assetValues, 1);
  const returnMin = Math.min(...returnValues, 0);
  const returnMax = Math.max(...returnValues, 1);

  ctx.clearRect(0, 0, width, height);
  ctx.fillStyle = "#faf8f3";
  ctx.fillRect(0, 0, width, height);
  ctx.strokeStyle = "#ddd8cf";
  ctx.lineWidth = 1;
  ctx.font = "12px system-ui, sans-serif";
  ctx.textBaseline = "middle";
  for (let i = 0; i <= 4; i += 1) {
    const y = pad.top + chartH * (i / 4);
    ctx.beginPath();
    ctx.moveTo(pad.left, y);
    ctx.lineTo(width - pad.right, y);
    ctx.stroke();
    const assetLabel = assetMin + (assetMax - assetMin) * (1 - i / 4);
    const returnLabel = returnMin + (returnMax - returnMin) * (1 - i / 4);
    ctx.fillStyle = "#0f6a7a";
    ctx.textAlign = "right";
    ctx.fillText(chartValue(assetLabel), pad.left - 8, y);
    ctx.fillStyle = "#c1742f";
    ctx.textAlign = "left";
    ctx.fillText(`${chartValue(returnLabel)}%`, width - pad.right + 8, y);
  }

  drawDualSeries(ctx, points, pad, chartW, chartH, "totalAssets", "#0f6a7a", assetMin, assetMax);
  const returnPoints = points.map((point) => ({ ...point, cumulativeReturnPct: Number(point.cumulativeReturn || 0) * 100 }));
  drawDualSeries(ctx, returnPoints, pad, chartW, chartH, "cumulativeReturnPct", "#c1742f", returnMin, returnMax);

  const labels = [
    { label: "总资产金额", color: "#0f6a7a", x: pad.left },
    { label: "净值累计收益率", color: "#c1742f", x: pad.left + 128 }
  ];
  labels.forEach((item) => {
    ctx.fillStyle = item.color;
    ctx.fillRect(item.x, 16, 10, 10);
    ctx.fillStyle = "#657069";
    ctx.textAlign = "left";
    ctx.fillText(item.label, item.x + 16, 21);
  });

  const tickIndexes = points.length <= 2 ? points.map((_, index) => index) : [0, Math.floor((points.length - 1) / 2), points.length - 1];
  tickIndexes.forEach((index) => {
    const point = points[index];
    const x = points.length === 1 ? pad.left + chartW / 2 : pad.left + (chartW * index) / (points.length - 1);
    ctx.fillStyle = "#657069";
    ctx.textAlign = "center";
    ctx.textBaseline = "top";
    ctx.fillText(point.date.slice(5), x, pad.top + chartH + 16);
  });
}

function renderCharts(data, calc) {
  drawPie(data);
  drawBar(data, calc);
  const history = loadHistory();
  const historyCount = document.querySelector("#historyCount");
  if (historyCount) historyCount.textContent = historySummary(history);
  drawLine(history);
}

function loadValuation() {
  try {
    const raw = localStorage.getItem(valuationKey);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

function saveValuation(payload, options = {}) {
  const saved = writeLocalStorage(valuationKey, JSON.stringify(payload), "IC/IM 估值");
  if (saved && !options.skipRemoteSync) scheduleAutoRemoteSync("valuation");
  return saved;
}

function applyValuation(payload, options = {}) {
  const ledger = loadLedger();
  const indexes = payload && payload.indexes ? payload.indexes : {};
  const ic = indexes.IC || {};
  const im = indexes.IM || {};
  const applied = [];
  const preserved = [];
  if (typeof ic.pb_percentile === "number") {
    if (ledger.settings.icPbSource === "auto") {
      ledger.settings.icPb = ic.pb_percentile;
      applied.push("IC PB 百分位");
    } else {
      preserved.push("IC");
    }
  }
  if (typeof im.pb_percentile === "number") {
    if (ledger.settings.imPbSource === "auto") {
      ledger.settings.imPb = im.pb_percentile;
      applied.push("IM PB 百分位");
    } else {
      preserved.push("IM");
    }
  }
  if (applied.length && !saveLedger(ledger)) return false;
  if (!saveValuation(payload)) return false;
  renderValuation(payload);
  if (options.render !== false) render();
  const status = document.querySelector("#valuationStatus");
  if (status) {
    const prefix = options.source === "api" ? "已自动刷新" : "已读取";
    if (applied.length) status.textContent = `${prefix}，已更新自动 PB：${applied.join("、")}`;
    else if (preserved.length) status.textContent = `${prefix}，保留手工 PB：${preserved.join("、")}；可点“用自动值”采纳`;
    else status.textContent = `${prefix}；PB 分位缺失，保留手工输入`;
  }
  if (preserved.length && !options.silent) showToast(`已保留 ${preserved.join("、")} 手工 PB`, "info");
  return true;
}

function renderValuation(payload = loadValuation()) {
  const cards = document.querySelector("#valuationCards");
  const status = document.querySelector("#valuationStatus");
  if (!cards || !status) return;
  if (!payload || !payload.indexes) {
    cards.innerHTML = `
      <article class="valuation-card">
        <h3>等待数据<span>PB 分位手工兜底</span></h3>
        <p class="valuation-note">运行脚本生成 JSON 后点击读取；直接打开 HTML 失败时可导入 JSON。</p>
      </article>
    `;
    status.textContent = "未读取";
    return;
  }
  const tradeDate = payload.trade_date || "-";
  cards.innerHTML = `
    <article class="valuation-card valuation-source-card">
      <h3>数据来源<span>${escapeHtml(displayDateTime(payload.generated_at))}</span></h3>
      <p class="valuation-note">交易日：${escapeHtml(displayTradeDate(tradeDate))}</p>
      <div class="source-list">
        <span>当前估值：${escapeHtml((payload.source || {}).current_valuation || "-")}</span>
        <span>PB兜底：${escapeHtml((payload.source || {}).current_pb_fallback || "-")}</span>
        <span>PE历史：${escapeHtml((payload.source || {}).pe_history || "-")}</span>
        <span>贴水：${escapeHtml((payload.source || {}).basis || "-")}</span>
      </div>
      <p class="valuation-note">${escapeHtml(payload.strategy_rule || "")}</p>
    </article>
  ` + ["IC", "IM"].map((key) => {
    const item = payload.indexes[key] || {};
    const pbPct = typeof item.pb_percentile === "number" ? pct(item.pb_percentile) : "手工";
    const pePct = typeof item.pe_percentile === "number" ? pct(item.pe_percentile) : "-";
    const note = item.pb_percentile_manual_required ? "PB 历史分位缺失，本次不覆盖策略 PB 百分位。" : "PB 百分位来自本地历史 CSV。";
    const basis = item.basis || {};
    const roll = basis.roll_notice || null;
    const rollBanner = roll ? `<p class="valuation-note ${escapeHtml(rollNoticeClass(roll.level))}">换月提醒：${escapeHtml(roll.contract || "-")}，${escapeHtml(roll.message || "-")}</p>` : "";
    const basisRows = ((basis.contracts || [])).map((contract) => `
      <tr>
        <td>${escapeHtml(contract.contract || "-")}</td>
        <td>${escapeHtml(displayNumber(contract.spot))}</td>
        <td>${escapeHtml(displayNumber(contract.future))}</td>
        <td>${escapeHtml(displaySigned(contract.basis))}</td>
        <td>${escapeHtml(contract.annualized_basis_pct === null || contract.annualized_basis_pct === undefined ? "-" : pct(contract.annualized_basis_pct))}</td>
        <td>${escapeHtml(contract.delivery_date || "-")}</td>
        <td>${escapeHtml(contract.days_left ?? "-")}</td>
        <td class="${escapeHtml(rollNoticeClass((contract.roll_notice || {}).level))}">${escapeHtml((contract.roll_notice || {}).level === "normal" ? "正常" : ((contract.roll_notice || {}).message || "-"))}</td>
      </tr>
    `).join("");
    const basisTable = basisRows ? `
      <details class="compact-details">
        <summary>查看 ${escapeHtml(key)} 贴水明细</summary>
        <div class="basis-table-wrap">
          <table class="basis-table">
            <thead><tr><th>合约</th><th>现货</th><th>期货</th><th>贴水</th><th>年化</th><th>交割日</th><th>天数</th><th>提醒</th></tr></thead>
            <tbody>${basisRows}</tbody>
          </table>
        </div>
      </details>
    ` : `<p class="valuation-note">贴水数据未生成。</p>`;
    return `
      <article class="valuation-card">
        <h3>${escapeHtml(key)} ${escapeHtml(item.name || "")}<span>${escapeHtml(item.trade_date || tradeDate)}</span></h3>
        <div class="valuation-values">
          <div class="valuation-item"><div class="name">PE</div><div class="num">${escapeHtml(displayNumber(item.pe))}</div></div>
          <div class="valuation-item"><div class="name">PB</div><div class="num">${escapeHtml(displayNumber(item.pb))}</div></div>
          <div class="valuation-item"><div class="name">PE分位</div><div class="num">${escapeHtml(pePct)}</div></div>
          <div class="valuation-item"><div class="name">PB分位</div><div class="num">${escapeHtml(pbPct)}</div></div>
        </div>
        <p class="valuation-note">${escapeHtml(note)}</p>
        <p class="valuation-note">当前 PB：${escapeHtml(valuationSourceLabel(item.pb_source))}；当前 PE：${escapeHtml(valuationSourceLabel(item.pe_source))}。</p>
        ${rollBanner}
        ${basisTable}
      </article>
    `;
  }).join("");
  status.textContent = `已读取 ${tradeDate}`;
}

function valuationSourceLabel(source) {
  const labels = {
    csindex_current: "中证官网",
    csindex_pe_history: "中证官网历史序列",
    eastmoney_current_fallback: "东方财富兜底"
  };
  return labels[source] || "缺失";
}

function rollNoticeClass(level) {
  if (level === "expired" || level === "alert") return "roll-alert";
  if (level === "watch") return "roll-watch";
  return "roll-normal";
}

function displaySigned(value) {
  if (value === null || value === undefined || value === "" || !Number.isFinite(Number(value))) return "-";
  const num = Number(value);
  return `${num > 0 ? "+" : ""}${chartValue(num)}`;
}

function amountFromQuantityPrice(quantity, price, multiplier = 1) {
  const q = Number(quantity);
  const p = Number(price);
  const m = Number(multiplier || 1);
  if (!Number.isFinite(q) || !Number.isFinite(p) || !Number.isFinite(m) || q <= 0 || p <= 0 || m <= 0) return null;
  return (q * p * m) / 10000;
}

function handleSettingsInput(event) {
  const form = event.target.closest("#settingsForm");
  if (!form) return;
  const partial = {
    annualExpense: numberFromForm(form, "annualExpense"),
    newMoney: numberFromForm(form, "newMoney"),
    manualTotalAssets: numberFromForm(form, "manualTotalAssets"),
    icPb: numberFromForm(form, "icPb"),
    imPb: numberFromForm(form, "imPb")
  };
  if (event.target.name === "icPb") partial.icPbSource = "manual";
  if (event.target.name === "imPb") partial.imPbSource = "manual";
  if (!saveSettings(partial)) return;
  render();
  showToast("核心参数已保存", "success");
}

function clearEntryValidation(form) {
  form.querySelectorAll(".field-invalid").forEach((field) => field.classList.remove("field-invalid"));
  Array.from(form.elements).forEach((element) => element.removeAttribute("aria-invalid"));
  const errorBox = form.querySelector("[data-entry-errors]");
  if (errorBox) {
    errorBox.hidden = true;
    errorBox.textContent = "";
  }
}

function addEntryValidationError(form, name, message, errors) {
  errors.push(message);
  const field = form.elements[name];
  if (!field) return;
  field.setAttribute("aria-invalid", "true");
  const label = field.closest("label");
  if (label) label.classList.add("field-invalid");
}

function validateEntryForm(form) {
  clearEntryValidation(form);
  const errors = [];
  const action = form.elements.action ? form.elements.action.value : "";
  const hasInstrument = Boolean(String(form.elements.symbol.value || "").trim() || String(form.elements.name.value || "").trim());
  const amount = amountWanFromEntryForm(form);
  const quantity = numberFromForm(form, "quantity");
  const price = numberFromForm(form, "price");
  const multiplier = form.elements.multiplier ? numberFromForm(form, "multiplier") : 1;
  const margin = marginWanFromEntryForm(form);
  const amountField = form.elements.amountYuan ? "amountYuan" : "amount";

  if (!form.elements.date.value) addEntryValidationError(form, "date", "日期必填", errors);
  if (!form.elements.bucket.value) addEntryValidationError(form, "bucket", "分类必填", errors);
  if (!action) addEntryValidationError(form, "action", "动作必填", errors);

  if (action === "buy" || action === "sell") {
    const label = entryActionLabel(form.dataset.module, action);
    if (!hasInstrument) addEntryValidationError(form, "symbol", `${label}必须填写标的代码或名称`, errors);
    if (quantity <= 0) addEntryValidationError(form, "quantity", `${label}必须填写数量/手数`, errors);
    else if (!Number.isInteger(quantity)) addEntryValidationError(form, "quantity", `${label}数量/手数必须为整数`, errors);
    if (price <= 0) addEntryValidationError(form, "price", `${label}必须填写价格/指数点位`, errors);
    if (multiplier <= 0) addEntryValidationError(form, "multiplier", "乘数必须大于 0", errors);
    if (amountFromQuantityPrice(quantity, price, multiplier) === null) addEntryValidationError(form, amountField, "金额无法自动计算", errors);
    if (form.dataset.module === "ic" && margin < 0) addEntryValidationError(form, "marginYuan", "保证金不能为负", errors);
  } else if (action === "dividend" || action === "interest") {
    if (!hasInstrument) addEntryValidationError(form, "symbol", `${actionLabels[action]}必须填写标的代码或名称`, errors);
    if (amount <= 0) addEntryValidationError(form, amountField, "金额必须大于 0", errors);
  } else if (action === "futures_deposit" || action === "expire") {
    if (amount <= 0) addEntryValidationError(form, amountField, "金额必须大于 0", errors);
  }

  const uniqueErrors = Array.from(new Set(errors));
  if (uniqueErrors.length) {
    const errorBox = form.querySelector("[data-entry-errors]");
    if (errorBox) {
      errorBox.hidden = false;
      errorBox.textContent = uniqueErrors.join("；");
    }
    const firstInvalid = form.querySelector("[aria-invalid='true']");
    if (firstInvalid) firstInvalid.focus();
    showToast(uniqueErrors[0], "error");
    return false;
  }
  return true;
}

function handleCashTransferSubmit(event) {
  const form = event.target.closest("#cashTransferForm");
  if (!form) return false;
  event.preventDefault();
  clearEntryValidation(form);
  const errors = [];
  const amountYuan = numberFromForm(form, "amountYuan");
  if (!form.elements.date.value) addEntryValidationError(form, "date", "日期必填", errors);
  if (amountYuan <= 0) addEntryValidationError(form, "amountYuan", "转账金额必须大于 0", errors);
  if (errors.length) {
    const errorBox = form.querySelector("[data-entry-errors]");
    if (errorBox) {
      errorBox.hidden = false;
      errorBox.textContent = Array.from(new Set(errors)).join("；");
    }
    showToast(errors[0], "error");
    return true;
  }
  const ledger = loadLedger();
  ledger.entries.push({
    id: makeId(),
    module: "cash",
    date: form.elements.date.value || today(),
    bucket: "现金池",
    action: form.elements.action.value === "withdraw" ? "withdraw" : "deposit",
    symbol: "",
    name: "全局现金池",
    quantity: "",
    price: "",
    amount: wanFromYuan(amountYuan),
    fee: 0,
    note: form.elements.note.value.trim()
  });
  if (!saveLedger(ledger)) return true;
  render();
  showToast("已记录全局现金池转账", "success");
  return true;
}

function handleEntrySubmit(event) {
  if (handleCashTransferSubmit(event)) return;
  const form = event.target.closest("#entryForm");
  if (!form) return;
  event.preventDefault();
  if (!validateEntryForm(form)) return;
  const ledger = loadLedger();
  const id = form.dataset.id || makeId();
  const entry = {
    id,
    module: form.dataset.module,
    date: form.elements.date.value || today(),
    bucket: form.elements.bucket.value,
    action: form.elements.action.value,
    symbol: form.elements.symbol.value.trim(),
    name: form.elements.name.value.trim(),
    quantity: form.elements.quantity.value,
    price: form.elements.price.value,
    multiplier: form.elements.multiplier ? numberFromForm(form, "multiplier") || defaultFuturesMultiplier : "",
    deliveryDate: form.elements.symbol ? (parseFuturesContract(form.elements.symbol.value) || {}).deliveryDate || "" : "",
    amount: amountWanFromEntryForm(form),
    margin: marginWanFromEntryForm(form),
    fee: feeWanFromEntryForm(form),
    note: form.elements.note.value.trim()
  };
  const index = ledger.entries.findIndex((item) => item.id === id);
  if (index >= 0) ledger.entries[index] = entry;
  else ledger.entries.push(entry);
  if (!saveLedger(ledger)) return;
  editingId = null;
  render();
  showToast(index >= 0 ? "已保存修改" : "已新增 1 笔记录", "success");
}

function updateAmountPreview(form) {
  const amount = amountWanFromEntryForm(form);
  const hint = form.querySelector("[data-amount-preview]");
  if (!hint) return;
  hint.textContent = amount > 0 ? `${yuan(amount)} / ${yuanTextFromWan(amount)}` : "买卖按数量 × 价格自动计算；分红/利息手填";
}

function updateMarginPreview(form) {
  const hint = form.querySelector("[data-margin-preview]");
  if (!hint) return;
  hint.textContent = marginRatioText(amountWanFromEntryForm(form), marginWanFromEntryForm(form));
}

function handleEntryAmountAutoCalc(event) {
  const form = event.target.closest("#entryForm");
  if (!form) return;
  const targetName = event.target.name;
  if (targetName === "marginYuan" || targetName === "margin") {
    updateMarginPreview(form);
    return;
  }
  if (targetName === "amountYuan" || targetName === "amount") {
    form.dataset.amountManual = "true";
    form.dataset.amountAuto = "false";
    updateAmountPreview(form);
    updateMarginPreview(form);
    return;
  }
  if (targetName !== "quantity" && targetName !== "price" && targetName !== "multiplier") return;
  const quantity = Number(form.elements.quantity ? form.elements.quantity.value : "");
  const price = Number(form.elements.price ? form.elements.price.value : "");
  const multiplier = form.elements.multiplier ? Number(form.elements.multiplier.value || defaultFuturesMultiplier) : 1;
  const amountInput = form.elements.amountYuan || form.elements.amount;
  const amount = amountFromQuantityPrice(quantity, price, multiplier);
  if (!amountInput || amount === null) {
    updateAmountPreview(form);
    updateMarginPreview(form);
    return;
  }
  const canAutoFill = form.dataset.amountManual !== "true" || form.dataset.amountAuto === "true" || amountInput.value === "";
  if (!canAutoFill) {
    updateAmountPreview(form);
    updateMarginPreview(form);
    return;
  }
  amountInput.value = chartValue(yuanFromWan(amount));
  form.dataset.amountAuto = "true";
  updateAmountPreview(form);
  updateMarginPreview(form);
}

function handleInstrumentMemoryInput(event) {
  const form = event.target.closest("#entryForm");
  if (!form || (event.target.name !== "symbol" && event.target.name !== "name")) return;
  const module = form.dataset.module || "";
  const symbolInput = form.elements.symbol;
  const nameInput = form.elements.name;
  if (!symbolInput || !nameInput) return;
  const contractHint = form.querySelector("[data-contract-hint]");
  if (contractHint) contractHint.textContent = futuresContractHint(symbolInput.value);

  const derivedName = module === "ic" ? futuresContractName(symbolInput.value) : "";
  if (module === "ic" && event.target.name === "symbol") {
    if (derivedName && (!nameInput.value.trim() || nameInput.dataset.autoName === "true")) {
      nameInput.value = derivedName;
      nameInput.dataset.autoName = "true";
    }
  }

  if (module === "ic" && event.target.name === "name") {
    nameInput.dataset.autoName = "false";
  }

  if (event.target.name === "symbol") {
    filterInstrumentSuggestions(form, "symbol");
    const match = findInstrumentMemory(symbolInput.value, "symbol", module);
    if (match && match.name && !derivedName) nameInput.value = match.name;
    return;
  }

  filterInstrumentSuggestions(form, "name");
  const match = findInstrumentMemory(nameInput.value, "name", module);
  if (match && match.symbol) symbolInput.value = match.symbol;
}

function closeInstrumentSuggestions() {
  document.querySelectorAll("[data-instrument-menu]").forEach((menu) => {
    menu.hidden = true;
  });
}

function filterInstrumentSuggestions(form, field) {
  const input = form.elements[field];
  const menu = form.querySelector(`[data-instrument-menu="${field}"]`);
  if (!input || !menu) return;
  const query = String(input.value || "").trim().toLowerCase();
  let visible = 0;
  menu.querySelectorAll("[data-action='choose-instrument']").forEach((button) => {
    const search = button.dataset.search || "";
    const matched = !query || search.includes(query);
    button.hidden = !matched;
    if (matched) visible += 1;
  });
  const empty = menu.querySelector("[data-instrument-empty]");
  if (empty) empty.hidden = visible > 0;
  menu.hidden = false;
}

function openInstrumentSuggestions(input) {
  const form = input.closest("#entryForm");
  const field = input.dataset.instrumentField;
  if (!form || !field) return;
  closeInstrumentSuggestions();
  filterInstrumentSuggestions(form, field);
}

function chooseInstrument(action) {
  const form = action.closest("#entryForm");
  if (!form || !form.elements.symbol || !form.elements.name) return false;
  form.elements.name.value = action.dataset.name || "";
  form.elements.symbol.value = action.dataset.symbol || "";
  form.elements.name.dataset.autoName = "false";
  closeInstrumentSuggestions();
  const contractHint = form.querySelector("[data-contract-hint]");
  if (contractHint) contractHint.textContent = futuresContractHint(form.elements.symbol.value);
  return true;
}

function handleLedgerFilterInput(event) {
  const tool = event.target.closest(".ledger-tools");
  if (!tool) return;
  const module = currentTab;
  const filter = getLedgerFilter(module);
  const search = tool.querySelector("#ledgerSearch");
  const action = tool.querySelector("#ledgerActionFilter");
  const bucket = tool.querySelector("#ledgerBucketFilter");
  filter.search = search ? search.value : "";
  filter.action = action ? action.value : "";
  filter.bucket = bucket ? bucket.value : "";
  filter.page = 1;
  updateLedgerTableArea(module);
}

function handlePositionValuationChange(event) {
  const input = event.target.closest("[data-position-field]");
  if (!input) return false;
  const rowNode = input.closest("[data-position-key]");
  if (!rowNode) return false;
  const key = rowNode.dataset.positionKey;
  const valuations = loadPositionValuations();
  const rowInputs = rowNode.querySelectorAll("[data-position-field]");
  const next = { ...(valuations[key] || {}) };
  rowInputs.forEach((fieldInput) => {
    const field = fieldInput.dataset.positionField;
    if (field === "note") next[field] = fieldInput.value.trim();
    else next[field] = fieldInput.value === "" ? null : Number(fieldInput.value);
  });
  next.updatedAt = new Date().toISOString();
  next.source = "手工估值";
  if (next.currentPrice === null && next.marketValue === null && !next.note) delete valuations[key];
  else valuations[key] = next;
  if (!savePositionValuations(valuations)) return true;
  render();
  return true;
}

function marketCodeForAshare(symbol) {
  const code = String(symbol || "").trim();
  if (!/^\d{6}$/.test(code)) return null;
  if (/^(000|001|002|003|159|300|301)/.test(code)) return `sz${code}`;
  if (/^(510|511|512|513|515|516|517|518|519|520|560|561|562|563|588|600|601|603|605|688|689)/.test(code)) return `sh${code}`;
  return null;
}

function isUsTicker(symbol) {
  const code = String(symbol || "").trim().toUpperCase();
  if (marketCodeForAshare(code)) return false;
  return /^[A-Z][A-Z0-9.-]{0,9}$/.test(code);
}

function canSyncPosition(position) {
  return Boolean(marketCodeForAshare(position.symbol) || isUsTicker(position.symbol));
}

function loadScript(src) {
  return new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = src;
    script.onload = resolve;
    script.onerror = reject;
    document.head.appendChild(script);
    setTimeout(() => {
      script.remove();
    }, 30000);
  });
}

async function fetchSinaAshareQuote(symbol) {
  const marketCode = marketCodeForAshare(symbol);
  if (!marketCode) throw new Error("暂不支持该代码自动同步");
  const variableName = `hq_str_${marketCode}`;
  delete window[variableName];
  await loadScript(`https://hq.sinajs.cn/list=${marketCode}&_=${Date.now()}`);
  const raw = window[variableName];
  if (!raw) throw new Error("行情源未返回数据");
  const fields = String(raw).split(",");
  const name = fields[0] || symbol;
  const price = Number(fields[3]);
  const tradeDate = fields[30] || today();
  const tradeTime = fields[31] || "";
  if (!Number.isFinite(price) || price <= 0) throw new Error("行情价格不可用");
  return {
    symbol,
    name,
    price,
    source: "新浪A股",
    updatedAt: tradeDate && tradeTime ? `${tradeDate} ${tradeTime}` : new Date().toISOString()
  };
}

async function fetchTencentAshareQuote(symbol) {
  const marketCode = marketCodeForAshare(symbol);
  if (!marketCode) throw new Error("暂不支持该代码自动同步");
  const variableName = `v_${marketCode}`;
  delete window[variableName];
  await loadScript(`https://qt.gtimg.cn/q=${marketCode}&_=${Date.now()}`);
  const raw = window[variableName];
  if (!raw) throw new Error("备用行情源未返回数据");
  const fields = String(raw).split("~");
  const name = fields[1] || symbol;
  const price = Number(fields[3]);
  const timestamp = fields[30] || "";
  if (!Number.isFinite(price) || price <= 0) throw new Error("备用行情价格不可用");
  return {
    symbol,
    name,
    price,
    source: "腾讯A股",
    updatedAt: timestamp || new Date().toISOString()
  };
}

async function fetchAshareQuote(symbol) {
  try {
    return await fetchSinaAshareQuote(symbol);
  } catch (sinaError) {
    try {
      const quote = await fetchTencentAshareQuote(symbol);
      return { ...quote, fallbackReason: sinaError.message || "新浪行情不可用" };
    } catch (tencentError) {
      throw new Error(`${sinaError.message || "新浪行情不可用"}；${tencentError.message || "腾讯行情不可用"}`);
    }
  }
}

function getPositionByKey(key) {
  return summarizeLedger().positions.find((position) => position.key === key) || null;
}

function localQuoteForSymbol(payload, symbol) {
  const quotes = payload && payload.quotes ? payload.quotes : {};
  const code = String(symbol || "").trim();
  return quotes[code] || quotes[code.toUpperCase()] || null;
}

async function loadLocalPositionQuotes() {
  const response = await fetch(`./data/position-quotes.json?ts=${Date.now()}`);
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.json();
}

async function fetchPositionQuote(position, localQuotesPayload = null) {
  if (marketCodeForAshare(position.symbol)) return fetchAshareQuote(position.symbol);
  if (isUsTicker(position.symbol)) {
    const payload = localQuotesPayload || await loadLocalPositionQuotes();
    const quote = localQuoteForSymbol(payload, position.symbol);
    if (!quote || !Number.isFinite(Number(quote.price))) throw new Error("本地价格 JSON 中没有该美股价格");
    return {
      symbol: position.symbol,
      name: quote.name || position.name || position.symbol,
      price: Number(quote.price),
      source: quote.source || "本地价格JSON",
      updatedAt: quote.trade_time || payload.generated_at || new Date().toISOString()
    };
  }
  throw new Error("该标的暂不支持自动同步");
}

async function syncPositionPrice(key) {
  const position = getPositionByKey(key);
  if (!position) return;
  const valuations = loadPositionValuations();
  const current = { ...(valuations[key] || {}) };
  let ok = true;
  let message = "已同步价格";
  try {
    const quote = await fetchPositionQuote(position);
    valuations[key] = {
      ...current,
      currentPrice: quote.price,
      updatedAt: new Date().toISOString(),
      source: quote.source,
      note: current.note || `${quote.source} ${quote.updatedAt}${quote.fallbackReason ? `；备用原因：${quote.fallbackReason}` : ""}`
    };
    if (!savePositionValuations(valuations)) return;
  } catch (error) {
    ok = false;
    message = `同步失败：${error.message || "行情不可用"}`;
    valuations[key] = {
      ...current,
      updatedAt: new Date().toISOString(),
      source: current.source || "手工估值",
      note: current.note || `同步失败：${error.message || "行情不可用"}`
    };
    if (!savePositionValuations(valuations)) return;
  }
  render();
  if (!ok && (fileMode() || isUsTicker(position.symbol))) showLocalServiceGuide("quotes");
  showToast(message, ok ? "success" : "error");
}

async function syncModulePrices(module) {
  const positions = positionsForModule(module).filter(canSyncPosition);
  if (positions.length === 0) {
    showToast("当前模块没有可自动同步的代码", "info");
    return;
  }
  const valuations = loadPositionValuations();
  let localQuotesPayload = null;
  if (positions.some((position) => isUsTicker(position.symbol))) {
    try {
      localQuotesPayload = await loadLocalPositionQuotes();
    } catch {
      localQuotesPayload = null;
    }
  }
  let success = 0;
  let failed = 0;
  for (const position of positions) {
    const current = { ...(valuations[position.key] || {}) };
    try {
      const quote = await fetchPositionQuote(position, localQuotesPayload);
      valuations[position.key] = {
        ...current,
        currentPrice: quote.price,
        updatedAt: new Date().toISOString(),
        source: quote.source,
        note: current.note || `${quote.source} ${quote.updatedAt}${quote.fallbackReason ? `；备用原因：${quote.fallbackReason}` : ""}`
      };
      success += 1;
    } catch (error) {
      valuations[position.key] = {
        ...current,
        updatedAt: new Date().toISOString(),
        source: current.source || "手工估值",
        note: current.note || `同步失败：${error.message || "行情不可用"}`
      };
      failed += 1;
    }
  }
  if (!savePositionValuations(valuations)) return;
  render();
  if (failed > 0 && (fileMode() || positions.some((position) => isUsTicker(position.symbol)))) showLocalServiceGuide("quotes");
  showToast(failed > 0 ? `已同步 ${success} 个，失败 ${failed} 个` : `已同步 ${success} 个价格`, failed > 0 ? "error" : "success");
}

function applyPositionQuotes(payload) {
  const quotes = payload && payload.quotes ? payload.quotes : {};
  const positions = summarizeLedger().positions;
  const valuations = loadPositionValuations();
  let applied = 0;
  positions.forEach((position) => {
    const quote = quotes[position.symbol] || quotes[String(position.symbol || "").toUpperCase()];
    if (!quote || !Number.isFinite(Number(quote.price))) return;
    const current = { ...(valuations[position.key] || {}) };
    valuations[position.key] = {
      ...current,
      currentPrice: Number(quote.price),
      updatedAt: payload.generated_at || new Date().toISOString(),
      source: quote.source || (payload.source && payload.source.name) || "本地价格JSON",
      note: current.note || `${quote.source || "本地价格JSON"} ${quote.trade_time || payload.generated_at || ""}`.trim()
    };
    applied += 1;
  });
  if (!savePositionValuations(valuations)) return -1;
  render();
  return applied;
}

async function loadPositionQuotesJson() {
  try {
    const response = await fetch(`./data/position-quotes.json?ts=${Date.now()}`);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const applied = applyPositionQuotes(await response.json());
    if (applied < 0) return;
    showToast(applied ? `已应用 ${applied} 条价格` : "价格 JSON 已读取，但没有匹配当前持仓代码", applied ? "success" : "info");
  } catch {
    showLocalServiceGuide("quotes");
    showToast("读取价格 JSON 失败：请先运行 tools/dashboard/update_position_quotes.py", "error");
  }
}

function csvCell(value) {
  const text = String(value ?? "");
  if (/[",\n\r]/.test(text)) return `"${text.replace(/"/g, '""')}"`;
  return text;
}

function exportEntries(module, format) {
  const config = moduleConfigs[module];
  if (!config) return;
  const entries = filterEntries(moduleEntries(module), getLedgerFilter(module));
  const fileBase = `${module}-${today()}-${entries.length}`;
  if (format === "json") {
    downloadText(`${fileBase}.json`, JSON.stringify({ module, title: config.title, exported_at: new Date().toISOString(), entries }, null, 2), "application/json");
    return;
  }
  const headers = ["日期", "模块", "分类", "动作", "代码", "名称", "数量", "价格", "金额元", "保证金元", "保证金率", "费用元", "备注"];
  const rows = entries.map((entry) => [
    entry.date || "",
    config.title,
    entry.bucket || "",
    entryActionLabel(module, entry.action) || "",
    entry.symbol || "",
    entry.name || "",
    entry.quantity || "",
    entry.price || "",
    yuanFromWan(entry.amount || 0),
    yuanFromWan(entry.margin || 0),
    entry.margin && entry.amount ? pct(ratio(safeAmount(entry.margin), safeAmount(entry.amount))) : "",
    yuanFromWan(entry.fee || 0),
    entry.note || ""
  ]);
  const csv = [headers, ...rows].map((rowData) => rowData.map(csvCell).join(",")).join("\n");
  downloadText(`${fileBase}.csv`, csv, "text/csv;charset=utf-8");
}

function fullBackupPayload() {
  return {
    schemaVersion: 1,
    exportedAt: new Date().toISOString(),
    source: "hybrid-barbell-dashboard",
    ledger: normalizeLedgerForStorage(loadLedger()),
    positionValuations: loadPositionValuations(),
    history: loadHistory(),
    valuation: loadValuation(),
    historyView: historyViewPeriod()
  };
}

function exportAllData() {
  const payload = fullBackupPayload();
  downloadText(`hybrid-barbell-dashboard-full-${today()}.json`, JSON.stringify(payload, null, 2), "application/json");
  showToast(`已导出全部数据：${payload.ledger.entries.length} 笔流水`, "success");
}

function validateFullBackupPayload(payload) {
  if (!payload || typeof payload !== "object") throw new Error("备份文件不是 JSON 对象");
  if (!payload.ledger || typeof payload.ledger !== "object") throw new Error("缺少 ledger");
  if (!Array.isArray(payload.ledger.entries)) throw new Error("ledger.entries 必须是数组");
  if (!payload.ledger.settings || typeof payload.ledger.settings !== "object") throw new Error("ledger.settings 必须是对象");
  if (payload.positionValuations !== undefined && (payload.positionValuations === null || typeof payload.positionValuations !== "object" || Array.isArray(payload.positionValuations))) {
    throw new Error("positionValuations 必须是对象");
  }
  if (payload.history !== undefined && !Array.isArray(payload.history)) throw new Error("history 必须是数组");
  return {
    ledger: normalizeLedgerForStorage(payload.ledger),
    positionValuations: payload.positionValuations || {},
    history: payload.history || [],
    valuation: payload.valuation || null,
    historyView: payload.historyView === "week" || payload.historyView === "month" ? payload.historyView : "day"
  };
}

function restoreFullBackup(payload) {
  const data = validateFullBackupPayload(payload);
  if (!confirmDanger(`确认导入完整备份？当前浏览器账本、持仓估值、历史快照和估值 JSON 会被覆盖。备份内含 ${data.ledger.entries.length} 笔流水。`)) return false;
  return applyFullBackupData(data, { toast: true });
}

function applyFullBackupData(data, options = {}) {
  const writeOptions = { skipRemoteSync: Boolean(options.skipRemoteSync) };
  if (!saveLedger(data.ledger, writeOptions)) return false;
  if (!savePositionValuations(data.positionValuations, writeOptions)) return false;
  if (!saveHistory(data.history, writeOptions)) return false;
  if (data.valuation && !saveValuation(data.valuation, writeOptions)) return false;
  if (!data.valuation) localStorage.removeItem(valuationKey);
  if (!setHistoryViewPeriod(data.historyView, writeOptions)) return false;
  editingId = null;
  render();
  renderValuation(data.valuation);
  syncDailyHistory({ render: true });
  if (options.toast !== false) showToast(`已导入完整备份：${data.ledger.entries.length} 笔流水`, "success");
  return true;
}

function loadSyncAccessKey() {
  try {
    return localStorage.getItem(syncAccessKeyKey) || "";
  } catch {
    return "";
  }
}

function saveSyncAccessKey(value) {
  return writeLocalStorage(syncAccessKeyKey, value, "云同步访问密钥");
}

function promptSyncAccessKey() {
  const current = loadSyncAccessKey();
  const value = window.prompt("请输入 APP_ACCESS_KEY。它只保存在本机浏览器，不会写入 Gitee。", current);
  if (value === null) return "";
  const next = value.trim();
  if (!next) {
    localStorage.removeItem(syncAccessKeyKey);
    showToast("云同步访问密钥为空，已取消", "info");
    return "";
  }
  saveSyncAccessKey(next);
  return next;
}

function syncAuthHeaders(key) {
  return {
    "Content-Type": "application/json",
    "X-App-Key": key
  };
}

function comparableBackupPayload(payload) {
  return JSON.stringify(validateFullBackupPayload(payload));
}

function currentBackupComparable() {
  return comparableBackupPayload(fullBackupPayload());
}

function persistRemoteSyncMarker(payload, sha) {
  const comparable = comparableBackupPayload(payload);
  if (sha) writeLocalStorage(syncLastShaKey, sha, "云同步版本");
  writeLocalStorage(syncLastComparableKey, comparable, "云同步基线");
}

function lastRemoteSyncSha() {
  try {
    return localStorage.getItem(syncLastShaKey) || "";
  } catch {
    return "";
  }
}

function lastRemoteSyncComparable() {
  try {
    return localStorage.getItem(syncLastComparableKey) || "";
  } catch {
    return "";
  }
}

function backupHasData(payload) {
  const data = validateFullBackupPayload(payload);
  return data.ledger.entries.length > 0
    || Object.keys(data.positionValuations).length > 0
    || data.history.length > 0
    || Boolean(data.valuation);
}

function remoteSyncPanel() {
  const statusText = {
    idle: "未检查",
    needs_key: "需要访问密钥",
    loading: "检查中",
    clean: "已同步",
    conflict: "需要选择",
    error: "异常"
  }[remoteSyncState.status] || remoteSyncState.status;
  const showChoice = remoteSyncState.status === "conflict";
  return `
    <div id="remoteSyncPanel" class="remote-sync-panel ${remoteSyncState.status === "clean" ? "is-quiet" : ""}">
      <div>
        <strong>云同步</strong>
        <span>${statusText}</span>
      </div>
      <p>${remoteSyncState.message}</p>
      <div class="inline-actions">
        ${showChoice ? `
          <button type="button" data-action="remote-sync-pull">拉取远端覆盖本地</button>
          <button type="button" data-action="remote-sync-push">推送本地覆盖远端</button>
          <button type="button" data-action="remote-sync-skip">暂不处理</button>
        ` : `
          <button type="button" data-action="remote-sync-refresh">检查</button>
          <button type="button" data-action="remote-sync-pull">拉取远端</button>
          <button type="button" data-action="remote-sync-push">推送本地</button>
        `}
        <button type="button" data-action="remote-sync-key">访问密钥</button>
        <button type="button" data-action="remote-sync-close">关闭</button>
      </div>
    </div>
  `;
}

function renderRemoteSyncPanel() {
  let host = document.querySelector("#remoteSyncHost");
  if (!host) {
    host = document.createElement("div");
    host.id = "remoteSyncHost";
    document.body.appendChild(host);
  }
  if (remoteSyncState.status !== "conflict") {
    host.innerHTML = "";
    return;
  }
  host.innerHTML = remoteSyncPanel();
}

async function fetchRemoteDashboardState(options = {}) {
  if (!ledgerServiceAvailable()) throw new Error("当前不是本地/公网服务入口，无法云同步");
  const key = options.prompt ? promptSyncAccessKey() : loadSyncAccessKey();
  if (!key) throw new Error("缺少 APP_ACCESS_KEY");
  const response = await fetchWithTimeout(`${remoteSyncStatePath}?ts=${Date.now()}`, {
    headers: syncAuthHeaders(key),
    cache: "no-store"
  }, 8000);
  if (response.status === 401) {
    localStorage.removeItem(syncAccessKeyKey);
    throw new Error("APP_ACCESS_KEY 无效，请重新输入");
  }
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  const payload = await response.json();
  if (!payload.ok) throw new Error(payload.error || "云同步读取失败");
  return payload;
}

async function checkRemoteDashboardState(options = {}) {
  if (!loadSyncAccessKey() && !options.prompt) {
    remoteSyncState = {
      status: "needs_key",
      message: "点击“访问密钥”输入 APP_ACCESS_KEY 后即可检查 Gitee 远端数据。",
      remotePayload: null,
      sha: null,
      checkedAt: null
    };
    if (!options.silent) renderRemoteSyncPanel();
    return;
  }
  remoteSyncState = { ...remoteSyncState, status: "loading", message: "正在检查 Gitee 远端数据..." };
  if (!options.silent) renderRemoteSyncPanel();
  try {
    const remote = await fetchRemoteDashboardState({ prompt: options.prompt });
    const remotePayload = remote.data;
    const same = comparableBackupPayload(remotePayload) === currentBackupComparable();
    const localHasData = backupHasData(fullBackupPayload());
    const remoteHasData = backupHasData(remotePayload);
    if (same) persistRemoteSyncMarker(remotePayload, remote.sha);
    remoteSyncState = {
      status: same ? "clean" : (localHasData && remoteHasData ? "conflict" : "clean"),
      message: same
        ? "本地数据与 Gitee 远端一致。"
        : (localHasData && remoteHasData
            ? "本地与 Gitee 远端都存在数据且不一致，请选择拉取、推送或暂不处理。"
            : "检测到单侧数据，可按需拉取或推送。"),
      remotePayload,
      sha: remote.sha,
      checkedAt: new Date().toISOString()
    };
    if (!options.silent || !same) renderRemoteSyncPanel();
    if (!options.silent) showToast("云同步检查完成", "success");
  } catch (error) {
    remoteSyncState = {
      status: "error",
      message: `云同步检查失败：${error.message || "未知错误"}`,
      remotePayload: null,
      sha: null,
      checkedAt: new Date().toISOString()
    };
    if (!options.silent) renderRemoteSyncPanel();
    if (!options.silent) showToast("云同步检查失败", "error");
  }
}

async function pullRemoteDashboardState() {
  let remotePayload = remoteSyncState.remotePayload;
  let remoteSha = remoteSyncState.sha;
  if (!remotePayload) {
    const remote = await fetchRemoteDashboardState({ prompt: !loadSyncAccessKey() });
    remotePayload = remote.data;
    remoteSha = remote.sha;
  }
  const data = validateFullBackupPayload(remotePayload);
  if (!confirmDanger(`确认用 Gitee 远端数据覆盖本地？远端含 ${data.ledger.entries.length} 笔流水。`)) return;
  if (!applyFullBackupData(data, { toast: false, skipRemoteSync: true })) return;
  persistRemoteSyncMarker(remotePayload, remoteSha);
  remoteSyncState = {
    status: "clean",
    message: "已拉取 Gitee 远端数据并覆盖本地。",
    remotePayload,
    sha: remoteSha,
    checkedAt: new Date().toISOString()
  };
  renderRemoteSyncPanel();
  showToast(`已拉取远端数据：${data.ledger.entries.length} 笔流水`, "success");
}

async function postRemoteDashboardState(payload, key) {
  const response = await fetchWithTimeout(remoteSyncStatePath, {
    method: "POST",
    headers: syncAuthHeaders(key),
    body: JSON.stringify({ data: payload })
  }, 10000);
  if (response.status === 401) {
    localStorage.removeItem(syncAccessKeyKey);
    throw new Error("APP_ACCESS_KEY 无效，请重新输入");
  }
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  const result = await response.json();
  if (!result.ok) throw new Error(result.error || "推送失败");
  return result;
}

function scheduleAutoRemoteSync(reason = "local-change") {
  if (!ledgerServiceAvailable()) return;
  if (!loadSyncAccessKey()) return;
  if (remoteSyncState.status === "conflict" || remoteSyncState.status === "loading") return;
  if (remoteSyncTimer) window.clearTimeout(remoteSyncTimer);
  remoteSyncTimer = window.setTimeout(() => {
    remoteSyncTimer = null;
    autoPushRemoteDashboardState(reason);
  }, 1500);
}

async function autoPushRemoteDashboardState(reason = "local-change") {
  if (remoteSyncInFlight) {
    scheduleAutoRemoteSync(reason);
    return;
  }
  const key = loadSyncAccessKey();
  if (!key) return;
  remoteSyncInFlight = true;
  try {
    const payload = fullBackupPayload();
    const comparable = comparableBackupPayload(payload);
    const remote = await fetchRemoteDashboardState({ prompt: false });
    const remoteComparable = comparableBackupPayload(remote.data);
    if (remoteComparable === comparable) {
      persistRemoteSyncMarker(remote.data, remote.sha);
      remoteSyncState = {
        status: "clean",
        message: "本地数据与 Gitee 远端一致。",
        remotePayload: remote.data,
        sha: remote.sha,
        checkedAt: new Date().toISOString()
      };
      renderRemoteSyncPanel();
      return;
    }
    const knownRemote = (lastRemoteSyncSha() && remote.sha === lastRemoteSyncSha())
      || (lastRemoteSyncComparable() && remoteComparable === lastRemoteSyncComparable());
    if (!knownRemote) {
      remoteSyncState = {
        status: "conflict",
        message: "自动同步暂停：Gitee 远端也有变化。请选择拉取、推送或暂不处理。",
        remotePayload: remote.data,
        sha: remote.sha,
        checkedAt: new Date().toISOString()
      };
      renderRemoteSyncPanel();
      showToast("云同步遇到冲突，已暂停自动推送", "error");
      return;
    }
    const result = await postRemoteDashboardState(payload, key);
    persistRemoteSyncMarker(payload, result.sha);
    remoteSyncState = {
      status: "clean",
      message: "本地改动已自动同步到 Gitee。",
      remotePayload: payload,
      sha: result.sha,
      checkedAt: new Date().toISOString()
    };
    renderRemoteSyncPanel();
    showToast("已自动同步到 Gitee", "success");
  } catch (error) {
    remoteSyncState = {
      status: "error",
      message: `自动同步失败：${error.message || "未知错误"}`,
      remotePayload: null,
      sha: null,
      checkedAt: new Date().toISOString()
    };
    renderRemoteSyncPanel();
    showToast("自动云同步失败", "error");
  } finally {
    remoteSyncInFlight = false;
  }
}

async function pushRemoteDashboardState() {
  const payload = fullBackupPayload();
  const data = validateFullBackupPayload(payload);
  if (!confirmDanger(`确认用本地数据覆盖 Gitee 远端？本地含 ${data.ledger.entries.length} 笔流水。`)) return;
  const key = loadSyncAccessKey() || promptSyncAccessKey();
  if (!key) return;
  remoteSyncState = { ...remoteSyncState, status: "loading", message: "正在推送本地数据到 Gitee..." };
  renderRemoteSyncPanel();
  try {
    const result = await postRemoteDashboardState(payload, key);
    persistRemoteSyncMarker(payload, result.sha);
    remoteSyncState = {
      status: "clean",
      message: "已将本地完整状态推送到 Gitee。",
      remotePayload: payload,
      sha: result.sha,
      checkedAt: new Date().toISOString()
    };
    renderRemoteSyncPanel();
    showToast(`已推送到 Gitee：${data.ledger.entries.length} 笔流水`, "success");
  } catch (error) {
    remoteSyncState = {
      status: "error",
      message: `推送失败：${error.message || "未知错误"}`,
      remotePayload: null,
      sha: null,
      checkedAt: new Date().toISOString()
    };
    renderRemoteSyncPanel();
    showToast("云同步推送失败", "error");
  }
}

async function importAllDataFile(file) {
  try {
    restoreFullBackup(JSON.parse(await file.text()));
  } catch (error) {
    showToast(`导入失败：${error.message || "备份文件无效"}`, "error");
  }
}

function downloadText(filename, content, type) {
  const blob = new Blob([content], { type });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function createSampleLedger() {
  const entries = [
    { module: "cash", bucket: "现金池", action: "deposit", symbol: "", name: "全局现金池", amount: 150, note: "示例：初始资金转入 150 万" },
    { module: "cash", bucket: "现金池", action: "withdraw", symbol: "", name: "全局现金池", amount: 2, note: "示例：生活支出取出" },
    { module: "dividend", bucket: "类现金", action: "buy", symbol: "GC001", name: "国债逆回购", amount: 8, note: "流动缓冲" },
    { module: "dividend", bucket: "高分红股票", action: "buy", symbol: "000568", name: "泸州老窖", amount: 18, fee: 0.0025, note: "示例高分红股票仓" },
    { module: "dividend", bucket: "高分红股票", action: "buy", symbol: "000858", name: "五粮液", amount: 14, fee: 0.0022, note: "示例高分红股票仓" },
    { module: "dividend", bucket: "高分红股票", action: "buy", symbol: "600519", name: "贵州茅台", amount: 10, fee: 0.0018, note: "示例高质量白酒观察仓" },
    { module: "dividend", bucket: "债券", action: "buy", symbol: "511010", name: "国债ETF", amount: 6, fee: 0.001, note: "波动缓冲" },
    { module: "dividend", bucket: "高分红股票", action: "dividend", symbol: "000568", name: "泸州老窖", amount: 0.45, note: "模拟 A 股分红，冲减成本并进入现金池" },
    { module: "dividend", bucket: "高分红股票", action: "dividend", symbol: "000858", name: "五粮液", amount: 0.35, note: "模拟 A 股分红，冲减成本并进入现金池" },
    { module: "dividend", bucket: "类现金", action: "interest", symbol: "GC001", name: "国债逆回购", amount: 0.05, note: "模拟逆回购利息" },
    { module: "qqq", bucket: "QQQ", action: "buy", symbol: "QQQ", name: "纳指100ETF", amount: 6, fee: 0.003, note: "右尾底仓" },
    { module: "qqq", bucket: "QLD", action: "buy", symbol: "QLD", name: "二倍纳指ETF", amount: 2, fee: 0.002, note: "站上120日线后买入" },
    { module: "qqq", bucket: "QQQ", action: "dividend", symbol: "QQQ", name: "纳指100ETF", amount: 0.06, note: "模拟 QQQ 分红" },
    { module: "put", bucket: "SPY put", action: "buy", symbol: "SPY 2027P300", name: "深度虚值保险", amount: 1.2, fee: 0.01, note: "Delta约-0.08，年度保险预算" },
    { module: "put", bucket: "保险预算", action: "expire", symbol: "SPY 2026P300", name: "到期归零示例", amount: 0.3, note: "模拟保险成本归零" },
    { module: "ic", bucket: "IC/IM资金池", action: "futures_deposit", symbol: "期货资金池", name: "专项资金", amount: 55, note: "示例：第一手 IC 账户资金" },
    { module: "ic", bucket: "IC", action: "buy", symbol: "IC2607", name: "中证500股指期货", amount: 172, margin: 20.64, quantity: "1", price: "8600", multiplier: 200, note: "示例：1手 IC，保证金率约12%" },
    { module: "ic", bucket: "移仓", action: "roll", symbol: "IC2607", name: "中证500股指期货", amount: 0, fee: 0.005, note: "记录移仓动作" }
  ];
  return {
    settings: { ...defaultSettings, annualExpense: 12, newMoney: 2, icPb: 35, imPb: 22 },
    entries: entries.map((item, index) => {
      const quantityMap = {
        "000568": "1500",
        "000858": "1400",
        "600519": "6",
        "511010": "6000",
        QQQ: "18",
        QLD: "10"
      };
      const priceMap = {
        "000568": "120",
        "000858": "100",
        "600519": "1666.67",
        "511010": "10",
        QQQ: "3333.3333",
        QLD: "2000"
      };
      return {
        id: makeId(),
        module: item.module,
        bucket: item.bucket,
        action: item.action,
        symbol: item.symbol,
        name: item.name,
        amount: item.amount,
        margin: item.margin || 0,
        quantity: item.quantity || (item.action === "buy" ? (quantityMap[item.symbol] || "") : ""),
        price: item.price || (item.action === "buy" ? (priceMap[item.symbol] || "") : ""),
        multiplier: item.multiplier || "",
        fee: item.fee || 0,
        note: item.note,
        date: new Date(Date.now() - index * 86400000).toISOString().slice(0, 10)
      };
    })
  };
}

function createSamplePositionValuations() {
  const now = new Date().toISOString();
  return {
    "dividend::000568": { currentPrice: 148, marketValue: 21.4, updatedAt: now, source: "手工估值", note: "示例：按当前市值手工覆盖" },
    "dividend::000858": { currentPrice: 126, marketValue: 12.9, updatedAt: now, source: "手工估值", note: "示例：价格回落后的市值" },
    "qqq::QQQ": { marketValue: 7.1, updatedAt: now, source: "手工估值", note: "示例：美股市值用万元手工录入" },
    "qqq::QLD": { marketValue: 2.35, updatedAt: now, source: "手工估值", note: "示例：趋势仓市值" },
    "put::SPY 2027P300": { marketValue: 0.82, updatedAt: now, source: "手工估值", note: "示例：期权按当前权利金估值" }
  };
}

document.addEventListener("click", (event) => {
  if (handleTermToggle(event)) return;

  const scrollButton = event.target.closest("[data-scroll-target]");
  if (scrollButton) {
    const target = document.querySelector(scrollButton.dataset.scrollTarget);
    if (target) target.scrollIntoView({ behavior: "auto", block: "start" });
    return;
  }

  const navButton = event.target.closest("[data-tab]");
  if (navButton) {
    setActiveTab(navButton.dataset.tab, navButton.dataset.subpage || "full");
    return;
  }

  const action = event.target.closest("[data-action]");
  if (action) {
    if (action.dataset.action === "choose-instrument") {
      chooseInstrument(action);
      return;
    }
    if (action.dataset.action === "copy-service-command") {
      copyText(dashboardServiceCommand, "已复制启动命令");
      return;
    }
    if (action.dataset.action === "copy-service-url") {
      copyText(dashboardServiceUrl(), "已复制本地入口 URL");
      return;
    }
    if (action.dataset.action === "copy-price-command") {
      copyText(positionQuotesCommand, "已复制价格更新命令");
      return;
    }
    if (action.dataset.action === "open-service-url") {
      window.open(dashboardServiceUrl(), "_blank", "noopener");
      showToast("已打开本地入口；若无法访问，请先运行启动命令", "info");
      return;
    }
    if (action.dataset.action === "dismiss-service-guide") {
      localServiceGuideVisible = false;
      localServiceGuideReason = "";
      localServiceGuideDismissed = true;
      renderLocalServiceGuide();
      showToast("已收起本地服务引导", "info");
      return;
    }
    if (action.dataset.action === "refresh-ledger-backups") {
      refreshLedgerBackups();
      return;
    }
    if (action.dataset.action === "manual-ledger-backup") {
      manualLedgerBackup();
      return;
    }
    if (action.dataset.action === "restore-ledger-backup") {
      restoreLedgerBackup(action.dataset.backupId);
      return;
    }
    if (action.dataset.action === "adopt-pb") {
      const value = Number(action.dataset.pbValue);
      if (!Number.isFinite(value)) return;
      const isIc = action.dataset.pbType === "ic";
      saveSettings(isIc ? { icPb: value, icPbSource: "auto" } : { imPb: value, imPbSource: "auto" });
      render();
      showToast(`已采用${isIc ? " IC" : " IM"} 自动 PB 分位`, "success");
      return;
    }
    if (action.dataset.action === "remote-sync-key") {
      if (promptSyncAccessKey()) checkRemoteDashboardState({ prompt: false });
      return;
    }
    if (action.dataset.action === "remote-sync-refresh") {
      checkRemoteDashboardState({ prompt: !loadSyncAccessKey() });
      return;
    }
    if (action.dataset.action === "remote-sync-pull") {
      pullRemoteDashboardState().catch((error) => {
        showToast(`拉取失败：${error.message || "未知错误"}`, "error");
      });
      return;
    }
    if (action.dataset.action === "remote-sync-push") {
      pushRemoteDashboardState();
      return;
    }
    if (action.dataset.action === "remote-sync-skip") {
      remoteSyncState = { ...remoteSyncState, status: "clean", message: "已暂不处理本次云同步冲突。" };
      renderRemoteSyncPanel();
      return;
    }
    if (action.dataset.action === "remote-sync-close") {
      remoteSyncState = { ...remoteSyncState, status: "idle" };
      renderRemoteSyncPanel();
      return;
    }
    if (action.dataset.action === "ledger-page-prev" || action.dataset.action === "ledger-page-next") {
      const module = action.dataset.module || currentTab;
      const filter = getLedgerFilter(module);
      const direction = action.dataset.action === "ledger-page-next" ? 1 : -1;
      filter.page = Math.max(1, (Number(filter.page) || 1) + direction);
      updateLedgerTableArea(module);
      return;
    }
    const ledger = loadLedger();
    if (action.dataset.action === "edit-entry") {
      editingId = action.dataset.id;
      if (isDesktopMode() && currentTab !== "overview" && currentTab !== "reports") currentSubpage = "entry";
      render();
      focusEntryFormForEdit();
      showToast("已切到编辑表单", "info");
    } else if (action.dataset.action === "delete-entry") {
      const target = ledger.entries.find((entry) => entry.id === action.dataset.id);
      const label = target ? `${target.date || "-"} ${target.symbol || target.name || target.bucket || "这笔记录"}` : "这笔记录";
      if (!confirmDanger(`确认删除 ${label}？删除后无法撤销。`)) return;
      ledger.entries = ledger.entries.filter((entry) => entry.id !== action.dataset.id);
      if (!saveLedger(ledger)) return;
      render();
      showToast("已删除 1 笔记录", "success");
    } else if (action.dataset.action === "cancel-edit") {
      editingId = null;
      render();
    } else if (action.dataset.action === "clear-filters") {
      ledgerFilters[action.dataset.module] = { search: "", action: "", bucket: "", page: 1 };
      render();
      showToast("已清除筛选", "info");
    } else if (action.dataset.action === "export-csv") {
      exportEntries(action.dataset.module, "csv");
      showToast("已导出 CSV", "success");
    } else if (action.dataset.action === "export-json") {
      exportEntries(action.dataset.module, "json");
      showToast("已导出 JSON", "success");
    } else if (action.dataset.action === "sync-position-price") {
      syncPositionPrice(action.dataset.positionKey);
    } else if (action.dataset.action === "sync-module-prices") {
      syncModulePrices(action.dataset.module);
    } else if (action.dataset.action === "load-sample-data") {
      const hasData = ledger.entries.length > 0 || loadHistory().length > 0 || Object.keys(loadPositionValuations()).length > 0;
      if (hasData && !confirmDanger(`示例数据会覆盖当前 ${ledger.entries.length} 笔流水，并清空历史快照。确认继续？`)) return;
      if (!saveLedger(createSampleLedger())) return;
      if (!savePositionValuations(createSamplePositionValuations())) return;
      if (!saveHistory([])) return;
      render();
      syncDailyHistory();
      showToast("已载入示例数据", "success");
    }
    return;
  }

  const historyPeriodButton = event.target.closest("[data-history-period]");
  if (historyPeriodButton) {
    setHistoryViewPeriod(historyPeriodButton.dataset.historyPeriod);
    render();
    return;
  }

  if (event.target.closest("#saveSnapshot")) {
    syncDailyHistory({ forceToday: true });
    showToast("已开始重算今日快照", "info");
    return;
  }

  if (event.target.closest("#syncCloudData")) {
    checkRemoteDashboardState({ prompt: !loadSyncAccessKey() });
    return;
  }

  if (event.target.closest("#exportAllData")) {
    exportAllData();
    return;
  }

  if (event.target.closest("#loadSample")) {
    const ledger = loadLedger();
    const hasData = ledger.entries.length > 0 || loadHistory().length > 0 || Object.keys(loadPositionValuations()).length > 0;
    if (hasData && !confirmDanger(`示例数据会覆盖当前 ${ledger.entries.length} 笔流水，并清空历史快照。确认继续？`)) return;
    if (!saveLedger(createSampleLedger())) return;
    if (!savePositionValuations(createSamplePositionValuations())) return;
    if (!saveHistory([])) return;
    render();
    syncDailyHistory();
    showToast("已载入示例数据", "success");
    return;
  }

  if (event.target.closest("#resetData")) {
    if (!confirmDanger("确认清空账本、历史快照、估值和视图设置？此操作无法撤销。")) return;
    localStorage.removeItem(ledgerKey);
    localStorage.removeItem(historyKey);
    localStorage.removeItem(valuationKey);
    localStorage.removeItem(positionValuationKey);
    localStorage.removeItem(historyViewKey);
    editingId = null;
    render();
    showToast("已清空本地数据", "success");
    return;
  }

  if (event.target.closest("#loadValuationJson") || event.target.closest("[data-action='load-valuation-json']")) {
    loadValuationJson();
  }

  if (event.target.closest("#loadPositionQuotesJson")) {
    loadPositionQuotesJson();
  }

  if (!event.target.closest(".instrument-combobox")) closeInstrumentSuggestions();
});

document.addEventListener("keydown", (event) => {
  if (event.key === "Escape") {
    closeTermTips();
    closeInstrumentSuggestions();
  }
});

document.addEventListener("focusin", (event) => {
  if (event.target.matches("[data-instrument-field]")) openInstrumentSuggestions(event.target);
});

document.addEventListener("submit", handleEntrySubmit);
document.addEventListener("input", (event) => {
  if (event.target.closest("#entryForm")) clearEntryValidation(event.target.closest("#entryForm"));
  if (event.target.matches("[data-instrument-field]")) openInstrumentSuggestions(event.target);
  handleEntryAmountAutoCalc(event);
  handleInstrumentMemoryInput(event);
  handleLedgerFilterInput(event);
});

document.addEventListener("change", async (event) => {
  if (event.target.closest("#entryForm")) clearEntryValidation(event.target.closest("#entryForm"));
  if (handlePositionValuationChange(event)) return;
  handleSettingsInput(event);
  handleInstrumentMemoryInput(event);
  handleLedgerFilterInput(event);
  if (event.target.id === "importAllDataFile") {
    const file = event.target.files && event.target.files[0];
    if (file) await importAllDataFile(file);
    event.target.value = "";
    return;
  }
  if (event.target.id !== "valuationFile") return;
  const file = event.target.files && event.target.files[0];
  if (!file) return;
  const status = document.querySelector("#valuationStatus");
  if (status) status.textContent = "导入中...";
  try {
    if (!applyValuation(JSON.parse(await file.text()))) {
      if (status) status.textContent = "导入失败：本地写入失败";
      return;
    }
    showToast("估值 JSON 已导入", "success");
  } catch {
    if (status) status.textContent = "导入失败";
    showToast("估值 JSON 导入失败", "error");
  } finally {
    event.target.value = "";
  }
});

async function loadValuationJson(options = {}) {
  const status = document.querySelector("#valuationStatus");
  if (status && !options.silent) status.textContent = "读取中...";
  if (status && options.silent && options.preferApi !== false) status.textContent = "自动刷新中...";
  const sources = options.preferApi === false
    ? [{ url: `./data/ic-im-valuation.json?ts=${Date.now()}`, source: "json" }]
    : [
        { url: `/api/ic-im-valuation?ts=${Date.now()}`, source: "api" },
        { url: `./data/ic-im-valuation.json?ts=${Date.now()}`, source: "json" }
      ];
  let lastError = null;
  for (const item of sources) {
    try {
      const response = await fetch(item.url);
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      if (!applyValuation(await response.json(), { render: options.render !== false, source: item.source, silent: options.silent })) return;
      if (!options.silent) showToast(item.source === "api" ? "估值已自动刷新" : "估值 JSON 已读取", "success");
      return;
    } catch (error) {
      lastError = error;
    }
  }
  try {
    throw lastError || new Error("valuation unavailable");
  } catch {
    showLocalServiceGuide("valuation");
    if (status && !options.silent) status.textContent = `读取失败：运行 ${dashboardServiceCommand} 后访问 ${dashboardServiceUrl()}，或改用导入 JSON`;
    if (status && options.silent) status.textContent = "自动刷新失败";
    if (!options.silent) showToast("估值读取失败：请按页面引导启动本地服务", "error");
  }
}

window.addEventListener("resize", () => {
  if (currentTab === "overview") render();
});

async function bootstrap() {
  render();
  await offerLedgerBackupRestore();
  refreshLedgerBackups({ silent: true });
  loadValuationJson({ silent: true, render: true });
  syncDailyHistory({ render: true });
  checkRemoteDashboardState({ silent: true });
}

bootstrap();

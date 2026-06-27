const legacyStorageKey = "hybrid-barbell-dashboard-v1";
const historyKey = "hybrid-barbell-dashboard-history-v1";
const valuationKey = "hybrid-barbell-dashboard-valuation-v1";
const ledgerKey = "hybrid-barbell-dashboard-ledger-v1";
const positionValuationKey = "hybrid-barbell-dashboard-position-valuation-v1";
const historyViewKey = "hybrid-barbell-dashboard-history-view-v1";
const ledgerBackupPromptKey = "hybrid-barbell-dashboard-ledger-backup-prompted-v1";
const ledgerApiPath = "/api/ledger";

const chartColors = ["#0f6a7a", "#c1742f", "#2f6b45", "#8b4f7d", "#b9493c", "#59616d", "#d2a23a"];

const moduleConfigs = {
  overview: {
    title: "总览"
  },
  reports: {
    title: "报表"
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

const actionLabels = {
  buy: "买入",
  sell: "卖出",
  dividend: "分红",
  interest: "利息",
  deposit: "转入",
  withdraw: "转出",
  internal_in: "内部划入",
  internal_out: "内部划出",
  fee: "费用",
  margin: "保证金",
  roll: "移仓"
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

const appRoot = document.querySelector("#appRoot");
const pageTitle = document.querySelector("#pageTitle");

const dashboardServiceCommand = "python3 scripts/serve_dashboard.py";
const positionQuotesCommand = "python3 scripts/update_position_quotes.py 000568 000858 QQQ QLD SPY";
const dashboardServiceBaseUrl = "http://127.0.0.1:8775/hybrid-barbell-dashboard/";

const subpageLabels = {
  overview: {
    full: "全部总览",
    snapshot: "净值快照",
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

function dashboardServiceUrl() {
  return `${dashboardServiceBaseUrl}${isDesktopMode() ? "index-desktop.html" : "index.html"}`;
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
      hasIc: Boolean(legacy.hasIc),
      futuresEquity: safeAmount(legacy.futuresEquity),
      usedMargin: safeAmount(legacy.usedMargin),
      futuresNotional: safeAmount(legacy.futuresNotional),
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
        settings: { ...defaultSettings, ...(parsed.settings || {}) }
      };
    }
  } catch {
    // Fall through to a clean ledger.
  }
  return {
    entries: [],
    settings: { ...defaultSettings, ...loadLegacySettings() }
  };
}

function ledgerServiceAvailable() {
  return window.location.protocol === "http:" || window.location.protocol === "https:";
}

function normalizeLedgerForStorage(ledger) {
  return {
    entries: Array.isArray(ledger.entries) ? ledger.entries.map(normalizeEntry) : [],
    settings: { ...defaultSettings, ...(ledger.settings || {}) }
  };
}

function saveLedger(ledger, options = {}) {
  const next = normalizeLedgerForStorage(ledger);
  localStorage.setItem(ledgerKey, JSON.stringify(next));
  if (!options.skipMirror) scheduleLedgerMirror(next);
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
      ledgerBackupState = {
        ...ledgerBackupState,
        status: "ready",
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
  saveLedger(ledger);
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
  if (!panel) return;
  panel.outerHTML = ledgerBackupPanel(loadLedger());
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
      message: "读取 SQLite 备份失败；请确认使用 python3 scripts/serve_dashboard.py 启动。"
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
  saveLedger(ledger);
  render();
  await refreshLedgerBackups({ silent: true });
  showToast(`已恢复快照 #${snapshot.id}，共 ${ledger.entries.length} 笔流水`, "success");
}

function saveSettings(partial) {
  const ledger = loadLedger();
  ledger.settings = { ...defaultSettings, ...ledger.settings, ...partial };
  saveLedger(ledger);
}

function loadPositionValuations() {
  try {
    const raw = localStorage.getItem(positionValuationKey);
    return raw ? JSON.parse(raw) : {};
  } catch {
    return {};
  }
}

function savePositionValuations(valuations) {
  localStorage.setItem(positionValuationKey, JSON.stringify(valuations));
}

function positionKey(module, symbol, name) {
  const code = String(symbol || "").trim();
  const title = String(name || "").trim();
  return `${module || "unknown"}::${(code || title || "未命名").toUpperCase()}`;
}

function quantityImpact(entry) {
  const quantity = safeAmount(entry.quantity);
  if (entry.action === "buy" || entry.action === "deposit" || entry.action === "internal_in" || entry.action === "margin") return quantity;
  if (entry.action === "sell" || entry.action === "withdraw" || entry.action === "internal_out") return -quantity;
  return 0;
}

function entryBalanceImpact(entry) {
  const sign = balanceActions[entry.action] ?? 0;
  return sign * safeAmount(entry.amount);
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
    const symbol = String(entry.symbol || "").trim();
    const name = String(entry.name || "").trim();
    const key = positionKey(entry.module, symbol, name);
    const isIncomeOnly = entry.action === "dividend" || entry.action === "interest";
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
      if (entry.action === "buy") existing.netInvestment += safeAmount(entry.fee);
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
  const hardCash = (buckets["现金"] || 0) + (buckets["硬现金"] || 0);
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
  const futuresExposure = (buckets["IC"] || 0) + (buckets["IM"] || 0);
  const derivedTotal = Object.values(buckets).reduce((sum, value) => sum + value, 0);
  const totalAssets = derivedTotal > 0 ? derivedTotal : settings.manualTotalAssets;
  const annualDividend = ledger.entries.reduce((sum, entry) => sum + entryIncomeImpact(entry), 0);
  const investedCost = positions.reduce((sum, position) => sum + position.netInvestment, 0);
  const unrealizedPnl = positions.reduce((sum, position) => sum + position.unrealizedPnl, 0);

  return {
    totalAssets,
    annualExpense: settings.annualExpense,
    hardCash,
    reverseRepo,
    highDividend,
    annualDividend,
    qqq,
    newMoney: settings.newMoney,
    futuresPool,
    futuresEquity: settings.futuresEquity,
    usedMargin: settings.usedMargin,
    futuresNotional: settings.futuresNotional || futuresExposure,
    spyPutBudget,
    whiteLiquor,
    otherAHighDividend,
    icPb: settings.icPb,
    imPb: settings.imPb,
    hasIc: settings.hasIc,
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
  const marginRisk = ratio(data.usedMargin, data.futuresEquity);
  const cashGap6 = gap(monthlyExpense * 6, data.hardCash);
  const liquidGap12 = gap(data.annualExpense, liquidAssets);
  const divGap = gap(data.annualExpense * 1.2, data.annualDividend);
  const qqqGap5 = gap(data.totalAssets * 0.05, data.qqq);
  const qqqGap10 = gap(data.totalAssets * 0.10, data.qqq);
  const futuresGap = gap(100, data.futuresPool);

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
    cashGap6,
    liquidGap12,
    divGap,
    qqqGap5,
    qqqGap10,
    futuresGap
  };
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

function allocationPlan(data, calc, stage) {
  const money = Math.max(data.newMoney, 0);
  let plan;
  if (stage.id === 0) {
    plan = [["硬现金", 100, "先补满 6 个月硬现金"]];
  } else if (stage.id === 1) {
    plan = [
      ["高分红", 80, "先建确定性现金流底座"],
      ["QQQ / QLD", 20, "把右尾仓补到当前总资产 5%"]
    ];
  } else if (stage.id === 2) {
    plan = [
      ["高分红", 70, "提高税后股息覆盖"],
      ["QQQ / QLD", 12, "维持右尾暴露但不抢跑"],
      ["现金 / 逆回购", 18, "把流动安全垫推向 12 个月"]
    ];
  } else if (stage.id === 3) {
    plan = [
      ["高分红", 55, "继续做生活现金流"],
      ["QQQ / QLD", 10, "保持美股成长敞口"],
      ["IC/IM 资金池", 25, "为低估时第一手 IC 做准备"],
      ["现金 / 逆回购", 10, "给保证金和生活预留缓冲"]
    ];
  } else {
    const putWeight = data.totalAssets >= 300 ? 2 : data.totalAssets >= 200 ? 1 : 0;
    plan = [
      ["高分红", 45, "维持核心现金流"],
      ["QQQ / QLD", 10, "不让右尾仓掉出目标区"],
      ["IC/IM 资金池", 25, "低估区才转换成期货敞口"],
      ["现金 / 逆回购", 20 - putWeight, "保留补保证金能力"],
      ["SPY put", putWeight, putWeight > 0 ? "进入年度保险预算" : "总资产不足时暂不预算化"]
    ].filter((row) => row[1] > 0);
  }
  return plan.map(([name, weight, reason]) => ({
    name,
    weight,
    amount: money * weight / 100,
    reason
  }));
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

  if (calc.marginRisk > 70) actions.push("期货风险度已超过 70%，若不补资应优先缩减 IM 或降低敞口。");
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
      value: data.moduleTotals.ic,
      share: ratio(data.moduleTotals.ic, data.totalAssets),
      hint: `风险度 ${pct(calc.marginRisk)}`
    }
  ];
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
              <small>${escapeHtml(item.hint)}</small>
              <span class="account-track"><span style="width:${Math.min(100, Math.max(0, item.share))}%"></span></span>
            </span>
            <span class="account-number">
              <strong>${escapeHtml(yuan(item.value))}</strong>
              <small>${escapeHtml(pct(item.share))}</small>
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
      text: calc.marginRisk > 0 ? `当前风险度 ${pct(calc.marginRisk)}` : "尚未录入期货账户权益和保证金"
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
  const [hero, onboarding, split, settings, target, metrics, strategy, valuation, charts, work, decision] = children;
  const keepMap = {
    snapshot: [hero, metrics, strategy],
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
  const hero = appRoot.children[0];
  const dashboard = appRoot.children[1];
  const workbench = appRoot.children[2];
  if (!workbench) return;
  const side = workbench.querySelector(".module-side");
  const entry = side ? side.children[0] : null;
  const buckets = side ? side.children[1] : null;
  const valuation = side ? side.children[2] : null;
  const ledger = workbench.querySelector(".module-ledger-panel");
  const icDataPanel = config.module === "ic" && dashboard ? dashboard.querySelector(".data-panel") : null;
  const keepMap = {
    overview: [hero, dashboard],
    entry: [entry],
    buckets: [buckets],
    valuation: [valuation, icDataPanel],
    ledger: [ledger]
  };
  keepAppChildren(keepMap[subpage] || [hero, dashboard, workbench]);
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
        <div><span>${termLabel("期货风险度")}</span><strong>${escapeHtml(pct(calc.marginRisk))}</strong><small>保证金 / 权益</small></div>
      </div>
    </section>

    ${mobileSectionNav("overview")}

    <div class="overview-onboarding">${onboardingPanel(ledger, data)}</div>

    <section class="dashboard-split">
      ${assetAccountTree(data, calc)}
      ${riskChecklist(data, calc)}
    </section>

    <section class="panel overview-settings">
      <div class="section-head">
        <h2>核心参数</h2>
        <span id="saveState">本地保存</span>
      </div>
      ${settingsForm(ledger.settings)}
      ${ledgerBackupPanel(ledger)}
    </section>

    ${targetProgressPanel(data, calc)}

    <section class="metrics-grid">
      ${[
        metric("硬现金", `${format(calc.hardMonths)} 月`, "目标 >= 6 月", classify(calc.hardMonths, 6, 3)),
        metric("流动安全垫", `${format(calc.liquidMonths)} 月`, "现金 + 逆回购目标 >= 12 月", classify(calc.liquidMonths, 12, 6)),
        metric("股息覆盖", pct(calc.divCoverage), "过去12个月收入 / 年支出", classify(calc.divCoverage, 120, 80)),
        metric("QQQ 权重", pct(calc.qqqPct), qqqStatus(calc.qqqPct), calc.qqqPct > 15 ? "status-danger" : calc.qqqPct >= 5 ? "status-good" : "status-warn"),
        metric("Put 预算", pct(calc.putPct), "年度保险费 / 当前总资产", classify(calc.putPct, 0.5, 2)),
        metric("期货风险度", pct(calc.marginRisk), "占用保证金 / 账户权益", classify(calc.marginRisk, 50, 70, true)),
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
        <p class="notice">净值收益率只剔除“转入 / 转出”外部现金流；若同时维护现金余额，买入资产时用“内部划出”扣减现金，卖出资产后用“内部划入”增加现金。</p>
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
      <div class="table-wrap">
        <h2>下一笔钱</h2>
        <table>
          <thead><tr><th>方向</th><th>比例</th><th>金额</th><th>原因</th></tr></thead>
          <tbody>${allocations.map((item) => row(item.name, pct(item.weight), yuan(item.amount), item.reason)).join("")}</tbody>
        </table>
      </div>
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
    ["期货增强", yuan(data.futuresPool), pct(calc.futuresPoolPct), data.futuresPool >= 100 ? "资金池达标" : `还差 ${yuan(calc.futuresGap)}`],
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
      value: yuan(data.futuresPool),
      meta: `IC PB ${pct(data.icPb)}`,
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
      <label>期货账户权益（万元）<input type="number" name="futuresEquity" min="0" step="0.1" value="${escapeHtml(s.futuresEquity)}" /></label>
      <label>占用保证金（万元）<input type="number" name="usedMargin" min="0" step="0.1" value="${escapeHtml(s.usedMargin)}" /></label>
      <label>期货名义敞口（万元）<input type="number" name="futuresNotional" min="0" step="0.1" value="${escapeHtml(s.futuresNotional)}" /></label>
      <label class="checkline"><input type="checkbox" name="hasIc" ${s.hasIc ? "checked" : ""} /> 已有 IC 底仓</label>
    </form>
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
  if (!ledgerFilters[module]) ledgerFilters[module] = { search: "", action: "", bucket: "" };
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

function ledgerFilterControls(config, entries, filteredEntries) {
  const filter = getLedgerFilter(config.module);
  const actions = Array.from(new Set(entries.map((entry) => entry.action).filter(Boolean)));
  return `
    <div class="ledger-tools">
      <label>搜索<input id="ledgerSearch" name="search" value="${escapeHtml(filter.search)}" placeholder="代码 / 名称 / 备注" autocomplete="off" /></label>
      <label>动作<select id="ledgerActionFilter" name="action">
        <option value="">全部动作</option>
        ${actions.map((action) => `<option value="${escapeHtml(action)}" ${filter.action === action ? "selected" : ""}>${escapeHtml(actionLabels[action] || action)}</option>`).join("")}
      </select></label>
      <label>分类<select id="ledgerBucketFilter" name="bucket">
        <option value="">全部分类</option>
        ${config.buckets.map((bucket) => `<option value="${escapeHtml(bucket)}" ${filter.bucket === bucket ? "selected" : ""}>${escapeHtml(bucket)}</option>`).join("")}
      </select></label>
      <div class="ledger-tool-actions">
        <button type="button" data-action="clear-filters" data-module="${escapeHtml(config.module)}">清除</button>
        <button type="button" data-action="export-csv" data-module="${escapeHtml(config.module)}">导出 CSV</button>
        <button type="button" data-action="export-json" data-module="${escapeHtml(config.module)}">导出 JSON</button>
      </div>
      <p class="ledger-count">显示 ${escapeHtml(String(filteredEntries.length))} / ${escapeHtml(String(entries.length))} 笔</p>
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
  return filteredEntries.length ? ledgerTable(filteredEntries) : emptyFilteredState(config, entries.length);
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
  area.innerHTML = filteredEntries.length ? ledgerTable(filteredEntries) : emptyFilteredState(config, entries.length);
  if (count) count.textContent = `显示 ${filteredEntries.length} / ${entries.length} 笔`;
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
  return `
    <section class="panel">
      <div class="section-head">
        <h2>持仓分布</h2>
        <button type="button" data-tab="${escapeHtml(config.module)}" data-subpage="buckets">查看分布</button>
      </div>
      <div class="bucket-stack">
        ${config.buckets.map((bucket) => {
          const value = data.bucketTotals[bucket] || 0;
          const share = ratio(value, Math.max(totals.balance, 1));
          return `
            <div class="bucket-row">
              <div><strong>${escapeHtml(bucket)}</strong><span>${escapeHtml(bucketHint(config.module, bucket))}</span></div>
              <div><strong>${escapeHtml(yuan(value))}</strong><span>${escapeHtml(pct(share))}</span></div>
              <div class="bucket-track"><span style="width:${Math.min(100, Math.max(0, share))}%"></span></div>
            </div>
          `;
        }).join("")}
      </div>
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

function renderLedgerModuleOverview(config, data, calc, entries, modulePositions, totals) {
  appRoot.innerHTML = `
    <section class="module-hero module-${escapeHtml(config.accent || config.module)}">
      <div>
        <div class="module-hero-title">${moduleIcon(config.icon, config.title)}<span>${escapeHtml(config.description)}</span></div>
        <strong>${escapeHtml(yuan(totals.balance))}</strong>
        <p class="module-hero-note">净投入 ${escapeHtml(yuan(totals.cost))}，浮盈亏 ${escapeHtml(yuan(totals.balance - totals.cost))}</p>
        <div class="overview-actions">
          <button type="button" data-tab="${escapeHtml(config.module)}" data-subpage="entry">记录一笔</button>
          <button type="button" data-tab="${escapeHtml(config.module)}" data-subpage="ledger">查看流水</button>
        </div>
      </div>
      <div class="hero-grid">
        <div><span>流水笔数</span><strong>${entries.length}</strong></div>
        <div><span>过去12个月收入</span><strong>${escapeHtml(yuan(totals.income))}</strong></div>
        <div><span>占总资产</span><strong>${escapeHtml(pct(ratio(totals.balance, data.totalAssets)))}</strong></div>
      </div>
    </section>

    ${moduleFocusPanel(config, data, calc, entries, totals)}

    <section class="module-overview-grid">
      <div class="module-overview-stack">
        ${moduleBucketOverviewPanel(config, data, totals)}
        ${modulePositionSummaryPanel(config, modulePositions)}
      </div>
      ${moduleLedgerSummaryPanel(config, entries)}
    </section>
  `;
}

function renderLedgerModule(config, subpage = "full") {
  const ledger = loadLedger();
  const data = summarizeLedger(ledger);
  const calc = calculate(data);
  const entries = moduleEntries(config.module);
  const filteredEntries = filterEntries(entries, getLedgerFilter(config.module));
  const modulePositions = positionsForModule(config.module, data);
  const totals = {
    balance: data.moduleTotals[config.module] || 0,
    cost: data.moduleCostTotals[config.module] || 0,
    income: entries.reduce((sum, entry) => sum + entryIncomeImpact(entry), 0)
  };
  const editing = editingId ? ledger.entries.find((entry) => entry.id === editingId) : null;

  if (isDesktopMode() && subpage === "full") {
    renderLedgerModuleOverview(config, data, calc, entries, modulePositions, totals);
    return;
  }

  appRoot.innerHTML = `
    <section class="module-hero module-${escapeHtml(config.accent || config.module)}">
      <div>
        <div class="module-hero-title">${moduleIcon(config.icon, config.title)}<span>${escapeHtml(config.description)}</span></div>
        <strong>${escapeHtml(yuan(totals.balance))}</strong>
        <p class="module-hero-note">净投入 ${escapeHtml(yuan(totals.cost))}，浮盈亏 ${escapeHtml(yuan(totals.balance - totals.cost))}</p>
      </div>
      <div class="hero-grid">
        <div><span>流水笔数</span><strong>${entries.length}</strong></div>
        <div><span>过去12个月收入</span><strong>${escapeHtml(yuan(totals.income))}</strong></div>
        <div><span>占总资产</span><strong>${escapeHtml(pct(ratio(totals.balance, data.totalAssets)))}</strong></div>
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
        <section class="panel bucket-panel">
          <div class="section-head">
            <h2>持仓分布</h2>
            <span>${escapeHtml(config.primaryMetric)}</span>
          </div>
          <div class="bucket-stack">
            ${config.buckets.map((bucket) => {
              const value = data.bucketTotals[bucket] || 0;
              const share = ratio(value, Math.max(totals.balance, 1));
              return `
                <div class="bucket-row">
                  <div><strong>${escapeHtml(bucket)}</strong><span>${escapeHtml(bucketHint(config.module, bucket))}</span></div>
                  <div><strong>${escapeHtml(yuan(value))}</strong><span>${escapeHtml(pct(share))}</span></div>
                  <div class="bucket-track"><span style="width:${Math.min(100, Math.max(0, share))}%"></span></div>
                </div>
              `;
            }).join("")}
          </div>
        </section>
        ${positionValuationPanel(config, modulePositions)}
      </div>
      <section class="panel module-ledger-panel">
        <div class="section-head">
          <h2>投资流水</h2>
          <span>${entries.length} 笔</span>
        </div>
        ${ledgerFilterControls(config, entries, filteredEntries)}
        <div id="ledgerTableArea">${filteredEntries.length ? ledgerTable(filteredEntries) : emptyFilteredState(config, entries.length)}</div>
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
        ${metric("IC PB 分位", pct(data.icPb), "低于 30 才进入执行区", classify(data.icPb, 30, 50, true))}
        ${metric("期货风险度", pct(calc.marginRisk), "占用保证金 / 账户权益", classify(calc.marginRisk, 50, 70, true))}
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
  loadLedger().entries.forEach((entry, index) => {
    const symbol = String(entry.symbol || "").trim();
    const name = String(entry.name || "").trim();
    if (!symbol || !name) return;
    bySymbol.set(symbol.toUpperCase(), {
      symbol,
      name,
      module: entry.module || "",
      date: entry.date || "",
      order: index
    });
  });
  return Array.from(bySymbol.values()).sort((a, b) => {
    const aInModule = a.module === module ? 0 : 1;
    const bInModule = b.module === module ? 0 : 1;
    if (aInModule !== bInModule) return aInModule - bInModule;
    return b.order - a.order;
  });
}

function instrumentMemoryDatalists(module) {
  const memory = getInstrumentMemory(module);
  if (!memory.length) return "";
  return `
    <datalist id="instrumentSymbolMemory">
      ${memory.map((item) => `<option value="${escapeHtml(item.symbol)}" label="${escapeHtml(item.name)}"></option>`).join("")}
    </datalist>
    <datalist id="instrumentNameMemory">
      ${memory.map((item) => `<option value="${escapeHtml(item.name)}" label="${escapeHtml(item.symbol)}"></option>`).join("")}
    </datalist>
  `;
}

function findInstrumentMemory(value, field, module = "") {
  const text = String(value || "").trim();
  if (!text) return null;
  const normalized = field === "symbol" ? text.toUpperCase() : text;
  return getInstrumentMemory(module).find((item) => {
    if (field === "symbol") return item.symbol.toUpperCase() === normalized;
    return item.name === normalized;
  }) || null;
}

function entryForm(config, entry = null) {
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
    note: ""
  };
  return `
    <form id="entryForm" class="entry-form" data-module="${escapeHtml(config.module)}" data-id="${escapeHtml(data.id || "")}" novalidate>
      <label>日期<input type="date" name="date" value="${escapeHtml(data.date || today())}" required /></label>
      <label>分类<select name="bucket">${config.buckets.map((bucket) => `<option value="${escapeHtml(bucket)}" ${bucket === data.bucket ? "selected" : ""}>${escapeHtml(bucket)}</option>`).join("")}</select></label>
      <label>动作 ${termHelp("内部划入/划出")}<select name="action">${Object.entries(actionLabels).map(([value, label]) => `<option value="${value}" ${value === data.action ? "selected" : ""}>${escapeHtml(label)}</option>`).join("")}</select></label>
      <label>标的代码<input name="symbol" list="instrumentSymbolMemory" value="${escapeHtml(data.symbol || "")}" placeholder="${escapeHtml(config.symbolPlaceholder || "如 QQQ / IC2609")}" autocomplete="off" /></label>
      <label>标的名称<input name="name" list="instrumentNameMemory" value="${escapeHtml(data.name || "")}" placeholder="可选" autocomplete="off" /></label>
      <label>数量（股/份）<input type="number" name="quantity" step="0.0001" value="${escapeHtml(data.quantity || "")}" /></label>
      <label>价格（元）<input type="number" name="price" step="0.0001" value="${escapeHtml(data.price || "")}" /></label>
      <label>金额（万元）<input type="number" name="amount" step="0.0001" value="${escapeHtml(data.amount || "")}" required /><small class="field-hint" data-amount-preview>${data.amount ? `≈ ${escapeHtml(chartValue(safeAmount(data.amount) * 10000))} 元` : "填数量和价格后自动计算，可手工覆盖"}</small></label>
      <label>费用（万元）<input type="number" name="fee" step="0.0001" value="${escapeHtml(data.fee || "")}" /></label>
      <label class="entry-note">备注<input name="note" value="${escapeHtml(data.note || "")}" placeholder="${escapeHtml(config.notePlaceholder || "买入理由、移仓说明、分红记录")}" /></label>
      ${instrumentMemoryDatalists(config.module)}
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
          <tr><th>日期</th><th>分类</th><th>动作</th><th>标的</th><th>数量</th><th>价格</th><th>金额</th><th>费用</th><th>备注</th><th>操作</th></tr>
        </thead>
        <tbody>
          ${entries.map((entry) => `
            <tr>
              <td>${escapeHtml(entry.date || "-")}</td>
              <td>${escapeHtml(entry.bucket || "-")}</td>
              <td>${escapeHtml(actionLabels[entry.action] || entry.action || "-")}</td>
              <td>${escapeHtml(entry.symbol || entry.name || "-")}</td>
              <td>${escapeHtml(entry.quantity || "-")}</td>
              <td>${escapeHtml(entry.price || "-")}</td>
              <td>${escapeHtml(yuan(safeAmount(entry.amount)))}</td>
              <td>${escapeHtml(entry.fee ? yuan(safeAmount(entry.fee)) : "-")}</td>
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
    { label: "IC/IM资金池", value: data.futuresPool }
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

function saveHistory(history) {
  const deduped = [];
  const byDate = new Map();
  history
    .filter((point) => point && point.date)
    .sort((a, b) => compareDate(a.date, b.date))
    .forEach((point) => byDate.set(point.date, point));
  byDate.forEach((point) => deduped.push(point));
  localStorage.setItem(historyKey, JSON.stringify(deduped.slice(-2500)));
}

function historyViewPeriod() {
  const value = localStorage.getItem(historyViewKey);
  return value === "week" || value === "month" ? value : "day";
}

function setHistoryViewPeriod(period) {
  localStorage.setItem(historyViewKey, period);
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
  const result = {};
  await Promise.all(symbols.map(async (symbol) => {
    try {
      result[symbol] = await fetchTencentDailyHistory(symbol, endDate, days);
    } catch {
      result[symbol] = [];
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
    saveHistory(history);
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

function saveValuation(payload) {
  localStorage.setItem(valuationKey, JSON.stringify(payload));
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
  if (applied.length) saveLedger(ledger);
  saveValuation(payload);
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

function handleSettingsInput(event) {
  const form = event.target.closest("#settingsForm");
  if (!form) return;
  const partial = {
    annualExpense: numberFromForm(form, "annualExpense"),
    newMoney: numberFromForm(form, "newMoney"),
    manualTotalAssets: numberFromForm(form, "manualTotalAssets"),
    icPb: numberFromForm(form, "icPb"),
    imPb: numberFromForm(form, "imPb"),
    futuresEquity: numberFromForm(form, "futuresEquity"),
    usedMargin: numberFromForm(form, "usedMargin"),
    futuresNotional: numberFromForm(form, "futuresNotional"),
    hasIc: Boolean(form.elements.hasIc && form.elements.hasIc.checked)
  };
  if (event.target.name === "icPb") partial.icPbSource = "manual";
  if (event.target.name === "imPb") partial.imPbSource = "manual";
  saveSettings(partial);
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
  const amount = numberFromForm(form, "amount");
  const quantity = numberFromForm(form, "quantity");
  const price = numberFromForm(form, "price");

  if (!form.elements.date.value) addEntryValidationError(form, "date", "日期必填", errors);
  if (!form.elements.bucket.value) addEntryValidationError(form, "bucket", "分类必填", errors);
  if (!action) addEntryValidationError(form, "action", "动作必填", errors);
  if (amount <= 0) addEntryValidationError(form, "amount", "金额必须大于 0", errors);

  if (action === "buy" || action === "sell") {
    if (!hasInstrument) addEntryValidationError(form, "symbol", `${actionLabels[action]}必须填写标的代码或名称`, errors);
    if (quantity <= 0) addEntryValidationError(form, "quantity", `${actionLabels[action]}必须填写数量`, errors);
    if (price <= 0) addEntryValidationError(form, "price", `${actionLabels[action]}必须填写价格`, errors);
  } else if (action === "dividend" || action === "interest") {
    if (!hasInstrument) addEntryValidationError(form, "symbol", `${actionLabels[action]}必须填写标的代码或名称`, errors);
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

function handleEntrySubmit(event) {
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
    amount: numberFromForm(form, "amount"),
    fee: numberFromForm(form, "fee"),
    note: form.elements.note.value.trim()
  };
  const index = ledger.entries.findIndex((item) => item.id === id);
  if (index >= 0) ledger.entries[index] = entry;
  else ledger.entries.push(entry);
  saveLedger(ledger);
  editingId = null;
  render();
  showToast(index >= 0 ? "已保存修改" : "已新增 1 笔记录", "success");
}

function updateAmountPreview(form) {
  const amount = numberFromForm(form, "amount");
  const hint = form.querySelector("[data-amount-preview]");
  if (!hint) return;
  hint.textContent = amount > 0 ? `≈ ${chartValue(amount * 10000)} 元` : "填数量和价格后自动计算，可手工覆盖";
}

function handleEntryAmountAutoCalc(event) {
  const form = event.target.closest("#entryForm");
  if (!form) return;
  const targetName = event.target.name;
  if (targetName === "amount") {
    form.dataset.amountManual = "true";
    form.dataset.amountAuto = "false";
    updateAmountPreview(form);
    return;
  }
  if (targetName !== "quantity" && targetName !== "price") return;
  const quantity = Number(form.elements.quantity ? form.elements.quantity.value : "");
  const price = Number(form.elements.price ? form.elements.price.value : "");
  const amountInput = form.elements.amount;
  if (!amountInput || !Number.isFinite(quantity) || !Number.isFinite(price) || quantity <= 0 || price <= 0) {
    updateAmountPreview(form);
    return;
  }
  const canAutoFill = form.dataset.amountManual !== "true" || form.dataset.amountAuto === "true" || amountInput.value === "";
  if (!canAutoFill) {
    updateAmountPreview(form);
    return;
  }
  const amount = quantity * price / 10000;
  amountInput.value = chartValue(amount);
  form.dataset.amountAuto = "true";
  updateAmountPreview(form);
}

function handleInstrumentMemoryInput(event) {
  const form = event.target.closest("#entryForm");
  if (!form || (event.target.name !== "symbol" && event.target.name !== "name")) return;
  const module = form.dataset.module || "";
  const symbolInput = form.elements.symbol;
  const nameInput = form.elements.name;
  if (!symbolInput || !nameInput) return;

  if (event.target.name === "symbol") {
    const match = findInstrumentMemory(symbolInput.value, "symbol", module);
    if (match && match.name) nameInput.value = match.name;
    return;
  }

  const match = findInstrumentMemory(nameInput.value, "name", module);
  if (match && match.symbol) symbolInput.value = match.symbol;
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
  savePositionValuations(valuations);
  render();
  return true;
}

function marketCodeForAshare(symbol) {
  const code = String(symbol || "").trim();
  if (!/^\d{6}$/.test(code)) return null;
  if (/^(000|001|002|003|300|301)/.test(code)) return `sz${code}`;
  if (/^(600|601|603|605|688|689)/.test(code)) return `sh${code}`;
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
    savePositionValuations(valuations);
  } catch (error) {
    ok = false;
    message = `同步失败：${error.message || "行情不可用"}`;
    valuations[key] = {
      ...current,
      updatedAt: new Date().toISOString(),
      source: current.source || "手工估值",
      note: current.note || `同步失败：${error.message || "行情不可用"}`
    };
    savePositionValuations(valuations);
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
  savePositionValuations(valuations);
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
  savePositionValuations(valuations);
  render();
  return applied;
}

async function loadPositionQuotesJson() {
  try {
    const response = await fetch(`./data/position-quotes.json?ts=${Date.now()}`);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const applied = applyPositionQuotes(await response.json());
    showToast(applied ? `已应用 ${applied} 条价格` : "价格 JSON 已读取，但没有匹配当前持仓代码", applied ? "success" : "info");
  } catch {
    showLocalServiceGuide("quotes");
    showToast("读取价格 JSON 失败：请先运行 scripts/update_position_quotes.py", "error");
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
  const headers = ["日期", "模块", "分类", "动作", "代码", "名称", "数量", "价格", "金额万元", "费用万元", "备注"];
  const rows = entries.map((entry) => [
    entry.date || "",
    config.title,
    entry.bucket || "",
    actionLabels[entry.action] || entry.action || "",
    entry.symbol || "",
    entry.name || "",
    entry.quantity || "",
    entry.price || "",
    entry.amount || 0,
    entry.fee || 0,
    entry.note || ""
  ]);
  const csv = [headers, ...rows].map((rowData) => rowData.map(csvCell).join(",")).join("\n");
  downloadText(`${fileBase}.csv`, csv, "text/csv;charset=utf-8");
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
    ["dividend", "现金", "deposit", "现金", "生活现金", 6, "6个月底线"],
    ["dividend", "类现金", "buy", "GC001", "逆回购", 8, "流动缓冲"],
    ["dividend", "高分红股票", "buy", "000568", "泸州老窖", 18, "高分红股票仓"],
    ["dividend", "高分红股票", "buy", "000858", "五粮液", 14, "高分红股票仓"],
    ["dividend", "高分红股票", "buy", "红利组合", "红利组合", 38, "现金流底座"],
    ["dividend", "债券", "buy", "债券基金", "债券", 6, "波动缓冲"],
    ["dividend", "高分红股票", "dividend", "红利组合", "税后分红", 3.8, "过去12个月分红"],
    ["qqq", "QQQ", "buy", "QQQ", "纳指100", 6, "右尾底仓"],
    ["qqq", "QLD", "buy", "QLD", "120日线策略", 2, "站上120日线后买入"],
    ["put", "SPY put", "buy", "SPY 2027P300", "深度虚值保险", 1.2, "Delta约-0.08，年度保险预算"],
    ["put", "保险预算", "deposit", "保险预算池", "保费预留", 0.8, "只用于极端黑天鹅保护"],
    ["ic", "IC/IM资金池", "deposit", "期货资金池", "专项资金", 10, "等待低估"],
    ["ic", "IC", "roll", "IC2607", "换月观察", 0, "记录移仓动作"]
  ];
  return {
    settings: { ...defaultSettings, annualExpense: 12, newMoney: 2, icPb: 35, imPb: 22 },
    entries: entries.map(([module, bucket, action, symbol, name, amount, note], index) => {
      const quantityMap = {
        "000568": "1500",
        "000858": "1400",
        QQQ: "18",
        QLD: "10"
      };
      const priceMap = {
        "000568": "120",
        "000858": "100",
        QQQ: "3333.3333",
        QLD: "2000"
      };
      return {
      id: makeId(),
      module,
      bucket,
      action,
      symbol,
      name,
      amount,
      quantity: action === "buy" ? (quantityMap[symbol] || "") : "",
      price: action === "buy" ? (priceMap[symbol] || "") : "",
      fee: 0,
      note,
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
      saveLedger(ledger);
      render();
      showToast("已删除 1 笔记录", "success");
    } else if (action.dataset.action === "cancel-edit") {
      editingId = null;
      render();
    } else if (action.dataset.action === "clear-filters") {
      ledgerFilters[action.dataset.module] = { search: "", action: "", bucket: "" };
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
      saveLedger(createSampleLedger());
      savePositionValuations(createSamplePositionValuations());
      saveHistory([]);
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

  if (event.target.closest("#loadSample")) {
    const ledger = loadLedger();
    const hasData = ledger.entries.length > 0 || loadHistory().length > 0 || Object.keys(loadPositionValuations()).length > 0;
    if (hasData && !confirmDanger(`示例数据会覆盖当前 ${ledger.entries.length} 笔流水，并清空历史快照。确认继续？`)) return;
    saveLedger(createSampleLedger());
    savePositionValuations(createSamplePositionValuations());
    saveHistory([]);
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

  if (event.target.closest("#loadValuationJson")) {
    loadValuationJson();
  }

  if (event.target.closest("#loadPositionQuotesJson")) {
    loadPositionQuotesJson();
  }
});

document.addEventListener("keydown", (event) => {
  if (event.key === "Escape") closeTermTips();
});

document.addEventListener("submit", handleEntrySubmit);
document.addEventListener("input", (event) => {
  if (event.target.closest("#entryForm")) clearEntryValidation(event.target.closest("#entryForm"));
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
  if (event.target.id !== "valuationFile") return;
  const file = event.target.files && event.target.files[0];
  if (!file) return;
  const status = document.querySelector("#valuationStatus");
  if (status) status.textContent = "导入中...";
  try {
    applyValuation(JSON.parse(await file.text()));
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
      applyValuation(await response.json(), { render: options.render !== false, source: item.source, silent: options.silent });
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
}

bootstrap();

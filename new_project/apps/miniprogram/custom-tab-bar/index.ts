interface TabItem {
  path: string;
  text: string;
  key: "home" | "ledger" | "strategy" | "market" | "reports";
}

const tabs: TabItem[] = [
  {path: "/pages/overview/overview", text: "总览", key: "home"},
  {path: "/pages/ledger/ledger", text: "账本", key: "ledger"},
  {path: "/pages/strategy/strategy", text: "策略", key: "strategy"},
  {path: "/pages/market/market", text: "市场", key: "market"},
  {path: "/pages/reports/reports", text: "报表", key: "reports"}
];

function currentTabIndex(): number {
  const pages = getCurrentPages();
  const currentPage = pages[pages.length - 1];
  const rawRoute = currentPage?.route || "";
  const route = rawRoute.startsWith("/") ? rawRoute : `/${rawRoute}`;
  const index = tabs.findIndex((tab) => tab.path === route);
  return index >= 0 ? index : 0;
}

Component({
  data: {
    selected: 0,
    tabs
  },

  lifetimes: {
    attached() {
      this.setData({selected: currentTabIndex()});
    }
  },

  pageLifetimes: {
    show() {
      this.setData({selected: currentTabIndex()});
    }
  },

  methods: {
    switchTab(event: WechatMiniprogram.CustomEvent<{path: string; index: number}>) {
      const {path, index} = event.currentTarget.dataset as {path: string; index: number};
      this.setData({selected: Number(index)});
      wx.switchTab({url: path});
    }
  }
});

package com.personal.investment.market.domain;

public enum AssetType {
  EQUITY,
  ETF,
  /** Non-tradable market index used only as a pricing and futures-basis anchor. */
  INDEX,
  FUTURE,
  OPTION
}

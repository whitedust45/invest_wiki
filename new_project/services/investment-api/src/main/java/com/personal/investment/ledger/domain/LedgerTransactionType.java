package com.personal.investment.ledger.domain;

/** Persisted transaction facts. FUTURES_ROLL remains a command that creates two persisted facts. */
public enum LedgerTransactionType {
  EXTERNAL_FUNDING,
  EXTERNAL_WITHDRAWAL,
  INTERNAL_TRANSFER,
  TRADE_BUY,
  TRADE_SELL,
  DIVIDEND,
  INTEREST,
  FEE,
  FUTURES_OPEN,
  FUTURES_CLOSE,
  FUTURES_MARGIN,
  FUTURES_DAILY_SETTLEMENT,
  OPTION_OPEN,
  OPTION_CLOSE,
  OPTION_EXPIRE,
  CORPORATE_ACTION,
  REVERSAL
}

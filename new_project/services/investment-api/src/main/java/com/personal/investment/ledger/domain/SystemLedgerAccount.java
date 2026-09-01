package com.personal.investment.ledger.domain;

/** System accounts are provisioned by the backend and are never selectable by the mini program. */
public enum SystemLedgerAccount {
  EXTERNAL_EQUITY(LedgerAccountKind.EQUITY_EXTERNAL, "外部资金"),
  DIVIDEND_INCOME(LedgerAccountKind.INCOME_DIVIDEND, "股息收入"),
  INTEREST_INCOME(LedgerAccountKind.INCOME_INTEREST, "利息收入"),
  FEE_EXPENSE(LedgerAccountKind.EXPENSE_FEE, "交易费用"),
  WITHHOLDING_TAX_EXPENSE(LedgerAccountKind.EXPENSE_WITHHOLDING_TAX, "代扣税费"),
  OPTION_EXPENSE(LedgerAccountKind.EXPENSE_OPTION, "期权损失"),
  REALIZED_PNL(LedgerAccountKind.PNL_REALIZED, "已实现损益");

  private final LedgerAccountKind accountKind;
  private final String displayName;

  SystemLedgerAccount(LedgerAccountKind accountKind, String displayName) {
    this.accountKind = accountKind;
    this.displayName = displayName;
  }

  public LedgerAccountKind accountKind() {
    return accountKind;
  }

  public String displayName() {
    return displayName;
  }

  public String accountCode(CurrencyCode currency) {
    return "SYS:" + name() + ":" + currency.name();
  }
}

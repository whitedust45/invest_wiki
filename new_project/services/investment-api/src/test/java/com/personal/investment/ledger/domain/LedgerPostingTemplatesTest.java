package com.personal.investment.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;

class LedgerPostingTemplatesTest {
  @Test
  void fundingAndTransferUseExplicitCounterparties() {
    assertThat(LedgerPostingTemplates.externalFunding("cash-account", "equity-external",
        Money.of(10_000, CurrencyCode.CNY)).postings()).containsExactly(
            debit("cash-account", 10_000), credit("equity-external", 10_000));

    assertThat(LedgerPostingTemplates.internalTransfer("cash-source", "cash-destination",
        Money.of(999, CurrencyCode.CNY)).postings()).containsExactly(
            debit("cash-destination", 999), credit("cash-source", 999));

    assertThatIllegalArgumentException().isThrownBy(() -> LedgerPostingTemplates.internalTransfer(
        "cash-account", "cash-account", Money.of(1, CurrencyCode.CNY)));
  }

  @Test
  void incomeTreatsOmittedTaxAsZeroAndSeparatesWithholdingWhenPresent() {
    assertThat(LedgerPostingTemplates.income("cash-account", "income-dividend", "expense-tax",
        Money.of(1_000, CurrencyCode.USD), Money.of(0, CurrencyCode.USD)).postings()).containsExactly(
            debitUsd("cash-account", 1_000), creditUsd("income-dividend", 1_000));

    assertThat(LedgerPostingTemplates.income("cash-account", "income-dividend", "expense-tax",
        Money.of(1_000, CurrencyCode.USD), Money.of(100, CurrencyCode.USD)).postings()).containsExactly(
            debitUsd("cash-account", 900), debitUsd("expense-tax", 100), creditUsd("income-dividend", 1_000));

    assertThatIllegalArgumentException().isThrownBy(() -> LedgerPostingTemplates.income(
        "cash-account", "income-dividend", "expense-tax", Money.of(100, CurrencyCode.USD),
        Money.of(101, CurrencyCode.USD)));
  }

  @Test
  void buyCapitalizesFeeAndSellSeparatesFeeFromRealizedGain() {
    assertThat(LedgerPostingTemplates.spotBuy("cash-account", "investment-account",
        Money.of(40_000, CurrencyCode.CNY), Money.of(5, CurrencyCode.CNY)).postings()).containsExactly(
            debit("investment-account", 40_005), credit("cash-account", 40_005));

    assertThat(LedgerPostingTemplates.spotSell("cash-account", "investment-account",
        "expense-fee", "pnl-realized", Money.of(50_000, CurrencyCode.CNY),
        Money.of(40_005, CurrencyCode.CNY), Money.of(5, CurrencyCode.CNY)).postings()).containsExactly(
            debit("cash-account", 49_995), debit("expense-fee", 5),
            credit("investment-account", 40_005), credit("pnl-realized", 9_995));
  }

  @Test
  void sellRepresentsLossOnTheDebitSideAndRejectsFeeAboveProceeds() {
    List<Posting> postings = LedgerPostingTemplates.spotSell("cash-account", "investment-account",
        "expense-fee", "pnl-realized", Money.of(30_000, CurrencyCode.CNY),
        Money.of(40_000, CurrencyCode.CNY), Money.of(5, CurrencyCode.CNY)).postings();

    assertThat(postings).containsExactly(
        debit("cash-account", 29_995), debit("expense-fee", 5), debit("pnl-realized", 10_000),
        credit("investment-account", 40_000));
    assertThatIllegalArgumentException().isThrownBy(() -> LedgerPostingTemplates.spotSell(
        "cash-account", "investment-account", "expense-fee", "pnl-realized",
        Money.of(4, CurrencyCode.CNY), Money.of(1, CurrencyCode.CNY), Money.of(5, CurrencyCode.CNY)));
  }

  private Posting debit(String accountId, long cent) {
    return new Posting(accountId, PostingSide.DEBIT, Money.of(cent, CurrencyCode.CNY));
  }

  private Posting credit(String accountId, long cent) {
    return new Posting(accountId, PostingSide.CREDIT, Money.of(cent, CurrencyCode.CNY));
  }

  private Posting debitUsd(String accountId, long cent) {
    return new Posting(accountId, PostingSide.DEBIT, Money.of(cent, CurrencyCode.USD));
  }

  private Posting creditUsd(String accountId, long cent) {
    return new Posting(accountId, PostingSide.CREDIT, Money.of(cent, CurrencyCode.USD));
  }
}

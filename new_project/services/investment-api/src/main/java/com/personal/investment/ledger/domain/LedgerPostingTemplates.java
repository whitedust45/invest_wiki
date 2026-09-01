package com.personal.investment.ledger.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The ledger's canonical double-entry templates. Command handlers resolve account identities and
 * exact FIFO cost before calling this class; this class never calculates money with floating point.
 */
public final class LedgerPostingTemplates {
  private LedgerPostingTemplates() {
  }

  public static BalancedPostings externalFunding(
      String cashAccountId, String externalEquityAccountId, Money amount) {
    requireDistinct(cashAccountId, externalEquityAccountId);
    requirePositive(amount, "funding amount");
    return BalancedPostings.of(List.of(
        debit(cashAccountId, amount), credit(externalEquityAccountId, amount)));
  }

  public static BalancedPostings externalWithdrawal(
      String cashAccountId, String externalEquityAccountId, Money amount) {
    requireDistinct(cashAccountId, externalEquityAccountId);
    requirePositive(amount, "withdrawal amount");
    return BalancedPostings.of(List.of(
        debit(externalEquityAccountId, amount), credit(cashAccountId, amount)));
  }

  public static BalancedPostings internalTransfer(
      String sourceCashAccountId, String destinationCashAccountId, Money amount) {
    requireDistinct(sourceCashAccountId, destinationCashAccountId);
    requirePositive(amount, "transfer amount");
    return BalancedPostings.of(List.of(
        debit(destinationCashAccountId, amount), credit(sourceCashAccountId, amount)));
  }

  public static BalancedPostings income(
      String cashAccountId,
      String incomeAccountId,
      String withholdingTaxExpenseAccountId,
      Money grossAmount,
      Money taxWithheld) {
    requireDistinct(cashAccountId, incomeAccountId, withholdingTaxExpenseAccountId);
    requirePositive(grossAmount, "gross income amount");
    requireSameCurrency(grossAmount, taxWithheld);
    if (taxWithheld.cent() > grossAmount.cent()) {
      throw new IllegalArgumentException("withholding tax must not exceed gross income");
    }

    List<Posting> postings = new ArrayList<>();
    addDebitWhenPositive(postings, cashAccountId, grossAmount.minus(taxWithheld));
    addDebitWhenPositive(postings, withholdingTaxExpenseAccountId, taxWithheld);
    postings.add(credit(incomeAccountId, grossAmount));
    return BalancedPostings.of(postings);
  }

  public static BalancedPostings spotBuy(
      String cashAccountId, String investmentAccountId, Money grossPurchaseAmount, Money fee) {
    requireDistinct(cashAccountId, investmentAccountId);
    requirePositive(grossPurchaseAmount, "gross purchase amount");
    requireSameCurrency(grossPurchaseAmount, fee);
    Money capitalizedCost = grossPurchaseAmount.plus(fee);
    return BalancedPostings.of(List.of(
        debit(investmentAccountId, capitalizedCost), credit(cashAccountId, capitalizedCost)));
  }

  public static BalancedPostings fee(String cashAccountId, String feeExpenseAccountId, Money amount) {
    requireDistinct(cashAccountId, feeExpenseAccountId);
    requirePositive(amount, "fee amount");
    return BalancedPostings.of(List.of(
        debit(feeExpenseAccountId, amount), credit(cashAccountId, amount)));
  }

  public static BalancedPostings futuresMargin(String cashAccountId, String availableMarginAccountId,
      MarginDirection direction, Money amount) {
    requireDistinct(cashAccountId, availableMarginAccountId);
    Objects.requireNonNull(direction, "margin direction must not be null");
    requirePositive(amount, "margin amount");
    return direction == MarginDirection.IN
        ? BalancedPostings.of(List.of(debit(availableMarginAccountId, amount), credit(cashAccountId, amount)))
        : BalancedPostings.of(List.of(debit(cashAccountId, amount), credit(availableMarginAccountId, amount)));
  }

  public static BalancedPostings futuresOpen(String availableMarginAccountId, String lockedMarginAccountId,
      String cashAccountId, String feeExpenseAccountId, Money initialMargin, Money fee) {
    requireDistinct(availableMarginAccountId, lockedMarginAccountId, cashAccountId, feeExpenseAccountId);
    requirePositive(initialMargin, "initial margin");
    requireSameCurrency(initialMargin, fee);
    List<Posting> postings = new ArrayList<>(4);
    postings.add(debit(lockedMarginAccountId, initialMargin));
    postings.add(credit(availableMarginAccountId, initialMargin));
    addDebitWhenPositive(postings, feeExpenseAccountId, fee);
    addCreditWhenPositive(postings, cashAccountId, fee);
    return BalancedPostings.of(postings);
  }

  /** Releases locked margin, recognizes close-to-last-settlement PnL and records an optional cash fee. */
  public static BalancedPostings futuresClose(String availableMarginAccountId, String lockedMarginAccountId,
      String cashAccountId, String feeExpenseAccountId, String realizedPnlAccountId, Money releasedMargin,
      long realizedPnlCent, Money fee) {
    requireDistinct(availableMarginAccountId, lockedMarginAccountId, cashAccountId, feeExpenseAccountId,
        realizedPnlAccountId);
    requirePositive(releasedMargin, "released margin");
    requireSameCurrency(releasedMargin, fee);
    Money pnl = Money.of(absolute(realizedPnlCent), releasedMargin.currency());
    List<Posting> postings = new ArrayList<>(6);
    postings.add(debit(availableMarginAccountId, releasedMargin));
    postings.add(credit(lockedMarginAccountId, releasedMargin));
    if (realizedPnlCent > 0) {
      postings.add(debit(availableMarginAccountId, pnl));
      postings.add(credit(realizedPnlAccountId, pnl));
    } else if (realizedPnlCent < 0) {
      postings.add(debit(realizedPnlAccountId, pnl));
      postings.add(credit(availableMarginAccountId, pnl));
    }
    addDebitWhenPositive(postings, feeExpenseAccountId, fee);
    addCreditWhenPositive(postings, cashAccountId, fee);
    return BalancedPostings.of(postings);
  }

  public static BalancedPostings futuresDailySettlement(String availableMarginAccountId, String realizedPnlAccountId,
      long realizedPnlCent, CurrencyCode currency) {
    requireDistinct(availableMarginAccountId, realizedPnlAccountId);
    if (realizedPnlCent == 0) {
      throw new IllegalArgumentException("zero PnL settlement has no monetary postings");
    }
    Money amount = Money.of(absolute(realizedPnlCent), currency);
    return realizedPnlCent > 0
        ? BalancedPostings.of(List.of(debit(availableMarginAccountId, amount), credit(realizedPnlAccountId, amount)))
        : BalancedPostings.of(List.of(debit(realizedPnlAccountId, amount), credit(availableMarginAccountId, amount)));
  }

  public static BalancedPostings spotSell(
      String cashAccountId,
      String investmentAccountId,
      String feeExpenseAccountId,
      String realizedPnlAccountId,
      Money grossProceeds,
      Money allocatedCost,
      Money fee) {
    requireDistinct(cashAccountId, investmentAccountId, feeExpenseAccountId, realizedPnlAccountId);
    requirePositive(grossProceeds, "gross sale proceeds");
    requireSameCurrency(grossProceeds, allocatedCost);
    requireSameCurrency(grossProceeds, fee);
    if (fee.cent() > grossProceeds.cent()) {
      throw new IllegalArgumentException("sale fee must not exceed gross proceeds");
    }

    List<Posting> postings = new ArrayList<>();
    addDebitWhenPositive(postings, cashAccountId, grossProceeds.minus(fee));
    addDebitWhenPositive(postings, feeExpenseAccountId, fee);
    if (grossProceeds.cent() < allocatedCost.cent()) {
      postings.add(debit(realizedPnlAccountId, allocatedCost.minus(grossProceeds)));
    }
    addCreditWhenPositive(postings, investmentAccountId, allocatedCost);
    if (grossProceeds.cent() > allocatedCost.cent()) {
      postings.add(credit(realizedPnlAccountId, grossProceeds.minus(allocatedCost)));
    }
    return BalancedPostings.of(postings);
  }

  /** Long option premium accounting is the same investment-cost shape as a spot purchase. */
  public static BalancedPostings optionOpen(String cashAccountId, String investmentAccountId, Money grossPremium,
      Money fee) {
    return spotBuy(cashAccountId, investmentAccountId, grossPremium, fee);
  }

  /** Long option sale accounting recognizes FIFO cost and a separately expensed closing fee. */
  public static BalancedPostings optionClose(String cashAccountId, String investmentAccountId,
      String feeExpenseAccountId, String realizedPnlAccountId, Money grossPremium, Money allocatedCost, Money fee) {
    return spotSell(cashAccountId, investmentAccountId, feeExpenseAccountId, realizedPnlAccountId, grossPremium,
        allocatedCost, fee);
  }

  /** A user-confirmed worthless expiry derecognizes the entire remaining option cost without inventing cash. */
  public static BalancedPostings optionExpire(String investmentAccountId, String optionExpenseAccountId,
      Money allocatedCost) {
    requireDistinct(investmentAccountId, optionExpenseAccountId);
    requirePositive(allocatedCost, "expired option cost");
    return BalancedPostings.of(List.of(
        debit(optionExpenseAccountId, allocatedCost), credit(investmentAccountId, allocatedCost)));
  }

  private static Posting debit(String accountId, Money amount) {
    return new Posting(accountId, PostingSide.DEBIT, amount);
  }

  private static Posting credit(String accountId, Money amount) {
    return new Posting(accountId, PostingSide.CREDIT, amount);
  }

  private static void addDebitWhenPositive(List<Posting> postings, String accountId, Money amount) {
    if (amount.isPositive()) {
      postings.add(debit(accountId, amount));
    }
  }

  private static void addCreditWhenPositive(List<Posting> postings, String accountId, Money amount) {
    if (amount.isPositive()) {
      postings.add(credit(accountId, amount));
    }
  }

  private static void requirePositive(Money amount, String field) {
    Objects.requireNonNull(amount, field + " must not be null");
    if (!amount.isPositive()) {
      throw new IllegalArgumentException(field + " must be positive");
    }
  }

  private static void requireSameCurrency(Money first, Money second) {
    Objects.requireNonNull(first, "money must not be null");
    Objects.requireNonNull(second, "money must not be null");
    if (first.currency() != second.currency()) {
      throw new IllegalArgumentException("all transaction amounts must use one native currency");
    }
  }

  private static void requireDistinct(String... accountIds) {
    Set<String> unique = new HashSet<>();
    for (String accountId : accountIds) {
      if (accountId == null || accountId.isBlank()) {
        throw new IllegalArgumentException("accountId must not be blank");
      }
      unique.add(accountId);
    }
    if (unique.size() != accountIds.length) {
      throw new IllegalArgumentException("ledger posting accounts must be distinct");
    }
  }

  private static long absolute(long value) {
    if (value == Long.MIN_VALUE) {
      throw new IllegalArgumentException("realized PnL overflow");
    }
    return Math.abs(value);
  }
}

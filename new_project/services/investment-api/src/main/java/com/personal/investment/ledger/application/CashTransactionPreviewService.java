package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.BalancedPostings;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.LedgerAccount;
import com.personal.investment.ledger.domain.LedgerAccountKind;
import com.personal.investment.ledger.domain.LedgerAccountStatus;
import com.personal.investment.ledger.domain.LedgerPostingFact;
import com.personal.investment.ledger.domain.LedgerPostingTemplates;
import com.personal.investment.ledger.domain.LedgerTransactionType;
import com.personal.investment.ledger.domain.Money;
import com.personal.investment.ledger.domain.Posting;
import com.personal.investment.ledger.domain.PostingSide;
import com.personal.investment.ledger.domain.SystemLedgerAccount;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only counterpart of the supported cash commands; it never creates ledger facts or IDs. */
@Service
public class CashTransactionPreviewService {
  private final LedgerCommandAccountPort accountPort;
  private final LedgerTransactionPort transactionPort;

  public CashTransactionPreviewService(LedgerCommandAccountPort accountPort, LedgerTransactionPort transactionPort) {
    this.accountPort = accountPort;
    this.transactionPort = transactionPort;
  }

  @Transactional(readOnly = true)
  public CashTransactionPreviewResult preview(String ownerUserId, LedgerTransactionType type, String cashAccountId,
      String destinationAccountId, long amountCent) {
    if (amountCent <= 0) {
      throw new IllegalArgumentException("amount cent must be positive");
    }
    LedgerAccount cash = activeCash(ownerUserId, cashAccountId);
    Money amount = Money.of(amountCent, cash.currency());
    return switch (type) {
      case EXTERNAL_FUNDING -> withPreview(ownerUserId, cash,
          LedgerPostingTemplates.externalFunding(cash.accountId(), externalEquity(ownerUserId, cash).accountId(), amount));
      case EXTERNAL_WITHDRAWAL -> withPreview(ownerUserId, cash,
          LedgerPostingTemplates.externalWithdrawal(cash.accountId(), externalEquity(ownerUserId, cash).accountId(), amount));
      case INTERNAL_TRANSFER -> {
        LedgerAccount destination = activeCash(ownerUserId, destinationAccountId);
        if (cash.accountId().equals(destination.accountId()) || cash.currency() != destination.currency()) {
          throw new IllegalArgumentException("internal transfer accounts must be distinct and use the same currency");
        }
        yield withPreview(ownerUserId, cash, destination,
            LedgerPostingTemplates.internalTransfer(cash.accountId(), destination.accountId(), amount));
      }
      case FEE -> withPreview(ownerUserId, cash,
          LedgerPostingTemplates.fee(cash.accountId(), feeExpense(ownerUserId, cash).accountId(), amount));
      default -> throw new IllegalArgumentException("cash preview does not support " + type);
    };
  }

  @Transactional(readOnly = true)
  public CashTransactionPreviewResult previewIncome(String ownerUserId, LedgerTransactionType type,
      String cashAccountId, long grossAmountCent, long taxWithheldCent) {
    if (type != LedgerTransactionType.DIVIDEND && type != LedgerTransactionType.INTEREST) {
      throw new IllegalArgumentException("income preview only accepts DIVIDEND or INTEREST");
    }
    if (grossAmountCent <= 0 || taxWithheldCent < 0) {
      throw new IllegalArgumentException("income amounts are invalid");
    }
    LedgerAccount cash = activeCash(ownerUserId, cashAccountId);
    SystemLedgerAccount incomeSystem = type == LedgerTransactionType.DIVIDEND
        ? SystemLedgerAccount.DIVIDEND_INCOME : SystemLedgerAccount.INTEREST_INCOME;
    BalancedPostings postings = LedgerPostingTemplates.income(cash.accountId(),
        activeSystem(ownerUserId, incomeSystem, cash).accountId(),
        activeSystem(ownerUserId, SystemLedgerAccount.WITHHOLDING_TAX_EXPENSE, cash).accountId(),
        Money.of(grossAmountCent, cash.currency()), Money.of(taxWithheldCent, cash.currency()));
    return withPreview(ownerUserId, cash, postings);
  }

  private CashTransactionPreviewResult withPreview(String ownerUserId, LedgerAccount cash, BalancedPostings postings) {
    return withPreview(ownerUserId, cash, null, postings);
  }

  private CashTransactionPreviewResult withPreview(String ownerUserId, LedgerAccount cash, LedgerAccount destination,
      BalancedPostings postings) {
    ensureNonNegativeCash(ownerUserId, postings, cash, destination);
    Map<String, LedgerAccount> accounts = new HashMap<>();
    accounts.put(cash.accountId(), cash);
    if (destination != null) {
      accounts.put(destination.accountId(), destination);
    }
    for (Posting posting : postings.postings()) {
      accountPort.findByIdAndOwner(posting.accountId(), ownerUserId).ifPresent(account -> accounts.put(account.accountId(), account));
    }
    return new CashTransactionPreviewResult(cash.currency(), postings.postings().stream().map(posting -> {
      LedgerAccount account = accounts.get(posting.accountId());
      if (account == null) {
        throw new IllegalStateException("preview account metadata is missing");
      }
      return new PreviewPosting(account.accountCode(), account.displayName(), posting.side(), posting.amount().cent(),
          posting.amount().currency());
    }).toList());
  }

  private void ensureNonNegativeCash(String ownerUserId, BalancedPostings proposed, LedgerAccount... cashAccounts) {
    Map<String, Long> balances = new HashMap<>();
    Map<String, CurrencyCode> currencies = new HashMap<>();
    for (LedgerAccount account : cashAccounts) {
      if (account != null) {
        balances.put(account.accountId(), 0L);
        currencies.put(account.accountId(), account.currency());
      }
    }
    for (LedgerPostingFact fact : transactionPort.findPostingFactsByOwner(ownerUserId)) {
      apply(balances, currencies, fact.accountId(), fact.side(), fact.amount());
    }
    for (Posting posting : proposed.postings()) {
      apply(balances, currencies, posting.accountId(), posting.side(), posting.amount());
    }
    balances.forEach((accountId, balance) -> {
      if (balance < 0) {
        throw new InsufficientBalanceException(accountId);
      }
    });
  }

  private static void apply(Map<String, Long> balances, Map<String, CurrencyCode> currencies, String accountId,
      PostingSide side, Money amount) {
    CurrencyCode currency = currencies.get(accountId);
    if (currency == null) {
      return;
    }
    if (currency != amount.currency()) {
      throw new IllegalStateException("cash posting currency does not match its account");
    }
    try {
      balances.put(accountId, side == PostingSide.DEBIT
          ? Math.addExact(balances.get(accountId), amount.cent())
          : Math.subtractExact(balances.get(accountId), amount.cent()));
    } catch (ArithmeticException exception) {
      throw new IllegalStateException("cash balance overflow", exception);
    }
  }

  private LedgerAccount activeCash(String ownerUserId, String accountId) {
    if (accountId == null || accountId.isBlank()) {
      throw new IllegalArgumentException("cash account id is required");
    }
    LedgerAccount account = accountPort.findByIdAndOwner(accountId, ownerUserId)
        .orElseThrow(() -> new IllegalArgumentException("cash account was not found"));
    if (account.accountKind() != LedgerAccountKind.ASSET_CASH || account.status() != LedgerAccountStatus.ACTIVE) {
      throw new IllegalArgumentException("cash account must be active");
    }
    return account;
  }

  private LedgerAccount externalEquity(String ownerUserId, LedgerAccount cash) {
    return activeSystem(ownerUserId, SystemLedgerAccount.EXTERNAL_EQUITY, cash);
  }

  private LedgerAccount feeExpense(String ownerUserId, LedgerAccount cash) {
    return activeSystem(ownerUserId, SystemLedgerAccount.FEE_EXPENSE, cash);
  }

  private LedgerAccount activeSystem(String ownerUserId, SystemLedgerAccount system, LedgerAccount cash) {
    LedgerAccount account = accountPort.findByOwnerAndCode(ownerUserId, system.accountCode(cash.currency()))
        .orElseThrow(() -> new IllegalArgumentException("required system account was not provisioned"));
    if (account.accountKind() != system.accountKind() || account.status() != LedgerAccountStatus.ACTIVE
        || account.currency() != cash.currency()) {
      throw new IllegalArgumentException("required system account is invalid");
    }
    return account;
  }
}

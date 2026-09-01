package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.BalancedPostings;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.FifoAllocation;
import com.personal.investment.ledger.domain.FifoCostAllocator;
import com.personal.investment.ledger.domain.FifoLot;
import com.personal.investment.ledger.domain.LedgerAccount;
import com.personal.investment.ledger.domain.LedgerAccountKind;
import com.personal.investment.ledger.domain.LedgerAccountStatus;
import com.personal.investment.ledger.domain.LedgerPostingFact;
import com.personal.investment.ledger.domain.LedgerPostingTemplates;
import com.personal.investment.ledger.domain.LedgerTransactionType;
import com.personal.investment.ledger.domain.Money;
import com.personal.investment.ledger.domain.Posting;
import com.personal.investment.ledger.domain.PostingSide;
import com.personal.investment.ledger.domain.Quantity;
import com.personal.investment.ledger.domain.SystemLedgerAccount;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only exact preview for long-option transactions. It never creates accounts, facts, or projections. */
@Service
public class OptionTradePreviewService {
  private final LedgerCommandAccountPort accountPort;
  private final LedgerTransactionPort transactionPort;
  private final OptionInstrumentPort instrumentPort;
  private final SpotLotPort lotPort;

  public OptionTradePreviewService(LedgerCommandAccountPort accountPort, LedgerTransactionPort transactionPort,
      OptionInstrumentPort instrumentPort, SpotLotPort lotPort) {
    this.accountPort = accountPort;
    this.transactionPort = transactionPort;
    this.instrumentPort = instrumentPort;
    this.lotPort = lotPort;
  }

  @Transactional(readOnly = true)
  public OptionTradePreviewResult preview(String ownerUserId, LedgerTransactionType type, OptionTradeCommand command) {
    if (type != LedgerTransactionType.OPTION_OPEN && type != LedgerTransactionType.OPTION_CLOSE) {
      throw new IllegalArgumentException("option preview only accepts OPTION_OPEN or OPTION_CLOSE");
    }
    validateTrade(ownerUserId, command);
    LedgerAccount cash = activeCash(ownerUserId, command.cashAccountId());
    OptionInstrument option = activeOption(command.instrumentId(), cash.currency(), command.occurredOn());
    return type == LedgerTransactionType.OPTION_OPEN ? open(ownerUserId, cash, option, command)
        : close(ownerUserId, cash, option, command);
  }

  @Transactional(readOnly = true)
  public OptionTradePreviewResult previewExpiry(String ownerUserId, OptionExpiryCommand command) {
    validateExpiry(ownerUserId, command);
    LedgerAccount cash = activeCash(ownerUserId, command.cashAccountId());
    OptionInstrument option = activeOption(command.instrumentId(), cash.currency(), command.occurredOn());
    if (!command.occurredOn().equals(option.maturityDate())) {
      throw new IllegalArgumentException("option expiry must occur on the maturity date");
    }
    LedgerAccount investment = investment(ownerUserId, cash, option.instrumentId());
    List<FifoLot> lots = lotPort.find(ownerUserId, cash.accountId(), option.instrumentId());
    BigDecimal total = lots.stream().map(FifoLot::remainingQuantity).reduce(BigDecimal.ZERO.setScale(8), BigDecimal::add);
    if (total.signum() <= 0 || command.quantity().compareTo(total) != 0) {
      throw new IllegalArgumentException("option expiry quantity must cover the entire open position");
    }
    FifoAllocation allocation = FifoCostAllocator.allocate(lots, command.quantity());
    LedgerAccount expense = activeSystem(ownerUserId, SystemLedgerAccount.OPTION_EXPENSE, cash);
    BalancedPostings postings = LedgerPostingTemplates.optionExpire(investment.accountId(), expense.accountId(),
        Money.of(allocation.allocatedCostCent(), cash.currency()));
    return new OptionTradePreviewResult(cash.currency(), previewPostings(postings, cash,
        Map.of(investment.accountId(), preview(investment), expense.accountId(), preview(expense))), List.of(),
        allocation.allocatedCostCent());
  }

  private OptionTradePreviewResult open(String ownerUserId, LedgerAccount cash, OptionInstrument option,
      OptionTradeCommand command) {
    String investmentCode = investmentCode(cash.accountId(), option.instrumentId());
    LedgerAccount investment = accountPort.findByOwnerAndCode(ownerUserId, investmentCode).orElse(null);
    long premium = premiumCent(command.quantity(), option.contractMultiplier(), command.unitPriceCent());
    BalancedPostings postings = LedgerPostingTemplates.optionOpen(cash.accountId(),
        investment == null ? investmentCode : investment.accountId(), Money.of(premium, cash.currency()),
        Money.of(command.feeCent(), cash.currency()));
    ensureNonNegativeCash(ownerUserId, cash, postings);
    Map<String, PreviewAccount> accounts = Map.of(investment == null ? investmentCode : investment.accountId(),
        new PreviewAccount(investmentCode, "期权投资成本 " + option.instrumentId()));
    return new OptionTradePreviewResult(cash.currency(), previewPostings(postings, cash, accounts),
        investment == null ? List.of(investmentCode) : List.of(), 0);
  }

  private OptionTradePreviewResult close(String ownerUserId, LedgerAccount cash, OptionInstrument option,
      OptionTradeCommand command) {
    LedgerAccount investment = investment(ownerUserId, cash, option.instrumentId());
    FifoAllocation allocation = FifoCostAllocator.allocate(lotPort.find(ownerUserId, cash.accountId(),
        option.instrumentId()), command.quantity());
    long premium = premiumCent(command.quantity(), option.contractMultiplier(), command.unitPriceCent());
    LedgerAccount fee = activeSystem(ownerUserId, SystemLedgerAccount.FEE_EXPENSE, cash);
    LedgerAccount pnl = activeSystem(ownerUserId, SystemLedgerAccount.REALIZED_PNL, cash);
    BalancedPostings postings = LedgerPostingTemplates.optionClose(cash.accountId(), investment.accountId(),
        fee.accountId(), pnl.accountId(), Money.of(premium, cash.currency()),
        Money.of(allocation.allocatedCostCent(), cash.currency()), Money.of(command.feeCent(), cash.currency()));
    ensureNonNegativeCash(ownerUserId, cash, postings);
    return new OptionTradePreviewResult(cash.currency(), previewPostings(postings, cash,
        Map.of(investment.accountId(), preview(investment), fee.accountId(), preview(fee), pnl.accountId(), preview(pnl))),
        List.of(), allocation.allocatedCostCent());
  }

  private LedgerAccount activeCash(String ownerUserId, String accountId) {
    LedgerAccount cash = accountPort.findByIdAndOwner(accountId, ownerUserId)
        .orElseThrow(() -> new IllegalArgumentException("cash account was not found"));
    if (cash.accountKind() != LedgerAccountKind.ASSET_CASH || cash.status() != LedgerAccountStatus.ACTIVE) {
      throw new IllegalArgumentException("cash account must be active");
    }
    return cash;
  }

  private OptionInstrument activeOption(String instrumentId, CurrencyCode currency, java.time.LocalDate occurredOn) {
    OptionInstrument option = instrumentPort.findById(instrumentId)
        .orElseThrow(() -> new IllegalArgumentException("option contract was not found or is incomplete"));
    if (option.currency() != currency) {
      throw new IllegalArgumentException("option currency must match the selected cash account");
    }
    if (occurredOn.isAfter(option.maturityDate())) {
      throw new IllegalArgumentException("option open or close must occur on or before maturity");
    }
    return option;
  }

  private LedgerAccount investment(String ownerUserId, LedgerAccount cash, String instrumentId) {
    LedgerAccount account = accountPort.findByOwnerAndCode(ownerUserId, investmentCode(cash.accountId(), instrumentId))
        .orElseThrow(() -> new IllegalArgumentException("option investment account was not found"));
    if (account.accountKind() != LedgerAccountKind.ASSET_INVESTMENT || account.status() != LedgerAccountStatus.ACTIVE
        || account.currency() != cash.currency()) {
      throw new IllegalStateException("option investment account is invalid");
    }
    return account;
  }

  private LedgerAccount activeSystem(String ownerUserId, SystemLedgerAccount type, LedgerAccount cash) {
    LedgerAccount account = accountPort.findByOwnerAndCode(ownerUserId, type.accountCode(cash.currency()))
        .orElseThrow(() -> new IllegalStateException("required system account was not provisioned"));
    if (account.accountKind() != type.accountKind() || account.status() != LedgerAccountStatus.ACTIVE
        || account.currency() != cash.currency()) {
      throw new IllegalStateException("required system account is invalid");
    }
    return account;
  }

  private void ensureNonNegativeCash(String ownerUserId, LedgerAccount cash, BalancedPostings proposed) {
    long balance = 0;
    for (LedgerPostingFact fact : transactionPort.findPostingFactsByOwner(ownerUserId)) {
      if (fact.accountId().equals(cash.accountId())) {
        balance = apply(balance, fact.side(), fact.amount());
      }
    }
    for (Posting posting : proposed.postings()) {
      if (posting.accountId().equals(cash.accountId())) {
        balance = apply(balance, posting.side(), posting.amount());
      }
    }
    if (balance < 0) {
      throw new InsufficientBalanceException(cash.accountId());
    }
  }

  private static List<PreviewPosting> previewPostings(BalancedPostings postings, LedgerAccount cash,
      Map<String, PreviewAccount> related) {
    return postings.postings().stream().map(posting -> {
      PreviewAccount account = posting.accountId().equals(cash.accountId()) ? preview(cash) : related.get(posting.accountId());
      if (account == null) {
        throw new IllegalStateException("option preview account metadata is missing");
      }
      return new PreviewPosting(account.accountCode(), account.displayName(), posting.side(), posting.amount().cent(),
          posting.amount().currency());
    }).toList();
  }

  private static long premiumCent(BigDecimal quantity, long multiplier, long priceCent) {
    try {
      return quantity.multiply(BigDecimal.valueOf(multiplier)).multiply(BigDecimal.valueOf(priceCent))
          .setScale(0, RoundingMode.UNNECESSARY).longValueExact();
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("option premium is not representable in whole minor units", exception);
    }
  }

  private static void validateTrade(String ownerUserId, OptionTradeCommand command) {
    if (ownerUserId == null || ownerUserId.isBlank() || command == null || command.cashAccountId() == null
        || command.cashAccountId().isBlank() || command.instrumentId() == null || command.instrumentId().isBlank()
        || command.occurredOn() == null || command.unitPriceCent() <= 0 || command.feeCent() < 0) {
      throw new IllegalArgumentException("option trade command is invalid");
    }
    Quantity.of(command.quantity());
    if (command.note() != null && command.note().length() > 1_000) {
      throw new IllegalArgumentException("note exceeds 1000 characters");
    }
  }

  private static void validateExpiry(String ownerUserId, OptionExpiryCommand command) {
    if (ownerUserId == null || ownerUserId.isBlank() || command == null || command.cashAccountId() == null
        || command.cashAccountId().isBlank() || command.instrumentId() == null || command.instrumentId().isBlank()
        || command.occurredOn() == null || command.expiryOutcome() != OptionExpiryOutcome.WORTHLESS) {
      throw new IllegalArgumentException("option expiry command requires explicit WORTHLESS confirmation");
    }
    Quantity.of(command.quantity());
    if (command.note() != null && command.note().length() > 1_000) {
      throw new IllegalArgumentException("note exceeds 1000 characters");
    }
  }

  private static long apply(long balance, PostingSide side, Money amount) {
    try {
      return side == PostingSide.DEBIT ? Math.addExact(balance, amount.cent())
          : Math.subtractExact(balance, amount.cent());
    } catch (ArithmeticException exception) {
      throw new IllegalStateException("cash balance overflow", exception);
    }
  }

  private static String investmentCode(String cashAccountId, String instrumentId) {
    return "INV:" + cashAccountId + ":" + instrumentId;
  }

  private static PreviewAccount preview(LedgerAccount account) {
    return new PreviewAccount(account.accountCode(), account.displayName());
  }

  private record PreviewAccount(String accountCode, String displayName) {
  }
}

package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.BalancedPostings;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.FifoAllocation;
import com.personal.investment.ledger.domain.FifoCostAllocator;
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
import com.personal.investment.ledger.domain.SpotTradeMath;
import com.personal.investment.ledger.domain.SystemLedgerAccount;
import com.personal.investment.ledger.domain.TradableInstrument;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only simulation of S1 spot commands. It never creates IDs, accounts, facts, or projections. */
@Service
public class SpotTradePreviewService {
  private final LedgerCommandAccountPort accountPort;
  private final LedgerTransactionPort transactionPort;
  private final SpotInstrumentPort instrumentPort;
  private final SpotLotPort lotPort;

  public SpotTradePreviewService(LedgerCommandAccountPort accountPort, LedgerTransactionPort transactionPort,
      SpotInstrumentPort instrumentPort, SpotLotPort lotPort) {
    this.accountPort = accountPort;
    this.transactionPort = transactionPort;
    this.instrumentPort = instrumentPort;
    this.lotPort = lotPort;
  }

  @Transactional(readOnly = true)
  public SpotTradePreviewResult preview(String ownerUserId, LedgerTransactionType type, SpotTradeCommand command) {
    if (type != LedgerTransactionType.TRADE_BUY && type != LedgerTransactionType.TRADE_SELL) {
      throw new IllegalArgumentException("spot preview only accepts TRADE_BUY or TRADE_SELL");
    }
    validateCommand(ownerUserId, command);
    LedgerAccount cash = activeCash(ownerUserId, command.cashAccountId());
    TradableInstrument instrument = instrumentPort.findById(command.instrumentId())
        .orElseThrow(() -> new IllegalArgumentException("instrument was not found"));
    if (instrument.nativeCurrency() != cash.currency()) {
      throw new IllegalArgumentException("instrument currency must match the selected cash account");
    }
    return type == LedgerTransactionType.TRADE_BUY ? buy(ownerUserId, cash, instrument, command)
        : sell(ownerUserId, cash, instrument, command);
  }

  private SpotTradePreviewResult buy(String ownerUserId, LedgerAccount cash, TradableInstrument instrument,
      SpotTradeCommand command) {
    String investmentCode = investmentCode(cash, instrument);
    LedgerAccount investment = accountPort.findByOwnerAndCode(ownerUserId, investmentCode).orElse(null);
    long grossCostCent = SpotTradeMath.grossCostCent(command.quantity(), command.unitPriceCent());
    BalancedPostings postings = LedgerPostingTemplates.spotBuy(cash.accountId(),
        investment == null ? investmentCode : investment.accountId(), Money.of(grossCostCent, cash.currency()),
        Money.of(command.feeCent(), cash.currency()));
    ensureNonNegativeCash(ownerUserId, cash, postings);
    return new SpotTradePreviewResult(cash.currency(), previewPostings(postings, cash,
        Map.of(investment == null ? investmentCode : investment.accountId(),
            new PreviewAccount(investmentCode, "投资成本 " + instrument.instrumentId()))),
        investment == null ? List.of(investmentCode) : List.of(), 0);
  }

  private SpotTradePreviewResult sell(String ownerUserId, LedgerAccount cash, TradableInstrument instrument,
      SpotTradeCommand command) {
    String investmentCode = investmentCode(cash, instrument);
    LedgerAccount investment = accountPort.findByOwnerAndCode(ownerUserId, investmentCode)
        .orElseThrow(() -> new IllegalArgumentException("investment account was not found"));
    LedgerAccount fee = activeSystem(ownerUserId, SystemLedgerAccount.FEE_EXPENSE, cash);
    LedgerAccount pnl = activeSystem(ownerUserId, SystemLedgerAccount.REALIZED_PNL, cash);
    FifoAllocation allocation = FifoCostAllocator.allocate(lotPort.find(ownerUserId, cash.accountId(),
        instrument.instrumentId()), command.quantity());
    long grossProceedsCent = SpotTradeMath.grossCostCent(command.quantity(), command.unitPriceCent());
    BalancedPostings postings = LedgerPostingTemplates.spotSell(cash.accountId(), investment.accountId(),
        fee.accountId(), pnl.accountId(), Money.of(grossProceedsCent, cash.currency()),
        Money.of(allocation.allocatedCostCent(), cash.currency()), Money.of(command.feeCent(), cash.currency()));
    ensureNonNegativeCash(ownerUserId, cash, postings);
    Map<String, PreviewAccount> accounts = Map.of(investment.accountId(), new PreviewAccount(investment.accountCode(),
        investment.displayName()), fee.accountId(), new PreviewAccount(fee.accountCode(), fee.displayName()),
        pnl.accountId(), new PreviewAccount(pnl.accountCode(), pnl.displayName()));
    return new SpotTradePreviewResult(cash.currency(), previewPostings(postings, cash, accounts), List.of(),
        allocation.allocatedCostCent());
  }

  private List<PreviewPosting> previewPostings(BalancedPostings postings, LedgerAccount cash,
      Map<String, PreviewAccount> relatedAccounts) {
    return postings.postings().stream().map(posting -> {
      PreviewAccount account = posting.accountId().equals(cash.accountId())
          ? new PreviewAccount(cash.accountCode(), cash.displayName()) : relatedAccounts.get(posting.accountId());
      if (account == null) {
        throw new IllegalStateException("preview account metadata is missing");
      }
      return new PreviewPosting(account.accountCode(), account.displayName(), posting.side(), posting.amount().cent(),
          posting.amount().currency());
    }).toList();
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

  private LedgerAccount activeCash(String ownerUserId, String accountId) {
    LedgerAccount account = accountPort.findByIdAndOwner(accountId, ownerUserId)
        .orElseThrow(() -> new IllegalArgumentException("cash account was not found"));
    if (account.accountKind() != LedgerAccountKind.ASSET_CASH || account.status() != LedgerAccountStatus.ACTIVE) {
      throw new IllegalArgumentException("cash account must be active");
    }
    return account;
  }

  private LedgerAccount activeSystem(String ownerUserId, SystemLedgerAccount systemAccount, LedgerAccount cash) {
    LedgerAccount account = accountPort.findByOwnerAndCode(ownerUserId, systemAccount.accountCode(cash.currency()))
        .orElseThrow(() -> new IllegalArgumentException("required system account was not provisioned"));
    if (account.accountKind() != systemAccount.accountKind() || account.status() != LedgerAccountStatus.ACTIVE
        || account.currency() != cash.currency()) {
      throw new IllegalArgumentException("required system account is invalid");
    }
    return account;
  }

  private static String investmentCode(LedgerAccount cash, TradableInstrument instrument) {
    return "INV:" + cash.accountId() + ":" + instrument.instrumentId();
  }

  private static long apply(long balance, PostingSide side, Money amount) {
    try {
      return side == PostingSide.DEBIT ? Math.addExact(balance, amount.cent())
          : Math.subtractExact(balance, amount.cent());
    } catch (ArithmeticException exception) {
      throw new IllegalStateException("cash balance overflow", exception);
    }
  }

  private static void validateCommand(String ownerUserId, SpotTradeCommand command) {
    if (ownerUserId == null || ownerUserId.isBlank()) {
      throw new IllegalArgumentException("ownerUserId must not be blank");
    }
    Objects.requireNonNull(command, "command must not be null");
    if (command.cashAccountId() == null || command.cashAccountId().isBlank()
        || command.instrumentId() == null || command.instrumentId().isBlank() || command.occurredOn() == null
        || command.unitPriceCent() <= 0 || command.feeCent() < 0) {
      throw new IllegalArgumentException("spot trade command is invalid");
    }
    Quantity.of(command.quantity());
  }

  private record PreviewAccount(String accountCode, String displayName) {
  }
}

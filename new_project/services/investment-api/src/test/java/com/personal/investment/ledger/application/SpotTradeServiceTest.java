package com.personal.investment.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.FifoLot;
import com.personal.investment.ledger.domain.InsufficientPositionException;
import com.personal.investment.ledger.domain.LedgerAccount;
import com.personal.investment.ledger.domain.LedgerAccountKind;
import com.personal.investment.ledger.domain.LedgerPostingFact;
import com.personal.investment.ledger.domain.LedgerTransaction;
import com.personal.investment.ledger.domain.LedgerTransactionType;
import com.personal.investment.ledger.domain.Money;
import com.personal.investment.ledger.domain.SystemLedgerAccount;
import com.personal.investment.ledger.domain.TradableInstrument;
import com.personal.investment.ledger.domain.TradableInstrumentType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SpotTradeServiceTest {
  private static final String OWNER = "01K8D43J4YFN7X9R2B6C8M0V3P";
  private static final String CASH = "01K8D43J4YFN7X9R2B6C8M0V3C";
  private static final String QQQ = "01K8D43J4YFN7X9R2B6C8M0V3Q";

  @Test
  void capitalizesBuyFeeThenUsesFifoCostForSellAndRealizedPnl() {
    Fixture fixture = fundedFixture(10_000);

    SpotTradeResult buy = fixture.service.buy(OWNER, command("2", 1_000, 5));
    SpotTradeResult sell = fixture.service.sell(OWNER, command("1", 1_100, 10));

    assertThat(buy.transaction().transactionType()).isEqualTo(LedgerTransactionType.TRADE_BUY);
    assertThat(buy.transaction().tradeDetails()).singleElement().satisfies(detail -> {
      assertThat(detail.feeCent()).isEqualTo(5);
      assertThat(detail.unitPriceCent()).isEqualTo(1_000);
    });
    assertThat(fixture.accounts.all()).anyMatch(account -> account.accountKind() == LedgerAccountKind.ASSET_INVESTMENT
        && account.accountCode().equals("INV:" + CASH + ":" + QQQ));
    assertThat(sell.allocatedCostCent()).isEqualTo(1_002);
    assertThat(sell.netRealizedPnlCent()).isEqualTo(88);
    assertThat(sell.transaction().postings()).extracting(posting -> posting.amount().cent())
        .containsExactly(1_090L, 10L, 1_002L, 98L);
    assertThat(fixture.lots.find(OWNER, CASH, QQQ)).singleElement().satisfies(lot -> {
      assertThat(lot.remainingQuantity()).isEqualByComparingTo("1");
      assertThat(lot.remainingCostCent()).isEqualTo(1_003);
    });
  }

  @Test
  void rejectsInsufficientCashAndOversellWithoutAppendingNewFactsOrChangingLots() {
    Fixture unfunded = fundedFixture(0);
    assertThatThrownBy(() -> unfunded.service.buy(OWNER, command("1", 1_000, 0)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("INSUFFICIENT_BALANCE");
    assertThat(unfunded.transactions.transactions()).isEmpty();

    Fixture fixture = fundedFixture(10_000);
    fixture.service.buy(OWNER, command("1", 1_000, 0));
    assertThatThrownBy(() -> fixture.service.sell(OWNER, command("1.00000001", 1_000, 0)))
        .isInstanceOf(InsufficientPositionException.class)
        .hasMessageContaining("INSUFFICIENT_POSITION");
    assertThat(fixture.transactions.transactions()).hasSize(2);
    assertThat(fixture.lots.find(OWNER, CASH, QQQ)).singleElement()
        .satisfies(lot -> assertThat(lot.remainingQuantity()).isEqualByComparingTo("1"));
  }

  @Test
  void previewUsesTheSameBalanceRulesWithoutCreatingAccountsFactsOrLots() {
    Fixture fixture = fundedFixture(10_000);
    SpotTradePreviewService preview = new SpotTradePreviewService(fixture.accounts, fixture.transactions,
        instrumentId -> QQQ.equals(instrumentId)
            ? Optional.of(new TradableInstrument(QQQ, TradableInstrumentType.ETF, CurrencyCode.USD))
            : Optional.empty(), fixture.lots);

    SpotTradePreviewResult result = preview.preview(OWNER, LedgerTransactionType.TRADE_BUY,
        command("2", 1_000, 5));

    assertThat(result.postings()).extracting(PreviewPosting::amountCent).containsExactly(2_005L, 2_005L);
    assertThat(result.accountProvisioning()).containsExactly("INV:" + CASH + ":" + QQQ);
    assertThat(fixture.transactions.transactions()).hasSize(1);
    assertThat(fixture.accounts.all()).noneMatch(account -> account.accountKind() == LedgerAccountKind.ASSET_INVESTMENT);
    assertThat(fixture.lots.find(OWNER, CASH, QQQ)).isEmpty();
  }

  @Test
  void cashPreviewUsesTheSameBalanceRulesWithoutAppendingFacts() {
    Fixture fixture = fundedFixture(1_000);
    CashTransactionPreviewService preview = new CashTransactionPreviewService(fixture.accounts, fixture.transactions);

    CashTransactionPreviewResult result = preview.preview(OWNER, LedgerTransactionType.EXTERNAL_WITHDRAWAL,
        CASH, null, 1_000);

    assertThat(result.currency()).isEqualTo(CurrencyCode.USD);
    assertThat(result.postings()).extracting(PreviewPosting::amountCent).containsExactly(1_000L, 1_000L);
    assertThat(fixture.transactions.transactions()).hasSize(1);
    assertThatThrownBy(() -> preview.preview(OWNER, LedgerTransactionType.EXTERNAL_WITHDRAWAL,
        CASH, null, 1_001)).isInstanceOf(IllegalStateException.class).hasMessageContaining("INSUFFICIENT_BALANCE");
    assertThat(fixture.transactions.transactions()).hasSize(1);
  }

  private static SpotTradeCommand command(String quantity, long unitPriceCent, long feeCent) {
    return new SpotTradeCommand(CASH, QQQ, LocalDate.of(2026, 7, 26), new BigDecimal(quantity), unitPriceCent,
        feeCent, "手工现货交易");
  }

  private static Fixture fundedFixture(long fundingCent) {
    InMemoryAccountPort accounts = new InMemoryAccountPort();
    LedgerAccount cash = LedgerAccount.newCash(CASH, OWNER, "美元现金", CurrencyCode.USD);
    accounts.add(cash);
    for (SystemLedgerAccount account : SystemLedgerAccount.values()) {
      accounts.add(LedgerAccount.newSystem("system-" + account.name(), OWNER, account, CurrencyCode.USD));
    }
    InMemoryTransactionPort transactions = new InMemoryTransactionPort();
    SequenceIdGenerator ids = new SequenceIdGenerator();
    LedgerTransactionService cashService = new LedgerTransactionService(accounts, transactions, ids);
    if (fundingCent > 0) {
      cashService.externalFunding(OWNER, new ExternalFundingCommand(CASH, LocalDate.of(2026, 7, 25),
          Money.of(fundingCent, CurrencyCode.USD), null));
    }
    InMemorySpotLots lots = new InMemorySpotLots();
    SpotTradeService service = new SpotTradeService(accounts, accounts, transactions,
        instrumentId -> QQQ.equals(instrumentId)
            ? Optional.of(new TradableInstrument(QQQ, TradableInstrumentType.ETF, CurrencyCode.USD))
            : Optional.empty(), lots, ids);
    return new Fixture(service, accounts, transactions, lots);
  }

  private record Fixture(SpotTradeService service, InMemoryAccountPort accounts,
                         InMemoryTransactionPort transactions, InMemorySpotLots lots) {
  }

  private static final class InMemoryAccountPort implements LedgerCommandAccountPort, LedgerAccountPort {
    private final Map<String, LedgerAccount> accounts = new HashMap<>();

    void add(LedgerAccount account) {
      accounts.put(account.accountId(), account);
    }

    @Override
    public void insert(LedgerAccount account) {
      accounts.put(account.accountId(), account);
    }

    @Override
    public void insertSystemIfAbsent(LedgerAccount account) {
      accounts.values().stream().filter(existing -> existing.ownerUserId().equals(account.ownerUserId())
          && existing.accountCode().equals(account.accountCode())).findFirst().orElseGet(() -> {
            accounts.put(account.accountId(), account);
            return account;
          });
    }

    @Override
    public List<LedgerAccount> findCashAccountsByOwner(String ownerUserId) {
      return accounts.values().stream().filter(account -> account.ownerUserId().equals(ownerUserId)
          && account.accountKind() == LedgerAccountKind.ASSET_CASH).toList();
    }

    @Override
    public Optional<LedgerAccount> findByIdAndOwner(String accountId, String ownerUserId) {
      return Optional.ofNullable(accounts.get(accountId)).filter(account -> account.ownerUserId().equals(ownerUserId));
    }

    @Override
    public Optional<LedgerAccount> findByOwnerAndCode(String ownerUserId, String accountCode) {
      return accounts.values().stream().filter(account -> account.ownerUserId().equals(ownerUserId)
          && account.accountCode().equals(accountCode)).findFirst();
    }

    List<LedgerAccount> all() {
      return List.copyOf(accounts.values());
    }
  }

  private static final class InMemoryTransactionPort implements LedgerTransactionPort {
    private final List<LedgerTransaction> transactions = new ArrayList<>();
    private final Map<String, Long> versions = new HashMap<>();

    @Override
    public long lockCurrentLedgerVersion(String ownerUserId, String newLedgerStateId) {
      return versions.getOrDefault(ownerUserId, 0L);
    }

    @Override
    public long reserveNextLedgerVersion(String ownerUserId, long lockedLedgerVersion) {
      long version = lockedLedgerVersion + 1;
      versions.put(ownerUserId, version);
      return version;
    }

    @Override
    public List<LedgerPostingFact> findPostingFactsByOwner(String ownerUserId) {
      return transactions.stream().filter(transaction -> transaction.ownerUserId().equals(ownerUserId))
          .flatMap(transaction -> transaction.postings().stream()).toList();
    }

    @Override
    public void append(LedgerTransaction transaction) {
      transactions.add(transaction);
    }

    List<LedgerTransaction> transactions() {
      return List.copyOf(transactions);
    }
  }

  private static final class InMemorySpotLots implements SpotLotPort {
    private final Map<String, List<FifoLot>> lots = new HashMap<>();

    @Override
    public List<FifoLot> find(String ownerUserId, String cashAccountId, String instrumentId) {
      return List.copyOf(lots.getOrDefault(key(ownerUserId, cashAccountId, instrumentId), List.of()));
    }

    @Override
    public void replace(String ownerUserId, String cashAccountId, String instrumentId, CurrencyCode currency,
        long sourceLedgerVersion, List<FifoLot> updatedLots) {
      lots.put(key(ownerUserId, cashAccountId, instrumentId), List.copyOf(updatedLots));
    }

    private String key(String ownerUserId, String cashAccountId, String instrumentId) {
      return ownerUserId + ":" + cashAccountId + ":" + instrumentId;
    }
  }

  private static final class SequenceIdGenerator implements LedgerIdGenerator {
    private int sequence;

    @Override
    public String next() {
      return "01K8D43J4YFN7X9R2B6C8M0V" + String.format("%02d", sequence++);
    }
  }
}

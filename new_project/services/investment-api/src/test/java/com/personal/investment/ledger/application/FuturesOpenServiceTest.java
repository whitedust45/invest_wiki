package com.personal.investment.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.FuturesLot;
import com.personal.investment.ledger.domain.LedgerAccount;
import com.personal.investment.ledger.domain.LedgerAccountKind;
import com.personal.investment.ledger.domain.LedgerPostingFact;
import com.personal.investment.ledger.domain.LedgerTransaction;
import com.personal.investment.ledger.domain.LedgerTransactionType;
import com.personal.investment.ledger.domain.Money;
import com.personal.investment.ledger.domain.PostingSide;
import com.personal.investment.ledger.domain.SystemLedgerAccount;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FuturesOpenServiceTest {
  private static final String OWNER = "01K8D43J4YFN7X9R2B6C8M0V3P";
  private static final String CASH = "01K8D43J4YFN7X9R2B6C8M0V3C";
  private static final String FUTURE = "01K8D43J4YFN7X9R2B6C8M0V3F";

  @Test
  void opensWholeCffexLotsByLockingAvailableMarginAndSnapshottingContractTerms() {
    Fixture fixture = fixture(60_000);
    FuturesOpenResult result = fixture.service.open(OWNER, new FuturesOpenCommand(CASH, FUTURE,
        LocalDate.of(2026, 7, 27), new BigDecimal("2"), new BigDecimal("5000.25"), 30_000, 100, "开仓"));

    LedgerTransaction transaction = result.transaction();
    assertThat(transaction.transactionType()).isEqualTo(LedgerTransactionType.FUTURES_OPEN);
    assertThat(transaction.tradeDetails()).singleElement().satisfies(detail -> {
      assertThat(detail.positionEffect().name()).isEqualTo("OPEN");
      assertThat(detail.contractMultiplierCent()).isEqualTo(20_000);
      assertThat(detail.deliveryDate()).isEqualTo(LocalDate.of(2026, 8, 21));
      assertThat(detail.pricePoints()).isEqualByComparingTo("5000.25");
    });
    assertThat(transaction.postings()).extracting(LedgerPostingFact::side)
        .containsExactly(PostingSide.DEBIT, PostingSide.CREDIT, PostingSide.DEBIT, PostingSide.CREDIT);
    assertThat(fixture.positions.replacedLots).singleElement().satisfies(lot -> {
      assertThat(lot.remainingQuantity()).isEqualByComparingTo("2");
      assertThat(lot.remainingInitialMarginCent()).isEqualTo(30_000);
      assertThat(lot.lastSettlementPricePoints()).isEqualByComparingTo("5000.25");
    });
  }

  @Test
  void rejectsOpenWhenAvailableMarginIsInsufficient() {
    Fixture fixture = fixture(29_999);

    assertThatIllegalStateException().isThrownBy(() -> fixture.service.open(OWNER, new FuturesOpenCommand(CASH,
        FUTURE, LocalDate.of(2026, 7, 27), new BigDecimal("1"), new BigDecimal("5000"), 30_000, 0, null)))
        .withMessageContaining("INSUFFICIENT_BALANCE");
  }

  @Test
  void previewsWithoutAppendingFactsOrChangingFutureLots() {
    Fixture fixture = fixture(60_000);

    FuturesOpenPreviewResult preview = fixture.service.preview(OWNER, new FuturesOpenCommand(CASH, FUTURE,
        LocalDate.of(2026, 7, 27), new BigDecimal("2"), new BigDecimal("5000.25"), 30_000, 100, "预览"));

    assertThat(preview.currency()).isEqualTo(CurrencyCode.CNY);
    assertThat(preview.postings()).hasSize(4);
    assertThat(preview.accountProvisioning()).isEmpty();
    assertThat(fixture.transactions.facts).hasSize(2);
    assertThat(fixture.positions.replacedLots).isEmpty();
  }

  @Test
  void rejectsFractionalLotsAndOpeningOnOrAfterMaturity() {
    Fixture fixture = fixture(60_000);

    assertThatIllegalArgumentException().isThrownBy(() -> fixture.service.preview(OWNER, new FuturesOpenCommand(CASH,
        FUTURE, LocalDate.of(2026, 7, 27), new BigDecimal("1.5"), new BigDecimal("5000"), 30_000, 0, null)))
        .withMessageContaining("whole lot");
    assertThatIllegalArgumentException().isThrownBy(() -> fixture.service.preview(OWNER, new FuturesOpenCommand(CASH,
        FUTURE, LocalDate.of(2026, 8, 21), new BigDecimal("1"), new BigDecimal("5000"), 30_000, 0, null)))
        .withMessageContaining("before its maturity");
  }

  @Test
  void rejectsAnInitialMarginThatCannotAllocateAtLeastOneMinorUnitPerLot() {
    Fixture fixture = fixture(60_000);

    assertThatIllegalArgumentException().isThrownBy(() -> fixture.service.preview(OWNER, new FuturesOpenCommand(CASH,
        FUTURE, LocalDate.of(2026, 7, 27), new BigDecimal("2"), new BigDecimal("5000"), 1, 0, null)))
        .withMessageContaining("at least one minor unit");
  }

  private static Fixture fixture(long availableMarginCent) {
    Accounts accounts = new Accounts();
    LedgerAccount cash = LedgerAccount.newCash(CASH, OWNER, "期货现金", CurrencyCode.CNY);
    LedgerAccount available = LedgerAccount.newMarginAvailable("01K8D43J4YFN7X9R2B6C8M0V3A", OWNER, CASH,
        CurrencyCode.CNY);
    LedgerAccount locked = LedgerAccount.newMarginLocked("01K8D43J4YFN7X9R2B6C8M0V3L", OWNER, CASH,
        CurrencyCode.CNY);
    accounts.add(cash);
    accounts.add(available);
    accounts.add(locked);
    accounts.add(LedgerAccount.newSystem("01K8D43J4YFN7X9R2B6C8M0V3E", OWNER, SystemLedgerAccount.FEE_EXPENSE,
        CurrencyCode.CNY));
    Transactions transactions = new Transactions();
    transactions.facts.add(new LedgerPostingFact("01K8D43J4YFN7X9R2B6C8M0V41", available.accountId(), 1,
        PostingSide.DEBIT, Money.of(availableMarginCent, CurrencyCode.CNY)));
    transactions.facts.add(new LedgerPostingFact("01K8D43J4YFN7X9R2B6C8M0V42", cash.accountId(), 2,
        PostingSide.DEBIT, Money.of(100_000, CurrencyCode.CNY)));
    Positions positions = new Positions();
    FuturesOpenService service = new FuturesOpenService(accounts, accounts, transactions,
        id -> Optional.of(new FuturesInstrument(FUTURE, CurrencyCode.CNY, LocalDate.of(2026, 8, 21), 20_000)),
        positions, new Ids(), LedgerTransactionEventPort.noop());
    return new Fixture(service, positions, transactions);
  }

  private record Fixture(FuturesOpenService service, Positions positions, Transactions transactions) {
  }

  private static final class Accounts implements LedgerCommandAccountPort, LedgerAccountPort {
    private final Map<String, LedgerAccount> byId = new HashMap<>();
    private final Map<String, LedgerAccount> byCode = new HashMap<>();

    void add(LedgerAccount account) {
      byId.put(account.accountId(), account);
      byCode.put(account.ownerUserId() + ":" + account.accountCode(), account);
    }

    @Override
    public Optional<LedgerAccount> findByIdAndOwner(String accountId, String ownerUserId) {
      return Optional.ofNullable(byId.get(accountId)).filter(account -> account.ownerUserId().equals(ownerUserId));
    }

    @Override
    public Optional<LedgerAccount> findByOwnerAndCode(String ownerUserId, String accountCode) {
      return Optional.ofNullable(byCode.get(ownerUserId + ":" + accountCode));
    }

    @Override
    public void insert(LedgerAccount account) {
      add(account);
    }

    @Override
    public void insertSystemIfAbsent(LedgerAccount account) {
      byId.putIfAbsent(account.accountId(), account);
      byCode.putIfAbsent(account.ownerUserId() + ":" + account.accountCode(), account);
    }

    @Override
    public List<LedgerAccount> findCashAccountsByOwner(String ownerUserId) {
      return List.of();
    }
  }

  private static final class Transactions implements LedgerTransactionPort {
    private final List<LedgerPostingFact> facts = new ArrayList<>();
    private long version;

    @Override
    public long lockCurrentLedgerVersion(String ownerUserId, String newLedgerStateId) {
      return version;
    }

    @Override
    public long reserveNextLedgerVersion(String ownerUserId, long lockedLedgerVersion) {
      version = lockedLedgerVersion + 1;
      return version;
    }

    @Override
    public List<LedgerPostingFact> findPostingFactsByOwner(String ownerUserId) {
      return List.copyOf(facts);
    }

    @Override
    public void append(LedgerTransaction transaction) {
      facts.addAll(transaction.postings());
    }
  }

  private static final class Positions implements FuturesPositionPort {
    private List<FuturesLot> replacedLots = List.of();

    @Override
    public List<FuturesLot> find(String ownerUserId, String lockedMarginAccountId, String instrumentId) {
      return replacedLots;
    }

    @Override
    public void replace(String ownerUserId, String lockedMarginAccountId, String instrumentId, CurrencyCode currency,
        long sourceLedgerVersion, List<FuturesLot> lots) {
      replacedLots = List.copyOf(lots);
    }
  }

  private static final class Ids implements LedgerIdGenerator {
    private int number;

    @Override
    public String next() {
      return "01K8D43J4YFN7X9R2B6C8M0V" + String.format("%02d", number++);
    }
  }
}

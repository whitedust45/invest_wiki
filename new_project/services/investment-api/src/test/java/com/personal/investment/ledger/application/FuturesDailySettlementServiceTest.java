package com.personal.investment.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

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

class FuturesDailySettlementServiceTest {
  private static final String OWNER = "01K8D43J4YFN7X9R2B6C8M0V3P";
  private static final String CASH = "01K8D43J4YFN7X9R2B6C8M0V3C";
  private static final String FUTURE = "01K8D43J4YFN7X9R2B6C8M0V3F";
  private static final String AVAILABLE = "01K8D43J4YFN7X9R2B6C8M0V3A";
  private static final String LOCKED = "01K8D43J4YFN7X9R2B6C8M0V3L";

  @Test
  void settlesAllOpenLotsAgainstTheirLastSettlementPointsAndUpdatesTheirBaseline() {
    Fixture fixture = fixture();

    FuturesDailySettlementResult result = fixture.service.settle(OWNER, new FuturesDailySettlementCommand(CASH,
        FUTURE, LocalDate.of(2026, 7, 26), new BigDecimal("5001"), "手工日结"));

    assertThat(result.transaction().transactionType()).isEqualTo(LedgerTransactionType.FUTURES_DAILY_SETTLEMENT);
    assertThat(result.realizedPnlCent()).isEqualTo(40_000L);
    assertThat(result.transaction().postings()).extracting(posting -> posting.amount().cent())
        .containsExactly(40_000L, 40_000L);
    assertThat(result.transaction().tradeDetails()).singleElement().satisfies(detail -> {
      assertThat(detail.positionEffect().name()).isEqualTo("NONE");
      assertThat(detail.quantity()).isEqualByComparingTo("2");
      assertThat(detail.pricePoints()).isEqualByComparingTo("5001");
    });
    assertThat(fixture.positions.lots).singleElement().satisfies(lot -> {
      assertThat(lot.lastSettlementPricePoints()).isEqualByComparingTo("5001");
      assertThat(lot.lastSettlementOn()).isEqualTo(LocalDate.of(2026, 7, 26));
    });
  }

  @Test
  void permitsZeroPnlAsAnAuditableNonMonetaryFactButRejectsDuplicateSettlementDate() {
    Fixture fixture = fixture();

    FuturesDailySettlementResult result = fixture.service.settle(OWNER, new FuturesDailySettlementCommand(CASH,
        FUTURE, LocalDate.of(2026, 7, 26), new BigDecimal("5000"), null));

    assertThat(result.realizedPnlCent()).isZero();
    assertThat(result.transaction().postings()).isEmpty();
    assertThatIllegalArgumentException().isThrownBy(() -> fixture.service.settle(OWNER,
        new FuturesDailySettlementCommand(CASH, FUTURE, LocalDate.of(2026, 7, 26), new BigDecimal("5001"), null)))
        .withMessageContaining("strictly later");
  }

  @Test
  void previewsDailySettlementWithoutChangingTheLotBaselineOrLedgerVersion() {
    Fixture fixture = fixture();

    FuturesDailySettlementPreviewResult preview = fixture.service.preview(OWNER,
        new FuturesDailySettlementCommand(CASH, FUTURE, LocalDate.of(2026, 7, 26), new BigDecimal("5001"), null));

    assertThat(preview.currency()).isEqualTo(CurrencyCode.CNY);
    assertThat(preview.realizedPnlCent()).isEqualTo(40_000L);
    assertThat(preview.postings()).extracting(PreviewPosting::amountCent).containsExactly(40_000L, 40_000L);
    assertThat(fixture.positions.lots).singleElement().satisfies(lot -> {
      assertThat(lot.lastSettlementPricePoints()).isEqualByComparingTo("5000");
      assertThat(lot.lastSettlementOn()).isEqualTo(LocalDate.of(2026, 7, 25));
    });
  }

  private static Fixture fixture() {
    Accounts accounts = new Accounts();
    accounts.add(LedgerAccount.newCash(CASH, OWNER, "期货现金", CurrencyCode.CNY));
    accounts.add(LedgerAccount.newMarginAvailable(AVAILABLE, OWNER, CASH, CurrencyCode.CNY));
    accounts.add(LedgerAccount.newMarginLocked(LOCKED, OWNER, CASH, CurrencyCode.CNY));
    accounts.add(LedgerAccount.newSystem("01K8D43J4YFN7X9R2B6C8M0V3R", OWNER, SystemLedgerAccount.REALIZED_PNL,
        CurrencyCode.CNY));
    Transactions transactions = new Transactions();
    transactions.facts.add(new LedgerPostingFact("01K8D43J4YFN7X9R2B6C8M0V41", AVAILABLE, 1, PostingSide.DEBIT,
        Money.of(10_000L, CurrencyCode.CNY)));
    Positions positions = new Positions(List.of(new FuturesLot("01K8D43J4YFN7X9R2B6C8M0V3D",
        LocalDate.of(2026, 7, 25), new BigDecimal("2"), new BigDecimal("2"), new BigDecimal("5000"),
        new BigDecimal("5000"), LocalDate.of(2026, 7, 25), 20_000, 6_000, 6_000, CurrencyCode.CNY)));
    FuturesDailySettlementService service = new FuturesDailySettlementService(accounts, transactions,
        id -> Optional.of(new FuturesInstrument(FUTURE, CurrencyCode.CNY, LocalDate.of(2026, 8, 21), 20_000)),
        positions, new Ids(), LedgerTransactionEventPort.noop());
    return new Fixture(service, positions);
  }

  private record Fixture(FuturesDailySettlementService service, Positions positions) {
  }

  private static final class Accounts implements LedgerCommandAccountPort {
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
    private List<FuturesLot> lots;

    private Positions(List<FuturesLot> lots) {
      this.lots = List.copyOf(lots);
    }

    @Override
    public List<FuturesLot> find(String ownerUserId, String lockedMarginAccountId, String instrumentId) {
      return lots;
    }

    @Override
    public void replace(String ownerUserId, String lockedMarginAccountId, String instrumentId, CurrencyCode currency,
        long sourceLedgerVersion, List<FuturesLot> updatedLots) {
      lots = List.copyOf(updatedLots);
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

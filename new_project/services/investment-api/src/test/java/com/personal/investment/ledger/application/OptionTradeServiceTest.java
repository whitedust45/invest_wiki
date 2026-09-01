package com.personal.investment.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.FifoLot;
import com.personal.investment.ledger.domain.LedgerAccount;
import com.personal.investment.ledger.domain.LedgerAccountKind;
import com.personal.investment.ledger.domain.LedgerPostingFact;
import com.personal.investment.ledger.domain.LedgerTransaction;
import com.personal.investment.ledger.domain.LedgerTransactionType;
import com.personal.investment.ledger.domain.Money;
import com.personal.investment.ledger.domain.SystemLedgerAccount;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OptionTradeServiceTest {
  private static final String OWNER = "01K8D43J4YFN7X9R2B6C8M0V3P";
  private static final String CASH = "01K8D43J4YFN7X9R2B6C8M0V3C";
  private static final String OPTION = "01K8D43J4YFN7X9R2B6C8M0V3O";
  private static final LocalDate EXPIRY = LocalDate.of(2026, 8, 21);

  @Test
  void opensAndClosesLongOptionsWithExactContractMultiplierAndFifoCost() {
    Fixture fixture = fundedFixture(100_000);

    OptionTradeResult open = fixture.service.open(OWNER, trade("2", 250, 5));
    OptionTradeResult close = fixture.service.close(OWNER, trade("1", 300, 10));

    assertThat(open.grossPremiumCent()).isEqualTo(50_000L);
    assertThat(open.transaction().transactionType()).isEqualTo(LedgerTransactionType.OPTION_OPEN);
    assertThat(open.transaction().tradeDetails()).singleElement().satisfies(detail -> {
      assertThat(detail.optionContractMultiplier()).isEqualTo(100L);
      assertThat(detail.unitPriceCent()).isEqualTo(250L);
      assertThat(detail.feeCent()).isEqualTo(5L);
    });
    assertThat(close.grossPremiumCent()).isEqualTo(30_000L);
    assertThat(close.allocatedCostCent()).isEqualTo(25_002L);
    assertThat(close.netRealizedPnlCent()).isEqualTo(4_988L);
    assertThat(fixture.lots.find(OWNER, CASH, OPTION)).singleElement().satisfies(lot -> {
      assertThat(lot.remainingQuantity()).isEqualByComparingTo("1");
      assertThat(lot.remainingCostCent()).isEqualTo(25_003L);
    });
  }

  @Test
  void expiresOnlyTheCompleteLongPositionOnMaturityAfterWorthlessConfirmation() {
    Fixture fixture = fundedFixture(100_000);
    fixture.service.open(OWNER, trade("2", 250, 0));

    OptionTradeResult result = fixture.service.expire(OWNER, new OptionExpiryCommand(CASH, OPTION, EXPIRY,
        new BigDecimal("2"), OptionExpiryOutcome.WORTHLESS, "确认无价值"));

    assertThat(result.transaction().transactionType()).isEqualTo(LedgerTransactionType.OPTION_EXPIRE);
    assertThat(result.allocatedCostCent()).isEqualTo(50_000L);
    assertThat(result.transaction().tradeDetails()).singleElement().satisfies(detail -> {
      assertThat(detail.unitPriceCent()).isNull();
      assertThat(detail.pricePoints()).isNull();
      assertThat(detail.optionContractMultiplier()).isEqualTo(100L);
    });
    assertThat(fixture.lots.find(OWNER, CASH, OPTION)).singleElement().satisfies(lot -> {
      assertThat(lot.remainingQuantity()).isZero();
      assertThat(lot.remainingCostCent()).isZero();
    });
  }

  @Test
  void rejectsPartialOrLateWorthlessExpiryWithoutMutatingThePosition() {
    Fixture fixture = fundedFixture(100_000);
    fixture.service.open(OWNER, trade("2", 250, 0));

    assertThatThrownBy(() -> fixture.service.expire(OWNER, new OptionExpiryCommand(CASH, OPTION, EXPIRY,
        new BigDecimal("1"), OptionExpiryOutcome.WORTHLESS, null)))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("entire open position");
    assertThatThrownBy(() -> fixture.service.expire(OWNER, new OptionExpiryCommand(CASH, OPTION,
        EXPIRY.plusDays(1), new BigDecimal("2"), OptionExpiryOutcome.WORTHLESS, null)))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maturity date");
    assertThat(fixture.lots.find(OWNER, CASH, OPTION)).singleElement().satisfies(lot ->
        assertThat(lot.remainingQuantity()).isEqualByComparingTo("2"));
  }

  @Test
  void rejectsAnOverlongNoteConsistentlyInPreviewAndCommit() {
    Fixture fixture = fundedFixture(100_000);
    OptionTradeCommand invalid = new OptionTradeCommand(CASH, OPTION, LocalDate.of(2026, 8, 20),
        new BigDecimal("1"), 250, 0, "x".repeat(1_001));

    assertThatThrownBy(() -> fixture.preview().preview(OWNER, LedgerTransactionType.OPTION_OPEN, invalid))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("note exceeds 1000");
    assertThatThrownBy(() -> fixture.service().open(OWNER, invalid))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("note exceeds 1000");
  }

  private static OptionTradeCommand trade(String quantity, long unitPriceCent, long feeCent) {
    return new OptionTradeCommand(CASH, OPTION, LocalDate.of(2026, 8, 20), new BigDecimal(quantity),
        unitPriceCent, feeCent, "手工期权交易");
  }

  private static Fixture fundedFixture(long fundingCent) {
    Accounts accounts = new Accounts();
    LedgerAccount cash = LedgerAccount.newCash(CASH, OWNER, "美元现金", CurrencyCode.USD);
    accounts.add(cash);
    for (SystemLedgerAccount account : SystemLedgerAccount.values()) {
      accounts.add(LedgerAccount.newSystem("system-" + account.name(), OWNER, account, CurrencyCode.USD));
    }
    Transactions transactions = new Transactions();
    Ids ids = new Ids();
    new LedgerTransactionService(accounts, transactions, ids).externalFunding(OWNER,
        new ExternalFundingCommand(CASH, LocalDate.of(2026, 8, 1), Money.of(fundingCent, CurrencyCode.USD), null));
    Lots lots = new Lots();
    OptionInstrumentPort options = instrumentId -> OPTION.equals(instrumentId)
        ? Optional.of(new OptionInstrument(OPTION, CurrencyCode.USD, EXPIRY, 100L)) : Optional.empty();
    OptionTradeService service = new OptionTradeService(accounts, accounts, transactions, options, lots, ids,
        LedgerTransactionEventPort.noop());
    OptionTradePreviewService preview = new OptionTradePreviewService(accounts, transactions, options, lots);
    return new Fixture(service, preview, lots);
  }

  private record Fixture(OptionTradeService service, OptionTradePreviewService preview, Lots lots) {
  }

  private static final class Accounts implements LedgerCommandAccountPort, LedgerAccountPort {
    private final Map<String, LedgerAccount> values = new HashMap<>();

    void add(LedgerAccount account) {
      values.put(account.accountId(), account);
    }

    @Override
    public void insert(LedgerAccount account) {
      add(account);
    }

    @Override
    public void insertSystemIfAbsent(LedgerAccount account) {
      values.values().stream().filter(existing -> existing.ownerUserId().equals(account.ownerUserId())
          && existing.accountCode().equals(account.accountCode())).findFirst().orElseGet(() -> {
            add(account);
            return account;
          });
    }

    @Override
    public List<LedgerAccount> findCashAccountsByOwner(String ownerUserId) {
      return values.values().stream().filter(account -> account.ownerUserId().equals(ownerUserId)
          && account.accountKind() == LedgerAccountKind.ASSET_CASH).toList();
    }

    @Override
    public Optional<LedgerAccount> findByIdAndOwner(String accountId, String ownerUserId) {
      return Optional.ofNullable(values.get(accountId)).filter(account -> account.ownerUserId().equals(ownerUserId));
    }

    @Override
    public Optional<LedgerAccount> findByOwnerAndCode(String ownerUserId, String accountCode) {
      return values.values().stream().filter(account -> account.ownerUserId().equals(ownerUserId)
          && account.accountCode().equals(accountCode)).findFirst();
    }
  }

  private static final class Transactions implements LedgerTransactionPort {
    private final List<LedgerTransaction> transactions = new ArrayList<>();
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
      return transactions.stream().filter(transaction -> transaction.ownerUserId().equals(ownerUserId))
          .flatMap(transaction -> transaction.postings().stream()).toList();
    }

    @Override
    public void append(LedgerTransaction transaction) {
      transactions.add(transaction);
    }
  }

  private static final class Lots implements SpotLotPort {
    private List<FifoLot> lots = List.of();

    @Override
    public List<FifoLot> find(String ownerUserId, String cashAccountId, String instrumentId) {
      return lots;
    }

    @Override
    public void replace(String ownerUserId, String cashAccountId, String instrumentId, CurrencyCode currency,
        long sourceLedgerVersion, List<FifoLot> updatedLots) {
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

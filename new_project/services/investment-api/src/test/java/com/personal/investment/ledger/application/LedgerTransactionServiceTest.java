package com.personal.investment.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.LedgerAccount;
import com.personal.investment.ledger.domain.LedgerTransaction;
import com.personal.investment.ledger.domain.LedgerTransactionType;
import com.personal.investment.ledger.domain.Money;
import com.personal.investment.ledger.domain.SystemLedgerAccount;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LedgerTransactionServiceTest {
  private static final String OWNER = "01K8D43J4YFN7X9R2B6C8M0V3P";
  private static final String OTHER_OWNER = "01K8D43J4YFN7X9R2B6C8M0V3Q";

  @Test
  void appendsExternalFundingWithAnOwnerScopedSequentialLedgerVersion() {
    InMemoryCommandAccountPort accounts = standardAccounts(OWNER, CurrencyCode.CNY);
    InMemoryLedgerTransactionPort transactions = new InMemoryLedgerTransactionPort();
    LedgerTransactionService service = new LedgerTransactionService(accounts, transactions,
        new SequenceLedgerIdGenerator("01K8D43J4YFN7X9R2B6C8M0V"));

    LedgerTransaction transaction = service.externalFunding(OWNER, new ExternalFundingCommand(
        "cash-cny", LocalDate.of(2026, 7, 26), Money.of(10_000, CurrencyCode.CNY), "首次入金"));

    assertThat(transaction.transactionType()).isEqualTo(LedgerTransactionType.EXTERNAL_FUNDING);
    assertThat(transaction.ledgerVersion()).isEqualTo(1);
    assertThat(transaction.correctionRootTransactionId()).isEqualTo(transaction.transactionId());
    assertThat(transaction.postings()).extracting(posting -> posting.amount().cent())
        .containsExactly(10_000L, 10_000L);
    assertThat(transactions.transactions()).containsExactly(transaction);
  }

  @Test
  void separatesDividendTaxFromGrossIncomeWhileCashReceivesTheNetAmount() {
    InMemoryCommandAccountPort accounts = standardAccounts(OWNER, CurrencyCode.USD);
    InMemoryLedgerTransactionPort transactions = new InMemoryLedgerTransactionPort();
    List<IncomeDetail> incomeDetails = new ArrayList<>();
    LedgerTransactionService service = new LedgerTransactionService(accounts, transactions,
        new SequenceLedgerIdGenerator("01K8D43J4YFN7X9R2B6C8M0V"), LedgerTransactionEventPort.noop(), incomeDetails::add);

    LedgerTransaction transaction = service.dividend(OWNER, new DividendCommand("cash-usd",
        "01K8D43J4YFN7X9R2B6C8M0V3I", LocalDate.of(2026, 7, 26), LocalDate.of(2026, 7, 25),
        Money.of(1_000, CurrencyCode.USD), Money.of(100, CurrencyCode.USD), null, "手工分红"));

    assertThat(transaction.transactionType()).isEqualTo(LedgerTransactionType.DIVIDEND);
    assertThat(transaction.postings()).extracting(posting -> posting.amount().cent())
        .containsExactly(900L, 100L, 1_000L);
    assertThat(incomeDetails).singleElement().satisfies(detail -> {
      assertThat(detail.transactionId()).isEqualTo(transaction.transactionId());
      assertThat(detail.instrumentId()).isEqualTo("01K8D43J4YFN7X9R2B6C8M0V3I");
      assertThat(detail.grossAmountCent()).isEqualTo(1_000);
      assertThat(detail.taxWithheldCent()).isEqualTo(100);
    });
  }

  @Test
  void validatesPerShareDividendAgainstTheExactEntitlementDatePosition() {
    InMemoryCommandAccountPort accounts = standardAccounts(OWNER, CurrencyCode.USD);
    InMemoryLedgerTransactionPort transactions = new InMemoryLedgerTransactionPort();
    String instrumentId = "01K8D43J4YFN7X9R2B6C8M0V3I";
    SpotInstrumentPort instrumentPort = id -> Optional.of(new com.personal.investment.ledger.domain.TradableInstrument(
        instrumentId, com.personal.investment.ledger.domain.TradableInstrumentType.EQUITY, CurrencyCode.USD));
    SpotHistoryReplayer replayer = new SpotHistoryReplayer() {
      @Override
      public void rebuild(String ownerUserId, long sourceLedgerVersion) {
      }

      @Override
      public BigDecimal quantityAt(String ownerUserId, String requestedInstrumentId, LocalDate asOf) {
        return new BigDecimal("3.00000000");
      }
    };
    LedgerTransactionService service = new LedgerTransactionService(accounts, transactions,
        new SequenceLedgerIdGenerator("01K8D43J4YFN7X9R2B6C8M0V"), LedgerTransactionEventPort.noop(),
        IncomeDetailPort.noop(), instrumentPort, replayer);

    LedgerTransaction transaction = service.dividend(OWNER, new DividendCommand("cash-usd", instrumentId,
        LocalDate.of(2026, 7, 26), LocalDate.of(2026, 7, 25), Money.of(999, CurrencyCode.USD),
        Money.of(0, CurrencyCode.USD), 333L, null));

    assertThat(transaction.transactionType()).isEqualTo(LedgerTransactionType.DIVIDEND);
    assertThatIllegalArgumentException().isThrownBy(() -> service.dividend(OWNER, new DividendCommand("cash-usd",
        instrumentId, LocalDate.of(2026, 7, 26), LocalDate.of(2026, 7, 25), Money.of(1_000, CurrencyCode.USD),
        Money.of(0, CurrencyCode.USD), 333L, null)));
  }

  @Test
  void appendsWithdrawalsTransfersInterestAndFeesWithTheirDedicatedCounterparties() {
    InMemoryCommandAccountPort accounts = standardAccounts(OWNER, CurrencyCode.CNY);
    accounts.add(LedgerAccount.newCash("cash-cny-2", OWNER, "备用现金", CurrencyCode.CNY));
    InMemoryLedgerTransactionPort transactions = new InMemoryLedgerTransactionPort();
    LedgerTransactionService service = new LedgerTransactionService(accounts, transactions,
        new SequenceLedgerIdGenerator("01K8D43J4YFN7X9R2B6C8M0V"));

    service.externalFunding(OWNER, new ExternalFundingCommand(
        "cash-cny", LocalDate.of(2026, 7, 25), Money.of(1_000, CurrencyCode.CNY), null));

    LedgerTransaction withdrawal = service.externalWithdrawal(OWNER, new ExternalFundingCommand(
        "cash-cny", LocalDate.of(2026, 7, 26), Money.of(100, CurrencyCode.CNY), null));
    LedgerTransaction transfer = service.internalTransfer(OWNER, new InternalTransferCommand(
        "cash-cny", "cash-cny-2", LocalDate.of(2026, 7, 26), Money.of(200, CurrencyCode.CNY), null));
    LedgerTransaction interest = service.interest(OWNER, new IncomeCommand("cash-cny",
        LocalDate.of(2026, 7, 26), Money.of(300, CurrencyCode.CNY), Money.of(0, CurrencyCode.CNY), null));
    LedgerTransaction fee = service.fee(OWNER, new FeeCommand("cash-cny", LocalDate.of(2026, 7, 26),
        Money.of(4, CurrencyCode.CNY), null));

    assertThat(withdrawal.transactionType()).isEqualTo(LedgerTransactionType.EXTERNAL_WITHDRAWAL);
    assertThat(transfer.transactionType()).isEqualTo(LedgerTransactionType.INTERNAL_TRANSFER);
    assertThat(interest.transactionType()).isEqualTo(LedgerTransactionType.INTEREST);
    assertThat(fee.transactionType()).isEqualTo(LedgerTransactionType.FEE);
    assertThat(transactions.transactions()).extracting(LedgerTransaction::ledgerVersion)
        .containsExactly(1L, 2L, 3L, 4L, 5L);
    assertThat(withdrawal.postings()).extracting(posting -> posting.amount().cent()).containsExactly(100L, 100L);
    assertThat(transfer.postings()).extracting(posting -> posting.amount().cent()).containsExactly(200L, 200L);
    assertThat(interest.postings()).extracting(posting -> posting.amount().cent()).containsExactly(300L, 300L);
    assertThat(fee.postings()).extracting(posting -> posting.amount().cent()).containsExactly(4L, 4L);
  }

  @Test
  void rejectsCashAccountsOutsideTheAuthenticatedOwner() {
    InMemoryCommandAccountPort accounts = standardAccounts(OTHER_OWNER, CurrencyCode.CNY);
    InMemoryLedgerTransactionPort transactions = new InMemoryLedgerTransactionPort();
    LedgerTransactionService service = new LedgerTransactionService(accounts, transactions,
        new SequenceLedgerIdGenerator("01K8D43J4YFN7X9R2B6C8M0V"));

    assertThatIllegalArgumentException().isThrownBy(() -> service.externalFunding(OWNER,
        new ExternalFundingCommand("cash-cny", LocalDate.of(2026, 7, 26),
            Money.of(10_000, CurrencyCode.CNY), null)));
    assertThat(transactions.transactions()).isEmpty();
  }

  @Test
  void rejectsWithdrawalAndTransferThatWouldMakeCashNegativeWithoutAppendingFacts() {
    InMemoryCommandAccountPort accounts = standardAccounts(OWNER, CurrencyCode.CNY);
    accounts.add(LedgerAccount.newCash("cash-cny-2", OWNER, "备用现金", CurrencyCode.CNY));
    InMemoryLedgerTransactionPort transactions = new InMemoryLedgerTransactionPort();
    LedgerTransactionService service = new LedgerTransactionService(accounts, transactions,
        new SequenceLedgerIdGenerator("01K8D43J4YFN7X9R2B6C8M0V"));

    assertThatIllegalStateException().isThrownBy(() -> service.externalWithdrawal(OWNER,
        new ExternalFundingCommand("cash-cny", LocalDate.of(2026, 7, 26),
            Money.of(1, CurrencyCode.CNY), null)))
        .withMessageContaining("INSUFFICIENT_BALANCE");
    assertThatIllegalStateException().isThrownBy(() -> service.internalTransfer(OWNER,
        new InternalTransferCommand("cash-cny", "cash-cny-2", LocalDate.of(2026, 7, 26),
            Money.of(1, CurrencyCode.CNY), null)))
        .withMessageContaining("INSUFFICIENT_BALANCE");
    assertThat(transactions.transactions()).isEmpty();
  }

  @Test
  void recordsTheAppendedFactWithTheTransactionalEventPortOnlyAfterItExists() {
    InMemoryCommandAccountPort accounts = standardAccounts(OWNER, CurrencyCode.USD);
    InMemoryLedgerTransactionPort transactions = new InMemoryLedgerTransactionPort();
    List<LedgerTransaction> events = new ArrayList<>();
    LedgerTransactionService service = new LedgerTransactionService(accounts, transactions,
        new SequenceLedgerIdGenerator("01K8D43J4YFN7X9R2B6C8M0V"), events::add);

    LedgerTransaction transaction = service.externalFunding(OWNER, new ExternalFundingCommand("cash-usd",
        LocalDate.of(2026, 7, 26), Money.of(666, CurrencyCode.USD), null));

    assertThat(transactions.transactions()).containsExactly(transaction);
    assertThat(events).containsExactly(transaction);
  }

  @Test
  void appendsCashReplacementWithThePreReservedCorrectionContext() {
    InMemoryCommandAccountPort accounts = standardAccounts(OWNER, CurrencyCode.USD);
    InMemoryLedgerTransactionPort transactions = new InMemoryLedgerTransactionPort();
    LedgerTransactionService service = new LedgerTransactionService(accounts, transactions,
        new SequenceLedgerIdGenerator("01K8D43J4YFN7X9R2B6C8M0V"));
    LedgerAppendContext context = new LedgerAppendContext(OWNER, "01K8D43J4YFN7X9R2B6C8M0V31", 2, 9);

    LedgerTransaction replacement = service.externalFundingReplacementByMinorUnit(context, "cash-usd",
        LocalDate.of(2026, 7, 26), 666, "修正后的入金");

    assertThat(replacement.transactionType()).isEqualTo(LedgerTransactionType.EXTERNAL_FUNDING);
    assertThat(replacement.sourceType()).isEqualTo(com.personal.investment.ledger.domain.LedgerSourceType.CORRECTION_REPLACEMENT);
    assertThat(replacement.correctionRootTransactionId()).isEqualTo(context.correctionRootTransactionId());
    assertThat(replacement.revisionNo()).isEqualTo(2);
    assertThat(replacement.ledgerVersion()).isEqualTo(9);
  }

  private InMemoryCommandAccountPort standardAccounts(String ownerUserId, CurrencyCode currency) {
    InMemoryCommandAccountPort port = new InMemoryCommandAccountPort();
    String cashId = currency == CurrencyCode.CNY ? "cash-cny" : "cash-usd";
    port.add(LedgerAccount.newCash(cashId, ownerUserId, "现金", currency));
    for (SystemLedgerAccount account : SystemLedgerAccount.values()) {
      port.add(LedgerAccount.newSystem("system-" + account.name(), ownerUserId, account, currency));
    }
    return port;
  }

  private static final class InMemoryCommandAccountPort implements LedgerCommandAccountPort {
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

  private static final class InMemoryLedgerTransactionPort implements LedgerTransactionPort {
    private final Map<String, Long> versions = new HashMap<>();
    private final List<LedgerTransaction> transactions = new ArrayList<>();

    @Override
    public long lockCurrentLedgerVersion(String ownerUserId, String newLedgerStateId) {
      return versions.getOrDefault(ownerUserId, 0L);
    }

    @Override
    public long reserveNextLedgerVersion(String ownerUserId, long lockedLedgerVersion) {
      long next = Math.addExact(lockedLedgerVersion, 1L);
      versions.put(ownerUserId, next);
      return next;
    }

    @Override
    public List<com.personal.investment.ledger.domain.LedgerPostingFact> findPostingFactsByOwner(
        String ownerUserId) {
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

  private static final class SequenceLedgerIdGenerator implements LedgerIdGenerator {
    private final String prefix;
    private int sequence;

    private SequenceLedgerIdGenerator(String prefix) {
      this.prefix = prefix;
    }

    @Override
    public String next() {
      return prefix + String.format("%02d", sequence++);
    }
  }
}

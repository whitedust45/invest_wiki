package com.personal.investment.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.LedgerAccount;
import com.personal.investment.ledger.domain.LedgerPostingFact;
import com.personal.investment.ledger.domain.LedgerTransaction;
import com.personal.investment.ledger.domain.MarginDirection;
import com.personal.investment.ledger.domain.Money;
import com.personal.investment.ledger.domain.PostingSide;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FuturesMarginServiceTest {
  private static final String OWNER = "01K8D43J4YFN7X9R2B6C8M0V3P";
  private static final String CASH = "01K8D43J4YFN7X9R2B6C8M0V3C";

  @Test
  void previewsWithoutCreatingAccountsThenMovesCashIntoAvailableMargin() {
    InMemoryAccounts accounts = new InMemoryAccounts();
    accounts.add(LedgerAccount.newCash(CASH, OWNER, "期货资金", CurrencyCode.CNY));
    InMemoryTransactions transactions = new InMemoryTransactions();
    transactions.add(funding());
    FuturesMarginService service = new FuturesMarginService(accounts, accounts, transactions,
        new SequenceIds(), LedgerTransactionEventPort.noop());
    FuturesMarginCommand command = new FuturesMarginCommand(CASH, LocalDate.of(2026, 7, 27), MarginDirection.IN,
        Money.of(60_000, CurrencyCode.CNY), "转入保证金");

    FuturesMarginPreviewResult preview = service.preview(OWNER, command);
    assertThat(preview.accountProvisioning()).containsExactly("MRGAV:" + CASH, "MRGLK:" + CASH);
    assertThat(accounts.findByOwnerAndCode(OWNER, "MRGAV:" + CASH)).isEmpty();

    LedgerTransaction transaction = service.move(OWNER, command);
    assertThat(transaction.transactionType().name()).isEqualTo("FUTURES_MARGIN");
    assertThat(transaction.postings()).extracting(LedgerPostingFact::side)
        .containsExactly(PostingSide.DEBIT, PostingSide.CREDIT);
    assertThat(accounts.findByOwnerAndCode(OWNER, "MRGAV:" + CASH)).isPresent();
    assertThat(accounts.findByOwnerAndCode(OWNER, "MRGLK:" + CASH)).isPresent();
  }

  @Test
  void rejectsMarginOutAboveTheAvailableMarginBalance() {
    InMemoryAccounts accounts = new InMemoryAccounts();
    accounts.add(LedgerAccount.newCash(CASH, OWNER, "期货资金", CurrencyCode.CNY));
    InMemoryTransactions transactions = new InMemoryTransactions();
    transactions.add(funding());
    FuturesMarginService service = new FuturesMarginService(accounts, accounts, transactions,
        new SequenceIds(), LedgerTransactionEventPort.noop());
    service.move(OWNER, new FuturesMarginCommand(CASH, LocalDate.of(2026, 7, 27), MarginDirection.IN,
        Money.of(50_000, CurrencyCode.CNY), null));

    assertThatIllegalStateException().isThrownBy(() -> service.move(OWNER, new FuturesMarginCommand(CASH,
        LocalDate.of(2026, 7, 27), MarginDirection.OUT, Money.of(50_001, CurrencyCode.CNY), null)))
        .withMessageContaining("INSUFFICIENT_BALANCE");
  }

  private static LedgerTransaction funding() {
    return LedgerTransaction.original("01K8D43J4YFN7X9R2B6C8M0V31", OWNER,
        com.personal.investment.ledger.domain.LedgerTransactionType.EXTERNAL_FUNDING,
        LocalDate.of(2026, 7, 26), 1, null, List.of(
            new LedgerPostingFact("01K8D43J4YFN7X9R2B6C8M0V41", CASH, 1, PostingSide.DEBIT,
                Money.of(100_000, CurrencyCode.CNY)),
            new LedgerPostingFact("01K8D43J4YFN7X9R2B6C8M0V42", "equity", 2, PostingSide.CREDIT,
                Money.of(100_000, CurrencyCode.CNY))));
  }

  private static final class InMemoryAccounts implements LedgerCommandAccountPort, LedgerAccountPort {
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
      return byId.values().stream().filter(account -> account.ownerUserId().equals(ownerUserId)
          && account.accountKind() == com.personal.investment.ledger.domain.LedgerAccountKind.ASSET_CASH).toList();
    }
  }

  private static final class InMemoryTransactions implements LedgerTransactionPort {
    private final List<LedgerTransaction> transactions = new ArrayList<>();
    private long version = 1;

    void add(LedgerTransaction transaction) {
      transactions.add(transaction);
    }

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

  private static final class SequenceIds implements LedgerIdGenerator {
    private int sequence;

    @Override
    public String next() {
      return "01K8D43J4YFN7X9R2B6C8M0V" + String.format("%02d", sequence++);
    }
  }
}

package com.personal.investment.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.LedgerAccount;
import com.personal.investment.ledger.domain.LedgerAccountKind;
import com.personal.investment.ledger.domain.LedgerAccountStatus;
import com.personal.investment.ledger.domain.LedgerPostingFact;
import com.personal.investment.ledger.domain.LedgerTransaction;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LedgerAccountServiceTest {
  @Test
  void createsOneUserCashAccountAndOnlyTheCurrencyScopedSystemAccounts() {
    InMemoryLedgerAccountPort port = new InMemoryLedgerAccountPort();
    LedgerAccountService service = new LedgerAccountService(port,
        new SequenceLedgerIdGenerator("01K8D43J4YFN7X9R2B6C8M0V"));

    LedgerAccount cash = service.createCashAccount("01K8D43J4YFN7X9R2B6C8M0V3P", "美元现金", CurrencyCode.USD);

    assertThat(cash.accountKind()).isEqualTo(LedgerAccountKind.ASSET_CASH);
    assertThat(cash.accountCode()).startsWith("CASH:");
    assertThat(port.accounts()).hasSize(8);
    assertThat(port.accounts()).filteredOn(account -> account.accountKind() == LedgerAccountKind.EXPENSE_WITHHOLDING_TAX)
        .singleElement()
        .satisfies(account -> assertThat(account.currency()).isEqualTo(CurrencyCode.USD));
    assertThat(port.accounts()).noneMatch(account -> account.accountKind() == LedgerAccountKind.ASSET_INVESTMENT);
  }

  @Test
  void doesNotCreateDuplicateSystemAccountsWhenAddingAnotherCashAccountInSameCurrency() {
    InMemoryLedgerAccountPort port = new InMemoryLedgerAccountPort();
    LedgerAccountService service = new LedgerAccountService(port,
        new SequenceLedgerIdGenerator("01K8D43J4YFN7X9R2B6C8M0V"));
    String ownerUserId = "01K8D43J4YFN7X9R2B6C8M0V3P";

    service.createCashAccount(ownerUserId, "美元现金一", CurrencyCode.USD);
    service.createCashAccount(ownerUserId, "美元现金二", CurrencyCode.USD);

    assertThat(port.accounts()).hasSize(9);
    assertThat(port.findCashAccountsByOwner(ownerUserId)).hasSize(2);
  }

  @Test
  void rejectsBlankNamesBeforePersistence() {
    InMemoryLedgerAccountPort port = new InMemoryLedgerAccountPort();
    LedgerAccountService service = new LedgerAccountService(port,
        new SequenceLedgerIdGenerator("01K8D43J4YFN7X9R2B6C8M0V"));

    assertThatIllegalArgumentException().isThrownBy(() -> service.createCashAccount(
        "01K8D43J4YFN7X9R2B6C8M0V3P", " ", CurrencyCode.CNY));
    assertThat(port.accounts()).isEmpty();
  }

  @Test
  void disablesOnlyAZeroBalanceAccountWithTheExpectedVersionAndNoOpenObligations() {
    String owner = "01K8D43J4YFN7X9R2B6C8M0V3P";
    InMemoryLedgerAccountPort port = new InMemoryLedgerAccountPort();
    LedgerAccount cash = LedgerAccount.newCash("01K8D43J4YFN7X9R2B6C8M0V3C", owner, "美元现金", CurrencyCode.USD);
    port.insert(cash);
    LedgerAccountService service = new LedgerAccountService(port, new InMemoryTransactionPort(),
        new SequenceLedgerIdGenerator("01K8D43J4YFN7X9R2B6C8M0V"));

    LedgerAccount disabled = service.disableCashAccount(owner, cash.accountId(), 0);

    assertThat(disabled.status()).isEqualTo(LedgerAccountStatus.DISABLED);
    assertThat(disabled.version()).isEqualTo(1);
    assertThat(port.findByIdAndOwner(cash.accountId(), owner)).get()
        .satisfies(account -> assertThat(account.status()).isEqualTo(LedgerAccountStatus.DISABLED));
    assertThatIllegalStateException().isThrownBy(() -> service.disableCashAccount(owner, cash.accountId(), 1))
        .withMessageContaining("not active");
  }

  private static final class InMemoryLedgerAccountPort implements LedgerAccountLifecyclePort {
    private final List<LedgerAccount> accounts = new ArrayList<>();

    @Override
    public void insert(LedgerAccount account) {
      accounts.add(account);
    }

    @Override
    public void insertSystemIfAbsent(LedgerAccount account) {
      if (accounts.stream().noneMatch(existing -> existing.ownerUserId().equals(account.ownerUserId())
          && existing.accountCode().equals(account.accountCode()))) {
        accounts.add(account);
      }
    }

    @Override
    public List<LedgerAccount> findCashAccountsByOwner(String ownerUserId) {
      return accounts.stream().filter(account -> account.ownerUserId().equals(ownerUserId)
          && account.accountKind() == LedgerAccountKind.ASSET_CASH).toList();
    }

    @Override
    public Optional<LedgerAccount> findByIdAndOwner(String accountId, String ownerUserId) {
      return accounts.stream().filter(account -> account.accountId().equals(accountId)
          && account.ownerUserId().equals(ownerUserId)).findFirst();
    }

    @Override
    public Optional<LedgerAccount> findByOwnerAndCode(String ownerUserId, String accountCode) {
      return accounts.stream().filter(account -> account.ownerUserId().equals(ownerUserId)
          && account.accountCode().equals(accountCode)).findFirst();
    }

    @Override
    public boolean hasOpenSpotPosition(String ownerUserId, String cashAccountId) {
      return false;
    }

    @Override
    public boolean hasOpenFuturesPosition(String ownerUserId, String cashAccountId) {
      return false;
    }

    @Override
    public boolean hasActiveImportReferencingCashAccount(String ownerUserId, String cashAccountId) {
      return false;
    }

    @Override
    public boolean disableIfCurrentVersion(String ownerUserId, String cashAccountId, long expectedVersion) {
      for (int index = 0; index < accounts.size(); index += 1) {
        LedgerAccount account = accounts.get(index);
        if (account.accountId().equals(cashAccountId) && account.ownerUserId().equals(ownerUserId)
            && account.status() == LedgerAccountStatus.ACTIVE && account.version() == expectedVersion) {
          accounts.set(index, new LedgerAccount(account.accountId(), account.ownerUserId(), account.accountCode(),
              account.accountKind(), account.currency(), account.displayName(), LedgerAccountStatus.DISABLED,
              account.version() + 1));
          return true;
        }
      }
      return false;
    }

    List<LedgerAccount> accounts() {
      return List.copyOf(accounts);
    }
  }

  private static final class InMemoryTransactionPort implements LedgerTransactionPort {
    @Override
    public long lockCurrentLedgerVersion(String ownerUserId, String newLedgerStateId) {
      return 0;
    }

    @Override
    public long reserveNextLedgerVersion(String ownerUserId, long lockedLedgerVersion) {
      return lockedLedgerVersion + 1;
    }

    @Override
    public List<LedgerPostingFact> findPostingFactsByOwner(String ownerUserId) {
      return List.of();
    }

    @Override
    public void append(LedgerTransaction transaction) {
      throw new UnsupportedOperationException();
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

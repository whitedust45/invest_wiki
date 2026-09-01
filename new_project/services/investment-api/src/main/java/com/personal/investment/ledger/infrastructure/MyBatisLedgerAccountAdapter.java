package com.personal.investment.ledger.infrastructure;

import com.personal.investment.ledger.application.LedgerAccountPort;
import com.personal.investment.ledger.application.LedgerAccountLifecyclePort;
import com.personal.investment.ledger.application.LedgerCommandAccountPort;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.LedgerAccount;
import com.personal.investment.ledger.domain.LedgerAccountKind;
import com.personal.investment.ledger.domain.LedgerAccountStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class MyBatisLedgerAccountAdapter implements LedgerAccountLifecyclePort {
  private final LedgerAccountMapper mapper;

  public MyBatisLedgerAccountAdapter(LedgerAccountMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void insert(LedgerAccount account) {
    mapper.insert(toRow(account));
  }

  @Override
  public void insertSystemIfAbsent(LedgerAccount account) {
    mapper.insertSystemIfAbsent(toRow(account));
  }

  @Override
  public List<LedgerAccount> findCashAccountsByOwner(String ownerUserId) {
    return mapper.findCashAccountsByOwner(ownerUserId).stream().map(this::toDomain).toList();
  }

  @Override
  public List<LedgerAccount> findAllAccountsByOwner(String ownerUserId) {
    return mapper.findAllAccountsByOwner(ownerUserId).stream().map(this::toDomain).toList();
  }

  @Override
  public boolean hasAnyAccountByOwner(String ownerUserId) {
    return mapper.hasAnyAccountByOwner(ownerUserId);
  }

  @Override
  public Optional<LedgerAccount> findByIdAndOwner(String accountId, String ownerUserId) {
    return Optional.ofNullable(mapper.findByIdAndOwner(accountId, ownerUserId)).map(this::toDomain);
  }

  @Override
  public Optional<LedgerAccount> findByOwnerAndCode(String ownerUserId, String accountCode) {
    return Optional.ofNullable(mapper.findByOwnerAndCode(ownerUserId, accountCode)).map(this::toDomain);
  }

  @Override
  public boolean hasOpenSpotPosition(String ownerUserId, String cashAccountId) {
    return mapper.hasOpenSpotPosition(ownerUserId, cashAccountId);
  }

  @Override
  public boolean hasOpenFuturesPosition(String ownerUserId, String cashAccountId) {
    return mapper.hasOpenFuturesPosition(ownerUserId, cashAccountId);
  }

  @Override
  public boolean hasActiveImportReferencingCashAccount(String ownerUserId, String cashAccountId) {
    return mapper.hasActiveImportReferencingCashAccount(ownerUserId, cashAccountId);
  }

  @Override
  public boolean disableIfCurrentVersion(String ownerUserId, String cashAccountId, long expectedVersion) {
    return mapper.disableIfCurrentVersion(ownerUserId, cashAccountId, expectedVersion) == 1;
  }

  private LedgerAccountMapper.AccountRow toRow(LedgerAccount account) {
    return new LedgerAccountMapper.AccountRow(account.accountId(), account.ownerUserId(),
        account.accountCode(), account.accountKind().name(), account.currency().name(),
        account.displayName(), account.status().name(), account.version());
  }

  private LedgerAccount toDomain(LedgerAccountMapper.AccountRow row) {
    return new LedgerAccount(row.accountId(), row.ownerUserId(), row.accountCode(),
        LedgerAccountKind.valueOf(row.accountKind()), CurrencyCode.of(row.currency()), row.displayName(),
        LedgerAccountStatus.valueOf(row.status()), row.version());
  }
}

package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.LedgerAccount;
import java.util.List;

public interface LedgerAccountPort {
  void insert(LedgerAccount account);

  void insertSystemIfAbsent(LedgerAccount account);

  List<LedgerAccount> findCashAccountsByOwner(String ownerUserId);

  /** All accounts are needed only by a portable, owner-scoped ledger backup. */
  default List<LedgerAccount> findAllAccountsByOwner(String ownerUserId) {
    return findCashAccountsByOwner(ownerUserId);
  }

  /** Used under the owner ledger-state lock before an empty-workspace restore. */
  default boolean hasAnyAccountByOwner(String ownerUserId) {
    return !findAllAccountsByOwner(ownerUserId).isEmpty();
  }
}

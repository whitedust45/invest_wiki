package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.LedgerAccount;
import java.util.Optional;

/** Account lookup boundary for commands; all lookups are owner scoped. */
public interface LedgerCommandAccountPort {
  Optional<LedgerAccount> findByIdAndOwner(String accountId, String ownerUserId);

  Optional<LedgerAccount> findByOwnerAndCode(String ownerUserId, String accountCode);
}

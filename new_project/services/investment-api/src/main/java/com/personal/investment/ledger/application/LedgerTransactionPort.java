package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.LedgerTransaction;
import com.personal.investment.ledger.domain.LedgerPostingFact;
import java.util.List;

public interface LedgerTransactionPort {
  /** Creates the owner-local state if absent and holds its row lock for the enclosing transaction. */
  long lockCurrentLedgerVersion(String ownerUserId, String newLedgerStateId);

  /** Reserves the next owner-local version after {@link #lockCurrentLedgerVersion}. */
  long reserveNextLedgerVersion(String ownerUserId, long lockedLedgerVersion);

  /** Returns immutable facts for owner-local replay while the owner ledger state is locked. */
  List<LedgerPostingFact> findPostingFactsByOwner(String ownerUserId);

  /** Checked only while the owner ledger-state row is locked before an empty-workspace recovery import. */
  default boolean hasAnyTransactionByOwner(String ownerUserId) {
    return !findPostingFactsByOwner(ownerUserId).isEmpty();
  }

  void append(LedgerTransaction transaction);
}

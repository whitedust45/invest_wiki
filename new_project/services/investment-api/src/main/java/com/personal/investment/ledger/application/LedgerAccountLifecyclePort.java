package com.personal.investment.ledger.application;

/** Persistence queries that must all be true before a user cash account may be stopped. */
public interface LedgerAccountLifecyclePort extends LedgerAccountPort, LedgerCommandAccountPort {
  boolean hasOpenSpotPosition(String ownerUserId, String cashAccountId);

  boolean hasOpenFuturesPosition(String ownerUserId, String cashAccountId);

  boolean hasActiveImportReferencingCashAccount(String ownerUserId, String cashAccountId);

  boolean disableIfCurrentVersion(String ownerUserId, String cashAccountId, long expectedVersion);
}

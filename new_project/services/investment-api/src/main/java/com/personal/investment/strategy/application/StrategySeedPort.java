package com.personal.investment.strategy.application;

public interface StrategySeedPort {
  long countLedgerTransactions(String ownerUserId);

  boolean hasSeedRun(String ownerUserId, String seedName);

  void appendSeedRun(String strategySeedRunId, String ownerUserId, String seedName, byte[] fixtureChecksum);
}

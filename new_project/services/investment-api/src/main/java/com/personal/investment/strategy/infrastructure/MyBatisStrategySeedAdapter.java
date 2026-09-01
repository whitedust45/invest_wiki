package com.personal.investment.strategy.infrastructure;

import com.personal.investment.strategy.application.StrategySeedPort;
import org.springframework.stereotype.Component;

@Component
public class MyBatisStrategySeedAdapter implements StrategySeedPort {
  private final StrategySeedMapper mapper;

  public MyBatisStrategySeedAdapter(StrategySeedMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public long countLedgerTransactions(String ownerUserId) {
    return mapper.countLedgerTransactions(ownerUserId);
  }

  @Override
  public boolean hasSeedRun(String ownerUserId, String seedName) {
    return mapper.countSeedRuns(ownerUserId, seedName) > 0;
  }

  @Override
  public void appendSeedRun(String strategySeedRunId, String ownerUserId, String seedName, byte[] fixtureChecksum) {
    if (mapper.insert(strategySeedRunId, ownerUserId, seedName, fixtureChecksum) != 1) {
      throw new IllegalStateException("strategy seed run was not appended");
    }
  }
}

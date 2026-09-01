package com.personal.investment.ledger.application;

import java.util.List;

public interface FuturesProjectionRebuildPort {
  void replaceOwnerProjection(String ownerUserId, long sourceLedgerVersion, List<FuturesLotProjection> projections);
}

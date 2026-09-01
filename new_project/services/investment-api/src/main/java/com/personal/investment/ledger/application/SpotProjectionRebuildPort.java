package com.personal.investment.ledger.application;

import java.util.List;

public interface SpotProjectionRebuildPort {
  void replaceOwnerProjection(String ownerUserId, long sourceLedgerVersion, List<SpotLotProjection> projections);
}

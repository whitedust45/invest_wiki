package com.personal.investment.ledger.infrastructure;

import com.personal.investment.ledger.application.LedgerSnapshot;
import com.personal.investment.ledger.application.LedgerSnapshotPort;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class MyBatisLedgerSnapshotAdapter implements LedgerSnapshotPort {
  private final LedgerSnapshotMapper mapper;

  public MyBatisLedgerSnapshotAdapter(LedgerSnapshotMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void append(LedgerSnapshot snapshot) {
    if (mapper.insert(new LedgerSnapshotMapper.Row(snapshot.ledgerSnapshotId(), snapshot.ownerUserId(),
        snapshot.asOfDate(), snapshot.sourceLedgerVersion(), snapshot.importExportFileId(),
        snapshot.contentSha256Hex(), snapshot.createdAt())) != 1) {
      throw new IllegalStateException("ledger snapshot was not persisted");
    }
  }

  @Override
  public Optional<LedgerSnapshot> findOwned(String ownerUserId, String ledgerSnapshotId) {
    return Optional.ofNullable(mapper.findOwned(ownerUserId, ledgerSnapshotId)).map(this::snapshot);
  }

  @Override
  public Optional<LedgerSnapshot> findOwnedAtVersion(String ownerUserId, LocalDate asOfDate, long sourceLedgerVersion) {
    return Optional.ofNullable(mapper.findOwnedAtVersion(ownerUserId, asOfDate, sourceLedgerVersion)).map(this::snapshot);
  }

  @Override
  public List<LedgerSnapshot> findOwnedRecent(String ownerUserId, int limit) {
    if (limit < 1 || limit > 100) {
      throw new IllegalArgumentException("snapshot list limit is invalid");
    }
    return mapper.findOwnedRecent(ownerUserId, limit).stream().map(this::snapshot).toList();
  }

  @Override
  public List<String> findOwnersWithLedgerFacts() {
    return mapper.findOwnersWithLedgerFacts();
  }

  private LedgerSnapshot snapshot(LedgerSnapshotMapper.Row row) {
    return new LedgerSnapshot(row.ledgerSnapshotId(), row.ownerUserId(), row.asOfDate(), row.sourceLedgerVersion(),
        row.importExportFileId(), row.contentSha256Hex(), row.createdAt());
  }
}

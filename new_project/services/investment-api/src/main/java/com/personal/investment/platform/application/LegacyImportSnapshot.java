package com.personal.investment.platform.application;

import java.util.List;

/** The sole selected legacy snapshot; JSON has no snapshot ID while SQLite always has one. */
public record LegacyImportSnapshot(String sourceSnapshotId, List<LegacyImportEntry> entries) {
  public LegacyImportSnapshot {
    entries = List.copyOf(entries);
  }
}

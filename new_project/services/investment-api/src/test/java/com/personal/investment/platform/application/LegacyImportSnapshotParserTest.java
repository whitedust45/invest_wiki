package com.personal.investment.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

class LegacyImportSnapshotParserTest {
  private final LegacyImportSnapshotParser parser = new LegacyImportSnapshotParser();

  @Test
  void readsOnlyTheFullDashboardLedgerEntriesArrayFromJson() {
    LegacyImportSnapshot snapshot = parser.parseJson("""
        {"ledger":{"entries":[{"module":"cash","action":"deposit"},{"module":"qqq","action":"buy"}]}}
        """.getBytes(java.nio.charset.StandardCharsets.UTF_8));

    assertThat(snapshot.sourceSnapshotId()).isNull();
    assertThat(snapshot.entries()).extracting(LegacyImportEntry::sourceRow).containsExactly(1, 2);
    assertThat(snapshot.entries().getFirst().module()).isEqualTo("cash");
    assertThat(snapshot.entries().get(1).action()).isEqualTo("buy");
  }

  @Test
  void readsOnlyOneExplicitSnapshotFromTheWhitelistedSqliteTable() throws Exception {
    Path database = Files.createTempFile("legacy-import-", ".sqlite");
    try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
      try (Statement statement = connection.createStatement()) {
        statement.execute("CREATE TABLE snapshots (id INTEGER PRIMARY KEY, created_at TEXT NOT NULL, payload_json TEXT NOT NULL)");
      }
      try (PreparedStatement statement = connection.prepareStatement(
          "INSERT INTO snapshots(id, created_at, payload_json) VALUES (?, ?, ?)")) {
        statement.setLong(1, 17L);
        statement.setString(2, "2026-08-21T00:00:00Z");
        statement.setString(3, "{\"entries\":[{\"module\":\"put\",\"action\":\"expire\"}]}");
        statement.executeUpdate();
      }
    }

    LegacyImportSnapshot snapshot = parser.parseSqlite(Files.readAllBytes(database), "17");

    assertThat(snapshot.sourceSnapshotId()).isEqualTo("17");
    assertThat(snapshot.entries()).singleElement().satisfies(entry -> {
      assertThat(entry.sourceRow()).isEqualTo(1);
      assertThat(entry.module()).isEqualTo("put");
      assertThat(entry.action()).isEqualTo("expire");
    });
  }

  @Test
  void rejectsIncompleteJsonAndMalformedOrMissingSqliteSnapshots() throws Exception {
    assertThatThrownBy(() -> parser.parseJson("{\"entries\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ledger.entries");

    Path database = Files.createTempFile("legacy-import-invalid-", ".sqlite");
    try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
         Statement statement = connection.createStatement()) {
      statement.execute("CREATE TABLE not_snapshots (id INTEGER PRIMARY KEY)");
    }
    assertThatThrownBy(() -> parser.parseSqlite(Files.readAllBytes(database), "1"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("snapshots");
  }
}

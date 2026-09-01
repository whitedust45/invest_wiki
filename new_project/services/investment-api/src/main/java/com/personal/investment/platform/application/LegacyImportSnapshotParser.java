package com.personal.investment.platform.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Structural reader for the legacy export only. SQLite access uses one hard-coded, parameterised SELECT and never
 * executes SQL supplied by an uploaded object.
 */
public final class LegacyImportSnapshotParser {
  private final ObjectMapper objectMapper;

  public LegacyImportSnapshotParser() {
    this(new ObjectMapper());
  }

  LegacyImportSnapshotParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public LegacyImportSnapshot parseJson(byte[] content) {
    JsonNode root = readTree(content, "legacy JSON");
    if (!root.isObject() || !root.path("ledger").isObject()) {
      throw new IllegalArgumentException("legacy JSON ledger.entries must be present under a ledger object");
    }
    return new LegacyImportSnapshot(null, entries(root.path("ledger").path("entries"), "legacy JSON ledger.entries"));
  }

  public LegacyImportSnapshot parseSqlite(byte[] content, String sourceSnapshotId) {
    if (sourceSnapshotId == null || !sourceSnapshotId.matches("[1-9][0-9]*")) {
      throw new IllegalArgumentException("SQLite snapshotId must be a positive decimal identifier");
    }
    Path temporaryDatabase = null;
    try {
      temporaryDatabase = Files.createTempFile("legacy-import-", ".sqlite");
      Files.write(temporaryDatabase, content);
      try (Connection connection = DriverManager.getConnection(readOnlyJdbcUrl(temporaryDatabase))) {
        requireSnapshotShape(connection);
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT payload_json FROM snapshots WHERE id = ?")) {
          statement.setLong(1, parseSnapshotId(sourceSnapshotId));
          try (ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
              throw new IllegalArgumentException("selected SQLite snapshots row was not found");
            }
            JsonNode root = readTree(result.getString(1).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "SQLite snapshots.payload_json");
            if (!root.isObject()) {
              throw new IllegalArgumentException("SQLite snapshots.payload_json must be an object");
            }
            return new LegacyImportSnapshot(sourceSnapshotId, entries(root.path("entries"),
                "SQLite snapshots.payload_json.entries"));
          }
        }
      }
    } catch (IOException | SQLException exception) {
      throw new IllegalArgumentException("legacy SQLite snapshot could not be read safely", exception);
    } finally {
      if (temporaryDatabase != null) {
        try {
          Files.deleteIfExists(temporaryDatabase);
        } catch (IOException ignored) {
          // Runtime cleanup is best-effort; an orphan scavenger handles any failed temporary-file deletion.
        }
      }
    }
  }

  private JsonNode readTree(byte[] content, String source) {
    if (content == null || content.length == 0) {
      throw new IllegalArgumentException(source + " must not be empty");
    }
    try {
      return objectMapper.readTree(content);
    } catch (IOException exception) {
      throw new IllegalArgumentException(source + " is not valid JSON", exception);
    }
  }

  private static List<LegacyImportEntry> entries(JsonNode node, String field) {
    if (!node.isArray()) {
      throw new IllegalArgumentException(field + " must be an array");
    }
    List<LegacyImportEntry> result = new ArrayList<>();
    int sourceRow = 1;
    for (JsonNode item : node) {
      if (!item.isObject()) {
        throw new IllegalArgumentException(field + " row " + sourceRow + " must be an object");
      }
      result.add(new LegacyImportEntry(sourceRow, requiredText(item, "module", sourceRow),
          requiredText(item, "action", sourceRow), item));
      sourceRow++;
    }
    return List.copyOf(result);
  }

  private static String requiredText(JsonNode item, String field, int sourceRow) {
    JsonNode value = item.get(field);
    if (value == null || !value.isTextual() || value.asText().isBlank()) {
      throw new IllegalArgumentException("legacy entry row " + sourceRow + " " + field + " must be a nonblank string");
    }
    return value.asText();
  }

  private static void requireSnapshotShape(Connection connection) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("PRAGMA table_info(snapshots)");
         ResultSet rows = statement.executeQuery()) {
      boolean id = false;
      boolean createdAt = false;
      boolean payload = false;
      while (rows.next()) {
        String name = rows.getString("name");
        id |= "id".equals(name);
        createdAt |= "created_at".equals(name);
        payload |= "payload_json".equals(name);
      }
      if (!id || !createdAt || !payload) {
        throw new IllegalArgumentException("SQLite snapshots table must contain id, created_at and payload_json");
      }
    }
  }

  private static long parseSnapshotId(String sourceSnapshotId) {
    try {
      return Long.parseLong(sourceSnapshotId);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("SQLite snapshotId exceeds long range", exception);
    }
  }

  private static String readOnlyJdbcUrl(Path database) {
    return "jdbc:sqlite:" + database.toUri() + "?mode=ro";
  }
}

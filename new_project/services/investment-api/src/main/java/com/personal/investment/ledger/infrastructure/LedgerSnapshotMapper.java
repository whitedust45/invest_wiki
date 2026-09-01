package com.personal.investment.ledger.infrastructure;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LedgerSnapshotMapper {
  @Insert("""
      INSERT INTO ledger_db.ledger_snapshot
        (ledger_snapshot_id, owner_user_id, as_of_date, source_ledger_version, import_export_file_id, checksum, created_at)
      VALUES
        (#{ledgerSnapshotId}, #{ownerUserId}, #{asOfDate}, #{sourceLedgerVersion}, #{importExportFileId},
         UNHEX(#{contentSha256Hex}), UTC_TIMESTAMP(3))
      """)
  int insert(Row row);

  @Select("""
      SELECT ledger_snapshot_id AS ledgerSnapshotId, owner_user_id AS ownerUserId, as_of_date AS asOfDate,
             source_ledger_version AS sourceLedgerVersion, import_export_file_id AS importExportFileId,
             LOWER(HEX(checksum)) AS contentSha256Hex, created_at AS createdAt
      FROM ledger_db.ledger_snapshot
      WHERE owner_user_id = #{ownerUserId} AND ledger_snapshot_id = #{ledgerSnapshotId}
      LIMIT 1
      """)
  Row findOwned(@Param("ownerUserId") String ownerUserId, @Param("ledgerSnapshotId") String ledgerSnapshotId);

  @Select("""
      SELECT ledger_snapshot_id AS ledgerSnapshotId, owner_user_id AS ownerUserId, as_of_date AS asOfDate,
             source_ledger_version AS sourceLedgerVersion, import_export_file_id AS importExportFileId,
             LOWER(HEX(checksum)) AS contentSha256Hex, created_at AS createdAt
      FROM ledger_db.ledger_snapshot
      WHERE owner_user_id = #{ownerUserId} AND as_of_date = #{asOfDate}
        AND source_ledger_version = #{sourceLedgerVersion}
      LIMIT 1
      """)
  Row findOwnedAtVersion(@Param("ownerUserId") String ownerUserId, @Param("asOfDate") LocalDate asOfDate,
                         @Param("sourceLedgerVersion") long sourceLedgerVersion);

  @Select("""
      SELECT ledger_snapshot_id AS ledgerSnapshotId, owner_user_id AS ownerUserId, as_of_date AS asOfDate,
             source_ledger_version AS sourceLedgerVersion, import_export_file_id AS importExportFileId,
             LOWER(HEX(checksum)) AS contentSha256Hex, created_at AS createdAt
      FROM ledger_db.ledger_snapshot
      WHERE owner_user_id = #{ownerUserId}
      ORDER BY created_at DESC, ledger_snapshot_id DESC
      LIMIT #{limit}
      """)
  List<Row> findOwnedRecent(@Param("ownerUserId") String ownerUserId, @Param("limit") int limit);

  @Select("""
      SELECT DISTINCT owner_user_id
      FROM ledger_db.ledger_transaction
      ORDER BY owner_user_id
      """)
  List<String> findOwnersWithLedgerFacts();

  record Row(String ledgerSnapshotId, String ownerUserId, LocalDate asOfDate, long sourceLedgerVersion,
             String importExportFileId, String contentSha256Hex, Instant createdAt) { }
}

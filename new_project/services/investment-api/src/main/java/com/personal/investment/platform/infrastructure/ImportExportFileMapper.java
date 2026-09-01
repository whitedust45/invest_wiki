package com.personal.investment.platform.infrastructure;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ImportExportFileMapper {
  @Insert("""
      INSERT INTO platform_db.import_export_file
        (import_export_file_id, owner_user_id, direction, object_key, content_sha256, media_type, byte_size, status,
         encryption_key_version, created_at, expires_at)
      VALUES
        (#{importExportFileId}, #{ownerUserId}, #{direction}, #{objectKey}, UNHEX(#{contentSha256Hex}), #{mediaType},
         #{byteSize}, #{status}, #{encryptionKeyVersion}, UTC_TIMESTAMP(3), #{expiresAt})
      """)
  int insert(FileRow row);

  @Select("""
      SELECT import_export_file_id AS importExportFileId, owner_user_id AS ownerUserId, direction, object_key AS objectKey,
             LOWER(HEX(content_sha256)) AS contentSha256Hex, media_type AS mediaType, byte_size AS byteSize, status,
             encryption_key_version AS encryptionKeyVersion, expires_at AS expiresAt
      FROM platform_db.import_export_file
      WHERE owner_user_id = #{ownerUserId} AND import_export_file_id = #{importExportFileId}
      """)
  FileRow findOwned(@Param("ownerUserId") String ownerUserId,
                    @Param("importExportFileId") String importExportFileId);

  @Update("""
      UPDATE platform_db.import_export_file
      SET status = #{to}, scan_lease_until = NULL
      WHERE owner_user_id = #{ownerUserId} AND import_export_file_id = #{importExportFileId} AND status = #{from}
      """)
  int transition(@Param("ownerUserId") String ownerUserId, @Param("importExportFileId") String importExportFileId,
                 @Param("from") String from, @Param("to") String to);

  @Update("""
      UPDATE platform_db.import_export_file
      SET status = 'SCANNED', object_key = #{evidenceObjectKey}, scan_lease_until = NULL
      WHERE owner_user_id = #{ownerUserId} AND import_export_file_id = #{importExportFileId} AND status = 'QUARANTINED'
      """)
  int completeScan(@Param("ownerUserId") String ownerUserId, @Param("importExportFileId") String importExportFileId,
                   @Param("evidenceObjectKey") String evidenceObjectKey);

  @Select("""
      SELECT import_export_file_id AS importExportFileId, owner_user_id AS ownerUserId, direction, object_key AS objectKey,
             LOWER(HEX(content_sha256)) AS contentSha256Hex, media_type AS mediaType, byte_size AS byteSize, status,
             encryption_key_version AS encryptionKeyVersion, expires_at AS expiresAt
      FROM platform_db.import_export_file
      WHERE status = 'QUARANTINED'
        AND (scan_lease_until IS NULL OR scan_lease_until <= UTC_TIMESTAMP(3))
      ORDER BY created_at, import_export_file_id
      LIMIT #{limit}
      """)
  List<FileRow> findQuarantinedForScan(@Param("limit") int limit);

  @Update("""
      UPDATE platform_db.import_export_file
      SET scan_lease_until = DATE_ADD(UTC_TIMESTAMP(3), INTERVAL #{leaseSeconds} SECOND)
      WHERE owner_user_id = #{ownerUserId} AND import_export_file_id = #{importExportFileId}
        AND status = 'QUARANTINED'
        AND (scan_lease_until IS NULL OR scan_lease_until <= UTC_TIMESTAMP(3))
      """)
  int tryClaimScan(@Param("ownerUserId") String ownerUserId, @Param("importExportFileId") String importExportFileId,
                   @Param("leaseSeconds") long leaseSeconds);

  @Select("""
      SELECT owner_user_id AS ownerUserId, import_export_file_id AS importExportFileId, object_key AS objectKey,
             LOWER(HEX(content_sha256)) AS contentSha256Hex, status
      FROM platform_db.import_export_file
      WHERE direction <> 'SNAPSHOT'
        AND created_at < DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 30 DAY)
        AND status <> 'DELETED'
      ORDER BY created_at, import_export_file_id
      LIMIT #{limit}
      """)
  List<ExpiredFileRow> findExpiredForDeletion(@Param("limit") int limit);

  @Update("""
      UPDATE platform_db.import_export_file
      SET status = 'DELETED', scan_lease_until = NULL
      WHERE owner_user_id = #{ownerUserId} AND import_export_file_id = #{importExportFileId}
        AND status = #{status}
      """)
  int markDeleted(@Param("ownerUserId") String ownerUserId, @Param("importExportFileId") String importExportFileId,
                  @Param("status") String status);

  record FileRow(String importExportFileId, String ownerUserId, String direction, String objectKey,
                 String contentSha256Hex, String mediaType, long byteSize, String status,
                 String encryptionKeyVersion, Instant expiresAt) {
  }

  record ExpiredFileRow(String ownerUserId, String importExportFileId, String objectKey, String contentSha256Hex,
                        String status) {
  }
}

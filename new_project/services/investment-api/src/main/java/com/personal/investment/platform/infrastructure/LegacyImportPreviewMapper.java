package com.personal.investment.platform.infrastructure;

import java.time.Instant;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface LegacyImportPreviewMapper {
  @Insert("""
      INSERT INTO platform_db.import_preview
        (import_preview_id, owner_user_id, job_id, import_export_file_id, import_format, source_snapshot_id,
         mapping_json, preview_json, preview_checksum, status, expires_at, created_at)
      VALUES
        (#{importPreviewId}, #{ownerUserId}, #{jobId}, #{importExportFileId}, #{format}, #{sourceSnapshotId},
         #{mappingJson}, #{previewJson}, UNHEX(#{previewChecksumHex}), #{status}, #{expiresAt}, #{createdAt})
      """)
  int insert(PreviewRow row);

  @Select("""
      SELECT import_preview_id AS importPreviewId, owner_user_id AS ownerUserId, job_id AS jobId,
             import_export_file_id AS importExportFileId, import_format AS format, source_snapshot_id AS sourceSnapshotId,
             CAST(mapping_json AS CHAR) AS mappingJson, CAST(preview_json AS CHAR) AS previewJson,
             LOWER(HEX(preview_checksum)) AS previewChecksumHex, status, expires_at AS expiresAt, created_at AS createdAt
      FROM platform_db.import_preview
      WHERE owner_user_id = #{ownerUserId} AND job_id = #{jobId}
      """)
  PreviewRow findOwned(@Param("ownerUserId") String ownerUserId, @Param("jobId") String jobId);

  @Select("""
      SELECT import_preview_id AS importPreviewId, owner_user_id AS ownerUserId, job_id AS jobId,
             import_export_file_id AS importExportFileId, import_format AS format, source_snapshot_id AS sourceSnapshotId,
             CAST(mapping_json AS CHAR) AS mappingJson, CAST(preview_json AS CHAR) AS previewJson,
             LOWER(HEX(preview_checksum)) AS previewChecksumHex, status, expires_at AS expiresAt, created_at AS createdAt
      FROM platform_db.import_preview
      WHERE owner_user_id = #{ownerUserId} AND job_id = #{jobId}
      FOR UPDATE
      """)
  PreviewRow lockOwned(@Param("ownerUserId") String ownerUserId, @Param("jobId") String jobId);

  @Update("""
      UPDATE platform_db.import_preview
      SET status = 'EXPIRED'
      WHERE owner_user_id = #{ownerUserId} AND import_export_file_id = #{importExportFileId}
        AND status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'NEEDS_REVIEW')
      """)
  int expireUncommitted(@Param("ownerUserId") String ownerUserId,
                         @Param("importExportFileId") String importExportFileId);

  @Update("""
      UPDATE platform_db.import_preview
      SET status = 'COMMITTED'
      WHERE owner_user_id = #{ownerUserId} AND job_id = #{jobId} AND status = 'SUCCEEDED'
      """)
  int markCommitted(@Param("ownerUserId") String ownerUserId, @Param("jobId") String jobId);

  record PreviewRow(String importPreviewId, String ownerUserId, String jobId, String importExportFileId,
                    String format, String sourceSnapshotId, String mappingJson, String previewJson,
                    String previewChecksumHex, String status, Instant expiresAt, Instant createdAt) {
  }
}

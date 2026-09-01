package com.personal.investment.platform.infrastructure;

import com.personal.investment.platform.application.LegacyImportFormat;
import com.personal.investment.platform.application.LegacyImportPreview;
import com.personal.investment.platform.application.LegacyImportPreviewPort;
import com.personal.investment.platform.application.LegacyImportPreviewStatus;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class MyBatisLegacyImportPreviewAdapter implements LegacyImportPreviewPort {
  private final LegacyImportPreviewMapper mapper;

  public MyBatisLegacyImportPreviewAdapter(LegacyImportPreviewMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void append(LegacyImportPreview preview) {
    mapper.insert(row(preview));
  }

  @Override
  public Optional<LegacyImportPreview> findOwned(String ownerUserId, String jobId) {
    return Optional.ofNullable(mapper.findOwned(ownerUserId, jobId)).map(this::preview);
  }

  @Override
  public Optional<LegacyImportPreview> lockOwned(String ownerUserId, String jobId) {
    return Optional.ofNullable(mapper.lockOwned(ownerUserId, jobId)).map(this::preview);
  }

  @Override
  public void expireUncommitted(String ownerUserId, String importExportFileId) {
    mapper.expireUncommitted(ownerUserId, importExportFileId);
  }

  @Override
  public void markCommitted(String ownerUserId, String jobId) {
    if (mapper.markCommitted(ownerUserId, jobId) != 1) {
      throw new IllegalArgumentException("import preview state changed concurrently");
    }
  }

  private LegacyImportPreviewMapper.PreviewRow row(LegacyImportPreview preview) {
    return new LegacyImportPreviewMapper.PreviewRow(preview.importPreviewId(), preview.ownerUserId(), preview.jobId(),
        preview.importExportFileId(), preview.format().name(), preview.sourceSnapshotId(), preview.mappingJson(),
        preview.previewJson(), preview.previewChecksumHex(), preview.status().name(), preview.expiresAt(),
        preview.createdAt());
  }

  private LegacyImportPreview preview(LegacyImportPreviewMapper.PreviewRow row) {
    return new LegacyImportPreview(row.importPreviewId(), row.ownerUserId(), row.jobId(), row.importExportFileId(),
        LegacyImportFormat.valueOf(row.format()), row.sourceSnapshotId(), row.mappingJson(), row.previewJson(),
        row.previewChecksumHex(), LegacyImportPreviewStatus.valueOf(row.status()), row.expiresAt(), row.createdAt());
  }
}

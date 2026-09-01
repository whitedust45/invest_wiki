package com.personal.investment.platform.infrastructure;

import com.personal.investment.platform.application.ImportExportFile;
import com.personal.investment.platform.application.ImportExportFilePort;
import com.personal.investment.platform.application.ImportExportFileDirection;
import com.personal.investment.platform.application.ImportExportFileStatus;
import com.personal.investment.platform.application.ExpiredImportExportFile;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class MyBatisImportExportFileAdapter implements ImportExportFilePort {
  private final ImportExportFileMapper mapper;

  public MyBatisImportExportFileAdapter(ImportExportFileMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void append(ImportExportFile file) {
    mapper.insert(new ImportExportFileMapper.FileRow(file.importExportFileId(), file.ownerUserId(),
        file.direction().name(), file.objectKey(), file.contentSha256Hex(), file.mediaType(), file.byteSize(),
        file.status().name(), file.encryptionKeyVersion(), file.expiresAt()));
  }

  @Override
  public Optional<ImportExportFile> findOwned(String ownerUserId, String importExportFileId) {
    ImportExportFileMapper.FileRow row = mapper.findOwned(ownerUserId, importExportFileId);
    if (row == null) {
      return Optional.empty();
    }
    return Optional.of(new ImportExportFile(row.importExportFileId(), row.ownerUserId(),
        ImportExportFileDirection.valueOf(row.direction()), row.objectKey(), row.contentSha256Hex(), row.mediaType(),
        row.byteSize(), ImportExportFileStatus.valueOf(row.status()), row.encryptionKeyVersion(), row.expiresAt()));
  }

  @Override
  public void transition(String ownerUserId, String importExportFileId, ImportExportFileStatus from,
      ImportExportFileStatus to) {
    if (mapper.transition(ownerUserId, importExportFileId, from.name(), to.name()) != 1) {
      throw new IllegalArgumentException("import/export file state changed concurrently");
    }
  }

  @Override
  public void completeScan(String ownerUserId, String importExportFileId, String evidenceObjectKey) {
    if (evidenceObjectKey == null || evidenceObjectKey.isBlank()
        || mapper.completeScan(ownerUserId, importExportFileId, evidenceObjectKey) != 1) {
      throw new IllegalArgumentException("import/export file state changed concurrently");
    }
  }

  @Override
  public List<ImportExportFile> findQuarantinedForScan(int limit) {
    if (limit < 1 || limit > 1_000) {
      throw new IllegalArgumentException("queued scan limit is invalid");
    }
    return mapper.findQuarantinedForScan(limit).stream().map(this::file).toList();
  }

  @Override
  public boolean tryClaimScan(String ownerUserId, String importExportFileId, Duration lease) {
    if (lease == null || lease.isZero() || lease.isNegative() || lease.getSeconds() < 1) {
      throw new IllegalArgumentException("file scan lease must be at least one second");
    }
    return mapper.tryClaimScan(ownerUserId, importExportFileId, lease.getSeconds()) == 1;
  }

  @Override
  public List<ExpiredImportExportFile> findExpiredForDeletion(int limit) {
    if (limit < 1 || limit > 1_000) {
      throw new IllegalArgumentException("retention sweep limit is invalid");
    }
    return mapper.findExpiredForDeletion(limit).stream().map(row -> new ExpiredImportExportFile(row.ownerUserId(),
        row.importExportFileId(), row.objectKey(), row.contentSha256Hex(), ImportExportFileStatus.valueOf(row.status())))
        .toList();
  }

  @Override
  public void markDeleted(ExpiredImportExportFile file) {
    if (mapper.markDeleted(file.ownerUserId(), file.importExportFileId(), file.status().name()) != 1) {
      throw new IllegalArgumentException("import/export file state changed concurrently");
    }
  }

  private ImportExportFile file(ImportExportFileMapper.FileRow row) {
    return new ImportExportFile(row.importExportFileId(), row.ownerUserId(),
        ImportExportFileDirection.valueOf(row.direction()), row.objectKey(), row.contentSha256Hex(), row.mediaType(),
        row.byteSize(), ImportExportFileStatus.valueOf(row.status()), row.encryptionKeyVersion(), row.expiresAt());
  }
}

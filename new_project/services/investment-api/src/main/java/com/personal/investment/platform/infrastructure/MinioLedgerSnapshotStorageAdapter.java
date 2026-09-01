package com.personal.investment.platform.infrastructure;

import com.personal.investment.bootstrap.config.ObjectStorageProperties;
import com.personal.investment.ledger.application.LedgerSnapshotStoragePort;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.ServerSideEncryption;
import java.io.ByteArrayInputStream;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Server-to-server encrypted snapshot storage; no snapshot object key is ever exposed as a public URL. */
@Component
public class MinioLedgerSnapshotStorageAdapter implements LedgerSnapshotStoragePort {
  private final MinioClient minioClient;
  private final ObjectStorageProperties properties;

  public MinioLedgerSnapshotStorageAdapter(MinioClient minioClient, ObjectStorageProperties properties) {
    this.minioClient = minioClient;
    this.properties = properties;
  }

  @Override
  public void write(String objectKey, byte[] content, String contentSha256Hex) {
    try (ByteArrayInputStream source = new ByteArrayInputStream(content)) {
      minioClient.putObject(PutObjectArgs.builder().bucket(properties.bucket()).object(objectKey)
          .stream(source, (long) content.length, -1L).contentType("application/json")
          .userMetadata(Map.of("content-sha256", contentSha256Hex, "artifact-kind", "ledger-snapshot"))
          .sse(new ServerSideEncryption.S3()).build());
    } catch (Exception exception) {
      throw new IllegalStateException("ledger snapshot could not be written to private storage", exception);
    }
  }

  @Override
  public byte[] read(String objectKey) {
    try (var response = minioClient.getObject(GetObjectArgs.builder().bucket(properties.bucket()).object(objectKey).build())) {
      return response.readAllBytes();
    } catch (Exception exception) {
      throw new IllegalStateException("ledger snapshot could not be read from private storage", exception);
    }
  }

  @Override
  public void delete(String objectKey) {
    try {
      minioClient.removeObject(RemoveObjectArgs.builder().bucket(properties.bucket()).object(objectKey).build());
    } catch (Exception exception) {
      throw new IllegalStateException("orphaned ledger snapshot could not be removed", exception);
    }
  }
}

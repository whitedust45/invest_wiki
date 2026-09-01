package com.personal.investment.platform.infrastructure;

import com.personal.investment.bootstrap.config.ObjectStorageProperties;
import com.personal.investment.platform.application.UploadedObject;
import com.personal.investment.platform.application.UploadedObjectStoragePort;
import io.minio.CopyObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.SourceObject;
import io.minio.ServerSideEncryption;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Trusted server-side object operations used after a client POST; credentials never leave the API process. */
@Component
public class MinioUploadedObjectStorageAdapter implements UploadedObjectStoragePort {
  private final MinioClient minioClient;
  private final ObjectStorageProperties properties;

  public MinioUploadedObjectStorageAdapter(MinioClient minioClient, ObjectStorageProperties properties) {
    this.minioClient = minioClient;
    this.properties = properties;
  }

  @Override
  public UploadedObject read(String objectKey) {
    try {
      StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder().bucket(properties.bucket())
          .object(objectKey).build());
      try (var response = minioClient.getObject(GetObjectArgs.builder().bucket(properties.bucket()).object(objectKey)
          .build())) {
        Map<String, String> metadata = new LinkedHashMap<>();
        stat.userMetadata().forEach(entry -> metadata.put(entry.getKey(), entry.getValue()));
        return new UploadedObject(response.readAllBytes(), stat.contentType(), Map.copyOf(metadata));
      }
    } catch (Exception exception) {
      throw new IllegalStateException("uploaded object could not be read from private storage", exception);
    }
  }

  @Override
  public void copy(String sourceObjectKey, String destinationObjectKey) {
    try {
      minioClient.copyObject(CopyObjectArgs.builder().bucket(properties.bucket()).object(destinationObjectKey)
          .source(SourceObject.builder().bucket(properties.bucket()).object(sourceObjectKey).build())
          .sse(new ServerSideEncryption.S3()).build());
    } catch (Exception exception) {
      throw new IllegalStateException("uploaded object could not be copied to evidence storage", exception);
    }
  }

  @Override
  public void delete(String objectKey) {
    try {
      minioClient.removeObject(RemoveObjectArgs.builder().bucket(properties.bucket()).object(objectKey).build());
    } catch (Exception exception) {
      throw new IllegalStateException("uploaded object could not be deleted from quarantine", exception);
    }
  }
}

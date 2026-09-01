package com.personal.investment.platform.infrastructure;

import com.personal.investment.bootstrap.config.ObjectStorageProperties;
import com.personal.investment.platform.application.PresignedUploadForm;
import com.personal.investment.platform.application.PresignedUploadRequest;
import com.personal.investment.platform.application.UploadObjectStoragePort;
import io.minio.MinioClient;
import io.minio.PostPolicy;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Generates constrained POST forms only; clients never receive object-storage credentials. */
@Component
public class MinioUploadObjectStorageAdapter implements UploadObjectStoragePort {
  private static final String SHA256_METADATA = "x-amz-meta-content-sha256";
  private static final String OWNER_METADATA = "x-amz-meta-owner-user-id";
  private static final String FILE_METADATA = "x-amz-meta-import-export-file-id";
  private static final String SSE_HEADER = "x-amz-server-side-encryption";
  private static final String SSE_S3 = "AES256";

  private final MinioClient minioClient;
  private final ObjectStorageProperties properties;

  public MinioUploadObjectStorageAdapter(MinioClient minioClient, ObjectStorageProperties properties) {
    this.minioClient = minioClient;
    this.properties = properties;
  }

  @Override
  public PresignedUploadForm presignPost(PresignedUploadRequest request) {
    try {
      PostPolicy policy = new PostPolicy(properties.bucket(),
          ZonedDateTime.ofInstant(request.expiresAt(), ZoneOffset.UTC));
      policy.addEqualsCondition("key", request.objectKey());
      policy.addEqualsCondition("Content-Type", request.mediaType());
      policy.addEqualsCondition(SHA256_METADATA, request.contentSha256Hex());
      policy.addEqualsCondition(OWNER_METADATA, request.ownerUserId());
      policy.addEqualsCondition(FILE_METADATA, request.importExportFileId());
      policy.addEqualsCondition(SSE_HEADER, SSE_S3);
      // The client-supplied declared size is a hard cap; the scanner independently verifies it after upload.
      policy.addContentLengthRangeCondition(request.byteSize(), request.byteSize());

      Map<String, String> formData = new LinkedHashMap<>(minioClient.getPresignedPostFormData(policy));
      formData.put("key", request.objectKey());
      formData.put("Content-Type", request.mediaType());
      formData.put(SHA256_METADATA, request.contentSha256Hex());
      formData.put(OWNER_METADATA, request.ownerUserId());
      formData.put(FILE_METADATA, request.importExportFileId());
      formData.put(SSE_HEADER, SSE_S3);
      return new PresignedUploadForm(uploadUrl(), "file", Map.copyOf(formData));
    } catch (Exception exception) {
      throw new IllegalStateException("object storage upload credential could not be issued", exception);
    }
  }

  private String uploadUrl() {
    return uploadUrl(properties.uploadEndpoint(), properties.bucket());
  }

  static String uploadUrl(String uploadEndpoint, String bucket) {
    return uploadEndpoint.replaceFirst("/+$", "") + "/" + bucket;
  }
}

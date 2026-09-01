package com.personal.investment.platform.application;

public interface UploadObjectStoragePort {
  PresignedUploadForm presignPost(PresignedUploadRequest request);
}

package com.personal.investment.platform.application;

public interface UploadedObjectStoragePort {
  UploadedObject read(String objectKey);

  void copy(String sourceObjectKey, String destinationObjectKey);

  void delete(String objectKey);
}

package com.personal.investment.platform.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MinioUploadObjectStorageAdapterTest {
  @Test
  void usesClientReachableUploadEndpointInsteadOfInternalServiceEndpoint() {
    String url = MinioUploadObjectStorageAdapter.uploadUrl("https://upload.example.test/", "investment-private");

    assertThat(url).isEqualTo("https://upload.example.test/investment-private");
  }
}

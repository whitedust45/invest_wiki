package com.personal.investment.bootstrap.config;

import com.personal.investment.platform.application.FileUploadPolicy;
import com.personal.investment.platform.application.ContentSafetyScanner;
import io.minio.MinioClient;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class ObjectStorageConfiguration {
  @Bean
  Clock applicationClock() {
    return Clock.systemUTC();
  }

  @Bean
  FileUploadPolicy fileUploadPolicy(ObjectStorageProperties properties) {
    return new FileUploadPolicy(properties.maxUploadBytes(), properties.uploadCredentialTtl(),
        properties.encryptionKeyVersion());
  }

  @Bean
  MinioClient minioClient(ObjectStorageProperties properties) {
    return MinioClient.builder()
        .endpoint(properties.endpoint())
        .credentials(properties.accessKey(), properties.secretKey())
        .build();
  }

  /** Production must replace this local-only structural scanner with a malware scanning integration. */
  @Bean
  @Profile("local")
  ContentSafetyScanner localContentSafetyScanner() {
    return ContentSafetyScanner.localStructural();
  }
}

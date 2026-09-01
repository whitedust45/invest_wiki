package com.personal.investment.bootstrap.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class NonLocalDeploymentConfigurationGuardTest {
  @Test
  void acceptsAnHttpsNonPlaceholderNonLocalConfiguration() {
    assertThatCode(() -> new NonLocalDeploymentConfigurationGuard(validAuth(), validStorage()).validate())
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsAnHttpOrLoopbackObjectStorageUploadEndpoint() {
    ObjectStorageProperties http = new ObjectStorageProperties("https://storage.example.invalid", "http://127.0.0.1:9000",
        "private", "access", "secret", "kms-v1", 10_485_760, Duration.ofMinutes(15), Duration.ofSeconds(10),
        Duration.ofMinutes(10), 20);

    assertThatThrownBy(() -> new NonLocalDeploymentConfigurationGuard(validAuth(), http).validate())
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("upload endpoint");
  }

  @Test
  void rejectsMockAuthenticationAndPlaceholderSecrets() {
    AuthProperties mockAuth = new AuthProperties("<TBD_AUTH_HMAC_KEY>", "session-key", "bootstrap", Duration.ofHours(12),
        true, "mock-code", "mock-openid");

    assertThatThrownBy(() -> new NonLocalDeploymentConfigurationGuard(mockAuth, validStorage()).validate())
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("Mock");
  }

  private static AuthProperties validAuth() {
    return new AuthProperties("openid-hmac-key", "session-hmac-key", "bootstrap-secret", Duration.ofHours(12), false,
        null, null);
  }

  private static ObjectStorageProperties validStorage() {
    return new ObjectStorageProperties("https://storage.example.invalid", "https://upload.example.invalid",
        "investment-private", "access-key", "secret-key", "kms-v1", 10_485_760, Duration.ofMinutes(15),
        Duration.ofSeconds(10), Duration.ofMinutes(10), 20);
  }
}

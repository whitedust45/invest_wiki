package com.personal.investment.bootstrap.config;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Fails closed before a non-local process can expose Mock credentials or a private upload endpoint. */
@Component
@Profile("!local")
public class NonLocalDeploymentConfigurationGuard {
  private final AuthProperties auth;
  private final ObjectStorageProperties storage;

  public NonLocalDeploymentConfigurationGuard(AuthProperties auth, ObjectStorageProperties storage) {
    this.auth = auth;
    this.storage = storage;
  }

  @PostConstruct
  void validate() {
    if (auth.mockEnabled()) {
      throw new IllegalStateException("non-local deployment must not enable Mock authentication");
    }
    requireSecret("auth openid HMAC key", auth.openidHmacKey());
    requireSecret("auth session HMAC key", auth.sessionHmacKey());
    requireSecret("bootstrap enrollment secret", auth.bootstrapEnrollmentSecret());
    requireSecret("object storage access key", storage.accessKey());
    requireSecret("object storage secret key", storage.secretKey());
    if (isPlaceholder(storage.encryptionKeyVersion()) || storage.encryptionKeyVersion().startsWith("local-")) {
      throw new IllegalStateException("non-local deployment must use a non-placeholder KMS key version");
    }
    requireSecureStorageEndpoint(storage.endpoint());
    requirePublicHttpsUploadEndpoint(storage.uploadEndpoint());
  }

  private static void requireSecret(String name, String value) {
    if (isPlaceholder(value)) {
      throw new IllegalStateException("non-local deployment has a placeholder " + name);
    }
  }

  private static boolean isPlaceholder(String value) {
    if (value == null || value.isBlank()) {
      return true;
    }
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    return normalized.contains("tbd") || normalized.contains("changeme") || normalized.contains("replace")
        || normalized.startsWith("<") || normalized.endsWith(">");
  }

  private static void requireSecureStorageEndpoint(String endpoint) {
    URI uri = uri(endpoint, "non-local object storage endpoint");
    String host = uri.getHost();
    if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || host.isBlank() || uri.getUserInfo() != null
        || uri.getRawQuery() != null || uri.getRawFragment() != null || isLoopback(host)) {
      throw new IllegalStateException("non-local object storage endpoint must be HTTPS and non-loopback");
    }
  }

  private static void requirePublicHttpsUploadEndpoint(String endpoint) {
    URI uri = uri(endpoint, "non-local object storage upload endpoint");
    String host = uri.getHost();
    if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || host.isBlank() || uri.getUserInfo() != null
        || uri.getRawQuery() != null || uri.getRawFragment() != null || isLoopback(host)) {
      throw new IllegalStateException("non-local object storage upload endpoint must be public HTTPS and non-loopback");
    }
  }

  private static URI uri(String endpoint, String name) {
    final URI uri;
    try {
      uri = URI.create(endpoint);
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException(name + " must be a valid HTTPS URL", exception);
    }
    return uri;
  }

  private static boolean isLoopback(String host) {
    String normalized = host.toLowerCase(Locale.ROOT);
    return "localhost".equals(normalized) || normalized.endsWith(".localhost") || "::1".equals(normalized)
        || "0:0:0:0:0:0:0:1".equals(normalized) || "0.0.0.0".equals(normalized)
        || normalized.matches("127(?:\\.[0-9]{1,3}){3}");
  }
}

package com.personal.investment.bootstrap.config;

import java.net.URI;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Explicit executor settings. The executor is disabled by default so local development never needs a scheduler.
 */
@Validated
@ConfigurationProperties(prefix = "app.xxl-job")
public record XxlJobProperties(boolean enabled, String adminAddresses, String appName, String accessToken,
                               String address, int port, String logPath, int logRetentionDays, int timeoutSeconds) {
  public XxlJobProperties {
    if (enabled) {
    require("XXL-JOB admin addresses", adminAddresses);
    require("XXL-JOB executor app name", appName);
    require("XXL-JOB executor access token", accessToken);
    require("XXL-JOB executor address", address);
    require("XXL-JOB executor log path", logPath);
    if (accessToken.trim().length() < 32) {
      throw new IllegalArgumentException("XXL-JOB executor access token must contain at least 32 characters");
    }
    if (port < 1 || port > 65_535) {
      throw new IllegalArgumentException("XXL-JOB executor port must be between 1 and 65535");
    }
    if (logRetentionDays < 3 || logRetentionDays > 365) {
      throw new IllegalArgumentException("XXL-JOB executor log retention must be between 3 and 365 days");
    }
    if (timeoutSeconds < 1 || timeoutSeconds > 60) {
      throw new IllegalArgumentException("XXL-JOB admin timeout must be between 1 and 60 seconds");
    }
    URI executorAddress;
    try {
      executorAddress = URI.create(address);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("XXL-JOB executor address must be a valid HTTP URL", exception);
    }
    boolean http = "http".equalsIgnoreCase(executorAddress.getScheme());
    boolean https = "https".equalsIgnoreCase(executorAddress.getScheme());
    if ((!http && !https) || executorAddress.getHost() == null || executorAddress.getPort() != port) {
      throw new IllegalArgumentException("XXL-JOB executor address must use HTTP(S) and its configured port");
    }
    }
  }

  private static void require(String name, String value) {
    if (value == null || value.isBlank() || value.toLowerCase(Locale.ROOT).contains("tbd")) {
      throw new IllegalArgumentException(name + " must be configured when XXL-JOB is enabled");
    }
  }
}

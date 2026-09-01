package com.personal.investment.bootstrap.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
    @NotBlank String openidHmacKey,
    @NotBlank String sessionHmacKey,
    @NotBlank String bootstrapEnrollmentSecret,
    @NotNull Duration sessionTtl,
    boolean mockEnabled,
    String mockLoginCode,
    String mockOpenId) {
}

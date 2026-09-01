package com.personal.investment.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class UlidGeneratorTest {
  @Test
  void generatesUppercaseCrockfordUlid() {
    String ulid = UlidGenerator.next(Clock.fixed(Instant.parse("2026-07-26T12:00:00Z"), ZoneOffset.UTC));

    assertThat(ulid).matches("[0-9A-HJKMNP-TV-Z]{26}");
  }
}

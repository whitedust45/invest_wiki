package com.personal.investment.bootstrap.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class XxlJobPropertiesTest {
  @Test
  void permitsBlankSchedulerValuesWhenTheExecutorIsDisabled() {
    assertThatCode(() -> new XxlJobProperties(false, "", "investment-api-executor", "", "", 9999,
        "/tmp/investment-xxl-job", 30, 3)).doesNotThrowAnyException();
  }

  @Test
  void rejectsAnEnabledExecutorWithoutARealConfiguration() {
    assertThatIllegalArgumentException().isThrownBy(() -> new XxlJobProperties(true, "", "", "", "", 9999,
        "/tmp/investment-xxl-job", 30, 3)).withMessageContaining("admin addresses");
  }

  @Test
  void acceptsAMatchingPrivateExecutorAddressAndPort() {
    assertThatCode(() -> new XxlJobProperties(true, "http://xxl-job-admin:8080", "investment-api-executor",
        "0123456789abcdef0123456789abcdef", "http://investment-api:9999", 9999,
        "/tmp/investment-xxl-job", 30, 3)).doesNotThrowAnyException();
  }
}

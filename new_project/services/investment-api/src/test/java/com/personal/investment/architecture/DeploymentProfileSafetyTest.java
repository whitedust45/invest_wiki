package com.personal.investment.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DeploymentProfileSafetyTest {
  @Test
  void sharedConfigurationNeverActivatesTheLocalProfileByDefault() throws IOException {
    String applicationYaml = Files.readString(Path.of("src/main/resources/application.yml"));

    assertThat(applicationYaml).doesNotContain("default: local");
  }
}

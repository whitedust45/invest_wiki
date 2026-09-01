package com.personal.investment.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class FlywayMigrationNamingTest {
  private static final Pattern VERSIONED_MIGRATION = Pattern.compile("^V([0-9]+)__.+\\.sql$");

  @Test
  void everyVersionedMigrationUsesAUniqueFlywayVersion() throws IOException {
    List<String> versions;
    try (var paths = Files.list(Path.of("src/main/resources/db/migration"))) {
      versions = paths.map(path -> path.getFileName().toString()).map(VERSIONED_MIGRATION::matcher)
          .filter(java.util.regex.Matcher::matches).map(matcher -> matcher.group(1)).toList();
    }

    assertThat(versions).doesNotHaveDuplicates();
  }
}

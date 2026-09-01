package com.personal.investment.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LedgerArchitectureTest {
  private static final Path JAVA_ROOT = Path.of("src/main/java/com/personal/investment");

  @Test
  void ledgerDomainIsFrameworkAndAdapterIndependent() throws Exception {
    try (var paths = Files.walk(JAVA_ROOT.resolve("ledger/domain"))) {
      for (Path path : paths.filter(candidate -> candidate.toString().endsWith(".java")).toList()) {
        String source = Files.readString(path);
        assertThat(source)
            .doesNotContain("org.springframework")
            .doesNotContain("org.apache.ibatis")
            .doesNotContain("ledger.infrastructure")
            .doesNotContain("ledger.interfaces");
      }
    }
  }

  @Test
  void applicationScansOnlyExplicitBoundedContextMappersAndProvidesLedgerAccountAdapter() throws Exception {
    assertThat(Files.readString(Path.of("src/main/java/com/personal/investment/InvestmentApiApplication.java")))
        .contains("@MapperScan(basePackages = \"com.personal.investment\", annotationClass = Mapper.class)");
    assertThat(Path.of("src/main/java/com/personal/investment/ledger/infrastructure/LedgerAccountMapper.java"))
        .exists();
    assertThat(Path.of("src/main/java/com/personal/investment/ledger/infrastructure/MyBatisLedgerAccountAdapter.java"))
        .exists();
    assertThat(Path.of("src/main/java/com/personal/investment/ledger/infrastructure/LedgerTransactionMapper.java"))
        .exists();
    assertThat(Path.of("src/main/java/com/personal/investment/ledger/infrastructure/MyBatisLedgerTransactionAdapter.java"))
        .exists();
  }
}

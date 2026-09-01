package com.personal.investment.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 用源代码级规则保护 DDD 边界，避免架构测试依赖宿主 JDK 的字节码版本。
 */
class IdentityArchitectureTest {
  private static final Path DOMAIN_SOURCE = Path.of(
      "src/main/java/com/personal/investment/identity/domain");

  @Test
  void domainMustNotDependOnInfrastructureOrSpring() throws IOException {
    try (var paths = Files.walk(DOMAIN_SOURCE)) {
      List<Path> sourceFiles = paths
          .filter(path -> path.toString().endsWith(".java"))
          .toList();

      assertThat(sourceFiles).isNotEmpty();
      for (Path sourceFile : sourceFiles) {
        String source = Files.readString(sourceFile);
        assertThat(source)
            .as("领域源文件不得依赖基础设施：%s", sourceFile)
            .doesNotContain(".identity.infrastructure.");
        assertThat(source)
            .as("领域源文件不得依赖 Spring：%s", sourceFile)
            .doesNotContain("org.springframework.");
      }
    }
  }
}

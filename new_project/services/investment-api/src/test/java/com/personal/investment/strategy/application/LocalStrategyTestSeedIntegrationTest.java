package com.personal.investment.strategy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personal.investment.InvestmentApiApplication;
import com.personal.investment.portfolio.application.PortfolioOverviewService;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/** Exercises the local fixture through the real Flyway schema and all application command services. */
@Tag("integration")
@SpringBootTest(classes = InvestmentApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local")
class LocalStrategyTestSeedIntegrationTest {
  private static final String OWNER_USER_ID = "00000000000000000000000000";
  private static final GenericContainer<?> MYSQL = new GenericContainer<>("mysql:8.4")
      .withEnv("MYSQL_ROOT_PASSWORD", "integration-root-password")
      .withEnv("MYSQL_DATABASE", "investment_bootstrap")
      .withEnv("MYSQL_USER", "investment_app")
      .withEnv("MYSQL_PASSWORD", "integration-app-password")
      .withExposedPorts(3306)
      .waitingFor(Wait.forListeningPort());
  private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
      .withCommand("redis-server", "--requirepass", "integration-redis-password")
      .withExposedPorts(6379)
      .waitingFor(Wait.forListeningPort());

  @Autowired
  private LocalStrategyTestSeedService seedService;

  @Autowired
  private JdbcTemplate jdbc;

  @Autowired
  private PortfolioOverviewService portfolioOverviewService;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    MYSQL.start();
    REDIS.start();
    String jdbcUrl = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306)
        + "/platform_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    registry.add("spring.datasource.url", () -> jdbcUrl);
    registry.add("spring.flyway.url", () -> "jdbc:mysql://" + MYSQL.getHost() + ":"
        + MYSQL.getMappedPort(3306) + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
    registry.add("spring.datasource.username", () -> "investment_app");
    registry.add("spring.datasource.password", () -> "integration-app-password");
    registry.add("spring.flyway.user", () -> "root");
    registry.add("spring.flyway.password", () -> "integration-root-password");
    registry.add("spring.flyway.placeholders.app_mysql_username", () -> "investment_app");
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    registry.add("spring.data.redis.password", () -> "integration-redis-password");
    registry.add("app.auth.openid-hmac-key", () -> "integration-openid-hmac-key");
    registry.add("app.auth.session-hmac-key", () -> "integration-session-hmac-key");
    registry.add("app.auth.bootstrap-enrollment-secret", () -> "integration-bootstrap-secret");
    registry.add("app.auth.mock-login-code", () -> "integration-login-code");
    registry.add("app.auth.mock-open-id", () -> "integration-open-id");
    registry.add("app.object-storage.access-key", () -> "integration-minio-access-key");
    registry.add("app.object-storage.secret-key", () -> "integration-minio-secret-key");
  }

  @AfterAll
  static void stopContainers() {
    REDIS.stop();
    MYSQL.stop();
  }

  @Test
  void writesTheConfirmedTwentyThreeFactPathExactlyOnce() {
    StrategySeedResult result = seedService.seed(OWNER_USER_ID);

    assertThat(result.seedName()).isEqualTo(LocalStrategyTestSeedService.SEED_NAME);
    assertThat(result.createdCashAccounts()).isEqualTo(3);
    assertThat(result.createdInstruments()).isEqualTo(10);
    assertThat(result.createdTransactions()).isEqualTo(23);
    assertThat(result.createdEvaluations()).isEqualTo(4);
    assertThat(result.currencies()).containsExactly("CNY", "USD");
    assertThat(count("SELECT COUNT(*) FROM ledger_db.ledger_transaction WHERE owner_user_id = ?", OWNER_USER_ID))
        .isEqualTo(23);
    assertThat(count("SELECT COUNT(*) FROM ledger_db.ledger_transaction "
        + "WHERE owner_user_id = ? AND strategy_key IS NULL", OWNER_USER_ID)).isZero();
    assertThat(count("SELECT COUNT(*) FROM strategy_db.strategy_evaluation WHERE owner_user_id = ?", OWNER_USER_ID))
        .isEqualTo(4);
    assertThat(count("SELECT COUNT(*) FROM strategy_db.strategy_seed_run WHERE owner_user_id = ?", OWNER_USER_ID))
        .isEqualTo(1);
    assertThat(count("SELECT COUNT(*) FROM market_db.market_sync_run")).isEqualTo(1);
    assertThat(count("SELECT COUNT(*) FROM market_db.quote_snapshot")).isEqualTo(6);
    assertThat(count("SELECT COUNT(*) FROM market_db.daily_metric")).isEqualTo(2);
    assertThat(count("SELECT COUNT(*) FROM market_db.basis_snapshot")).isEqualTo(2);
    assertThat(jdbc.queryForList("SELECT rule_version.strategy_key, evaluation.status "
        + "FROM strategy_db.strategy_evaluation evaluation "
        + "INNER JOIN strategy_db.strategy_rule_version rule_version "
        + "ON rule_version.strategy_rule_version_id = evaluation.strategy_rule_version_id "
        + "WHERE evaluation.owner_user_id = ?", OWNER_USER_ID)).extracting(value -> value.get("strategy_key"),
        value -> value.get("status")).containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple("HIGH_DIVIDEND", "WATCH"),
            org.assertj.core.groups.Tuple.tuple("QQQ_GROWTH", "IN_RANGE"),
            org.assertj.core.groups.Tuple.tuple("IC_IM", "IN_RANGE"),
            org.assertj.core.groups.Tuple.tuple("DEEP_PUT", "IN_RANGE"));
    assertThat(count("SELECT COUNT(*) FROM ledger_db.ledger_transaction "
        + "WHERE owner_user_id = ? AND transaction_type IN ('FUTURES_CLOSE', 'FUTURES_OPEN') "
        + "AND operation_group_key IS NOT NULL", OWNER_USER_ID)).isEqualTo(2);
    assertThat(portfolioOverviewService.summary(OWNER_USER_ID, LocalDate.of(2026, 7, 31)).items())
        .extracting(value -> value.currency().name()).containsExactlyInAnyOrder("CNY", "USD");

    assertThatThrownBy(() -> seedService.seed(OWNER_USER_ID))
        .isInstanceOf(StrategySeedRequiresEmptyLedgerException.class);
    assertThat(count("SELECT COUNT(*) FROM ledger_db.ledger_transaction WHERE owner_user_id = ?", OWNER_USER_ID))
        .isEqualTo(23);
  }

  private long count(String sql, String ownerUserId) {
    Long value = jdbc.queryForObject(sql, Long.class, ownerUserId);
    return value == null ? 0 : value;
  }

  private long count(String sql) {
    Long value = jdbc.queryForObject(sql, Long.class);
    return value == null ? 0 : value;
  }
}

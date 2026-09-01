package com.personal.investment.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

@Tag("integration")
class FlywaySchemaGateContainerTest {
  @Test
  void finalSchemaHasNoGenericBusinessIdOrForeignKey() throws Exception {
    try (GenericContainer<?> mysql = new GenericContainer<>("mysql:8.4")
        .withEnv("MYSQL_ROOT_PASSWORD", "integration-root-password")
        .withExposedPorts(3306)
        .waitingFor(Wait.forListeningPort())) {
      mysql.start();
      String jdbcUrl = "jdbc:mysql://" + mysql.getHost() + ":" + mysql.getMappedPort(3306)
          + "/?useSSL=false&allowPublicKeyRetrieval=true";
      try (Connection rootConnection = DriverManager.getConnection(jdbcUrl, "root", "integration-root-password");
          Statement statement = rootConnection.createStatement()) {
        statement.execute("CREATE USER 'investment_app'@'%' IDENTIFIED BY 'integration-app-password'");
      }
      Flyway.configure()
          .dataSource(jdbcUrl, "root", "integration-root-password")
          .defaultSchema("platform_db")
          .schemas("platform_db")
          .createSchemas(true)
          .locations("classpath:db/migration")
          .placeholders(Map.of("app_mysql_username", "investment_app"))
          .load()
          .migrate();

      try (Connection connection = DriverManager.getConnection(jdbcUrl, "root", "integration-root-password")) {
        assertThat(count(connection, """
            SELECT COUNT(*) FROM information_schema.tables
            WHERE table_schema IN ('identity_db','ledger_db','portfolio_db','market_db','strategy_db','reporting_db','platform_db')
              AND table_type = 'BASE TABLE'
              AND table_name <> 'flyway_schema_history'
            """)).isEqualTo(58);
        assertThat(count(connection, """
            SELECT COUNT(*) FROM information_schema.tables
            WHERE table_schema = 'strategy_db'
              AND table_name IN ('strategy_active_rule', 'strategy_active_rule_event',
                                 'strategy_reference_nav', 'strategy_seed_run', 'strategy_scan', 'strategy_scan_item')
            """)).isEqualTo(6);
        assertThat(count(connection, """
            SELECT COUNT(*) FROM information_schema.columns
            WHERE table_schema IN ('identity_db','ledger_db','portfolio_db','market_db','strategy_db','reporting_db','platform_db')
              AND column_name LIKE '%biz%'
            """)).isZero();
        assertThat(count(connection, """
            SELECT COUNT(*) FROM information_schema.statistics
            WHERE table_schema IN ('identity_db','ledger_db','portfolio_db','market_db','strategy_db','reporting_db','platform_db')
              AND index_name LIKE '%biz%'
            """)).isZero();
        assertThat(count(connection, """
            SELECT COUNT(*) FROM information_schema.table_constraints
            WHERE table_schema IN ('identity_db','ledger_db','portfolio_db','market_db','strategy_db','reporting_db','platform_db')
              AND constraint_type = 'FOREIGN KEY'
            """)).isZero();
        assertThat(count(connection, """
            SELECT COUNT(*) FROM information_schema.columns
            WHERE table_schema IN ('identity_db','ledger_db','portfolio_db','market_db','strategy_db','reporting_db','platform_db')
              AND column_name LIKE '%!_cent' ESCAPE '!'
              AND data_type <> 'bigint'
            """)).isZero();
        assertThat(count(connection, """
            SELECT COUNT(*) FROM information_schema.table_constraints
            WHERE constraint_schema = 'ledger_db'
              AND table_name = 'ledger_transaction'
              AND constraint_name = 'ck_ledger_transaction_type'
              AND constraint_type = 'CHECK'
            """)).isEqualTo(1);
        assertThat(count(connection, """
            SELECT COUNT(*) FROM information_schema.table_constraints
            WHERE constraint_schema = 'ledger_db'
              AND table_name = 'ledger_account'
              AND constraint_name = 'ck_ledger_account_kind'
              AND constraint_type = 'CHECK'
            """)).isEqualTo(1);
        assertThat(count(connection, """
            SELECT COUNT(*) FROM information_schema.statistics
            WHERE table_schema = 'platform_db'
              AND table_name = 'import_export_file'
              AND index_name = 'uk_import_export_content'
            """)).isZero();
        assertThat(count(connection, """
            SELECT COUNT(*) FROM information_schema.columns
            WHERE table_schema = 'platform_db'
              AND table_name = 'import_preview'
              AND column_name IN ('mapping_json', 'preview_json')
              AND data_type = 'longtext'
            """)).isEqualTo(2);
      }

      try (Connection applicationConnection = DriverManager.getConnection(jdbcUrl, "investment_app",
          "integration-app-password")) {
        assertThat(grants(applicationConnection))
            .anyMatch(grant -> grant.contains("ON `ledger_db`.`ledger_transaction`")
                && grant.contains("SELECT") && grant.contains("INSERT"));
        assertThatThrownBy(() -> execute(applicationConnection,
            "UPDATE ledger_db.ledger_transaction SET note = 'forbidden'"))
            .hasMessageContaining("UPDATE command denied");
        assertThatThrownBy(() -> execute(applicationConnection,
            "DELETE FROM ledger_db.ledger_posting"))
            .hasMessageContaining("DELETE command denied");
      }
    }
  }

  private List<String> grants(Connection connection) throws Exception {
    List<String> grants = new ArrayList<>();
    try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SHOW GRANTS")) {
      while (result.next()) {
        grants.add(result.getString(1));
      }
    }
    return grants;
  }

  private void execute(Connection connection, String sql) throws Exception {
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate(sql);
    }
  }

  private long count(Connection connection, String sql) throws Exception {
    try (var statement = connection.prepareStatement(sql); var result = statement.executeQuery()) {
      result.next();
      return result.getLong(1);
    }
  }
}

/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.server.integration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PostgreSQL integration smoke test for the AEP Phase-1 Testcontainers suite.
 *
 * @doc.type class
 * @doc.purpose Verify AEP integration test wiring can reach a live PostgreSQL container
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@Tag("integration")
@Tag("infrastructure-backed")
@Testcontainers(disabledWithoutDocker = true) // GH-90000
@DisplayName("PostgresIntegrationTest")
class PostgresIntegrationTest {

    @Container
    @SuppressWarnings("resource") // Managed by Testcontainers/JUnit lifecycle
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("aep_integration")
            .withUsername("aep")
            .withPassword("aep");

    @Test
    @DisplayName("can create a table and round-trip a row")
    void canCreateTableAndRoundTripRow() throws Exception { // GH-90000
        HikariConfig config = new HikariConfig(); // GH-90000
        config.setJdbcUrl(POSTGRES.getJdbcUrl()); // GH-90000
        config.setUsername(POSTGRES.getUsername()); // GH-90000
        config.setPassword(POSTGRES.getPassword()); // GH-90000

        try (HikariDataSource dataSource = new HikariDataSource(config); // GH-90000
             Connection connection = dataSource.getConnection(); // GH-90000
             Statement statement = connection.createStatement()) { // GH-90000
            statement.execute("CREATE TABLE IF NOT EXISTS aep_probe (id TEXT PRIMARY KEY, value TEXT NOT NULL)");
            statement.execute("INSERT INTO aep_probe (id, value) VALUES ('probe-1', 'ok')");

            try (ResultSet resultSet = statement.executeQuery("SELECT value FROM aep_probe WHERE id = 'probe-1'")) {
                assertThat(resultSet.next()).isTrue(); // GH-90000
                assertThat(resultSet.getString(1)).isEqualTo("ok");
            }
        }
    }
}

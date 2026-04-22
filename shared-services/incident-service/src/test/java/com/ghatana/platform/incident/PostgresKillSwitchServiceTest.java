/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.incident;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Integration tests for {@link PostgresKillSwitchService} against a real PostgreSQL container.
 *
 * @doc.type class
 * @doc.purpose Integration tests for PostgresKillSwitchService with Testcontainers
 * @doc.layer shared-service
 * @doc.pattern Test
 */
@Tag("integration [GH-90000]")
@Testcontainers
@DisplayName("PostgresKillSwitchService — integration tests [GH-90000]")
class PostgresKillSwitchServiceTest extends EventloopTestBase {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine [GH-90000]")
                    .withDatabaseName("aep_killswitch_test [GH-90000]")
                    .withUsername("aep_test [GH-90000]")
                    .withPassword("aep_test [GH-90000]");

    private HikariDataSource dataSource;
    private PostgresKillSwitchService service;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        HikariConfig config = new HikariConfig(); // GH-90000
        config.setJdbcUrl(POSTGRES.getJdbcUrl()); // GH-90000
        config.setUsername(POSTGRES.getUsername()); // GH-90000
        config.setPassword(POSTGRES.getPassword()); // GH-90000
        config.setMaximumPoolSize(5); // GH-90000
        dataSource = new HikariDataSource(config); // GH-90000

        try (Connection conn = dataSource.getConnection(); // GH-90000
             Statement stmt = conn.createStatement()) { // GH-90000
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS kill_switch_state ( // GH-90000
                        scope           VARCHAR(512) PRIMARY KEY, // GH-90000
                        active          BOOLEAN      NOT NULL DEFAULT FALSE,
                        reason          TEXT,
                        incident_id     VARCHAR(255), // GH-90000
                        activated_at    TIMESTAMPTZ,
                        deactivated_at  TIMESTAMPTZ,
                        updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW() // GH-90000
                    )
                    """);
            // Seed the global sentinel row
            stmt.execute("INSERT INTO kill_switch_state (scope, active) VALUES ('global', FALSE) ON CONFLICT DO NOTHING [GH-90000]");
        }

        service = new PostgresKillSwitchService(dataSource); // GH-90000
    }

    @AfterEach
    void tearDown() throws Exception { // GH-90000
        try (Connection conn = dataSource.getConnection(); // GH-90000
             Statement stmt = conn.createStatement()) { // GH-90000
            stmt.execute("DROP TABLE IF EXISTS kill_switch_state [GH-90000]");
        }
        dataSource.close(); // GH-90000
    }

    @Test
    @DisplayName("isActive returns false for a tenant with no kill-switch record [GH-90000]")
    void isActive_unknownTenant_returnsFalse() { // GH-90000
        boolean active = runPromise(() -> service.isActive("tenant-unknown [GH-90000]"));
        assertThat(active).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("activate sets isActive to true for the given tenant [GH-90000]")
    void activate_thenIsActive_returnsTrue() { // GH-90000
        runPromise(() -> service.activate("tenant-1", "security incident", "INC-001")); // GH-90000
        boolean active = runPromise(() -> service.isActive("tenant-1 [GH-90000]"));
        assertThat(active).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("deactivate after activate sets isActive back to false [GH-90000]")
    void deactivate_afterActivate_returnsFalse() { // GH-90000
        runPromise(() -> service.activate("tenant-2", "security incident", "INC-002")); // GH-90000
        runPromise(() -> service.deactivate("tenant-2", "incident resolved")); // GH-90000
        boolean active = runPromise(() -> service.isActive("tenant-2 [GH-90000]"));
        assertThat(active).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("activateGlobal sets global kill-switch to active [GH-90000]")
    void activateGlobal_setsGlobalActive() { // GH-90000
        runPromise(() -> service.activateGlobal("platform-wide incident", "INC-GLOBAL-001")); // GH-90000
        boolean globalActive = runPromise(() -> service.isGlobalActive()); // GH-90000
        assertThat(globalActive).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("activate and deactivate complete without error [GH-90000]")
    void activateDeactivate_completeWithoutError() { // GH-90000
        assertThatCode(() -> { // GH-90000
            runPromise(() -> service.activate("tenant-3", "test reason", "INC-003")); // GH-90000
            runPromise(() -> service.deactivate("tenant-3", "test deactivation")); // GH-90000
        }).doesNotThrowAnyException(); // GH-90000
    }
}

/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@Tag("integration")
@Testcontainers
@DisplayName("PostgresKillSwitchService — integration tests")
class PostgresKillSwitchServiceTest extends EventloopTestBase {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("aep_killswitch_test")
                    .withUsername("aep_test")
                    .withPassword("aep_test");

    private HikariDataSource dataSource;
    private PostgresKillSwitchService service;

    @BeforeEach
    void setUp() throws Exception { 
        HikariConfig config = new HikariConfig(); 
        config.setJdbcUrl(POSTGRES.getJdbcUrl()); 
        config.setUsername(POSTGRES.getUsername()); 
        config.setPassword(POSTGRES.getPassword()); 
        config.setMaximumPoolSize(5); 
        dataSource = new HikariDataSource(config); 

        try (Connection conn = dataSource.getConnection(); 
             Statement stmt = conn.createStatement()) { 
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS kill_switch_state (
                        scope           VARCHAR(512) PRIMARY KEY,
                        active          BOOLEAN      NOT NULL DEFAULT FALSE,
                        reason          TEXT,
                        incident_id     VARCHAR(255),
                        activated_at    TIMESTAMPTZ,
                        deactivated_at  TIMESTAMPTZ,
                        updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
                    )
                    """);
            // Seed the global sentinel row
            stmt.execute("INSERT INTO kill_switch_state (scope, active) VALUES ('global', FALSE) ON CONFLICT DO NOTHING");
        }

        service = new PostgresKillSwitchService(dataSource); 
    }

    @AfterEach
    void tearDown() throws Exception { 
        try (Connection conn = dataSource.getConnection(); 
             Statement stmt = conn.createStatement()) { 
            stmt.execute("DROP TABLE IF EXISTS kill_switch_state");
        }
        dataSource.close(); 
    }

    @Test
    @DisplayName("isActive returns false for a tenant with no kill-switch record")
    void isActive_unknownTenant_returnsFalse() { 
        boolean active = runPromise(() -> service.isActive("tenant-unknown"));
        assertThat(active).isFalse(); 
    }

    @Test
    @DisplayName("activate sets isActive to true for the given tenant")
    void activate_thenIsActive_returnsTrue() { 
        runPromise(() -> service.activate("tenant-1", "security incident", "INC-001")); 
        boolean active = runPromise(() -> service.isActive("tenant-1"));
        assertThat(active).isTrue(); 
    }

    @Test
    @DisplayName("deactivate after activate sets isActive back to false")
    void deactivate_afterActivate_returnsFalse() { 
        runPromise(() -> service.activate("tenant-2", "security incident", "INC-002")); 
        runPromise(() -> service.deactivate("tenant-2", "incident resolved")); 
        boolean active = runPromise(() -> service.isActive("tenant-2"));
        assertThat(active).isFalse(); 
    }

    @Test
    @DisplayName("activateGlobal sets global kill-switch to active")
    void activateGlobal_setsGlobalActive() { 
        runPromise(() -> service.activateGlobal("platform-wide incident", "INC-GLOBAL-001")); 
        boolean globalActive = runPromise(() -> service.isGlobalActive()); 
        assertThat(globalActive).isTrue(); 
    }

    @Test
    @DisplayName("activate and deactivate complete without error")
    void activateDeactivate_completeWithoutError() { 
        assertThatCode(() -> { 
            runPromise(() -> service.activate("tenant-3", "test reason", "INC-003")); 
            runPromise(() -> service.deactivate("tenant-3", "test deactivation")); 
        }).doesNotThrowAnyException(); 
    }
}

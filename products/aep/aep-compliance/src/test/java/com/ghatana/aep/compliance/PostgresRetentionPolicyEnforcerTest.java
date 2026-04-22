/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.compliance;

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
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link PostgresRetentionPolicyEnforcer} against a real
 * PostgreSQL container.
 *
 * @doc.type class
 * @doc.purpose Integration tests for PostgresRetentionPolicyEnforcer with Testcontainers
 * @doc.layer product
 * @doc.pattern Test
 */
@Tag("integration [GH-90000]")
@Testcontainers
@DisplayName("PostgresRetentionPolicyEnforcer — integration tests [GH-90000]")
class PostgresRetentionPolicyEnforcerTest extends EventloopTestBase {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine [GH-90000]")
                    .withDatabaseName("aep_retention_test [GH-90000]")
                    .withUsername("aep_test [GH-90000]")
                    .withPassword("aep_test [GH-90000]");

    private HikariDataSource dataSource;
    private PostgresRetentionPolicyEnforcer enforcer;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        HikariConfig config = new HikariConfig(); // GH-90000
        config.setJdbcUrl(POSTGRES.getJdbcUrl()); // GH-90000
        config.setUsername(POSTGRES.getUsername()); // GH-90000
        config.setPassword(POSTGRES.getPassword()); // GH-90000
        config.setMaximumPoolSize(5); // GH-90000
        dataSource = new HikariDataSource(config); // GH-90000

        initSchema(); // GH-90000
        enforcer = new PostgresRetentionPolicyEnforcer(dataSource); // GH-90000
    }

    @AfterEach
    void tearDown() throws Exception { // GH-90000
        try (Connection conn = dataSource.getConnection(); // GH-90000
             Statement stmt = conn.createStatement()) { // GH-90000
            stmt.execute("DROP TABLE IF EXISTS retention_policies [GH-90000]");
        }
        dataSource.close(); // GH-90000
    }

    @Test
    @DisplayName("registerRetention then checkRetention within TTL succeeds [GH-90000]")
    void registerRetention_checkWithinTtl_passes() { // GH-90000
        String tenantId = "tenant-1";
        String dataId   = "data-object-1";

        runPromise(() -> enforcer.registerRetention(tenantId, dataId, Duration.ofHours(1))); // GH-90000

        // Check within TTL — should not throw
        assertThatCode(() -> runPromise(() -> enforcer.checkRetention(tenantId, dataId))) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("checkRetention for unknown data passes (open policy) [GH-90000]")
    void checkRetention_unknownData_passes() { // GH-90000
        assertThatCode(() -> runPromise(() -> enforcer.checkRetention("tenant-x", "unknown-data"))) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("registerRetention with zero TTL causes immediate expiry [GH-90000]")
    void registerRetention_zeroTtl_isExpiredImmediately() { // GH-90000
        String tenantId = "tenant-2";
        String dataId   = "expired-data";

        runPromise(() -> enforcer.registerRetention(tenantId, dataId, Duration.ZERO)); // GH-90000

        // Check after zero-duration TTL — should throw RetentionExpiredException
        assertThatThrownBy(() -> runPromise(() -> enforcer.checkRetention(tenantId, dataId))) // GH-90000
                .isInstanceOf(RetentionExpiredException.class); // GH-90000
    }

    @Test
    @DisplayName("scheduleDeletion completes without error for registered data [GH-90000]")
    void scheduleDeletion_registeredData_completes() { // GH-90000
        String tenantId = "tenant-3";
        String dataId   = "to-delete";

        runPromise(() -> enforcer.registerRetention(tenantId, dataId, Duration.ofDays(30))); // GH-90000
        assertThatCode(() -> runPromise(() -> enforcer.scheduleDeletion(tenantId, dataId))) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("re-registering with longer TTL extends the deadline (upsert semantics) [GH-90000]")
    void registerRetention_upsert_extendsDeadline() { // GH-90000
        String tenantId = "tenant-4";
        String dataId   = "updatable-data";

        runPromise(() -> enforcer.registerRetention(tenantId, dataId, Duration.ZERO)); // GH-90000
        // Now overwrite with 1-hour TTL
        runPromise(() -> enforcer.registerRetention(tenantId, dataId, Duration.ofHours(1))); // GH-90000

        // After upsert the record should be within TTL
        assertThatCode(() -> runPromise(() -> enforcer.checkRetention(tenantId, dataId))) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    // ---- Schema helpers ----------------------------------------------------

    private void initSchema() throws Exception { // GH-90000
        try (Connection conn = dataSource.getConnection(); // GH-90000
             Statement stmt = conn.createStatement()) { // GH-90000
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS retention_policies ( // GH-90000
                    tenant_id              TEXT        NOT NULL,
                    data_id                TEXT        NOT NULL,
                    expires_at             TIMESTAMPTZ NOT NULL,
                    scheduled_for_deletion BOOLEAN     NOT NULL DEFAULT FALSE,
                    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(), // GH-90000
                    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(), // GH-90000
                    PRIMARY KEY (tenant_id, data_id) // GH-90000
                )
                """);
        }
    }
}

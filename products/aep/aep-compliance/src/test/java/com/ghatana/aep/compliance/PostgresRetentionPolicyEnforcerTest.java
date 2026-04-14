/*
 * Copyright (c) 2026 Ghatana Inc.
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
@Tag("integration")
@Testcontainers
@DisplayName("PostgresRetentionPolicyEnforcer — integration tests")
class PostgresRetentionPolicyEnforcerTest extends EventloopTestBase {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("aep_retention_test")
                    .withUsername("aep_test")
                    .withPassword("aep_test");

    private HikariDataSource dataSource;
    private PostgresRetentionPolicyEnforcer enforcer;

    @BeforeEach
    void setUp() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        config.setMaximumPoolSize(5);
        dataSource = new HikariDataSource(config);

        initSchema();
        enforcer = new PostgresRetentionPolicyEnforcer(dataSource);
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS retention_policies");
        }
        dataSource.close();
    }

    @Test
    @DisplayName("registerRetention then checkRetention within TTL succeeds")
    void registerRetention_checkWithinTtl_passes() {
        String tenantId = "tenant-1";
        String dataId   = "data-object-1";

        runPromise(() -> enforcer.registerRetention(tenantId, dataId, Duration.ofHours(1)));

        // Check within TTL — should not throw
        assertThatCode(() -> runPromise(() -> enforcer.checkRetention(tenantId, dataId)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("checkRetention for unknown data passes (open policy)")
    void checkRetention_unknownData_passes() {
        assertThatCode(() -> runPromise(() -> enforcer.checkRetention("tenant-x", "unknown-data")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("registerRetention with zero TTL causes immediate expiry")
    void registerRetention_zeroTtl_isExpiredImmediately() {
        String tenantId = "tenant-2";
        String dataId   = "expired-data";

        runPromise(() -> enforcer.registerRetention(tenantId, dataId, Duration.ZERO));

        // Check after zero-duration TTL — should throw RetentionExpiredException
        assertThatThrownBy(() -> runPromise(() -> enforcer.checkRetention(tenantId, dataId)))
                .isInstanceOf(RetentionExpiredException.class);
    }

    @Test
    @DisplayName("scheduleDeletion completes without error for registered data")
    void scheduleDeletion_registeredData_completes() {
        String tenantId = "tenant-3";
        String dataId   = "to-delete";

        runPromise(() -> enforcer.registerRetention(tenantId, dataId, Duration.ofDays(30)));
        assertThatCode(() -> runPromise(() -> enforcer.scheduleDeletion(tenantId, dataId)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("re-registering with longer TTL extends the deadline (upsert semantics)")
    void registerRetention_upsert_extendsDeadline() {
        String tenantId = "tenant-4";
        String dataId   = "updatable-data";

        runPromise(() -> enforcer.registerRetention(tenantId, dataId, Duration.ZERO));
        // Now overwrite with 1-hour TTL
        runPromise(() -> enforcer.registerRetention(tenantId, dataId, Duration.ofHours(1)));

        // After upsert the record should be within TTL
        assertThatCode(() -> runPromise(() -> enforcer.checkRetention(tenantId, dataId)))
                .doesNotThrowAnyException();
    }

    // ---- Schema helpers ----------------------------------------------------

    private void initSchema() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS retention_policies (
                    tenant_id              TEXT        NOT NULL,
                    data_id                TEXT        NOT NULL,
                    expires_at             TIMESTAMPTZ NOT NULL,
                    scheduled_for_deletion BOOLEAN     NOT NULL DEFAULT FALSE,
                    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    PRIMARY KEY (tenant_id, data_id)
                )
                """);
        }
    }
}

/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.compliance;

import com.ghatana.aep.audit.EventProcessingAuditService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for {@link DataRetentionAutomationService}: scan-and-expire behaviour,
 * audit trail emission, and lifecycle correctness.
 *
 * <p>Uses a real PostgreSQL container so that the SQL predicates and timestamp
 * semantics are exercised against an actual engine rather than stubs.
 *
 * @doc.type class
 * @doc.purpose Integration tests for DataRetentionAutomationService
 * @doc.layer product
 * @doc.pattern Test
 */
@Tag("integration")
@Testcontainers
@DisplayName("DataRetentionAutomationService — integration tests")
class DataRetentionAutomationServiceTest extends EventloopTestBase {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("aep_retention_auto_test")
                    .withUsername("aep_test")
                    .withPassword("aep_test");

    private HikariDataSource dataSource;
    private PostgresRetentionPolicyEnforcer retentionEnforcer;
    private EventProcessingAuditService auditService;
    private DataRetentionAutomationService automationService;

    @BeforeEach
    void setUp() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        config.setMaximumPoolSize(5);
        dataSource = new HikariDataSource(config);

        initSchema();

        retentionEnforcer = new PostgresRetentionPolicyEnforcer(dataSource);
        auditService = new EventProcessingAuditService();
        automationService = new DataRetentionAutomationService(
                dataSource, retentionEnforcer, auditService, Duration.ofDays(1));
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS retention_policies");
        }
        dataSource.close();
    }

    // ---- scanNow() correctness ---------------------------------------------

    @Nested
    @DisplayName("scanNow — scan result counts")
    class ScanResultTests {

        @Test
        @DisplayName("empty table returns zero counts")
        void scanNow_emptyTable_returnsZeroCounts() {
            DataRetentionAutomationService.RetentionScanResult result =
                    runPromise(() -> automationService.scanNow());

            assertThat(result.scanned()).isZero();
            assertThat(result.expired()).isZero();
            assertThat(result.scheduled()).isZero();
            assertThat(result.failed()).isZero();
            assertThat(result.hasExpired()).isFalse();
            assertThat(result.hasFailures()).isFalse();
        }

        @Test
        @DisplayName("data within retention window is not picked up by scan")
        void scanNow_dataWithinTtl_isNotExpired() throws Exception {
            insertRetentionRow("tenant-a", "live-data", Instant.now().plus(Duration.ofHours(1)), false);

            DataRetentionAutomationService.RetentionScanResult result =
                    runPromise(() -> automationService.scanNow());

            assertThat(result.scanned()).isZero();
            assertThat(result.expired()).isZero();
        }

        @Test
        @DisplayName("expired data is picked up, deletion scheduled, audit recorded")
        void scanNow_expiredData_schedulesForDeletionAndRecordsAudit() throws Exception {
            insertRetentionRow("tenant-b", "old-data", Instant.now().minus(Duration.ofHours(2)), false);

            DataRetentionAutomationService.RetentionScanResult result =
                    runPromise(() -> automationService.scanNow());

            assertThat(result.scanned()).isEqualTo(1);
            assertThat(result.expired()).isEqualTo(1);
            assertThat(result.scheduled()).isEqualTo(1);
            assertThat(result.failed()).isZero();
            assertThat(result.hasExpired()).isTrue();
            assertThat(result.hasFailures()).isFalse();

            // Verify audit trail was written
            List<EventProcessingAuditService.AuditEntry> auditEntries =
                    auditService.getAuditEntriesForEvent("tenant-b", "old-data");
            assertThat(auditEntries).hasSize(1);
            assertThat(auditEntries.get(0).decisionType()).isEqualTo("RETENTION_EXPIRY");
            assertThat(auditEntries.get(0).outcome()).isEqualTo("SCHEDULED_FOR_DELETION");
        }

        @Test
        @DisplayName("data already scheduled for deletion is skipped by scan")
        void scanNow_alreadyScheduled_isSkipped() throws Exception {
            // scheduled_for_deletion = TRUE — the WHERE clause excludes it
            insertRetentionRow("tenant-c", "already-gone", Instant.now().minus(Duration.ofDays(1)), true);

            DataRetentionAutomationService.RetentionScanResult result =
                    runPromise(() -> automationService.scanNow());

            assertThat(result.scanned()).isZero();
        }

        @Test
        @DisplayName("multiple expired rows are all processed in a single scan")
        void scanNow_multipleExpiredRows_allProcessed() throws Exception {
            insertRetentionRow("tenant-d", "data-1", Instant.now().minus(Duration.ofHours(3)), false);
            insertRetentionRow("tenant-d", "data-2", Instant.now().minus(Duration.ofHours(2)), false);
            insertRetentionRow("tenant-d", "data-3", Instant.now().minus(Duration.ofHours(1)), false);

            DataRetentionAutomationService.RetentionScanResult result =
                    runPromise(() -> automationService.scanNow());

            assertThat(result.scanned()).isEqualTo(3);
            assertThat(result.scheduled()).isEqualTo(3);
            assertThat(result.failed()).isZero();

            // Each expired item should have its own audit entry
            List<EventProcessingAuditService.AuditEntry> auditEntries =
                    auditService.getAuditEntries("tenant-d");
            assertThat(auditEntries).hasSize(3);
            assertThat(auditEntries)
                    .allMatch(e -> "RETENTION_EXPIRY".equals(e.decisionType()))
                    .allMatch(e -> "SCHEDULED_FOR_DELETION".equals(e.outcome()));
        }

        @Test
        @DisplayName("second scan skips rows already scheduled by first scan")
        void scanNow_secondScan_skipsAlreadyScheduled() throws Exception {
            insertRetentionRow("tenant-e", "item", Instant.now().minus(Duration.ofHours(1)), false);

            // First scan — should schedule
            DataRetentionAutomationService.RetentionScanResult first =
                    runPromise(() -> automationService.scanNow());
            assertThat(first.scheduled()).isEqualTo(1);

            // Second scan — the row is now scheduled_for_deletion=TRUE, skip it
            DataRetentionAutomationService.RetentionScanResult second =
                    runPromise(() -> automationService.scanNow());
            assertThat(second.scanned()).isZero();
        }
    }

    // ---- Lifecycle: start() / stop() ---------------------------------------

    @Nested
    @DisplayName("Lifecycle — start and stop")
    class LifecycleTests {

        @Test
        @DisplayName("start then stop completes without error")
        void startStop_noError() {
            assertThatCode(() -> {
                automationService.start();
                automationService.stop();
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("calling start() twice is safe (idempotent warning path)")
        void startTwice_idempotentNoError() {
            assertThatCode(() -> {
                automationService.start();
                automationService.start(); // second call logs a warning, must not throw
                automationService.stop();
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("calling stop() before start is safe (no-op)")
        void stopBeforeStart_noError() {
            assertThatCode(() -> automationService.stop()).doesNotThrowAnyException();
        }
    }

    // ---- Schema helper -----------------------------------------------------

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

    private void insertRetentionRow(String tenantId, String dataId,
                                    Instant expiresAt, boolean scheduledForDeletion) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 INSERT INTO retention_policies
                     (tenant_id, data_id, expires_at, scheduled_for_deletion)
                 VALUES (?, ?, ?, ?)
                 ON CONFLICT (tenant_id, data_id) DO UPDATE
                     SET expires_at = EXCLUDED.expires_at,
                         scheduled_for_deletion = EXCLUDED.scheduled_for_deletion
                 """)) {
            ps.setString(1, tenantId);
            ps.setString(2, dataId);
            ps.setTimestamp(3, Timestamp.from(expiresAt));
            ps.setBoolean(4, scheduledForDeletion);
            ps.executeUpdate();
        }
    }
}

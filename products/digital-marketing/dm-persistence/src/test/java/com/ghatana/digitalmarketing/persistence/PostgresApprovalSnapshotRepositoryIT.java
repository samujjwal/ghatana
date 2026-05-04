package com.ghatana.digitalmarketing.persistence;

import com.ghatana.digitalmarketing.domain.approval.ApprovalSnapshot;
import com.ghatana.digitalmarketing.domain.approval.ApprovalTargetType;
import com.ghatana.digitalmarketing.persistence.approval.PostgresApprovalSnapshotRepository;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link PostgresApprovalSnapshotRepository} using a real PostgreSQL container.
 *
 * @doc.type class
 * @doc.purpose Testcontainers integration test for PostgresApprovalSnapshotRepository (DMOS-P1-006)
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("PostgresApprovalSnapshotRepository — integration tests")
class PostgresApprovalSnapshotRepositoryIT extends EventloopTestBase {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("dmos_test")
            .withUsername("dmos")
            .withPassword("dmos_secret");

    private static PostgresApprovalSnapshotRepository repository;

    @BeforeAll
    static void migrateSchema() {
        Flyway flyway = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .load();
        flyway.migrate();

        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Executor executor = Runnable::run;
        repository = new PostgresApprovalSnapshotRepository(ds, executor);
    }

    @BeforeEach
    void cleanTable() {
        try (var conn = POSTGRES.createConnection("")) {
            conn.createStatement().executeUpdate("DELETE FROM dmos_approval_snapshots");
        } catch (Exception e) {
            throw new RuntimeException("Failed to clean dmos_approval_snapshots", e);
        }
    }

    private static ApprovalSnapshot buildSnapshot(
            String requestId, String workspaceId, ApprovalTargetType targetType, int riskLevel) {
        return new ApprovalSnapshot(
            requestId,
            targetType,
            "target-" + requestId,
            workspaceId,
            "Summary for " + requestId,
            null,
            riskLevel,
            "brand-manager",
            Instant.parse("2026-01-10T10:00:00Z"),
            0L
        );
    }

    @Test
    @DisplayName("save — persists a snapshot and returns it unchanged")
    void save_persistsSnapshot() {
        ApprovalSnapshot snapshot = buildSnapshot("req-1", "ws-a", ApprovalTargetType.STRATEGY, 3);

        ApprovalSnapshot saved = runPromise(() -> repository.save("ws-a", snapshot));

        assertThat(saved.requestId()).isEqualTo("req-1");
        assertThat(saved.targetType()).isEqualTo(ApprovalTargetType.STRATEGY);
        assertThat(saved.riskLevel()).isEqualTo(3);
        assertThat(saved.validationResultId()).isNull();
    }

    @Test
    @DisplayName("findByRequestId — returns saved snapshot")
    void findByRequestId_returnsSaved() {
        ApprovalSnapshot snapshot = buildSnapshot("req-2", "ws-b", ApprovalTargetType.BUDGET, 2);
        runPromise(() -> repository.save("ws-b", snapshot));

        Optional<ApprovalSnapshot> found =
            runPromise(() -> repository.findByRequestId("ws-b", "req-2"));

        assertThat(found).isPresent();
        assertThat(found.get().requestId()).isEqualTo("req-2");
        assertThat(found.get().targetType()).isEqualTo(ApprovalTargetType.BUDGET);
        assertThat(found.get().riskLevel()).isEqualTo(2);
        assertThat(found.get().snapshotSummary()).isEqualTo("Summary for req-2");
    }

    @Test
    @DisplayName("findByRequestId — returns empty when not found")
    void findByRequestId_returnsEmpty_whenNotFound() {
        Optional<ApprovalSnapshot> found =
            runPromise(() -> repository.findByRequestId("ws-x", "nonexistent"));

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findByRequestId — workspace isolation prevents cross-workspace access")
    void findByRequestId_workspaceIsolation() {
        ApprovalSnapshot snapshot = buildSnapshot("req-3", "ws-owner", ApprovalTargetType.SOW, 1);
        runPromise(() -> repository.save("ws-owner", snapshot));

        Optional<ApprovalSnapshot> found =
            runPromise(() -> repository.findByRequestId("ws-other", "req-3"));

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("save — idempotent: second save with same key is a no-op")
    void save_idempotent_doesNotOverwrite() {
        ApprovalSnapshot first = buildSnapshot("req-4", "ws-c", ApprovalTargetType.CAMPAIGN_LAUNCH, 4);
        runPromise(() -> repository.save("ws-c", first));

        ApprovalSnapshot second = new ApprovalSnapshot(
            "req-4", ApprovalTargetType.OVERRIDE, "different-target", "ws-c",
            "SHOULD NOT OVERWRITE", null, 5, "ceo", Instant.parse("2026-02-01T00:00:00Z"),
            0L
        );
        runPromise(() -> repository.save("ws-c", second));

        Optional<ApprovalSnapshot> found =
            runPromise(() -> repository.findByRequestId("ws-c", "req-4"));

        assertThat(found).isPresent();
        assertThat(found.get().snapshotSummary()).isEqualTo("Summary for req-4");
        assertThat(found.get().targetType()).isEqualTo(ApprovalTargetType.CAMPAIGN_LAUNCH);
    }

    @Test
    @DisplayName("save — preserves validationResultId when present")
    void save_preservesValidationResultId() {
        ApprovalSnapshot snapshot = new ApprovalSnapshot(
            "req-5", ApprovalTargetType.CONTENT_VERSION, "content-abc", "ws-d",
            "Content snapshot", "val-result-99", 2, "content-lead",
            Instant.parse("2026-03-01T09:00:00Z"),
            0L
        );
        runPromise(() -> repository.save("ws-d", snapshot));

        Optional<ApprovalSnapshot> found =
            runPromise(() -> repository.findByRequestId("ws-d", "req-5"));

        assertThat(found).isPresent();
        assertThat(found.get().validationResultId()).isEqualTo("val-result-99");
    }

    @Test
    @DisplayName("save — increments version on each update")
    void save_incrementsVersion() {
        ApprovalSnapshot snapshot = buildSnapshot("req-8", "ws-f", ApprovalTargetType.STRATEGY, 3);
        ApprovalSnapshot saved = runPromise(() -> repository.save("ws-f", snapshot));
        assertThat(saved.version()).isEqualTo(1L);

        ApprovalSnapshot updated = new ApprovalSnapshot(
            "req-8", ApprovalTargetType.STRATEGY, "target-req-8", "ws-f",
            "Updated summary", null, 3, "brand-manager",
            Instant.parse("2026-01-10T10:00:00Z"),
            1L
        );
        ApprovalSnapshot saved2 = runPromise(() -> repository.save("ws-f", updated));
        assertThat(saved2.version()).isEqualTo(2L);

        Optional<ApprovalSnapshot> found =
            runPromise(() -> repository.findByRequestId("ws-f", "req-8"));
        assertThat(found).isPresent();
        assertThat(found.get().version()).isEqualTo(2L);
    }

    @Test
    @DisplayName("save — throws optimistic lock exception when version mismatch")
    void save_throwsOptimisticLockException_onVersionMismatch() {
        ApprovalSnapshot snapshot = buildSnapshot("req-9", "ws-g", ApprovalTargetType.BUDGET, 2);
        ApprovalSnapshot saved = runPromise(() -> repository.save("ws-g", snapshot));
        assertThat(saved.version()).isEqualTo(1L);

        // Try to update with wrong version
        ApprovalSnapshot stale = new ApprovalSnapshot(
            "req-9", ApprovalTargetType.BUDGET, "target-req-9", "ws-g",
            "Stale update", null, 2, "brand-manager",
            Instant.parse("2026-01-10T10:00:00Z"),
            0L
        );

        org.junit.jupiter.api.Assertions.assertThrows(
            com.ghatana.digitalmarketing.persistence.campaign.DmPersistenceException.class,
            () -> runPromise(() -> repository.save("ws-g", stale))
        );
    }

    @Test
    @DisplayName("save — risk level boundaries 1 and 5 are accepted")
    void save_riskLevelBoundaries() {
        ApprovalSnapshot low = buildSnapshot("req-6", "ws-e", ApprovalTargetType.PROPOSAL, 1);
        ApprovalSnapshot high = buildSnapshot("req-7", "ws-e", ApprovalTargetType.OVERRIDE, 5);

        runPromise(() -> repository.save("ws-e", low));
        runPromise(() -> repository.save("ws-e", high));

        assertThat(runPromise(() -> repository.findByRequestId("ws-e", "req-6")))
            .isPresent().get().extracting(ApprovalSnapshot::riskLevel).isEqualTo(1);
        assertThat(runPromise(() -> repository.findByRequestId("ws-e", "req-7")))
            .isPresent().get().extracting(ApprovalSnapshot::riskLevel).isEqualTo(5);
    }
}

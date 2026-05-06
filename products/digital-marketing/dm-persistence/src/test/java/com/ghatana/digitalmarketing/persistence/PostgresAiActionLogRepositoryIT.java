package com.ghatana.digitalmarketing.persistence;

import com.ghatana.digitalmarketing.domain.transparency.AiActionLogEntry;
import com.ghatana.digitalmarketing.domain.transparency.AiActionStatus;
import com.ghatana.digitalmarketing.domain.transparency.AiActionType;
import com.ghatana.digitalmarketing.persistence.transparency.PostgresAiActionLogRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import io.activej.eventloop.Eventloop;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link PostgresAiActionLogRepository} using a real PostgreSQL container.
 *
 * @doc.type class
 * @doc.purpose Testcontainers integration test for PostgresAiActionLogRepository (DMOS-P1-006)
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("PostgresAiActionLogRepository — integration tests")
class PostgresAiActionLogRepositoryIT extends EventloopTestBase {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("dmos_test")
            .withUsername("dmos")
            .withPassword("dmos_secret");

    private static PostgresAiActionLogRepository repository;

    @BeforeAll
    static void migrateSchema() {
        Flyway flyway = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("filesystem:src/main/resources/db/migration")
            .load();
        flyway.migrate();

        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Executor executor = Runnable::run;
        repository = new PostgresAiActionLogRepository(ds, executor);
    }

    @BeforeEach
    void cleanTable() {
        try (var conn = POSTGRES.createConnection("")) {
            conn.createStatement().executeUpdate("DELETE FROM dmos_ai_action_log");
        } catch (Exception e) {
            throw new RuntimeException("Failed to clean dmos_ai_action_log", e);
        }
    }

    private static AiActionLogEntry buildEntry(
            String actionId,
            String workspaceId,
            String correlationId,
            AiActionType type,
            AiActionStatus status,
            String relatedEntityId,
            Double confidence,
            List<String> evidenceLinks) {
        return new AiActionLogEntry(
            actionId, workspaceId, correlationId, type, status,
            "system", true, confidence,
            evidenceLinks, List.of(),
            "Summary for " + actionId, "Details for " + actionId,
            relatedEntityId, Instant.parse("2026-01-10T10:00:00Z"),
            0L
        );
    }

    @Test
    @DisplayName("save — persists entry and returns it unchanged")
    void save_persistsEntry() {
        AiActionLogEntry entry = buildEntry(
            "act-1", "ws-a", "corr-1",
            AiActionType.RECOMMENDATION_GENERATED, AiActionStatus.PROPOSED,
            "entity-1", 0.87, List.of("https://evidence.example/1"));

        AiActionLogEntry saved = runPromise(() -> repository.save(entry));

        assertThat(saved.actionId()).isEqualTo("act-1");
        assertThat(saved.actionType()).isEqualTo(AiActionType.RECOMMENDATION_GENERATED);
        assertThat(saved.status()).isEqualTo(AiActionStatus.PROPOSED);
        assertThat(saved.confidence()).isEqualTo(0.87);
        assertThat(saved.evidenceLinks()).containsExactly("https://evidence.example/1");
    }

    @Test
    @DisplayName("findById — returns saved entry")
    void findById_returnsSaved() {
        AiActionLogEntry entry = buildEntry(
            "act-2", "ws-b", "corr-2",
            AiActionType.DRAFT_GENERATED, AiActionStatus.EXECUTED,
            null, null, List.of());
        runPromise(() -> repository.save(entry));

        Optional<AiActionLogEntry> found =
            runPromise(() -> repository.findById("ws-b", "act-2"));

        assertThat(found).isPresent();
        assertThat(found.get().actionId()).isEqualTo("act-2");
        assertThat(found.get().workspaceId()).isEqualTo("ws-b");
        assertThat(found.get().confidence()).isNull();
        assertThat(found.get().evidenceLinks()).isEmpty();
    }

    @Test
    @DisplayName("findById — returns empty when not found")
    void findById_returnsEmpty_whenNotFound() {
        Optional<AiActionLogEntry> found =
            runPromise(() -> repository.findById("ws-x", "nonexistent"));
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findById — workspace isolation prevents cross-workspace access")
    void findById_workspaceIsolation() {
        AiActionLogEntry entry = buildEntry(
            "act-3", "ws-owner", "corr-3",
            AiActionType.ACTION_EXECUTED, AiActionStatus.EXECUTED,
            null, null, List.of());
        runPromise(() -> repository.save(entry));

        Optional<AiActionLogEntry> found =
            runPromise(() -> repository.findById("ws-other", "act-3"));
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("save — idempotent: second save for same key is a no-op")
    void save_idempotent_doesNotOverwrite() {
        AiActionLogEntry first = buildEntry(
            "act-4", "ws-c", "corr-4",
            AiActionType.VALIDATION_RESULT, AiActionStatus.PROPOSED,
            null, null, List.of());
        runPromise(() -> repository.save(first));

        AiActionLogEntry duplicate = new AiActionLogEntry(
            "act-4", "ws-c", "corr-4",
            AiActionType.ACTION_BLOCKED, AiActionStatus.BLOCKED,
            "other-actor", false, null,
            List.of(), List.of(),
            "DIFFERENT SUMMARY", "DIFFERENT DETAILS", null,
            Instant.parse("2026-02-01T00:00:00Z"),
            0L
        );
        runPromise(() -> repository.save(duplicate));

        Optional<AiActionLogEntry> found =
            runPromise(() -> repository.findById("ws-c", "act-4"));
        assertThat(found).isPresent();
        assertThat(found.get().summary()).isEqualTo("Summary for act-4");
        assertThat(found.get().actionType()).isEqualTo(AiActionType.VALIDATION_RESULT);
    }

    @Test
    @DisplayName("save — increments version on each update")
    void save_incrementsVersion() {
        AiActionLogEntry entry = buildEntry(
            "act-8", "ws-f", "corr-8",
            AiActionType.DRAFT_GENERATED, AiActionStatus.EXECUTED,
            null, null, List.of());
        AiActionLogEntry saved = runPromise(() -> repository.save(entry));
        assertThat(saved.version()).isEqualTo(1L);

        AiActionLogEntry updated = new AiActionLogEntry(
            "act-8", "ws-f", "corr-8",
            AiActionType.DRAFT_GENERATED, AiActionStatus.EXECUTED,
            "system", true, null,
            List.of(), List.of(),
            "Updated summary", "Updated details", null,
            Instant.parse("2026-01-10T10:00:00Z"),
            1L
        );
        AiActionLogEntry saved2 = runPromise(() -> repository.save(updated));
        assertThat(saved2.version()).isEqualTo(2L);

        Optional<AiActionLogEntry> found =
            runPromise(() -> repository.findById("ws-f", "act-8"));
        assertThat(found).isPresent();
        assertThat(found.get().version()).isEqualTo(2L);
    }

    @Test
    @DisplayName("save — throws optimistic lock exception when version mismatch")
    void save_throwsOptimisticLockException_onVersionMismatch() {
        AiActionLogEntry entry = buildEntry(
            "act-9", "ws-g", "corr-9",
            AiActionType.ACTION_EXECUTED, AiActionStatus.EXECUTED,
            null, null, List.of());
        AiActionLogEntry saved = runPromise(() -> repository.save(entry));
        assertThat(saved.version()).isEqualTo(1L);

        // Try to update with wrong version (stored is 1, sending 2 = stale)
        AiActionLogEntry stale = new AiActionLogEntry(
            "act-9", "ws-g", "corr-9",
            AiActionType.ACTION_EXECUTED, AiActionStatus.EXECUTED,
            "system", true, null,
            List.of(), List.of(),
            "Stale update", "Stale details", null,
            Instant.parse("2026-01-10T10:00:00Z"),
            2L
        );

        org.junit.jupiter.api.Assertions.assertThrows(
            com.ghatana.digitalmarketing.persistence.campaign.DmPersistenceException.class,
            () -> runPromise(() -> repository.save(stale))
        );
    }

    @Test
    @DisplayName("findByWorkspace — returns all entries for workspace ordered by occurred_at desc")
    void findByWorkspace_returnsAllEntries() {
        String ws = "ws-query";
        runPromise(() -> repository.save(buildEntry("act-10", ws, "c-1",
            AiActionType.DRAFT_GENERATED, AiActionStatus.EXECUTED, null, null, List.of())));
        runPromise(() -> repository.save(buildEntry("act-11", ws, "c-2",
            AiActionType.RECOMMENDATION_GENERATED, AiActionStatus.PROPOSED, null, null, List.of())));
        runPromise(() -> repository.save(buildEntry("act-other", "ws-unrelated", "c-3",
            AiActionType.ACTION_EXECUTED, AiActionStatus.EXECUTED, null, null, List.of())));

        List<AiActionLogEntry> results =
            runPromise(() -> repository.findByWorkspace(ws, null, null, 50));

        assertThat(results).hasSize(2);
        assertThat(results).extracting(AiActionLogEntry::workspaceId)
            .allMatch(w -> w.equals(ws));
    }

    @Test
    @DisplayName("findByWorkspace — filters by correlationId when provided")
    void findByWorkspace_filtersByCorrelationId() {
        String ws = "ws-corr";
        runPromise(() -> repository.save(buildEntry("act-20", ws, "corr-match",
            AiActionType.DRAFT_GENERATED, AiActionStatus.EXECUTED, null, null, List.of())));
        runPromise(() -> repository.save(buildEntry("act-21", ws, "corr-other",
            AiActionType.APPROVAL_DECISION, AiActionStatus.APPROVED, null, null, List.of())));

        List<AiActionLogEntry> results =
            runPromise(() -> repository.findByWorkspace(ws, "corr-match", null, 50));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).actionId()).isEqualTo("act-20");
    }

    @Test
    @DisplayName("findByWorkspace — filters by relatedEntityId when provided")
    void findByWorkspace_filtersByRelatedEntityId() {
        String ws = "ws-entity";
        runPromise(() -> repository.save(buildEntry("act-30", ws, "c-1",
            AiActionType.ACTION_EXECUTED, AiActionStatus.EXECUTED, "entity-target", null, List.of())));
        runPromise(() -> repository.save(buildEntry("act-31", ws, "c-2",
            AiActionType.ACTION_BLOCKED, AiActionStatus.BLOCKED, "entity-other", null, List.of())));

        List<AiActionLogEntry> results =
            runPromise(() -> repository.findByWorkspace(ws, null, "entity-target", 50));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).actionId()).isEqualTo("act-30");
    }

    @Test
    @DisplayName("findByWorkspace — respects limit parameter")
    void findByWorkspace_respectsLimit() {
        String ws = "ws-limit";
        for (int i = 0; i < 5; i++) {
            String id = "act-lim-" + i;
            runPromise(() -> repository.save(buildEntry(
                id, ws, "c-lim", AiActionType.DRAFT_GENERATED, AiActionStatus.EXECUTED,
                null, null, List.of())));
        }

        List<AiActionLogEntry> results =
            runPromise(() -> repository.findByWorkspace(ws, null, null, 3));

        assertThat(results).hasSize(3);
    }

    @Test
    @DisplayName("save — null policyChecks and evidenceLinks round-trip as empty lists")
    void save_emptyArraysRoundTrip() {
        AiActionLogEntry entry = buildEntry(
            "act-arr-" + UUID.randomUUID(), "ws-arr", "c-arr",
            AiActionType.VALIDATION_RESULT, AiActionStatus.BLOCKED,
            null, null, List.of());

        runPromise(() -> repository.save(entry));
        Optional<AiActionLogEntry> found =
            runPromise(() -> repository.findById("ws-arr", entry.actionId()));

        assertThat(found).isPresent();
        assertThat(found.get().evidenceLinks()).isEmpty();
        assertThat(found.get().policyChecks()).isEmpty();
    }
}

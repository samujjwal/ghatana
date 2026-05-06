package com.ghatana.digitalmarketing.persistence.budget;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.budget.BudgetChannelAllocation;
import com.ghatana.digitalmarketing.domain.budget.BudgetRecommendation;
import com.ghatana.digitalmarketing.domain.budget.BudgetRecommendationStatus;
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
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link PostgresBudgetRecommendationRepository} using a real PostgreSQL container.
 *
 * <p>Tests are event-loop safe: all {@code Promise}-returning calls are executed
 * via {@link #runPromise(java.util.concurrent.Callable)}.</p>
 *
 * @doc.type class
 * @doc.purpose Testcontainers integration test for PostgresBudgetRecommendationRepository
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("PostgresBudgetRecommendationRepository — integration tests")
class PostgresBudgetRecommendationRepositoryIT extends EventloopTestBase {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("dmos_test")
            .withUsername("dmos")
            .withPassword("dmos_secret");

    private static PostgresBudgetRecommendationRepository repository;

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
        repository = new PostgresBudgetRecommendationRepository(ds, executor);
    }

    @BeforeEach
    void cleanTable() {
        try (var conn = POSTGRES.createConnection("")) {
            conn.createStatement().executeUpdate("DELETE FROM dmos_budget_recommendations");
        } catch (Exception e) {
            throw new RuntimeException("Failed to clean dmos_budget_recommendations table", e);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static BudgetRecommendation buildRecommendation(String recommendationId, String workspaceId) {
        BudgetChannelAllocation allocation = new BudgetChannelAllocation(
            "google_search",
            5000.0,
            166.67,
            "Primary search channel"
        );

        return BudgetRecommendation.builder()
            .recommendationId(recommendationId)
            .workspaceId(DmWorkspaceId.of(workspaceId))
            .strategyId("str-001")
            .totalMonthlyCap(10000.0)
            .changeThresholdPct(0.1)
            .channelAllocations(List.of(allocation))
            .rationale("Test rationale")
            .assumptions("Test assumptions")
            .modelVersion("v1")
            .status(BudgetRecommendationStatus.DRAFT)
            .generatedAt(Instant.parse("2026-01-01T00:00:00Z"))
            .generatedBy("test-user")
            .build();
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("save — persists a new recommendation and returns it unchanged")
    void save_persistsNewRecommendation() {
        BudgetRecommendation recommendation = buildRecommendation("rec-001", "ws-alpha");

        BudgetRecommendation saved = runPromise(() -> repository.save(recommendation));

        assertThat(saved.getRecommendationId()).isEqualTo("rec-001");
        assertThat(saved.getWorkspaceId().getValue()).isEqualTo("ws-alpha");
        assertThat(saved.getTotalMonthlyCap()).isEqualTo(10000.0);
        assertThat(saved.getStatus()).isEqualTo(BudgetRecommendationStatus.DRAFT);
    }

    @Test
    @DisplayName("findById — returns saved recommendation when it exists")
    void findById_returnsSavedRecommendation() {
        BudgetRecommendation recommendation = buildRecommendation("rec-002", "ws-beta");
        runPromise(() -> repository.save(recommendation));

        Optional<BudgetRecommendation> found = runPromise(() ->
            repository.findById("rec-002"));

        assertThat(found).isPresent();
        assertThat(found.get().getRecommendationId()).isEqualTo("rec-002");
        assertThat(found.get().getWorkspaceId().getValue()).isEqualTo("ws-beta");
        assertThat(found.get().getRationale()).isEqualTo("Test rationale");
        assertThat(found.get().getGeneratedBy()).isEqualTo("test-user");
    }

    @Test
    @DisplayName("findById — returns empty when recommendation does not exist")
    void findById_returnsEmpty_whenNotFound() {
        Optional<BudgetRecommendation> found = runPromise(() ->
            repository.findById("nonexistent"));

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findById — returns empty when workspace does not match")
    void findById_returnsEmpty_whenWorkspaceMismatch() {
        BudgetRecommendation recommendation = buildRecommendation("rec-003", "ws-gamma");
        runPromise(() -> repository.save(recommendation));

        Optional<BudgetRecommendation> found = runPromise(() ->
            repository.findById(DmWorkspaceId.of("ws-wrong"), "rec-003"));

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("save — idempotent upsert updates values on conflict")
    void save_upsert_updatesOnConflict() {
        BudgetRecommendation original = buildRecommendation("rec-004", "ws-delta");
        runPromise(() -> repository.save(original));

        BudgetChannelAllocation updatedAllocation = new BudgetChannelAllocation(
            "social_media",
            7500.0,
            250.0,
            "Social media ads"
        );

        BudgetRecommendation updated = BudgetRecommendation.builder()
            .recommendationId("rec-004")
            .workspaceId(DmWorkspaceId.of("ws-delta"))
            .strategyId("str-002")
            .totalMonthlyCap(15000.0)
            .changeThresholdPct(0.15)
            .channelAllocations(List.of(updatedAllocation))
            .rationale("Updated rationale")
            .assumptions("Updated assumptions")
            .modelVersion("v2")
            .status(BudgetRecommendationStatus.APPROVED)
            .generatedAt(Instant.parse("2026-02-01T00:00:00Z"))
            .generatedBy("updater")
            .approvedAt(Instant.parse("2026-02-02T00:00:00Z"))
            .approvedBy("approver")
            .build();
        runPromise(() -> repository.save(updated));

        Optional<BudgetRecommendation> found = runPromise(() ->
            repository.findById("rec-004"));
        assertThat(found).isPresent();
        assertThat(found.get().getTotalMonthlyCap()).isEqualTo(15000.0);
        assertThat(found.get().getRationale()).isEqualTo("Updated rationale");
    }

    @Test
    @DisplayName("findLatestByWorkspace — returns latest recommendation for workspace")
    void findLatestByWorkspace_returnsLatest() {
        BudgetRecommendation oldRec = buildRecommendation("rec-005", "ws-latest");
        runPromise(() -> repository.save(oldRec));

        BudgetRecommendation newRec = buildRecommendation("rec-006", "ws-latest");
        runPromise(() -> repository.save(newRec));

        Optional<BudgetRecommendation> found = runPromise(() ->
            repository.findLatestByWorkspace(DmWorkspaceId.of("ws-latest")));

        assertThat(found).isPresent();
        assertThat(found.get().getRecommendationId()).isEqualTo("rec-006");
    }

    @Test
    @DisplayName("findLatestByWorkspace — returns empty when workspace has no recommendations")
    void findLatestByWorkspace_returnsEmpty_whenNone() {
        Optional<BudgetRecommendation> found = runPromise(() ->
            repository.findLatestByWorkspace(DmWorkspaceId.of("ws-empty")));

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("save — recommendations in different workspaces are stored independently")
    void save_multipleWorkspaces_storedIndependently() {
        BudgetRecommendation r1 = buildRecommendation("rec-007", "ws-one");
        BudgetRecommendation r2 = buildRecommendation("rec-007", "ws-two");
        runPromise(() -> repository.save(r1));
        runPromise(() -> repository.save(r2));

        Optional<BudgetRecommendation> found1 = runPromise(() ->
            repository.findById(DmWorkspaceId.of("ws-one"), "rec-007"));
        Optional<BudgetRecommendation> found2 = runPromise(() ->
            repository.findById(DmWorkspaceId.of("ws-two"), "rec-007"));

        assertThat(found1).isPresent();
        assertThat(found2).isPresent();
        assertThat(found1.get().getWorkspaceId().getValue()).isEqualTo("ws-one");
        assertThat(found2.get().getWorkspaceId().getValue()).isEqualTo("ws-two");
    }
}

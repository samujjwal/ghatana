package com.ghatana.digitalmarketing.persistence.strategy;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.strategy.CampaignPlan;
import com.ghatana.digitalmarketing.domain.strategy.MarketingStrategy;
import com.ghatana.digitalmarketing.domain.strategy.StrategyChannel;
import com.ghatana.digitalmarketing.domain.strategy.StrategyGoal;
import com.ghatana.digitalmarketing.domain.strategy.StrategyStatus;
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
 * Integration tests for {@link PostgresMarketingStrategyRepository} using a real PostgreSQL container.
 *
 * <p>Tests are event-loop safe: all {@code Promise}-returning calls are executed
 * via {@link #runPromise(java.util.concurrent.Callable)}.</p>
 *
 * @doc.type class
 * @doc.purpose Testcontainers integration test for PostgresMarketingStrategyRepository
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("PostgresMarketingStrategyRepository — integration tests")
class PostgresMarketingStrategyRepositoryIT extends EventloopTestBase {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("dmos_test")
            .withUsername("dmos")
            .withPassword("dmos_secret");

    private static PostgresMarketingStrategyRepository repository;

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
        repository = new PostgresMarketingStrategyRepository(ds, executor);
    }

    @BeforeEach
    void cleanTable() {
        try (var conn = POSTGRES.createConnection("")) {
            conn.createStatement().executeUpdate("DELETE FROM dmos_marketing_strategies");
        } catch (Exception e) {
            throw new RuntimeException("Failed to clean dmos_marketing_strategies table", e);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static MarketingStrategy buildStrategy(String strategyId, String workspaceId) {
        StrategyGoal goal = new StrategyGoal("awareness", "Increase brand awareness by 20%", "20% increase", "surveys");
        CampaignPlan plan = new CampaignPlan(
            StrategyChannel.GOOGLE_SEARCH,
            "drive traffic",
            5000,
            List.of("keyword1", "keyword2"),
            List.of("message1", "message2")
        );

        return MarketingStrategy.builder()
            .strategyId(strategyId)
            .workspaceId(DmWorkspaceId.of(workspaceId))
            .status(StrategyStatus.DRAFT)
            .goals(List.of(goal))
            .channelPlans(List.of(plan))
            .budgetCap(10000)
            .rationale("Test rationale")
            .assumptions("Test assumptions")
            .measurementPlan("Test measurement plan")
            .contentPlan("Test content plan")
            .modelVersion("v1")
            .generatedAt(Instant.parse("2026-01-01T00:00:00Z"))
            .generatedBy("test-user")
            .build();
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("save — persists a new strategy and returns it unchanged")
    void save_persistsNewStrategy() {
        MarketingStrategy strategy = buildStrategy("str-001", "ws-alpha");

        MarketingStrategy saved = runPromise(() -> repository.save(strategy));

        assertThat(saved.getStrategyId()).isEqualTo("str-001");
        assertThat(saved.getWorkspaceId().getValue()).isEqualTo("ws-alpha");
        assertThat(saved.getStatus()).isEqualTo(StrategyStatus.DRAFT);
        assertThat(saved.getBudgetCap()).isEqualTo(10000.0);
    }

    @Test
    @DisplayName("findById — returns saved strategy when it exists")
    void findById_returnsSavedStrategy() {
        MarketingStrategy strategy = buildStrategy("str-002", "ws-beta");
        runPromise(() -> repository.save(strategy));

        Optional<MarketingStrategy> found = runPromise(() ->
            repository.findById(DmWorkspaceId.of("ws-beta"), "str-002"));

        assertThat(found).isPresent();
        assertThat(found.get().getStrategyId()).isEqualTo("str-002");
        assertThat(found.get().getWorkspaceId().getValue()).isEqualTo("ws-beta");
        assertThat(found.get().getRationale()).isEqualTo("Test rationale");
        assertThat(found.get().getGeneratedBy()).isEqualTo("test-user");
    }

    @Test
    @DisplayName("findById — returns empty when strategy does not exist")
    void findById_returnsEmpty_whenNotFound() {
        Optional<MarketingStrategy> found = runPromise(() ->
            repository.findById(DmWorkspaceId.of("ws-missing"), "nonexistent"));

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findById — returns empty when workspace does not match")
    void findById_returnsEmpty_whenWorkspaceMismatch() {
        MarketingStrategy strategy = buildStrategy("str-003", "ws-gamma");
        runPromise(() -> repository.save(strategy));

        Optional<MarketingStrategy> found = runPromise(() ->
            repository.findById(DmWorkspaceId.of("ws-wrong"), "str-003"));

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("save — idempotent upsert updates status on conflict")
    void save_upsert_updatesOnConflict() {
        MarketingStrategy original = buildStrategy("str-004", "ws-delta");
        runPromise(() -> repository.save(original));

        StrategyGoal updatedGoal = new StrategyGoal("conversion", "Increase conversion rate by 15%", "15% increase", "analytics");
        CampaignPlan updatedPlan = new CampaignPlan(
            StrategyChannel.LANDING_PAGE,
            "optimize landing page",
            7500,
            List.of("keyword3"),
            List.of("message3")
        );

        MarketingStrategy updated = MarketingStrategy.builder()
            .strategyId("str-004")
            .workspaceId(DmWorkspaceId.of("ws-delta"))
            .status(StrategyStatus.APPROVED)
            .goals(List.of(updatedGoal))
            .channelPlans(List.of(updatedPlan))
            .budgetCap(15000)
            .rationale("Updated rationale")
            .assumptions("Updated assumptions")
            .measurementPlan("Updated measurement plan")
            .contentPlan("Updated content plan")
            .modelVersion("v2")
            .generatedAt(Instant.parse("2026-02-01T00:00:00Z"))
            .generatedBy("updater")
            .approvedAt(Instant.parse("2026-02-02T00:00:00Z"))
            .approvedBy("approver")
            .build();
        runPromise(() -> repository.save(updated));

        Optional<MarketingStrategy> found = runPromise(() ->
            repository.findById(DmWorkspaceId.of("ws-delta"), "str-004"));
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(StrategyStatus.APPROVED);
        assertThat(found.get().getBudgetCap()).isEqualTo(15000.0);
        assertThat(found.get().getRationale()).isEqualTo("Updated rationale");
    }

    @Test
    @DisplayName("findLatestByWorkspace — returns latest strategy for workspace")
    void findLatestByWorkspace_returnsLatest() {
        MarketingStrategy oldStrategy = buildStrategy("str-005", "ws-latest");
        runPromise(() -> repository.save(oldStrategy));

        MarketingStrategy newStrategy = buildStrategy("str-006", "ws-latest");
        runPromise(() -> repository.save(newStrategy));

        Optional<MarketingStrategy> found = runPromise(() ->
            repository.findLatestByWorkspace(DmWorkspaceId.of("ws-latest")));

        assertThat(found).isPresent();
        assertThat(found.get().getStrategyId()).isEqualTo("str-006");
    }

    @Test
    @DisplayName("findLatestByWorkspace — returns empty when workspace has no strategies")
    void findLatestByWorkspace_returnsEmpty_whenNone() {
        Optional<MarketingStrategy> found = runPromise(() ->
            repository.findLatestByWorkspace(DmWorkspaceId.of("ws-empty")));

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("save — strategies in different workspaces are stored independently")
    void save_multipleWorkspaces_storedIndependently() {
        MarketingStrategy s1 = buildStrategy("str-007", "ws-one");
        MarketingStrategy s2 = buildStrategy("str-007", "ws-two");
        runPromise(() -> repository.save(s1));
        runPromise(() -> repository.save(s2));

        Optional<MarketingStrategy> found1 = runPromise(() ->
            repository.findById(DmWorkspaceId.of("ws-one"), "str-007"));
        Optional<MarketingStrategy> found2 = runPromise(() ->
            repository.findById(DmWorkspaceId.of("ws-two"), "str-007"));

        assertThat(found1).isPresent();
        assertThat(found2).isPresent();
        assertThat(found1.get().getWorkspaceId().getValue()).isEqualTo("ws-one");
        assertThat(found2.get().getWorkspaceId().getValue()).isEqualTo("ws-two");
    }
}

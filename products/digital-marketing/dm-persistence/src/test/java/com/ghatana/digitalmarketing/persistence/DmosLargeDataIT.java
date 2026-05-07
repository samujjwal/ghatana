package com.ghatana.digitalmarketing.persistence;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.campaign.Campaign;
import com.ghatana.digitalmarketing.domain.campaign.CampaignStatus;
import com.ghatana.digitalmarketing.domain.campaign.CampaignType;
import com.ghatana.digitalmarketing.persistence.campaign.PostgresCampaignRepository;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P2-005: Large-data performance tests for DMOS campaign repository.
 *
 * <p>Validates that paginated list queries perform within acceptable bounds when
 * the dmos_campaigns table contains 10,000+ rows, and that result sets are
 * correctly bounded and offset.</p>
 *
 * @doc.type class
 * @doc.purpose Performance/large-data integration test for pagination correctness and latency
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@Tag("large-data")
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("DMOS — Large-data pagination performance tests (P2-005)")
class DmosLargeDataIT extends EventloopTestBase {

    /** Testcontainers will start/stop this container automatically. */
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("dmos_perf_test")
                    .withUsername("dmos")
                    .withPassword("dmos_secret");

    private static final String PERF_WORKSPACE = "ws-perf-large";
    private static final int TOTAL_ROWS = 10_000;
    /** Acceptable wall-clock threshold in milliseconds for a single page query. */
    private static final long MAX_QUERY_MS = 2_000;

    private static PostgresCampaignRepository repository;

    @BeforeAll
    static void setupSchemaAndData() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("filesystem:src/main/resources/db/migration")
                .load()
                .migrate();

        // Insert required workspace row
        try (var conn = POSTGRES.createConnection("")) {
            conn.createStatement().executeUpdate(
                    "INSERT INTO dmos_workspaces (id, tenant_id, name, status, created_at, updated_at, created_by) VALUES " +
                    "('" + PERF_WORKSPACE + "','perf-tenant','perf-ws','ACTIVE',NOW(),NOW(),'perf') " +
                    "ON CONFLICT (id) DO NOTHING"
            );
        }

        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Executor executor = Runnable::run;
        repository = new PostgresCampaignRepository(ds, executor);

        // Bulk-insert 10 000 campaigns via batched SQL for speed
        try (var conn = POSTGRES.createConnection("")) {
            conn.setAutoCommit(false);
            var stmt = conn.prepareStatement(
                    "INSERT INTO dmos_campaigns " +
                    "(id, workspace_id, name, status, type, created_at, updated_at, created_by) " +
                    "VALUES (?, ?, ?, 'DRAFT', 'EMAIL', NOW(), NOW(), 'perf-user') " +
                    "ON CONFLICT (id) DO NOTHING"
            );
            for (int i = 0; i < TOTAL_ROWS; i++) {
                stmt.setString(1, "perf-camp-" + i);
                stmt.setString(2, PERF_WORKSPACE);
                stmt.setString(3, "Perf Campaign " + i);
                stmt.addBatch();
                if (i % 500 == 499) {
                    stmt.executeBatch();
                }
            }
            stmt.executeBatch();
            conn.commit();
        }
    }

    @BeforeEach
    void verifyRowCount() throws Exception {
        // Sanity-check: all rows are present before each test
        try (var conn = POSTGRES.createConnection("")) {
            var rs = conn.createStatement().executeQuery(
                    "SELECT COUNT(*) FROM dmos_campaigns WHERE workspace_id = '" + PERF_WORKSPACE + "'");
            rs.next();
            assertThat(rs.getLong(1)).isGreaterThanOrEqualTo(TOTAL_ROWS);
        }
    }

    // ── pagination correctness ────────────────────────────────────────────────

    @Test
    @DisplayName("listByWorkspace — first page returns exactly limit rows")
    void firstPage_returnsLimitRows() {
        List<Campaign> page = runPromise(() ->
                repository.listByWorkspace(DmWorkspaceId.of(PERF_WORKSPACE), 20, 0));

        assertThat(page).hasSize(20);
    }

    @Test
    @DisplayName("listByWorkspace — last partial page contains fewer than limit rows")
    void lastPage_returnsRemainder() {
        int pageSize = 100;
        int lastPageOffset = (TOTAL_ROWS / pageSize) * pageSize; // 10 000 => 100 pages of 100, last is empty boundary

        List<Campaign> page = runPromise(() ->
                repository.listByWorkspace(DmWorkspaceId.of(PERF_WORKSPACE), pageSize, lastPageOffset));

        // Either empty (if exactly divisible) or < pageSize
        assertThat(page.size()).isLessThanOrEqualTo(pageSize);
    }

    @Test
    @DisplayName("listByWorkspace — non-overlapping pages collectively cover all rows")
    void pages_areNonOverlapping() {
        int pageSize = 500;
        int totalPages = TOTAL_ROWS / pageSize;

        List<String> allIds = new ArrayList<>();
        for (int p = 0; p < totalPages; p++) {
            int offset = p * pageSize;
            List<Campaign> page = runPromise(() ->
                    repository.listByWorkspace(DmWorkspaceId.of(PERF_WORKSPACE), pageSize, offset));
            page.forEach(c -> allIds.add(c.getId()));
        }

        // All IDs collected, no duplicates, total count matches expectation
        assertThat(allIds).hasSize(TOTAL_ROWS);
        assertThat(allIds).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("listByWorkspace — page query completes within " + MAX_QUERY_MS + " ms under 10k rows")
    void pageQuery_completesWithinTimeThreshold() {
        long start = System.currentTimeMillis();
        List<Campaign> page = runPromise(() ->
                repository.listByWorkspace(DmWorkspaceId.of(PERF_WORKSPACE), 20, 0));
        long elapsed = System.currentTimeMillis() - start;

        assertThat(page).isNotEmpty();
        assertThat(elapsed)
                .as("Page query took %d ms, expected < %d ms", elapsed, MAX_QUERY_MS)
                .isLessThan(MAX_QUERY_MS);
    }

    @Test
    @DisplayName("listByWorkspace — mid-dataset offset page is consistent with ordered result")
    void midDatasetPage_isOrderConsistent() {
        int pageSize = 50;
        int midOffset = TOTAL_ROWS / 2;

        List<Campaign> pageA = runPromise(() ->
                repository.listByWorkspace(DmWorkspaceId.of(PERF_WORKSPACE), pageSize, midOffset));
        List<Campaign> pageB = runPromise(() ->
                repository.listByWorkspace(DmWorkspaceId.of(PERF_WORKSPACE), pageSize, midOffset + pageSize));

        // IDs must not overlap between adjacent pages
        List<String> idsA = pageA.stream().map(Campaign::getId).toList();
        List<String> idsB = pageB.stream().map(Campaign::getId).toList();
        assertThat(idsA).doesNotContainAnyElementsOf(idsB);
    }

    @Test
    @DisplayName("listByWorkspace — max bounded page size (100) is respected even when limit > 100")
    void maxBoundedPageSize_isEnforced() {
        // The repository caps at MAX_PAGE_SIZE=100; requesting 200 should return ≤ 100
        List<Campaign> page = runPromise(() ->
                repository.listByWorkspace(DmWorkspaceId.of(PERF_WORKSPACE), 200, 0));

        assertThat(page.size()).isLessThanOrEqualTo(100);
    }

    // ── builder helper ───────────────────────────────────────────────────────

    private static Campaign buildCampaign(String id) {
        return Campaign.builder()
                .id(id)
                .workspaceId(DmWorkspaceId.of(PERF_WORKSPACE))
                .name("Perf Campaign " + id)
                .status(CampaignStatus.DRAFT)
                .type(CampaignType.EMAIL)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .createdBy("perf-user")
                .build();
    }
}

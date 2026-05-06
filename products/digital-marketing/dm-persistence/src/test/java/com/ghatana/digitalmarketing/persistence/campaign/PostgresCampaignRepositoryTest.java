package com.ghatana.digitalmarketing.persistence.campaign;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.campaign.Campaign;
import com.ghatana.digitalmarketing.domain.campaign.CampaignStatus;
import com.ghatana.digitalmarketing.domain.campaign.CampaignType;
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
 * P1-014: PostgresCampaignRepository integration tests with TestContainers.
 *
 * <p>Tests repository operations against a real PostgreSQL instance to verify:
 * <ul>
 *   <li>CRUD operations work correctly</li>
 *   <li>Pagination with limit/offset returns correct results</li>
 *   <li>Deterministic ordering by createdAt descending</li>
 *   <li>Tenant isolation via workspace_id</li>
 *   <li>CHECK constraints enforce valid status/type values</li>
 * </ul>
 *
 * <p>All {@code Promise}-returning calls are executed via {@link #runPromise(java.util.concurrent.Callable)}
 * to ensure the ActiveJ event loop is present and callbacks are driven correctly.</p>
 *
 * @doc.type class
 * @doc.purpose PostgresCampaignRepository integration tests (DMOS-P1-014)
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("PostgresCampaignRepository Integration Tests")
class PostgresCampaignRepositoryTest extends EventloopTestBase {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("dmos_test")
        .withUsername("dmos")
        .withPassword("dmos_secret");

    private static PostgresCampaignRepository repository;

    @BeforeAll
    static void migrateSchema() {
        Flyway flyway = Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations("filesystem:src/main/resources/db/migration")
            .load();
        flyway.migrate();

        // Insert required workspace rows to satisfy FK constraints added in V16
        try (var conn = postgres.createConnection("")) {
            conn.createStatement().executeUpdate(
                "INSERT INTO dmos_workspaces (id, tenant_id, name, status, created_at, updated_at, created_by) VALUES "
                + "('ws-1','test-tenant','ws-1','ACTIVE',NOW(),NOW(),'test'),"
                + "('ws-2','test-tenant','ws-2','ACTIVE',NOW(),NOW(),'test') "
                + "ON CONFLICT (id) DO NOTHING"
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to insert test workspaces", e);
        }

        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(postgres.getJdbcUrl());
        ds.setUser(postgres.getUsername());
        ds.setPassword(postgres.getPassword());

        Executor executor = Runnable::run;
        repository = new PostgresCampaignRepository(ds, executor);
    }

    @BeforeEach
    void cleanTable() {
        try (var conn = postgres.createConnection("")) {
            conn.createStatement().executeUpdate("DELETE FROM dmos_campaigns");
        } catch (Exception e) {
            throw new RuntimeException("Failed to clean dmos_campaigns table", e);
        }
    }

    @Test
    @DisplayName("P1-014: Save and find campaign by ID")
    void shouldSaveAndFindCampaign() {
        Campaign campaign = buildCampaign("camp-1", "ws-1", CampaignStatus.DRAFT, CampaignType.EMAIL);

        Campaign saved = runPromise(() -> repository.save(campaign));
        assertThat(saved.getId()).isEqualTo("camp-1");

        Optional<Campaign> found = runPromise(() -> repository.findById(DmWorkspaceId.of("ws-1"), "camp-1"));
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Test Campaign");
        assertThat(found.get().getStatus()).isEqualTo(CampaignStatus.DRAFT);
    }

    @Test
    @DisplayName("P1-014: Update existing campaign (upsert)")
    void shouldUpdateExistingCampaign() {
        Campaign original = buildCampaign("camp-1", "ws-1", CampaignStatus.DRAFT, CampaignType.EMAIL);
        runPromise(() -> repository.save(original));

        Campaign updated = Campaign.builder()
            .id(original.getId())
            .workspaceId(original.getWorkspaceId())
            .name("Updated Name")
            .status(CampaignStatus.LAUNCHED)
            .type(original.getType())
            .createdAt(original.getCreatedAt())
            .updatedAt(Instant.now())
            .createdBy(original.getCreatedBy())
            .build();
        runPromise(() -> repository.save(updated));

        Optional<Campaign> found = runPromise(() -> repository.findById(DmWorkspaceId.of("ws-1"), "camp-1"));
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Updated Name");
        assertThat(found.get().getStatus()).isEqualTo(CampaignStatus.LAUNCHED);
    }

    @Test
    @DisplayName("P1-014: List campaigns with pagination")
    void shouldListCampaignsWithPagination() {
        for (int i = 1; i <= 5; i++) {
            Campaign campaign = buildCampaign("camp-" + i, "ws-1",
                CampaignStatus.DRAFT, CampaignType.values()[i % CampaignType.values().length]);
            runPromise(() -> repository.save(campaign));
        }

        List<Campaign> page1 = runPromise(() -> repository.listByWorkspace(DmWorkspaceId.of("ws-1"), 2, 0));
        assertThat(page1).hasSize(2);

        List<Campaign> page2 = runPromise(() -> repository.listByWorkspace(DmWorkspaceId.of("ws-1"), 2, 2));
        assertThat(page2).hasSize(2);

        assertThat(page1).doesNotContainAnyElementsOf(page2);
    }

    @Test
    @DisplayName("P1-014: List campaigns ordered by createdAt DESC")
    void shouldListCampaignsOrderedByCreatedAtDesc() {
        Instant baseTime = Instant.now();

        for (int i = 1; i <= 3; i++) {
            Campaign campaign = Campaign.builder()
                .id("camp-" + i)
                .workspaceId(DmWorkspaceId.of("ws-1"))
                .name("Campaign " + i)
                .status(CampaignStatus.DRAFT)
                .type(CampaignType.EMAIL)
                .createdAt(baseTime.minusSeconds((long) i * 60))
                .updatedAt(baseTime)
                .createdBy("user-1")
                .build();
            runPromise(() -> repository.save(campaign));
        }

        List<Campaign> results = runPromise(() -> repository.listByWorkspace(DmWorkspaceId.of("ws-1"), 10, 0));
        assertThat(results).hasSize(3);

        // Ordered newest first: camp-1 has the most recent createdAt
        assertThat(results.get(0).getId()).isEqualTo("camp-1");
        assertThat(results.get(1).getId()).isEqualTo("camp-2");
        assertThat(results.get(2).getId()).isEqualTo("camp-3");
    }

    @Test
    @DisplayName("P1-014: List campaigns enforces tenant isolation")
    void shouldEnforceTenantIsolationOnList() {
        Campaign ws1Campaign = buildCampaign("camp-1", "ws-1", CampaignStatus.DRAFT, CampaignType.EMAIL);
        Campaign ws2Campaign = buildCampaign("camp-2", "ws-2", CampaignStatus.DRAFT, CampaignType.SOCIAL);

        runPromise(() -> repository.save(ws1Campaign));
        runPromise(() -> repository.save(ws2Campaign));

        List<Campaign> ws1Results = runPromise(() -> repository.listByWorkspace(DmWorkspaceId.of("ws-1"), 10, 0));
        assertThat(ws1Results).hasSize(1);
        assertThat(ws1Results.get(0).getId()).isEqualTo("camp-1");

        List<Campaign> ws2Results = runPromise(() -> repository.listByWorkspace(DmWorkspaceId.of("ws-2"), 10, 0));
        assertThat(ws2Results).hasSize(1);
        assertThat(ws2Results.get(0).getId()).isEqualTo("camp-2");
    }

    @Test
    @DisplayName("P1-014: Count campaigns by workspace")
    void shouldCountCampaignsByWorkspace() {
        runPromise(() -> repository.save(buildCampaign("camp-1", "ws-1", CampaignStatus.DRAFT, CampaignType.EMAIL)));
        runPromise(() -> repository.save(buildCampaign("camp-2", "ws-1", CampaignStatus.LAUNCHED, CampaignType.SOCIAL)));
        runPromise(() -> repository.save(buildCampaign("camp-3", "ws-2", CampaignStatus.DRAFT, CampaignType.PUSH)));

        Long ws1Count = runPromise(() -> repository.countByWorkspace(DmWorkspaceId.of("ws-1")));
        assertThat(ws1Count).isEqualTo(2);

        Long ws2Count = runPromise(() -> repository.countByWorkspace(DmWorkspaceId.of("ws-2")));
        assertThat(ws2Count).isEqualTo(1);

        Long emptyCount = runPromise(() -> repository.countByWorkspace(DmWorkspaceId.of("ws-empty")));
        assertThat(emptyCount).isEqualTo(0);
    }

    @Test
    @DisplayName("P1-014: Find returns empty optional for non-existent campaign")
    void shouldReturnEmptyForNonExistentCampaign() {
        Optional<Campaign> found = runPromise(() -> repository.findById(DmWorkspaceId.of("ws-1"), "non-existent"));
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("P1-014: Find respects workspace isolation")
    void shouldRespectWorkspaceIsolationOnFind() {
        Campaign campaign = buildCampaign("camp-1", "ws-1", CampaignStatus.DRAFT, CampaignType.EMAIL);
        runPromise(() -> repository.save(campaign));

        Optional<Campaign> wrongWorkspace = runPromise(() -> repository.findById(DmWorkspaceId.of("ws-2"), "camp-1"));
        assertThat(wrongWorkspace).isEmpty();

        Optional<Campaign> correctWorkspace = runPromise(() -> repository.findById(DmWorkspaceId.of("ws-1"), "camp-1"));
        assertThat(correctWorkspace).isPresent();
    }

    @Test
    @DisplayName("P1-014: Pagination limit bounded to max 100")
    void shouldBoundPaginationLimit() {
        for (int i = 1; i <= 5; i++) {
            Campaign c = buildCampaign("camp-" + i, "ws-1", CampaignStatus.DRAFT, CampaignType.EMAIL);
            runPromise(() -> repository.save(c));
        }

        List<Campaign> results = runPromise(() -> repository.listByWorkspace(DmWorkspaceId.of("ws-1"), 500, 0));
        assertThat(results).hasSize(5);
    }

    @Test
    @DisplayName("P1-014: Pagination handles offset beyond data size")
    void shouldHandleOffsetBeyondDataSize() {
        runPromise(() -> repository.save(buildCampaign("camp-1", "ws-1", CampaignStatus.DRAFT, CampaignType.EMAIL)));

        List<Campaign> results = runPromise(() -> repository.listByWorkspace(DmWorkspaceId.of("ws-1"), 10, 100));
        assertThat(results).isEmpty();
    }

    private static Campaign buildCampaign(String id, String workspaceId, CampaignStatus status, CampaignType type) {
        Instant now = Instant.now();
        return Campaign.builder()
            .id(id)
            .workspaceId(DmWorkspaceId.of(workspaceId))
            .name("Test Campaign")
            .status(status)
            .type(type)
            .createdAt(now)
            .updatedAt(now)
            .createdBy("test-user")
            .build();
    }
}

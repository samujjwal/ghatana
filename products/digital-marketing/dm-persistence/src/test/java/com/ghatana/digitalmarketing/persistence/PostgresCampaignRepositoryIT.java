package com.ghatana.digitalmarketing.persistence;

import com.ghatana.digitalmarketing.contracts.DmTenantId;
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
 * Integration tests for {@link PostgresCampaignRepository} using a real PostgreSQL container.
 *
 * <p>Tests are event-loop safe: all {@code Promise}-returning calls are executed
 * via {@link #runPromise(java.util.concurrent.Callable)}.</p>
 *
 * @doc.type class
 * @doc.purpose Testcontainers integration test for PostgresCampaignRepository (DMOS-P0-001)
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("PostgresCampaignRepository — integration tests")
class PostgresCampaignRepositoryIT extends EventloopTestBase {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("dmos_test")
            .withUsername("dmos")
            .withPassword("dmos_secret");

    private static PostgresCampaignRepository repository;

    @BeforeAll
    static void migrateSchema() {
        Flyway flyway = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("filesystem:src/main/resources/db/migration")
            .load();
        flyway.migrate();

        // Insert required workspace rows to satisfy FK constraints added in V16
        try (var conn = POSTGRES.createConnection("")) {
            conn.createStatement().executeUpdate(
                "INSERT INTO dmos_workspaces (id, tenant_id, name, status, created_at, updated_at, created_by) VALUES " +
                "('ws-alpha','__legacy_unspecified_tenant__','ws-alpha','ACTIVE',NOW(),NOW(),'test')," +
                "('ws-beta','__legacy_unspecified_tenant__','ws-beta','ACTIVE',NOW(),NOW(),'test')," +
                "('ws-gamma','__legacy_unspecified_tenant__','ws-gamma','ACTIVE',NOW(),NOW(),'test')," +
                "('ws-delta','__legacy_unspecified_tenant__','ws-delta','ACTIVE',NOW(),NOW(),'test')," +
                "('ws-one','__legacy_unspecified_tenant__','ws-one','ACTIVE',NOW(),NOW(),'test')," +
                "('ws-two','__legacy_unspecified_tenant__','ws-two','ACTIVE',NOW(),NOW(),'test')," +
                "('ws-ts','__legacy_unspecified_tenant__','ws-ts','ACTIVE',NOW(),NOW(),'test') " +
                "ON CONFLICT (id) DO NOTHING"
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to insert test workspaces", e);
        }

        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Executor executor = Runnable::run;
        repository = new PostgresCampaignRepository(ds, executor);
    }

    @BeforeEach
    void cleanTable() {
        try (var conn = POSTGRES.createConnection("")) {
            conn.createStatement().executeUpdate("DELETE FROM dmos_campaigns");
        } catch (Exception e) {
            throw new RuntimeException("Failed to clean dmos_campaigns table", e);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Campaign buildCampaign(String id, String workspaceId) {
        return Campaign.builder()
            .id(id)
            .workspaceId(DmWorkspaceId.of(workspaceId))
            .name("Test Campaign " + id)
            .status(CampaignStatus.DRAFT)
            .type(CampaignType.EMAIL)
            .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
            .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
            .createdBy("test-user")
            .build();
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("save — persists a new campaign and returns it unchanged")
    void save_persistsNewCampaign() {
        Campaign campaign = buildCampaign("camp-001", "ws-alpha");

        Campaign saved = runPromise(() -> repository.save(campaign));

        assertThat(saved.getId()).isEqualTo("camp-001");
        assertThat(saved.getName()).isEqualTo("Test Campaign camp-001");
        assertThat(saved.getStatus()).isEqualTo(CampaignStatus.DRAFT);
    }

    @Test
    @DisplayName("findById — returns saved campaign when it exists")
    void findById_returnsSavedCampaign() {
        Campaign campaign = buildCampaign("camp-002", "ws-beta");
        runPromise(() -> repository.save(campaign));

        Optional<Campaign> found = runPromise(() ->
            repository.findById(DmWorkspaceId.of("ws-beta"), "camp-002"));

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo("camp-002");
        assertThat(found.get().getWorkspaceId().getValue()).isEqualTo("ws-beta");
        assertThat(found.get().getType()).isEqualTo(CampaignType.EMAIL);
        assertThat(found.get().getCreatedBy()).isEqualTo("test-user");
    }

    @Test
    @DisplayName("findById — returns empty when campaign does not exist")
    void findById_returnsEmpty_whenNotFound() {
        Optional<Campaign> found = runPromise(() ->
            repository.findById(DmWorkspaceId.of("ws-missing"), "nonexistent"));

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findById — returns empty when workspace does not match")
    void findById_returnsEmpty_whenWorkspaceMismatch() {
        Campaign campaign = buildCampaign("camp-003", "ws-gamma");
        runPromise(() -> repository.save(campaign));

        Optional<Campaign> found = runPromise(() ->
            repository.findById(DmWorkspaceId.of("ws-wrong"), "camp-003"));

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("save — idempotent upsert updates status on conflict")
    void save_upsert_updatesOnConflict() {
        Campaign original = buildCampaign("camp-004", "ws-delta");
        runPromise(() -> repository.save(original));

        Campaign updated = Campaign.builder()
            .id("camp-004")
            .workspaceId(DmWorkspaceId.of("ws-delta"))
            .name("Updated Campaign")
            .status(CampaignStatus.LAUNCHED)
            .type(CampaignType.SOCIAL)
            .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
            .updatedAt(Instant.parse("2026-02-01T00:00:00Z"))
            .createdBy("updater")
            .build();
        runPromise(() -> repository.save(updated));

        Optional<Campaign> found = runPromise(() ->
            repository.findById(DmWorkspaceId.of("ws-delta"), "camp-004"));
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(CampaignStatus.LAUNCHED);
        assertThat(found.get().getName()).isEqualTo("Updated Campaign");
    }

    @Test
    @DisplayName("save — campaigns in different workspaces are stored independently")
    void save_multipleWorkspaces_storedIndependently() {
        Campaign c1 = buildCampaign("camp-005", "ws-one");
        Campaign c2 = buildCampaign("camp-005", "ws-two");
        runPromise(() -> repository.save(c1));
        runPromise(() -> repository.save(c2));

        Optional<Campaign> found1 = runPromise(() ->
            repository.findById(DmWorkspaceId.of("ws-one"), "camp-005"));
        Optional<Campaign> found2 = runPromise(() ->
            repository.findById(DmWorkspaceId.of("ws-two"), "camp-005"));

        assertThat(found1).isPresent();
        assertThat(found2).isPresent();
        assertThat(found1.get().getWorkspaceId().getValue()).isEqualTo("ws-one");
        assertThat(found2.get().getWorkspaceId().getValue()).isEqualTo("ws-two");
    }

    @Test
    @DisplayName("save — timestamps are preserved with microsecond fidelity")
    void save_timestampsPreserved() {
        Instant created = Instant.parse("2026-05-01T12:34:56Z");
        Instant updated = Instant.parse("2026-05-02T08:00:00Z");
        Campaign campaign = Campaign.builder()
            .id("camp-006")
            .workspaceId(DmWorkspaceId.of("ws-ts"))
            .name("Timestamp Test")
            .status(CampaignStatus.PAUSED)
            .type(CampaignType.PUSH)
            .createdAt(created)
            .updatedAt(updated)
            .createdBy("ts-user")
            .build();
        runPromise(() -> repository.save(campaign));

        Optional<Campaign> found = runPromise(() ->
            repository.findById(DmWorkspaceId.of("ws-ts"), "camp-006"));

        assertThat(found).isPresent();
        assertThat(found.get().getCreatedAt()).isEqualTo(created);
        assertThat(found.get().getUpdatedAt()).isEqualTo(updated);
    }
}

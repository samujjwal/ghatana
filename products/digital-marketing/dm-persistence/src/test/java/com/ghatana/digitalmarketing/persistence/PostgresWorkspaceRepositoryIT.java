package com.ghatana.digitalmarketing.persistence;

import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.workspace.Workspace;
import com.ghatana.digitalmarketing.domain.workspace.WorkspaceStatus;
import com.ghatana.digitalmarketing.persistence.workspace.PostgresWorkspaceRepository;
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
 * Integration tests for {@link PostgresWorkspaceRepository} using a real PostgreSQL container.
 *
 * @doc.type class
 * @doc.purpose Testcontainers integration test for PostgresWorkspaceRepository (DMOS-P0-002)
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("PostgresWorkspaceRepository — integration tests")
class PostgresWorkspaceRepositoryIT extends EventloopTestBase {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("dmos_ws_test")
            .withUsername("dmos")
            .withPassword("dmos_secret");

    private static PostgresWorkspaceRepository repository;

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
        repository = new PostgresWorkspaceRepository(ds, executor);
    }

    @BeforeEach
    void cleanTable() {
        try (var conn = POSTGRES.createConnection("")) {
            conn.createStatement().executeUpdate("DELETE FROM dmos_workspaces");
        } catch (Exception e) {
            throw new RuntimeException("Failed to clean dmos_workspaces table", e);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Workspace buildWorkspace(String id, String tenantId) {
        return Workspace.builder()
            .id(DmWorkspaceId.of(id))
            .tenantId(DmTenantId.of(tenantId))
            .name("Workspace " + id)
            .description("Test workspace for " + tenantId)
            .status(WorkspaceStatus.ACTIVE)
            .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
            .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
            .createdBy("test-admin")
            .build();
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("save — persists a new workspace and returns it unchanged")
    void save_persistsNewWorkspace() {
        Workspace workspace = buildWorkspace("ws-001", "tenant-alpha");

        Workspace saved = runPromise(() -> repository.save(workspace));

        assertThat(saved.getId().getValue()).isEqualTo("ws-001");
        assertThat(saved.getTenantId().getValue()).isEqualTo("tenant-alpha");
        assertThat(saved.getStatus()).isEqualTo(WorkspaceStatus.ACTIVE);
    }

    @Test
    @DisplayName("findById — returns saved workspace when it exists")
    void findById_returnsSavedWorkspace() {
        Workspace workspace = buildWorkspace("ws-002", "tenant-beta");
        runPromise(() -> repository.save(workspace));

        Optional<Workspace> found = runPromise(() ->
            repository.findById(DmTenantId.of("tenant-beta"), DmWorkspaceId.of("ws-002")));

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Workspace ws-002");
        assertThat(found.get().getDescription()).isEqualTo("Test workspace for tenant-beta");
        assertThat(found.get().getCreatedBy()).isEqualTo("test-admin");
    }

    @Test
    @DisplayName("findById — returns empty when workspace does not exist")
    void findById_returnsEmpty_whenNotFound() {
        Optional<Workspace> found = runPromise(() ->
            repository.findById(DmTenantId.of("tenant-missing"), DmWorkspaceId.of("ws-nonexistent")));

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findById — returns empty when tenant does not match")
    void findById_returnsEmpty_whenTenantMismatch() {
        Workspace workspace = buildWorkspace("ws-003", "tenant-gamma");
        runPromise(() -> repository.save(workspace));

        Optional<Workspace> found = runPromise(() ->
            repository.findById(DmTenantId.of("tenant-wrong"), DmWorkspaceId.of("ws-003")));

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("listByTenant — returns all workspaces for the given tenant")
    void listByTenant_returnsAllForTenant() {
        runPromise(() -> repository.save(buildWorkspace("ws-004a", "tenant-delta")));
        runPromise(() -> repository.save(buildWorkspace("ws-004b", "tenant-delta")));
        runPromise(() -> repository.save(buildWorkspace("ws-004c", "tenant-other")));

        List<Workspace> forDelta = runPromise(() ->
            repository.listByTenant(DmTenantId.of("tenant-delta")));

        assertThat(forDelta).hasSize(2);
        assertThat(forDelta).allMatch(w -> w.getTenantId().getValue().equals("tenant-delta"));
    }

    @Test
    @DisplayName("listByTenant — returns empty list when tenant has no workspaces")
    void listByTenant_returnsEmpty_whenNoWorkspaces() {
        List<Workspace> result = runPromise(() ->
            repository.listByTenant(DmTenantId.of("tenant-none")));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("save — upsert preserves tenant isolation")
    void save_upsert_preservesTenantIsolation() {
        Workspace original = buildWorkspace("ws-005", "tenant-epsilon");
        runPromise(() -> repository.save(original));

        Workspace updated = Workspace.builder()
            .id(DmWorkspaceId.of("ws-005"))
            .tenantId(DmTenantId.of("tenant-epsilon"))
            .name("Updated Workspace")
            .description("New description")
            .status(WorkspaceStatus.SUSPENDED)
            .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
            .updatedAt(Instant.parse("2026-06-01T00:00:00Z"))
            .createdBy("admin")
            .build();
        runPromise(() -> repository.save(updated));

        Optional<Workspace> found = runPromise(() ->
            repository.findById(DmTenantId.of("tenant-epsilon"), DmWorkspaceId.of("ws-005")));
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(WorkspaceStatus.SUSPENDED);
        assertThat(found.get().getName()).isEqualTo("Updated Workspace");
        assertThat(found.get().getDescription()).isEqualTo("New description");
    }

    @Test
    @DisplayName("save — default empty string for description when not provided")
    void save_defaultDescription_whenEmpty() {
        Workspace workspace = Workspace.builder()
            .id(DmWorkspaceId.of("ws-006"))
            .tenantId(DmTenantId.of("tenant-zeta"))
            .name("No Description Workspace")
            .status(WorkspaceStatus.ACTIVE)
            .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
            .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
            .createdBy("admin")
            .build();
        runPromise(() -> repository.save(workspace));

        Optional<Workspace> found = runPromise(() ->
            repository.findById(DmTenantId.of("tenant-zeta"), DmWorkspaceId.of("ws-006")));
        assertThat(found).isPresent();
        assertThat(found.get().getDescription()).isEqualTo("");
    }
}

package com.ghatana.digitalmarketing.infra.workspace;

import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.workspace.Workspace;
import com.ghatana.digitalmarketing.domain.workspace.WorkspaceStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("EphemeralWorkspaceRepository")
class EphemeralWorkspaceRepositoryTest extends EventloopTestBase {

    private EphemeralWorkspaceRepository repository;

    private static final DmTenantId TENANT_A = DmTenantId.of("tenant-a");
    private static final DmTenantId TENANT_B = DmTenantId.of("tenant-b");
    private static final DmWorkspaceId WS_1   = DmWorkspaceId.of("ws-1");
    private static final DmWorkspaceId WS_2   = DmWorkspaceId.of("ws-2");

    @BeforeEach
    void setUp() {
        repository = new EphemeralWorkspaceRepository();
    }

    @Test
    @DisplayName("save returns the saved workspace")
    void shouldReturnSavedWorkspace() {
        Workspace ws = buildWorkspace(TENANT_A, WS_1, "Acme");
        Workspace saved = runPromise(() -> repository.save(ws));
        assertThat(saved).isSameAs(ws);
    }

    @Test
    @DisplayName("findById returns saved workspace within same tenant")
    void shouldFindSavedWorkspace() {
        Workspace ws = buildWorkspace(TENANT_A, WS_1, "Acme");
        runPromise(() -> repository.save(ws));

        Optional<Workspace> found = runPromise(() -> repository.findById(TENANT_A, WS_1));

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(WS_1);
    }

    @Test
    @DisplayName("findById returns empty when workspace does not exist")
    void shouldReturnEmptyForMissingWorkspace() {
        Optional<Workspace> found = runPromise(() -> repository.findById(TENANT_A, WS_1));
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("tenant isolation: workspace saved under tenant-a is not visible to tenant-b")
    void shouldIsolateTenants() {
        runPromise(() -> repository.save(buildWorkspace(TENANT_A, WS_1, "Acme")));

        Optional<Workspace> found = runPromise(() -> repository.findById(TENANT_B, WS_1));

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("save updates an existing workspace")
    void shouldOverwriteExistingWorkspace() {
        Workspace original = buildWorkspace(TENANT_A, WS_1, "Acme");
        Workspace updated  = buildWorkspace(TENANT_A, WS_1, "Acme Updated");

        runPromise(() -> repository.save(original));
        runPromise(() -> repository.save(updated));

        Optional<Workspace> found = runPromise(() -> repository.findById(TENANT_A, WS_1));
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Acme Updated");
    }

    @Test
    @DisplayName("listByTenant returns only workspaces for the requested tenant")
    void shouldListOnlyForRequestedTenant() {
        runPromise(() -> repository.save(buildWorkspace(TENANT_A, WS_1, "Acme 1")));
        runPromise(() -> repository.save(buildWorkspace(TENANT_A, WS_2, "Acme 2")));
        runPromise(() -> repository.save(buildWorkspace(TENANT_B, WS_1, "Beta")));

        List<Workspace> tenantAWorkspaces = runPromise(() -> repository.listByTenant(TENANT_A));

        assertThat(tenantAWorkspaces).hasSize(2);
        assertThat(tenantAWorkspaces).allMatch(w -> w.getTenantId().equals(TENANT_A));
    }

    @Test
    @DisplayName("listByTenant returns empty list when tenant has no workspaces")
    void shouldReturnEmptyListForUnknownTenant() {
        List<Workspace> result = runPromise(() -> repository.listByTenant(TENANT_B));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("save rejects null workspace")
    void shouldRejectNullWorkspace() {
        assertThatNullPointerException()
            .isThrownBy(() -> repository.save(null));
    }

    @Test
    @DisplayName("findById rejects null arguments")
    void shouldRejectNullFindByIdArgs() {
        assertThatNullPointerException()
            .isThrownBy(() -> repository.findById(null, WS_1));
        assertThatNullPointerException()
            .isThrownBy(() -> repository.findById(TENANT_A, null));
    }

    @Test
    @DisplayName("listByTenant rejects null tenantId")
    void shouldRejectNullListByTenantArg() {
        assertThatNullPointerException()
            .isThrownBy(() -> repository.listByTenant(null));
    }

    private static Workspace buildWorkspace(DmTenantId tenantId, DmWorkspaceId workspaceId, String name) {
        Instant now = Instant.now();
        return Workspace.builder()
            .id(workspaceId)
            .tenantId(tenantId)
            .name(name)
            .status(WorkspaceStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .createdBy("test-user")
            .build();
    }
}

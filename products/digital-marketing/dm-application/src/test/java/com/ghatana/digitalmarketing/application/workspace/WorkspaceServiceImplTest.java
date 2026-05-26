package com.ghatana.digitalmarketing.application.workspace;

import com.ghatana.digitalmarketing.application.capabilities.DmosCapabilityRegistry;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.workspace.Workspace;
import com.ghatana.digitalmarketing.domain.workspace.WorkspaceStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("WorkspaceServiceImpl")
class WorkspaceServiceImplTest extends EventloopTestBase {

    private RecordingKernelAdapter kernelAdapter;
    private EphemeralWorkspaceRepository repository;
    private WorkspaceServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        kernelAdapter = new RecordingKernelAdapter();
        repository = new EphemeralWorkspaceRepository();
        service = new WorkspaceServiceImpl(kernelAdapter, repository);

        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("root"))
            .actor(ActorRef.user("user-42"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();

        kernelAdapter.setDefaultAuthorization(true);
    }

    @Test
    @DisplayName("constructor throws on null dependencies")
    void shouldRejectNullDependencies() {
        assertThatNullPointerException()
            .isThrownBy(() -> new WorkspaceServiceImpl(null, repository));
        assertThatNullPointerException()
            .isThrownBy(() -> new WorkspaceServiceImpl(kernelAdapter, null));
    }

    @Test
    @DisplayName("createWorkspace creates active workspace and records audit")
    void shouldCreateWorkspace() {
        Workspace created = runPromise(() -> service.createWorkspace(
            ctx,
            new WorkspaceService.CreateWorkspaceCommand("My Workspace", "Description")));

        assertThat(created.getStatus()).isEqualTo(WorkspaceStatus.ACTIVE);
        assertThat(created.getId().getValue()).isNotBlank();
        assertThat(created.getTenantId()).isEqualTo(DmTenantId.of("tenant-1"));
        assertThat(created.getName()).isEqualTo("My Workspace");
        assertThat(created.getDescription()).isEqualTo("Description");
        assertThat(created.getCreatedBy()).isEqualTo("user-42");
        assertThat(kernelAdapter.auditActions()).contains("create");
    }

    @Test
    @DisplayName("createWorkspace rejects when not authorized")
    void shouldDenyCreateWhenNotAuthorized() {
        kernelAdapter.setAuthorization("workspaces/*", "write", false);

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.createWorkspace(
                ctx,
                new WorkspaceService.CreateWorkspaceCommand("Denied", null))));
    }

    @Test
    @DisplayName("createWorkspace command rejects blank name")
    void shouldRejectBlankWorkspaceName() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new WorkspaceService.CreateWorkspaceCommand("", null));
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new WorkspaceService.CreateWorkspaceCommand(null, null));
    }

    @Test
    @DisplayName("getWorkspace returns workspace for valid tenant")
    void shouldGetWorkspace() {
        Workspace created = runPromise(() -> service.createWorkspace(
            ctx,
            new WorkspaceService.CreateWorkspaceCommand("Readable", null)));

        Workspace found = runPromise(() -> service.getWorkspace(ctx, created.getId().getValue()));

        assertThat(found.getId()).isEqualTo(created.getId());
        assertThat(found.getName()).isEqualTo("Readable");
    }

    @Test
    @DisplayName("getWorkspace throws when workspace not found")
    void shouldThrowWhenWorkspaceNotFound() {
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.getWorkspace(ctx, "missing")));
    }

    @Test
    @DisplayName("getWorkspace enforces read authorization")
    void shouldDenyReadWhenNotAuthorized() {
        Workspace created = runPromise(() -> service.createWorkspace(
            ctx,
            new WorkspaceService.CreateWorkspaceCommand("SecuredWS", null)));

        kernelAdapter.setAuthorization("workspaces/" + created.getId().getValue(), "read", false);

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.getWorkspace(ctx, created.getId().getValue())));
    }

    @Test
    @DisplayName("listWorkspaces returns all workspaces for tenant")
    void shouldListWorkspaces() {
        runPromise(() -> service.createWorkspace(ctx,
            new WorkspaceService.CreateWorkspaceCommand("WS-A", null)));
        runPromise(() -> service.createWorkspace(ctx,
            new WorkspaceService.CreateWorkspaceCommand("WS-B", null)));

        List<Workspace> list = runPromise(() -> service.listWorkspaces(ctx));

        assertThat(list).hasSize(2);
        assertThat(list).extracting(Workspace::getName).containsExactlyInAnyOrder("WS-A", "WS-B");
    }

    @Test
    @DisplayName("listWorkspaces enforces read authorization")
    void shouldDenyListWhenNotAuthorized() {
        kernelAdapter.setAuthorization("workspaces/*", "read", false);

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.listWorkspaces(ctx)));
    }

    @Test
    @DisplayName("suspendWorkspace transitions ACTIVE to SUSPENDED and records audit")
    void shouldSuspendActiveWorkspace() {
        Workspace created = runPromise(() -> service.createWorkspace(ctx,
            new WorkspaceService.CreateWorkspaceCommand("ToSuspend", null)));

        Workspace suspended = runPromise(() -> service.suspendWorkspace(ctx, created.getId().getValue()));

        assertThat(suspended.getStatus()).isEqualTo(WorkspaceStatus.SUSPENDED);
        assertThat(kernelAdapter.auditActions()).contains("suspend");
    }

    @Test
    @DisplayName("reactivateWorkspace transitions SUSPENDED to ACTIVE and records audit")
    void shouldReactivateSuspendedWorkspace() {
        Workspace created = runPromise(() -> service.createWorkspace(ctx,
            new WorkspaceService.CreateWorkspaceCommand("ToReactivate", null)));
        runPromise(() -> service.suspendWorkspace(ctx, created.getId().getValue()));

        Workspace reactivated = runPromise(
            () -> service.reactivateWorkspace(ctx, created.getId().getValue()));

        assertThat(reactivated.getStatus()).isEqualTo(WorkspaceStatus.ACTIVE);
        assertThat(kernelAdapter.auditActions()).contains("reactivate");
    }

    @Test
    @DisplayName("suspendWorkspace throws when workspace not found")
    void shouldThrowWhenSuspendingMissingWorkspace() {
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.suspendWorkspace(ctx, "missing")));
    }

    @Test
    @DisplayName("reactivateWorkspace throws on non-suspended workspace")
    void shouldThrowWhenReactivatingActiveWorkspace() {
        Workspace created = runPromise(() -> service.createWorkspace(ctx,
            new WorkspaceService.CreateWorkspaceCommand("Active", null)));

        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> runPromise(
                () -> service.reactivateWorkspace(ctx, created.getId().getValue())));
    }

    @Test
    @DisplayName("getWorkspace throws IllegalArgumentException when workspaceId is blank")
    void shouldThrowOnBlankWorkspaceId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> service.getWorkspace(ctx, "  ")));
    }

    @Test
    @DisplayName("suspendWorkspace rejects when not authorized")
    void shouldDenySuspendWhenNotAuthorized() {
        Workspace created = runPromise(() -> service.createWorkspace(ctx,
            new WorkspaceService.CreateWorkspaceCommand("ToSuspendDenied", null)));

        kernelAdapter.setAuthorization("workspaces/" + created.getId().getValue(), "suspend", false);

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.suspendWorkspace(ctx, created.getId().getValue())));
    }

    @Test
    @DisplayName("reactivateWorkspace rejects when not authorized")
    void shouldDenyReactivateWhenNotAuthorized() {
        Workspace created = runPromise(() -> service.createWorkspace(ctx,
            new WorkspaceService.CreateWorkspaceCommand("ToReactivateDenied", null)));
        runPromise(() -> service.suspendWorkspace(ctx, created.getId().getValue()));

        kernelAdapter.setAuthorization("workspaces/" + created.getId().getValue(), "reactivate", false);

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.reactivateWorkspace(ctx, created.getId().getValue())));
    }

    @Test
    @DisplayName("active workspace enables connector and release-readiness runtime capabilities")
    void shouldEnableRuntimeTruthCapabilitiesForActiveWorkspace() {
        Workspace created = runPromise(() -> service.createWorkspace(ctx,
            new WorkspaceService.CreateWorkspaceCommand("Runtime Truth", null)));

        List<WorkspaceService.WorkspaceCapability> capabilities = runPromise(
            () -> service.getWorkspaceCapabilities(ctx, created.getId().getValue()));

        assertThat(capabilities)
            .filteredOn(WorkspaceService.WorkspaceCapability::enabled)
            .extracting(WorkspaceService.WorkspaceCapability::key)
            .contains(DmosCapabilityRegistry.CONNECTORS, DmosCapabilityRegistry.RELEASE_READINESS);
    }

    // -----------------------------------------------------------------------
    // Test doubles
    // -----------------------------------------------------------------------

    private static final class EphemeralWorkspaceRepository implements WorkspaceRepository {
        private final ConcurrentHashMap<String, Workspace> store = new ConcurrentHashMap<>();

        @Override
        public Promise<Workspace> save(Workspace workspace) {
            store.put(workspace.getTenantId().getValue() + ":" + workspace.getId().getValue(), workspace);
            return Promise.of(workspace);
        }

        @Override
        public Promise<Optional<Workspace>> findById(DmTenantId tenantId, DmWorkspaceId workspaceId) {
            return Promise.of(Optional.ofNullable(
                store.get(tenantId.getValue() + ":" + workspaceId.getValue())));
        }

        @Override
        public Promise<List<Workspace>> listByTenant(DmTenantId tenantId) {
            List<Workspace> result = store.entrySet().stream()
                .filter(e -> e.getKey().startsWith(tenantId.getValue() + ":"))
                .map(Map.Entry::getValue)
                .toList();
            return Promise.of(result);
        }
    }

    private static final class RecordingKernelAdapter implements DigitalMarketingKernelAdapter {
        private final ConcurrentHashMap<String, Boolean> decisionMap = new ConcurrentHashMap<>();
        private volatile boolean defaultAuthorization = true;
        private final List<String> auditActions = new ArrayList<>();

        void setDefaultAuthorization(boolean allowed) {
            defaultAuthorization = allowed;
        }

        void setAuthorization(String resource, String action, boolean allowed) {
            decisionMap.put(resource + "|" + action, allowed);
        }

        List<String> auditActions() {
            return auditActions;
        }

        @Override
        public void start() { }

        @Override
        public void stop() { }

        @Override
        public Promise<Boolean> isAuthorized(DmOperationContext context, String resource, String action) {
            return Promise.of(decisionMap.getOrDefault(resource + "|" + action, defaultAuthorization));
        }

        @Override
        public Promise<Boolean> verifyConsent(DmOperationContext context, String subjectId, String purpose) {
            return Promise.of(true);
        }

        @Override
        public Promise<String> requestApproval(
                DmOperationContext context,
                String operationType,
                String subjectId,
                String description) {
            return Promise.of("approval-1");
        }

        @Override
        public Promise<String> recordAudit(
                DmOperationContext context,
                String entityId,
                String action,
                Map<String, Object> attributes) {
            auditActions.add(action);
            return Promise.of("audit-1");
        }
    }
}

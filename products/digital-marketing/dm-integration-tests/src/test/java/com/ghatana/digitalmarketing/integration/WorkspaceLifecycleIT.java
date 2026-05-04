package com.ghatana.digitalmarketing.integration;

import com.ghatana.digitalmarketing.application.workspace.WorkspaceRepository;
import com.ghatana.digitalmarketing.application.workspace.WorkspaceService;
import com.ghatana.digitalmarketing.application.workspace.WorkspaceServiceImpl;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.workspace.Workspace;
import com.ghatana.digitalmarketing.domain.workspace.WorkspaceStatus;
import com.ghatana.digitalmarketing.persistence.workspace.PostgresWorkspaceRepository;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Integration tests for the DMOS workspace lifecycle using PostgreSQL.
 *
 * <p>Covers: create, get, list, suspend, reactivate, and authorization enforcement
 * across the full application-service stack with real PostgreSQL persistence.</p>
 *
 * <p>P1: PostgreSQL integration test configuration.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Workspace Lifecycle Integration (PostgreSQL)")
class WorkspaceLifecycleIT extends EventloopTestBase {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("dmos")
            .withUsername("dmos")
            .withPassword("dmos_password");

    private RecordingKernelAdapter kernelAdapter;
    private WorkspaceRepository repository;
    private WorkspaceService workspaceService;
    private DmOperationContext writeCtx;
    private DmOperationContext readCtx;
    private Eventloop eventloop;

    @BeforeEach
    void setUp() {
        eventloop = Eventloop.create();
        DataSource dataSource = createPostgresDataSource();
        repository = new PostgresWorkspaceRepository(dataSource, eventloop);
        kernelAdapter = new RecordingKernelAdapter();
        workspaceService = new WorkspaceServiceImpl(kernelAdapter, repository);

        writeCtx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-ws-it"))
            .workspaceId(DmWorkspaceId.of("root"))
            .actor(ActorRef.user("owner-1"))
            .correlationId(DmCorrelationId.generate())
            .idempotencyKey(DmIdempotencyKey.generate())
            .build();

        readCtx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-ws-it"))
            .workspaceId(DmWorkspaceId.of("root"))
            .actor(ActorRef.user("owner-1"))
            .correlationId(DmCorrelationId.generate())
            .build();

        kernelAdapter.setDefaultAuthorization(true);
    }

    private DataSource createPostgresDataSource() {
        org.postgresql.ds.PGSimpleDataSource dataSource = new org.postgresql.ds.PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    // -----------------------------------------------------------------------
    // Full lifecycle path
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("full lifecycle: create, get, list, suspend, reactivate")
    void shouldExecuteFullWorkspaceLifecycle() {
        // Create
        Workspace created = runPromise(() -> workspaceService.createWorkspace(
            writeCtx,
            new WorkspaceService.CreateWorkspaceCommand("Main Workspace", "Primary brand workspace")));

        assertThat(created.getStatus()).isEqualTo(WorkspaceStatus.ACTIVE);
        assertThat(created.getId().getValue()).isNotBlank();
        assertThat(created.getTenantId()).isEqualTo(DmTenantId.of("tenant-ws-it"));
        assertThat(created.getName()).isEqualTo("Main Workspace");
        assertThat(created.getDescription()).isEqualTo("Primary brand workspace");
        assertThat(created.getCreatedBy()).isEqualTo("owner-1");

        // Get
        Workspace fetched = runPromise(() -> workspaceService.getWorkspace(readCtx, created.getId().getValue()));
        assertThat(fetched.getId()).isEqualTo(created.getId());
        assertThat(fetched.getStatus()).isEqualTo(WorkspaceStatus.ACTIVE);

        // List
        List<Workspace> list = runPromise(() -> workspaceService.listWorkspaces(readCtx));
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getId()).isEqualTo(created.getId());

        // Suspend
        Workspace suspended = runPromise(() -> workspaceService.suspendWorkspace(writeCtx, created.getId().getValue()));
        assertThat(suspended.getStatus()).isEqualTo(WorkspaceStatus.SUSPENDED);

        // Reactivate
        Workspace reactivated = runPromise(() -> workspaceService.reactivateWorkspace(writeCtx, created.getId().getValue()));
        assertThat(reactivated.getStatus()).isEqualTo(WorkspaceStatus.ACTIVE);
    }

    @Test
    @DisplayName("audit records emitted for each state-changing operation")
    void shouldRecordAuditForEachStateChange() {
        Workspace created = runPromise(() -> workspaceService.createWorkspace(
            writeCtx,
            new WorkspaceService.CreateWorkspaceCommand("Audited Workspace", null)));

        runPromise(() -> workspaceService.suspendWorkspace(writeCtx, created.getId().getValue()));
        runPromise(() -> workspaceService.reactivateWorkspace(writeCtx, created.getId().getValue()));

        assertThat(kernelAdapter.auditActions()).containsExactly("create", "suspend", "reactivate");
    }

    // -----------------------------------------------------------------------
    // Tenant isolation
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("workspaces are isolated by tenant")
    void shouldIsolateWorkspacesByTenant() {
        DmOperationContext tenantA = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-A"))
            .workspaceId(DmWorkspaceId.of("root"))
            .actor(ActorRef.user("user-A"))
            .correlationId(DmCorrelationId.generate())
            .idempotencyKey(DmIdempotencyKey.generate())
            .build();

        DmOperationContext tenantARead = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-A"))
            .workspaceId(DmWorkspaceId.of("root"))
            .actor(ActorRef.user("user-A"))
            .correlationId(DmCorrelationId.generate())
            .build();

        DmOperationContext tenantB = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-B"))
            .workspaceId(DmWorkspaceId.of("root"))
            .actor(ActorRef.user("user-B"))
            .correlationId(DmCorrelationId.generate())
            .idempotencyKey(DmIdempotencyKey.generate())
            .build();

        DmOperationContext tenantBRead = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-B"))
            .workspaceId(DmWorkspaceId.of("root"))
            .actor(ActorRef.user("user-B"))
            .correlationId(DmCorrelationId.generate())
            .build();

        runPromise(() -> workspaceService.createWorkspace(tenantA,
            new WorkspaceService.CreateWorkspaceCommand("WS-A-1", null)));
        runPromise(() -> workspaceService.createWorkspace(tenantA,
            new WorkspaceService.CreateWorkspaceCommand("WS-A-2", null)));
        runPromise(() -> workspaceService.createWorkspace(tenantB,
            new WorkspaceService.CreateWorkspaceCommand("WS-B-1", null)));

        List<Workspace> forA = runPromise(() -> workspaceService.listWorkspaces(tenantARead));
        List<Workspace> forB = runPromise(() -> workspaceService.listWorkspaces(tenantBRead));

        assertThat(forA).hasSize(2);
        assertThat(forB).hasSize(1);
        assertThat(forA).extracting(Workspace::getName).containsExactlyInAnyOrder("WS-A-1", "WS-A-2");
        assertThat(forB).extracting(Workspace::getName).containsExactly("WS-B-1");
    }

    // -----------------------------------------------------------------------
    // Authorization enforcement
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("create is denied when not authorized")
    void shouldDenyCreateWhenNotAuthorized() {
        kernelAdapter.setAuthorization("workspaces/*", "write", false);

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> workspaceService.createWorkspace(
                writeCtx,
                new WorkspaceService.CreateWorkspaceCommand("Blocked", null))));
    }

    @Test
    @DisplayName("get is denied when not authorized for specific workspace")
    void shouldDenyGetWhenNotAuthorized() {
        Workspace created = runPromise(() -> workspaceService.createWorkspace(
            writeCtx,
            new WorkspaceService.CreateWorkspaceCommand("Protected", null)));

        kernelAdapter.setAuthorization("workspaces/" + created.getId().getValue(), "read", false);

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> workspaceService.getWorkspace(
                readCtx, created.getId().getValue())));
    }

    @Test
    @DisplayName("list is denied when not authorized")
    void shouldDenyListWhenNotAuthorized() {
        kernelAdapter.setAuthorization("workspaces/*", "read", false);

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> workspaceService.listWorkspaces(readCtx)));
    }

    @Test
    @DisplayName("suspend is denied when not authorized")
    void shouldDenySuspendWhenNotAuthorized() {
        Workspace created = runPromise(() -> workspaceService.createWorkspace(
            writeCtx,
            new WorkspaceService.CreateWorkspaceCommand("CannotSuspend", null)));

        kernelAdapter.setAuthorization("workspaces/" + created.getId().getValue(), "suspend", false);

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> workspaceService.suspendWorkspace(
                writeCtx, created.getId().getValue())));
    }

    @Test
    @DisplayName("reactivate is denied when not authorized")
    void shouldDenyReactivateWhenNotAuthorized() {
        Workspace created = runPromise(() -> workspaceService.createWorkspace(
            writeCtx,
            new WorkspaceService.CreateWorkspaceCommand("CannotReactivate", null)));
        runPromise(() -> workspaceService.suspendWorkspace(writeCtx, created.getId().getValue()));

        kernelAdapter.setAuthorization("workspaces/" + created.getId().getValue(), "reactivate", false);

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> workspaceService.reactivateWorkspace(
                writeCtx, created.getId().getValue())));
    }

    // -----------------------------------------------------------------------
    // Not-found path
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("get throws NoSuchElementException for non-existent workspace")
    void shouldThrowWhenWorkspaceNotFound() {
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> workspaceService.getWorkspace(readCtx, "missing-id")));
    }

    @Test
    @DisplayName("suspend throws NoSuchElementException for non-existent workspace")
    void shouldThrowWhenSuspendingMissingWorkspace() {
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> workspaceService.suspendWorkspace(writeCtx, "missing-id")));
    }

    @Test
    @DisplayName("reactivate throws NoSuchElementException for non-existent workspace")
    void shouldThrowWhenReactivatingMissingWorkspace() {
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> workspaceService.reactivateWorkspace(writeCtx, "missing-id")));
    }

    // -----------------------------------------------------------------------
    // In-memory test doubles
    // -----------------------------------------------------------------------

    private static final class InMemoryWorkspaceRepository implements WorkspaceRepository {
        private final ConcurrentHashMap<String, Workspace> store = new ConcurrentHashMap<>();

        @Override
        public Promise<Workspace> save(Workspace workspace) {
            store.put(key(workspace.getTenantId().getValue(), workspace.getId().getValue()), workspace);
            return Promise.of(workspace);
        }

        @Override
        public Promise<Optional<Workspace>> findById(DmTenantId tenantId, DmWorkspaceId workspaceId) {
            return Promise.of(Optional.ofNullable(store.get(key(tenantId.getValue(), workspaceId.getValue()))));
        }

        @Override
        public Promise<List<Workspace>> listByTenant(DmTenantId tenantId) {
            List<Workspace> result = store.entrySet().stream()
                .filter(e -> e.getKey().startsWith(tenantId.getValue() + ":"))
                .map(Map.Entry::getValue)
                .toList();
            return Promise.of(result);
        }

        private static String key(String tenantId, String workspaceId) {
            return tenantId + ":" + workspaceId;
        }
    }

    private static final class RecordingKernelAdapter implements DigitalMarketingKernelAdapter {
        private final ConcurrentHashMap<String, Boolean> decisionMap = new ConcurrentHashMap<>();
        private volatile boolean defaultAuthorization = true;
        private final List<String> auditActions = new CopyOnWriteArrayList<>();

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
        public void start() {
            // no-op
        }

        @Override
        public void stop() {
            // no-op
        }

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
            String description
        ) {
            return Promise.of("approval-ws-it-1");
        }

        @Override
        public Promise<String> recordAudit(
            DmOperationContext context,
            String entityId,
            String action,
            Map<String, Object> attributes
        ) {
            auditActions.add(action);
            return Promise.of("audit-ws-it-1");
        }
    }
}

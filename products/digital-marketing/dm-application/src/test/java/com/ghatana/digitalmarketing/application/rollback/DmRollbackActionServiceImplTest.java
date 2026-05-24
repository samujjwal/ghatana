package com.ghatana.digitalmarketing.application.rollback;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.rollback.DmRollbackAction;
import com.ghatana.digitalmarketing.domain.rollback.DmRollbackStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("DmRollbackActionServiceImpl")
class DmRollbackActionServiceImplTest extends EventloopTestBase {

    private EphemeralRollbackRepository repository;
    private DmRollbackActionServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        repository = new EphemeralRollbackRepository();
        service = new DmRollbackActionServiceImpl(repository, new StubKernelAdapter(true));
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    @Test
    @DisplayName("schedule stores PENDING rollback action")
    void scheduleSuccess() {
        DmRollbackAction action = runPromise(() -> service.schedule(ctx, cmd()));

        assertThat(action.getStatus()).isEqualTo(DmRollbackStatus.PENDING);
        assertThat(action.getTenantId()).isEqualTo("tenant-1");
        assertThat(action.getCommandId()).isEqualTo("cmd-1");
    }

    @Test
    @DisplayName("schedule rejects unauthorized actor")
    void scheduleUnauthorized() {
        service = new DmRollbackActionServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.schedule(ctx, cmd())));
    }

    @Test
    @DisplayName("markCompleted transitions PENDING to COMPLETED")
    void markCompletedSuccess() {
        DmRollbackAction scheduled = runPromise(() -> service.schedule(ctx, cmd()));
        DmRollbackAction completed = runPromise(() -> service.markCompleted(ctx, scheduled.getId()));

        assertThat(completed.getStatus()).isEqualTo(DmRollbackStatus.COMPLETED);
    }

    @Test
    @DisplayName("markCompleted rejects unauthorized actor")
    void markCompletedUnauthorized() {
        DmRollbackAction scheduled = runPromise(() -> service.schedule(ctx, cmd()));
        service = new DmRollbackActionServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.markCompleted(ctx, scheduled.getId())));
    }

    @Test
    @DisplayName("markCompleted rejects already COMPLETED action")
    void markCompletedDuplicate() {
        DmRollbackAction scheduled = runPromise(() -> service.schedule(ctx, cmd()));
        runPromise(() -> service.markCompleted(ctx, scheduled.getId()));

        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> runPromise(() -> service.markCompleted(ctx, scheduled.getId())));
    }

    @Test
    @DisplayName("markFailed sets FAILED status with reason")
    void markFailedSuccess() {
        DmRollbackAction scheduled = runPromise(() -> service.schedule(ctx, cmd()));
        DmRollbackAction failed = runPromise(() -> service.markFailed(ctx, scheduled.getId(), "Timeout"));

        assertThat(failed.getStatus()).isEqualTo(DmRollbackStatus.FAILED);
        assertThat(failed.getFailureReason()).isEqualTo("Timeout");
    }

    @Test
    @DisplayName("markFailed rejects unauthorized actor")
    void markFailedUnauthorized() {
        DmRollbackAction scheduled = runPromise(() -> service.schedule(ctx, cmd()));
        service = new DmRollbackActionServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.markFailed(ctx, scheduled.getId(), "Reason")));
    }

    @Test
    @DisplayName("listByCommand rejects unauthorized actor")
    void listByCommandUnauthorized() {
        service = new DmRollbackActionServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.listByCommand(ctx, "cmd-1")));
    }

    @Test
    @DisplayName("findById enforces tenant isolation")
    void findByIdTenantIsolation() {
        DmRollbackAction scheduled = runPromise(() -> service.schedule(ctx, cmd()));
        DmOperationContext other = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-2"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("u2"))
            .correlationId(DmCorrelationId.of("c2"))
            .idempotencyKey(DmIdempotencyKey.of("i2"))
            .build();

        Optional<DmRollbackAction> result = runPromise(() -> service.findById(other, scheduled.getId()));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("listByCommand returns tenant-scoped actions")
    void listByCommandSuccess() {
        runPromise(() -> service.schedule(ctx, cmd()));
        List<DmRollbackAction> results = runPromise(() -> service.listByCommand(ctx, "cmd-1"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCommandId()).isEqualTo("cmd-1");
    }

    @Test
    @DisplayName("listByStatus rejects unauthorized actor")
    void listByStatusUnauthorized() {
        service = new DmRollbackActionServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.listByStatus(ctx, DmRollbackStatus.PENDING, 10)));
    }

    @Test
    @DisplayName("schedule command validates blank commandId")
    void commandValidation() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmRollbackActionService.ScheduleRollbackCommand(
                "", "REVERT", "entity-1", "Campaign"));
    }

    private DmRollbackActionService.ScheduleRollbackCommand cmd() {
        return new DmRollbackActionService.ScheduleRollbackCommand(
            "cmd-1", "REVERT_BUDGET", "entity-1", "Campaign");
    }

    static final class EphemeralRollbackRepository implements DmRollbackActionRepository {
        private final Map<String, DmRollbackAction> store = new ConcurrentHashMap<>();

        @Override public Promise<DmRollbackAction> save(DmRollbackAction a) {
            store.put(a.getId(), a); return Promise.of(a);
        }
        @Override public Promise<DmRollbackAction> update(DmRollbackAction a) {
            store.put(a.getId(), a); return Promise.of(a);
        }
        @Override public Promise<Optional<DmRollbackAction>> findById(String id) {
            return Promise.of(Optional.ofNullable(store.get(id)));
        }
        @Override public Promise<List<DmRollbackAction>> listByCommand(String tenantId, String commandId) {
            return Promise.of(store.values().stream()
                .filter(a -> a.getTenantId().equals(tenantId) && a.getCommandId().equals(commandId)).toList());
        }
        @Override public Promise<List<DmRollbackAction>> listByStatus(String tenantId, DmRollbackStatus status, int limit) {
            return Promise.of(store.values().stream()
                .filter(a -> a.getTenantId().equals(tenantId) && a.getStatus() == status).limit(limit).toList());
        }
    }

    static final class StubKernelAdapter implements DigitalMarketingKernelAdapter {
        private final boolean allowed;
        StubKernelAdapter(boolean allowed) { this.allowed = allowed; }
        @Override public void start() {}
        @Override public void stop() {}
        @Override public Promise<Boolean> isAuthorized(DmOperationContext ctx, String resource, String action) {
            return Promise.of(allowed);
        }
        @Override public Promise<Boolean> verifyConsent(DmOperationContext ctx, String subjectId, String purpose) {
            return Promise.of(true);
        }
        @Override public Promise<String> requestApproval(DmOperationContext ctx, String type, String subjectId, String desc) {
            return Promise.of("approval-1");
        }
        @Override public Promise<String> recordAudit(DmOperationContext ctx, String entityId, String action, Map<String, Object> meta) {
            return Promise.of("audit-1");
        }
    }
}

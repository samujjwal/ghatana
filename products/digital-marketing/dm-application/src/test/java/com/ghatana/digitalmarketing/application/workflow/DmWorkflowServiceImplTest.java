package com.ghatana.digitalmarketing.application.workflow;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.workflow.DmWorkflowExecution;
import com.ghatana.digitalmarketing.domain.workflow.DmWorkflowStatus;
import com.ghatana.digitalmarketing.domain.workflow.DmWorkflowStep;
import com.ghatana.digitalmarketing.domain.workflow.DmWorkflowStepStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Unit tests for {@link DmWorkflowServiceImpl}.
 *
 * @doc.type class
 * @doc.purpose Verifies durable workflow lifecycle management (DMOS-F2-004)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DmWorkflowServiceImpl Tests")
class DmWorkflowServiceImplTest extends EventloopTestBase {

    private EphemeralWorkflowRepository repo;
    private AllowingKernelAdapter kernelAdapter;
    private DmWorkflowServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        repo          = new EphemeralWorkflowRepository();
        kernelAdapter = new AllowingKernelAdapter(true);
        service       = new DmWorkflowServiceImpl(repo, kernelAdapter);

        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    private DmWorkflowService.InitiateWorkflowRequest sampleRequest() {
        return new DmWorkflowService.InitiateWorkflowRequest(
            "Campaign Workflow",
            "corr-1",
            List.of(pendingStep("step-1"), pendingStep("step-2"))
        );
    }

    private DmWorkflowStep pendingStep(String name) {
        return DmWorkflowStep.builder()
            .name(name)
            .stepType("ACTION")
            .status(DmWorkflowStepStatus.PENDING)
            .build();
    }

    // ── initiate ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("initiate creates PENDING workflow for authorized request")
    void initiateSuccess() {
        DmWorkflowExecution result = runPromise(() -> service.initiate(ctx, sampleRequest()));
        assertThat(result.getId()).isNotBlank();
        assertThat(result.getStatus()).isEqualTo(DmWorkflowStatus.PENDING);
        assertThat(result.getTenantId()).isEqualTo("tenant-1");
        assertThat(result.getName()).isEqualTo("Campaign Workflow");
        assertThat(result.getSteps()).hasSize(2);
    }

    @Test
    @DisplayName("initiate rejects null context")
    void initiateRejectsNullCtx() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> runPromise(() -> service.initiate(null, sampleRequest())));
    }

    @Test
    @DisplayName("initiate rejects null request")
    void initiateRejectsNullRequest() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> runPromise(() -> service.initiate(ctx, null)));
    }

    @Test
    @DisplayName("initiate throws SecurityException when unauthorized")
    void initiateUnauthorized() {
        service = new DmWorkflowServiceImpl(repo, new AllowingKernelAdapter(false));
        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.initiate(ctx, sampleRequest())));
    }

    // ── start ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("start transitions PENDING workflow to RUNNING")
    void startSuccess() {
        DmWorkflowExecution created = runPromise(() -> service.initiate(ctx, sampleRequest()));
        DmWorkflowExecution running = runPromise(() -> service.start(ctx, created.getId()));
        assertThat(running.getStatus()).isEqualTo(DmWorkflowStatus.RUNNING);
    }

    @Test
    @DisplayName("start rejects blank id")
    void startRejectsBlankId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> service.start(ctx, "  ")));
    }

    @Test
    @DisplayName("start throws NoSuchElementException for unknown id")
    void startUnknownId() {
        assertThatExceptionOfType(java.util.NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.start(ctx, "nonexistent")));
    }

    @Test
    @DisplayName("start throws SecurityException when unauthorized")
    void startUnauthorized() {
        DmWorkflowExecution created = runPromise(() -> service.initiate(ctx, sampleRequest()));
        service = new DmWorkflowServiceImpl(repo, new AllowingKernelAdapter(false));
        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.start(ctx, created.getId())));
    }

    // ── advanceStep ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("advanceStep moves to next step")
    void advanceStepSuccess() {
        DmWorkflowExecution exec = runPromise(() ->
            service.initiate(ctx, sampleRequest())
                .then(e -> service.start(ctx, e.getId())));

        DmWorkflowStep done = exec.currentStep().markCompleted();
        DmWorkflowExecution advanced = runPromise(() -> service.advanceStep(ctx, exec.getId(), done));

        assertThat(advanced.getCurrentStepIndex()).isEqualTo(1);
    }

    @Test
    @DisplayName("advanceStep rejects null completedStep")
    void advanceStepRejectsNullStep() {
        DmWorkflowExecution exec = runPromise(() ->
            service.initiate(ctx, sampleRequest())
                .then(e -> service.start(ctx, e.getId())));
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> runPromise(() -> service.advanceStep(ctx, exec.getId(), null)));
    }

    @Test
    @DisplayName("advanceStep rejects unauthorized actor")
    void advanceStepUnauthorized() {
        DmWorkflowExecution exec = runPromise(() ->
            service.initiate(ctx, sampleRequest())
                .then(e -> service.start(ctx, e.getId())));
        DmWorkflowStep done = exec.currentStep().markCompleted();
        service = new DmWorkflowServiceImpl(repo, new AllowingKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.advanceStep(ctx, exec.getId(), done)));
    }

    // ── complete ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("complete transitions RUNNING workflow to COMPLETED")
    void completeSuccess() {
        DmWorkflowExecution created = runPromise(() -> service.initiate(ctx, sampleRequest()));
        DmWorkflowExecution running = runPromise(() -> service.start(ctx, created.getId()));
        DmWorkflowExecution completed = runPromise(() -> service.complete(ctx, running.getId()));
        assertThat(completed.getStatus()).isEqualTo(DmWorkflowStatus.COMPLETED);
        assertThat(completed.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("complete rejects unauthorized actor")
    void completeUnauthorized() {
        DmWorkflowExecution running = runPromise(() ->
            service.initiate(ctx, sampleRequest()).then(e -> service.start(ctx, e.getId())));
        service = new DmWorkflowServiceImpl(repo, new AllowingKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.complete(ctx, running.getId())));
    }

    // ── fail ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("fail transitions RUNNING workflow to FAILED")
    void failSuccess() {
        DmWorkflowExecution created = runPromise(() -> service.initiate(ctx, sampleRequest()));
        DmWorkflowExecution running = runPromise(() -> service.start(ctx, created.getId()));
        DmWorkflowExecution failed  = runPromise(() -> service.fail(ctx, running.getId(), "timeout"));
        assertThat(failed.getStatus()).isEqualTo(DmWorkflowStatus.FAILED);
        assertThat(failed.getFailureReason()).isEqualTo("timeout");
    }

    @Test
    @DisplayName("fail rejects null reason")
    void failRejectsNullReason() {
        DmWorkflowExecution created = runPromise(() -> service.initiate(ctx, sampleRequest()));
        DmWorkflowExecution running = runPromise(() -> service.start(ctx, created.getId()));
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> runPromise(() -> service.fail(ctx, running.getId(), null)));
    }

    @Test
    @DisplayName("fail rejects unauthorized actor")
    void failUnauthorized() {
        DmWorkflowExecution running = runPromise(() ->
            service.initiate(ctx, sampleRequest()).then(e -> service.start(ctx, e.getId())));
        service = new DmWorkflowServiceImpl(repo, new AllowingKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.fail(ctx, running.getId(), "reason")));
    }

    // ── pause / resume ────────────────────────────────────────────────────────

    @Test
    @DisplayName("pause and resume cycle works correctly")
    void pauseResumeSuccess() {
        DmWorkflowExecution exec = runPromise(() ->
            service.initiate(ctx, sampleRequest())
                .then(e -> service.start(ctx, e.getId())));

        DmWorkflowExecution paused  = runPromise(() -> service.pause(ctx, exec.getId()));
        assertThat(paused.getStatus()).isEqualTo(DmWorkflowStatus.PAUSED);

        DmWorkflowExecution resumed = runPromise(() -> service.resume(ctx, paused.getId()));
        assertThat(resumed.getStatus()).isEqualTo(DmWorkflowStatus.RUNNING);
    }

    @Test
    @DisplayName("pause rejects unauthorized actor")
    void pauseUnauthorized() {
        DmWorkflowExecution exec = runPromise(() ->
            service.initiate(ctx, sampleRequest()).then(e -> service.start(ctx, e.getId())));
        service = new DmWorkflowServiceImpl(repo, new AllowingKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.pause(ctx, exec.getId())));
    }

    // ── rollback ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("rollback transitions FAILED to ROLLED_BACK")
    void rollbackSuccess() {
        DmWorkflowExecution exec = runPromise(() ->
            service.initiate(ctx, sampleRequest())
                .then(e -> service.start(ctx, e.getId()))
                .then(e -> service.fail(ctx, e.getId(), "err")));

        DmWorkflowExecution rb = runPromise(() -> service.rollback(ctx, exec.getId()));
        assertThat(rb.getStatus()).isEqualTo(DmWorkflowStatus.ROLLED_BACK);
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findById returns empty for unknown id")
    void findByIdEmpty() {
        Optional<DmWorkflowExecution> result = runPromise(() -> service.findById(ctx, "unknown"));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findById filters cross-tenant access")
    void findByIdCrossTenant() {
        DmWorkflowExecution created = runPromise(() -> service.initiate(ctx, sampleRequest()));

        DmOperationContext otherCtx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("other-tenant"))
            .workspaceId(DmWorkspaceId.of("ws-2"))
            .actor(ActorRef.user("user-2"))
            .correlationId(DmCorrelationId.of("corr-2"))
            .idempotencyKey(DmIdempotencyKey.of("idk-2"))
            .build();

        Optional<DmWorkflowExecution> result = runPromise(() -> service.findById(otherCtx, created.getId()));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findById rejects blank id")
    void findByIdBlankId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> service.findById(ctx, "")));
    }

    // ── listActive ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listActive returns RUNNING and PAUSED workflows")
    void listActiveSuccess() {
        DmWorkflowExecution exec = runPromise(() ->
            service.initiate(ctx, sampleRequest())
                .then(e -> service.start(ctx, e.getId())));

        List<DmWorkflowExecution> active = runPromise(() -> service.listActive(ctx, 10));
        assertThat(active).anyMatch(e -> e.getId().equals(exec.getId()));
    }

    // ── countByStatus ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("countByStatus returns correct count")
    void countByStatusSuccess() {
        runPromise(() -> service.initiate(ctx, sampleRequest()));
        runPromise(() -> service.initiate(ctx, sampleRequest()));

        Long count = runPromise(() -> service.countByStatus(ctx, DmWorkflowStatus.PENDING));
        assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("countByStatus rejects null status")
    void countByStatusNullStatus() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> runPromise(() -> service.countByStatus(ctx, null)));
    }

    // ── Test Doubles ──────────────────────────────────────────────────────────

    private static final class EphemeralWorkflowRepository implements DmWorkflowRepository {
        private final ConcurrentHashMap<String, DmWorkflowExecution> store = new ConcurrentHashMap<>();

        @Override
        public Promise<DmWorkflowExecution> save(DmWorkflowExecution execution) {
            store.put(execution.getId(), execution);
            return Promise.of(execution);
        }

        @Override
        public Promise<Optional<DmWorkflowExecution>> findById(String id) {
            return Promise.of(Optional.ofNullable(store.get(id)));
        }

        @Override
        public Promise<List<DmWorkflowExecution>> findByStatus(String tenantId, DmWorkflowStatus status, int limit) {
            List<DmWorkflowExecution> result = new ArrayList<>();
            for (DmWorkflowExecution e : store.values()) {
                if (e.getTenantId().equals(tenantId) && e.getStatus() == status) {
                    result.add(e);
                    if (result.size() >= limit) break;
                }
            }
            return Promise.of(result);
        }

        @Override
        public Promise<List<DmWorkflowExecution>> findActive(String tenantId, int limit) {
            List<DmWorkflowExecution> result = new ArrayList<>();
            for (DmWorkflowExecution e : store.values()) {
                if (e.getTenantId().equals(tenantId)
                    && (e.getStatus() == DmWorkflowStatus.RUNNING || e.getStatus() == DmWorkflowStatus.PAUSED)) {
                    result.add(e);
                    if (result.size() >= limit) break;
                }
            }
            return Promise.of(result);
        }

        @Override
        public Promise<DmWorkflowExecution> update(DmWorkflowExecution execution) {
            store.put(execution.getId(), execution);
            return Promise.of(execution);
        }

        @Override
        public Promise<Long> countByStatus(String tenantId, DmWorkflowStatus status) {
            long count = store.values().stream()
                .filter(e -> e.getTenantId().equals(tenantId) && e.getStatus() == status)
                .count();
            return Promise.of(count);
        }
    }

    private static final class AllowingKernelAdapter implements DigitalMarketingKernelAdapter {
        private final boolean authorized;

        AllowingKernelAdapter(boolean authorized) {
            this.authorized = authorized;
        }

        @Override
        public void start() {}

        @Override
        public void stop() {}

        @Override
        public Promise<Boolean> isAuthorized(DmOperationContext context, String resource, String action) {
            return Promise.of(authorized);
        }

        @Override
        public Promise<Boolean> verifyConsent(DmOperationContext context, String subjectId, String purpose) {
            return Promise.of(true);
        }

        @Override
        public Promise<String> requestApproval(
                DmOperationContext context, String operationType, String subjectId, String description) {
            return Promise.of("approval-1");
        }

        @Override
        public Promise<String> recordAudit(
                DmOperationContext context, String entityId, String action,
                Map<String, Object> attributes) {
            return Promise.of("audit-1");
        }
    }
}

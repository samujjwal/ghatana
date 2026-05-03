package com.ghatana.digitalmarketing.application.model;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.model.DmCustomModelTrainingJob;
import com.ghatana.digitalmarketing.domain.model.DmCustomModelTrainingStatus;
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

@DisplayName("DmCustomModelTrainingJobServiceImpl")
class DmCustomModelTrainingJobServiceImplTest extends EventloopTestBase {

    private InMemoryJobRepository repository;
    private DmCustomModelTrainingJobServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        repository = new InMemoryJobRepository();
        service = new DmCustomModelTrainingJobServiceImpl(repository, new StubKernelAdapter(true));
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    private DmCustomModelTrainingJobService.EnqueueTrainingJobCommand cmd() {
        return new DmCustomModelTrainingJobService.EnqueueTrainingJobCommand(
            "AdClickPredictor", "base-gpt-tiny", "s3://bucket/train.csv", "ws-1");
    }

    @Test
    @DisplayName("enqueue creates QUEUED job")
    void enqueueSuccess() {
        DmCustomModelTrainingJob job = runPromise(() -> service.enqueue(ctx, cmd()));

        assertThat(job.getTenantId()).isEqualTo("tenant-1");
        assertThat(job.getModelName()).isEqualTo("AdClickPredictor");
        assertThat(job.getStatus()).isEqualTo(DmCustomModelTrainingStatus.QUEUED);
    }

    @Test
    @DisplayName("enqueue rejects unauthorized actor")
    void enqueueUnauthorized() {
        service = new DmCustomModelTrainingJobServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.enqueue(ctx, cmd())));
    }

    @Test
    @DisplayName("lifecycle: QUEUED → TRAINING → EVALUATING → COMPLETE")
    void fullLifecycleSuccess() {
        DmCustomModelTrainingJob job = runPromise(() -> service.enqueue(ctx, cmd()));
        DmCustomModelTrainingJob training = runPromise(() -> service.markTraining(ctx, job.getId()));
        assertThat(training.getStatus()).isEqualTo(DmCustomModelTrainingStatus.TRAINING);
        assertThat(training.getStartedAt()).isNotNull();

        DmCustomModelTrainingJob eval = runPromise(() -> service.markEvaluating(ctx, training.getId()));
        assertThat(eval.getStatus()).isEqualTo(DmCustomModelTrainingStatus.EVALUATING);

        DmCustomModelTrainingJob done = runPromise(() -> service.markComplete(ctx, eval.getId(), 0.91, "s3://bucket/model.bin"));
        assertThat(done.getStatus()).isEqualTo(DmCustomModelTrainingStatus.COMPLETE);
        assertThat(done.getBestEvalScore()).isEqualTo(0.91);
        assertThat(done.getArtifactRef()).isEqualTo("s3://bucket/model.bin");
        assertThat(done.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("markFailed records failure reason")
    void markFailedSuccess() {
        DmCustomModelTrainingJob job = runPromise(() -> service.enqueue(ctx, cmd()));
        DmCustomModelTrainingJob failed = runPromise(() -> service.markFailed(ctx, job.getId(), "Out of GPU memory"));

        assertThat(failed.getStatus()).isEqualTo(DmCustomModelTrainingStatus.FAILED);
        assertThat(failed.getFailureReason()).isEqualTo("Out of GPU memory");
    }

    @Test
    @DisplayName("cancel transitions to CANCELLED with tenant check")
    void cancelSuccess() {
        DmCustomModelTrainingJob job = runPromise(() -> service.enqueue(ctx, cmd()));
        DmCustomModelTrainingJob cancelled = runPromise(() -> service.cancel(ctx, job.getId()));

        assertThat(cancelled.getStatus()).isEqualTo(DmCustomModelTrainingStatus.CANCELLED);
    }

    @Test
    @DisplayName("listByTenant returns all jobs for tenant")
    void listByTenantSuccess() {
        runPromise(() -> service.enqueue(ctx, cmd()));
        runPromise(() -> service.enqueue(ctx, cmd()));

        List<DmCustomModelTrainingJob> list = runPromise(() -> service.listByTenant(ctx));
        assertThat(list).hasSize(2);
    }

    @Test
    @DisplayName("EnqueueTrainingJobCommand rejects blank modelName")
    void commandValidation() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmCustomModelTrainingJobService.EnqueueTrainingJobCommand(
                "", "base-model", "s3://data", "ws-1"));
    }

    // ── In-memory repository ──────────────────────────────────────────────────

    static final class InMemoryJobRepository implements DmCustomModelTrainingJobRepository {
        private final Map<String, DmCustomModelTrainingJob> store = new ConcurrentHashMap<>();

        @Override public Promise<DmCustomModelTrainingJob> save(DmCustomModelTrainingJob j) { store.put(j.getId(), j); return Promise.of(j); }
        @Override public Promise<DmCustomModelTrainingJob> update(DmCustomModelTrainingJob j) { store.put(j.getId(), j); return Promise.of(j); }
        @Override public Promise<Optional<DmCustomModelTrainingJob>> findById(String id) { return Promise.of(Optional.ofNullable(store.get(id))); }
        @Override public Promise<List<DmCustomModelTrainingJob>> listByTenant(String tenantId) {
            List<DmCustomModelTrainingJob> r = new ArrayList<>();
            for (DmCustomModelTrainingJob j : store.values()) if (j.getTenantId().equals(tenantId)) r.add(j);
            return Promise.of(r);
        }
        @Override public Promise<List<DmCustomModelTrainingJob>> listByTenantAndStatus(String tenantId, DmCustomModelTrainingStatus status) {
            List<DmCustomModelTrainingJob> r = new ArrayList<>();
            for (DmCustomModelTrainingJob j : store.values()) if (j.getTenantId().equals(tenantId) && j.getStatus() == status) r.add(j);
            return Promise.of(r);
        }
    }

    // ── Stub kernel adapter ───────────────────────────────────────────────────

    static final class StubKernelAdapter implements DigitalMarketingKernelAdapter {
        private final boolean allowed;
        StubKernelAdapter(boolean allowed) { this.allowed = allowed; }
        @Override public void start() {}
        @Override public void stop() {}
        @Override public Promise<Boolean> isAuthorized(DmOperationContext ctx, String resource, String action) { return Promise.of(allowed); }
        @Override public Promise<Boolean> verifyConsent(DmOperationContext ctx, String subjectId, String purpose) { return Promise.of(true); }
        @Override public Promise<String> requestApproval(DmOperationContext ctx, String type, String subjectId, String desc) { return Promise.of("approval-1"); }
        @Override public Promise<String> recordAudit(DmOperationContext ctx, String entityId, String action, Map<String, Object> meta) { return Promise.of("audit-1"); }
    }
}

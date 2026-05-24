package com.ghatana.digitalmarketing.application.evaluation;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.evaluation.DmAgentEvaluation;
import com.ghatana.digitalmarketing.domain.evaluation.DmAgentEvaluation.DmEvalMetric;
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

@DisplayName("DmAgentEvaluationServiceImpl")
class DmAgentEvaluationServiceImplTest extends EventloopTestBase {

    private EphemeralEvaluationRepository repository;
    private DmAgentEvaluationServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        repository = new EphemeralEvaluationRepository();
        service = new DmAgentEvaluationServiceImpl(repository, new StubKernelAdapter(true));
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    @Test
    @DisplayName("submit stores evaluation with evaluatedBy actor")
    void submitSuccess() {
        DmAgentEvaluation eval = runPromise(() -> service.submit(ctx, cmd()));

        assertThat(eval.getTenantId()).isEqualTo("tenant-1");
        assertThat(eval.getEvaluatedBy()).isEqualTo("user-1");
        assertThat(eval.getVerdict()).isEqualTo("PASS");
    }

    @Test
    @DisplayName("submit rejects unauthorized actor")
    void submitUnauthorized() {
        service = new DmAgentEvaluationServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.submit(ctx, cmd())));
    }

    @Test
    @DisplayName("findById enforces tenant isolation")
    void findByIdTenantIsolation() {
        DmAgentEvaluation created = runPromise(() -> service.submit(ctx, cmd()));
        DmOperationContext other = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-2"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("u2"))
            .correlationId(DmCorrelationId.of("c2"))
            .idempotencyKey(DmIdempotencyKey.of("i2"))
            .build();

        assertThat(runPromise(() -> service.findById(other, created.getId()))).isEmpty();
    }

    @Test
    @DisplayName("listByAgent returns evaluations for agent")
    void listByAgentSuccess() {
        runPromise(() -> service.submit(ctx, cmd()));
        List<DmAgentEvaluation> results = runPromise(() -> service.listByAgent(ctx, "agent-1"));

        assertThat(results).hasSize(1);
    }

    @Test
    @DisplayName("listByAgent rejects unauthorized actor")
    void listByAgentUnauthorized() {
        service = new DmAgentEvaluationServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.listByAgent(ctx, "agent-1")));
    }

    @Test
    @DisplayName("command validates blank agentId")
    void commandValidation() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmAgentEvaluationService.SubmitEvaluationCommand("", "PLANNING", List.of(), 0.9, "PASS"));
    }

    private DmAgentEvaluationService.SubmitEvaluationCommand cmd() {
        return new DmAgentEvaluationService.SubmitEvaluationCommand(
            "agent-1", "PLANNING",
            List.of(new DmEvalMetric("accuracy", 0.95, "High accuracy on test set")),
            0.95, "PASS");
    }

    static final class EphemeralEvaluationRepository implements DmAgentEvaluationRepository {
        private final Map<String, DmAgentEvaluation> store = new ConcurrentHashMap<>();

        @Override public Promise<DmAgentEvaluation> save(DmAgentEvaluation e) { store.put(e.getId(), e); return Promise.of(e); }
        @Override public Promise<Optional<DmAgentEvaluation>> findById(String id) { return Promise.of(Optional.ofNullable(store.get(id))); }
        @Override public Promise<List<DmAgentEvaluation>> listByAgent(String tenantId, String agentId) {
            return Promise.of(store.values().stream()
                .filter(e -> e.getTenantId().equals(tenantId) && e.getAgentId().equals(agentId)).toList());
        }
        @Override public Promise<List<DmAgentEvaluation>> listByTenant(String tenantId) {
            return Promise.of(store.values().stream().filter(e -> e.getTenantId().equals(tenantId)).toList());
        }
    }

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

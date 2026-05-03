package com.ghatana.digitalmarketing.application.recommendation;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.recommendation.DmEngineRecommendation;
import com.ghatana.digitalmarketing.domain.recommendation.DmEngineRecommendationStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("DmEngineRecommendationServiceImpl")
class DmEngineRecommendationServiceImplTest extends EventloopTestBase {

    private InMemoryRecRepository repository;
    private DmEngineRecommendationServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRecRepository();
        service = new DmEngineRecommendationServiceImpl(repository, new StubKernelAdapter(true));
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    @Test
    @DisplayName("publish creates recommendation with ACTIVE status")
    void publishSuccess() {
        DmEngineRecommendation rec = runPromise(() -> service.publish(ctx, cmd()));

        assertThat(rec.getTenantId()).isEqualTo("tenant-1");
        assertThat(rec.getStatus()).isEqualTo(DmEngineRecommendationStatus.ACTIVE);
    }

    @Test
    @DisplayName("publish rejects unauthorized actor")
    void publishUnauthorized() {
        service = new DmEngineRecommendationServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.publish(ctx, cmd())));
    }

    @Test
    @DisplayName("accept transitions to ACCEPTED")
    void acceptSuccess() {
        DmEngineRecommendation created = runPromise(() -> service.publish(ctx, cmd()));
        DmEngineRecommendation accepted = runPromise(() -> service.accept(ctx, created.getId()));

        assertThat(accepted.getStatus()).isEqualTo(DmEngineRecommendationStatus.ACCEPTED);
    }

    @Test
    @DisplayName("accept rejects unauthorized actor")
    void acceptUnauthorized() {
        DmEngineRecommendation created = runPromise(() -> service.publish(ctx, cmd()));
        DmEngineRecommendationServiceImpl deniedService = new DmEngineRecommendationServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> deniedService.accept(ctx, created.getId())));
    }

    @Test
    @DisplayName("reject transitions to REJECTED")
    void rejectSuccess() {
        DmEngineRecommendation created = runPromise(() -> service.publish(ctx, cmd()));
        DmEngineRecommendation rejected = runPromise(() -> service.reject(ctx, created.getId()));

        assertThat(rejected.getStatus()).isEqualTo(DmEngineRecommendationStatus.REJECTED);
    }

    @Test
    @DisplayName("reject rejects unauthorized actor")
    void rejectUnauthorized() {
        DmEngineRecommendation created = runPromise(() -> service.publish(ctx, cmd()));
        DmEngineRecommendationServiceImpl deniedService = new DmEngineRecommendationServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> deniedService.reject(ctx, created.getId())));
    }

    @Test
    @DisplayName("findById enforces tenant isolation")
    void findByIdTenantIsolation() {
        DmEngineRecommendation created = runPromise(() -> service.publish(ctx, cmd()));
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
    @DisplayName("listByStatus returns matching recommendations")
    void listByStatusSuccess() {
        runPromise(() -> service.publish(ctx, cmd()));
        List<DmEngineRecommendation> results = runPromise(() -> service.listByStatus(ctx, DmEngineRecommendationStatus.ACTIVE));

        assertThat(results).hasSize(1);
    }

    @Test
    @DisplayName("command validates confidenceScore out of range")
    void commandValidation() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmEngineRecommendationService.PublishRecommendationCommand(
                "type", "rationale", 1.5, List.of(), List.of(), null));
    }

    @Test
    @DisplayName("command rejects negative confidenceScore")
    void commandValidationNegativeScore() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmEngineRecommendationService.PublishRecommendationCommand(
                "type", "rationale", -0.1, List.of(), List.of(), null));
    }

    @Test
    @DisplayName("command rejects blank recommendationType")
    void commandValidationBlankType() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmEngineRecommendationService.PublishRecommendationCommand(
                "", "rationale", 0.5, List.of(), List.of(), null));
    }

    @Test
    @DisplayName("listByTenant rejects unauthorized actor")
    void listByTenantUnauthorized() {
        service = new DmEngineRecommendationServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.listByTenant(ctx)));
    }

    @Test
    @DisplayName("listByStatus rejects unauthorized actor")
    void listByStatusUnauthorized() {
        service = new DmEngineRecommendationServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.listByStatus(ctx, DmEngineRecommendationStatus.ACTIVE)));
    }

    @Test
    @DisplayName("publish with null lists defaults to empty lists")
    void publishNullListsDefaultToEmpty() {
        DmEngineRecommendationService.PublishRecommendationCommand cmd =
            new DmEngineRecommendationService.PublishRecommendationCommand(
                "type", "rationale", 0.5, null, null, null);

        DmEngineRecommendation rec = runPromise(() -> service.publish(ctx, cmd));

        assertThat(rec.getSupportingMetricKeys()).isEmpty();
        assertThat(rec.getSuggestedActions()).isEmpty();
    }

    @Test
    @DisplayName("accept throws NoSuchElementException for unknown id")
    void acceptUnknownId() {
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.accept(ctx, "nonexistent-id")));
    }

    @Test
    @DisplayName("reject throws NoSuchElementException for unknown id")
    void rejectUnknownId() {
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.reject(ctx, "nonexistent-id")));
    }

    @Test
    @DisplayName("accept throws SecurityException for cross-tenant recommendation")
    void acceptCrossTenantIsolation() {
        DmEngineRecommendation created = runPromise(() -> service.publish(ctx, cmd()));
        DmOperationContext other = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-2"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("u2"))
            .correlationId(DmCorrelationId.of("c2"))
            .idempotencyKey(DmIdempotencyKey.of("i2"))
            .build();

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.accept(other, created.getId())));
    }

    private DmEngineRecommendationService.PublishRecommendationCommand cmd() {
        return new DmEngineRecommendationService.PublishRecommendationCommand(
            "budget-reallocation", "Low ROAS detected", 0.87, List.of(), List.of(), null);
    }

    static final class InMemoryRecRepository implements DmEngineRecommendationRepository {
        private final Map<String, DmEngineRecommendation> store = new ConcurrentHashMap<>();

        @Override public Promise<DmEngineRecommendation> save(DmEngineRecommendation r) { store.put(r.getId(), r); return Promise.of(r); }
        @Override public Promise<DmEngineRecommendation> update(DmEngineRecommendation r) { store.put(r.getId(), r); return Promise.of(r); }
        @Override public Promise<Optional<DmEngineRecommendation>> findById(String id) {
            return Promise.of(Optional.ofNullable(store.get(id)));
        }
        @Override public Promise<List<DmEngineRecommendation>> listByTenant(String tenantId) {
            return Promise.of(store.values().stream().filter(r -> r.getTenantId().equals(tenantId)).toList());
        }
        @Override public Promise<List<DmEngineRecommendation>> listByStatus(String tenantId, DmEngineRecommendationStatus status) {
            return Promise.of(store.values().stream()
                .filter(r -> r.getTenantId().equals(tenantId) && r.getStatus() == status).toList());
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

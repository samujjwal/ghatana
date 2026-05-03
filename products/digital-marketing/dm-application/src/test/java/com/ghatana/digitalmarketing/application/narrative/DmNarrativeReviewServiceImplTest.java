package com.ghatana.digitalmarketing.application.narrative;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.narrative.DmNarrativePeriodType;
import com.ghatana.digitalmarketing.domain.narrative.DmNarrativeReview;
import com.ghatana.digitalmarketing.domain.narrative.DmNarrativeReviewStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("DmNarrativeReviewServiceImpl")
class DmNarrativeReviewServiceImplTest extends EventloopTestBase {

    private InMemoryNarrativeRepository repository;
    private DmNarrativeReviewServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        repository = new InMemoryNarrativeRepository();
        service = new DmNarrativeReviewServiceImpl(repository, new StubKernelAdapter(true));
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    @Test
    @DisplayName("generate creates review with PENDING status")
    void generateSuccess() {
        DmNarrativeReview review = runPromise(() -> service.generate(ctx, cmd()));

        assertThat(review.getStatus()).isEqualTo(DmNarrativeReviewStatus.PENDING);
        assertThat(review.getTenantId()).isEqualTo("tenant-1");
    }

    @Test
    @DisplayName("generate rejects unauthorized actor")
    void generateUnauthorized() {
        service = new DmNarrativeReviewServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.generate(ctx, cmd())));
    }

    @Test
    @DisplayName("markReady transitions to READY")
    void markReadySuccess() {
        DmNarrativeReview created = runPromise(() -> service.generate(ctx, cmd()));
        DmNarrativeReview ready = runPromise(() -> service.markReady(ctx, created.getId(),
            "Performance improved.", List.of("CTR up 10%"), List.of("Increase budget")));

        assertThat(ready.getStatus()).isEqualTo(DmNarrativeReviewStatus.READY);
        assertThat(ready.getNarrativeText()).isEqualTo("Performance improved.");
    }

    @Test
    @DisplayName("markFailed transitions to FAILED")
    void markFailedSuccess() {
        DmNarrativeReview created = runPromise(() -> service.generate(ctx, cmd()));
        DmNarrativeReview failed = runPromise(() -> service.markFailed(ctx, created.getId()));

        assertThat(failed.getStatus()).isEqualTo(DmNarrativeReviewStatus.FAILED);
    }

    @Test
    @DisplayName("findById enforces tenant isolation")
    void findByIdTenantIsolation() {
        DmNarrativeReview created = runPromise(() -> service.generate(ctx, cmd()));
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
    @DisplayName("markReady rejects unauthorized actor")
    void markReadyUnauthorized() {
        DmNarrativeReview created = runPromise(() -> service.generate(ctx, cmd()));
        DmNarrativeReviewServiceImpl denied = new DmNarrativeReviewServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> denied.markReady(ctx, created.getId(),
                "text", List.of(), List.of())));
    }

    @Test
    @DisplayName("markReady handles null keyInsights and recommendations")
    void markReadyNullInsightsAndRecommendations() {
        DmNarrativeReview created = runPromise(() -> service.generate(ctx, cmd()));
        DmNarrativeReview ready = runPromise(() -> service.markReady(ctx, created.getId(),
            "Narrative text", null, null));

        assertThat(ready.getStatus()).isEqualTo(DmNarrativeReviewStatus.READY);
        assertThat(ready.getKeyInsights()).isNull();
        assertThat(ready.getRecommendations()).isNull();
    }

    @Test
    @DisplayName("markFailed rejects unauthorized actor")
    void markFailedUnauthorized() {
        DmNarrativeReview created = runPromise(() -> service.generate(ctx, cmd()));
        DmNarrativeReviewServiceImpl denied = new DmNarrativeReviewServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> denied.markFailed(ctx, created.getId())));
    }

    @Test
    @DisplayName("listByTenant rejects unauthorized actor")
    void listByTenantUnauthorized() {
        DmNarrativeReviewServiceImpl denied = new DmNarrativeReviewServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> denied.listByTenant(ctx)));
    }

    @Test
    @DisplayName("listByTenant returns all reviews for the tenant")
    void listByTenantSuccess() {
        runPromise(() -> service.generate(ctx, cmd()));
        List<DmNarrativeReview> results = runPromise(() -> service.listByTenant(ctx));

        assertThat(results).hasSize(1);
    }

    @Test
    @DisplayName("command validates null periodType")
    void commandValidation() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> new DmNarrativeReviewService.GenerateNarrativeReviewCommand(
                null, Instant.now(), Instant.now()));
    }

    private DmNarrativeReviewService.GenerateNarrativeReviewCommand cmd() {
        Instant now = Instant.now();
        return new DmNarrativeReviewService.GenerateNarrativeReviewCommand(
            DmNarrativePeriodType.MONTHLY,
            now.minusSeconds(30 * 24 * 3600),
            now);
    }

    static final class InMemoryNarrativeRepository implements DmNarrativeReviewRepository {
        private final Map<String, DmNarrativeReview> store = new ConcurrentHashMap<>();

        @Override public Promise<DmNarrativeReview> save(DmNarrativeReview r) { store.put(r.getId(), r); return Promise.of(r); }
        @Override public Promise<DmNarrativeReview> update(DmNarrativeReview r) { store.put(r.getId(), r); return Promise.of(r); }
        @Override public Promise<Optional<DmNarrativeReview>> findById(String id) { return Promise.of(Optional.ofNullable(store.get(id))); }
        @Override public Promise<List<DmNarrativeReview>> listByTenant(String tenantId) {
            return Promise.of(store.values().stream().filter(r -> r.getTenantId().equals(tenantId)).toList());
        }
        @Override public Promise<List<DmNarrativeReview>> listByStatus(String tenantId, DmNarrativeReviewStatus status) {
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

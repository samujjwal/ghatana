package com.ghatana.digitalmarketing.application.attribution;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.attribution.DmMediaMixModel;
import com.ghatana.digitalmarketing.domain.attribution.DmMediaMixModel.DmChannelContribution;
import com.ghatana.digitalmarketing.domain.attribution.DmMediaMixModelStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("DmMediaMixModelServiceImpl")
class DmMediaMixModelServiceImplTest extends EventloopTestBase {

    private EphemeralMixModelRepository repository;
    private DmMediaMixModelServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        repository = new EphemeralMixModelRepository();
        service = new DmMediaMixModelServiceImpl(repository, new StubKernelAdapter(true));
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    private DmMediaMixModelService.SubmitMediaMixModelCommand cmd() {
        return new DmMediaMixModelService.SubmitMediaMixModelCommand(
            "Q1 Attribution", "ws-1", List.of("ch-1", "ch-2"),
            Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-03-31T00:00:00Z")
        );
    }

    @Test
    @DisplayName("submit creates PENDING model")
    void submitSuccess() {
        DmMediaMixModel model = runPromise(() -> service.submit(ctx, cmd()));

        assertThat(model.getTenantId()).isEqualTo("tenant-1");
        assertThat(model.getModelName()).isEqualTo("Q1 Attribution");
        assertThat(model.getStatus()).isEqualTo(DmMediaMixModelStatus.PENDING);
        assertThat(model.getChannelIds()).containsExactly("ch-1", "ch-2");
    }

    @Test
    @DisplayName("submit rejects unauthorized actor")
    void submitUnauthorized() {
        service = new DmMediaMixModelServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.submit(ctx, cmd())));
    }

    @Test
    @DisplayName("submit with null workspaceId defaults to context workspaceId")
    void submitWithNullWorkspaceId() {
        DmMediaMixModelService.SubmitMediaMixModelCommand cmd =
            new DmMediaMixModelService.SubmitMediaMixModelCommand(
                "Q1 Attribution", null, List.of("ch-1", "ch-2"),
                Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-03-31T00:00:00Z")
            );
        DmMediaMixModel model = runPromise(() -> service.submit(ctx, cmd));
        assertThat(model.getWorkspaceId()).isEqualTo("ws-1");
    }

    @Test
    @DisplayName("submit with non-null workspaceId preserves it")
    void markFittingSuccess() {
        DmMediaMixModel submitted = runPromise(() -> service.submit(ctx, cmd()));
        DmMediaMixModel fitting = runPromise(() -> service.markFitting(ctx, submitted.getId()));

        assertThat(fitting.getStatus()).isEqualTo(DmMediaMixModelStatus.FITTING);
    }

    @Test
    @DisplayName("markReady records rSquared and contributions")
    void markReadySuccess() {
        DmMediaMixModel submitted = runPromise(() -> service.submit(ctx, cmd()));
        runPromise(() -> service.markFitting(ctx, submitted.getId()));
        List<DmChannelContribution> contributions = List.of(
            new DmChannelContribution("ch-1", 0.6, 3.5),
            new DmChannelContribution("ch-2", 0.4, 2.1)
        );
        DmMediaMixModel ready = runPromise(() -> service.markReady(ctx, submitted.getId(), 0.92, contributions));

        assertThat(ready.getStatus()).isEqualTo(DmMediaMixModelStatus.READY);
        assertThat(ready.getRSquared()).isEqualTo(0.92);
        assertThat(ready.getContributions()).hasSize(2);
        assertThat(ready.getFittedAt()).isNotNull();
    }

    @Test
    @DisplayName("markFailed records failure reason")
    void markFailedSuccess() {
        DmMediaMixModel submitted = runPromise(() -> service.submit(ctx, cmd()));
        DmMediaMixModel failed = runPromise(() -> service.markFailed(ctx, submitted.getId(), "Insufficient data"));

        assertThat(failed.getStatus()).isEqualTo(DmMediaMixModelStatus.FAILED);
        assertThat(failed.getFailureReason()).isEqualTo("Insufficient data");
    }

    @Test
    @DisplayName("listByTenant returns all models for tenant")
    void listByTenantSuccess() {
        runPromise(() -> service.submit(ctx, cmd()));
        runPromise(() -> service.submit(ctx, cmd()));

        List<DmMediaMixModel> list = runPromise(() -> service.listByTenant(ctx));
        assertThat(list).hasSize(2);
    }

    @Test
    @DisplayName("SubmitMediaMixModelCommand rejects empty channelIds")
    void commandValidation() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmMediaMixModelService.SubmitMediaMixModelCommand(
                "Model", "ws-1", List.of(),
                Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-03-31T00:00:00Z")));
    }

    @Test
    @DisplayName("SubmitMediaMixModelCommand rejects blank modelName")
    void commandValidationBlankName() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmMediaMixModelService.SubmitMediaMixModelCommand(
                "", "ws-1", List.of("ch-1"),
                Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-03-31T00:00:00Z")));
    }

    @Test
    @DisplayName("SubmitMediaMixModelCommand rejects null dataFrom")
    void commandValidationNullDataFrom() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmMediaMixModelService.SubmitMediaMixModelCommand(
                "Model", "ws-1", List.of("ch-1"), null, Instant.parse("2024-03-31T00:00:00Z")));
    }

    @Test
    @DisplayName("SubmitMediaMixModelCommand rejects dataTo before dataFrom")
    void commandValidationBadDateRange() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmMediaMixModelService.SubmitMediaMixModelCommand(
                "Model", "ws-1", List.of("ch-1"),
                Instant.parse("2024-03-31T00:00:00Z"), Instant.parse("2024-01-01T00:00:00Z")));
    }

    // ── In-memory repository ──────────────────────────────────────────────────

    static final class EphemeralMixModelRepository implements DmMediaMixModelRepository {
        private final Map<String, DmMediaMixModel> store = new ConcurrentHashMap<>();

        @Override public Promise<DmMediaMixModel> save(DmMediaMixModel m) { store.put(m.getId(), m); return Promise.of(m); }
        @Override public Promise<DmMediaMixModel> update(DmMediaMixModel m) { store.put(m.getId(), m); return Promise.of(m); }
        @Override public Promise<Optional<DmMediaMixModel>> findById(String id) { return Promise.of(Optional.ofNullable(store.get(id))); }
        @Override public Promise<List<DmMediaMixModel>> listByTenant(String tenantId) {
            List<DmMediaMixModel> r = new ArrayList<>();
            for (DmMediaMixModel m : store.values()) if (m.getTenantId().equals(tenantId)) r.add(m);
            return Promise.of(r);
        }
        @Override public Promise<List<DmMediaMixModel>> listByTenantAndStatus(String tenantId, DmMediaMixModelStatus status) {
            List<DmMediaMixModel> r = new ArrayList<>();
            for (DmMediaMixModel m : store.values()) if (m.getTenantId().equals(tenantId) && m.getStatus() == status) r.add(m);
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

package com.ghatana.digitalmarketing.application.experiment;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.experiment.DmExperiment;
import com.ghatana.digitalmarketing.domain.experiment.DmExperiment.DmExperimentVariant;
import com.ghatana.digitalmarketing.domain.experiment.DmExperimentStatus;
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

@DisplayName("DmExperimentServiceImpl")
class DmExperimentServiceImplTest extends EventloopTestBase {

    private InMemoryExperimentRepository repository;
    private DmExperimentServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        repository = new InMemoryExperimentRepository();
        service = new DmExperimentServiceImpl(repository, new StubKernelAdapter(true));
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    @Test
    @DisplayName("create experiment with DRAFT status")
    void createSuccess() {
        DmExperiment exp = runPromise(() -> service.create(ctx, cmd()));

        assertThat(exp.getStatus()).isEqualTo(DmExperimentStatus.DRAFT);
        assertThat(exp.getTenantId()).isEqualTo("tenant-1");
    }

    @Test
    @DisplayName("create rejects unauthorized actor")
    void createUnauthorized() {
        service = new DmExperimentServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.create(ctx, cmd())));
    }

    @Test
    @DisplayName("start transitions to RUNNING")
    void startSuccess() {
        DmExperiment created = runPromise(() -> service.create(ctx, cmd()));
        DmExperiment started = runPromise(() -> service.start(ctx, created.getId()));

        assertThat(started.getStatus()).isEqualTo(DmExperimentStatus.RUNNING);
    }

    @Test
    @DisplayName("start rejects unauthorized actor")
    void startUnauthorized() {
        DmExperiment created = runPromise(() -> service.create(ctx, cmd()));
        DmExperimentServiceImpl denied = new DmExperimentServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> denied.start(ctx, created.getId())));
    }

    @Test
    @DisplayName("conclude transitions to CONCLUDED with winner")
    void concludeSuccess() {
        DmExperiment created = runPromise(() -> service.create(ctx, cmd()));
        runPromise(() -> service.start(ctx, created.getId()));
        DmExperiment concluded = runPromise(() -> service.conclude(ctx, created.getId(), "variant-a"));

        assertThat(concluded.getStatus()).isEqualTo(DmExperimentStatus.CONCLUDED);
        assertThat(concluded.getWinnerVariantId()).isEqualTo("variant-a");
    }

    @Test
    @DisplayName("conclude rejects unauthorized actor")
    void concludeUnauthorized() {
        DmExperiment created = runPromise(() -> service.create(ctx, cmd()));
        runPromise(() -> service.start(ctx, created.getId()));
        DmExperimentServiceImpl denied = new DmExperimentServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> denied.conclude(ctx, created.getId(), "variant-a")));
    }

    @Test
    @DisplayName("cancel transitions to CANCELLED")
    void cancelSuccess() {
        DmExperiment created = runPromise(() -> service.create(ctx, cmd()));
        DmExperiment cancelled = runPromise(() -> service.cancel(ctx, created.getId()));

        assertThat(cancelled.getStatus()).isEqualTo(DmExperimentStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancel rejects unauthorized actor")
    void cancelUnauthorized() {
        DmExperiment created = runPromise(() -> service.create(ctx, cmd()));
        DmExperimentServiceImpl denied = new DmExperimentServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> denied.cancel(ctx, created.getId())));
    }

    @Test
    @DisplayName("findById enforces tenant isolation")
    void findByIdTenantIsolation() {
        DmExperiment created = runPromise(() -> service.create(ctx, cmd()));
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
    @DisplayName("listByStatus returns matching experiments")
    void listByStatusSuccess() {
        runPromise(() -> service.create(ctx, cmd()));
        List<DmExperiment> results = runPromise(() -> service.listByStatus(ctx, DmExperimentStatus.DRAFT));

        assertThat(results).hasSize(1);
    }

    @Test
    @DisplayName("command validates blank name")
    void commandValidation() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmExperimentService.CreateExperimentCommand("", null, List.of()));
    }

    private DmExperimentService.CreateExperimentCommand cmd() {
        return new DmExperimentService.CreateExperimentCommand(
            "Headline Test", "New headline increases CTR",
            List.of(new DmExperimentVariant("v-a", "Variant A", 50),
                    new DmExperimentVariant("v-b", "Variant B", 50)));
    }

    static final class InMemoryExperimentRepository implements DmExperimentRepository {
        private final Map<String, DmExperiment> store = new ConcurrentHashMap<>();

        @Override public Promise<DmExperiment> save(DmExperiment e) { store.put(e.getId(), e); return Promise.of(e); }
        @Override public Promise<DmExperiment> update(DmExperiment e) { store.put(e.getId(), e); return Promise.of(e); }
        @Override public Promise<Optional<DmExperiment>> findById(String id) { return Promise.of(Optional.ofNullable(store.get(id))); }
        @Override public Promise<List<DmExperiment>> listByTenant(String tenantId) {
            return Promise.of(store.values().stream().filter(e -> e.getTenantId().equals(tenantId)).toList());
        }
        @Override public Promise<List<DmExperiment>> listByStatus(String tenantId, DmExperimentStatus status) {
            return Promise.of(store.values().stream()
                .filter(e -> e.getTenantId().equals(tenantId) && e.getStatus() == status).toList());
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

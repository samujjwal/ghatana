package com.ghatana.digitalmarketing.application.budget;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.budget.DmBudgetAlert;
import com.ghatana.digitalmarketing.domain.budget.DmBudgetAlertLevel;
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

@DisplayName("DmBudgetAlertServiceImpl")
class DmBudgetAlertServiceImplTest extends EventloopTestBase {

    private InMemoryAlertRepository repository;
    private DmBudgetAlertServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAlertRepository();
        service = new DmBudgetAlertServiceImpl(repository, new StubKernelAdapter(true));
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    @Test
    @DisplayName("fire creates unacknowledged alert")
    void fireSuccess() {
        DmBudgetAlert alert = runPromise(() -> service.fire(ctx, cmd()));

        assertThat(alert.getTenantId()).isEqualTo("tenant-1");
        assertThat(alert.isAcknowledged()).isFalse();
    }

    @Test
    @DisplayName("fire rejects unauthorized actor")
    void fireUnauthorized() {
        service = new DmBudgetAlertServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.fire(ctx, cmd())));
    }

    @Test
    @DisplayName("acknowledge transitions alert to acknowledged")
    void acknowledgeSuccess() {
        DmBudgetAlert created = runPromise(() -> service.fire(ctx, cmd()));
        DmBudgetAlert acked = runPromise(() -> service.acknowledge(ctx, created.getId()));

        assertThat(acked.isAcknowledged()).isTrue();
    }

    @Test
    @DisplayName("findById enforces tenant isolation")
    void findByIdTenantIsolation() {
        DmBudgetAlert created = runPromise(() -> service.fire(ctx, cmd()));
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
    @DisplayName("listUnacknowledged returns open alerts")
    void listUnacknowledgedSuccess() {
        runPromise(() -> service.fire(ctx, cmd()));
        List<DmBudgetAlert> results = runPromise(() -> service.listUnacknowledged(ctx));

        assertThat(results).hasSize(1);
    }

    @Test
    @DisplayName("listByCampaign returns alerts for campaign")
    void listByCampaignSuccess() {
        runPromise(() -> service.fire(ctx, cmd()));
        List<DmBudgetAlert> results = runPromise(() -> service.listByCampaign(ctx, "campaign-1"));

        assertThat(results).hasSize(1);
    }

    @Test
    @DisplayName("command validates blank campaignId")
    void commandValidation() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmBudgetAlertService.FireBudgetAlertCommand(
                "", 10000L, 8000L, 0.8, DmBudgetAlertLevel.WARNING, "pacing over 80%"));
    }

    @Test
    @DisplayName("acknowledge rejects unauthorized actor")
    void acknowledgeUnauthorized() {
        DmBudgetAlert fired = runPromise(() -> service.fire(ctx, cmd()));
        service = new DmBudgetAlertServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.acknowledge(ctx, fired.getId())));
    }

    @Test
    @DisplayName("listByCampaign rejects unauthorized actor")
    void listByCampaignUnauthorized() {
        service = new DmBudgetAlertServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.listByCampaign(ctx, "campaign-1")));
    }

    @Test
    @DisplayName("listUnacknowledged rejects unauthorized actor")
    void listUnacknowledgedUnauthorized() {
        service = new DmBudgetAlertServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.listUnacknowledged(ctx)));
    }

    @Test
    @DisplayName("acknowledge throws NoSuchElementException for unknown alert id")
    void acknowledgeUnknownId() {
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.acknowledge(ctx, "nonexistent-alert")));
    }

    private DmBudgetAlertService.FireBudgetAlertCommand cmd() {
        return new DmBudgetAlertService.FireBudgetAlertCommand(
            "campaign-1", 10_000_000L, 8_500_000L, 0.85, DmBudgetAlertLevel.WARNING, "Over 85% spent");
    }

    static final class InMemoryAlertRepository implements DmBudgetAlertRepository {
        private final Map<String, DmBudgetAlert> store = new ConcurrentHashMap<>();

        @Override public Promise<DmBudgetAlert> save(DmBudgetAlert a) { store.put(a.getId(), a); return Promise.of(a); }
        @Override public Promise<DmBudgetAlert> update(DmBudgetAlert a) { store.put(a.getId(), a); return Promise.of(a); }
        @Override public Promise<Optional<DmBudgetAlert>> findById(String id) { return Promise.of(Optional.ofNullable(store.get(id))); }
        @Override public Promise<List<DmBudgetAlert>> listByTenant(String tenantId) {
            return Promise.of(store.values().stream().filter(a -> a.getTenantId().equals(tenantId)).toList());
        }
        @Override public Promise<List<DmBudgetAlert>> listByCampaign(String tenantId, String campaignId) {
            return Promise.of(store.values().stream()
                .filter(a -> a.getTenantId().equals(tenantId) && a.getCampaignId().equals(campaignId)).toList());
        }
        @Override public Promise<List<DmBudgetAlert>> listUnacknowledged(String tenantId) {
            return Promise.of(store.values().stream()
                .filter(a -> a.getTenantId().equals(tenantId) && !a.isAcknowledged()).toList());
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

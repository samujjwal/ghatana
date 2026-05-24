package com.ghatana.digitalmarketing.application.dashboard;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.dashboard.DmAnalyticsDashboard;
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

@DisplayName("DmAnalyticsDashboardServiceImpl")
class DmAnalyticsDashboardServiceImplTest extends EventloopTestBase {

    private EphemeralDashboardRepository repository;
    private DmAnalyticsDashboardServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        repository = new EphemeralDashboardRepository();
        service = new DmAnalyticsDashboardServiceImpl(repository, new StubKernelAdapter(true));
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    @Test
    @DisplayName("create stores dashboard")
    void createSuccess() {
        DmAnalyticsDashboard dashboard = runPromise(() -> service.create(ctx, createCmd()));

        assertThat(dashboard.getTenantId()).isEqualTo("tenant-1");
        assertThat(dashboard.getName()).isEqualTo("My Dashboard");
    }

    @Test
    @DisplayName("create rejects unauthorized actor")
    void createUnauthorized() {
        service = new DmAnalyticsDashboardServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.create(ctx, createCmd())));
    }

    @Test
    @DisplayName("update changes dashboard name and widgets")
    void updateSuccess() {
        DmAnalyticsDashboard created = runPromise(() -> service.create(ctx, createCmd()));
        DmAnalyticsDashboard updated = runPromise(() -> service.update(ctx, created.getId(),
            new DmAnalyticsDashboardService.UpdateDashboardCommand("Updated Name", null, List.of())));

        assertThat(updated.getName()).isEqualTo("Updated Name");
    }

    @Test
    @DisplayName("update rejects unauthorized actor")
    void updateUnauthorized() {
        DmAnalyticsDashboard created = runPromise(() -> service.create(ctx, createCmd()));
        service = new DmAnalyticsDashboardServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.update(ctx, created.getId(),
                new DmAnalyticsDashboardService.UpdateDashboardCommand("X", null, List.of()))));
    }

    @Test
    @DisplayName("findById enforces tenant isolation")
    void findByIdTenantIsolation() {
        DmAnalyticsDashboard created = runPromise(() -> service.create(ctx, createCmd()));
        DmOperationContext other = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-2"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("u2"))
            .correlationId(DmCorrelationId.of("c2"))
            .idempotencyKey(DmIdempotencyKey.of("i2"))
            .build();

        Optional<DmAnalyticsDashboard> result = runPromise(() -> service.findById(other, created.getId()));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("listByTenant returns tenant dashboards")
    void listByTenantSuccess() {
        runPromise(() -> service.create(ctx, createCmd()));
        List<DmAnalyticsDashboard> results = runPromise(() -> service.listByTenant(ctx));

        assertThat(results).hasSize(1);
    }

    @Test
    @DisplayName("listByTenant rejects unauthorized actor")
    void listByTenantUnauthorized() {
        service = new DmAnalyticsDashboardServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.listByTenant(ctx)));
    }

    @Test
    @DisplayName("create command validates blank name")
    void commandValidation() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmAnalyticsDashboardService.CreateDashboardCommand("", null, List.of()));
    }

    private DmAnalyticsDashboardService.CreateDashboardCommand createCmd() {
        return new DmAnalyticsDashboardService.CreateDashboardCommand("My Dashboard", "Desc", List.of());
    }

    static final class EphemeralDashboardRepository implements DmAnalyticsDashboardRepository {
        private final Map<String, DmAnalyticsDashboard> store = new ConcurrentHashMap<>();

        @Override public Promise<DmAnalyticsDashboard> save(DmAnalyticsDashboard d) { store.put(d.getId(), d); return Promise.of(d); }
        @Override public Promise<DmAnalyticsDashboard> update(DmAnalyticsDashboard d) { store.put(d.getId(), d); return Promise.of(d); }
        @Override public Promise<Optional<DmAnalyticsDashboard>> findById(String id) {
            return Promise.of(Optional.ofNullable(store.get(id)));
        }
        @Override public Promise<List<DmAnalyticsDashboard>> listByTenant(String tenantId) {
            return Promise.of(store.values().stream().filter(d -> d.getTenantId().equals(tenantId)).toList());
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

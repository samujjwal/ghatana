package com.ghatana.digitalmarketing.application.killswitch;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.killswitch.DmKillSwitch;
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

@DisplayName("DmKillSwitchServiceImpl")
class DmKillSwitchServiceImplTest extends EventloopTestBase {

    private EphemeralKillSwitchRepository repository;
    private DmKillSwitchServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        repository = new EphemeralKillSwitchRepository();
        service = new DmKillSwitchServiceImpl(repository, new StubKernelAdapter(true));
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    @Test
    @DisplayName("activate creates active kill switch")
    void activateSuccess() {
        DmKillSwitch ks = runPromise(() -> service.activate(ctx, cmd()));

        assertThat(ks.isActive()).isTrue();
        assertThat(ks.getTenantId()).isEqualTo("tenant-1");
        assertThat(ks.getScope()).isEqualTo("campaign");
    }

    @Test
    @DisplayName("activate rejects unauthorized actor")
    void activateUnauthorized() {
        service = new DmKillSwitchServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.activate(ctx, cmd())));
    }

    @Test
    @DisplayName("deactivate sets active to false")
    void deactivateSuccess() {
        DmKillSwitch activated = runPromise(() -> service.activate(ctx, cmd()));
        DmKillSwitch deactivated = runPromise(() -> service.deactivate(ctx, activated.getId()));

        assertThat(deactivated.isActive()).isFalse();
        assertThat(deactivated.getDeactivatedAt()).isNotNull();
    }

    @Test
    @DisplayName("deactivate rejects already inactive kill switch")
    void deactivateAlreadyInactive() {
        DmKillSwitch activated = runPromise(() -> service.activate(ctx, cmd()));
        runPromise(() -> service.deactivate(ctx, activated.getId()));

        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> runPromise(() -> service.deactivate(ctx, activated.getId())));
    }

    @Test
    @DisplayName("findById enforces tenant isolation")
    void findByIdTenantIsolation() {
        DmKillSwitch activated = runPromise(() -> service.activate(ctx, cmd()));
        DmOperationContext other = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-2"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("u2"))
            .correlationId(DmCorrelationId.of("c2"))
            .idempotencyKey(DmIdempotencyKey.of("i2"))
            .build();

        Optional<DmKillSwitch> result = runPromise(() -> service.findById(other, activated.getId()));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("listActive returns only active kill switches for tenant")
    void listActiveSuccess() {
        runPromise(() -> service.activate(ctx, cmd()));
        List<DmKillSwitch> active = runPromise(() -> service.listActive(ctx));

        assertThat(active).hasSize(1);
        assertThat(active.get(0).isActive()).isTrue();
    }

    @Test
    @DisplayName("listActive rejects unauthorized actor")
    void listActiveUnauthorized() {
        service = new DmKillSwitchServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.listActive(ctx)));
    }

    @Test
    @DisplayName("findActiveByScope returns matching active kill switch")
    void findActiveByScopeSuccess() {
        runPromise(() -> service.activate(ctx, cmd()));
        Optional<DmKillSwitch> result = runPromise(() -> service.findActiveByScope(ctx, "campaign", "camp-1"));

        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("command validates blank reason")
    void commandValidatesReason() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmKillSwitchService.ActivateKillSwitchCommand("campaign", "camp-1", ""));
    }

    private DmKillSwitchService.ActivateKillSwitchCommand cmd() {
        return new DmKillSwitchService.ActivateKillSwitchCommand("campaign", "camp-1", "Emergency: budget exceeded");
    }

    static final class EphemeralKillSwitchRepository implements DmKillSwitchRepository {
        private final Map<String, DmKillSwitch> store = new ConcurrentHashMap<>();

        @Override public Promise<DmKillSwitch> save(DmKillSwitch ks) { store.put(ks.getId(), ks); return Promise.of(ks); }
        @Override public Promise<DmKillSwitch> update(DmKillSwitch ks) { store.put(ks.getId(), ks); return Promise.of(ks); }
        @Override public Promise<Optional<DmKillSwitch>> findById(String id) {
            return Promise.of(Optional.ofNullable(store.get(id)));
        }
        @Override public Promise<List<DmKillSwitch>> listActive(String tenantId) {
            return Promise.of(store.values().stream().filter(ks -> ks.getTenantId().equals(tenantId) && ks.isActive()).toList());
        }
        @Override public Promise<Optional<DmKillSwitch>> findActiveByScope(String tenantId, String scope, String scopeId) {
            return Promise.of(store.values().stream()
                .filter(ks -> ks.getTenantId().equals(tenantId) && ks.isActive()
                    && ks.getScope().equals(scope) && ks.getScopeId().equals(scopeId))
                .findFirst());
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

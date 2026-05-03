package com.ghatana.digitalmarketing.application.security;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.security.DmEnterpriseSecurityConfig;
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

@DisplayName("DmEnterpriseSecurityConfigServiceImpl")
class DmEnterpriseSecurityConfigServiceImplTest extends EventloopTestBase {

    private InMemorySecurityConfigRepository repository;
    private DmEnterpriseSecurityConfigServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        repository = new InMemorySecurityConfigRepository();
        service = new DmEnterpriseSecurityConfigServiceImpl(repository, new StubKernelAdapter(true));
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    private DmEnterpriseSecurityConfigService.ProvisionSecurityConfigCommand provisionCmd() {
        return new DmEnterpriseSecurityConfigService.ProvisionSecurityConfigCommand(
            true, false, List.of(), true, 30, null, null
        );
    }

    @Test
    @DisplayName("provision creates config for tenant")
    void provisionSuccess() {
        DmEnterpriseSecurityConfig config = runPromise(() -> service.provision(ctx, provisionCmd()));

        assertThat(config.getTenantId()).isEqualTo("tenant-1");
        assertThat(config.isMfaRequired()).isTrue();
        assertThat(config.getSessionTimeoutMinutes()).isEqualTo(30);
        assertThat(config.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("provision rejects unauthorized actor")
    void provisionUnauthorized() {
        service = new DmEnterpriseSecurityConfigServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.provision(ctx, provisionCmd())));
    }

    @Test
    @DisplayName("update modifies config fields")
    void updateSuccess() {
        DmEnterpriseSecurityConfig created = runPromise(() -> service.provision(ctx, provisionCmd()));

        DmEnterpriseSecurityConfigService.UpdateSecurityConfigCommand updateCmd =
            new DmEnterpriseSecurityConfigService.UpdateSecurityConfigCommand(
                false, true, List.of("10.0.0.0/8"), true, 60, "okta", "https://okta.example.com/meta"
            );

        DmEnterpriseSecurityConfig updated = runPromise(() -> service.update(ctx, created.getId(), updateCmd));

        assertThat(updated.isMfaRequired()).isFalse();
        assertThat(updated.isIpAllowlistEnabled()).isTrue();
        assertThat(updated.getAllowedIpCidrs()).containsExactly("10.0.0.0/8");
        assertThat(updated.getSessionTimeoutMinutes()).isEqualTo(60);
        assertThat(updated.getSsoProvider()).isEqualTo("okta");
        assertThat(updated.getCreatedAt()).isEqualTo(created.getCreatedAt());
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(created.getUpdatedAt());
    }

    @Test
    @DisplayName("update rejects config from different tenant")
    void updateTenantIsolation() {
        DmEnterpriseSecurityConfig created = runPromise(() -> service.provision(ctx, provisionCmd()));
        DmOperationContext otherCtx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("other-tenant"))
            .workspaceId(DmWorkspaceId.of("ws-2"))
            .actor(ActorRef.user("user-2"))
            .correlationId(DmCorrelationId.of("corr-2"))
            .idempotencyKey(DmIdempotencyKey.of("idk-2"))
            .build();

        DmEnterpriseSecurityConfigService.UpdateSecurityConfigCommand updateCmd =
            new DmEnterpriseSecurityConfigService.UpdateSecurityConfigCommand(
                false, false, List.of(), false, 30, null, null
            );

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.update(otherCtx, created.getId(), updateCmd)));
    }

    @Test
    @DisplayName("findByTenant returns config for current tenant")
    void findByTenantSuccess() {
        runPromise(() -> service.provision(ctx, provisionCmd()));

        Optional<DmEnterpriseSecurityConfig> found = runPromise(() -> service.findByTenant(ctx));
        assertThat(found).isPresent();
        assertThat(found.get().getTenantId()).isEqualTo("tenant-1");
    }

    @Test
    @DisplayName("ProvisionSecurityConfigCommand rejects invalid sessionTimeoutMinutes")
    void commandValidation() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmEnterpriseSecurityConfigService.ProvisionSecurityConfigCommand(
                true, false, List.of(), true, 0, null, null));
    }

    // ── In-memory repository ──────────────────────────────────────────────────

    static final class InMemorySecurityConfigRepository implements DmEnterpriseSecurityConfigRepository {
        private final Map<String, DmEnterpriseSecurityConfig> store = new ConcurrentHashMap<>();

        @Override public Promise<DmEnterpriseSecurityConfig> save(DmEnterpriseSecurityConfig c) { store.put(c.getId(), c); return Promise.of(c); }
        @Override public Promise<DmEnterpriseSecurityConfig> update(DmEnterpriseSecurityConfig c) { store.put(c.getId(), c); return Promise.of(c); }
        @Override public Promise<Optional<DmEnterpriseSecurityConfig>> findById(String id) { return Promise.of(Optional.ofNullable(store.get(id))); }
        @Override public Promise<Optional<DmEnterpriseSecurityConfig>> findByTenantId(String tenantId) {
            return Promise.of(store.values().stream().filter(c -> c.getTenantId().equals(tenantId)).findFirst());
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

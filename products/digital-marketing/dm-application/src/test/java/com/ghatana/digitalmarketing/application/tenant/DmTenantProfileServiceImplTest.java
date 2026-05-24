package com.ghatana.digitalmarketing.application.tenant;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.tenant.DmSelfMarketingTenantProfile;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("DmTenantProfileServiceImpl")
class DmTenantProfileServiceImplTest extends EventloopTestBase {

    private EphemeralProfileRepository repository;
    private DmTenantProfileServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        repository = new EphemeralProfileRepository();
        service = new DmTenantProfileServiceImpl(repository, new StubKernelAdapter(true));
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    @Test
    @DisplayName("provision creates tenant profile")
    void provisionSuccess() {
        DmSelfMarketingTenantProfile profile = runPromise(() -> service.provision(ctx, provisionCmd()));

        assertThat(profile.getTenantId()).isEqualTo("tenant-1");
        assertThat(profile.getDisplayName()).isEqualTo("Acme Corp");
    }

    @Test
    @DisplayName("provision rejects unauthorized actor")
    void provisionUnauthorized() {
        service = new DmTenantProfileServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.provision(ctx, provisionCmd())));
    }

    @Test
    @DisplayName("update modifies existing profile")
    void updateSuccess() {
        runPromise(() -> service.provision(ctx, provisionCmd()));
        DmSelfMarketingTenantProfile updated = runPromise(() -> service.update(ctx, updateCmd()));

        assertThat(updated.getDisplayName()).isEqualTo("Acme Corp Updated");
        assertThat(updated.isKillSwitchEnabled()).isTrue();
    }

    @Test
    @DisplayName("update rejects unauthorized actor")
    void updateUnauthorized() {
        runPromise(() -> service.provision(ctx, provisionCmd()));
        service = new DmTenantProfileServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.update(ctx, updateCmd())));
    }

    @Test
    @DisplayName("findByTenant returns profile for tenant")
    void findByTenantSuccess() {
        runPromise(() -> service.provision(ctx, provisionCmd()));
        Optional<DmSelfMarketingTenantProfile> result = runPromise(() -> service.findByTenant(ctx));

        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("findByTenant returns empty for unknown tenant")
    void findByTenantEmpty() {
        Optional<DmSelfMarketingTenantProfile> result = runPromise(() -> service.findByTenant(ctx));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("provision command validates blank displayName")
    void commandValidation() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmTenantProfileService.ProvisionProfileCommand("", null, null, null));
    }

    private DmTenantProfileService.ProvisionProfileCommand provisionCmd() {
        return new DmTenantProfileService.ProvisionProfileCommand("Acme Corp", "Tech", "UTC", "USD");
    }

    private DmTenantProfileService.UpdateProfileCommand updateCmd() {
        return new DmTenantProfileService.UpdateProfileCommand(
            "Acme Corp Updated", "Tech", "UTC", "USD", true, 5, 10, 10_000_000_000L);
    }

    static final class EphemeralProfileRepository implements DmTenantProfileRepository {
        private final Map<String, DmSelfMarketingTenantProfile> store = new ConcurrentHashMap<>();

        @Override public Promise<DmSelfMarketingTenantProfile> save(DmSelfMarketingTenantProfile p) {
            store.put(p.getTenantId(), p); return Promise.of(p);
        }
        @Override public Promise<DmSelfMarketingTenantProfile> update(DmSelfMarketingTenantProfile p) {
            store.put(p.getTenantId(), p); return Promise.of(p);
        }
        @Override public Promise<Optional<DmSelfMarketingTenantProfile>> findByTenantId(String tenantId) {
            return Promise.of(Optional.ofNullable(store.get(tenantId)));
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

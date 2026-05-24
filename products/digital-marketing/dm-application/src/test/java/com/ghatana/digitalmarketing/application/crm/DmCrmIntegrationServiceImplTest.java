package com.ghatana.digitalmarketing.application.crm;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.crm.DmCrmIntegration;
import com.ghatana.digitalmarketing.domain.crm.DmCrmIntegrationStatus;
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

@DisplayName("DmCrmIntegrationServiceImpl")
class DmCrmIntegrationServiceImplTest extends EventloopTestBase {

    private EphemeralCrmRepository repository;
    private DmCrmIntegrationServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        repository = new EphemeralCrmRepository();
        service = new DmCrmIntegrationServiceImpl(repository, new StubKernelAdapter(true));
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    private DmCrmIntegrationService.CreateCrmIntegrationCommand cmd() {
        return new DmCrmIntegrationService.CreateCrmIntegrationCommand(
            "Salesforce", "My CRM", "https://api.salesforce.com", "cred-ref-1"
        );
    }

    @Test
    @DisplayName("create produces integration with PENDING status")
    void createSuccess() {
        DmCrmIntegration integration = runPromise(() -> service.create(ctx, cmd()));

        assertThat(integration.getStatus()).isEqualTo(DmCrmIntegrationStatus.PENDING);
        assertThat(integration.getTenantId()).isEqualTo("tenant-1");
        assertThat(integration.getCrmProvider()).isEqualTo("Salesforce");
    }

    @Test
    @DisplayName("create rejects unauthorized actor")
    void createUnauthorized() {
        service = new DmCrmIntegrationServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.create(ctx, cmd())));
    }

    @Test
    @DisplayName("activate rejects unauthorized actor")
    void activateUnauthorized() {
        DmCrmIntegration created = runPromise(() -> service.create(ctx, cmd()));
        DmCrmIntegrationServiceImpl deniedService = new DmCrmIntegrationServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> deniedService.activate(ctx, created.getId())));
    }

    @Test
    @DisplayName("activate transitions integration to ACTIVE")
    void activateSuccess() {
        DmCrmIntegration created = runPromise(() -> service.create(ctx, cmd()));
        DmCrmIntegration activated = runPromise(() -> service.activate(ctx, created.getId()));

        assertThat(activated.getStatus()).isEqualTo(DmCrmIntegrationStatus.ACTIVE);
    }

    @Test
    @DisplayName("recordSync updates lastSyncAt")
    void recordSyncSuccess() {
        DmCrmIntegration created = runPromise(() -> service.create(ctx, cmd()));
        runPromise(() -> service.activate(ctx, created.getId()));
        DmCrmIntegration synced = runPromise(() -> service.recordSync(ctx, created.getId()));

        assertThat(synced.getLastSyncAt()).isNotNull();
    }

    @Test
    @DisplayName("markFailed transitions integration to FAILED")
    void markFailedSuccess() {
        DmCrmIntegration created = runPromise(() -> service.create(ctx, cmd()));
        DmCrmIntegration failed = runPromise(() -> service.markFailed(ctx, created.getId(), "timeout"));

        assertThat(failed.getStatus()).isEqualTo(DmCrmIntegrationStatus.FAILED);
        assertThat(failed.getFailureReason()).isEqualTo("timeout");
    }

    @Test
    @DisplayName("findById returns empty for different tenant")
    void findByIdTenantIsolation() {
        DmCrmIntegration created = runPromise(() -> service.create(ctx, cmd()));

        DmOperationContext otherCtx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("other-tenant"))
            .workspaceId(DmWorkspaceId.of("ws-2"))
            .actor(ActorRef.user("user-2"))
            .correlationId(DmCorrelationId.of("corr-2"))
            .idempotencyKey(DmIdempotencyKey.of("idk-2"))
            .build();

        Optional<DmCrmIntegration> result = runPromise(() -> service.findById(otherCtx, created.getId()));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("listByTenant returns only tenant integrations")
    void listByTenantSuccess() {
        runPromise(() -> service.create(ctx, cmd()));
        runPromise(() -> service.create(ctx, cmd()));

        List<DmCrmIntegration> list = runPromise(() -> service.listByTenant(ctx));
        assertThat(list).hasSize(2);
    }

    @Test
    @DisplayName("CreateCrmIntegrationCommand rejects blank crmProvider")
    void commandValidation() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmCrmIntegrationService.CreateCrmIntegrationCommand(
                "", "Display", "https://api.example.com", "cred-1"
            ));
    }

    @Test
    @DisplayName("CreateCrmIntegrationCommand rejects null displayName")
    void commandValidationNullDisplay() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmCrmIntegrationService.CreateCrmIntegrationCommand(
                "HubSpot", null, "https://api.example.com", "cred-1"
            ));
    }

    @Test
    @DisplayName("CreateCrmIntegrationCommand rejects blank apiEndpoint")
    void commandValidationBlankEndpoint() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmCrmIntegrationService.CreateCrmIntegrationCommand(
                "HubSpot", "Display", "", "cred-1"
            ));
    }

    @Test
    @DisplayName("CreateCrmIntegrationCommand rejects null credentialRef")
    void commandValidationNullCred() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmCrmIntegrationService.CreateCrmIntegrationCommand(
                "HubSpot", "Display", "https://api.example.com", null
            ));
    }

    // ── In-memory repository ──────────────────────────────────────────────────

    static final class EphemeralCrmRepository implements DmCrmIntegrationRepository {
        private final Map<String, DmCrmIntegration> store = new ConcurrentHashMap<>();

        @Override public Promise<DmCrmIntegration> save(DmCrmIntegration i) { store.put(i.getId(), i); return Promise.of(i); }
        @Override public Promise<DmCrmIntegration> update(DmCrmIntegration i) { store.put(i.getId(), i); return Promise.of(i); }
        @Override public Promise<Optional<DmCrmIntegration>> findById(String id) { return Promise.of(Optional.ofNullable(store.get(id))); }
        @Override public Promise<List<DmCrmIntegration>> listByTenant(String tenantId) {
            List<DmCrmIntegration> r = new ArrayList<>();
            for (DmCrmIntegration i : store.values()) if (i.getTenantId().equals(tenantId)) r.add(i);
            return Promise.of(r);
        }
        @Override public Promise<List<DmCrmIntegration>> listByStatus(String tenantId, DmCrmIntegrationStatus status) {
            List<DmCrmIntegration> r = new ArrayList<>();
            for (DmCrmIntegration i : store.values()) if (i.getTenantId().equals(tenantId) && i.getStatus() == status) r.add(i);
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

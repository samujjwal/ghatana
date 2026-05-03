package com.ghatana.digitalmarketing.application.connector;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.connector.DmMetaAdsConnector;
import com.ghatana.digitalmarketing.domain.connector.DmMetaAdsConnectorStatus;
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

@DisplayName("DmMetaAdsConnectorServiceImpl")
class DmMetaAdsConnectorServiceImplTest extends EventloopTestBase {

    private InMemoryConnectorRepository repository;
    private DmMetaAdsConnectorServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        repository = new InMemoryConnectorRepository();
        service = new DmMetaAdsConnectorServiceImpl(repository, new StubKernelAdapter(true));
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    private DmMetaAdsConnectorService.RegisterMetaAdsConnectorCommand cmd() {
        return new DmMetaAdsConnectorService.RegisterMetaAdsConnectorCommand(
            "My Meta Connector", "app-123", "account-456", "access-token-abc"
        );
    }

    @Test
    @DisplayName("register creates connector with PENDING status")
    void registerSuccess() {
        DmMetaAdsConnector connector = runPromise(() -> service.register(ctx, cmd()));

        assertThat(connector.getStatus()).isEqualTo(DmMetaAdsConnectorStatus.PENDING);
        assertThat(connector.getTenantId()).isEqualTo("tenant-1");
        assertThat(connector.getDisplayName()).isEqualTo("My Meta Connector");
        assertThat(connector.getId()).isNotBlank();
    }

    @Test
    @DisplayName("register rejects unauthorized actor")
    void registerUnauthorized() {
        service = new DmMetaAdsConnectorServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.register(ctx, cmd())));
    }

    @Test
    @DisplayName("activate rejects unauthorized actor")
    void activateUnauthorized() {
        DmMetaAdsConnector registered = runPromise(() -> service.register(ctx, cmd()));
        DmMetaAdsConnectorServiceImpl deniedService = new DmMetaAdsConnectorServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> deniedService.activate(ctx, registered.getId())));
    }

    @Test
    @DisplayName("activate transitions connector to ACTIVE")
    void activateSuccess() {
        DmMetaAdsConnector registered = runPromise(() -> service.register(ctx, cmd()));
        DmMetaAdsConnector activated = runPromise(() -> service.activate(ctx, registered.getId()));

        assertThat(activated.getStatus()).isEqualTo(DmMetaAdsConnectorStatus.ACTIVE);
    }

    @Test
    @DisplayName("markFailed transitions connector to FAILED")
    void markFailedSuccess() {
        DmMetaAdsConnector registered = runPromise(() -> service.register(ctx, cmd()));
        DmMetaAdsConnector failed = runPromise(() -> service.markFailed(ctx, registered.getId(), "OAuth error"));

        assertThat(failed.getStatus()).isEqualTo(DmMetaAdsConnectorStatus.FAILED);
        assertThat(failed.getFailureReason()).isEqualTo("OAuth error");
    }

    @Test
    @DisplayName("disconnect transitions connector to DISCONNECTED")
    void disconnectSuccess() {
        DmMetaAdsConnector registered = runPromise(() -> service.register(ctx, cmd()));
        runPromise(() -> service.activate(ctx, registered.getId()));
        DmMetaAdsConnector disconnected = runPromise(() -> service.disconnect(ctx, registered.getId()));

        assertThat(disconnected.getStatus()).isEqualTo(DmMetaAdsConnectorStatus.DISCONNECTED);
    }

    @Test
    @DisplayName("findById returns empty for different tenant")
    void findByIdTenantIsolation() {
        DmMetaAdsConnector registered = runPromise(() -> service.register(ctx, cmd()));

        DmOperationContext otherCtx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("other-tenant"))
            .workspaceId(DmWorkspaceId.of("ws-2"))
            .actor(ActorRef.user("user-2"))
            .correlationId(DmCorrelationId.of("corr-2"))
            .idempotencyKey(DmIdempotencyKey.of("idk-2"))
            .build();

        Optional<DmMetaAdsConnector> result = runPromise(() -> service.findById(otherCtx, registered.getId()));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("listByTenant returns only tenant connectors")
    void listByTenantSuccess() {
        runPromise(() -> service.register(ctx, cmd()));
        runPromise(() -> service.register(ctx, cmd()));

        List<DmMetaAdsConnector> list = runPromise(() -> service.listByTenant(ctx));
        assertThat(list).hasSize(2);
        assertThat(list).allMatch(c -> c.getTenantId().equals("tenant-1"));
    }

    @Test
    @DisplayName("RegisterMetaAdsConnectorCommand rejects blank displayName")
    void commandValidation() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmMetaAdsConnectorService.RegisterMetaAdsConnectorCommand(
                "", "app-1", "acct-1", "token-1"
            ));
    }

    @Test
    @DisplayName("RegisterMetaAdsConnectorCommand rejects null appId")
    void commandValidationNullAppId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmMetaAdsConnectorService.RegisterMetaAdsConnectorCommand(
                "Name", null, "acct-1", "token-1"
            ));
    }

    @Test
    @DisplayName("RegisterMetaAdsConnectorCommand rejects blank accountId")
    void commandValidationBlankAccountId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmMetaAdsConnectorService.RegisterMetaAdsConnectorCommand(
                "Name", "app-1", "", "token-1"
            ));
    }

    @Test
    @DisplayName("RegisterMetaAdsConnectorCommand rejects null accessToken")
    void commandValidationNullToken() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmMetaAdsConnectorService.RegisterMetaAdsConnectorCommand(
                "Name", "app-1", "acct-1", null
            ));
    }

    // ── In-memory repository ──────────────────────────────────────────────────

    static final class InMemoryConnectorRepository implements DmMetaAdsConnectorRepository {
        private final Map<String, DmMetaAdsConnector> store = new ConcurrentHashMap<>();

        @Override
        public Promise<DmMetaAdsConnector> save(DmMetaAdsConnector c) {
            store.put(c.getId(), c);
            return Promise.of(c);
        }

        @Override
        public Promise<DmMetaAdsConnector> update(DmMetaAdsConnector c) {
            store.put(c.getId(), c);
            return Promise.of(c);
        }

        @Override
        public Promise<Optional<DmMetaAdsConnector>> findById(String id) {
            return Promise.of(Optional.ofNullable(store.get(id)));
        }

        @Override
        public Promise<List<DmMetaAdsConnector>> listByTenant(String tenantId) {
            List<DmMetaAdsConnector> result = new ArrayList<>();
            for (DmMetaAdsConnector c : store.values()) {
                if (c.getTenantId().equals(tenantId)) result.add(c);
            }
            return Promise.of(result);
        }

        @Override
        public Promise<List<DmMetaAdsConnector>> listByStatus(String tenantId, DmMetaAdsConnectorStatus status) {
            List<DmMetaAdsConnector> result = new ArrayList<>();
            for (DmMetaAdsConnector c : store.values()) {
                if (c.getTenantId().equals(tenantId) && c.getStatus() == status) result.add(c);
            }
            return Promise.of(result);
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

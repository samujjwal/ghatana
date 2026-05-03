package com.ghatana.digitalmarketing.application.connector;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorConfig;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorStatus;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorType;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Unit tests for {@link DmConnectorServiceImpl}.
 *
 * @doc.type class
 * @doc.purpose Verifies connector service lifecycle with authorization and tenant isolation (DMOS-F2-006)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DmConnectorServiceImpl Tests")
class DmConnectorServiceImplTest extends EventloopTestBase {

    private InMemoryConnectorRepository repo;
    private DmConnectorServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        repo    = new InMemoryConnectorRepository();
        service = new DmConnectorServiceImpl(repo, new AllowingKernelAdapter(true));
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    private DmConnectorService.RegisterConnectorRequest sampleRequest() {
        return new DmConnectorService.RegisterConnectorRequest(
            "Google Ads",
            DmConnectorType.GOOGLE_ADS,
            Map.of("clientId", "abc123"),
            "ext-account-1"
        );
    }

    @Test
    @DisplayName("register creates PENDING connector")
    void registerSuccess() {
        DmConnectorConfig c = runPromise(() -> service.register(ctx, sampleRequest()));
        assertThat(c.getId()).isNotBlank();
        assertThat(c.getStatus()).isEqualTo(DmConnectorStatus.PENDING);
        assertThat(c.getTenantId()).isEqualTo("tenant-1");
        assertThat(c.getConnectorType()).isEqualTo(DmConnectorType.GOOGLE_ADS);
    }

    @Test
    @DisplayName("register rejects null ctx")
    void registerNullCtx() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> runPromise(() -> service.register(null, sampleRequest())));
    }

    @Test
    @DisplayName("register rejects null request")
    void registerNullRequest() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> runPromise(() -> service.register(ctx, null)));
    }

    @Test
    @DisplayName("register throws SecurityException when unauthorized")
    void registerUnauthorized() {
        service = new DmConnectorServiceImpl(repo, new AllowingKernelAdapter(false));
        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.register(ctx, sampleRequest())));
    }

    @Test
    @DisplayName("activate transitions PENDING to ACTIVE")
    void activateSuccess() {
        DmConnectorConfig c = runPromise(() -> service.register(ctx, sampleRequest()));
        DmConnectorConfig activated = runPromise(() -> service.activate(ctx, c.getId()));
        assertThat(activated.getStatus()).isEqualTo(DmConnectorStatus.ACTIVE);
        assertThat(activated.isOperational()).isTrue();
    }

    @Test
    @DisplayName("activate rejects blank id")
    void activateBlankId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> service.activate(ctx, "")));
    }

    @Test
    @DisplayName("activate throws NoSuchElementException for unknown id")
    void activateUnknownId() {
        assertThatExceptionOfType(java.util.NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.activate(ctx, "nonexistent")));
    }

    @Test
    @DisplayName("suspend transitions ACTIVE to SUSPENDED")
    void suspendSuccess() {
        DmConnectorConfig c = runPromise(() -> service.register(ctx, sampleRequest()));
        runPromise(() -> service.activate(ctx, c.getId()));
        DmConnectorConfig suspended = runPromise(() -> service.suspend(ctx, c.getId()));
        assertThat(suspended.getStatus()).isEqualTo(DmConnectorStatus.SUSPENDED);
    }

    @Test
    @DisplayName("reactivate transitions SUSPENDED to ACTIVE")
    void reactivateSuccess() {
        DmConnectorConfig c = runPromise(() -> service.register(ctx, sampleRequest()));
        runPromise(() -> service.activate(ctx, c.getId()));
        runPromise(() -> service.suspend(ctx, c.getId()));
        DmConnectorConfig reactivated = runPromise(() -> service.reactivate(ctx, c.getId()));
        assertThat(reactivated.getStatus()).isEqualTo(DmConnectorStatus.ACTIVE);
    }

    @Test
    @DisplayName("markAuthFailed sets AUTH_FAILED status")
    void markAuthFailed() {
        DmConnectorConfig c = runPromise(() -> service.register(ctx, sampleRequest()));
        runPromise(() -> service.activate(ctx, c.getId()));
        DmConnectorConfig failed = runPromise(() -> service.markAuthFailed(ctx, c.getId(), "expired"));
        assertThat(failed.getStatus()).isEqualTo(DmConnectorStatus.AUTH_FAILED);
        assertThat(failed.getFailureReason()).isEqualTo("expired");
    }

    @Test
    @DisplayName("disable transitions to DISABLED")
    void disable() {
        DmConnectorConfig c = runPromise(() -> service.register(ctx, sampleRequest()));
        DmConnectorConfig disabled = runPromise(() -> service.disable(ctx, c.getId()));
        assertThat(disabled.getStatus()).isEqualTo(DmConnectorStatus.DISABLED);
    }

    @Test
    @DisplayName("findById enforces tenant isolation")
    void findByIdCrossTenant() {
        DmConnectorConfig c = runPromise(() -> service.register(ctx, sampleRequest()));
        DmOperationContext other = DmOperationContext.builder()
            .tenantId(DmTenantId.of("other-tenant"))
            .workspaceId(DmWorkspaceId.of("ws-2"))
            .actor(ActorRef.user("user-2"))
            .correlationId(DmCorrelationId.of("corr-2"))
            .idempotencyKey(DmIdempotencyKey.of("idk-2"))
            .build();
        Optional<DmConnectorConfig> found = runPromise(() -> service.findById(other, c.getId()));
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findByType returns only matching type for tenant")
    void findByType() {
        runPromise(() -> service.register(ctx, sampleRequest()));
        runPromise(() -> service.register(ctx, new DmConnectorService.RegisterConnectorRequest(
            "Meta Ads", DmConnectorType.META_ADS, Map.of(), null)));
        List<DmConnectorConfig> result = runPromise(() -> service.findByType(ctx, DmConnectorType.GOOGLE_ADS, 10));
        assertThat(result).hasSize(1);
        assertThat(result).allMatch(c -> c.getConnectorType() == DmConnectorType.GOOGLE_ADS);
    }

    @Test
    @DisplayName("listActive returns only ACTIVE connectors")
    void listActive() {
        DmConnectorConfig c = runPromise(() -> service.register(ctx, sampleRequest()));
        runPromise(() -> service.activate(ctx, c.getId()));
        runPromise(() -> service.register(ctx, sampleRequest())); // stays PENDING
        List<DmConnectorConfig> active = runPromise(() -> service.listActive(ctx, 10));
        assertThat(active).hasSize(1);
    }

    @Test
    @DisplayName("countByStatus returns correct count")
    void countByStatus() {
        runPromise(() -> service.register(ctx, sampleRequest()));
        runPromise(() -> service.register(ctx, sampleRequest()));
        Long count = runPromise(() -> service.countByStatus(ctx, DmConnectorStatus.PENDING));
        assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("countByStatus rejects null status")
    void countByStatusNull() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> runPromise(() -> service.countByStatus(ctx, null)));
    }

    @Test
    @DisplayName("activate throws SecurityException when unauthorized")
    void activateUnauthorized() {
        DmConnectorConfig c = runPromise(() -> service.register(ctx, sampleRequest()));
        service = new DmConnectorServiceImpl(repo, new AllowingKernelAdapter(false));
        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.activate(ctx, c.getId())));
    }

    @Test
    @DisplayName("activate throws IllegalArgumentException for null id")
    void activateNullId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> service.activate(ctx, null)));
    }

    @Test
    @DisplayName("suspend throws SecurityException when unauthorized")
    void suspendUnauthorized() {
        DmConnectorConfig c = runPromise(() -> service.register(ctx, sampleRequest()));
        runPromise(() -> service.activate(ctx, c.getId()));
        service = new DmConnectorServiceImpl(repo, new AllowingKernelAdapter(false));
        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.suspend(ctx, c.getId())));
    }

    @Test
    @DisplayName("suspend throws IllegalArgumentException for null id")
    void suspendNullId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> service.suspend(ctx, null)));
    }

    @Test
    @DisplayName("reactivate throws SecurityException when unauthorized")
    void reactivateUnauthorized() {
        DmConnectorConfig c = runPromise(() -> service.register(ctx, sampleRequest()));
        runPromise(() -> service.activate(ctx, c.getId()));
        runPromise(() -> service.suspend(ctx, c.getId()));
        service = new DmConnectorServiceImpl(repo, new AllowingKernelAdapter(false));
        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.reactivate(ctx, c.getId())));
    }

    @Test
    @DisplayName("reactivate throws IllegalArgumentException for null id")
    void reactivateNullId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> service.reactivate(ctx, null)));
    }

    @Test
    @DisplayName("markAuthFailed throws SecurityException when unauthorized")
    void markAuthFailedUnauthorized() {
        DmConnectorConfig c = runPromise(() -> service.register(ctx, sampleRequest()));
        runPromise(() -> service.activate(ctx, c.getId()));
        service = new DmConnectorServiceImpl(repo, new AllowingKernelAdapter(false));
        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.markAuthFailed(ctx, c.getId(), "expired")));
    }

    @Test
    @DisplayName("markAuthFailed throws IllegalArgumentException for null id")
    void markAuthFailedNullId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> service.markAuthFailed(ctx, null, "reason")));
    }

    @Test
    @DisplayName("disable throws SecurityException when unauthorized")
    void disableUnauthorized() {
        DmConnectorConfig c = runPromise(() -> service.register(ctx, sampleRequest()));
        service = new DmConnectorServiceImpl(repo, new AllowingKernelAdapter(false));
        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.disable(ctx, c.getId())));
    }

    @Test
    @DisplayName("disable throws IllegalArgumentException for null id")
    void disableNullId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> service.disable(ctx, null)));
    }

    @Test
    @DisplayName("findById throws IllegalArgumentException for blank id")
    void findByIdBlankId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> service.findById(ctx, "")));
    }

    @Test
    @DisplayName("findById throws IllegalArgumentException for null id")
    void findByIdNullId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> service.findById(ctx, null)));
    }

    // ── Test Doubles ──────────────────────────────────────────────────────────

    private static final class InMemoryConnectorRepository implements DmConnectorRepository {
        private final ConcurrentHashMap<String, DmConnectorConfig> store = new ConcurrentHashMap<>();

        @Override
        public Promise<DmConnectorConfig> save(DmConnectorConfig c) {
            store.put(c.getId(), c);
            return Promise.of(c);
        }

        @Override
        public Promise<Optional<DmConnectorConfig>> findById(String id) {
            return Promise.of(Optional.ofNullable(store.get(id)));
        }

        @Override
        public Promise<List<DmConnectorConfig>> findByType(String tenantId, DmConnectorType type, int limit) {
            List<DmConnectorConfig> result = new ArrayList<>();
            for (DmConnectorConfig c : store.values()) {
                if (c.getTenantId().equals(tenantId) && c.getConnectorType() == type) {
                    result.add(c);
                    if (result.size() >= limit) break;
                }
            }
            return Promise.of(result);
        }

        @Override
        public Promise<List<DmConnectorConfig>> findByStatus(String tenantId, DmConnectorStatus status, int limit) {
            List<DmConnectorConfig> result = new ArrayList<>();
            for (DmConnectorConfig c : store.values()) {
                if (c.getTenantId().equals(tenantId) && c.getStatus() == status) {
                    result.add(c);
                    if (result.size() >= limit) break;
                }
            }
            return Promise.of(result);
        }

        @Override
        public Promise<DmConnectorConfig> update(DmConnectorConfig c) {
            store.put(c.getId(), c);
            return Promise.of(c);
        }

        @Override
        public Promise<Long> countByStatus(String tenantId, DmConnectorStatus status) {
            long count = store.values().stream()
                .filter(c -> c.getTenantId().equals(tenantId) && c.getStatus() == status)
                .count();
            return Promise.of(count);
        }
    }

    private static final class AllowingKernelAdapter implements DigitalMarketingKernelAdapter {
        private final boolean authorized;

        AllowingKernelAdapter(boolean authorized) { this.authorized = authorized; }

        @Override public void start() {}
        @Override public void stop() {}

        @Override
        public Promise<Boolean> isAuthorized(DmOperationContext context, String resource, String action) {
            return Promise.of(authorized);
        }

        @Override
        public Promise<Boolean> verifyConsent(DmOperationContext context, String subjectId, String purpose) {
            return Promise.of(true);
        }

        @Override
        public Promise<String> requestApproval(DmOperationContext context, String operationType,
                String subjectId, String description) {
            return Promise.of("approval-1");
        }

        @Override
        public Promise<String> recordAudit(DmOperationContext context, String entityId,
                String action, Map<String, Object> attributes) {
            return Promise.of("audit-1");
        }
    }
}

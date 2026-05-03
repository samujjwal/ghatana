package com.ghatana.digitalmarketing.application.analytics;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.analytics.DmAnalyticsEvent;
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

@DisplayName("DmAnalyticsEventServiceImpl")
class DmAnalyticsEventServiceImplTest extends EventloopTestBase {

    private InMemoryAnalyticsEventRepository repository;
    private DmAnalyticsEventServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAnalyticsEventRepository();
        service = new DmAnalyticsEventServiceImpl(repository, new StubKernelAdapter(true));
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    @Test
    @DisplayName("ingest stores analytics event")
    void ingestSuccess() {
        DmAnalyticsEvent event = runPromise(() -> service.ingest(ctx, cmd()));

        assertThat(event.getTenantId()).isEqualTo("tenant-1");
        assertThat(event.getEventType()).isEqualTo("page_view");
        assertThat(event.getSessionId()).isEqualTo("session-1");
    }

    @Test
    @DisplayName("ingest rejects unauthorized actor")
    void ingestUnauthorized() {
        service = new DmAnalyticsEventServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.ingest(ctx, cmd())));
    }

    @Test
    @DisplayName("findById enforces tenant isolation")
    void findByIdTenantIsolation() {
        DmAnalyticsEvent saved = runPromise(() -> service.ingest(ctx, cmd()));
        DmOperationContext other = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-2"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("u2"))
            .correlationId(DmCorrelationId.of("c2"))
            .idempotencyKey(DmIdempotencyKey.of("i2"))
            .build();

        Optional<DmAnalyticsEvent> result = runPromise(() -> service.findById(other, saved.getId()));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("listBySession returns session-scoped events")
    void listBySessionSuccess() {
        runPromise(() -> service.ingest(ctx, cmd()));
        List<DmAnalyticsEvent> results = runPromise(() -> service.listBySession(ctx, "session-1"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getSessionId()).isEqualTo("session-1");
    }

    @Test
    @DisplayName("listByTenant rejects unauthorized actor")
    void listByTenantUnauthorized() {
        service = new DmAnalyticsEventServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.listByTenant(ctx, 10)));
    }

    @Test
    @DisplayName("command validates blank sessionId")
    void commandValidation() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmAnalyticsEventService.IngestAnalyticsEventCommand(
                "", "page_view", null, null, null, null, null, null, null, Map.of()));
    }

    private DmAnalyticsEventService.IngestAnalyticsEventCommand cmd() {
        return new DmAnalyticsEventService.IngestAnalyticsEventCommand(
            "session-1", "page_view", "https://example.com",
            "google", "cpc", "summer-sale", null, null, "visitor-1", Map.of());
    }

    static final class InMemoryAnalyticsEventRepository implements DmAnalyticsEventRepository {
        private final Map<String, DmAnalyticsEvent> store = new ConcurrentHashMap<>();

        @Override public Promise<DmAnalyticsEvent> save(DmAnalyticsEvent e) { store.put(e.getId(), e); return Promise.of(e); }
        @Override public Promise<Optional<DmAnalyticsEvent>> findById(String id) {
            return Promise.of(Optional.ofNullable(store.get(id)));
        }
        @Override public Promise<List<DmAnalyticsEvent>> listByTenant(String tenantId, int limit) {
            return Promise.of(store.values().stream().filter(e -> e.getTenantId().equals(tenantId)).limit(limit).toList());
        }
        @Override public Promise<List<DmAnalyticsEvent>> listBySession(String tenantId, String sessionId) {
            return Promise.of(store.values().stream()
                .filter(e -> e.getTenantId().equals(tenantId) && e.getSessionId().equals(sessionId)).toList());
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

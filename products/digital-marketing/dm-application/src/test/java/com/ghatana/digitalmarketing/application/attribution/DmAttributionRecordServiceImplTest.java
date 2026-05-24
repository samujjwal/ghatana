package com.ghatana.digitalmarketing.application.attribution;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.attribution.DmAttributionModel;
import com.ghatana.digitalmarketing.domain.attribution.DmAttributionRecord;
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

@DisplayName("DmAttributionRecordServiceImpl")
class DmAttributionRecordServiceImplTest extends EventloopTestBase {

    private EphemeralAttributionRepository repository;
    private DmAttributionRecordServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        repository = new EphemeralAttributionRepository();
        service = new DmAttributionRecordServiceImpl(repository, new StubKernelAdapter(true));
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    @Test
    @DisplayName("record stores attribution record")
    void recordSuccess() {
        DmAttributionRecord rec = runPromise(() -> service.record(ctx, cmd()));

        assertThat(rec.getTenantId()).isEqualTo("tenant-1");
        assertThat(rec.getModel()).isEqualTo(DmAttributionModel.LAST_CLICK);
        assertThat(rec.getConversionEventId()).isEqualTo("conv-1");
    }

    @Test
    @DisplayName("record rejects unauthorized actor")
    void recordUnauthorized() {
        service = new DmAttributionRecordServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.record(ctx, cmd())));
    }

    @Test
    @DisplayName("findById enforces tenant isolation")
    void findByIdTenantIsolation() {
        DmAttributionRecord saved = runPromise(() -> service.record(ctx, cmd()));
        DmOperationContext other = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-2"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("u2"))
            .correlationId(DmCorrelationId.of("c2"))
            .idempotencyKey(DmIdempotencyKey.of("i2"))
            .build();

        Optional<DmAttributionRecord> result = runPromise(() -> service.findById(other, saved.getId()));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("listByVisitor returns visitor-scoped records")
    void listByVisitorSuccess() {
        runPromise(() -> service.record(ctx, cmd()));
        List<DmAttributionRecord> results = runPromise(() -> service.listByVisitor(ctx, "visitor-1"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getVisitorId()).isEqualTo("visitor-1");
    }

    @Test
    @DisplayName("findByConversionEvent returns matching record")
    void findByConversionEventSuccess() {
        runPromise(() -> service.record(ctx, cmd()));
        Optional<DmAttributionRecord> result = runPromise(() -> service.findByConversionEvent(ctx, "conv-1"));

        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("command rejects invalid attributionWeight")
    void commandValidatesWeight() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmAttributionRecordService.RecordAttributionCommand(
                "visitor-1", "session-1", "conv-1", "google", "cpc", null, null, null,
                DmAttributionModel.LAST_CLICK, 1.5));
    }

    private DmAttributionRecordService.RecordAttributionCommand cmd() {
        return new DmAttributionRecordService.RecordAttributionCommand(
            "visitor-1", "session-1", "conv-1", "google", "cpc", "summer-sale", null, null,
            DmAttributionModel.LAST_CLICK, 1.0);
    }

    static final class EphemeralAttributionRepository implements DmAttributionRecordRepository {
        private final Map<String, DmAttributionRecord> store = new ConcurrentHashMap<>();

        @Override public Promise<DmAttributionRecord> save(DmAttributionRecord r) { store.put(r.getId(), r); return Promise.of(r); }
        @Override public Promise<Optional<DmAttributionRecord>> findById(String id) {
            return Promise.of(Optional.ofNullable(store.get(id)));
        }
        @Override public Promise<List<DmAttributionRecord>> listByVisitor(String tenantId, String visitorId) {
            return Promise.of(store.values().stream()
                .filter(r -> r.getTenantId().equals(tenantId) && r.getVisitorId().equals(visitorId)).toList());
        }
        @Override public Promise<Optional<DmAttributionRecord>> findByConversionEvent(String tenantId, String conversionEventId) {
            return Promise.of(store.values().stream()
                .filter(r -> r.getTenantId().equals(tenantId) && r.getConversionEventId().equals(conversionEventId))
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

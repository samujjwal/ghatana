package com.ghatana.digitalmarketing.application.report;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.report.DmPerformanceReport;
import com.ghatana.digitalmarketing.domain.report.DmPerformanceReport.DmReportPeriod;
import com.ghatana.digitalmarketing.domain.report.DmReportStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("DmPerformanceReportServiceImpl")
class DmPerformanceReportServiceImplTest extends EventloopTestBase {

    private EphemeralReportRepository repository;
    private DmPerformanceReportServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        repository = new EphemeralReportRepository();
        service = new DmPerformanceReportServiceImpl(repository, new StubKernelAdapter(true));
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    @Test
    @DisplayName("generate creates report with PENDING status")
    void generateSuccess() {
        DmPerformanceReport report = runPromise(() -> service.generate(ctx, cmd()));

        assertThat(report.getTenantId()).isEqualTo("tenant-1");
        assertThat(report.getStatus()).isEqualTo(DmReportStatus.PENDING);
    }

    @Test
    @DisplayName("generate rejects unauthorized actor")
    void generateUnauthorized() {
        service = new DmPerformanceReportServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.generate(ctx, cmd())));
    }

    @Test
    @DisplayName("markReady transitions to READY")
    void markReadySuccess() {
        DmPerformanceReport created = runPromise(() -> service.generate(ctx, cmd()));
        DmPerformanceReport ready = runPromise(() -> service.markReady(ctx, created.getId()));

        assertThat(ready.getStatus()).isEqualTo(DmReportStatus.READY);
    }

    @Test
    @DisplayName("markReady rejects unauthorized actor")
    void markReadyUnauthorized() {
        DmPerformanceReport created = runPromise(() -> service.generate(ctx, cmd()));
        service = new DmPerformanceReportServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.markReady(ctx, created.getId())));
    }

    @Test
    @DisplayName("markReady throws when report is already READY")
    void markReadyWhenAlreadyReady() {
        DmPerformanceReport created = runPromise(() -> service.generate(ctx, cmd()));
        runPromise(() -> service.markReady(ctx, created.getId()));

        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> runPromise(() -> service.markReady(ctx, created.getId())));
    }

    @Test
    @DisplayName("markFailed transitions to FAILED")
    void markFailedSuccess() {
        DmPerformanceReport created = runPromise(() -> service.generate(ctx, cmd()));
        DmPerformanceReport failed = runPromise(() -> service.markFailed(ctx, created.getId()));

        assertThat(failed.getStatus()).isEqualTo(DmReportStatus.FAILED);
    }

    @Test
    @DisplayName("markFailed rejects unauthorized actor")
    void markFailedUnauthorized() {
        DmPerformanceReport created = runPromise(() -> service.generate(ctx, cmd()));
        service = new DmPerformanceReportServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.markFailed(ctx, created.getId())));
    }

    @Test
    @DisplayName("markReady throws for unknown report id")
    void markReadyUnknownId() {
        assertThatExceptionOfType(java.util.NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.markReady(ctx, "nonexistent-id")));
    }

    @Test
    @DisplayName("findById enforces tenant isolation")
    void findByIdTenantIsolation() {
        DmPerformanceReport created = runPromise(() -> service.generate(ctx, cmd()));
        DmOperationContext other = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-2"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("u2"))
            .correlationId(DmCorrelationId.of("c2"))
            .idempotencyKey(DmIdempotencyKey.of("i2"))
            .build();

        Optional<DmPerformanceReport> result = runPromise(() -> service.findById(other, created.getId()));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("listByStatus returns matching reports")
    void listByStatusSuccess() {
        runPromise(() -> service.generate(ctx, cmd()));
        List<DmPerformanceReport> results = runPromise(() -> service.listByStatus(ctx, DmReportStatus.PENDING));

        assertThat(results).hasSize(1);
    }

    @Test
    @DisplayName("generate command validates blank title")
    void commandValidation() {
        Instant now = Instant.now();
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmPerformanceReportService.GenerateReportCommand("", new DmReportPeriod(now, now)));
    }

    private DmPerformanceReportService.GenerateReportCommand cmd() {
        Instant now = Instant.now();
        return new DmPerformanceReportService.GenerateReportCommand(
            "Q1 Report", new DmReportPeriod(now.minusSeconds(86400), now));
    }

    static final class EphemeralReportRepository implements DmPerformanceReportRepository {
        private final Map<String, DmPerformanceReport> store = new ConcurrentHashMap<>();

        @Override public Promise<DmPerformanceReport> save(DmPerformanceReport r) { store.put(r.getId(), r); return Promise.of(r); }
        @Override public Promise<DmPerformanceReport> update(DmPerformanceReport r) { store.put(r.getId(), r); return Promise.of(r); }
        @Override public Promise<Optional<DmPerformanceReport>> findById(String id) {
            return Promise.of(Optional.ofNullable(store.get(id)));
        }
        @Override public Promise<List<DmPerformanceReport>> listByTenant(String tenantId) {
            return Promise.of(store.values().stream().filter(r -> r.getTenantId().equals(tenantId)).toList());
        }
        @Override public Promise<List<DmPerformanceReport>> listByStatus(String tenantId, DmReportStatus status) {
            return Promise.of(store.values().stream()
                .filter(r -> r.getTenantId().equals(tenantId) && r.getStatus() == status)
                .toList());
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

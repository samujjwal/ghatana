package com.ghatana.digitalmarketing.application.email;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.email.DmEmailFollowUp;
import com.ghatana.digitalmarketing.domain.email.DmEmailFollowUpStatus;
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

@DisplayName("DmEmailFollowUpServiceImpl")
class DmEmailFollowUpServiceImplTest extends EventloopTestBase {

    private InMemoryRepository repository;
    private DmEmailFollowUpServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRepository();
        service = new DmEmailFollowUpServiceImpl(repository, new StubKernelAdapter(true));
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    @Test
    @DisplayName("schedule stores PENDING follow-up")
    void scheduleSuccess() {
        DmEmailFollowUp followUp = runPromise(() -> service.schedule(ctx, cmd()));

        assertThat(followUp.getStatus()).isEqualTo(DmEmailFollowUpStatus.PENDING);
        assertThat(followUp.getTenantId()).isEqualTo("tenant-1");
        assertThat(followUp.getConnectorId()).isEqualTo("connector-1");
    }

    @Test
    @DisplayName("schedule rejects unauthorized actor")
    void scheduleUnauthorized() {
        service = new DmEmailFollowUpServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.schedule(ctx, cmd())));
    }

    @Test
    @DisplayName("markSent transitions PENDING to SENT")
    void markSentSuccess() {
        DmEmailFollowUp scheduled = runPromise(() -> service.schedule(ctx, cmd()));
        DmEmailFollowUp sent = runPromise(() -> service.markSent(ctx, scheduled.getId(), 10, 1));

        assertThat(sent.getStatus()).isEqualTo(DmEmailFollowUpStatus.SENT);
        assertThat(sent.getSentCount()).isEqualTo(10);
        assertThat(sent.getFailedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("markSent rejects if already SENT")
    void markSentRejectsDuplicate() {
        DmEmailFollowUp scheduled = runPromise(() -> service.schedule(ctx, cmd()));
        runPromise(() -> service.markSent(ctx, scheduled.getId(), 5, 0));

        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> runPromise(() -> service.markSent(ctx, scheduled.getId(), 5, 0)));
    }

    @Test
    @DisplayName("markFailed sets FAILED status with reason")
    void markFailedSuccess() {
        DmEmailFollowUp scheduled = runPromise(() -> service.schedule(ctx, cmd()));
        DmEmailFollowUp failed = runPromise(() -> service.markFailed(ctx, scheduled.getId(), "SMTP timeout"));

        assertThat(failed.getStatus()).isEqualTo(DmEmailFollowUpStatus.FAILED);
        assertThat(failed.getFailureReason()).isEqualTo("SMTP timeout");
    }

    @Test
    @DisplayName("cancel transitions PENDING to CANCELLED")
    void cancelSuccess() {
        DmEmailFollowUp scheduled = runPromise(() -> service.schedule(ctx, cmd()));
        DmEmailFollowUp cancelled = runPromise(() -> service.cancel(ctx, scheduled.getId()));

        assertThat(cancelled.getStatus()).isEqualTo(DmEmailFollowUpStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancel rejects SENT follow-up")
    void cancelRejectsSent() {
        DmEmailFollowUp scheduled = runPromise(() -> service.schedule(ctx, cmd()));
        runPromise(() -> service.markSent(ctx, scheduled.getId(), 1, 0));

        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> runPromise(() -> service.cancel(ctx, scheduled.getId())));
    }

    @Test
    @DisplayName("findById enforces tenant isolation")
    void findByIdTenantIsolation() {
        DmEmailFollowUp scheduled = runPromise(() -> service.schedule(ctx, cmd()));
        DmOperationContext other = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-2"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-2"))
            .correlationId(DmCorrelationId.of("corr-2"))
            .idempotencyKey(DmIdempotencyKey.of("idk-2"))
            .build();

        Optional<DmEmailFollowUp> result = runPromise(() -> service.findById(other, scheduled.getId()));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("listByStatus returns tenant-scoped follow-ups")
    void listByStatusSuccess() {
        runPromise(() -> service.schedule(ctx, cmd()));
        List<DmEmailFollowUp> results = runPromise(() -> service.listByStatus(ctx, DmEmailFollowUpStatus.PENDING, 10));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTenantId()).isEqualTo("tenant-1");
    }

    @Test
    @DisplayName("listByStatus rejects unauthorized actor")
    void listByStatusUnauthorized() {
        service = new DmEmailFollowUpServiceImpl(repository, new StubKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.listByStatus(ctx, DmEmailFollowUpStatus.PENDING, 10)));
    }

    @Test
    @DisplayName("schedule command validates blank connectorId")
    void commandValidationConnectorId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmEmailFollowUpService.ScheduleEmailFollowUpCommand(
                "", List.of("a@b.com"), "Subject", "<p>body</p>"));
    }

    @Test
    @DisplayName("schedule command validates empty recipients")
    void commandValidationRecipients() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmEmailFollowUpService.ScheduleEmailFollowUpCommand(
                "c-1", List.of(), "Subject", "<p>body</p>"));
    }

    private DmEmailFollowUpService.ScheduleEmailFollowUpCommand cmd() {
        return new DmEmailFollowUpService.ScheduleEmailFollowUpCommand(
            "connector-1",
            List.of("a@example.com", "b@example.com"),
            "Follow-up subject",
            "<p>Hello!</p>"
        );
    }

    static final class InMemoryRepository implements DmEmailFollowUpRepository {
        private final Map<String, DmEmailFollowUp> store = new ConcurrentHashMap<>();

        @Override
        public Promise<DmEmailFollowUp> save(DmEmailFollowUp e) {
            store.put(e.getId(), e);
            return Promise.of(e);
        }

        @Override
        public Promise<DmEmailFollowUp> update(DmEmailFollowUp e) {
            store.put(e.getId(), e);
            return Promise.of(e);
        }

        @Override
        public Promise<Optional<DmEmailFollowUp>> findById(String id) {
            return Promise.of(Optional.ofNullable(store.get(id)));
        }

        @Override
        public Promise<List<DmEmailFollowUp>> listByStatus(String tenantId, DmEmailFollowUpStatus status, int limit) {
            return Promise.of(store.values().stream()
                .filter(e -> e.getTenantId().equals(tenantId) && e.getStatus() == status)
                .limit(limit).toList());
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
        @Override public Promise<String> recordAudit(DmOperationContext ctx, String entityId, String eventType, Map<String, Object> meta) {
            return Promise.of("audit-1");
        }
    }
}

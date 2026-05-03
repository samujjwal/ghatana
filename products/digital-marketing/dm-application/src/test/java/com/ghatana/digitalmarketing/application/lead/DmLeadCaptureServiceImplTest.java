package com.ghatana.digitalmarketing.application.lead;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.lead.DmLeadCapture;
import com.ghatana.digitalmarketing.domain.lead.DmLeadStatus;
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

@DisplayName("DmLeadCaptureServiceImpl")
class DmLeadCaptureServiceImplTest extends EventloopTestBase {

    private InMemoryLeadCaptureRepository repository;
    private DmLeadCaptureServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        repository = new InMemoryLeadCaptureRepository();
        service = new DmLeadCaptureServiceImpl(repository, new AllowingKernelAdapter(true));

        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    @Test
    @DisplayName("capture stores NEW lead capture")
    void captureSuccess() {
        DmLeadCapture lead = runPromise(() -> service.capture(
            ctx,
            new DmLeadCaptureService.CaptureLeadFormCommand(
                "lp-1",
                "lead@example.com",
                "Alice",
                "+12025550123",
                Map.of("businessType", "clinic"),
                "google",
                "cpc",
                "spring-2026"
            )));

        assertThat(lead.getStatus()).isEqualTo(DmLeadStatus.NEW);
        assertThat(lead.getLandingPageId()).isEqualTo("lp-1");
        assertThat(lead.getTenantId()).isEqualTo("tenant-1");
    }

    @Test
    @DisplayName("capture rejects duplicate email for landing page")
    void captureRejectsDuplicate() {
        runPromise(() -> service.capture(
            ctx,
            new DmLeadCaptureService.CaptureLeadFormCommand(
                "lp-1",
                "lead@example.com",
                "Alice",
                null,
                Map.of(),
                null,
                null,
                null
            )));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> service.capture(
                ctx,
                new DmLeadCaptureService.CaptureLeadFormCommand(
                    "lp-1",
                    "lead@example.com",
                    "Alice",
                    null,
                    Map.of(),
                    null,
                    null,
                    null
                ))));
    }

    @Test
    @DisplayName("capture rejects unauthorized actor")
    void captureUnauthorized() {
        service = new DmLeadCaptureServiceImpl(repository, new AllowingKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.capture(
                ctx,
                new DmLeadCaptureService.CaptureLeadFormCommand(
                    "lp-1",
                    "lead@example.com",
                    "Alice",
                    null,
                    Map.of(),
                    null,
                    null,
                    null
                ))));
    }

    @Test
    @DisplayName("qualify transitions NEW to QUALIFIED")
    void qualifySuccess() {
        DmLeadCapture created = createLead();
        DmLeadCapture qualified = runPromise(() -> service.qualify(ctx, created.getId()));

        assertThat(qualified.getStatus()).isEqualTo(DmLeadStatus.QUALIFIED);
    }

    @Test
    @DisplayName("markContacted requires QUALIFIED")
    void markContactedRequiresQualified() {
        DmLeadCapture created = createLead();

        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> runPromise(() -> service.markContacted(ctx, created.getId())));
    }

    @Test
    @DisplayName("convert allows CONTACTED status")
    void convertFromContacted() {
        DmLeadCapture created = createLead();
        DmLeadCapture qualified = runPromise(() -> service.qualify(ctx, created.getId()));
        DmLeadCapture contacted = runPromise(() -> service.markContacted(ctx, qualified.getId()));
        DmLeadCapture converted = runPromise(() -> service.convert(ctx, contacted.getId()));

        assertThat(converted.getStatus()).isEqualTo(DmLeadStatus.CONVERTED);
    }

    @Test
    @DisplayName("disqualify rejects CONVERTED lead")
    void disqualifyRejectsConverted() {
        DmLeadCapture created = createLead();
        DmLeadCapture qualified = runPromise(() -> service.qualify(ctx, created.getId()));
        DmLeadCapture converted = runPromise(() -> service.convert(ctx, qualified.getId()));

        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> runPromise(() -> service.disqualify(ctx, converted.getId())));
    }

    @Test
    @DisplayName("findById enforces tenant isolation")
    void findByIdTenantIsolation() {
        DmLeadCapture created = createLead();

        DmOperationContext otherTenant = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-2"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-2"))
            .correlationId(DmCorrelationId.of("corr-2"))
            .idempotencyKey(DmIdempotencyKey.of("idk-2"))
            .build();

        Optional<DmLeadCapture> visible = runPromise(() -> service.findById(otherTenant, created.getId()));
        assertThat(visible).isEmpty();
    }

    @Test
    @DisplayName("qualify rejects unauthorized actor")
    void qualifyUnauthorized() {
        DmLeadCapture created = createLead();
        DmLeadCaptureServiceImpl deniedService = new DmLeadCaptureServiceImpl(repository, new AllowingKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> deniedService.qualify(ctx, created.getId())));
    }

    @Test
    @DisplayName("disqualify transitions QUALIFIED to DISQUALIFIED")
    void disqualifySuccess() {
        DmLeadCapture created = createLead();
        DmLeadCapture qualified = runPromise(() -> service.qualify(ctx, created.getId()));
        DmLeadCapture disqualified = runPromise(() -> service.disqualify(ctx, qualified.getId()));

        assertThat(disqualified.getStatus()).isEqualTo(DmLeadStatus.DISQUALIFIED);
    }

    @Test
    @DisplayName("findById rejects blank id")
    void findByIdBlankId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> service.findById(ctx, "")));
    }

    @Test
    @DisplayName("listByStatus rejects limit <= 0")
    void listByStatusZeroLimit() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> service.listByStatus(ctx, DmLeadStatus.NEW, 0)));
    }

    @Test
    @DisplayName("listByStatus rejects unauthorized actor")
    void listByStatusUnauthorized() {
        service = new DmLeadCaptureServiceImpl(repository, new AllowingKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.listByStatus(ctx, DmLeadStatus.NEW, 10)));
    }

    @Test
    @DisplayName("listByStatus returns tenant scoped leads")
    void listByStatusSuccess() {
        createLead();
        List<DmLeadCapture> leads = runPromise(() -> service.listByStatus(ctx, DmLeadStatus.NEW, 10));

        assertThat(leads).hasSize(1);
        assertThat(leads.get(0).getTenantId()).isEqualTo("tenant-1");
    }

    private DmLeadCapture createLead() {
        return runPromise(() -> service.capture(
            ctx,
            new DmLeadCaptureService.CaptureLeadFormCommand(
                "lp-1",
                "lead@example.com",
                "Alice",
                null,
                Map.of(),
                "google",
                "cpc",
                "spring-2026"
            )));
    }

    static final class InMemoryLeadCaptureRepository implements DmLeadCaptureRepository {
        private final Map<String, DmLeadCapture> byId = new ConcurrentHashMap<>();

        @Override
        public Promise<DmLeadCapture> save(DmLeadCapture leadCapture) {
            byId.put(leadCapture.getId(), leadCapture);
            return Promise.of(leadCapture);
        }

        @Override
        public Promise<DmLeadCapture> update(DmLeadCapture leadCapture) {
            byId.put(leadCapture.getId(), leadCapture);
            return Promise.of(leadCapture);
        }

        @Override
        public Promise<Optional<DmLeadCapture>> findById(String leadCaptureId) {
            return Promise.of(Optional.ofNullable(byId.get(leadCaptureId)));
        }

        @Override
        public Promise<Optional<DmLeadCapture>> findByEmailAndLandingPage(String tenantId, String landingPageId, String email) {
            return Promise.of(byId.values().stream()
                .filter(v -> v.getTenantId().equals(tenantId)
                    && v.getLandingPageId().equals(landingPageId)
                    && v.getEmail().equals(email))
                .findFirst());
        }

        @Override
        public Promise<List<DmLeadCapture>> listByStatus(String tenantId, DmLeadStatus status, int limit) {
            return Promise.of(byId.values().stream()
                .filter(v -> v.getTenantId().equals(tenantId) && v.getStatus() == status)
                .limit(limit)
                .toList());
        }
    }

    static final class AllowingKernelAdapter implements DigitalMarketingKernelAdapter {
        private final boolean allowed;

        AllowingKernelAdapter(boolean allowed) {
            this.allowed = allowed;
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public Promise<Boolean> isAuthorized(DmOperationContext context, String resource, String action) {
            return Promise.of(allowed);
        }

        @Override
        public Promise<Boolean> verifyConsent(DmOperationContext context, String subjectId, String purpose) {
            return Promise.of(true);
        }

        @Override
        public Promise<String> requestApproval(
                DmOperationContext context,
                String operationType,
                String subjectId,
                String description) {
            return Promise.of("approval-1");
        }

        @Override
        public Promise<String> recordAudit(
                DmOperationContext context,
                String entityId,
                String action,
                Map<String, Object> attributes) {
            return Promise.of("audit-1");
        }
    }
}

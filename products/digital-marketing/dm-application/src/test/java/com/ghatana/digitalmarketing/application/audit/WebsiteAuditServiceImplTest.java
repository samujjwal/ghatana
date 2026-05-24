package com.ghatana.digitalmarketing.application.audit;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.audit.WebsiteAuditReport;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("WebsiteAuditServiceImpl")
class WebsiteAuditServiceImplTest extends EventloopTestBase {

    private RecordingKernelAdapter kernelAdapter;
    private EphemeralRepository repository;
    private WebsiteAuditServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        kernelAdapter = new RecordingKernelAdapter();
        repository = new EphemeralRepository();
        service = new WebsiteAuditServiceImpl(kernelAdapter, repository);

        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("owner-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    @Test
    @DisplayName("generates baseline audit findings and records audit")
    void shouldGenerateAuditFindings() {
        WebsiteAuditReport report = runPromise(() -> service.runAudit(
            ctx,
            new WebsiteAuditService.RunWebsiteAuditCommand(
                "https://acme.test",
                true,
                3600,
                "",
                "",
                "",
                false,
                false
            )
        ));

        assertThat(report.getFindings()).hasSizeGreaterThanOrEqualTo(4);
        assertThat(kernelAdapter.auditActions).contains("website-audit-generated");
    }

    @Test
    @DisplayName("returns critical finding for unreachable website")
    void shouldHandleUnreachableWebsite() {
        WebsiteAuditReport report = runPromise(() -> service.runAudit(
            ctx,
            new WebsiteAuditService.RunWebsiteAuditCommand(
                "https://offline.test",
                false,
                0,
                "",
                "",
                "",
                false,
                false
            )
        ));

        assertThat(report.getFindings()).hasSize(1);
        assertThat(report.getFindings().get(0).category()).isEqualTo("AVAILABILITY");
    }

    @Test
    @DisplayName("reads latest audit and handles missing case")
    void shouldGetLatestAudit() {
        runPromise(() -> service.runAudit(
            ctx,
            new WebsiteAuditService.RunWebsiteAuditCommand(
                "https://acme.test",
                true,
                500,
                "Acme",
                "desc",
                "Grow",
                true,
                true
            )
        ));

        WebsiteAuditReport latest = runPromise(() -> service.getLatestAudit(ctx));
        assertThat(latest.getWebsiteUrl()).isEqualTo("https://acme.test");

        DmOperationContext other = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-2"))
            .actor(ActorRef.user("owner-1"))
            .correlationId(DmCorrelationId.of("corr-2"))
            .idempotencyKey(DmIdempotencyKey.of("idk-2"))
            .build();
        assertThatExceptionOfType(java.util.NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.getLatestAudit(other)));
    }

    @Test
    @DisplayName("rejects unauthorized reads and writes")
    void shouldRejectUnauthorizedOperations() {
        kernelAdapter.authorized = false;

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.runAudit(
                ctx,
                new WebsiteAuditService.RunWebsiteAuditCommand(
                    "https://acme.test",
                    true,
                    500,
                    "Acme",
                    "desc",
                    "Grow",
                    true,
                    true
                )
            )));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.getLatestAudit(ctx)));
    }

    @Test
    @DisplayName("constructor rejects null dependencies")
    void shouldRejectNullDependencies() {
        assertThatThrownBy(() -> new WebsiteAuditServiceImpl(null, repository))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new WebsiteAuditServiceImpl(kernelAdapter, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("command rejects blank url and negative response time")
    void shouldRejectInvalidCommandInputs() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new WebsiteAuditService.RunWebsiteAuditCommand(
                " ",
                true,
                10,
                "title",
                "desc",
                "h1",
                true,
                true
            ));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new WebsiteAuditService.RunWebsiteAuditCommand(
                "https://acme.test",
                true,
                -1,
                "title",
                "desc",
                "h1",
                true,
                true
            ));
    }

    @Test
    @DisplayName("run and read operations reject null context and command")
    void shouldRejectNullInputs() {
        WebsiteAuditService.RunWebsiteAuditCommand command = new WebsiteAuditService.RunWebsiteAuditCommand(
            "https://acme.test",
            true,
            100,
            "title",
            "desc",
            "h1",
            true,
            true
        );

        assertThatThrownBy(() -> runPromise(() -> service.runAudit(null, command)))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("ctx");

        assertThatThrownBy(() -> runPromise(() -> service.runAudit(ctx, null)))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("command");

        assertThatThrownBy(() -> runPromise(() -> service.getLatestAudit(null)))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("ctx");
    }

    private static final class EphemeralRepository implements WebsiteAuditReportRepository {
        private final ConcurrentHashMap<String, WebsiteAuditReport> store = new ConcurrentHashMap<>();

        @Override
        public Promise<WebsiteAuditReport> save(WebsiteAuditReport report) {
            store.put(report.getWorkspaceId().getValue(), report);
            return Promise.of(report);
        }

        @Override
        public Promise<Optional<WebsiteAuditReport>> findLatestByWorkspace(DmWorkspaceId workspaceId) {
            return Promise.of(Optional.ofNullable(store.get(workspaceId.getValue())));
        }
    }

    private static final class RecordingKernelAdapter implements DigitalMarketingKernelAdapter {
        private boolean authorized = true;
        private final java.util.List<String> auditActions = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public Promise<Boolean> isAuthorized(DmOperationContext context, String resource, String action) {
            return Promise.of(authorized);
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
            auditActions.add(action);
            return Promise.of("audit-1");
        }
    }
}

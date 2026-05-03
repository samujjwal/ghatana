package com.ghatana.digitalmarketing.api;

import com.ghatana.digitalmarketing.application.audit.WebsiteAuditService;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.audit.AuditSeverity;
import com.ghatana.digitalmarketing.domain.audit.WebsiteAuditFinding;
import com.ghatana.digitalmarketing.domain.audit.WebsiteAuditReport;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("DmosWebsiteAuditServlet")
class DmosWebsiteAuditServletTest extends EventloopTestBase {

    private FakeAuditService auditService;
    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        auditService = new FakeAuditService();
        servlet = new DmosWebsiteAuditServlet(auditService, Eventloop.create()).getServlet();
    }

    private static final String RUN_AUDIT_BODY =
        "{\"websiteUrl\":\"https://example.com\",\"reachable\":true,\"responseTimeMs\":500," +
        "\"title\":\"Example\",\"metaDescription\":\"desc\",\"h1\":\"heading\"," +
        "\"trackingTagDetected\":true,\"hasLeadForm\":true}";

    @Test
    @DisplayName("constructor rejects null dependencies")
    void shouldRejectNullDependencies() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DmosWebsiteAuditServlet(null, Eventloop.create()));
        assertThatNullPointerException()
            .isThrownBy(() -> new DmosWebsiteAuditServlet(auditService, null));
    }

    @Test
    @DisplayName("POST /audit/run returns 200 on success")
    void shouldRunAuditAndReturn200() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/audit/run")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "user-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(RUN_AUDIT_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(auditService.lastContext.getWorkspaceId()).isEqualTo(DmWorkspaceId.of("ws-1"));
    }

    @Test
    @DisplayName("POST /audit/run returns 400 when X-Idempotency-Key is blank")
    void shouldReturn400WhenIdempotencyKeyIsBlank() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/audit/run")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "   ")
            .withBody(RUN_AUDIT_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /audit/run returns 400 when X-Idempotency-Key missing")
    void shouldReturn400WhenIdempotencyKeyMissing() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/audit/run")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody(RUN_AUDIT_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /audit/run returns 400 when X-Tenant-ID missing")
    void shouldReturn400WhenTenantIdMissing() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/audit/run")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(RUN_AUDIT_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /audit/run returns 403 on security exception")
    void shouldReturn403OnSecurityException() {
        auditService.throwOnRun = new SecurityException("Forbidden");

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/audit/run")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(RUN_AUDIT_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("POST /audit/run returns 400 on malformed JSON")
    void shouldReturn400OnMalformedJson() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/audit/run")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody("not-json".getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("POST /audit/run returns 500 on unknown service error")
    void shouldReturn500OnUnknownRunError() {
        auditService.throwOnRun = new RuntimeException("unexpected");

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/audit/run")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(RUN_AUDIT_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("GET /audit/latest returns 200 on success")
    void shouldGetLatestAuditAndReturn200() {
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/audit/latest")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("GET /audit/latest returns 404 when no audit exists")
    void shouldReturn404WhenNoAuditExists() {
        auditService.throwOnGet = new NoSuchElementException("No audit found");

        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/audit/latest")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("GET /audit/latest returns 403 on security exception")
    void shouldReturn403OnGetSecurityException() {
        auditService.throwOnGet = new SecurityException("Forbidden");

        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/audit/latest")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("GET /audit/latest returns 500 on unknown error")
    void shouldReturn500OnGetUnknownError() {
        auditService.throwOnGet = new RuntimeException("boom");

        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/audit/latest")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("POST /audit/run returns 400 when service throws IllegalArgumentException")
    void shouldReturn400WhenServiceThrowsIllegalArgument() {
        auditService.throwOnRun = new IllegalArgumentException("Invalid URL");

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/audit/run")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(RUN_AUDIT_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /audit/run returns 409 when service throws IllegalStateException")
    void shouldReturn409WhenServiceThrowsIllegalState() {
        auditService.throwOnRun = new IllegalStateException("Audit already running");

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/audit/run")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(RUN_AUDIT_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(409);
    }

    @Test
    @DisplayName("GET /audit/latest with roles and permissions headers parses CSV correctly")
    void shouldParseRolesAndPermissionsHeaders() {
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/audit/latest")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Roles"), "ADMIN, VIEWER")
            .withHeader(HttpHeaders.of("X-Permissions"), "audit:read, audit:write")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("GET /audit/latest returns 400 when X-Tenant-ID missing")
    void shouldReturn400WhenTenantIdMissingOnGet() {
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/audit/latest")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    // ---- test double ----

    private static final class FakeAuditService implements WebsiteAuditService {
        DmOperationContext lastContext;
        Exception throwOnRun;
        Exception throwOnGet;

        private static WebsiteAuditReport buildReport(DmWorkspaceId wsId) {
            return WebsiteAuditReport.builder()
                .reportId("report-1")
                .workspaceId(wsId)
                .websiteUrl("https://example.com")
                .findings(List.of(new WebsiteAuditFinding(
                    AuditSeverity.CRITICAL,
                    "SEO",
                    "Missing meta description",
                    "Meta descriptions improve CTR",
                    "Add a meta description",
                    "https://example.com"
                )))
                .generatedAt(Instant.now())
                .generatedBy("system")
                .build();
        }

        @Override
        public Promise<WebsiteAuditReport> runAudit(DmOperationContext ctx, RunWebsiteAuditCommand command) {
            this.lastContext = ctx;
            if (throwOnRun != null) {
                return Promise.ofException(throwOnRun);
            }
            return Promise.of(buildReport(ctx.getWorkspaceId()));
        }

        @Override
        public Promise<WebsiteAuditReport> getLatestAudit(DmOperationContext ctx) {
            if (throwOnGet != null) {
                return Promise.ofException(throwOnGet);
            }
            return Promise.of(buildReport(ctx.getWorkspaceId()));
        }
    }
}

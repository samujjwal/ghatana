package com.ghatana.digitalmarketing.api;

import com.ghatana.digitalmarketing.application.sow.SowService;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.sow.SowClause;
import com.ghatana.digitalmarketing.domain.sow.SowClauseStatus;
import com.ghatana.digitalmarketing.domain.sow.SowDraft;
import com.ghatana.digitalmarketing.domain.sow.SowStatus;
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
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("DmosSowServlet")
class DmosSowServletTest extends EventloopTestBase {

    private FakeSowService sowService;
    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        sowService = new FakeSowService();
        servlet = new DmosSowServlet(sowService, Eventloop.create()).getServlet();
    }

    private static final String GENERATE_BODY =
        "{\"proposalId\":\"proposal-1\",\"templateVersion\":\"v1.0\","
        + "\"assumptions\":\"standard\",\"exclusions\":\"none\"}";

    // ---- constructor validation ----

    @Test
    @DisplayName("constructor rejects null dependencies")
    void shouldRejectNullDependencies() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DmosSowServlet(null, Eventloop.create()));
        assertThatNullPointerException()
            .isThrownBy(() -> new DmosSowServlet(sowService, null));
    }

    // ---- POST /sow (generate draft) ----

    @Test
    @DisplayName("POST /sow generates SOW draft and returns 201")
    void shouldGenerateDraft() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/sow")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "owner-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(201);
        assertThat(sowService.lastContext.getWorkspaceId()).isEqualTo(DmWorkspaceId.of("ws-1"));
    }

    @Test
    @DisplayName("POST /sow returns 400 when X-Idempotency-Key is missing")
    void shouldRejectGenerateWithoutIdempotencyKey() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/sow")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /sow returns 400 when X-Idempotency-Key is blank")
    void shouldRejectGenerateWithBlankIdempotencyKey() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/sow")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "   ")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /sow returns 400 when X-Tenant-ID is missing")
    void shouldRejectGenerateWithoutTenant() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/sow")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /sow returns 403 on SecurityException")
    void shouldReturn403OnGenerateSecurityException() {
        sowService.throwOnGenerate = new SecurityException("not authorized");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/sow")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("POST /sow returns 400 on IllegalArgumentException from service")
    void shouldReturn400OnGenerateIllegalArgument() {
        sowService.throwOnGenerate = new IllegalArgumentException("invalid proposalId");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/sow")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /sow returns 500 on unknown RuntimeException")
    void shouldReturn500OnGenerateUnknownException() {
        sowService.throwOnGenerate = new RuntimeException("boom");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/sow")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("POST /sow passes X-Roles and X-Permissions headers")
    void shouldPassRolesAndPermissions() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/sow")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "owner-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withHeader(HttpHeaders.of("X-Roles"), "admin,editor")
            .withHeader(HttpHeaders.of("X-Permissions"), "sow:write")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(201);
    }

    @Test
    @DisplayName("POST /sow passes X-Correlation-ID and X-Session-ID headers")
    void shouldPassCorrelationAndSessionHeaders() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/sow")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "corr-1")
            .withHeader(HttpHeaders.of("X-Session-ID"), "sess-1")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(201);
    }

    @Test
    @DisplayName("POST /sow passes X-Roles with blank tokens (filter branch)")
    void shouldHandleRolesWithBlankTokens() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/sow")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withHeader(HttpHeaders.of("X-Roles"), "admin,,editor")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(201);
    }

    // ---- POST /sow/:sowId/submit ----

    @Test
    @DisplayName("POST /sow/:sowId/submit returns 200 on success")
    void shouldSubmitForReview() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/sow/sow-1/submit")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-2")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("POST /sow/:sowId/submit returns 400 when X-Idempotency-Key missing")
    void shouldRejectSubmitWithoutIdempotencyKey() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/sow/sow-1/submit")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /sow/:sowId/submit returns 400 when X-Tenant-ID missing")
    void shouldRejectSubmitWithoutTenant() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/sow/sow-1/submit")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-2")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /sow/:sowId/submit returns 404 on NoSuchElement")
    void shouldReturn404OnSubmitNotFound() {
        sowService.throwOnSubmit = new NoSuchElementException("sow not found");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/sow/sow-1/submit")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-2")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("POST /sow/:sowId/submit returns 409 on IllegalStateException")
    void shouldReturn409OnSubmitIllegalState() {
        sowService.throwOnSubmit = new IllegalStateException("already submitted");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/sow/sow-1/submit")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-2")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(409);
    }

    @Test
    @DisplayName("POST /sow/:sowId/submit returns 403 on SecurityException")
    void shouldReturn403OnSubmitSecurityException() {
        sowService.throwOnSubmit = new SecurityException("not authorized");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/sow/sow-1/submit")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-2")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("POST /sow/:sowId/submit returns 500 on unknown RuntimeException")
    void shouldReturn500OnSubmitUnknownException() {
        sowService.throwOnSubmit = new RuntimeException("unexpected");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/sow/sow-1/submit")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-2")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(500);
    }

    // ---- POST /sow/:sowId/approve ----

    @Test
    @DisplayName("POST /sow/:sowId/approve returns 200 on success")
    void shouldApproveDraft() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/sow/sow-1/approve")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-3")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("POST /sow/:sowId/approve returns 400 when X-Idempotency-Key missing")
    void shouldRejectApproveWithoutIdempotencyKey() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/sow/sow-1/approve")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /sow/:sowId/approve returns 400 when X-Idempotency-Key is blank")
    void shouldRejectApproveWithBlankIdempotencyKey() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/sow/sow-1/approve")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "  ")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /sow/:sowId/approve returns 400 when X-Tenant-ID missing")
    void shouldRejectApproveWithoutTenant() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/sow/sow-1/approve")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-3")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /sow/:sowId/approve returns 404 on NoSuchElement")
    void shouldReturn404OnApproveNotFound() {
        sowService.throwOnApprove = new NoSuchElementException("sow not found");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/sow/sow-1/approve")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-3")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("POST /sow/:sowId/approve returns 409 on IllegalStateException")
    void shouldReturn409OnApproveIllegalState() {
        sowService.throwOnApprove = new IllegalStateException("not in pending review");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/sow/sow-1/approve")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-3")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(409);
    }

    @Test
    @DisplayName("POST /sow/:sowId/approve returns 403 on SecurityException")
    void shouldReturn403OnApproveSecurityException() {
        sowService.throwOnApprove = new SecurityException("not authorized");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/sow/sow-1/approve")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-3")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("POST /sow/:sowId/approve returns 500 on unknown RuntimeException")
    void shouldReturn500OnApproveUnknownException() {
        sowService.throwOnApprove = new RuntimeException("unexpected");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/sow/sow-1/approve")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-3")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(500);
    }

    // ---- POST /sow/:sowId/export ----

    @Test
    @DisplayName("POST /sow/:sowId/export returns 200 on success")
    void shouldExportDraft() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/sow/sow-1/export")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-4")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("POST /sow/:sowId/export returns 400 when X-Idempotency-Key missing")
    void shouldRejectExportWithoutIdempotencyKey() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/sow/sow-1/export")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /sow/:sowId/export returns 404 on NoSuchElement")
    void shouldReturn404OnExportNotFound() {
        sowService.throwOnExport = new NoSuchElementException("sow not found");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/sow/sow-1/export")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-4")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("POST /sow/:sowId/export returns 409 on IllegalStateException")
    void shouldReturn409OnExportIllegalState() {
        sowService.throwOnExport = new IllegalStateException("not approved");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/sow/sow-1/export")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-4")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(409);
    }

    @Test
    @DisplayName("POST /sow/:sowId/export returns 403 on SecurityException")
    void shouldReturn403OnExportSecurityException() {
        sowService.throwOnExport = new SecurityException("not authorized");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/sow/sow-1/export")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-4")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("POST /sow/:sowId/export returns 500 on unknown RuntimeException")
    void shouldReturn500OnExportUnknownException() {
        sowService.throwOnExport = new RuntimeException("unexpected");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/sow/sow-1/export")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-4")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(500);
    }

    // ---- GET /sow/:sowId ----

    @Test
    @DisplayName("GET /sow/:sowId returns 200 when SOW found and ID matches")
    void shouldGetDraftById() {
        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/sow/sow-1")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("GET /sow/:sowId returns 404 when sowId does not match service result")
    void shouldReturn404WhenSowIdMismatch() {
        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/sow/nonexistent-id")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("GET /sow/:sowId returns 404 on NoSuchElement from service")
    void shouldReturn404OnGetNoSuchElement() {
        sowService.throwOnGet = new NoSuchElementException("no sow found");

        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/sow/sow-1")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("GET /sow/:sowId returns 403 on SecurityException")
    void shouldReturn403OnGetSecurityException() {
        sowService.throwOnGet = new SecurityException("not authorized");

        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/sow/sow-1")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("GET /sow/:sowId returns 400 when X-Tenant-ID is missing")
    void shouldReturn400WhenTenantMissingOnGet() {
        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/sow/sow-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("GET /sow/:sowId returns 500 on unknown RuntimeException")
    void shouldReturn500OnGetUnknownException() {
        sowService.throwOnGet = new RuntimeException("unexpected");

        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/sow/sow-1")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("GET /sow/:sowId passes X-Roles and X-Idempotency-Key (optional for GET)")
    void shouldPassRolesOnGet() {
        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/sow/sow-1")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Roles"), "viewer")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-optional")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    // ---- fake service ----

    private static final class FakeSowService implements SowService {

        DmOperationContext lastContext;
        RuntimeException throwOnGenerate;
        RuntimeException throwOnGet;
        RuntimeException throwOnSubmit;
        RuntimeException throwOnApprove;
        RuntimeException throwOnExport;

        private SowDraft stubDraft() {
            SowClause clause = new SowClause(
                "clause-1", "SCOPE", "v1.0",
                "Service delivery scope.",
                "Legal", "Counsel",
                LocalDate.of(2026, 1, 1), SowClauseStatus.APPROVED);
            return SowDraft.builder()
                .sowId("sow-1")
                .workspaceId(DmWorkspaceId.of("ws-1"))
                .proposalId("proposal-1")
                .templateVersion("v1.0")
                .selectedClauses(List.of(clause))
                .riskFlags(List.of())
                .assumptions("Standard assumptions")
                .exclusions("No media buying")
                .disclaimer(SowDraft.LEGAL_DISCLAIMER)
                .modelVersion("v1.0")
                .status(SowStatus.DRAFT)
                .createdAt(Instant.now())
                .build();
        }

        @Override
        public Promise<SowDraft> generateDraft(DmOperationContext ctx, GenerateSowCommand command) {
            this.lastContext = ctx;
            if (throwOnGenerate != null) {
                return Promise.ofException((Exception) throwOnGenerate);
            }
            return Promise.of(stubDraft());
        }

        @Override
        public Promise<SowDraft> getDraft(DmOperationContext ctx) {
            this.lastContext = ctx;
            if (throwOnGet != null) {
                return Promise.ofException((Exception) throwOnGet);
            }
            return Promise.of(stubDraft());
        }

        @Override
        public Promise<SowDraft> submitForReview(DmOperationContext ctx, String sowId) {
            this.lastContext = ctx;
            if (throwOnSubmit != null) {
                return Promise.ofException((Exception) throwOnSubmit);
            }
            return Promise.of(stubDraft().submitForReview());
        }

        @Override
        public Promise<SowDraft> approveDraft(DmOperationContext ctx, String sowId) {
            this.lastContext = ctx;
            if (throwOnApprove != null) {
                return Promise.ofException((Exception) throwOnApprove);
            }
            SowDraft pending = stubDraft().submitForReview();
            return Promise.of(pending.approve("reviewer-1", Instant.now()));
        }

        @Override
        public Promise<SowDraft> exportDraft(DmOperationContext ctx, String sowId) {
            this.lastContext = ctx;
            if (throwOnExport != null) {
                return Promise.ofException((Exception) throwOnExport);
            }
            SowDraft pending = stubDraft().submitForReview();
            SowDraft approved = pending.approve("reviewer-1", Instant.now());
            return Promise.of(approved.export());
        }
    }
}

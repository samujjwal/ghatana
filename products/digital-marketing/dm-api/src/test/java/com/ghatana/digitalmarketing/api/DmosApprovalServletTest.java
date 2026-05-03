package com.ghatana.digitalmarketing.api;

import com.ghatana.digitalmarketing.application.approval.ApprovalSnapshotRepository;
import com.ghatana.digitalmarketing.application.approval.ApprovalWorkflowService;
import com.ghatana.digitalmarketing.application.approval.ApprovalWorkflowService.RecordApprovalDecisionCommand;
import com.ghatana.digitalmarketing.application.approval.ApprovalWorkflowService.SubmitForApprovalCommand;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.approval.ApprovalSnapshot;
import com.ghatana.digitalmarketing.domain.approval.ApprovalTargetType;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.approval.ApprovalDecision;
import com.ghatana.plugin.approval.ApprovalRecord;
import com.ghatana.plugin.approval.ApprovalStatus;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("DmosApprovalServlet")
class DmosApprovalServletTest extends EventloopTestBase {

    private FakeApprovalWorkflowService approvalService;
    private AsyncServlet servlet;

    private static final String VALID_SUBMIT_BODY =
        "{\"targetType\":\"CONTENT_VERSION\",\"targetId\":\"cv-1\"," +
        "\"description\":\"Approve this ad copy\",\"riskLevel\":2}";

    private static final String APPROVE_BODY =
        "{\"decision\":\"APPROVED\"}";

    private static final String REJECT_BODY =
        "{\"decision\":\"REJECTED\",\"notes\":\"Missing legal disclosure\"}";

    @BeforeEach
    void setUp() {
        approvalService = new FakeApprovalWorkflowService();
        servlet = new DmosApprovalServlet(approvalService, Eventloop.create()).routes();
    }

    // -------------------------------------------------------------------------
    // Constructor guard
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("constructor rejects null dependencies")
    void shouldRejectNullDependencies() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DmosApprovalServlet(null, Eventloop.create()));
        assertThatNullPointerException()
            .isThrownBy(() -> new DmosApprovalServlet(approvalService, null));
    }

    // -------------------------------------------------------------------------
    // POST /v1/workspaces/:workspaceId/approvals — submit
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /approvals with valid body returns 201")
    void shouldSubmitForApproval201() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/approvals")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "user-alice")
            .withBody(VALID_SUBMIT_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(201);
    }

    @Test
    @DisplayName("POST /approvals without X-Tenant-ID returns 400")
    void shouldRejectSubmitWithoutTenant() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/approvals")
            .withBody(VALID_SUBMIT_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /approvals with invalid targetType returns 400")
    void shouldRejectSubmitWithBadTargetType() {
        String body = "{\"targetType\":\"BOGUS\",\"targetId\":\"x\",\"description\":\"desc\"}";

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/approvals")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody(body.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /approvals with malformed JSON returns 400")
    void shouldRejectSubmitWithMalformedBody() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/approvals")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody("{invalid json".getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /approvals returns 403 on SecurityException from service")
    void shouldReturn403OnSubmitSecurityException() {
        approvalService.throwOnSubmit = new SecurityException("not authorised");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/approvals")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody(VALID_SUBMIT_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("POST /approvals returns 400 on IllegalArgumentException from service")
    void shouldReturn400OnSubmitIllegalArgument() {
        approvalService.throwOnSubmit = new IllegalArgumentException("invalid field");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/approvals")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody(VALID_SUBMIT_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /approvals returns 500 on unknown exception from service")
    void shouldReturn500OnSubmitUnknownException() {
        approvalService.throwOnSubmit = new RuntimeException("boom");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/approvals")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody(VALID_SUBMIT_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(500);
    }

    // -------------------------------------------------------------------------
    // POST .../approvals/:requestId/decide
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /approvals/:requestId/decide with APPROVED returns 200")
    void shouldApprove200() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/approvals/req-1/decide")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody(APPROVE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("POST /approvals/:requestId/decide with REJECTED and notes returns 200")
    void shouldReject200WithNotes() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/approvals/req-1/decide")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody(REJECT_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("POST /approvals/:requestId/decide with invalid decision value returns 400")
    void shouldRejectBadDecisionValue() {
        String body = "{\"decision\":\"MAYBE\",\"notes\":\"hm\"}";

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/approvals/req-1/decide")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody(body.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /approvals/:requestId/decide without tenant returns 400")
    void shouldRejectDecideWithoutTenant() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/approvals/req-1/decide")
            .withBody(APPROVE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /approvals/:requestId/decide returns 403 on SecurityException")
    void shouldReturn403OnDecideSecurityException() {
        approvalService.throwOnDecide = new SecurityException("not authorised");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/approvals/req-1/decide")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody(APPROVE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("POST /approvals/:requestId/decide returns 400 when service says notes required")
    void shouldReturn400WhenRejectNotesRequired() {
        approvalService.throwOnDecide = new IllegalArgumentException("Notes are required");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/approvals/req-1/decide")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody("{\"decision\":\"REJECTED\"}".getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /approvals/:requestId/decide returns 500 on unknown exception")
    void shouldReturn500OnDecideUnknownException() {
        approvalService.throwOnDecide = new RuntimeException("boom");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/approvals/req-1/decide")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody(APPROVE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(500);
    }

    // -------------------------------------------------------------------------
    // GET .../approvals/:requestId
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /approvals/:requestId returns 200 when found")
    void shouldGetApprovalStatus200() {
        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/approvals/req-1")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("GET /approvals/:requestId returns 404 when not found")
    void shouldGetApprovalStatus404() {
        approvalService.approvalStatusEmpty = true;

        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/approvals/req-missing")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("GET /approvals/:requestId returns 403 on SecurityException")
    void shouldReturn403OnGetStatusSecurity() {
        approvalService.throwOnGetStatus = new SecurityException("denied");

        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/approvals/req-1")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("GET /approvals/:requestId returns 500 on unknown error")
    void shouldReturn500OnGetStatusUnknown() {
        approvalService.throwOnGetStatus = new RuntimeException("boom");

        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/approvals/req-1")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(500);
    }

    // -------------------------------------------------------------------------
    // GET .../approvals/:requestId/snapshot
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /approvals/:requestId/snapshot returns 200 when found")
    void shouldGetSnapshot200() {
        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/approvals/req-1/snapshot")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("GET /approvals/:requestId/snapshot returns 404 when not found")
    void shouldGetSnapshot404() {
        approvalService.snapshotEmpty = true;

        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/approvals/req-missing/snapshot")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("GET /approvals/:requestId/snapshot returns 403 on SecurityException")
    void shouldReturn403OnGetSnapshotSecurity() {
        approvalService.throwOnGetSnapshot = new SecurityException("denied");

        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/approvals/req-1/snapshot")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    // -------------------------------------------------------------------------
    // GET .../approvals/pending/:subjectId
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /approvals/pending/:subjectId returns 200 with list")
    void shouldListPending200() {
        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/approvals/pending/subject-ws1")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("GET /approvals/pending/:subjectId returns 403 on SecurityException")
    void shouldReturn403OnListPendingSecurity() {
        approvalService.throwOnListPending = new SecurityException("denied");

        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/approvals/pending/subject-ws1")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("GET /approvals/pending/:subjectId returns 500 on unknown error")
    void shouldReturn500OnListPendingUnknown() {
        approvalService.throwOnListPending = new RuntimeException("boom");

        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/approvals/pending/subject-ws1")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(500);
    }

    // -------------------------------------------------------------------------
    // Missing-tenant guard for GET endpoints
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /approvals/:requestId without X-Tenant-ID returns 400")
    void shouldRejectGetStatusWithoutTenant() {
        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/approvals/req-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("GET /approvals/:requestId/snapshot without X-Tenant-ID returns 400")
    void shouldRejectGetSnapshotWithoutTenant() {
        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/approvals/req-1/snapshot")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("GET /approvals/pending/:subjectId without X-Tenant-ID returns 400")
    void shouldRejectListPendingWithoutTenant() {
        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/approvals/pending/subject-ws1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    // -------------------------------------------------------------------------
    // NoSuchElementException → 404 via mapServiceError
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /approvals/:requestId returns 404 when service throws NoSuchElementException")
    void shouldReturn404OnGetStatusNoSuchElement() {
        approvalService.throwOnGetStatus = new java.util.NoSuchElementException("not found");

        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/approvals/req-gone")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(404);
    }

    // -------------------------------------------------------------------------
    // CSV header parsing (roles/permissions branch)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /approvals with roles and permissions headers succeeds")
    void shouldAcceptRolesAndPermissionsHeaders() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/approvals")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Roles"), "brand-manager,admin")
            .withHeader(HttpHeaders.of("X-Permissions"), "approve:write")
            .withBody(VALID_SUBMIT_BODY.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(201);
    }

    @Test
    @DisplayName("POST /approvals with null riskLevel uses default 1")
    void shouldUseDefaultRiskLevelWhenNull() {
        String body = "{\"targetType\":\"STRATEGY\",\"targetId\":\"s-1\",\"description\":\"desc\"}";

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/approvals")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody(body.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(201);
    }

    @Test
    @DisplayName("POST /approvals with explicit requiredApproverRole uses it")
    void shouldUseExplicitRequiredApproverRole() {
        String body = "{\"targetType\":\"BUDGET\",\"targetId\":\"b-1\"," +
            "\"description\":\"desc\",\"riskLevel\":4,\"requiredApproverRole\":\"exec-sponsor\"}";

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/approvals")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody(body.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(201);
    }

    // -------------------------------------------------------------------------
    // Test double
    // -------------------------------------------------------------------------

    private static final class FakeApprovalWorkflowService implements ApprovalWorkflowService {
        RuntimeException throwOnSubmit;
        RuntimeException throwOnDecide;
        RuntimeException throwOnGetStatus;
        RuntimeException throwOnGetSnapshot;
        RuntimeException throwOnListPending;
        boolean approvalStatusEmpty = false;
        boolean snapshotEmpty       = false;

        private static ApprovalRecord sampleRecord(String requestId) {
            return new ApprovalRecord(
                requestId, "cv-1", "user-alice",
                "dmos-approval/content_version", ApprovalStatus.PENDING,
                Instant.now(), null, null, null, null);
        }

        private static ApprovalSnapshot sampleSnapshot(String requestId) {
            return new ApprovalSnapshot(
                requestId, ApprovalTargetType.CONTENT_VERSION, "cv-1",
                "ws-1", "Approve ad copy", null, 2, "brand-manager", Instant.now(), 0L);
        }

        @Override
        public Promise<ApprovalRecord> submitForApproval(
                DmOperationContext ctx, SubmitForApprovalCommand command) {
            if (throwOnSubmit != null) return Promise.ofException(throwOnSubmit);
            return Promise.of(sampleRecord("req-" + command.targetId()));
        }

        @Override
        public Promise<ApprovalRecord> recordDecision(
                DmOperationContext ctx, RecordApprovalDecisionCommand command) {
            if (throwOnDecide != null) return Promise.ofException(throwOnDecide);
            ApprovalRecord record = sampleRecord(command.requestId());
            ApprovalStatus status = command.decision() == ApprovalDecision.APPROVED
                ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED;
            return Promise.of(new ApprovalRecord(
                command.requestId(), record.subjectId(), record.requestedBy(), record.action(),
                status, record.requestedAt(), null, Instant.now(), "user-alice", command.notes()));
        }

        @Override
        public Promise<Optional<ApprovalRecord>> getApprovalStatus(
                DmOperationContext ctx, String requestId) {
            if (throwOnGetStatus != null) return Promise.ofException(throwOnGetStatus);
            if (approvalStatusEmpty) return Promise.of(Optional.empty());
            return Promise.of(Optional.of(sampleRecord(requestId)));
        }

        @Override
        public Promise<List<ApprovalRecord>> listPendingApprovals(
                DmOperationContext ctx, String subjectId) {
            if (throwOnListPending != null) return Promise.ofException(throwOnListPending);
            return Promise.of(List.of(sampleRecord("req-a"), sampleRecord("req-b")));
        }

        @Override
        public Promise<Optional<ApprovalSnapshot>> getSnapshot(
                DmOperationContext ctx, String requestId) {
            if (throwOnGetSnapshot != null) return Promise.ofException(throwOnGetSnapshot);
            if (snapshotEmpty) return Promise.of(Optional.empty());
            return Promise.of(Optional.of(sampleSnapshot(requestId)));
        }
    }
}

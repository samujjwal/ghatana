package com.ghatana.yappc.services.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.agent.AepEventPublisher;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpHeader;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Verifies lifecycle approval HTTP handlers for tenant validation and approval decision flows.
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApprovalHttpHandlers")
class ApprovalHttpHandlersTest extends EventloopTestBase {

    @Mock
    private AepEventPublisher publisher;

    private ApprovalHttpHandlers handlers;
    private HumanApprovalService humanApprovalService;

    @BeforeEach
    void setUp() {
        // lenient: publisher stub is only needed for tests that call requestApproval
        lenient().when(publisher.publish(anyString(), anyString(), any())).thenReturn(Promise.complete());
        humanApprovalService = new HumanApprovalService(publisher);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        handlers = new ApprovalHttpHandlers(humanApprovalService, objectMapper);
    }

    @Test
    @DisplayName("listPending rejects requests without tenant header")
    void listPendingRejectsMissingTenantHeader() {
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/approvals/pending").build();

        HttpResponse response = runPromise(() -> handlers.listPending(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("listPending returns pending approvals for tenant")
    void listPendingReturnsPendingApprovals() {
        ApprovalRequest created = runPromise(() -> humanApprovalService.requestApproval(
                "tenant-1",
                "project-1",
                "agent-1",
                ApprovalRequest.ApprovalType.PHASE_ADVANCE,
                new ApprovalRequest.ApprovalContext("INTENT", "SHAPE", "blocked", List.of("criteria"), List.of())
        ));

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/approvals/pending")
                .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant-1")
                .build();

        HttpResponse response = runPromise(() -> handlers.listPending(request));
        String body = response.getBody().getString(StandardCharsets.UTF_8);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(body).contains(created.id());
        assertThat(body).contains("PENDING");
    }

    @Test
    @DisplayName("approve transitions request to approved")
    void approveTransitionsRequest() {
        ApprovalRequest created = runPromise(() -> humanApprovalService.requestApproval(
                "tenant-1",
                "project-1",
                "agent-1",
                ApprovalRequest.ApprovalType.PHASE_ADVANCE,
                new ApprovalRequest.ApprovalContext("INTENT", "SHAPE", "blocked", List.of("criteria"), List.of())
        ));

        HttpRequest request = HttpRequest.post("http://localhost/api/v1/approvals/" + created.id() + "/approve")
                .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant-1")
                .withBody(ByteBuf.wrapForReading("{\"decidedBy\":\"reviewer-1\"}".getBytes(StandardCharsets.UTF_8)))
                .build();

        HttpResponse response = runPromise(() -> handlers.approve(request, created.id()));
        String body = response.getBody().getString(StandardCharsets.UTF_8);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(body).contains("APPROVED");
        assertThat(body).contains("reviewer-1");
    }

    @Test
    @DisplayName("reject returns conflict when approval request is missing")
    void rejectReturnsConflictWhenRequestMissing() {
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/approvals/missing/reject")
                .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant-1")
                .withBody(ByteBuf.wrapForReading("{\"decidedBy\":\"reviewer-1\"}".getBytes(StandardCharsets.UTF_8)))
                .build();

        HttpResponse response = runPromise(() -> handlers.reject(request, "missing"));
        String body = response.getBody().getString(StandardCharsets.UTF_8);

        assertThat(response.getCode()).isEqualTo(409);
        assertThat(body).contains("Approval request not found");
    }

    @Test
    @DisplayName("approve rejects malformed payload")
    void approveRejectsMalformedPayload() {
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/approvals/request-1/approve")
                .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant-1")
                .withBody(ByteBuf.wrapForReading("not-json".getBytes(StandardCharsets.UTF_8)))
                .build();

        HttpResponse response = runPromise(() -> handlers.approve(request, "request-1"));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("create returns 400 when missing tenant header")
    void createRejectsMissingTenantHeader() {
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/approvals")
                .withBody(ByteBuf.wrapForReading("{}".getBytes(StandardCharsets.UTF_8)))
                .build();

        HttpResponse response = runPromise(() -> handlers.create(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("create returns 400 when projectId is missing")
    void createRejectsMissingProjectId() {
        String body = "{\"approvalType\":\"PHASE_ADVANCE\"}";
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/approvals")
                .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant-1")
                .withBody(ByteBuf.wrapForReading(body.getBytes(StandardCharsets.UTF_8)))
                .build();

        HttpResponse response = runPromise(() -> handlers.create(request));

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getBody().getString(StandardCharsets.UTF_8)).contains("projectId");
    }

    @Test
    @DisplayName("create returns 400 for unknown approvalType")
    void createRejectsUnknownApprovalType() {
        String body = "{\"projectId\":\"p1\",\"approvalType\":\"NONEXISTENT\"}";
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/approvals")
                .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant-1")
                .withBody(ByteBuf.wrapForReading(body.getBytes(StandardCharsets.UTF_8)))
                .build();

        HttpResponse response = runPromise(() -> handlers.create(request));

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getBody().getString(StandardCharsets.UTF_8)).contains("NONEXISTENT");
    }

    @Test
    @DisplayName("create returns 201 and persists a new PENDING approval request")
    void createPersistsNewPendingApprovalRequest() {
        String body = "{\"projectId\":\"p1\",\"approvalType\":\"PHASE_ADVANCE\","
                + "\"context\":{\"fromPhase\":\"INTENT\",\"toPhase\":\"SHAPE\","
                + "\"blockReason\":\"criteria not met\",\"unmetCriteria\":[],\"missingArtifacts\":[]}}";
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/approvals")
                .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant-1")
                .withBody(ByteBuf.wrapForReading(body.getBytes(StandardCharsets.UTF_8)))
                .build();

        HttpResponse response = runPromise(() -> handlers.create(request));
        String responseBody = response.getBody().getString(StandardCharsets.UTF_8);

        assertThat(response.getCode()).isEqualTo(201);
        assertThat(responseBody).contains("PENDING");
        assertThat(responseBody).contains("p1");
        assertThat(responseBody).contains("PHASE_ADVANCE");
    }

    @Test
    @DisplayName("getById returns 400 when missing tenant header")
    void getByIdRejectsMissingTenantHeader() {
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/approvals/some-id").build();

        HttpResponse response = runPromise(() -> handlers.getById(request, "some-id"));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("getById returns 404 when request does not exist")
    void getByIdReturnsNotFoundForMissingRequest() {
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/approvals/missing")
                .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant-1")
                .build();

        HttpResponse response = runPromise(() -> handlers.getById(request, "missing"));

        assertThat(response.getCode()).isEqualTo(404);
        assertThat(response.getBody().getString(StandardCharsets.UTF_8)).contains("not found");
    }

    @Test
    @DisplayName("getById returns the approval request when it exists")
    void getByIdReturnsExistingApprovalRequest() {
        ApprovalRequest created = runPromise(() -> humanApprovalService.requestApproval(
                "tenant-1",
                "project-get",
                null,
                ApprovalRequest.ApprovalType.DEPLOYMENT,
                new ApprovalRequest.ApprovalContext("", "", "deployment gate", List.of(), List.of())
        ));

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/approvals/" + created.id())
                .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant-1")
                .build();

        HttpResponse response = runPromise(() -> handlers.getById(request, created.id()));
        String responseBody = response.getBody().getString(StandardCharsets.UTF_8);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(responseBody).contains(created.id());
        assertThat(responseBody).contains("DEPLOYMENT");
        assertThat(responseBody).contains("PENDING");
    }
}
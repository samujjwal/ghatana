package com.ghatana.yappc.services.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.agent.AepEventPublisher;
import io.activej.bytebuf.ByteBuf;
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

/**
 * @doc.type class
 * @doc.purpose Verifies lifecycle approval HTTP handlers for tenant validation and approval decision flows.
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("ApprovalHttpHandlers")
class ApprovalHttpHandlersTest extends EventloopTestBase {

    @Mock
    private AepEventPublisher publisher;

    private ApprovalHttpHandlers handlers;
    private HumanApprovalService humanApprovalService;

    @BeforeEach
    void setUp() { // GH-90000
        // lenient: publisher stub is only needed for tests that call requestApproval
        lenient().when(publisher.publish(anyString(), anyString(), any())).thenReturn(Promise.complete()); // GH-90000
        humanApprovalService = new HumanApprovalService(publisher); // GH-90000
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule()); // GH-90000
        handlers = new ApprovalHttpHandlers(humanApprovalService, objectMapper); // GH-90000
    }

    @Test
    @DisplayName("listPending rejects requests without tenant header")
    void listPendingRejectsMissingTenantHeader() { // GH-90000
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/approvals/pending").build();

        HttpResponse response = runPromise(() -> handlers.listPending(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(400); // GH-90000
    }

    @Test
    @DisplayName("listPending returns pending approvals for tenant")
    void listPendingReturnsPendingApprovals() { // GH-90000
        ApprovalRequest created = runPromise(() -> humanApprovalService.requestApproval( // GH-90000
                "tenant-1",
                "project-1",
                "agent-1",
                ApprovalRequest.ApprovalType.PHASE_ADVANCE,
                new ApprovalRequest.ApprovalContext("INTENT", "SHAPE", "blocked", List.of("criteria"), List.of())
        ));

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/approvals/pending")
                .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant-1")
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> handlers.listPending(request)); // GH-90000
        String body = response.getBody().getString(StandardCharsets.UTF_8); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        assertThat(body).contains(created.id()); // GH-90000
        assertThat(body).contains("PENDING");
    }

    @Test
    @DisplayName("approve transitions request to approved")
    void approveTransitionsRequest() { // GH-90000
        ApprovalRequest created = runPromise(() -> humanApprovalService.requestApproval( // GH-90000
                "tenant-1",
                "project-1",
                "agent-1",
                ApprovalRequest.ApprovalType.PHASE_ADVANCE,
                new ApprovalRequest.ApprovalContext("INTENT", "SHAPE", "blocked", List.of("criteria"), List.of())
        ));

        HttpRequest request = HttpRequest.post("http://localhost/api/v1/approvals/" + created.id() + "/approve") // GH-90000
                .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant-1")
                .withBody(ByteBuf.wrapForReading("{\"decidedBy\":\"reviewer-1\"}".getBytes(StandardCharsets.UTF_8))) // GH-90000
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> handlers.approve(request, created.id())); // GH-90000
        String body = response.getBody().getString(StandardCharsets.UTF_8); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        assertThat(body).contains("APPROVED");
        assertThat(body).contains("reviewer-1");
    }

    @Test
    @DisplayName("reject returns conflict when approval request is missing")
    void rejectReturnsConflictWhenRequestMissing() { // GH-90000
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/approvals/missing/reject")
                .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant-1")
                .withBody(ByteBuf.wrapForReading("{\"decidedBy\":\"reviewer-1\"}".getBytes(StandardCharsets.UTF_8))) // GH-90000
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> handlers.reject(request, "missing")); // GH-90000
        String body = response.getBody().getString(StandardCharsets.UTF_8); // GH-90000

        assertThat(response.getCode()).isEqualTo(409); // GH-90000
        assertThat(body).contains("Approval request not found");
    }

    @Test
    @DisplayName("approve rejects malformed payload")
    void approveRejectsMalformedPayload() { // GH-90000
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/approvals/request-1/approve")
                .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant-1")
                .withBody(ByteBuf.wrapForReading("not-json".getBytes(StandardCharsets.UTF_8))) // GH-90000
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> handlers.approve(request, "request-1")); // GH-90000

        assertThat(response.getCode()).isEqualTo(400); // GH-90000
    }

    @Test
    @DisplayName("create returns 400 when missing tenant header")
    void createRejectsMissingTenantHeader() { // GH-90000
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/approvals")
                .withBody(ByteBuf.wrapForReading("{}".getBytes(StandardCharsets.UTF_8))) // GH-90000
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> handlers.create(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(400); // GH-90000
    }

    @Test
    @DisplayName("create returns 400 when projectId is missing")
    void createRejectsMissingProjectId() { // GH-90000
        String body = "{\"approvalType\":\"PHASE_ADVANCE\"}";
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/approvals")
                .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant-1")
                .withBody(ByteBuf.wrapForReading(body.getBytes(StandardCharsets.UTF_8))) // GH-90000
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> handlers.create(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(400); // GH-90000
        assertThat(response.getBody().getString(StandardCharsets.UTF_8)).contains("projectId");
    }

    @Test
    @DisplayName("create returns 400 for unknown approvalType")
    void createRejectsUnknownApprovalType() { // GH-90000
        String body = "{\"projectId\":\"p1\",\"approvalType\":\"NONEXISTENT\"}";
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/approvals")
                .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant-1")
                .withBody(ByteBuf.wrapForReading(body.getBytes(StandardCharsets.UTF_8))) // GH-90000
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> handlers.create(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(400); // GH-90000
        assertThat(response.getBody().getString(StandardCharsets.UTF_8)).contains("NONEXISTENT");
    }

    @Test
    @DisplayName("create returns 201 and persists a new PENDING approval request")
    void createPersistsNewPendingApprovalRequest() { // GH-90000
        String body = "{\"projectId\":\"p1\",\"approvalType\":\"PHASE_ADVANCE\","
                + "\"context\":{\"fromPhase\":\"INTENT\",\"toPhase\":\"SHAPE\","
                + "\"blockReason\":\"criteria not met\",\"unmetCriteria\":[],\"missingArtifacts\":[]}}";
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/approvals")
                .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant-1")
                .withBody(ByteBuf.wrapForReading(body.getBytes(StandardCharsets.UTF_8))) // GH-90000
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> handlers.create(request)); // GH-90000
        String responseBody = response.getBody().getString(StandardCharsets.UTF_8); // GH-90000

        assertThat(response.getCode()).isEqualTo(201); // GH-90000
        assertThat(responseBody).contains("PENDING");
        assertThat(responseBody).contains("p1");
        assertThat(responseBody).contains("PHASE_ADVANCE");
    }

    @Test
    @DisplayName("getById returns 400 when missing tenant header")
    void getByIdRejectsMissingTenantHeader() { // GH-90000
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/approvals/some-id").build();

        HttpResponse response = runPromise(() -> handlers.getById(request, "some-id")); // GH-90000

        assertThat(response.getCode()).isEqualTo(400); // GH-90000
    }

    @Test
    @DisplayName("getById returns 404 when request does not exist")
    void getByIdReturnsNotFoundForMissingRequest() { // GH-90000
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/approvals/missing")
                .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant-1")
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> handlers.getById(request, "missing")); // GH-90000

        assertThat(response.getCode()).isEqualTo(404); // GH-90000
        assertThat(response.getBody().getString(StandardCharsets.UTF_8)).contains("not found");
    }

    @Test
    @DisplayName("getById returns the approval request when it exists")
    void getByIdReturnsExistingApprovalRequest() { // GH-90000
        ApprovalRequest created = runPromise(() -> humanApprovalService.requestApproval( // GH-90000
                "tenant-1",
                "project-get",
                null,
                ApprovalRequest.ApprovalType.DEPLOYMENT,
                new ApprovalRequest.ApprovalContext("", "", "deployment gate", List.of(), List.of()) // GH-90000
        ));

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/approvals/" + created.id()) // GH-90000
                .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant-1")
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> handlers.getById(request, created.id())); // GH-90000
        String responseBody = response.getBody().getString(StandardCharsets.UTF_8); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        assertThat(responseBody).contains(created.id()); // GH-90000
        assertThat(responseBody).contains("DEPLOYMENT");
        assertThat(responseBody).contains("PENDING");
    }
}

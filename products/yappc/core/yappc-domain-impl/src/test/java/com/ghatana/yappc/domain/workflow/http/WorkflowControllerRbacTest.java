package com.ghatana.yappc.domain.workflow.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.workflow.AiWorkflowService;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * RBAC enforcement tests for {@link WorkflowController}.
 *
 * <p>Verifies that mutations require an appropriate {@code X-User-Role} header and that
 * VIEWER or missing roles are rejected with {@code 403 Forbidden}.
 *
 * @doc.type class
 * @doc.purpose RBAC guard regression tests for workflow mutations
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("WorkflowController — RBAC Guard")
@ExtendWith(MockitoExtension.class)
class WorkflowControllerRbacTest extends EventloopTestBase {

    @Mock
    private AiWorkflowService workflowService;

    private WorkflowController controller;
    private ObjectMapper objectMapper;

    private static final String TENANT_ID = "tenant-rbac-test";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        controller = new WorkflowController(workflowService, objectMapper);

        // Stub service methods as lenient — only invoked when RBAC passes
        lenient().when(workflowService.createWorkflow(any()))
            .thenReturn(io.activej.promise.Promise.ofException(new RuntimeException("should not be called")));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Missing role header
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Missing X-User-Role header")
    class MissingRoleHeader {

        @Test
        @DisplayName("createWorkflow should return 403 when X-User-Role is absent")
        void createWorkflow_missingRole_returns403() {
            HttpRequest request = buildRequest(HttpMethod.POST, "/api/v1/workflows", null)
                .withBody("{\"name\":\"wf\",\"description\":\"d\",\"type\":\"DETERMINISTIC\",\"createdBy\":null}".getBytes())
                .build();

            HttpResponse response = runPromise(() -> controller.createWorkflow(request));

            assertThat(response.getCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("deleteWorkflow should return 403 when X-User-Role is absent")
        void deleteWorkflow_missingRole_returns403() {
            HttpRequest request = buildRequest(HttpMethod.DELETE, "/api/v1/workflows/wf-1", null)
                .build();

            HttpResponse response = runPromise(() -> controller.deleteWorkflow(request, "wf-1"));

            assertThat(response.getCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("approvePlan should return 403 when X-User-Role is absent")
        void approvePlan_missingRole_returns403() {
            HttpRequest request = buildRequest(HttpMethod.POST, "/api/v1/workflows/wf-1/plans/plan-1/approve", null)
                .withHeader(HttpHeaders.of("Idempotency-Key"), "key-001")
                .build();

            HttpResponse response = runPromise(() -> controller.approvePlan(request, "wf-1", "plan-1"));

            assertThat(response.getCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("rejectPlan should return 403 when X-User-Role is absent")
        void rejectPlan_missingRole_returns403() {
            HttpRequest request = buildRequest(HttpMethod.POST, "/api/v1/workflows/wf-1/plans/plan-1/reject", null)
                .withHeader(HttpHeaders.of("Idempotency-Key"), "key-002")
                .withBody("{\"reason\":\"not good\"}".getBytes())
                .build();

            HttpResponse response = runPromise(() -> controller.rejectPlan(request, "wf-1", "plan-1"));

            assertThat(response.getCode()).isEqualTo(403);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VIEWER role — denied for all mutations
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("VIEWER role — denied on mutations")
    class ViewerRoleDenied {

        @Test
        @DisplayName("createWorkflow returns 403 for VIEWER role")
        void createWorkflow_viewerRole_returns403() {
            HttpRequest request = buildRequest(HttpMethod.POST, "/api/v1/workflows", WorkflowController.WorkflowRoles.VIEWER)
                .withBody("{\"name\":\"wf\",\"description\":\"d\",\"type\":\"DETERMINISTIC\",\"createdBy\":null}".getBytes())
                .build();

            HttpResponse response = runPromise(() -> controller.createWorkflow(request));

            assertThat(response.getCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("deleteWorkflow returns 403 for VIEWER role")
        void deleteWorkflow_viewerRole_returns403() {
            HttpRequest request = buildRequest(HttpMethod.DELETE, "/api/v1/workflows/wf-1", WorkflowController.WorkflowRoles.VIEWER)
                .build();

            HttpResponse response = runPromise(() -> controller.deleteWorkflow(request, "wf-1"));

            assertThat(response.getCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("startWorkflow returns 403 for VIEWER role")
        void startWorkflow_viewerRole_returns403() {
            HttpRequest request = buildRequest(HttpMethod.POST, "/api/v1/workflows/wf-1/start", WorkflowController.WorkflowRoles.VIEWER)
                .build();

            HttpResponse response = runPromise(() -> controller.startWorkflow(request, "wf-1"));

            assertThat(response.getCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("approvePlan returns 403 for VIEWER role")
        void approvePlan_viewerRole_returns403() {
            HttpRequest request = buildRequest(HttpMethod.POST, "/api/v1/workflows/wf-1/plans/plan-1/approve", WorkflowController.WorkflowRoles.VIEWER)
                .withHeader(HttpHeaders.of("Idempotency-Key"), "key-003")
                .build();

            HttpResponse response = runPromise(() -> controller.approvePlan(request, "wf-1", "plan-1"));

            assertThat(response.getCode()).isEqualTo(403);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DEVELOPER role — denied for approval operations
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DEVELOPER role — denied on approval operations")
    class DeveloperDeniedOnApproval {

        @Test
        @DisplayName("approvePlan returns 403 for DEVELOPER role")
        void approvePlan_developerRole_returns403() {
            HttpRequest request = buildRequest(HttpMethod.POST, "/api/v1/workflows/wf-1/plans/plan-1/approve", WorkflowController.WorkflowRoles.DEVELOPER)
                .withHeader(HttpHeaders.of("Idempotency-Key"), "key-004")
                .build();

            HttpResponse response = runPromise(() -> controller.approvePlan(request, "wf-1", "plan-1"));

            assertThat(response.getCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("rejectPlan returns 403 for DEVELOPER role")
        void rejectPlan_developerRole_returns403() {
            HttpRequest request = buildRequest(HttpMethod.POST, "/api/v1/workflows/wf-1/plans/plan-1/reject", WorkflowController.WorkflowRoles.DEVELOPER)
                .withHeader(HttpHeaders.of("Idempotency-Key"), "key-005")
                .withBody("{\"reason\":\"not good\"}".getBytes())
                .build();

            HttpResponse response = runPromise(() -> controller.rejectPlan(request, "wf-1", "plan-1"));

            assertThat(response.getCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("deleteWorkflow returns 403 for DEVELOPER role")
        void deleteWorkflow_developerRole_returns403() {
            HttpRequest request = buildRequest(HttpMethod.DELETE, "/api/v1/workflows/wf-1", WorkflowController.WorkflowRoles.DEVELOPER)
                .build();

            HttpResponse response = runPromise(() -> controller.deleteWorkflow(request, "wf-1"));

            assertThat(response.getCode()).isEqualTo(403);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LEAD role — approved for write and approval ops but not delete
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("LEAD role — denied on admin operations")
    class LeadDeniedOnAdmin {

        @Test
        @DisplayName("deleteWorkflow returns 403 for LEAD role")
        void deleteWorkflow_leadRole_returns403() {
            HttpRequest request = buildRequest(HttpMethod.DELETE, "/api/v1/workflows/wf-1", WorkflowController.WorkflowRoles.LEAD)
                .build();

            HttpResponse response = runPromise(() -> controller.deleteWorkflow(request, "wf-1"));

            assertThat(response.getCode()).isEqualTo(403);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Role constant surface area
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("WRITE_ROLES contains OWNER, ADMIN, LEAD, DEVELOPER")
    void writeRoles_containsExpectedRoles() {
        assertThat(WorkflowController.WorkflowRoles.WRITE_ROLES)
            .containsExactlyInAnyOrder(
                WorkflowController.WorkflowRoles.OWNER,
                WorkflowController.WorkflowRoles.ADMIN,
                WorkflowController.WorkflowRoles.LEAD,
                WorkflowController.WorkflowRoles.DEVELOPER
            );
    }

    @Test
    @DisplayName("APPROVAL_ROLES contains OWNER, ADMIN, LEAD — not DEVELOPER")
    void approvalRoles_excludesDeveloper() {
        assertThat(WorkflowController.WorkflowRoles.APPROVAL_ROLES)
            .contains(WorkflowController.WorkflowRoles.OWNER,
                      WorkflowController.WorkflowRoles.ADMIN,
                      WorkflowController.WorkflowRoles.LEAD)
            .doesNotContain(WorkflowController.WorkflowRoles.DEVELOPER,
                            WorkflowController.WorkflowRoles.VIEWER);
    }

    @Test
    @DisplayName("ADMIN_ROLES contains OWNER and ADMIN only")
    void adminRoles_containsOwnerAndAdminOnly() {
        assertThat(WorkflowController.WorkflowRoles.ADMIN_ROLES)
            .containsExactlyInAnyOrder(
                WorkflowController.WorkflowRoles.OWNER,
                WorkflowController.WorkflowRoles.ADMIN
            );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private HttpRequest.Builder buildRequest(HttpMethod method, String path, String role) {
        HttpRequest.Builder builder = HttpRequest.builder(method, "http://localhost" + path)
            .withHeader(HttpHeaders.of("X-Tenant-ID"), TENANT_ID);
        if (role != null) {
            builder.withHeader(HttpHeaders.of("X-User-Role"), role);
        }
        return builder;
    }
}

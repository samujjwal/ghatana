package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * P1-033: Strategy full lifecycle integration tests.
 *
 * <p>End-to-end API tests covering the complete strategy journey:
 * <ol>
 *   <li>Generate strategy (AI-assisted)</li>
 *   <li>View generated strategy</li>
 *   <li>Submit for approval</li>
 *   <li>Approver reviews and approves/rejects</li>
 *   <li>Strategy activation</li>
 * </ol>
 *
 * <p>Tests verify API, database state, approval snapshot, audit trail,
 * and AI action log consistency.</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("P1-033: Strategy Lifecycle Integration Tests")
class StrategyLifecycleIntegrationTest {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String TENANT_ID = "strategy-test-tenant";
    private static final String WORKSPACE_ID = "strategy-test-workspace";
    private static final String AUTH_TOKEN = "Bearer test-token-strategy";
    private static final String REVIEWER_TOKEN = "Bearer test-token-reviewer";

    private static String strategyId;
    private static String approvalId;

    @BeforeAll
    static void setUp() {
        RestAssured.baseURI = "http://localhost:8080";
        RestAssured.basePath = "/v1";
    }

    @Test
    @Order(1)
    @DisplayName("P1-033: Generate strategy returns 201 with generated content")
    void shouldGenerateStrategySuccessfully() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();

        String requestBody = mapper.writeValueAsString(Map.of(
            "campaignId", "campaign-123",
            "goals", new String[]{"awareness", "conversion"},
            "budget", 50000,
            "channels", new String[]{"EMAIL", "SOCIAL", "PAID_SEARCH"}
        ));

        Response response = given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", idempotencyKey)
            .header("X-Correlation-ID", UUID.randomUUID().toString())
            .header("Authorization", AUTH_TOKEN)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/workspaces/{workspaceId}/strategies/generate", WORKSPACE_ID);

        response.then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("workspaceId", equalTo(WORKSPACE_ID))
            .body("campaignId", equalTo("campaign-123"))
            .body("status", equalTo("GENERATED"))
            .body("content", notNullValue())
            .body("aiActionLogId", notNullValue());

        strategyId = response.jsonPath().getString("id");
    }

    @Test
    @Order(2)
    @DisplayName("P1-033: Get generated strategy returns 200 with content")
    void shouldGetGeneratedStrategy() {
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("Authorization", AUTH_TOKEN)
        .when()
            .get("/workspaces/{workspaceId}/strategies/{strategyId}", WORKSPACE_ID, strategyId)
        .then()
            .statusCode(200)
            .body("id", equalTo(strategyId))
            .body("status", equalTo("GENERATED"))
            .body("content", notNullValue())
            .body("createdBy", notNullValue())
            .body("createdAt", notNullValue());
    }

    @Test
    @Order(3)
    @DisplayName("P1-033: Submit strategy for approval creates approval request")
    void shouldSubmitStrategyForApproval() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();

        String requestBody = mapper.writeValueAsString(Map.of(
            "justification", "Strategy aligns with Q1 campaign goals",
            "notes", "Please review budget allocation across channels"
        ));

        Response response = given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", idempotencyKey)
            .header("Authorization", AUTH_TOKEN)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/workspaces/{workspaceId}/strategies/{strategyId}/submit", WORKSPACE_ID, strategyId);

        response.then()
            .statusCode(201)
            .body("approvalId", notNullValue())
            .body("strategyId", equalTo(strategyId))
            .body("status", equalTo("PENDING"))
            .body("submittedBy", notNullValue())
            .body("submittedAt", notNullValue());

        approvalId = response.jsonPath().getString("approvalId");
    }

    @Test
    @Order(4)
    @DisplayName("P1-033: Strategy status changes to PENDING_APPROVAL after submit")
    void shouldShowStrategyAsPendingApproval() {
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("Authorization", AUTH_TOKEN)
        .when()
            .get("/workspaces/{workspaceId}/strategies/{strategyId}", WORKSPACE_ID, strategyId)
        .then()
            .statusCode(200)
            .body("id", equalTo(strategyId))
            .body("status", equalTo("PENDING_APPROVAL"))
            .body("approvalId", equalTo(approvalId));
    }

    @Test
    @Order(5)
    @DisplayName("P1-033: Approver can view pending approval")
    void shouldAllowApproverToViewPendingApproval() {
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("Authorization", REVIEWER_TOKEN)
        .when()
            .get("/workspaces/{workspaceId}/approvals/{approvalId}", WORKSPACE_ID, approvalId)
        .then()
            .statusCode(200)
            .body("id", equalTo(approvalId))
            .body("strategyId", equalTo(strategyId))
            .body("status", equalTo("PENDING"))
            .body("strategySnapshot", notNullValue())
            .body("strategySnapshot.content", notNullValue());
    }

    @Test
    @Order(6)
    @DisplayName("P1-033: Non-reviewer cannot approve strategy")
    void shouldRejectNonReviewerApproval() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();

        String requestBody = mapper.writeValueAsString(Map.of(
            "decision", "APPROVE",
            "comment", "Looks good"
        ));

        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", idempotencyKey)
            .header("Authorization", AUTH_TOKEN) // Regular user token, not reviewer
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/workspaces/{workspaceId}/approvals/{approvalId}/decide", WORKSPACE_ID, approvalId)
        .then()
            .statusCode(403)
            .body("error", equalTo("FORBIDDEN"))
            .body("message", containsString("reviewer"));
    }

    @Test
    @Order(7)
    @DisplayName("P1-033: Reviewer can approve strategy")
    void shouldAllowReviewerToApprove() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();

        String requestBody = mapper.writeValueAsString(Map.of(
            "decision", "APPROVE",
            "comment", "Strategy aligns with campaign objectives. Approved.",
            "budgetVerified", true
        ));

        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", idempotencyKey)
            .header("Authorization", REVIEWER_TOKEN)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/workspaces/{workspaceId}/approvals/{approvalId}/decide", WORKSPACE_ID, approvalId)
        .then()
            .statusCode(200)
            .body("approvalId", equalTo(approvalId))
            .body("status", equalTo("APPROVED"))
            .body("decidedBy", notNullValue())
            .body("decidedAt", notNullValue())
            .body("comment", equalTo("Strategy aligns with campaign objectives. Approved."));
    }

    @Test
    @Order(8)
    @DisplayName("P1-033: Strategy status changes to APPROVED after approval")
    void shouldShowStrategyAsApproved() {
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("Authorization", AUTH_TOKEN)
        .when()
            .get("/workspaces/{workspaceId}/strategies/{strategyId}", WORKSPACE_ID, strategyId)
        .then()
            .statusCode(200)
            .body("id", equalTo(strategyId))
            .body("status", equalTo("APPROVED"))
            .body("approvalId", equalTo(approvalId))
            .body("approvedAt", notNullValue())
            .body("approvedBy", notNullValue());
    }

    @Test
    @Order(9)
    @DisplayName("P1-033: Approved strategy appears in AI action log")
    void shouldHaveAuditTrailInAiActionLog() {
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("Authorization", AUTH_TOKEN)
        .when()
            .get("/workspaces/{workspaceId}/ai-actions?entityType=strategy&entityId={strategyId}",
                WORKSPACE_ID, strategyId)
        .then()
            .statusCode(200)
            .body("items", hasSize(greaterThanOrEqualTo(1)))
            .body("items[0].action", equalTo("STRATEGY_GENERATED"))
            .body("items[0].entityId", equalTo(strategyId))
            .body("items[0].tenantId", equalTo(TENANT_ID));
    }

    @Test
    @Order(10)
    @DisplayName("P1-033: Full strategy lifecycle with rejection path")
    void shouldHandleStrategyRejection() throws Exception {
        // Create a new strategy for rejection test
        String generateKey = UUID.randomUUID().toString();
        String generateBody = mapper.writeValueAsString(Map.of(
            "campaignId", "campaign-456",
            "goals", new String[]{"retention"},
            "budget", 25000
        ));

        String rejectStrategyId = given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", generateKey)
            .header("Authorization", AUTH_TOKEN)
            .contentType("application/json")
            .body(generateBody)
        .when()
            .post("/workspaces/{workspaceId}/strategies/generate", WORKSPACE_ID)
        .then()
            .statusCode(201)
            .extract()
            .jsonPath()
            .getString("id");

        // Submit for approval
        String submitKey = UUID.randomUUID().toString();
        String rejectApprovalId = given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", submitKey)
            .header("Authorization", AUTH_TOKEN)
            .contentType("application/json")
            .body(mapper.writeValueAsString(Map.of("justification", "Test rejection flow")))
        .when()
            .post("/workspaces/{workspaceId}/strategies/{strategyId}/submit", WORKSPACE_ID, rejectStrategyId)
        .then()
            .statusCode(201)
            .extract()
            .jsonPath()
            .getString("approvalId");

        // Reject the strategy
        String rejectKey = UUID.randomUUID().toString();
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", rejectKey)
            .header("Authorization", REVIEWER_TOKEN)
            .contentType("application/json")
            .body(mapper.writeValueAsString(Map.of(
                "decision", "REJECT",
                "comment", "Budget allocation needs revision. Please resubmit."
            )))
        .when()
            .post("/workspaces/{workspaceId}/approvals/{approvalId}/decide", WORKSPACE_ID, rejectApprovalId)
        .then()
            .statusCode(200)
            .body("status", equalTo("REJECTED"));

        // Verify strategy shows rejected status
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("Authorization", AUTH_TOKEN)
        .when()
            .get("/workspaces/{workspaceId}/strategies/{strategyId}", WORKSPACE_ID, rejectStrategyId)
        .then()
            .statusCode(200)
            .body("status", equalTo("REJECTED"))
            .body("rejectionReason", equalTo("Budget allocation needs revision. Please resubmit."));
    }

    @Test
    @DisplayName("P1-033: Strategy generation requires valid campaign")
    void shouldRejectStrategyGenerationForInvalidCampaign() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();

        String requestBody = mapper.writeValueAsString(Map.of(
            "campaignId", "non-existent-campaign",
            "goals", new String[]{"awareness"}
        ));

        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", idempotencyKey)
            .header("Authorization", AUTH_TOKEN)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/workspaces/{workspaceId}/strategies/generate", WORKSPACE_ID)
        .then()
            .statusCode(404)
            .body("error", equalTo("NOT_FOUND"))
            .body("message", containsString("campaign"));
    }

    @Test
    @DisplayName("P1-033: Cross-tenant strategy access is blocked")
    void shouldBlockCrossTenantStrategyAccess() {
        given()
            .header("X-Tenant-ID", "different-tenant")
            .header("Authorization", AUTH_TOKEN)
        .when()
            .get("/workspaces/{workspaceId}/strategies/{strategyId}", WORKSPACE_ID, strategyId)
        .then()
            .statusCode(404); // Should not find strategy in different tenant
    }
}

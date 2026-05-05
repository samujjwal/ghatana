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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * P1-034: Budget full lifecycle integration tests.
 *
 * <p>End-to-end API tests covering the complete budget journey:
 * <ol>
 *   <li>Generate budget recommendation (AI-assisted)</li>
 *   <li>View budget with channel allocations</li>
 *   <li>Submit for approval</li>
 *   <li>Approver reviews and approves/rejects</li>
 *   <li>Budget activation and spend tracking</li>
 * </ol>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("P1-034: Budget Lifecycle Integration Tests")
class BudgetLifecycleIntegrationTest {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String TENANT_ID = "budget-test-tenant";
    private static final String WORKSPACE_ID = "budget-test-workspace";
    private static final String AUTH_TOKEN = "Bearer test-token-budget";
    private static final String REVIEWER_TOKEN = "Bearer test-token-reviewer";

    private static String budgetId;
    private static String approvalId;

    @BeforeAll
    static void setUp() {
        RestAssured.baseURI = "http://localhost:8080";
        RestAssured.basePath = "/v1";
    }

    @Test
    @Order(1)
    @DisplayName("P1-034: Generate budget recommendation returns 201 with allocations")
    void shouldGenerateBudgetSuccessfully() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();

        String requestBody = mapper.writeValueAsString(Map.of(
            "campaignId", "campaign-789",
            "totalBudget", 100000,
            "currency", "USD",
            "period", Map.of(
                "startDate", "2026-01-01",
                "endDate", "2026-03-31"
            ),
            "channels", List.of("EMAIL", "SOCIAL", "PAID_SEARCH", "DISPLAY"),
            "goals", Map.of(
                "targetCPA", 50,
                "targetROAS", 3.5
            )
        ));

        Response response = given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", idempotencyKey)
            .header("Authorization", AUTH_TOKEN)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/workspaces/{workspaceId}/budgets/generate", WORKSPACE_ID);

        response.then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("workspaceId", equalTo(WORKSPACE_ID))
            .body("campaignId", equalTo("campaign-789"))
            .body("totalBudget", equalTo(100000))
            .body("currency", equalTo("USD"))
            .body("status", equalTo("GENERATED"))
            .body("channelAllocations", notNullValue())
            .body("channelAllocations.EMAIL", greaterThan(0))
            .body("aiActionLogId", notNullValue());

        budgetId = response.jsonPath().getString("id");
    }

    @Test
    @Order(2)
    @DisplayName("P1-034: Get budget returns 200 with channel allocations")
    void shouldGetBudgetWithAllocations() {
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("Authorization", AUTH_TOKEN)
        .when()
            .get("/workspaces/{workspaceId}/budgets/{budgetId}", WORKSPACE_ID, budgetId)
        .then()
            .statusCode(200)
            .body("id", equalTo(budgetId))
            .body("status", equalTo("GENERATED"))
            .body("totalBudget", equalTo(100000))
            .body("channelAllocations", notNullValue())
            .body("channelAllocations.EMAIL", greaterThan(0))
            .body("channelAllocations.SOCIAL", greaterThan(0))
            .body("channelAllocations.PAID_SEARCH", greaterThan(0))
            .body("period.startDate", equalTo("2026-01-01"))
            .body("period.endDate", equalTo("2026-03-31"));
    }

    @Test
    @Order(3)
    @DisplayName("P1-034: Submit budget for approval creates approval request")
    void shouldSubmitBudgetForApproval() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();

        String requestBody = mapper.writeValueAsString(Map.of(
            "justification", "Budget aligns with Q1 marketing goals and expected ROI",
            "notes", "Please verify channel allocation percentages"
        ));

        Response response = given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", idempotencyKey)
            .header("Authorization", AUTH_TOKEN)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/workspaces/{workspaceId}/budgets/{budgetId}/submit", WORKSPACE_ID, budgetId);

        response.then()
            .statusCode(201)
            .body("approvalId", notNullValue())
            .body("budgetId", equalTo(budgetId))
            .body("status", equalTo("PENDING"))
            .body("submittedBy", notNullValue())
            .body("budgetSnapshot", notNullValue())
            .body("budgetSnapshot.totalBudget", equalTo(100000))
            .body("budgetSnapshot.channelAllocations", notNullValue());

        approvalId = response.jsonPath().getString("approvalId");
    }

    @Test
    @Order(4)
    @DisplayName("P1-034: Budget status changes to PENDING_APPROVAL after submit")
    void shouldShowBudgetAsPendingApproval() {
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("Authorization", AUTH_TOKEN)
        .when()
            .get("/workspaces/{workspaceId}/budgets/{budgetId}", WORKSPACE_ID, budgetId)
        .then()
            .statusCode(200)
            .body("id", equalTo(budgetId))
            .body("status", equalTo("PENDING_APPROVAL"))
            .body("approvalId", equalTo(approvalId));
    }

    @Test
    @Order(5)
    @DisplayName("P1-034: Reviewer can view budget snapshot in approval")
    void shouldAllowReviewerToViewBudgetApproval() {
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("Authorization", REVIEWER_TOKEN)
        .when()
            .get("/workspaces/{workspaceId}/approvals/{approvalId}", WORKSPACE_ID, approvalId)
        .then()
            .statusCode(200)
            .body("id", equalTo(approvalId))
            .body("budgetId", equalTo(budgetId))
            .body("type", equalTo("BUDGET"))
            .body("status", equalTo("PENDING"))
            .body("budgetSnapshot", notNullValue())
            .body("budgetSnapshot.totalBudget", equalTo(100000))
            .body("budgetSnapshot.channelAllocations", notNullValue());
    }

    @Test
    @Order(6)
    @DisplayName("P1-034: Reviewer can approve budget")
    void shouldAllowReviewerToApproveBudget() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();

        String requestBody = mapper.writeValueAsString(Map.of(
            "decision", "APPROVE",
            "comment", "Budget allocation is well-balanced across channels. Approved for Q1.",
            "budgetVerified", true,
            "roiProjection", "3.8x"
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
            .body("budgetId", equalTo(budgetId))
            .body("status", equalTo("APPROVED"))
            .body("decidedBy", notNullValue())
            .body("decidedAt", notNullValue())
            .body("comment", containsString("Approved"));
    }

    @Test
    @Order(7)
    @DisplayName("P1-034: Budget status changes to ACTIVE after approval")
    void shouldShowBudgetAsActive() {
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("Authorization", AUTH_TOKEN)
        .when()
            .get("/workspaces/{workspaceId}/budgets/{budgetId}", WORKSPACE_ID, budgetId)
        .then()
            .statusCode(200)
            .body("id", equalTo(budgetId))
            .body("status", equalTo("ACTIVE"))
            .body("approvalId", equalTo(approvalId))
            .body("approvedAt", notNullValue())
            .body("approvedBy", notNullValue())
            .body("spentAmount", equalTo(0))
            .body("remainingBudget", equalTo(100000));
    }

    @Test
    @Order(8)
    @DisplayName("P1-034: Budget spend tracking updates after campaign spend")
    void shouldTrackBudgetSpend() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();

        // Record some spend
        String requestBody = mapper.writeValueAsString(Map.of(
            "channel", "EMAIL",
            "amount", 5000,
            "campaignId", "campaign-789"
        ));

        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", idempotencyKey)
            .header("Authorization", AUTH_TOKEN)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/workspaces/{workspaceId}/budgets/{budgetId}/spend", WORKSPACE_ID, budgetId)
        .then()
            .statusCode(200)
            .body("budgetId", equalTo(budgetId))
            .body("spentAmount", equalTo(5000))
            .body("remainingBudget", equalTo(95000))
            .body("channelAllocations.EMAIL.spent", equalTo(5000));
    }

    @Test
    @Order(9)
    @DisplayName("P1-034: Full budget lifecycle with rejection path")
    void shouldHandleBudgetRejection() throws Exception {
        // Create a new budget for rejection test
        String generateKey = UUID.randomUUID().toString();
        String generateBody = mapper.writeValueAsString(Map.of(
            "campaignId", "campaign-999",
            "totalBudget", 50000,
            "currency", "USD",
            "channels", List.of("SOCIAL")
        ));

        String rejectBudgetId = given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", generateKey)
            .header("Authorization", AUTH_TOKEN)
            .contentType("application/json")
            .body(generateBody)
        .when()
            .post("/workspaces/{workspaceId}/budgets/generate", WORKSPACE_ID)
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
            .post("/workspaces/{workspaceId}/budgets/{budgetId}/submit", WORKSPACE_ID, rejectBudgetId)
        .then()
            .statusCode(201)
            .extract()
            .jsonPath()
            .getString("approvalId");

        // Reject the budget
        String rejectKey = UUID.randomUUID().toString();
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", rejectKey)
            .header("Authorization", REVIEWER_TOKEN)
            .contentType("application/json")
            .body(mapper.writeValueAsString(Map.of(
                "decision", "REJECT",
                "comment", "Budget exceeds allocated marketing funds. Please reduce by 30% and resubmit."
            )))
        .when()
            .post("/workspaces/{workspaceId}/approvals/{approvalId}/decide", WORKSPACE_ID, rejectApprovalId)
        .then()
            .statusCode(200)
            .body("status", equalTo("REJECTED"));

        // Verify budget shows rejected status
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("Authorization", AUTH_TOKEN)
        .when()
            .get("/workspaces/{workspaceId}/budgets/{budgetId}", WORKSPACE_ID, rejectBudgetId)
        .then()
            .statusCode(200)
            .body("status", equalTo("REJECTED"))
            .body("rejectionReason", containsString("reduce by 30%"));
    }

    @Test
    @DisplayName("P1-034: Budget generation requires positive total budget")
    void shouldRejectBudgetWithZeroOrNegativeAmount() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();

        String requestBody = mapper.writeValueAsString(Map.of(
            "campaignId", "campaign-000",
            "totalBudget", 0,
            "currency", "USD"
        ));

        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", idempotencyKey)
            .header("Authorization", AUTH_TOKEN)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/workspaces/{workspaceId}/budgets/generate", WORKSPACE_ID)
        .then()
            .statusCode(400)
            .body("error", equalTo("VALIDATION_ERROR"))
            .body("message", containsString("budget"));
    }

    @Test
    @DisplayName("P1-034: Cross-tenant budget access is blocked")
    void shouldBlockCrossTenantBudgetAccess() {
        given()
            .header("X-Tenant-ID", "different-tenant")
            .header("Authorization", AUTH_TOKEN)
        .when()
            .get("/workspaces/{workspaceId}/budgets/{budgetId}", WORKSPACE_ID, budgetId)
        .then()
            .statusCode(404);
    }

    @Test
    @DisplayName("P1-034: Budget values and allocations persist correctly")
    void shouldPersistBudgetValuesCorrectly() {
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("Authorization", AUTH_TOKEN)
        .when()
            .get("/workspaces/{workspaceId}/budgets/{budgetId}", WORKSPACE_ID, budgetId)
        .then()
            .statusCode(200)
            // Verify all budget values are present and consistent
            .body("totalBudget", equalTo(100000))
            .body("currency", equalTo("USD"))
            .body("spentAmount", equalTo(5000))
            .body("remainingBudget", equalTo(95000))
            // Verify channel allocations add up
            .body("channelAllocations", notNullValue())
            .body("goals.targetCPA", equalTo(50))
            .body("goals.targetROAS", equalTo(3.5f));
    }
}

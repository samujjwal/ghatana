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
 * P1-043: Changed-flow API integration tests.
 *
 * <p>Comprehensive API tests covering all "changed" workflows:
 * <ul>
 *   <li>Campaign modification after approval</li>
 *   <li>Budget changes requiring re-approval</li>
 *   <li>Strategy updates and version tracking</li>
 *   <li>Approval workflow for modifications</li>
 *   <li>Change history and audit trails</li>
 *   <li>Rollback scenarios</li>
 * </ul>
 *
 * <p>Tests verify that modifications trigger proper approval workflows,
 * maintain version history, and provide change visibility.</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("P1-043: Changed-Flow API Integration Tests")
class ChangedFlowIntegrationTest {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String TENANT_ID = "changed-flow-tenant";
    private static final String WORKSPACE_ID = "changed-flow-workspace";
    private static final String AUTH_TOKEN = "Bearer test-token-changed";
    private static final String REVIEWER_TOKEN = "Bearer test-token-reviewer";

    private static String campaignId;
    private static String originalBudgetId;
    private static String modifiedBudgetId;
    private static String originalStrategyId;
    private static String changeApprovalId;

    @BeforeAll
    static void setUp() {
        RestAssured.baseURI = "http://localhost:8080";
        RestAssured.basePath = "/v1";
    }

    @Test
    @Order(1)
    @DisplayName("P1-043: Create initial campaign for change flow testing")
    void shouldCreateInitialCampaign() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();

        String requestBody = mapper.writeValueAsString(Map.of(
            "name", "Change Flow Test Campaign",
            "description", "Testing modification workflows",
            "status", "DRAFT",
            "goals", List.of("AWARENESS", "CONVERSION"),
            "targetAudience", Map.of(
                "ageRange", "25-54",
                "gender", "ALL"
            ),
            "budget", Map.of(
                "totalBudget", 100000,
                "currency", "USD"
            )
        ));

        Response response = given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", idempotencyKey)
            .header("Authorization", AUTH_TOKEN)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/workspaces/{workspaceId}/campaigns", WORKSPACE_ID);

        response.then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("name", equalTo("Change Flow Test Campaign"))
            .body("status", equalTo("DRAFT"))
            .body("version", equalTo(1));

        campaignId = response.jsonPath().getString("id");
    }

    @Test
    @Order(2)
    @DisplayName("P1-043: Generate and approve initial strategy")
    void shouldGenerateAndApproveInitialStrategy() throws Exception {
        // Generate strategy
        String generateKey = UUID.randomUUID().toString();
        Response generateResponse = given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", generateKey)
            .header("Authorization", AUTH_TOKEN)
            .contentType("application/json")
            .body(mapper.writeValueAsString(Map.of(
                "campaignId", campaignId,
                "goals", new String[]{"awareness"},
                "budget", 100000
            )))
        .when()
            .post("/workspaces/{workspaceId}/strategies/generate", WORKSPACE_ID);

        generateResponse.then()
            .statusCode(201);

        originalStrategyId = generateResponse.jsonPath().getString("id");

        // Submit for approval
        String submitKey = UUID.randomUUID().toString();
        Response submitResponse = given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", submitKey)
            .header("Authorization", AUTH_TOKEN)
            .contentType("application/json")
            .body(mapper.writeValueAsString(Map.of(
                "justification", "Initial strategy for campaign"
            )))
        .when()
            .post("/workspaces/{workspaceId}/strategies/{strategyId}/submit", WORKSPACE_ID, originalStrategyId);

        submitResponse.then()
            .statusCode(201);

        String approvalId = submitResponse.jsonPath().getString("approvalId");

        // Approve strategy
        String approveKey = UUID.randomUUID().toString();
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", approveKey)
            .header("Authorization", REVIEWER_TOKEN)
            .contentType("application/json")
            .body(mapper.writeValueAsString(Map.of(
                "decision", "APPROVE",
                "comment", "Strategy approved"
            )))
        .when()
            .post("/workspaces/{workspaceId}/approvals/{approvalId}/decide", WORKSPACE_ID, approvalId)
        .then()
            .statusCode(200)
            .body("status", equalTo("APPROVED"));
    }

    @Test
    @Order(3)
    @DisplayName("P1-043: Generate and approve initial budget")
    void shouldGenerateAndApproveInitialBudget() throws Exception {
        // Generate budget
        String generateKey = UUID.randomUUID().toString();
        Response generateResponse = given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", generateKey)
            .header("Authorization", AUTH_TOKEN)
            .contentType("application/json")
            .body(mapper.writeValueAsString(Map.of(
                "campaignId", campaignId,
                "totalBudget", 100000,
                "currency", "USD"
            )))
        .when()
            .post("/workspaces/{workspaceId}/budgets/generate", WORKSPACE_ID);

        generateResponse.then()
            .statusCode(201);

        originalBudgetId = generateResponse.jsonPath().getString("id");

        // Submit for approval
        String submitKey = UUID.randomUUID().toString();
        Response submitResponse = given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", submitKey)
            .header("Authorization", AUTH_TOKEN)
            .contentType("application/json")
            .body(mapper.writeValueAsString(Map.of(
                "justification", "Initial budget for campaign"
            )))
        .when()
            .post("/workspaces/{workspaceId}/budgets/{budgetId}/submit", WORKSPACE_ID, originalBudgetId);

        submitResponse.then()
            .statusCode(201);

        String approvalId = submitResponse.jsonPath().getString("approvalId");

        // Approve budget
        String approveKey = UUID.randomUUID().toString();
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", approveKey)
            .header("Authorization", REVIEWER_TOKEN)
            .contentType("application/json")
            .body(mapper.writeValueAsString(Map.of(
                "decision", "APPROVE",
                "comment", "Budget approved"
            )))
        .when()
            .post("/workspaces/{workspaceId}/approvals/{approvalId}/decide", WORKSPACE_ID, approvalId)
        .then()
            .statusCode(200)
            .body("status", equalTo("APPROVED"));
    }

    @Test
    @Order(4)
    @DisplayName("P1-043: Campaign modification triggers change approval workflow")
    void shouldTriggerChangeApprovalForCampaignModification() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();

        String requestBody = mapper.writeValueAsString(Map.of(
            "name", "Change Flow Test Campaign - Modified",
            "description", "Updated description for testing",
            "modificationReason", "Updated campaign name to better reflect goals"
        ));

        Response response = given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", idempotencyKey)
            .header("Authorization", AUTH_TOKEN)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .put("/workspaces/{workspaceId}/campaigns/{campaignId}", WORKSPACE_ID, campaignId);

        response.then()
            .statusCode(200)
            .body("id", equalTo(campaignId))
            .body("name", equalTo("Change Flow Test Campaign - Modified"))
            .body("status", equalTo("MODIFICATION_PENDING"))
            .body("version", equalTo(2))
            .body("pendingChangeId", notNullValue())
            .body("requiresApproval", equalTo(true));

        String changeId = response.jsonPath().getString("pendingChangeId");

        // Verify change request was created
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("Authorization", AUTH_TOKEN)
        .when()
            .get("/workspaces/{workspaceId}/campaigns/{campaignId}/changes/{changeId}",
                WORKSPACE_ID, campaignId, changeId)
        .then()
            .statusCode(200)
            .body("id", equalTo(changeId))
            .body("campaignId", equalTo(campaignId))
            .body("type", equalTo("MODIFICATION"))
            .body("status", equalTo("PENDING_APPROVAL"))
            .body("changes.name.from", equalTo("Change Flow Test Campaign"))
            .body("changes.name.to", equalTo("Change Flow Test Campaign - Modified"))
            .body("requiresApproval", equalTo(true));

        changeApprovalId = changeId;
    }

    @Test
    @Order(5)
    @DisplayName("P1-043: Budget modification requires re-approval")
    void shouldRequireReApprovalForBudgetModification() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();

        // Try to modify budget
        String requestBody = mapper.writeValueAsString(Map.of(
            "totalBudget", 150000,
            "modificationReason", "Increased budget for expanded reach"
        ));

        Response response = given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", idempotencyKey)
            .header("Authorization", AUTH_TOKEN)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .put("/workspaces/{workspaceId}/budgets/{budgetId}", WORKSPACE_ID, originalBudgetId);

        response.then()
            .statusCode(202) // Accepted but pending approval
            .body("budgetId", equalTo(originalBudgetId))
            .body("status", equalTo("MODIFICATION_PENDING"))
            .body("newBudget.amount", equalTo(150000))
            .body("oldBudget.amount", equalTo(100000))
            .body("approvalId", notNullValue());

        modifiedBudgetId = response.jsonPath().getString("newBudget.id");
        String budgetApprovalId = response.jsonPath().getString("approvalId");

        // Verify budget modification needs approval
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("Authorization", AUTH_TOKEN)
        .when()
            .get("/workspaces/{workspaceId}/approvals/{approvalId}", WORKSPACE_ID, budgetApprovalId)
        .then()
            .statusCode(200)
            .body("type", equalTo("BUDGET_MODIFICATION"))
            .body("status", equalTo("PENDING"))
            .body("previousAmount", equalTo(100000))
            .body("proposedAmount", equalTo(150000));
    }

    @Test
    @Order(6)
    @DisplayName("P1-043: Approve campaign modification")
    void shouldApproveCampaignModification() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();

        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", idempotencyKey)
            .header("Authorization", REVIEWER_TOKEN)
            .contentType("application/json")
            .body(mapper.writeValueAsString(Map.of(
                "decision", "APPROVE",
                "comment", "Campaign modification approved"
            )))
        .when()
            .post("/workspaces/{workspaceId}/approvals/{approvalId}/decide", WORKSPACE_ID, changeApprovalId)
        .then()
            .statusCode(200)
            .body("status", equalTo("APPROVED"));

        // Verify campaign is updated
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("Authorization", AUTH_TOKEN)
        .when()
            .get("/workspaces/{workspaceId}/campaigns/{campaignId}", WORKSPACE_ID, campaignId)
        .then()
            .statusCode(200)
            .body("id", equalTo(campaignId))
            .body("name", equalTo("Change Flow Test Campaign - Modified"))
            .body("status", equalTo("APPROVED"))
            .body("version", equalTo(2))
            .body("pendingChangeId", nullValue());
    }

    @Test
    @Order(7)
    @DisplayName("P1-043: Change history tracks all modifications")
    void shouldTrackChangeHistory() {
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("Authorization", AUTH_TOKEN)
        .when()
            .get("/workspaces/{workspaceId}/campaigns/{campaignId}/history", WORKSPACE_ID, campaignId)
        .then()
            .statusCode(200)
            .body("items", hasSize(greaterThanOrEqualTo(2)))
            .body("items[0].type", equalTo("MODIFICATION"))
            .body("items[0].status", equalTo("APPROVED"))
            .body("items[0].changes.name", notNullValue())
            .body("items[1].type", equalTo("CREATION"));
    }

    @Test
    @Order(8)
    @DisplayName("P1-043: Reject campaign modification restores previous state")
    void shouldRestorePreviousStateOnRejection() throws Exception {
        // Create a new modification
        String idempotencyKey = UUID.randomUUID().toString();

        Response modifyResponse = given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", idempotencyKey)
            .header("Authorization", AUTH_TOKEN)
            .contentType("application/json")
            .body(mapper.writeValueAsString(Map.of(
                "name", "Change Flow Test Campaign - Rejected Change",
                "modificationReason", "This change should be rejected"
            )))
        .when()
            .put("/workspaces/{workspaceId}/campaigns/{campaignId}", WORKSPACE_ID, campaignId);

        modifyResponse.then().statusCode(200);

        String rejectedChangeId = modifyResponse.jsonPath().getString("pendingChangeId");

        // Reject the change
        String rejectKey = UUID.randomUUID().toString();
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", rejectKey)
            .header("Authorization", REVIEWER_TOKEN)
            .contentType("application/json")
            .body(mapper.writeValueAsString(Map.of(
                "decision", "REJECT",
                "comment", "Rejected: name change not aligned with brand guidelines"
            )))
        .when()
            .post("/workspaces/{workspaceId}/approvals/{approvalId}/decide", WORKSPACE_ID, rejectedChangeId)
        .then()
            .statusCode(200)
            .body("status", equalTo("REJECTED"));

        // Verify campaign name is restored to approved version
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("Authorization", AUTH_TOKEN)
        .when()
            .get("/workspaces/{workspaceId}/campaigns/{campaignId}", WORKSPACE_ID, campaignId)
        .then()
            .statusCode(200)
            .body("name", equalTo("Change Flow Test Campaign - Modified")) // Previous approved name
            .body("status", equalTo("APPROVED"));
    }

    @Test
    @DisplayName("P1-043: Unapproved modifications cannot be published")
    void shouldBlockPublishWithPendingChanges() throws Exception {
        // Create a modification but don't approve
        String idempotencyKey = UUID.randomUUID().toString();

        Response modifyResponse = given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", idempotencyKey)
            .header("Authorization", AUTH_TOKEN)
            .contentType("application/json")
            .body(mapper.writeValueAsString(Map.of(
                "description", "Pending change description",
                "modificationReason", "Testing publish block"
            )))
        .when()
            .put("/workspaces/{workspaceId}/campaigns/{campaignId}", WORKSPACE_ID, campaignId);

        String pendingChangeId = modifyResponse.jsonPath().getString("pendingChangeId");

        // Try to publish - should fail
        String publishKey = UUID.randomUUID().toString();
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", publishKey)
            .header("Authorization", AUTH_TOKEN)
        .when()
            .post("/workspaces/{workspaceId}/campaigns/{campaignId}/publish", WORKSPACE_ID, campaignId)
        .then()
            .statusCode(409)
            .body("error", equalTo("PENDING_CHANGES"))
            .body("message", containsString("pending"));
    }

    @Test
    @DisplayName("P1-043: Change diff shows exact modifications")
    void shouldShowChangeDiff() {
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("Authorization", AUTH_TOKEN)
        .when()
            .get("/workspaces/{workspaceId}/campaigns/{campaignId}/changes/{changeId}/diff",
                WORKSPACE_ID, campaignId, changeApprovalId)
        .then()
            .statusCode(200)
            .body("added", notNullValue())
            .body("removed", notNullValue())
            .body("modified", notNullValue())
            .body("modified.name.from", equalTo("Change Flow Test Campaign"))
            .body("modified.name.to", equalTo("Change Flow Test Campaign - Modified"));
    }
}

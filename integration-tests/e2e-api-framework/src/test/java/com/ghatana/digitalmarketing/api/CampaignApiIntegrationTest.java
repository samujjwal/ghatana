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
 * P1-015: DMOS Campaign API Integration Tests.
 *
 * <p>Full end-to-end API tests against a running DMOS instance.
 * Tests happy paths and error scenarios including:</p>
 * <ul>
 *   <li>Full campaign lifecycle (create, list, get, launch, pause)</li>
 *   <li>Error handling with canonical error envelope</li>
 *   <li>Tenant isolation validation</li>
 *   <li>Idempotency enforcement</li>
 *   <li>Correlation ID propagation</li>
 * </ul>
 *
 * <p>Requires: DMOS application running on localhost:8080</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Campaign API Integration Tests (P1-015)")
class CampaignApiIntegrationTest {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String TENANT_ID = "integration-test-tenant";
    private static final String WORKSPACE_ID = "integration-test-workspace";
    private static final String AUTH_TOKEN = "Bearer test-token-integration";

    @BeforeAll
    static void setUp() {
        RestAssured.baseURI = "http://localhost:8080";
        RestAssured.basePath = "/v1";
    }

    @Test
    @Order(1)
    @DisplayName("P1-015: Create campaign returns 201 with valid data")
    void shouldCreateCampaignSuccessfully() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();

        String requestBody = mapper.writeValueAsString(Map.of(
            "name", "Integration Test Campaign",
            "type", "EMAIL"
        ));

        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", idempotencyKey)
            .header("X-Correlation-ID", correlationId)
            .header("Authorization", AUTH_TOKEN)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/workspaces/{workspaceId}/campaigns", WORKSPACE_ID)
        .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("workspaceId", equalTo(WORKSPACE_ID))
            .body("name", equalTo("Integration Test Campaign"))
            .body("type", equalTo("EMAIL"))
            .body("status", equalTo("DRAFT"))
            .body("createdBy", notNullValue())
            .body("createdAt", notNullValue())
            .body("updatedAt", notNullValue());
    }

    @Test
    @Order(2)
    @DisplayName("P1-015: Create campaign returns 400 without tenant header")
    void shouldRejectCreateWithoutTenantHeader() throws Exception {
        String requestBody = mapper.writeValueAsString(Map.of(
            "name", "Test Campaign",
            "type", "EMAIL"
        ));

        given()
            .header("X-Idempotency-Key", UUID.randomUUID().toString())
            .header("Authorization", AUTH_TOKEN)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/workspaces/{workspaceId}/campaigns", WORKSPACE_ID)
        .then()
            .statusCode(400)
            .body("error", equalTo("BAD_REQUEST"))
            .body("message", containsString("X-Tenant-ID"))
            .body("status", equalTo(400))
            .body("correlationId", notNullValue());
    }

    @Test
    @Order(3)
    @DisplayName("P1-015: Create campaign returns 400 without idempotency key")
    void shouldRejectCreateWithoutIdempotencyKey() throws Exception {
        String requestBody = mapper.writeValueAsString(Map.of(
            "name", "Test Campaign",
            "type", "EMAIL"
        ));

        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("Authorization", AUTH_TOKEN)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/workspaces/{workspaceId}/campaigns", WORKSPACE_ID)
        .then()
            .statusCode(400)
            .body("error", equalTo("BAD_REQUEST"))
            .body("message", containsString("X-Idempotency-Key"));
    }

    @Test
    @Order(4)
    @DisplayName("P1-015: Idempotent create returns same campaign on retry")
    void shouldBeIdempotentOnCreate() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();

        String requestBody = mapper.writeValueAsString(Map.of(
            "name", "Idempotent Test Campaign",
            "type", "SOCIAL"
        ));

        // First request
        Response first = given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", idempotencyKey)
            .header("Authorization", AUTH_TOKEN)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/workspaces/{workspaceId}/campaigns", WORKSPACE_ID);

        first.then().statusCode(201);
        String campaignId = first.jsonPath().getString("id");

        // Retry with same idempotency key
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", idempotencyKey)
            .header("Authorization", AUTH_TOKEN)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/workspaces/{workspaceId}/campaigns", WORKSPACE_ID)
        .then()
            .statusCode(201)
            .body("id", equalTo(campaignId)); // Same campaign returned
    }

    @Test
    @Order(5)
    @DisplayName("P1-015: List campaigns returns paginated results")
    void shouldListCampaignsPaginated() {
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("Authorization", AUTH_TOKEN)
        .when()
            .get("/workspaces/{workspaceId}/campaigns?limit=5&offset=0", WORKSPACE_ID)
        .then()
            .statusCode(200)
            .body("items", notNullValue())
            .body("count", greaterThanOrEqualTo(0))
            .body("offset", equalTo(0));
    }

    @Test
    @Order(6)
    @DisplayName("P1-015: List campaigns enforces limit bounds")
    void shouldEnforceListLimitBounds() {
        // Request limit over 100 should be clamped
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("Authorization", AUTH_TOKEN)
        .when()
            .get("/workspaces/{workspaceId}/campaigns?limit=500", WORKSPACE_ID)
        .then()
            .statusCode(200)
            .body("items.size()", lessThanOrEqualTo(100));
    }

    @Test
    @Order(7)
    @DisplayName("P1-015: Get campaign returns 200 when found")
    void shouldGetCampaignSuccessfully() throws Exception {
        // First create a campaign
        String idempotencyKey = UUID.randomUUID().toString();
        String requestBody = mapper.writeValueAsString(Map.of(
            "name", "Get Test Campaign",
            "type", "PAID_SEARCH"
        ));

        Response create = given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", idempotencyKey)
            .header("Authorization", AUTH_TOKEN)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/workspaces/{workspaceId}/campaigns", WORKSPACE_ID);

        String campaignId = create.jsonPath().getString("id");

        // Then get it
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("Authorization", AUTH_TOKEN)
        .when()
            .get("/workspaces/{workspaceId}/campaigns/{campaignId}", WORKSPACE_ID, campaignId)
        .then()
            .statusCode(200)
            .body("id", equalTo(campaignId))
            .body("name", equalTo("Get Test Campaign"))
            .body("type", equalTo("PAID_SEARCH"));
    }

    @Test
    @Order(8)
    @DisplayName("P1-015: Get campaign returns 404 when not found")
    void shouldReturn404ForNonExistentCampaign() {
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("Authorization", AUTH_TOKEN)
        .when()
            .get("/workspaces/{workspaceId}/campaigns/{campaignId}", WORKSPACE_ID, "non-existent-id")
        .then()
            .statusCode(404)
            .body("error", equalTo("NOT_FOUND"))
            .body("status", equalTo(404))
            .body("correlationId", notNullValue());
    }

    @Test
    @Order(9)
    @DisplayName("P1-015: Launch campaign transitions status to LAUNCHED")
    void shouldLaunchCampaignSuccessfully() throws Exception {
        // Create a campaign first
        String idempotencyKey = UUID.randomUUID().toString();
        String requestBody = mapper.writeValueAsString(Map.of(
            "name", "Launch Test Campaign",
            "type", "EMAIL"
        ));

        Response create = given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", idempotencyKey)
            .header("Authorization", AUTH_TOKEN)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/workspaces/{workspaceId}/campaigns", WORKSPACE_ID);

        String campaignId = create.jsonPath().getString("id");

        // Launch it
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", UUID.randomUUID().toString())
            .header("Authorization", AUTH_TOKEN)
        .when()
            .post("/workspaces/{workspaceId}/campaigns/{campaignId}/launch", WORKSPACE_ID, campaignId)
        .then()
            .statusCode(200)
            .body("id", equalTo(campaignId))
            .body("status", equalTo("LAUNCHED"));
    }

    @Test
    @Order(10)
    @DisplayName("P1-015: Launch campaign returns 409 for invalid state")
    void shouldReturn409ForInvalidLaunchState() throws Exception {
        // Create and launch a campaign
        String idempotencyKey = UUID.randomUUID().toString();
        String requestBody = mapper.writeValueAsString(Map.of(
            "name", "Double Launch Test",
            "type", "EMAIL"
        ));

        Response create = given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", idempotencyKey)
            .header("Authorization", AUTH_TOKEN)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/workspaces/{workspaceId}/campaigns", WORKSPACE_ID);

        String campaignId = create.jsonPath().getString("id");

        // Launch first time
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", UUID.randomUUID().toString())
            .header("Authorization", AUTH_TOKEN)
        .when()
            .post("/workspaces/{workspaceId}/campaigns/{campaignId}/launch", WORKSPACE_ID, campaignId);

        // Try to launch again (should fail with 409)
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", UUID.randomUUID().toString())
            .header("Authorization", AUTH_TOKEN)
        .when()
            .post("/workspaces/{workspaceId}/campaigns/{campaignId}/launch", WORKSPACE_ID, campaignId)
        .then()
            .statusCode(409)
            .body("error", equalTo("CONFLICT"))
            .body("status", equalTo(409));
    }

    @Test
    @Order(11)
    @DisplayName("P1-015: Pause campaign transitions status to PAUSED")
    void shouldPauseCampaignSuccessfully() throws Exception {
        // Create and launch a campaign
        String idempotencyKey = UUID.randomUUID().toString();
        String requestBody = mapper.writeValueAsString(Map.of(
            "name", "Pause Test Campaign",
            "type", "EMAIL"
        ));

        Response create = given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", idempotencyKey)
            .header("Authorization", AUTH_TOKEN)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/workspaces/{workspaceId}/campaigns", WORKSPACE_ID);

        String campaignId = create.jsonPath().getString("id");

        // Launch it
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", UUID.randomUUID().toString())
            .header("Authorization", AUTH_TOKEN)
        .when()
            .post("/workspaces/{workspaceId}/campaigns/{campaignId}/launch", WORKSPACE_ID, campaignId);

        // Pause it
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", UUID.randomUUID().toString())
            .header("Authorization", AUTH_TOKEN)
        .when()
            .post("/workspaces/{workspaceId}/campaigns/{campaignId}/pause", WORKSPACE_ID, campaignId)
        .then()
            .statusCode(200)
            .body("id", equalTo(campaignId))
            .body("status", equalTo("PAUSED"));
    }

    @Test
    @Order(12)
    @DisplayName("P1-015: Correlation ID is returned in error responses")
    void shouldReturnCorrelationIdInErrorResponse() {
        String correlationId = UUID.randomUUID().toString();

        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Correlation-ID", correlationId)
            .header("Authorization", AUTH_TOKEN)
        .when()
            .get("/workspaces/{workspaceId}/campaigns/{campaignId}", WORKSPACE_ID, "does-not-exist")
        .then()
            .statusCode(404)
            .body("correlationId", equalTo(correlationId));
    }

    @Test
    @Order(13)
    @DisplayName("P1-015: Tenant isolation - campaigns not visible across tenants")
    void shouldEnforceTenantIsolation() throws Exception {
        String otherTenant = "other-tenant-" + UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();

        String requestBody = mapper.writeValueAsString(Map.of(
            "name", "Tenant Isolation Test",
            "type", "EMAIL"
        ));

        // Create campaign in original tenant
        Response create = given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", idempotencyKey)
            .header("Authorization", AUTH_TOKEN)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/workspaces/{workspaceId}/campaigns", WORKSPACE_ID);

        String campaignId = create.jsonPath().getString("id");

        // Try to access from different tenant (should fail)
        given()
            .header("X-Tenant-ID", otherTenant)
            .header("Authorization", AUTH_TOKEN)
        .when()
            .get("/workspaces/{workspaceId}/campaigns/{campaignId}", WORKSPACE_ID, campaignId)
        .then()
            .statusCode(404); // Not visible to other tenant
    }

    @Test
    @Order(14)
    @DisplayName("P1-015: Full campaign lifecycle test")
    void shouldCompleteFullCampaignLifecycle() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();

        // 1. Create
        String requestBody = mapper.writeValueAsString(Map.of(
            "name", "Lifecycle Test Campaign",
            "type", "OMNICHANNEL"
        ));

        Response create = given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", idempotencyKey)
            .header("Authorization", AUTH_TOKEN)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/workspaces/{workspaceId}/campaigns", WORKSPACE_ID);

        create.then().statusCode(201);
        String campaignId = create.jsonPath().getString("id");

        // 2. List includes the campaign
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("Authorization", AUTH_TOKEN)
        .when()
            .get("/workspaces/{workspaceId}/campaigns", WORKSPACE_ID)
        .then()
            .statusCode(200)
            .body("items.id", hasItem(campaignId));

        // 3. Get returns the campaign
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("Authorization", AUTH_TOKEN)
        .when()
            .get("/workspaces/{workspaceId}/campaigns/{campaignId}", WORKSPACE_ID, campaignId)
        .then()
            .statusCode(200)
            .body("status", equalTo("DRAFT"));

        // 4. Launch
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", UUID.randomUUID().toString())
            .header("Authorization", AUTH_TOKEN)
        .when()
            .post("/workspaces/{workspaceId}/campaigns/{campaignId}/launch", WORKSPACE_ID, campaignId)
        .then()
            .statusCode(200)
            .body("status", equalTo("LAUNCHED"));

        // 5. Pause
        given()
            .header("X-Tenant-ID", TENANT_ID)
            .header("X-Idempotency-Key", UUID.randomUUID().toString())
            .header("Authorization", AUTH_TOKEN)
        .when()
            .post("/workspaces/{workspaceId}/campaigns/{campaignId}/pause", WORKSPACE_ID, campaignId)
        .then()
            .statusCode(200)
            .body("status", equalTo("PAUSED"));
    }
}

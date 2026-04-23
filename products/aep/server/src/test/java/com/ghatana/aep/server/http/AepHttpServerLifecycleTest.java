/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.server.http;

import com.ghatana.aep.Aep;
import com.ghatana.aep.AepEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for AEP HTTP lifecycle endpoints.
 *
 * <p>Starts a real {@link AepHttpServer} on a free port and exercises all
 * change approval ({@code /lifecycle/changes/**}) and recertification // GH-90000
 * ({@code /lifecycle/recertification/**}) endpoints, validating status codes, // GH-90000
 * JSON shape, and lifecycle state transitions (PENDING_REVIEW → APPROVED / // GH-90000
 * REJECTED / WITHDRAWN; PENDING → CERTIFIED / REVOKED).
 *
 * @doc.type class
 * @doc.purpose Integration tests for /lifecycle/** HTTP endpoints
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AepHttpServer – Lifecycle Endpoints")
class AepHttpServerLifecycleTest {

    private AepEngine engine;
    private AepHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000
    private String previousAuthDisabled;
    private String previousJwtSecret;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        previousAuthDisabled = System.getProperty("AEP_AUTH_DISABLED");
        previousJwtSecret = System.getProperty("AEP_JWT_SECRET");
        System.setProperty("AEP_AUTH_DISABLED", "true");
        System.clearProperty("AEP_JWT_SECRET");

        engine = Aep.forTesting(); // GH-90000
        port = findFreePort(); // GH-90000
        httpClient = HttpClient.newBuilder().build(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) server.stop(); // GH-90000
        if (engine != null) engine.close(); // GH-90000

        if (previousAuthDisabled == null) {
            System.clearProperty("AEP_AUTH_DISABLED");
        } else {
            System.setProperty("AEP_AUTH_DISABLED", previousAuthDisabled);
        }

        if (previousJwtSecret == null) {
            System.clearProperty("AEP_JWT_SECRET");
        } else {
            System.setProperty("AEP_JWT_SECRET", previousJwtSecret);
        }
    }

    // ==================== POST /lifecycle/changes ====================

    @Nested
    @DisplayName("POST /lifecycle/changes")
    class SubmitChangeTests {

        @Test
        @DisplayName("low-risk change (FEATURE_FLAG, risk=20) is auto-approved immediately")
        void autoApprovesLowRiskChange() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post("/lifecycle/changes", // GH-90000
                mapper.writeValueAsString(Map.of( // GH-90000
                    "tenantId", "tenant-1",
                    "requestingAgent", "agent-bootstrap",
                    "changeType", "FEATURE_FLAG",
                    "description", "Enable dark-mode feature flag"
                )));
            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000

            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("tenantId")).isEqualTo("tenant-1");
            assertThat(body.get("changeType")).isEqualTo("FEATURE_FLAG");
            assertThat(body.get("status")).isEqualTo("APPROVED");
            assertThat(body).containsKey("changeId");
            assertThat(body).containsKey("riskScore");
        }

        @Test
        @DisplayName("high-risk change (AGENT_DEPLOYMENT, risk=65) enters PENDING_REVIEW")
        void highRiskChangePendingReview() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post("/lifecycle/changes", // GH-90000
                mapper.writeValueAsString(Map.of( // GH-90000
                    "tenantId", "tenant-1",
                    "requestingAgent", "devops-agent",
                    "changeType", "AGENT_DEPLOYMENT",
                    "description", "Deploy new classifier agent"
                )));
            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000

            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("status")).isEqualTo("PENDING_REVIEW");
            assertThat(body).containsKey("changeId");
        }

        @Test
        @DisplayName("returns 400 when required fields are missing")
        void returns400WhenFieldsMissing() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post("/lifecycle/changes", // GH-90000
                mapper.writeValueAsString(Map.of( // GH-90000
                    "tenantId", "tenant-1",
                    "changeType", "FEATURE_FLAG"
                    // missing requestingAgent and description
                )));
            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        }

        @Test
        @DisplayName("returns 400 for unknown changeType")
        void returns400ForUnknownChangeType() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post("/lifecycle/changes", // GH-90000
                mapper.writeValueAsString(Map.of( // GH-90000
                    "tenantId", "tenant-1",
                    "requestingAgent", "agent-x",
                    "changeType", "UNKNOWN_CHANGE_TYPE_XYZ",
                    "description", "test"
                )));
            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        }
    }

    // ==================== GET /lifecycle/changes ====================

    @Nested
    @DisplayName("GET /lifecycle/changes")
    class ListPendingChangesTests {

        @Test
        @DisplayName("returns pending changes list for a tenant")
        void returnsPendingChanges() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            // Submit a high-risk (PENDING_REVIEW) change first // GH-90000
            post("/lifecycle/changes", // GH-90000
                mapper.writeValueAsString(Map.of( // GH-90000
                    "tenantId", "tenant-1",
                    "requestingAgent", "ops-agent",
                    "changeType", "AGENT_DEPLOYMENT",
                    "description", "Deploy summarizer"
                )));

            HttpResponse<String> resp = get("/lifecycle/changes?tenantId=tenant-1");
            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000

            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("tenantId")).isEqualTo("tenant-1");
            assertThat(body).containsKey("pending");
            assertThat(body).containsKey("count");

            @SuppressWarnings("unchecked")
            List<Object> pending = (List<Object>) body.get("pending");
            assertThat(pending).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("returns 400 when tenantId is missing")
        void returns400WhenTenantIdMissing() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = get("/lifecycle/changes");
            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        }
    }

    // ==================== GET /lifecycle/changes/:changeId ====================

    @Nested
    @DisplayName("GET /lifecycle/changes/:changeId")
    class GetChangeTests {

        @Test
        @DisplayName("returns 200 with change details for existing changeId")
        void returnsExistingChange() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> submitResp = post("/lifecycle/changes", // GH-90000
                mapper.writeValueAsString(Map.of( // GH-90000
                    "tenantId", "tenant-1",
                    "requestingAgent", "agent-x",
                    "changeType", "CONFIG_CHANGE",
                    "description", "Update rate limit"
                )));
            String changeId = (String) mapper.readValue(submitResp.body(), Map.class).get("changeId");

            HttpResponse<String> resp = get("/lifecycle/changes/" + changeId); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000

            Map<String, Object> body = mapper.readValue( // GH-90000
                resp.body(), // GH-90000
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {} // GH-90000
            );
            assertThat(body.get("changeId")).isEqualTo(changeId);
            assertThat(body.get("tenantId")).isEqualTo("tenant-1");
        }

        @Test
        @DisplayName("returns 404 for non-existent changeId")
        void returns404ForMissingChange() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = get("/lifecycle/changes/non-existent-change-id");
            assertThat(resp.statusCode()).isEqualTo(404); // GH-90000
        }
    }

    // ==================== POST /lifecycle/changes/:changeId/approve ====================

    @Nested
    @DisplayName("POST /lifecycle/changes/:changeId/approve")
    class ApproveChangeTests {

        @Test
        @DisplayName("approves a PENDING_REVIEW change, returns APPROVED status with reviewerId")
        void approvesPendingChange() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> submitResp = post("/lifecycle/changes", // GH-90000
                mapper.writeValueAsString(Map.of( // GH-90000
                    "tenantId", "tenant-1",
                    "requestingAgent", "devops",
                    "changeType", "AGENT_DEPLOYMENT",
                    "description", "Deploy audit agent"
                )));
            String changeId = (String) mapper.readValue(submitResp.body(), Map.class).get("changeId");

            HttpResponse<String> resp = post("/lifecycle/changes/" + changeId + "/approve", // GH-90000
                mapper.writeValueAsString(Map.of( // GH-90000
                    "reviewerId", "reviewer-001",
                    "notes", "Looks good, approved"
                )));
            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000

            Map<String, Object> body = mapper.readValue( // GH-90000
                resp.body(), // GH-90000
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {} // GH-90000
            );
            assertThat(body.get("status")).isEqualTo("APPROVED");
            assertThat(body.get("reviewerId")).isEqualTo("reviewer-001");
        }

        @Test
        @DisplayName("returns 400 when reviewerId is missing")
        void returns400WhenReviewerIdMissing() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> submitResp = post("/lifecycle/changes", // GH-90000
                mapper.writeValueAsString(Map.of( // GH-90000
                    "tenantId", "tenant-1",
                    "requestingAgent", "agent",
                    "changeType", "AGENT_DEPLOYMENT",
                    "description", "deploy"
                )));
            String changeId = (String) mapper.readValue(submitResp.body(), Map.class).get("changeId");

            HttpResponse<String> resp = post("/lifecycle/changes/" + changeId + "/approve", // GH-90000
                mapper.writeValueAsString(Map.of())); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        }

        @Test
        @DisplayName("returns 409 when change is already in a terminal state")
        void returns409WhenAlreadyApproved() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            // FEATURE_FLAG (risk=20 < threshold=60) is auto-approved on submit // GH-90000
            HttpResponse<String> submitResp = post("/lifecycle/changes", // GH-90000
                mapper.writeValueAsString(Map.of( // GH-90000
                    "tenantId", "tenant-1",
                    "requestingAgent", "agent",
                    "changeType", "FEATURE_FLAG",
                    "description", "enable dark mode"
                )));
            String changeId = (String) mapper.readValue(submitResp.body(), Map.class).get("changeId");

            // Attempting to approve an already-APPROVED change → 409
            HttpResponse<String> resp = post("/lifecycle/changes/" + changeId + "/approve", // GH-90000
                mapper.writeValueAsString(Map.of("reviewerId", "reviewer-002"))); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(409); // GH-90000
        }

        @Test
        @DisplayName("returns 404 for non-existent changeId")
        void returns404ForMissingChange() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post("/lifecycle/changes/no-such-id/approve", // GH-90000
                mapper.writeValueAsString(Map.of("reviewerId", "reviewer-001"))); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(404); // GH-90000
        }
    }

    // ==================== POST /lifecycle/changes/:changeId/reject ====================

    @Nested
    @DisplayName("POST /lifecycle/changes/:changeId/reject")
    class RejectChangeTests {

        @Test
        @DisplayName("rejects a PENDING_REVIEW change, returns REJECTED status")
        void rejectsPendingChange() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> submitResp = post("/lifecycle/changes", // GH-90000
                mapper.writeValueAsString(Map.of( // GH-90000
                    "tenantId", "tenant-1",
                    "requestingAgent", "devops",
                    "changeType", "PERMISSION_GRANT",
                    "description", "Grant elevated read access"
                )));
            String changeId = (String) mapper.readValue(submitResp.body(), Map.class).get("changeId");

            HttpResponse<String> resp = post("/lifecycle/changes/" + changeId + "/reject", // GH-90000
                mapper.writeValueAsString(Map.of( // GH-90000
                    "reviewerId", "security-reviewer",
                    "reason", "Violates least-privilege policy"
                )));
            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000

            Map<String, Object> body = mapper.readValue( // GH-90000
                resp.body(), // GH-90000
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {} // GH-90000
            );
            assertThat(body.get("status")).isEqualTo("REJECTED");
        }

        @Test
        @DisplayName("returns 400 when reason is missing")
        void returns400WhenReasonMissing() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> submitResp = post("/lifecycle/changes", // GH-90000
                mapper.writeValueAsString(Map.of( // GH-90000
                    "tenantId", "tenant-1",
                    "requestingAgent", "agent",
                    "changeType", "PERMISSION_GRANT",
                    "description", "grant elevated read"
                )));
            String changeId = (String) mapper.readValue(submitResp.body(), Map.class).get("changeId");

            HttpResponse<String> resp = post("/lifecycle/changes/" + changeId + "/reject", // GH-90000
                mapper.writeValueAsString(Map.of("reviewerId", "reviewer-1"))); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        }
    }

    // ==================== POST /lifecycle/changes/:changeId/withdraw ====================

    @Nested
    @DisplayName("POST /lifecycle/changes/:changeId/withdraw")
    class WithdrawChangeTests {

        @Test
        @DisplayName("withdraws a PENDING_REVIEW change, returns WITHDRAWN status")
        void withdrawsPendingChange() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            // TOOL_REGISTRATION risk=60 >= threshold=60 → PENDING_REVIEW
            HttpResponse<String> submitResp = post("/lifecycle/changes", // GH-90000
                mapper.writeValueAsString(Map.of( // GH-90000
                    "tenantId", "tenant-1",
                    "requestingAgent", "ops-agent",
                    "changeType", "TOOL_REGISTRATION",
                    "description", "Register new search tool"
                )));
            String changeId = (String) mapper.readValue(submitResp.body(), Map.class).get("changeId");

            HttpResponse<String> resp = post("/lifecycle/changes/" + changeId + "/withdraw", "{}"); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000

            Map<String, Object> body = mapper.readValue( // GH-90000
                resp.body(), // GH-90000
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {} // GH-90000
            );
            assertThat(body.get("status")).isEqualTo("WITHDRAWN");
        }

        @Test
        @DisplayName("returns 409 when withdrawing an already-approved change")
        void returns409WhenWithdrawingApproved() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            // FEATURE_FLAG auto-approved on submit
            HttpResponse<String> submitResp = post("/lifecycle/changes", // GH-90000
                mapper.writeValueAsString(Map.of( // GH-90000
                    "tenantId", "tenant-1",
                    "requestingAgent", "agent",
                    "changeType", "FEATURE_FLAG",
                    "description", "some feature flag"
                )));
            String changeId = (String) mapper.readValue(submitResp.body(), Map.class).get("changeId");

            HttpResponse<String> resp = post("/lifecycle/changes/" + changeId + "/withdraw", "{}"); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(409); // GH-90000
        }
    }

    // ==================== POST /lifecycle/recertification/campaigns ====================

    @Nested
    @DisplayName("POST /lifecycle/recertification/campaigns")
    class CreateCampaignTests {

        @Test
        @DisplayName("creates a campaign and returns campaign details with items pre-populated")
        void createsCampaign() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post("/lifecycle/recertification/campaigns", // GH-90000
                mapper.writeValueAsString(Map.of( // GH-90000
                    "tenantId", "tenant-1",
                    "campaignName", "Q1 Policy Review",
                    "scope", "POLICIES"
                )));
            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000

            Map<String, Object> body = mapper.readValue( // GH-90000
                resp.body(), // GH-90000
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {} // GH-90000
            );
            assertThat(body.get("tenantId")).isEqualTo("tenant-1");
            assertThat(body.get("campaignName")).isEqualTo("Q1 Policy Review");
            assertThat(body.get("scope")).isEqualTo("POLICIES");
            assertThat(body).containsKey("campaignId");
            assertThat((int) body.get("totalItems")).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("returns 400 when required fields are missing")
        void returns400WhenFieldsMissing() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post("/lifecycle/recertification/campaigns", // GH-90000
                mapper.writeValueAsString(Map.of("tenantId", "tenant-1"))); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        }

        @Test
        @DisplayName("returns 400 for unknown scope")
        void returns400ForUnknownScope() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post("/lifecycle/recertification/campaigns", // GH-90000
                mapper.writeValueAsString(Map.of( // GH-90000
                    "tenantId", "tenant-1",
                    "campaignName", "Test",
                    "scope", "NOT_A_REAL_SCOPE"
                )));
            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        }
    }

    // ==================== GET /lifecycle/recertification/campaigns ====================

    @Nested
    @DisplayName("GET /lifecycle/recertification/campaigns")
    class ListCampaignsTests {

        @Test
        @DisplayName("lists all campaigns for a tenant")
        void listsCampaigns() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            post("/lifecycle/recertification/campaigns", // GH-90000
                mapper.writeValueAsString(Map.of( // GH-90000
                    "tenantId", "tenant-1",
                    "campaignName", "Annual Review",
                    "scope", "AGENT_PERMISSIONS"
                )));

            HttpResponse<String> resp = get("/lifecycle/recertification/campaigns?tenantId=tenant-1");
            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000

            Map<String, Object> body = mapper.readValue( // GH-90000
                resp.body(), // GH-90000
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {} // GH-90000
            );
            assertThat(body.get("tenantId")).isEqualTo("tenant-1");
            assertThat(body).containsKey("campaigns");
            assertThat(body).containsKey("count");

            @SuppressWarnings("unchecked")
            List<Object> campaigns = (List<Object>) body.get("campaigns");
            assertThat(campaigns).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("returns 400 when tenantId is missing")
        void returns400WhenTenantIdMissing() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = get("/lifecycle/recertification/campaigns");
            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        }
    }

    // ==================== GET /lifecycle/recertification/campaigns/:campaignId ====================

    @Nested
    @DisplayName("GET /lifecycle/recertification/campaigns/:campaignId")
    class GetCampaignTests {

        @Test
        @DisplayName("returns 200 with campaign details for existing campaignId")
        void returnsExistingCampaign() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> createResp = post("/lifecycle/recertification/campaigns", // GH-90000
                mapper.writeValueAsString(Map.of( // GH-90000
                    "tenantId", "tenant-1",
                    "campaignName", "Tool Review",
                    "scope", "TOOL_REGISTRATIONS"
                )));
            String campaignId = (String) mapper.readValue(createResp.body(), Map.class).get("campaignId");

            HttpResponse<String> resp = get("/lifecycle/recertification/campaigns/" + campaignId); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000

            Map<String, Object> body = mapper.readValue( // GH-90000
                resp.body(), // GH-90000
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {} // GH-90000
            );
            assertThat(body.get("campaignId")).isEqualTo(campaignId);
            assertThat(body.get("scope")).isEqualTo("TOOL_REGISTRATIONS");
        }

        @Test
        @DisplayName("returns 404 for non-existent campaignId")
        void returns404ForMissingCampaign() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp =
                get("/lifecycle/recertification/campaigns/no-such-campaign-id");
            assertThat(resp.statusCode()).isEqualTo(404); // GH-90000
        }
    }

    // ==================== GET .../campaigns/:campaignId/items ====================

    @Nested
    @DisplayName("GET /lifecycle/recertification/campaigns/:campaignId/items")
    class GetCampaignItemsTests {

        @Test
        @DisplayName("returns items list for an existing campaign")
        void returnsItems() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            String campaignId =
                createCampaignAndGetId("tenant-1", "Consent Review", "DATA_ACCESS_CONSENTS"); // GH-90000

            HttpResponse<String> resp =
                get("/lifecycle/recertification/campaigns/" + campaignId + "/items"); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000

            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("campaignId")).isEqualTo(campaignId);
            assertThat(body).containsKey("items");
            assertThat((int) body.get("count")).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("returns 404 for non-existent campaignId")
        void returns404ForMissingCampaign() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp =
                get("/lifecycle/recertification/campaigns/no-such-campaign-id/items");
            assertThat(resp.statusCode()).isEqualTo(404); // GH-90000
        }
    }

    // ==================== POST .../items/:itemId/certify ====================

    @Nested
    @DisplayName("POST /lifecycle/recertification/campaigns/:campaignId/items/:itemId/certify")
    class CertifyItemTests {

        @Test
        @DisplayName("certifies a PENDING item, returns CERTIFIED decision")
        void certifiesItem() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            String campaignId =
                createCampaignAndGetId("tenant-1", "Policy Recertification", "POLICIES"); // GH-90000
            String itemId = getFirstItemId(campaignId); // GH-90000

            HttpResponse<String> resp = post( // GH-90000
                "/lifecycle/recertification/campaigns/" + campaignId + "/items/" + itemId + "/certify",
                mapper.writeValueAsString(Map.of("certifierId", "compliance-officer-001"))); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000

            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("decision")).isEqualTo("CERTIFIED");
            assertThat(body.get("certifierId")).isEqualTo("compliance-officer-001");
        }

        @Test
        @DisplayName("returns 400 when certifierId is missing")
        void returns400WhenCertifierIdMissing() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            String campaignId =
                createCampaignAndGetId("tenant-1", "Policy Recertification 2", "POLICIES"); // GH-90000
            String itemId = getFirstItemId(campaignId); // GH-90000

            HttpResponse<String> resp = post( // GH-90000
                "/lifecycle/recertification/campaigns/" + campaignId + "/items/" + itemId + "/certify",
                mapper.writeValueAsString(Map.of())); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        }

        @Test
        @DisplayName("returns 409 when item has already been reviewed")
        void returns409WhenAlreadyReviewed() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            String campaignId =
                createCampaignAndGetId("tenant-1", "Policy Recertification 3", "POLICIES"); // GH-90000
            String itemId = getFirstItemId(campaignId); // GH-90000

            // Certify once
            post("/lifecycle/recertification/campaigns/" + campaignId + "/items/" + itemId + "/certify", // GH-90000
                mapper.writeValueAsString(Map.of("certifierId", "officer-first"))); // GH-90000

            // Attempt to certify again → already reviewed → 409
            HttpResponse<String> resp = post( // GH-90000
                "/lifecycle/recertification/campaigns/" + campaignId + "/items/" + itemId + "/certify",
                mapper.writeValueAsString(Map.of("certifierId", "officer-second"))); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(409); // GH-90000
        }
    }

    // ==================== POST .../items/:itemId/revoke ====================

    @Nested
    @DisplayName("POST /lifecycle/recertification/campaigns/:campaignId/items/:itemId/revoke")
    class RevokeItemTests {

        @Test
        @DisplayName("revokes a PENDING item, returns REVOKED decision")
        void revokesItem() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            String campaignId =
                createCampaignAndGetId("tenant-1", "Access Revocation Review", "AGENT_PERMISSIONS"); // GH-90000
            String itemId = getFirstItemId(campaignId); // GH-90000

            HttpResponse<String> resp = post( // GH-90000
                "/lifecycle/recertification/campaigns/" + campaignId + "/items/" + itemId + "/revoke",
                mapper.writeValueAsString(Map.of( // GH-90000
                    "certifierId", "security-officer",
                    "reason", "Agent no longer active in this tenant"
                )));
            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000

            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("decision")).isEqualTo("REVOKED");
        }

        @Test
        @DisplayName("returns 400 when reason is missing")
        void returns400WhenReasonMissing() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            String campaignId =
                createCampaignAndGetId("tenant-1", "Access Revocation Review 2", "AGENT_PERMISSIONS"); // GH-90000
            String itemId = getFirstItemId(campaignId); // GH-90000

            HttpResponse<String> resp = post( // GH-90000
                "/lifecycle/recertification/campaigns/" + campaignId + "/items/" + itemId + "/revoke",
                mapper.writeValueAsString(Map.of("certifierId", "officer"))); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        }
    }

    // ==================== GET .../campaigns/:campaignId/report ====================

    @Nested
    @DisplayName("GET /lifecycle/recertification/campaigns/:campaignId/report")
    class GenerateReportTests {

        @Test
        @DisplayName("returns audit report with campaign summary and certification rate")
        void returnsAuditReport() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            String campaignId = createCampaignAndGetId("tenant-1", "Full Compliance Audit", "FULL"); // GH-90000

            HttpResponse<String> resp =
                get("/lifecycle/recertification/campaigns/" + campaignId + "/report"); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000

            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("campaignId")).isEqualTo(campaignId);
            assertThat(body.get("tenantId")).isEqualTo("tenant-1");
            assertThat(body).containsKey("certificationRate");
            assertThat(body).containsKey("totalItems");
            assertThat(body).containsKey("revokedItems");
        }

        @Test
        @DisplayName("returns 404 for non-existent campaignId")
        void returns404ForMissingCampaign() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp =
                get("/lifecycle/recertification/campaigns/no-such-campaign-id/report");
            assertThat(resp.statusCode()).isEqualTo(404); // GH-90000
        }
    }

    // ==================== helpers ====================

    /** Creates a campaign and returns its {@code campaignId}. */
    private String createCampaignAndGetId(String tenantId, String name, String scope) // GH-90000
            throws Exception {
        HttpResponse<String> resp = post("/lifecycle/recertification/campaigns", // GH-90000
            mapper.writeValueAsString(Map.of( // GH-90000
                "tenantId", tenantId,
                "campaignName", name,
                "scope", scope
            )));
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        return (String) body.get("campaignId");
    }

    /** Retrieves items for a campaign and returns the first item's {@code itemId}. */
    private String getFirstItemId(String campaignId) throws Exception { // GH-90000
        HttpResponse<String> resp =
            get("/lifecycle/recertification/campaigns/" + campaignId + "/items"); // GH-90000
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        return (String) items.get(0).get("itemId");
    }

    private int findFreePort() throws IOException { // GH-90000
        try (ServerSocket s = new ServerSocket(0)) { // GH-90000
            return s.getLocalPort(); // GH-90000
        }
    }

    private void waitForServerReady(int targetPort) throws Exception { // GH-90000
        for (int i = 0; i < 50; i++) { // GH-90000
            try (Socket s = new Socket("localhost", targetPort)) { // GH-90000
                return;
            } catch (IOException e) { // GH-90000
                Thread.sleep(100); // GH-90000
            }
        }
        throw new IllegalStateException("Server did not start within 5 seconds on port " + targetPort); // GH-90000
    }

    private HttpResponse<String> get(String path) throws Exception { // GH-90000
        return httpClient.send( // GH-90000
            HttpRequest.newBuilder() // GH-90000
                .uri(URI.create("http://localhost:" + port + path)) // GH-90000
                .GET() // GH-90000
                .build(), // GH-90000
            HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private HttpResponse<String> post(String path, String body) throws Exception { // GH-90000
        return httpClient.send( // GH-90000
            HttpRequest.newBuilder() // GH-90000
                .uri(URI.create("http://localhost:" + port + path)) // GH-90000
                .header("Content-Type", "application/json") // GH-90000
                .POST(HttpRequest.BodyPublishers.ofString(body)) // GH-90000
                .build(), // GH-90000
            HttpResponse.BodyHandlers.ofString()); // GH-90000
    }
}

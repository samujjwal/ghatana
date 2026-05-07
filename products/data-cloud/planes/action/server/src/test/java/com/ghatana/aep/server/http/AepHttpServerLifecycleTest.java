/*
 * Copyright (c) 2026 Ghatana Inc. 
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
import org.junit.jupiter.api.Tag;
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
 * change approval ({@code /lifecycle/changes/**}) and recertification 
 * ({@code /lifecycle/recertification/**}) endpoints, validating status codes, 
 * JSON shape, and lifecycle state transitions (PENDING_REVIEW → APPROVED / 
 * REJECTED / WITHDRAWN; PENDING → CERTIFIED / REVOKED).
 *
 * @doc.type class
 * @doc.purpose Integration tests for /lifecycle/** HTTP endpoints
 * @doc.layer product
 * @doc.pattern Test
 */
@Tag("local-network")
@DisplayName("AepHttpServer – Lifecycle Endpoints")
class AepHttpServerLifecycleTest {

    private AepEngine engine;
    private AepHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper(); 
    private String previousAuthDisabled;
    private String previousJwtSecret;

    @BeforeEach
    void setUp() throws Exception { 
        previousAuthDisabled = System.getProperty("AEP_AUTH_DISABLED");
        previousJwtSecret = System.getProperty("AEP_JWT_SECRET");
        System.setProperty("AEP_AUTH_DISABLED", "true");
        System.clearProperty("AEP_JWT_SECRET");

        engine = Aep.forTesting(); 
        port = findFreePort(); 
        httpClient = HttpClient.newBuilder().build(); 
    }

    @AfterEach
    void tearDown() { 
        if (server != null) server.stop(); 
        if (engine != null) engine.close(); 

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
        void autoApprovesLowRiskChange() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp = post("/lifecycle/changes", 
                mapper.writeValueAsString(Map.of( 
                    "tenantId", "tenant-1",
                    "requestingAgent", "agent-bootstrap",
                    "changeType", "FEATURE_FLAG",
                    "description", "Enable dark-mode feature flag"
                )));
            assertThat(resp.statusCode()).isEqualTo(200); 

            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); 
            assertThat(body.get("tenantId")).isEqualTo("tenant-1");
            assertThat(body.get("changeType")).isEqualTo("FEATURE_FLAG");
            assertThat(body.get("status")).isEqualTo("APPROVED");
            assertThat(body).containsKey("changeId");
            assertThat(body).containsKey("riskScore");
        }

        @Test
        @DisplayName("high-risk change (AGENT_DEPLOYMENT, risk=65) enters PENDING_REVIEW")
        void highRiskChangePendingReview() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp = post("/lifecycle/changes", 
                mapper.writeValueAsString(Map.of( 
                    "tenantId", "tenant-1",
                    "requestingAgent", "devops-agent",
                    "changeType", "AGENT_DEPLOYMENT",
                    "description", "Deploy new classifier agent"
                )));
            assertThat(resp.statusCode()).isEqualTo(200); 

            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); 
            assertThat(body.get("status")).isEqualTo("PENDING_REVIEW");
            assertThat(body).containsKey("changeId");
        }

        @Test
        @DisplayName("returns 400 when required fields are missing")
        void returns400WhenFieldsMissing() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp = post("/lifecycle/changes", 
                mapper.writeValueAsString(Map.of( 
                    "tenantId", "tenant-1",
                    "changeType", "FEATURE_FLAG"
                    // missing requestingAgent and description
                )));
            assertThat(resp.statusCode()).isEqualTo(400); 
        }

        @Test
        @DisplayName("returns 400 for unknown changeType")
        void returns400ForUnknownChangeType() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp = post("/lifecycle/changes", 
                mapper.writeValueAsString(Map.of( 
                    "tenantId", "tenant-1",
                    "requestingAgent", "agent-x",
                    "changeType", "UNKNOWN_CHANGE_TYPE_XYZ",
                    "description", "test"
                )));
            assertThat(resp.statusCode()).isEqualTo(400); 
        }
    }

    // ==================== GET /lifecycle/changes ====================

    @Nested
    @DisplayName("GET /lifecycle/changes")
    class ListPendingChangesTests {

        @Test
        @DisplayName("returns pending changes list for a tenant")
        void returnsPendingChanges() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            // Submit a high-risk (PENDING_REVIEW) change first 
            post("/lifecycle/changes", 
                mapper.writeValueAsString(Map.of( 
                    "tenantId", "tenant-1",
                    "requestingAgent", "ops-agent",
                    "changeType", "AGENT_DEPLOYMENT",
                    "description", "Deploy summarizer"
                )));

            HttpResponse<String> resp = get("/lifecycle/changes?tenantId=tenant-1");
            assertThat(resp.statusCode()).isEqualTo(200); 

            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); 
            assertThat(body.get("tenantId")).isEqualTo("tenant-1");
            assertThat(body).containsKey("pending");
            assertThat(body).containsKey("count");

            @SuppressWarnings("unchecked")
            List<Object> pending = (List<Object>) body.get("pending");
            assertThat(pending).isNotEmpty(); 
        }

        @Test
        @DisplayName("returns 400 when tenantId is missing")
        void returns400WhenTenantIdMissing() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp = get("/lifecycle/changes");
            assertThat(resp.statusCode()).isEqualTo(400); 
        }
    }

    // ==================== GET /lifecycle/changes/:changeId ====================

    @Nested
    @DisplayName("GET /lifecycle/changes/:changeId")
    class GetChangeTests {

        @Test
        @DisplayName("returns 200 with change details for existing changeId")
        void returnsExistingChange() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> submitResp = post("/lifecycle/changes", 
                mapper.writeValueAsString(Map.of( 
                    "tenantId", "tenant-1",
                    "requestingAgent", "agent-x",
                    "changeType", "CONFIG_CHANGE",
                    "description", "Update rate limit"
                )));
            String changeId = (String) mapper.readValue(submitResp.body(), Map.class).get("changeId");

            HttpResponse<String> resp = get("/lifecycle/changes/" + changeId); 
            assertThat(resp.statusCode()).isEqualTo(200); 

            Map<String, Object> body = mapper.readValue( 
                resp.body(), 
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {} 
            );
            assertThat(body.get("changeId")).isEqualTo(changeId);
            assertThat(body.get("tenantId")).isEqualTo("tenant-1");
        }

        @Test
        @DisplayName("returns 404 for non-existent changeId")
        void returns404ForMissingChange() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp = get("/lifecycle/changes/non-existent-change-id");
            assertThat(resp.statusCode()).isEqualTo(404); 
        }
    }

    // ==================== POST /lifecycle/changes/:changeId/approve ====================

    @Nested
    @DisplayName("POST /lifecycle/changes/:changeId/approve")
    class ApproveChangeTests {

        @Test
        @DisplayName("approves a PENDING_REVIEW change, returns APPROVED status with reviewerId")
        void approvesPendingChange() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> submitResp = post("/lifecycle/changes", 
                mapper.writeValueAsString(Map.of( 
                    "tenantId", "tenant-1",
                    "requestingAgent", "devops",
                    "changeType", "AGENT_DEPLOYMENT",
                    "description", "Deploy audit agent"
                )));
            String changeId = (String) mapper.readValue(submitResp.body(), Map.class).get("changeId");

            HttpResponse<String> resp = post("/lifecycle/changes/" + changeId + "/approve", 
                mapper.writeValueAsString(Map.of( 
                    "reviewerId", "reviewer-001",
                    "notes", "Looks good, approved"
                )));
            assertThat(resp.statusCode()).isEqualTo(200); 

            Map<String, Object> body = mapper.readValue( 
                resp.body(), 
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {} 
            );
            assertThat(body.get("status")).isEqualTo("APPROVED");
            assertThat(body.get("reviewerId")).isEqualTo("reviewer-001");
        }

        @Test
        @DisplayName("returns 400 when reviewerId is missing")
        void returns400WhenReviewerIdMissing() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> submitResp = post("/lifecycle/changes", 
                mapper.writeValueAsString(Map.of( 
                    "tenantId", "tenant-1",
                    "requestingAgent", "agent",
                    "changeType", "AGENT_DEPLOYMENT",
                    "description", "deploy"
                )));
            String changeId = (String) mapper.readValue(submitResp.body(), Map.class).get("changeId");

            HttpResponse<String> resp = post("/lifecycle/changes/" + changeId + "/approve", 
                mapper.writeValueAsString(Map.of())); 
            assertThat(resp.statusCode()).isEqualTo(400); 
        }

        @Test
        @DisplayName("returns 409 when change is already in a terminal state")
        void returns409WhenAlreadyApproved() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            // FEATURE_FLAG (risk=20 < threshold=60) is auto-approved on submit 
            HttpResponse<String> submitResp = post("/lifecycle/changes", 
                mapper.writeValueAsString(Map.of( 
                    "tenantId", "tenant-1",
                    "requestingAgent", "agent",
                    "changeType", "FEATURE_FLAG",
                    "description", "enable dark mode"
                )));
            String changeId = (String) mapper.readValue(submitResp.body(), Map.class).get("changeId");

            // Attempting to approve an already-APPROVED change → 409
            HttpResponse<String> resp = post("/lifecycle/changes/" + changeId + "/approve", 
                mapper.writeValueAsString(Map.of("reviewerId", "reviewer-002"))); 
            assertThat(resp.statusCode()).isEqualTo(409); 
        }

        @Test
        @DisplayName("returns 404 for non-existent changeId")
        void returns404ForMissingChange() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp = post("/lifecycle/changes/no-such-id/approve", 
                mapper.writeValueAsString(Map.of("reviewerId", "reviewer-001"))); 
            assertThat(resp.statusCode()).isEqualTo(404); 
        }
    }

    // ==================== POST /lifecycle/changes/:changeId/reject ====================

    @Nested
    @DisplayName("POST /lifecycle/changes/:changeId/reject")
    class RejectChangeTests {

        @Test
        @DisplayName("rejects a PENDING_REVIEW change, returns REJECTED status")
        void rejectsPendingChange() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> submitResp = post("/lifecycle/changes", 
                mapper.writeValueAsString(Map.of( 
                    "tenantId", "tenant-1",
                    "requestingAgent", "devops",
                    "changeType", "PERMISSION_GRANT",
                    "description", "Grant elevated read access"
                )));
            String changeId = (String) mapper.readValue(submitResp.body(), Map.class).get("changeId");

            HttpResponse<String> resp = post("/lifecycle/changes/" + changeId + "/reject", 
                mapper.writeValueAsString(Map.of( 
                    "reviewerId", "security-reviewer",
                    "reason", "Violates least-privilege policy"
                )));
            assertThat(resp.statusCode()).isEqualTo(200); 

            Map<String, Object> body = mapper.readValue( 
                resp.body(), 
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {} 
            );
            assertThat(body.get("status")).isEqualTo("REJECTED");
        }

        @Test
        @DisplayName("returns 400 when reason is missing")
        void returns400WhenReasonMissing() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> submitResp = post("/lifecycle/changes", 
                mapper.writeValueAsString(Map.of( 
                    "tenantId", "tenant-1",
                    "requestingAgent", "agent",
                    "changeType", "PERMISSION_GRANT",
                    "description", "grant elevated read"
                )));
            String changeId = (String) mapper.readValue(submitResp.body(), Map.class).get("changeId");

            HttpResponse<String> resp = post("/lifecycle/changes/" + changeId + "/reject", 
                mapper.writeValueAsString(Map.of("reviewerId", "reviewer-1"))); 
            assertThat(resp.statusCode()).isEqualTo(400); 
        }
    }

    // ==================== POST /lifecycle/changes/:changeId/withdraw ====================

    @Nested
    @DisplayName("POST /lifecycle/changes/:changeId/withdraw")
    class WithdrawChangeTests {

        @Test
        @DisplayName("withdraws a PENDING_REVIEW change, returns WITHDRAWN status")
        void withdrawsPendingChange() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            // TOOL_REGISTRATION risk=60 >= threshold=60 → PENDING_REVIEW
            HttpResponse<String> submitResp = post("/lifecycle/changes", 
                mapper.writeValueAsString(Map.of( 
                    "tenantId", "tenant-1",
                    "requestingAgent", "ops-agent",
                    "changeType", "TOOL_REGISTRATION",
                    "description", "Register new search tool"
                )));
            String changeId = (String) mapper.readValue(submitResp.body(), Map.class).get("changeId");

            HttpResponse<String> resp = post("/lifecycle/changes/" + changeId + "/withdraw", "{}"); 
            assertThat(resp.statusCode()).isEqualTo(200); 

            Map<String, Object> body = mapper.readValue( 
                resp.body(), 
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {} 
            );
            assertThat(body.get("status")).isEqualTo("WITHDRAWN");
        }

        @Test
        @DisplayName("returns 409 when withdrawing an already-approved change")
        void returns409WhenWithdrawingApproved() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            // FEATURE_FLAG auto-approved on submit
            HttpResponse<String> submitResp = post("/lifecycle/changes", 
                mapper.writeValueAsString(Map.of( 
                    "tenantId", "tenant-1",
                    "requestingAgent", "agent",
                    "changeType", "FEATURE_FLAG",
                    "description", "some feature flag"
                )));
            String changeId = (String) mapper.readValue(submitResp.body(), Map.class).get("changeId");

            HttpResponse<String> resp = post("/lifecycle/changes/" + changeId + "/withdraw", "{}"); 
            assertThat(resp.statusCode()).isEqualTo(409); 
        }
    }

    // ==================== POST /lifecycle/recertification/campaigns ====================

    @Nested
    @DisplayName("POST /lifecycle/recertification/campaigns")
    class CreateCampaignTests {

        @Test
        @DisplayName("creates a campaign and returns campaign details with items pre-populated")
        void createsCampaign() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp = post("/lifecycle/recertification/campaigns", 
                mapper.writeValueAsString(Map.of( 
                    "tenantId", "tenant-1",
                    "campaignName", "Q1 Policy Review",
                    "scope", "POLICIES"
                )));
            assertThat(resp.statusCode()).isEqualTo(200); 

            Map<String, Object> body = mapper.readValue( 
                resp.body(), 
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {} 
            );
            assertThat(body.get("tenantId")).isEqualTo("tenant-1");
            assertThat(body.get("campaignName")).isEqualTo("Q1 Policy Review");
            assertThat(body.get("scope")).isEqualTo("POLICIES");
            assertThat(body).containsKey("campaignId");
            assertThat((int) body.get("totalItems")).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("returns 400 when required fields are missing")
        void returns400WhenFieldsMissing() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp = post("/lifecycle/recertification/campaigns", 
                mapper.writeValueAsString(Map.of("tenantId", "tenant-1"))); 
            assertThat(resp.statusCode()).isEqualTo(400); 
        }

        @Test
        @DisplayName("returns 400 for unknown scope")
        void returns400ForUnknownScope() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp = post("/lifecycle/recertification/campaigns", 
                mapper.writeValueAsString(Map.of( 
                    "tenantId", "tenant-1",
                    "campaignName", "Test",
                    "scope", "NOT_A_REAL_SCOPE"
                )));
            assertThat(resp.statusCode()).isEqualTo(400); 
        }
    }

    // ==================== GET /lifecycle/recertification/campaigns ====================

    @Nested
    @DisplayName("GET /lifecycle/recertification/campaigns")
    class ListCampaignsTests {

        @Test
        @DisplayName("lists all campaigns for a tenant")
        void listsCampaigns() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            post("/lifecycle/recertification/campaigns", 
                mapper.writeValueAsString(Map.of( 
                    "tenantId", "tenant-1",
                    "campaignName", "Annual Review",
                    "scope", "AGENT_PERMISSIONS"
                )));

            HttpResponse<String> resp = get("/lifecycle/recertification/campaigns?tenantId=tenant-1");
            assertThat(resp.statusCode()).isEqualTo(200); 

            Map<String, Object> body = mapper.readValue( 
                resp.body(), 
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {} 
            );
            assertThat(body.get("tenantId")).isEqualTo("tenant-1");
            assertThat(body).containsKey("campaigns");
            assertThat(body).containsKey("count");

            @SuppressWarnings("unchecked")
            List<Object> campaigns = (List<Object>) body.get("campaigns");
            assertThat(campaigns).isNotEmpty(); 
        }

        @Test
        @DisplayName("returns 400 when tenantId is missing")
        void returns400WhenTenantIdMissing() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp = get("/lifecycle/recertification/campaigns");
            assertThat(resp.statusCode()).isEqualTo(400); 
        }
    }

    // ==================== GET /lifecycle/recertification/campaigns/:campaignId ====================

    @Nested
    @DisplayName("GET /lifecycle/recertification/campaigns/:campaignId")
    class GetCampaignTests {

        @Test
        @DisplayName("returns 200 with campaign details for existing campaignId")
        void returnsExistingCampaign() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> createResp = post("/lifecycle/recertification/campaigns", 
                mapper.writeValueAsString(Map.of( 
                    "tenantId", "tenant-1",
                    "campaignName", "Tool Review",
                    "scope", "TOOL_REGISTRATIONS"
                )));
            String campaignId = (String) mapper.readValue(createResp.body(), Map.class).get("campaignId");

            HttpResponse<String> resp = get("/lifecycle/recertification/campaigns/" + campaignId); 
            assertThat(resp.statusCode()).isEqualTo(200); 

            Map<String, Object> body = mapper.readValue( 
                resp.body(), 
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {} 
            );
            assertThat(body.get("campaignId")).isEqualTo(campaignId);
            assertThat(body.get("scope")).isEqualTo("TOOL_REGISTRATIONS");
        }

        @Test
        @DisplayName("returns 404 for non-existent campaignId")
        void returns404ForMissingCampaign() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp =
                get("/lifecycle/recertification/campaigns/no-such-campaign-id");
            assertThat(resp.statusCode()).isEqualTo(404); 
        }
    }

    // ==================== GET .../campaigns/:campaignId/items ====================

    @Nested
    @DisplayName("GET /lifecycle/recertification/campaigns/:campaignId/items")
    class GetCampaignItemsTests {

        @Test
        @DisplayName("returns items list for an existing campaign")
        void returnsItems() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            String campaignId =
                createCampaignAndGetId("tenant-1", "Consent Review", "DATA_ACCESS_CONSENTS"); 

            HttpResponse<String> resp =
                get("/lifecycle/recertification/campaigns/" + campaignId + "/items"); 
            assertThat(resp.statusCode()).isEqualTo(200); 

            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); 
            assertThat(body.get("campaignId")).isEqualTo(campaignId);
            assertThat(body).containsKey("items");
            assertThat((int) body.get("count")).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("returns 404 for non-existent campaignId")
        void returns404ForMissingCampaign() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp =
                get("/lifecycle/recertification/campaigns/no-such-campaign-id/items");
            assertThat(resp.statusCode()).isEqualTo(404); 
        }
    }

    // ==================== POST .../items/:itemId/certify ====================

    @Nested
    @DisplayName("POST /lifecycle/recertification/campaigns/:campaignId/items/:itemId/certify")
    class CertifyItemTests {

        @Test
        @DisplayName("certifies a PENDING item, returns CERTIFIED decision")
        void certifiesItem() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            String campaignId =
                createCampaignAndGetId("tenant-1", "Policy Recertification", "POLICIES"); 
            String itemId = getFirstItemId(campaignId); 

            HttpResponse<String> resp = post( 
                "/lifecycle/recertification/campaigns/" + campaignId + "/items/" + itemId + "/certify",
                mapper.writeValueAsString(Map.of("certifierId", "compliance-officer-001"))); 
            assertThat(resp.statusCode()).isEqualTo(200); 

            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); 
            assertThat(body.get("decision")).isEqualTo("CERTIFIED");
            assertThat(body.get("certifierId")).isEqualTo("compliance-officer-001");
        }

        @Test
        @DisplayName("returns 400 when certifierId is missing")
        void returns400WhenCertifierIdMissing() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            String campaignId =
                createCampaignAndGetId("tenant-1", "Policy Recertification 2", "POLICIES"); 
            String itemId = getFirstItemId(campaignId); 

            HttpResponse<String> resp = post( 
                "/lifecycle/recertification/campaigns/" + campaignId + "/items/" + itemId + "/certify",
                mapper.writeValueAsString(Map.of())); 
            assertThat(resp.statusCode()).isEqualTo(400); 
        }

        @Test
        @DisplayName("returns 409 when item has already been reviewed")
        void returns409WhenAlreadyReviewed() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            String campaignId =
                createCampaignAndGetId("tenant-1", "Policy Recertification 3", "POLICIES"); 
            String itemId = getFirstItemId(campaignId); 

            // Certify once
            post("/lifecycle/recertification/campaigns/" + campaignId + "/items/" + itemId + "/certify", 
                mapper.writeValueAsString(Map.of("certifierId", "officer-first"))); 

            // Attempt to certify again → already reviewed → 409
            HttpResponse<String> resp = post( 
                "/lifecycle/recertification/campaigns/" + campaignId + "/items/" + itemId + "/certify",
                mapper.writeValueAsString(Map.of("certifierId", "officer-second"))); 
            assertThat(resp.statusCode()).isEqualTo(409); 
        }
    }

    // ==================== POST .../items/:itemId/revoke ====================

    @Nested
    @DisplayName("POST /lifecycle/recertification/campaigns/:campaignId/items/:itemId/revoke")
    class RevokeItemTests {

        @Test
        @DisplayName("revokes a PENDING item, returns REVOKED decision")
        void revokesItem() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            String campaignId =
                createCampaignAndGetId("tenant-1", "Access Revocation Review", "AGENT_PERMISSIONS"); 
            String itemId = getFirstItemId(campaignId); 

            HttpResponse<String> resp = post( 
                "/lifecycle/recertification/campaigns/" + campaignId + "/items/" + itemId + "/revoke",
                mapper.writeValueAsString(Map.of( 
                    "certifierId", "security-officer",
                    "reason", "Agent no longer active in this tenant"
                )));
            assertThat(resp.statusCode()).isEqualTo(200); 

            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); 
            assertThat(body.get("decision")).isEqualTo("REVOKED");
        }

        @Test
        @DisplayName("returns 400 when reason is missing")
        void returns400WhenReasonMissing() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            String campaignId =
                createCampaignAndGetId("tenant-1", "Access Revocation Review 2", "AGENT_PERMISSIONS"); 
            String itemId = getFirstItemId(campaignId); 

            HttpResponse<String> resp = post( 
                "/lifecycle/recertification/campaigns/" + campaignId + "/items/" + itemId + "/revoke",
                mapper.writeValueAsString(Map.of("certifierId", "officer"))); 
            assertThat(resp.statusCode()).isEqualTo(400); 
        }
    }

    // ==================== GET .../campaigns/:campaignId/report ====================

    @Nested
    @DisplayName("GET /lifecycle/recertification/campaigns/:campaignId/report")
    class GenerateReportTests {

        @Test
        @DisplayName("returns audit report with campaign summary and certification rate")
        void returnsAuditReport() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            String campaignId = createCampaignAndGetId("tenant-1", "Full Compliance Audit", "FULL"); 

            HttpResponse<String> resp =
                get("/lifecycle/recertification/campaigns/" + campaignId + "/report"); 
            assertThat(resp.statusCode()).isEqualTo(200); 

            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); 
            assertThat(body.get("campaignId")).isEqualTo(campaignId);
            assertThat(body.get("tenantId")).isEqualTo("tenant-1");
            assertThat(body).containsKey("certificationRate");
            assertThat(body).containsKey("totalItems");
            assertThat(body).containsKey("revokedItems");
        }

        @Test
        @DisplayName("returns 404 for non-existent campaignId")
        void returns404ForMissingCampaign() throws Exception { 
            server = new AepHttpServer(engine, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp =
                get("/lifecycle/recertification/campaigns/no-such-campaign-id/report");
            assertThat(resp.statusCode()).isEqualTo(404); 
        }
    }

    // ==================== helpers ====================

    /** Creates a campaign and returns its {@code campaignId}. */
    private String createCampaignAndGetId(String tenantId, String name, String scope) 
            throws Exception {
        HttpResponse<String> resp = post("/lifecycle/recertification/campaigns", 
            mapper.writeValueAsString(Map.of( 
                "tenantId", tenantId,
                "campaignName", name,
                "scope", scope
            )));
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class); 
        return (String) body.get("campaignId");
    }

    /** Retrieves items for a campaign and returns the first item's {@code itemId}. */
    private String getFirstItemId(String campaignId) throws Exception { 
        HttpResponse<String> resp =
            get("/lifecycle/recertification/campaigns/" + campaignId + "/items"); 
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class); 
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        return (String) items.get(0).get("itemId");
    }

    private int findFreePort() throws IOException { 
        try (ServerSocket s = new ServerSocket(0)) { 
            return s.getLocalPort(); 
        }
    }

    private void waitForServerReady(int targetPort) throws Exception { 
        for (int i = 0; i < 50; i++) { 
            try (Socket s = new Socket("localhost", targetPort)) { 
                return;
            } catch (IOException e) { 
                Thread.sleep(100); 
            }
        }
        throw new IllegalStateException("Server did not start within 5 seconds on port " + targetPort); 
    }

    private HttpResponse<String> get(String path) throws Exception { 
        return httpClient.send( 
            HttpRequest.newBuilder() 
                .uri(URI.create("http://localhost:" + port + path)) 
                .GET() 
                .build(), 
            HttpResponse.BodyHandlers.ofString()); 
    }

    private HttpResponse<String> post(String path, String body) throws Exception { 
        return httpClient.send( 
            HttpRequest.newBuilder() 
                .uri(URI.create("http://localhost:" + port + path)) 
                .header("Content-Type", "application/json") 
                .POST(HttpRequest.BodyPublishers.ofString(body)) 
                .build(), 
            HttpResponse.BodyHandlers.ofString()); 
    }
}

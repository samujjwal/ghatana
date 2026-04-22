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
@DisplayName("AepHttpServer – Lifecycle Endpoints [GH-90000]")
class AepHttpServerLifecycleTest {

    private AepEngine engine;
    private AepHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        engine = Aep.forTesting(); // GH-90000
        port = findFreePort(); // GH-90000
        httpClient = HttpClient.newBuilder().build(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) server.stop(); // GH-90000
        if (engine != null) engine.close(); // GH-90000
    }

    // ==================== POST /lifecycle/changes ====================

    @Nested
    @DisplayName("POST /lifecycle/changes [GH-90000]")
    class SubmitChangeTests {

        @Test
        @DisplayName("low-risk change (FEATURE_FLAG, risk=20) is auto-approved immediately [GH-90000]")
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

            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("tenantId [GH-90000]")).isEqualTo("tenant-1 [GH-90000]");
            assertThat(body.get("changeType [GH-90000]")).isEqualTo("FEATURE_FLAG [GH-90000]");
            assertThat(body.get("status [GH-90000]")).isEqualTo("APPROVED [GH-90000]");
            assertThat(body).containsKey("changeId [GH-90000]");
            assertThat(body).containsKey("riskScore [GH-90000]");
        }

        @Test
        @DisplayName("high-risk change (AGENT_DEPLOYMENT, risk=65) enters PENDING_REVIEW [GH-90000]")
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

            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("status [GH-90000]")).isEqualTo("PENDING_REVIEW [GH-90000]");
            assertThat(body).containsKey("changeId [GH-90000]");
        }

        @Test
        @DisplayName("returns 400 when required fields are missing [GH-90000]")
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
        @DisplayName("returns 400 for unknown changeType [GH-90000]")
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
    @DisplayName("GET /lifecycle/changes [GH-90000]")
    class ListPendingChangesTests {

        @Test
        @DisplayName("returns pending changes list for a tenant [GH-90000]")
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

            HttpResponse<String> resp = get("/lifecycle/changes?tenantId=tenant-1 [GH-90000]");
            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000

            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("tenantId [GH-90000]")).isEqualTo("tenant-1 [GH-90000]");
            assertThat(body).containsKey("pending [GH-90000]");
            assertThat(body).containsKey("count [GH-90000]");

            @SuppressWarnings("unchecked [GH-90000]")
            List<Object> pending = (List<Object>) body.get("pending [GH-90000]");
            assertThat(pending).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("returns 400 when tenantId is missing [GH-90000]")
        void returns400WhenTenantIdMissing() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = get("/lifecycle/changes [GH-90000]");
            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        }
    }

    // ==================== GET /lifecycle/changes/:changeId ====================

    @Nested
    @DisplayName("GET /lifecycle/changes/:changeId [GH-90000]")
    class GetChangeTests {

        @Test
        @DisplayName("returns 200 with change details for existing changeId [GH-90000]")
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
            String changeId = (String) mapper.readValue(submitResp.body(), Map.class).get("changeId [GH-90000]");

            HttpResponse<String> resp = get("/lifecycle/changes/" + changeId); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000

            Map<String, Object> body = mapper.readValue( // GH-90000
                resp.body(), // GH-90000
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {} // GH-90000
            );
            assertThat(body.get("changeId [GH-90000]")).isEqualTo(changeId);
            assertThat(body.get("tenantId [GH-90000]")).isEqualTo("tenant-1 [GH-90000]");
        }

        @Test
        @DisplayName("returns 404 for non-existent changeId [GH-90000]")
        void returns404ForMissingChange() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = get("/lifecycle/changes/non-existent-change-id [GH-90000]");
            assertThat(resp.statusCode()).isEqualTo(404); // GH-90000
        }
    }

    // ==================== POST /lifecycle/changes/:changeId/approve ====================

    @Nested
    @DisplayName("POST /lifecycle/changes/:changeId/approve [GH-90000]")
    class ApproveChangeTests {

        @Test
        @DisplayName("approves a PENDING_REVIEW change, returns APPROVED status with reviewerId [GH-90000]")
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
            String changeId = (String) mapper.readValue(submitResp.body(), Map.class).get("changeId [GH-90000]");

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
            assertThat(body.get("status [GH-90000]")).isEqualTo("APPROVED [GH-90000]");
            assertThat(body.get("reviewerId [GH-90000]")).isEqualTo("reviewer-001 [GH-90000]");
        }

        @Test
        @DisplayName("returns 400 when reviewerId is missing [GH-90000]")
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
            String changeId = (String) mapper.readValue(submitResp.body(), Map.class).get("changeId [GH-90000]");

            HttpResponse<String> resp = post("/lifecycle/changes/" + changeId + "/approve", // GH-90000
                mapper.writeValueAsString(Map.of())); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        }

        @Test
        @DisplayName("returns 409 when change is already in a terminal state [GH-90000]")
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
            String changeId = (String) mapper.readValue(submitResp.body(), Map.class).get("changeId [GH-90000]");

            // Attempting to approve an already-APPROVED change → 409
            HttpResponse<String> resp = post("/lifecycle/changes/" + changeId + "/approve", // GH-90000
                mapper.writeValueAsString(Map.of("reviewerId", "reviewer-002"))); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(409); // GH-90000
        }

        @Test
        @DisplayName("returns 404 for non-existent changeId [GH-90000]")
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
    @DisplayName("POST /lifecycle/changes/:changeId/reject [GH-90000]")
    class RejectChangeTests {

        @Test
        @DisplayName("rejects a PENDING_REVIEW change, returns REJECTED status [GH-90000]")
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
            String changeId = (String) mapper.readValue(submitResp.body(), Map.class).get("changeId [GH-90000]");

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
            assertThat(body.get("status [GH-90000]")).isEqualTo("REJECTED [GH-90000]");
        }

        @Test
        @DisplayName("returns 400 when reason is missing [GH-90000]")
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
            String changeId = (String) mapper.readValue(submitResp.body(), Map.class).get("changeId [GH-90000]");

            HttpResponse<String> resp = post("/lifecycle/changes/" + changeId + "/reject", // GH-90000
                mapper.writeValueAsString(Map.of("reviewerId", "reviewer-1"))); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        }
    }

    // ==================== POST /lifecycle/changes/:changeId/withdraw ====================

    @Nested
    @DisplayName("POST /lifecycle/changes/:changeId/withdraw [GH-90000]")
    class WithdrawChangeTests {

        @Test
        @DisplayName("withdraws a PENDING_REVIEW change, returns WITHDRAWN status [GH-90000]")
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
            String changeId = (String) mapper.readValue(submitResp.body(), Map.class).get("changeId [GH-90000]");

            HttpResponse<String> resp = post("/lifecycle/changes/" + changeId + "/withdraw", "{}"); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000

            Map<String, Object> body = mapper.readValue( // GH-90000
                resp.body(), // GH-90000
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {} // GH-90000
            );
            assertThat(body.get("status [GH-90000]")).isEqualTo("WITHDRAWN [GH-90000]");
        }

        @Test
        @DisplayName("returns 409 when withdrawing an already-approved change [GH-90000]")
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
            String changeId = (String) mapper.readValue(submitResp.body(), Map.class).get("changeId [GH-90000]");

            HttpResponse<String> resp = post("/lifecycle/changes/" + changeId + "/withdraw", "{}"); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(409); // GH-90000
        }
    }

    // ==================== POST /lifecycle/recertification/campaigns ====================

    @Nested
    @DisplayName("POST /lifecycle/recertification/campaigns [GH-90000]")
    class CreateCampaignTests {

        @Test
        @DisplayName("creates a campaign and returns campaign details with items pre-populated [GH-90000]")
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
            assertThat(body.get("tenantId [GH-90000]")).isEqualTo("tenant-1 [GH-90000]");
            assertThat(body.get("campaignName [GH-90000]")).isEqualTo("Q1 Policy Review [GH-90000]");
            assertThat(body.get("scope [GH-90000]")).isEqualTo("POLICIES [GH-90000]");
            assertThat(body).containsKey("campaignId [GH-90000]");
            assertThat((int) body.get("totalItems [GH-90000]")).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("returns 400 when required fields are missing [GH-90000]")
        void returns400WhenFieldsMissing() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post("/lifecycle/recertification/campaigns", // GH-90000
                mapper.writeValueAsString(Map.of("tenantId", "tenant-1"))); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        }

        @Test
        @DisplayName("returns 400 for unknown scope [GH-90000]")
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
    @DisplayName("GET /lifecycle/recertification/campaigns [GH-90000]")
    class ListCampaignsTests {

        @Test
        @DisplayName("lists all campaigns for a tenant [GH-90000]")
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

            HttpResponse<String> resp = get("/lifecycle/recertification/campaigns?tenantId=tenant-1 [GH-90000]");
            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000

            Map<String, Object> body = mapper.readValue( // GH-90000
                resp.body(), // GH-90000
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {} // GH-90000
            );
            assertThat(body.get("tenantId [GH-90000]")).isEqualTo("tenant-1 [GH-90000]");
            assertThat(body).containsKey("campaigns [GH-90000]");
            assertThat(body).containsKey("count [GH-90000]");

            @SuppressWarnings("unchecked [GH-90000]")
            List<Object> campaigns = (List<Object>) body.get("campaigns [GH-90000]");
            assertThat(campaigns).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("returns 400 when tenantId is missing [GH-90000]")
        void returns400WhenTenantIdMissing() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = get("/lifecycle/recertification/campaigns [GH-90000]");
            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        }
    }

    // ==================== GET /lifecycle/recertification/campaigns/:campaignId ====================

    @Nested
    @DisplayName("GET /lifecycle/recertification/campaigns/:campaignId [GH-90000]")
    class GetCampaignTests {

        @Test
        @DisplayName("returns 200 with campaign details for existing campaignId [GH-90000]")
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
            String campaignId = (String) mapper.readValue(createResp.body(), Map.class).get("campaignId [GH-90000]");

            HttpResponse<String> resp = get("/lifecycle/recertification/campaigns/" + campaignId); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000

            Map<String, Object> body = mapper.readValue( // GH-90000
                resp.body(), // GH-90000
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {} // GH-90000
            );
            assertThat(body.get("campaignId [GH-90000]")).isEqualTo(campaignId);
            assertThat(body.get("scope [GH-90000]")).isEqualTo("TOOL_REGISTRATIONS [GH-90000]");
        }

        @Test
        @DisplayName("returns 404 for non-existent campaignId [GH-90000]")
        void returns404ForMissingCampaign() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp =
                get("/lifecycle/recertification/campaigns/no-such-campaign-id [GH-90000]");
            assertThat(resp.statusCode()).isEqualTo(404); // GH-90000
        }
    }

    // ==================== GET .../campaigns/:campaignId/items ====================

    @Nested
    @DisplayName("GET /lifecycle/recertification/campaigns/:campaignId/items [GH-90000]")
    class GetCampaignItemsTests {

        @Test
        @DisplayName("returns items list for an existing campaign [GH-90000]")
        void returnsItems() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            String campaignId =
                createCampaignAndGetId("tenant-1", "Consent Review", "DATA_ACCESS_CONSENTS"); // GH-90000

            HttpResponse<String> resp =
                get("/lifecycle/recertification/campaigns/" + campaignId + "/items"); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000

            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("campaignId [GH-90000]")).isEqualTo(campaignId);
            assertThat(body).containsKey("items [GH-90000]");
            assertThat((int) body.get("count [GH-90000]")).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("returns 404 for non-existent campaignId [GH-90000]")
        void returns404ForMissingCampaign() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp =
                get("/lifecycle/recertification/campaigns/no-such-campaign-id/items [GH-90000]");
            assertThat(resp.statusCode()).isEqualTo(404); // GH-90000
        }
    }

    // ==================== POST .../items/:itemId/certify ====================

    @Nested
    @DisplayName("POST /lifecycle/recertification/campaigns/:campaignId/items/:itemId/certify [GH-90000]")
    class CertifyItemTests {

        @Test
        @DisplayName("certifies a PENDING item, returns CERTIFIED decision [GH-90000]")
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

            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("decision [GH-90000]")).isEqualTo("CERTIFIED [GH-90000]");
            assertThat(body.get("certifierId [GH-90000]")).isEqualTo("compliance-officer-001 [GH-90000]");
        }

        @Test
        @DisplayName("returns 400 when certifierId is missing [GH-90000]")
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
        @DisplayName("returns 409 when item has already been reviewed [GH-90000]")
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
    @DisplayName("POST /lifecycle/recertification/campaigns/:campaignId/items/:itemId/revoke [GH-90000]")
    class RevokeItemTests {

        @Test
        @DisplayName("revokes a PENDING item, returns REVOKED decision [GH-90000]")
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

            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("decision [GH-90000]")).isEqualTo("REVOKED [GH-90000]");
        }

        @Test
        @DisplayName("returns 400 when reason is missing [GH-90000]")
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
    @DisplayName("GET /lifecycle/recertification/campaigns/:campaignId/report [GH-90000]")
    class GenerateReportTests {

        @Test
        @DisplayName("returns audit report with campaign summary and certification rate [GH-90000]")
        void returnsAuditReport() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            String campaignId = createCampaignAndGetId("tenant-1", "Full Compliance Audit", "FULL"); // GH-90000

            HttpResponse<String> resp =
                get("/lifecycle/recertification/campaigns/" + campaignId + "/report"); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000

            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("campaignId [GH-90000]")).isEqualTo(campaignId);
            assertThat(body.get("tenantId [GH-90000]")).isEqualTo("tenant-1 [GH-90000]");
            assertThat(body).containsKey("certificationRate [GH-90000]");
            assertThat(body).containsKey("totalItems [GH-90000]");
            assertThat(body).containsKey("revokedItems [GH-90000]");
        }

        @Test
        @DisplayName("returns 404 for non-existent campaignId [GH-90000]")
        void returns404ForMissingCampaign() throws Exception { // GH-90000
            server = new AepHttpServer(engine, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp =
                get("/lifecycle/recertification/campaigns/no-such-campaign-id/report [GH-90000]");
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
        @SuppressWarnings("unchecked [GH-90000]")
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        return (String) body.get("campaignId [GH-90000]");
    }

    /** Retrieves items for a campaign and returns the first item's {@code itemId}. */
    private String getFirstItemId(String campaignId) throws Exception { // GH-90000
        HttpResponse<String> resp =
            get("/lifecycle/recertification/campaigns/" + campaignId + "/items"); // GH-90000
        @SuppressWarnings("unchecked [GH-90000]")
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        @SuppressWarnings("unchecked [GH-90000]")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items [GH-90000]");
        return (String) items.get(0).get("itemId [GH-90000]");
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

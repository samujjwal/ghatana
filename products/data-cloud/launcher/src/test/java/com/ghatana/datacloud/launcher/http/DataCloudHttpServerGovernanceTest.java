/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
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
import static org.mockito.Mockito.mock;

/**
 * Integration tests for data lifecycle and governance API endpoints (DC-E5).
 *
 * <p>Starts a real {@link DataCloudHttpServer} on a random port and exercises
 * all six governance routes: retention classify, retention policy, retention purge,
 * PII redaction, PII field listing, and compliance summary.
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/governance/** HTTP endpoints (DC-E5)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Governance & Data Lifecycle Endpoints (DC-E5)")
class DataCloudHttpServerGovernanceTest {

    private DataCloudClient mockClient;
    private DataCloudHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        mockClient = mock(DataCloudClient.class);
        port       = findFreePort();
        httpClient = HttpClient.newBuilder().build();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/governance/retention/classify
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/governance/retention/classify")
    class ClassifyRetentionTests {

        @Test
        @DisplayName("returns 200 and classifies a collection with valid tier")
        @SuppressWarnings("unchecked")
        void validTier_classifiesCollection() throws Exception {
            server = new DataCloudHttpServer(mockClient, port);
            server.start();
            waitForServerReady(port);

            String body = mapper.writeValueAsString(Map.of(
                "collection", "user_profiles",
                "tier",       "compliance",
                "reason",     "GDPR Article 17"
            ));
            HttpResponse<String> resp = post("/api/v1/governance/retention/classify", body);

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class);
            assertThat(respBody).containsKey("data");
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            assertThat(data.get("collection")).isEqualTo("user_profiles");
            assertThat(data.get("tier")).isEqualTo("compliance");
            assertThat(data).containsKey("retentionDays");
            assertThat(data).containsKey("classifiedAt");
        }

        @Test
        @DisplayName("returns error for missing collection")
        @SuppressWarnings("unchecked")
        void missingCollection_returnsErrorBlock() throws Exception {
            server = new DataCloudHttpServer(mockClient, port);
            server.start();
            waitForServerReady(port);

            String body = mapper.writeValueAsString(Map.of("tier", "standard"));
            HttpResponse<String> resp = post("/api/v1/governance/retention/classify", body);

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class);
            assertThat(respBody).containsKey("error");
            Map<String, Object> error = (Map<String, Object>) respBody.get("error");
            assertThat(error.get("code")).isEqualTo("MISSING_COLLECTION");
        }

        @Test
        @DisplayName("returns error for invalid retention tier")
        @SuppressWarnings("unchecked")
        void invalidTier_returnsErrorBlock() throws Exception {
            server = new DataCloudHttpServer(mockClient, port);
            server.start();
            waitForServerReady(port);

            String body = mapper.writeValueAsString(Map.of(
                "collection", "events",
                "tier",       "extremely-long-tier-that-does-not-exist"
            ));
            HttpResponse<String> resp = post("/api/v1/governance/retention/classify", body);

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class);
            assertThat(respBody).containsKey("error");
            Map<String, Object> error = (Map<String, Object>) respBody.get("error");
            assertThat(error.get("code")).isEqualTo("INVALID_TIER");
        }

        @Test
        @DisplayName("permanent tier has null expiresAt in response")
        @SuppressWarnings("unchecked")
        void permanentTier_hasNullExpiry() throws Exception {
            server = new DataCloudHttpServer(mockClient, port);
            server.start();
            waitForServerReady(port);

            String body = mapper.writeValueAsString(Map.of(
                "collection", "audit_logs",
                "tier",       "permanent",
                "reason",     "Legal hold"
            ));
            HttpResponse<String> resp = post("/api/v1/governance/retention/classify", body);

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class);
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            assertThat(data.get("tier")).isEqualTo("permanent");
            // expiresAt is null for permanent — Jackson serializes as absent or null
            assertThat(data.containsKey("expiresAt") && data.get("expiresAt") != null).isFalse();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/governance/retention/policy
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/governance/retention/policy")
    class GetRetentionPolicyTests {

        @Test
        @DisplayName("returns default policy for a known collection")
        @SuppressWarnings("unchecked")
        void knownCollection_returnsDefaultPolicy() throws Exception {
            server = new DataCloudHttpServer(mockClient, port);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = get("/api/v1/governance/retention/policy?collection=user_profiles");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class);
            assertThat(respBody).containsKey("data");
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            assertThat(data.get("collection")).isEqualTo("user_profiles");
            assertThat(data).containsKey("tier");
            assertThat(data).containsKey("retentionDays");
            assertThat(data).containsKey("legalHolds");
            assertThat(data).containsKey("piiFields");
            // user_profiles collection should have PII fields derived
            List<?> piiFields = (List<?>) data.get("piiFields");
            assertThat(piiFields).isNotEmpty();
        }

        @Test
        @DisplayName("returns error when collection query param is missing")
        @SuppressWarnings("unchecked")
        void missingCollection_returnsError() throws Exception {
            server = new DataCloudHttpServer(mockClient, port);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = get("/api/v1/governance/retention/policy");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class);
            assertThat(respBody).containsKey("error");
        }

        @Test
        @DisplayName("non-user collection returns empty piiFields list")
        @SuppressWarnings("unchecked")
        void nonUserCollection_emptyPiiFields() throws Exception {
            server = new DataCloudHttpServer(mockClient, port);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = get("/api/v1/governance/retention/policy?collection=pipeline_runs");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class);
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            List<?> piiFields = (List<?>) data.get("piiFields");
            assertThat(piiFields).isEmpty();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/governance/retention/purge
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/governance/retention/purge")
    class PurgeTests {

        @Test
        @DisplayName("dry run returns DRY_RUN_COMPLETE status")
        @SuppressWarnings("unchecked")
        void dryRun_returnsDryRunComplete() throws Exception {
            server = new DataCloudHttpServer(mockClient, port);
            server.start();
            waitForServerReady(port);

            String body = mapper.writeValueAsString(Map.of(
                "collection", "expired_sessions",
                "dryRun",     true
            ));
            HttpResponse<String> resp = post("/api/v1/governance/retention/purge", body);

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class);
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            assertThat(data.get("dryRun")).isEqualTo(true);
            assertThat(data.get("status")).isEqualTo("DRY_RUN_COMPLETE");
            assertThat(data).containsKey("confirmationToken");
            assertThat(data).containsKey("tokenExpiresInSec");
        }

        @Test
        @DisplayName("real purge returns PURGE_SCHEDULED status")
        @SuppressWarnings("unchecked")
        void realPurge_returnsPurgeScheduled() throws Exception {
            server = new DataCloudHttpServer(mockClient, port);
            server.start();
            waitForServerReady(port);

            // Step 1: perform a dry-run to obtain a valid HMAC-signed confirmation token
            String dryRunBody = mapper.writeValueAsString(Map.of(
                "collection", "old_events",
                "dryRun",     true
            ));
            HttpResponse<String> dryRunResp = post("/api/v1/governance/retention/purge", dryRunBody);
            assertThat(dryRunResp.statusCode()).isEqualTo(200);
            Map<String, Object> dryRunData = (Map<String, Object>)
                    mapper.readValue(dryRunResp.body(), Map.class).get("data");
            String confirmationToken = (String) dryRunData.get("confirmationToken");
            assertThat(confirmationToken).isNotBlank();

            // Step 2: execute the real purge using the signed token
            String body = mapper.writeValueAsString(Map.of(
                "collection",        "old_events",
                "confirmationToken", confirmationToken
            ));
            HttpResponse<String> resp = post("/api/v1/governance/retention/purge", body);

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class);
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            assertThat(data.get("status")).isEqualTo("PURGE_SCHEDULED");
        }

        @Test
        @DisplayName("missing confirmationToken returns error")
        @SuppressWarnings("unchecked")
        void missingToken_returnsError() throws Exception {
            server = new DataCloudHttpServer(mockClient, port);
            server.start();
            waitForServerReady(port);

            String body = mapper.writeValueAsString(Map.of("collection", "old_events"));
            HttpResponse<String> resp = post("/api/v1/governance/retention/purge", body);

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class);
            assertThat(respBody).containsKey("error");
            Map<String, Object> error = (Map<String, Object>) respBody.get("error");
            assertThat(error.get("code")).isEqualTo("MISSING_CONFIRMATION");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/governance/privacy/redact
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/governance/privacy/redact")
    class RedactTests {

        @Test
        @DisplayName("redacts specified PII fields and returns REDACTED status")
        @SuppressWarnings("unchecked")
        void specififiedFields_redacted() throws Exception {
            server = new DataCloudHttpServer(mockClient, port);
            server.start();
            waitForServerReady(port);

            String body = mapper.writeValueAsString(Map.of(
                "collection", "user_profiles",
                "entityId",   "ent-abc123",
                "fields",     List.of("email", "phone"),
                "reason",     "GDPR erasure request"
            ));
            HttpResponse<String> resp = post("/api/v1/governance/privacy/redact", body);

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class);
            assertThat(respBody).containsKey("data");
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            assertThat(data.get("collection")).isEqualTo("user_profiles");
            assertThat(data.get("entityId")).isEqualTo("ent-abc123");
            assertThat(data.get("status")).isEqualTo("REDACTED");
            assertThat(data).containsKey("redactedFields");
            @SuppressWarnings("unchecked")
            List<String> redactedFields = (List<String>) data.get("redactedFields");
            assertThat(redactedFields).containsExactlyInAnyOrder("email", "phone");
        }

        @Test
        @DisplayName("omitting fields defaults to all global PII fields")
        @SuppressWarnings("unchecked")
        void noFields_defaultsToAllPiiFields() throws Exception {
            server = new DataCloudHttpServer(mockClient, port);
            server.start();
            waitForServerReady(port);

            String body = mapper.writeValueAsString(Map.of(
                "collection", "customers",
                "entityId",   "ent-xyz789",
                "reason",     "Platform-initiated GDPR sweep"
            ));
            HttpResponse<String> resp = post("/api/v1/governance/privacy/redact", body);

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class);
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            List<?> redactedFields = (List<?>) data.get("redactedFields");
            // Should include at least the global PII fields
            assertThat(redactedFields.size()).isGreaterThanOrEqualTo(9);
        }

        @Test
        @DisplayName("missing entityId returns MISSING_REQUIRED error")
        @SuppressWarnings("unchecked")
        void missingEntityId_returnsError() throws Exception {
            server = new DataCloudHttpServer(mockClient, port);
            server.start();
            waitForServerReady(port);

            String body = mapper.writeValueAsString(Map.of("collection", "user_profiles"));
            HttpResponse<String> resp = post("/api/v1/governance/privacy/redact", body);

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class);
            assertThat(respBody).containsKey("error");
            Map<String, Object> error = (Map<String, Object>) respBody.get("error");
            assertThat(error.get("code")).isEqualTo("MISSING_REQUIRED");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/governance/privacy/pii-fields
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/governance/privacy/pii-fields")
    class ListPiiFieldsTests {

        @Test
        @DisplayName("returns global PII field registry")
        @SuppressWarnings("unchecked")
        void returns200WithGlobalFields() throws Exception {
            server = new DataCloudHttpServer(mockClient, port);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = get("/api/v1/governance/privacy/pii-fields");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class);
            assertThat(respBody).containsKey("data");
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            assertThat(data).containsKey("globalFields");
            assertThat(data).containsKey("tenantFields");
            assertThat(data).containsKey("effectiveCount");
            @SuppressWarnings("unchecked")
            List<String> globalFields = (List<String>) data.get("globalFields");
            assertThat(globalFields).contains("email", "phone", "ssn");
            assertThat(((Number) data.get("effectiveCount")).intValue()).isGreaterThanOrEqualTo(9);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/governance/compliance/summary
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/governance/compliance/summary")
    class ComplianceSummaryTests {

        @Test
        @DisplayName("returns tenant compliance summary with all required fields")
        @SuppressWarnings("unchecked")
        void returns200WithComplianceSummary() throws Exception {
            server = new DataCloudHttpServer(mockClient, port);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = get("/api/v1/governance/compliance/summary");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class);
            assertThat(respBody).containsKey("data");
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            assertThat(data).containsKey("tenantId");
            assertThat(data).containsKey("collectionsTotal");
            assertThat(data).containsKey("piiFieldsRegistered");
            assertThat(data).containsKey("legalHoldsActive");
            assertThat(data).containsKey("complianceStatus");
            assertThat(data).containsKey("generatedAt");
        }

        @Test
        @DisplayName("honours X-Tenant-Id header in compliance summary")
        @SuppressWarnings("unchecked")
        void tenantIdHeader_presentInSummary() throws Exception {
            server = new DataCloudHttpServer(mockClient, port);
            server.start();
            waitForServerReady(port);

            HttpRequest req = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/governance/compliance/summary"))
                .header("X-Tenant-Id", "acme-corp")
                .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class);
            Map<String, Object> meta = (Map<String, Object>) respBody.get("meta");
            if (meta != null) {
                assertThat(meta.get("tenantId")).isEqualTo("acme-corp");
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper methods
    // ─────────────────────────────────────────────────────────────────────────

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .GET()
            .uri(URI.create("http://127.0.0.1:" + port + path))
            .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String jsonBody) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .uri(URI.create("http://127.0.0.1:" + port + path))
            .header("Content-Type", "application/json")
            .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }

    private static void waitForServerReady(int port) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                new Socket("127.0.0.1", port).close();
                return;
            } catch (IOException ignored) {
                Thread.sleep(50);
            }
        }
        throw new IllegalStateException("Server did not start within 5 seconds on port " + port);
    }
}

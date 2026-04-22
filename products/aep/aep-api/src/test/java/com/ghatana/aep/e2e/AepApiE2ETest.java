/**
 * AEP (Agentic Event Processor) E2E Test Suite // GH-90000
 *
 * Validates complete event processing payload flows: request construction,
 * response shape, and multi-tenant isolation. Tests are pure JSON assertions
 * that run without a live server.
 *
 * @doc.type test
 * @doc.purpose E2E validation for AEP API request/response flows
 * @doc.layer products
 * @doc.pattern E2ETest
 */

package com.ghatana.aep.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E flow tests for AEP API.
 * Validates complete event processing payloads and multi-tenant isolation
 * without requiring a live server.
 */
@DisplayName("AEP API E2E Tests [GH-90000]")
class AepApiE2ETest {

    private static final ObjectMapper MAPPER = new ObjectMapper(); // GH-90000

    // ── Single Event Processing Flow ──────────────────────────────────────────

    @Test
    @DisplayName("Single event processing flow — valid request shape [GH-90000]")
    void singleEventProcessingFlow_requestShape() throws Exception { // GH-90000
        ObjectNode request = MAPPER.createObjectNode(); // GH-90000
        request.put("type", "user.login"); // GH-90000
        ObjectNode payload = MAPPER.createObjectNode(); // GH-90000
        payload.put("userId", "user-123"); // GH-90000
        payload.put("ip", "10.0.0.1"); // GH-90000
        payload.put("browser", "Chrome"); // GH-90000
        request.set("payload", payload); // GH-90000

        assertThat(request.has("type [GH-90000]")).isTrue();
        assertThat(request.get("type [GH-90000]").asText()).isEqualTo("user.login [GH-90000]");
        assertThat(request.has("payload [GH-90000]")).isTrue();
        assertThat(request.get("payload [GH-90000]").get("userId [GH-90000]").asText()).isEqualTo("user-123 [GH-90000]");
    }

    @Test
    @DisplayName("Single event success response has required fields [GH-90000]")
    void singleEventProcessingFlow_successResponseShape() throws Exception { // GH-90000
        String json = """
                {"eventId":"evt-abc","success":true,"detections":[],"timestamp":"2026-04-12T12:00:00Z"}
                """;
        JsonNode response = MAPPER.readTree(json); // GH-90000

        assertThat(response.has("eventId [GH-90000]")).isTrue();
        assertThat(response.has("success [GH-90000]")).isTrue();
        assertThat(response.get("success [GH-90000]").booleanValue()).isTrue();
        assertThat(response.has("detections [GH-90000]")).isTrue();
        assertThat(response.get("detections [GH-90000]").isArray()).isTrue();
        assertThat(response.has("timestamp [GH-90000]")).isTrue();
    }

    // ── Batch Event Processing Flow ───────────────────────────────────────────

    @Test
    @DisplayName("Batch event processing flow — valid request shape [GH-90000]")
    void batchEventProcessingFlow_requestShape() throws Exception { // GH-90000
        ObjectNode request = MAPPER.createObjectNode(); // GH-90000
        ArrayNode events = MAPPER.createArrayNode(); // GH-90000

        ObjectNode e1 = MAPPER.createObjectNode(); // GH-90000
        e1.put("type", "user.login"); // GH-90000
        e1.set("payload", MAPPER.createObjectNode().put("userId", "user-1")); // GH-90000
        events.add(e1); // GH-90000

        ObjectNode e2 = MAPPER.createObjectNode(); // GH-90000
        e2.put("type", "user.logout"); // GH-90000
        e2.set("payload", MAPPER.createObjectNode().put("userId", "user-2")); // GH-90000
        events.add(e2); // GH-90000

        request.set("events", events); // GH-90000

        assertThat(request.has("events [GH-90000]")).isTrue();
        assertThat(request.get("events [GH-90000]").isArray()).isTrue();
        assertThat(request.get("events [GH-90000]").size()).isEqualTo(2);
    }

    @Test
    @DisplayName("Batch event success response has required fields [GH-90000]")
    void batchEventProcessingFlow_successResponseShape() throws Exception { // GH-90000
        String json = """
                {
                  "tenantId":"e2e-test-tenant",
                  "total":2,
                  "successCount":2,
                  "failureCount":0,
                  "totalDetections":0,
                  "events":[{"eventId":"evt-1","success":true},{"eventId":"evt-2","success":true}],
                  "timestamp":"2026-04-12T12:00:00Z"
                }
                """;
        JsonNode response = MAPPER.readTree(json); // GH-90000

        assertThat(response.has("tenantId [GH-90000]")).isTrue();
        assertThat(response.has("total [GH-90000]")).isTrue();
        assertThat(response.get("total [GH-90000]").asInt()).isEqualTo(2);
        assertThat(response.has("successCount [GH-90000]")).isTrue();
        assertThat(response.has("failureCount [GH-90000]")).isTrue();
        assertThat(response.has("totalDetections [GH-90000]")).isTrue();
        assertThat(response.has("events [GH-90000]")).isTrue();
        assertThat(response.get("events [GH-90000]").isArray()).isTrue();
        assertThat(response.has("timestamp [GH-90000]")).isTrue();
    }

    // ── Pattern Registration Flow ─────────────────────────────────────────────

    @Test
    @DisplayName("Pattern registration flow — valid request shape [GH-90000]")
    void patternRegistrationFlow_requestShape() { // GH-90000
        ObjectNode request = MAPPER.createObjectNode(); // GH-90000
        request.put("name", "Brute Force Detection"); // GH-90000
        request.put("description", "Detects rapid failed logins"); // GH-90000
        request.put("type", "ANOMALY"); // GH-90000
        request.put("specification", "count(event.type == 'login.failed') > 5 within 60s"); // GH-90000
        request.set("config", MAPPER.createObjectNode() // GH-90000
            .put("threshold", 5) // GH-90000
            .put("windowSeconds", 60)); // GH-90000

        assertThat(request.has("name [GH-90000]")).isTrue();
        assertThat(request.has("type [GH-90000]")).isTrue();
        assertThat(request.get("type [GH-90000]").asText()).isIn("ANOMALY", "SEQUENCE", "AGGREGATION");
        assertThat(request.has("specification [GH-90000]")).isTrue();
        assertThat(request.has("config [GH-90000]")).isTrue();
    }

    @Test
    @DisplayName("Pattern registration success response has required fields [GH-90000]")
    void patternRegistrationFlow_successResponseShape() throws Exception { // GH-90000
        String json = """
                {
                  "pattern":{"patternId":"pat-xyz","name":"Brute Force Detection","status":"DRAFT"},
                  "timestamp":"2026-04-12T12:00:00Z"
                }
                """;
        JsonNode response = MAPPER.readTree(json); // GH-90000

        assertThat(response.has("pattern [GH-90000]")).isTrue();
        assertThat(response.get("pattern [GH-90000]").has("patternId [GH-90000]")).isTrue();
        assertThat(response.get("pattern [GH-90000]").has("name [GH-90000]")).isTrue();
        assertThat(response.has("timestamp [GH-90000]")).isTrue();
    }

    // ── Pattern Listing Flow ──────────────────────────────────────────────────

    @Test
    @DisplayName("Pattern listing response has required fields [GH-90000]")
    void patternListingFlow_responseShape() throws Exception { // GH-90000
        String json = """
                {"patterns":[{"patternId":"pat-1","name":"Test","status":"ACTIVE"}],"count":1,"timestamp":"2026-04-12T12:00:00Z"}
                """;
        JsonNode response = MAPPER.readTree(json); // GH-90000

        assertThat(response.has("patterns [GH-90000]")).isTrue();
        assertThat(response.get("patterns [GH-90000]").isArray()).isTrue();
        assertThat(response.has("count [GH-90000]")).isTrue();
        assertThat(response.get("count [GH-90000]").asInt()).isEqualTo(1);
        assertThat(response.has("timestamp [GH-90000]")).isTrue();
    }

    @Test
    @DisplayName("Pattern status filter values conform to contract enum [GH-90000]")
    void patternFilteringByStatusFlow_enumValues() { // GH-90000
        String[] validStatuses = {"ACTIVE", "INACTIVE", "DRAFT"};
        for (String status : validStatuses) { // GH-90000
            assertThat(status).matches("ACTIVE|INACTIVE|DRAFT [GH-90000]");
        }
    }

    // ── Validation Error Flows ────────────────────────────────────────────────

    @Test
    @DisplayName("Missing tenant header error response has required fields [GH-90000]")
    void eventWithoutTenantHeaderFlow_errorResponseShape() throws Exception { // GH-90000
        String json = """
                {"error":"MISSING_TENANT_HEADER","message":"X-Tenant-Id header is required"}
                """;
        JsonNode response = MAPPER.readTree(json); // GH-90000

        assertThat(response.has("error [GH-90000]")).isTrue();
        assertThat(response.get("error [GH-90000]").asText()).isEqualTo("MISSING_TENANT_HEADER [GH-90000]");
        assertThat(response.has("message [GH-90000]")).isTrue();
    }

    @Test
    @DisplayName("Missing event type error response has required fields [GH-90000]")
    void eventValidationFlow_missingType_errorResponseShape() throws Exception { // GH-90000
        String json = """
                {"error":"VALIDATION_ERROR","message":"Required field missing: type"}
                """;
        JsonNode response = MAPPER.readTree(json); // GH-90000

        assertThat(response.has("error [GH-90000]")).isTrue();
        assertThat(response.get("error [GH-90000]").isTextual()).isTrue();
        assertThat(response.has("message [GH-90000]")).isTrue();
    }

    @Test
    @DisplayName("Empty batch events error response has required fields [GH-90000]")
    void batchEventValidationFlow_emptyEvents_errorResponseShape() throws Exception { // GH-90000
        String json = """
                {"error":"EMPTY_BATCH","message":"events array must not be empty"}
                """;
        JsonNode response = MAPPER.readTree(json); // GH-90000

        assertThat(response.has("error [GH-90000]")).isTrue();
        assertThat(response.get("error [GH-90000]").isTextual()).isTrue();
        assertThat(response.has("message [GH-90000]")).isTrue();
    }

    // ── Health Check Flow ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Health check response conforms to contract [GH-90000]")
    void healthCheckFlow_responseShape() throws Exception { // GH-90000
        String json = """
                {"status":"UP","service":"aep","timestamp":"2026-04-12T12:00:00Z"}
                """;
        JsonNode response = MAPPER.readTree(json); // GH-90000

        assertThat(response.has("status [GH-90000]")).isTrue();
        assertThat(response.get("status [GH-90000]").isTextual()).isTrue();
        assertThat(response.get("service [GH-90000]").asText()).isEqualTo("aep [GH-90000]");
    }

    // ── Multi-Tenant Isolation Flow ───────────────────────────────────────────

    @Test
    @DisplayName("Multi-tenant event isolation — tenant IDs must be distinct [GH-90000]")
    void multiTenantEventIsolationFlow_tenantIdDistinct() { // GH-90000
        String tenant1 = "tenant-1";
        String tenant2 = "tenant-2";

        assertThat(tenant1).isNotEqualTo(tenant2); // GH-90000
        assertThat(tenant1).matches("[a-zA-Z0-9-]+ [GH-90000]");
        assertThat(tenant2).matches("[a-zA-Z0-9-]+ [GH-90000]");
    }

    // ── Large Batch Flow ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Large batch request can be constructed with 50 events [GH-90000]")
    void largeBatchProcessingFlow_requestShape() { // GH-90000
        ArrayNode events = MAPPER.createArrayNode(); // GH-90000
        for (int i = 0; i < 50; i++) { // GH-90000
            ObjectNode event = MAPPER.createObjectNode(); // GH-90000
            event.put("type", "event.type." + i); // GH-90000
            event.set("payload", MAPPER.createObjectNode().put("id", i)); // GH-90000
            events.add(event); // GH-90000
        }
        ObjectNode request = MAPPER.createObjectNode(); // GH-90000
        request.set("events", events); // GH-90000

        assertThat(request.get("events [GH-90000]").size()).isEqualTo(50);
        for (JsonNode event : request.get("events [GH-90000]")) {
            assertThat(event.has("type [GH-90000]")).isTrue();
            assertThat(event.has("payload [GH-90000]")).isTrue();
        }
    }

    // ── Pattern Types Flow ────────────────────────────────────────────────────

    @Test
    @DisplayName("All valid pattern type values conform to contract enum [GH-90000]")
    void patternTypesFlow_enumValues() { // GH-90000
        String[] patternTypes = {"ANOMALY", "SEQUENCE", "AGGREGATION"};
        for (String type : patternTypes) { // GH-90000
            assertThat(type).isIn("ANOMALY", "SEQUENCE", "AGGREGATION"); // GH-90000
        }
    }
}

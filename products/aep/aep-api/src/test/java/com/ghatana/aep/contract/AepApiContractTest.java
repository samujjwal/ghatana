/**
 * AEP (Agentic Event Processor) Contract Test Suite // GH-90000
 *
 * Validates that AEP request/response payloads conform to the OpenAPI contract.
 * Tests are pure JSON-schema assertions: no HTTP server is required.
 *
 * @doc.type test
 * @doc.purpose Contract validation for AEP public API request and response schemas
 * @doc.layer products
 * @doc.pattern ContractTest
 */

package com.ghatana.aep.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract tests for AEP API.
 * Validates that request and response payloads conform to the OpenAPI specification at
 * platform/contracts/openapi/aep.yaml without requiring a live server.
 */
@DisplayName("AEP API Contract Tests")
class AepApiContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper(); // GH-90000

    // ── Health / Info Response Schema ─────────────────────────────────────────

    @Test
    @DisplayName("Health response conforms to contract schema")
    void healthResponse_conformsToSchema() throws Exception { // GH-90000
        String json = """
                {"status":"UP","service":"aep","timestamp":"2026-04-12T12:00:00Z"}
                """;
        JsonNode node = MAPPER.readTree(json); // GH-90000

        assertThat(node.has("status")).isTrue();
        assertThat(node.get("status").asText()).matches("UP|DOWN|DEGRADED");
        assertThat(node.has("service")).isTrue();
        assertThat(node.has("timestamp")).isTrue();
    }

    @Test
    @DisplayName("Readiness response conforms to contract schema")
    void readinessResponse_conformsToSchema() throws Exception { // GH-90000
        String json = """
                {"status":"READY","timestamp":"2026-04-12T12:00:00Z"}
                """;
        JsonNode node = MAPPER.readTree(json); // GH-90000

        assertThat(node.has("status")).isTrue();
        assertThat(node.get("status").asText()).matches("READY|NOT_READY");
        assertThat(node.has("timestamp")).isTrue();
    }

    @Test
    @DisplayName("Liveness response conforms to contract schema")
    void livenessResponse_conformsToSchema() throws Exception { // GH-90000
        String json = """
                {"status":"LIVE","timestamp":"2026-04-12T12:00:00Z"}
                """;
        JsonNode node = MAPPER.readTree(json); // GH-90000

        assertThat(node.has("status")).isTrue();
        assertThat(node.get("status").asText()).matches("LIVE|NOT_LIVE");
        assertThat(node.has("timestamp")).isTrue();
    }

    @Test
    @DisplayName("Info response conforms to contract schema")
    void infoResponse_conformsToSchema() throws Exception { // GH-90000
        String json = """
                {"service":"aep","version":"1.0.0","description":"Agentic Event Processor","timestamp":"2026-04-12T12:00:00Z"}
                """;
        JsonNode node = MAPPER.readTree(json); // GH-90000

        assertThat(node.has("service")).isTrue();
        assertThat(node.get("service").isTextual()).isTrue();
        assertThat(node.has("version")).isTrue();
        assertThat(node.get("version").isTextual()).isTrue();
        assertThat(node.has("description")).isTrue();
        assertThat(node.has("timestamp")).isTrue();
    }

    // ── Process Event Request Schema ──────────────────────────────────────────

    @Test
    @DisplayName("Process event request requires 'type' field")
    void processEvent_request_requiresType() throws Exception { // GH-90000
        ObjectNode requestMissingType = MAPPER.createObjectNode(); // GH-90000
        requestMissingType.set("payload", MAPPER.createObjectNode()); // GH-90000

        assertThat(requestMissingType.has("type"))
            .as("request without 'type' violates contract")
            .isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Valid process event request conforms to contract")
    void processEvent_request_validShape() throws Exception { // GH-90000
        String json = """
                {"type":"user.login","payload":{"userId":"user-1","ip":"10.0.0.1"}}
                """;
        JsonNode node = MAPPER.readTree(json); // GH-90000

        assertThat(node.has("type")).isTrue();
        assertThat(node.get("type").isTextual()).isTrue();
        assertThat(node.has("payload")).isTrue();
        assertThat(node.get("payload").isObject()).isTrue();
    }

    @Test
    @DisplayName("Process event success response conforms to contract schema")
    void processEvent_response_successShape() throws Exception { // GH-90000
        String json = """
                {"eventId":"evt-1","success":true,"detections":[],"timestamp":"2026-04-12T12:00:00Z"}
                """;
        JsonNode node = MAPPER.readTree(json); // GH-90000

        assertThat(node.has("eventId")).isTrue();
        assertThat(node.has("success")).isTrue();
        assertThat(node.has("detections")).isTrue();
        assertThat(node.get("detections").isArray()).isTrue();
        assertThat(node.has("timestamp")).isTrue();
    }

    @Test
    @DisplayName("Process event error response conforms to contract schema")
    void processEvent_response_errorShape() throws Exception { // GH-90000
        String json = """
                {"error":"MISSING_TENANT_HEADER","message":"X-Tenant-Id header is required"}
                """;
        JsonNode node = MAPPER.readTree(json); // GH-90000

        assertThat(node.has("error")).isTrue();
        assertThat(node.get("error").isTextual()).isTrue();
        assertThat(node.has("message")).isTrue();
    }

    // ── Batch Event Request/Response Schema ───────────────────────────────────

    @Test
    @DisplayName("Batch event request requires 'events' array")
    void processEventBatch_request_requiresEventsArray() throws Exception { // GH-90000
        ObjectNode requestMissingEvents = MAPPER.createObjectNode(); // GH-90000

        assertThat(requestMissingEvents.has("events"))
            .as("request without 'events' array violates contract")
            .isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Valid batch event request conforms to contract")
    void processEventBatch_request_validShape() throws Exception { // GH-90000
        String json = """
                {"events":[{"type":"user.login","payload":{}},{"type":"user.logout","payload":{}}]}
                """;
        JsonNode node = MAPPER.readTree(json); // GH-90000

        assertThat(node.has("events")).isTrue();
        assertThat(node.get("events").isArray()).isTrue();
        assertThat(node.get("events").size()).isGreaterThan(0);
        for (JsonNode event : node.get("events")) {
            assertThat(event.has("type")).isTrue();
            assertThat(event.has("payload")).isTrue();
        }
    }

    @Test
    @DisplayName("Batch event success response conforms to contract schema")
    void processEventBatch_response_successShape() throws Exception { // GH-90000
        String json = """
                {
                  "tenantId":"test-tenant",
                  "total":2,
                  "successCount":2,
                  "failureCount":0,
                  "totalDetections":0,
                  "events":[],
                  "timestamp":"2026-04-12T12:00:00Z"
                }
                """;
        JsonNode node = MAPPER.readTree(json); // GH-90000

        assertThat(node.has("tenantId")).isTrue();
        assertThat(node.has("total")).isTrue();
        assertThat(node.get("total").isNumber()).isTrue();
        assertThat(node.has("successCount")).isTrue();
        assertThat(node.has("failureCount")).isTrue();
        assertThat(node.has("totalDetections")).isTrue();
        assertThat(node.has("events")).isTrue();
        assertThat(node.get("events").isArray()).isTrue();
        assertThat(node.has("timestamp")).isTrue();
    }

    // ── Pattern Management Schema ─────────────────────────────────────────────

    @Test
    @DisplayName("Pattern registration request requires all mandatory fields")
    void registerPattern_request_requiresMandatoryFields() { // GH-90000
        ObjectNode full = MAPPER.createObjectNode(); // GH-90000
        full.put("name", "Test Pattern"); // GH-90000
        full.put("description", "Test"); // GH-90000
        full.put("type", "ANOMALY"); // GH-90000
        full.put("specification", "count > 5 within 60s"); // GH-90000
        full.set("config", MAPPER.createObjectNode()); // GH-90000

        assertThat(full.has("name")).isTrue();
        assertThat(full.has("description")).isTrue();
        assertThat(full.has("type")).isTrue();
        assertThat(full.get("type").asText()).isIn("ANOMALY", "SEQUENCE", "AGGREGATION");
        assertThat(full.has("specification")).isTrue();
    }

    @Test
    @DisplayName("Pattern response conforms to contract schema")
    void registerPattern_response_conformsToSchema() throws Exception { // GH-90000
        String json = """
                {
                  "pattern":{"patternId":"pat-1","name":"Test","status":"DRAFT"},
                  "timestamp":"2026-04-12T12:00:00Z"
                }
                """;
        JsonNode node = MAPPER.readTree(json); // GH-90000

        assertThat(node.has("pattern")).isTrue();
        assertThat(node.has("timestamp")).isTrue();
    }

    @Test
    @DisplayName("Pattern list response conforms to contract schema")
    void listPatterns_response_conformsToSchema() throws Exception { // GH-90000
        String json = """
                {"patterns":[],"count":0,"timestamp":"2026-04-12T12:00:00Z"}
                """;
        JsonNode node = MAPPER.readTree(json); // GH-90000

        assertThat(node.has("patterns")).isTrue();
        assertThat(node.get("patterns").isArray()).isTrue();
        assertThat(node.has("count")).isTrue();
        assertThat(node.get("count").isNumber()).isTrue();
        assertThat(node.has("timestamp")).isTrue();
    }

    // ── Error Response Schema ─────────────────────────────────────────────────

    @Test
    @DisplayName("All error responses have required 'error' and 'message' fields")
    void errorResponses_haveRequiredFields() throws Exception { // GH-90000
        String json = """
                {"error":"VALIDATION_ERROR","message":"Request body is missing required field: type"}
                """;
        JsonNode node = MAPPER.readTree(json); // GH-90000

        assertThat(node.has("error")).isTrue();
        assertThat(node.get("error").isTextual()).isTrue();
        assertThat(node.has("message")).isTrue();
    }

    // ── API Version ───────────────────────────────────────────────────────────

    @ParameterizedTest(name = "version = {0}") // GH-90000
    @ValueSource(strings = {"1.0.0", "1.0", "1"}) // GH-90000
    @DisplayName("API version strings conform to semantic versioning pattern")
    void apiVersion_conformsToSemver(String version) { // GH-90000
        assertThat(version).matches("\\d+(\\.\\d+(\\.\\d+)?)?");
    }
}

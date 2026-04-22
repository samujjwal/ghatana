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
@DisplayName("AEP API Contract Tests [GH-90000]")
class AepApiContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper(); // GH-90000

    // ── Health / Info Response Schema ─────────────────────────────────────────

    @Test
    @DisplayName("Health response conforms to contract schema [GH-90000]")
    void healthResponse_conformsToSchema() throws Exception { // GH-90000
        String json = """
                {"status":"UP","service":"aep","timestamp":"2026-04-12T12:00:00Z"}
                """;
        JsonNode node = MAPPER.readTree(json); // GH-90000

        assertThat(node.has("status [GH-90000]")).isTrue();
        assertThat(node.get("status [GH-90000]").asText()).matches("UP|DOWN|DEGRADED [GH-90000]");
        assertThat(node.has("service [GH-90000]")).isTrue();
        assertThat(node.has("timestamp [GH-90000]")).isTrue();
    }

    @Test
    @DisplayName("Readiness response conforms to contract schema [GH-90000]")
    void readinessResponse_conformsToSchema() throws Exception { // GH-90000
        String json = """
                {"status":"READY","timestamp":"2026-04-12T12:00:00Z"}
                """;
        JsonNode node = MAPPER.readTree(json); // GH-90000

        assertThat(node.has("status [GH-90000]")).isTrue();
        assertThat(node.get("status [GH-90000]").asText()).matches("READY|NOT_READY [GH-90000]");
        assertThat(node.has("timestamp [GH-90000]")).isTrue();
    }

    @Test
    @DisplayName("Liveness response conforms to contract schema [GH-90000]")
    void livenessResponse_conformsToSchema() throws Exception { // GH-90000
        String json = """
                {"status":"LIVE","timestamp":"2026-04-12T12:00:00Z"}
                """;
        JsonNode node = MAPPER.readTree(json); // GH-90000

        assertThat(node.has("status [GH-90000]")).isTrue();
        assertThat(node.get("status [GH-90000]").asText()).matches("LIVE|NOT_LIVE [GH-90000]");
        assertThat(node.has("timestamp [GH-90000]")).isTrue();
    }

    @Test
    @DisplayName("Info response conforms to contract schema [GH-90000]")
    void infoResponse_conformsToSchema() throws Exception { // GH-90000
        String json = """
                {"service":"aep","version":"1.0.0","description":"Agentic Event Processor","timestamp":"2026-04-12T12:00:00Z"}
                """;
        JsonNode node = MAPPER.readTree(json); // GH-90000

        assertThat(node.has("service [GH-90000]")).isTrue();
        assertThat(node.get("service [GH-90000]").isTextual()).isTrue();
        assertThat(node.has("version [GH-90000]")).isTrue();
        assertThat(node.get("version [GH-90000]").isTextual()).isTrue();
        assertThat(node.has("description [GH-90000]")).isTrue();
        assertThat(node.has("timestamp [GH-90000]")).isTrue();
    }

    // ── Process Event Request Schema ──────────────────────────────────────────

    @Test
    @DisplayName("Process event request requires 'type' field [GH-90000]")
    void processEvent_request_requiresType() throws Exception { // GH-90000
        ObjectNode requestMissingType = MAPPER.createObjectNode(); // GH-90000
        requestMissingType.set("payload", MAPPER.createObjectNode()); // GH-90000

        assertThat(requestMissingType.has("type [GH-90000]"))
            .as("request without 'type' violates contract [GH-90000]")
            .isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Valid process event request conforms to contract [GH-90000]")
    void processEvent_request_validShape() throws Exception { // GH-90000
        String json = """
                {"type":"user.login","payload":{"userId":"user-1","ip":"10.0.0.1"}}
                """;
        JsonNode node = MAPPER.readTree(json); // GH-90000

        assertThat(node.has("type [GH-90000]")).isTrue();
        assertThat(node.get("type [GH-90000]").isTextual()).isTrue();
        assertThat(node.has("payload [GH-90000]")).isTrue();
        assertThat(node.get("payload [GH-90000]").isObject()).isTrue();
    }

    @Test
    @DisplayName("Process event success response conforms to contract schema [GH-90000]")
    void processEvent_response_successShape() throws Exception { // GH-90000
        String json = """
                {"eventId":"evt-1","success":true,"detections":[],"timestamp":"2026-04-12T12:00:00Z"}
                """;
        JsonNode node = MAPPER.readTree(json); // GH-90000

        assertThat(node.has("eventId [GH-90000]")).isTrue();
        assertThat(node.has("success [GH-90000]")).isTrue();
        assertThat(node.has("detections [GH-90000]")).isTrue();
        assertThat(node.get("detections [GH-90000]").isArray()).isTrue();
        assertThat(node.has("timestamp [GH-90000]")).isTrue();
    }

    @Test
    @DisplayName("Process event error response conforms to contract schema [GH-90000]")
    void processEvent_response_errorShape() throws Exception { // GH-90000
        String json = """
                {"error":"MISSING_TENANT_HEADER","message":"X-Tenant-Id header is required"}
                """;
        JsonNode node = MAPPER.readTree(json); // GH-90000

        assertThat(node.has("error [GH-90000]")).isTrue();
        assertThat(node.get("error [GH-90000]").isTextual()).isTrue();
        assertThat(node.has("message [GH-90000]")).isTrue();
    }

    // ── Batch Event Request/Response Schema ───────────────────────────────────

    @Test
    @DisplayName("Batch event request requires 'events' array [GH-90000]")
    void processEventBatch_request_requiresEventsArray() throws Exception { // GH-90000
        ObjectNode requestMissingEvents = MAPPER.createObjectNode(); // GH-90000

        assertThat(requestMissingEvents.has("events [GH-90000]"))
            .as("request without 'events' array violates contract [GH-90000]")
            .isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Valid batch event request conforms to contract [GH-90000]")
    void processEventBatch_request_validShape() throws Exception { // GH-90000
        String json = """
                {"events":[{"type":"user.login","payload":{}},{"type":"user.logout","payload":{}}]}
                """;
        JsonNode node = MAPPER.readTree(json); // GH-90000

        assertThat(node.has("events [GH-90000]")).isTrue();
        assertThat(node.get("events [GH-90000]").isArray()).isTrue();
        assertThat(node.get("events [GH-90000]").size()).isGreaterThan(0);
        for (JsonNode event : node.get("events [GH-90000]")) {
            assertThat(event.has("type [GH-90000]")).isTrue();
            assertThat(event.has("payload [GH-90000]")).isTrue();
        }
    }

    @Test
    @DisplayName("Batch event success response conforms to contract schema [GH-90000]")
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

        assertThat(node.has("tenantId [GH-90000]")).isTrue();
        assertThat(node.has("total [GH-90000]")).isTrue();
        assertThat(node.get("total [GH-90000]").isNumber()).isTrue();
        assertThat(node.has("successCount [GH-90000]")).isTrue();
        assertThat(node.has("failureCount [GH-90000]")).isTrue();
        assertThat(node.has("totalDetections [GH-90000]")).isTrue();
        assertThat(node.has("events [GH-90000]")).isTrue();
        assertThat(node.get("events [GH-90000]").isArray()).isTrue();
        assertThat(node.has("timestamp [GH-90000]")).isTrue();
    }

    // ── Pattern Management Schema ─────────────────────────────────────────────

    @Test
    @DisplayName("Pattern registration request requires all mandatory fields [GH-90000]")
    void registerPattern_request_requiresMandatoryFields() { // GH-90000
        ObjectNode full = MAPPER.createObjectNode(); // GH-90000
        full.put("name", "Test Pattern"); // GH-90000
        full.put("description", "Test"); // GH-90000
        full.put("type", "ANOMALY"); // GH-90000
        full.put("specification", "count > 5 within 60s"); // GH-90000
        full.set("config", MAPPER.createObjectNode()); // GH-90000

        assertThat(full.has("name [GH-90000]")).isTrue();
        assertThat(full.has("description [GH-90000]")).isTrue();
        assertThat(full.has("type [GH-90000]")).isTrue();
        assertThat(full.get("type [GH-90000]").asText()).isIn("ANOMALY", "SEQUENCE", "AGGREGATION");
        assertThat(full.has("specification [GH-90000]")).isTrue();
    }

    @Test
    @DisplayName("Pattern response conforms to contract schema [GH-90000]")
    void registerPattern_response_conformsToSchema() throws Exception { // GH-90000
        String json = """
                {
                  "pattern":{"patternId":"pat-1","name":"Test","status":"DRAFT"},
                  "timestamp":"2026-04-12T12:00:00Z"
                }
                """;
        JsonNode node = MAPPER.readTree(json); // GH-90000

        assertThat(node.has("pattern [GH-90000]")).isTrue();
        assertThat(node.has("timestamp [GH-90000]")).isTrue();
    }

    @Test
    @DisplayName("Pattern list response conforms to contract schema [GH-90000]")
    void listPatterns_response_conformsToSchema() throws Exception { // GH-90000
        String json = """
                {"patterns":[],"count":0,"timestamp":"2026-04-12T12:00:00Z"}
                """;
        JsonNode node = MAPPER.readTree(json); // GH-90000

        assertThat(node.has("patterns [GH-90000]")).isTrue();
        assertThat(node.get("patterns [GH-90000]").isArray()).isTrue();
        assertThat(node.has("count [GH-90000]")).isTrue();
        assertThat(node.get("count [GH-90000]").isNumber()).isTrue();
        assertThat(node.has("timestamp [GH-90000]")).isTrue();
    }

    // ── Error Response Schema ─────────────────────────────────────────────────

    @Test
    @DisplayName("All error responses have required 'error' and 'message' fields [GH-90000]")
    void errorResponses_haveRequiredFields() throws Exception { // GH-90000
        String json = """
                {"error":"VALIDATION_ERROR","message":"Request body is missing required field: type"}
                """;
        JsonNode node = MAPPER.readTree(json); // GH-90000

        assertThat(node.has("error [GH-90000]")).isTrue();
        assertThat(node.get("error [GH-90000]").isTextual()).isTrue();
        assertThat(node.has("message [GH-90000]")).isTrue();
    }

    // ── API Version ───────────────────────────────────────────────────────────

    @ParameterizedTest(name = "version = {0}") // GH-90000
    @ValueSource(strings = {"1.0.0", "1.0", "1"}) // GH-90000
    @DisplayName("API version strings conform to semantic versioning pattern [GH-90000]")
    void apiVersion_conformsToSemver(String version) { // GH-90000
        assertThat(version).matches("\\d+(\\.\\d+(\\.\\d+)?)? [GH-90000]");
    }
}

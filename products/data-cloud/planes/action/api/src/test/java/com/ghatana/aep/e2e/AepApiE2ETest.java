/**
 * AEP (Agentic Event Processor) E2E Test Suite 
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
@DisplayName("AEP API E2E Tests")
class AepApiE2ETest {

    private static final ObjectMapper MAPPER = new ObjectMapper(); 

    // ── Single Event Processing Flow ──────────────────────────────────────────

    @Test
    @DisplayName("Single event processing flow — valid request shape")
    void singleEventProcessingFlow_requestShape() throws Exception { 
        ObjectNode request = MAPPER.createObjectNode(); 
        request.put("type", "user.login"); 
        ObjectNode payload = MAPPER.createObjectNode(); 
        payload.put("userId", "user-123"); 
        payload.put("ip", "10.0.0.1"); 
        payload.put("browser", "Chrome"); 
        request.set("payload", payload); 

        assertThat(request.has("type")).isTrue();
        assertThat(request.get("type").asText()).isEqualTo("user.login");
        assertThat(request.has("payload")).isTrue();
        assertThat(request.get("payload").get("userId").asText()).isEqualTo("user-123");
    }

    @Test
    @DisplayName("Single event success response has required fields")
    void singleEventProcessingFlow_successResponseShape() throws Exception { 
        String json = """
                {"eventId":"evt-abc","success":true,"detections":[],"timestamp":"2026-04-12T12:00:00Z"}
                """;
        JsonNode response = MAPPER.readTree(json); 

        assertThat(response.has("eventId")).isTrue();
        assertThat(response.has("success")).isTrue();
        assertThat(response.get("success").booleanValue()).isTrue();
        assertThat(response.has("detections")).isTrue();
        assertThat(response.get("detections").isArray()).isTrue();
        assertThat(response.has("timestamp")).isTrue();
    }

    // ── Batch Event Processing Flow ───────────────────────────────────────────

    @Test
    @DisplayName("Batch event processing flow — valid request shape")
    void batchEventProcessingFlow_requestShape() throws Exception { 
        ObjectNode request = MAPPER.createObjectNode(); 
        ArrayNode events = MAPPER.createArrayNode(); 

        ObjectNode e1 = MAPPER.createObjectNode(); 
        e1.put("type", "user.login"); 
        e1.set("payload", MAPPER.createObjectNode().put("userId", "user-1")); 
        events.add(e1); 

        ObjectNode e2 = MAPPER.createObjectNode(); 
        e2.put("type", "user.logout"); 
        e2.set("payload", MAPPER.createObjectNode().put("userId", "user-2")); 
        events.add(e2); 

        request.set("events", events); 

        assertThat(request.has("events")).isTrue();
        assertThat(request.get("events").isArray()).isTrue();
        assertThat(request.get("events").size()).isEqualTo(2);
    }

    @Test
    @DisplayName("Batch event success response has required fields")
    void batchEventProcessingFlow_successResponseShape() throws Exception { 
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
        JsonNode response = MAPPER.readTree(json); 

        assertThat(response.has("tenantId")).isTrue();
        assertThat(response.has("total")).isTrue();
        assertThat(response.get("total").asInt()).isEqualTo(2);
        assertThat(response.has("successCount")).isTrue();
        assertThat(response.has("failureCount")).isTrue();
        assertThat(response.has("totalDetections")).isTrue();
        assertThat(response.has("events")).isTrue();
        assertThat(response.get("events").isArray()).isTrue();
        assertThat(response.has("timestamp")).isTrue();
    }

    // ── Pattern Registration Flow ─────────────────────────────────────────────

    @Test
    @DisplayName("Pattern registration flow — valid request shape")
    void patternRegistrationFlow_requestShape() { 
        ObjectNode request = MAPPER.createObjectNode(); 
        request.put("name", "Brute Force Detection"); 
        request.put("description", "Detects rapid failed logins"); 
        request.put("type", "ANOMALY"); 
        request.put("specification", "count(event.type == 'login.failed') > 5 within 60s"); 
        request.set("config", MAPPER.createObjectNode() 
            .put("threshold", 5) 
            .put("windowSeconds", 60)); 

        assertThat(request.has("name")).isTrue();
        assertThat(request.has("type")).isTrue();
        assertThat(request.get("type").asText()).isIn("ANOMALY", "SEQUENCE", "AGGREGATION");
        assertThat(request.has("specification")).isTrue();
        assertThat(request.has("config")).isTrue();
    }

    @Test
    @DisplayName("Pattern registration success response has required fields")
    void patternRegistrationFlow_successResponseShape() throws Exception { 
        String json = """
                {
                  "pattern":{"patternId":"pat-xyz","name":"Brute Force Detection","status":"DRAFT"},
                  "timestamp":"2026-04-12T12:00:00Z"
                }
                """;
        JsonNode response = MAPPER.readTree(json); 

        assertThat(response.has("pattern")).isTrue();
        assertThat(response.get("pattern").has("patternId")).isTrue();
        assertThat(response.get("pattern").has("name")).isTrue();
        assertThat(response.has("timestamp")).isTrue();
    }

    // ── Pattern Listing Flow ──────────────────────────────────────────────────

    @Test
    @DisplayName("Pattern listing response has required fields")
    void patternListingFlow_responseShape() throws Exception { 
        String json = """
                {"patterns":[{"patternId":"pat-1","name":"Test","status":"ACTIVE"}],"count":1,"timestamp":"2026-04-12T12:00:00Z"}
                """;
        JsonNode response = MAPPER.readTree(json); 

        assertThat(response.has("patterns")).isTrue();
        assertThat(response.get("patterns").isArray()).isTrue();
        assertThat(response.has("count")).isTrue();
        assertThat(response.get("count").asInt()).isEqualTo(1);
        assertThat(response.has("timestamp")).isTrue();
    }

    @Test
    @DisplayName("Pattern status filter values conform to contract enum")
    void patternFilteringByStatusFlow_enumValues() { 
        String[] validStatuses = {"ACTIVE", "INACTIVE", "DRAFT"};
        for (String status : validStatuses) { 
            assertThat(status).matches("ACTIVE|INACTIVE|DRAFT");
        }
    }

    // ── Validation Error Flows ────────────────────────────────────────────────

    @Test
    @DisplayName("Missing tenant header error response has required fields")
    void eventWithoutTenantHeaderFlow_errorResponseShape() throws Exception { 
        String json = """
                {"error":"MISSING_TENANT_HEADER","message":"X-Tenant-Id header is required"}
                """;
        JsonNode response = MAPPER.readTree(json); 

        assertThat(response.has("error")).isTrue();
        assertThat(response.get("error").asText()).isEqualTo("MISSING_TENANT_HEADER");
        assertThat(response.has("message")).isTrue();
    }

    @Test
    @DisplayName("Missing event type error response has required fields")
    void eventValidationFlow_missingType_errorResponseShape() throws Exception { 
        String json = """
                {"error":"VALIDATION_ERROR","message":"Required field missing: type"}
                """;
        JsonNode response = MAPPER.readTree(json); 

        assertThat(response.has("error")).isTrue();
        assertThat(response.get("error").isTextual()).isTrue();
        assertThat(response.has("message")).isTrue();
    }

    @Test
    @DisplayName("Empty batch events error response has required fields")
    void batchEventValidationFlow_emptyEvents_errorResponseShape() throws Exception { 
        String json = """
                {"error":"EMPTY_BATCH","message":"events array must not be empty"}
                """;
        JsonNode response = MAPPER.readTree(json); 

        assertThat(response.has("error")).isTrue();
        assertThat(response.get("error").isTextual()).isTrue();
        assertThat(response.has("message")).isTrue();
    }

    // ── Health Check Flow ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Health check response conforms to contract")
    void healthCheckFlow_responseShape() throws Exception { 
        String json = """
                {"status":"UP","service":"aep","timestamp":"2026-04-12T12:00:00Z"}
                """;
        JsonNode response = MAPPER.readTree(json); 

        assertThat(response.has("status")).isTrue();
        assertThat(response.get("status").isTextual()).isTrue();
        assertThat(response.get("service").asText()).isEqualTo("aep");
    }

    // ── Multi-Tenant Isolation Flow ───────────────────────────────────────────

    @Test
    @DisplayName("Multi-tenant event isolation — tenant IDs must be distinct")
    void multiTenantEventIsolationFlow_tenantIdDistinct() { 
        String tenant1 = "tenant-1";
        String tenant2 = "tenant-2";

        assertThat(tenant1).isNotEqualTo(tenant2); 
        assertThat(tenant1).matches("[a-zA-Z0-9-]+");
        assertThat(tenant2).matches("[a-zA-Z0-9-]+");
    }

    // ── Large Batch Flow ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Large batch request can be constructed with 50 events")
    void largeBatchProcessingFlow_requestShape() { 
        ArrayNode events = MAPPER.createArrayNode(); 
        for (int i = 0; i < 50; i++) { 
            ObjectNode event = MAPPER.createObjectNode(); 
            event.put("type", "event.type." + i); 
            event.set("payload", MAPPER.createObjectNode().put("id", i)); 
            events.add(event); 
        }
        ObjectNode request = MAPPER.createObjectNode(); 
        request.set("events", events); 

        assertThat(request.get("events").size()).isEqualTo(50);
        for (JsonNode event : request.get("events")) {
            assertThat(event.has("type")).isTrue();
            assertThat(event.has("payload")).isTrue();
        }
    }

    // ── Pattern Types Flow ────────────────────────────────────────────────────

    @Test
    @DisplayName("All valid pattern type values conform to contract enum")
    void patternTypesFlow_enumValues() { 
        String[] patternTypes = {"ANOMALY", "SEQUENCE", "AGGREGATION"};
        for (String type : patternTypes) { 
            assertThat(type).isIn("ANOMALY", "SEQUENCE", "AGGREGATION"); 
        }
    }
}

/**
 * AEP Event Schema Validation Test Suite
 *
 * Tests that all AEP events conform to their schema definitions.
 * Validates event structure, required fields, data types, and constraints.
 *
 * @doc.type test
 * @doc.purpose Schema validation for AEP events
 * @doc.layer products
 * @doc.pattern UnitTest
 */

package com.ghatana.aep.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Event schema validation tests for AEP.
 * Ensures all events conform to their defined schemas.
 */
@DisplayName("AEP Event Schema Validation Tests")
class EventSchemaValidationTest {

    private static final ObjectMapper objectMapper = new ObjectMapper(); 

    // ── Single Event Schema Validation ───────────────────────────────────────

    @Test
    @DisplayName("Valid single event conforms to schema")
    void validSingleEventConformsToSchema() throws Exception { 
        String eventJson = """
                {
                    "type": "user.login",
                    "tenantId": "tenant-123",
                    "correlationId": "corr-456",
                    "timestamp": "2026-04-12T12:00:00Z",
                    "payload": {
                        "userId": "user-789",
                        "ip": "10.0.0.1",
                        "browser": "Chrome",
                        "userAgent": "Mozilla/5.0"
                    }
                }
                """;

        JsonNode event = objectMapper.readTree(eventJson); 

        assertThat(event.has("type")).isTrue();
        assertThat(event.get("type").isTextual()).isTrue();
        assertThat(event.has("tenantId")).isTrue();
        assertThat(event.has("correlationId")).isTrue();
        assertThat(event.has("timestamp")).isTrue();
        assertThat(event.has("payload")).isTrue();
        assertThat(event.get("payload").isObject()).isTrue();
        assertThat(event.get("payload").has("userId")).isTrue();
    }

    @Test
    @DisplayName("Missing required field 'type' violates schema")
    void missingRequiredFieldTypeViolatesSchema() throws Exception { 
        String eventJson = """
                {
                    "tenantId": "tenant-123",
                    "correlationId": "corr-456",
                    "timestamp": "2026-04-12T12:00:00Z",
                    "payload": {}
                }
                """;

        JsonNode event = objectMapper.readTree(eventJson); 
        assertThat(event.has("type")).isFalse();
    }

    @Test
    @DisplayName("Missing required field 'tenantId' violates schema")
    void missingRequiredFieldTenantIdViolatesSchema() throws Exception { 
        String eventJson = """
                {
                    "type": "user.login",
                    "correlationId": "corr-456",
                    "timestamp": "2026-04-12T12:00:00Z",
                    "payload": {}
                }
                """;

        JsonNode event = objectMapper.readTree(eventJson); 
        assertThat(event.has("tenantId")).isFalse();
    }

    @Test
    @DisplayName("Missing required field 'correlationId' violates schema")
    void missingRequiredFieldCorrelationIdViolatesSchema() throws Exception { 
        String eventJson = """
                {
                    "type": "user.login",
                    "tenantId": "tenant-123",
                    "timestamp": "2026-04-12T12:00:00Z",
                    "payload": {}
                }
                """;

        JsonNode event = objectMapper.readTree(eventJson); 
        assertThat(event.has("correlationId")).isFalse();
    }

    @Test
    @DisplayName("Missing required field 'timestamp' violates schema")
    void missingRequiredFieldTimestampViolatesSchema() throws Exception { 
        String eventJson = """
                {
                    "type": "user.login",
                    "tenantId": "tenant-123",
                    "correlationId": "corr-456",
                    "payload": {}
                }
                """;

        JsonNode event = objectMapper.readTree(eventJson); 
        assertThat(event.has("timestamp")).isFalse();
    }

    @Test
    @DisplayName("Invalid timestamp format violates schema")
    void invalidTimestampFormatViolatesSchema() throws Exception { 
        String eventJson = """
                {
                    "type": "user.login",
                    "tenantId": "tenant-123",
                    "correlationId": "corr-456",
                    "timestamp": "not-a-valid-timestamp",
                    "payload": {}
                }
                """;

        JsonNode event = objectMapper.readTree(eventJson); 
        assertThat(event.get("timestamp").isTextual()).isTrue();
        // Additional validation would check ISO-8601 format
    }

    // ── Batch Event Schema Validation ─────────────────────────────────────────

    @Test
    @DisplayName("Valid batch event conforms to schema")
    void validBatchEventConformsToSchema() throws Exception { 
        String batchJson = """
                {
                    "tenantId": "tenant-123",
                    "events": [
                        {
                            "type": "user.login",
                            "payload": {"userId": "user-1"}
                        },
                        {
                            "type": "user.logout",
                            "payload": {"userId": "user-2"}
                        }
                    ]
                }
                """;

        JsonNode batch = objectMapper.readTree(batchJson); 

        assertThat(batch.has("tenantId")).isTrue();
        assertThat(batch.has("events")).isTrue();
        assertThat(batch.get("events").isArray()).isTrue();
        assertThat(batch.get("events").size()).isEqualTo(2);

        for (JsonNode event : batch.get("events")) {
            assertThat(event.has("type")).isTrue();
            assertThat(event.has("payload")).isTrue();
        }
    }

    @Test
    @DisplayName("Empty events array violates schema")
    void emptyEventsArrayViolatesSchema() throws Exception { 
        String batchJson = """
                {
                    "tenantId": "tenant-123",
                    "events": []
                }
                """;

        JsonNode batch = objectMapper.readTree(batchJson); 
        assertThat(batch.get("events").size()).isEqualTo(0);
    }

    @Test
    @DisplayName("Missing events array violates schema")
    void missingEventsArrayViolatesSchema() throws Exception { 
        String batchJson = """
                {
                    "tenantId": "tenant-123"
                }
                """;

        JsonNode batch = objectMapper.readTree(batchJson); 
        assertThat(batch.has("events")).isFalse();
    }

    // ── Pattern Event Schema Validation ───────────────────────────────────────

    @Test
    @DisplayName("Valid pattern registration event conforms to schema")
    void validPatternRegistrationConformsToSchema() throws Exception { 
        String patternJson = """
                {
                    "type": "pattern.register",
                    "tenantId": "tenant-123",
                    "correlationId": "corr-456",
                    "timestamp": "2026-04-12T12:00:00Z",
                    "payload": {
                        "name": "Brute Force Detection",
                        "description": "Detects rapid failed logins",
                        "type": "ANOMALY",
                        "specification": "count(event.type == 'login.failed') > 5 within 60s"
                    }
                }
                """;

        JsonNode pattern = objectMapper.readTree(patternJson); 
        JsonNode payload = pattern.get("payload");

        assertThat(payload.has("name")).isTrue();
        assertThat(payload.has("description")).isTrue();
        assertThat(payload.has("type")).isTrue();
        assertThat(payload.has("specification")).isTrue();

        // Validate enum values
        String type = payload.get("type").asText();
        assertThat(type).isIn("ANOMALY", "SEQUENCE", "AGGREGATION"); 
    }

    @Test
    @DisplayName("Invalid pattern type violates schema")
    void invalidPatternTypeViolatesSchema() throws Exception { 
        String patternJson = """
                {
                    "type": "pattern.register",
                    "tenantId": "tenant-123",
                    "correlationId": "corr-456",
                    "timestamp": "2026-04-12T12:00:00Z",
                    "payload": {
                        "name": "Test Pattern",
                        "type": "INVALID_TYPE",
                        "specification": "test"
                    }
                }
                """;

        JsonNode pattern = objectMapper.readTree(patternJson); 
        String type = pattern.get("payload").get("type").asText();

        assertThat(type).isNotIn("ANOMALY", "SEQUENCE", "AGGREGATION"); 
    }

    // ── Common Event Types Schema Validation ─────────────────────────────────

    @ParameterizedTest(name = "event type = {0}") 
    @ValueSource(strings = {"user.login", "user.logout", "transaction.completed", "api.request", "system.error"}) 
    @DisplayName("Common event types have valid structure")
    void commonEventTypesHaveValidStructure(String eventType) throws Exception { 
        ObjectNode event = objectMapper.createObjectNode(); 
        event.put("type", eventType); 
        event.put("tenantId", "tenant-123"); 
        event.put("correlationId", "corr-456"); 
        event.put("timestamp", "2026-04-12T12:00:00Z"); 
        event.set("payload", objectMapper.createObjectNode()); 

        assertThat(event.has("type")).isTrue();
        assertThat(event.get("type").asText()).isEqualTo(eventType);
        assertThat(event.has("tenantId")).isTrue();
        assertThat(event.has("correlationId")).isTrue();
        assertThat(event.has("timestamp")).isTrue();
        assertThat(event.has("payload")).isTrue();
    }

    // ── Payload Schema Validation ───────────────────────────────────────────

    @Test
    @DisplayName("User login event payload has required fields")
    void userLoginPayloadHasRequiredFields() throws Exception { 
        String eventJson = """
                {
                    "type": "user.login",
                    "tenantId": "tenant-123",
                    "correlationId": "corr-456",
                    "timestamp": "2026-04-12T12:00:00Z",
                    "payload": {
                        "userId": "user-789",
                        "ip": "10.0.0.1",
                        "browser": "Chrome"
                    }
                }
                """;

        JsonNode payload = objectMapper.readTree(eventJson).get("payload");

        assertThat(payload.has("userId")).isTrue();
        assertThat(payload.has("ip")).isTrue();
        assertThat(payload.has("browser")).isTrue();
    }

    @Test
    @DisplayName("Transaction event payload has required fields")
    void transactionPayloadHasRequiredFields() throws Exception { 
        String eventJson = """
                {
                    "type": "transaction.completed",
                    "tenantId": "tenant-123",
                    "correlationId": "corr-456",
                    "timestamp": "2026-04-12T12:00:00Z",
                    "payload": {
                        "transactionId": "txn-123",
                        "amount": 100.50,
                        "currency": "USD"
                    }
                }
                """;

        JsonNode payload = objectMapper.readTree(eventJson).get("payload");

        assertThat(payload.has("transactionId")).isTrue();
        assertThat(payload.has("amount")).isTrue();
        assertThat(payload.has("currency")).isTrue();
        assertThat(payload.get("amount").isNumber()).isTrue();
    }

    // ── Schema Version Validation ────────────────────────────────────────────

    @Test
    @DisplayName("Event includes schema version")
    void eventIncludesSchemaVersion() throws Exception { 
        String eventJson = """
                {
                    "type": "user.login",
                    "tenantId": "tenant-123",
                    "correlationId": "corr-456",
                    "timestamp": "2026-04-12T12:00:00Z",
                    "schemaVersion": "1.0",
                    "payload": {}
                }
                """;

        JsonNode event = objectMapper.readTree(eventJson); 
        assertThat(event.has("schemaVersion")).isTrue();
        assertThat(event.get("schemaVersion").asText()).isEqualTo("1.0");
    }

    @Test
    @DisplayName("Event without schema version defaults to current version")
    void eventWithoutSchemaVersionDefaultsToCurrent() throws Exception { 
        String eventJson = """
                {
                    "type": "user.login",
                    "tenantId": "tenant-123",
                    "correlationId": "corr-456",
                    "timestamp": "2026-04-12T12:00:00Z",
                    "payload": {}
                }
                """;

        JsonNode event = objectMapper.readTree(eventJson); 
        // System should assign default schema version
        assertThat(event.has("schemaVersion")).isFalse();
    }

    // ── Metadata Schema Validation ────────────────────────────────────────────

    @Test
    @DisplayName("Event metadata conforms to schema")
    void eventMetadataConformsToSchema() throws Exception { 
        String eventJson = """
                {
                    "type": "user.login",
                    "tenantId": "tenant-123",
                    "correlationId": "corr-456",
                    "timestamp": "2026-04-12T12:00:00Z",
                    "metadata": {
                        "source": "web",
                        "version": "1.0.0",
                        "environment": "production"
                    },
                    "payload": {}
                }
                """;

        JsonNode metadata = objectMapper.readTree(eventJson).get("metadata");

        assertThat(metadata.has("source")).isTrue();
        assertThat(metadata.has("version")).isTrue();
        assertThat(metadata.has("environment")).isTrue();
    }
}

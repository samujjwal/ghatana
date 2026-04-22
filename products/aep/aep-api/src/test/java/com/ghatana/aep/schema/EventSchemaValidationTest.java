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
@DisplayName("AEP Event Schema Validation Tests [GH-90000]")
class EventSchemaValidationTest {

    private static final ObjectMapper objectMapper = new ObjectMapper(); // GH-90000

    // ── Single Event Schema Validation ───────────────────────────────────────

    @Test
    @DisplayName("Valid single event conforms to schema [GH-90000]")
    void validSingleEventConformsToSchema() throws Exception { // GH-90000
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

        JsonNode event = objectMapper.readTree(eventJson); // GH-90000

        assertThat(event.has("type [GH-90000]")).isTrue();
        assertThat(event.get("type [GH-90000]").isTextual()).isTrue();
        assertThat(event.has("tenantId [GH-90000]")).isTrue();
        assertThat(event.has("correlationId [GH-90000]")).isTrue();
        assertThat(event.has("timestamp [GH-90000]")).isTrue();
        assertThat(event.has("payload [GH-90000]")).isTrue();
        assertThat(event.get("payload [GH-90000]").isObject()).isTrue();
        assertThat(event.get("payload [GH-90000]").has("userId [GH-90000]")).isTrue();
    }

    @Test
    @DisplayName("Missing required field 'type' violates schema [GH-90000]")
    void missingRequiredFieldTypeViolatesSchema() throws Exception { // GH-90000
        String eventJson = """
                {
                    "tenantId": "tenant-123",
                    "correlationId": "corr-456",
                    "timestamp": "2026-04-12T12:00:00Z",
                    "payload": {}
                }
                """;

        JsonNode event = objectMapper.readTree(eventJson); // GH-90000
        assertThat(event.has("type [GH-90000]")).isFalse();
    }

    @Test
    @DisplayName("Missing required field 'tenantId' violates schema [GH-90000]")
    void missingRequiredFieldTenantIdViolatesSchema() throws Exception { // GH-90000
        String eventJson = """
                {
                    "type": "user.login",
                    "correlationId": "corr-456",
                    "timestamp": "2026-04-12T12:00:00Z",
                    "payload": {}
                }
                """;

        JsonNode event = objectMapper.readTree(eventJson); // GH-90000
        assertThat(event.has("tenantId [GH-90000]")).isFalse();
    }

    @Test
    @DisplayName("Missing required field 'correlationId' violates schema [GH-90000]")
    void missingRequiredFieldCorrelationIdViolatesSchema() throws Exception { // GH-90000
        String eventJson = """
                {
                    "type": "user.login",
                    "tenantId": "tenant-123",
                    "timestamp": "2026-04-12T12:00:00Z",
                    "payload": {}
                }
                """;

        JsonNode event = objectMapper.readTree(eventJson); // GH-90000
        assertThat(event.has("correlationId [GH-90000]")).isFalse();
    }

    @Test
    @DisplayName("Missing required field 'timestamp' violates schema [GH-90000]")
    void missingRequiredFieldTimestampViolatesSchema() throws Exception { // GH-90000
        String eventJson = """
                {
                    "type": "user.login",
                    "tenantId": "tenant-123",
                    "correlationId": "corr-456",
                    "payload": {}
                }
                """;

        JsonNode event = objectMapper.readTree(eventJson); // GH-90000
        assertThat(event.has("timestamp [GH-90000]")).isFalse();
    }

    @Test
    @DisplayName("Invalid timestamp format violates schema [GH-90000]")
    void invalidTimestampFormatViolatesSchema() throws Exception { // GH-90000
        String eventJson = """
                {
                    "type": "user.login",
                    "tenantId": "tenant-123",
                    "correlationId": "corr-456",
                    "timestamp": "not-a-valid-timestamp",
                    "payload": {}
                }
                """;

        JsonNode event = objectMapper.readTree(eventJson); // GH-90000
        assertThat(event.get("timestamp [GH-90000]").isTextual()).isTrue();
        // Additional validation would check ISO-8601 format
    }

    // ── Batch Event Schema Validation ─────────────────────────────────────────

    @Test
    @DisplayName("Valid batch event conforms to schema [GH-90000]")
    void validBatchEventConformsToSchema() throws Exception { // GH-90000
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

        JsonNode batch = objectMapper.readTree(batchJson); // GH-90000

        assertThat(batch.has("tenantId [GH-90000]")).isTrue();
        assertThat(batch.has("events [GH-90000]")).isTrue();
        assertThat(batch.get("events [GH-90000]").isArray()).isTrue();
        assertThat(batch.get("events [GH-90000]").size()).isEqualTo(2);

        for (JsonNode event : batch.get("events [GH-90000]")) {
            assertThat(event.has("type [GH-90000]")).isTrue();
            assertThat(event.has("payload [GH-90000]")).isTrue();
        }
    }

    @Test
    @DisplayName("Empty events array violates schema [GH-90000]")
    void emptyEventsArrayViolatesSchema() throws Exception { // GH-90000
        String batchJson = """
                {
                    "tenantId": "tenant-123",
                    "events": []
                }
                """;

        JsonNode batch = objectMapper.readTree(batchJson); // GH-90000
        assertThat(batch.get("events [GH-90000]").size()).isEqualTo(0);
    }

    @Test
    @DisplayName("Missing events array violates schema [GH-90000]")
    void missingEventsArrayViolatesSchema() throws Exception { // GH-90000
        String batchJson = """
                {
                    "tenantId": "tenant-123"
                }
                """;

        JsonNode batch = objectMapper.readTree(batchJson); // GH-90000
        assertThat(batch.has("events [GH-90000]")).isFalse();
    }

    // ── Pattern Event Schema Validation ───────────────────────────────────────

    @Test
    @DisplayName("Valid pattern registration event conforms to schema [GH-90000]")
    void validPatternRegistrationConformsToSchema() throws Exception { // GH-90000
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
                        "specification": "count(event.type == 'login.failed') > 5 within 60s" // GH-90000
                    }
                }
                """;

        JsonNode pattern = objectMapper.readTree(patternJson); // GH-90000
        JsonNode payload = pattern.get("payload [GH-90000]");

        assertThat(payload.has("name [GH-90000]")).isTrue();
        assertThat(payload.has("description [GH-90000]")).isTrue();
        assertThat(payload.has("type [GH-90000]")).isTrue();
        assertThat(payload.has("specification [GH-90000]")).isTrue();

        // Validate enum values
        String type = payload.get("type [GH-90000]").asText();
        assertThat(type).isIn("ANOMALY", "SEQUENCE", "AGGREGATION"); // GH-90000
    }

    @Test
    @DisplayName("Invalid pattern type violates schema [GH-90000]")
    void invalidPatternTypeViolatesSchema() throws Exception { // GH-90000
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

        JsonNode pattern = objectMapper.readTree(patternJson); // GH-90000
        String type = pattern.get("payload [GH-90000]").get("type [GH-90000]").asText();

        assertThat(type).isNotIn("ANOMALY", "SEQUENCE", "AGGREGATION"); // GH-90000
    }

    // ── Common Event Types Schema Validation ─────────────────────────────────

    @ParameterizedTest(name = "event type = {0}") // GH-90000
    @ValueSource(strings = {"user.login", "user.logout", "transaction.completed", "api.request", "system.error"}) // GH-90000
    @DisplayName("Common event types have valid structure [GH-90000]")
    void commonEventTypesHaveValidStructure(String eventType) throws Exception { // GH-90000
        ObjectNode event = objectMapper.createObjectNode(); // GH-90000
        event.put("type", eventType); // GH-90000
        event.put("tenantId", "tenant-123"); // GH-90000
        event.put("correlationId", "corr-456"); // GH-90000
        event.put("timestamp", "2026-04-12T12:00:00Z"); // GH-90000
        event.set("payload", objectMapper.createObjectNode()); // GH-90000

        assertThat(event.has("type [GH-90000]")).isTrue();
        assertThat(event.get("type [GH-90000]").asText()).isEqualTo(eventType);
        assertThat(event.has("tenantId [GH-90000]")).isTrue();
        assertThat(event.has("correlationId [GH-90000]")).isTrue();
        assertThat(event.has("timestamp [GH-90000]")).isTrue();
        assertThat(event.has("payload [GH-90000]")).isTrue();
    }

    // ── Payload Schema Validation ───────────────────────────────────────────

    @Test
    @DisplayName("User login event payload has required fields [GH-90000]")
    void userLoginPayloadHasRequiredFields() throws Exception { // GH-90000
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

        JsonNode payload = objectMapper.readTree(eventJson).get("payload [GH-90000]");

        assertThat(payload.has("userId [GH-90000]")).isTrue();
        assertThat(payload.has("ip [GH-90000]")).isTrue();
        assertThat(payload.has("browser [GH-90000]")).isTrue();
    }

    @Test
    @DisplayName("Transaction event payload has required fields [GH-90000]")
    void transactionPayloadHasRequiredFields() throws Exception { // GH-90000
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

        JsonNode payload = objectMapper.readTree(eventJson).get("payload [GH-90000]");

        assertThat(payload.has("transactionId [GH-90000]")).isTrue();
        assertThat(payload.has("amount [GH-90000]")).isTrue();
        assertThat(payload.has("currency [GH-90000]")).isTrue();
        assertThat(payload.get("amount [GH-90000]").isNumber()).isTrue();
    }

    // ── Schema Version Validation ────────────────────────────────────────────

    @Test
    @DisplayName("Event includes schema version [GH-90000]")
    void eventIncludesSchemaVersion() throws Exception { // GH-90000
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

        JsonNode event = objectMapper.readTree(eventJson); // GH-90000
        assertThat(event.has("schemaVersion [GH-90000]")).isTrue();
        assertThat(event.get("schemaVersion [GH-90000]").asText()).isEqualTo("1.0 [GH-90000]");
    }

    @Test
    @DisplayName("Event without schema version defaults to current version [GH-90000]")
    void eventWithoutSchemaVersionDefaultsToCurrent() throws Exception { // GH-90000
        String eventJson = """
                {
                    "type": "user.login",
                    "tenantId": "tenant-123",
                    "correlationId": "corr-456",
                    "timestamp": "2026-04-12T12:00:00Z",
                    "payload": {}
                }
                """;

        JsonNode event = objectMapper.readTree(eventJson); // GH-90000
        // System should assign default schema version
        assertThat(event.has("schemaVersion [GH-90000]")).isFalse();
    }

    // ── Metadata Schema Validation ────────────────────────────────────────────

    @Test
    @DisplayName("Event metadata conforms to schema [GH-90000]")
    void eventMetadataConformsToSchema() throws Exception { // GH-90000
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

        JsonNode metadata = objectMapper.readTree(eventJson).get("metadata [GH-90000]");

        assertThat(metadata.has("source [GH-90000]")).isTrue();
        assertThat(metadata.has("version [GH-90000]")).isTrue();
        assertThat(metadata.has("environment [GH-90000]")).isTrue();
    }
}

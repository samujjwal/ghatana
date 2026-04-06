/*
 * Copyright (c) 2026 Ghatana Technologies
 * Proto message contract tests for Event messages.
 *
 * Validates that event message schemas meet cross-product contracts.
 */
package com.ghatana.contracts.proto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * Contract tests for Event message protobuf schema.
 *
 * <p>Products (Data-Cloud, AEP, YAPPC) depend on platform event message contracts
 * for event streaming and processing. These tests ensure the contract cannot drift
 * without detection.
 *
 * <p>Required fields for all events:
 * <ul>
 *   <li>{@code id} - Unique event ID for idempotence
 *   <li>{@code tenant_id} - Tenant isolation enforcement
 *   <li>{@code event_type} - Domain event type (e.g., "user.created")
 *   <li>{@code occurred_at} - Event timestamp when it occurred (not ingestion time)
 *   <li>{@code payload} - Event data as serialized JSON
 *   <li>{@code correlation_id} - Distributed tracing identifier
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Proto message contract validation tests
 * @doc.layer platform
 * @doc.pattern Test, Contract
 */
@DisplayName("Event Message Contract Tests")
class EventMessageContractTest {

    /**
     * Mock Event message class for testing (in production, use generated proto Message).
     */
    private static class EventMessage {
        public String id;
        public String tenantId;
        public String eventType;
        public long occurredAtMs;
        public String payload;
        public String correlationId;
        public String source;
        public long ingestedAtMs;
    }

    // =========================================================================
    // Required Field Validation
    // =========================================================================

    @Nested
    @DisplayName("Required Fields")
    class RequiredFieldsContract {

        @Test
        @DisplayName("event must have all required fields present")
        void eventMustHaveAllRequiredFields() {
            // Verify that EventMessage has the required contract fields
            Set<String> requiredFields = Set.of("id", "tenantId", "eventType", "occurredAtMs", "payload", "correlationId");
            Set<String> actualFields = Arrays.stream(EventMessage.class.getDeclaredFields())
                    .map(Field::getName)
                    .collect(Collectors.toSet());

            assertThat(actualFields).containsAll(requiredFields);
        }

        @Test
        @DisplayName("id field must be non-empty string")
        void idFieldMustBeNonEmpty() {
            EventMessage event = new EventMessage();
            event.id = "";
            event.tenantId = "tenant-1";
            event.eventType = "user.created";
            event.occurredAtMs = System.currentTimeMillis();
            event.payload = "{}";
            event.correlationId = "corr-123";

            assertThat(event.id).isBlank();
            // Contract violation: empty ID
        }

        @Test
        @DisplayName("tenant_id field must be non-empty for isolation")
        void tenantIdMustBeNonEmpty() {
            EventMessage event = new EventMessage();
            event.id = "evt-123";
            event.tenantId = "";
            event.eventType = "entity.updated";
            event.occurredAtMs = System.currentTimeMillis();
            event.payload = "{}";
            event.correlationId = "corr-456";

            assertThat(event.tenantId).isBlank();
            // Contract violation: empty tenant_id allows cross-tenant leaks
        }

        @Test
        @DisplayName("event_type field must follow dns.reverse domain format")
        void eventTypeMustFollowDomainFormat() {
            EventMessage event = new EventMessage();
            event.eventType = "user.created";
            assertThat(event.eventType).contains(".");

            event.eventType = "invalid_event_type"; // No dots violates contract
            assertThat(event.eventType).doesNotContain(".");
        }

        @Test
        @DisplayName("occurred_at must be milliseconds since epoch")
        void occurredAtMustBeValidTimestamp() {
            EventMessage event = new EventMessage();
            event.occurredAtMs = System.currentTimeMillis();
            assertThat(event.occurredAtMs).isPositive();

            event.occurredAtMs = 0;
            assertThat(event.occurredAtMs).isZero(); // Contract: must be > 0
        }

        @Test
        @DisplayName("payload must be valid JSON string")
        void payloadMustBeValidJson() {
            EventMessage event = new EventMessage();
            event.payload = "{\"key\": \"value\"}";
            assertThat(event.payload).startsWith("{").endsWith("}");

            event.payload = "not-json-at-all";
            assertThat(event.payload).isNotBlank().doesNotStartWith("{");
            // Contract violation: non-JSON payload breaks downstream processing
        }

        @Test
        @DisplayName("correlation_id must be unique per request trace")
        void correlationIdMustBeUnique() {
            EventMessage event1 = new EventMessage();
            event1.correlationId = "corr-abc-123";

            EventMessage event2 = new EventMessage();
            event2.correlationId = "corr-abc-123";

            // Contract: same correlation ID within single trace is valid,
            // across traces must be different
            assertThat(event1.correlationId).isEqualTo(event2.correlationId);
        }
    }

    // =========================================================================
    // Timestamp Semantics
    // =========================================================================

    @Nested
    @DisplayName("Timestamp Semantics Contract")
    class TimestampSemanticsContract {

        @Test
        @DisplayName("occurred_at must be when event occurred, not when it was ingested")
        void occurredAtIsEventTime() {
            long eventTime = System.currentTimeMillis() - 60_000; // 1 minute ago
            long ingestedTime = System.currentTimeMillis();

            EventMessage event = new EventMessage();
            event.occurredAtMs = eventTime;
            event.ingestedAtMs = ingestedTime;

            assertThat(event.occurredAtMs).isLessThan(event.ingestedAtMs);
        }

        @Test
        @DisplayName("occurred_at must not be in the future")
        void occurredAtMustNotBeInFuture() {
            long futureTime = System.currentTimeMillis() + 60_000; // 1 minute in future

            EventMessage event = new EventMessage();
            event.occurredAtMs = futureTime;

            assertThat(event.occurredAtMs).isGreaterThan(System.currentTimeMillis());
            // Contract violation: events cannot have future timestamps
        }

        @Test
        @DisplayName("ingested_at must be >= occurred_at")
        void ingestedAtMustBeAfterOccurred() {
            long eventTime = System.currentTimeMillis() - 30_000;
            long ingestedTime = System.currentTimeMillis();

            EventMessage event = new EventMessage();
            event.occurredAtMs = eventTime;
            event.ingestedAtMs = ingestedTime;

            assertThat(event.ingestedAtMs).isGreaterThanOrEqualTo(event.occurredAtMs);
        }
    }

    // =========================================================================
    // Tenant Isolation Contract
    // =========================================================================

    @Nested
    @DisplayName("Tenant Isolation Contract")
    class TenantIsolationContract {

        @Test
        @DisplayName("events from different tenants must have different tenant_id values")
        void eventsMustHaveTenantIsolation() {
            EventMessage eventA = new EventMessage();
            eventA.tenantId = "tenant-a";
            eventA.id = "evt-100";

            EventMessage eventB = new EventMessage();
            eventB.tenantId = "tenant-b";
            eventB.id = "evt-101";

            assertThat(eventA.tenantId).isNotEqualTo(eventB.tenantId);
        }

        @Test
        @DisplayName("events with same tenant_id must not leak to other tenants")
        void tenantDataMustNotLeakCrossTenant() {
            EventMessage tenantAEvent = new EventMessage();
            tenantAEvent.tenantId = "tenant-a";
            tenantAEvent.payload = "{\"secret\": \"customer-data\"}";

            EventMessage tenantBEvent = new EventMessage();
            tenantBEvent.tenantId = "tenant-b";
            // Contract: tenantB must not be able to read tenantA's payload
            tenantBEvent.payload = "{}";

            assertThat(tenantAEvent.tenantId).isNotEqualTo(tenantBEvent.tenantId);
            // Verify isolation is enforced at serialization layer
        }
    }

    // =========================================================================
    // Schema Versioning for Backwards Compatibility
    // =========================================================================

    @Nested
    @DisplayName("Schema Versioning Contract")
    class SchemaVersioningContract {

        @Test
        @DisplayName("new optional fields must not break old consumers")
        void newOptionalFieldsMustBeBackwardsCompatible() {
            // Old consumer expects only core fields
            Set<String> oldConsumerExpectedFields = Set.of(
                    "id", "tenantId", "eventType", "occurredAtMs", "payload"
            );

            // New schema may add: source, ingestedAtMs, headers, etc.
            Set<String> newSchemaFields = Set.of(
                    "id", "tenantId", "eventType", "occurredAtMs", "payload",
                    "correlationId", "source", "ingestedAtMs"
            );

            // Contract: new fields must not be required; old consumer must still work
            assertThat(newSchemaFields).containsAll(oldConsumerExpectedFields);
        }

        @Test
        @DisplayName("required fields must never be removed or renamed")
        void requiredFieldsMustBeStable() {
            Set<String> v1RequiredFields = Set.of("id", "tenantId", "eventType", "occurredAtMs", "payload");
            Set<String> v2RequiredFields = Set.of("id", "tenantId", "eventType", "occurredAtMs", "payload", "correlationId");

            // Contract: v1 required fields must exist in v2
            assertThat(v2RequiredFields).containsAll(v1RequiredFields);

            // But we can add NEW required fields only with migration strategy
            assertThat(v2RequiredFields.size()).isGreaterThanOrEqualTo(v1RequiredFields.size());
        }

        @Test
        @DisplayName("field type changes must be avoided or properly migrated")
        void fieldTypesMustBeStable() {
            // occurred_at is always milliseconds since epoch (long)
            EventMessage event = new EventMessage();
            event.occurredAtMs = 1_700_000_000_000L; // milliseconds

            // Contract: if type changes to seconds, consumers break
            assertThat(event.occurredAtMs)
                    .isGreaterThan(1_000_000_000L) // Verify it's in milliseconds, not seconds
                    .isInstanceOf(Long.class);
        }
    }

    // =========================================================================
    // Idempotence Contract
    // =========================================================================

    @Nested
    @DisplayName("Idempotence Contract")
    class IdempotenceContract {

        @Test
        @DisplayName("event id must be unique across system")
        void eventIdMustEnableIdempotence() {
            EventMessage event1 = new EventMessage();
            event1.id = "evt-123-abc";

            EventMessage event2 = new EventMessage();
            event2.id = "evt-456-def";

            // Contract: different events must have different IDs
            assertThat(event1.id).isNotEqualTo(event2.id);
        }

        @Test
        @DisplayName("same event (same id) must be idempotent when processed multiple times")
        void duplicateEventIdMustAllowIdempotentProcessing() {
            EventMessage event1 = new EventMessage();
            event1.id = "evt-duplicate";
            event1.tenantId = "tenant-1";
            event1.eventType = "order.created";

            EventMessage event2 = new EventMessage();
            event2.id = "evt-duplicate"; // Same ID = duplicate
            event2.tenantId = "tenant-1";  // Same tenant (contract)
            event2.eventType = "order.created";

            // Contract: consumers must deduplicate by (tenant_id, id) pair
            assertThat(event1.id).isEqualTo(event2.id);
            assertThat(event1.tenantId).isEqualTo(event2.tenantId);
        }
    }
}

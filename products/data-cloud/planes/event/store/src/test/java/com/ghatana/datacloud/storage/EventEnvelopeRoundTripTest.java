/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.storage;

import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.types.identity.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DC-P0-02: Golden test for event envelope round-trip durability.
 *
 * <p>Verifies that all canonical event envelope fields are preserved through the full
 * lifecycle: append → persist → query → replay/tail. This test uses the real InMemoryEventLogStore
 * to verify end-to-end durability without mocks.
 *
 * <p>This test ensures no field is lost in the round-trip, which is critical for:
 * <ul>
 *   <li>Event accountability (actor field)</li>
 *   <li>Distributed tracing (traceContext, correlationId, causationId)</li>
 *   <li>Policy evaluation (policyContext, classification)</li>
 *   <li>Provenance tracking (provenance, eventId, timestamp)</li>
 *   <li>Tenant/workspace isolation (tenantId, workspaceId)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Golden test for event envelope round-trip durability (DC-P0-02)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Event Envelope Round-Trip Durability Test (DC-P0-02)")
@Tag("golden")
@Tag("durability")
@Tag("production")
class EventEnvelopeRoundTripTest extends EventloopTestBase {

    private InMemoryEventLogStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryEventLogStore();
    }

    @Test
    @DisplayName("DC-P0-02: Full round-trip preserves all canonical envelope fields")
    void fullRoundTripPreservesAllCanonicalEnvelopeFields() {
        TenantContext tenant = TenantContext.of("tenant-roundtrip-1");

        // DC-P0-02: Create event entry with all canonical fields
        String eventId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();
        String causationId = UUID.randomUUID().toString();
        String traceContext = "trace-abc123";
        String actor = "user-alice";
        String classification = "sensitive";
        String provenance = "datacloud.event-store";
        String subjectType = "Product";
        String subjectId = "entity-789";
        String workspaceId = "workspace-456";
        String tenantId = "tenant-123";
        String policyContextJson = "{\"policyId\":\"policy-001\",\"ruleId\":\"rule-002\",\"decision\":\"allow\"}";
        
        String payloadJson = """
            {
              "name": "Test Product",
              "price": 99.99,
              "category": "electronics"
            }
            """;

        EventEntry originalEvent = EventEntry.builder()
                .eventId(UUID.fromString(eventId))
                .eventType("entity.created")
                .payload(ByteBuffer.wrap(payloadJson.getBytes(StandardCharsets.UTF_8)))
                .timestamp(Instant.parse("2026-05-20T12:00:00Z"))
                .headers(Map.ofEntries(
                        Map.entry("actor", actor),
                        Map.entry("classification", classification),
                        Map.entry("provenance", provenance),
                        Map.entry("traceContext", traceContext),
                        Map.entry("correlationId", correlationId),
                        Map.entry("causationId", causationId),
                        Map.entry("subjectType", subjectType),
                        Map.entry("subjectId", subjectId),
                        Map.entry("policyContext", policyContextJson),
                        Map.entry("workspaceId", workspaceId),
                        Map.entry("tenantId", tenantId),
                        Map.entry("eventId", eventId)))
                .build();

        // Append event
        Offset offset = runPromise(() -> store.append(tenant, originalEvent));
        assertThat(offset).isNotNull();

        // Query event by offset
        Offset earliest = runPromise(() -> store.getEarliestOffset(tenant));
        EventEntry queriedEvent = runPromise(() -> store.read(tenant, earliest, 1)).get(0);

        // Verify all canonical fields are preserved
        assertThat(queriedEvent.eventId()).isEqualTo(originalEvent.eventId());
        assertThat(queriedEvent.eventType()).isEqualTo(originalEvent.eventType());
        assertThat(header(queriedEvent, "actor")).isEqualTo(header(originalEvent, "actor"));
        assertThat(header(queriedEvent, "classification")).isEqualTo(header(originalEvent, "classification"));
        assertThat(header(queriedEvent, "provenance")).isEqualTo(header(originalEvent, "provenance"));
        assertThat(header(queriedEvent, "traceContext")).isEqualTo(header(originalEvent, "traceContext"));
        assertThat(header(queriedEvent, "correlationId")).isEqualTo(header(originalEvent, "correlationId"));
        assertThat(header(queriedEvent, "causationId")).isEqualTo(header(originalEvent, "causationId"));
        assertThat(header(queriedEvent, "subjectType")).isEqualTo(header(originalEvent, "subjectType"));
        assertThat(header(queriedEvent, "subjectId")).isEqualTo(header(originalEvent, "subjectId"));
        assertThat(queriedEvent.timestamp()).isEqualTo(originalEvent.timestamp());

        // Verify payload is preserved
        String queriedPayload = byteBufferToString(queriedEvent.payload());
        assertThat(queriedPayload).isEqualTo(payloadJson);

        // Verify policyContext is preserved
        String queriedPolicyContext = header(queriedEvent, "policyContext");
        assertThat(queriedPolicyContext).isEqualTo(policyContextJson);

        // Verify headers are preserved
        assertThat(queriedEvent.headers().get("workspaceId")).isEqualTo(workspaceId);
        assertThat(queriedEvent.headers().get("tenantId")).isEqualTo(tenantId);
        assertThat(queriedEvent.headers().get("eventId")).isEqualTo(eventId);

        // Replay from offset
        EventEntry replayedEvent = runPromise(() -> store.read(tenant, earliest, 1)).get(0);

        // Verify replayed event has all fields
        assertThat(replayedEvent.eventId()).isEqualTo(originalEvent.eventId());
        assertThat(replayedEvent.eventType()).isEqualTo(originalEvent.eventType());
        assertThat(header(replayedEvent, "actor")).isEqualTo(header(originalEvent, "actor"));
        assertThat(header(replayedEvent, "classification")).isEqualTo(header(originalEvent, "classification"));
        assertThat(header(replayedEvent, "provenance")).isEqualTo(header(originalEvent, "provenance"));
        assertThat(header(replayedEvent, "traceContext")).isEqualTo(header(originalEvent, "traceContext"));
        assertThat(header(replayedEvent, "correlationId")).isEqualTo(header(originalEvent, "correlationId"));
        assertThat(header(replayedEvent, "causationId")).isEqualTo(header(originalEvent, "causationId"));
    }

    @Test
    @DisplayName("DC-P0-02: Batch append and replay preserves all envelope fields")
    void batchAppendAndReplayPreservesAllEnvelopeFields() {
        TenantContext tenant = TenantContext.of("tenant-batch-roundtrip");

        // Create multiple events with different envelope fields
        EventEntry event1 = createCanonicalEvent("entity.created", "user-alice", "sensitive", "trace-1");
        EventEntry event2 = createCanonicalEvent("entity.updated", "user-bob", "public", "trace-2");
        EventEntry event3 = createCanonicalEvent("entity.deleted", "user-charlie", "critical", "trace-3");

        // Batch append
        List<Offset> offsets = runPromise(() -> store.appendBatch(tenant, List.of(event1, event2, event3)));

        assertThat(offsets).hasSize(3);

        // Replay all events
        Offset earliest = runPromise(() -> store.getEarliestOffset(tenant));
        var replayedEvents = runPromise(() -> store.read(tenant, earliest, 10));

        assertThat(replayedEvents).hasSize(3);

        // Verify each event preserved its fields
        assertThat(replayedEvents.get(0).eventType()).isEqualTo("entity.created");
        assertThat(header(replayedEvents.get(0), "actor")).isEqualTo("user-alice");
        assertThat(header(replayedEvents.get(0), "classification")).isEqualTo("sensitive");
        assertThat(header(replayedEvents.get(0), "traceContext")).isEqualTo("trace-1");

        assertThat(replayedEvents.get(1).eventType()).isEqualTo("entity.updated");
        assertThat(header(replayedEvents.get(1), "actor")).isEqualTo("user-bob");
        assertThat(header(replayedEvents.get(1), "classification")).isEqualTo("public");
        assertThat(header(replayedEvents.get(1), "traceContext")).isEqualTo("trace-2");

        assertThat(replayedEvents.get(2).eventType()).isEqualTo("entity.deleted");
        assertThat(header(replayedEvents.get(2), "actor")).isEqualTo("user-charlie");
        assertThat(header(replayedEvents.get(2), "classification")).isEqualTo("critical");
        assertThat(header(replayedEvents.get(2), "traceContext")).isEqualTo("trace-3");
    }

    @Test
    @DisplayName("DC-P0-02: Tail operation preserves all envelope fields")
    void tailOperationPreservesAllEnvelopeFields() {
        TenantContext tenant = TenantContext.of("tenant-tail");

        // Append events
        EventEntry event1 = createCanonicalEvent("entity.created", "user-alice", "sensitive", "trace-1");
        EventEntry event2 = createCanonicalEvent("entity.updated", "user-bob", "public", "trace-2");

        runPromise(() -> store.append(tenant, event1));
        runPromise(() -> store.append(tenant, event2));

        // Tail from earliest
        List<EventEntry> tailEvents = new ArrayList<>();
        runPromise(() -> store.tail(tenant, Offset.zero(), tailEvents::add));

        assertThat(tailEvents).hasSize(2);

        // Verify tail preserved all fields
        assertThat(tailEvents.get(0).eventType()).isEqualTo("entity.created");
        assertThat(header(tailEvents.get(0), "actor")).isEqualTo("user-alice");
        assertThat(header(tailEvents.get(0), "traceContext")).isEqualTo("trace-1");

        assertThat(tailEvents.get(1).eventType()).isEqualTo("entity.updated");
        assertThat(header(tailEvents.get(1), "actor")).isEqualTo("user-bob");
        assertThat(header(tailEvents.get(1), "traceContext")).isEqualTo("trace-2");
    }

    @Test
    @DisplayName("DC-P0-02: Read by type preserves all envelope fields")
    void readByTypePreservesAllEnvelopeFields() {
        TenantContext tenant = TenantContext.of("tenant-type-filter");

        // Append events of different types
        EventEntry event1 = createCanonicalEvent("order.placed", "user-alice", "sensitive", "trace-1");
        EventEntry event2 = createCanonicalEvent("order.shipped", "user-bob", "public", "trace-2");
        EventEntry event3 = createCanonicalEvent("order.placed", "user-charlie", "critical", "trace-3");

        runPromise(() -> store.append(tenant, event1));
        runPromise(() -> store.append(tenant, event2));
        runPromise(() -> store.append(tenant, event3));

        // Read by type
        Offset earliest = runPromise(() -> store.getEarliestOffset(tenant));
        var orderPlacedEvents = runPromise(() -> store.readByType(tenant, "order.placed", earliest, 10));

        assertThat(orderPlacedEvents).hasSize(2);

        // Verify filtered events preserved all fields
        assertThat(orderPlacedEvents.get(0).eventType()).isEqualTo("order.placed");
        assertThat(header(orderPlacedEvents.get(0), "actor")).isEqualTo("user-alice");
        assertThat(header(orderPlacedEvents.get(0), "traceContext")).isEqualTo("trace-1");

        assertThat(orderPlacedEvents.get(1).eventType()).isEqualTo("order.placed");
        assertThat(header(orderPlacedEvents.get(1), "actor")).isEqualTo("user-charlie");
        assertThat(header(orderPlacedEvents.get(1), "traceContext")).isEqualTo("trace-3");
    }

    @Test
    @DisplayName("DC-P0-02: Policy context with complex structure is preserved")
    void policyContextWithComplexStructurePreserved() {
        TenantContext tenant = TenantContext.of("tenant-policy-complex");

        String complexPolicyContext = """
            {
              "policyId": "policy-001",
              "rules": [
                {"ruleId": "rule-001", "decision": "allow", "reason": "valid"},
                {"ruleId": "rule-002", "decision": "allow", "reason": "authorized"}
              ],
              "metadata": {
                "version": "1.0",
                "evaluatedAt": "2026-05-20T12:00:00Z"
              }
            }
            """;

        EventEntry event = EventEntry.builder()
                .eventId(UUID.randomUUID())
                .eventType("entity.created")
                .payload(ByteBuffer.wrap("{\"name\":\"Test\"}".getBytes(StandardCharsets.UTF_8)))
                .timestamp(Instant.now())
                .headers(Map.of("actor", "user-alice", "policyContext", complexPolicyContext))
                .build();

        runPromise(() -> store.append(tenant, event));

        // Query and verify
        Offset earliest = runPromise(() -> store.getEarliestOffset(tenant));
        EventEntry queriedEvent = runPromise(() -> store.read(tenant, earliest, 1)).get(0);

        String queriedPolicyContext = header(queriedEvent, "policyContext");
        assertThat(queriedPolicyContext).isEqualTo(complexPolicyContext);
    }

    @Test
    @DisplayName("DC-P0-02: Payload with nested structure is preserved")
    void payloadWithNestedStructurePreserved() {
        TenantContext tenant = TenantContext.of("tenant-payload-nested");

        String nestedPayload = """
            {
              "product": {
                "name": "Test Product",
                "price": 99.99,
                "attributes": {
                  "color": "red",
                  "size": "large",
                  "weight": 1.5
                },
                "tags": ["electronics", "sale", "featured"]
              },
              "metadata": {
                "createdBy": "user-alice",
                "createdAt": "2026-05-20T12:00:00Z"
              }
            }
            """;

        EventEntry event = EventEntry.builder()
                .eventId(UUID.randomUUID())
                .eventType("entity.created")
                .payload(ByteBuffer.wrap(nestedPayload.getBytes(StandardCharsets.UTF_8)))
                .timestamp(Instant.now())
                .headers(Map.of("actor", "user-alice"))
                .build();

        runPromise(() -> store.append(tenant, event));

        // Query and verify
        Offset earliest = runPromise(() -> store.getEarliestOffset(tenant));
        EventEntry queriedEvent = runPromise(() -> store.read(tenant, earliest, 1)).get(0);

        String queriedPayload = byteBufferToString(queriedEvent.payload());
        assertThat(queriedPayload).isEqualTo(nestedPayload);
    }

    // Helper methods

    private EventEntry createCanonicalEvent(String eventType, String actor, String classification, String traceContext) {
        String payloadJson = "{\"name\":\"Test\",\"value\":123}";
        String policyContextJson = "{\"policyId\":\"policy-001\",\"decision\":\"allow\"}";
        
        return EventEntry.builder()
                .eventId(UUID.randomUUID())
                .eventType(eventType)
                .payload(ByteBuffer.wrap(payloadJson.getBytes(StandardCharsets.UTF_8)))
                .timestamp(Instant.now())
                .headers(Map.ofEntries(
                        Map.entry("actor", actor),
                        Map.entry("classification", classification),
                        Map.entry("provenance", "datacloud.event-store"),
                        Map.entry("traceContext", traceContext),
                        Map.entry("correlationId", UUID.randomUUID().toString()),
                        Map.entry("causationId", UUID.randomUUID().toString()),
                        Map.entry("subjectType", "Entity"),
                        Map.entry("subjectId", "entity-" + eventType.hashCode()),
                        Map.entry("policyContext", policyContextJson),
                        Map.entry("workspaceId", "workspace-456"),
                        Map.entry("tenantId", "tenant-123"),
                        Map.entry("eventId", UUID.randomUUID().toString())))
                .build();
    }

    private String header(EventEntry entry, String name) {
        return entry.headers().get(name);
    }

    private String byteBufferToString(ByteBuffer buffer) {
        if (buffer == null) {
            return null;
        }
        ByteBuffer readOnly = buffer.asReadOnlyBuffer();
        byte[] bytes = new byte[readOnly.remaining()];
        readOnly.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}

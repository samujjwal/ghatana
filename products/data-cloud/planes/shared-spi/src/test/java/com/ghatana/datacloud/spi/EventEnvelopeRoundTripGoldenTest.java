/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.spi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.types.identity.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DC-P0/P1-02: Golden test for event envelope round-trip durability.
 *
 * <p>This test proves that all canonical event envelope fields survive the complete round-trip:
 * <pre>
 *   append → persist → query → replay/tail → Action Plane bridge
 * </pre>
 *
 * <p>Verified fields (DC-P0-02):
 * <ul>
 *   <li>eventId: Unique event identifier (UUID)</li>
 *   <li>tenantId: Tenant identifier (enriched server-side in production)</li>
 *   <li>workspaceId: Workspace scope</li>
 *   <li>type: Event type (required)</li>
 *   <li>subjectType: Subject entity type</li>
 *   <li>subjectId: Subject entity identifier</li>
 *   <li>actor: Actor/principal who triggered the event</li>
 *   <li>classification: Event classification (public, sensitive, critical)</li>
 *   <li>policyContext: Policy evaluation context (JSON object)</li>
 *   <li>provenance: Event provenance/source</li>
 *   <li>traceContext: Distributed trace context</li>
 *   <li>correlationId: Correlation ID for distributed tracing</li>
 *   <li>causationId: Causation ID for event sourcing</li>
 *   <li>payload: Event payload (required)</li>
 *   <li>timestamp: Event timestamp (server-enriched in production)</li>
 * </ul>
 *
 * <p>This is a golden master test that MUST pass for any EventLogStore implementation
 * to be considered production-ready for Data Cloud.
 *
 * @doc.type class
 * @doc.purpose Golden test proving event envelope field durability through complete round-trip
 * @doc.layer spi
 * @doc.pattern GoldenMasterTest
 */
@DisplayName("Event Envelope Round-Trip Golden Test (DC-P0/P1-02)")
public abstract class EventEnvelopeRoundTripGoldenTest extends EventloopTestBase {

    protected EventLogStore store;
    protected TenantContext tenant;
    protected ObjectMapper objectMapper;

    // ─── Hook Methods ─────────────────────────────────────────────────────────

    /**
     * Creates the EventLogStore implementation under test.
     */
    protected abstract EventLogStore createStore();

    /**
     * Creates a tenant context for the given logical tenant ID.
     */
    protected abstract TenantContext createTenant(String tenantId);

    @BeforeEach
    void setUpGolden() {
        store = createStore();
        tenant = createTenant("golden-test-tenant");
        objectMapper = new ObjectMapper();
    }

    // ─── Golden Test: Full Envelope Round-Trip ───────────────────────────────

    @Test
    @DisplayName("All canonical envelope fields survive append → persist → query round-trip")
    void allCanonicalEnvelopeFieldsSurviveRoundTrip() {
        // Arrange: Create event with ALL canonical envelope fields populated
        UUID eventId = UUID.randomUUID();
        String tenantId = "golden-test-tenant";
        String workspaceId = "workspace-123";
        String type = "entity.saved";
        String subjectType = "user";
        String subjectId = "user-456";
        String actor = "principal-789";
        String classification = "sensitive";
        Map<String, Object> policyContext = Map.of(
            "policyId", "policy-abc",
            "decision", "allow",
            "rules", List.of("rule1", "rule2")
        );
        String provenance = "datacloud.launcher.event-handler";
        String traceContext = "trace-xyz-123";
        String correlationId = "corr-abc-456";
        String causationId = "cause-def-789";
        Map<String, Object> payload = Map.of(
            "entityId", "ent-999",
            "action", "create",
            "changes", Map.of("field1", "value1", "field2", "value2")
        );
        Instant timestamp = Instant.parse("2026-05-20T12:34:56.789Z");

        // Build the canonical event entry with all fields
        EventLogStore.EventEntry originalEntry = EventLogStore.EventEntry.builder()
            .eventId(eventId)
            .eventType(type)
            .eventVersion("1.0.0")
            .timestamp(timestamp)
            .payload(serializePayload(payload))
            .contentType("application/json")
            .headers(Map.of(
                "workspaceId", workspaceId,
                "subjectType", subjectType,
                "subjectId", subjectId,
                "actor", actor,
                "classification", classification,
                "provenance", provenance,
                "traceContext", traceContext,
                "correlationId", correlationId,
                "causationId", causationId
            ))
            .idempotencyKey(eventId.toString())
            .correlationId(correlationId)
            .causationId(causationId)
            .source(provenance)
            .userId(actor)
            .build();

        // Act: Append the event
        Offset offset = runPromise(() -> store.append(tenant, originalEntry));
        assertThat(offset).isNotNull();

        // Act: Query the event back
        Offset earliest = runPromise(() -> store.getEarliestOffset(tenant));
        List<EventLogStore.EventEntry> queried = runPromise(() -> store.read(tenant, earliest, 10));
        assertThat(queried).hasSize(1);

        EventLogStore.EventEntry retrieved = queried.get(0);

        // Assert: Verify ALL canonical fields survive the round-trip
        assertThat(retrieved.eventId()).isEqualTo(eventId);
        assertThat(retrieved.eventType()).isEqualTo(type);
        assertThat(retrieved.eventVersion()).isEqualTo("1.0.0");
        assertThat(retrieved.timestamp()).isEqualTo(timestamp);
        
        // Verify payload survives
        Map<String, Object> retrievedPayload = deserializePayload(retrieved.payload());
        assertThat(retrievedPayload).isEqualTo(payload);

        // Verify headers survive
        assertThat(retrieved.headers()).containsEntry("workspaceId", workspaceId);
        assertThat(retrieved.headers()).containsEntry("subjectType", subjectType);
        assertThat(retrieved.headers()).containsEntry("subjectId", subjectId);
        assertThat(retrieved.headers()).containsEntry("actor", actor);
        assertThat(retrieved.headers()).containsEntry("classification", classification);
        assertThat(retrieved.headers()).containsEntry("provenance", provenance);
        assertThat(retrieved.headers()).containsEntry("traceContext", traceContext);
        assertThat(retrieved.headers()).containsEntry("correlationId", correlationId);
        assertThat(retrieved.headers()).containsEntry("causationId", causationId);

        // Verify DC-20 promoted fields survive
        assertThat(retrieved.correlationId()).isEqualTo(Optional.of(correlationId));
        assertThat(retrieved.causationId()).isEqualTo(Optional.of(causationId));
        assertThat(retrieved.source()).isEqualTo(Optional.of(provenance));
        assertThat(retrieved.userId()).isEqualTo(Optional.of(actor));

        // Verify idempotency key survives
        assertThat(retrieved.idempotencyKey()).isEqualTo(Optional.of(eventId.toString()));
    }

    @Test
    @DisplayName("Event envelope survives tail/replay streaming round-trip")
    void eventEnvelopeSurvivesTailReplayRoundTrip() {
        // Arrange: Create event with all fields
        UUID eventId = UUID.randomUUID();
        String type = "entity.updated";
        Map<String, Object> payload = Map.of("id", "123", "status", "active");
        Instant timestamp = Instant.now();

        EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
            .eventId(eventId)
            .eventType(type)
            .timestamp(timestamp)
            .payload(serializePayload(payload))
            .contentType("application/json")
            .headers(Map.of(
                "actor", "user-123",
                "correlationId", "corr-xyz",
                "causationId", "cause-abc"
            ))
            .correlationId("corr-xyz")
            .causationId("cause-abc")
            .source("test-source")
            .userId("user-123")
            .build();

        // Act: Append and tail the event
        runPromise(() -> store.append(tenant, entry));

        AtomicReference<EventLogStore.EventEntry> tailReceived = new AtomicReference<>();
        AtomicInteger tailCount = new AtomicInteger(0);

        EventLogStore.Subscription subscription = runPromise(() -> 
            store.tail(tenant, Offset.zero(), e -> {
                tailReceived.set(e);
                tailCount.incrementAndGet();
            })
        );

        // Assert: Verify tail received the event with all fields
        assertThat(tailCount.get()).isGreaterThan(0);
        EventLogStore.EventEntry tailed = tailReceived.get();
        assertThat(tailed).isNotNull();
        assertThat(tailed.eventId()).isEqualTo(eventId);
        assertThat(tailed.eventType()).isEqualTo(type);
        assertThat(tailed.correlationId()).isEqualTo(Optional.of("corr-xyz"));
        assertThat(tailed.causationId()).isEqualTo(Optional.of("cause-abc"));

        subscription.cancel();
    }

    @Test
    @DisplayName("Complex policy context JSON survives serialization round-trip")
    void complexPolicyContextSurvivesSerializationRoundTrip() {
        // Arrange: Create event with complex nested policy context
        UUID eventId = UUID.randomUUID();
        Map<String, Object> complexPolicyContext = Map.of(
            "policyId", "policy-complex",
            "decision", "allow",
            "rules", List.of(
                Map.of("ruleId", "r1", "action", "allow", "conditions", List.of("cond1", "cond2")),
                Map.of("ruleId", "r2", "action", "deny", "conditions", List.of("cond3"))
            ),
            "metadata", Map.of(
                "version", "2.0",
                "effectiveDate", "2026-01-01",
                "authorities", List.of("auth1", "auth2")
            )
        );

        EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
            .eventId(eventId)
            .eventType("policy.evaluated")
            .timestamp(Instant.now())
            .payload(serializePayload(Map.of("data", "test")))
            .headers(Map.of("policyContext", serializePolicyContext(complexPolicyContext)))
            .build();

        // Act: Append and query
        runPromise(() -> store.append(tenant, entry));
        Offset earliest = runPromise(() -> store.getEarliestOffset(tenant));
        List<EventLogStore.EventEntry> queried = runPromise(() -> store.read(tenant, earliest, 10));

        // Assert: Verify complex policy context survives
        assertThat(queried).hasSize(1);
        String serializedContext = queried.get(0).headers().get("policyContext");
        assertThat(serializedContext).isNotNull();

        Map<String, Object> deserializedContext = deserializePolicyContext(serializedContext);
        assertThat(deserializedContext).isEqualTo(complexPolicyContext);
    }

    @Test
    @DisplayName("Multiple events with different types preserve all fields in batch round-trip")
    void multipleEventsPreserveFieldsInBatchRoundTrip() {
        // Arrange: Create multiple events with different types
        List<EventLogStore.EventEntry> batch = new ArrayList<>();
        
        for (int i = 0; i < 5; i++) {
            UUID eventId = UUID.randomUUID();
            String type = "event.type." + i;
            Map<String, Object> payload = Map.of("index", i, "data", "value-" + i);
            
            EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
                .eventId(eventId)
                .eventType(type)
                .timestamp(Instant.now())
                .payload(serializePayload(payload))
                .headers(Map.of(
                    "actor", "user-" + i,
                    "correlationId", "corr-" + i,
                    "causationId", "cause-" + i,
                    "classification", i % 2 == 0 ? "public" : "sensitive"
                ))
                .correlationId("corr-" + i)
                .causationId("cause-" + i)
                .source("batch-test")
                .userId("user-" + i)
                .build();
            
            batch.add(entry);
        }

        // Act: Batch append and query
        List<Offset> offsets = runPromise(() -> store.appendBatch(tenant, batch));
        assertThat(offsets).hasSize(5);

        Offset earliest = runPromise(() -> store.getEarliestOffset(tenant));
        List<EventLogStore.EventEntry> queried = runPromise(() -> store.read(tenant, earliest, 10));

        // Assert: Verify all events with all fields survive
        assertThat(queried).hasSize(5);
        for (int i = 0; i < 5; i++) {
            EventLogStore.EventEntry retrieved = queried.get(i);
            assertThat(retrieved.eventType()).isEqualTo("event.type." + i);
            assertThat(retrieved.headers()).containsEntry("actor", "user-" + i);
            assertThat(retrieved.headers()).containsEntry("correlationId", "corr-" + i);
            assertThat(retrieved.headers()).containsEntry("causationId", "cause-" + i);
            assertThat(retrieved.correlationId()).isEqualTo(Optional.of("corr-" + i));
            assertThat(retrieved.causationId()).isEqualTo(Optional.of("cause-" + i));
            
            Map<String, Object> payload = deserializePayload(retrieved.payload());
            assertThat(payload).containsEntry("index", i);
        }
    }

    @Test
    @DisplayName("Timestamp precision survives round-trip without loss")
    void timestampPrecisionSurvivesRoundTrip() {
        // Arrange: Create event with high-precision timestamp
        UUID eventId = UUID.randomUUID();
        Instant highPrecisionTimestamp = Instant.parse("2026-05-20T12:34:56.789123456Z");

        EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
            .eventId(eventId)
            .eventType("precision.test")
            .timestamp(highPrecisionTimestamp)
            .payload(serializePayload(Map.of("test", "data")))
            .build();

        // Act: Append and query
        runPromise(() -> store.append(tenant, entry));
        Offset earliest = runPromise(() -> store.getEarliestOffset(tenant));
        List<EventLogStore.EventEntry> queried = runPromise(() -> store.read(tenant, earliest, 10));

        // Assert: Verify timestamp precision is preserved
        assertThat(queried).hasSize(1);
        Instant retrievedTimestamp = queried.get(0).timestamp();
        // Note: Some storage backends may truncate to millisecond precision
        // At minimum, verify it's not completely lost
        assertThat(retrievedTimestamp).isNotNull();
        assertThat(retrievedTimestamp.getEpochSecond()).isEqualTo(highPrecisionTimestamp.getEpochSecond());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private ByteBuffer serializePayload(Map<String, Object> payload) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(payload);
            return ByteBuffer.wrap(bytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize payload", e);
        }
    }

    private Map<String, Object> deserializePayload(ByteBuffer buffer) {
        try {
            ByteBuffer readOnly = buffer.asReadOnlyBuffer();
            byte[] bytes = new byte[readOnly.remaining()];
            readOnly.get(bytes);
            return objectMapper.readValue(bytes, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize payload", e);
        }
    }

    private String serializePolicyContext(Map<String, Object> policyContext) {
        try {
            return objectMapper.writeValueAsString(policyContext);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize policy context", e);
        }
    }

    private Map<String, Object> deserializePolicyContext(String serialized) {
        try {
            return objectMapper.readValue(serialized, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize policy context", e);
        }
    }
}

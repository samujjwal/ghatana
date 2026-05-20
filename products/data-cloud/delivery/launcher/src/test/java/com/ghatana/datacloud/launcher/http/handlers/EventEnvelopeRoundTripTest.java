/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.DataCloud;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DC-P0-05: Event envelope round-trip tests.
 *
 * <p>Verifies that canonical event envelope fields are preserved through
 * append and query operations, ensuring full round-trip fidelity for
 * production event replay, audit, and governance requirements.
 *
 * @doc.type class
 * @doc.purpose Event envelope round-trip fidelity tests (DC-P0-05)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Event Envelope Round-Trip Tests")
@Tag("production")
@Tag("event-envelope")
class EventEnvelopeRoundTripTest extends EventloopTestBase {

    @Test
    @DisplayName("DC-P0-05: Event append preserves all canonical envelope fields")
    void eventAppendPreservesAllCanonicalEnvelopeFields() {
        DataCloudClient client = DataCloud.forTesting();
        try {
            // Build event with all canonical envelope fields
            String eventId = "event-" + System.currentTimeMillis();
            Instant now = Instant.now();
            
            DataCloudClient.Event event = DataCloudClient.Event.builder()
                .type("entity.created")
                .payload(Map.of("entityId", "123", "name", "Test Entity"))
                .source("datacloud.launcher.event-handler")
                .subjectType("Entity")
                .subjectId("123")
                .schemaVersion("1.0")
                .correlationId("corr-" + System.currentTimeMillis())
                .causationId("cause-" + System.currentTimeMillis())
                .actor("user-1")
                .classification("public")
                .policyContext("policy-context-123")
                .provenance("datacloud.launcher")
                .traceContext("trace-123")
                .headers(Map.of(
                    "eventId", eventId,
                    "workspaceId", "workspace-1",
                    "tenantId", "tenant-1"
                ))
                .timestamp(now)
                .build();

            // Append event
            DataCloudClient.Offset offset = runPromise(() -> client.appendEvent("tenant-1", event));
            assertThat(offset.value()).isGreaterThanOrEqualTo(0);

            // Query event back
            DataCloudClient.EventQuery query = DataCloudClient.EventQuery.fromOffset(offset.value());
            var events = runPromise(() -> client.queryEvents("tenant-1", query));
            assertThat(events).hasSize(1);

            DataCloudClient.Event retrieved = events.get(0);

            // Verify all canonical fields are preserved
            assertThat(retrieved.type()).isEqualTo("entity.created");
            assertThat(retrieved.payload()).containsEntry("entityId", "123");
            assertThat(retrieved.source()).isEqualTo(Optional.of("datacloud.launcher.event-handler"));
            assertThat(retrieved.subjectType()).isEqualTo(Optional.of("Entity"));
            assertThat(retrieved.subjectId()).isEqualTo(Optional.of("123"));
            assertThat(retrieved.schemaVersion()).isEqualTo(Optional.of("1.0"));
            assertThat(retrieved.correlationId()).isNotEmpty();
            assertThat(retrieved.causationId()).isNotEmpty();
            assertThat(retrieved.actor()).isEqualTo(Optional.of("user-1"));
            assertThat(retrieved.classification()).isEqualTo(Optional.of("public"));
            assertThat(retrieved.policyContext()).isEqualTo(Optional.of("policy-context-123"));
            assertThat(retrieved.provenance()).isEqualTo(Optional.of("datacloud.launcher"));
            assertThat(retrieved.traceContext()).isEqualTo(Optional.of("trace-123"));
            assertThat(retrieved.headers()).containsKey("eventId");
            assertThat(retrieved.timestamp()).isNotNull();
        } finally {
            client.close();
        }
    }

    @Test
    @DisplayName("DC-P0-05: Event query returns events with complete envelope metadata")
    void eventQueryReturnsEventsWithCompleteEnvelopeMetadata() {
        DataCloudClient client = DataCloud.forTesting();
        try {
            // Append multiple events with different envelope fields
            DataCloudClient.Event event1 = DataCloudClient.Event.builder()
                .type("entity.created")
                .payload(Map.of("id", "1"))
                .source("source-1")
                .actor("user-1")
                .classification("public")
                .traceContext("trace-1")
                .build();

            DataCloudClient.Event event2 = DataCloudClient.Event.builder()
                .type("entity.updated")
                .payload(Map.of("id", "2"))
                .source("source-2")
                .actor("user-2")
                .classification("sensitive")
                .traceContext("trace-2")
                .build();

            runPromise(() -> client.appendEvent("tenant-1", event1));
            runPromise(() -> client.appendEvent("tenant-1", event2));

            // Query all events
            var events = runPromise(() -> client.queryEvents("tenant-1", DataCloudClient.EventQuery.all()));
            assertThat(events).hasSizeGreaterThanOrEqualTo(2);

            // Verify envelope metadata is preserved for each event
            for (DataCloudClient.Event event : events) {
                assertThat(event.type()).isNotEmpty();
                assertThat(event.payload()).isNotEmpty();
                assertThat(event.source()).isNotEmpty();
                assertThat(event.timestamp()).isNotNull();
                assertThat(event.headers()).isNotNull();
            }
        } finally {
            client.close();
        }
    }

    @Test
    @DisplayName("DC-P0-05: Event envelope with optional null fields round-trips correctly")
    void eventEnvelopeWithOptionalNullFieldsRoundTripsCorrectly() {
        DataCloudClient client = DataCloud.forTesting();
        try {
            // Event with minimal required fields (optional fields as empty)
            DataCloudClient.Event minimalEvent = DataCloudClient.Event.builder()
                .type("minimal.event")
                .payload(Map.of("data", "value"))
                .source("minimal-source")
                .build();

            DataCloudClient.Offset offset = runPromise(() -> client.appendEvent("tenant-1", minimalEvent));
            assertThat(offset.value()).isGreaterThanOrEqualTo(0);

            // Query back
            var events = runPromise(() -> client.queryEvents("tenant-1", DataCloudClient.EventQuery.fromOffset(offset.value())));
            assertThat(events).hasSize(1);

            DataCloudClient.Event retrieved = events.get(0);
            assertThat(retrieved.type()).isEqualTo("minimal.event");
            assertThat(retrieved.source()).isEqualTo(Optional.of("minimal-source"));
            // Optional fields should be empty, not null
            assertThat(retrieved.subjectType()).isEmpty();
            assertThat(retrieved.subjectId()).isEmpty();
            assertThat(retrieved.actor()).isEmpty();
        } finally {
            client.close();
        }
    }

    @Test
    @DisplayName("DC-P0-05: Event envelope correlation and causation IDs are preserved")
    void eventEnvelopeCorrelationAndCausationIdsArePreserved() {
        DataCloudClient client = DataCloud.forTesting();
        try {
            String correlationId = "corr-" + System.currentTimeMillis();
            String causationId = "cause-" + System.currentTimeMillis();

            DataCloudClient.Event event = DataCloudClient.Event.builder()
                .type("correlated.event")
                .payload(Map.of("action", "create"))
                .source("correlation-test")
                .correlationId(correlationId)
                .causationId(causationId)
                .build();

            DataCloudClient.Offset offset = runPromise(() -> client.appendEvent("tenant-1", event));

            // Query back
            var events = runPromise(() -> client.queryEvents("tenant-1", DataCloudClient.EventQuery.fromOffset(offset.value())));
            assertThat(events).hasSize(1);

            DataCloudClient.Event retrieved = events.get(0);
            assertThat(retrieved.correlationId()).isEqualTo(Optional.of(correlationId));
            assertThat(retrieved.causationId()).isEqualTo(Optional.of(causationId));
        } finally {
            client.close();
        }
    }

    @Test
    @DisplayName("DC-P0-05: Event envelope trace context is preserved for distributed tracing")
    void eventEnvelopeTraceContextIsPreservedForDistributedTracing() {
        DataCloudClient client = DataCloud.forTesting();
        try {
            String traceContext = "trace-parent:00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";

            DataCloudClient.Event event = DataCloudClient.Event.builder()
                .type("traced.event")
                .payload(Map.of("operation", "query"))
                .source("trace-test")
                .traceContext(traceContext)
                .build();

            DataCloudClient.Offset offset = runPromise(() -> client.appendEvent("tenant-1", event));

            // Query back
            var events = runPromise(() -> client.queryEvents("tenant-1", DataCloudClient.EventQuery.fromOffset(offset.value())));
            assertThat(events).hasSize(1);

            DataCloudClient.Event retrieved = events.get(0);
            assertThat(retrieved.traceContext()).isEqualTo(Optional.of(traceContext));
        } finally {
            client.close();
        }
    }

    @Test
    @DisplayName("DC-P0-05: Event envelope actor field is preserved for audit trail")
    void eventEnvelopeActorFieldIsPreservedForAuditTrail() {
        DataCloudClient client = DataCloud.forTesting();
        try {
            String actor = "service-account-123";

            DataCloudClient.Event event = DataCloudClient.Event.builder()
                .type("audit.event")
                .payload(Map.of("action", "delete"))
                .source("audit-test")
                .actor(actor)
                .classification("critical")
                .build();

            DataCloudClient.Offset offset = runPromise(() -> client.appendEvent("tenant-1", event));

            // Query back
            var events = runPromise(() -> client.queryEvents("tenant-1", DataCloudClient.EventQuery.fromOffset(offset.value())));
            assertThat(events).hasSize(1);

            DataCloudClient.Event retrieved = events.get(0);
            assertThat(retrieved.actor()).isEqualTo(Optional.of(actor));
            assertThat(retrieved.classification()).isEqualTo(Optional.of("critical"));
        } finally {
            client.close();
        }
    }

    @Test
    @DisplayName("DC-P0-05: Event envelope headers preserve custom metadata")
    void eventEnvelopeHeadersPreserveCustomMetadata() {
        DataCloudClient client = DataCloud.forTesting();
        try {
            Map<String, String> customHeaders = Map.of(
                "custom-field-1", "value-1",
                "custom-field-2", "value-2",
                "eventId", "custom-event-123"
            );

            DataCloudClient.Event event = DataCloudClient.Event.builder()
                .type("custom.metadata.event")
                .payload(Map.of("data", "test"))
                .source("headers-test")
                .headers(customHeaders)
                .build();

            DataCloudClient.Offset offset = runPromise(() -> client.appendEvent("tenant-1", event));

            // Query back
            var events = runPromise(() -> client.queryEvents("tenant-1", DataCloudClient.EventQuery.fromOffset(offset.value())));
            assertThat(events).hasSize(1);

            DataCloudClient.Event retrieved = events.get(0);
            assertThat(retrieved.headers()).containsEntry("custom-field-1", "value-1");
            assertThat(retrieved.headers()).containsEntry("custom-field-2", "value-2");
            assertThat(retrieved.headers()).containsEntry("eventId", "custom-event-123");
        } finally {
            client.close();
        }
    }
}

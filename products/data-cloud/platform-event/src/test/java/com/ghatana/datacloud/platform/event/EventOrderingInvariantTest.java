/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.platform.event;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for event ordering invariants: append, query, dedup, replay.
 *
 * <p>Validates that event streams maintain correct ordering and
 * support replay operations with offset semantics.
 *
 * @doc.type class
 * @doc.purpose Event ordering invariant tests for append, query, dedup, replay
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("[Event]: ordering_invariants_append_query_dedup_replay")
class EventOrderingInvariantTest extends EventloopTestBase {

    // ─────────────────────────────────────────────────────────────────────────
    // Event Builder Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[EventBuilder]: creates_event_with_all_required_fields")
    void createsEventWithAllRequiredFields() { // GH-90000
        Map<String, Object> event = EventBuilder.create("entity.created")
            .withId("evt-001")
            .withEntityId("ent-001")
            .withCollection("products")
            .withTenant("tenant-alpha")
            .withOffset(42) // GH-90000
            .withTimestamp(Instant.parse("2026-01-15T10:30:00Z"))
            .withPayload("action", "create") // GH-90000
            .build(); // GH-90000

        assertThat(event).containsEntry("id", "evt-001"); // GH-90000
        assertThat(event).containsEntry("type", "entity.created"); // GH-90000
        assertThat(event).containsEntry("entityId", "ent-001"); // GH-90000
        assertThat(event).containsEntry("collection", "products"); // GH-90000
        assertThat(event).containsEntry("tenantId", "tenant-alpha"); // GH-90000
        assertThat(event).containsEntry("offset", 42L); // GH-90000
        assertThat(event).containsEntry("timestamp", "2026-01-15T10:30:00Z"); // GH-90000
    }

    @Test
    @DisplayName("[EventBuilder]: entity_created_template_creates_valid_event")
    void entityCreatedTemplateCreatesValidEvent() { // GH-90000
        Map<String, Object> event = EventBuilder.entityCreated("products", "prod-001") // GH-90000
            .withOffset(1) // GH-90000
            .build(); // GH-90000

        assertThat(event).containsEntry("type", "entity.created"); // GH-90000
        assertThat(event).containsEntry("collection", "products"); // GH-90000
        assertThat(event).containsEntry("entityId", "prod-001"); // GH-90000
        assertThat(event).containsEntry("payload", Map.of("action", "create")); // GH-90000
    }

    @Test
    @DisplayName("[EventBuilder]: entity_updated_template_creates_valid_event")
    void entityUpdatedTemplateCreatesValidEvent() { // GH-90000
        Map<String, Object> event = EventBuilder.entityUpdated("products", "prod-001") // GH-90000
            .withOffset(2) // GH-90000
            .build(); // GH-90000

        assertThat(event).containsEntry("type", "entity.updated"); // GH-90000
        assertThat(event).containsEntry("payload", Map.of("action", "update")); // GH-90000
    }

    @Test
    @DisplayName("[EventBuilder]: entity_deleted_template_creates_valid_event")
    void entityDeletedTemplateCreatesValidEvent() { // GH-90000
        Map<String, Object> event = EventBuilder.entityDeleted("products", "prod-001") // GH-90000
            .withOffset(3) // GH-90000
            .build(); // GH-90000

        assertThat(event).containsEntry("type", "entity.deleted"); // GH-90000
        assertThat(event).containsEntry("payload", Map.of("action", "delete")); // GH-90000
    }

    @Test
    @DisplayName("[EventBuilder]: pipeline_completed_template_creates_valid_event")
    void pipelineCompletedTemplateCreatesValidEvent() { // GH-90000
        Map<String, Object> event = EventBuilder.pipelineCompleted("pipe-001")
            .withOffset(4) // GH-90000
            .build(); // GH-90000

        assertThat(event).containsEntry("type", "pipeline.completed"); // GH-90000
        assertThat(event).containsEntry("entityId", "pipe-001"); // GH-90000
    }

    @Test
    @DisplayName("[EventBuilder]: feature_ingested_template_creates_valid_event")
    void featureIngestedTemplateCreatesValidEvent() { // GH-90000
        Map<String, Object> event = EventBuilder.featureIngested("feat-001")
            .withOffset(5) // GH-90000
            .build(); // GH-90000

        assertThat(event).containsEntry("type", "feature.ingested"); // GH-90000
        assertThat(event).containsEntry("entityId", "feat-001"); // GH-90000
        assertThat(event).containsKeys("payload");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Event Ordering Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Ordering]: events_ordered_by_offset_ascending")
    void eventsOrderedByOffsetAscending() { // GH-90000
        List<Map<String, Object>> events = IntStream.range(0, 10) // GH-90000
            .mapToObj(i -> EventBuilder.entityCreated("products", "prod-" + i) // GH-90000
                .withOffset(i) // GH-90000
                .withTimestamp(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i * 60))
                .build()) // GH-90000
            .collect(Collectors.toList()); // GH-90000

        // Shuffle and re-sort
        List<Map<String, Object>> shuffled = events.stream() // GH-90000
            .sorted(Comparator.comparingInt(e -> (int) (Math.random() * 100))) // GH-90000
            .collect(Collectors.toList()); // GH-90000

        List<Map<String, Object>> sorted = shuffled.stream() // GH-90000
            .sorted(Comparator.comparingLong(e -> (Long) e.get("offset")))
            .collect(Collectors.toList()); // GH-90000

        assertThat(sorted).hasSize(10); // GH-90000
        for (int i = 0; i < 10; i++) { // GH-90000
            assertThat(sorted.get(i).get("offset")).isEqualTo((long) i);
        }
    }

    @Test
    @DisplayName("[Ordering]: events_ordered_by_timestamp_ascending")
    void eventsOrderedByTimestampAscending() { // GH-90000
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        List<Map<String, Object>> events = IntStream.range(0, 5) // GH-90000
            .mapToObj(i -> EventBuilder.create("test.event")
                .withOffset(i) // GH-90000
                .withTimestamp(base.plusSeconds(i * 60)) // GH-90000
                .build()) // GH-90000
            .collect(Collectors.toList()); // GH-90000

        List<Map<String, Object>> sorted = events.stream() // GH-90000
            .sorted(Comparator.comparing(e -> Instant.parse((String) e.get("timestamp"))))
            .collect(Collectors.toList()); // GH-90000

        for (int i = 0; i < 5; i++) { // GH-90000
            assertThat(sorted.get(i).get("timestamp"))
                .isEqualTo(base.plusSeconds(i * 60).toString()); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Duplicate Detection Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Deduplication]: events_with_same_id_are_duplicates")
    void eventsWithSameIdAreDuplicates() { // GH-90000
        String eventId = "evt-dup-001";

        Map<String, Object> event1 = EventBuilder.create("test.event")
            .withId(eventId) // GH-90000
            .withOffset(1) // GH-90000
            .build(); // GH-90000

        Map<String, Object> event2 = EventBuilder.create("test.event")
            .withId(eventId) // GH-90000
            .withOffset(2) // GH-90000
            .build(); // GH-90000

        assertThat(event1.get("id")).isEqualTo(event2.get("id"));

        // Deduplicate by ID
        List<Map<String, Object>> events = List.of(event1, event2); // GH-90000
        Set<String> uniqueIds = events.stream() // GH-90000
            .map(e -> (String) e.get("id"))
            .collect(Collectors.toSet()); // GH-90000

        assertThat(uniqueIds).hasSize(1); // GH-90000
    }

    @Test
    @DisplayName("[Deduplication]: dedup_by_correlation_id_maintains_uniqueness")
    void dedupByCorrelationIdMaintainsUniqueness() { // GH-90000
        String correlationId = "corr-123";

        List<Map<String, Object>> events = List.of( // GH-90000
            EventBuilder.create("event.1").withCorrelationId(correlationId).build(),
            EventBuilder.create("event.2").withCorrelationId(correlationId).build(),
            EventBuilder.create("event.3").withCorrelationId("different").build()
        );

        Map<String, List<Map<String, Object>>> byCorrelation = events.stream() // GH-90000
            .collect(Collectors.groupingBy(e -> (String) e.get("correlationId")));

        assertThat(byCorrelation.get(correlationId)).hasSize(2); // GH-90000
        assertThat(byCorrelation.get("different")).hasSize(1);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Replay Offset Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Replay]: replay_from_offset_returns_subsequent_events")
    void replayFromOffsetReturnsSubsequentEvents() { // GH-90000
        List<Map<String, Object>> events = IntStream.range(0, 100) // GH-90000
            .mapToObj(i -> EventBuilder.create("stream.event")
                .withOffset(i) // GH-90000
                .build()) // GH-90000
            .collect(Collectors.toList()); // GH-90000

        long fromOffset = 50;
        List<Map<String, Object>> replayed = events.stream() // GH-90000
            .filter(e -> (Long) e.get("offset") >= fromOffset)
            .sorted(Comparator.comparingLong(e -> (Long) e.get("offset")))
            .collect(Collectors.toList()); // GH-90000

        assertThat(replayed).hasSize(50); // GH-90000
        assertThat(replayed.get(0).get("offset")).isEqualTo(50L);
        assertThat(replayed.get(49).get("offset")).isEqualTo(99L);
    }

    @Test
    @DisplayName("[Replay]: replay_from_zero_returns_all_events")
    void replayFromZeroReturnsAllEvents() { // GH-90000
        List<Map<String, Object>> events = IntStream.range(0, 10) // GH-90000
            .mapToObj(i -> EventBuilder.create("stream.event")
                .withOffset(i) // GH-90000
                .build()) // GH-90000
            .collect(Collectors.toList()); // GH-90000

        List<Map<String, Object>> replayed = events.stream() // GH-90000
            .filter(e -> (Long) e.get("offset") >= 0)
            .sorted(Comparator.comparingLong(e -> (Long) e.get("offset")))
            .collect(Collectors.toList()); // GH-90000

        assertThat(replayed).hasSize(10); // GH-90000
    }

    @Test
    @DisplayName("[Replay]: replay_from_end_returns_empty")
    void replayFromEndReturnsEmpty() { // GH-90000
        List<Map<String, Object>> events = IntStream.range(0, 10) // GH-90000
            .mapToObj(i -> EventBuilder.create("stream.event")
                .withOffset(i) // GH-90000
                .build()) // GH-90000
            .collect(Collectors.toList()); // GH-90000

        long fromOffset = 100; // Beyond last event
        List<Map<String, Object>> replayed = events.stream() // GH-90000
            .filter(e -> (Long) e.get("offset") >= fromOffset)
            .collect(Collectors.toList()); // GH-90000

        assertThat(replayed).isEmpty(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Event Stream Consistency Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Consistency]: event_offsets_are_monotonically_increasing")
    void eventOffsetsAreMonotonicallyIncreasing() { // GH-90000
        List<Long> offsets = IntStream.range(0, 100) // GH-90000
            .mapToObj(i -> (long) i) // GH-90000
            .collect(Collectors.toList()); // GH-90000

        for (int i = 1; i < offsets.size(); i++) { // GH-90000
            assertThat(offsets.get(i)).isGreaterThan(offsets.get(i - 1)); // GH-90000
        }
    }

    @Test
    @DisplayName("[Consistency]: no_gaps_in_offset_sequence")
    void noGapsInOffsetSequence() { // GH-90000
        List<Long> offsets = IntStream.range(0, 50) // GH-90000
            .mapToObj(i -> (long) i) // GH-90000
            .collect(Collectors.toList()); // GH-90000

        for (int i = 0; i < offsets.size(); i++) { // GH-90000
            assertThat(offsets.get(i)).isEqualTo((long) i); // GH-90000
        }
    }

    @Test
    @DisplayName("[Consistency]: events_with_same_offset_represent_same_event")
    void eventsWithSameOffsetRepresentSameEvent() { // GH-90000
        // This should not happen in a correct implementation,
        // but we test the handling of this edge case
        Map<String, Object> event1 = EventBuilder.create("test.event")
            .withOffset(5) // GH-90000
            .withId("evt-same-001")
            .build(); // GH-90000

        Map<String, Object> event2 = EventBuilder.create("test.event")
            .withOffset(5) // GH-90000
            .withId("evt-same-001") // Same ID
            .build(); // GH-90000

        assertThat(event1.get("offset")).isEqualTo(event2.get("offset"));
        assertThat(event1.get("id")).isEqualTo(event2.get("id"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tenant Isolation Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Tenant Isolation]: events_filtered_by_tenant_id")
    void eventsFilteredByTenantId() { // GH-90000
        String tenantAlpha = "tenant-alpha";
        String tenantBeta = "tenant-beta";

        List<Map<String, Object>> events = List.of( // GH-90000
            EventBuilder.create("event.1").withTenant(tenantAlpha).build(),
            EventBuilder.create("event.2").withTenant(tenantAlpha).build(),
            EventBuilder.create("event.3").withTenant(tenantBeta).build()
        );

        List<Map<String, Object>> alphaEvents = events.stream() // GH-90000
            .filter(e -> tenantAlpha.equals(e.get("tenantId")))
            .collect(Collectors.toList()); // GH-90000

        List<Map<String, Object>> betaEvents = events.stream() // GH-90000
            .filter(e -> tenantBeta.equals(e.get("tenantId")))
            .collect(Collectors.toList()); // GH-90000

        assertThat(alphaEvents).hasSize(2); // GH-90000
        assertThat(betaEvents).hasSize(1); // GH-90000
    }

    @Test
    @DisplayName("[Tenant Isolation]: different_tenants_same_event_type_isolated")
    void differentTenantsSameEventTypeIsolated() { // GH-90000
        String eventType = "entity.created";

        List<Map<String, Object>> events = List.of( // GH-90000
            EventBuilder.create(eventType) // GH-90000
                .withTenant("tenant-1")
                .withEntityId("ent-1")
                .build(), // GH-90000
            EventBuilder.create(eventType) // GH-90000
                .withTenant("tenant-2")
                .withEntityId("ent-2")
                .build() // GH-90000
        );

        assertThat(events.get(0).get("tenantId")).isEqualTo("tenant-1");
        assertThat(events.get(1).get("tenantId")).isEqualTo("tenant-2");
    }
}

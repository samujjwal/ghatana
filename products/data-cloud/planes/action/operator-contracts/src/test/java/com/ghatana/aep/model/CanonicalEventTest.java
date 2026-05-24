package com.ghatana.aep.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CanonicalEventTest {

    @Test
    void requiresMandatoryIdentityFields() {
        assertThatThrownBy(() -> event(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("eventId");
    }

    @Test
    void normalizesNullableCollectionsToImmutableEmptyCollections() {
        CanonicalEvent event = new CanonicalEvent(
            "event-1",
            "tenant-a",
            "deploy.started",
            "1.0.0",
            Instant.parse("2026-05-23T00:00:00Z"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            null,
            null,
            "corr-1",
            Optional.empty(),
            null,
            null,
            null,
            null,
            "idem-1");

        assertThat(event.source()).isEmpty();
        assertThat(event.entityRefs()).isEmpty();
        assertThat(event.payload()).isEmpty();
        assertThat(event.confidence()).isEmpty();
        assertThat(event.provenance()).isEmpty();
        assertThat(event.policyTags()).isEmpty();
    }

    @Test
    void copiesInputCollections() {
        Map<String, Object> payload = Map.of("service", "checkout");
        List<String> refs = List.of("service:checkout");

        CanonicalEvent event = new CanonicalEvent(
            "event-1",
            "tenant-a",
            "deploy.started",
            "1.0.0",
            Instant.parse("2026-05-23T00:00:00Z"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Map.of(),
            refs,
            "corr-1",
            Optional.empty(),
            payload,
            Map.of(),
            Map.of(),
            List.of(),
            "idem-1");

        assertThat(event.entityRefs()).containsExactly("service:checkout");
        assertThat(event.payload()).containsEntry("service", "checkout");
    }

    private static CanonicalEvent event(String eventId) {
        return new CanonicalEvent(
            eventId,
            "tenant-a",
            "deploy.started",
            "1.0.0",
            Instant.parse("2026-05-23T00:00:00Z"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Map.of(),
            List.of(),
            "corr-1",
            Optional.empty(),
            Map.of(),
            Map.of(),
            Map.of(),
            List.of(),
            "idem-1");
    }
}

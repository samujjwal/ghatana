/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.event.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers {@link Event}'s payload-alias builder path and free accessor methods.
 */
@DisplayName("Event model")
class EventModelTest {

    private Event buildMinimal(Map<String, Object> payload) { // GH-90000
        return Event.builder() // GH-90000
                .tenantId("tenant-1")
                .streamName("orders")
                .eventOffset(1L) // GH-90000
                .eventTypeName("commerce.order.created")
                .occurrenceTime(Instant.now()) // GH-90000
                .detectionTime(Instant.now()) // GH-90000
                .payload(payload)   // exercises EventBuilder.payload() alias // GH-90000
                .build(); // GH-90000
    }

    @Test
    void builderPayloadAliasPopulatesData() { // GH-90000
        Event event = buildMinimal(Map.of("orderId", "order-1", "amount", 99.99)); // GH-90000
        assertThat(event.getPayload()).containsEntry("orderId", "order-1"); // GH-90000
    }

    @Test
    void getPayloadAsReturnsTypedValue() { // GH-90000
        Event event = buildMinimal(Map.of("qty", 5)); // GH-90000
        assertThat(event.getPayloadAs("qty", Integer.class)).isEqualTo(5); // GH-90000
    }

    @Test
    void getPayloadAsReturnsNullForMissingKey() { // GH-90000
        Event event = buildMinimal(Map.of()); // GH-90000
        assertThat(event.getPayloadAs("missing", String.class)).isNull(); // GH-90000
    }

    @Test
    void getHeaderReturnsNullWhenAbsent() { // GH-90000
        Event event = buildMinimal(Map.of()); // GH-90000
        assertThat(event.getHeader("x-not-set")).isNull();
    }

    @Test
    void toStringContainsEventType() { // GH-90000
        Event event = buildMinimal(Map.of()); // GH-90000
        assertThat(event.toString()).contains("commerce.order.created");
    }
}

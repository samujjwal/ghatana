/*
 * Copyright (c) 2026 Ghatana Inc. 
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

    private Event buildMinimal(Map<String, Object> payload) { 
        return Event.builder() 
                .tenantId("tenant-1")
                .streamName("orders")
                .eventOffset(1L) 
                .eventTypeName("commerce.order.created")
                .occurrenceTime(Instant.now()) 
                .detectionTime(Instant.now()) 
                .payload(payload)   // exercises EventBuilder.payload() alias 
                .build(); 
    }

    @Test
    void builderPayloadAliasPopulatesData() { 
        Event event = buildMinimal(Map.of("orderId", "order-1", "amount", 99.99)); 
        assertThat(event.getPayload()).containsEntry("orderId", "order-1"); 
    }

    @Test
    void getPayloadAsReturnsTypedValue() { 
        Event event = buildMinimal(Map.of("qty", 5)); 
        assertThat(event.getPayloadAs("qty", Integer.class)).isEqualTo(5); 
    }

    @Test
    void getPayloadAsReturnsNullForMissingKey() { 
        Event event = buildMinimal(Map.of()); 
        assertThat(event.getPayloadAs("missing", String.class)).isNull(); 
    }

    @Test
    void getHeaderReturnsNullWhenAbsent() { 
        Event event = buildMinimal(Map.of()); 
        assertThat(event.getHeader("x-not-set")).isNull();
    }

    @Test
    void toStringContainsEventType() { 
        Event event = buildMinimal(Map.of()); 
        assertThat(event.toString()).contains("commerce.order.created");
    }
}

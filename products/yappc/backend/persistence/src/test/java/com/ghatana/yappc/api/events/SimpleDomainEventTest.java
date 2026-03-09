/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module — Simple Event Tests
 */
package com.ghatana.yappc.api.events;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**

 * @doc.type class

 * @doc.purpose Handles simple domain event test operations

 * @doc.layer product

 * @doc.pattern Test

 */

class SimpleDomainEventTest {

    @Test
    void testDomainEventImplementation() {
        // Given
        String eventType = "TEST_EVENT";
        String aggregateId = "agg123";
        String aggregateType = "TestAggregate";
        String tenantId = "tenant123";
        String userId = "user123";

        // When
        TestDomainEvent event = new TestDomainEvent(
            eventType, aggregateId, aggregateType, tenantId, userId);

        // Then
        assertThat(event.getEventId()).isNotNull();
        assertThat(event.getEventType()).isEqualTo(eventType);
        assertThat(event.getAggregateId()).isEqualTo(aggregateId);
        assertThat(event.getAggregateType()).isEqualTo(aggregateType);
        assertThat(event.getTenantId()).isEqualTo(tenantId);
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getOccurredAt()).isNotNull();
        assertThat(event.getOccurredAt()).isBefore(Instant.now().plusSeconds(1));
        assertThat(event.getSchemaVersion()).isEqualTo(1);
    }

    @Test
    void testDomainEventToString() {
        // Given
        TestDomainEvent event = new TestDomainEvent(
            "TEST_EVENT", "agg123", "TestAggregate", "tenant123", "user123");

        // When
        String stringRepresentation = event.toString();

        // Then
        assertThat(stringRepresentation).contains("TestDomainEvent");
        assertThat(stringRepresentation).contains("agg123");
        assertThat(stringRepresentation).contains("tenant123");
    }

    @Test
    void testDomainEventPayload() {
        // Given
        TestDomainEvent event = new TestDomainEvent(
            "TEST_EVENT", "agg123", "TestAggregate", "tenant123", "user123");

        // When
        Map<String, Object> payload = event.toPayload();

        // Then
        assertThat(payload).isNotNull();
        assertThat(payload).hasSize(2);
        assertThat(payload.get("testField")).isEqualTo("testValue");
        assertThat(payload.get("testNumber")).isEqualTo(42);
    }

    @Test
    void testDomainEventWithNullParameters() {
        // When/Then - The constructor doesn't validate null parameters
        assertThatNoException().isThrownBy(() -> {
            new TestDomainEvent(null, "agg123", "TestAggregate", "tenant123", "user123");
        });
    }

    @Test
    void testDomainEventUniqueIdGeneration() {
        // Given
        TestDomainEvent event1 = new TestDomainEvent(
            "TEST_EVENT", "agg123", "TestAggregate", "tenant123", "user123");
        TestDomainEvent event2 = new TestDomainEvent(
            "TEST_EVENT", "agg123", "TestAggregate", "tenant123", "user123");

        // When/Then
        assertThat(event1.getEventId()).isNotEqualTo(event2.getEventId());
        assertThat(UUID.fromString(event1.getEventId())).isNotNull();
        assertThat(UUID.fromString(event2.getEventId())).isNotNull();
    }

    @Test
    void testDomainEventTimestampGeneration() {
        // Given
        Instant before = Instant.now();
        TestDomainEvent event = new TestDomainEvent(
            "TEST_EVENT", "agg123", "TestAggregate", "tenant123", "user123");
        Instant after = Instant.now();

        // Then
        assertThat(event.getOccurredAt()).isBetween(before, after);
    }

    // Test implementation of DomainEvent
    private static class TestDomainEvent extends DomainEvent {
        private final String testField;
        private final int testNumber;

        public TestDomainEvent(String eventType, String aggregateId, String aggregateType, 
                             String tenantId, String userId) {
            super(eventType, aggregateId, aggregateType, tenantId, userId);
            this.testField = "testValue";
            this.testNumber = 42;
        }

        @Override
        public Map<String, Object> toPayload() {
            return Map.of(
                "testField", testField,
                "testNumber", testNumber
            );
        }
    }
}

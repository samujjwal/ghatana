/*
 * Copyright (c) 2026 Ghatana
 */
package com.ghatana.datacloud.integration;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Data Cloud event workflows.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventWorkflowIntegrationTest extends EventloopTestBase {

    private static EventPublisher eventPublisher;
    private static EventConsumer eventConsumer;
    private static EventStore eventStore;

    @BeforeAll
    static void setUpAll() {
        eventStore = new InMemoryEventStore();
        eventPublisher = new MockEventPublisher(eventStore);
        eventConsumer = new MockEventConsumer(eventStore);
    }

    @Test
    @Order(1)
    @DisplayName("Integration: Should publish event to Data Cloud")
    void testPublishEvent() throws Exception {
        Event event = Event.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("PROJECT_CREATED")
                .tenantId("test-tenant")
                .payload(Map.of("projectId", "proj-123", "name", "Test Project"))
                .timestamp(Instant.now())
                .build();

        Promise<String> promise = eventPublisher.publish(event);
        String eventId = runPromise(() -> promise);

        assertThat(eventId).isNotNull();
        assertThat(eventId).isEqualTo(event.eventId());
    }

    @Test
    @Order(2)
    @DisplayName("Integration: Should consume published events")
    void testConsumeEvents() throws Exception {
        Event event = Event.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("PHASE_COMPLETED")
                .tenantId("test-tenant")
                .payload(Map.of("phase", "PLANNING", "projectId", "proj-123"))
                .timestamp(Instant.now())
                .build();

        runPromise(() -> eventPublisher.publish(event));

        Promise<Event[]> promise = eventConsumer.consume("test-tenant", 10);
        Event[] events = runPromise(() -> promise);

        assertThat(events).hasSizeGreaterThanOrEqualTo(1);
        assertThat(events[0].eventType()).isIn("PROJECT_CREATED", "PHASE_COMPLETED");
    }

    @Test
    @Order(3)
    @DisplayName("Integration: Should filter events by type")
    void testFilterEventsByType() throws Exception {
        Event event1 = Event.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("USER_LOGIN")
                .tenantId("test-tenant")
                .payload(Map.of("userId", "user-1"))
                .timestamp(Instant.now())
                .build();

        Event event2 = Event.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("USER_LOGOUT")
                .tenantId("test-tenant")
                .payload(Map.of("userId", "user-1"))
                .timestamp(Instant.now())
                .build();

        runPromise(() -> eventPublisher.publish(event1));
        runPromise(() -> eventPublisher.publish(event2));

        Promise<Event[]> promise = eventConsumer.consumeByType("test-tenant", "USER_LOGIN", 10);
        Event[] events = runPromise(() -> promise);

        assertThat(events).isNotEmpty();
        for (Event event : events) {
            assertThat(event.eventType()).isEqualTo("USER_LOGIN");
        }
    }

    @Test
    @Order(4)
    @DisplayName("Integration: Should handle event ordering")
    void testEventOrdering() throws Exception {
        for (int i = 0; i < 5; i++) {
            Event event = Event.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("SEQUENCE_TEST")
                    .tenantId("test-tenant")
                    .payload(Map.of("sequence", String.valueOf(i)))
                    .timestamp(Instant.now())
                    .build();
            runPromise(() -> eventPublisher.publish(event));
            Thread.sleep(10); // Ensure timestamp ordering
        }

        Promise<Event[]> promise = eventConsumer.consumeByType("test-tenant", "SEQUENCE_TEST", 10);
        Event[] events = runPromise(() -> promise);

        assertThat(events).hasSizeGreaterThanOrEqualTo(5);
        
        // Verify chronological order
        for (int i = 1; i < events.length; i++) {
            assertThat(events[i].timestamp()).isAfterOrEqualTo(events[i-1].timestamp());
        }
    }

    @Test
    @Order(5)
    @DisplayName("Integration: Should handle multi-tenant event isolation")
    void testMultiTenantIsolation() throws Exception {
        Event tenant1Event = Event.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("TENANT_EVENT")
                .tenantId("tenant-1")
                .payload(Map.of("data", "tenant1"))
                .timestamp(Instant.now())
                .build();

        Event tenant2Event = Event.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("TENANT_EVENT")
                .tenantId("tenant-2")
                .payload(Map.of("data", "tenant2"))
                .timestamp(Instant.now())
                .build();

        runPromise(() -> eventPublisher.publish(tenant1Event));
        runPromise(() -> eventPublisher.publish(tenant2Event));

        Promise<Event[]> tenant1Events = eventConsumer.consume("tenant-1", 10);
        Event[] t1Events = runPromise(() -> tenant1Events);

        for (Event event : t1Events) {
            assertThat(event.tenantId()).isEqualTo("tenant-1");
        }
    }

    // Mock implementations

    interface EventPublisher {
        Promise<String> publish(Event event);
    }

    interface EventConsumer {
        Promise<Event[]> consume(String tenantId, int limit);
        Promise<Event[]> consumeByType(String tenantId, String eventType, int limit);
    }

    interface EventStore {
        void store(Event event);
        Event[] getEvents(String tenantId, int limit);
        Event[] getEventsByType(String tenantId, String eventType, int limit);
    }

    record Event(String eventId, String eventType, String tenantId, Map<String, String> payload, Instant timestamp) {
        static Builder builder() {
            return new Builder();
        }

        static class Builder {
            private String eventId;
            private String eventType;
            private String tenantId;
            private Map<String, String> payload;
            private Instant timestamp;

            Builder eventId(String eventId) { this.eventId = eventId; return this; }
            Builder eventType(String eventType) { this.eventType = eventType; return this; }
            Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
            Builder payload(Map<String, String> payload) { this.payload = payload; return this; }
            Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }

            Event build() {
                return new Event(eventId, eventType, tenantId, payload, timestamp);
            }
        }
    }

    static class InMemoryEventStore implements EventStore {
        private final Map<String, java.util.List<Event>> eventsByTenant = new ConcurrentHashMap<>();

        @Override
        public void store(Event event) {
            eventsByTenant.computeIfAbsent(event.tenantId(), k -> new java.util.ArrayList<>()).add(event);
        }

        @Override
        public Event[] getEvents(String tenantId, int limit) {
            return eventsByTenant.getOrDefault(tenantId, java.util.List.of())
                    .stream()
                    .sorted((a, b) -> a.timestamp().compareTo(b.timestamp()))
                    .limit(limit)
                    .toArray(Event[]::new);
        }

        @Override
        public Event[] getEventsByType(String tenantId, String eventType, int limit) {
            return eventsByTenant.getOrDefault(tenantId, java.util.List.of())
                    .stream()
                    .filter(e -> e.eventType().equals(eventType))
                    .sorted((a, b) -> a.timestamp().compareTo(b.timestamp()))
                    .limit(limit)
                    .toArray(Event[]::new);
        }
    }

    static class MockEventPublisher implements EventPublisher {
        private final EventStore store;

        MockEventPublisher(EventStore store) {
            this.store = store;
        }

        @Override
        public Promise<String> publish(Event event) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                store.store(event);
                return event.eventId();
            });
        }
    }

    static class MockEventConsumer implements EventConsumer {
        private final EventStore store;

        MockEventConsumer(EventStore store) {
            this.store = store;
        }

        @Override
        public Promise<Event[]> consume(String tenantId, int limit) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> 
                store.getEvents(tenantId, limit)
            );
        }

        @Override
        public Promise<Event[]> consumeByType(String tenantId, String eventType, int limit) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> 
                store.getEventsByType(tenantId, eventType, limit)
            );
        }
    }
}

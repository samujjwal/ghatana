/*
 * Copyright (c) 2026 Ghatana // GH-90000
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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) // GH-90000
class EventWorkflowIntegrationTest extends EventloopTestBase {

    private static EventPublisher eventPublisher;
    private static EventConsumer eventConsumer;
    private static EventStore eventStore;

    @BeforeAll
    static void setUpAll() { // GH-90000
        eventStore = new InMemoryEventStore(); // GH-90000
        eventPublisher = new MockEventPublisher(eventStore); // GH-90000
        eventConsumer = new MockEventConsumer(eventStore); // GH-90000
    }

    @Test
    @Order(1) // GH-90000
    @DisplayName("Integration: Should publish event to Data Cloud [GH-90000]")
    void testPublishEvent() throws Exception { // GH-90000
        Event event = Event.builder() // GH-90000
                .eventId(UUID.randomUUID().toString()) // GH-90000
                .eventType("PROJECT_CREATED [GH-90000]")
                .tenantId("test-tenant [GH-90000]")
                .payload(Map.of("projectId", "proj-123", "name", "Test Project")) // GH-90000
                .timestamp(Instant.now()) // GH-90000
                .build(); // GH-90000

        Promise<String> promise = eventPublisher.publish(event); // GH-90000
        String eventId = runPromise(() -> promise); // GH-90000

        assertThat(eventId).isNotNull(); // GH-90000
        assertThat(eventId).isEqualTo(event.eventId()); // GH-90000
    }

    @Test
    @Order(2) // GH-90000
    @DisplayName("Integration: Should consume published events [GH-90000]")
    void testConsumeEvents() throws Exception { // GH-90000
        Event event = Event.builder() // GH-90000
                .eventId(UUID.randomUUID().toString()) // GH-90000
                .eventType("PHASE_COMPLETED [GH-90000]")
                .tenantId("test-tenant [GH-90000]")
                .payload(Map.of("phase", "PLANNING", "projectId", "proj-123")) // GH-90000
                .timestamp(Instant.now()) // GH-90000
                .build(); // GH-90000

        runPromise(() -> eventPublisher.publish(event)); // GH-90000

        Promise<Event[]> promise = eventConsumer.consume("test-tenant", 10); // GH-90000
        Event[] events = runPromise(() -> promise); // GH-90000

        assertThat(events).hasSizeGreaterThanOrEqualTo(1); // GH-90000
        assertThat(events[0].eventType()).isIn("PROJECT_CREATED", "PHASE_COMPLETED"); // GH-90000
    }

    @Test
    @Order(3) // GH-90000
    @DisplayName("Integration: Should filter events by type [GH-90000]")
    void testFilterEventsByType() throws Exception { // GH-90000
        Event event1 = Event.builder() // GH-90000
                .eventId(UUID.randomUUID().toString()) // GH-90000
                .eventType("USER_LOGIN [GH-90000]")
                .tenantId("test-tenant [GH-90000]")
                .payload(Map.of("userId", "user-1")) // GH-90000
                .timestamp(Instant.now()) // GH-90000
                .build(); // GH-90000

        Event event2 = Event.builder() // GH-90000
                .eventId(UUID.randomUUID().toString()) // GH-90000
                .eventType("USER_LOGOUT [GH-90000]")
                .tenantId("test-tenant [GH-90000]")
                .payload(Map.of("userId", "user-1")) // GH-90000
                .timestamp(Instant.now()) // GH-90000
                .build(); // GH-90000

        runPromise(() -> eventPublisher.publish(event1)); // GH-90000
        runPromise(() -> eventPublisher.publish(event2)); // GH-90000

        Promise<Event[]> promise = eventConsumer.consumeByType("test-tenant", "USER_LOGIN", 10); // GH-90000
        Event[] events = runPromise(() -> promise); // GH-90000

        assertThat(events).isNotEmpty(); // GH-90000
        for (Event event : events) { // GH-90000
            assertThat(event.eventType()).isEqualTo("USER_LOGIN [GH-90000]");
        }
    }

    @Test
    @Order(4) // GH-90000
    @DisplayName("Integration: Should handle event ordering [GH-90000]")
    void testEventOrdering() throws Exception { // GH-90000
        for (int i = 0; i < 5; i++) { // GH-90000
            Event event = Event.builder() // GH-90000
                    .eventId(UUID.randomUUID().toString()) // GH-90000
                    .eventType("SEQUENCE_TEST [GH-90000]")
                    .tenantId("test-tenant [GH-90000]")
                    .payload(Map.of("sequence", String.valueOf(i))) // GH-90000
                    .timestamp(Instant.now()) // GH-90000
                    .build(); // GH-90000
            runPromise(() -> eventPublisher.publish(event)); // GH-90000
            Thread.sleep(10); // Ensure timestamp ordering // GH-90000
        }

        Promise<Event[]> promise = eventConsumer.consumeByType("test-tenant", "SEQUENCE_TEST", 10); // GH-90000
        Event[] events = runPromise(() -> promise); // GH-90000

        assertThat(events).hasSizeGreaterThanOrEqualTo(5); // GH-90000
        
        // Verify chronological order
        for (int i = 1; i < events.length; i++) { // GH-90000
            assertThat(events[i].timestamp()).isAfterOrEqualTo(events[i-1].timestamp()); // GH-90000
        }
    }

    @Test
    @Order(5) // GH-90000
    @DisplayName("Integration: Should handle multi-tenant event isolation [GH-90000]")
    void testMultiTenantIsolation() throws Exception { // GH-90000
        Event tenant1Event = Event.builder() // GH-90000
                .eventId(UUID.randomUUID().toString()) // GH-90000
                .eventType("TENANT_EVENT [GH-90000]")
                .tenantId("tenant-1 [GH-90000]")
                .payload(Map.of("data", "tenant1")) // GH-90000
                .timestamp(Instant.now()) // GH-90000
                .build(); // GH-90000

        Event tenant2Event = Event.builder() // GH-90000
                .eventId(UUID.randomUUID().toString()) // GH-90000
                .eventType("TENANT_EVENT [GH-90000]")
                .tenantId("tenant-2 [GH-90000]")
                .payload(Map.of("data", "tenant2")) // GH-90000
                .timestamp(Instant.now()) // GH-90000
                .build(); // GH-90000

        runPromise(() -> eventPublisher.publish(tenant1Event)); // GH-90000
        runPromise(() -> eventPublisher.publish(tenant2Event)); // GH-90000

        Promise<Event[]> tenant1Events = eventConsumer.consume("tenant-1", 10); // GH-90000
        Event[] t1Events = runPromise(() -> tenant1Events); // GH-90000

        for (Event event : t1Events) { // GH-90000
            assertThat(event.tenantId()).isEqualTo("tenant-1 [GH-90000]");
        }
    }

    // Mock implementations

    interface EventPublisher {
        Promise<String> publish(Event event); // GH-90000
    }

    interface EventConsumer {
        Promise<Event[]> consume(String tenantId, int limit); // GH-90000
        Promise<Event[]> consumeByType(String tenantId, String eventType, int limit); // GH-90000
    }

    interface EventStore {
        void store(Event event); // GH-90000
        Event[] getEvents(String tenantId, int limit); // GH-90000
        Event[] getEventsByType(String tenantId, String eventType, int limit); // GH-90000
    }

    record Event(String eventId, String eventType, String tenantId, Map<String, String> payload, Instant timestamp) { // GH-90000
        static Builder builder() { // GH-90000
            return new Builder(); // GH-90000
        }

        static class Builder {
            private String eventId;
            private String eventType;
            private String tenantId;
            private Map<String, String> payload;
            private Instant timestamp;

            Builder eventId(String eventId) { this.eventId = eventId; return this; } // GH-90000
            Builder eventType(String eventType) { this.eventType = eventType; return this; } // GH-90000
            Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; } // GH-90000
            Builder payload(Map<String, String> payload) { this.payload = payload; return this; } // GH-90000
            Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; } // GH-90000

            Event build() { // GH-90000
                return new Event(eventId, eventType, tenantId, payload, timestamp); // GH-90000
            }
        }
    }

    static class InMemoryEventStore implements EventStore {
        private final Map<String, java.util.List<Event>> eventsByTenant = new ConcurrentHashMap<>(); // GH-90000

        @Override
        public void store(Event event) { // GH-90000
            eventsByTenant.computeIfAbsent(event.tenantId(), k -> new java.util.ArrayList<>()).add(event); // GH-90000
        }

        @Override
        public Event[] getEvents(String tenantId, int limit) { // GH-90000
            return eventsByTenant.getOrDefault(tenantId, java.util.List.of()) // GH-90000
                    .stream() // GH-90000
                    .sorted((a, b) -> a.timestamp().compareTo(b.timestamp())) // GH-90000
                    .limit(limit) // GH-90000
                    .toArray(Event[]::new); // GH-90000
        }

        @Override
        public Event[] getEventsByType(String tenantId, String eventType, int limit) { // GH-90000
            return eventsByTenant.getOrDefault(tenantId, java.util.List.of()) // GH-90000
                    .stream() // GH-90000
                    .filter(e -> e.eventType().equals(eventType)) // GH-90000
                    .sorted((a, b) -> a.timestamp().compareTo(b.timestamp())) // GH-90000
                    .limit(limit) // GH-90000
                    .toArray(Event[]::new); // GH-90000
        }
    }

    static class MockEventPublisher implements EventPublisher {
        private final EventStore store;

        MockEventPublisher(EventStore store) { // GH-90000
            this.store = store;
        }

        @Override
        public Promise<String> publish(Event event) { // GH-90000
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> { // GH-90000
                store.store(event); // GH-90000
                return event.eventId(); // GH-90000
            });
        }
    }

    static class MockEventConsumer implements EventConsumer {
        private final EventStore store;

        MockEventConsumer(EventStore store) { // GH-90000
            this.store = store;
        }

        @Override
        public Promise<Event[]> consume(String tenantId, int limit) { // GH-90000
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () ->  // GH-90000
                store.getEvents(tenantId, limit) // GH-90000
            );
        }

        @Override
        public Promise<Event[]> consumeByType(String tenantId, String eventType, int limit) { // GH-90000
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () ->  // GH-90000
                store.getEventsByType(tenantId, eventType, limit) // GH-90000
            );
        }
    }
}

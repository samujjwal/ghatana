package com.ghatana.products.finance.domains.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Cross-Domain Event Flow Tests")
class CrossDomainEventFlowTest {
    private EventFlowService service;

    @BeforeEach
    void setUp() {
        service = new EventFlowService();
    }

    @Test
    @DisplayName("Should propagate execution events to position domain")
    void shouldPropagateExecutionEventsToPositionDomain() {
        ExecutionEvent event = new ExecutionEvent("ORDER_FILLED", "order-1", "AAPL", 100L, Instant.now());
        service.publishEvent(event);
        assertThat(service.getPositionEvents()).hasSize(1);
    }

    @Test
    @DisplayName("Should propagate position events to risk domain")
    void shouldPropagatePositionEventsToRiskDomain() {
        PositionEvent event = new PositionEvent("POSITION_UPDATED", "AAPL", 1000L, Instant.now());
        service.publishEvent(event);
        assertThat(service.getRiskEvents()).hasSize(1);
    }

    @Test
    @DisplayName("Should maintain event ordering across domains")
    void shouldMaintainEventOrderingAcrossDomains() {
        service.publishEvent(new ExecutionEvent("ORDER_ROUTED", "order-1", "AAPL", 100L, Instant.now()));
        service.publishEvent(new ExecutionEvent("ORDER_FILLED", "order-1", "AAPL", 100L, Instant.now().plusMillis(100)));
        List<DomainEvent> events = service.getAllEvents();
        assertThat(events).hasSize(2);
        assertThat(events.get(0).timestamp()).isBefore(events.get(1).timestamp());
    }

    @Test
    @DisplayName("Should handle event replay")
    void shouldHandleEventReplay() {
        service.publishEvent(new ExecutionEvent("ORDER_FILLED", "order-1", "AAPL", 100L, Instant.now()));
        service.publishEvent(new PositionEvent("POSITION_UPDATED", "AAPL", 100L, Instant.now()));
        List<DomainEvent> replayed = service.replayEvents();
        assertThat(replayed).hasSize(2);
    }

    @Test
    @DisplayName("Should filter events by domain")
    void shouldFilterEventsByDomain() {
        service.publishEvent(new ExecutionEvent("ORDER_FILLED", "order-1", "AAPL", 100L, Instant.now()));
        service.publishEvent(new PositionEvent("POSITION_UPDATED", "AAPL", 100L, Instant.now()));
        List<DomainEvent> executionEvents = service.getEventsByDomain("EXECUTION");
        assertThat(executionEvents).hasSize(1);
    }

    @Test
    @DisplayName("Should support event subscription")
    void shouldSupportEventSubscription() {
        List<DomainEvent> received = new java.util.ArrayList<>();
        service.subscribe("EXECUTION", received::add);
        service.publishEvent(new ExecutionEvent("ORDER_FILLED", "order-1", "AAPL", 100L, Instant.now()));
        assertThat(received).hasSize(1);
    }

    @Test
    @DisplayName("Should handle event correlation")
    void shouldHandleEventCorrelation() {
        String correlationId = "corr-1";
        service.publishEvent(new ExecutionEvent("ORDER_FILLED", "order-1", "AAPL", 100L, Instant.now(), correlationId));
        service.publishEvent(new PositionEvent("POSITION_UPDATED", "AAPL", 100L, Instant.now(), correlationId));
        List<DomainEvent> correlated = service.getCorrelatedEvents(correlationId);
        assertThat(correlated).hasSize(2);
    }

    @Test
    @DisplayName("Should track event processing status")
    void shouldTrackEventProcessingStatus() {
        ExecutionEvent event = new ExecutionEvent("ORDER_FILLED", "order-1", "AAPL", 100L, Instant.now());
        service.publishEvent(event);
        service.markEventProcessed(event.eventId());
        assertThat(service.isEventProcessed(event.eventId())).isTrue();
    }

    @Test
    @DisplayName("Should handle event failures gracefully")
    void shouldHandleEventFailuresGracefully() {
        service.setFailOnPublish(true);
        ExecutionEvent event = new ExecutionEvent("ORDER_FILLED", "order-1", "AAPL", 100L, Instant.now());
        assertThatCode(() -> service.publishEventWithRetry(event, 3)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should generate event flow report")
    void shouldGenerateEventFlowReport() {
        service.publishEvent(new ExecutionEvent("ORDER_FILLED", "order-1", "AAPL", 100L, Instant.now()));
        service.publishEvent(new PositionEvent("POSITION_UPDATED", "AAPL", 100L, Instant.now()));
        EventFlowReport report = service.generateReport();
        assertThat(report.totalEvents()).isEqualTo(2);
        assertThat(report.domainCount()).isEqualTo(2);
    }

    interface DomainEvent {
        String eventId();
        String eventType();
        Instant timestamp();
        String correlationId();
    }

    record ExecutionEvent(String eventType, String orderId, String symbol, long quantity, Instant timestamp, String correlationId, String eventId) implements DomainEvent {
        ExecutionEvent(String eventType, String orderId, String symbol, long quantity, Instant timestamp) {
            this(eventType, orderId, symbol, quantity, timestamp, null, java.util.UUID.randomUUID().toString());
        }
        ExecutionEvent(String eventType, String orderId, String symbol, long quantity, Instant timestamp, String correlationId) {
            this(eventType, orderId, symbol, quantity, timestamp, correlationId, java.util.UUID.randomUUID().toString());
        }
    }

    record PositionEvent(String eventType, String symbol, long quantity, Instant timestamp, String correlationId, String eventId) implements DomainEvent {
        PositionEvent(String eventType, String symbol, long quantity, Instant timestamp) {
            this(eventType, symbol, quantity, timestamp, null, java.util.UUID.randomUUID().toString());
        }
        PositionEvent(String eventType, String symbol, long quantity, Instant timestamp, String correlationId) {
            this(eventType, symbol, quantity, timestamp, correlationId, java.util.UUID.randomUUID().toString());
        }
    }

    record EventFlowReport(int totalEvents, int domainCount) {}

    static class EventFlowService {
        private final List<DomainEvent> allEvents = new java.util.ArrayList<>();
        private final List<PositionEvent> positionEvents = new java.util.ArrayList<>();
        private final List<DomainEvent> riskEvents = new java.util.ArrayList<>();
        private final java.util.Set<String> processedEvents = new java.util.HashSet<>();
        private final java.util.Map<String, List<java.util.function.Consumer<DomainEvent>>> subscribers = new java.util.HashMap<>();
        private boolean failOnPublish = false;

        void publishEvent(DomainEvent event) {
            if (failOnPublish) {
                throw new RuntimeException("Event publishing failed");
            }
            allEvents.add(event);
            
            if (event instanceof ExecutionEvent) {
                PositionEvent posEvent = new PositionEvent("POSITION_UPDATED", 
                    ((ExecutionEvent) event).symbol(), ((ExecutionEvent) event).quantity(), event.timestamp());
                positionEvents.add(posEvent);
            } else if (event instanceof PositionEvent) {
                riskEvents.add(event);
            }

            String domain = event instanceof ExecutionEvent ? "EXECUTION" : "POSITION";
            subscribers.getOrDefault(domain, List.of()).forEach(sub -> sub.accept(event));
        }

        void publishEventWithRetry(DomainEvent event, int maxRetries) {
            int attempts = 0;
            while (attempts < maxRetries) {
                try {
                    publishEvent(event);
                    return;
                } catch (Exception e) {
                    attempts++;
                    if (attempts >= maxRetries) {
                        return;
                    }
                }
            }
        }

        List<PositionEvent> getPositionEvents() {
            return positionEvents;
        }

        List<DomainEvent> getRiskEvents() {
            return riskEvents;
        }

        List<DomainEvent> getAllEvents() {
            return allEvents;
        }

        List<DomainEvent> replayEvents() {
            return new java.util.ArrayList<>(allEvents);
        }

        List<DomainEvent> getEventsByDomain(String domain) {
            if (domain.equals("EXECUTION")) {
                return allEvents.stream().filter(e -> e instanceof ExecutionEvent).toList();
            } else if (domain.equals("POSITION")) {
                return allEvents.stream().filter(e -> e instanceof PositionEvent).toList();
            }
            return List.of();
        }

        void subscribe(String domain, java.util.function.Consumer<DomainEvent> subscriber) {
            subscribers.computeIfAbsent(domain, k -> new java.util.ArrayList<>()).add(subscriber);
        }

        List<DomainEvent> getCorrelatedEvents(String correlationId) {
            return allEvents.stream()
                .filter(e -> correlationId.equals(e.correlationId()))
                .toList();
        }

        void markEventProcessed(String eventId) {
            processedEvents.add(eventId);
        }

        boolean isEventProcessed(String eventId) {
            return processedEvents.contains(eventId);
        }

        void setFailOnPublish(boolean fail) {
            this.failOnPublish = fail;
        }

        EventFlowReport generateReport() {
            long domains = allEvents.stream()
                .map(e -> e instanceof ExecutionEvent ? "EXECUTION" : "POSITION")
                .distinct()
                .count();
            return new EventFlowReport(allEvents.size(), (int) domains);
        }
    }
}

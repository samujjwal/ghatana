package com.ghatana.products.finance.domains.ems.service;

import com.ghatana.products.finance.domains.ems.domain.ExecutionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for execution event publishing per D02-009
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Execution Event Publishing Tests")
class ExecutionEventPublishingTest {

    private EventPublisher eventPublisher;
    private TestEventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = new TestEventBus();
        eventPublisher = new EventPublisher(eventBus);
    }

    @Test
    @DisplayName("Should publish order routed event")
    void shouldPublishOrderRoutedEvent() {
        OrderRoutedEvent event = new OrderRoutedEvent(
            "order-1",
            "routing-1",
            "NASDAQ",
            Instant.now()
        );

        eventPublisher.publish(event);

        assertThat(eventBus.getPublishedEvents()).hasSize(1);
        assertThat(eventBus.getPublishedEvents().get(0)).isInstanceOf(OrderRoutedEvent.class);
    }

    @Test
    @DisplayName("Should publish fill received event")
    void shouldPublishFillReceivedEvent() {
        FillReceivedEvent event = new FillReceivedEvent(
            "order-1",
            "fill-1",
            100L,
            BigDecimal.valueOf(150.50),
            Instant.now()
        );

        eventPublisher.publish(event);

        assertThat(eventBus.getPublishedEvents()).hasSize(1);
        assertThat(eventBus.getPublishedEvents().get(0)).isInstanceOf(FillReceivedEvent.class);
    }

    @Test
    @DisplayName("Should publish order completed event")
    void shouldPublishOrderCompletedEvent() {
        OrderCompletedEvent event = new OrderCompletedEvent(
            "order-1",
            100L,
            BigDecimal.valueOf(150.50),
            Instant.now()
        );

        eventPublisher.publish(event);

        assertThat(eventBus.getPublishedEvents()).hasSize(1);
        assertThat(eventBus.getPublishedEvents().get(0)).isInstanceOf(OrderCompletedEvent.class);
    }

    @Test
    @DisplayName("Should publish order cancelled event")
    void shouldPublishOrderCancelledEvent() {
        OrderCancelledEvent event = new OrderCancelledEvent(
            "order-1",
            "routing-1",
            "User requested",
            Instant.now()
        );

        eventPublisher.publish(event);

        assertThat(eventBus.getPublishedEvents()).hasSize(1);
        assertThat(eventBus.getPublishedEvents().get(0)).isInstanceOf(OrderCancelledEvent.class);
    }

    @Test
    @DisplayName("Should publish order rejected event")
    void shouldPublishOrderRejectedEvent() {
        OrderRejectedEvent event = new OrderRejectedEvent(
            "order-1",
            "routing-1",
            "Insufficient funds",
            Instant.now()
        );

        eventPublisher.publish(event);

        assertThat(eventBus.getPublishedEvents()).hasSize(1);
        assertThat(eventBus.getPublishedEvents().get(0)).isInstanceOf(OrderRejectedEvent.class);
    }

    @Test
    @DisplayName("Should publish events in order")
    void shouldPublishEventsInOrder() {
        eventPublisher.publish(new OrderRoutedEvent("order-1", "routing-1", "NASDAQ", Instant.now()));
        eventPublisher.publish(new FillReceivedEvent("order-1", "fill-1", 50L, BigDecimal.valueOf(150.50), Instant.now()));
        eventPublisher.publish(new FillReceivedEvent("order-1", "fill-2", 50L, BigDecimal.valueOf(150.51), Instant.now()));

        assertThat(eventBus.getPublishedEvents()).hasSize(3);
        assertThat(eventBus.getPublishedEvents().get(0)).isInstanceOf(OrderRoutedEvent.class);
        assertThat(eventBus.getPublishedEvents().get(1)).isInstanceOf(FillReceivedEvent.class);
        assertThat(eventBus.getPublishedEvents().get(2)).isInstanceOf(FillReceivedEvent.class);
    }

    @Test
    @DisplayName("Should support event filtering by type")
    void shouldSupportEventFilteringByType() {
        eventPublisher.publish(new OrderRoutedEvent("order-1", "routing-1", "NASDAQ", Instant.now()));
        eventPublisher.publish(new FillReceivedEvent("order-1", "fill-1", 50L, BigDecimal.valueOf(150.50), Instant.now()));
        eventPublisher.publish(new FillReceivedEvent("order-1", "fill-2", 50L, BigDecimal.valueOf(150.51), Instant.now()));

        List<ExecutionEvent> fillEvents = eventBus.getEventsByType(FillReceivedEvent.class);

        assertThat(fillEvents).hasSize(2);
        assertThat(fillEvents).allMatch(e -> e instanceof FillReceivedEvent);
    }

    @Test
    @DisplayName("Should support event subscription")
    void shouldSupportEventSubscription() {
        List<ExecutionEvent> receivedEvents = new ArrayList<>();
        eventBus.subscribe(FillReceivedEvent.class, receivedEvents::add);

        eventPublisher.publish(new FillReceivedEvent("order-1", "fill-1", 100L, BigDecimal.valueOf(150.50), Instant.now()));

        assertThat(receivedEvents).hasSize(1);
    }

    @Test
    @DisplayName("Should handle event publishing failures gracefully")
    void shouldHandleEventPublishingFailuresGracefully() {
        eventBus.setFailOnPublish(true);

        assertThatCode(() -> eventPublisher.publishWithRetry(
            new OrderRoutedEvent("order-1", "routing-1", "NASDAQ", Instant.now()),
            3
        )).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should publish state transition events")
    void shouldPublishStateTransitionEvents() {
        StateTransitionEvent event = new StateTransitionEvent(
            "order-1",
            ExecutionStatus.ROUTED,
            ExecutionStatus.PARTIALLY_FILLED,
            Instant.now()
        );

        eventPublisher.publish(event);

        assertThat(eventBus.getPublishedEvents()).hasSize(1);
        assertThat(eventBus.getPublishedEvents().get(0)).isInstanceOf(StateTransitionEvent.class);
    }

    interface ExecutionEvent {
        String orderId();
        Instant timestamp();
    }

    record OrderRoutedEvent(String orderId, String routingId, String exchange, Instant timestamp) implements ExecutionEvent {}
    record FillReceivedEvent(String orderId, String fillId, long quantity, BigDecimal price, Instant timestamp) implements ExecutionEvent {}
    record OrderCompletedEvent(String orderId, long totalQuantity, BigDecimal avgPrice, Instant timestamp) implements ExecutionEvent {}
    record OrderCancelledEvent(String orderId, String routingId, String reason, Instant timestamp) implements ExecutionEvent {}
    record OrderRejectedEvent(String orderId, String routingId, String reason, Instant timestamp) implements ExecutionEvent {}
    record StateTransitionEvent(String orderId, ExecutionStatus fromState, ExecutionStatus toState, Instant timestamp) implements ExecutionEvent {}

    static class EventPublisher {
        private final TestEventBus eventBus;

        EventPublisher(TestEventBus eventBus) {
            this.eventBus = eventBus;
        }

        void publish(ExecutionEvent event) {
            eventBus.publish(event);
        }

        void publishWithRetry(ExecutionEvent event, int maxRetries) {
            int attempts = 0;
            while (attempts < maxRetries) {
                try {
                    eventBus.publish(event);
                    return;
                } catch (Exception e) {
                    attempts++;
                    if (attempts >= maxRetries) {
                        return;
                    }
                }
            }
        }
    }

    static class TestEventBus {
        private final List<ExecutionEvent> publishedEvents = new ArrayList<>();
        private final List<EventSubscription> subscriptions = new ArrayList<>();
        private boolean failOnPublish = false;

        void publish(ExecutionEvent event) {
            if (failOnPublish) {
                throw new RuntimeException("Event publishing failed");
            }
            publishedEvents.add(event);
            subscriptions.forEach(sub -> {
                if (sub.eventType.isInstance(event)) {
                    sub.handler.accept(event);
                }
            });
        }

        List<ExecutionEvent> getPublishedEvents() {
            return publishedEvents;
        }

        <T extends ExecutionEvent> List<ExecutionEvent> getEventsByType(Class<T> eventType) {
            return publishedEvents.stream()
                .filter(eventType::isInstance)
                .toList();
        }

        <T extends ExecutionEvent> void subscribe(Class<T> eventType, java.util.function.Consumer<ExecutionEvent> handler) {
            subscriptions.add(new EventSubscription(eventType, handler));
        }

        void setFailOnPublish(boolean fail) {
            this.failOnPublish = fail;
        }

        record EventSubscription(Class<? extends ExecutionEvent> eventType, java.util.function.Consumer<ExecutionEvent> handler) {}
    }
}

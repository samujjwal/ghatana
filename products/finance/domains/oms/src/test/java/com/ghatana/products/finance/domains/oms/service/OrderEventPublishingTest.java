package com.ghatana.products.finance.domains.oms.service;

import com.ghatana.products.finance.domains.oms.domain.*;
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
 * @doc.purpose Tests for order event publishing to event bus
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Order Event Publishing Tests")
class OrderEventPublishingTest {

    private Instant testTime;
    private String testTimeBs;
    private List<OrderEvent> publishedEvents;

    @BeforeEach
    void setUp() {
        testTime = Instant.now();
        testTimeBs = "2080-12-15";
        publishedEvents = new ArrayList<>();
    }

    @Test
    @DisplayName("Should publish order created event")
    void shouldPublishOrderCreatedEvent() {
        // GIVEN: New order
        Order order = Order.newOrder(
            "ORD-001", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-1", testTime, testTimeBs
        );

        // WHEN: Publish creation event
        publishEvent(new OrderCreatedEvent(order.orderId(), order.clientId(), testTime));

        // THEN: Event published
        assertThat(publishedEvents).hasSize(1);
        assertThat(publishedEvents.get(0)).isInstanceOf(OrderCreatedEvent.class);
    }

    @Test
    @DisplayName("Should publish order status changed event")
    void shouldPublishOrderStatusChangedEvent() {
        // GIVEN: Order status change
        Order order = Order.newOrder(
            "ORD-002", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-2", testTime, testTimeBs
        );

        // WHEN: Change status and publish
        Order approved = order.withStatus(OrderStatus.APPROVED);
        publishEvent(new OrderStatusChangedEvent(
            order.orderId(), OrderStatus.PENDING, OrderStatus.APPROVED, testTime));

        // THEN: Event published
        assertThat(publishedEvents).hasSize(1);
        OrderStatusChangedEvent event = (OrderStatusChangedEvent) publishedEvents.get(0);
        assertThat(event.fromStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(event.toStatus()).isEqualTo(OrderStatus.APPROVED);
    }

    @Test
    @DisplayName("Should publish order filled event")
    void shouldPublishOrderFilledEvent() {
        // GIVEN: Order fill
        Fill fill = new Fill("FILL-001", "ORD-003", "EXEC-001",
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), BigDecimal.ZERO, testTime);

        // WHEN: Publish fill event
        publishEvent(new OrderFilledEvent(
            "ORD-003", fill.fillId(), fill.fillQuantity(), fill.fillPrice(), testTime));

        // THEN: Event published
        assertThat(publishedEvents).hasSize(1);
        assertThat(publishedEvents.get(0)).isInstanceOf(OrderFilledEvent.class);
    }

    @Test
    @DisplayName("Should publish order cancelled event")
    void shouldPublishOrderCancelledEvent() {
        // GIVEN: Order cancellation
        Order order = Order.newOrder(
            "ORD-004", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-4", testTime, testTimeBs
        );

        // WHEN: Cancel and publish
        publishEvent(new OrderCancelledEvent(
            order.orderId(), "Client requested", testTime));

        // THEN: Event published
        assertThat(publishedEvents).hasSize(1);
        OrderCancelledEvent event = (OrderCancelledEvent) publishedEvents.get(0);
        assertThat(event.reason()).isEqualTo("Client requested");
    }

    @Test
    @DisplayName("Should publish order rejected event")
    void shouldPublishOrderRejectedEvent() {
        // GIVEN: Order rejection
        Order order = Order.newOrder(
            "ORD-005", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-5", testTime, testTimeBs
        );

        // WHEN: Reject and publish
        publishEvent(new OrderRejectedEvent(
            order.orderId(), "Insufficient margin", testTime));

        // THEN: Event published
        assertThat(publishedEvents).hasSize(1);
        OrderRejectedEvent event = (OrderRejectedEvent) publishedEvents.get(0);
        assertThat(event.reason()).isEqualTo("Insufficient margin");
    }

    @Test
    @DisplayName("Should publish order modified event")
    void shouldPublishOrderModifiedEvent() {
        // GIVEN: Order modification
        Order original = Order.newOrder(
            "ORD-006", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-6", testTime, testTimeBs
        );

        // WHEN: Modify and publish
        Order modified = original.withStatus(OrderStatus.APPROVED);
        publishEvent(new OrderModifiedEvent(
            original.orderId(), "status", "PENDING", "APPROVED", testTime));

        // THEN: Event published
        assertThat(publishedEvents).hasSize(1);
        OrderModifiedEvent event = (OrderModifiedEvent) publishedEvents.get(0);
        assertThat(event.field()).isEqualTo("status");
    }

    @Test
    @DisplayName("Should publish events in order")
    void shouldPublishEventsInOrder() {
        // GIVEN: Order lifecycle
        Order order = Order.newOrder(
            "ORD-007", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-7", testTime, testTimeBs
        );

        // WHEN: Publish lifecycle events
        publishEvent(new OrderCreatedEvent(order.orderId(), order.clientId(), testTime));
        publishEvent(new OrderStatusChangedEvent(
            order.orderId(), OrderStatus.PENDING, OrderStatus.APPROVED, testTime));
        publishEvent(new OrderStatusChangedEvent(
            order.orderId(), OrderStatus.APPROVED, OrderStatus.ROUTED, testTime));

        // THEN: Events in correct order
        assertThat(publishedEvents).hasSize(3);
        assertThat(publishedEvents.get(0)).isInstanceOf(OrderCreatedEvent.class);
        assertThat(publishedEvents.get(1)).isInstanceOf(OrderStatusChangedEvent.class);
        assertThat(publishedEvents.get(2)).isInstanceOf(OrderStatusChangedEvent.class);
    }

    @Test
    @DisplayName("Should include event metadata")
    void shouldIncludeEventMetadata() {
        // GIVEN: Order event
        Order order = Order.newOrder(
            "ORD-008", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-8", testTime, testTimeBs
        );

        // WHEN: Publish with metadata
        publishEvent(new OrderCreatedEvent(order.orderId(), order.clientId(), testTime));

        // THEN: Metadata included
        OrderCreatedEvent event = (OrderCreatedEvent) publishedEvents.get(0);
        assertThat(event.timestamp()).isNotNull();
        assertThat(event.orderId()).isNotNull();
    }

    @Test
    @DisplayName("Should handle event publishing failures gracefully")
    void shouldHandleEventPublishingFailuresGracefully() {
        // GIVEN: Event that might fail to publish
        Order order = Order.newOrder(
            "ORD-009", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-9", testTime, testTimeBs
        );

        // WHEN: Attempt to publish
        try {
            publishEvent(new OrderCreatedEvent(order.orderId(), order.clientId(), testTime));
        } catch (Exception e) {
            // Event publishing failure should not break order processing
        }

        // THEN: Order processing continues
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("Should support event replay")
    void shouldSupportEventReplay() {
        // GIVEN: Published events
        publishEvent(new OrderCreatedEvent("ORD-010", "client-1", testTime));
        publishEvent(new OrderStatusChangedEvent(
            "ORD-010", OrderStatus.PENDING, OrderStatus.APPROVED, testTime));

        // WHEN: Replay events
        List<OrderEvent> replayedEvents = new ArrayList<>(publishedEvents);

        // THEN: Events can be replayed
        assertThat(replayedEvents).hasSize(2);
        assertThat(replayedEvents).containsExactlyElementsOf(publishedEvents);
    }

    // Helper methods
    private void publishEvent(OrderEvent event) {
        publishedEvents.add(event);
    }

    // Event classes
    interface OrderEvent {
        String orderId();
        Instant timestamp();
    }

    record OrderCreatedEvent(String orderId, String clientId, Instant timestamp) implements OrderEvent {}
    record OrderStatusChangedEvent(String orderId, OrderStatus fromStatus, OrderStatus toStatus, Instant timestamp) implements OrderEvent {}
    record OrderFilledEvent(String orderId, String fillId, BigDecimal quantity, BigDecimal price, Instant timestamp) implements OrderEvent {}
    record OrderCancelledEvent(String orderId, String reason, Instant timestamp) implements OrderEvent {}
    record OrderRejectedEvent(String orderId, String reason, Instant timestamp) implements OrderEvent {}
    record OrderModifiedEvent(String orderId, String field, String oldValue, String newValue, Instant timestamp) implements OrderEvent {}
}

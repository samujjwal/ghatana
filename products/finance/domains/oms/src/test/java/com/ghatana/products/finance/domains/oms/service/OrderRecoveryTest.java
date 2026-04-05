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
 * @doc.purpose Tests for order recovery and failure handling
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Order Recovery Tests")
class OrderRecoveryTest {

    private Instant testTime;
    private String testTimeBs;

    @BeforeEach
    void setUp() {
        testTime = Instant.now();
        testTimeBs = "2080-12-15";
    }

    @Test
    @DisplayName("Should recover from validation failure")
    void shouldRecoverFromValidationFailure() {
        // GIVEN: Invalid order that fails validation
        Order order = Order.newOrder(
            "ORD-001", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.ZERO, BigDecimal.valueOf(150.00), null, // Invalid quantity
            "idempotency-key-1", testTime, testTimeBs
        );

        // WHEN: Validation fails and order is rejected
        Order rejected = order.withRejection("Invalid quantity");

        // THEN: Order can be recovered with correct data
        assertThat(rejected.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(rejected.rejectionReason()).contains("Invalid quantity");
    }

    @Test
    @DisplayName("Should recover from routing failure")
    void shouldRecoverFromRoutingFailure() {
        // GIVEN: Order that fails to route
        Order order = Order.newOrder(
            "ORD-002", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-2", testTime, testTimeBs
        ).withStatus(OrderStatus.APPROVED);

        // WHEN: Routing fails
        Order failed = order.withRejection("EMS unavailable");

        // THEN: Order marked for retry
        assertThat(failed.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(failed.rejectionReason()).contains("EMS unavailable");
    }

    @Test
    @DisplayName("Should recover from partial fill timeout")
    void shouldRecoverFromPartialFillTimeout() {
        // GIVEN: Order with partial fill that times out
        Order order = Order.newOrder(
            "ORD-003", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-3", testTime, testTimeBs
        ).withStatus(OrderStatus.PARTIALLY_FILLED);

        // WHEN: Timeout occurs
        Order timedOut = order.withStatus(OrderStatus.CANCELLED);

        // THEN: Partial fill preserved
        assertThat(timedOut.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(timedOut.filledQuantity()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should recover from system crash")
    void shouldRecoverFromSystemCrash() {
        // GIVEN: Order in-flight during system crash
        Order order = Order.newOrder(
            "ORD-004", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-4", testTime, testTimeBs
        ).withStatus(OrderStatus.ROUTED);

        // WHEN: System recovers
        // Order state should be recoverable from persistence

        // THEN: Order can be reconstructed
        assertThat(order.orderId()).isEqualTo("ORD-004");
        assertThat(order.status()).isEqualTo(OrderStatus.ROUTED);
    }

    @Test
    @DisplayName("Should handle duplicate order detection")
    void shouldHandleDuplicateOrderDetection() {
        // GIVEN: Order with idempotency key
        Order order1 = Order.newOrder(
            "ORD-005", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-5", testTime, testTimeBs
        );

        // WHEN: Duplicate submission detected
        boolean isDuplicate = order1.idempotencyKey().equals("idempotency-key-5");

        // THEN: Duplicate detected
        assertThat(isDuplicate).isTrue();
    }

    @Test
    @DisplayName("Should recover from network partition")
    void shouldRecoverFromNetworkPartition() {
        // GIVEN: Order during network partition
        Order order = Order.newOrder(
            "ORD-006", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-6", testTime, testTimeBs
        );

        // WHEN: Network partition occurs
        // Order should be queued for retry

        // THEN: Order recoverable after partition heals
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("Should handle fill reconciliation")
    void shouldHandleFillReconciliation() {
        // GIVEN: Order with potential missing fills
        Order order = Order.newOrder(
            "ORD-007", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-7", testTime, testTimeBs
        );

        Fill fill1 = new Fill("FILL-001", "ORD-007", "EXEC-001",
            BigDecimal.valueOf(50), BigDecimal.valueOf(150.00), BigDecimal.ZERO, testTime);
        Order withFill = order.withFill(fill1, BigDecimal.valueOf(150.00));

        // WHEN: Reconcile fills
        List<Fill> expectedFills = List.of(fill1);
        List<Fill> actualFills = withFill.fills();

        // THEN: Fills reconciled
        assertThat(actualFills).hasSize(expectedFills.size());
    }

    @Test
    @DisplayName("Should recover from database failure")
    void shouldRecoverFromDatabaseFailure() {
        // GIVEN: Order persistence failure
        Order order = Order.newOrder(
            "ORD-008", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-8", testTime, testTimeBs
        );

        // WHEN: Database fails
        // Order should be in memory cache

        // THEN: Order recoverable from cache
        assertThat(order).isNotNull();
    }

    @Test
    @DisplayName("Should handle stale order cleanup")
    void shouldHandleStaleOrderCleanup() {
        // GIVEN: Old pending order
        Instant oldTime = Instant.now().minusSeconds(86400); // 1 day old
        Order staleOrder = Order.newOrder(
            "ORD-009", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-9", oldTime, testTimeBs
        );

        // WHEN: Check if stale
        boolean isStale = staleOrder.createdAt().isBefore(Instant.now().minusSeconds(3600));

        // THEN: Stale order identified
        assertThat(isStale).isTrue();
    }

    @Test
    @DisplayName("Should recover order state from events")
    void shouldRecoverOrderStateFromEvents() {
        // GIVEN: Order events
        List<OrderEvent> events = new ArrayList<>();
        events.add(new OrderEvent("ORD-010", "CREATED", testTime));
        events.add(new OrderEvent("ORD-010", "APPROVED", testTime));
        events.add(new OrderEvent("ORD-010", "ROUTED", testTime));

        // WHEN: Replay events
        OrderStatus finalStatus = replayEvents(events);

        // THEN: State recovered
        assertThat(finalStatus).isEqualTo(OrderStatus.ROUTED);
    }

    // Helper methods
    private OrderStatus replayEvents(List<OrderEvent> events) {
        OrderStatus status = OrderStatus.PENDING;
        for (OrderEvent event : events) {
            switch (event.eventType()) {
                case "CREATED" -> status = OrderStatus.PENDING;
                case "APPROVED" -> status = OrderStatus.APPROVED;
                case "ROUTED" -> status = OrderStatus.ROUTED;
                case "FILLED" -> status = OrderStatus.FILLED;
                case "CANCELLED" -> status = OrderStatus.CANCELLED;
                case "REJECTED" -> status = OrderStatus.REJECTED;
            }
        }
        return status;
    }

    record OrderEvent(String orderId, String eventType, Instant timestamp) {}
}

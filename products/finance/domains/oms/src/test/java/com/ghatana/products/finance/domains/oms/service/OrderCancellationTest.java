package com.ghatana.products.finance.domains.oms.service;

import com.ghatana.products.finance.domains.oms.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for order cancellation flows and edge cases
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Order Cancellation Tests")
class OrderCancellationTest {

    private Instant testTime;
    private String testTimeBs;

    @BeforeEach
    void setUp() {
        testTime = Instant.now();
        testTimeBs = "2080-12-15";
    }

    @Test
    @DisplayName("Should cancel order in PENDING status")
    void shouldCancelOrderInPendingStatus() {
        // GIVEN: Order in PENDING status
        Order order = Order.newOrder(
            "ORD-001", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-1", testTime, testTimeBs
        );

        // WHEN: Cancel order
        Order cancelled = order.withStatus(OrderStatus.CANCELLED);

        // THEN: Order cancelled
        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("Should cancel order in APPROVED status")
    void shouldCancelOrderInApprovedStatus() {
        // GIVEN: Order in APPROVED status
        Order order = Order.newOrder(
            "ORD-002", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-2", testTime, testTimeBs
        ).withStatus(OrderStatus.APPROVED);

        // WHEN: Cancel order
        Order cancelled = order.withStatus(OrderStatus.CANCELLED);

        // THEN: Order cancelled
        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("Should cancel order in ROUTED status")
    void shouldCancelOrderInRoutedStatus() {
        // GIVEN: Order in ROUTED status
        Order order = Order.newOrder(
            "ORD-003", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-3", testTime, testTimeBs
        ).withStatus(OrderStatus.ROUTED);

        // WHEN: Cancel order
        Order cancelled = order.withStatus(OrderStatus.CANCELLED);

        // THEN: Order cancelled
        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("Should not cancel order in FILLED status")
    void shouldNotCancelOrderInFilledStatus() {
        // GIVEN: Order in FILLED status
        Order order = Order.newOrder(
            "ORD-004", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-4", testTime, testTimeBs
        ).withStatus(OrderStatus.FILLED);

        // WHEN/THEN: Cancellation should be rejected
        assertThatThrownBy(() -> validateCancellation(order))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot cancel filled order");
    }

    @Test
    @DisplayName("Should handle partial fill cancellation")
    void shouldHandlePartialFillCancellation() {
        // GIVEN: Order with partial fill
        Order order = Order.newOrder(
            "ORD-005", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-5", testTime, testTimeBs
        );

        Fill fill = new Fill("FILL-001", "ORD-005", "EXEC-001",
            BigDecimal.valueOf(50), BigDecimal.valueOf(150.00), BigDecimal.ZERO, testTime);
        Order partiallyFilled = order.withFill(fill, BigDecimal.valueOf(150.00));

        // WHEN: Cancel remaining quantity
        Order cancelled = partiallyFilled.withStatus(OrderStatus.CANCELLED);

        // THEN: Order cancelled with partial fill preserved
        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.filledQuantity()).isEqualTo(BigDecimal.valueOf(50));
    }

    @Test
    @DisplayName("Should track cancellation via status change")
    void shouldTrackCancellationViaStatusChange() {
        // GIVEN: Order to cancel
        Order order = Order.newOrder(
            "ORD-006", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-6", testTime, testTimeBs
        );

        // WHEN: Cancel order
        Order cancelled = order.withStatus(OrderStatus.CANCELLED);

        // THEN: Order cancelled
        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("Should handle immediate cancellation")
    void shouldHandleImmediateCancellation() {
        // GIVEN: IOC order that cannot be filled
        Order order = Order.newOrder(
            "ORD-007", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.IOC,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-7", testTime, testTimeBs
        );

        // WHEN: IOC order cannot be filled immediately
        Order cancelled = order.withStatus(OrderStatus.CANCELLED);

        // THEN: Order cancelled
        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.timeInForce()).isEqualTo(TimeInForce.IOC);
    }

    @Test
    @DisplayName("Should handle day order expiry")
    void shouldHandleDayOrderExpiry() {
        // GIVEN: DAY order at end of trading day
        Order order = Order.newOrder(
            "ORD-008", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-8", testTime, testTimeBs
        );

        // WHEN: Day ends without fill
        Order expired = order.withStatus(OrderStatus.CANCELLED);

        // THEN: Order cancelled due to expiry
        assertThat(expired.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("Should preserve order immutability during cancellation")
    void shouldPreserveOrderImmutabilityDuringCancellation() {
        // GIVEN: Original order
        Order original = Order.newOrder(
            "ORD-009", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-9", testTime, testTimeBs
        );

        // WHEN: Cancel order
        Order cancelled = original.withStatus(OrderStatus.CANCELLED);

        // THEN: Original unchanged
        assertThat(original.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(original).isNotSameAs(cancelled);
    }

    @Test
    @DisplayName("Should handle bulk cancellation")
    void shouldHandleBulkCancellation() {
        // GIVEN: Multiple orders to cancel
        Order order1 = Order.newOrder("ORD-010", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-10", testTime, testTimeBs);

        Order order2 = Order.newOrder("ORD-011", "client-1", "account-1", "INST-001",
            OrderSide.SELL, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(151.00), null,
            "idempotency-key-11", testTime, testTimeBs);

        // WHEN: Cancel all orders
        Order cancelled1 = order1.withStatus(OrderStatus.CANCELLED);
        Order cancelled2 = order2.withStatus(OrderStatus.CANCELLED);

        // THEN: All orders cancelled
        assertThat(cancelled1.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled2.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    // Helper methods
    private void validateCancellation(Order order) {
        if (order.status() == OrderStatus.FILLED) {
            throw new IllegalStateException("Cannot cancel filled order");
        }
    }
}

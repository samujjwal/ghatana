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
 * @doc.purpose Tests for complete order lifecycle from creation to completion
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Order Lifecycle Tests")
class OrderLifecycleTest {

    private Instant testTime;
    private String testTimeBs;

    @BeforeEach
    void setUp() {
        testTime = Instant.now();
        testTimeBs = "2080-12-15"; // Bikram Sambat date
    }

    @Test
    @DisplayName("Should create new order in PENDING status")
    void shouldCreateNewOrderInPendingStatus() {
        // WHEN: Create new order
        Order order = Order.newOrder(
            "ORD-001",
            "client-1",
            "account-1",
            "INST-001",
            OrderSide.BUY,
            OrderType.LIMIT,
            TimeInForce.DAY,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(150.00),
            null,
            "idempotency-key-1",
            testTime,
            testTimeBs
        );

        // THEN: Order is in PENDING status
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.orderId()).isEqualTo("ORD-001");
        assertThat(order.filledQuantity()).isEqualTo(BigDecimal.ZERO);
        assertThat(order.remainingQuantity()).isEqualTo(BigDecimal.valueOf(100));
        assertThat(order.fills()).isEmpty();
    }

    @Test
    @DisplayName("Should transition from PENDING to APPROVED")
    void shouldTransitionFromPendingToApproved() {
        // GIVEN: Order in PENDING status
        Order order = Order.newOrder(
            "ORD-002", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-2", testTime, testTimeBs
        );

        // WHEN: Transition to APPROVED
        Order approvedOrder = order.withStatus(OrderStatus.APPROVED);

        // THEN: Status updated
        assertThat(approvedOrder.status()).isEqualTo(OrderStatus.APPROVED);
        assertThat(approvedOrder.orderId()).isEqualTo(order.orderId());
    }

    @Test
    @DisplayName("Should transition from APPROVED to ROUTED")
    void shouldTransitionFromApprovedToRouted() {
        // GIVEN: Order in APPROVED status
        Order order = Order.newOrder(
            "ORD-003", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-3", testTime, testTimeBs
        ).withStatus(OrderStatus.APPROVED);

        // WHEN: Transition to ROUTED
        Order routedOrder = order.withStatus(OrderStatus.ROUTED);

        // THEN: Status updated
        assertThat(routedOrder.status()).isEqualTo(OrderStatus.ROUTED);
    }

    @Test
    @DisplayName("Should handle order enrichment")
    void shouldHandleOrderEnrichment() {
        // GIVEN: Basic order
        Order order = Order.newOrder(
            "ORD-004", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-4", testTime, testTimeBs
        );

        // WHEN: Enrich with instrument details
        Order enrichedOrder = order.withEnrichment(
            "AAPL",
            "NASDAQ",
            "USD",
            BigDecimal.valueOf(15000.00),
            BigDecimal.valueOf(149.50)
        );

        // THEN: Enrichment applied
        assertThat(enrichedOrder.instrumentSymbol()).isEqualTo("AAPL");
        assertThat(enrichedOrder.exchange()).isEqualTo("NASDAQ");
        assertThat(enrichedOrder.currency()).isEqualTo("USD");
        assertThat(enrichedOrder.orderValue()).isEqualTo(BigDecimal.valueOf(15000.00));
        assertThat(enrichedOrder.arrivalPrice()).isEqualTo(BigDecimal.valueOf(149.50));
    }

    @Test
    @DisplayName("Should handle order rejection")
    void shouldHandleOrderRejection() {
        // GIVEN: Order in PENDING status
        Order order = Order.newOrder(
            "ORD-005", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-5", testTime, testTimeBs
        );

        // WHEN: Reject order
        String rejectionReason = "Insufficient margin";
        Order rejectedOrder = order.withRejection(rejectionReason);

        // THEN: Order rejected with reason
        assertThat(rejectedOrder.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(rejectedOrder.rejectionReason()).isEqualTo(rejectionReason);
    }

    @Test
    @DisplayName("Should handle partial fill")
    void shouldHandlePartialFill() {
        // GIVEN: Order in ROUTED status
        Order order = Order.newOrder(
            "ORD-006", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-6", testTime, testTimeBs
        ).withStatus(OrderStatus.ROUTED);

        // WHEN: Partial fill of 40 shares
        Fill fill = new Fill(
            "FILL-001",
            "ORD-006",
            "EXEC-001",
            BigDecimal.valueOf(40),
            BigDecimal.valueOf(150.00),
            BigDecimal.ZERO,
            Instant.now()
        );
        Order partiallyFilledOrder = order.withFill(fill, BigDecimal.valueOf(150.00));

        // THEN: Fill recorded and quantities updated
        assertThat(partiallyFilledOrder.fills()).hasSize(1);
        assertThat(partiallyFilledOrder.filledQuantity()).isEqualTo(BigDecimal.valueOf(40));
        assertThat(partiallyFilledOrder.remainingQuantity()).isEqualTo(BigDecimal.valueOf(60));
        assertThat(partiallyFilledOrder.status()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
    }

    @Test
    @DisplayName("Should handle complete fill")
    void shouldHandleCompleteFill() {
        // GIVEN: Order with partial fill
        Order order = Order.newOrder(
            "ORD-007", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-7", testTime, testTimeBs
        ).withStatus(OrderStatus.ROUTED);
        Fill fill1 = new Fill("FILL-001", "ORD-007", "EXEC-001",
            BigDecimal.valueOf(60), BigDecimal.valueOf(150.00), BigDecimal.ZERO, Instant.now());
        Order partialOrder = order.withFill(fill1, BigDecimal.valueOf(150.00));

        // WHEN: Complete remaining fill
        Fill fill2 = new Fill("FILL-002", "ORD-007", "EXEC-002",
            BigDecimal.valueOf(40), BigDecimal.valueOf(150.50), BigDecimal.ZERO, Instant.now());
        Order filledOrder = partialOrder.withFill(fill2, BigDecimal.valueOf(150.20));

        // THEN: Order completely filled
        assertThat(filledOrder.fills()).hasSize(2);
        assertThat(filledOrder.filledQuantity()).isEqualTo(BigDecimal.valueOf(100));
        assertThat(filledOrder.remainingQuantity()).isEqualTo(BigDecimal.ZERO);
        assertThat(filledOrder.status()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    @DisplayName("Should calculate average fill price correctly")
    void shouldCalculateAverageFillPriceCorrectly() {
        // GIVEN: Order with multiple fills at different prices
        Order order = Order.newOrder(
            "ORD-008", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-8", testTime, testTimeBs
        ).withStatus(OrderStatus.ROUTED);

        // WHEN: Add fills at different prices
        Fill fill1 = new Fill("FILL-001", "ORD-008", "EXEC-001",
            BigDecimal.valueOf(50), BigDecimal.valueOf(150.00), BigDecimal.ZERO, Instant.now());
        Order order1 = order.withFill(fill1, BigDecimal.valueOf(150.00));

        Fill fill2 = new Fill("FILL-002", "ORD-008", "EXEC-002",
            BigDecimal.valueOf(50), BigDecimal.valueOf(151.00), BigDecimal.ZERO, Instant.now());
        Order order2 = order1.withFill(fill2, BigDecimal.valueOf(150.50));

        // THEN: Average price calculated correctly
        assertThat(order2.avgFillPrice()).isEqualTo(BigDecimal.valueOf(150.50));
    }

    @Test
    @DisplayName("Should handle order cancellation")
    void shouldHandleOrderCancellation() {
        // GIVEN: Order in ROUTED status
        Order order = Order.newOrder(
            "ORD-009", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-9", testTime, testTimeBs
        ).withStatus(OrderStatus.ROUTED);

        // WHEN: Cancel order
        Order cancelledOrder = order.withStatus(OrderStatus.CANCELLED);

        // THEN: Order cancelled
        assertThat(cancelledOrder.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("Should preserve order immutability")
    void shouldPreserveOrderImmutability() {
        // GIVEN: Original order
        Order original = Order.newOrder(
            "ORD-010", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-10", testTime, testTimeBs
        );

        // WHEN: Modify order
        Order modified = original.withStatus(OrderStatus.APPROVED);

        // THEN: Original unchanged
        assertThat(original.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(modified.status()).isEqualTo(OrderStatus.APPROVED);
        assertThat(original).isNotSameAs(modified);
    }

    @Test
    @DisplayName("Should track order timestamps")
    void shouldTrackOrderTimestamps() {
        // GIVEN: New order
        Instant createdAt = Instant.now();
        Order order = Order.newOrder(
            "ORD-011", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-11", createdAt, testTimeBs
        );

        // WHEN: Update order status
        Order updated = order.withStatus(OrderStatus.APPROVED);

        // THEN: Timestamps tracked
        assertThat(order.createdAt()).isEqualTo(createdAt);
        assertThat(order.updatedAt()).isEqualTo(createdAt);
        assertThat(updated.updatedAt()).isAfterOrEqualTo(order.updatedAt());
    }

    @Test
    @DisplayName("Should handle STOP order lifecycle")
    void shouldHandleStopOrderLifecycle() {
        // GIVEN: STOP order
        Order stopOrder = Order.newOrder(
            "ORD-012", "client-1", "account-1", "INST-001",
            OrderSide.SELL, OrderType.STOP, TimeInForce.DAY,
            BigDecimal.valueOf(100), null, BigDecimal.valueOf(145.00),
            "idempotency-key-12", testTime, testTimeBs
        );

        // THEN: STOP order created correctly
        assertThat(stopOrder.orderType()).isEqualTo(OrderType.STOP);
        assertThat(stopOrder.stopPrice()).isEqualTo(BigDecimal.valueOf(145.00));
        assertThat(stopOrder.price()).isNull();
    }
}

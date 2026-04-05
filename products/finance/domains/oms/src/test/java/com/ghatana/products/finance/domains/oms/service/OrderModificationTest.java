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
 * @doc.purpose Tests for order modification and amendment flows
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Order Modification Tests")
class OrderModificationTest {

    private Instant testTime;
    private String testTimeBs;

    @BeforeEach
    void setUp() {
        testTime = Instant.now();
        testTimeBs = "2080-12-15";
    }

    @Test
    @DisplayName("Should track order modifications via status changes")
    void shouldTrackOrderModificationsViaStatusChanges() {
        // GIVEN: Order in PENDING status
        Order order = Order.newOrder(
            "ORD-001", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-1", testTime, testTimeBs
        );

        // WHEN: Modify via status change
        Order modifiedOrder = order.withStatus(OrderStatus.APPROVED);

        // THEN: Status updated
        assertThat(modifiedOrder.status()).isEqualTo(OrderStatus.APPROVED);
        assertThat(modifiedOrder.orderId()).isEqualTo(order.orderId());
    }

    @Test
    @DisplayName("Should track order enrichment as modification")
    void shouldTrackOrderEnrichmentAsModification() {
        // GIVEN: Order in PENDING status
        Order order = Order.newOrder(
            "ORD-002", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-2", testTime, testTimeBs
        );

        // WHEN: Enrich order
        Order modifiedOrder = order.withEnrichment("AAPL", "NASDAQ", "USD",
            BigDecimal.valueOf(15000.00), BigDecimal.valueOf(149.50));

        // THEN: Enrichment applied
        assertThat(modifiedOrder.instrumentSymbol()).isEqualTo("AAPL");
        assertThat(modifiedOrder.orderId()).isEqualTo(order.orderId());
    }

    @Test
    @DisplayName("Should not allow modification after partial fill")
    void shouldNotAllowModificationAfterPartialFill() {
        // GIVEN: Order with partial fill (simulated by setting filled quantity > 0)
        Order order = Order.newOrder(
            "ORD-003", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-3", testTime, testTimeBs
        );
        
        Fill fill = new Fill("FILL-001", "ORD-003", "EXEC-001",
            BigDecimal.valueOf(50), BigDecimal.valueOf(150.00), BigDecimal.ZERO, testTime);
        Order partiallyFilled = order.withFill(fill, BigDecimal.valueOf(150.00));

        // WHEN/THEN: Modification should be rejected
        assertThatThrownBy(() -> validateModification(partiallyFilled))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot modify order with fills");
    }

    @Test
    @DisplayName("Should allow modification in PENDING status")
    void shouldAllowModificationInPendingStatus() {
        // GIVEN: Order in PENDING status
        Order order = Order.newOrder(
            "ORD-004", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-4", testTime, testTimeBs
        );

        // WHEN/THEN: Modification should be allowed
        assertThatCode(() -> validateModification(order))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should allow modification in APPROVED status")
    void shouldAllowModificationInApprovedStatus() {
        // GIVEN: Order in APPROVED status
        Order order = Order.newOrder(
            "ORD-005", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-5", testTime, testTimeBs
        ).withStatus(OrderStatus.APPROVED);

        // WHEN/THEN: Modification should be allowed
        assertThatCode(() -> validateModification(order))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should not allow modification in FILLED status")
    void shouldNotAllowModificationInFilledStatus() {
        // GIVEN: Order in FILLED status
        Order order = Order.newOrder(
            "ORD-006", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-6", testTime, testTimeBs
        ).withStatus(OrderStatus.FILLED);

        // WHEN/THEN: Modification should be rejected
        assertThatThrownBy(() -> validateModification(order))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot modify completed order");
    }

    @Test
    @DisplayName("Should track modification history")
    void shouldTrackModificationHistory() {
        // GIVEN: Order with modifications
        Order original = Order.newOrder(
            "ORD-007", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-7", testTime, testTimeBs
        );

        // WHEN: Apply multiple modifications
        Order mod1 = original.withStatus(OrderStatus.APPROVED);
        Order mod2 = mod1.withEnrichment("AAPL", "NASDAQ", "USD",
            BigDecimal.valueOf(15000.00), BigDecimal.valueOf(149.50));

        // THEN: Modifications tracked
        assertThat(mod2.status()).isEqualTo(OrderStatus.APPROVED);
        assertThat(mod2.instrumentSymbol()).isEqualTo("AAPL");
        assertThat(mod2.updatedAt()).isAfter(original.updatedAt());
    }

    @Test
    @DisplayName("Should validate order state transitions")
    void shouldValidateOrderStateTransitions() {
        // GIVEN: Order to modify
        Order order = Order.newOrder(
            "ORD-008", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-8", testTime, testTimeBs
        );

        // WHEN: Apply valid status transitions
        Order approved = order.withStatus(OrderStatus.APPROVED);
        Order routed = approved.withStatus(OrderStatus.ROUTED);

        // THEN: Transitions successful
        assertThat(routed.status()).isEqualTo(OrderStatus.ROUTED);
    }

    @Test
    @DisplayName("Should preserve order immutability during modification")
    void shouldPreserveOrderImmutabilityDuringModification() {
        // GIVEN: Original order
        Order original = Order.newOrder(
            "ORD-009", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-9", testTime, testTimeBs
        );

        // WHEN: Modify order
        Order modified = original.withStatus(OrderStatus.APPROVED);

        // THEN: Original unchanged
        assertThat(original.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(modified.status()).isEqualTo(OrderStatus.APPROVED);
        assertThat(original).isNotSameAs(modified);
    }

    @Test
    @DisplayName("Should handle order enrichment")
    void shouldHandleOrderEnrichment() {
        // GIVEN: Order without enrichment
        Order order = Order.newOrder(
            "ORD-010", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-10", testTime, testTimeBs
        );

        // WHEN: Enrich order
        Order modified = order.withEnrichment("AAPL", "NASDAQ", "USD",
            BigDecimal.valueOf(15000.00), BigDecimal.valueOf(149.50));

        // THEN: Enrichment applied
        assertThat(modified.instrumentSymbol()).isEqualTo("AAPL");
    }

    // Helper methods
    private void validateModification(Order order) {
        if (order.status() == OrderStatus.FILLED || order.status() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Cannot modify completed order");
        }
        if (order.filledQuantity().compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException("Cannot modify order with fills");
        }
    }
}

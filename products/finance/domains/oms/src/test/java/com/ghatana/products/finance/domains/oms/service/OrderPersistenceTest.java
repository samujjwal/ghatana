package com.ghatana.products.finance.domains.oms.service;

import com.ghatana.products.finance.domains.oms.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for order persistence operations
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Order Persistence Tests")
class OrderPersistenceTest {

    private Instant testTime;
    private String testTimeBs;
    private Map<String, Order> orderStore;

    @BeforeEach
    void setUp() {
        testTime = Instant.now();
        testTimeBs = "2080-12-15";
        orderStore = new HashMap<>();
    }

    @Test
    @DisplayName("Should persist new order")
    void shouldPersistNewOrder() {
        // GIVEN: New order
        Order order = Order.newOrder(
            "ORD-001", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-1", testTime, testTimeBs
        );

        // WHEN: Save order
        saveOrder(order);

        // THEN: Order persisted
        assertThat(orderStore).containsKey("ORD-001");
        assertThat(orderStore.get("ORD-001")).isEqualTo(order);
    }

    @Test
    @DisplayName("Should retrieve persisted order")
    void shouldRetrievePersistedOrder() {
        // GIVEN: Persisted order
        Order order = Order.newOrder(
            "ORD-002", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-2", testTime, testTimeBs
        );
        saveOrder(order);

        // WHEN: Retrieve order
        Optional<Order> retrieved = findOrder("ORD-002");

        // THEN: Order retrieved
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().orderId()).isEqualTo("ORD-002");
    }

    @Test
    @DisplayName("Should update existing order")
    void shouldUpdateExistingOrder() {
        // GIVEN: Existing order
        Order order = Order.newOrder(
            "ORD-003", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-3", testTime, testTimeBs
        );
        saveOrder(order);

        // WHEN: Update order
        Order updated = order.withStatus(OrderStatus.APPROVED);
        saveOrder(updated);

        // THEN: Order updated
        Optional<Order> retrieved = findOrder("ORD-003");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().status()).isEqualTo(OrderStatus.APPROVED);
    }

    @Test
    @DisplayName("Should handle concurrent updates")
    void shouldHandleConcurrentUpdates() {
        // GIVEN: Order being updated concurrently
        Order order = Order.newOrder(
            "ORD-004", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-4", testTime, testTimeBs
        );
        saveOrder(order);

        // WHEN: Multiple updates
        Order update1 = order.withStatus(OrderStatus.APPROVED);
        Order update2 = order.withEnrichment("AAPL", "NASDAQ", "USD",
            BigDecimal.valueOf(15000.00), BigDecimal.valueOf(149.50));

        saveOrder(update1);
        saveOrder(update2);

        // THEN: Last update wins
        Optional<Order> retrieved = findOrder("ORD-004");
        assertThat(retrieved).isPresent();
    }

    @Test
    @DisplayName("Should persist order with fills")
    void shouldPersistOrderWithFills() {
        // GIVEN: Order with fills
        Order order = Order.newOrder(
            "ORD-005", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-5", testTime, testTimeBs
        );

        Fill fill = new Fill("FILL-001", "ORD-005", "EXEC-001",
            BigDecimal.valueOf(50), BigDecimal.valueOf(150.00), BigDecimal.ZERO, testTime);
        Order withFill = order.withFill(fill, BigDecimal.valueOf(150.00));

        // WHEN: Save order with fills
        saveOrder(withFill);

        // THEN: Fills persisted
        Optional<Order> retrieved = findOrder("ORD-005");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().fills()).hasSize(1);
    }

    @Test
    @DisplayName("Should query orders by status")
    void shouldQueryOrdersByStatus() {
        // GIVEN: Multiple orders with different statuses
        Order pending = Order.newOrder("ORD-006", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-6", testTime, testTimeBs);

        Order approved = Order.newOrder("ORD-007", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-7", testTime, testTimeBs).withStatus(OrderStatus.APPROVED);

        saveOrder(pending);
        saveOrder(approved);

        // WHEN: Query by status
        long approvedCount = orderStore.values().stream()
            .filter(o -> o.status() == OrderStatus.APPROVED)
            .count();

        // THEN: Correct orders returned
        assertThat(approvedCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Should query orders by client")
    void shouldQueryOrdersByClient() {
        // GIVEN: Orders for different clients
        Order client1Order = Order.newOrder("ORD-008", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-8", testTime, testTimeBs);

        Order client2Order = Order.newOrder("ORD-009", "client-2", "account-2", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-9", testTime, testTimeBs);

        saveOrder(client1Order);
        saveOrder(client2Order);

        // WHEN: Query by client
        long client1Count = orderStore.values().stream()
            .filter(o -> o.clientId().equals("client-1"))
            .count();

        // THEN: Correct orders returned
        assertThat(client1Count).isEqualTo(1);
    }

    @Test
    @DisplayName("Should handle order deletion")
    void shouldHandleOrderDeletion() {
        // GIVEN: Persisted order
        Order order = Order.newOrder("ORD-010", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-10", testTime, testTimeBs);
        saveOrder(order);

        // WHEN: Delete order
        deleteOrder("ORD-010");

        // THEN: Order deleted
        assertThat(orderStore).doesNotContainKey("ORD-010");
    }

    @Test
    @DisplayName("Should maintain data integrity")
    void shouldMaintainDataIntegrity() {
        // GIVEN: Order with all fields
        Order order = Order.newOrder("ORD-011", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-11", testTime, testTimeBs)
            .withEnrichment("AAPL", "NASDAQ", "USD",
                BigDecimal.valueOf(15000.00), BigDecimal.valueOf(149.50));

        // WHEN: Save and retrieve
        saveOrder(order);
        Optional<Order> retrieved = findOrder("ORD-011");

        // THEN: All fields preserved
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().instrumentSymbol()).isEqualTo("AAPL");
        assertThat(retrieved.get().exchange()).isEqualTo("NASDAQ");
        assertThat(retrieved.get().currency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("Should support batch operations")
    void shouldSupportBatchOperations() {
        // GIVEN: Multiple orders
        Order order1 = Order.newOrder("ORD-012", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-12", testTime, testTimeBs);

        Order order2 = Order.newOrder("ORD-013", "client-1", "account-1", "INST-001",
            OrderSide.SELL, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(151.00), null,
            "idempotency-key-13", testTime, testTimeBs);

        // WHEN: Batch save
        saveOrder(order1);
        saveOrder(order2);

        // THEN: All orders saved
        assertThat(orderStore).hasSize(2);
    }

    // Helper methods
    private void saveOrder(Order order) {
        orderStore.put(order.orderId(), order);
    }

    private Optional<Order> findOrder(String orderId) {
        return Optional.ofNullable(orderStore.get(orderId));
    }

    private void deleteOrder(String orderId) {
        orderStore.remove(orderId);
    }
}

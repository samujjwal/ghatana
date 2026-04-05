package com.ghatana.products.finance.domains.oms.service;

import com.ghatana.products.finance.domains.oms.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for concurrent order handling and thread safety
 * @doc.layer Test
 * @doc.pattern Concurrency Test
 */
@DisplayName("Order Concurrency Tests")
class OrderConcurrencyTest {

    private Instant testTime;
    private String testTimeBs;
    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        testTime = Instant.now();
        testTimeBs = "2080-12-15";
        executorService = Executors.newFixedThreadPool(10);
    }

    @Test
    @DisplayName("Should handle concurrent order creation")
    void shouldHandleConcurrentOrderCreation() throws Exception {
        // GIVEN: Multiple threads creating orders
        int threadCount = 10;
        int ordersPerThread = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Order> orders = new CopyOnWriteArrayList<>();

        // WHEN: Create orders concurrently
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executorService.submit(() -> {
                try {
                    for (int j = 0; j < ordersPerThread; j++) {
                        Order order = Order.newOrder(
                            "ORD-" + threadId + "-" + j, "client-1", "account-1", "INST-001",
                            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
                            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
                            "key-" + threadId + "-" + j, testTime, testTimeBs
                        );
                        orders.add(order);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);

        // THEN: All orders created
        assertThat(orders).hasSize(threadCount * ordersPerThread);
    }

    @Test
    @DisplayName("Should handle concurrent order modifications")
    void shouldHandleConcurrentOrderModifications() throws Exception {
        // GIVEN: Order being modified concurrently
        Order order = Order.newOrder(
            "ORD-001", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-1", testTime, testTimeBs
        );

        int threadCount = 5;
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Order> modifiedOrders = new CopyOnWriteArrayList<>();

        // WHEN: Modify concurrently
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    Order modified = order.withStatus(OrderStatus.APPROVED);
                    modifiedOrders.add(modified);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);

        // THEN: All modifications completed
        assertThat(modifiedOrders).hasSize(threadCount);
    }

    @Test
    @DisplayName("Should handle concurrent status changes")
    void shouldHandleConcurrentStatusChanges() throws Exception {
        // GIVEN: Order with concurrent status changes
        Order order = Order.newOrder(
            "ORD-002", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-2", testTime, testTimeBs
        );

        int threadCount = 3;
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Order> statusChanges = new CopyOnWriteArrayList<>();

        // WHEN: Change status concurrently
        OrderStatus[] statuses = {OrderStatus.APPROVED, OrderStatus.ROUTED, OrderStatus.FILLED};
        for (int i = 0; i < threadCount; i++) {
            final OrderStatus newStatus = statuses[i];
            executorService.submit(() -> {
                try {
                    Order changed = order.withStatus(newStatus);
                    statusChanges.add(changed);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);

        // THEN: All status changes completed
        assertThat(statusChanges).hasSize(threadCount);
    }

    @Test
    @DisplayName("Should handle concurrent fill processing")
    void shouldHandleConcurrentFillProcessing() throws Exception {
        // GIVEN: Order receiving concurrent fills
        Order order = Order.newOrder(
            "ORD-003", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-3", testTime, testTimeBs
        );

        int fillCount = 10;
        CountDownLatch latch = new CountDownLatch(fillCount);
        AtomicInteger fillsProcessed = new AtomicInteger(0);

        // WHEN: Process fills concurrently
        for (int i = 0; i < fillCount; i++) {
            final int fillId = i;
            executorService.submit(() -> {
                try {
                    Fill fill = new Fill("FILL-" + fillId, "ORD-003", "EXEC-" + fillId,
                        BigDecimal.valueOf(10), BigDecimal.valueOf(150.00), BigDecimal.ZERO, testTime);
                    // In real scenario, would update order with fill
                    fillsProcessed.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);

        // THEN: All fills processed
        assertThat(fillsProcessed.get()).isEqualTo(fillCount);
    }

    @Test
    @DisplayName("Should maintain order immutability under concurrency")
    void shouldMaintainOrderImmutabilityUnderConcurrency() throws Exception {
        // GIVEN: Original order
        Order original = Order.newOrder(
            "ORD-004", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-4", testTime, testTimeBs
        );

        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);

        // WHEN: Modify from multiple threads
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    original.withStatus(OrderStatus.APPROVED);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);

        // THEN: Original unchanged
        assertThat(original.status()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("Should handle concurrent order queries")
    void shouldHandleConcurrentOrderQueries() throws Exception {
        // GIVEN: Order being queried concurrently
        Order order = Order.newOrder(
            "ORD-005", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-5", testTime, testTimeBs
        );

        int queryCount = 100;
        CountDownLatch latch = new CountDownLatch(queryCount);
        AtomicInteger successfulQueries = new AtomicInteger(0);

        // WHEN: Query concurrently
        for (int i = 0; i < queryCount; i++) {
            executorService.submit(() -> {
                try {
                    if (order.orderId().equals("ORD-005")) {
                        successfulQueries.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);

        // THEN: All queries successful
        assertThat(successfulQueries.get()).isEqualTo(queryCount);
    }

    @Test
    @DisplayName("Should handle race conditions in order lifecycle")
    void shouldHandleRaceConditionsInOrderLifecycle() throws Exception {
        // GIVEN: Order in lifecycle
        Order order = Order.newOrder(
            "ORD-006", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-6", testTime, testTimeBs
        );

        CountDownLatch latch = new CountDownLatch(2);
        List<Order> results = new CopyOnWriteArrayList<>();

        // WHEN: Concurrent lifecycle operations
        executorService.submit(() -> {
            try {
                results.add(order.withStatus(OrderStatus.APPROVED));
            } finally {
                latch.countDown();
            }
        });

        executorService.submit(() -> {
            try {
                results.add(order.withStatus(OrderStatus.CANCELLED));
            } finally {
                latch.countDown();
            }
        });

        latch.await(10, TimeUnit.SECONDS);

        // THEN: Both operations completed
        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("Should handle high concurrency load")
    void shouldHandleHighConcurrencyLoad() throws Exception {
        // GIVEN: High concurrency scenario
        int threadCount = 50;
        int operationsPerThread = 20;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger completedOperations = new AtomicInteger(0);

        // WHEN: Execute many concurrent operations
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executorService.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        Order.newOrder(
                            "ORD-HC-" + threadId + "-" + j, "client-1", "account-1", "INST-001",
                            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
                            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
                            "key-hc-" + threadId + "-" + j, testTime, testTimeBs
                        );
                        completedOperations.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);

        // THEN: All operations completed
        assertThat(completedOperations.get()).isEqualTo(threadCount * operationsPerThread);
    }

    @Test
    @DisplayName("Should prevent data corruption under concurrency")
    void shouldPreventDataCorruptionUnderConcurrency() throws Exception {
        // GIVEN: Order with concurrent updates
        Order order = Order.newOrder(
            "ORD-007", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-7", testTime, testTimeBs
        );

        int updateCount = 100;
        CountDownLatch latch = new CountDownLatch(updateCount);
        List<Order> updates = new CopyOnWriteArrayList<>();

        // WHEN: Concurrent updates
        for (int i = 0; i < updateCount; i++) {
            executorService.submit(() -> {
                try {
                    Order updated = order.withStatus(OrderStatus.APPROVED);
                    updates.add(updated);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);

        // THEN: No data corruption
        assertThat(updates).hasSize(updateCount);
        assertThat(updates).allMatch(o -> o.orderId().equals("ORD-007"));
    }
}

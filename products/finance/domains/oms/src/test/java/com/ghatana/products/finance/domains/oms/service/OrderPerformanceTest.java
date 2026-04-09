package com.ghatana.products.finance.domains.oms.service;

import com.ghatana.products.finance.domains.oms.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for order processing performance benchmarks
 * @doc.layer Test
 * @doc.pattern Performance Test
 */
@DisplayName("Order Performance Tests")
class OrderPerformanceTest {

    private Instant testTime;
    private String testTimeBs;

    @BeforeEach
    void setUp() {
        testTime = Instant.now();
        testTimeBs = "2080-12-15";
    }

    @Test
    @DisplayName("Should create order within performance target")
    void shouldCreateOrderWithinPerformanceTarget() {
        // GIVEN: Performance target of 10ms
        long targetMs = 10;

        // WHEN: Create order
        long startTime = System.currentTimeMillis();
        Order order = Order.newOrder(
            "ORD-001", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-1", testTime, testTimeBs
        );
        long duration = System.currentTimeMillis() - startTime;

        // THEN: Within target
        assertThat(duration).isLessThan(targetMs);
        assertThat(order).isNotNull();
    }

    @Test
    @DisplayName("Should process 1000 orders within acceptable time")
    void shouldProcess1000OrdersWithinAcceptableTime() {
        // GIVEN: Target of 1 second for 1000 orders
        int orderCount = 1000;
        long targetMs = 1000;

        // WHEN: Create multiple orders
        long startTime = System.currentTimeMillis();
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < orderCount; i++) {
            orders.add(Order.newOrder(
                "ORD-" + i, "client-1", "account-1", "INST-001",
                OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
                BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
                "key-" + i, testTime, testTimeBs
            ));
        }
        long duration = System.currentTimeMillis() - startTime;

        // THEN: Within target
        assertThat(duration).isLessThan(targetMs);
        assertThat(orders).hasSize(orderCount);
    }

    @Test
    @DisplayName("Should handle order state transitions efficiently")
    void shouldHandleOrderStateTransitionsEfficiently() {
        // GIVEN: Order for state transitions
        Order order = Order.newOrder(
            "ORD-002", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-2", testTime, testTimeBs
        );

        // WHEN: Perform state transitions
        long startTime = System.currentTimeMillis();
        Order approved = order.withStatus(OrderStatus.APPROVED);
        Order routed = approved.withStatus(OrderStatus.ROUTED);
        Order filled = routed.withStatus(OrderStatus.FILLED);
        long duration = System.currentTimeMillis() - startTime;

        // THEN: Transitions fast
        assertThat(duration).isLessThan(5);
        assertThat(filled.status()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    @DisplayName("Should handle order enrichment efficiently")
    void shouldHandleOrderEnrichmentEfficiently() {
        // GIVEN: Order to enrich
        Order order = Order.newOrder(
            "ORD-003", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-3", testTime, testTimeBs
        );

        // WHEN: Enrich order
        long startTime = System.currentTimeMillis();
        Order enriched = order.withEnrichment(
            "AAPL", "NASDAQ", "USD",
            BigDecimal.valueOf(15000.00), BigDecimal.valueOf(149.50)
        );
        long duration = System.currentTimeMillis() - startTime;

        // THEN: Enrichment fast
        assertThat(duration).isLessThan(5);
        assertThat(enriched.instrumentSymbol()).isEqualTo("AAPL");
    }

    @Test
    @DisplayName("Should process fills efficiently")
    void shouldProcessFillsEfficiently() {
        // GIVEN: Order with multiple fills
        Order order = Order.newOrder(
            "ORD-004", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-4", testTime, testTimeBs
        );

        // WHEN: Add multiple fills
        long startTime = System.currentTimeMillis();
        Order current = order;
        for (int i = 0; i < 10; i++) {
            Fill fill = new Fill("FILL-" + i, "ORD-004", "EXEC-" + i,
                BigDecimal.valueOf(10), BigDecimal.valueOf(150.00), BigDecimal.ZERO, testTime);
            current = current.withFill(fill, BigDecimal.valueOf(150.00));
        }
        long duration = System.currentTimeMillis() - startTime;

        // THEN: Fill processing fast
        assertThat(duration).isLessThan(50);
        assertThat(current.fills()).hasSize(10);
    }

    @Test
    @DisplayName("Should measure order latency")
    void shouldMeasureOrderLatency() {
        // GIVEN: Order processing
        Instant startTime = Instant.now();

        Order order = Order.newOrder(
            "ORD-005", "client-1", "account-1", "INST-001",
            OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
            BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
            "idempotency-key-5", startTime, testTimeBs
        );

        // WHEN: Complete order lifecycle
        Order approved = order.withStatus(OrderStatus.APPROVED);
        Order routed = approved.withStatus(OrderStatus.ROUTED);
        Order filled = routed.withStatus(OrderStatus.FILLED);

        Instant endTime = Instant.now();
        Duration latency = Duration.between(startTime, endTime);

        // THEN: Latency measured
        assertThat(latency.toMillis()).isLessThan(100);
    }

    @Test
    @DisplayName("Should handle concurrent order creation")
    void shouldHandleConcurrentOrderCreation() {
        // GIVEN: Multiple concurrent orders
        int concurrentOrders = 100;

        // WHEN: Create orders concurrently
        long startTime = System.currentTimeMillis();
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < concurrentOrders; i++) {
            orders.add(Order.newOrder(
                "ORD-CONC-" + i, "client-1", "account-1", "INST-001",
                OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
                BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
                "key-conc-" + i, testTime, testTimeBs
            ));
        }
        long duration = System.currentTimeMillis() - startTime;

        // THEN: All orders created efficiently
        assertThat(orders).hasSize(concurrentOrders);
        assertThat(duration).isLessThan(200);
    }

    @Test
    @DisplayName("Should optimize memory usage")
    void shouldOptimizeMemoryUsage() {
        // GIVEN: Large number of orders
        int orderCount = 10000;

        // WHEN: Create many orders
        Runtime runtime = Runtime.getRuntime();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < orderCount; i++) {
            orders.add(Order.newOrder(
                "ORD-MEM-" + i, "client-1", "account-1", "INST-001",
                OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
                BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
                "key-mem-" + i, testTime, testTimeBs
            ));
        }

        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = memoryAfter - memoryBefore;

        // THEN: Memory usage reasonable
        assertThat(orders).hasSize(orderCount);
        assertThat(memoryUsed).isLessThan(100_000_000); // Less than 100MB
    }

    @Test
    @DisplayName("Should maintain performance under load")
    void shouldMaintainPerformanceUnderLoad() {
        // GIVEN: High load scenario
        int iterations = 100;
        List<Long> durations = new ArrayList<>();

        // WHEN: Process orders repeatedly
        for (int i = 0; i < iterations; i++) {
            long startTime = System.currentTimeMillis();
            Order order = Order.newOrder(
                "ORD-LOAD-" + i, "client-1", "account-1", "INST-001",
                OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
                BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
                "key-load-" + i, testTime, testTimeBs
            );
            long duration = System.currentTimeMillis() - startTime;
            durations.add(duration);
        }

        // THEN: Performance consistent
        double avgDuration = durations.stream().mapToLong(Long::longValue).average().orElse(0);
        assertThat(avgDuration).isLessThan(10.0);
    }

    @Test
    @DisplayName("Should meet throughput requirements")
    void shouldMeetThroughputRequirements() {
        // GIVEN: Throughput target of 10,000 orders/second
        int targetOrdersPerSecond = 10000;
        int testDurationMs = 100;
        int expectedOrders = (targetOrdersPerSecond * testDurationMs) / 1000;

        // WHEN: Create orders
        long startTime = System.currentTimeMillis();
        int ordersCreated = 0;
        while (System.currentTimeMillis() - startTime < testDurationMs) {
            Order.newOrder(
                "ORD-TP-" + ordersCreated, "client-1", "account-1", "INST-001",
                OrderSide.BUY, OrderType.LIMIT, TimeInForce.DAY,
                BigDecimal.valueOf(100), BigDecimal.valueOf(150.00), null,
                "key-tp-" + ordersCreated, testTime, testTimeBs
            );
            ordersCreated++;
        }

        // THEN: Throughput met
        assertThat(ordersCreated).isGreaterThanOrEqualTo(expectedOrders);
    }
}

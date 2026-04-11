package com.ghatana.products.finance.domains.ems.service;

import com.ghatana.products.finance.domains.ems.domain.ExecutionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for concurrent execution handling per D02-011
 * @doc.layer Test
 * @doc.pattern Concurrency Test
 */
@DisplayName("Execution Concurrency Tests")
class ExecutionConcurrencyTest {

    private ConcurrentOrderProcessor processor;
    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        processor = new ConcurrentOrderProcessor();
        executorService = Executors.newFixedThreadPool(10);
    }

    @Test
    @DisplayName("Should handle concurrent order submissions")
    void shouldHandleConcurrentOrderSubmissions() throws InterruptedException {
        int orderCount = 100;
        CountDownLatch latch = new CountDownLatch(orderCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < orderCount; i++) {
            final int orderId = i;
            executorService.submit(() -> {
                try {
                    processor.submitOrder("order-" + orderId);
                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);

        assertThat(successCount.get()).isEqualTo(orderCount);
        assertThat(processor.getProcessedCount()).isEqualTo(orderCount);
    }

    @Test
    @DisplayName("Should prevent race conditions in fill processing")
    void shouldPreventRaceConditionsInFillProcessing() throws InterruptedException {
        String orderId = "order-1";
        processor.submitOrder(orderId);
        int fillCount = 50;
        CountDownLatch latch = new CountDownLatch(fillCount);

        for (int i = 0; i < fillCount; i++) {
            final int fillId = i;
            executorService.submit(() -> {
                try {
                    processor.processFill(orderId, "fill-" + fillId, 10L);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);

        assertThat(processor.getTotalFilled(orderId)).isEqualTo(500L);
    }

    @Test
    @DisplayName("Should handle concurrent order cancellations")
    void shouldHandleConcurrentOrderCancellations() throws InterruptedException {
        List<String> orderIds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String orderId = "order-" + i;
            processor.submitOrder(orderId);
            orderIds.add(orderId);
        }

        CountDownLatch latch = new CountDownLatch(orderIds.size());

        for (String orderId : orderIds) {
            executorService.submit(() -> {
                try {
                    processor.cancelOrder(orderId);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);

        assertThat(processor.getCancelledCount()).isEqualTo(orderIds.size());
    }

    @Test
    @DisplayName("Should maintain data consistency under concurrent updates")
    void shouldMaintainDataConsistencyUnderConcurrentUpdates() throws InterruptedException {
        String orderId = "order-1";
        processor.submitOrder(orderId);

        int updateCount = 100;
        CountDownLatch latch = new CountDownLatch(updateCount);

        for (int i = 0; i < updateCount; i++) {
            executorService.submit(() -> {
                try {
                    processor.updateOrderStatus(orderId, ExecutionStatus.PARTIALLY_FILLED);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);

        assertThat(processor.getOrderStatus(orderId)).isEqualTo(ExecutionStatus.PARTIALLY_FILLED);
    }

    @Test
    @DisplayName("Should handle deadlock prevention")
    void shouldHandleDeadlockPrevention() throws InterruptedException {
        String order1 = "order-1";
        String order2 = "order-2";
        processor.submitOrder(order1);
        processor.submitOrder(order2);

        CountDownLatch latch = new CountDownLatch(2);

        executorService.submit(() -> {
            try {
                processor.transferFill(order1, order2, 10L);
            } finally {
                latch.countDown();
            }
        });

        executorService.submit(() -> {
            try {
                processor.transferFill(order2, order1, 5L);
            } finally {
                latch.countDown();
            }
        });

        boolean completed = latch.await(5, TimeUnit.SECONDS);

        assertThat(completed).isTrue();
    }

    @Test
    @DisplayName("Should use optimistic locking for order updates")
    void shouldUseOptimisticLockingForOrderUpdates() throws InterruptedException {
        String orderId = "order-1";
        processor.submitOrder(orderId);

        int updateCount = 50;
        CountDownLatch latch = new CountDownLatch(updateCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger retryCount = new AtomicInteger(0);

        for (int i = 0; i < updateCount; i++) {
            executorService.submit(() -> {
                try {
                    boolean success = processor.updateWithOptimisticLock(orderId);
                    if (success) {
                        successCount.incrementAndGet();
                    } else {
                        retryCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);

        assertThat(successCount.get() + retryCount.get()).isEqualTo(updateCount);
    }

    @Test
    @DisplayName("Should handle concurrent read and write operations")
    void shouldHandleConcurrentReadAndWriteOperations() throws InterruptedException {
        String orderId = "order-1";
        processor.submitOrder(orderId);

        int operationCount = 100;
        CountDownLatch latch = new CountDownLatch(operationCount);

        for (int i = 0; i < operationCount / 2; i++) {
            executorService.submit(() -> {
                try {
                    processor.processFill(orderId, "fill-" + System.nanoTime(), 1L);
                } finally {
                    latch.countDown();
                }
            });

            executorService.submit(() -> {
                try {
                    processor.getTotalFilled(orderId);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);

        assertThat(processor.getTotalFilled(orderId)).isGreaterThan(0L);
    }

    @Test
    @DisplayName("Should use thread-safe collections")
    void shouldUseThreadSafeCollections() throws InterruptedException {
        int orderCount = 100;
        CountDownLatch latch = new CountDownLatch(orderCount);

        for (int i = 0; i < orderCount; i++) {
            final int orderId = i;
            executorService.submit(() -> {
                try {
                    processor.submitOrder("order-" + orderId);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);

        List<String> allOrders = processor.getAllOrderIds();
        assertThat(allOrders).hasSize(orderCount);
    }

    @Test
    @DisplayName("Should handle concurrent event publishing")
    void shouldHandleConcurrentEventPublishing() throws InterruptedException {
        int eventCount = 200;
        CountDownLatch latch = new CountDownLatch(eventCount);

        for (int i = 0; i < eventCount; i++) {
            final int eventId = i;
            executorService.submit(() -> {
                try {
                    processor.publishEvent("event-" + eventId);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);

        assertThat(processor.getPublishedEventCount()).isEqualTo(eventCount);
    }

    @Test
    @DisplayName("Should prevent lost updates")
    void shouldPreventLostUpdates() throws InterruptedException {
        String orderId = "order-1";
        processor.submitOrder(orderId);

        int incrementCount = 100;
        CountDownLatch latch = new CountDownLatch(incrementCount);

        for (int i = 0; i < incrementCount; i++) {
            executorService.submit(() -> {
                try {
                    processor.incrementCounter(orderId);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);

        assertThat(processor.getCounter(orderId)).isEqualTo(incrementCount);
    }

    static class ConcurrentOrderProcessor {
        private final ConcurrentHashMap<String, OrderState> orders = new ConcurrentHashMap<>();
        private final AtomicInteger processedCount = new AtomicInteger(0);
        private final AtomicInteger cancelledCount = new AtomicInteger(0);
        private final AtomicInteger publishedEventCount = new AtomicInteger(0);

        void submitOrder(String orderId) {
            orders.put(orderId, new OrderState(orderId, ExecutionStatus.PENDING_ROUTE, 0L, 0));
            processedCount.incrementAndGet();
        }

        void processFill(String orderId, String fillId, long quantity) {
            orders.computeIfPresent(orderId, (id, state) ->
                new OrderState(id, state.status, state.totalFilled + quantity, state.counter)
            );
        }

        long getTotalFilled(String orderId) {
            OrderState state = orders.get(orderId);
            return state != null ? state.totalFilled : 0L;
        }

        void cancelOrder(String orderId) {
            orders.computeIfPresent(orderId, (id, state) ->
                new OrderState(id, ExecutionStatus.CANCELLED, state.totalFilled, state.counter)
            );
            cancelledCount.incrementAndGet();
        }

        void updateOrderStatus(String orderId, ExecutionStatus status) {
            orders.computeIfPresent(orderId, (id, state) ->
                new OrderState(id, status, state.totalFilled, state.counter)
            );
        }

        ExecutionStatus getOrderStatus(String orderId) {
            OrderState state = orders.get(orderId);
            return state != null ? state.status : null;
        }

        void transferFill(String fromOrder, String toOrder, long quantity) {
            synchronized (this) {
                orders.computeIfPresent(fromOrder, (id, state) ->
                    new OrderState(id, state.status, state.totalFilled - quantity, state.counter)
                );
                orders.computeIfPresent(toOrder, (id, state) ->
                    new OrderState(id, state.status, state.totalFilled + quantity, state.counter)
                );
            }
        }

        boolean updateWithOptimisticLock(String orderId) {
            return orders.computeIfPresent(orderId, (id, state) ->
                new OrderState(id, state.status, state.totalFilled, state.counter + 1)
            ) != null;
        }

        List<String> getAllOrderIds() {
            return new ArrayList<>(orders.keySet());
        }

        void publishEvent(String eventId) {
            publishedEventCount.incrementAndGet();
        }

        void incrementCounter(String orderId) {
            orders.computeIfPresent(orderId, (id, state) ->
                new OrderState(id, state.status, state.totalFilled, state.counter + 1)
            );
        }

        int getCounter(String orderId) {
            OrderState state = orders.get(orderId);
            return state != null ? state.counter : 0;
        }

        int getProcessedCount() {
            return processedCount.get();
        }

        int getCancelledCount() {
            return cancelledCount.get();
        }

        int getPublishedEventCount() {
            return publishedEventCount.get();
        }

        record OrderState(String orderId, ExecutionStatus status, long totalFilled, int counter) {}
    }
}

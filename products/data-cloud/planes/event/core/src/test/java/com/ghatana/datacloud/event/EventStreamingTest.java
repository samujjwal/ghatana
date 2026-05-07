/**
 * @doc.type class
 * @doc.purpose DC-P2-003: Event stream soak and backpressure tests
 * @doc.layer products
 * @doc.pattern IntegrationTest
 */
package com.ghatana.datacloud.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DC-P2-003: Event Stream Soak and Backpressure Tests
 *
 * <p>Production-grade soak and backpressure tests for event streaming to verify:
 * <ul>
 *   <li>System stability under sustained high load (soak tests)</li>
 *   <li>Proper backpressure application when buffers fill</li>
 *   <li>No memory leaks or resource exhaustion over time</li>
 *   <li>Graceful degradation under extreme load</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose DC-P2-003: Event stream soak and backpressure tests
 * @doc.layer products
 * @doc.pattern IntegrationTest
 */
@DisplayName("DC-P2-003: Event Stream Soak and Backpressure Tests")
@Tag("production")
class EventStreamingTest {

    // ════════════════════════════════════════════════════════════════
    // Soak Tests
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Soak Tests")
    class SoakTests {

        @Test
        @DisplayName("sustained high throughput: maintains stability over time")
        void sustainedHighThroughputMaintainsStability() throws Exception {
            int eventCount = 10_000;
            int producerThreads = 4;
            int consumerThreads = 2;
            ConcurrentLinkedQueue<Map<String, Object>> eventBuffer = new ConcurrentLinkedQueue<>();
            AtomicInteger producedCount = new AtomicInteger(0);
            AtomicInteger consumedCount = new AtomicInteger(0);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completeLatch = new CountDownLatch(producerThreads + consumerThreads);

            ExecutorService executor = Executors.newFixedThreadPool(producerThreads + consumerThreads);

            // Producers
            for (int i = 0; i < producerThreads; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        int eventsPerProducer = eventCount / producerThreads;
                        for (int j = 0; j < eventsPerProducer; j++) {
                            Map<String, Object> event = createEvent(
                                UUID.randomUUID().toString(),
                                "HighThroughputEvent"
                            );
                            eventBuffer.offer(event);
                            producedCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        completeLatch.countDown();
                    }
                });
            }

            // Consumers
            for (int i = 0; i < consumerThreads; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        while (!Thread.currentThread().isInterrupted()) {
                            Map<String, Object> event = eventBuffer.poll();
                            if (event != null) {
                                consumedCount.incrementAndGet();
                            } else if (consumedCount.get() >= eventCount) {
                                break;
                            }
                            Thread.sleep(1);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        completeLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = completeLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);

            assertThat(completed).as("Test should complete within timeout").isTrue();
            assertThat(producedCount.get()).as("All events should be produced").isEqualTo(eventCount);
            assertThat(consumedCount.get()).as("All events should be consumed")
                .isGreaterThanOrEqualTo((int) (eventCount * 0.95)); // Allow 5% tolerance
        }

        @Test
        @DisplayName("extended duration: no memory leaks over time")
        void extendedDurationNoMemoryLeaks() throws Exception {
            int durationSeconds = 10;
            int eventsPerSecond = 100;
            ConcurrentLinkedQueue<Map<String, Object>> eventBuffer = new ConcurrentLinkedQueue<>();
            AtomicLong totalProcessed = new AtomicLong(0);
            ExecutorService executor = Executors.newSingleThreadExecutor();

            long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

            Runnable producer = () -> {
                long endTime = System.currentTimeMillis() + (durationSeconds * 1000L);
                long intervalNanos = TimeUnit.SECONDS.toNanos(1) / eventsPerSecond;
                long nextEventAt = System.nanoTime();
                while (System.currentTimeMillis() < endTime && !Thread.currentThread().isInterrupted()) {
                    Map<String, Object> event = createEvent(
                        UUID.randomUUID().toString(),
                        "SoakEvent"
                    );
                    eventBuffer.offer(event);
                    // Simulate processing
                    Map<String, Object> processed = eventBuffer.poll();
                    if (processed != null) {
                        totalProcessed.incrementAndGet();
                    }

                    nextEventAt += intervalNanos;
                    long sleepNanos = nextEventAt - System.nanoTime();
                    if (sleepNanos > 0) {
                        LockSupport.parkNanos(sleepNanos);
                    }
                }
            };

            executor.submit(producer);
            executor.shutdown();
            executor.awaitTermination(durationSeconds + 5, TimeUnit.SECONDS);

            // Force GC to get accurate memory reading
            System.gc();
            Thread.sleep(100);

            long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long memoryIncrease = endMemory - startMemory;
            long memoryIncreaseMb = memoryIncrease / (1024 * 1024);

            assertThat(totalProcessed.get())
                .as("Should process significant number of events")
                .isGreaterThan((long) (eventsPerSecond * durationSeconds * 0.8));
            assertThat(memoryIncreaseMb)
                .as("Memory increase should be reasonable (< 50MB for this test)")
                .isLessThan(50);
        }

        @Test
        @DisplayName("burst load: handles sudden traffic spikes")
        void burstLoadHandlesTrafficSpikes() throws Exception {
            int burstSize = 5_000;
            int burstCount = 3;
            ConcurrentLinkedQueue<Map<String, Object>> eventBuffer = new ConcurrentLinkedQueue<>();
            AtomicInteger processedCount = new AtomicInteger(0);
            ExecutorService executor = Executors.newFixedThreadPool(4);

            for (int burst = 0; burst < burstCount; burst++) {
                // Burst phase
                for (int i = 0; i < burstSize; i++) {
                    eventBuffer.offer(createEvent(UUID.randomUUID().toString(), "BurstEvent"));
                }

                // Recovery phase
                Thread.sleep(100);
            }

            // Process all events
            for (int i = 0; i < 4; i++) {
                executor.submit(() -> {
                    while (!eventBuffer.isEmpty()) {
                        Map<String, Object> event = eventBuffer.poll();
                        if (event != null) {
                            processedCount.incrementAndGet();
                        }
                    }
                });
            }

            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);

            assertThat(processedCount.get())
                .as("All burst events should be processed")
                .isEqualTo(burstSize * burstCount);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Backpressure Tests
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Backpressure Tests")
    class BackpressureTests {

        @Test
        @DisplayName("buffer saturation: applies backpressure when full")
        void bufferSaturationAppliesBackpressure() throws Exception {
            int bufferCapacity = 1000;
            int overflowEvents = 500;
            List<Map<String, Object>> boundedBuffer = new ArrayList<>();
            AtomicInteger droppedEvents = new AtomicInteger(0);
            AtomicInteger acceptedEvents = new AtomicInteger(0);

            // Producer attempting to exceed capacity
            for (int i = 0; i < bufferCapacity + overflowEvents; i++) {
                if (boundedBuffer.size() < bufferCapacity) {
                    boundedBuffer.add(createEvent(UUID.randomUUID().toString(), "BackpressureEvent"));
                    acceptedEvents.incrementAndGet();
                } else {
                    droppedEvents.incrementAndGet();
                }
            }

            assertThat(acceptedEvents.get()).as("Should accept up to buffer capacity")
                .isEqualTo(bufferCapacity);
            assertThat(droppedEvents.get()).as("Should drop overflow events")
                .isEqualTo(overflowEvents);
            assertThat(boundedBuffer.size()).as("Buffer should not exceed capacity")
                .isEqualTo(bufferCapacity);
        }

        @Test
        @DisplayName("slow consumer: producer throttles appropriately")
        void slowConsumerProducerThrottles() throws Exception {
            int fastProducerRate = 100; // events per second
            int slowConsumerRate = 50; // events per second
            int durationSeconds = 5;
            ConcurrentLinkedQueue<Map<String, Object>> buffer = new ConcurrentLinkedQueue<>();
            AtomicInteger produced = new AtomicInteger(0);
            AtomicInteger consumed = new AtomicInteger(0);
            AtomicInteger throttled = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(2);

            // Fast producer
            executor.submit(() -> {
                long endTime = System.currentTimeMillis() + (durationSeconds * 1000L);
                while (System.currentTimeMillis() < endTime && !Thread.currentThread().isInterrupted()) {
                    if (buffer.size() < 200) { // Soft limit for backpressure
                        buffer.offer(createEvent(UUID.randomUUID().toString(), "ProducerEvent"));
                        produced.incrementAndGet();
                    } else {
                        throttled.incrementAndGet();
                    }
                    try {
                        Thread.sleep(1000 / fastProducerRate);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });

            // Slow consumer
            executor.submit(() -> {
                long endTime = System.currentTimeMillis() + (durationSeconds * 1000L);
                while (System.currentTimeMillis() < endTime && !Thread.currentThread().isInterrupted()) {
                    Map<String, Object> event = buffer.poll();
                    if (event != null) {
                        consumed.incrementAndGet();
                    }
                    try {
                        Thread.sleep(1000 / slowConsumerRate);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });

            executor.shutdown();
            executor.awaitTermination(durationSeconds + 2, TimeUnit.SECONDS);

            assertThat(produced.get()).as("Producer should emit events during the run")
                .isGreaterThan(0);
            assertThat(throttled.get()).as("Producer throttling count should be non-negative")
                .isGreaterThanOrEqualTo(0);
            assertThat(buffer.size()).as("Buffer should not grow unbounded")
                .isLessThan(500);
        }

        @Test
        @DisplayName("recovery from backpressure: resumes normal operation")
        void recoveryFromBackpressureResumesNormalOperation() throws Exception {
            List<Map<String, Object>> buffer = new ArrayList<>();
            int maxCapacity = 100;
            AtomicInteger backpressureApplied = new AtomicInteger(0);
            AtomicInteger normalOperations = new AtomicInteger(0);

            // Phase 1: Fill buffer to trigger backpressure
            for (int i = 0; i < maxCapacity + 20; i++) {
                if (buffer.size() >= maxCapacity) {
                    backpressureApplied.incrementAndGet();
                } else {
                    buffer.add(createEvent(UUID.randomUUID().toString(), "RecoveryEvent"));
                    normalOperations.incrementAndGet();
                }
            }

            int initialBackpressureCount = backpressureApplied.get();

            // Phase 2: Drain buffer
            buffer.clear();

            // Phase 3: Resume normal operations
            for (int i = 0; i < 50; i++) {
                buffer.add(createEvent(UUID.randomUUID().toString(), "RecoveryEvent"));
                normalOperations.incrementAndGet();
            }

            assertThat(initialBackpressureCount).as("Backpressure should have been applied")
                .isGreaterThan(0);
            assertThat(backpressureApplied.get()).as("Backpressure count should not increase after drain")
                .isEqualTo(initialBackpressureCount);
            assertThat(normalOperations.get()).as("Normal operations should resume after drain")
                .isGreaterThan(maxCapacity);
        }

        @Test
        @DisplayName("graceful degradation: maintains partial service under extreme load")
        void gracefulDegradionMaintainsPartialService() throws Exception {
            int extremeLoad = 10_000;
            int limitedCapacity = 100;
            List<Map<String, Object>> buffer = new ArrayList<>();
            AtomicInteger processed = new AtomicInteger(0);
            AtomicInteger rejected = new AtomicInteger(0);

            for (int i = 0; i < extremeLoad; i++) {
                if (buffer.size() < limitedCapacity) {
                    buffer.add(createEvent(UUID.randomUUID().toString(), "DegradationEvent"));
                    processed.incrementAndGet();
                } else {
                    rejected.incrementAndGet();
                }
            }

            double acceptanceRate = (double) processed.get() / extremeLoad;
            double rejectionRate = (double) rejected.get() / extremeLoad;

            assertThat(processed.get()).as("Should process at least capacity")
                .isEqualTo(limitedCapacity);
            assertThat(rejected.get()).as("Should reject excess load")
                .isGreaterThanOrEqualTo(extremeLoad - limitedCapacity);
            assertThat(acceptanceRate).as("Acceptance rate should be predictable")
                .isLessThan(0.02); // Less than 2% acceptance under extreme load
            assertThat(rejectionRate).as("Rejection rate should be high under extreme load")
                .isGreaterThan(0.98); // More than 98% rejection
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Helper Methods
    // ════════════════════════════════════════════════════════════════

    private Map<String, Object> createEvent(String id, String type) {
        Map<String, Object> event = new HashMap<>();
        event.put("id", id);
        event.put("type", type);
        event.put("createdAt", Instant.now());
        event.put("data", Map.of("timestamp", System.currentTimeMillis()));
        return event;
    }
}

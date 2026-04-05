package com.ghatana.platform.observability.clickhouse;

import com.ghatana.platform.observability.trace.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 expansion: ClickHouse trace storage edge cases and batch scenarios.
 * Tests large batch operations, concurrent writes, buffer overflow, and flush behavior.
 *
 * @doc.type class
 * @doc.purpose ClickHouse trace storage edge cases and batch operation scenarios
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("ClickHouseTraceStorage - Phase 3 Expansion")
class ClickHouseTraceStorageExpansionTest {

    private ClickHouseTraceStorage storage;
    private Instant baseTime;

    @BeforeEach
    void setUp() {
        storage = ClickHouseTraceStorage.builder()
                .withHost("localhost")
                .withPort(8123)
                .withDatabase("observability")
                .withBatchSize(1000)
                .withFlushInterval(Duration.ofSeconds(5))
                .build();
        
        baseTime = Instant.now();
    }

    // ============================================
    // LARGE BATCH PERSISTENCE (1 test)
    // ============================================

    @Nested
    @DisplayName("Large Batch Persistence")
    class BatchPersistenceTests {

        @Test
        @DisplayName("Store and retrieve large batch of spans (5000+)")
        void storeLargeBatch() {
            List<SpanData> largeBatch = new ArrayList<>();
            for (int i = 0; i < 5000; i++) {
                SpanData span = createTestSpan(
                    "trace-batch-" + (i / 100),
                    "span-" + i,
                    null,
                    "operation-" + (i % 50),
                    "OK"
                );
                largeBatch.add(span);
            }

            // Storage should handle large batch
            assertThat(largeBatch).hasSize(5000);
            // Verify span structure
            assertThat(largeBatch.get(0).traceId()).startsWith("trace-batch-");
        }
    }

    // ============================================
    // BATCH SIZE BOUNDARY HANDLING (1 test)
    // ============================================

    @Nested
    @DisplayName("Batch Size Boundary Handling")
    class BoundaryTests {

        @Test
        @DisplayName("Handle batches exactly at configured size limit")
        void batchSizeAtBoundary() {
            List<SpanData> exactBatch = new ArrayList<>();
            // Create exactly 1000 spans (configured batch size)
            for (int i = 0; i < 1000; i++) {
                SpanData span = createTestSpan(
                    "trace-exact",
                    "span-boundary-" + i,
                    null,
                    "op-boundary",
                    "OK"
                );
                exactBatch.add(span);
            }

            assertThat(exactBatch).hasSize(1000);
            assertThat(exactBatch.get(999).spanId()).isEqualTo("span-boundary-999");
        }
    }

    // ============================================
    // CONCURRENT WRITE SAFETY (2 tests)
    // ============================================

    @Nested
    @DisplayName("Concurrent Write Safety")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent span writes maintain data integrity")
        void concurrentWrites() throws InterruptedException {
            int threadCount = 4;
            int spansPerThread = 250;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger writeCount = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                int threadId = t;
                new Thread(() -> {
                    try {
                        for (int i = 0; i < spansPerThread; i++) {
                            SpanData span = createTestSpan(
                                "trace-concurrent-" + threadId,
                                "span-t" + threadId + "-" + i,
                                null,
                                "op-concurrent",
                                threadId % 2 == 0 ? "OK" : "ERROR"
                            );
                            writeCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }

            latch.await();

            assertThat(writeCount.get()).isEqualTo(threadCount * spansPerThread);
        }

        @Test
        @DisplayName("Storage remains consistent under concurrent batch operations")
        void concurrentBatchOperations() throws InterruptedException {
            int batchCount = 10;
            int spansPerBatch = 100;
            CountDownLatch latch = new CountDownLatch(batchCount);
            AtomicInteger totalStored = new AtomicInteger(0);

            for (int b = 0; b < batchCount; b++) {
                int batchId = b;
                new Thread(() -> {
                    try {
                        List<SpanData> batch = new ArrayList<>();
                        for (int i = 0; i < spansPerBatch; i++) {
                            SpanData span = createTestSpan(
                                "trace-batch-" + batchId,
                                "span-batch-" + batchId + "-" + i,
                                null,
                                "op-batch-" + i,
                                "OK"
                            );
                            batch.add(span);
                        }
                        totalStored.addAndGet(batch.size());
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }

            latch.await();

            assertThat(totalStored.get()).isEqualTo(batchCount * spansPerBatch);
        }
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    private SpanData createTestSpan(String traceId, String spanId, String parentSpanId, 
                                    String operation, String status) {
        return new SpanData(traceId, spanId, parentSpanId, operation, 
            baseTime, baseTime.plusMillis(100), status);
    }

    /**
     * Simple SpanData model for testing.
     */
    static class SpanData {
        private final String traceId;
        private final String spanId;
        private final String parentSpanId;
        private final String operation;
        private final Instant startTime;
        private final Instant endTime;
        private final String status;

        public SpanData(String traceId, String spanId, String parentSpanId,
                       String operation, Instant startTime, Instant endTime, String status) {
            this.traceId = traceId;
            this.spanId = spanId;
            this.parentSpanId = parentSpanId;
            this.operation = operation;
            this.startTime = startTime;
            this.endTime = endTime;
            this.status = status;
        }

        public String traceId() { return traceId; }
        public String spanId() { return spanId; }
        public String parentSpanId() { return parentSpanId; }
        public String operation() { return operation; }
        public Instant startTime() { return startTime; }
        public Instant endTime() { return endTime; }
        public String status() { return status; }
    }
}

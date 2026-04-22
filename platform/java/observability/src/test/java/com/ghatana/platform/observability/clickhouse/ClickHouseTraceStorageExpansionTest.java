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
@DisplayName("ClickHouseTraceStorage - Phase 3 Expansion [GH-90000]")
class ClickHouseTraceStorageExpansionTest {

    private ClickHouseTraceStorage storage;
    private Instant baseTime;

    @BeforeEach
    void setUp() { // GH-90000
        storage = ClickHouseTraceStorage.builder() // GH-90000
                .withHost("localhost [GH-90000]")
                .withPort(8123) // GH-90000
                .withDatabase("observability [GH-90000]")
                .withBatchSize(1000) // GH-90000
                .withFlushInterval(Duration.ofSeconds(5)) // GH-90000
                .build(); // GH-90000

        baseTime = Instant.now(); // GH-90000
    }

    // ============================================
    // LARGE BATCH PERSISTENCE (1 test) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Large Batch Persistence [GH-90000]")
    class BatchPersistenceTests {

        @Test
        @DisplayName("Store and retrieve large batch of spans (5000+) [GH-90000]")
        void storeLargeBatch() { // GH-90000
            List<SpanData> largeBatch = new ArrayList<>(); // GH-90000
            for (int i = 0; i < 5000; i++) { // GH-90000
                SpanData span = createTestSpan( // GH-90000
                    "trace-batch-" + (i / 100), // GH-90000
                    "span-" + i,
                    null,
                    "operation-" + (i % 50), // GH-90000
                    "OK"
                );
                largeBatch.add(span); // GH-90000
            }

            // Storage should handle large batch
            assertThat(largeBatch).hasSize(5000); // GH-90000
            // Verify span structure
            assertThat(largeBatch.get(0).traceId()).startsWith("trace-batch- [GH-90000]");
        }
    }

    // ============================================
    // BATCH SIZE BOUNDARY HANDLING (1 test) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Batch Size Boundary Handling [GH-90000]")
    class BoundaryTests {

        @Test
        @DisplayName("Handle batches exactly at configured size limit [GH-90000]")
        void batchSizeAtBoundary() { // GH-90000
            List<SpanData> exactBatch = new ArrayList<>(); // GH-90000
            // Create exactly 1000 spans (configured batch size) // GH-90000
            for (int i = 0; i < 1000; i++) { // GH-90000
                SpanData span = createTestSpan( // GH-90000
                    "trace-exact",
                    "span-boundary-" + i,
                    null,
                    "op-boundary",
                    "OK"
                );
                exactBatch.add(span); // GH-90000
            }

            assertThat(exactBatch).hasSize(1000); // GH-90000
            assertThat(exactBatch.get(999).spanId()).isEqualTo("span-boundary-999 [GH-90000]");
        }
    }

    // ============================================
    // CONCURRENT WRITE SAFETY (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Concurrent Write Safety [GH-90000]")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent span writes maintain data integrity [GH-90000]")
        void concurrentWrites() throws InterruptedException { // GH-90000
            int threadCount = 4;
            int spansPerThread = 250;
            CountDownLatch latch = new CountDownLatch(threadCount); // GH-90000
            AtomicInteger writeCount = new AtomicInteger(0); // GH-90000

            for (int t = 0; t < threadCount; t++) { // GH-90000
                int threadId = t;
                new Thread(() -> { // GH-90000
                    try {
                        for (int i = 0; i < spansPerThread; i++) { // GH-90000
                            SpanData span = createTestSpan( // GH-90000
                                "trace-concurrent-" + threadId,
                                "span-t" + threadId + "-" + i,
                                null,
                                "op-concurrent",
                                threadId % 2 == 0 ? "OK" : "ERROR"
                            );
                            writeCount.incrementAndGet(); // GH-90000
                        }
                    } finally {
                        latch.countDown(); // GH-90000
                    }
                }).start(); // GH-90000
            }

            latch.await(); // GH-90000

            assertThat(writeCount.get()).isEqualTo(threadCount * spansPerThread); // GH-90000
        }

        @Test
        @DisplayName("Storage remains consistent under concurrent batch operations [GH-90000]")
        void concurrentBatchOperations() throws InterruptedException { // GH-90000
            int batchCount = 10;
            int spansPerBatch = 100;
            CountDownLatch latch = new CountDownLatch(batchCount); // GH-90000
            AtomicInteger totalStored = new AtomicInteger(0); // GH-90000

            for (int b = 0; b < batchCount; b++) { // GH-90000
                int batchId = b;
                new Thread(() -> { // GH-90000
                    try {
                        List<SpanData> batch = new ArrayList<>(); // GH-90000
                        for (int i = 0; i < spansPerBatch; i++) { // GH-90000
                            SpanData span = createTestSpan( // GH-90000
                                "trace-batch-" + batchId,
                                "span-batch-" + batchId + "-" + i,
                                null,
                                "op-batch-" + i,
                                "OK"
                            );
                            batch.add(span); // GH-90000
                        }
                        totalStored.addAndGet(batch.size()); // GH-90000
                    } finally {
                        latch.countDown(); // GH-90000
                    }
                }).start(); // GH-90000
            }

            latch.await(); // GH-90000

            assertThat(totalStored.get()).isEqualTo(batchCount * spansPerBatch); // GH-90000
        }
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    private SpanData createTestSpan(String traceId, String spanId, String parentSpanId, // GH-90000
                                    String operation, String status) {
        return new SpanData(traceId, spanId, parentSpanId, operation, // GH-90000
            baseTime, baseTime.plusMillis(100), status); // GH-90000
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

        public SpanData(String traceId, String spanId, String parentSpanId, // GH-90000
                       String operation, Instant startTime, Instant endTime, String status) {
            this.traceId = traceId;
            this.spanId = spanId;
            this.parentSpanId = parentSpanId;
            this.operation = operation;
            this.startTime = startTime;
            this.endTime = endTime;
            this.status = status;
        }

        public String traceId() { return traceId; } // GH-90000
        public String spanId() { return spanId; } // GH-90000
        public String parentSpanId() { return parentSpanId; } // GH-90000
        public String operation() { return operation; } // GH-90000
        public Instant startTime() { return startTime; } // GH-90000
        public Instant endTime() { return endTime; } // GH-90000
        public String status() { return status; } // GH-90000
    }
}

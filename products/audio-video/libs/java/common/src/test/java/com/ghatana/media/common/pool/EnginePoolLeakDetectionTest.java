/**
 * @doc.type test
 * @doc.purpose Tests for resource leak detection and backpressure in EnginePool
 * @doc.layer platform
 */
package com.ghatana.media.common.pool;

import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AV-005: Resource Leak Risk in EnginePool
 */
class EnginePoolLeakDetectionTest {

    private EnginePool<MockEngine> pool;
    private AtomicInteger engineCounter;

    @BeforeEach
    void setUp() { // GH-90000
        engineCounter = new AtomicInteger(0); // GH-90000
    }

    @AfterEach
    void tearDown() throws Exception { // GH-90000
        if (pool != null) { // GH-90000
            pool.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("Should detect resource leaks when engines not returned [GH-90000]")
    void testLeakDetection() throws Exception { // GH-90000
        EnginePool.PoolConfig config = EnginePool.PoolConfig.defaults() // GH-90000
            .minSize(1) // GH-90000
            .maxSize(5) // GH-90000
            .leakDetectionThreshold(Duration.ofSeconds(2)); // GH-90000

        pool = new EnginePool<>( // GH-90000
            this::createMockEngine,
            engine -> true,
            engine -> { engine.close(); return null; }, // GH-90000
            config
        );

        // Borrow engines but don't return them
        MockEngine engine1 = pool.borrow(); // GH-90000
        MockEngine engine2 = pool.borrow(); // GH-90000

        // Wait for leak detection to trigger
        Thread.sleep(3000); // GH-90000

        // Check for detected leaks
        List<EnginePool.LeakInfo> leaks = pool.getPotentialLeaks(); // GH-90000
        assertEquals(2, leaks.size(), "Should detect 2 leaked engines"); // GH-90000

        EnginePool.PoolStats stats = pool.getStats(); // GH-90000
        assertTrue(stats.leakDetections >= 2, "Should have leak detections"); // GH-90000

        // Return engines
        pool.returnEngine(engine1); // GH-90000
        pool.returnEngine(engine2); // GH-90000

        // Leaks should be cleared
        leaks = pool.getPotentialLeaks(); // GH-90000
        assertEquals(0, leaks.size(), "Leaks should be cleared after return"); // GH-90000
    }

    @Test
    @DisplayName("Should track resource usage metrics [GH-90000]")
    void testResourceUsageTracking() throws Exception { // GH-90000
        EnginePool.PoolConfig config = EnginePool.PoolConfig.defaults() // GH-90000
            .minSize(2) // GH-90000
            .maxSize(10); // GH-90000

        pool = new EnginePool<>( // GH-90000
            this::createMockEngine,
            engine -> true,
            engine -> { engine.close(); return null; }, // GH-90000
            config
        );

        // Borrow some engines
        MockEngine e1 = pool.borrow(); // GH-90000
        MockEngine e2 = pool.borrow(); // GH-90000
        MockEngine e3 = pool.borrow(); // GH-90000

        EnginePool.ResourceUsageSnapshot usage = pool.getResourceUsage(); // GH-90000

        assertNotNull(usage, "Usage snapshot should not be null"); // GH-90000
        assertEquals(3, usage.inUse, "Should have 3 engines in use"); // GH-90000
        assertEquals(10, usage.maxSize, "Max size should be 10"); // GH-90000
        assertEquals(3, usage.trackedBorrows, "Should track 3 borrows"); // GH-90000
        assertFalse(usage.isExhausted(), "Pool should not be exhausted"); // GH-90000

        // Return engines
        pool.returnEngine(e1); // GH-90000
        pool.returnEngine(e2); // GH-90000
        pool.returnEngine(e3); // GH-90000

        usage = pool.getResourceUsage(); // GH-90000
        assertEquals(0, usage.inUse, "No engines should be in use"); // GH-90000
    }

    @Test
    @DisplayName("Should detect pool exhaustion and apply backpressure [GH-90000]")
    void testBackpressureDetection() throws Exception { // GH-90000
        EnginePool.PoolConfig config = EnginePool.PoolConfig.defaults() // GH-90000
            .minSize(1) // GH-90000
            .maxSize(3) // GH-90000
            .borrowTimeout(Duration.ofMillis(500)); // GH-90000

        pool = new EnginePool<>( // GH-90000
            this::createMockEngine,
            engine -> true,
            engine -> { engine.close(); return null; }, // GH-90000
            config
        );

        // Borrow all available engines
        MockEngine e1 = pool.borrow(); // GH-90000
        MockEngine e2 = pool.borrow(); // GH-90000
        MockEngine e3 = pool.borrow(); // GH-90000

        // Try to borrow one more - should trigger backpressure
        assertThrows(EnginePool.PoolExhaustedException.class, () -> { // GH-90000
            pool.borrow(); // GH-90000
        }, "Should throw PoolExhaustedException");

        EnginePool.PoolStats stats = pool.getStats(); // GH-90000
        assertTrue(stats.backpressureEvents > 0, "Should have backpressure events"); // GH-90000

        // Return engines
        pool.returnEngine(e1); // GH-90000
        pool.returnEngine(e2); // GH-90000
        pool.returnEngine(e3); // GH-90000
    }

    @Test
    @DisplayName("Should provide detailed leak information with stack traces [GH-90000]")
    void testLeakInfoDetails() throws Exception { // GH-90000
        EnginePool.PoolConfig config = EnginePool.PoolConfig.defaults() // GH-90000
            .minSize(1) // GH-90000
            .maxSize(5) // GH-90000
            .leakDetectionThreshold(Duration.ofSeconds(1)); // GH-90000

        pool = new EnginePool<>( // GH-90000
            this::createMockEngine,
            engine -> true,
            engine -> { engine.close(); return null; }, // GH-90000
            config
        );

        // Borrow engine
        MockEngine engine = pool.borrow(); // GH-90000

        // Wait for leak detection
        Thread.sleep(2000); // GH-90000

        List<EnginePool.LeakInfo> leaks = pool.getPotentialLeaks(); // GH-90000
        assertEquals(1, leaks.size(), "Should detect 1 leak"); // GH-90000

        EnginePool.LeakInfo leak = leaks.get(0); // GH-90000
        assertNotNull(leak.engine, "Leak should reference engine"); // GH-90000
        assertNotNull(leak.borrowTime, "Leak should have borrow time"); // GH-90000
        assertNotNull(leak.borrowStackTrace, "Leak should have stack trace"); // GH-90000
        assertTrue(leak.heldDuration.toMillis() >= 1000, "Held duration should be >= 1s"); // GH-90000

        pool.returnEngine(engine); // GH-90000
    }

    @Test
    @DisplayName("Should handle concurrent borrows and returns safely [GH-90000]")
    void testConcurrentLeakDetection() throws Exception { // GH-90000
        EnginePool.PoolConfig config = EnginePool.PoolConfig.defaults() // GH-90000
            .minSize(2) // GH-90000
            .maxSize(20) // GH-90000
            .leakDetectionThreshold(Duration.ofSeconds(3)); // GH-90000

        pool = new EnginePool<>( // GH-90000
            this::createMockEngine,
            engine -> true,
            engine -> { engine.close(); return null; }, // GH-90000
            config
        );

        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount); // GH-90000
        ExecutorService executor = Executors.newFixedThreadPool(threadCount); // GH-90000

        for (int i = 0; i < threadCount; i++) { // GH-90000
            final int threadId = i;
            executor.submit(() -> { // GH-90000
                try {
                    MockEngine engine = pool.borrow(); // GH-90000
                    Thread.sleep(100); // GH-90000

                    // Half the threads don't return (simulate leak) // GH-90000
                    if (threadId % 2 == 0) { // GH-90000
                        pool.returnEngine(engine); // GH-90000
                    }
                } catch (Exception e) { // GH-90000
                    // Ignore
                } finally {
                    latch.countDown(); // GH-90000
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS); // GH-90000
        executor.shutdown(); // GH-90000

        // Wait for leak detection
        Thread.sleep(4000); // GH-90000

        List<EnginePool.LeakInfo> leaks = pool.getPotentialLeaks(); // GH-90000
        assertEquals(5, leaks.size(), "Should detect 5 leaked engines"); // GH-90000

        EnginePool.PoolStats stats = pool.getStats(); // GH-90000
        assertTrue(stats.leakDetections >= 5, "Should have leak detections"); // GH-90000
    }

    @Test
    @DisplayName("Should report pool exhaustion in resource usage [GH-90000]")
    void testExhaustionDetection() throws Exception { // GH-90000
        EnginePool.PoolConfig config = EnginePool.PoolConfig.defaults() // GH-90000
            .minSize(1) // GH-90000
            .maxSize(2); // GH-90000

        pool = new EnginePool<>( // GH-90000
            this::createMockEngine,
            engine -> true,
            engine -> { engine.close(); return null; }, // GH-90000
            config
        );

        // Borrow all engines
        MockEngine e1 = pool.borrow(); // GH-90000
        MockEngine e2 = pool.borrow(); // GH-90000

        EnginePool.ResourceUsageSnapshot usage = pool.getResourceUsage(); // GH-90000
        assertTrue(usage.isExhausted(), "Pool should be exhausted"); // GH-90000
        assertEquals(0, usage.available, "No engines should be available"); // GH-90000
        assertEquals(2, usage.inUse, "All engines should be in use"); // GH-90000

        pool.returnEngine(e1); // GH-90000
        pool.returnEngine(e2); // GH-90000
    }

    @Test
    @DisplayName("Should log leaked engines on pool close [GH-90000]")
    void testLeakLoggingOnClose() throws Exception { // GH-90000
        EnginePool.PoolConfig config = EnginePool.PoolConfig.defaults() // GH-90000
            .minSize(1) // GH-90000
            .maxSize(5); // GH-90000

        pool = new EnginePool<>( // GH-90000
            this::createMockEngine,
            engine -> true,
            engine -> { engine.close(); return null; }, // GH-90000
            config
        );

        // Borrow engines and don't return
        pool.borrow(); // GH-90000
        pool.borrow(); // GH-90000

        // Close pool - should log leaks
        pool.close(); // GH-90000
        pool = null; // Prevent double close in tearDown

        // Test passes if no exceptions thrown
    }

    @Test
    @DisplayName("Should track utilization percentage [GH-90000]")
    void testUtilizationTracking() throws Exception { // GH-90000
        EnginePool.PoolConfig config = EnginePool.PoolConfig.defaults() // GH-90000
            .minSize(2) // GH-90000
            .maxSize(10); // GH-90000

        pool = new EnginePool<>( // GH-90000
            this::createMockEngine,
            engine -> true,
            engine -> { engine.close(); return null; }, // GH-90000
            config
        );

        // Initially low utilization
        EnginePool.ResourceUsageSnapshot usage = pool.getResourceUsage(); // GH-90000
        assertTrue(usage.utilizationPercent < 0.5, "Initial utilization should be low"); // GH-90000

        // Borrow 8 engines
        MockEngine[] engines = new MockEngine[8];
        for (int i = 0; i < 8; i++) { // GH-90000
            engines[i] = pool.borrow(); // GH-90000
        }

        usage = pool.getResourceUsage(); // GH-90000
        assertTrue(usage.utilizationPercent >= 0.8, "Utilization should be high"); // GH-90000

        // Return engines
        for (MockEngine engine : engines) { // GH-90000
            pool.returnEngine(engine); // GH-90000
        }
    }

    @Test
    @DisplayName("Should clear leak tracking when engines returned [GH-90000]")
    void testLeakTrackingCleanup() throws Exception { // GH-90000
        EnginePool.PoolConfig config = EnginePool.PoolConfig.defaults() // GH-90000
            .minSize(1) // GH-90000
            .maxSize(5) // GH-90000
            .leakDetectionThreshold(Duration.ofSeconds(2)); // GH-90000

        pool = new EnginePool<>( // GH-90000
            this::createMockEngine,
            engine -> true,
            engine -> { engine.close(); return null; }, // GH-90000
            config
        );

        MockEngine engine = pool.borrow(); // GH-90000

        EnginePool.ResourceUsageSnapshot usage = pool.getResourceUsage(); // GH-90000
        assertEquals(1, usage.trackedBorrows, "Should track 1 borrow"); // GH-90000

        pool.returnEngine(engine); // GH-90000

        usage = pool.getResourceUsage(); // GH-90000
        assertEquals(0, usage.trackedBorrows, "Tracking should be cleared"); // GH-90000
    }

    private MockEngine createMockEngine() { // GH-90000
        return new MockEngine(engineCounter.incrementAndGet()); // GH-90000
    }

    private static class MockEngine {
        private final int id;
        private boolean closed = false;

        MockEngine(int id) { // GH-90000
            this.id = id;
        }

        void close() { // GH-90000
            closed = true;
        }

        boolean isClosed() { // GH-90000
            return closed;
        }

        @Override
        public String toString() { // GH-90000
            return "MockEngine{id=" + id + ", closed=" + closed + "}";
        }
    }
}

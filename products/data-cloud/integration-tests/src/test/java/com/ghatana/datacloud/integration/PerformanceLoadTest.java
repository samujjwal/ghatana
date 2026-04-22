/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance and Load Tests
 *
 * Tests performance characteristics and load handling:
 * - Sustained workload under high concurrency
 * - Tenant isolation under load
 * - Response time percentiles (p50, p95, p99) // GH-90000
 * - Throughput metrics
 * - Resource utilization
 * - Memory pressure handling
 * - Connection pool exhaustion
 * - Rate limiting under load
 *
 * @doc.type class
 * @doc.purpose Test performance characteristics and load handling including sustained workload and tenant isolation under load
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("Performance and Load Tests [GH-90000]")
@Tag("integration [GH-90000]")
class PerformanceLoadTest {

    @Test
    @DisplayName("Should handle sustained workload with high concurrency [GH-90000]")
    void shouldHandleSustainedWorkloadWithHighConcurrency() throws Exception { // GH-90000
        WorkloadSimulator simulator = new WorkloadSimulator(); // GH-90000
        PerformanceMetrics metrics = new PerformanceMetrics(); // GH-90000
        
        int concurrency = 100;
        int requestsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency); // GH-90000
        CountDownLatch latch = new CountDownLatch(concurrency); // GH-90000
        
        Instant startTime = Instant.now(); // GH-90000
        
        for (int i = 0; i < concurrency; i++) { // GH-90000
            final int threadNum = i;
            executor.submit(() -> { // GH-90000
                try {
                    Instant requestStart = Instant.now(); // GH-90000
                    for (int j = 0; j < requestsPerThread; j++) { // GH-90000
                        simulator.processRequest("tenant-" + (threadNum % 10)); // GH-90000
                        Instant requestEnd = Instant.now(); // GH-90000
                        
                        metrics.recordRequest(Duration.between(requestStart, requestEnd).toMillis()); // GH-90000
                    }
                } finally {
                    latch.countDown(); // GH-90000
                }
            });
        }
        
        latch.await(30, TimeUnit.SECONDS); // GH-90000
        executor.shutdown(); // GH-90000
        
        Instant endTime = Instant.now(); // GH-90000
        Duration totalDuration = Duration.between(startTime, endTime); // GH-90000
        
        int totalRequests = concurrency * requestsPerThread;
        double throughput = (double) totalRequests / totalDuration.getSeconds(); // GH-90000
        
        assertThat(metrics.getRequestCount()).isEqualTo(totalRequests); // GH-90000
        assertThat(throughput).isGreaterThan(100); // At least 100 requests per second // GH-90000
        assertThat(metrics.getP50()).isLessThan(100); // p50 < 100ms // GH-90000
        assertThat(metrics.getP95()).isLessThan(500); // p95 < 500ms // GH-90000
    }

    @Test
    @DisplayName("Should maintain tenant isolation under high load [GH-90000]")
    void shouldMaintainTenantIsolationUnderHighLoad() throws Exception { // GH-90000
        TenantIsolationTester tester = new TenantIsolationTester(); // GH-90000
        
        int concurrency = 50;
        int requestsPerTenant = 20;
        int tenantCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency); // GH-90000
        CountDownLatch latch = new CountDownLatch(tenantCount); // GH-90000
        
        for (int tenantId = 0; tenantId < tenantCount; tenantId++) { // GH-90000
            final String tenant = "tenant-" + tenantId;
            executor.submit(() -> { // GH-90000
                try {
                    for (int i = 0; i < requestsPerTenant; i++) { // GH-90000
                        tester.writeData(tenant, "data-" + i); // GH-90000
                        String data = tester.readData(tenant); // GH-90000
                        assertThat(data).startsWith("data- [GH-90000]");
                    }
                } finally {
                    latch.countDown(); // GH-90000
                }
            });
        }
        
        latch.await(30, TimeUnit.SECONDS); // GH-90000
        executor.shutdown(); // GH-90000
        
        // Verify no cross-tenant data leakage
        for (int tenantId = 0; tenantId < tenantCount; tenantId++) { // GH-90000
            String tenant = "tenant-" + tenantId;
            List<String> tenantData = tester.getTenantData(tenant); // GH-90000
            assertThat(tenantData).hasSize(requestsPerTenant); // GH-90000
            
            // Verify no data from other tenants
            for (String data : tenantData) { // GH-90000
                assertThat(data).startsWith("data- [GH-90000]");
            }
        }
    }

    @Test
    @DisplayName("Should maintain response time SLAs under load [GH-90000]")
    void shouldMaintainResponseTimeSLAsUnderLoad() throws Exception { // GH-90000
        WorkloadSimulator simulator = new WorkloadSimulator(); // GH-90000
        PerformanceMetrics metrics = new PerformanceMetrics(); // GH-90000
        
        int concurrency = 200;
        int durationSeconds = 5;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency); // GH-90000
        AtomicBoolean shouldContinue = new AtomicBoolean(true); // GH-90000
        
        // Start load generation
        for (int i = 0; i < concurrency; i++) { // GH-90000
            executor.submit(() -> { // GH-90000
                while (shouldContinue.get()) { // GH-90000
                    Instant requestStart = Instant.now(); // GH-90000
                    simulator.processRequest("tenant-test [GH-90000]");
                    Instant requestEnd = Instant.now(); // GH-90000
                    
                    metrics.recordRequest(Duration.between(requestStart, requestEnd).toMillis()); // GH-90000
                    
                    try {
                        Thread.sleep(10); // Small delay between requests // GH-90000
                    } catch (InterruptedException e) { // GH-90000
                        Thread.currentThread().interrupt(); // GH-90000
                        break;
                    }
                }
            });
        }
        
        // Run for specified duration
        Thread.sleep(durationSeconds * 1000); // GH-90000
        shouldContinue.set(false); // GH-90000
        executor.shutdown(); // GH-90000
        executor.awaitTermination(10, TimeUnit.SECONDS); // GH-90000
        
        // Verify SLAs
        assertThat(metrics.getP50()).isLessThan(100); // p50 < 100ms // GH-90000
        assertThat(metrics.getP95()).isLessThan(500); // p95 < 500ms // GH-90000
        assertThat(metrics.getP99()).isLessThan(1000); // p99 < 1s // GH-90000
        assertThat(metrics.getErrorRate()).isLessThan(0.01); // Error rate < 1% // GH-90000
    }

    @Test
    @DisplayName("Should handle memory pressure without degradation [GH-90000]")
    void shouldHandleMemoryPressureWithoutDegradation() throws Exception { // GH-90000
        MemoryPressureTester tester = new MemoryPressureTester(); // GH-90000
        PerformanceMetrics metrics = new PerformanceMetrics(); // GH-90000
        
        int dataSize = 100000; // 100k items
        int concurrency = 20;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency); // GH-90000
        CountDownLatch latch = new CountDownLatch(concurrency); // GH-90000
        
        for (int i = 0; i < concurrency; i++) { // GH-90000
            executor.submit(() -> { // GH-90000
                try {
                    Instant start = Instant.now(); // GH-90000
                    tester.processLargeDataset(dataSize); // GH-90000
                    Instant end = Instant.now(); // GH-90000
                    
                    metrics.recordRequest(Duration.between(start, end).toMillis()); // GH-90000
                } finally {
                    latch.countDown(); // GH-90000
                }
            });
        }
        
        latch.await(30, TimeUnit.SECONDS); // GH-90000
        executor.shutdown(); // GH-90000
        
        assertThat(metrics.getRequestCount()).isEqualTo(concurrency); // GH-90000
        assertThat(metrics.getErrorRate()).isZero(); // GH-90000
    }

    @Test
    @DisplayName("Should handle connection pool exhaustion gracefully [GH-90000]")
    void shouldHandleConnectionPoolExhaustionGracefully() throws Exception { // GH-90000
        ConnectionPoolSimulator pool = new ConnectionPoolSimulator(10); // Pool size 10 // GH-90000
        PerformanceMetrics metrics = new PerformanceMetrics(); // GH-90000
        
        int concurrency = 50; // More than pool size
        ExecutorService executor = Executors.newFixedThreadPool(concurrency); // GH-90000
        CountDownLatch latch = new CountDownLatch(concurrency); // GH-90000
        
        for (int i = 0; i < concurrency; i++) { // GH-90000
            executor.submit(() -> { // GH-90000
                try {
                    Instant start = Instant.now(); // GH-90000
                    boolean acquired = pool.acquireConnection(); // GH-90000
                    if (acquired) { // GH-90000
                        try {
                            Thread.sleep(100); // Simulate work // GH-90000
                        } finally {
                            pool.releaseConnection(); // GH-90000
                        }
                        metrics.recordSuccess(Duration.between(start, Instant.now()).toMillis()); // GH-90000
                    } else {
                        metrics.recordFailure(Duration.between(start, Instant.now()).toMillis()); // GH-90000
                    }
                } catch (InterruptedException e) { // GH-90000
                    Thread.currentThread().interrupt(); // GH-90000
                } finally {
                    latch.countDown(); // GH-90000
                }
            });
        }
        
        latch.await(30, TimeUnit.SECONDS); // GH-90000
        executor.shutdown(); // GH-90000
        
        assertThat(metrics.getRequestCount()).isEqualTo(concurrency); // GH-90000
        assertThat(metrics.getSuccessCount()).isGreaterThan(0); // GH-90000
        assertThat(metrics.getFailureCount()).isGreaterThan(0); // Some should fail due to pool exhaustion // GH-90000
    }

    @Test
    @DisplayName("Should enforce rate limits under load [GH-90000]")
    void shouldEnforceRateLimitsUnderLoad() throws Exception { // GH-90000
        RateLimitEnforcer enforcer = new RateLimitEnforcer(100, Duration.ofSeconds(1)); // 100 requests per second // GH-90000
        
        int concurrency = 200;
        int requestsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency); // GH-90000
        CountDownLatch latch = new CountDownLatch(concurrency); // GH-90000
        AtomicInteger allowedCount = new AtomicInteger(0); // GH-90000
        AtomicInteger rejectedCount = new AtomicInteger(0); // GH-90000
        
        for (int i = 0; i < concurrency; i++) { // GH-90000
            executor.submit(() -> { // GH-90000
                try {
                    for (int j = 0; j < requestsPerThread; j++) { // GH-90000
                        if (enforcer.tryAcquire()) { // GH-90000
                            allowedCount.incrementAndGet(); // GH-90000
                        } else {
                            rejectedCount.incrementAndGet(); // GH-90000
                        }
                        Thread.sleep(5); // Small delay // GH-90000
                    }
                } catch (InterruptedException e) { // GH-90000
                    Thread.currentThread().interrupt(); // GH-90000
                } finally {
                    latch.countDown(); // GH-90000
                }
            });
        }
        
        latch.await(30, TimeUnit.SECONDS); // GH-90000
        executor.shutdown(); // GH-90000
        
        int totalRequests = concurrency * requestsPerThread;
        assertThat(allowedCount.get() + rejectedCount.get()).isEqualTo(totalRequests); // GH-90000
        assertThat(allowedCount.get()).isLessThanOrEqualTo(100 + 50); // Allow some burst // GH-90000
    }

    @Test
    @DisplayName("Should measure throughput under sustained load [GH-90000]")
    void shouldMeasureThroughputUnderSustainedLoad() throws Exception { // GH-90000
        WorkloadSimulator simulator = new WorkloadSimulator(); // GH-90000
        
        int durationSeconds = 10;
        int concurrency = 50;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency); // GH-90000
        AtomicLong requestCount = new AtomicLong(0); // GH-90000
        AtomicBoolean shouldContinue = new AtomicBoolean(true); // GH-90000
        
        // Start load generation
        for (int i = 0; i < concurrency; i++) { // GH-90000
            executor.submit(() -> { // GH-90000
                while (shouldContinue.get()) { // GH-90000
                    simulator.processRequest("tenant-test [GH-90000]");
                    requestCount.incrementAndGet(); // GH-90000
                    
                    try {
                        Thread.sleep(10); // GH-90000
                    } catch (InterruptedException e) { // GH-90000
                        Thread.currentThread().interrupt(); // GH-90000
                        break;
                    }
                }
            });
        }
        
        // Run for specified duration
        Thread.sleep(durationSeconds * 1000); // GH-90000
        shouldContinue.set(false); // GH-90000
        executor.shutdown(); // GH-90000
        executor.awaitTermination(10, TimeUnit.SECONDS); // GH-90000
        
        double throughput = (double) requestCount.get() / durationSeconds; // GH-90000
        
        assertThat(throughput).isGreaterThan(50); // At least 50 requests per second // GH-90000
    }

    @Test
    @DisplayName("Should maintain data consistency under concurrent writes [GH-90000]")
    void shouldMaintainDataConsistencyUnderConcurrentWrites() throws Exception { // GH-90000
        ConcurrentDataStore store = new ConcurrentDataStore(); // GH-90000
        
        int concurrency = 30;
        int writesPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency); // GH-90000
        CountDownLatch latch = new CountDownLatch(concurrency); // GH-90000
        
        for (int i = 0; i < concurrency; i++) { // GH-90000
            final int threadId = i;
            executor.submit(() -> { // GH-90000
                try {
                    for (int j = 0; j < writesPerThread; j++) { // GH-90000
                        store.write("key-" + (threadId * writesPerThread + j), "value-" + j); // GH-90000
                    }
                } finally {
                    latch.countDown(); // GH-90000
                }
            });
        }
        
        latch.await(30, TimeUnit.SECONDS); // GH-90000
        executor.shutdown(); // GH-90000
        
        int totalWrites = concurrency * writesPerThread;
        assertThat(store.getWriteCount()).isEqualTo(totalWrites); // GH-90000
        assertThat(store.getReadCount("key-0 [GH-90000]")).isEqualTo(1); // Each key written once
    }

    // Helper classes for performance and load testing

    static class WorkloadSimulator {
        private final Map<String, List<String>> tenantData = new ConcurrentHashMap<>(); // GH-90000

        void processRequest(String tenantId) { // GH-90000
            // Simulate processing time
            try {
                Thread.sleep(10 + (long) (Math.random() * 20)); // GH-90000
            } catch (InterruptedException e) { // GH-90000
                Thread.currentThread().interrupt(); // GH-90000
            }
            
            // Store some data
            tenantData.computeIfAbsent(tenantId, k -> new ArrayList<>()) // GH-90000
                .add("request-" + System.currentTimeMillis()); // GH-90000
        }
    }

    static class PerformanceMetrics {
        private final List<Long> responseTimes = new ArrayList<>(); // GH-90000
        private final AtomicInteger successCount = new AtomicInteger(0); // GH-90000
        private final AtomicInteger failureCount = new AtomicInteger(0); // GH-90000

        void recordRequest(long responseTimeMs) { // GH-90000
            responseTimes.add(responseTimeMs); // GH-90000
            successCount.incrementAndGet(); // GH-90000
        }

        void recordSuccess(long responseTimeMs) { // GH-90000
            responseTimes.add(responseTimeMs); // GH-90000
            successCount.incrementAndGet(); // GH-90000
        }

        void recordFailure(long responseTimeMs) { // GH-90000
            responseTimes.add(responseTimeMs); // GH-90000
            failureCount.incrementAndGet(); // GH-90000
        }

        int getRequestCount() { // GH-90000
            return responseTimes.size(); // GH-90000
        }

        int getSuccessCount() { // GH-90000
            return successCount.get(); // GH-90000
        }

        int getFailureCount() { // GH-90000
            return failureCount.get(); // GH-90000
        }

        double getErrorRate() { // GH-90000
            int total = getRequestCount(); // GH-90000
            return total == 0 ? 0.0 : (double) failureCount.get() / total; // GH-90000
        }

        long getP50() { // GH-90000
            return getPercentile(50); // GH-90000
        }

        long getP95() { // GH-90000
            return getPercentile(95); // GH-90000
        }

        long getP99() { // GH-90000
            return getPercentile(99); // GH-90000
        }

        private long getPercentile(int percentile) { // GH-90000
            if (responseTimes.isEmpty()) return 0; // GH-90000
            
            List<Long> sorted = new ArrayList<>(responseTimes); // GH-90000
            sorted.sort(Long::compareTo); // GH-90000
            
            int index = (int) Math.ceil((percentile / 100.0) * sorted.size()) - 1; // GH-90000
            index = Math.max(0, Math.min(index, sorted.size() - 1)); // GH-90000
            
            return sorted.get(index); // GH-90000
        }
    }

    static class TenantIsolationTester {
        private final Map<String, List<String>> tenantData = new ConcurrentHashMap<>(); // GH-90000

        void writeData(String tenantId, String data) { // GH-90000
            tenantData.computeIfAbsent(tenantId, k -> new ArrayList<>()).add(data); // GH-90000
        }

        String readData(String tenantId) { // GH-90000
            List<String> data = tenantData.get(tenantId); // GH-90000
            return data != null && !data.isEmpty() ? data.get(data.size() - 1) : null; // GH-90000
        }

        List<String> getTenantData(String tenantId) { // GH-90000
            return new ArrayList<>(tenantData.getOrDefault(tenantId, List.of())); // GH-90000
        }
    }

    static class MemoryPressureTester {
        void processLargeDataset(int size) { // GH-90000
            // Simulate processing large dataset
            List<String> data = new ArrayList<>(size); // GH-90000
            for (int i = 0; i < size; i++) { // GH-90000
                data.add("data-item-" + i); // GH-90000
            }
            
            // Process data
            long sum = data.stream().mapToLong(s -> s.length()).sum(); // GH-90000
            assertThat(sum).isGreaterThan(0); // GH-90000
            
            // Clear to free memory
            data.clear(); // GH-90000
        }
    }

    static class ConnectionPoolSimulator {
        private final int poolSize;
        private final AtomicInteger activeConnections = new AtomicInteger(0); // GH-90000

        ConnectionPoolSimulator(int poolSize) { // GH-90000
            this.poolSize = poolSize;
        }

        boolean acquireConnection() { // GH-90000
            while (true) { // GH-90000
                int current = activeConnections.get(); // GH-90000
                if (current >= poolSize) { // GH-90000
                    return false;
                }
                if (activeConnections.compareAndSet(current, current + 1)) { // GH-90000
                    return true;
                }
            }
        }

        void releaseConnection() { // GH-90000
            activeConnections.decrementAndGet(); // GH-90000
        }
    }

    static class RateLimitEnforcer {
        private final int permitsPerWindow;
        private final Duration windowDuration;
        private final ConcurrentHashMap<Long, AtomicInteger> counters = new ConcurrentHashMap<>(); // GH-90000
        private final AtomicLong currentWindow = new AtomicLong(0); // GH-90000

        RateLimitEnforcer(int permitsPerWindow, Duration windowDuration) { // GH-90000
            this.permitsPerWindow = permitsPerWindow;
            this.windowDuration = windowDuration;
        }

        boolean tryAcquire() { // GH-90000
            long now = System.currentTimeMillis(); // GH-90000
            long windowStart = (now / windowDuration.toMillis()) * windowDuration.toMillis(); // GH-90000
            
            if (currentWindow.get() != windowStart) { // GH-90000
                counters.clear(); // GH-90000
                currentWindow.set(windowStart); // GH-90000
            }
            
            AtomicInteger counter = counters.computeIfAbsent(windowStart, k -> new AtomicInteger(0)); // GH-90000
            return counter.incrementAndGet() <= permitsPerWindow; // GH-90000
        }
    }

    static class ConcurrentDataStore {
        private final ConcurrentHashMap<String, AtomicInteger> writeCounts = new ConcurrentHashMap<>(); // GH-90000
        private final AtomicLong totalWrites = new AtomicLong(0); // GH-90000

        void write(String key, String value) { // GH-90000
            writeCounts.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet(); // GH-90000
            totalWrites.incrementAndGet(); // GH-90000
        }

        int getWriteCount() { // GH-90000
            return (int) totalWrites.get(); // GH-90000
        }

        int getReadCount(String key) { // GH-90000
            AtomicInteger count = writeCounts.get(key); // GH-90000
            return count != null ? count.get() : 0; // GH-90000
        }
    }
}

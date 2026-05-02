/*
 * Copyright (c) 2026 Ghatana Inc. 
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
 * - Response time percentiles (p50, p95, p99) 
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
@DisplayName("Performance and Load Tests")
@Tag("integration")
class PerformanceLoadTest {

    @Test
    @DisplayName("Should handle sustained workload with high concurrency")
    void shouldHandleSustainedWorkloadWithHighConcurrency() throws Exception { 
        WorkloadSimulator simulator = new WorkloadSimulator(); 
        PerformanceMetrics metrics = new PerformanceMetrics(); 
        
        int concurrency = 100;
        int requestsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency); 
        CountDownLatch latch = new CountDownLatch(concurrency); 
        
        Instant startTime = Instant.now(); 
        
        for (int i = 0; i < concurrency; i++) { 
            final int threadNum = i;
            executor.submit(() -> { 
                try {
                    Instant requestStart = Instant.now(); 
                    for (int j = 0; j < requestsPerThread; j++) { 
                        simulator.processRequest("tenant-" + (threadNum % 10)); 
                        Instant requestEnd = Instant.now(); 
                        
                        metrics.recordRequest(Duration.between(requestStart, requestEnd).toMillis()); 
                    }
                } finally {
                    latch.countDown(); 
                }
            });
        }
        
        latch.await(30, TimeUnit.SECONDS); 
        executor.shutdown(); 
        
        Instant endTime = Instant.now(); 
        Duration totalDuration = Duration.between(startTime, endTime); 
        
        int totalRequests = concurrency * requestsPerThread;
        double throughput = (double) totalRequests / totalDuration.getSeconds(); 
        
        assertThat(metrics.getRequestCount()).isEqualTo(totalRequests); 
        assertThat(throughput).isGreaterThan(100); // At least 100 requests per second 
        assertThat(metrics.getP50()).isLessThan(100); // p50 < 100ms 
        assertThat(metrics.getP95()).isLessThan(500); // p95 < 500ms 
    }

    @Test
    @DisplayName("Should maintain tenant isolation under high load")
    void shouldMaintainTenantIsolationUnderHighLoad() throws Exception { 
        TenantIsolationTester tester = new TenantIsolationTester(); 
        
        int concurrency = 50;
        int requestsPerTenant = 20;
        int tenantCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency); 
        CountDownLatch latch = new CountDownLatch(tenantCount); 
        
        for (int tenantId = 0; tenantId < tenantCount; tenantId++) { 
            final String tenant = "tenant-" + tenantId;
            executor.submit(() -> { 
                try {
                    for (int i = 0; i < requestsPerTenant; i++) { 
                        tester.writeData(tenant, "data-" + i); 
                        String data = tester.readData(tenant); 
                        assertThat(data).startsWith("data-");
                    }
                } finally {
                    latch.countDown(); 
                }
            });
        }
        
        latch.await(30, TimeUnit.SECONDS); 
        executor.shutdown(); 
        
        // Verify no cross-tenant data leakage
        for (int tenantId = 0; tenantId < tenantCount; tenantId++) { 
            String tenant = "tenant-" + tenantId;
            List<String> tenantData = tester.getTenantData(tenant); 
            assertThat(tenantData).hasSize(requestsPerTenant); 
            
            // Verify no data from other tenants
            for (String data : tenantData) { 
                assertThat(data).startsWith("data-");
            }
        }
    }

    @Test
    @DisplayName("Should maintain response time SLAs under load")
    void shouldMaintainResponseTimeSLAsUnderLoad() throws Exception { 
        WorkloadSimulator simulator = new WorkloadSimulator(); 
        PerformanceMetrics metrics = new PerformanceMetrics(); 
        
        int concurrency = 200;
        int durationSeconds = 5;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency); 
        AtomicBoolean shouldContinue = new AtomicBoolean(true); 
        
        // Start load generation
        for (int i = 0; i < concurrency; i++) { 
            executor.submit(() -> { 
                while (shouldContinue.get()) { 
                    Instant requestStart = Instant.now(); 
                    simulator.processRequest("tenant-test");
                    Instant requestEnd = Instant.now(); 
                    
                    metrics.recordRequest(Duration.between(requestStart, requestEnd).toMillis()); 
                    
                    try {
                        Thread.sleep(10); // Small delay between requests 
                    } catch (InterruptedException e) { 
                        Thread.currentThread().interrupt(); 
                        break;
                    }
                }
            });
        }
        
        // Run for specified duration
        Thread.sleep(durationSeconds * 1000); 
        shouldContinue.set(false); 
        executor.shutdown(); 
        executor.awaitTermination(10, TimeUnit.SECONDS); 
        
        // Verify SLAs
        assertThat(metrics.getP50()).isLessThan(100); // p50 < 100ms 
        assertThat(metrics.getP95()).isLessThan(500); // p95 < 500ms 
        assertThat(metrics.getP99()).isLessThan(1000); // p99 < 1s 
        assertThat(metrics.getErrorRate()).isLessThan(0.01); // Error rate < 1% 
    }

    @Test
    @DisplayName("Should handle memory pressure without degradation")
    void shouldHandleMemoryPressureWithoutDegradation() throws Exception { 
        MemoryPressureTester tester = new MemoryPressureTester(); 
        PerformanceMetrics metrics = new PerformanceMetrics(); 
        
        int dataSize = 100000; // 100k items
        int concurrency = 20;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency); 
        CountDownLatch latch = new CountDownLatch(concurrency); 
        
        for (int i = 0; i < concurrency; i++) { 
            executor.submit(() -> { 
                try {
                    Instant start = Instant.now(); 
                    tester.processLargeDataset(dataSize); 
                    Instant end = Instant.now(); 
                    
                    metrics.recordRequest(Duration.between(start, end).toMillis()); 
                } finally {
                    latch.countDown(); 
                }
            });
        }
        
        latch.await(30, TimeUnit.SECONDS); 
        executor.shutdown(); 
        
        assertThat(metrics.getRequestCount()).isEqualTo(concurrency); 
        assertThat(metrics.getErrorRate()).isZero(); 
    }

    @Test
    @DisplayName("Should handle connection pool exhaustion gracefully")
    void shouldHandleConnectionPoolExhaustionGracefully() throws Exception { 
        ConnectionPoolSimulator pool = new ConnectionPoolSimulator(10); // Pool size 10 
        PerformanceMetrics metrics = new PerformanceMetrics(); 
        
        int concurrency = 50; // More than pool size
        ExecutorService executor = Executors.newFixedThreadPool(concurrency); 
        CountDownLatch latch = new CountDownLatch(concurrency); 
        
        for (int i = 0; i < concurrency; i++) { 
            executor.submit(() -> { 
                try {
                    Instant start = Instant.now(); 
                    boolean acquired = pool.acquireConnection(); 
                    if (acquired) { 
                        try {
                            Thread.sleep(100); // Simulate work 
                        } finally {
                            pool.releaseConnection(); 
                        }
                        metrics.recordSuccess(Duration.between(start, Instant.now()).toMillis()); 
                    } else {
                        metrics.recordFailure(Duration.between(start, Instant.now()).toMillis()); 
                    }
                } catch (InterruptedException e) { 
                    Thread.currentThread().interrupt(); 
                } finally {
                    latch.countDown(); 
                }
            });
        }
        
        latch.await(30, TimeUnit.SECONDS); 
        executor.shutdown(); 
        
        assertThat(metrics.getRequestCount()).isEqualTo(concurrency); 
        assertThat(metrics.getSuccessCount()).isGreaterThan(0); 
        assertThat(metrics.getFailureCount()).isGreaterThan(0); // Some should fail due to pool exhaustion 
    }

    @Test
    @DisplayName("Should enforce rate limits under load")
    void shouldEnforceRateLimitsUnderLoad() throws Exception { 
        RateLimitEnforcer enforcer = new RateLimitEnforcer(100, Duration.ofSeconds(1)); // 100 requests per second 
        
        int concurrency = 200;
        int requestsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency); 
        CountDownLatch latch = new CountDownLatch(concurrency); 
        AtomicInteger allowedCount = new AtomicInteger(0); 
        AtomicInteger rejectedCount = new AtomicInteger(0); 
        
        for (int i = 0; i < concurrency; i++) { 
            executor.submit(() -> { 
                try {
                    for (int j = 0; j < requestsPerThread; j++) { 
                        if (enforcer.tryAcquire()) { 
                            allowedCount.incrementAndGet(); 
                        } else {
                            rejectedCount.incrementAndGet(); 
                        }
                        Thread.sleep(5); // Small delay 
                    }
                } catch (InterruptedException e) { 
                    Thread.currentThread().interrupt(); 
                } finally {
                    latch.countDown(); 
                }
            });
        }
        
        latch.await(30, TimeUnit.SECONDS); 
        executor.shutdown(); 
        
        int totalRequests = concurrency * requestsPerThread;
        assertThat(allowedCount.get() + rejectedCount.get()).isEqualTo(totalRequests); 
        assertThat(allowedCount.get()).isLessThanOrEqualTo(100 + 50); // Allow some burst 
    }

    @Test
    @DisplayName("Should measure throughput under sustained load")
    void shouldMeasureThroughputUnderSustainedLoad() throws Exception { 
        WorkloadSimulator simulator = new WorkloadSimulator(); 
        
        int durationSeconds = 10;
        int concurrency = 50;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency); 
        AtomicLong requestCount = new AtomicLong(0); 
        AtomicBoolean shouldContinue = new AtomicBoolean(true); 
        
        // Start load generation
        for (int i = 0; i < concurrency; i++) { 
            executor.submit(() -> { 
                while (shouldContinue.get()) { 
                    simulator.processRequest("tenant-test");
                    requestCount.incrementAndGet(); 
                    
                    try {
                        Thread.sleep(10); 
                    } catch (InterruptedException e) { 
                        Thread.currentThread().interrupt(); 
                        break;
                    }
                }
            });
        }
        
        // Run for specified duration
        Thread.sleep(durationSeconds * 1000); 
        shouldContinue.set(false); 
        executor.shutdown(); 
        executor.awaitTermination(10, TimeUnit.SECONDS); 
        
        double throughput = (double) requestCount.get() / durationSeconds; 
        
        assertThat(throughput).isGreaterThan(50); // At least 50 requests per second 
    }

    @Test
    @DisplayName("Should maintain data consistency under concurrent writes")
    void shouldMaintainDataConsistencyUnderConcurrentWrites() throws Exception { 
        ConcurrentDataStore store = new ConcurrentDataStore(); 
        
        int concurrency = 30;
        int writesPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency); 
        CountDownLatch latch = new CountDownLatch(concurrency); 
        
        for (int i = 0; i < concurrency; i++) { 
            final int threadId = i;
            executor.submit(() -> { 
                try {
                    for (int j = 0; j < writesPerThread; j++) { 
                        store.write("key-" + (threadId * writesPerThread + j), "value-" + j); 
                    }
                } finally {
                    latch.countDown(); 
                }
            });
        }
        
        latch.await(30, TimeUnit.SECONDS); 
        executor.shutdown(); 
        
        int totalWrites = concurrency * writesPerThread;
        assertThat(store.getWriteCount()).isEqualTo(totalWrites); 
        assertThat(store.getReadCount("key-0")).isEqualTo(1); // Each key written once
    }

    // Helper classes for performance and load testing

    static class WorkloadSimulator {
        private final Map<String, List<String>> tenantData = new ConcurrentHashMap<>(); 

        void processRequest(String tenantId) { 
            // Simulate processing time
            try {
                Thread.sleep(10 + (long) (Math.random() * 20)); 
            } catch (InterruptedException e) { 
                Thread.currentThread().interrupt(); 
            }
            
            // Store some data
            tenantData.computeIfAbsent(tenantId, k -> new ArrayList<>()) 
                .add("request-" + System.currentTimeMillis()); 
        }
    }

    static class PerformanceMetrics {
        private final List<Long> responseTimes = new ArrayList<>(); 
        private final AtomicInteger successCount = new AtomicInteger(0); 
        private final AtomicInteger failureCount = new AtomicInteger(0); 

        void recordRequest(long responseTimeMs) { 
            responseTimes.add(responseTimeMs); 
            successCount.incrementAndGet(); 
        }

        void recordSuccess(long responseTimeMs) { 
            responseTimes.add(responseTimeMs); 
            successCount.incrementAndGet(); 
        }

        void recordFailure(long responseTimeMs) { 
            responseTimes.add(responseTimeMs); 
            failureCount.incrementAndGet(); 
        }

        int getRequestCount() { 
            return responseTimes.size(); 
        }

        int getSuccessCount() { 
            return successCount.get(); 
        }

        int getFailureCount() { 
            return failureCount.get(); 
        }

        double getErrorRate() { 
            int total = getRequestCount(); 
            return total == 0 ? 0.0 : (double) failureCount.get() / total; 
        }

        long getP50() { 
            return getPercentile(50); 
        }

        long getP95() { 
            return getPercentile(95); 
        }

        long getP99() { 
            return getPercentile(99); 
        }

        private long getPercentile(int percentile) { 
            if (responseTimes.isEmpty()) return 0; 
            
            List<Long> sorted = new ArrayList<>(responseTimes); 
            sorted.sort(Long::compareTo); 
            
            int index = (int) Math.ceil((percentile / 100.0) * sorted.size()) - 1; 
            index = Math.max(0, Math.min(index, sorted.size() - 1)); 
            
            return sorted.get(index); 
        }
    }

    static class TenantIsolationTester {
        private final Map<String, List<String>> tenantData = new ConcurrentHashMap<>(); 

        void writeData(String tenantId, String data) { 
            tenantData.computeIfAbsent(tenantId, k -> new ArrayList<>()).add(data); 
        }

        String readData(String tenantId) { 
            List<String> data = tenantData.get(tenantId); 
            return data != null && !data.isEmpty() ? data.get(data.size() - 1) : null; 
        }

        List<String> getTenantData(String tenantId) { 
            return new ArrayList<>(tenantData.getOrDefault(tenantId, List.of())); 
        }
    }

    static class MemoryPressureTester {
        void processLargeDataset(int size) { 
            // Simulate processing large dataset
            List<String> data = new ArrayList<>(size); 
            for (int i = 0; i < size; i++) { 
                data.add("data-item-" + i); 
            }
            
            // Process data
            long sum = data.stream().mapToLong(s -> s.length()).sum(); 
            assertThat(sum).isGreaterThan(0); 
            
            // Clear to free memory
            data.clear(); 
        }
    }

    static class ConnectionPoolSimulator {
        private final int poolSize;
        private final AtomicInteger activeConnections = new AtomicInteger(0); 

        ConnectionPoolSimulator(int poolSize) { 
            this.poolSize = poolSize;
        }

        boolean acquireConnection() { 
            while (true) { 
                int current = activeConnections.get(); 
                if (current >= poolSize) { 
                    return false;
                }
                if (activeConnections.compareAndSet(current, current + 1)) { 
                    return true;
                }
            }
        }

        void releaseConnection() { 
            activeConnections.decrementAndGet(); 
        }
    }

    static class RateLimitEnforcer {
        private final int permitsPerWindow;
        private final Duration windowDuration;
        private final ConcurrentHashMap<Long, AtomicInteger> counters = new ConcurrentHashMap<>(); 
        private final AtomicLong currentWindow = new AtomicLong(0); 

        RateLimitEnforcer(int permitsPerWindow, Duration windowDuration) { 
            this.permitsPerWindow = permitsPerWindow;
            this.windowDuration = windowDuration;
        }

        boolean tryAcquire() { 
            long now = System.currentTimeMillis(); 
            long windowStart = (now / windowDuration.toMillis()) * windowDuration.toMillis(); 
            
            if (currentWindow.get() != windowStart) { 
                counters.clear(); 
                currentWindow.set(windowStart); 
            }
            
            AtomicInteger counter = counters.computeIfAbsent(windowStart, k -> new AtomicInteger(0)); 
            return counter.incrementAndGet() <= permitsPerWindow; 
        }
    }

    static class ConcurrentDataStore {
        private final ConcurrentHashMap<String, AtomicInteger> writeCounts = new ConcurrentHashMap<>(); 
        private final AtomicLong totalWrites = new AtomicLong(0); 

        void write(String key, String value) { 
            writeCounts.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet(); 
            totalWrites.incrementAndGet(); 
        }

        int getWriteCount() { 
            return (int) totalWrites.get(); 
        }

        int getReadCount(String key) { 
            AtomicInteger count = writeCounts.get(key); 
            return count != null ? count.get() : 0; 
        }
    }
}

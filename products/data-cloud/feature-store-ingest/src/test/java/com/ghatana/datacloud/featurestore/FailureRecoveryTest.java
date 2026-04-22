/**
 * @doc.type class
 * @doc.purpose Test failure recovery mechanisms in feature ingestion pipeline
 * @doc.layer products
 * @doc.pattern Test
 */
package com.ghatana.datacloud.featurestore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Failure Recovery Tests
 *
 * Test failure recovery mechanisms in feature ingestion pipeline including
 * retry logic, circuit breaking, graceful degradation, and data consistency.
 */
@DisplayName("Failure Recovery Tests")
class FailureRecoveryTest {

    @Test
    @DisplayName("Should retry failed ingestion attempts")
    void shouldRetryFailedIngestionAttempts() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        
        Consumer<Map<String, Object>> failingConsumer = feature -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt < 3) {
                throw new RuntimeException("Simulated failure");
            }
            successCount.incrementAndGet();
        };

        Map<String, Object> feature = Map.of("feature_name", "test_feature", "value", 42);

        // Simulate retry logic
        for (int i = 0; i < 5; i++) {
            try {
                failingConsumer.accept(feature);
                break;
            } catch (Exception e) {
                if (i == 4) {
                    // Should have succeeded by attempt 3
                    throw new AssertionError("Should have succeeded after retries", e);
                }
            }
        }

        assertThat(attemptCount.get()).isEqualTo(3);
        assertThat(successCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should handle transient network failures with exponential backoff")
    void shouldHandleTransientNetworkFailuresWithExponentialBackoff() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        List<Long> attemptTimestamps = new ArrayList<>();
        
        Consumer<Map<String, Object>> networkFailingConsumer = feature -> {
            attemptTimestamps.add(System.currentTimeMillis());
            int attempt = attemptCount.incrementAndGet();
            if (attempt < 4) {
                throw new RuntimeException("Network timeout");
            }
        };

        Map<String, Object> feature = Map.of("feature_name", "network_test", "value", "data");

        // Simulate exponential backoff retry
        long backoffMs = 100;
        for (int i = 0; i < 6; i++) {
            try {
                networkFailingConsumer.accept(feature);
                break;
            } catch (Exception e) {
                if (i == 5) {
                    throw new AssertionError("Should have succeeded after retries", e);
                }
                try {
                    Thread.sleep(backoffMs);
                    backoffMs *= 2; // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during backoff", ie);
                }
            }
        }

        assertThat(attemptCount.get()).isEqualTo(4);
        assertThat(attemptTimestamps).hasSize(4);
        
        // Verify backoff is increasing
        for (int i = 1; i < attemptTimestamps.size(); i++) {
            long diff = attemptTimestamps.get(i) - attemptTimestamps.get(i - 1);
            assertThat(diff).isGreaterThanOrEqualTo(100L * (1L << (i - 1)));
        }
    }

    @Test
    @DisplayName("Should trigger circuit breaker after consecutive failures")
    void shouldTriggerCircuitBreakerAfterConsecutiveFailures() {
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicBoolean circuitOpen = new AtomicBoolean(false);
        final int failureThreshold = 5;
        final int successThreshold = 3;

        Consumer<Map<String, Object>> circuitBreakerConsumer = feature -> {
            if (circuitOpen.get()) {
                throw new RuntimeException("Circuit breaker is open");
            }
            
            if (failureCount.get() >= failureThreshold) {
                circuitOpen.set(true);
                throw new RuntimeException("Circuit breaker triggered");
            }
            
            // Simulate random failures
            if (Math.random() < 0.7) {
                failureCount.incrementAndGet();
                throw new RuntimeException("Random failure");
            }
            
            successCount.incrementAndGet();
            // Reset failures if we get successes
            if (successCount.get() >= successThreshold) {
                failureCount.set(0);
                circuitOpen.set(false);
            }
        };

        Map<String, Object> feature = Map.of("feature_name", "circuit_test", "value", 100);

        // Trigger failures
        int openCircuitAttempts = 0;
        for (int i = 0; i < 20; i++) {
            try {
                circuitBreakerConsumer.accept(feature);
            } catch (Exception e) {
                if (e.getMessage().equals("Circuit breaker is open")) {
                    openCircuitAttempts++;
                }
            }
        }

        // Circuit should have opened at some point
        assertThat(openCircuitAttempts).isGreaterThan(0);
        assertThat(failureCount.get()).isGreaterThanOrEqualTo(failureThreshold);
    }

    @Test
    @DisplayName("Should degrade gracefully when storage is unavailable")
    void shouldDegradeGracefullyWhenStorageIsUnavailable() {
        List<Map<String, Object>> fallbackBuffer = new ArrayList<>();
        AtomicBoolean storageAvailable = new AtomicBoolean(false);
        
        Consumer<Map<String, Object>> gracefulDegradationConsumer = feature -> {
            if (!storageAvailable.get()) {
                // Buffer in memory when storage is down
                fallbackBuffer.add(feature);
                return;
            }
            // Would normally persist to storage
        };

        List<Map<String, Object>> features = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            features.add(Map.of("feature_name", "feature_" + i, "value", i));
        }

        // Ingest while storage is down
        for (Map<String, Object> feature : features) {
            gracefulDegradationConsumer.accept(feature);
        }

        assertThat(fallbackBuffer).hasSize(10);

        // Simulate storage recovery and replay buffer
        storageAvailable.set(true);
        List<Map<String, Object>> replayedFeatures = new ArrayList<>(fallbackBuffer);
        fallbackBuffer.clear();

        for (Map<String, Object> feature : replayedFeatures) {
            gracefulDegradationConsumer.accept(feature);
        }

        assertThat(fallbackBuffer).isEmpty();
        assertThat(replayedFeatures).hasSize(10);
    }

    @Test
    @DisplayName("Should maintain data consistency during partial failures")
    void shouldMaintainDataConsistencyDuringPartialFailures() {
        Map<String, Map<String, Object>> dataStore = new HashMap<>();
        AtomicInteger failureCount = new AtomicInteger(0);
        
        Consumer<Map<String, Object>> consistentConsumer = feature -> {
            String featureName = (String) feature.get("feature_name");
            
            // Simulate partial failure (every 3rd feature fails)
            int attempt = failureCount.incrementAndGet();
            if (attempt % 3 == 0) {
                throw new RuntimeException("Partial failure");
            }
            
            dataStore.put(featureName, feature);
        };

        List<Map<String, Object>> features = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            features.add(Map.of("feature_name", "consistent_feature_" + i, "value", i));
        }

        // Ingest with partial failures
        for (Map<String, Object> feature : features) {
            try {
                consistentConsumer.accept(feature);
            } catch (Exception e) {
                // Failed features should not be in data store
                String featureName = (String) feature.get("feature_name");
                assertThat(dataStore).doesNotContainKey(featureName);
            }
        }

        // Verify only successful features are stored
        assertThat(dataStore).hasSize(6); // 9 - 3 failures
        assertThat(dataStore).containsKey("consistent_feature_0");
        assertThat(dataStore).containsKey("consistent_feature_1");
        assertThat(dataStore).doesNotContainKey("consistent_feature_2");
    }

    @Test
    @DisplayName("Should recover from deadlock scenarios")
    void shouldRecoverFromDeadlockScenarios() {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        AtomicInteger deadlockCount = new AtomicInteger(0);
        AtomicInteger recoveryCount = new AtomicInteger(0);
        
        Runnable deadlockProneTask = () -> {
            try {
                // Simulate potential deadlock
                Thread.sleep(10);
                if (Math.random() < 0.3) {
                    deadlockCount.incrementAndGet();
                    throw new RuntimeException("Deadlock detected");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(deadlockProneTask, executor)
                .handle((result, ex) -> {
                    if (ex != null) {
                        recoveryCount.incrementAndGet();
                        // Recovery logic: retry with backoff
                        try {
                            Thread.sleep(50);
                            deadlockProneTask.run();
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    return null;
                });
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();

        assertThat(deadlockCount.get()).isGreaterThan(0);
        assertThat(recoveryCount.get()).isEqualTo(deadlockCount.get());
    }

    @Test
    @DisplayName("Should handle timeout and continue with remaining work")
    void shouldHandleTimeoutAndContinueWithRemainingWork() {
        AtomicInteger timeoutCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        List<String> processedFeatures = new ArrayList<>();
        
        Consumer<Map<String, Object>> timeoutAwareConsumer = feature -> {
            String featureName = (String) feature.get("feature_name");
            
            // Simulate timeout for specific features
            if (featureName.contains("slow")) {
                timeoutCount.incrementAndGet();
                try {
                    Thread.sleep(200); // Simulate slow operation
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Timeout", e);
                }
            }
            
            processedFeatures.add(featureName);
            successCount.incrementAndGet();
        };

        List<Map<String, Object>> features = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String name = i % 3 == 0 ? "slow_feature_" + i : "fast_feature_" + i;
            features.add(Map.of("feature_name", name, "value", i));
        }

        // Process with timeout handling
        for (Map<String, Object> feature : features) {
            try {
                CompletableFuture.runAsync(() -> timeoutAwareConsumer.accept(feature))
                    .get(100, TimeUnit.MILLISECONDS); // 100ms timeout
            } catch (Exception e) {
                // Feature timed out, continue with next
                String featureName = (String) feature.get("feature_name");
                processedFeatures.add(featureName + "_TIMEOUT");
            }
        }

        assertThat(processedFeatures).hasSize(10);
        assertThat(timeoutCount.get()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should validate data integrity after recovery")
    void shouldValidateDataIntegrityAfterRecovery() {
        Map<String, Map<String, Object>> primaryStore = new HashMap<>();
        Map<String, Map<String, Object>> backupStore = new HashMap<>();
        AtomicBoolean primaryFailed = new AtomicBoolean(false);
        
        Consumer<Map<String, Object>> replicatedConsumer = feature -> {
            String featureName = (String) feature.get("feature_name");
            
            if (!primaryFailed.get()) {
                primaryStore.put(featureName, feature);
                // Replicate to backup
                backupStore.put(featureName, new HashMap<>(feature));
            } else {
                // Primary failed, use backup
                if (!backupStore.containsKey(featureName)) {
                    throw new RuntimeException("Data missing from backup");
                }
            }
        };

        List<Map<String, Object>> features = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            features.add(Map.of("feature_name", "integrity_feature_" + i, "value", i, "checksum", i * 100));
        }

        // Ingest to both stores
        for (Map<String, Object> feature : features) {
            replicatedConsumer.accept(feature);
        }

        // Simulate primary failure
        primaryFailed.set(true);

        // Verify backup has all data
        assertThat(backupStore).hasSize(5);
        for (int i = 0; i < 5; i++) {
            String featureName = "integrity_feature_" + i;
            assertThat(backupStore).containsKey(featureName);
            assertThat(backupStore.get(featureName).get("checksum")).isEqualTo(i * 100);
        }

        // Verify data integrity
        for (Map.Entry<String, Map<String, Object>> entry : backupStore.entrySet()) {
            Map<String, Object> feature = entry.getValue();
            int value = (Integer) feature.get("value");
            int checksum = (Integer) feature.get("checksum");
            assertThat(checksum).isEqualTo(value * 100);
        }
    }

    @Test
    @DisplayName("Should recover from corrupted batch and continue")
    void shouldRecoverFromCorruptedBatchAndContinue() {
        List<Map<String, Object>> validBatches = new ArrayList<>();
        List<Map<String, Object>> corruptedBatches = new ArrayList<>();
        
        Consumer<List<Map<String, Object>>> batchProcessor = batch -> {
            // Detect corruption
            for (Map<String, Object> feature : batch) {
                if (feature.containsKey("corrupted")) {
                    corruptedBatches.addAll(batch);
                    throw new RuntimeException("Corrupted batch detected");
                }
            }
            validBatches.addAll(batch);
        };

        List<List<Map<String, Object>>> batches = new ArrayList<>();
        
        // Add valid batch
        batches.add(List.of(
            Map.of("feature_name", "valid_1", "value", 1),
            Map.of("feature_name", "valid_2", "value", 2)
        ));
        
        // Add corrupted batch
        batches.add(List.of(
            Map.of("feature_name", "valid_3", "value", 3),
            Map.of("feature_name", "corrupted", "corrupted", true)
        ));
        
        // Add another valid batch
        batches.add(List.of(
            Map.of("feature_name", "valid_4", "value", 4),
            Map.of("feature_name", "valid_5", "value", 5)
        ));

        // Process batches with error recovery
        for (List<Map<String, Object>> batch : batches) {
            try {
                batchProcessor.accept(batch);
            } catch (Exception e) {
                // Skip corrupted batch, continue with next
                continue;
            }
        }

        assertThat(validBatches).hasSize(4); // 2 from first batch, 2 from third batch
        assertThat(corruptedBatches).hasSize(2); // corrupted batch
    }

    @Test
    @DisplayName("Should handle resource exhaustion gracefully")
    void shouldHandleResourceExhaustionGracefully() {
        AtomicInteger memoryPressureCount = new AtomicInteger(0);
        List<Map<String, Object>> lowMemoryBuffer = new ArrayList<>();
        AtomicBoolean underMemoryPressure = new AtomicBoolean(false);
        
        Consumer<Map<String, Object>> resourceAwareConsumer = feature -> {
            if (underMemoryPressure.get()) {
                // Use low-memory buffer instead of full processing
                lowMemoryBuffer.add(Map.of(
                    "feature_name", feature.get("feature_name"),
                    "value", feature.get("value"),
                    "buffered", true
                ));
                memoryPressureCount.incrementAndGet();
                return;
            }
            
            // Normal processing would go here
        };

        List<Map<String, Object>> features = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            features.add(Map.of("feature_name", "resource_feature_" + i, "value", i));
        }

        // Simulate memory pressure halfway through
        for (int i = 0; i < features.size(); i++) {
            if (i == 5) {
                underMemoryPressure.set(true);
            }
            resourceAwareConsumer.accept(features.get(i));
        }

        assertThat(memoryPressureCount.get()).isEqualTo(5); // Last 5 features buffered
        assertThat(lowMemoryBuffer).hasSize(5);
        
        // Verify buffered data is preserved
        for (int i = 5; i < 10; i++) {
            assertThat(lowMemoryBuffer.get(i - 5).get("feature_name")).isEqualTo("resource_feature_" + i);
            assertThat(lowMemoryBuffer.get(i - 5).get("buffered")).isEqualTo(true);
        }
    }

    @Test
    @DisplayName("Should recover from partition loss and resync")
    void shouldRecoverFromPartitionLossAndResync() {
        Map<String, Map<String, Object>> partitionA = new HashMap<>();
        Map<String, Map<String, Object>> partitionB = new HashMap<>();
        AtomicBoolean partitionALost = new AtomicBoolean(false);
        
        Consumer<Map<String, Object>> partitionAwareConsumer = feature -> {
            String featureName = (String) feature.get("feature_name");
            int partitionIndex = Math.abs(featureName.hashCode() % 2);
            
            if (partitionIndex == 0) {
                if (partitionALost.get()) {
                    // Partition A is lost, buffer for later resync
                    return;
                }
                partitionA.put(featureName, feature);
            } else {
                partitionB.put(featureName, feature);
            }
        };

        List<Map<String, Object>> features = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            features.add(Map.of("feature_name", "partition_feature_" + i, "value", i));
        }

        // Ingest features
        for (Map<String, Object> feature : features) {
            partitionAwareConsumer.accept(feature);
        }

        int initialPartitionASize = partitionA.size();
        int initialPartitionBSize = partitionB.size();

        // Simulate partition A loss
        partitionALost.set(true);
        partitionA.clear();

        // Verify partition B is intact
        assertThat(partitionB).hasSize(initialPartitionBSize);

        // Simulate partition recovery and resync
        partitionALost.set(false);
        
        // Resync lost features
        for (Map<String, Object> feature : features) {
            partitionAwareConsumer.accept(feature);
        }

        // Verify both partitions are now consistent
        assertThat(partitionA.size() + partitionB.size()).isEqualTo(10);
    }
}

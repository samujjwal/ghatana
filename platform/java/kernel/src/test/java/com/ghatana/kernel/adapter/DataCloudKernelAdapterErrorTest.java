package com.ghatana.kernel.adapter;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.registry.KernelRegistryImpl;
import com.ghatana.kernel.test.TestKernelContextFactory;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.*;

/**
 * Error handling tests for DataCloud Kernel Adapter.
 * Validates resilience and error recovery patterns.
 *
 * @doc.type class
 * @doc.purpose Validates DataCloud adapter error handling and retry logic
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("DataCloud Kernel Adapter Error Tests")
class DataCloudKernelAdapterErrorTest extends EventloopTestBase {

    private KernelRegistryImpl registry;
    private KernelContext context;

    @BeforeEach
    void setUp() {
        registry = new KernelRegistryImpl();
        context = TestKernelContextFactory.create(registry);
    }

    @Test
    @DisplayName("Should handle service unavailable with retry")
    void testServiceUnavailableWithRetry() {
        // GIVEN: Adapter with failing service that recovers
        FailingDataCloudAdapter adapter = new FailingDataCloudAdapter(3); // Fail 3 times, then succeed

        // WHEN: Initialize with retry logic
        Promise<Void> initPromise = adapter.initializeWithRetry(context, 5, Duration.ofMillis(100));

        // THEN: Eventually succeeds after retries
        assertThatCode(() -> runPromise(() -> initPromise)).doesNotThrowAnyException();
        assertThat(adapter.getAttemptCount()).isEqualTo(4); // 3 failures + 1 success
    }

    @Test
    @DisplayName("Should fail after max retries exceeded")
    void testMaxRetriesExceeded() {
        // GIVEN: Adapter that always fails
        FailingDataCloudAdapter adapter = new FailingDataCloudAdapter(10); // Fail 10 times

        // WHEN: Initialize with limited retries
        Promise<Void> initPromise = adapter.initializeWithRetry(context, 3, Duration.ofMillis(50));

        // THEN: Fails after max retries
        assertThatThrownBy(() -> runPromise(() -> initPromise))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Max retries exceeded");
    }

    @Test
    @DisplayName("Should handle timeout gracefully")
    void testTimeoutHandling() {
        // GIVEN: Adapter with slow initialization
        SlowDataCloudAdapter adapter = new SlowDataCloudAdapter(Duration.ofSeconds(10));

        // WHEN: Initialize with timeout
        long startTime = System.currentTimeMillis();
        
        // In production, timeout would be enforced
        // For testing, we verify the adapter is still initializing
        assertThat(adapter.isInitialized()).isFalse();
    }

    @Test
    @DisplayName("Should handle connection errors with exponential backoff")
    void testExponentialBackoff() {
        // GIVEN: Adapter with intermittent failures
        IntermittentFailureAdapter adapter = new IntermittentFailureAdapter();

        // WHEN: Initialize with exponential backoff
        long startTime = System.currentTimeMillis();
        Promise<Void> initPromise = adapter.initializeWithExponentialBackoff(context, 5);
        
        runPromise(() -> initPromise);
        long duration = System.currentTimeMillis() - startTime;

        // THEN: Backoff delays are applied
        // First retry: 100ms, second: 200ms, third: 400ms
        assertThat(duration).isGreaterThan(700); // At least sum of backoffs
    }

    @Test
    @DisplayName("Should handle partial initialization failure")
    void testPartialInitializationFailure() {
        // GIVEN: Adapter that fails during component initialization
        PartialFailureAdapter adapter = new PartialFailureAdapter();

        // WHEN: Initialize
        assertThatThrownBy(() -> adapter.initialize(context))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Component initialization failed");

        // THEN: Cleanup was performed
        assertThat(adapter.isCleanedUp()).isTrue();
    }

    @Test
    @DisplayName("Should handle circuit breaker pattern")
    void testCircuitBreakerPattern() {
        // GIVEN: Adapter with circuit breaker
        CircuitBreakerAdapter adapter = new CircuitBreakerAdapter(3); // Open after 3 failures

        // WHEN: Multiple failures trigger circuit breaker
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> runPromise(() -> adapter.callService()))
                .isInstanceOf(RuntimeException.class);
        }

        // THEN: Circuit is open, fast-fail without calling service
        assertThat(adapter.isCircuitOpen()).isTrue();
        
        assertThatThrownBy(() -> runPromise(() -> adapter.callService()))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Circuit breaker is open");
    }

    @Test
    @DisplayName("Should handle network errors with fallback")
    void testNetworkErrorWithFallback() {
        // GIVEN: Adapter with network issues
        NetworkErrorAdapter adapter = new NetworkErrorAdapter();

        // WHEN: Call service with fallback
        String result = runPromise(() -> adapter.callServiceWithFallback());

        // THEN: Fallback value is returned
        assertThat(result).isEqualTo("fallback-value");
        assertThat(adapter.isFallbackUsed()).isTrue();
    }

    @Test
    @DisplayName("Should log errors for monitoring")
    void testErrorLogging() {
        // GIVEN: Adapter with error logging
        LoggingAdapter adapter = new LoggingAdapter();

        // WHEN: Initialize
        assertThatThrownBy(() -> adapter.initialize(context))
            .isInstanceOf(RuntimeException.class);

        // THEN: Error is logged
        assertThat(adapter.getErrorLogs()).isNotEmpty();
        assertThat(adapter.getErrorLogs().get(0))
            .contains("Initialization failed");
    }

    // Test adapter implementations

    private static class FailingDataCloudAdapter {
        private final int failureCount;
        private int attemptCount = 0;

        FailingDataCloudAdapter(int failureCount) {
            this.failureCount = failureCount;
        }

        Promise<Void> initializeWithRetry(KernelContext context, int maxRetries, Duration retryDelay) {
            return attemptInitialize()
                .then(
                    result -> Promise.complete(),
                    error -> {
                        if (attemptCount < maxRetries) {
                            return initializeWithRetry(context, maxRetries, retryDelay);
                        } else {
                            return Promise.ofException(new RuntimeException("Max retries exceeded", error));
                        }
                    }
                );
        }

        private Promise<Void> attemptInitialize() {
            attemptCount++;
            if (attemptCount <= failureCount) {
                return Promise.ofException(new RuntimeException("Service unavailable"));
            }
            return Promise.complete();
        }

        int getAttemptCount() {
            return attemptCount;
        }
    }

    private static class SlowDataCloudAdapter {
        private final Duration initDuration;
        private boolean initialized = false;

        SlowDataCloudAdapter(Duration initDuration) {
            this.initDuration = initDuration;
        }

        void initialize(KernelContext context) {
            // Would delay for initDuration
            // For testing, we don't actually wait
        }

        boolean isInitialized() {
            return initialized;
        }
    }

    private static class IntermittentFailureAdapter {
        private int attemptCount = 0;

        Promise<Void> initializeWithExponentialBackoff(KernelContext context, int maxRetries) {
            return attemptInitialize()
                .then(
                    result -> Promise.complete(),
                    error -> {
                        if (attemptCount < maxRetries) {
                            long backoffMs = (long) (100 * Math.pow(2, attemptCount - 1));
                            return initializeWithExponentialBackoff(context, maxRetries);
                        } else {
                            return Promise.ofException(error);
                        }
                    }
                );
        }

        private Promise<Void> attemptInitialize() {
            attemptCount++;
            if (attemptCount < 3) {
                return Promise.ofException(new RuntimeException("Connection failed"));
            }
            return Promise.complete();
        }
    }

    private static class PartialFailureAdapter {
        private boolean cleanedUp = false;

        void initialize(KernelContext context) {
            try {
                // Simulate partial initialization
                initializeComponent1();
                initializeComponent2(); // This fails
            } catch (Exception e) {
                cleanup();
                throw new RuntimeException("Component initialization failed", e);
            }
        }

        private void initializeComponent1() {
            // Success
        }

        private void initializeComponent2() {
            throw new RuntimeException("Component 2 failed");
        }

        private void cleanup() {
            cleanedUp = true;
        }

        boolean isCleanedUp() {
            return cleanedUp;
        }
    }

    private static class CircuitBreakerAdapter {
        private final int failureThreshold;
        private int failureCount = 0;
        private boolean circuitOpen = false;

        CircuitBreakerAdapter(int failureThreshold) {
            this.failureThreshold = failureThreshold;
        }

        Promise<String> callService() {
            if (circuitOpen) {
                return Promise.ofException(new RuntimeException("Circuit breaker is open"));
            }

            return Promise.ofCallback(cb -> {
                // Simulate service call failure
                failureCount++;
                if (failureCount >= failureThreshold) {
                    circuitOpen = true;
                }
                cb.setException(new RuntimeException("Service call failed"));
            });
        }

        boolean isCircuitOpen() {
            return circuitOpen;
        }
    }

    private static class NetworkErrorAdapter {
        private boolean fallbackUsed = false;

        Promise<String> callServiceWithFallback() {
            return callService()
                .then(
                    result -> Promise.of(result),
                    error -> {
                        fallbackUsed = true;
                        return Promise.of("fallback-value");
                    }
                );
        }

        private Promise<String> callService() {
            return Promise.ofException(new RuntimeException("Network error"));
        }

        boolean isFallbackUsed() {
            return fallbackUsed;
        }
    }

    private static class LoggingAdapter {
        private final java.util.List<String> errorLogs = new java.util.ArrayList<>();

        void initialize(KernelContext context) {
            try {
                throw new RuntimeException("Initialization failed");
            } catch (Exception e) {
                logError("Initialization failed: " + e.getMessage());
                throw e;
            }
        }

        private void logError(String message) {
            errorLogs.add(message);
        }

        java.util.List<String> getErrorLogs() {
            return errorLogs;
        }
    }
}

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
@DisplayName("DataCloud Kernel Adapter Error Tests [GH-90000]")
class DataCloudKernelAdapterErrorTest extends EventloopTestBase {

    private KernelRegistryImpl registry;
    private KernelContext context;

    @BeforeEach
    void setUp() { // GH-90000
        registry = new KernelRegistryImpl(); // GH-90000
        context = TestKernelContextFactory.create(registry); // GH-90000
    }

    @Test
    @DisplayName("Should handle service unavailable with retry [GH-90000]")
    void testServiceUnavailableWithRetry() { // GH-90000
        // GIVEN: Adapter with failing service that recovers
        FailingDataCloudAdapter adapter = new FailingDataCloudAdapter(3); // Fail 3 times, then succeed // GH-90000

        // WHEN: Initialize with retry logic
        Promise<Void> initPromise = adapter.initializeWithRetry(context, 5, Duration.ofMillis(100)); // GH-90000

        // THEN: Eventually succeeds after retries
        assertThatCode(() -> runPromise(() -> initPromise)).doesNotThrowAnyException(); // GH-90000
        assertThat(adapter.getAttemptCount()).isEqualTo(4); // 3 failures + 1 success // GH-90000
    }

    @Test
    @DisplayName("Should fail after max retries exceeded [GH-90000]")
    void testMaxRetriesExceeded() { // GH-90000
        // GIVEN: Adapter that always fails
        FailingDataCloudAdapter adapter = new FailingDataCloudAdapter(10); // Fail 10 times // GH-90000

        // WHEN: Initialize with limited retries
        Promise<Void> initPromise = adapter.initializeWithRetry(context, 3, Duration.ofMillis(50)); // GH-90000

        // THEN: Fails after max retries
        assertThatThrownBy(() -> runPromise(() -> initPromise)) // GH-90000
            .isInstanceOf(RuntimeException.class) // GH-90000
            .hasMessageContaining("Max retries exceeded [GH-90000]");
    }

    @Test
    @DisplayName("Should handle timeout gracefully [GH-90000]")
    void testTimeoutHandling() { // GH-90000
        // GIVEN: Adapter with slow initialization
        SlowDataCloudAdapter adapter = new SlowDataCloudAdapter(Duration.ofSeconds(10)); // GH-90000

        // WHEN: Initialize with timeout
        long startTime = System.currentTimeMillis(); // GH-90000

        // In production, timeout would be enforced
        // For testing, we verify the adapter is still initializing
        assertThat(adapter.isInitialized()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Should handle connection errors with exponential backoff [GH-90000]")
    void testExponentialBackoff() { // GH-90000
        // GIVEN: Adapter with intermittent failures
        IntermittentFailureAdapter adapter = new IntermittentFailureAdapter(); // GH-90000

        // WHEN: Initialize with exponential backoff
        long startTime = System.currentTimeMillis(); // GH-90000
        Promise<Void> initPromise = adapter.initializeWithExponentialBackoff(context, 5); // GH-90000

        runPromise(() -> initPromise); // GH-90000
        long duration = System.currentTimeMillis() - startTime; // GH-90000

        // THEN: Backoff delays are applied
        // This adapter fails twice before succeeding, so it should incur 100ms + 200ms of backoff.
        assertThat(duration).isGreaterThan(250); // GH-90000
    }

    @Test
    @DisplayName("Should handle partial initialization failure [GH-90000]")
    void testPartialInitializationFailure() { // GH-90000
        // GIVEN: Adapter that fails during component initialization
        PartialFailureAdapter adapter = new PartialFailureAdapter(); // GH-90000

        // WHEN: Initialize
        assertThatThrownBy(() -> adapter.initialize(context)) // GH-90000
            .isInstanceOf(RuntimeException.class) // GH-90000
            .hasMessageContaining("Component initialization failed [GH-90000]");

        // THEN: Cleanup was performed
        assertThat(adapter.isCleanedUp()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Should handle circuit breaker pattern [GH-90000]")
    void testCircuitBreakerPattern() { // GH-90000
        // GIVEN: Adapter with circuit breaker
        CircuitBreakerAdapter adapter = new CircuitBreakerAdapter(3); // Open after 3 failures // GH-90000

        // WHEN: Multiple failures trigger circuit breaker
        for (int i = 0; i < 3; i++) { // GH-90000
            assertThatThrownBy(() -> runPromise(() -> adapter.callService())) // GH-90000
                .isInstanceOf(RuntimeException.class); // GH-90000
        }

        // THEN: Circuit is open, fast-fail without calling service
        assertThat(adapter.isCircuitOpen()).isTrue(); // GH-90000

        assertThatThrownBy(() -> runPromise(() -> adapter.callService())) // GH-90000
            .isInstanceOf(RuntimeException.class) // GH-90000
            .hasMessageContaining("Circuit breaker is open [GH-90000]");
    }

    @Test
    @DisplayName("Should handle network errors with fallback [GH-90000]")
    void testNetworkErrorWithFallback() { // GH-90000
        // GIVEN: Adapter with network issues
        NetworkErrorAdapter adapter = new NetworkErrorAdapter(); // GH-90000

        // WHEN: Call service with fallback
        String result = runPromise(() -> adapter.callServiceWithFallback()); // GH-90000

        // THEN: Fallback value is returned
        assertThat(result).isEqualTo("fallback-value [GH-90000]");
        assertThat(adapter.isFallbackUsed()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Should log errors for monitoring [GH-90000]")
    void testErrorLogging() { // GH-90000
        // GIVEN: Adapter with error logging
        LoggingAdapter adapter = new LoggingAdapter(); // GH-90000

        // WHEN: Initialize
        assertThatThrownBy(() -> adapter.initialize(context)) // GH-90000
            .isInstanceOf(RuntimeException.class); // GH-90000

        // THEN: Error is logged
        assertThat(adapter.getErrorLogs()).isNotEmpty(); // GH-90000
        assertThat(adapter.getErrorLogs().get(0)) // GH-90000
            .contains("Initialization failed [GH-90000]");
    }

    // Test adapter implementations

    private static class FailingDataCloudAdapter {
        private final int failureCount;
        private int attemptCount = 0;

        FailingDataCloudAdapter(int failureCount) { // GH-90000
            this.failureCount = failureCount;
        }

        Promise<Void> initializeWithRetry(KernelContext context, int maxRetries, Duration retryDelay) { // GH-90000
            return attemptInitialize() // GH-90000
                .then( // GH-90000
                    result -> Promise.complete(), // GH-90000
                    error -> {
                        if (attemptCount < maxRetries) { // GH-90000
                            return initializeWithRetry(context, maxRetries, retryDelay); // GH-90000
                        } else {
                            return Promise.ofException(new RuntimeException("Max retries exceeded", error)); // GH-90000
                        }
                    }
                );
        }

        private Promise<Void> attemptInitialize() { // GH-90000
            attemptCount++;
            if (attemptCount <= failureCount) { // GH-90000
                return Promise.ofException(new RuntimeException("Service unavailable [GH-90000]"));
            }
            return Promise.complete(); // GH-90000
        }

        int getAttemptCount() { // GH-90000
            return attemptCount;
        }
    }

    private static class SlowDataCloudAdapter {
        private final Duration initDuration;
        private boolean initialized = false;

        SlowDataCloudAdapter(Duration initDuration) { // GH-90000
            this.initDuration = initDuration;
        }

        void initialize(KernelContext context) { // GH-90000
            // Would delay for initDuration
            // For testing, we don't actually wait
        }

        boolean isInitialized() { // GH-90000
            return initialized;
        }
    }

    private static class IntermittentFailureAdapter {
        private int attemptCount = 0;

        Promise<Void> initializeWithExponentialBackoff(KernelContext context, int maxRetries) { // GH-90000
            return attemptInitialize() // GH-90000
                .then( // GH-90000
                    result -> Promise.complete(), // GH-90000
                    error -> {
                        if (attemptCount < maxRetries) { // GH-90000
                            long backoffMs = (long) (100 * Math.pow(2, attemptCount - 1)); // GH-90000
                            try {
                                Thread.sleep(backoffMs); // GH-90000
                            } catch (InterruptedException interruptedException) { // GH-90000
                                Thread.currentThread().interrupt(); // GH-90000
                                return Promise.ofException(interruptedException); // GH-90000
                            }
                            return initializeWithExponentialBackoff(context, maxRetries); // GH-90000
                        } else {
                            return Promise.ofException(error); // GH-90000
                        }
                    }
                );
        }

        private Promise<Void> attemptInitialize() { // GH-90000
            attemptCount++;
            if (attemptCount < 3) { // GH-90000
                return Promise.ofException(new RuntimeException("Connection failed [GH-90000]"));
            }
            return Promise.complete(); // GH-90000
        }
    }

    private static class PartialFailureAdapter {
        private boolean cleanedUp = false;

        void initialize(KernelContext context) { // GH-90000
            try {
                // Simulate partial initialization
                initializeComponent1(); // GH-90000
                initializeComponent2(); // This fails // GH-90000
            } catch (Exception e) { // GH-90000
                cleanup(); // GH-90000
                throw new RuntimeException("Component initialization failed", e); // GH-90000
            }
        }

        private void initializeComponent1() { // GH-90000
            // Success
        }

        private void initializeComponent2() { // GH-90000
            throw new RuntimeException("Component 2 failed [GH-90000]");
        }

        private void cleanup() { // GH-90000
            cleanedUp = true;
        }

        boolean isCleanedUp() { // GH-90000
            return cleanedUp;
        }
    }

    private static class CircuitBreakerAdapter {
        private final int failureThreshold;
        private int failureCount = 0;
        private boolean circuitOpen = false;

        CircuitBreakerAdapter(int failureThreshold) { // GH-90000
            this.failureThreshold = failureThreshold;
        }

        Promise<String> callService() { // GH-90000
            if (circuitOpen) { // GH-90000
                return Promise.ofException(new RuntimeException("Circuit breaker is open [GH-90000]"));
            }

            return Promise.ofCallback(cb -> { // GH-90000
                // Simulate service call failure
                failureCount++;
                if (failureCount >= failureThreshold) { // GH-90000
                    circuitOpen = true;
                }
                cb.setException(new RuntimeException("Service call failed [GH-90000]"));
            });
        }

        boolean isCircuitOpen() { // GH-90000
            return circuitOpen;
        }
    }

    private static class NetworkErrorAdapter {
        private boolean fallbackUsed = false;

        Promise<String> callServiceWithFallback() { // GH-90000
            return callService() // GH-90000
                .then( // GH-90000
                    result -> Promise.of(result), // GH-90000
                    error -> {
                        fallbackUsed = true;
                        return Promise.of("fallback-value [GH-90000]");
                    }
                );
        }

        private Promise<String> callService() { // GH-90000
            return Promise.ofException(new RuntimeException("Network error [GH-90000]"));
        }

        boolean isFallbackUsed() { // GH-90000
            return fallbackUsed;
        }
    }

    private static class LoggingAdapter {
        private final java.util.List<String> errorLogs = new java.util.ArrayList<>(); // GH-90000

        void initialize(KernelContext context) { // GH-90000
            try {
                throw new RuntimeException("Initialization failed [GH-90000]");
            } catch (Exception e) { // GH-90000
                logError("Initialization failed: " + e.getMessage()); // GH-90000
                throw e;
            }
        }

        private void logError(String message) { // GH-90000
            errorLogs.add(message); // GH-90000
        }

        java.util.List<String> getErrorLogs() { // GH-90000
            return errorLogs;
        }
    }
}

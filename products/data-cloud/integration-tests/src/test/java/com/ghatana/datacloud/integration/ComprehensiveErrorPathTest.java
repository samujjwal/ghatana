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
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive Error Path Tests
 *
 * Tests comprehensive error handling patterns including:
 * - Failure modes (network failures, timeouts, rate limits, dependency failures) // GH-90000
 * - Retries (exponential backoff, max retries, retryable vs non-retryable errors) // GH-90000
 * - Timeouts (connection timeouts, read timeouts, request timeouts) // GH-90000
 * - Circuit breakers (state transitions, failure thresholds, recovery) // GH-90000
 *
 * @doc.type class
 * @doc.purpose Test comprehensive error handling patterns including failure modes, retries, timeouts, and circuit breakers
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("Comprehensive Error Path Tests")
@Tag("integration")
class ComprehensiveErrorPathTest {

    @Test
    @DisplayName("Should handle network failures with exponential backoff retry")
    void shouldHandleNetworkFailuresWithExponentialBackoffRetry() throws Exception { // GH-90000
        AtomicInteger attemptCount = new AtomicInteger(0); // GH-90000
        List<Long> attemptTimestamps = new ArrayList<>(); // GH-90000
        
        Supplier<String> networkOperation = () -> { // GH-90000
            attemptTimestamps.add(System.currentTimeMillis()); // GH-90000
            int attempt = attemptCount.incrementAndGet(); // GH-90000
            
            if (attempt < 4) { // GH-90000
                throw new RuntimeException("Network timeout - attempt " + attempt); // GH-90000
            }
            
            return "success";
        };

        ExponentialBackoffRetry retry = new ExponentialBackoffRetry.Builder() // GH-90000
            .maxRetries(5) // GH-90000
            .initialBackoff(Duration.ofMillis(100)) // GH-90000
            .maxBackoff(Duration.ofSeconds(2)) // GH-90000
            .backoffMultiplier(2.0) // GH-90000
            .build(); // GH-90000

        String result = retry.execute(networkOperation); // GH-90000

        assertThat(result).isEqualTo("success");
        assertThat(attemptCount.get()).isEqualTo(4); // GH-90000
        assertThat(attemptTimestamps).hasSize(4); // GH-90000
        
        // Verify exponential backoff pattern
        for (int i = 1; i < attemptTimestamps.size(); i++) { // GH-90000
            long diff = attemptTimestamps.get(i) - attemptTimestamps.get(i - 1); // GH-90000
            long expectedMin = (long) (100 * Math.pow(2, i - 1)); // GH-90000
            assertThat(diff).isGreaterThanOrEqualTo(expectedMin - 50); // Allow some variance // GH-90000
        }
    }

    @Test
    @DisplayName("Should fail after max retries exhausted")
    void shouldFailAfterMaxRetriesExhausted() { // GH-90000
        AtomicInteger attemptCount = new AtomicInteger(0); // GH-90000
        
        Supplier<String> failingOperation = () -> { // GH-90000
            attemptCount.incrementAndGet(); // GH-90000
            throw new RuntimeException("Persistent failure");
        };

        ExponentialBackoffRetry retry = new ExponentialBackoffRetry.Builder() // GH-90000
            .maxRetries(3) // GH-90000
            .initialBackoff(Duration.ofMillis(10)) // GH-90000
            .maxBackoff(Duration.ofMillis(100)) // GH-90000
            .backoffMultiplier(2.0) // GH-90000
            .build(); // GH-90000

        try {
            retry.execute(failingOperation); // GH-90000
            throw new AssertionError("Should have thrown exception after max retries");
        } catch (RuntimeException e) { // GH-90000
            assertThat(e.getMessage()).isEqualTo("Persistent failure");
            assertThat(attemptCount.get()).isEqualTo(4); // Initial attempt + 3 retries // GH-90000
        }
    }

    @Test
    @DisplayName("Should not retry non-retryable errors")
    void shouldNotRetryNonRetryableErrors() { // GH-90000
        AtomicInteger attemptCount = new AtomicInteger(0); // GH-90000
        
        Supplier<String> nonRetryableOperation = () -> { // GH-90000
            attemptCount.incrementAndGet(); // GH-90000
            throw new IllegalArgumentException("Invalid argument - not retryable");
        };

        ExponentialBackoffRetry retry = new ExponentialBackoffRetry.Builder() // GH-90000
            .maxRetries(5) // GH-90000
            .initialBackoff(Duration.ofMillis(10)) // GH-90000
            .maxBackoff(Duration.ofMillis(100)) // GH-90000
            .retryableExceptions(List.of(RuntimeException.class)) // GH-90000
            .build(); // GH-90000

        try {
            retry.execute(nonRetryableOperation); // GH-90000
            throw new AssertionError("Should have thrown exception");
        } catch (IllegalArgumentException e) { // GH-90000
            assertThat(e.getMessage()).isEqualTo("Invalid argument - not retryable");
            assertThat(attemptCount.get()).isEqualTo(1); // Should not retry // GH-90000
        }
    }

    @Test
    @DisplayName("Should handle connection timeouts gracefully")
    void shouldHandleConnectionTimeoutsGracefully() throws Exception { // GH-90000
        Supplier<String> slowOperation = () -> { // GH-90000
            try {
                Thread.sleep(2000); // Simulate slow operation // GH-90000
                return "success";
            } catch (InterruptedException e) { // GH-90000
                Thread.currentThread().interrupt(); // GH-90000
                throw new RuntimeException("Interrupted", e); // GH-90000
            }
        };

        TimeoutExecutor executor = new TimeoutExecutor(Duration.ofMillis(100)); // GH-90000

        try {
            executor.execute(slowOperation); // GH-90000
            throw new AssertionError("Should have timed out");
        } catch (TimeoutException | InterruptedException e) { // GH-90000
            assertThat(e.getMessage()).contains("Operation timed out");
        }
    }

    @Test
    @DisplayName("Should handle read timeouts during streaming")
    void shouldHandleReadTimeoutsDuringStreaming() throws Exception { // GH-90000
        AtomicInteger dataChunks = new AtomicInteger(0); // GH-90000
        
        Supplier<List<String>> streamingOperation = () -> { // GH-90000
            List<String> chunks = new ArrayList<>(); // GH-90000
            for (int i = 0; i < 10; i++) { // GH-90000
                try {
                    Thread.sleep(500); // Simulate slow data // GH-90000
                    chunks.add("chunk_" + i); // GH-90000
                    dataChunks.incrementAndGet(); // GH-90000
                } catch (InterruptedException e) { // GH-90000
                    Thread.currentThread().interrupt(); // GH-90000
                    throw new RuntimeException("Interrupted", e); // GH-90000
                }
            }
            return chunks;
        };

        TimeoutExecutor executor = new TimeoutExecutor(Duration.ofMillis(1200)); // GH-90000

        try {
            executor.execute(streamingOperation); // GH-90000
            throw new AssertionError("Should have timed out during streaming");
        } catch (TimeoutException | InterruptedException e) { // GH-90000
            assertThat(dataChunks.get()).isLessThan(10); // Should have timed out before completion // GH-90000
            assertThat(dataChunks.get()).isGreaterThan(0); // Should have processed some data // GH-90000
        }
    }

    @Test
    @DisplayName("Circuit breaker should open after consecutive failures")
    void circuitBreakerShouldOpenAfterConsecutiveFailures() { // GH-90000
        CircuitBreaker breaker = new CircuitBreaker.Builder() // GH-90000
            .failureThreshold(5) // GH-90000
            .timeout(Duration.ofSeconds(30)) // GH-90000
            .build(); // GH-90000

        AtomicInteger failureCount = new AtomicInteger(0); // GH-90000
        
        for (int i = 0; i < 10; i++) { // GH-90000
            try {
                breaker.execute(() -> { // GH-90000
                    failureCount.incrementAndGet(); // GH-90000
                    throw new RuntimeException("Simulated failure");
                });
            } catch (Exception e) { // GH-90000
                // Expected failures
            }
        }

        assertThat(breaker.getState()).isEqualTo(CircuitState.OPEN); // GH-90000
        assertThat(failureCount.get()).isEqualTo(5); // Should stop after threshold // GH-90000
    }

    @Test
    @DisplayName("Circuit breaker should transition to half-open after timeout")
    void circuitBreakerShouldTransitionToHalfOpenAfterTimeout() throws Exception { // GH-90000
        CircuitBreaker breaker = new CircuitBreaker.Builder() // GH-90000
            .failureThreshold(3) // GH-90000
            .timeout(Duration.ofMillis(100)) // GH-90000
            .build(); // GH-90000

        // Trigger failures to open circuit
        for (int i = 0; i < 3; i++) { // GH-90000
            try {
                breaker.execute(() -> { // GH-90000
                    throw new RuntimeException("Failure");
                });
            } catch (Exception e) { // GH-90000
                // Expected
            }
        }

        assertThat(breaker.getState()).isEqualTo(CircuitState.OPEN); // GH-90000

        // Wait for timeout
        Thread.sleep(150); // GH-90000

        // Circuit should transition to half-open
        assertThat(breaker.getState()).isEqualTo(CircuitState.HALF_OPEN); // GH-90000
    }

    @Test
    @DisplayName("Circuit breaker should close after successful request in half-open state")
    void circuitBreakerShouldCloseAfterSuccessfulRequestInHalfOpenState() { // GH-90000
        CircuitBreaker breaker = new CircuitBreaker.Builder() // GH-90000
            .failureThreshold(3) // GH-90000
            .timeout(Duration.ZERO) // GH-90000
            .build(); // GH-90000

        // Manually transition to half-open
        breaker.forceState(CircuitState.HALF_OPEN); // GH-90000

        // Execute successful request
        String result = breaker.execute(() -> "success"); // GH-90000

        assertThat(result).isEqualTo("success");
        assertThat(breaker.getState()).isEqualTo(CircuitState.CLOSED); // GH-90000
    }

    @Test
    @DisplayName("Circuit breaker should fail fast when open")
    void circuitBreakerShouldFailFastWhenOpen() { // GH-90000
        CircuitBreaker breaker = new CircuitBreaker.Builder() // GH-90000
            .failureThreshold(3) // GH-90000
            .timeout(Duration.ofSeconds(30)) // GH-90000
            .build(); // GH-90000

        // Manually open circuit
        breaker.forceState(CircuitState.OPEN); // GH-90000

        long startTime = System.nanoTime(); // GH-90000
        
        try {
            breaker.execute(() -> { // GH-90000
                try {
                    Thread.sleep(1000); // This should not execute // GH-90000
                } catch (InterruptedException e) { // GH-90000
                    Thread.currentThread().interrupt(); // GH-90000
                    throw new RuntimeException(e); // GH-90000
                }
                return "should not reach here";
            });
            throw new AssertionError("Should have thrown CircuitOpenException");
        } catch (CircuitOpenException e) { // GH-90000
            long duration = System.nanoTime() - startTime; // GH-90000
            assertThat(duration).isLessThan(TimeUnit.MILLISECONDS.toNanos(100)); // Should fail fast // GH-90000
        }
    }

    @Test
    @DisplayName("Should handle rate limiting with backpressure")
    void shouldHandleRateLimitingWithBackpressure() throws Exception { // GH-90000
        RateLimiter rateLimiter = new RateLimiter(10, Duration.ofSeconds(1)); // 10 requests per second // GH-90000
        AtomicInteger rejectedCount = new AtomicInteger(0); // GH-90000
        AtomicInteger acceptedCount = new AtomicInteger(0); // GH-90000

        ExecutorService executor = Executors.newFixedThreadPool(20); // GH-90000
        CountDownLatch latch = new CountDownLatch(20); // GH-90000

        for (int i = 0; i < 20; i++) { // GH-90000
            executor.submit(() -> { // GH-90000
                try {
                    if (rateLimiter.tryAcquire()) { // GH-90000
                        acceptedCount.incrementAndGet(); // GH-90000
                    } else {
                        rejectedCount.incrementAndGet(); // GH-90000
                    }
                } finally {
                    latch.countDown(); // GH-90000
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS); // GH-90000
        executor.shutdown(); // GH-90000

        assertThat(acceptedCount.get()).isLessThanOrEqualTo(10); // Should accept at most 10 // GH-90000
        assertThat(rejectedCount.get()).isGreaterThan(0); // Some should be rejected // GH-90000
    }

    @Test
    @DisplayName("Should handle cascading failures with fallback")
    void shouldHandleCascadingFailuresWithFallback() { // GH-90000
        AtomicInteger primaryFailureCount = new AtomicInteger(0); // GH-90000
        AtomicInteger fallbackSuccessCount = new AtomicInteger(0); // GH-90000
        
        Supplier<String> primaryService = () -> { // GH-90000
            primaryFailureCount.incrementAndGet(); // GH-90000
            throw new RuntimeException("Primary service unavailable");
        };

        Supplier<String> fallbackService = () -> { // GH-90000
            fallbackSuccessCount.incrementAndGet(); // GH-90000
            return "fallback response";
        };

        ResilientChain<String> chain = new ResilientChain.Builder<String>() // GH-90000
            .withPrimary(primaryService) // GH-90000
            .withFallback(fallbackService) // GH-90000
            .withRetry(new ExponentialBackoffRetry.Builder().maxRetries(2).build()) // GH-90000
            .build(); // GH-90000

        String chainResult = chain.execute(); // GH-90000

        assertThat(chainResult).isEqualTo("fallback response");
        assertThat(primaryFailureCount.get()).isEqualTo(3); // Initial + 2 retries // GH-90000
        assertThat(fallbackSuccessCount.get()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("Should track error metrics for observability")
    void shouldTrackErrorMetricsForObservability() { // GH-90000
        ErrorMetrics metrics = new ErrorMetrics(); // GH-90000
        
        Supplier<String> operation = () -> { // GH-90000
            metrics.recordAttempt(); // GH-90000
            if (Math.random() < 0.3) { // GH-90000
                metrics.recordFailure("random_failure");
                throw new RuntimeException("Random failure");
            }
            metrics.recordSuccess(); // GH-90000
            return "success";
        };

        for (int i = 0; i < 100; i++) { // GH-90000
            try {
                operation.get(); // GH-90000
            } catch (Exception e) { // GH-90000
                // Expected some failures
            }
        }

        assertThat(metrics.getTotalAttempts()).isEqualTo(100); // GH-90000
        assertThat(metrics.getSuccessCount()).isGreaterThan(50); // GH-90000
        assertThat(metrics.getFailureCount()).isGreaterThan(10); // GH-90000
        assertThat(metrics.getFailureRate()).isBetween(0.1, 0.5); // GH-90000
        
        Map<String, Long> errorBreakdown = metrics.getErrorBreakdown(); // GH-90000
        assertThat(errorBreakdown).containsKey("random_failure");
    }

    @Test
    @DisplayName("Should handle dependency failures gracefully")
    void shouldHandleDependencyFailuresGracefully() { // GH-90000
        AtomicInteger dependencyFailureCount = new AtomicInteger(0); // GH-90000
        AtomicInteger cachedResponseCount = new AtomicInteger(0); // GH-90000
        
        DependencyManager dependencyManager = new DependencyManager(); // GH-90000
        
        // Set up a failing dependency
        dependencyManager.registerDependency("critical-service", () -> { // GH-90000
            dependencyFailureCount.incrementAndGet(); // GH-90000
            throw new RuntimeException("Critical service unavailable");
        });

        // Set up cached response as fallback
        dependencyManager.setCachedResponse("critical-service", "cached_data"); // GH-90000

        // Execute with dependency and fallback
        String result = dependencyManager.executeWithFallback("critical-service", "default_value"); // GH-90000

        assertThat(result).isEqualTo("cached_data");
        assertThat(dependencyFailureCount.get()).isEqualTo(1); // GH-90000
        assertThat(cachedResponseCount.get()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("Should handle concurrent error scenarios correctly")
    void shouldHandleConcurrentErrorScenariosCorrectly() throws Exception { // GH-90000
        AtomicInteger successCount = new AtomicInteger(0); // GH-90000
        AtomicInteger failureCount = new AtomicInteger(0); // GH-90000
        CircuitBreaker breaker = new CircuitBreaker.Builder() // GH-90000
            .failureThreshold(10) // GH-90000
            .timeout(Duration.ofSeconds(10)) // GH-90000
            .build(); // GH-90000

        ExecutorService executor = Executors.newFixedThreadPool(20); // GH-90000
        CountDownLatch latch = new CountDownLatch(100); // GH-90000

        for (int i = 0; i < 100; i++) { // GH-90000
            final int index = i;
            executor.submit(() -> { // GH-90000
                try {
                    String result = breaker.execute(() -> { // GH-90000
                        if (index % 3 == 0) { // GH-90000
                            throw new RuntimeException("Simulated failure");
                        }
                        return "success_" + index;
                    });
                    successCount.incrementAndGet(); // GH-90000
                } catch (Exception e) { // GH-90000
                    failureCount.incrementAndGet(); // GH-90000
                } finally {
                    latch.countDown(); // GH-90000
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS); // GH-90000
        executor.shutdown(); // GH-90000

        assertThat(successCount.get() + failureCount.get()).isEqualTo(100); // GH-90000
        // Circuit should remain closed since failures are below threshold
        assertThat(breaker.getState()).isEqualTo(CircuitState.CLOSED); // GH-90000
    }

    // Helper classes for error path testing

    static class ExponentialBackoffRetry {
        private final int maxRetries;
        private final Duration initialBackoff;
        private final Duration maxBackoff;
        private final double backoffMultiplier;
        private final List<Class<? extends Exception>> retryableExceptions;

        private ExponentialBackoffRetry(Builder builder) { // GH-90000
            this.maxRetries = builder.maxRetries;
            this.initialBackoff = builder.initialBackoff;
            this.maxBackoff = builder.maxBackoff;
            this.backoffMultiplier = builder.backoffMultiplier;
            this.retryableExceptions = builder.retryableExceptions;
        }

        <T> T execute(Supplier<T> operation) { // GH-90000
            Exception lastException = null;
            for (int attempt = 0; attempt <= maxRetries; attempt++) { // GH-90000
                try {
                    return operation.get(); // GH-90000
                } catch (Exception e) { // GH-90000
                    lastException = e;
                    
                    // Check if exception is retryable
                    if (!isRetryable(e)) { // GH-90000
                        throw e;
                    }
                    
                    // Don't sleep after last attempt
                    if (attempt < maxRetries) { // GH-90000
                        Duration backoff = calculateBackoff(attempt); // GH-90000
                        try {
                            Thread.sleep(backoff.toMillis()); // GH-90000
                        } catch (InterruptedException ie) { // GH-90000
                            Thread.currentThread().interrupt(); // GH-90000
                            throw new RuntimeException("Interrupted during retry", ie); // GH-90000
                        }
                    }
                }
            }
            throw new RuntimeException("Operation failed after " + (maxRetries + 1) + " attempts", lastException); // GH-90000
        }

        private boolean isRetryable(Exception e) { // GH-90000
            if (retryableExceptions.isEmpty()) { // GH-90000
                return true; // Retry all by default
            }
            return retryableExceptions.stream().anyMatch(clazz -> clazz.isInstance(e)); // GH-90000
        }

        private Duration calculateBackoff(int attempt) { // GH-90000
            long backoffMs = (long) (initialBackoff.toMillis() * Math.pow(backoffMultiplier, attempt)); // GH-90000
            backoffMs = Math.min(backoffMs, maxBackoff.toMillis()); // GH-90000
            return Duration.ofMillis(backoffMs); // GH-90000
        }

        static class Builder {
            private int maxRetries = 3;
            private Duration initialBackoff = Duration.ofMillis(100); // GH-90000
            private Duration maxBackoff = Duration.ofSeconds(30); // GH-90000
            private double backoffMultiplier = 2.0;
            private List<Class<? extends Exception>> retryableExceptions = List.of(); // GH-90000

            Builder maxRetries(int maxRetries) { // GH-90000
                this.maxRetries = maxRetries;
                return this;
            }

            Builder initialBackoff(Duration initialBackoff) { // GH-90000
                this.initialBackoff = initialBackoff;
                return this;
            }

            Builder maxBackoff(Duration maxBackoff) { // GH-90000
                this.maxBackoff = maxBackoff;
                return this;
            }

            Builder backoffMultiplier(double backoffMultiplier) { // GH-90000
                this.backoffMultiplier = backoffMultiplier;
                return this;
            }

            Builder retryableExceptions(List<Class<? extends Exception>> retryableExceptions) { // GH-90000
                this.retryableExceptions = retryableExceptions;
                return this;
            }

            ExponentialBackoffRetry build() { // GH-90000
                return new ExponentialBackoffRetry(this); // GH-90000
            }
        }
    }

    static class TimeoutExecutor {
        private final Duration timeout;

        TimeoutExecutor(Duration timeout) { // GH-90000
            this.timeout = timeout;
        }

        <T> T execute(Supplier<T> operation) throws InterruptedException, java.util.concurrent.TimeoutException { // GH-90000
            ExecutorService executor = Executors.newSingleThreadExecutor(); // GH-90000
            try {
                java.util.concurrent.Future<T> future = executor.submit(operation::get); // GH-90000
                return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS); // GH-90000
            } catch (java.util.concurrent.TimeoutException e) { // GH-90000
                throw new TimeoutException("Operation timed out after " + timeout.toMillis() + "ms"); // GH-90000
            } catch (Exception e) { // GH-90000
                throw new RuntimeException("Operation failed", e); // GH-90000
            } finally {
                executor.shutdownNow(); // GH-90000
            }
        }
    }

    static class TimeoutException extends RuntimeException {
        TimeoutException(String message) { // GH-90000
            super(message); // GH-90000
        }
    }

    static class CircuitBreaker {
        private final int failureThreshold;
        private final Duration timeout;
        private CircuitState state = CircuitState.CLOSED;
        private int failureCount = 0;
        private Instant lastFailureTime;

        private CircuitBreaker(Builder builder) { // GH-90000
            this.failureThreshold = builder.failureThreshold;
            this.timeout = builder.timeout;
        }

        <T> T execute(Supplier<T> operation) { // GH-90000
            CircuitState currentState = getState(); // GH-90000
            
            if (currentState == CircuitState.OPEN) { // GH-90000
                throw new CircuitOpenException("Circuit breaker is OPEN");
            }

            try {
                T result = operation.get(); // GH-90000
                recordSuccess(); // GH-90000
                return result;
            } catch (Exception e) { // GH-90000
                recordFailure(); // GH-90000
                throw e;
            }
        }

        CircuitState getState() { // GH-90000
            if (state == CircuitState.OPEN &&  // GH-90000
                lastFailureTime != null &&
                lastFailureTime.plus(timeout).isBefore(Instant.now())) { // GH-90000
                state = CircuitState.HALF_OPEN;
            }
            return state;
        }

        void recordSuccess() { // GH-90000
            if (state == CircuitState.HALF_OPEN) { // GH-90000
                state = CircuitState.CLOSED;
                failureCount = 0;
            }
        }

        void recordFailure() { // GH-90000
            failureCount++;
            lastFailureTime = Instant.now(); // GH-90000
            
            if (state == CircuitState.HALF_OPEN || failureCount >= failureThreshold) { // GH-90000
                state = CircuitState.OPEN;
            }
        }

        void forceState(CircuitState newState) { // GH-90000
            this.state = newState;
        }

        static class Builder {
            private int failureThreshold = 5;
            private Duration timeout = Duration.ofSeconds(60); // GH-90000

            Builder failureThreshold(int failureThreshold) { // GH-90000
                this.failureThreshold = failureThreshold;
                return this;
            }

            Builder timeout(Duration timeout) { // GH-90000
                this.timeout = timeout;
                return this;
            }

            CircuitBreaker build() { // GH-90000
                return new CircuitBreaker(this); // GH-90000
            }
        }
    }

    enum CircuitState {
        CLOSED, OPEN, HALF_OPEN
    }

    static class CircuitOpenException extends RuntimeException {
        CircuitOpenException(String message) { // GH-90000
            super(message); // GH-90000
        }
    }

    static class RateLimiter {
        private final int permitsPerSecond;
        private final Duration window;
        private final ConcurrentHashMap<Long, AtomicInteger> counters = new ConcurrentHashMap<>(); // GH-90000
        private final AtomicLong currentWindow = new AtomicLong(0); // GH-90000

        RateLimiter(int permitsPerSecond, Duration window) { // GH-90000
            this.permitsPerSecond = permitsPerSecond;
            this.window = window;
        }

        boolean tryAcquire() { // GH-90000
            long now = System.currentTimeMillis(); // GH-90000
            long windowStart = (now / window.toMillis()) * window.toMillis(); // GH-90000
            
            if (currentWindow.get() != windowStart) { // GH-90000
                counters.clear(); // GH-90000
                currentWindow.set(windowStart); // GH-90000
            }

            AtomicInteger counter = counters.computeIfAbsent(windowStart, k -> new AtomicInteger(0)); // GH-90000
            return counter.incrementAndGet() <= permitsPerSecond; // GH-90000
        }
    }

    static class ResilientChain<T> {
        private final Supplier<T> primary;
        private final Supplier<T> fallback;
        private final ExponentialBackoffRetry retry;

        private ResilientChain(Builder<T> builder) { // GH-90000
            this.primary = builder.primary;
            this.fallback = builder.fallback;
            this.retry = builder.retry;
        }

        T execute() { // GH-90000
            try {
                return retry.execute(primary); // GH-90000
            } catch (Exception e) { // GH-90000
                if (fallback != null) { // GH-90000
                    return fallback.get(); // GH-90000
                }
                throw e;
            }
        }

        static class Builder<T> {
            private Supplier<T> primary;
            private Supplier<T> fallback;
            private ExponentialBackoffRetry retry;

            Builder<T> withPrimary(Supplier<T> primary) { // GH-90000
                this.primary = primary;
                return this;
            }

            Builder<T> withFallback(Supplier<T> fallback) { // GH-90000
                this.fallback = fallback;
                return this;
            }

            Builder<T> withRetry(ExponentialBackoffRetry retry) { // GH-90000
                this.retry = retry;
                return this;
            }

            ResilientChain<T> build() { // GH-90000
                return new ResilientChain<>(this); // GH-90000
            }
        }
    }

    static class ErrorMetrics {
        private final AtomicLong totalAttempts = new AtomicLong(0); // GH-90000
        private final AtomicLong successCount = new AtomicLong(0); // GH-90000
        private final AtomicLong failureCount = new AtomicLong(0); // GH-90000
        private final ConcurrentHashMap<String, AtomicLong> errorBreakdown = new ConcurrentHashMap<>(); // GH-90000

        void recordAttempt() { // GH-90000
            totalAttempts.incrementAndGet(); // GH-90000
        }

        void recordSuccess() { // GH-90000
            successCount.incrementAndGet(); // GH-90000
        }

        void recordFailure(String errorType) { // GH-90000
            failureCount.incrementAndGet(); // GH-90000
            errorBreakdown.computeIfAbsent(errorType, k -> new AtomicLong(0)).incrementAndGet(); // GH-90000
        }

        long getTotalAttempts() { // GH-90000
            return totalAttempts.get(); // GH-90000
        }

        long getSuccessCount() { // GH-90000
            return successCount.get(); // GH-90000
        }

        long getFailureCount() { // GH-90000
            return failureCount.get(); // GH-90000
        }

        double getFailureRate() { // GH-90000
            long total = getTotalAttempts(); // GH-90000
            return total == 0 ? 0.0 : (double) failureCount.get() / total; // GH-90000
        }

        Map<String, Long> getErrorBreakdown() { // GH-90000
            Map<String, Long> result = new HashMap<>(); // GH-90000
            errorBreakdown.forEach((k, v) -> result.put(k, v.get())); // GH-90000
            return result;
        }
    }

    static class DependencyManager {
        private final ConcurrentHashMap<String, Supplier<Object>> dependencies = new ConcurrentHashMap<>(); // GH-90000
        private final ConcurrentHashMap<String, Object> cachedResponses = new ConcurrentHashMap<>(); // GH-90000

        void registerDependency(String name, Supplier<Object> supplier) { // GH-90000
            dependencies.put(name, supplier); // GH-90000
        }

        void setCachedResponse(String name, Object response) { // GH-90000
            cachedResponses.put(name, response); // GH-90000
        }

        String executeWithFallback(String dependencyName, String defaultValue) { // GH-90000
            Supplier<Object> dependency = dependencies.get(dependencyName); // GH-90000
            if (dependency == null) { // GH-90000
                return defaultValue;
            }

            try {
                return (String) dependency.get(); // GH-90000
            } catch (Exception e) { // GH-90000
                Object cached = cachedResponses.get(dependencyName); // GH-90000
                return cached != null ? (String) cached : defaultValue; // GH-90000
            }
        }
    }
}

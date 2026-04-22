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
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive Error Path Tests
 *
 * Tests comprehensive error handling patterns including:
 * - Failure modes (network failures, timeouts, rate limits, dependency failures)
 * - Retries (exponential backoff, max retries, retryable vs non-retryable errors)
 * - Timeouts (connection timeouts, read timeouts, request timeouts)
 * - Circuit breakers (state transitions, failure thresholds, recovery)
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
    void shouldHandleNetworkFailuresWithExponentialBackoffRetry() throws Exception {
        AtomicInteger attemptCount = new AtomicInteger(0);
        List<Long> attemptTimestamps = new ArrayList<>();
        
        Supplier<String> networkOperation = () -> {
            attemptTimestamps.add(System.currentTimeMillis());
            int attempt = attemptCount.incrementAndGet();
            
            if (attempt < 4) {
                throw new RuntimeException("Network timeout - attempt " + attempt);
            }
            
            return "success";
        };

        ExponentialBackoffRetry retry = new ExponentialBackoffRetry.Builder()
            .maxRetries(5)
            .initialBackoff(Duration.ofMillis(100))
            .maxBackoff(Duration.ofSeconds(2))
            .backoffMultiplier(2.0)
            .build();

        String result = retry.execute(networkOperation);

        assertThat(result).isEqualTo("success");
        assertThat(attemptCount.get()).isEqualTo(4);
        assertThat(attemptTimestamps).hasSize(4);
        
        // Verify exponential backoff pattern
        for (int i = 1; i < attemptTimestamps.size(); i++) {
            long diff = attemptTimestamps.get(i) - attemptTimestamps.get(i - 1);
            long expectedMin = (long) (100 * Math.pow(2, i - 1));
            assertThat(diff).isGreaterThanOrEqualTo(expectedMin - 50); // Allow some variance
        }
    }

    @Test
    @DisplayName("Should fail after max retries exhausted")
    void shouldFailAfterMaxRetriesExhausted() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        
        Supplier<String> failingOperation = () -> {
            attemptCount.incrementAndGet();
            throw new RuntimeException("Persistent failure");
        };

        ExponentialBackoffRetry retry = new ExponentialBackoffRetry.Builder()
            .maxRetries(3)
            .initialBackoff(Duration.ofMillis(10))
            .maxBackoff(Duration.ofMillis(100))
            .backoffMultiplier(2.0)
            .build();

        try {
            retry.execute(failingOperation);
            throw new AssertionError("Should have thrown exception after max retries");
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).isEqualTo("Persistent failure");
            assertThat(attemptCount.get()).isEqualTo(4); // Initial attempt + 3 retries
        }
    }

    @Test
    @DisplayName("Should not retry non-retryable errors")
    void shouldNotRetryNonRetryableErrors() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        
        Supplier<String> nonRetryableOperation = () -> {
            attemptCount.incrementAndGet();
            throw new IllegalArgumentException("Invalid argument - not retryable");
        };

        ExponentialBackoffRetry retry = new ExponentialBackoffRetry.Builder()
            .maxRetries(5)
            .initialBackoff(Duration.ofMillis(10))
            .maxBackoff(Duration.ofMillis(100))
            .retryableExceptions(List.of(RuntimeException.class))
            .build();

        try {
            retry.execute(nonRetryableOperation);
            throw new AssertionError("Should have thrown exception");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("Invalid argument - not retryable");
            assertThat(attemptCount.get()).isEqualTo(1); // Should not retry
        }
    }

    @Test
    @DisplayName("Should handle connection timeouts gracefully")
    void shouldHandleConnectionTimeoutsGracefully() throws Exception {
        Supplier<String> slowOperation = () -> {
            try {
                Thread.sleep(2000); // Simulate slow operation
                return "success";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted", e);
            }
        };

        TimeoutExecutor executor = new TimeoutExecutor(Duration.ofMillis(100));

        try {
            executor.execute(slowOperation);
            throw new AssertionError("Should have timed out");
        } catch (TimeoutException | InterruptedException e) {
            assertThat(e.getMessage()).contains("Operation timed out");
        }
    }

    @Test
    @DisplayName("Should handle read timeouts during streaming")
    void shouldHandleReadTimeoutsDuringStreaming() throws Exception {
        AtomicInteger dataChunks = new AtomicInteger(0);
        
        Supplier<List<String>> streamingOperation = () -> {
            List<String> chunks = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                try {
                    Thread.sleep(500); // Simulate slow data
                    chunks.add("chunk_" + i);
                    dataChunks.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted", e);
                }
            }
            return chunks;
        };

        TimeoutExecutor executor = new TimeoutExecutor(Duration.ofMillis(1200));

        try {
            executor.execute(streamingOperation);
            throw new AssertionError("Should have timed out during streaming");
        } catch (TimeoutException | InterruptedException e) {
            assertThat(dataChunks.get()).isLessThan(10); // Should have timed out before completion
            assertThat(dataChunks.get()).isGreaterThan(0); // Should have processed some data
        }
    }

    @Test
    @DisplayName("Circuit breaker should open after consecutive failures")
    void circuitBreakerShouldOpenAfterConsecutiveFailures() {
        CircuitBreaker breaker = new CircuitBreaker.Builder()
            .failureThreshold(5)
            .timeout(Duration.ofSeconds(30))
            .build();

        AtomicInteger failureCount = new AtomicInteger(0);
        
        for (int i = 0; i < 10; i++) {
            try {
                breaker.execute(() -> {
                    failureCount.incrementAndGet();
                    throw new RuntimeException("Simulated failure");
                });
            } catch (Exception e) {
                // Expected failures
            }
        }

        assertThat(breaker.getState()).isEqualTo(CircuitState.OPEN);
        assertThat(failureCount.get()).isEqualTo(5); // Should stop after threshold
    }

    @Test
    @DisplayName("Circuit breaker should transition to half-open after timeout")
    void circuitBreakerShouldTransitionToHalfOpenAfterTimeout() throws Exception {
        CircuitBreaker breaker = new CircuitBreaker.Builder()
            .failureThreshold(3)
            .timeout(Duration.ofMillis(100))
            .build();

        // Trigger failures to open circuit
        for (int i = 0; i < 3; i++) {
            try {
                breaker.execute(() -> {
                    throw new RuntimeException("Failure");
                });
            } catch (Exception e) {
                // Expected
            }
        }

        assertThat(breaker.getState()).isEqualTo(CircuitState.OPEN);

        // Wait for timeout
        Thread.sleep(150);

        // Circuit should transition to half-open
        assertThat(breaker.getState()).isEqualTo(CircuitState.HALF_OPEN);
    }

    @Test
    @DisplayName("Circuit breaker should close after successful request in half-open state")
    void circuitBreakerShouldCloseAfterSuccessfulRequestInHalfOpenState() {
        CircuitBreaker breaker = new CircuitBreaker.Builder()
            .failureThreshold(3)
            .timeout(Duration.ZERO)
            .build();

        // Manually transition to half-open
        breaker.forceState(CircuitState.HALF_OPEN);

        // Execute successful request
        String result = breaker.execute(() -> "success");

        assertThat(result).isEqualTo("success");
        assertThat(breaker.getState()).isEqualTo(CircuitState.CLOSED);
    }

    @Test
    @DisplayName("Circuit breaker should fail fast when open")
    void circuitBreakerShouldFailFastWhenOpen() {
        CircuitBreaker breaker = new CircuitBreaker.Builder()
            .failureThreshold(3)
            .timeout(Duration.ofSeconds(30))
            .build();

        // Manually open circuit
        breaker.forceState(CircuitState.OPEN);

        long startTime = System.nanoTime();
        
        try {
            breaker.execute(() -> {
                try {
                    Thread.sleep(1000); // This should not execute
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                return "should not reach here";
            });
            throw new AssertionError("Should have thrown CircuitOpenException");
        } catch (CircuitOpenException e) {
            long duration = System.nanoTime() - startTime;
            assertThat(duration).isLessThan(TimeUnit.MILLISECONDS.toNanos(100)); // Should fail fast
        }
    }

    @Test
    @DisplayName("Should handle rate limiting with backpressure")
    void shouldHandleRateLimitingWithBackpressure() throws Exception {
        RateLimiter rateLimiter = new RateLimiter(10, Duration.ofSeconds(1)); // 10 requests per second
        AtomicInteger rejectedCount = new AtomicInteger(0);
        AtomicInteger acceptedCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(20);

        for (int i = 0; i < 20; i++) {
            executor.submit(() -> {
                try {
                    if (rateLimiter.tryAcquire()) {
                        acceptedCount.incrementAndGet();
                    } else {
                        rejectedCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(acceptedCount.get()).isLessThanOrEqualTo(10); // Should accept at most 10
        assertThat(rejectedCount.get()).isGreaterThan(0); // Some should be rejected
    }

    @Test
    @DisplayName("Should handle cascading failures with fallback")
    void shouldHandleCascadingFailuresWithFallback() {
        AtomicInteger primaryFailureCount = new AtomicInteger(0);
        AtomicInteger fallbackSuccessCount = new AtomicInteger(0);
        
        Supplier<String> primaryService = () -> {
            primaryFailureCount.incrementAndGet();
            throw new RuntimeException("Primary service unavailable");
        };

        Supplier<String> fallbackService = () -> {
            fallbackSuccessCount.incrementAndGet();
            return "fallback response";
        };

        ResilientChain<String> chain = new ResilientChain.Builder<String>()
            .withPrimary(primaryService)
            .withFallback(fallbackService)
            .withRetry(new ExponentialBackoffRetry.Builder().maxRetries(2).build())
            .build();

        String chainResult = chain.execute();

        assertThat(chainResult).isEqualTo("fallback response");
        assertThat(primaryFailureCount.get()).isEqualTo(3); // Initial + 2 retries
        assertThat(fallbackSuccessCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should track error metrics for observability")
    void shouldTrackErrorMetricsForObservability() {
        ErrorMetrics metrics = new ErrorMetrics();
        
        Supplier<String> operation = () -> {
            metrics.recordAttempt();
            if (Math.random() < 0.3) {
                metrics.recordFailure("random_failure");
                throw new RuntimeException("Random failure");
            }
            metrics.recordSuccess();
            return "success";
        };

        for (int i = 0; i < 100; i++) {
            try {
                operation.get();
            } catch (Exception e) {
                // Expected some failures
            }
        }

        assertThat(metrics.getTotalAttempts()).isEqualTo(100);
        assertThat(metrics.getSuccessCount()).isGreaterThan(50);
        assertThat(metrics.getFailureCount()).isGreaterThan(10);
        assertThat(metrics.getFailureRate()).isBetween(0.1, 0.5);
        
        Map<String, Long> errorBreakdown = metrics.getErrorBreakdown();
        assertThat(errorBreakdown).containsKey("random_failure");
    }

    @Test
    @DisplayName("Should handle dependency failures gracefully")
    void shouldHandleDependencyFailuresGracefully() {
        AtomicInteger dependencyFailureCount = new AtomicInteger(0);
        AtomicInteger cachedResponseCount = new AtomicInteger(0);
        
        DependencyManager dependencyManager = new DependencyManager();
        
        // Set up a failing dependency
        dependencyManager.registerDependency("critical-service", () -> {
            dependencyFailureCount.incrementAndGet();
            throw new RuntimeException("Critical service unavailable");
        });

        // Set up cached response as fallback
        dependencyManager.setCachedResponse("critical-service", "cached_data");

        // Execute with dependency and fallback
        String result = dependencyManager.executeWithFallback("critical-service", "default_value");

        assertThat(result).isEqualTo("cached_data");
        assertThat(dependencyFailureCount.get()).isEqualTo(1);
        assertThat(cachedResponseCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should handle concurrent error scenarios correctly")
    void shouldHandleConcurrentErrorScenariosCorrectly() throws Exception {
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        CircuitBreaker breaker = new CircuitBreaker.Builder()
            .failureThreshold(10)
            .timeout(Duration.ofSeconds(10))
            .build();

        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(100);

        for (int i = 0; i < 100; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    String result = breaker.execute(() -> {
                        if (index % 3 == 0) {
                            throw new RuntimeException("Simulated failure");
                        }
                        return "success_" + index;
                    });
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get() + failureCount.get()).isEqualTo(100);
        // Circuit should remain closed since failures are below threshold
        assertThat(breaker.getState()).isEqualTo(CircuitState.CLOSED);
    }

    // Helper classes for error path testing

    static class ExponentialBackoffRetry {
        private final int maxRetries;
        private final Duration initialBackoff;
        private final Duration maxBackoff;
        private final double backoffMultiplier;
        private final List<Class<? extends Exception>> retryableExceptions;

        private ExponentialBackoffRetry(Builder builder) {
            this.maxRetries = builder.maxRetries;
            this.initialBackoff = builder.initialBackoff;
            this.maxBackoff = builder.maxBackoff;
            this.backoffMultiplier = builder.backoffMultiplier;
            this.retryableExceptions = builder.retryableExceptions;
        }

        <T> T execute(Supplier<T> operation) {
            Exception lastException = null;
            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                try {
                    return operation.get();
                } catch (Exception e) {
                    lastException = e;
                    
                    // Check if exception is retryable
                    if (!isRetryable(e)) {
                        throw e;
                    }
                    
                    // Don't sleep after last attempt
                    if (attempt < maxRetries) {
                        Duration backoff = calculateBackoff(attempt);
                        try {
                            Thread.sleep(backoff.toMillis());
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Interrupted during retry", ie);
                        }
                    }
                }
            }
            throw new RuntimeException("Operation failed after " + (maxRetries + 1) + " attempts", lastException);
        }

        private boolean isRetryable(Exception e) {
            if (retryableExceptions.isEmpty()) {
                return true; // Retry all by default
            }
            return retryableExceptions.stream().anyMatch(clazz -> clazz.isInstance(e));
        }

        private Duration calculateBackoff(int attempt) {
            long backoffMs = (long) (initialBackoff.toMillis() * Math.pow(backoffMultiplier, attempt));
            backoffMs = Math.min(backoffMs, maxBackoff.toMillis());
            return Duration.ofMillis(backoffMs);
        }

        static class Builder {
            private int maxRetries = 3;
            private Duration initialBackoff = Duration.ofMillis(100);
            private Duration maxBackoff = Duration.ofSeconds(30);
            private double backoffMultiplier = 2.0;
            private List<Class<? extends Exception>> retryableExceptions = List.of();

            Builder maxRetries(int maxRetries) {
                this.maxRetries = maxRetries;
                return this;
            }

            Builder initialBackoff(Duration initialBackoff) {
                this.initialBackoff = initialBackoff;
                return this;
            }

            Builder maxBackoff(Duration maxBackoff) {
                this.maxBackoff = maxBackoff;
                return this;
            }

            Builder backoffMultiplier(double backoffMultiplier) {
                this.backoffMultiplier = backoffMultiplier;
                return this;
            }

            Builder retryableExceptions(List<Class<? extends Exception>> retryableExceptions) {
                this.retryableExceptions = retryableExceptions;
                return this;
            }

            ExponentialBackoffRetry build() {
                return new ExponentialBackoffRetry(this);
            }
        }
    }

    static class TimeoutExecutor {
        private final Duration timeout;

        TimeoutExecutor(Duration timeout) {
            this.timeout = timeout;
        }

        <T> T execute(Supplier<T> operation) throws InterruptedException, java.util.concurrent.TimeoutException {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                java.util.concurrent.Future<T> future = executor.submit(operation::get);
                return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                throw new TimeoutException("Operation timed out after " + timeout.toMillis() + "ms");
            } catch (Exception e) {
                throw new RuntimeException("Operation failed", e);
            } finally {
                executor.shutdownNow();
            }
        }
    }

    static class TimeoutException extends RuntimeException {
        TimeoutException(String message) {
            super(message);
        }
    }

    static class CircuitBreaker {
        private final int failureThreshold;
        private final Duration timeout;
        private CircuitState state = CircuitState.CLOSED;
        private int failureCount = 0;
        private Instant lastFailureTime;

        private CircuitBreaker(Builder builder) {
            this.failureThreshold = builder.failureThreshold;
            this.timeout = builder.timeout;
        }

        <T> T execute(Supplier<T> operation) {
            CircuitState currentState = getState();
            
            if (currentState == CircuitState.OPEN) {
                throw new CircuitOpenException("Circuit breaker is OPEN");
            }

            try {
                T result = operation.get();
                recordSuccess();
                return result;
            } catch (Exception e) {
                recordFailure();
                throw e;
            }
        }

        CircuitState getState() {
            if (state == CircuitState.OPEN && 
                lastFailureTime != null &&
                lastFailureTime.plus(timeout).isBefore(Instant.now())) {
                state = CircuitState.HALF_OPEN;
            }
            return state;
        }

        void recordSuccess() {
            if (state == CircuitState.HALF_OPEN) {
                state = CircuitState.CLOSED;
                failureCount = 0;
            }
        }

        void recordFailure() {
            failureCount++;
            lastFailureTime = Instant.now();
            
            if (state == CircuitState.HALF_OPEN || failureCount >= failureThreshold) {
                state = CircuitState.OPEN;
            }
        }

        void forceState(CircuitState newState) {
            this.state = newState;
        }

        static class Builder {
            private int failureThreshold = 5;
            private Duration timeout = Duration.ofSeconds(60);

            Builder failureThreshold(int failureThreshold) {
                this.failureThreshold = failureThreshold;
                return this;
            }

            Builder timeout(Duration timeout) {
                this.timeout = timeout;
                return this;
            }

            CircuitBreaker build() {
                return new CircuitBreaker(this);
            }
        }
    }

    enum CircuitState {
        CLOSED, OPEN, HALF_OPEN
    }

    static class CircuitOpenException extends RuntimeException {
        CircuitOpenException(String message) {
            super(message);
        }
    }

    static class RateLimiter {
        private final int permitsPerSecond;
        private final Duration window;
        private final ConcurrentHashMap<Long, AtomicInteger> counters = new ConcurrentHashMap<>();
        private final AtomicLong currentWindow = new AtomicLong(0);

        RateLimiter(int permitsPerSecond, Duration window) {
            this.permitsPerSecond = permitsPerSecond;
            this.window = window;
        }

        boolean tryAcquire() {
            long now = System.currentTimeMillis();
            long windowStart = (now / window.toMillis()) * window.toMillis();
            
            if (currentWindow.get() != windowStart) {
                counters.clear();
                currentWindow.set(windowStart);
            }

            AtomicInteger counter = counters.computeIfAbsent(windowStart, k -> new AtomicInteger(0));
            return counter.incrementAndGet() <= permitsPerSecond;
        }
    }

    static class ResilientChain<T> {
        private final Supplier<T> primary;
        private final Supplier<T> fallback;
        private final ExponentialBackoffRetry retry;

        private ResilientChain(Builder<T> builder) {
            this.primary = builder.primary;
            this.fallback = builder.fallback;
            this.retry = builder.retry;
        }

        T execute() {
            try {
                return retry.execute(primary);
            } catch (Exception e) {
                if (fallback != null) {
                    return fallback.get();
                }
                throw e;
            }
        }

        static class Builder<T> {
            private Supplier<T> primary;
            private Supplier<T> fallback;
            private ExponentialBackoffRetry retry;

            Builder<T> withPrimary(Supplier<T> primary) {
                this.primary = primary;
                return this;
            }

            Builder<T> withFallback(Supplier<T> fallback) {
                this.fallback = fallback;
                return this;
            }

            Builder<T> withRetry(ExponentialBackoffRetry retry) {
                this.retry = retry;
                return this;
            }

            ResilientChain<T> build() {
                return new ResilientChain<>(this);
            }
        }
    }

    static class ErrorMetrics {
        private final AtomicLong totalAttempts = new AtomicLong(0);
        private final AtomicLong successCount = new AtomicLong(0);
        private final AtomicLong failureCount = new AtomicLong(0);
        private final ConcurrentHashMap<String, AtomicLong> errorBreakdown = new ConcurrentHashMap<>();

        void recordAttempt() {
            totalAttempts.incrementAndGet();
        }

        void recordSuccess() {
            successCount.incrementAndGet();
        }

        void recordFailure(String errorType) {
            failureCount.incrementAndGet();
            errorBreakdown.computeIfAbsent(errorType, k -> new AtomicLong(0)).incrementAndGet();
        }

        long getTotalAttempts() {
            return totalAttempts.get();
        }

        long getSuccessCount() {
            return successCount.get();
        }

        long getFailureCount() {
            return failureCount.get();
        }

        double getFailureRate() {
            long total = getTotalAttempts();
            return total == 0 ? 0.0 : (double) failureCount.get() / total;
        }

        Map<String, Long> getErrorBreakdown() {
            Map<String, Long> result = new HashMap<>();
            errorBreakdown.forEach((k, v) -> result.put(k, v.get()));
            return result;
        }
    }

    static class DependencyManager {
        private final ConcurrentHashMap<String, Supplier<Object>> dependencies = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Object> cachedResponses = new ConcurrentHashMap<>();

        void registerDependency(String name, Supplier<Object> supplier) {
            dependencies.put(name, supplier);
        }

        void setCachedResponse(String name, Object response) {
            cachedResponses.put(name, response);
        }

        String executeWithFallback(String dependencyName, String defaultValue) {
            Supplier<Object> dependency = dependencies.get(dependencyName);
            if (dependency == null) {
                return defaultValue;
            }

            try {
                return (String) dependency.get();
            } catch (Exception e) {
                Object cached = cachedResponses.get(dependencyName);
                return cached != null ? (String) cached : defaultValue;
            }
        }
    }
}

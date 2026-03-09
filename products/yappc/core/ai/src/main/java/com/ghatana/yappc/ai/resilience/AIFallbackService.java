/*
 * Copyright (c) 2024 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.ai.resilience;

import io.activej.promise.Promise;

import java.util.List;
import java.util.function.Supplier;

/**
 * AI fallback service with circuit breaker and retry logic.
 *
 * <p>Provides resilient AI service invocation:</p>
 * <ul>
 *   <li>Circuit breaker pattern</li>
 *   <li>Exponential backoff retry</li>
 *   <li>Multiple provider fallback</li>
 *   <li>Automatic failover</li>
 * </ul>
 *
 * <p>Migrated from {@code com.ghatana.yappc.framework.ai.AIFallbackService}.</p>
 *
 * @doc.type interface
 * @doc.purpose Resilient AI service invocation with circuit breaker and fallback
 * @doc.layer product
 * @doc.pattern Circuit Breaker, Retry, Fallback
 */
public interface AIFallbackService {

    /**
     * Execute AI operation with fallback chain.
     *
     * @param primary   primary AI provider operation
     * @param fallbacks fallback operations in priority order
     * @param <T>       result type
     * @return result from primary or first successful fallback
     */
    <T> Promise<T> executeWithFallback(
            Supplier<Promise<T>> primary,
            List<Supplier<Promise<T>>> fallbacks
    );

    /**
     * Execute with single fallback.
     *
     * @param primary  primary operation
     * @param fallback fallback operation
     * @param <T>      result type
     * @return result from primary or fallback
     */
    <T> Promise<T> executeWithFallback(
            Supplier<Promise<T>> primary,
            Supplier<Promise<T>> fallback
    );

    /**
     * Execute with retry logic.
     *
     * @param operation operation to execute
     * @param config    retry configuration
     * @param <T>       result type
     * @return operation result
     */
    <T> Promise<T> executeWithRetry(
            Supplier<Promise<T>> operation,
            RetryConfig config
    );

    /**
     * Execute with circuit breaker.
     *
     * @param providerId provider identifier
     * @param operation  operation to execute
     * @param <T>        result type
     * @return operation result
     */
    <T> Promise<T> executeWithCircuitBreaker(
            String providerId,
            Supplier<Promise<T>> operation
    );

    /**
     * Get circuit breaker state for provider.
     *
     * @param providerId provider identifier
     * @return circuit breaker state
     */
    Promise<CircuitBreakerState> getCircuitBreakerState(String providerId);

    /**
     * Reset circuit breaker for provider.
     *
     * @param providerId provider identifier
     * @return void promise
     */
    Promise<Void> resetCircuitBreaker(String providerId);

    /**
     * Get provider health status.
     *
     * @param providerId provider identifier
     * @return health status
     */
    Promise<ProviderHealth> getProviderHealth(String providerId);

    // ========================================================================
    // Nested Types
    // ========================================================================

    /**
     * Retry configuration with exponential backoff support.
     *
     * @doc.type class
     * @doc.purpose Retry policy configuration
     * @doc.layer product
     * @doc.pattern Builder
     */
    class RetryConfig {
        private final int maxAttempts;
        private final long initialDelayMs;
        private final long maxDelayMs;
        private final double backoffMultiplier;
        private final boolean useJitter;

        private RetryConfig(Builder builder) {
            this.maxAttempts = builder.maxAttempts;
            this.initialDelayMs = builder.initialDelayMs;
            this.maxDelayMs = builder.maxDelayMs;
            this.backoffMultiplier = builder.backoffMultiplier;
            this.useJitter = builder.useJitter;
        }

        /** Create a new builder. */
        public static Builder builder() {
            return new Builder();
        }

        /** Create default retry configuration (3 attempts, 1s initial, 30s max, 2x backoff, jitter on). */
        public static RetryConfig defaults() {
            return builder().build();
        }

        public int getMaxAttempts() { return maxAttempts; }
        public long getInitialDelayMs() { return initialDelayMs; }
        public long getMaxDelayMs() { return maxDelayMs; }
        public double getBackoffMultiplier() { return backoffMultiplier; }
        public boolean isUseJitter() { return useJitter; }

        /**
         * Builder for {@link RetryConfig}.
         */
        public static class Builder {
            private int maxAttempts = 3;
            private long initialDelayMs = 1000;
            private long maxDelayMs = 30_000;
            private double backoffMultiplier = 2.0;
            private boolean useJitter = true;

            public Builder maxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; return this; }
            public Builder initialDelayMs(long initialDelayMs) { this.initialDelayMs = initialDelayMs; return this; }
            public Builder maxDelayMs(long maxDelayMs) { this.maxDelayMs = maxDelayMs; return this; }
            public Builder backoffMultiplier(double backoffMultiplier) { this.backoffMultiplier = backoffMultiplier; return this; }
            public Builder useJitter(boolean useJitter) { this.useJitter = useJitter; return this; }

            public RetryConfig build() {
                return new RetryConfig(this);
            }
        }
    }

    /**
     * Circuit breaker state.
     */
    enum CircuitBreakerState {
        /** Normal operation — requests pass through. */
        CLOSED,
        /** Failing — requests rejected immediately. */
        OPEN,
        /** Testing recovery — limited requests allowed. */
        HALF_OPEN
    }

    /**
     * Provider health snapshot.
     *
     * @doc.type class
     * @doc.purpose AI provider health metrics
     * @doc.layer product
     * @doc.pattern Value Object
     */
    class ProviderHealth {
        private final String providerId;
        private final HealthStatus status;
        private final long successCount;
        private final long failureCount;
        private final double successRate;
        private final long avgResponseTimeMs;

        public ProviderHealth(String providerId, HealthStatus status,
                              long successCount, long failureCount,
                              double successRate, long avgResponseTimeMs) {
            this.providerId = providerId;
            this.status = status;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.successRate = successRate;
            this.avgResponseTimeMs = avgResponseTimeMs;
        }

        public String getProviderId() { return providerId; }
        public HealthStatus getStatus() { return status; }
        public long getSuccessCount() { return successCount; }
        public long getFailureCount() { return failureCount; }
        public double getSuccessRate() { return successRate; }
        public long getAvgResponseTimeMs() { return avgResponseTimeMs; }
    }

    /**
     * Provider health status.
     */
    enum HealthStatus {
        HEALTHY,
        DEGRADED,
        UNHEALTHY
    }
}

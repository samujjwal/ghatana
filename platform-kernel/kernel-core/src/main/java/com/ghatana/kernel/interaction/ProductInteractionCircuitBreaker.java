package com.ghatana.kernel.interaction;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Circuit breaker and retry wrapper for product interaction handlers.
 *
 * <p>Provides resilience patterns for product interactions:
 * <ul>
 *   <li>Circuit breaker to prevent cascading failures</li>
 *   <li>Retry with exponential backoff for transient failures</li>
 *   <li>Per-contract isolation to prevent one bad contract from affecting others</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Circuit breaker and retry wrapper for product interaction handlers
 * @doc.layer kernel
 * @doc.pattern Resilience
 */
public final class ProductInteractionCircuitBreaker {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final Map<String, CircuitBreaker> circuitBreakersByContract;
    private final Map<String, Retry> retriesByContract;
    private final CircuitBreakerConfig circuitBreakerConfig;
    private final RetryConfig retryConfig;

    private ProductInteractionCircuitBreaker(Builder builder) {
        this.circuitBreakerConfig = builder.circuitBreakerConfig;
        this.retryConfig = builder.retryConfig;
        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(builder.circuitBreakerConfig);
        this.retryRegistry = RetryRegistry.of(builder.retryConfig);
        this.circuitBreakersByContract = new ConcurrentHashMap<>();
        this.retriesByContract = new ConcurrentHashMap<>();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Executes a supplier with circuit breaker and retry protection.
     *
     * @param contractId the contract ID for isolation
     * @param supplier the supplier to execute
     * @param <T> the return type
     * @return the result of the supplier
     * @throws Exception if the circuit breaker is open or all retries are exhausted
     */
    public <T> T execute(String contractId, Supplier<T> supplier) throws Exception {
        CircuitBreaker circuitBreaker = circuitBreakersByContract.computeIfAbsent(
                contractId,
                id -> circuitBreakerRegistry.circuitBreaker(id));
        Retry retry = retriesByContract.computeIfAbsent(
                contractId,
                id -> retryRegistry.retry(id));

        Supplier<T> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker, supplier);
        decoratedSupplier = Retry.decorateSupplier(retry, decoratedSupplier);

        try {
            return decoratedSupplier.get();
        } catch (Exception e) {
            if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
                throw new ProductInteractionCircuitBreakerOpenException(
                        "Circuit breaker is open for contract: " + contractId, e);
            }
            throw e;
        }
    }

    /**
     * Returns the circuit breaker state for a contract.
     *
     * @param contractId the contract ID
     * @return the circuit breaker state
     */
    public CircuitBreaker.State getCircuitBreakerState(String contractId) {
        CircuitBreaker circuitBreaker = circuitBreakersByContract.get(contractId);
        return circuitBreaker != null ? circuitBreaker.getState() : CircuitBreaker.State.CLOSED;
    }

    /**
     * Returns circuit breaker metrics for a contract.
     *
     * @param contractId the contract ID
     * @return the circuit breaker metrics
     */
    public CircuitBreaker.Metrics getCircuitBreakerMetrics(String contractId) {
        CircuitBreaker circuitBreaker = circuitBreakersByContract.get(contractId);
        return circuitBreaker != null ? circuitBreaker.getMetrics() : null;
    }

    /**
     * Resets the circuit breaker for a contract to closed state.
     *
     * @param contractId the contract ID
     */
    public void resetCircuitBreaker(String contractId) {
        CircuitBreaker circuitBreaker = circuitBreakersByContract.get(contractId);
        if (circuitBreaker != null) {
            circuitBreaker.reset();
        }
    }

    /**
     * Resets all circuit breakers to closed state.
     */
    public void resetAllCircuitBreakers() {
        circuitBreakersByContract.values().forEach(CircuitBreaker::reset);
    }

    public static final class Builder {
        private CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .slidingWindowSize(10)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .build();
        private RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(100))
                .retryOnException(e -> true)
                .build();

        public Builder circuitBreakerConfig(CircuitBreakerConfig config) {
            this.circuitBreakerConfig = config;
            return this;
        }

        public Builder retryConfig(RetryConfig config) {
            this.retryConfig = config;
            return this;
        }

        public Builder failureRateThreshold(int failureRateThreshold) {
            this.circuitBreakerConfig = CircuitBreakerConfig.from(circuitBreakerConfig)
                    .failureRateThreshold(failureRateThreshold)
                    .build();
            return this;
        }

        public Builder waitDurationInOpenState(Duration waitDuration) {
            this.circuitBreakerConfig = CircuitBreakerConfig.from(circuitBreakerConfig)
                    .waitDurationInOpenState(waitDuration)
                    .build();
            return this;
        }

        public Builder maxRetryAttempts(int maxAttempts) {
            this.retryConfig = RetryConfig.from(retryConfig)
                    .maxAttempts(maxAttempts)
                    .build();
            return this;
        }

        public Builder retryWaitDuration(Duration waitDuration) {
            this.retryConfig = RetryConfig.from(retryConfig)
                    .waitDuration(waitDuration)
                    .build();
            return this;
        }

        public ProductInteractionCircuitBreaker build() {
            return new ProductInteractionCircuitBreaker(this);
        }
    }

    /**
     * Exception thrown when circuit breaker is open.
     */
    public static class ProductInteractionCircuitBreakerOpenException extends Exception {
        public ProductInteractionCircuitBreakerOpenException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

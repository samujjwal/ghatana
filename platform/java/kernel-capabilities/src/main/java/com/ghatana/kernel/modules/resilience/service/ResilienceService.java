/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.modules.resilience.service;

import com.ghatana.kernel.context.KernelContext;
import io.activej.promise.Promise;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Generic resilience service.
 *
 * <p>Provides product-agnostic resilience patterns including circuit breaker,
 * retry mechanisms, bulkhead pattern, timeout management, and fallback patterns.
 * This service contains NO finance-specific logic.</p>
 *
 * @doc.type class
 * @doc.purpose Generic resilience service - circuit breaker, retry, bulkhead, timeout, fallback
 * @doc.layer kernel
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class ResilienceService {

    private static final Logger log = LoggerFactory.getLogger(ResilienceService.class);

    private final KernelContext context;
    private final Executor executor;
    private final ConcurrentHashMap<String, CircuitBreaker> circuitBreakers;
    private final ConcurrentHashMap<String, Retry> retryMechanisms;
    private final ConcurrentHashMap<String, Bulkhead> bulkheads;
    private final ConcurrentHashMap<String, TimeLimiter> timeLimiters;
    private volatile boolean started = false;

    /**
     * Creates a new resilience service.
     *
     * @param context the kernel context
     */
    public ResilienceService(KernelContext context) {
        this.context = context;
        this.executor = context.getExecutor("resilience");
        this.circuitBreakers = new ConcurrentHashMap<>();
        this.retryMechanisms = new ConcurrentHashMap<>();
        this.bulkheads = new ConcurrentHashMap<>();
        this.timeLimiters = new ConcurrentHashMap<>();
    }

    /**
     * Starts the resilience service.
     */
    public void start() {
        log.info("Starting resilience service");
        started = true;
        log.info("Resilience service started");
    }

    /**
     * Stops the resilience service.
     */
    public void stop() {
        log.info("Stopping resilience service");
        started = false;
        log.info("Resilience service stopped");
    }

    /**
     * Checks if the service is healthy.
     *
     * @return true if healthy
     */
    public boolean isHealthy() {
        return started;
    }

    /**
     * Executes an operation with circuit breaker protection.
     *
     * @param name      circuit breaker name
     * @param operation the operation to execute
     * @param <T>       return type
     * @return Promise containing the result
     */
    public <T> Promise<T> executeWithCircuitBreaker(String name, Supplier<T> operation) {
        if (!started) {
            return Promise.ofException(new IllegalStateException("Resilience service not started"));
        }

        return Promise.ofBlocking(executor, () -> {
            CircuitBreaker circuitBreaker = circuitBreakers.computeIfAbsent(name, this::createCircuitBreaker);
            
            Supplier<T> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker, operation);
            
            try {
                T result = decoratedSupplier.get();
                log.debug("Operation executed successfully with circuit breaker: {}", name);
                return result;
            } catch (Exception e) {
                log.warn("Operation failed with circuit breaker: {}", name, e);
                throw e;
            }
        });
    }

    /**
     * Executes an operation with retry mechanism.
     *
     * @param name      retry mechanism name
     * @param operation the operation to execute
     * @param <T>       return type
     * @return Promise containing the result
     */
    public <T> Promise<T> executeWithRetry(String name, Supplier<T> operation) {
        if (!started) {
            return Promise.ofException(new IllegalStateException("Resilience service not started"));
        }

        return Promise.ofBlocking(executor, () -> {
            Retry retry = retryMechanisms.computeIfAbsent(name, this::createRetry);
            
            Supplier<T> decoratedSupplier = Retry.decorateSupplier(retry, operation);
            
            try {
                T result = decoratedSupplier.get();
                log.debug("Operation executed successfully with retry: {}", name);
                return result;
            } catch (Exception e) {
                log.warn("Operation failed after retries: {}", name, e);
                throw e;
            }
        });
    }

    /**
     * Executes an operation with bulkhead protection.
     *
     * @param name      bulkhead name
     * @param operation the operation to execute
     * @param <T>       return type
     * @return Promise containing the result
     */
    public <T> Promise<T> executeWithBulkhead(String name, Supplier<T> operation) {
        if (!started) {
            return Promise.ofException(new IllegalStateException("Resilience service not started"));
        }

        return Promise.ofBlocking(executor, () -> {
            Bulkhead bulkhead = bulkheads.computeIfAbsent(name, this::createBulkhead);
            
            Supplier<T> decoratedSupplier = Bulkhead.decorateSupplier(bulkhead, operation);
            
            try {
                T result = decoratedSupplier.get();
                log.debug("Operation executed successfully with bulkhead: {}", name);
                return result;
            } catch (Exception e) {
                log.warn("Operation failed with bulkhead: {}", name, e);
                throw e;
            }
        });
    }

    /**
     * Executes an operation with timeout protection.
     *
     * @param name      timeout name
     * @param operation the operation to execute
     * @param <T>       return type
     * @return Promise containing the result
     */
    public <T> Promise<T> executeWithTimeout(String name, Supplier<T> operation) {
        if (!started) {
            return Promise.ofException(new IllegalStateException("Resilience service not started"));
        }

        return Promise.ofBlocking(executor, () -> {
            TimeLimiter timeLimiter = timeLimiters.computeIfAbsent(name, this::createTimeLimiter);
            
            try {
                T result = timeLimiter.executeFutureSupplier(() -> 
                    java.util.concurrent.CompletableFuture.supplyAsync(operation));
                log.debug("Operation executed successfully with timeout: {}", name);
                return result;
            } catch (TimeoutException e) {
                log.warn("Operation timed out: {}", name);
                throw new RuntimeException("Operation timed out", e);
            } catch (Exception e) {
                log.warn("Operation failed with timeout: {}", name, e);
                throw e;
            }
        });
    }

    // ==================== Private Methods ====================

    private CircuitBreaker createCircuitBreaker(String name) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50.0f)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(5)
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .build();

        return CircuitBreaker.of(name, config);
    }

    private Retry createRetry(String name) {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(500))
            .retryExceptions(RuntimeException.class)
            .build();

        return Retry.of(name, config);
    }

    private Bulkhead createBulkhead(String name) {
        BulkheadConfig config = BulkheadConfig.custom()
            .maxConcurrentCalls(10)
            .maxWaitDuration(Duration.ofSeconds(5))
            .build();

        return Bulkhead.of(name, config);
    }

    private TimeLimiter createTimeLimiter(String name) {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofSeconds(10))
            .cancelRunningFuture(true)
            .build();

        return TimeLimiter.of(name, config);
    }
}

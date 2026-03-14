/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 *
 * PHASE: A
 * OWNER: @platform-team
 * MIGRATED: 2026-02-04
 * DEPENDS_ON: platform:java:core
 */
package com.ghatana.platform.resilience;

import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Random;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Retry policy with exponential backoff for ActiveJ Promise-based operations.
 *
 * @doc.type class
 * @doc.purpose Configurable retry policy with backoff strategies
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public class RetryPolicy {
    
    private final int maxRetries;
    private final Duration initialDelay;
    private final Duration maxDelay;
    private final double multiplier;
    private final double jitter;
    private final Predicate<Throwable> retryPredicate;
    private final Duration maxDuration;
    private final Random random;
    
    private RetryPolicy(Builder builder) {
        this.maxRetries = builder.maxRetries;
        this.initialDelay = builder.initialDelay;
        this.maxDelay = builder.maxDelay;
        this.multiplier = builder.multiplier;
        this.jitter = builder.jitter;
        this.retryPredicate = builder.retryPredicate;
        this.maxDuration = builder.maxDuration;
        this.random = new Random();
    }
    
    public <T> Promise<T> execute(Eventloop eventloop, Supplier<Promise<T>> operation) {
        return executeWithRetry(eventloop, operation, 0, Instant.now());
    }

    /**
     * Executes the operation with K-18 retry context propagation.
     *
     * <p>The context-aware function receives a {@link RetryContext} on each attempt,
     * enabling callers to log attempt numbers, include idempotency markers, or adjust
     * behaviour on retries.
     *
     * @param eventloop          ActiveJ eventloop for retry delays
     * @param contextualOperation function that receives retry context and returns a Promise
     * @param <T>                 result type
     * @return promise that resolves to the operation result or final failure
     */
    public <T> Promise<T> executeWithContext(
            Eventloop eventloop,
            java.util.function.Function<RetryContext, Promise<T>> contextualOperation) {
        return executeWithContextInternal(eventloop, contextualOperation, 1, Instant.now(), null);
    }

    private <T> Promise<T> executeWithContextInternal(
            Eventloop eventloop,
            java.util.function.Function<RetryContext, Promise<T>> contextualOperation,
            int attempt,
            Instant startTime,
            Throwable lastError) {
        RetryContext ctx = attempt == 1
                ? RetryContext.first(maxRetries + 1)
                : RetryContext.retry(attempt, maxRetries + 1, lastError);

        return contextualOperation.apply(ctx)
                .then((result, error) -> {
                    if (error == null) {
                        return Promise.of(result);
                    }
                    if (!shouldRetry(error, attempt - 1, startTime)) {
                        return Promise.ofException(error);
                    }
                    Duration delay = calculateDelay(attempt - 1);
                    return Promises.delay(delay, eventloop)
                            .then(() -> executeWithContextInternal(
                                    eventloop, contextualOperation, attempt + 1, startTime, error));
                });
    }

    public <T> Promise<T> executeBlocking(
            Eventloop eventloop,
            Supplier<T> operation,
            java.util.concurrent.Executor executor) {
        return execute(eventloop, () -> Promise.ofBlocking(executor, operation::get));
    }
    
    private <T> Promise<T> executeWithRetry(
            Eventloop eventloop,
            Supplier<Promise<T>> operation,
            int attempt,
            Instant startTime) {
        
        return operation.get()
                .then(result -> Promise.of(result))
                .then((result, error) -> {
                    if (error == null) {
                        return Promise.of(result);
                    }
                    
                    if (!shouldRetry(error, attempt, startTime)) {
                        return Promise.ofException(error);
                    }
                    
                    Duration delay = calculateDelay(attempt);
                    
                    return Promises.delay(delay, eventloop)
                            .then(() -> executeWithRetry(eventloop, operation, attempt + 1, startTime));
                });
    }
    
    private boolean shouldRetry(Throwable error, int attempt, Instant startTime) {
        if (attempt >= maxRetries) {
            return false;
        }
        
        if (maxDuration != null && Duration.between(startTime, Instant.now()).compareTo(maxDuration) >= 0) {
            return false;
        }
        
        return retryPredicate.test(error);
    }
    
    private Duration calculateDelay(int attempt) {
        double delayMs = initialDelay.toMillis() * Math.pow(multiplier, attempt);
        delayMs = Math.min(delayMs, maxDelay.toMillis());
        
        if (jitter > 0) {
            double jitterFactor = 1.0 + (random.nextDouble() * 2 - 1) * jitter;
            delayMs *= jitterFactor;
        }
        
        return Duration.ofMillis((long) delayMs);
    }
    
    public static RetryPolicy defaultPolicy() {
        return builder().build();
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private int maxRetries = 3;
        private Duration initialDelay = Duration.ofMillis(100);
        private Duration maxDelay = Duration.ofSeconds(30);
        private double multiplier = 2.0;
        private double jitter = 0.1;
        private Predicate<Throwable> retryPredicate = e -> true;
        private Duration maxDuration = null;
        
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }
        
        public Builder initialDelay(Duration initialDelay) {
            this.initialDelay = Objects.requireNonNull(initialDelay);
            return this;
        }
        
        public Builder maxDelay(Duration maxDelay) {
            this.maxDelay = Objects.requireNonNull(maxDelay);
            return this;
        }
        
        public Builder multiplier(double multiplier) {
            this.multiplier = multiplier;
            return this;
        }
        
        public Builder jitter(double jitter) {
            this.jitter = jitter;
            return this;
        }
        
        public Builder retryIf(Predicate<Throwable> predicate) {
            this.retryPredicate = Objects.requireNonNull(predicate);
            return this;
        }
        
        @SafeVarargs
        public final Builder retryOn(Class<? extends Throwable>... exceptionTypes) {
            this.retryPredicate = error -> {
                for (Class<? extends Throwable> type : exceptionTypes) {
                    if (type.isInstance(error) || 
                        (error.getCause() != null && type.isInstance(error.getCause()))) {
                        return true;
                    }
                }
                return false;
            };
            return this;
        }
        
        public Builder maxDuration(Duration maxDuration) {
            this.maxDuration = maxDuration;
            return this;
        }
        
        public RetryPolicy build() {
            return new RetryPolicy(this);
        }
    }
}

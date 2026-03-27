/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.connector;

import com.ghatana.aep.connector.config.RetryConfig;
import com.ghatana.aep.connector.strategy.QueueMessage;
import com.ghatana.aep.connector.strategy.QueueProducerStrategy;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Decorator that wraps any {@link QueueProducerStrategy} with retry logic from a
 * {@link RetryConfig}. Enables callers to add resilience without modifying the
 * underlying connector implementation.
 *
 * <p>Useful for legacy or external connectors that do not already have built-in retry.
 * The AEP built-in connectors ({@code KafkaProducerStrategy}, etc.) extend
 * {@link AbstractResilientConnector} directly, so this decorator is primarily for
 * third-party or dynamically-registered connectors.
 *
 * <p>Usage:
 * <pre>{@code
 * QueueProducerStrategy resilient = new RetryingConnectorDecorator(
 *     myLegacyStrategy,
 *     RetryConfig.DEFAULT
 * );
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Retry decorator for any QueueProducerStrategy
 * @doc.layer infrastructure
 * @doc.pattern Decorator
 */
public final class RetryingConnectorDecorator implements QueueProducerStrategy {

    private static final Logger log = LoggerFactory.getLogger(RetryingConnectorDecorator.class);

    private final QueueProducerStrategy delegate;
    private final RetryConfig retryConfig;

    /**
     * Wrap {@code delegate} with the given retry policy.
     *
     * @param delegate    the underlying strategy to delegate to
     * @param retryConfig retry policy to apply on send failures
     */
    public RetryingConnectorDecorator(QueueProducerStrategy delegate, RetryConfig retryConfig) {
        this.delegate = Objects.requireNonNull(delegate, "delegate required");
        this.retryConfig = Objects.requireNonNull(retryConfig, "retryConfig required");
    }

    @Override
    public boolean send(QueueMessage message) {
        int attempts = 0;
        long delayMs = retryConfig.initialDelay().toMillis();

        while (attempts < retryConfig.maxAttempts()) {
            try {
                boolean result = delegate.send(message);
                if (result) {
                    return true;
                }
                // Treat false return as a soft failure worth retrying
                attempts++;
                if (attempts < retryConfig.maxAttempts()) {
                    log.warn("Connector returned false on attempt {}/{}, retrying in {}ms.",
                        attempts, retryConfig.maxAttempts(), delayMs);
                    sleep(delayMs);
                    delayMs = nextDelay(delayMs);
                }
            } catch (Exception e) {
                attempts++;
                if (attempts >= retryConfig.maxAttempts()) {
                    log.error("All {} attempts exhausted for message key={}: {}",
                        retryConfig.maxAttempts(), message.getId(), e.getMessage());
                    throw e instanceof RuntimeException re ? re : new RuntimeException(e);
                }
                log.warn("Attempt {}/{} threw exception: {}. Retrying in {}ms.",
                    attempts, retryConfig.maxAttempts(), e.getMessage(), delayMs);
                sleep(delayMs);
                delayMs = nextDelay(delayMs);
            }
        }
        return false;
    }

    @Override
    public Promise<Void> start() {
        return delegate.start();
    }

    @Override
    public Promise<Void> stop() {
        return delegate.stop();
    }

    @Override
    public Promise<Void> flush() {
        return delegate.flush();
    }

    @Override
    public boolean isRunning() {
        return delegate.isRunning();
    }

    private void sleep(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private long nextDelay(long currentMs) {
        return Math.min((long) (currentMs * retryConfig.backoffMultiplier()), retryConfig.maxDelay().toMillis());
    }
}

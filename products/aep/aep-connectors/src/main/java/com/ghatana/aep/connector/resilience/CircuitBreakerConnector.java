/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.connector.resilience;

import com.ghatana.aep.connector.strategy.QueueMessage;
import com.ghatana.aep.connector.strategy.QueueProducerStrategy;
import com.ghatana.platform.resilience.CircuitBreaker;
import io.activej.promise.Promise;

import java.time.Duration;
import java.util.Objects;

/**
 * Circuit-breaker decorator for external queue producers (AEP-014).
 *
 * <p>This decorator fails fast when a downstream producer repeatedly fails,
 * preventing cascading overload during external broker outages. It composes
 * naturally with {@link com.ghatana.aep.connector.RetryingConnectorDecorator}:
 * wrap the delegate with retry first, then place this circuit breaker around it.
 *
 * @doc.type class
 * @doc.purpose Protect external queue producers with a circuit breaker
 * @doc.layer infrastructure
 * @doc.pattern Decorator
 */
public final class CircuitBreakerConnector implements QueueProducerStrategy {

    private final QueueProducerStrategy delegate;
    private final CircuitBreaker circuitBreaker;

    private CircuitBreakerConnector(Builder builder) {
        this.delegate = Objects.requireNonNull(builder.delegate, "delegate required");
        this.circuitBreaker = CircuitBreaker.builder(builder.name)
            .failureThreshold(builder.failureThreshold)
            .successThreshold(builder.successThreshold)
            .resetTimeout(builder.resetTimeout)
            .maxBackoff(builder.maxBackoff)
            .backoffMultiplier(builder.backoffMultiplier)
            .build();
    }

    @Override
    public boolean send(QueueMessage message) {
        try {
            return circuitBreaker.executeSync(() -> {
                boolean result = delegate.send(message);
                if (!result) {
                    throw new ConnectorSendFailedException("delegate returned false for message=" + message.getId());
                }
                return true;
            }, () -> false);
        } catch (CircuitBreaker.CircuitBreakerOpenException ignored) {
            return false;
        } catch (ConnectorSendFailedException ignored) {
            return false;
        }
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

    /**
     * @return current circuit breaker state
     */
    public CircuitBreaker.State state() {
        return circuitBreaker.getState();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link CircuitBreakerConnector}.
     */
    public static final class Builder {
        private QueueProducerStrategy delegate;
        private String name = "aep-connector";
        private int failureThreshold = 5;
        private int successThreshold = 1;
        private Duration resetTimeout = Duration.ofSeconds(30);
        private Duration maxBackoff = Duration.ofMinutes(5);
        private double backoffMultiplier = 2.0;

        private Builder() {
        }

        public Builder delegate(QueueProducerStrategy delegate) {
            this.delegate = delegate;
            return this;
        }

        public Builder name(String name) {
            this.name = Objects.requireNonNull(name, "name required");
            return this;
        }

        public Builder failureThreshold(int failureThreshold) {
            this.failureThreshold = failureThreshold;
            return this;
        }

        public Builder successThreshold(int successThreshold) {
            this.successThreshold = successThreshold;
            return this;
        }

        public Builder resetTimeout(Duration resetTimeout) {
            this.resetTimeout = resetTimeout;
            return this;
        }

        public Builder maxBackoff(Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
            return this;
        }

        public Builder backoffMultiplier(double backoffMultiplier) {
            this.backoffMultiplier = backoffMultiplier;
            return this;
        }

        public CircuitBreakerConnector build() {
            if (failureThreshold < 1) {
                throw new IllegalArgumentException("failureThreshold must be >= 1");
            }
            if (successThreshold < 1) {
                throw new IllegalArgumentException("successThreshold must be >= 1");
            }
            return new CircuitBreakerConnector(this);
        }
    }

    private static final class ConnectorSendFailedException extends RuntimeException {
        private ConnectorSendFailedException(String message) {
            super(message);
        }
    }
}
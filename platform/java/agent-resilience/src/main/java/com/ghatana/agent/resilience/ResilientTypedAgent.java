/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ghatana.agent.resilience;

import com.ghatana.agent.*;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.platform.resilience.CircuitBreaker;
import com.ghatana.platform.resilience.RetryPolicy;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Decorator that wraps any {@link TypedAgent} with a full resilience stack:
 * circuit breaker → bulkhead → retry → timeout.
 *
 * <h2>Execution order</h2>
 * <pre>
 *   process(ctx, input)
 *     └─ bulkhead.acquire()
 *          └─ circuitBreaker.execute()
 *               └─ retryPolicy.execute()
 *                    └─ delegate.process()
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * TypedAgent<Req, Resp> delegate = new MyAgent();
 * TypedAgent<Req, Resp> resilient = ResilientTypedAgent.<Req, Resp>builder()
 *     .delegate(delegate)
 *     .eventloop(eventloop)
 *     .circuitBreaker(CircuitBreaker.builder("my-agent").failureThreshold(5).build())
 *     .retryPolicy(RetryPolicy.builder().maxRetries(3).build())
 *     .bulkhead(AgentBulkhead.of("my-agent", 20))
 *     .build();
 * }</pre>
 *
 * @param <I> input type
 * @param <O> output type
 *
 * @doc.type class
 * @doc.purpose Resilience decorator for TypedAgent (circuit breaker + retry + bulkhead)
 * @doc.layer platform
 * @doc.pattern Decorator
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
public final class ResilientTypedAgent<I, O> implements TypedAgent<I, O> {

    private static final Logger log = LoggerFactory.getLogger(ResilientTypedAgent.class);

    private final TypedAgent<I, O> delegate;
    private final Eventloop eventloop;
    private final CircuitBreaker circuitBreaker;
    private final RetryPolicy retryPolicy;
    private final AgentBulkhead bulkhead;

    private ResilientTypedAgent(Builder<I, O> builder) {
        this.delegate = Objects.requireNonNull(builder.delegate, "delegate");
        this.eventloop = Objects.requireNonNull(builder.eventloop, "eventloop");
        this.circuitBreaker = builder.circuitBreaker;
        this.retryPolicy = builder.retryPolicy;
        this.bulkhead = builder.bulkhead;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TypedAgent delegation — metadata & lifecycle pass-through
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    @NotNull
    public AgentDescriptor descriptor() {
        return delegate.descriptor();
    }

    @Override
    @NotNull
    public Promise<Void> initialize(@NotNull AgentConfig config) {
        return delegate.initialize(config);
    }

    @Override
    @NotNull
    public Promise<Void> shutdown() {
        return delegate.shutdown();
    }

    @Override
    @NotNull
    public Promise<HealthStatus> healthCheck() {
        return delegate.healthCheck().map(status -> {
            if (circuitBreaker != null
                    && circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
                return HealthStatus.DEGRADED;
            }
            if (bulkhead != null && bulkhead.isExhausted()) {
                return HealthStatus.DEGRADED;
            }
            return status;
        });
    }

    @Override
    @NotNull
    public Promise<Void> reconfigure(@NotNull AgentConfig newConfig) {
        return delegate.reconfigure(newConfig);
    }

    @Override
    public boolean validateInput(@NotNull I input) {
        return delegate.validateInput(input);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Processing — resilience stack
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    @NotNull
    public Promise<AgentResult<O>> process(@NotNull AgentContext ctx, @NotNull I input) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(input, "input");

        if (bulkhead != null) {
            if (!bulkhead.tryAcquire()) {
                String agentId = descriptor().getAgentId();
                log.warn("Bulkhead exhausted for agent: {}", agentId);
                return Promise.of(AgentResult.failure(
                        new BulkheadFullException(agentId, bulkhead.getMaxConcurrency()),
                        agentId,
                        Duration.ZERO));
            }
            return executeWithResilience(ctx, input)
                    .whenComplete(() -> bulkhead.release());
        }
        return executeWithResilience(ctx, input);
    }

    @Override
    @NotNull
    public Promise<List<AgentResult<O>>> processBatch(@NotNull AgentContext ctx,
                                                       @NotNull List<I> inputs) {
        return delegate.processBatch(ctx, inputs);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Internal — circuit breaker + retry
    // ═══════════════════════════════════════════════════════════════════════════

    private Promise<AgentResult<O>> executeWithResilience(AgentContext ctx, I input) {
        if (circuitBreaker != null && retryPolicy != null) {
            return circuitBreaker.execute(eventloop,
                    () -> retryPolicy.execute(eventloop,
                            () -> delegate.process(ctx, input)));
        }
        if (circuitBreaker != null) {
            return circuitBreaker.execute(eventloop, () -> delegate.process(ctx, input));
        }
        if (retryPolicy != null) {
            return retryPolicy.execute(eventloop, () -> delegate.process(ctx, input));
        }
        return delegate.process(ctx, input);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Builder
    // ═══════════════════════════════════════════════════════════════════════════

    public static <I, O> Builder<I, O> builder() {
        return new Builder<>();
    }

    public static final class Builder<I, O> {
        private TypedAgent<I, O> delegate;
        private Eventloop eventloop;
        private CircuitBreaker circuitBreaker;
        private RetryPolicy retryPolicy;
        private AgentBulkhead bulkhead;

        private Builder() {}

        public Builder<I, O> delegate(@NotNull TypedAgent<I, O> delegate) {
            this.delegate = delegate;
            return this;
        }

        public Builder<I, O> eventloop(@NotNull Eventloop eventloop) {
            this.eventloop = eventloop;
            return this;
        }

        public Builder<I, O> circuitBreaker(@Nullable CircuitBreaker circuitBreaker) {
            this.circuitBreaker = circuitBreaker;
            return this;
        }

        public Builder<I, O> retryPolicy(@Nullable RetryPolicy retryPolicy) {
            this.retryPolicy = retryPolicy;
            return this;
        }

        public Builder<I, O> bulkhead(@Nullable AgentBulkhead bulkhead) {
            this.bulkhead = bulkhead;
            return this;
        }

        public ResilientTypedAgent<I, O> build() {
            return new ResilientTypedAgent<>(this);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Exceptions
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Thrown when the bulkhead rejects a call because max concurrency is reached.
     */
    public static final class BulkheadFullException extends RuntimeException {
        private final String agentId;
        private final int maxConcurrency;

        public BulkheadFullException(String agentId, int maxConcurrency) {
            super("Bulkhead full for agent '" + agentId + "' (max=" + maxConcurrency + ")");
            this.agentId = agentId;
            this.maxConcurrency = maxConcurrency;
        }

        public String getAgentId() { return agentId; }
        public int getMaxConcurrency() { return maxConcurrency; }
    }
}

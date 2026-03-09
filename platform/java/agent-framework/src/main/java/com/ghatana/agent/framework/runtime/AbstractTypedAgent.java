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

package com.ghatana.agent.framework.runtime;

import com.ghatana.agent.*;
import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Skeletal implementation of {@link TypedAgent} providing common lifecycle,
 * metrics, and error-handling plumbing.
 *
 * <p>Subclasses only need to implement:
 * <ul>
 *   <li>{@link #descriptor()} — return the agent's metadata</li>
 *   <li>{@link #doProcess(AgentContext, Object)} — the actual processing logic</li>
 * </ul>
 *
 * <p>Optionally override:
 * <ul>
 *   <li>{@link #doInitialize(AgentConfig)} — type-specific initialization</li>
 *   <li>{@link #doShutdown()} — type-specific cleanup</li>
 *   <li>{@link #validateInput(Object)} — input validation</li>
 * </ul>
 *
 * <h2>What AbstractTypedAgent Provides</h2>
 * <ul>
 *   <li>Automatic processing-time measurement</li>
 *   <li>Invocation counting (total, success, failure)</li>
 *   <li>Lifecycle state tracking (CREATED → INITIALIZING → READY → PROCESSING → SHUTTING_DOWN → STOPPED)</li>
 *   <li>Config storage</li>
 *   <li>Error wrapping into AgentResult.failure()</li>
 * </ul>
 *
 * <h2>Relationship to BaseAgent</h2>
 * <p>{@link BaseAgent} provides the GAA lifecycle (PERCEIVE→REASON→ACT→CAPTURE→REFLECT)
 * using an {@link com.ghatana.agent.framework.api.OutputGenerator}. This class provides
 * a simpler lifecycle focused on typed input/output processing with structured results.
 * Use {@code BaseAgent} when you need the full GAA cognitive architecture; use
 * {@code AbstractTypedAgent} for typed pipeline agents with AgentResult semantics.</p>
 *
 * @param <I> input type
 * @param <O> output type
 *
 * @doc.type class
 * @doc.purpose Skeletal TypedAgent with lifecycle and metrics
 * @doc.layer framework
 * @doc.pattern Template Method
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
public abstract class AbstractTypedAgent<I, O> implements TypedAgent<I, O> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    // ═══════════════════════════════════════════════════════════════════════════
    // State
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Agent lifecycle state.
     */
    public enum State {
        CREATED, INITIALIZING, READY, PROCESSING, SHUTTING_DOWN, STOPPED
    }

    private final AtomicReference<State> state = new AtomicReference<>(State.CREATED);
    private volatile AgentConfig config;

    // ═══════════════════════════════════════════════════════════════════════════
    // Metrics
    // ═══════════════════════════════════════════════════════════════════════════

    private final AtomicLong totalInvocations = new AtomicLong();
    private final AtomicLong successCount = new AtomicLong();
    private final AtomicLong failureCount = new AtomicLong();
    private final AtomicLong timeoutCount = new AtomicLong();
    private final AtomicLong totalProcessingNanos = new AtomicLong();

    // ═══════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    @NotNull
    public final Promise<Void> initialize(@NotNull AgentConfig config) {
        Objects.requireNonNull(config, "config must not be null");

        if (!state.compareAndSet(State.CREATED, State.INITIALIZING)) {
            State current = state.get();
            if (current == State.READY) {
                // Re-initialization (reconfigure)
                state.set(State.INITIALIZING);
            } else {
                return Promise.ofException(new IllegalStateException(
                        "Cannot initialize agent in state: " + current));
            }
        }

        this.config = config;
        log.info("Initializing agent: {}", descriptor().getAgentId());

        try {
            return doInitialize(config)
                    .whenResult($ -> {
                        state.set(State.READY);
                        log.info("Agent initialized: {}", descriptor().getAgentId());
                    })
                    .whenException(e -> {
                        state.set(State.CREATED);
                        log.error("Agent initialization failed: {}", descriptor().getAgentId(), e);
                    });
        } catch (Exception e) {
            state.set(State.CREATED);
            return Promise.ofException(e);
        }
    }

    @Override
    @NotNull
    public final Promise<Void> shutdown() {
        State current = state.get();
        if (current == State.STOPPED || current == State.SHUTTING_DOWN) {
            return Promise.complete(); // Idempotent
        }

        state.set(State.SHUTTING_DOWN);
        log.info("Shutting down agent: {}", descriptor().getAgentId());

        try {
            return doShutdown()
                    .whenResult($ -> {
                        state.set(State.STOPPED);
                        log.info("Agent stopped: {} (invocations={}, success={}, failures={})",
                                descriptor().getAgentId(),
                                totalInvocations.get(), successCount.get(), failureCount.get());
                    })
                    .whenException(e -> {
                        state.set(State.STOPPED);
                        log.error("Agent shutdown error: {}", descriptor().getAgentId(), e);
                    });
        } catch (Exception e) {
            state.set(State.STOPPED);
            return Promise.ofException(e);
        }
    }

    @Override
    @NotNull
    public Promise<HealthStatus> healthCheck() {
        return switch (state.get()) {
            case READY, PROCESSING -> Promise.of(HealthStatus.HEALTHY);
            case INITIALIZING -> Promise.of(HealthStatus.STARTING);
            case SHUTTING_DOWN -> Promise.of(HealthStatus.STOPPING);
            case CREATED, STOPPED -> Promise.of(HealthStatus.UNHEALTHY);
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Processing (Template Method)
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    @NotNull
    public final Promise<AgentResult<O>> process(@NotNull AgentContext ctx, @NotNull I input) {
        Objects.requireNonNull(ctx, "context must not be null");
        Objects.requireNonNull(input, "input must not be null");

        State current = state.get();
        if (current != State.READY && current != State.PROCESSING) {
            return Promise.of(AgentResult.failure(
                    new IllegalStateException("Agent not ready; state=" + current),
                    descriptor().getAgentId(),
                    Duration.ZERO));
        }

        totalInvocations.incrementAndGet();
        Instant start = Instant.now();
        String agentId = descriptor().getAgentId();

        ctx.recordMetric("agent.invocation.total", 1);
        ctx.addTraceTag("agent.id", agentId);
        ctx.addTraceTag("agent.type", descriptor().getType().name());

        try {
            return doProcess(ctx, input)
                    .map(result -> {
                        Duration elapsed = Duration.between(start, Instant.now());
                        totalProcessingNanos.addAndGet(elapsed.toNanos());

                        // Enrich result with timing if missing
                        AgentResult<O> enriched = result.getProcessingTime() == null
                                ? result.toBuilder().processingTime(elapsed).agentId(agentId).build()
                                : result.getAgentId() == null
                                ? result.toBuilder().agentId(agentId).build()
                                : result;

                        if (enriched.isSuccess()) {
                            successCount.incrementAndGet();
                            ctx.recordMetric("agent.success", 1);
                        } else if (enriched.isFailed()) {
                            failureCount.incrementAndGet();
                            ctx.recordMetric("agent.failure", 1);
                        }

                        ctx.recordMetric("agent.latency.ms", elapsed.toMillis());
                        return enriched;
                    })
                    .whenException(e -> {
                        failureCount.incrementAndGet();
                        ctx.recordMetric("agent.failure", 1);
                        log.error("Agent processing error: agent={}, turn={}",
                                agentId, ctx.getTurnId(), e);
                    });
        } catch (Exception e) {
            Duration elapsed = Duration.between(start, Instant.now());
            failureCount.incrementAndGet();
            ctx.recordMetric("agent.failure", 1);
            return Promise.of(AgentResult.failure(e, agentId, elapsed));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Template Methods — Override in Subclasses
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Type-specific initialization. Default is a no-op.
     *
     * @param config the agent configuration
     * @return a Promise completing when ready
     */
    @NotNull
    protected Promise<Void> doInitialize(@NotNull AgentConfig config) {
        return Promise.complete();
    }

    /**
     * Type-specific processing logic. Implementors focus on input → output
     * transformation; timing, metrics, and error wrapping are handled by the base.
     *
     * @param ctx   execution context
     * @param input the input to process
     * @return a Promise of the result
     */
    @NotNull
    protected abstract Promise<AgentResult<O>> doProcess(@NotNull AgentContext ctx, @NotNull I input);

    /**
     * Type-specific shutdown logic. Default is a no-op.
     *
     * @return a Promise completing when cleanup is done
     */
    @NotNull
    protected Promise<Void> doShutdown() {
        return Promise.complete();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Accessors
    // ═══════════════════════════════════════════════════════════════════════════

    /** Current lifecycle state. */
    public State getState() { return state.get(); }

    /** The agent's current configuration (null before initialize). */
    public AgentConfig getConfig() { return config; }

    /** Total process() invocations. */
    public long getTotalInvocations() { return totalInvocations.get(); }

    /** Successful invocations. */
    public long getSuccessCount() { return successCount.get(); }

    /** Failed invocations. */
    public long getFailureCount() { return failureCount.get(); }

    /** Timed-out invocations. */
    public long getTimeoutCount() { return timeoutCount.get(); }

    /** Average processing time in milliseconds. */
    public double getAverageProcessingTimeMs() {
        long total = totalInvocations.get();
        return total > 0 ? (totalProcessingNanos.get() / 1_000_000.0) / total : 0;
    }
}

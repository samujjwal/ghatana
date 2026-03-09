/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.resilience;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.FailureMode;
import com.ghatana.platform.resilience.CircuitBreaker;
import com.ghatana.platform.resilience.DeadLetterQueue;
import com.ghatana.platform.resilience.RetryPolicy;

import java.time.Duration;
import java.util.Objects;

/**
 * Builds resilience primitives from {@link AgentConfig} declarative settings.
 *
 * <p>This factory bridges the configuration-first paradigm (YAML → AgentConfig)
 * with runtime resilience infrastructure (CircuitBreaker, RetryPolicy, DeadLetterQueue).
 * Each agent's resilience stack is assembled from its config without manual wiring.</p>
 *
 * <p>Usage:
 * <pre>
 * AgentConfig config = agentConfigMaterializer.materialize(yaml);
 * ResilienceStack stack = ResilienceFactory.fromConfig(config);
 *
 * // Use in agent execution pipeline:
 * stack.getCircuitBreaker().execute(eventloop, () -&gt;
 *     stack.getRetryPolicy().execute(eventloop, () -&gt;
 *         agent.process(event)
 *     )
 * );
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Builds resilience primitives (CircuitBreaker, RetryPolicy, DLQ) from AgentConfig declarative settings
 * @doc.layer platform
 * @doc.pattern Factory
 */
public final class ResilienceFactory {

    private ResilienceFactory() {} // utility class

    /**
     * Build a complete resilience stack from agent configuration.
     *
     * @param config agent configuration with resilience fields
     * @return assembled resilience stack
     */
    public static ResilienceStack fromConfig(AgentConfig config) {
        Objects.requireNonNull(config, "AgentConfig must not be null");

        CircuitBreaker circuitBreaker = buildCircuitBreaker(config);
        RetryPolicy retryPolicy = buildRetryPolicy(config);
        DeadLetterQueue dlq = buildDeadLetterQueue(config);

        return new ResilienceStack(config.getAgentId(), circuitBreaker, retryPolicy, dlq);
    }

    /**
     * Build a circuit breaker from agent config.
     * Always created — provides passive protection even if CIRCUIT_BREAKER is not the failure mode.
     */
    private static CircuitBreaker buildCircuitBreaker(AgentConfig config) {
        return CircuitBreaker.builder(config.getAgentId())
                .failureThreshold(config.getCircuitBreakerThreshold())
                .resetTimeout(config.getCircuitBreakerReset())
                .build();
    }

    /**
     * Build retry policy from agent config retry settings.
     * Returns null if maxRetries &le; 0.
     */
    private static RetryPolicy buildRetryPolicy(AgentConfig config) {
        if (config.getMaxRetries() <= 0) {
            return null;
        }
        return RetryPolicy.builder()
                .maxRetries(config.getMaxRetries())
                .initialDelay(config.getRetryBackoff())
                .maxDelay(config.getMaxRetryBackoff())
                .build();
    }

    /**
     * Build DLQ if the agent uses DEAD_LETTER failure mode.
     * DLQ sizing can be customized via agent properties.
     */
    private static DeadLetterQueue buildDeadLetterQueue(AgentConfig config) {
        if (config.getFailureMode() != FailureMode.DEAD_LETTER) {
            return null;
        }
        int maxSize = getIntProperty(config, "dlq.maxSize", 10_000);
        Duration ttl = getDurationProperty(config, "dlq.ttl", Duration.ofDays(7));
        boolean enableReplay = getBooleanProperty(config, "dlq.enableReplay", true);

        return DeadLetterQueue.builder()
                .maxSize(maxSize)
                .ttl(ttl)
                .enableReplay(enableReplay)
                .build();
    }

    // ────────────────────────────────────────────────────────────────────

    private static int getIntProperty(AgentConfig config, String key, int defaultValue) {
        Object val = config.getProperties().get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    private static Duration getDurationProperty(AgentConfig config, String key, Duration defaultValue) {
        Object val = config.getProperties().get(key);
        if (val instanceof String s) {
            try { return Duration.parse(s); } catch (Exception ignored) {}
        }
        return defaultValue;
    }

    private static boolean getBooleanProperty(AgentConfig config, String key, boolean defaultValue) {
        Object val = config.getProperties().get(key);
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) return Boolean.parseBoolean(s);
        return defaultValue;
    }

    // ────────────────────────────────────────────────────────────────────

    /**
     * Assembled resilience stack for a single agent, containing the circuit breaker,
     * retry policy, and dead letter queue (each nullable if not applicable).
     */
    public static final class ResilienceStack {
        private final String agentId;
        private final CircuitBreaker circuitBreaker;
        private final RetryPolicy retryPolicy;
        private final DeadLetterQueue deadLetterQueue;

        ResilienceStack(String agentId, CircuitBreaker circuitBreaker,
                        RetryPolicy retryPolicy, DeadLetterQueue deadLetterQueue) {
            this.agentId = agentId;
            this.circuitBreaker = circuitBreaker;
            this.retryPolicy = retryPolicy;
            this.deadLetterQueue = deadLetterQueue;
        }

        public String getAgentId() { return agentId; }
        public CircuitBreaker getCircuitBreaker() { return circuitBreaker; }
        public RetryPolicy getRetryPolicy() { return retryPolicy; }
        public DeadLetterQueue getDeadLetterQueue() { return deadLetterQueue; }
        public boolean hasRetry() { return retryPolicy != null; }
        public boolean hasDlq() { return deadLetterQueue != null; }

        /**
         * Whether this stack is effectively passive (no retries, no DLQ).
         */
        public boolean isPassThrough() {
            return retryPolicy == null && deadLetterQueue == null;
        }
    }
}

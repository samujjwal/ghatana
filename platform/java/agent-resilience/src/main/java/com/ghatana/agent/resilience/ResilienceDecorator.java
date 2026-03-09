/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.agent.resilience;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.TypedAgent;
import com.ghatana.platform.resilience.CircuitBreaker;
import com.ghatana.platform.resilience.RetryPolicy;
import io.activej.eventloop.Eventloop;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Factory for assembling a {@link ResilientTypedAgent} from an {@link AgentConfig}.
 *
 * <p>Mirrors the approach of {@code ResilienceFactory} in {@code agent-framework} but
 * produces agent-level decorators rather than raw resilience primitives, and also
 * registers the resulting agent with {@link AgentHealthMonitor}.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * TypedAgent<Req, Resp> resilient = ResilienceDecorator.decorate(
 *     rawAgent, agentConfig, eventloop, healthMonitor);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Factory for decorating TypedAgent with the resilience stack
 * @doc.layer platform
 * @doc.pattern Factory
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
public final class ResilienceDecorator {

    private static final Logger log = LoggerFactory.getLogger(ResilienceDecorator.class);

    private ResilienceDecorator() {}

    /**
     * Decorates the given agent with circuit breaker, retry, and bulkhead derived
     * from its {@link AgentConfig}, and registers it with the health monitor.
     *
     * @param <I>           input type
     * @param <O>           output type
     * @param agent         the raw agent to decorate
     * @param config        agent configuration (provides resilience parameters)
     * @param eventloop     ActiveJ eventloop (required by circuit-breaker and retry)
     * @param healthMonitor health monitor to register the decorated agent with (may be null)
     * @return a {@link ResilientTypedAgent} wrapping {@code agent}
     */
    @NotNull
    public static <I, O> ResilientTypedAgent<I, O> decorate(
            @NotNull TypedAgent<I, O> agent,
            @NotNull AgentConfig config,
            @NotNull Eventloop eventloop,
            @Nullable AgentHealthMonitor healthMonitor) {

        Objects.requireNonNull(agent, "agent");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(eventloop, "eventloop");

        String agentId = agent.descriptor().getAgentId();

        CircuitBreaker circuitBreaker = buildCircuitBreaker(config);
        RetryPolicy retryPolicy = buildRetryPolicy(config);
        AgentBulkhead bulkhead = buildBulkhead(config, agentId);

        ResilientTypedAgent<I, O> resilient = ResilientTypedAgent.<I, O>builder()
                .delegate(agent)
                .eventloop(eventloop)
                .circuitBreaker(circuitBreaker)
                .retryPolicy(retryPolicy)
                .bulkhead(bulkhead)
                .build();

        if (healthMonitor != null) {
            healthMonitor.register(agentId, resilient, circuitBreaker, bulkhead);
        }

        log.info("Decorated agent '{}' with resilience stack [cb={}, retry={}, bulkhead={}]",
                agentId,
                circuitBreaker != null ? "on" : "off",
                retryPolicy != null ? "on" : "off",
                bulkhead != null ? bulkhead.getMaxConcurrency() : "off");

        return resilient;
    }

    /**
     * Decorates without health monitor registration.
     */
    @NotNull
    public static <I, O> ResilientTypedAgent<I, O> decorate(
            @NotNull TypedAgent<I, O> agent,
            @NotNull AgentConfig config,
            @NotNull Eventloop eventloop) {
        return decorate(agent, config, eventloop, null);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static CircuitBreaker buildCircuitBreaker(AgentConfig config) {
        return CircuitBreaker.builder(config.getAgentId())
                .failureThreshold(config.getCircuitBreakerThreshold())
                .resetTimeout(config.getCircuitBreakerReset())
                .build();
    }

    private static RetryPolicy buildRetryPolicy(AgentConfig config) {
        if (config.getMaxRetries() <= 0) return null;
        return RetryPolicy.builder()
                .maxRetries(config.getMaxRetries())
                .initialDelay(config.getRetryBackoff())
                .maxDelay(config.getMaxRetryBackoff())
                .build();
    }

    private static AgentBulkhead buildBulkhead(AgentConfig config, String agentId) {
        int maxConcurrency = getIntProperty(config, "bulkhead.maxConcurrency", 0);
        return maxConcurrency > 0 ? AgentBulkhead.of(agentId, maxConcurrency) : null;
    }

    private static int getIntProperty(AgentConfig config, String key, int defaultValue) {
        if (config.getProperties() == null) return defaultValue;
        Object val = config.getProperties().get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }
}

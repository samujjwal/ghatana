/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.ghatana.agent.migration;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.coordination.OrchestrationStrategy;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Bridges between the {@link TypedAgent} interface and the coordination
 * layer's {@link OrchestrationStrategy.Agent} interface.
 *
 * <p>The coordination package defines its own inner {@code Agent<I,O>}
 * type that is incompatible with both the legacy {@link com.ghatana.agent.Agent}
 * and the new {@link TypedAgent}. This bridge enables unified agents to
 * participate in Sequential, Parallel, and Hierarchical orchestration
 * without duplicating implementation.
 *
 * <h2>Direction: TypedAgent → OrchestrationStrategy.Agent</h2>
 * Use {@link #toOrchestrationAgent(TypedAgent)} to wrap a TypedAgent for use
 * in orchestration strategies.
 *
 * <h2>Direction: OrchestrationStrategy.Agent → TypedAgent</h2>
 * Use {@link #toTypedAgent(OrchestrationStrategy.Agent)} to wrap an orchestration
 * agent for registration in the unified registry.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // TypedAgent → orchestration
 * TypedAgent<SensorData, Alert> detector = new ThresholdAgent();
 * OrchestrationStrategy.Agent<SensorData, Alert> orchestrable =
 *     OrchestrationBridge.toOrchestrationAgent(detector);
 *
 * SequentialOrchestration seq = new SequentialOrchestration();
 * seq.orchestrate(List.of(orchestrable), sensorData, ctx);
 *
 * // Orchestration agent → TypedAgent (for registry)
 * TypedAgent<SensorData, Alert> unified =
 *     OrchestrationBridge.toTypedAgent(orchestrable);
 * registry.register(unified, config);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Bridge TypedAgent ↔ OrchestrationStrategy.Agent
 * @doc.layer migration
 * @doc.pattern Adapter, Bridge
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
public final class OrchestrationBridge {

    private OrchestrationBridge() {
        // Utility class
    }

    // ═════════════════════════════════════════════════════════════════════════
    // TypedAgent → OrchestrationStrategy.Agent
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Wraps a {@link TypedAgent} as an {@link OrchestrationStrategy.Agent}
     * for use in orchestration strategies.
     *
     * @param typed the TypedAgent to wrap
     * @param <I>   input type
     * @param <O>   output type
     * @return an OrchestrationStrategy.Agent that delegates to the TypedAgent
     */
    @NotNull
    public static <I, O> OrchestrationStrategy.Agent<I, O> toOrchestrationAgent(
            @NotNull TypedAgent<I, O> typed) {
        Objects.requireNonNull(typed, "typed agent cannot be null");
        return new TypedToOrchestrationAgent<>(typed);
    }

    /**
     * Wraps a list of TypedAgents for orchestration.
     *
     * @param agents list of TypedAgents
     * @param <I>    input type
     * @param <O>    output type
     * @return list of OrchestrationStrategy.Agent instances
     */
    @NotNull
    public static <I, O> List<OrchestrationStrategy.Agent<I, O>> toOrchestrationAgents(
            @NotNull List<TypedAgent<I, O>> agents) {
        return agents.stream()
                .map(OrchestrationBridge::toOrchestrationAgent)
                .collect(Collectors.toList());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // OrchestrationStrategy.Agent → TypedAgent
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Wraps an {@link OrchestrationStrategy.Agent} as a {@link TypedAgent}
     * for registration in the unified agent registry.
     *
     * <p>Note: The resulting TypedAgent has minimal descriptor metadata since
     * the orchestration Agent interface does not expose capabilities.
     * Use {@link #toTypedAgent(OrchestrationStrategy.Agent, com.ghatana.agent.AgentDescriptor)}
     * for richer metadata.
     *
     * @param orchestrationAgent the agent to wrap
     * @param <I>                input type
     * @param <O>                output type
     * @return a TypedAgent that delegates to the orchestration agent
     */
    @NotNull
    public static <I, O> TypedAgent<I, O> toTypedAgent(
            @NotNull OrchestrationStrategy.Agent<I, O> orchestrationAgent) {
        Objects.requireNonNull(orchestrationAgent, "orchestration agent cannot be null");
        return new OrchestrationToTypedAgent<>(
                orchestrationAgent,
                com.ghatana.agent.AgentDescriptor.builder()
                        .agentId(orchestrationAgent.getAgentId())
                        .name(orchestrationAgent.getAgentId())
                        .type(com.ghatana.agent.AgentType.HYBRID)
                        .labels(java.util.Map.of("adapter", "orchestration-bridge"))
                        .build());
    }

    /**
     * Wraps an {@link OrchestrationStrategy.Agent} as a {@link TypedAgent}
     * with a custom descriptor.
     *
     * @param orchestrationAgent the agent to wrap
     * @param descriptor         custom descriptor with richer metadata
     * @param <I>                input type
     * @param <O>                output type
     * @return a TypedAgent that delegates to the orchestration agent
     */
    @NotNull
    public static <I, O> TypedAgent<I, O> toTypedAgent(
            @NotNull OrchestrationStrategy.Agent<I, O> orchestrationAgent,
            @NotNull com.ghatana.agent.AgentDescriptor descriptor) {
        Objects.requireNonNull(orchestrationAgent, "orchestration agent cannot be null");
        Objects.requireNonNull(descriptor, "descriptor cannot be null");
        return new OrchestrationToTypedAgent<>(orchestrationAgent, descriptor);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Inner adapter: TypedAgent → OrchestrationStrategy.Agent
    // ═════════════════════════════════════════════════════════════════════════

    private static final class TypedToOrchestrationAgent<I, O>
            implements OrchestrationStrategy.Agent<I, O> {

        private final TypedAgent<I, O> typed;

        TypedToOrchestrationAgent(TypedAgent<I, O> typed) {
            this.typed = typed;
        }

        @Override
        @NotNull
        public String getAgentId() {
            return typed.descriptor().getAgentId();
        }

        @Override
        @NotNull
        public Promise<O> execute(@NotNull I input, @NotNull AgentContext context) {
            return typed.process(context, input)
                    .map(AgentResult::getOutput);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Inner adapter: OrchestrationStrategy.Agent → TypedAgent
    // ═════════════════════════════════════════════════════════════════════════

    private static final class OrchestrationToTypedAgent<I, O>
            implements TypedAgent<I, O> {

        private final OrchestrationStrategy.Agent<I, O> delegate;
        private final com.ghatana.agent.AgentDescriptor descriptor;

        OrchestrationToTypedAgent(
                OrchestrationStrategy.Agent<I, O> delegate,
                com.ghatana.agent.AgentDescriptor descriptor) {
            this.delegate = delegate;
            this.descriptor = descriptor;
        }

        @Override
        @NotNull
        public com.ghatana.agent.AgentDescriptor descriptor() {
            return descriptor;
        }

        @Override
        @NotNull
        public Promise<Void> initialize(@NotNull com.ghatana.agent.AgentConfig config) {
            return Promise.complete();
        }

        @Override
        @NotNull
        public Promise<Void> shutdown() {
            return Promise.complete();
        }

        @Override
        @NotNull
        public Promise<com.ghatana.agent.HealthStatus> healthCheck() {
            return Promise.of(com.ghatana.agent.HealthStatus.HEALTHY);
        }

        @Override
        @NotNull
        public Promise<AgentResult<O>> process(@NotNull AgentContext ctx, @NotNull I input) {
            java.time.Instant start = java.time.Instant.now();
            return delegate.execute(input, ctx)
                    .map(output -> AgentResult.success(
                            output,
                            descriptor.getAgentId(),
                            java.time.Duration.between(start, java.time.Instant.now())));
        }
    }
}

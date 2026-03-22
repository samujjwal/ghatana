/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.ghatana.agent.migration;

import com.ghatana.agent.*;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.runtime.BaseAgent;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Adapter that wraps a GAA-lifecycle {@link BaseAgent}{@code <I, O>} as a
 * {@link TypedAgent}{@code <I, O>}.
 *
 * <p>This enables existing product agents that extend {@code BaseAgent}
 * (tutorputor's {@code LearnerInteractionAgent}, yappc's {@code YAPPCAgentBase}, etc.)
 * to be registered in the {@link com.ghatana.agent.registry.AgentFrameworkRegistry}
 * without rewriting them.
 *
 * <h2>Mapping</h2>
 * <table>
 *   <tr><th>BaseAgent (GAA)</th><th>TypedAgent</th></tr>
 *   <tr><td>{@code getAgentId()}</td><td>{@code descriptor().getAgentId()}</td></tr>
 *   <tr><td>{@code executeTurn(input, ctx)}</td><td>{@code process(ctx, input) → AgentResult<O>}</td></tr>
 *   <tr><td>(no lifecycle)</td><td>{@code initialize/shutdown} are no-ops</td></tr>
 *   <tr><td>(no health check)</td><td>{@code healthCheck()} returns HEALTHY</td></tr>
 * </table>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * BaseAgent<ContentReq, ContentResp> contentAgent = new ContentGenerationAgent(generator);
 * TypedAgent<ContentReq, ContentResp> adapted = new BaseAgentAdapter<>(contentAgent);
 * registry.register(adapted, config);
 * }</pre>
 *
 * @param <I> input type matching the BaseAgent's TInput
 * @param <O> output type matching the BaseAgent's TOutput
 *
 * @doc.type class
 * @doc.purpose Bridge GAA BaseAgent → TypedAgent
 * @doc.layer migration
 * @doc.pattern Adapter
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
public class BaseAgentAdapter<I, O> implements TypedAgent<I, O> {

    private final BaseAgent<I, O> delegate;
    private final AgentDescriptor descriptor;

    /**
     * Creates an adapter wrapping the given GAA BaseAgent.
     *
     * @param delegate the {@link BaseAgent} to wrap
     */
    public BaseAgentAdapter(@NotNull BaseAgent<I, O> delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
        this.descriptor = AgentDescriptor.builder()
                .agentId(delegate.getAgentId())
                .name(delegate.getAgentId())
                .type(AgentType.HYBRID) // GAA agents are typically hybrid
                .determinism(DeterminismGuarantee.NONE)
                .labels(java.util.Map.of("adapter", "base-agent-gaa"))
                .build();
    }

    /**
     * Creates an adapter with a custom descriptor.
     *
     * @param delegate   the {@link BaseAgent} to wrap
     * @param descriptor custom agent descriptor
     */
    public BaseAgentAdapter(
            @NotNull BaseAgent<I, O> delegate,
            @NotNull AgentDescriptor descriptor) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor cannot be null");
    }

    @Override
    @NotNull
    public AgentDescriptor descriptor() {
        return descriptor;
    }

    /**
     * No-op — BaseAgent has no explicit initialization lifecycle.
     */
    @Override
    @NotNull
    public Promise<Void> initialize(@NotNull AgentConfig config) {
        return Promise.complete();
    }

    /**
     * No-op — BaseAgent has no explicit shutdown lifecycle.
     */
    @Override
    @NotNull
    public Promise<Void> shutdown() {
        return Promise.complete();
    }

    @Override
    @NotNull
    public Promise<HealthStatus> healthCheck() {
        return Promise.of(HealthStatus.HEALTHY);
    }

    /**
     * Delegates to {@code BaseAgent.executeTurn(input, context)} and wraps
     * the raw output into an {@link AgentResult}.
     *
     * <p>The GAA lifecycle (PERCEIVE→REASON→ACT→CAPTURE→REFLECT) executes
     * within the delegate's {@code executeTurn} call.
     */
    @Override
    @NotNull
    public Promise<AgentResult<O>> process(@NotNull AgentContext ctx, @NotNull I input) {
        Instant start = Instant.now();

        return delegate.executeTurn(input, ctx)
                .map(output -> AgentResult.success(
                        output,
                        descriptor.getAgentId(),
                        Duration.between(start, Instant.now())));
    }

    /**
     * Returns the wrapped GAA BaseAgent.
     *
     * @return the delegate
     */
    @NotNull
    public BaseAgent<I, O> getDelegate() {
        return delegate;
    }
}

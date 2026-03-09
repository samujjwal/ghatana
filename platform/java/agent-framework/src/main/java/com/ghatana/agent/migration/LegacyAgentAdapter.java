/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.ghatana.agent.migration;

import com.ghatana.agent.*;
import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Adapter that wraps a legacy {@link Agent} as a {@link TypedAgent}{@code <Object, Object>}.
 *
 * <p>This enables legacy {@code Agent} implementations (virtual-org, yappc workflow)
 * to participate in the unified agent framework — registered in
 * {@link com.ghatana.agent.registry.AgentFrameworkRegistry}, composed in operator trees,
 * and health-checked by the runtime.
 *
 * <h2>Mapping</h2>
 * <table>
 *   <tr><th>Legacy Agent</th><th>TypedAgent</th></tr>
 *   <tr><td>{@code getId()}</td><td>{@code descriptor().getAgentId()}</td></tr>
 *   <tr><td>{@code getCapabilities()}</td><td>{@code descriptor().toCapabilities()}</td></tr>
 *   <tr><td>{@code initialize(AgentContext)}</td><td>Called inside {@code initialize(AgentConfig)}</td></tr>
 *   <tr><td>{@code start()}</td><td>Called after initialize</td></tr>
 *   <tr><td>{@code process(T, AgentContext)}</td><td>{@code process(AgentContext, I) → AgentResult<Object>}</td></tr>
 *   <tr><td>{@code shutdown()}</td><td>{@code shutdown()}</td></tr>
 * </table>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Agent legacyAgent = new VirtualOrgAgent();
 * TypedAgent<Object, Object> adapted = new LegacyAgentAdapter(legacyAgent);
 * registry.register(adapted, config);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Bridge legacy Agent → TypedAgent
 * @doc.layer migration
 * @doc.pattern Adapter
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
public class LegacyAgentAdapter implements TypedAgent<Object, Object> {

    private final Agent delegate;
    private final AgentDescriptor descriptor;

    /**
     * Creates an adapter wrapping the given legacy agent.
     *
     * @param delegate the legacy {@link Agent} to wrap
     */
    public LegacyAgentAdapter(@NotNull Agent delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
        this.descriptor = buildDescriptor(delegate);
    }

    /**
     * Creates an adapter with a custom descriptor (for overriding type/capabilities).
     *
     * @param delegate   the legacy {@link Agent} to wrap
     * @param descriptor custom descriptor
     */
    public LegacyAgentAdapter(@NotNull Agent delegate, @NotNull AgentDescriptor descriptor) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor cannot be null");
    }

    @Override
    @NotNull
    public AgentDescriptor descriptor() {
        return descriptor;
    }

    /**
     * Initializes the adapted agent.
     *
     * <p>Creates a canonical {@link AgentContext} from the
     * config and calls {@code delegate.initialize(context)} followed by {@code delegate.start()}.
     */
    @Override
    @NotNull
    public Promise<Void> initialize(@NotNull AgentConfig config) {
        AgentContext ctx = AgentContext.builder()
                .turnId("init-" + delegate.getId())
                .agentId(delegate.getId())
                .tenantId(config.getLabels().getOrDefault("tenantId", "system"))
                .startTime(Instant.now())
                .memoryStore(com.ghatana.agent.framework.memory.MemoryStore.noOp())
                .config(config.getProperties())
                .build();

        return delegate.initialize(ctx)
                .then(() -> delegate.start());
    }

    @Override
    @NotNull
    public Promise<Void> shutdown() {
        return delegate.shutdown();
    }

    @Override
    @NotNull
    public Promise<HealthStatus> healthCheck() {
        // Legacy Agent has no health check — assume HEALTHY if we got this far
        return Promise.of(HealthStatus.HEALTHY);
    }

    /**
     * Processes input through the legacy agent.
     *
     * <p>Creates a canonical {@link AgentContext} and delegates to
     * {@code Agent.process(input, context)}, wrapping the result in an {@link AgentResult}.
     */
    @Override
    @NotNull
    @SuppressWarnings("unchecked")
    public Promise<AgentResult<Object>> process(@NotNull AgentContext ctx, @NotNull Object input) {
        Instant start = Instant.now();

        return delegate.<Object, Object>process(input, ctx)
                .map(result -> AgentResult.success(
                        result,
                        descriptor.getAgentId(),
                        Duration.between(start, Instant.now())))
                .mapException(ex -> {
                    // Don't swallow — let it propagate
                    return ex;
                });
    }

    /**
     * Returns the wrapped legacy agent.
     *
     * @return the delegate
     */
    @NotNull
    public Agent getDelegate() {
        return delegate;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Internal helpers
    // ═════════════════════════════════════════════════════════════════════════

    private static AgentDescriptor buildDescriptor(Agent agent) {
        AgentCapabilities caps = agent.getCapabilities();
        return AgentDescriptor.builder()
                .agentId(agent.getId())
                .name(caps.name() != null ? caps.name() : agent.getId())
                .description(caps.description())
                .type(AgentType.HYBRID) // Legacy agents are untyped — default to HYBRID
                .determinism(DeterminismGuarantee.NONE)
                .capabilities(caps.supportedTaskTypes() != null
                        ? caps.supportedTaskTypes()
                        : Set.of())
                .labels(java.util.Map.of("adapter", "legacy-agent"))
                .build();
    }

}

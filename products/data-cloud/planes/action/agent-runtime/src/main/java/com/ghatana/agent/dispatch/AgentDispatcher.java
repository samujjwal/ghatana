/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.agent.dispatch;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Dispatches agent invocations to the correct execution tier based on
 * catalog definitions and available runtime implementations.
 *
 * <h2>Three-Tier Resolution</h2>
 * <ul>
 *   <li><b>Tier-J (Java-Implemented)</b>: A registered {@code TypedAgent} bean matches the agent ID</li>
 *   <li><b>Tier-S (Service-Orchestrated)</b>: Agent has PIPELINE generator with delegation chain</li>
 *   <li><b>Tier-L (LLM-Executed)</b>: Agent has LLM generator step — prompt-based execution</li>
 * </ul>
 *
 * <h2>WS2: Replay-Safe Execution</h2>
 * <ul>
 *   <li>Supports replay mode with dry-run and side-effect awareness</li>
 *   <li>Idempotency key generation for exactly-once semantics</li>
 *   <li>Side-effect declaration and control for safe replay</li>
 *   <li>Compensation strategy for rollback support</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Three-tier agent dispatch SPI with replay-safe execution
 * @doc.layer framework
 * @doc.pattern Strategy, Dispatcher
 *
 * @author Ghatana AI Platform
 * @since 2.2.0
 */
public interface AgentDispatcher {

    /**
     * Dispatches an invocation to the resolved execution tier.
     *
     * @param agentId the catalog agent ID to invoke
     * @param input   the typed input payload
     * @param ctx     execution context (tenant, project, trace)
     * @param <I>     input type
     * @param <O>     output type
     * @return a Promise of the agent result
     */
    @NotNull
    <I, O> Promise<AgentResult<O>> dispatch(
            @NotNull String agentId,
            @NotNull I input,
            @NotNull AgentContext ctx);

    /**
     * Dispatches an invocation with replay-safe execution.
     *
     * <p>WS2: Supports replay mode with side-effect control:
     * <ul>
     *   <li>isReplay: true if this is a replay (dry-run or replay mode)</li>
     *   <li>idempotencyKey: key for deduplication in replay scenarios</li>
     *   <li>Side effects are skipped during dry-run replay</li>
     * </ul>
     *
     * @param agentId        the catalog agent ID to invoke
     * @param input          the typed input payload
     * @param ctx            execution context (tenant, project, trace)
     * @param isReplay       true if this is a replay operation
     * @param idempotencyKey optional idempotency key for deduplication
     * @param <I>            input type
     * @param <O>            output type
     * @return a Promise of the agent result
     */
    @NotNull
    <I, O> Promise<AgentResult<O>> dispatch(
            @NotNull String agentId,
            @NotNull I input,
            @NotNull AgentContext ctx,
            boolean isReplay,
            @NotNull String idempotencyKey);

    /**
     * Declares side effects for an agent.
     *
     * <p>WS2: Returns the side-effect declaration for the agent,
     * enabling replay-safe execution decisions.
     *
     * @param agentId the catalog agent ID
     * @return side-effect declaration
     */
    @NotNull
    SideEffectDeclaration declareSideEffects(@NotNull String agentId);

    /**
     * Resolves the execution tier for a given agent without executing.
     *
     * @param agentId the catalog agent ID
     * @return the resolved execution tier
     */
    @NotNull
    ExecutionTier resolve(@NotNull String agentId);

    // ==================== WS2: Supporting Types ====================

    /**
     * Side-effect declaration for agent execution.
     */
    record SideEffectDeclaration(
            boolean hasSideEffects,
            boolean isDestructive,
            boolean isReversible,
            @NotNull String compensationStrategy,
            @NotNull java.util.Set<String> affectedResources
    ) {
        public SideEffectDeclaration {
            affectedResources = java.util.Set.copyOf(affectedResources);
        }

        public boolean isSafeToReplay() {
            return !isDestructive || isReversible;
        }

        public static SideEffectDeclaration none() {
            return new SideEffectDeclaration(false, false, true, "none", java.util.Set.of());
        }
    }
}

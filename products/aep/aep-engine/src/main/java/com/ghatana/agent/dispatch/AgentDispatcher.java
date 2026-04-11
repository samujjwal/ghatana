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
 * @doc.type interface
 * @doc.purpose Three-tier agent dispatch SPI
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
     * Resolves the execution tier for a given agent without executing.
     *
     * @param agentId the catalog agent ID
     * @return the resolved execution tier
     */
    @NotNull
    ExecutionTier resolve(@NotNull String agentId);
}

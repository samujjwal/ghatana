/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.composition;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Minimal invocation abstraction used by composition orchestrators.
 *
 * <p>This interface avoids a dependency on the AEP {@code AgentDispatcher}.
 * In production, the AEP runtime adapts its {@code AgentDispatcher} to this interface
 * when constructing composition orchestrators. Input and output types use {@code Object}
 * to remain a usable functional interface in Java (generic methods on @FunctionalInterface
 * prevent lambda construction).
 *
 * @doc.type interface
 * @doc.purpose Invocation abstraction for composition orchestrators
 * @doc.layer platform
 * @doc.pattern Strategy
 */
@FunctionalInterface
public interface AgentInvoker {

    /**
     * Invokes a specific agent and returns its result.
     *
     * @param agentId the ID of the agent to invoke
     * @param input   the input to provide to the agent (untyped to allow lambda usage)
     * @param ctx     the shared agent context
     * @return a {@link Promise} resolving to the agent's result
     */
    @NotNull
    Promise<AgentResult<Object>> invoke(@NotNull String agentId, @NotNull Object input, @NotNull AgentContext ctx);
}

/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * @doc.type interface
 * @doc.purpose Universal execution entry point for invoking TypedAgent instances
 * @doc.layer agent-core
 * @doc.pattern Interface
 */
/**
 * Universal execution entry point for invoking {@link TypedAgent} instances.
 */
public interface TypedAgentExecutor {
    @NotNull
    <I, O> Promise<AgentResult<O>> execute(
            @NotNull TypedAgent<I, O> agent,
            @NotNull AgentContext context,
            @NotNull I input);
}

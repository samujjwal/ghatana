/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Shared — Agent Runtime Port
 */
package com.ghatana.yappc.agent.spi;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * YAPPC-internal port interface for agent runtime dispatch operations.
 *
 * <p>This port is the adapter seam between YAPPC evaluation and execution
 * flows and the underlying agent runtime (e.g., AEP {@code AgentDispatcher}).
 * All YAPPC modules that need to dispatch agent executions must depend on this
 * port, <em>not</em> directly on AEP runtime classes.
 *
 * <p>Implementations live in the infrastructure layer (e.g.,
 * {@code yappc-infrastructure}) and adapt the external dispatcher to this contract.
 *
 * @doc.type interface
 * @doc.purpose YAPPC-internal port for dispatching agent execution
 * @doc.layer platform
 * @doc.pattern Port (Hexagonal Architecture / Ports &amp; Adapters)
 */
public interface AgentRuntimePort {

    /**
     * Dispatch an execution request to the named agent.
     *
     * @param agentId the catalog agent identifier to invoke
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
}

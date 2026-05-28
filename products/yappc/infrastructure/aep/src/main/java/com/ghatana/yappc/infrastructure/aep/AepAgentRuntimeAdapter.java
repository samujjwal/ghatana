/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Infrastructure - AEP Runtime Adapter
 */
package com.ghatana.yappc.infrastructure.aep;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.dispatch.AgentDispatcher;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.yappc.agent.spi.AgentRuntimePort;
import io.activej.inject.annotation.Inject;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Adapts AEP's {@code AgentDispatcher} to YAPPC's {@link AgentRuntimePort}.
 *
 * <p>This adapter is the <em>only</em> place in YAPPC that directly references
 * the AEP dispatcher. All other YAPPC modules should depend on
 * {@link AgentRuntimePort} and receive this adapter through dependency injection.
 *
 * @doc.type class
 * @doc.purpose Adapter from AEP AgentDispatcher to YAPPC AgentRuntimePort
 * @doc.layer product
 * @doc.pattern Adapter (Ports &amp; Adapters)
 */
public class AepAgentRuntimeAdapter implements AgentRuntimePort {

    private final AgentDispatcher delegate;

    /**
     * @param delegate the underlying AEP {@code AgentDispatcher}
     */
    @Inject
    public AepAgentRuntimeAdapter(AgentDispatcher delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    @NotNull
    public <I, O> Promise<AgentResult<O>> dispatch(
            @NotNull String agentId,
            @NotNull I input,
            @NotNull AgentContext ctx) {
        return delegate.dispatch(agentId, input, ctx);
    }
}

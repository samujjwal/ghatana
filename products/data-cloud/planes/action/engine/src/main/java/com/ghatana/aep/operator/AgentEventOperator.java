/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.operator;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;

import java.util.Map;
import java.util.Objects;

/**
 * Adapts a {@link TypedAgent} to an event-oriented operator interface.
 *
 * <p>Wraps a {@code TypedAgent<Map<String, Object>, Map<String, Object>>} and exposes
 * a {@link #submit(AgentContext, Map)} method that delegates to the agent's process
 * loop, returning the typed output on success or propagating the failure promise.</p>
 *
 * <p>This is the primary bridge between the agent-core processing model and the
 * AEP event pipeline. It can be used directly or wrapped in a
 * {@link DeadLetterOperator} for resilient event handling.</p>
 *
 * @doc.type class
 * @doc.purpose Adapts a TypedAgent for use as an event-oriented operator in AEP pipelines
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class AgentEventOperator {

    private final TypedAgent<Map<String, Object>, Map<String, Object>> agent;

    /**
     * Creates an {@code AgentEventOperator} wrapping the given agent.
     *
     * @param agent the TypedAgent to delegate event processing to; must not be null
     */
    public AgentEventOperator(TypedAgent<Map<String, Object>, Map<String, Object>> agent) {
        this.agent = Objects.requireNonNull(agent, "agent must not be null");
    }

    /**
     * Submit an event for processing by the underlying agent.
     *
     * <p>Calls {@code agent.process(ctx, event)} and returns the typed output map
     * on a successful result. If the agent's promise is failed, the failure
     * propagates to the caller unchanged.
     *
     * @param ctx   the agent execution context
     * @param event the event payload to process
     * @return a Promise that resolves to the agent's output map, or fails with
     *         the agent's error
     */
    public Promise<Map<String, Object>> submit(
            AgentContext ctx, Map<String, Object> event) {
        return agent.process(ctx, event)
                .map(AgentResult::getOutput);
    }

    /**
     * Returns the agentId of the wrapped agent (via its descriptor).
     *
     * @return agentId string
     */
    public String getAgentId() {
        return agent.descriptor().getAgentId();
    }
}

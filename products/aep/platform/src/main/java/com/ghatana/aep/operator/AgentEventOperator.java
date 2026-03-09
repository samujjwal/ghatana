/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.aep.operator;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * Bridges a {@link TypedAgent} into the AEP event processing pipeline as an
 * operator that can receive events, invoke the agent, and emit results downstream.
 *
 * <p>This operator converts AEP events (represented as {@code Map<String, Object>})
 * into agent inputs, delegates to the wrapped {@code TypedAgent}, and converts
 * the {@link AgentResult} back into an AEP event for downstream operators.
 *
 * <h2>Pipeline Integration</h2>
 * <pre>
 *   EventSource → ... → AgentEventOperator → ... → EventSink
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * TypedAgent<Map<String, Object>, Map<String, Object>> agent = ...;
 * AgentEventOperator operator = new AgentEventOperator(agent);
 * Promise<Map<String, Object>> result = operator.processEvent(ctx, event);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Bridge TypedAgent into AEP event pipeline as an operator
 * @doc.layer product-aep
 * @doc.pattern Adapter
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
public class AgentEventOperator {

    private static final Logger log = LoggerFactory.getLogger(AgentEventOperator.class);

    private final TypedAgent<Map<String, Object>, Map<String, Object>> agent;
    private final String operatorId;

    public AgentEventOperator(
            @NotNull TypedAgent<Map<String, Object>, Map<String, Object>> agent) {
        this.agent = Objects.requireNonNull(agent, "agent");
        this.operatorId = "agent-op:" + agent.descriptor().getAgentId();
    }

    /**
     * Processes a single AEP event through the wrapped agent.
     *
     * @param ctx   agent execution context
     * @param event the AEP event as a map
     * @return a Promise of the result event (output map), or an error event on failure
     */
    @NotNull
    public Promise<Map<String, Object>> processEvent(
            @NotNull AgentContext ctx,
            @NotNull Map<String, Object> event) {

        return agent.process(ctx, event)
                .map(result -> {
                    if (result.isSuccess() && result.getOutput() != null) {
                        return result.getOutput();
                    }
                    Map<String, Object> errorEvent = new java.util.HashMap<>();
                    errorEvent.put("_operator", operatorId);
                    errorEvent.put("_status", result.getStatus().name());
                    errorEvent.put("_error", result.getExplanation() != null
                            ? result.getExplanation() : "unknown");
                    return errorEvent;
                })
                .mapException(e -> {
                    log.error("AgentEventOperator '{}' failed: {}", operatorId, e.getMessage());
                    return e;
                });
    }

    /**
     * Returns the operator identifier (derived from agent ID).
     */
    public String getOperatorId() {
        return operatorId;
    }

    /**
     * Returns the wrapped agent's descriptor ID.
     */
    public String getAgentId() {
        return agent.descriptor().getAgentId();
    }
}

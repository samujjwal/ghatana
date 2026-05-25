/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.safety.stage;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.dispatch.AgentDispatcher;
import com.ghatana.agent.framework.api.AgentContext;

import java.util.Map;
import java.util.Objects;

/**
 * Stage that invokes the delegate agent dispatcher.
 *
 * <p>This is the final stage in the pipeline that actually executes the agent
 * after all gates have passed and all preparation stages have completed.
 *
 * @doc.type class
 * @doc.purpose Invokes the delegate agent dispatcher
 * @doc.layer product
 * @doc.pattern Stage
 */
public final class AgentDelegateInvoker implements AgentDispatchStage {

    private final AgentDispatcher delegate;

    public AgentDelegateInvoker(AgentDispatcher delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    @Override
    @SuppressWarnings("unchecked")
    public StageResult execute(StageContext context) {
        Objects.requireNonNull(context, "context must not be null");

        try {
            String agentId = context.agentId();
            Object input = context.metadata().get("input");
            AgentContext agentContext = (AgentContext) context.metadata().get("agentContext");

            if (input == null) {
                return StageResult.failure("Input is missing from stage context");
            }

            if (agentContext == null) {
                return StageResult.failure("AgentContext is missing from stage context");
            }

            // This is a synchronous wrapper for the async delegate
            // In production, this should be properly integrated with the async flow
            AgentResult<Object> result = delegate.dispatch(agentId, input, agentContext)
                .getResult(); // Note: This blocks, should be refactored for async flow

            Map<String, Object> output = new java.util.LinkedHashMap<>();
            output.put("result", result);

            return StageResult.success(output);
        } catch (Exception e) {
            return StageResult.failure("Delegate invocation failed: " + e.getMessage());
        }
    }
}

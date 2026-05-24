package com.ghatana.aep.operator.agent;

import com.ghatana.aep.model.EventContext;

import java.util.Map;

/**
 * Invocation payload passed from an agent capability to an agent runtime.
 *
 * @doc.type record
 * @doc.purpose Carries typed event context and policies for one agent capability invocation
 * @doc.layer product
 * @doc.pattern Contract
 */
public record AgentInvocationRequest(
        String operatorId,
        String agentRef,
        String outputSchema,
        EventContext<Map<String, Object>> eventContext,
        Map<String, Object> policies
) {

    public AgentInvocationRequest {
        operatorId = requireText(operatorId, "operatorId");
        agentRef = requireText(agentRef, "agentRef");
        outputSchema = requireText(outputSchema, "outputSchema");
        if (eventContext == null) {
            throw new IllegalArgumentException("eventContext must not be null");
        }
        policies = Map.copyOf(policies != null ? policies : Map.of());
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}

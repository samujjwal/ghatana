package com.ghatana.aep.agent.capability;

import com.ghatana.agent.framework.api.AgentContext;

import java.util.Map;
import java.util.Objects;

/**
 * @doc.type record
 * @doc.purpose Carries a typed capability invocation and its execution context
 * @doc.layer product
 * @doc.pattern Contract
 */
public record CapabilityInvocation<I>(
        CapabilityId capabilityId,
        AgentContext agentContext,
        I input,
        Map<String, Object> attributes) {

    public CapabilityInvocation {
        capabilityId = Objects.requireNonNull(capabilityId, "capabilityId");
        agentContext = Objects.requireNonNull(agentContext, "agentContext");
        attributes = Map.copyOf(attributes != null ? attributes : Map.of());
    }
}

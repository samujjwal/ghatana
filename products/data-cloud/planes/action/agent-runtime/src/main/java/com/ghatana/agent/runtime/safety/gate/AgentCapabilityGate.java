/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.safety.gate;

import com.ghatana.agent.pluggability.AgentCapabilityManifest;
import com.ghatana.agent.pluggability.InteractionMode;

import java.util.Map;
import java.util.Objects;

/**
 * Gate that checks if the agent capability manifest supports the required interaction mode.
 *
 * <p>This gate rejects dispatch if the agent does not support AUTONOMOUS execution
 * and no supervisor agent ID is set in the context.
 *
 * @doc.type class
 * @doc.purpose Validates agent capability manifest before dispatch
 * @doc.layer product
 * @doc.pattern Gate
 */
public final class AgentCapabilityGate implements AgentDispatchGate {

    private final AgentCapabilityManifest capabilityManifest;

    public AgentCapabilityGate(AgentCapabilityManifest capabilityManifest) {
        if (capabilityManifest == null) {
            throw new IllegalArgumentException("capabilityManifest must not be null");
        }
        this.capabilityManifest = capabilityManifest;
    }

    @Override
    public GateResult evaluate(DispatchContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }

        if (!capabilityManifest.supports(InteractionMode.AUTONOMOUS)) {
            boolean isSupervised = context.metadata().containsKey("supervisorAgentId");
            if (!isSupervised) {
                return GateResult.failure(
                    "Agent [" + context.agentId() + "] does not support AUTONOMOUS execution " +
                    "and no supervisorAgentId is set in context");
            }
        }

        return GateResult.success();
    }
}

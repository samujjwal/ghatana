/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.validation;

import com.ghatana.agent.AgentType;
import com.ghatana.agent.framework.config.AgentDefinition;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Dispatches canonical AgentSpec validation to type-specific validators.
 */
public final class AgentSpecValidator {
    private static final Map<AgentType, AgentTypeSpecificValidator> VALIDATORS = new EnumMap<>(AgentType.class);

    static {
        VALIDATORS.put(AgentType.DETERMINISTIC, new DeterministicAgentValidator());
        VALIDATORS.put(AgentType.PROBABILISTIC, new ProbabilisticAgentValidator());
        VALIDATORS.put(AgentType.HYBRID, new HybridAgentValidator());
        VALIDATORS.put(AgentType.ADAPTIVE, new AdaptiveAgentValidator());
        VALIDATORS.put(AgentType.COMPOSITE, new CompositeAgentValidator());
        VALIDATORS.put(AgentType.PLANNING, new PlanningAgentValidator());
        VALIDATORS.put(AgentType.STREAM_PROCESSOR, new StreamProcessorAgentValidator());
        VALIDATORS.put(AgentType.REACTIVE, new ReactiveAgentValidator());
        VALIDATORS.put(AgentType.CUSTOM, new CustomAgentValidator());
    }

    private AgentSpecValidator() {}

    public static List<String> validateTypeSpecific(AgentDefinition definition) {
        AgentTypeSpecificValidator validator = VALIDATORS.get(definition.getType());
        if (validator == null) {
            return List.of("[type] no validator registered for " + definition.getType());
        }
        return new ArrayList<>(validator.validate(definition));
    }
}

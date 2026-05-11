/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.validation;

import com.ghatana.agent.framework.config.AgentDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PlanningAgentValidator implements AgentTypeSpecificValidator {
    @Override
    public List<String> validate(AgentDefinition definition) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> metadata = definition.getMetadata();
        if (!metadata.containsKey("planRepresentation")) {
            errors.add("[type:planning] plan representation is required");
        }
        if (!metadata.containsKey("checkpointStrategy")) {
            errors.add("[type:planning] checkpoint strategy is required");
        }
        if (!metadata.containsKey("compensationPolicy")) {
            errors.add("[type:planning] compensation policy is required");
        }
        return errors;
    }
}

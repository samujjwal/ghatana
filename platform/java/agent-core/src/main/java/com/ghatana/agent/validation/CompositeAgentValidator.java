/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.validation;

import com.ghatana.agent.framework.config.AgentDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CompositeAgentValidator implements AgentTypeSpecificValidator {
    @Override
    public List<String> validate(AgentDefinition definition) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> metadata = definition.getMetadata();
        if (!metadata.containsKey("subAgents")) {
            errors.add("[type:composite] sub-agent list is required");
        }
        if (!metadata.containsKey("aggregationStrategy")) {
            errors.add("[type:composite] aggregation strategy is required");
        }
        if (!metadata.containsKey("disagreementHandling")) {
            errors.add("[type:composite] disagreement handling is required");
        }
        return errors;
    }
}

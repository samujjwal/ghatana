/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.validation;

import com.ghatana.agent.framework.config.AgentDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AdaptiveAgentValidator implements AgentTypeSpecificValidator {
    @Override
    public List<String> validate(AgentDefinition definition) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> metadata = definition.getMetadata();
        String learningLevel = definition.getLearningLevel() != null
                ? definition.getLearningLevel()
                : String.valueOf(metadata.getOrDefault("learningLevel", "L0"));
        if (learningLevel.equals("L0") || learningLevel.equals("L1")) {
            errors.add("[type:adaptive] learning level must be L2 or higher");
        }
        if (!metadata.containsKey("adaptationTargets")) {
            errors.add("[type:adaptive] adaptation targets are required");
        }
        if (!metadata.containsKey("driftControls")) {
            errors.add("[type:adaptive] drift controls are required");
        }
        return errors;
    }
}

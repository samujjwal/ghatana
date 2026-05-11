/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.validation;

import com.ghatana.agent.framework.config.AgentDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @doc.type class
 * @doc.purpose Validates probabilistic agent definitions for confidence and uncertainty metadata
 * @doc.layer agent-core
 * @doc.pattern Validator
 */
public final class ProbabilisticAgentValidator implements AgentTypeSpecificValidator {
    @Override
    public List<String> validate(AgentDefinition definition) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> metadata = definition.getMetadata();
        if (definition.getSubtype() == null || definition.getSubtype().isBlank()) {
            errors.add("[type:probabilistic] subtype is required");
        }
        if (!metadata.containsKey("confidenceModel") && metadata.get("confidenceModelRef") == null) {
            errors.add("[type:probabilistic] confidence model is required");
        }
        if (!metadata.containsKey("uncertaintyBehavior")) {
            errors.add("[type:probabilistic] uncertainty behavior is required");
        }
        if (!metadata.containsKey("lowConfidenceStatusPolicy")) {
            errors.add("[type:probabilistic] low-confidence status policy is required");
        }
        return errors;
    }
}

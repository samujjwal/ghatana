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
 * @doc.purpose Validates hybrid agent definitions for routing and fallback metadata
 * @doc.layer agent-core
 * @doc.pattern Validator
 */
public final class HybridAgentValidator implements AgentTypeSpecificValidator {
    @Override
    public List<String> validate(AgentDefinition definition) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> metadata = definition.getMetadata();
        if (!metadata.containsKey("routingStrategy")) {
            errors.add("[type:hybrid] routing strategy is required");
        }
        if (!metadata.containsKey("fallbackCondition")) {
            errors.add("[type:hybrid] fallback condition is required");
        }
        if (!metadata.containsKey("deterministicPath") || !metadata.containsKey("probabilisticPath")) {
            errors.add("[type:hybrid] deterministicPath and probabilisticPath are required");
        }
        return errors;
    }
}

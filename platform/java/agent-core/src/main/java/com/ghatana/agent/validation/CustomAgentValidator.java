/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.validation;

import com.ghatana.agent.AgentType;
import com.ghatana.agent.framework.config.AgentDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CustomAgentValidator implements AgentTypeSpecificValidator {
    @Override
    public List<String> validate(AgentDefinition definition) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> metadata = definition.getMetadata();
        String subtype = definition.getSubtype();
        if (subtype == null || !AgentType.isCustomTypeRegistered(subtype)) {
            errors.add("[type:custom] registered custom type subtype is required");
        }
        if (definition.getOwners().isEmpty()) {
            errors.add("[type:custom] owner is required");
        }
        if (!metadata.containsKey("schema")) {
            errors.add("[type:custom] schema is required");
        }
        if (!metadata.containsKey("validationHook")) {
            errors.add("[type:custom] validation hook is required");
        }
        return errors;
    }
}

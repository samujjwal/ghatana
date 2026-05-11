/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.validation;

import com.ghatana.agent.StateMutability;
import com.ghatana.agent.framework.config.AgentDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @doc.type class
 * @doc.purpose Validates reactive agent definitions for stateless execution requirements
 * @doc.layer agent-core
 * @doc.pattern Validator
 */
public final class ReactiveAgentValidator implements AgentTypeSpecificValidator {
    @Override
    public List<String> validate(AgentDefinition definition) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> metadata = definition.getMetadata();
        if (definition.getStateMutability() != StateMutability.STATELESS) {
            errors.add("[type:reactive] stateless execution is required");
        }
        if (!metadata.containsKey("idempotence")) {
            errors.add("[type:reactive] idempotence declaration is required");
        }
        if (!metadata.containsKey("triggerActionContract")) {
            errors.add("[type:reactive] trigger/action contract is required");
        }
        return errors;
    }
}

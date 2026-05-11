/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.validation;

import com.ghatana.agent.DeterminismGuarantee;
import com.ghatana.agent.framework.config.AgentDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class DeterministicAgentValidator implements AgentTypeSpecificValidator {
    private static final Set<String> VALID_SUBTYPES = Set.of(
            "RULE_ENGINE", "THRESHOLD", "FSM", "PATTERN_MATCHER",
            "POLICY_ENGINE", "OPERATOR", "EXACT_MATCH", "TEMPLATE");

    @Override
    public List<String> validate(AgentDefinition definition) {
        List<String> errors = new ArrayList<>();
        if (definition.getDeterminism() != DeterminismGuarantee.FULL
                && definition.getDeterminism() != DeterminismGuarantee.CONFIG_SCOPED) {
            errors.add("[type:deterministic] determinism must be FULL or CONFIG_SCOPED");
        }
        if (definition.getSubtype() != null && !VALID_SUBTYPES.contains(definition.getSubtype().toUpperCase())) {
            errors.add("[type:deterministic] subtype must be one of " + VALID_SUBTYPES);
        }
        if (definition.getSystemPrompt() != null && !definition.getSystemPrompt().isBlank()) {
            errors.add("[type:deterministic] authoritative path must not use a probabilistic reasoner");
        }
        return errors;
    }
}

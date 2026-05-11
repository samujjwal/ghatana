/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.validation;

import com.ghatana.agent.framework.config.AgentDefinition;

import java.util.List;

/**
 * Validates semantics that are specific to one canonical AgentType.
 */
public interface AgentTypeSpecificValidator {
    List<String> validate(AgentDefinition definition);
}

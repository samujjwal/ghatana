/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Validator for agent tool calls during evaluation.
 *
 * @doc.type interface
 * @doc.purpose Tool call validation for evaluation
 * @doc.layer agent-core
 * @doc.pattern Strategy
 */
public interface ToolCallValidator {

    /**
     * Validates actual tool calls against expected tool calls with tolerance configuration.
     *
     * @param expected expected tool calls
     * @param actual actual tool calls
     * @param toleranceConfig tolerance configuration for validation
     * @return validation result
     */
    @NotNull
    ToolCallValidationResult validate(
            @NotNull List<Object> expected,
            @NotNull List<Object> actual,
            @NotNull Map<String, String> toleranceConfig);
}

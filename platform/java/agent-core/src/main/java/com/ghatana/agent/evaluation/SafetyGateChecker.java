/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Checker for safety gates during evaluation.
 *
 * @doc.type interface
 * @doc.purpose Safety gate checking for evaluation
 * @doc.layer agent-core
 * @doc.pattern Strategy
 */
public interface SafetyGateChecker {

    /**
     * Checks whether execution result passes all safety gates.
     *
     * @param gates safety gates to check
     * @param executionResult execution result to evaluate
     * @return safety gate check result
     */
    @NotNull
    SafetyGateCheckResult check(
            @NotNull List<EvaluationGate> gates,
            @NotNull Object executionResult);
}
